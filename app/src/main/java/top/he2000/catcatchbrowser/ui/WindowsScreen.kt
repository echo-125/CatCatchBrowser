package top.he2000.catcatchbrowser.ui

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import top.he2000.catcatchbrowser.data.BookmarkEntity
import top.he2000.catcatchbrowser.ui.components.BrowserToolbar
import top.he2000.catcatchbrowser.ui.components.SnifferBottomSheet
import top.he2000.catcatchbrowser.ui.components.WindowSidebar
import top.he2000.catcatchbrowser.viewmodel.MainViewModel
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WindowsScreen(viewModel: MainViewModel) {
    val windows by viewModel.windows.collectAsState()
    val currentIndex by viewModel.currentWindowIndex.collectAsState()
    val sniffedUrls by viewModel.sniffedUrls.collectAsState()
    val currentUrl by viewModel.currentUrl.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val currentWindow = windows.getOrNull(currentIndex)
    val isHomePage = currentUrl == MainViewModel.HOME_URL

    var showSidebar by remember { mutableStateOf(false) }
    var showSnifferPanel by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var editingBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }

    // 侧边栏动画状态
    val sidebarOffsetX by animateFloatAsState(
        targetValue = if (showSidebar) 0f else -280f,
        animationSpec = tween(300),
        label = "sidebarOffset"
    )
    val overlayAlpha by animateFloatAsState(
        targetValue = if (showSidebar) 0.5f else 0f,
        animationSpec = tween(300),
        label = "overlayAlpha"
    )

    val context = LocalContext.current
    val bridge = remember { viewModel.getBridge() }

    Box(modifier = Modifier.fillMaxSize()) {
        // 主内容：工具栏 + 内容区
        Column(modifier = Modifier.fillMaxSize()) {
            // 工具栏（固定高度）
            BrowserToolbar(
                url = if (isHomePage) "" else currentUrl,
                windowCount = windows.size,
                canGoBack = currentWindow?.canGoBack ?: false,
                canGoForward = currentWindow?.canGoForward ?: false,
                onWindowsClick = { showSidebar = true },
                onHomeClick = { viewModel.loadHome() },
                onBackClick = { webView?.goBack() },
                onForwardClick = { webView?.goForward() },
                onRefreshClick = { webView?.reload() },
                onUrlSubmit = { url ->
                    val finalUrl = resolveUrlOrSearch(url)
                    viewModel.loadUrl(finalUrl)
                    webView?.loadUrl(finalUrl)
                },
                onNewTab = { viewModel.addNewWindow() },
                onHistory = { /* TODO */ },
                onAddBookmark = { showAddBookmarkDialog = true },
                onBookmarkList = { /* TODO */ },
                onDesktopMode = {
                    webView?.settings?.userAgentString =
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    webView?.reload()
                },
                onSettings = { /* TODO */ }
            )

            // 进度条
            if (!isHomePage && currentWindow?.isLoading == true) {
                LinearProgressIndicator(
                    progress = { currentWindow.loadProgress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            } else {
                HorizontalDivider(thickness = 0.5.dp)
            }

            // 内容区：书签首页 或 WebView
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (isHomePage) {
                    // 书签首页
                    BookmarksScreen(
                        bookmarks = bookmarks,
                        onBookmarkClick = { bookmark ->
                            viewModel.loadUrl(bookmark.url)
                            webView?.loadUrl(bookmark.url)
                        },
                        onBookmarkLongClick = { bookmark ->
                            editingBookmark = bookmark
                        },
                        onAddClick = { showAddBookmarkDialog = true }
                    )
                } else {
                    // WebView
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
                                        viewModel.updateNavigationState(
                                            view?.canGoBack() ?: false,
                                            view?.canGoForward() ?: false
                                        )
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        view?.title?.let { viewModel.updateCurrentWindowTitle(it) }
                                        url?.let { viewModel.updateBookmarkFavicon(it) }
                                        viewModel.updateNavigationState(
                                            view?.canGoBack() ?: false,
                                            view?.canGoForward() ?: false
                                        )
                                        try {
                                            val script = ctx.assets.open("sniffer.js").bufferedReader().use { it.readText() }
                                            view?.evaluateJavascript(script, null)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            val description = error?.description?.toString() ?: "页面加载失败"
                                            viewModel.updatePageError(description)
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
                                        viewModel.updatePageProgress(newProgress)
                                    }
                                }

                                addJavascriptInterface(bridge, "SnifferBridge")

                                webView = this
                            }
                        },
                        update = { wv ->
                            val url = currentUrl
                            if (url.isNotEmpty() && url != MainViewModel.HOME_URL && url != wv.url) {
                                wv.loadUrl(url)
                            }
                            viewModel.updateNavigationState(wv.canGoBack(), wv.canGoForward())
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 错误页面覆盖
                    if (currentWindow?.hasError == true) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "页面加载失败",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    currentWindow.errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                FilledTonalButton(onClick = { webView?.reload() }) {
                                    Text("重试")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 悬浮嗅探按钮（非首页时显示）
        if (!isHomePage) {
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
        }

        // 侧边栏（遮罩淡入淡出 + 面板平移）
        if (showSidebar || sidebarOffsetX > -279f) {
            WindowSidebar(
                windows = windows,
                currentIndex = currentIndex,
                sidebarOffsetX = sidebarOffsetX.dp,
                overlayAlpha = overlayAlpha,
                onWindowSelect = { index ->
                    viewModel.switchToWindow(index)
                    val selectedUrl = windows.getOrNull(index)?.url ?: ""
                    if (selectedUrl.isNotEmpty() && selectedUrl != MainViewModel.HOME_URL) {
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

    // 添加书签对话框
    if (showAddBookmarkDialog) {
        BookmarkDialog(
            title = "添加书签",
            onConfirm = { name, url ->
                viewModel.addBookmark(name, url)
                showAddBookmarkDialog = false
            },
            onDismiss = { showAddBookmarkDialog = false }
        )
    }

    // 编辑书签对话框
    editingBookmark?.let { bookmark ->
        BookmarkDialog(
            title = "编辑书签",
            initialName = bookmark.title,
            initialUrl = bookmark.url,
            onConfirm = { name, url ->
                viewModel.updateBookmark(bookmark.copy(title = name, url = url))
                editingBookmark = null
            },
            onDelete = {
                viewModel.deleteBookmark(bookmark)
                editingBookmark = null
            },
            onDismiss = { editingBookmark = null }
        )
    }
}

private fun resolveUrlOrSearch(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return ""

    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return trimmed
    }

    if (trimmed.contains(".") && !trimmed.contains(" ")) {
        return "https://$trimmed"
    }

    val ipPattern = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$")
    if (ipPattern.matches(trimmed)) {
        return "https://$trimmed"
    }

    return "https://www.baidu.com/s?wd=${URLEncoder.encode(trimmed, "UTF-8")}"
}
