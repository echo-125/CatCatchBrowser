package top.he2000.catcatchbrowser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.he2000.catcatchbrowser.data.DownloadTaskEntity
import top.he2000.catcatchbrowser.data.TaskStatus
import top.he2000.catcatchbrowser.ui.components.DownloadTaskItem
import top.he2000.catcatchbrowser.viewmodel.MainViewModel

@Composable
fun DownloadingScreen(viewModel: MainViewModel) {
    val tasks by viewModel.downloadingTasks.collectAsState()
    val failedTasks by viewModel.failedTasks.collectAsState()
    val completedTasks by viewModel.completedTasks.collectAsState()

    val downloadingCount = tasks.count { it.status == TaskStatus.DOWNLOADING.name.lowercase() }
    val convertingCount = tasks.count { it.status == TaskStatus.CONVERTING.name.lowercase() }
    val pendingCount = tasks.count { it.status == TaskStatus.PENDING.name.lowercase() }
    val pausedCount = tasks.count { it.status == TaskStatus.PAUSED.name.lowercase() }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部标题栏
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "下载中",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (tasks.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "${tasks.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))

                // 批量操作按钮
                // 开始全部等待中
                if (pendingCount > 0) {
                    IconButton(onClick = { viewModel.startAllPending() }) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "开始全部($pendingCount)",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                // 恢复全部暂停
                if (pausedCount > 0) {
                    IconButton(onClick = { viewModel.resumeAllPaused() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "恢复全部($pausedCount)",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                // 暂停全部下载中
                if (downloadingCount > 0) {
                    IconButton(onClick = { viewModel.pauseAll() }) {
                        Icon(
                            Icons.Default.Pause,
                            contentDescription = "暂停全部($downloadingCount)"
                        )
                    }
                }
                // 清除已完成
                if (completedTasks.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearCompleted() }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "清除已完成(${completedTasks.size})",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 清除失败
                if (failedTasks.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearFailed() }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "清除失败(${failedTasks.size})",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp)

        if (tasks.isEmpty() && failedTasks.isEmpty() && completedTasks.isEmpty()) {
            // 空状态
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "暂无下载任务",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "在浏览器中嗅探到M3U8链接后可添加下载",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // 任务列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // 活跃任务（下载中 > 转换中 > 等待中 > 已暂停）
                val sortedTasks = tasks.sortedByDescending { task ->
                    when (task.status) {
                        TaskStatus.DOWNLOADING.name.lowercase() -> 3
                        TaskStatus.CONVERTING.name.lowercase() -> 2
                        TaskStatus.PENDING.name.lowercase() -> 1
                        TaskStatus.PAUSED.name.lowercase() -> 0
                        else -> -1
                    }
                }
                items(sortedTasks, key = { it.id }) { task ->
                    DownloadTaskItem(
                        task = task,
                        onPause = { viewModel.pauseTask(task.id) },
                        onResume = { viewModel.resumeTask(task.id) },
                        onDelete = { viewModel.deleteTask(task.id) },
                        onRetry = { viewModel.retryTask(task.id) }
                    )
                }

                // 失败/已取消任务
                if (failedTasks.isNotEmpty()) {
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                            thickness = 0.5.dp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "失败/已取消",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(onClick = { viewModel.clearFailed() }) {
                                Text("清除全部", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                            }
                        }
                    }
                    items(failedTasks, key = { it.id }) { task ->
                        DownloadTaskItem(
                            task = task,
                            onPause = { viewModel.pauseTask(task.id) },
                            onResume = { viewModel.resumeTask(task.id) },
                            onDelete = { viewModel.deleteTask(task.id) },
                            onRetry = { viewModel.retryTask(task.id) }
                        )
                    }
                }

                // 已完成任务
                if (completedTasks.isNotEmpty()) {
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                            thickness = 0.5.dp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "已完成",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(onClick = { viewModel.clearCompleted() }) {
                                Text("清除全部", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                            }
                        }
                    }
                    items(completedTasks, key = { it.id }) { task ->
                        DownloadTaskItem(
                            task = task,
                            onPause = { viewModel.pauseTask(task.id) },
                            onResume = { viewModel.resumeTask(task.id) },
                            onDelete = { viewModel.deleteTask(task.id) },
                            onRetry = { viewModel.retryTask(task.id) }
                        )
                    }
                }
            }
        }
    }
}
