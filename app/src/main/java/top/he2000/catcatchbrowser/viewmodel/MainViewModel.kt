package top.he2000.catcatchbrowser.viewmodel

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.he2000.catcatchbrowser.data.*
import top.he2000.catcatchbrowser.downloader.TaskManager
import top.he2000.catcatchbrowser.sniffer.SnifferBridge

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val HOME_URL = "about:bookmarks"

        const val MAX_WINDOWS = 20

        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    private val taskManager = TaskManager(application)
    private val bridge = SnifferBridge()
    private val bookmarkDao = AppDatabase.getInstance(application).bookmarkDao()
    private val historyDao = AppDatabase.getInstance(application).historyDao()
    private val userPrefs = UserPreferencesRepository.getInstance(application)

    // 窗口状态
    private val _windows = MutableStateFlow<List<BrowserWindow>>(emptyList())
    val windows: StateFlow<List<BrowserWindow>> = _windows.asStateFlow()

    private val _currentWindowIndex = MutableStateFlow(0)
    val currentWindowIndex: StateFlow<Int> = _currentWindowIndex.asStateFlow()

    @Volatile
    private var cachedDefaultUserAgent: String? = null

    // 下载状态
    val allTasks: StateFlow<List<DownloadTaskEntity>> = taskManager.observeAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadingTasks: StateFlow<List<DownloadTaskEntity>> = allTasks.map { tasks ->
        tasks.filter {
            it.status == TaskStatus.DOWNLOADING.name.lowercase() ||
                    it.status == TaskStatus.PENDING.name.lowercase()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasks: StateFlow<List<DownloadTaskEntity>> = allTasks.map { tasks ->
        tasks.filter { it.status == TaskStatus.COMPLETED.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sniffedUrls: StateFlow<List<SniffedM3u8>> = bridge.sniffedUrls

    val bookmarks: StateFlow<List<BookmarkEntity>> = bookmarkDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyEntries: StateFlow<List<HistoryEntity>> = historyDao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val desktopSite: StateFlow<Boolean> = userPrefs.desktopSite
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val homepageUrlSetting: StateFlow<String> = userPrefs.homepageUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val searchTemplate: StateFlow<String> = userPrefs.searchTemplate
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            UserPreferencesRepository.DEFAULT_SEARCH_TEMPLATE
        )

    private val _currentUrl = MutableStateFlow("")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _uiMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val uiMessages: SharedFlow<String> = _uiMessages.asSharedFlow()

    val themeMode: StateFlow<Int> = userPrefs.themeMode
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            UserPreferencesRepository.THEME_FOLLOW_SYSTEM
        )

    init {
        addNewWindow()
    }

    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            userPrefs.setThemeMode(mode)
        }
    }

    fun cacheDefaultUserAgentIfNeeded(ua: String) {
        if (cachedDefaultUserAgent == null) {
            cachedDefaultUserAgent = ua
        }
    }

    fun getCachedDefaultUserAgent(): String? = cachedDefaultUserAgent

    fun addNewWindow(url: String = HOME_URL): Boolean {
        if (_windows.value.size >= MAX_WINDOWS) {
            viewModelScope.launch {
                _uiMessages.emit("最多开启 $MAX_WINDOWS 个标签")
            }
            return false
        }
        val newWindow = BrowserWindow(
            title = "空白页",
            url = url,
            isActive = true
        )
        val updatedWindows = _windows.value.map { it.copy(isActive = false) } + newWindow
        _windows.value = updatedWindows
        _currentWindowIndex.value = updatedWindows.size - 1
        _currentUrl.value = url
        return true
    }

    fun switchToWindow(index: Int) {
        if (index in _windows.value.indices) {
            _windows.value = _windows.value.mapIndexed { i, window ->
                window.copy(isActive = i == index)
            }
            _currentWindowIndex.value = index
            _currentUrl.value = _windows.value[index].url
        }
    }

    fun closeWindow(index: Int) {
        if (_windows.value.size <= 1) return

        val updatedWindows = _windows.value.toMutableList()
        updatedWindows.removeAt(index)

        val newIndex = if (_currentWindowIndex.value >= updatedWindows.size) {
            updatedWindows.size - 1
        } else {
            _currentWindowIndex.value
        }

        _windows.value = updatedWindows.mapIndexed { i, window ->
            window.copy(isActive = i == newIndex)
        }
        _currentWindowIndex.value = newIndex
        _currentUrl.value = _windows.value[newIndex].url
    }

    /** 关闭全部标签，保留一个新标签（与「主页 / 新建标签首页」逻辑一致） */
    fun closeAllWindows() {
        val startUrl = homepageUrlSetting.value.ifBlank { HOME_URL }
        _windows.value = listOf(
            BrowserWindow(
                title = "空白页",
                url = startUrl,
                isActive = true
            )
        )
        _currentWindowIndex.value = 0
        _currentUrl.value = startUrl
    }

    fun updateCurrentWindowUrl(url: String) {
        val index = _currentWindowIndex.value
        if (index in _windows.value.indices) {
            _windows.value = _windows.value.toMutableList().apply {
                set(
                    index,
                    get(index).copy(
                        url = url,
                        hasError = false,
                        isLoading = true,
                        loadProgress = 0
                    )
                )
            }
            _currentUrl.value = url
        }
    }

    fun updateCurrentWindowTitle(title: String) {
        val index = _currentWindowIndex.value
        if (index in _windows.value.indices) {
            _windows.value = _windows.value.toMutableList().apply {
                set(index, get(index).copy(title = title))
            }
        }
    }

    fun updatePageProgress(progress: Int) {
        val index = _currentWindowIndex.value
        if (index in _windows.value.indices) {
            _windows.value = _windows.value.toMutableList().apply {
                set(
                    index,
                    get(index).copy(
                        isLoading = progress < 100,
                        loadProgress = progress,
                        hasError = false
                    )
                )
            }
        }
    }

    fun updateNavigationState(canGoBack: Boolean, canGoForward: Boolean) {
        val index = _currentWindowIndex.value
        if (index in _windows.value.indices) {
            _windows.value = _windows.value.toMutableList().apply {
                set(
                    index,
                    get(index).copy(
                        canGoBack = canGoBack,
                        canGoForward = canGoForward
                    )
                )
            }
        }
    }

    fun updatePageError(description: String) {
        val index = _currentWindowIndex.value
        if (index in _windows.value.indices) {
            _windows.value = _windows.value.toMutableList().apply {
                set(
                    index,
                    get(index).copy(
                        isLoading = false,
                        hasError = true,
                        errorMessage = description
                    )
                )
            }
        }
    }

    fun loadUrl(url: String) {
        updateCurrentWindowUrl(normalizeNavigationUrl(url))
    }

    /** 在新标签中打开 URL（书签、历史等） */
    fun openUrlInNewTab(url: String): Boolean {
        return addNewWindow(normalizeNavigationUrl(url))
    }

    /** 新建标签并打开首页（自定义首页或内置书签页） */
    fun openNewTabHome(): Boolean {
        val hp = homepageUrlSetting.value.ifBlank { HOME_URL }
        return addNewWindow(hp)
    }

    private fun normalizeNavigationUrl(url: String): String {
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("about:") -> url
            else -> "https://$url"
        }
    }

    fun setDesktopSite(enabled: Boolean) {
        viewModelScope.launch {
            userPrefs.setDesktopSite(enabled)
        }
    }

    fun setHomepageUrlSetting(url: String) {
        viewModelScope.launch {
            userPrefs.setHomepageUrl(url.trim())
        }
    }

    fun setSearchTemplateSetting(template: String) {
        viewModelScope.launch {
            userPrefs.setSearchTemplate(template.trim())
        }
    }

    fun recordHistoryVisit(title: String, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                historyDao.recordVisit(title.ifBlank { url }, url)
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.clearAll()
        }
    }

    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.deleteById(id)
        }
    }

    fun clearCookiesAndSiteStorage() {
        viewModelScope.launch(Dispatchers.Main) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }
    }

    /** 清除 WebView 本地缓存（模板目录与内存缓存），不影响 Cookie */
    fun clearWebViewDiskCache() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                WebView(app.applicationContext).clearCache(true)
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    suspend fun addBookmark(title: String, url: String): Boolean {
        return withContext(Dispatchers.IO) {
            if (bookmarkDao.getByUrl(url) != null) return@withContext false
            val order = bookmarkDao.getAll().size
            bookmarkDao.insert(
                BookmarkEntity(
                    title = title,
                    url = url,
                    iconUrl = "",
                    sortOrder = order
                )
            )
            true
        }
    }

    fun updateBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            bookmarkDao.update(bookmark)
        }
    }

    fun updateBookmarkFavicon(url: String) {
        viewModelScope.launch {
            val host = try {
                java.net.URI(url).host
            } catch (_: Exception) {
                return@launch
            }
            if (host.isNullOrEmpty()) return@launch
            val matching = bookmarks.value.find { bm ->
                try {
                    java.net.URI(bm.url).host == host
                } catch (_: Exception) {
                    false
                }
            }
            if (matching != null && matching.iconUrl.isEmpty()) {
                val faviconUrl = "https://$host/favicon.ico"
                bookmarkDao.update(matching.copy(iconUrl = faviconUrl))
            }
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            bookmarkDao.delete(bookmark)
        }
    }

    fun createDownloadTask(sniffed: SniffedM3u8) {
        viewModelScope.launch {
            val fileName = sniffed.title ?: "video_${System.currentTimeMillis()}"
            val savePath = getApplication<Application>()
                .getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)?.absolutePath
                ?: getApplication<Application>().filesDir.absolutePath

            taskManager.createTask(
                url = sniffed.url,
                fileName = fileName,
                savePath = savePath,
                requestHeaders = sniffed.requestHeaders
            )
        }
    }

    fun startTask(taskId: Long) {
        taskManager.startTask(taskId)
    }

    fun pauseTask(taskId: Long) {
        taskManager.pauseTask(taskId)
    }

    fun resumeTask(taskId: Long) {
        viewModelScope.launch {
            taskManager.retryTask(taskId)
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            taskManager.deleteTask(taskId)
        }
    }

    fun pauseAll() {
        taskManager.pauseAll()
    }

    fun clearCompleted() {
        viewModelScope.launch {
            taskManager.clearCompletedTasks()
        }
    }

    fun getBridge(): SnifferBridge = bridge

    override fun onCleared() {
        super.onCleared()
        taskManager.cleanup()
    }
}
