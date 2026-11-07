package com.atqiyu.douyinkit.floating

import com.atqiyu.douyinkit.data.prefs.FloatingMode
import com.atqiyu.douyinkit.data.prefs.FloatingQueueMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide state shared by the floating service, accessibility service and settings UI.
 * It deliberately contains no Android objects so updates are cheap and thread-safe.
 */
object FloatingAssistantBus {
    private val _state = MutableStateFlow(FloatingUiState())
    val state: StateFlow<FloatingUiState> = _state.asStateFlow()

    private val _copySignals = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val copySignals: SharedFlow<Unit> = _copySignals.asSharedFlow()

    fun signalClipboardCopy() {
        _copySignals.tryEmit(Unit)
    }

    fun update(transform: (FloatingUiState) -> FloatingUiState) {
        _state.update(transform)
    }

    fun setEnabled(enabled: Boolean) {
        update { it.copy(enabled = enabled) }
    }

    fun setMode(mode: FloatingMode) {
        update { it.copy(mode = mode) }
    }

    fun setQueueMode(mode: FloatingQueueMode) {
        update { it.copy(queueMode = mode) }
    }

    fun setAccessibilityReady(ready: Boolean) {
        update {
            it.copy(
                accessibilityReady = ready,
                foreground = if (ready) it.foreground else ForegroundTarget.UNKNOWN,
            )
        }
    }

    fun setOverlayReady(ready: Boolean) {
        update { it.copy(overlayReady = ready) }
    }

    fun setAppForeground(foreground: Boolean) {
        update { it.copy(appForeground = foreground) }
    }

    fun setForeground(packageName: String?) {
        val target = when {
            packageName == null -> ForegroundTarget.UNKNOWN
            packageName == DouyinPackages.MAIN -> ForegroundTarget.DOUYIN
            packageName == DouyinPackages.LITE -> ForegroundTarget.DOUYIN_LITE
            packageName == "com.atqiyu.douyinkit" -> ForegroundTarget.OTHER
            else -> ForegroundTarget.OTHER
        }
        update { it.copy(foreground = target) }
    }

    fun setClipboardLink(link: String?) {
        update { it.copy(clipboardLink = link) }
    }

    fun setPromptVisible(visible: Boolean) {
        update { it.copy(promptVisible = visible) }
    }

    fun setExpanded(expanded: Boolean) {
        update { it.copy(expanded = expanded) }
    }

    fun setPhase(
        phase: FloatingPhase,
        message: String? = null,
        title: String? = null,
        detail: String? = null,
        progress: Float? = null,
        queueSize: Int? = null,
    ) {
        update {
            it.copy(
                phase = phase,
                message = message ?: it.message,
                title = title ?: it.title,
                detail = detail ?: it.detail,
                progress = progress,
                queueSize = queueSize ?: it.queueSize,
            )
        }
    }

    fun setNeedsUserAction(verification: Boolean, login: Boolean) {
        update {
            it.copy(
                needsVerification = verification,
                needsLogin = login,
                phase = if (verification || login) FloatingPhase.VERIFYING else it.phase,
            )
        }
    }

    fun setAutomationBusy(busy: Boolean) {
        update { it.copy(automationBusy = busy) }
    }

    fun setResultReady(ready: Boolean) {
        update { it.copy(resultReady = ready) }
    }

    fun setQueueSize(size: Int) {
        update { it.copy(queueSize = size) }
    }

    fun setProgress(progress: Float?) {
        update { it.copy(progress = progress) }
    }

    fun resetTask(message: String = "待命") {
        update {
            it.copy(
                phase = FloatingPhase.IDLE,
                title = null,
                message = message,
                detail = null,
                progress = null,
                queueSize = 0,
                resultReady = false,
                automationBusy = false,
                needsVerification = false,
                needsLogin = false,
            )
        }
    }
}
