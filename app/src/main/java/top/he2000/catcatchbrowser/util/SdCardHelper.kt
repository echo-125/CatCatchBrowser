package top.he2000.catcatchbrowser.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import java.io.File

/**
 * SD 卡路径辅助工具
 * 处理 /mnt/media_rw/ 路径转换、SD 卡写入权限、SAF URI 解析
 */
object SdCardHelper {

    private const val TAG = "SdCardHelper"

    /**
     * 从 SAF URI 解析出文件路径
     * 支持 primary storage 和 external storage volumes
     */
    fun resolvePathFromUri(context: Context, uri: Uri): String? {
        if (!DocumentsContract.isTreeUri(uri)) return null

        val docId = DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":")
        if (parts.size < 2) return null

        val volumeId = parts[0]
        val subPath = parts[1]

        return when (volumeId) {
            "primary" -> {
                val primary = Environment.getExternalStorageDirectory()
                if (subPath.isEmpty()) primary.absolutePath
                else "${primary.absolutePath}/$subPath"
            }
            else -> {
                // 外部存储卷（SD 卡等）
                // 尝试 /mnt/media_rw/ 路径（小米等设备直接可用）
                val mediaRwPath = "/mnt/media_rw/$volumeId/$subPath"
                if (File(mediaRwPath).exists()) {
                    Log.d(TAG, "resolvePathFromUri: 使用 /mnt/media_rw 路径: $mediaRwPath")
                    return mediaRwPath
                }
                // 回退到 /storage/ 路径
                val storagePath = "/storage/$volumeId/$subPath"
                Log.d(TAG, "resolvePathFromUri: 使用 /storage 路径: $storagePath")
                storagePath
            }
        }
    }

    /**
     * 检查路径是否是 SD 卡路径
     */
    fun isSdCardPath(path: String): Boolean {
        val normalized = path.replace('\\', '/')

        if (normalized.startsWith("/mnt/media_rw/")) {
            val volId = normalized.removePrefix("/mnt/media_rw/").substringBefore('/')
            return volId.isNotEmpty() && volId.contains('-')
        }

        if (normalized.startsWith("/storage/")) {
            val volId = normalized.removePrefix("/storage/").substringBefore('/')
            if (volId == "emulated" || volId == "self") return false
            return volId.isNotEmpty() && volId.contains('-')
        }

        return false
    }

    /**
     * 从路径提取 SD 卡卷 ID
     */
    fun extractVolumeId(path: String): String? {
        val normalized = path.replace('\\', '/')
        val prefix = when {
            normalized.startsWith("/mnt/media_rw/") -> "/mnt/media_rw/"
            normalized.startsWith("/storage/") -> "/storage/"
            else -> return null
        }
        val volId = normalized.removePrefix(prefix).substringBefore('/')
        return volId.takeIf { it.isNotEmpty() && it.contains('-') }
    }

    /**
     * 规范化下载路径
     * - 优先尝试 /storage/ 路径（标准 AOSP 行为）
     * - 如果 /storage/ 路径不存在，保留原始 /mnt/media_rw/ 路径（小米等设备）
     */
    fun normalizePath(path: String): String {
        var normalized = path.replace('\\', '/')
        if (normalized.length > 1 && normalized.endsWith('/')) {
            normalized = normalized.trimEnd('/')
        }

        if (normalized.startsWith("/mnt/media_rw/")) {
            val storagePath = normalized.replace("/mnt/media_rw/", "/storage/")
            val storageParent = File(File(storagePath).parent ?: "/storage")
            if (storageParent.exists()) {
                return storagePath
            }
            return normalized
        }

        return normalized
    }

    /**
     * 检查是否有所有文件访问权限 (Android 11+)
     */
    fun hasManageStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * 创建请求所有文件访问权限的 Intent
     */
    fun createManageStorageIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:top.he2000.catcatchbrowser")
            }
        } else {
            null
        }
    }

    /**
     * 检查路径是否可写
     */
    fun isPathWritable(path: String): Boolean {
        val normalizedPath = normalizePath(path)
        val dir = File(normalizedPath)
        if (!dir.exists()) dir.mkdirs()
        return dir.exists() && dir.canWrite()
    }

    enum class PathStatus {
        OK,
        NOT_SD_CARD,
        CANNOT_CREATE,
        NEED_PERMISSION,
        NOT_WRITABLE
    }
}
