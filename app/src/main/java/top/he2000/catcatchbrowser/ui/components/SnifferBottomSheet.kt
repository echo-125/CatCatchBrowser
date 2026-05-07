package top.he2000.catcatchbrowser.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.he2000.catcatchbrowser.data.SniffedM3u8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnifferBottomSheet(
    sniffedUrls: List<SniffedM3u8>,
    onDownload: (SniffedM3u8) -> Unit,
    onDismiss: () -> Unit
) {
    val bottomSheet = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheet
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // 标题
            Text(
                "嗅探结果",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (sniffedUrls.isEmpty()) {
                // 空状态
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "暂无嗅探结果",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 结果列表
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(sniffedUrls) { item ->
                        SniffedItem(
                            item = item,
                            onDownload = { onDownload(item) }
                        )
                    }
                }
            }

            // 底部间距
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SniffedItem(
    item: SniffedM3u8,
    onDownload: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 类型标签
            if (item.isTarget) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        "目标",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // URL
            Text(
                text = item.url,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // 时长
            if (item.duration > 0) {
                Text(
                    text = "时长: ${formatDuration(item.duration.toInt())}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 操作按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                // 复制按钮
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(item.url))
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "复制",
                        modifier = Modifier.size(16.dp)
                    )
                }

                // 下载按钮
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "下载",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%d:%02d", minutes, secs)
    }
}
