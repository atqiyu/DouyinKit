package com.atqiyu.douyinkit.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.webkit.WebSettings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.atqiyu.douyinkit.MainActivity
import com.atqiyu.douyinkit.R
import com.atqiyu.douyinkit.core.model.DouyinKitException
import com.atqiyu.douyinkit.core.net.DouyinUserAgent
import com.atqiyu.douyinkit.core.net.HttpClients
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val activeJobs = AtomicInteger(0)
    private val semaphore = Semaphore(MAX_CONCURRENT)
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private var engine: DownloadEngine? = null
    private var lastNotifyAt = 0L

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        engine = createEngine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ENQUEUE -> {
                val requestJson = intent.getStringExtra(EXTRA_REQUEST)
                val request = requestJson?.let {
                    runCatching { json.decodeFromString<DownloadRequest>(it) }.getOrNull()
                }
                if (request == null) {
                    stopIfIdle()
                    return START_NOT_STICKY
                }
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: UUID.randomUUID().toString()
                DownloadRegistry.add(DownloadTask(id = taskId, request = request))
                startForegroundIfNeeded()
                launchTask(taskId, request)
            }

            ACTION_CANCEL -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                if (taskId != null) {
                    jobs[taskId]?.cancel()
                    DownloadRegistry.update(taskId) {
                        it.copy(status = DownloadStatus.CANCELLED, message = "已取消")
                    }
                }
                startForegroundIfNeeded()
            }

            ACTION_CANCEL_ALL -> {
                jobs.values.forEach { it.cancel() }
                DownloadRegistry.tasks.value
                    .filter { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RUNNING }
                    .forEach { task ->
                        DownloadRegistry.update(task.id) {
                            it.copy(status = DownloadStatus.CANCELLED, message = "已取消")
                        }
                    }
                startForegroundIfNeeded()
            }

            else -> startForegroundIfNeeded()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        jobs.values.forEach { it.cancel() }
        scope.cancel()
        super.onDestroy()
    }

    private fun createEngine(): DownloadEngine {
        val ua = runCatching {
            DouyinUserAgent.desktopFrom(WebSettings.getDefaultUserAgent(this))
        }.getOrDefault(DouyinUserAgent.DESKTOP_FALLBACK)
        return DownloadEngine(
            context = applicationContext,
            downloader = FileDownloader(HttpClients.download(), ua),
            saver = MediaStoreSaver(applicationContext),
            userAgent = ua,
        )
    }

    private fun launchTask(taskId: String, request: DownloadRequest) {
        activeJobs.incrementAndGet()
        val job = scope.launch {
            try {
                semaphore.withPermit {
                    DownloadRegistry.update(taskId) {
                        it.copy(status = DownloadStatus.RUNNING, message = "准备下载")
                    }
                    updateNotification()
                    val outcome = engine!!.execute(request) { progress ->
                        DownloadRegistry.update(taskId) {
                            it.copy(progress = progress, message = progressMessage(progress))
                        }
                        maybeNotify()
                    }
                    val status = if (outcome.isPartial) {
                        DownloadStatus.PARTIAL
                    } else {
                        DownloadStatus.COMPLETED
                    }
                    DownloadRegistry.update(taskId) {
                        it.copy(
                            status = status,
                            files = outcome.files,
                            zip = outcome.zip,
                            message = when {
                                outcome.warnings.isEmpty() -> "下载完成"
                                outcome.files.isEmpty() -> outcome.warnings.first()
                                else -> "部分完成：${outcome.warnings.size} 个文件失败"
                            },
                            error = outcome.warnings.takeIf { it.isNotEmpty() }?.joinToString("；"),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                DownloadRegistry.update(taskId) {
                    it.copy(status = DownloadStatus.CANCELLED, message = "已取消")
                }
                throw cancelled
            } catch (error: DownloadException) {
                val status = if (error.failure == DownloadFailure.EXPIRED) {
                    DownloadStatus.NEEDS_REFRESH
                } else {
                    DownloadStatus.FAILED
                }
                DownloadRegistry.update(taskId) {
                    it.copy(
                        status = status,
                        message = if (status == DownloadStatus.NEEDS_REFRESH) {
                            "下载地址已过期，请刷新后重试"
                        } else {
                            error.message
                        },
                        error = error.message,
                    )
                }
            } catch (error: DouyinKitException) {
                DownloadRegistry.update(taskId) {
                    it.copy(status = DownloadStatus.FAILED, message = error.message, error = error.message)
                }
            } catch (error: Exception) {
                DownloadRegistry.update(taskId) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        message = error.message ?: "下载失败",
                        error = error.message,
                    )
                }
            } finally {
                jobs.remove(taskId)
                activeJobs.decrementAndGet()
                updateNotification()
                stopIfIdle()
            }
        }
        jobs[taskId] = job
    }

    private fun startForegroundIfNeeded() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            type,
        )
    }

    private fun buildNotification(): Notification {
        val active = DownloadRegistry.tasks.value.filter {
            it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED
        }
        val title: String
        val text: String
        var progress: Int? = null
        if (active.isEmpty()) {
            val last = DownloadRegistry.tasks.value.lastOrNull()
            title = if (last?.status == DownloadStatus.FAILED) "下载失败" else "下载完成"
            text = last?.message ?: "任务已结束"
        } else if (active.size == 1) {
            val task = active.first()
            title = task.request.aweme.displayTitle
            text = task.message.ifBlank { "准备下载" }
            progress = percent(task.progress)
        } else {
            title = "正在下载 ${active.size} 个任务"
            text = active.first().message.ifBlank { "后台下载中" }
            progress = aggregatePercent(active)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(active.isNotEmpty())
            .setAutoCancel(active.isEmpty())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (active.isNotEmpty()) {
            builder.addAction(R.drawable.ic_notification, "取消全部", cancelIntent)
        }
        if (progress != null) {
            builder.setProgress(100, progress, false)
        }
        return builder.build()
    }

    private fun updateNotification() {
        lastNotifyAt = SystemClock.elapsedRealtime()
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun maybeNotify() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotifyAt >= NOTIFY_INTERVAL_MS) {
            updateNotification()
        }
    }

    private fun percent(progress: DownloadProgress): Int? {
        val total = progress.total
        if (total == null || total <= 0) return null
        return ((progress.bytes * 100) / total).toInt().coerceIn(0, 100)
    }

    private fun aggregatePercent(tasks: List<DownloadTask>): Int? {
        val known = tasks.filter { it.progress.total != null && it.progress.total!! > 0 }
        if (known.isEmpty()) return null
        val bytes = known.sumOf { it.progress.bytes }
        val total = known.sumOf { it.progress.total!! }
        if (total <= 0) return null
        return ((bytes * 100) / total).toInt().coerceIn(0, 100)
    }

    private fun progressMessage(progress: DownloadProgress): String {
        val items = if (progress.itemsTotal > 1) {
            " ${progress.itemsDone}/${progress.itemsTotal}"
        } else {
            ""
        }
        val size = if (progress.total != null && progress.total > 0) {
            val percent = (progress.bytes * 100 / progress.total).coerceIn(0, 100)
            "$percent%"
        } else {
            formatDownloaded(progress.bytes)
        }
        return "下载中$items · $size"
    }

    private fun formatDownloaded(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1) String.format(java.util.Locale.CHINA, "%.1f MB", mb) else "${bytes / 1024} KB"
    }

    private fun stopIfIdle() {
        scope.launch {
            delay(1500)
            if (activeJobs.get() == 0) {
                updateNotification()
                ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "作品下载",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "显示视频、图集和音频的下载进度"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_ENQUEUE = "com.atqiyu.douyinkit.download.ENQUEUE"
        const val ACTION_CANCEL = "com.atqiyu.douyinkit.download.CANCEL"
        const val ACTION_CANCEL_ALL = "com.atqiyu.douyinkit.download.CANCEL_ALL"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_REQUEST = "extra_request"

        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 4101
        private const val MAX_CONCURRENT = 2
        private const val NOTIFY_INTERVAL_MS = 800L

        fun enqueue(context: Context, request: DownloadRequest): String {
            val taskId = UUID.randomUUID().toString()
            DownloadRegistry.add(DownloadTask(id = taskId, request = request))
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_ENQUEUE
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_REQUEST, downloadJson.encodeToString(request))
            }
            ContextCompat.startForegroundService(context, intent)
            return taskId
        }

        fun cancel(context: Context, taskId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_TASK_ID, taskId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancelAll(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL_ALL
            }
            ContextCompat.startForegroundService(context, intent)
        }

        private val downloadJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
