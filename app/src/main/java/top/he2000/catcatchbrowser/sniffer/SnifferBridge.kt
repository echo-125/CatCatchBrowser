package top.he2000.catcatchbrowser.sniffer

import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.he2000.catcatchbrowser.data.SniffedM3u8

/**
 * JS Bridge
 * 用于接收 WebView 中注入脚本的消息
 */
class SnifferBridge {

    private val _sniffedUrls = MutableStateFlow<List<SniffedM3u8>>(emptyList())
    val sniffedUrls: StateFlow<List<SniffedM3u8>> = _sniffedUrls.asStateFlow()

    private var onM3u8Detected: ((SniffedM3u8) -> Unit)? = null

    /**
     * 设置回调
     */
    fun setOnM3u8Detected(callback: (SniffedM3u8) -> Unit) {
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

        // 更新列表
        val currentList = _sniffedUrls.value.toMutableList()

        // 避免重复
        if (currentList.none { it.url == url }) {
            currentList.add(0, sniffed)
            _sniffedUrls.value = currentList.take(50)

            // 回调通知
            onM3u8Detected?.invoke(sniffed)
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

        onM3u8Detected?.invoke(sniffed)
    }

    /**
     * JS 调用：记录日志
     */
    @JavascriptInterface
    fun log(message: String) {
        android.util.Log.d("SnifferBridge", message)
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
     * 清空捕获的链接
     */
    fun clear() {
        _sniffedUrls.value = emptyList()
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
