package top.he2000.catcatchbrowser.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrowserToolbar(
    url: String,
    windowCount: Int,
    canGoBack: Boolean = false,
    canGoForward: Boolean = false,
    isDesktopSite: Boolean,
    onWindowsClick: () -> Unit,
    onHomeClick: () -> Unit,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onUrlSubmit: (String) -> Unit,
    onNewTab: () -> Unit,
    onHistory: () -> Unit,
    onAddBookmark: () -> Unit,
    onBookmarkList: () -> Unit,
    onDesktopSiteChange: (Boolean) -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onSettings: () -> Unit,
    canShareCurrentPage: Boolean
) {
    val focusManager = LocalFocusManager.current
    var textValue by remember(url) { mutableStateOf(url) }
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 窗口管理按钮：空心方形内显示数字
        Surface(
            onClick = onWindowsClick,
            shape = RoundedCornerShape(3.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.size(28.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$windowCount",
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }

        IconButton(onClick = onHomeClick) {
            Icon(
                Icons.Default.Home,
                contentDescription = "首页",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            onUrlSubmit(textValue)
                            focusManager.clearFocus()
                        }
                    ),
                    decorationBox = { innerTextField ->
                        if (textValue.isEmpty()) {
                            Text(
                                "输入网址或搜索",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                )

                IconButton(
                    onClick = onRefreshClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "刷新",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        IconButton(
            onClick = onBackClick,
            enabled = canGoBack
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = if (canGoBack) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        }

        IconButton(
            onClick = onForwardClick,
            enabled = canGoForward
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "前进",
                tint = if (canGoForward) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "菜单",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("新建标签") },
                    onClick = {
                        showMenu = false
                        onNewTab()
                    }
                )
                DropdownMenuItem(
                    text = { Text("历史记录") },
                    onClick = {
                        showMenu = false
                        onHistory()
                    }
                )
                DropdownMenuItem(
                    text = { Text("添加书签") },
                    onClick = {
                        showMenu = false
                        onAddBookmark()
                    }
                )
                DropdownMenuItem(
                    text = { Text("书签列表") },
                    onClick = {
                        showMenu = false
                        onBookmarkList()
                    }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("桌面版网站") },
                    onClick = {
                        showMenu = false
                        onDesktopSiteChange(!isDesktopSite)
                    },
                    trailingIcon = {
                        Switch(
                            checked = isDesktopSite,
                            onCheckedChange = null,
                            modifier = Modifier.height(24.dp)
                        )
                    }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("分享") },
                    onClick = {
                        showMenu = false
                        onShare()
                    },
                    enabled = canShareCurrentPage
                )
                DropdownMenuItem(
                    text = { Text("复制链接") },
                    onClick = {
                        showMenu = false
                        onCopyLink()
                    },
                    enabled = canShareCurrentPage
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("设置") },
                    onClick = {
                        showMenu = false
                        onSettings()
                    }
                )
            }
        }
    }
}
