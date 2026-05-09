package top.he2000.catcatchbrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.he2000.catcatchbrowser.data.SniffedM3u8

enum class SnifferMode(val displayName: String, val scriptFile: String) {
    GENERAL("通用模式", "sniffer_general.js"),
    ROU("rou模式", "sniffer_rou.js"),
    CHIGUA("吃瓜模式", "sniffer_chigua.js"),
    CAT_CATCH("猫抓模式", "sniffer_catcatch.js")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnifferBottomSheet(
    sniffedUrls: List<SniffedM3u8>,
    logs: List<String>,
    currentMode: SnifferMode,
    onModeChange: (SnifferMode) -> Unit,
    onDownload: (SniffedM3u8) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val bottomSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showModeMenu by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheet
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(min = 500.dp, max = 700.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "嗅探结果",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (sniffedUrls.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "${sniffedUrls.size} 条",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // 模式选择按钮
                Box {
                    OutlinedButton(
                        onClick = { showModeMenu = true },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(currentMode.displayName, fontSize = 12.sp)
                    }

                    DropdownMenu(
                        expanded = showModeMenu,
                        onDismissRequest = { showModeMenu = false }
                    ) {
                        SnifferMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(mode.displayName, fontSize = 14.sp)
                                        Text(
                                            getModeDescription(mode),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    onModeChange(mode)
                                    showModeMenu = false
                                },
                                modifier = if (mode == currentMode) {
                                    Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                } else Modifier
                            )
                        }
                    }
                }
            }

            // 操作按钮行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 重新嗅探按钮
                Button(
                    onClick = onRefresh,
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("重新嗅探", fontSize = 12.sp)
                }
            }

            // 嗅探结果列表（如果有结果则置顶显示）
            if (sniffedUrls.isNotEmpty()) {
                Text(
                    "捕获结果",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sniffedUrls) { item ->
                        SniffedItem(
                            item = item,
                            onDownload = { onDownload(item) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // 日志区域
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small
            ) {
                Column {
                    // 日志标题栏（可点击展开/收起）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLogs = !showLogs }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "运行日志",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "${logs.size} 条",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 复制日志按钮
                            if (logs.isNotEmpty()) {
                                val clipboardManager = LocalClipboardManager.current
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(logs.joinToString("\n")))
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "复制日志",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(2.dp))
                            }
                            Icon(
                                if (showLogs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 日志内容
                    if (showLogs && logs.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(logs) { log ->
                                Text(
                                    log,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    } else if (showLogs && logs.isEmpty()) {
                        Text(
                            "暂无日志，请播放视频后点击「重新嗅探」",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // 底部间距
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SniffedItem(
    item: SniffedM3u8,
    onDownload: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    // 从 variants 中提取最高分辨率
    val bestResolution = item.variants
        .filter { it.resolution != null }
        .maxByOrNull { it.bandwidth }
        ?.resolution

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 标签行：时长 + 分辨率
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 时长标签
                if (item.duration > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            formatDuration(item.duration.toInt()),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // 分辨率标签
                if (bestResolution != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            bestResolution,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // 多分辨率提示
                if (item.variants.size > 1) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "${item.variants.size}个分辨率",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // URL
            Text(
                text = item.url,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // 操作按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                // 复制按钮
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(item.url))
                    }
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("复制", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 下载按钮
                Button(
                    onClick = onDownload
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("下载", fontSize = 12.sp)
                }
            }
        }
    }
}

private fun getModeDescription(mode: SnifferMode): String {
    return when (mode) {
        SnifferMode.GENERAL -> "基础网络请求拦截"
        SnifferMode.ROU -> "自动播放 + 广告拦截(rou专用)"
        SnifferMode.CHIGUA -> "深度扫描 + DOM监听"
        SnifferMode.CAT_CATCH -> "深度内容嗅探(参考猫抓插件)"
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
