package com.atqiyu.douyinkit.parser

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.atqiyu.douyinkit.BuildConfig
import com.atqiyu.douyinkit.core.model.ErrorCode
import com.atqiyu.douyinkit.core.net.DouyinUserAgent
import com.atqiyu.douyinkit.core.util.LinkExtractor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

data class DetailCapture(
    val awemeId: String,
    val detail: JsonObject,
    val url: String = "",
    val bytes: Int = 0,
    val source: String = "xhr",
    val receivedAt: Long = System.currentTimeMillis(),
)

data class PageState(
    val href: String = "",
    val title: String = "",
    val readyState: String = "",
    val hook: Boolean = false,
    val hasRoot: Boolean = false,
    val bodyLength: Int = 0,
    val text: String = "",
    val detailIds: List<String> = emptyList(),
    val bridge: String = "",
) {
    val looksLikeChallenge: Boolean
        get() {
            val lower = (text + " " + title + " " + href).lowercase()
            if (lower.contains("captcha") || lower.contains("verify") || lower.contains("验证码")) {
                return true
            }
            if (text.contains("安全验证") ||
                text.contains("请完成验证") ||
                text.contains("抱歉出错了") ||
                text.contains("请尝试在抖音内观看")
            ) {
                return true
            }
            return !hasRoot && bodyLength in 1..20_000
        }
}

sealed interface ParserEvent {
    data class Detail(val capture: DetailCapture) : ParserEvent
    data class PageModel(val capture: DetailCapture) : ParserEvent
    data class PageStarted(val url: String) : ParserEvent
    data class PageReady(val state: PageState) : ParserEvent
    data class PageError(val code: ErrorCode, val message: String) : ParserEvent
    data class Progress(val percent: Int) : ParserEvent
    data class Diagnostic(val event: String, val data: JsonObject? = null) : ParserEvent
    data class Fatal(val code: ErrorCode, val message: String) : ParserEvent
}

/**
 * Owns exactly one WebView. The Activity hosting this engine must not be recreated while parsing
 * (ParseActivity declares configChanges); the engine is deliberately not kept in a ViewModel.
 */
class DouyinWebViewEngine(private val activity: Activity) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _events = MutableSharedFlow<ParserEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ParserEvent> = _events.asSharedFlow()

    private val detailCache = ConcurrentHashMap<String, DetailCapture>()
    private val pageModelCache = ConcurrentHashMap<String, DetailCapture>()
    private val started = AtomicBoolean(false)
    private val destroyed = AtomicBoolean(false)
    private var lastPageState: PageState = PageState()
    private var lastLoadedUrl: String = ""

    private val injectionScript: String = buildString {
        append(activity.assets.open("parser/hook.js").bufferedReader().use { it.readText() })
        append("\n\n")
        append(activity.assets.open("parser/page_model.js").bufferedReader().use { it.readText() })
    }

    private val documentStartSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    private val webMessageSupported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    val desktopUserAgent: String
    val defaultUserAgent: String

    val webView: WebView = WebView(activity).apply {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    init {
        defaultUserAgent = WebSettings.getDefaultUserAgent(activity)
        desktopUserAgent = DouyinUserAgent.desktopFrom(defaultUserAgent)

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        configureWebView()
        installBridge()
        installScripts()
        webView.webViewClient = createWebViewClient()
        webView.webChromeClient = createChromeClient()

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
    }

    fun load(url: String) {
        lastLoadedUrl = url
        lastPageState = PageState()
        mainHandler.post {
            webView.loadUrl(
                url,
                mapOf("Accept-Language" to "zh-CN,zh;q=0.9"),
            )
        }
    }

    fun loadVideo(awemeId: String) = load("https://www.douyin.com/video/$awemeId")

    fun loadNote(awemeId: String) = load("https://www.douyin.com/note/$awemeId")

    fun reload() {
        mainHandler.post { webView.reload() }
    }

    fun stopLoading() {
        mainHandler.post {
            webView.stopLoading()
        }
    }

    fun cachedDetail(awemeId: String): DetailCapture? = detailCache[awemeId]

    fun cachedPageModel(awemeId: String): DetailCapture? = pageModelCache[awemeId]

    fun requestPageModel(awemeId: String): Boolean {
        val escaped = awemeId.replace("\\", "\\\\").replace("'", "\\'")
        val script = "(function(){try{return !!(window.__DK_extractPageModel && window.__DK_extractPageModel('$escaped'));}catch(e){return false;}})()"
        if (!started.get()) return false
        mainHandler.post {
            webView.evaluateJavascript(script) { result ->
                if (result == "false" && !documentStartSupported) {
                    webView.evaluateJavascript(injectionScript, null)
                    mainHandler.postDelayed({
                        webView.evaluateJavascript(script, null)
                    }, 350)
                }
            }
        }
        return true
    }

    suspend fun readPageState(): PageState = suspendCancellableCoroutine { cont ->
        mainHandler.post {
            val script = "(function(){try{return (window.__DK_getState ? window.__DK_getState() : '{}');}catch(e){return '{}';}})()"
            webView.evaluateJavascript(script) { raw ->
                val decoded = decodeJsString(raw)
                val state = parsePageState(decoded)
                lastPageState = state
                if (cont.isActive) cont.resume(state)
            }
        }
    }

    fun clearBrowserData(onDone: (() -> Unit)? = null) {
        mainHandler.post {
            CookieManager.getInstance().removeAllCookies {
                CookieManager.getInstance().flush()
                onDone?.invoke()
            }
        }
    }

    fun clearCache() {
        mainHandler.post {
            webView.clearCache(true)
            webView.clearFormData()
            webView.clearHistory()
        }
    }

    fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        mainHandler.post {
            runCatching {
                webView.removeJavascriptInterface(BRIDGE_JS_NAME)
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.clearCache(true)
                webView.destroy()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            blockNetworkImage = false
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            textZoom = 100
            setGeolocationEnabled(false)
            saveFormData = false
            setNeedInitialFocus(false)
            userAgentString = desktopUserAgent
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }
    }

    @SuppressLint("RequiresFeature")
    private fun installBridge() {
        if (webMessageSupported) {
            runCatching {
                WebViewCompat.addWebMessageListener(
                    webView,
                    BRIDGE_JS_NAME,
                    ALLOWED_ORIGINS,
                    object : WebViewCompat.WebMessageListener {
                        override fun onPostMessage(
                            view: WebView,
                            message: WebMessageCompat,
                            sourceOrigin: Uri,
                            isMainFrame: Boolean,
                            replyProxy: JavaScriptReplyProxy,
                        ) {
                            if (!isMainFrame) return
                            val host = sourceOrigin.host.orEmpty()
                            if (!LinkExtractor.isDouyinHost(host)) return
                            val text = message.data ?: return
                            handleRawMessage(text)
                        }
                    },
                )
            }
        }

        // The JS interface also acts as the fallback for WebViews without WebMessageListener.
        webView.addJavascriptInterface(JsBridge(), BRIDGE_ANDROID_NAME)
    }

    @SuppressLint("RequiresFeature")
    private fun installScripts() {
        if (documentStartSupported) {
            runCatching {
                WebViewCompat.addDocumentStartJavaScript(webView, injectionScript, ALLOWED_ORIGINS)
            }
        }
    }

    private fun createWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val uri = request?.url ?: return true
                if (uri.scheme == null) return true
                if (uri.scheme == "about") return false
                if (uri.scheme != "https" && uri.scheme != "http") return true
                val host = uri.host.orEmpty()
                return !LinkExtractor.isDouyinHost(host)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                started.set(true)
                lastLoadedUrl = url.orEmpty()
                _events.tryEmit(ParserEvent.PageStarted(url.orEmpty()))
                if (!documentStartSupported && view != null && !url.isNullOrBlank() && url != "about:blank") {
                    view.evaluateJavascript(injectionScript, null)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (view == null) return
                if (!documentStartSupported) {
                    view.evaluateJavascript(injectionScript, null)
                }
                view.evaluateJavascript(
                    "(function(){try{return (window.__DK_getState ? window.__DK_getState() : '{}');}catch(e){return '{}';}})()",
                ) { raw ->
                    val state = parsePageState(decodeJsString(raw))
                    lastPageState = state
                    _events.tryEmit(ParserEvent.PageReady(state))
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame != true) return
                val code = error?.errorCode ?: -1
                if (code == WebViewClient.ERROR_UNKNOWN) return
                _events.tryEmit(
                    ParserEvent.PageError(
                        ErrorCode.PAGE_LOAD_FAILED,
                        "页面加载失败（${error?.description ?: "未知错误"}）",
                    ),
                )
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame != true) return
                val status = errorResponse?.statusCode ?: 0
                if (status >= 400) {
                    _events.tryEmit(
                        ParserEvent.Diagnostic(
                            "main_frame_http_error",
                            JsonObject(
                                mapOf(
                                    "status" to kotlinx.serialization.json.JsonPrimitive(status),
                                    "host" to kotlinx.serialization.json.JsonPrimitive(
                                        request.url.host.orEmpty(),
                                    ),
                                ),
                            ),
                        ),
                    )
                }
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?,
            ): Boolean {
                _events.tryEmit(
                    ParserEvent.Fatal(
                        ErrorCode.WEBVIEW_UNAVAILABLE,
                        "网页渲染进程已退出，请重试",
                    ),
                )
                return true
            }

            @androidx.annotation.RequiresApi(Build.VERSION_CODES.O_MR1)
            override fun onSafeBrowsingHit(
                view: WebView?,
                request: WebResourceRequest?,
                threatType: Int,
                callback: SafeBrowsingResponse?,
            ) {
                callback?.backToSafety(true)
            }
        }
    }

    private fun createChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                _events.tryEmit(ParserEvent.Progress(newProgress))
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.deny()
            }

            override fun onConsoleMessage(
                consoleMessage: android.webkit.ConsoleMessage?,
            ): Boolean {
                if (BuildConfig.DEBUG) {
                    android.util.Log.d(
                        "DouyinWebView",
                        consoleMessage?.message().orEmpty(),
                    )
                }
                return true
            }
        }
    }

    private fun handleRawMessage(raw: String) {
        if (raw.length > MAX_BRIDGE_CHARS) return
        mainHandler.post {
            runCatching {
                val root = JSONObject(raw)
                val type = root.optString("type")
                val payload = root.optJSONObject("payload") ?: return@runCatching
                when (type) {
                    "detail", "page" -> {
                        val detailJson = payload.optJSONObject("detail") ?: return@runCatching
                        val awemeId = payload.optString("awemeId")
                            .ifBlank { detailJson.optString("aweme_id") }
                            .ifBlank { detailJson.optString("awemeId") }
                        if (!AWE_ID_REGEX.matches(awemeId)) return@runCatching
                        val obj = json.parseToJsonElement(detailJson.toString()).jsonObject
                        val capture = DetailCapture(
                            awemeId = awemeId,
                            detail = obj,
                            url = payload.optString("url"),
                            bytes = payload.optInt("bytes"),
                            source = payload.optString("source", type),
                            receivedAt = System.currentTimeMillis(),
                        )
                        if (type == "detail") {
                            detailCache[awemeId] = capture
                            _events.tryEmit(ParserEvent.Detail(capture))
                        } else {
                            pageModelCache[awemeId] = capture
                            _events.tryEmit(ParserEvent.PageModel(capture))
                        }
                    }

                    "diag" -> {
                        val event = payload.optString("event", "diag")
                        _events.tryEmit(ParserEvent.Diagnostic(event))
                    }
                }
            }
        }
    }

    private fun parsePageState(raw: String?): PageState {
        if (raw.isNullOrBlank() || raw == "null") return PageState()
        return runCatching {
            val obj = JSONObject(raw)
            PageState(
                href = obj.optString("href"),
                title = obj.optString("title"),
                readyState = obj.optString("readyState"),
                hook = obj.optBoolean("hook"),
                hasRoot = obj.optBoolean("hasRoot"),
                bodyLength = obj.optInt("bodyLength"),
                text = obj.optString("text"),
                detailIds = buildList {
                    obj.optJSONArray("detailIds")?.let { array ->
                        for (i in 0 until array.length()) add(array.optString(i))
                    }
                },
                bridge = obj.optString("bridge"),
            )
        }.getOrElse { PageState() }
    }

    private fun decodeJsString(raw: String?): String? {
        if (raw == null || raw == "null" || raw == "undefined") return null
        return runCatching {
            val value = JSONTokener(raw).nextValue()
            value as? String ?: raw
        }.getOrElse { raw.trim('"') }
    }

    private inner class JsBridge {
        @JavascriptInterface
        fun postMessage(json: String) {
            val url = webView.url
            val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
            if (!LinkExtractor.isDouyinHost(host) && !url.isNullOrBlank() && !url.startsWith("about:")) {
                return
            }
            handleRawMessage(json)
        }
    }

    private companion object {
        const val BRIDGE_JS_NAME = "DouyinKitBridge"
        const val BRIDGE_ANDROID_NAME = "DouyinKitAndroid"
        const val MAX_BRIDGE_CHARS = 500_000
        val AWE_ID_REGEX = Regex("""^\d{8,25}$""")
        val ALLOWED_ORIGINS = setOf(
            "https://douyin.com",
            "https://*.douyin.com",
            "https://www.douyin.com",
            "https://iesdouyin.com",
            "https://*.iesdouyin.com",
            "https://www.iesdouyin.com",
        )
    }
}
