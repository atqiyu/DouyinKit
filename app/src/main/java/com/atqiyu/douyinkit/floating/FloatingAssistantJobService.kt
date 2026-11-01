package com.atqiyu.douyinkit.floating

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import com.atqiyu.douyinkit.di.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import android.util.Log
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Periodic watchdog for the floating foreground service. It uses the persisted job to recover
 * after a process kill and intentionally re-checks the user's setting before starting anything.
 */
class FloatingAssistantJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        scope.launch {
            try {
                val enabled = withTimeoutOrNull(8_000) {
                    runCatching {
                        applicationContext.appContainer.settingsRepository.settings.first()
                            .floatingBallEnabled
                    }.getOrDefault(false)
                } ?: false
                Log.d(TAG, "watchdog enabled=$enabled")
                if (enabled && FloatingPermissions.canDrawOverlays(applicationContext)) {
                    val started = runCatching {
                        FloatingAssistantService.start(applicationContext)
                        true
                    }.getOrDefault(false)
                    if (!started) {
                        FloatingAssistantScheduler.notifyRestoreNeeded(
                            applicationContext,
                            "系统限制了后台启动，点击打开 DouyinKit 后重新开启浮球",
                        )
                    }
                }
            } finally {
                withContext(NonCancellable) { jobFinished(params, false) }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        scope.cancel()
        // Keep the persisted watchdog scheduled if the system stops this run.
        return true
    }

    companion object {
        private const val TAG = "FloatingJobService"
    }
}
