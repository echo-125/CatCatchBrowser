package top.he2000.catcatchbrowser.downloader

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import top.he2000.catcatchbrowser.data.M3u8ParseResult
import top.he2000.catcatchbrowser.data.M3u8Variant
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * M3U8 分片下载器
 * 移植自 Python 项目的 m3u8_downloader.py
 */
class SegmentDownloader(
    private val okHttpClient: OkHttpClient,
    private val maxRetries: Int = 3,
    private val retryDelayMs: Long = 2000
) {
    /**
     * 下载回调接口
     */
    interface Callback {
        fun onProgress(downloaded: Int, total: Int, speed: String)
        fun onSegmentDownloaded(index: Int, success: Boolean)
        fun onComplete(outputFile: File)
        fun onError(message: String)
    }

    /**
     * 下载结果
     */
    sealed class Result {
        data class Success(val outputFile: File) : Result()
        data class Error(val message: String) : Result()
        data object Cancelled : Result()
    }

    private val isCancelled = AtomicBoolean(false)
    private val activeJobs = ConcurrentHashMap<Int, Job>()
    private val totalBytes = AtomicLong(0)

    // 滑动窗口速度追踪
    private val speedSamples = mutableListOf<SpeedSample>()
    private val speedLock = Any()

    private data class SpeedSample(val timestamp: Long, val totalBytes: Long)

    /**
     * 下载 M3U8 视频
     * @param m3u8Url M3U8 链接
     * @param outputFile 输出文件
     * @param requestHeaders 请求头
     * @param concurrency 并发数
     * @param scope 协程作用域
     * @param progressCallback 进度回调
     */
    suspend fun download(
        m3u8Url: String,
        outputFile: File,
        requestHeaders: Map<String, String> = emptyMap(),
        concurrency: Int = 16,
        scope: CoroutineScope,
        initialDownloadedSegments: Int = 0,
        initialBytes: Long = 0,
        progressCallback: ((downloaded: Int, total: Int, speed: String) -> Unit)? = null
    ): Result = withContext(Dispatchers.IO) {
        isCancelled.set(false)
        totalBytes.set(initialBytes)
        synchronized(speedLock) {
            speedSamples.clear()
            speedSamples.add(SpeedSample(System.currentTimeMillis(), initialBytes))
        }

        try {
            // 1. 下载 M3U8 内容
            val m3u8Content = downloadM3u8Content(m3u8Url, requestHeaders)
            if (isCancelled.get()) return@withContext Result.Cancelled

            // 2. 解析 M3U8
            val segments = parseM3u8WithRecursion(m3u8Content, m3u8Url, requestHeaders)
            if (segments.isEmpty()) {
                return@withContext Result.Error("未找到视频分片")
            }
            if (isCancelled.get()) return@withContext Result.Cancelled

            // 3. 创建临时目录
            val tempDir = File(outputFile.parent, "${outputFile.nameWithoutExtension}_temp")
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }

            // 4. 并发下载分片
            val totalSegments = segments.size
            val downloadedCount = AtomicInteger(initialDownloadedSegments)
            val failedCount = AtomicInteger(0)
            val startTime = System.currentTimeMillis()

            val results = ConcurrentHashMap<Int, File?>()

            withContext(Dispatchers.IO) {
                segments.mapIndexed { index, segmentUrl ->
                    async {
                        if (isCancelled.get()) return@async

                        val segmentFile = File(tempDir, "segment_${index.toString().padStart(5, '0')}.ts")

                        // 检查已存在的分片
                        if (segmentFile.exists() && segmentFile.length() > 0) {
                            downloadedCount.incrementAndGet()
                            results[index] = segmentFile
                            return@async
                        }

                        // 下载分片
                        val success = downloadSegment(segmentUrl, segmentFile, requestHeaders)
                        if (success) {
                            downloadedCount.incrementAndGet()
                            results[index] = segmentFile
                        } else {
                            failedCount.incrementAndGet()
                            results[index] = null
                        }

                        // 更新进度
                        val current = downloadedCount.get()
                        val speed = calculateSpeed()
                        progressCallback?.invoke(current, totalSegments, speed)
                    }
                }.awaitAll()
            }

            if (isCancelled.get()) {
                return@withContext Result.Cancelled
            }

            // 5. 重试失败的分片
            val failedIndices = results.filter { it.value == null }.keys.toList()
            if (failedIndices.isNotEmpty()) {
                retryFailedSegments(
                    segments, failedIndices, results, tempDir,
                    requestHeaders, downloadedCount, totalSegments, startTime, progressCallback
                )
            }

            // 6. 合并分片
            val validSegments = (0 until totalSegments).mapNotNull { results[it] }
            if (validSegments.isEmpty()) {
                return@withContext Result.Error("没有成功下载任何分片")
            }

            mergeSegments(validSegments, outputFile)

            // 7. 清理临时文件
            tempDir.deleteRecursively()

            Result.Success(outputFile)

        } catch (e: CancellationException) {
            Result.Cancelled
        } catch (e: Exception) {
            Result.Error(e.message ?: "下载失败")
        }
    }

    /**
     * 下载 M3U8 内容
     */
    private suspend fun downloadM3u8Content(
        url: String,
        headers: Map<String, String>
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (key, value) -> addHeader(key, value) } }
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("下载播放列表失败: ${response.code}")
            }
            response.body?.string() ?: throw IOException("响应内容为空")
        }
    }

    /**
     * 递归解析 M3U8（支持主播放列表）
     */
    private suspend fun parseM3u8WithRecursion(
        content: String,
        baseUrl: String,
        headers: Map<String, String>,
        depth: Int = 0
    ): List<String> {
        if (depth > MAX_PLAYLIST_DEPTH) {
            throw IllegalArgumentException("M3U8 嵌套层级过深")
        }

        when (val result = M3u8Parser.parse(content, baseUrl)) {
            is M3u8ParseResult.MediaPlaylist -> {
                return result.segments
            }
            is M3u8ParseResult.MasterPlaylist -> {
                // 选择带宽最高的变体
                val selectedVariant = result.variants.maxByOrNull { it.bandwidth }
                    ?: throw IllegalArgumentException("未找到有效的播放列表")

                val childContent = downloadM3u8Content(selectedVariant.url, headers)
                return parseM3u8WithRecursion(childContent, selectedVariant.url, headers, depth + 1)
            }
        }
    }

    /**
     * 下载单个分片
     */
    private suspend fun downloadSegment(
        url: String,
        outputFile: File,
        headers: Map<String, String>
    ): Boolean {
        repeat(maxRetries) { retry ->
            if (isCancelled.get()) return false

            try {
                val request = Request.Builder()
                    .url(url)
                    .apply { headers.forEach { (key, value) -> addHeader(key, value) } }
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            outputFile.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    totalBytes.addAndGet(bytesRead.toLong())
                                    // 每读取 64KB 记录一次速度采样
                                    val currentTotal = totalBytes.get()
                                    if (currentTotal % 65536 < 8192) {
                                        recordSpeedSample(currentTotal)
                                    }
                                }
                            }
                        }
                        return true
                    }
                }
            } catch (e: Exception) {
                // 忽略，继续重试
            }

            if (retry < maxRetries - 1) {
                delay(retryDelayMs * (1L shl retry))
            }
        }
        return false
    }

    /**
     * 记录速度采样点
     */
    private fun recordSpeedSample(bytes: Long) {
        synchronized(speedLock) {
            val now = System.currentTimeMillis()
            speedSamples.add(SpeedSample(now, bytes))
            // 保留最近 10 秒的采样
            val cutoff = now - 10_000
            speedSamples.removeAll { it.timestamp < cutoff }
        }
    }

    /**
     * 基于滑动窗口计算当前速度
     * 使用最近 5 秒的字节增量计算，而非全局平均
     */
    private fun calculateSpeed(): String {
        synchronized(speedLock) {
            if (speedSamples.size < 2) return ""

            val now = System.currentTimeMillis()
            val windowMs = 5_000L // 5 秒窗口
            val cutoff = now - windowMs

            // 找到窗口内最早的采样点
            val windowStart = speedSamples.firstOrNull { it.timestamp >= cutoff }
                ?: speedSamples.first()

            val windowEnd = speedSamples.last()
            val elapsed = (windowEnd.timestamp - windowStart.timestamp) / 1000.0

            if (elapsed <= 0) return ""

            val bytesInWindow = windowEnd.totalBytes - windowStart.totalBytes
            val bytesPerSecond = (bytesInWindow / elapsed).toLong()

            return formatSpeed(bytesPerSecond)
        }
    }

    /**
     * 重试失败的分片
     */
    private suspend fun retryFailedSegments(
        segments: List<String>,
        failedIndices: List<Int>,
        results: ConcurrentHashMap<Int, File?>,
        tempDir: File,
        headers: Map<String, String>,
        downloadedCount: AtomicInteger,
        totalSegments: Int,
        startTime: Long,
        progressCallback: ((Int, Int, String) -> Unit)?
    ) {
        for (retry in 0 until maxRetries) {
            if (failedIndices.isEmpty() || isCancelled.get()) break

            val stillFailed = mutableListOf<Int>()

            withContext(Dispatchers.IO) {
                failedIndices.map { index ->
                    async {
                        val segmentFile = File(tempDir, "segment_${index.toString().padStart(5, '0')}.ts")
                        val success = downloadSegment(segments[index], segmentFile, headers)

                        if (success) {
                            results[index] = segmentFile
                            downloadedCount.incrementAndGet()
                        } else {
                            stillFailed.add(index)
                        }

                        val current = downloadedCount.get()
                        val speed = calculateSpeed()
                        progressCallback?.invoke(current, totalSegments, speed)
                    }
                }.awaitAll()
            }

            if (stillFailed.isEmpty()) break
            delay(retryDelayMs)
        }
    }

    /**
     * 合并分片
     */
    private fun mergeSegments(segmentFiles: List<File>, outputFile: File) {
        outputFile.outputStream().use { output ->
            segmentFiles.forEach { file ->
                file.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
        }
    }

    /**
     * 取消下载
     */
    fun cancel() {
        isCancelled.set(true)
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }

    fun getTotalBytesDownloaded(): Long = totalBytes.get()

    companion object {
        private const val MAX_PLAYLIST_DEPTH = 5

        /**
         * 格式化速度
         */
        fun formatSpeed(bps: Long): String = when {
            bps >= 1_000_000 -> "%.1f MB/s".format(bps / 1_000_000.0)
            bps >= 1_000 -> "%.1f KB/s".format(bps / 1_000.0)
            else -> "$bps B/s"
        }

        /**
         * 格式化文件大小
         */
        fun formatSize(bytes: Long): String = when {
            bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024 -> "%.0f KB".format(bytes / 1_024.0)
            else -> "$bytes B"
        }

        /**
         * 格式化剩余时间
         */
        fun formatEta(seconds: Long): String = when {
            seconds < 0 -> "--:--"
            seconds < 60 -> "${seconds}秒"
            seconds < 3600 -> "${seconds / 60}分${seconds % 60}秒"
            else -> "${seconds / 3600}时${(seconds % 3600) / 60}分"
        }
    }
}
