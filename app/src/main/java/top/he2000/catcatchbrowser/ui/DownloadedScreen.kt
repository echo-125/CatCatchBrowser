package top.he2000.catcatchbrowser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.he2000.catcatchbrowser.ui.components.DownloadedItem
import top.he2000.catcatchbrowser.viewmodel.MainViewModel

@Composable
fun DownloadedScreen(viewModel: MainViewModel) {
    val tasks by viewModel.completedTasks.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部标题栏，高度与网址栏一致 (52dp)
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
                    "已下载",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                if (tasks.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearCompleted() }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "清除已完成"
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp)

        if (tasks.isEmpty()) {
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
                    Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "暂无已完成的下载",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "下载完成后会显示在这里",
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
                items(tasks) { task ->
                    DownloadedItem(
                        task = task,
                        onDelete = { viewModel.deleteTask(task.id) }
                    )
                }
            }
        }
    }
}
