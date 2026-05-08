package top.he2000.catcatchbrowser.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.he2000.catcatchbrowser.data.HistoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryBottomSheet(
    entries: List<HistoryEntity>,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onClearAll: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "历史记录",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(
                    onClick = { showClearConfirm = true },
                    enabled = entries.isNotEmpty()
                ) {
                    Text("清空")
                }
            }

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "暂无记录",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(entries, key = { it.id }) { item ->
                        HistoryRow(
                            entry = item,
                            onClick = {
                                onOpenUrl(item.url)
                                onDismiss()
                            },
                            onDelete = { onDeleteEntry(item.id) }
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空历史记录") },
            text = { Text("确定删除全部浏览历史吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClearAll()
                        onDismiss()
                    }
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val timeStr = remember(entry.visitedAt) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(entry.visitedAt))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title.ifBlank { entry.url },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = timeStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.DeleteOutline,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}
