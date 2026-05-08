package top.he2000.catcatchbrowser.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Window
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Windows : Screen(
        route = "windows",
        title = "窗口",
        icon = Icons.Default.Window
    )

    data object Downloading : Screen(
        route = "downloading",
        title = "下载中",
        icon = Icons.Default.Download
    )

    data object Downloaded : Screen(
        route = "downloaded",
        title = "已下载",
        icon = Icons.Default.Folder
    )

    data object Settings : Screen(
        route = "settings",
        title = "设置",
        icon = Icons.Default.Settings
    )

    companion object {
        val bottomNavScreens = listOf(Windows, Downloading, Downloaded)
    }
}
