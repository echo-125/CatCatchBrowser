package top.he2000.catcatchbrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.he2000.catcatchbrowser.data.BrowserWindow

@Composable
fun WindowSidebar(
    windows: List<BrowserWindow>,
    currentIndex: Int,
    onWindowSelect: (Int) -> Unit,
    onWindowClose: (Int) -> Unit,
    onNewWindow: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 遮罩
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() }
        )

        // 侧边栏
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .align(Alignment.CenterStart)
        ) {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "窗口管理",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onNewWindow) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "新建窗口",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider()

            // 窗口列表
            LazyColumn {
                itemsIndexed(windows) { index, window ->
                    WindowListItem(
                        window = window,
                        isActive = index == currentIndex,
                        onClick = { onWindowSelect(index) },
                        onClose = { onWindowClose(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WindowListItem(
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
            // 选中指示器
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

            // 内容
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

            // 关闭按钮
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
