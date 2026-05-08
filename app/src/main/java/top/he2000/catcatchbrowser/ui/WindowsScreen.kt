package top.he2000.catcatchbrowser.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import top.he2000.catcatchbrowser.data.BookmarkEntity
import top.he2000.catcatchbrowser.ui.components.BrowserToolbar
import top.he2000.catcatchbrowser.ui.components.HistoryBottomSheet
import top.he2000.catcatchbrowser.ui.components.SnifferBottomSheet
import top.he2000.catcatchbrowser.ui.components.WindowSidebar
import top.he2000.catcatchbrowser.ui.navigation.Screen
import top.he2000.catcatchbrowser.viewmodel.MainViewModel
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WindowsScreen(
    viewModel: MainViewModel,
    navController: NavController
) {
    val windows by viewModel.windows.collectAsState()
    val currentIndex by viewModel.currentWindowIndex.collectAsState()
    val sniffedUrls by viewModel.sniffedUrls.collectAsState()
    val currentUrl by viewModel.currentUrl.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val historyEntries by viewModel.historyEntries.collectAsState()
    val desktopSite by viewModel.desktopSite.collectAsState()
    val searchTemplate by viewModel.searchTemplate.collectAsState()

    val currentWindow = windows.getOrNull(currentIndex)
    val isHomePage = currentUrl == MainViewModel.HOME_URL

    var showSidebar by remember { mutableStateOf(false) }
    var showSnifferPanel by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var editingBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }
    var bookmarkDialogTitle by remember { mutableStateOf("") }
    var bookmarkDialogUrl by remember { mutableStateOf("") }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var prevDesktop by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(desktopSite) {
        if (prevDesktop != null && prevDesktop != desktopSite) {
            webView?.let { wv ->
                val mobile = viewModel.getCachedDefaultUserAgent() ?: return@let
                wv.settings.userAgentString =
                    if (desktopSite) MainViewModel.DESKTOP_USER_AGENT else mobile
                val u = wv.url
                if (!u.isNullOrBlank() &&
                    u != MainViewModel.HOME_URL &&
                    !u.startsWith("about:")
                ) {
                    wv.reload()
                }
            }
        }
        prevDesktop = desktopSite
    }

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

    val bridge = remember { viewModel.getBridge() }

    // WebView 生命周期管理：离开页面时销毁
    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                removeJavascriptInterface("SnifferBridge")
                destroy()
            }
            webView = null
        }
    }

    val canShareCurrentPage =
        !isHomePage &&
            (currentUrl.startsWith("http://") || currentUrl.startsWith("https://"))

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                BrowserToolbar(
                    url = if (isHomePage) "" else currentUrl,
                    windowCount = windows.size,
                    canGoBack = currentWindow?.canGoBack ?: false,
                    canGoForward = currentWindow?.canGoForward ?: false,
                    isDesktopSite = desktopSite,
                    onWindowsClick = { showSidebar = true },
                    onHomeClick = { viewModel.loadHome() },
                    onBackClick = { webView?.goBack() },
                    onForwardClick = { webView?.goForward() },
                    onRefreshClick = { webView?.reload() },
                    onUrlSubmit = { raw ->
                        val finalUrl = resolveUrlOrSearch(raw, searchTemplate)
                        if (finalUrl.isNotEmpty()) {
                            viewModel.loadUrl(finalUrl)
                            // URL 加载由 update lambda 处理，避免重复
                        }
                    },
                    onNewTab = {
                        viewModel.addNewWindow()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                "新标签已创建",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    onHistory = { showHistorySheet = true },
                    onAddBookmark = {
                        if (!canBookmarkCurrentPage(currentUrl)) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("请在网页中打开后再添加")
                            }
                        } else {
                            bookmarkDialogTitle = currentWindow?.title ?: ""
                            bookmarkDialogUrl = currentUrl
                            showAddBookmarkDialog = true
                        }
                    },
                    onBookmarkList = { viewModel.openBookmarksHome() },
                    onDesktopSiteChange = { enabled -> viewModel.setDesktopSite(enabled) },
                    onShare = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, currentUrl)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享"))
                    },
                    onCopyLink = {
                        val cm =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("url", currentUrl))
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("链接已复制")
                        }
                    },
                    onClearHistory = { showClearHistoryConfirm = true },
                    onOpenInBrowser = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(currentUrl))
                        )
                    },
                    onSettings = { navController.navigate(Screen.Settings.route) },
                    canShareCurrentPage = canShareCurrentPage
                )

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
                                    mixedContentMode =
                                        WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                }

                                viewModel.cacheDefaultUserAgentIfNeeded(settings.userAgentString)

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: android.graphics.Bitmap?
                                    ) {
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
                                        url?.let {
                                            viewModel.updateBookmarkFavicon(it)
                                            viewModel.recordHistoryVisit(view?.title ?: "", it)
                                        }
                                        viewModel.updateNavigationState(
                                            view?.canGoBack() ?: false,
                                            view?.canGoForward() ?: false
                                        )
                                        try {
                                            val script = ctx.assets.open("sniffer.js")
                                                .bufferedReader().use { it.readText() }
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
                                            val description =
                                                error?.description?.toString() ?: "页面加载失败"
                                            viewModel.updatePageError(description)
                                        }
                                    }

                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): WebResourceResponse? {
                                        request?.url?.toString()?.let { url ->
                                            if (url.contains(".m3u8")) {
                                                // 嗅探由脚本与原生扩展处理
                                            }
                                        }
                                        return super.shouldInterceptRequest(view, request)
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(
                                        view: WebView?,
                                        newProgress: Int
                                    ) {
                                        viewModel.updatePageProgress(newProgress)
                                    }
                                }

                                addJavascriptInterface(bridge, "SnifferBridge")

                                webView = this
                            }
                        },
                        update = { wv ->
                            viewModel.getCachedDefaultUserAgent()?.let { mobile ->
                                val want =
                                    if (desktopSite) MainViewModel.DESKTOP_USER_AGENT else mobile
                                if (wv.settings.userAgentString != want) {
                                    wv.settings.userAgentString = want
                                }
                            }
                            val url = currentUrl
                            if (url.isNotEmpty() &&
                                url != MainViewModel.HOME_URL &&
                                url != wv.url
                            ) {
                                wv.loadUrl(url)
                            }
                            viewModel.updateNavigationState(
                                wv.canGoBack(),
                                wv.canGoForward()
                            )
                        },
                        modifier = Modifier.fillMaxSize().alpha(if (isHomePage) 0f else 1f)
                    )

                    if (!isHomePage && currentWindow?.hasError == true) {
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

                    if (isHomePage) {
                        BookmarksScreen(
                            bookmarks = bookmarks,
                            onBookmarkClick = { bookmark ->
                                viewModel.loadUrl(bookmark.url)
                                // URL 加载由 update lambda 处理
                            },
                            onBookmarkLongClick = { bookmark ->
                                editingBookmark = bookmark
                            },
                            onAddClick = {
                                bookmarkDialogTitle = ""
                                bookmarkDialogUrl = ""
                                showAddBookmarkDialog = true
                            }
                        )
                    }
                }
            }

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

            if (showSidebar || sidebarOffsetX > -279f) {
                WindowSidebar(
                    windows = windows,
                    currentIndex = currentIndex,
                    sidebarOffsetX = sidebarOffsetX.dp,
                    overlayAlpha = overlayAlpha,
                    onWindowSelect = { index ->
                        viewModel.switchToWindow(index)
                        // URL 加载由 update lambda 处理
                        showSidebar = false
                    },
                    onWindowClose = { index ->
                        viewModel.closeWindow(index)
                    },
                    onNewWindow = {
                        viewModel.addNewWindow()
                        showSidebar = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                "新标签已创建",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    onDismiss = { showSidebar = false }
                )
            }

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

            if (showHistorySheet) {
                HistoryBottomSheet(
                    entries = historyEntries,
                    onDismiss = { showHistorySheet = false },
                    onOpenUrl = { url ->
                        viewModel.loadUrl(url)
                        // URL 加载由 update lambda 处理
                    },
                    onDeleteEntry = { id -> viewModel.deleteHistoryEntry(id) },
                    onClearAll = { viewModel.clearAllHistory() }
                )
            }

            if (showAddBookmarkDialog) {
                BookmarkDialog(
                    title = "添加书签",
                    initialName = bookmarkDialogTitle,
                    initialUrl = bookmarkDialogUrl,
                    onConfirm = { name, url ->
                        coroutineScope.launch {
                            val added = viewModel.addBookmark(name, url)
                            showAddBookmarkDialog = false
                            if (!added) {
                                snackbarHostState.showSnackbar("已在书签中")
                            }
                        }
                    },
                    onDismiss = { showAddBookmarkDialog = false }
                )
            }

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

            if (showClearHistoryConfirm) {
                AlertDialog(
                    onDismissRequest = { showClearHistoryConfirm = false },
                    title = { Text("清除历史记录") },
                    text = { Text("确定删除全部浏览历史吗？") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showClearHistoryConfirm = false
                                viewModel.clearAllHistory()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("已清空历史记录")
                                }
                            }
                        ) {
                            Text("清除", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearHistoryConfirm = false }) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    }
}

private fun canBookmarkCurrentPage(url: String): Boolean =
    url.startsWith("http://") || url.startsWith("https://")

private fun resolveUrlOrSearch(input: String, searchTemplate: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return ""

    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return trimmed
    }

    if (trimmed.startsWith("about:")) {
        return trimmed
    }

    if (trimmed.contains(".") && !trimmed.contains(" ")) {
        return "https://$trimmed"
    }

    val ipPattern = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$")
    if (ipPattern.matches(trimmed)) {
        return "https://$trimmed"
    }

    val encoded = URLEncoder.encode(trimmed, "UTF-8")
    return if (searchTemplate.contains("%s")) {
        searchTemplate.replace("%s", encoded)
    } else {
        "$searchTemplate$encoded"
    }
}
