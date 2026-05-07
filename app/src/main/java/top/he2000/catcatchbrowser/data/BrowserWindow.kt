package top.he2000.catcatchbrowser.data

import java.util.UUID

data class BrowserWindow(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "空白页",
    val url: String = "",
    val isActive: Boolean = false
)
