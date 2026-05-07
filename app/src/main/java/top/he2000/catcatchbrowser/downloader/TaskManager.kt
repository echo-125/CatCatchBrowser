package top.he2000.catcatchbrowser.downloader

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import top.he2000.catcatchbrowser.data.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 下载任务管理器
 * 负责任务的创建、启动、暂停、恢复、删除等操作
 */
class TaskManager(private val context: Context) {

    private val database: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "catcatchbrowser.db"
    ).build()

    private val taskDao: DownloadTaskDao = database.downloadTaskDao()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val downloaders = ConcurrentHashMap<Long, SegmentDownloader>()
    private val downloadJobs = ConcurrentHashMap<Long, Job>()
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 最大并发下载数
    var maxConcurrentDownloads: Int = 3

    // 当前活动下载数
    private val activeDownloads = MutableStateFlow(0)

    /**
     * 观察所有任务
     */
    fun observeAllTasks(): Flow<List<DownloadTaskEntity>> = taskDao.observeAll()

    /**
     * 获取所有任务
     */
    suspend fun getAllTasks(): List<DownloadTaskEntity> = taskDao.getAll()

    /**
     * 创建新任务
     */
    suspend fun createTask(
        url: String,
        fileName: String,
        savePath: String,
        requestHeaders: Map<String, String> = emptyMap()
    ): Long {
        val task = DownloadTaskEntity(
            url = url,
            fileName = fileName,
            savePath = savePath,
            requestHeaders = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.serializer<Map<String, String>>(),
                requestHeaders
            ),
            status = TaskStatus.PENDING.name.lowercase()
        )
        return taskDao.insert(task)
    }

    /**
     * 启动任务
     */
    fun startTask(taskId: Long) {
        val job = downloadJobs[taskId]
        if (job != null && job.isActive) {
            return // 已经在运行
        }

        downloadJobs[taskId] = downloadScope.launch {
            val task = taskDao.getById(taskId) ?: return@launch

            // 更新状态为下载中
            taskDao.updateStatus(taskId, TaskStatus.DOWNLOADING.name.lowercase())

            val downloader = SegmentDownloader(okHttpClient)
            downloaders[taskId] = downloader

            // 解析请求头
            val headers = try {
                kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(task.requestHeaders)
            } catch (e: Exception) {
                emptyMap()
            }

            // 输出文件
            val outputFile = File(task.savePath, "${task.fileName}.ts")

            val result = downloader.download(
                m3u8Url = task.url,
                outputFile = outputFile,
                requestHeaders = headers,
                concurrency = 16,
                scope = downloadScope
            ) { downloaded, total, speed ->
                // 更新进度
                val progress = if (total > 0) (downloaded.toFloat() / total) * 100 else 0f
                downloadScope.launch {
                    taskDao.updateProgress(taskId, progress, downloaded, total, speed)
                }
            }

            // 处理结果
            when (result) {
                is SegmentDownloader.Result.Success -> {
                    taskDao.update(
                        task.copy(
                            status = TaskStatus.COMPLETED.name.lowercase(),
                            progress = 100f,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                is SegmentDownloader.Result.Error -> {
                    taskDao.update(
                        task.copy(
                            status = TaskStatus.FAILED.name.lowercase(),
                            errorMessage = result.message,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                is SegmentDownloader.Result.Cancelled -> {
                    taskDao.update(
                        task.copy(
                            status = TaskStatus.CANCELLED.name.lowercase(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }

            downloaders.remove(taskId)
            downloadJobs.remove(taskId)
            activeDownloads.value--
        }
    }

    /**
     * 暂停任务
     */
    fun pauseTask(taskId: Long) {
        downloaders[taskId]?.cancel()
        downloadJobs[taskId]?.cancel()
        downloaders.remove(taskId)
        downloadJobs.remove(taskId)

        downloadScope.launch {
            taskDao.updateStatus(taskId, TaskStatus.PAUSED.name.lowercase())
        }
    }

    /**
     * 取消任务
     */
    fun cancelTask(taskId: Long) {
        pauseTask(taskId)
        downloadScope.launch {
            taskDao.updateStatus(taskId, TaskStatus.CANCELLED.name.lowercase())
        }
    }

    /**
     * 删除任务
     */
    suspend fun deleteTask(taskId: Long) {
        cancelTask(taskId)
        taskDao.deleteById(taskId)
    }

    /**
     * 重试失败的任务
     */
    suspend fun retryTask(taskId: Long) {
        val task = taskDao.getById(taskId) ?: return
        if (task.status == TaskStatus.FAILED.name.lowercase() ||
            task.status == TaskStatus.CANCELLED.name.lowercase()
        ) {
            taskDao.update(
                task.copy(
                    status = TaskStatus.PENDING.name.lowercase(),
                    progress = 0f,
                    downloadedSegments = 0,
                    errorMessage = "",
                    updatedAt = System.currentTimeMillis()
                )
            )
            startTask(taskId)
        }
    }

    /**
     * 清除已完成的任务
     */
    suspend fun clearCompletedTasks() {
        taskDao.deleteFinished()
    }

    /**
     * 开始所有等待中的任务
     */
    suspend fun startAllPending() {
        val pendingTasks = taskDao.getByStatus(TaskStatus.PENDING.name.lowercase())
        for (task in pendingTasks) {
            if (activeDownloads.value < maxConcurrentDownloads) {
                startTask(task.id)
                activeDownloads.value++
            } else {
                break
            }
        }
    }

    /**
     * 暂停所有任务
     */
    fun pauseAll() {
        downloaders.values.forEach { it.cancel() }
        downloadJobs.values.forEach { it.cancel() }
        downloaders.clear()
        downloadJobs.clear()

        downloadScope.launch {
            val downloadingTasks = taskDao.getByStatus(TaskStatus.DOWNLOADING.name.lowercase())
            downloadingTasks.forEach {
                taskDao.updateStatus(it.id, TaskStatus.PAUSED.name.lowercase())
            }
        }
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        pauseAll()
        downloadScope.cancel()
        database.close()
    }
}
