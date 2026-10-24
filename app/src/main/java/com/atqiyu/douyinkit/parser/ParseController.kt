package com.atqiyu.douyinkit.parser

import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.core.model.DouyinKitException
import com.atqiyu.douyinkit.core.model.ErrorCode
import com.atqiyu.douyinkit.core.model.ParseSource
import com.atqiyu.douyinkit.core.util.LinkExtractor
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update

/**
 * Orchestrates link resolution, page loading, detail capture and page-model enrichment.
 * A single instance is used by ParseActivity for the lifetime of one parse batch.
 */
class ParseController(
    private val engine: DouyinWebViewEngine,
    private val resolver: ShortLinkResolver,
    private val normalizer: DouyinDetailNormalizer,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ParseUiState())
    val state: StateFlow<ParseUiState> = _state.asStateFlow()

    private val events = Channel<ParserEvent>(Channel.UNLIMITED)
    private val eventJob = scope.launch {
        engine.events.collect { events.send(it) }
    }

    private var parseJob: Job? = null
    private var tickerJob: Job? = null
    private var startedAtMillis: Long = 0L
    private var currentItemStartedAt: Long = 0L

    fun start(inputs: List<String>, forceRefresh: Boolean = false) {
        parseJob?.cancel()
        tickerJob?.cancel()
        drainEvents()

        val cleaned = inputs
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_BATCH)

        if (cleaned.isEmpty()) {
            _state.value = ParseUiState(
                running = false,
                stage = ParseStage.FAILED,
                failures = listOf(ParseFailure("", "没有可解析的链接")),
                message = "没有可解析的链接",
            )
            return
        }

        startedAtMillis = System.currentTimeMillis()
        _state.value = ParseUiState(
            running = true,
            stage = ParseStage.RESOLVING,
            total = cleaned.size,
            message = "准备解析 ${cleaned.size} 个链接",
        )
        startTicker()

        parseJob = scope.launch {
            val results = mutableListOf<Aweme>()
            val failures = mutableListOf<ParseFailure>()
            try {
                cleaned.forEachIndexed { index, input ->
                    currentItemStartedAt = System.currentTimeMillis()
                    _state.update {
                        it.copy(
                            currentIndex = index,
                            stage = ParseStage.RESOLVING,
                            pageProgress = 0,
                            showWebView = false,
                            needsVerification = false,
                            message = "正在识别链接",
                        )
                    }
                    try {
                        val aweme = parseOne(input, forceRefresh)
                        results += aweme
                        _state.update {
                            it.copy(
                                results = results.toList(),
                                showWebView = false,
                                needsVerification = false,
                                message = "已解析：${aweme.displayTitle}",
                            )
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        val failure = toFailure(input, error)
                        failures += failure
                        _state.update {
                            it.copy(
                                failures = failures.toList(),
                                showWebView = false,
                                needsVerification = false,
                                message = failure.message,
                            )
                        }
                    }
                }

                val finalStage = if (results.isEmpty()) ParseStage.FAILED else ParseStage.DONE
                val finalMessage = when {
                    failures.isEmpty() -> "全部解析完成"
                    results.isEmpty() -> failures.last().message
                    else -> "完成 ${results.size} 个，失败 ${failures.size} 个"
                }
                _state.update {
                    it.copy(
                        running = false,
                        stage = finalStage,
                        showWebView = false,
                        needsVerification = false,
                        elapsedMs = System.currentTimeMillis() - startedAtMillis,
                        message = finalMessage,
                    )
                }
            } catch (cancelled: CancellationException) {
                _state.update {
                    it.copy(
                        running = false,
                        stage = ParseStage.CANCELLED,
                        showWebView = false,
                        message = "已取消",
                        elapsedMs = System.currentTimeMillis() - startedAtMillis,
                    )
                }
                throw cancelled
            } finally {
                tickerJob?.cancel()
            }
        }
    }

    fun cancel() {
        parseJob?.cancel()
        tickerJob?.cancel()
        engine.stopLoading()
        _state.update {
            it.copy(
                running = false,
                stage = ParseStage.CANCELLED,
                showWebView = false,
                message = "已取消",
            )
        }
    }

    fun showWebView() {
        _state.update { it.copy(showWebView = true) }
    }

    fun hideWebView() {
        _state.update { it.copy(showWebView = false) }
    }

    fun reloadPage() {
        engine.reload()
    }

    fun destroy() {
        cancel()
        eventJob.cancel()
        engine.destroy()
    }

    private suspend fun parseOne(input: String, forceRefresh: Boolean): Aweme {
        _state.update { it.copy(stage = ParseStage.RESOLVING, message = "正在解析短链") }
        val resolved = resolver.resolve(input)
        val awemeId = resolved.awemeId

        _state.update { it.copy(stage = ParseStage.OPENING, message = "正在打开作品页") }
        engine.loadVideo(awemeId)

        _state.update {
            it.copy(
                stage = ParseStage.WAITING_DETAIL,
                message = "正在等待作品数据（首次解析可能需要 10–20 秒）",
            )
        }
        val bundle = awaitCapture(awemeId)
        var aweme = normalizeBundle(bundle, resolved)

        if (aweme.kind == AwemeKind.IMAGES) {
            _state.update {
                it.copy(
                    stage = ParseStage.WAITING_NOTE,
                    message = "正在读取图文与 LivePhoto 数据",
                    showWebView = false,
                    needsVerification = false,
                )
            }
            engine.loadNote(awemeId)
            awaitNoteReady(awemeId, 10_000)
            val noteSince = System.currentTimeMillis()
            _state.update { it.copy(stage = ParseStage.ENRICHING, message = "正在整理图片信息") }
            engine.requestPageModel(awemeId)
            val extra = awaitPageOrNewDetail(awemeId, noteSince, 10_000)
            if (extra != null) {
                aweme = normalizer.enrich(aweme, extra.detail)
                if (aweme.images.isEmpty()) {
                    aweme = runCatching {
                        normalizer.normalize(
                            extra.detail,
                            awemeId,
                            ParseSource.PAGE_MODEL,
                            resolved.sourceUrl,
                        )
                    }.getOrDefault(aweme)
                }
            }
        } else {
            _state.update { it.copy(stage = ParseStage.ENRICHING, message = "正在整理清晰度与音频信息") }
            val since = System.currentTimeMillis()
            engine.requestPageModel(awemeId)
            val extra = awaitPageOrNewDetail(awemeId, since, 3_000)
            if (extra != null) {
                aweme = normalizer.enrich(aweme, extra.detail)
            }
        }

        _state.update {
            it.copy(
                showWebView = false,
                needsVerification = false,
                pageProgress = 100,
            )
        }
        return aweme
    }

    private fun normalizeBundle(bundle: CaptureBundle, resolved: ResolvedLink): Aweme {
        val detail = bundle.detail
        val page = bundle.page
        var detailError: Exception? = null

        if (detail != null) {
            try {
                return normalizer.normalize(
                    detail.detail,
                    resolved.awemeId,
                    ParseSource.DETAIL,
                    resolved.sourceUrl,
                )
            } catch (error: DouyinKitException) {
                detailError = error
                if (error.code != ErrorCode.NO_MEDIA) throw error
            }
        }

        if (page != null) {
            val fromPage = normalizer.normalizePageModel(page.detail, resolved.awemeId)
            if (fromPage != null) return fromPage
        }

        throw detailError ?: DouyinKitException(ErrorCode.DETAIL_TIMEOUT, "没有拿到作品数据")
    }

    private suspend fun awaitCapture(awemeId: String): CaptureBundle {
        var detail = engine.cachedDetail(awemeId)
        var page = engine.cachedPageModel(awemeId)
        var pageState: PageState? = null
        val itemStart = System.currentTimeMillis()
        var deadline = itemStart + DETAIL_TIMEOUT_MS
        var nextProbe = itemStart + FIRST_PROBE_MS
        var challengeExtended = false
        var lastError: String? = null

        while (System.currentTimeMillis() < deadline) {
            if (detail != null) {
                return CaptureBundle(detail = detail, page = page, pageState = pageState)
            }

            val event = withTimeoutOrNull(350) { events.receive() }
            when (event) {
                is ParserEvent.Detail -> {
                    if (event.capture.awemeId == awemeId) {
                        detail = event.capture
                        _state.update { it.copy(message = "已获取作品数据") }
                    }
                }

                is ParserEvent.PageModel -> {
                    if (event.capture.awemeId == awemeId) page = event.capture
                }

                is ParserEvent.PageReady -> {
                    pageState = event.state
                    _state.update { it.copy(pageState = event.state) }
                    if (event.state.looksLikeChallenge && !challengeExtended) {
                        challengeExtended = true
                        deadline = System.currentTimeMillis() + VERIFICATION_TIMEOUT_MS
                        _state.update {
                            it.copy(
                                stage = ParseStage.VERIFYING,
                                showWebView = true,
                                needsVerification = true,
                                message = "请在页面中完成安全验证，完成后会自动继续",
                            )
                        }
                    }
                }

                is ParserEvent.PageError -> {
                    lastError = event.message
                }

                is ParserEvent.Fatal -> {
                    throw DouyinKitException(event.code, event.message)
                }

                is ParserEvent.Progress -> {
                    _state.update { it.copy(pageProgress = event.percent) }
                }

                else -> Unit
            }

            val now = System.currentTimeMillis()
            if (now >= nextProbe) {
                nextProbe = now + PROBE_INTERVAL_MS
                engine.requestPageModel(awemeId)
                val state = runCatching { engine.readPageState() }.getOrNull()
                if (state != null) {
                    pageState = state
                    _state.update { it.copy(pageState = state) }
                    if (state.looksLikeChallenge && !challengeExtended) {
                        challengeExtended = true
                        deadline = now + VERIFICATION_TIMEOUT_MS
                        _state.update {
                            it.copy(
                                stage = ParseStage.VERIFYING,
                                showWebView = true,
                                needsVerification = true,
                                message = "请在页面中完成安全验证，完成后会自动继续",
                            )
                        }
                    } else if (!challengeExtended && now - itemStart > SLOW_PARSE_MS) {
                        _state.update {
                            it.copy(
                                showWebView = true,
                                message = "解析较慢，可展开页面查看状态",
                            )
                        }
                    }
                }
            }

            if (page != null &&
                !challengeExtended &&
                System.currentTimeMillis() - itemStart > PAGE_ONLY_FALLBACK_MS
            ) {
                return CaptureBundle(detail = null, page = page, pageState = pageState)
            }
        }

        if (detail == null && page == null) {
            val message = when {
                pageState?.looksLikeChallenge == true -> "需要完成安全验证后重试"
                lastError != null -> lastError!!
                else -> "等待作品数据超时，请重试"
            }
            throw DouyinKitException(
                if (pageState?.looksLikeChallenge == true) ErrorCode.NEEDS_VERIFICATION else ErrorCode.DETAIL_TIMEOUT,
                message,
            )
        }
        return CaptureBundle(detail = detail, page = page, pageState = pageState)
    }

    private suspend fun awaitNoteReady(awemeId: String, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val event = withTimeoutOrNull(300) { events.receive() }
            when (event) {
                is ParserEvent.PageReady -> {
                    if (event.state.href.contains("/note/")) {
                        if (event.state.looksLikeChallenge) {
                            _state.update {
                                it.copy(
                                    stage = ParseStage.VERIFYING,
                                    showWebView = true,
                                    needsVerification = true,
                                    message = "请在页面中完成验证，完成后会自动继续",
                                )
                            }
                        }
                        return
                    }
                }

                is ParserEvent.Fatal -> throw DouyinKitException(event.code, event.message)
                else -> Unit
            }
        }
    }

    private suspend fun awaitPageOrNewDetail(
        awemeId: String,
        since: Long,
        timeoutMs: Long,
    ): DetailCapture? {
        engine.cachedPageModel(awemeId)?.takeIf { it.receivedAt >= since }?.let { return it }
        engine.cachedDetail(awemeId)?.takeIf { it.receivedAt >= since }?.let { return it }

        val deadline = System.currentTimeMillis() + timeoutMs
        var nextProbe = System.currentTimeMillis() + 1_200
        while (System.currentTimeMillis() < deadline) {
            val event = withTimeoutOrNull(300) { events.receive() }
            when (event) {
                is ParserEvent.PageModel -> {
                    if (event.capture.awemeId == awemeId && event.capture.receivedAt >= since) {
                        return event.capture
                    }
                }

                is ParserEvent.Detail -> {
                    if (event.capture.awemeId == awemeId && event.capture.receivedAt >= since) {
                        return event.capture
                    }
                }

                is ParserEvent.PageReady -> {
                    if (event.state.looksLikeChallenge) {
                        _state.update {
                            it.copy(
                                stage = ParseStage.VERIFYING,
                                showWebView = true,
                                needsVerification = true,
                                message = "请在页面中完成验证，完成后会自动继续",
                            )
                        }
                    }
                }

                is ParserEvent.Fatal -> throw DouyinKitException(event.code, event.message)
                else -> Unit
            }

            if (System.currentTimeMillis() >= nextProbe) {
                nextProbe = System.currentTimeMillis() + 2_000
                engine.requestPageModel(awemeId)
            }
        }

        return engine.cachedPageModel(awemeId)?.takeIf { it.receivedAt >= since }
            ?: engine.cachedDetail(awemeId)?.takeIf { it.receivedAt >= since }
    }

    private fun startTicker() {
        tickerJob = scope.launch {
            while (isActive) {
                delay(500)
                if (_state.value.running) {
                    _state.update {
                        it.copy(elapsedMs = System.currentTimeMillis() - startedAtMillis)
                    }
                }
            }
        }
    }

    private fun drainEvents() {
        while (events.tryReceive().isSuccess) {
            // Drop stale events from a previous batch.
        }
    }

    private fun toFailure(input: String, error: Exception): ParseFailure {
        val mapped = when (error) {
            is DouyinKitException -> error
            is SocketTimeoutException -> DouyinKitException(ErrorCode.NETWORK, "网络超时，请重试", error)
            is InterruptedIOException -> DouyinKitException(ErrorCode.NETWORK, "网络请求被中断", error)
            else -> DouyinKitException(ErrorCode.UNKNOWN, error.message ?: "解析失败，请重试", error)
        }
        return ParseFailure(input = input, message = mapped.message, code = mapped.code)
    }

    private data class CaptureBundle(
        val detail: DetailCapture?,
        val page: DetailCapture?,
        val pageState: PageState?,
    )

    companion object {
        private const val MAX_BATCH = 20
        private const val DETAIL_TIMEOUT_MS = 45_000L
        private const val VERIFICATION_TIMEOUT_MS = 120_000L
        private const val FIRST_PROBE_MS = 7_000L
        private const val PROBE_INTERVAL_MS = 6_000L
        private const val SLOW_PARSE_MS = 20_000L
        private const val PAGE_ONLY_FALLBACK_MS = 22_000L
    }
}
