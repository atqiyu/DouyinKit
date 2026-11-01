package com.atqiyu.douyinkit.floating

import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.atqiyu.douyinkit.R

object FloatingAssistantScheduler {
    private const val JOB_ID = 7411
    private const val INTERVAL_MS = 15L * 60L * 1_000L

    fun schedule(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val component = ComponentName(context, FloatingAssistantJobService::class.java)
        val job = android.app.job.JobInfo.Builder(JOB_ID, component)
            .setPeriodic(INTERVAL_MS)
            .setPersisted(true)
            .setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_NONE)
            .build()
        runCatching {
            (context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as android.app.job.JobScheduler)
                .schedule(job)
        }
    }

    fun cancel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        runCatching {
            (context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as android.app.job.JobScheduler)
                .cancel(JOB_ID)
        }
    }

    fun notifyRestoreNeeded(context: Context, reason: String) {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    "floating_restore",
                    "Kit 浮球恢复",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "浮球被系统停止时提醒恢复"
                },
            )
        }
        val intent = android.content.Intent(context, com.atqiyu.douyinkit.MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = android.app.PendingIntent.getActivity(
            context,
            7412,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = androidx.core.app.NotificationCompat.Builder(
            context,
            "floating_restore",
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Kit 浮球需要恢复")
            .setContentText(reason)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(7412, notification) }
    }
}
