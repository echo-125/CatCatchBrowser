package top.he2000.catcatchbrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.he2000.catcatchbrowser.data.BrowserWindow

@Composable
fun WindowSidebar(
    windows: List<BrowserWindow>,
    currentIndex: Int,
    sidebarOffsetX: Dp,
    overlayAlpha: Float,
    onWindowSelect: (Int) -> Unit,
    onWindowClose: (Int) -> Unit,
    onNewWindow: () -> Unit,
    onCloseAllWindows: () -> Unit,
    onDismiss: () -> Unit
) {
    val swipeEnabled = windows.size > 1

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = overlayAlpha))
                .clickable { onDismiss() }
        )

        Column(
            modifier = Modifier
                .offset(x = sidebarOffsetX)
                .width(280.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "窗口管理",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCloseAllWindows) {
                        Icon(
                            Icons.Outlined.DeleteSweep,
                            contentDescription = "关闭全部窗口",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = onNewWindow) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "新建窗口",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            HorizontalDivider()

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(windows, key = { _, w -> w.id }) { index, window ->
                    WindowListItemSwipeable(
                        window = window,
                        isActive = index == currentIndex,
                        swipeEnabled = swipeEnabled,
                        onClick = { onWindowSelect(index) },
                        onClose = { onWindowClose(index) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WindowListItemSwipeable(
    window: BrowserWindow,
    isActive: Boolean,
    swipeEnabled: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    if (swipeEnabled) {
                        showConfirmDialog = true
                        false // 不立即关闭，先显示确认对话框
                    } else {
                        false
                    }
                }
                else -> false
            }
        },
        positionalThreshold = { it * 0.6f } // 需要滑动超过 60% 才触发
    )

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("确认关闭") },
            text = { Text("确定要关闭此标签页吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onClose()
                    }
                ) {
                    Text("关闭", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.fillMaxWidth(),
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = swipeEnabled,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 20.dp)
                )
            }
        },
        content = {
            WindowListItemContent(
                window = window,
                isActive = isActive,
                onClick = onClick,
                onClose = onClose
            )
        }
    )
}

@Composable
private fun WindowListItemContent(
    window: BrowserWindow,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isActive) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(40.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.shapes.small
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = window.title,
                    fontSize = 14.sp,
                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = window.url.ifEmpty { "空白页" },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
