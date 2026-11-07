package com.atqiyu.douyinkit.floating

import android.content.Context
import android.util.Log
import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.ErrorCode
import com.atqiyu.douyinkit.core.util.LinkExtractor
import com.atqiyu.douyinkit.data.prefs.AppSettings
import com.atqiyu.douyinkit.di.appContainer
import com.atqiyu.douyinkit.download.DownloadKind
import com.atqiyu.douyinkit.download.DownloadRegistry
import com.atqiyu.douyinkit.download.DownloadRequest
import com.atqiyu.douyinkit.download.DownloadService
import com.atqiyu.douyinkit.download.DownloadStatus
import com.atqiyu.douyinkit.download.collectExistingDownloadContent
import com.atqiyu.douyinkit.download.findDuplicateDownloads
import com.atqiyu.douyinkit.download.hasContentAfterSkip
import com.atqiyu.douyinkit.parser.DouyinWebViewEngine
import com.atqiyu.douyinkit.parser.ParseBatchResult
import com.atqiyu.douyinkit.parser.ParseController
import com.atqiyu.douyinkit.parser.ParseSessionStore
import com.atqiyu.douyinkit.parser.ParseStage
import com.atqiyu.douyinkit.parser.ParseUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sequential background parse/download queue for 连播模式. It keeps one WebView alive between
 * queue items, shows the page only when Douyin asks for a captcha/login, and waits for the
 * automatic download to finish before starting the next work.
 */
class FloatingParseQueue(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val settingsRepository = appContext.appContainer.settingsRepository
    private val historyRepository = appContext.appContainer.historyRepository
    private val downloadHistoryRepository = appContext.appContainer.downloadHistoryRepository

    private val pending = ArrayDeque<String>()
    private val lock = Any()
    private var pumpJob: Job? = null
    private var activeLink: String? = null
    private var activeDownloadId: String? = null
    private var verificationDismissed = false
    private var engine: DouyinWebViewEngine? = null
    private var controller: ParseController? = null
    private var host: FloatingWebViewHost? = null
    private var consecutiveFailures = 0

    fun enqueue(raw: String): Boolean {
        val link = LinkExtractor.extract(raw).firstOrNull()?.url ?: return false
        val added = synchronized(lock) {
            if (activeLink == link || pending.contains(link)) {
                false
            } else {
                pending.addLast(link)
                true
            }
        }
        if (!added) {
            FloatingAssistantBus.update {
                it.copy(message = "这个作品已经在队列中", expanded = true)
            }
            return false
        }
        val size = synchronized(lock) { pending.size + if (activeLink != null) 1 else 0 }
        FloatingAssistantBus.update {
            it.copy(
                clipboardLink = link,
                promptVisible = false,
                expanded = true,
                resultReady = false,
                queueSize = size,
                message = if (activeLink != null) {
                    "已加入等待队列"
                } else {
                    "准备解析…"
                },
            )
        }
        startPump()
        return true
    }

    fun showPage() {
        verificationDismissed = false
        host?.showPage()
    }

    fun hidePage() {
        host?.hidePage()
    }

    fun isBusy(): Boolean = pumpJob?.isActive == true || activeLink != null

    fun stopAll() {
        synchronized(lock) {
            pending.clear()
        }
        activeDownloadId?.let { DownloadService.cancel(appContext, it) }
        activeDownloadId = null
        pumpJob?.cancel()
        pumpJob = null
        controller?.cancel()
        host?.hidePage()
        activeLink = null
        FloatingAssistantBus.update {
            it.copy(
                phase = FloatingPhase.IDLE,
                title = null,
                message = "已停止当前任务",
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

    fun destroy() {
        stopAll()
        resetController()
        consecutiveFailures = 0
    }

    private fun noteParseFailure() {
        consecutiveFailures += 1
        if (consecutiveFailures < WATCHDOG_FAILURE_LIMIT) return
        Log.w(TAG, "recreating hidden WebView after $consecutiveFailures consecutive timeouts")
        resetController()
        consecutiveFailures = 0
        FloatingAssistantBus.update {
            it.copy(
                expanded = true,
                message = "解析环境无响应，已自动重建，正在继续队列…",
                progress = null,
            )
        }
    }

    private fun resetController() {
        verificationDismissed = false
        host?.destroy()
        controller?.destroy()
        engine?.destroy()
        host = null
        controller = null
        engine = null
    }

    private fun startPump() {
        if (pumpJob?.isActive == true) return
        pumpJob = scope.launch {
            try {
                while (true) {
                    val next = synchronized(lock) {
                        if (pending.isEmpty()) null else pending.removeFirst()
                    } ?: break
                    activeLink = next
                    updateQueueSize()
                    process(next)
                    activeLink = null
                    updateQueueSize()
                    val hasPending = synchronized(lock) { pending.isNotEmpty() }
                    if (hasPending) {
                        val seconds = runCatching {
                            settingsRepository.settings.first().floatingQueueIntervalSeconds
                        }.getOrDefault(5).coerceIn(0, 60)
                        if (seconds > 0) {
                            delay(seconds * 1_000L)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "queue stopped", error)
                FloatingAssistantBus.update {
                    it.copy(
                        phase = FloatingPhase.ERROR,
                        message = error.message ?: "浮球任务失败",
                        progress = null,
                    )
                }
            } finally {
                activeLink = null
                pumpJob = null
                val hasPending = synchronized(lock) { pending.isNotEmpty() }
                if (FloatingAssistantBus.state.value.phase != FloatingPhase.ERROR && !hasPending) {
                    FloatingAssistantBus.update {
                        it.copy(
                            phase = FloatingPhase.IDLE,
                            progress = null,
                            queueSize = 0,
                            automationBusy = false,
                            needsVerification = false,
                            needsLogin = false,
                        )
                    }
                }
                if (hasPending) startPump()
            }
        }
    }

    private suspend fun process(link: String) {
        val parser = ensureController()
        Log.d(TAG, "parsing queued link, controller=${parser != null}")
        if (parser == null) {
            FloatingAssistantBus.update {
                it.copy(
                    phase = FloatingPhase.ERROR,
                    message = "无法启动后台解析，请检查悬浮窗权限",
                    detail = link,
                    progress = null,
                )
            }
            return
        }
        verificationDismissed = false
        FloatingAssistantBus.update {
            it.copy(
                phase = FloatingPhase.PARSING,
                title = null,
                message = "正在解析作品…",
                detail = link,
                progress = 0f,
                resultReady = false,
                needsVerification = false,
                needsLogin = false,
            )
        }
        val monitor = scope.launch {
            parser.state.collect { state -> renderParseState(state) }
        }
        var watchdogFailure = false
        try {
            parser.start(listOf(link))
            val finalState = withTimeoutOrNull(PARSE_TIMEOUT_MS) {
                parser.state.first { state ->
                    !state.running && state.stage in FINAL_STAGES
                }
            }
            if (finalState == null) {
                watchdogFailure = true
                parser.cancel()
                FloatingAssistantBus.update {
                    it.copy(
                        phase = FloatingPhase.ERROR,
                        message = "解析超时，已跳过这个作品",
                        progress = null,
                        needsVerification = false,
                        needsLogin = false,
                    )
                }
                host?.hidePage()
                return
            }
            host?.hidePage()
            val aweme = finalState.results.lastOrNull()
            Log.d(TAG, "parse finished: stage=${finalState.stage} results=${finalState.results.size}")
            if (aweme == null) {
                val failure = finalState.failures.lastOrNull()
                watchdogFailure = failure?.code == ErrorCode.DETAIL_TIMEOUT ||
                    failure?.code == ErrorCode.WEBVIEW_UNAVAILABLE
                val message = failure?.message ?: "没有解析到作品"
                FloatingAssistantBus.update {
                    it.copy(
                        phase = FloatingPhase.ERROR,
                        message = message,
                        detail = link,
                        progress = null,
                        resultReady = false,
                        needsVerification = false,
                        needsLogin = false,
                    )
                }
                return
            }
            withContext(Dispatchers.IO) {
                runCatching { historyRepository.saveAll(finalState.results) }
            }
            ParseSessionStore.publish(
                ParseBatchResult(
                    results = finalState.results,
                    failures = finalState.failures,
                ),
            )
            FloatingAssistantBus.update {
                it.copy(
                    phase = FloatingPhase.DONE,
                    title = aweme.displayTitle,
                    message = "解析完成",
                    detail = link,
                    progress = null,
                    resultReady = true,
                    needsVerification = false,
                    needsLogin = false,
                )
            }
            val settings = settingsRepository.settings.first()
            if (settings.floatingAutoDownload) {
                autoDownload(aweme, settings)
            } else {
                FloatingAssistantBus.update {
                    it.copy(message = "解析完成，可在 DouyinKit 中查看并下载")
                }
            }
        } finally {
            monitor.cancelAndJoin()
            if (watchdogFailure) noteParseFailure() else consecutiveFailures = 0
            if (parser.state.value.let { !it.running && it.stage in FINAL_STAGES }) {
                // keep the last result visible
            }
        }
    }

    private fun renderParseState(state: ParseUiState) {
        val progress = if (state.total > 0) {
            ((state.currentIndex + state.pageProgress / 100f) / state.total)
                .coerceIn(0f, 1f)
        } else {
            null
        }
        val loginRequired = state.pageState?.let {
            it.loginRequired && !it.looksLikeChallenge
        } == true
        when {
            state.needsVerification -> {
                if (!verificationDismissed) host?.showPage()
                FloatingAssistantBus.update {
                    it.copy(
                        phase = FloatingPhase.VERIFYING,
                        message = state.message.ifBlank { "需要完成安全验证" },
                        progress = progress,
                        needsVerification = true,
                        needsLogin = false,
                        expanded = true,
                    )
                }
            }

            loginRequired -> {
                FloatingAssistantBus.update {
                    it.copy(
                        phase = FloatingPhase.VERIFYING,
                        message = state.message.ifBlank { "该作品需要登录后查看" },
                        progress = progress,
                        needsVerification = false,
                        needsLogin = true,
                        expanded = true,
                    )
                }
            }

            state.running -> {
                if (!state.showWebView) host?.hidePage()
                FloatingAssistantBus.update {
                    it.copy(
                        phase = when (state.stage) {
                            ParseStage.RESOLVING,
                            ParseStage.OPENING,
                            ParseStage.WAITING_DETAIL,
                            ParseStage.WAITING_NOTE,
                            ParseStage.ENRICHING,
                            -> FloatingPhase.PARSING

                            ParseStage.VERIFYING -> FloatingPhase.VERIFYING
                            else -> FloatingPhase.PARSING
                        },
                        message = state.message,
                        title = state.results.lastOrNull()?.displayTitle ?: it.title,
                        progress = progress,
                        needsVerification = state.needsVerification,
                        needsLogin = false,
                    )
                }
            }
        }
    }

    private suspend fun autoDownload(aweme: Aweme, settings: AppSettings) {
        val request = buildFloatingDownloadRequest(aweme, settings)
        if (request == null) {
            FloatingAssistantBus.update { it.copy(message = "解析完成，但没有可自动下载的内容") }
            return
        }
        val finalRequest = withContext(Dispatchers.IO) {
            val existingTasks = runCatching {
                (
                    DownloadRegistry.tasks.value +
                        downloadHistoryRepository.tasksForAweme(aweme.awemeId)
                    ).distinctBy { it.id }
            }.getOrElse { DownloadRegistry.tasks.value }
            val existing = collectExistingDownloadContent(aweme.awemeId, existingTasks)
            val check = findDuplicateDownloads(listOf(request), existing)
            if (!check.hasDuplicates) {
                request
            } else {
                when (request.kind) {
                    DownloadKind.IMAGES, DownloadKind.ZIP -> {
                        val keys = check.matches.flatMap { it.keys }.toSet()
                        if (request.hasContentAfterSkip(keys)) {
                            request.copy(skipMediaKeys = keys)
                        } else {
                            null
                        }
                    }

                    else -> null
                }
            }
        }
        if (finalRequest == null) {
            FloatingAssistantBus.update {
                it.copy(
                    phase = FloatingPhase.DONE,
                    message = "该作品内容已经下载过，已跳过",
                    resultReady = true,
                    progress = null,
                )
            }
            return
        }
        val taskId = runCatching {
            DownloadService.enqueue(appContext, finalRequest)
        }.getOrElse { error ->
            FloatingAssistantBus.update {
                it.copy(
                    phase = FloatingPhase.ERROR,
                    message = "无法开始自动下载：${error.message ?: "系统限制"}",
                    progress = null,
                )
            }
            return
        }
        activeDownloadId = taskId
        FloatingAssistantBus.update {
            it.copy(
                phase = FloatingPhase.DOWNLOADING,
                message = "开始自动下载…",
                progress = 0f,
                queueSize = synchronized(lock) {
                    pending.size + if (activeLink != null) 1 else 0
                },
            )
        }
        val progressMonitor = scope.launch {
            DownloadRegistry.tasks.collect { tasks ->
                val task = tasks.firstOrNull { it.id == taskId } ?: return@collect
                val progress = task.progress.total?.takeIf { it > 0 }?.let { total ->
                    task.progress.bytes.toFloat() / total
                } ?: task.progress.itemsTotal.takeIf { it > 0 }?.let { total ->
                    task.progress.itemsDone.toFloat() / total
                }
                if (task.status == DownloadStatus.RUNNING ||
                    task.status == DownloadStatus.QUEUED
                ) {
                    FloatingAssistantBus.update {
                        it.copy(
                            phase = FloatingPhase.DOWNLOADING,
                            message = task.message.ifBlank { "正在自动下载…" },
                            progress = progress?.coerceIn(0f, 1f),
                        )
                    }
                }
            }
        }
        val finished = withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
            DownloadRegistry.tasks
                .map { tasks -> tasks.firstOrNull { it.id == taskId } }
                .first { task ->
                    task != null && task.status in FINISHED_DOWNLOAD_STATUSES
                }
        }
        progressMonitor.cancelAndJoin()
        activeDownloadId = null
        val task = finished ?: DownloadRegistry.get(taskId)
        when (task?.status) {
            DownloadStatus.COMPLETED -> {
                FloatingAssistantBus.update {
                    it.copy(
                        phase = FloatingPhase.DONE,
                        message = "已保存到相册",
                        progress = 1f,
                        resultReady = true,
                    )
                }
            }

            DownloadStatus.PARTIAL -> {
                FloatingAssistantBus.update {
                    it.copy(
                        phase = FloatingPhase.ERROR,
                        message = "部分文件没有保存成功，可在下载页查看",
                        progress = null,
                    )
                }
            }

            DownloadStatus.CANCELLED -> {
                FloatingAssistantBus.update {
                    it.copy(phase = FloatingPhase.IDLE, message = "自动下载已取消", progress = null)
                }
            }

            DownloadStatus.FAILED, DownloadStatus.NEEDS_REFRESH -> {
                FloatingAssistantBus.update {
                    it.copy(
                        phase = FloatingPhase.ERROR,
                        message = task.message.ifBlank { "自动下载失败" },
                        progress = null,
                    )
                }
            }

            else -> {
                FloatingAssistantBus.update {
                    it.copy(
                        phase = FloatingPhase.ERROR,
                        message = "自动下载等待超时，请在下载页查看",
                        progress = null,
                    )
                }
            }
        }
    }

    private fun ensureController(): ParseController? {
        controller?.let { return it }
        val newEngine = DouyinWebViewEngine(appContext)
        val newHost = FloatingWebViewHost(appContext, newEngine.webView) {
            verificationDismissed = true
            controller?.hideWebView()
            FloatingAssistantBus.update {
                it.copy(expanded = true, message = "已收起验证页面，可再次点击处理")
            }
        }
        if (!newHost.attach()) {
            newHost.destroy()
            newEngine.destroy()
            return null
        }
        engine = newEngine
        host = newHost
        controller = ParseController(
            engine = newEngine,
            resolver = appContext.appContainer.newShortLinkResolver(),
            normalizer = appContext.appContainer.newNormalizer(),
            scope = scope,
        )
        return controller
    }

    private fun updateQueueSize() {
        val size = synchronized(lock) {
            pending.size + if (activeLink != null) 1 else 0
        }
        FloatingAssistantBus.setQueueSize(size)
    }

    companion object {
        private const val TAG = "FloatingParseQueue"
        private const val PARSE_TIMEOUT_MS = 180_000L
        private const val WATCHDOG_FAILURE_LIMIT = 2
        private const val DOWNLOAD_TIMEOUT_MS = 30L * 60L * 1_000L
        private val FINAL_STAGES = setOf(
            ParseStage.DONE,
            ParseStage.FAILED,
            ParseStage.CANCELLED,
        )
        private val FINISHED_DOWNLOAD_STATUSES = setOf(
            DownloadStatus.COMPLETED,
            DownloadStatus.PARTIAL,
            DownloadStatus.FAILED,
            DownloadStatus.NEEDS_REFRESH,
            DownloadStatus.CANCELLED,
        )
    }
}
