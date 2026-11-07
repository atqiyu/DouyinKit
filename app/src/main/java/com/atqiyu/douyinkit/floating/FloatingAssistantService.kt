package com.atqiyu.douyinkit.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.atqiyu.douyinkit.MainActivity
import com.atqiyu.douyinkit.R
import com.atqiyu.douyinkit.core.util.LinkExtractor
import com.atqiyu.douyinkit.data.prefs.FloatingMode
import com.atqiyu.douyinkit.data.prefs.FloatingQueueMode
import com.atqiyu.douyinkit.di.appContainer
import com.atqiyu.douyinkit.parser.ParseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the floating orb, the background parse queue and clipboard
 * monitoring. It uses the specialUse FGS type because Android 14+ requires a declared subtype
 * for a persistent overlay service. The JobScheduler watchdog is scheduled alongside it.
 */
class FloatingAssistantService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private var overlay: FloatingOverlayController? = null
    private var queue: FloatingParseQueue? = null
    private var stateJob: Job? = null
    private var settingsJob: Job? = null
    private var clipboardManager: ClipboardManager? = null
    private var automationJob: Job? = null
    private var recordCountJob: Job? = null
    private val shareLinkRepository by lazy {
        applicationContext.appContainer.shareLinkRepository
    }
    private var automationOwnsClipboard = false
    private var lastClipboardLink: String? = null
    private var lastClipboardAt = 0L
    private var lastNotificationAt = 0L
    private var lastNotificationText = ""
    private var foregroundStarted = false
    private var userStopping = false
    private val focusReader by lazy { ClipboardFocusReader(applicationContext) }

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!automationOwnsClipboard) onClipboardChanged()
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat(buildNotification("Kit 浮球运行中", "等待抖音作品链接"))
        FloatingAssistantBus.setEnabled(true)
        FloatingAssistantBus.setOverlayReady(FloatingPermissions.canDrawOverlays(this))
        if (!FloatingPermissions.canDrawOverlays(this)) {
            FloatingAssistantBus.update {
                it.copy(
                    message = "需要悬浮窗权限才能显示浮球",
                    expanded = true,
                )
            }
        }

        val newOverlay = FloatingOverlayController(
            context = applicationContext,
            onPrimaryAction = ::handlePrimaryAction,
            onAutoAction = ::triggerSuperAction,
            onOpenInput = { prefill -> overlay?.showInput(prefill) },
            onOpenApp = ::openApp,
            onExpandedChanged = { expanded ->
                FloatingAssistantBus.setExpanded(expanded)
            },
            onStop = ::stopCurrent,
            onInputSubmit = ::handleInputSubmit,
            onInputCancel = {},
            onOrbPositionChanged = { x, y ->
                scope.launch {
                    runCatching {
                        applicationContext.appContainer.settingsRepository
                            .setFloatingOrbPosition(x, y)
                    }
                }
            },
            onPanelGeometryChanged = { x, y, width, height ->
                scope.launch {
                    runCatching {
                        applicationContext.appContainer.settingsRepository
                            .setFloatingPanelGeometry(x, y, width, height)
                    }
                }
            },
            onOrbLongPress = {
                if (!FloatingAssistantBus.state.value.accessibilityReady) {
                    FloatingPermissions.openAccessibilitySettings(this)
                    FloatingAssistantBus.update {
                        it.copy(
                            expanded = true,
                            message = "请开启 Kit 浮球无障碍服务",
                        )
                    }
                } else {
                    FloatingAssistantBus.update {
                        it.copy(message = "无障碍服务已开启")
                    }
                }
            },
        )
        overlay = newOverlay
        queue = FloatingParseQueue(applicationContext, scope)
        // The orb is shown by the first render() after settings (including its saved
        // position) have been loaded, so it does not jump after the service starts.
        FloatingAssistantBus.setOverlayReady(false)

        stateJob = scope.launch {
            FloatingAssistantBus.state.collect { state ->
                overlay?.render(state)
                FloatingAssistantBus.setOverlayReady(overlay?.isOrbShown() == true)
                if (state.enabled) updateNotification(state)
            }
        }
        settingsJob = scope.launch {
            applicationContext.appContainer.settingsRepository.settings.collect { settings ->
                FloatingAssistantBus.setMode(settings.floatingMode)
                FloatingAssistantBus.setQueueMode(settings.floatingQueueMode)
                FloatingAssistantBus.update {
                    it.copy(
                        settingsLoaded = true,
                        hidePanel = settings.floatingHidePanel,
                        orbSizeDp = settings.floatingOrbSizeDp,
                        orbAlpha = settings.floatingOrbAlpha,
                        panelOpacity = settings.floatingPanelOpacity,
                        panelStyle = settings.floatingPanelStyle,
                        panelColorArgb = settings.floatingPanelColor.argb,
                        panelBlur = settings.floatingPanelBlur,
                        showClipboard = settings.floatingShowClipboard,
                        tapShowsPanel = settings.floatingTapShowsPanel,
                        actionDistanceDp = settings.floatingActionDistanceDp,
                        actionAngleOffset = settings.floatingActionAngleOffset,
                        panelMinWidthDp = settings.floatingPanelMinWidthDp,
                        panelMinHeightDp = settings.floatingPanelMinHeightDp,
                        idleTimeoutSeconds = settings.floatingIdleTimeoutSeconds,
                        panelPaddingLeftDp = settings.floatingPanelPaddingLeftDp,
                        panelPaddingTopDp = settings.floatingPanelPaddingTopDp,
                        panelPaddingRightDp = settings.floatingPanelPaddingRightDp,
                        panelPaddingBottomDp = settings.floatingPanelPaddingBottomDp,
                        panelDragHandleTopDp = settings.floatingPanelDragHandleTopDp,
                        orbXFraction = settings.floatingOrbXFraction,
                        orbYFraction = settings.floatingOrbYFraction,
                        panelXFraction = settings.floatingPanelXFraction,
                        panelYFraction = settings.floatingPanelYFraction,
                        panelWidthDp = settings.floatingPanelWidthDp,
                        panelHeightDp = settings.floatingPanelHeightDp,
                    )
                }
            }
        }
        recordCountJob = scope.launch {
            applicationContext.appContainer.settingsRepository.settings
                .map { it.floatingQueueMode }
                .distinctUntilChanged()
                .collectLatest { mode ->
                    if (mode == FloatingQueueMode.RECORD_ONLY) {
                        shareLinkRepository.observePendingCount().collect { count ->
                            FloatingAssistantBus.setQueueSize(count)
                        }
                    } else {
                        FloatingAssistantBus.setQueueSize(queue?.currentQueueSize() ?: 0)
                    }
                }
        }

        registerClipboard()
        scope.launch {
            FloatingAssistantBus.copySignals.collect {
                if (automationOwnsClipboard) return@collect
                scope.launch {
                    val text = focusReader.read()
                    val link = text?.let { LinkExtractor.extract(it).firstOrNull()?.url }
                    if (link != null) handleClipboardLink(link)
                }
            }
        }
        FloatingAssistantScheduler.schedule(this)
        scope.launch {
            delay(500)
            readInitialClipboard()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopFromUser()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_ENQUEUE_BATCH) {
            if (!foregroundStarted) {
                startForegroundCompat(buildNotification("Kit 浮球运行中", "后台批量处理"))
            }
            val links = intent.getStringArrayListExtra(EXTRA_LINKS).orEmpty()
            links.forEach { queue?.enqueue(it) }
            return START_STICKY
        }
        if (!foregroundStarted) {
            startForegroundCompat(buildNotification("Kit 浮球运行中", "等待抖音作品链接"))
        }
        refreshOverlay()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        clipboardManager = null
        automationJob?.cancel()
        recordCountJob?.cancel()
        queue?.destroy()
        queue = null
        overlay?.dismiss()
        overlay = null
        stateJob?.cancel()
        settingsJob?.cancel()
        FloatingAssistantBus.setEnabled(false)
        FloatingAssistantBus.setOverlayReady(false)
        if (userStopping) FloatingAssistantScheduler.cancel(this)
        scope.cancel()
        super.onDestroy()
    }

    private fun handlePrimaryAction() {
        val state = FloatingAssistantBus.state.value
        when {
            state.needsVerification || state.needsLogin -> {
                queue?.showPage()
                FloatingAssistantBus.update {
                    it.copy(
                        expanded = true,
                        message = if (state.needsLogin) {
                            "请在页面中登录，完成后会自动继续"
                        } else {
                            "请在页面中完成安全验证"
                        },
                    )
                }
            }

            state.phase == FloatingPhase.PARSING ||
                state.phase == FloatingPhase.WAITING ||
                state.phase == FloatingPhase.DOWNLOADING ||
                state.automationBusy -> Unit

            state.promptVisible && !state.clipboardLink.isNullOrBlank() -> {
                parseClipboardLink()
            }

            state.resultReady -> openApp()

            state.mode == FloatingMode.AUTO && !state.accessibilityReady -> {
                FloatingAssistantBus.update {
                    it.copy(message = "请在系统设置中开启 Kit 浮球无障碍服务")
                }
                FloatingPermissions.openAccessibilitySettings(this)
            }

            state.mode == FloatingMode.AUTO -> {
                triggerSuperAction()
            }

            else -> openApp()
        }
    }

    private fun parseClipboardLink() {
        val link = FloatingAssistantBus.state.value.clipboardLink
            ?: readClipboardText()?.let { LinkExtractor.extract(it).firstOrNull()?.url }
        if (link == null) {
            FloatingAssistantBus.update { it.copy(message = "剪贴板里没有识别到抖音链接") }
            return
        }
        FloatingAssistantBus.setPromptVisible(false)
        if (FloatingAssistantBus.state.value.mode == FloatingMode.AUTO) {
            handleAutoLink(link)
        } else {
            openParseInApp(listOf(link))
        }
    }

    private fun handleInputSubmit(raw: String) {
        val links = LinkExtractor.extract(raw).map { it.url }.distinct()
        if (links.isEmpty()) {
            FloatingAssistantBus.update { it.copy(message = "没有识别到抖音作品链接") }
            return
        }
        if (FloatingAssistantBus.state.value.mode == FloatingMode.AUTO) {
            links.forEach { handleAutoLink(it) }
            FloatingAssistantBus.update { it.copy(expanded = true) }
        } else {
            openParseInApp(links)
        }
    }

    private fun triggerSuperAction() {
        if (automationJob?.isActive == true) return
        val state = FloatingAssistantBus.state.value
        if (state.mode != FloatingMode.AUTO) return
        if (!state.accessibilityReady) {
            FloatingAssistantBus.update {
                it.copy(message = "请先开启 Kit 浮球无障碍服务")
            }
            FloatingPermissions.openAccessibilitySettings(this)
            return
        }
        if (state.appForeground) {
            FloatingAssistantBus.update { it.copy(message = "请先在抖音或抖音极速版中打开一个作品") }
            return
        }
        val service = DouyinAccessibilityService.instance
        if (service == null) {
            FloatingAssistantBus.update { it.copy(message = "无障碍服务尚未连接，请稍后重试") }
            return
        }
        automationJob = scope.launch {
            Log.d(TAG, "super action starting")
            val (previousText, previousStamp) = readClipboardSnapshot()
            val previousLink = previousText
                ?.let { LinkExtractor.extract(it).firstOrNull()?.url }
            automationOwnsClipboard = true
            FloatingAssistantBus.setAutomationBusy(true)
            FloatingAssistantBus.update {
                it.copy(
                    expanded = true,
                    phase = FloatingPhase.WAITING,
                    message = "正在打开抖音分享面板…",
                    progress = null,
                )
            }
            try {
                when (val result = service.autoShareAndCopy()) {
                    is DouyinAccessibilityService.AutomationResult.Success -> {
                        Log.d(TAG, "share automation succeeded, waiting for clipboard")
                        val link = awaitClipboardLink(previousLink, previousStamp)
                        if (link != null) {
                            FloatingAssistantBus.setClipboardLink(link)
                            handleAutoLink(link)
                        } else {
                            FloatingAssistantBus.update {
                                it.copy(
                                    phase = FloatingPhase.ERROR,
                                    message = if (previousLink != null) {
                                        "剪贴板仍是上一个作品，本次没有加入队列，请重试"
                                    } else {
                                        "已点击分享链接，但没有读取到剪贴板；请手动打开“解析链接”"
                                    },
                                )
                            }
                        }
                    }

                    is DouyinAccessibilityService.AutomationResult.Failure -> {
                        FloatingAssistantBus.update {
                            it.copy(
                                phase = FloatingPhase.ERROR,
                                message = result.message,
                            )
                        }
                    }
                }
            } catch (error: Exception) {
                Log.w(TAG, "super action failed", error)
                FloatingAssistantBus.update {
                    it.copy(
                        phase = FloatingPhase.ERROR,
                        message = error.message ?: "自动点击失败",
                    )
                }
            } finally {
                automationOwnsClipboard = false
                FloatingAssistantBus.setAutomationBusy(false)
            }
        }
    }

    private suspend fun awaitClipboardLink(previousLink: String?, previousStamp: Long): String? {
        repeat(8) {
            val (text, stamp) = readClipboardSnapshot()
            val link = text?.let { LinkExtractor.extract(it).firstOrNull()?.url }
            val changed = stamp > previousStamp ||
                (previousStamp == 0L && link != null && link != previousLink)
            if (link != null && changed) return link
            delay(250)
        }
        val focusedText = focusReader.read()
        val focusedLink = focusedText?.let { LinkExtractor.extract(it).firstOrNull()?.url }
        return focusedLink?.takeIf { it != previousLink }
    }

    private fun readClipboardSnapshot(): Pair<String?, Long> {
        return runCatching {
            val manager = clipboardManager ?: return null to 0L
            val clip = manager.primaryClip ?: return null to 0L
            if (clip.itemCount == 0) return null to 0L
            val text = clip.getItemAt(0)?.coerceToText(this)?.toString()
            val stamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.primaryClipDescription?.timestamp ?: 0L
            } else {
                0L
            }
            text to stamp
        }.onFailure { error ->
            Log.w(TAG, "clipboard snapshot failed: ${error.javaClass.simpleName}")
        }.getOrDefault(null to 0L)
    }

    private fun onClipboardChanged() {
        val text = readClipboardText()
        if (text == null) {
            if (!FloatingAssistantBus.state.value.foreground.isDouyin) {
                Log.d(TAG, "clipboard event unavailable outside Douyin")
                return
            }
            Log.d(TAG, "clipboard event but text was unavailable; trying focus read")
            scope.launch {
                val focused = focusReader.read()
                val link = focused?.let { LinkExtractor.extract(it).firstOrNull()?.url }
                if (link != null) handleClipboardLink(link)
            }
            return
        }
        val link = LinkExtractor.extract(text).firstOrNull()?.url ?: return
        handleClipboardLink(link)
    }

    private fun handleClipboardLink(link: String) {
        val now = System.currentTimeMillis()
        if (link == lastClipboardLink && now - lastClipboardAt < 5_000) return
        lastClipboardLink = link
        lastClipboardAt = now
        FloatingAssistantBus.setClipboardLink(link)
        val state = FloatingAssistantBus.state.value
        if (state.mode == FloatingMode.AUTO && state.foreground.isDouyin) {
            handleAutoLink(link)
        } else {
            FloatingAssistantBus.update {
                it.copy(
                    promptVisible = true,
                    expanded = true,
                    message = if (state.mode == FloatingMode.AUTO) {
                        "检测到抖音链接，点击即可加入连播"
                    } else {
                        "检测到抖音链接，点击解析"
                    },
                )
            }
        }
    }

    private fun handleAutoLink(link: String) {
        val queueMode = FloatingAssistantBus.state.value.queueMode
        if (queueMode == FloatingQueueMode.RECORD_ONLY) {
            scope.launch {
                val url = runCatching { shareLinkRepository.record(link) }.getOrNull()
                if (url == null) {
                    FloatingAssistantBus.update {
                        it.copy(message = "没有识别到可记录的抖音链接")
                    }
                    return@launch
                }
                FloatingAssistantBus.setPromptVisible(false)
                FloatingAssistantBus.update {
                    it.copy(
                        phase = FloatingPhase.IDLE,
                        title = null,
                        message = "已记录分享链接，可在资料库批量处理",
                        detail = url,
                        progress = null,
                        resultReady = false,
                        expanded = true,
                        needsVerification = false,
                        needsLogin = false,
                    )
                }
            }
            return
        }
        // Record first so a very fast parse cannot finish before the row exists.
        scope.launch {
            runCatching { shareLinkRepository.record(link) }
            queue?.enqueue(link)
        }
    }

    private fun readInitialClipboard() {
        val text = readClipboardText() ?: return
        val link = LinkExtractor.extract(text).firstOrNull()?.url ?: return
        lastClipboardLink = link
        lastClipboardAt = System.currentTimeMillis()
        FloatingAssistantBus.setClipboardLink(link)
        FloatingAssistantBus.update {
            it.copy(
                promptVisible = true,
                expanded = false,
                message = if (it.mode == FloatingMode.AUTO) {
                    "检测到剪贴板链接，点开浮球可加入连播"
                } else {
                    "检测到剪贴板链接，点开浮球即可解析"
                },
            )
        }
    }

    private fun readClipboardText(): String? {
        return runCatching {
            val manager = clipboardManager ?: return null
            val clip = manager.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            clip.getItemAt(0)?.coerceToText(this)?.toString()
        }.onFailure { error ->
            Log.w(TAG, "clipboard read failed: ${error.javaClass.simpleName}")
        }.getOrNull()
    }

    private fun openParseInApp(links: List<String>) {
        val intent = ParseActivity.intent(
            context = this,
            inputs = links,
            openResultInApp = true,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
            .onFailure {
                FloatingAssistantBus.update {
                    it.copy(message = "无法打开解析页，请打开 DouyinKit 后重试")
                }
            }
        FloatingAssistantBus.setPromptVisible(false)
        FloatingAssistantBus.setExpanded(false)
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { startActivity(intent) }
    }

    private fun stopCurrent() {
        automationJob?.cancel()
        automationJob = null
        automationOwnsClipboard = false
        queue?.stopAll()
        FloatingAssistantBus.setAutomationBusy(false)
    }

    private fun stopFromUser() {
        userStopping = true
        scope.launch {
            runCatching {
                applicationContext.appContainer.settingsRepository.setFloatingBallEnabled(false)
            }
            FloatingAssistantScheduler.cancel(this@FloatingAssistantService)
            overlay?.dismiss()
            queue?.stopAll()
            FloatingAssistantBus.setEnabled(false)
            FloatingAssistantBus.resetTask("浮球已关闭")
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun refreshOverlay() {
        val controller = overlay ?: return
        // Wait for the settings snapshot so the orb is created directly at its saved
        // position instead of jumping from the default position a moment later.
        if (!FloatingAssistantBus.state.value.settingsLoaded) return
        if (FloatingPermissions.canDrawOverlays(this)) {
            val shown = controller.showOrb()
            FloatingAssistantBus.setOverlayReady(shown)
        } else {
            FloatingAssistantBus.setOverlayReady(false)
        }
    }

    private fun registerClipboard() {
        runCatching {
            clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
        }.onFailure { Log.w(TAG, "register clipboard failed", it) }
    }

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        foregroundStarted = true
    }

    private fun stopForegroundCompat() {
        if (!foregroundStarted) return
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Kit 浮球",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "浮球运行状态、解析与自动下载进度"
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(title: String, text: String, progress: Int? = null): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            7413,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            7414,
            Intent(this, FloatingAssistantService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (progress != null) {
                    setProgress(100, progress.coerceIn(0, 100), false)
                }
            }
            .addAction(0, "关闭浮球", stopIntent)
            .build()
    }

    private fun updateNotification(state: FloatingUiState) {
        val now = System.currentTimeMillis()
        val title = when (state.phase) {
            FloatingPhase.PARSING -> "正在解析作品"
            FloatingPhase.VERIFYING -> "等待页面操作"
            FloatingPhase.DOWNLOADING -> "正在自动下载"
            FloatingPhase.ERROR -> "Kit 浮球遇到问题"
            FloatingPhase.DONE -> "Kit 浮球已完成"
            else -> "Kit 浮球运行中"
        }
        val text = buildString {
            append(state.foreground.label)
            if (state.queueSize > 0) append(" · 队列 ${state.queueSize}")
            val message = state.title ?: state.message
            if (message.isNotBlank()) append(" · ${message.take(48)}")
        }
        val progress = state.progress?.let { (it * 100).toInt() }
        if (now - lastNotificationAt < 700 && text == lastNotificationText) return
        lastNotificationAt = now
        lastNotificationText = text
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        runCatching {
            notificationManager.notify(
                NOTIFICATION_ID,
                buildNotification(title, text, progress),
            )
        }
    }

    companion object {
        private const val TAG = "FloatingAssistant"
        private const val CHANNEL_ID = "floating_assistant"
        private const val NOTIFICATION_ID = 7410
        const val ACTION_START = "com.atqiyu.douyinkit.floating.START"
        const val ACTION_STOP = "com.atqiyu.douyinkit.floating.STOP"
        const val ACTION_ENQUEUE_BATCH = "com.atqiyu.douyinkit.floating.ENQUEUE_BATCH"
        const val EXTRA_LINKS = "extra_links"

        fun start(context: Context) {
            val intent = Intent(context, FloatingAssistantService::class.java)
                .setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun enqueueBatch(context: Context, links: List<String>) {
            if (links.isEmpty()) return
            val intent = Intent(context, FloatingAssistantService::class.java).apply {
                action = ACTION_ENQUEUE_BATCH
                putStringArrayListExtra(EXTRA_LINKS, ArrayList(links))
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingAssistantService::class.java))
        }
    }
}
