package top.he2000.catcatchbrowser.sniffer

import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.he2000.catcatchbrowser.data.SniffedM3u8

/**
 * JS Bridge
 * 用于接收 WebView 中注入脚本的消息
 * 支持多窗口嗅探隔离
 */
class SnifferBridge {

    private val _sniffedUrls = MutableStateFlow<List<SniffedM3u8>>(emptyList())
    val sniffedUrls: StateFlow<List<SniffedM3u8>> = _sniffedUrls.asStateFlow()

    // 日志列表
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var onM3u8Detected: ((SniffedM3u8, String?) -> Unit)? = null

    private val logTimeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())

    // 当前窗口 ID
    private var currentWindowId: String? = null

    /**
     * 设置当前窗口 ID
     */
    fun setCurrentWindowId(windowId: String) {
        currentWindowId = windowId
    }

    /**
     * 设置回调
     */
    fun setOnM3u8Detected(callback: (SniffedM3u8, String?) -> Unit) {
        onM3u8Detected = callback
    }

    /**
     * JS 调用：报告捕获的 M3U8 URL
     */
    @JavascriptInterface
    fun reportM3u8(url: String, title: String?, duration: Double, headersJson: String?) {
        val headers = parseHeadersJson(headersJson)

        val sniffed = SniffedM3u8(
            url = url,
            title = title,
            duration = duration,
            requestHeaders = headers
        )

        val currentList = _sniffedUrls.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.url == url }
        if (existingIndex >= 0) {
            val existing = currentList[existingIndex]
            // 只在新数据更丰富时替换（保留已有的更好数据）
            val better = (duration > 0 && existing.duration == 0.0) ||
                (title != null && title.isNotEmpty() && existing.title.isNullOrEmpty())
            if (better) {
                val updated = existing.copy(
                    duration = if (duration > 0) duration else existing.duration,
                    title = title ?: existing.title,
                    requestHeaders = if (headers.isNotEmpty()) headers else existing.requestHeaders
                )
                currentList[existingIndex] = updated
                _sniffedUrls.value = currentList
                onM3u8Detected?.invoke(updated, currentWindowId)
            }
        } else {
            currentList.add(0, sniffed)
            _sniffedUrls.value = currentList.take(50)
            onM3u8Detected?.invoke(sniffed, currentWindowId)
        }
    }

    /**
     * JS 调用：报告播放列表变体
     */
    @JavascriptInterface
    fun reportPlaylistVariants(url: String, variantsJson: String, headersJson: String?) {
        // 解析变体信息
        val variants = parseVariantsJson(variantsJson)
        val headers = parseHeadersJson(headersJson)

        val sniffed = SniffedM3u8(
            url = url,
            requestHeaders = headers,
            variants = variants
        )

        // 更新列表
        val currentList = _sniffedUrls.value.toMutableList()

        // 移除旧的相同 URL，添加新的
        currentList.removeAll { it.url == url }
        currentList.add(0, sniffed)
        _sniffedUrls.value = currentList.take(50)

        onM3u8Detected?.invoke(sniffed, currentWindowId)
    }

    /**
     * JS 调用：记录日志
     */
    @JavascriptInterface
    fun log(message: String) {
        android.util.Log.d("SnifferBridge", message)
        // 添加到日志列表
        val timestamp = logTimeFormat.format(java.util.Date())
        val logEntry = "[$timestamp] $message"
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(0, logEntry)
        _logs.value = currentLogs.take(100) // 保留最近100条
    }

    /**
     * JS 调用：报告脚本加载成功
     */
    @JavascriptInterface
    fun onScriptLoaded(mode: String) {
        log("脚本已加载: $mode")
    }

    /**
     * 原生层调用：从 shouldInterceptRequest 捕获 m3u8 URL
     */
    fun reportM3u8FromNative(url: String, headers: Map<String, String>, duration: Double = 0.0) {
        android.util.Log.d("SnifferBridge", "Native captured: $url (duration=${duration}s)")
        log("[原生捕获] $url (${duration}s)")

        val currentList = _sniffedUrls.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.url == url }
        if (existingIndex >= 0) {
            val existing = currentList[existingIndex]
            // 只在新数据更丰富时替换（保留已有的更好数据）
            if (duration > 0 && existing.duration == 0.0) {
                val updated = existing.copy(duration = duration)
                currentList[existingIndex] = updated
                _sniffedUrls.value = currentList
                onM3u8Detected?.invoke(updated, currentWindowId)
            }
            // duration=0 的新数据不覆盖已有条目
        } else {
            val sniffed = SniffedM3u8(
                url = url, title = null, duration = duration, requestHeaders = headers
            )
            currentList.add(0, sniffed)
            _sniffedUrls.value = currentList.take(50)
            onM3u8Detected?.invoke(sniffed, currentWindowId)
        }
    }

    /**
     * 解析请求头 JSON
     */
    private fun parseHeadersJson(json: String?): Map<String, String> {
        if (json.isNullOrEmpty()) return emptyMap()

        return try {
            kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(json)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * 解析变体 JSON
     */
    private fun parseVariantsJson(json: String): List<top.he2000.catcatchbrowser.data.M3u8Variant> {
        if (json.isEmpty()) return emptyList()

        return try {
            kotlinx.serialization.json.Json.decodeFromString<List<VariantJson>>(json).map {
                top.he2000.catcatchbrowser.data.M3u8Variant(
                    url = it.url,
                    bandwidth = it.bandwidth,
                    resolution = it.resolution,
                    codecs = it.codecs,
                    frameRate = it.frameRate
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 清空当前捕获的链接和日志
     */
    fun clear() {
        _sniffedUrls.value = emptyList()
        _logs.value = emptyList()
    }

    /**
     * 清空日志
     */
    fun clearLogs() {
        _logs.value = emptyList()
    }

    /**
     * 变体 JSON 数据类
     */
    @kotlinx.serialization.Serializable
    private data class VariantJson(
        val url: String,
        val bandwidth: Int = 0,
        val resolution: String? = null,
        val codecs: String? = null,
        val frameRate: Float? = null
    )
}
