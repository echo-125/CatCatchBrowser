package top.he2000.catcatchbrowser.sniffer

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.he2000.catcatchbrowser.data.SniffedM3u8
import java.util.concurrent.ConcurrentHashMap

/**
 * M3U8 嗅探器
 * 通过拦截 WebView 网络请求来捕获 M3U8 链接
 */
class M3u8Sniffer(
    private val onM3u8Detected: (SniffedM3u8) -> Unit
) : WebViewClient() {

    private val capturedUrls = ConcurrentHashMap.newKeySet<String>()

    private val _sniffedUrls = MutableStateFlow<List<SniffedM3u8>>(emptyList())
    val sniffedUrls: StateFlow<List<SniffedM3u8>> = _sniffedUrls.asStateFlow()

    // 缓存当前页面 URL（避免在后台线程调用 WebView.getUrl()）
    @Volatile
    private var currentPageUrl: String = ""

    // 页面加载完成回调
    var onPageFinishedCallback: ((WebView?, String?) -> Unit)? = null

    // 页面开始加载回调
    var onPageStartedCallback: ((WebView?, String?) -> Unit)? = null

    // 广告关键词
    private val adKeywords = listOf("ad", "ads", "adv", "advertisement", "silent-basis")

    // 目标标记
    private val targetMarkers = listOf("index.jpg", "index.m3u8")

    // 最小时长过滤（秒）
    var minDurationFilter: Int = 90

    /**
     * 页面开始加载时更新当前 URL
     */
    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        currentPageUrl = url ?: ""
        onPageStartedCallback?.invoke(view, url)
    }

    /**
     * 页面加载完成
     */
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onPageFinishedCallback?.invoke(view, url)
    }

    /**
     * 拦截请求
     */
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        request?.url?.toString()?.let { url ->
            processUrl(url, currentPageUrl)
        }
        return super.shouldInterceptRequest(view, request)
    }

    /**
     * 处理 URL
     */
    private fun processUrl(url: String, pageUrl: String) {
        // 只处理 M3U8 链接
        if (!url.lowercase().contains(".m3u8")) {
            return
        }

        // 避免重复捕获
        if (capturedUrls.contains(url)) {
            return
        }

        // 过滤广告链接
        if (isAdUrl(url)) {
            return
        }

        capturedUrls.add(url)

        // 构建请求头
        val headers = mapOf(
            "origin" to extractOrigin(pageUrl),
            "referer" to pageUrl
        )

        // 判断是否是目标链接
        val isTarget = targetMarkers.any { url.lowercase().contains(it) }

        val sniffed = SniffedM3u8(
            url = url,
            requestHeaders = headers,
            isTarget = isTarget
        )

        // 更新列表
        val currentList = _sniffedUrls.value.toMutableList()
        currentList.add(0, sniffed)
        _sniffedUrls.value = currentList.take(50) // 保留最近 50 条

        // 回调通知
        onM3u8Detected(sniffed)
    }

    /**
     * 判断是否是广告 URL
     */
    private fun isAdUrl(url: String): Boolean {
        val lower = url.lowercase()
        return adKeywords.any { lower.contains(it) }
    }

    /**
     * 提取 origin
     */
    private fun extractOrigin(url: String): String {
        return try {
            val uri = java.net.URI(url)
            "${uri.scheme}://${uri.host}"
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 清空捕获的链接
     */
    fun clear() {
        capturedUrls.clear()
        _sniffedUrls.value = emptyList()
    }

    /**
     * 获取所有捕获的链接
     */
    fun getAllSniffedUrls(): List<SniffedM3u8> = _sniffedUrls.value

    /**
     * 移除指定链接
     */
    fun removeSniffedUrl(url: String) {
        capturedUrls.remove(url)
        _sniffedUrls.value = _sniffedUrls.value.filter { it.url != url }
    }
}
