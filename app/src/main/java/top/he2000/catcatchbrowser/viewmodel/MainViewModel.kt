package top.he2000.catcatchbrowser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import top.he2000.catcatchbrowser.data.*
import top.he2000.catcatchbrowser.downloader.TaskManager
import top.he2000.catcatchbrowser.sniffer.M3u8Sniffer
import top.he2000.catcatchbrowser.sniffer.SnifferBridge

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val HOME_URL = "about:bookmarks"
    }

    private val taskManager = TaskManager(application)
    private val bridge = SnifferBridge()
    private val bookmarkDao = AppDatabase.getInstance(application).bookmarkDao()

    // 窗口状态
    private val _windows = MutableStateFlow<List<BrowserWindow>>(emptyList())
    val windows: StateFlow<List<BrowserWindow>> = _windows.asStateFlow()

    private val _currentWindowIndex = MutableStateFlow(0)
    val currentWindowIndex: StateFlow<Int> = _currentWindowIndex.asStateFlow()

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

    // 嗅探结果
    val sniffedUrls: StateFlow<List<SniffedM3u8>> = bridge.sniffedUrls

    // 书签
    val bookmarks: StateFlow<List<BookmarkEntity>> = bookmarkDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 当前URL
    private val _currentUrl = MutableStateFlow("")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    init {
        // 添加默认窗口
        addNewWindow()
    }

    fun addNewWindow(url: String = HOME_URL) {
        val newWindow = BrowserWindow(
            title = "空白页",
            url = url,
            isActive = true
        )
        val updatedWindows = _windows.value.map { it.copy(isActive = false) } + newWindow
        _windows.value = updatedWindows
        _currentWindowIndex.value = updatedWindows.size - 1
        _currentUrl.value = url
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

    fun updateCurrentWindowUrl(url: String) {
        val index = _currentWindowIndex.value
        if (index in _windows.value.indices) {
            _windows.value = _windows.value.toMutableList().apply {
                set(index, get(index).copy(
                    url = url,
                    hasError = false,
                    isLoading = true,
                    loadProgress = 0
                ))
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
                set(index, get(index).copy(
                    isLoading = progress < 100,
                    loadProgress = progress,
                    hasError = false
                ))
            }
        }
    }

    fun updateNavigationState(canGoBack: Boolean, canGoForward: Boolean) {
        val index = _currentWindowIndex.value
        if (index in _windows.value.indices) {
            _windows.value = _windows.value.toMutableList().apply {
                set(index, get(index).copy(
                    canGoBack = canGoBack,
                    canGoForward = canGoForward
                ))
            }
        }
    }

    fun updatePageError(description: String) {
        val index = _currentWindowIndex.value
        if (index in _windows.value.indices) {
            _windows.value = _windows.value.toMutableList().apply {
                set(index, get(index).copy(
                    isLoading = false,
                    hasError = true,
                    errorMessage = description
                ))
            }
        }
    }

    fun loadUrl(url: String) {
        var finalUrl = url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            finalUrl = "https://$url"
        }
        updateCurrentWindowUrl(finalUrl)
    }

    fun loadHome() {
        updateCurrentWindowUrl(HOME_URL)
    }

    // 书签操作
    fun addBookmark(title: String, url: String) {
        viewModelScope.launch {
            bookmarkDao.insert(
                BookmarkEntity(
                    title = title,
                    url = url,
                    iconUrl = "",
                    sortOrder = bookmarks.value.size
                )
            )
        }
    }

    fun updateBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            bookmarkDao.update(bookmark)
        }
    }

    fun updateBookmarkFavicon(url: String) {
        viewModelScope.launch {
            val host = try { java.net.URI(url).host } catch (_: Exception) { return@launch }
            if (host.isNullOrEmpty()) return@launch
            val matching = bookmarks.value.find { bm ->
                try { java.net.URI(bm.url).host == host } catch (_: Exception) { false }
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

    // 下载任务操作
    fun createDownloadTask(sniffed: SniffedM3u8) {
        viewModelScope.launch {
            val fileName = sniffed.title ?: "video_${System.currentTimeMillis()}"
            val savePath = getApplication<android.app.Application>()
                .getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)?.absolutePath
                ?: getApplication<android.app.Application>().filesDir.absolutePath

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
