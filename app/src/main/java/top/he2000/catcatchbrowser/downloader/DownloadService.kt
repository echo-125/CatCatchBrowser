package top.he2000.catcatchbrowser.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import top.he2000.catcatchbrowser.R
import top.he2000.catcatchbrowser.data.DownloadTaskEntity
import top.he2000.catcatchbrowser.data.TaskStatus

/**
 * 下载前台服务
 * 负责在后台执行下载任务，并在通知栏显示进度
 */
class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val CHANNEL_NAME = "下载服务"

        const val ACTION_START = "top.he2000.catcatchbrowser.action.START"
        const val ACTION_PAUSE = "top.he2000.catcatchbrowser.action.PAUSE"
        const val ACTION_CANCEL = "top.he2000.catcatchbrowser.action.CANCEL"
        const val ACTION_STOP = "top.he2000.catcatchbrowser.action.STOP"

        const val EXTRA_TASK_ID = "task_id"

        private var taskManager: TaskManager? = null

        fun setTaskManager(manager: TaskManager) {
            taskManager = manager
        }
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var notificationManager: NotificationManager

    inner class LocalBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // 观察任务变化，更新通知
        taskManager?.observeAllTasks()?.observeTasks()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
                if (taskId > 0) {
                    taskManager?.startTask(taskId)
                }
            }
            ACTION_PAUSE -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
                if (taskId > 0) {
                    taskManager?.pauseTask(taskId)
                }
            }
            ACTION_CANCEL -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
                if (taskId > 0) {
                    taskManager?.cancelTask(taskId)
                }
            }
            ACTION_STOP -> {
                taskManager?.pauseAll()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "视频下载进度通知"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 观察任务列表变化
     */
    private fun Flow<List<DownloadTaskEntity>>.observeTasks() {
        serviceScope.launch {
            collect { tasks ->
                val downloadingTasks = tasks.filter {
                    it.status == TaskStatus.DOWNLOADING.name.lowercase() ||
                        it.status == TaskStatus.CONVERTING.name.lowercase()
                }

                if (downloadingTasks.isEmpty()) {
                    // 没有正在下载的任务，停止前台服务
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    // 更新通知
                    updateNotification(downloadingTasks)
                }
            }
        }
    }

    /**
     * 更新通知
     */
    private fun updateNotification(tasks: List<DownloadTaskEntity>) {
        if (tasks.isEmpty()) return

        val notification = if (tasks.size == 1) {
            createSingleTaskNotification(tasks.first())
        } else {
            createMultiTaskNotification(tasks)
        }

        startForeground(1, notification)
    }

    /**
     * 创建单任务通知
     */
    private fun createSingleTaskNotification(task: DownloadTaskEntity): Notification {
        val progress = task.progress.toInt()
        val contentText = if (task.status == TaskStatus.CONVERTING.name.lowercase()) {
            "正在转换格式..."
        } else {
            "下载进度: $progress% | ${task.currentSpeed}"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(task.fileName)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "暂停",
                createPauseIntent(task.id)
            )
            .build()
    }

    /**
     * 创建多任务通知
     */
    private fun createMultiTaskNotification(tasks: List<DownloadTaskEntity>): Notification {
        val downloading = tasks.count { it.status == TaskStatus.DOWNLOADING.name.lowercase() }
        val converting = tasks.count { it.status == TaskStatus.CONVERTING.name.lowercase() }
        val total = tasks.size
        val avgProgress = if (total > 0) tasks.sumOf { it.progress.toDouble() / total }.toInt() else 0

        val statusText = buildString {
            append("$downloading 个下载中")
            if (converting > 0) append("，$converting 个转换中")
            append(" | 平均 $avgProgress%")
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$total 个任务进行中")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, avgProgress, false)
            .setOngoing(true)
            .build()
    }

    /**
     * 创建暂停意图
     */
    private fun createPauseIntent(taskId: Long): PendingIntent {
        val intent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_PAUSE
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getService(
            this,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
