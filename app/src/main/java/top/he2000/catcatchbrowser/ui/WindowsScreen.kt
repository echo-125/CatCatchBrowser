package top.he2000.catcatchbrowser.ui

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import top.he2000.catcatchbrowser.ui.components.BrowserToolbar
import top.he2000.catcatchbrowser.ui.components.SnifferBottomSheet
import top.he2000.catcatchbrowser.ui.components.WindowSidebar
import top.he2000.catcatchbrowser.viewmodel.MainViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WindowsScreen(viewModel: MainViewModel) {
    val windows by viewModel.windows.collectAsState()
    val currentIndex by viewModel.currentWindowIndex.collectAsState()
    val sniffedUrls by viewModel.sniffedUrls.collectAsState()
    val currentUrl by viewModel.currentUrl.collectAsState()

    var showSidebar by remember { mutableStateOf(false) }
    var showSnifferPanel by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    val context = LocalContext.current
    val bridge = remember { viewModel.getBridge() }

    Box(modifier = Modifier.fillMaxSize()) {
        // 主内容：工具栏 + WebView
        Column(modifier = Modifier.fillMaxSize()) {
            // 工具栏（固定高度）
            BrowserToolbar(
                url = currentUrl,
                windowCount = windows.size,
                onWindowsClick = { showSidebar = true },
                onHomeClick = {
                    viewModel.loadHome()
                    webView?.loadUrl("https://www.baidu.com")
                },
                onBackClick = { webView?.goBack() },
                onForwardClick = { webView?.goForward() },
                onRefreshClick = { webView?.reload() },
                onUrlSubmit = { url ->
                    var finalUrl = url
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        finalUrl = "https://$url"
                    }
                    viewModel.loadUrl(finalUrl)
                    webView?.loadUrl(finalUrl)
                }
            )

            // WebView容器（用Box包裹，确保fillMaxSize生效）
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setBackgroundColor(0xFFFFFFFF.toInt())

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                allowFileAccess = true
                                allowContentAccess = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    url?.let { viewModel.updateCurrentWindowUrl(it) }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    view?.title?.let { viewModel.updateCurrentWindowTitle(it) }
                                    try {
                                        val script = ctx.assets.open("sniffer.js").bufferedReader().use { it.readText() }
                                        view?.evaluateJavascript(script, null)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    request?.url?.toString()?.let { url ->
                                        if (url.contains(".m3u8")) {
                                            // 通过bridge处理嗅探结果
                                        }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    // 进度更新
                                }
                            }

                            addJavascriptInterface(bridge, "SnifferBridge")

                            webView = this
                        }
                    },
                    update = { wv ->
                        val url = currentUrl
                        if (url.isNotEmpty() && url != wv.url) {
                            wv.loadUrl(url)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 悬浮嗅探按钮
        FloatingActionButton(
            onClick = { showSnifferPanel = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = "嗅探",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        // 侧边栏
        if (showSidebar) {
            WindowSidebar(
                windows = windows,
                currentIndex = currentIndex,
                onWindowSelect = { index ->
                    viewModel.switchToWindow(index)
                    val selectedUrl = windows.getOrNull(index)?.url ?: ""
                    if (selectedUrl.isNotEmpty()) {
                        webView?.loadUrl(selectedUrl)
                    }
                    showSidebar = false
                },
                onWindowClose = { index ->
                    viewModel.closeWindow(index)
                },
                onNewWindow = {
                    viewModel.addNewWindow()
                    showSidebar = false
                },
                onDismiss = { showSidebar = false }
            )
        }

        // 嗅探面板
        if (showSnifferPanel) {
            SnifferBottomSheet(
                sniffedUrls = sniffedUrls,
                onDownload = { sniffed ->
                    viewModel.createDownloadTask(sniffed)
                    showSnifferPanel = false
                },
                onDismiss = { showSnifferPanel = false }
            )
        }
    }
}
