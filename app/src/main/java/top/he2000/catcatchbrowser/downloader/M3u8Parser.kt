package top.he2000.catcatchbrowser.downloader

import top.he2000.catcatchbrowser.data.M3u8ParseResult
import top.he2000.catcatchbrowser.data.M3u8Variant
import java.net.URL

/**
 * M3U8 播放列表解析器
 * 移植自 Python 项目的 m3u8_downloader.py
 */
object M3u8Parser {

    private const val MAX_PLAYLIST_DEPTH = 5

    /**
     * 解析 M3U8 内容
     * @param content M3U8 文件内容
     * @param baseUrl 基础 URL，用于解析相对路径
     * @return 解析结果，可能是主播放列表或媒体播放列表
     */
    fun parse(content: String, baseUrl: String): M3u8ParseResult {
        val lines = content.lines()
        if (lines.isEmpty() || !lines[0].trim().uppercase().startsWith("#EXTM3U")) {
            throw IllegalArgumentException("不是有效的 M3U8 文件")
        }

        // 检查是否是主播放列表（包含多个码率）
        val hasStreamInf = lines.any { it.trim().startsWith("#EXT-X-STREAM-INF") }

        return if (hasStreamInf) {
            parseMasterPlaylist(lines, baseUrl)
        } else {
            parseMediaPlaylist(lines, baseUrl)
        }
    }

    /**
     * 解析主播放列表（Master Playlist）
     */
    private fun parseMasterPlaylist(lines: List<String>, baseUrl: String): M3u8ParseResult.MasterPlaylist {
        val variants = mutableListOf<M3u8Variant>()
        var pendingBandwidth = -1
        var pendingResolution: String? = null
        var pendingCodecs: String? = null
        var pendingFrameRate: Float? = null

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#EXT-X-STREAM-INF:")) {
                // 解析属性
                pendingBandwidth = extractBandwidth(trimmed)
                pendingResolution = extractResolution(trimmed)
                pendingCodecs = extractCodecs(trimmed)
                pendingFrameRate = extractFrameRate(trimmed)
            } else if (!trimmed.startsWith("#")) {
                // 这是一个 URL
                val url = resolveUrl(trimmed, baseUrl)
                if (url.isNotEmpty()) {
                    variants.add(
                        M3u8Variant(
                            url = url,
                            bandwidth = pendingBandwidth,
                            resolution = pendingResolution,
                            codecs = pendingCodecs,
                            frameRate = pendingFrameRate
                        )
                    )
                }
                // 重置临时变量
                pendingBandwidth = -1
                pendingResolution = null
                pendingCodecs = null
                pendingFrameRate = null
            }
        }

        return M3u8ParseResult.MasterPlaylist(variants)
    }

    /**
     * 解析媒体播放列表（Media Playlist）
     */
    private fun parseMediaPlaylist(lines: List<String>, baseUrl: String): M3u8ParseResult.MediaPlaylist {
        val segments = mutableListOf<String>()
        var totalDuration = 0.0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // 解析分片时长
            if (trimmed.startsWith("#EXTINF:")) {
                val durationMatch = Regex("#EXTINF:([\\d.]+)").find(trimmed)
                durationMatch?.groupValues?.get(1)?.toDoubleOrNull()?.let {
                    totalDuration += it
                }
            } else if (!trimmed.startsWith("#")) {
                // 这是一个分片 URL
                val url = resolveUrl(trimmed, baseUrl)
                if (url.isNotEmpty()) {
                    segments.add(url)
                }
            }
        }

        return M3u8ParseResult.MediaPlaylist(segments, totalDuration)
    }

    /**
     * 解析相对 URL
     */
    private fun resolveUrl(url: String, baseUrl: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }

        return try {
            val base = URL(baseUrl)
            URL(base, url).toString()
        } catch (e: Exception) {
            // 简单的路径拼接
            val baseEnd = baseUrl.lastIndexOf('/')
            if (baseEnd > 0) {
                baseUrl.substring(0, baseEnd + 1) + url
            } else {
                url
            }
        }
    }

    private fun extractBandwidth(line: String): Int {
        val match = Regex("BANDWIDTH=(\\d+)", RegexOption.IGNORE_CASE).find(line)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }

    private fun extractResolution(line: String): String? {
        val match = Regex("RESOLUTION=([\\d]+x[\\d]+)", RegexOption.IGNORE_CASE).find(line)
        return match?.groupValues?.get(1)
    }

    private fun extractCodecs(line: String): String? {
        val match = Regex("CODECS=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(line)
        return match?.groupValues?.get(1)
    }

    private fun extractFrameRate(line: String): Float? {
        val match = Regex("FRAME-RATE=([\\d.]+)", RegexOption.IGNORE_CASE).find(line)
        return match?.groupValues?.get(1)?.toFloatOrNull()
    }

    /**
     * 判断 URL 是否是 M3U8 文件
     */
    fun isM3u8Url(url: String): Boolean {
        val path = url.split("?").firstOrNull()?.lowercase() ?: return false
        return path.endsWith(".m3u8")
    }

    /**
     * 格式化时长
     */
    fun formatDuration(seconds: Double): String {
        if (seconds <= 0) return "未知"

        val totalSeconds = seconds.toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60

        return when {
            hours > 0 -> "${hours}小时${minutes}分${secs}秒"
            minutes > 0 -> "${minutes}分${secs}秒"
            else -> "${secs}秒"
        }
    }

    /**
     * 格式化带宽
     */
    fun formatBandwidth(bps: Int): String {
        return when {
            bps >= 1_000_000 -> "%.1f Mbps".format(bps / 1_000_000.0)
            bps >= 1_000 -> "%d Kbps".format(bps / 1_000)
            else -> "$bps bps"
        }
    }
}
