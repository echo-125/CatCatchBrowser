package top.he2000.catcatchbrowser.ui.components

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
            LinearProgressIndicator(
                progress = { task.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = when (task.status) {
                    TaskStatus.DOWNLOADING.name.lowercase() -> MaterialTheme.colorScheme.primary
                    TaskStatus.PAUSED.name.lowercase() -> MaterialTheme.colorScheme.error
                    TaskStatus.FAILED.name.lowercase() -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 状态信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 分片进度
                Text(
                    text = "${task.downloadedSegments}/${task.totalSegments}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 速度
                if (task.currentSpeed.isNotEmpty()) {
                    Text(
                        text = task.currentSpeed,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 状态
                Text(
                    text = when (task.status) {
                        TaskStatus.DOWNLOADING.name.lowercase() -> "下载中"
                        TaskStatus.PAUSED.name.lowercase() -> "已暂停"
                        TaskStatus.PENDING.name.lowercase() -> "等待中"
                        TaskStatus.FAILED.name.lowercase() -> "失败"
                        TaskStatus.CANCELLED.name.lowercase() -> "已取消"
                        else -> task.status
                    },
                    fontSize = 12.sp,
                    color = when (task.status) {
                        TaskStatus.FAILED.name.lowercase() -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
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
                }

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
