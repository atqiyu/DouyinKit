package com.atqiyu.douyinkit.floating

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.atqiyu.douyinkit.di.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val enabled = withTimeoutOrNull(8_000) {
                runCatching {
                    context.applicationContext.appContainer.settingsRepository.settings.first()
                        .floatingBallEnabled
                }.getOrDefault(false)
            } ?: false
            FloatingAssistantScheduler.schedule(context)
            if (enabled && FloatingPermissions.canDrawOverlays(context)) {
                runCatching { FloatingAssistantService.start(context) }
            }
            pendingResult.finish()
            scope.cancel()
        }
    }
}
