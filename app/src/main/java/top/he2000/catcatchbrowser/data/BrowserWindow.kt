package top.he2000.catcatchbrowser.data

import java.util.UUID

data class BrowserWindow(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "空白页",
    val url: String = "",
    val isActive: Boolean = false,
    val isLoading: Boolean = false,
    val loadProgress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val hasError: Boolean = false,
    val errorMessage: String = "",
    val sniffedUrls: List<SniffedM3u8> = emptyList()
)
