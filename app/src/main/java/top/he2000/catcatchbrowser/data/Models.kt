package top.he2000.catcatchbrowser.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 下载任务实体
 */
@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val fileName: String,
    val savePath: String,
    val requestHeaders: String, // JSON 格式的请求头
    val status: String = "pending", // pending, downloading, paused, completed, failed, cancelled
    val progress: Float = 0f, // 0-100
    val downloadedSegments: Int = 0,
    val totalSegments: Int = 0,
    val currentSpeed: String = "",
    val errorMessage: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 下载任务状态
 */
enum class TaskStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED;

    companion object {
        fun fromString(value: String): TaskStatus = when (value.lowercase()) {
            "pending" -> PENDING
            "downloading" -> DOWNLOADING
            "paused" -> PAUSED
            "completed" -> COMPLETED
            "failed" -> FAILED
            "cancelled" -> CANCELLED
            else -> PENDING
        }
    }
}

/**
 * M3U8 播放列表类型
 */
enum class M3u8PlaylistType {
    MASTER,  // 主播放列表，包含多个码率
    MEDIA    // 媒体播放列表，包含分片
}

/**
 * M3U8 变体（码率/分辨率选项）
 */
data class M3u8Variant(
    val url: String,
    val bandwidth: Int,
    val resolution: String? = null,
    val codecs: String? = null,
    val frameRate: Float? = null
)

/**
 * M3U8 解析结果
 */
sealed class M3u8ParseResult {
    data class MasterPlaylist(
        val variants: List<M3u8Variant>
    ) : M3u8ParseResult()

    data class MediaPlaylist(
        val segments: List<String>,
        val duration: Double
    ) : M3u8ParseResult()
}

/**
 * 嗅探到的 M3U8 链接
 */
data class SniffedM3u8(
    val url: String,
    val title: String? = null,
    val duration: Double = 0.0,
    val requestHeaders: Map<String, String> = emptyMap(),
    val variants: List<M3u8Variant> = emptyList(),
    val isTarget: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 下载进度信息
 */
data class DownloadProgress(
    val taskId: Long,
    val status: TaskStatus,
    val progress: Float,
    val downloadedSegments: Int,
    val totalSegments: Int,
    val speed: String,
    val errorMessage: String = ""
)
