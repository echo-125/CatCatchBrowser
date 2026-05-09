package top.he2000.catcatchbrowser.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.he2000.catcatchbrowser.data.DownloadTaskEntity
import top.he2000.catcatchbrowser.data.TaskStatus
import top.he2000.catcatchbrowser.downloader.SegmentDownloader

@Composable
fun DownloadTaskItem(
    task: DownloadTaskEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 文件名
            Text(
                text = task.fileName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 进度条
            val isPending = task.status == TaskStatus.PENDING.name.lowercase()
            val progressColor = when (task.status) {
                TaskStatus.DOWNLOADING.name.lowercase() -> MaterialTheme.colorScheme.primary
                TaskStatus.CONVERTING.name.lowercase() -> MaterialTheme.colorScheme.tertiary
                TaskStatus.PAUSED.name.lowercase() -> MaterialTheme.colorScheme.outline
                TaskStatus.FAILED.name.lowercase() -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }
            if (isPending) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                LinearProgressIndicator(
                    progress = { task.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = progressColor
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 状态信息行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：分片进度 + 已下载大小
                Column {
                    Text(
                        text = "${task.downloadedSegments}/${task.totalSegments} 分片",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (task.totalBytesDownloaded > 0) {
                        Text(
                            text = SegmentDownloader.formatSize(task.totalBytesDownloaded),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 中间：速度 + ETA
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (task.currentSpeed.isNotEmpty() && task.status == TaskStatus.DOWNLOADING.name.lowercase()) {
                        Text(
                            text = task.currentSpeed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 右侧：状态文本
                val statusText = when (task.status) {
                    TaskStatus.DOWNLOADING.name.lowercase() -> {
                        if (task.progress > 0) "%.0f%%".format(task.progress) else "下载中"
                    }
                    TaskStatus.CONVERTING.name.lowercase() -> "转换中"
                    TaskStatus.PAUSED.name.lowercase() -> "已暂停"
                    TaskStatus.PENDING.name.lowercase() -> "准备中..."
                    TaskStatus.FAILED.name.lowercase() -> "失败"
                    TaskStatus.CANCELLED.name.lowercase() -> "已取消"
                    TaskStatus.COMPLETED.name.lowercase() -> "完成"
                    else -> task.status
                }
                val statusColor = when (task.status) {
                    TaskStatus.FAILED.name.lowercase() -> MaterialTheme.colorScheme.error
                    TaskStatus.COMPLETED.name.lowercase() -> MaterialTheme.colorScheme.primary
                    TaskStatus.CONVERTING.name.lowercase() -> MaterialTheme.colorScheme.tertiary
                    TaskStatus.PENDING.name.lowercase() -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = statusColor
                )
            }

            // 错误信息
            if (task.status == TaskStatus.FAILED.name.lowercase() && task.errorMessage.isNotEmpty()) {
                Text(
                    text = task.errorMessage,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // 重试次数
            if (task.retryCount > 0) {
                Text(
                    text = "已重试 ${task.retryCount} 次",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // 操作按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                when (task.status) {
                    TaskStatus.DOWNLOADING.name.lowercase() -> {
                        IconButton(onClick = onPause) {
                            Icon(
                                Icons.Default.Pause,
                                contentDescription = "暂停",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    TaskStatus.PAUSED.name.lowercase(), TaskStatus.PENDING.name.lowercase() -> {
                        IconButton(onClick = onResume) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "继续",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    TaskStatus.FAILED.name.lowercase(), TaskStatus.CANCELLED.name.lowercase() -> {
                        IconButton(onClick = onRetry) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "重试",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    TaskStatus.CONVERTING.name.lowercase() -> {
                        // 转换中不显示操作按钮
                    }
                    TaskStatus.COMPLETED.name.lowercase() -> {
                        // 已完成不显示操作按钮
                    }
                }

                // 删除按钮（转换中和完成状态不显示）
                if (task.status != TaskStatus.CONVERTING.name.lowercase() &&
                    task.status != TaskStatus.COMPLETED.name.lowercase()
                ) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
