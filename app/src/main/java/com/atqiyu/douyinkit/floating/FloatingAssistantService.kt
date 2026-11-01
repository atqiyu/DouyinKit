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
import com.atqiyu.douyinkit.di.appContainer
import com.atqiyu.douyinkit.parser.ParseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
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
        runCatching {
            DouyinAccessibilityService.instance?.rootInActiveWindow?.packageName
                ?.toString()
                ?.let(FloatingAssistantBus::setForeground)
        }
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
                if (expanded) refreshForeground()
                FloatingAssistantBus.setExpanded(expanded)
            },
            onStop = ::stopCurrent,
            onInputSubmit = ::handleInputSubmit,
            onInputCancel = {},
        )
        overlay = newOverlay
        queue = FloatingParseQueue(applicationContext, scope)
        if (FloatingPermissions.canDrawOverlays(this) && newOverlay.showOrb()) {
            FloatingAssistantBus.setOverlayReady(true)
        } else {
            FloatingAssistantBus.setOverlayReady(false)
        }

        stateJob = scope.launch {
            FloatingAssistantBus.state.collect { state ->
                overlay?.render(state)
                if (state.enabled) updateNotification(state)
            }
        }
        settingsJob = scope.launch {
            applicationContext.appContainer.settingsRepository.settings.collect { settings ->
                FloatingAssistantBus.setMode(settings.floatingMode)
                FloatingAssistantBus.setAutoDownload(settings.floatingAutoDownload)
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
        refreshForeground()
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
            queue?.enqueue(link)
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
            links.forEach { queue?.enqueue(it) }
            FloatingAssistantBus.update { it.copy(expanded = true) }
        } else {
            openParseInApp(links)
        }
    }

    private fun triggerSuperAction() {
        if (automationJob?.isActive == true) return
        refreshForeground()
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
                        val link = awaitClipboardLink()
                        if (link != null) {
                            FloatingAssistantBus.setClipboardLink(link)
                            queue?.enqueue(link)
                        } else {
                            FloatingAssistantBus.update {
                                it.copy(
                                    phase = FloatingPhase.ERROR,
                                    message = "已点击分享链接，但没有读取到剪贴板；请手动打开“解析链接”",
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

    private suspend fun awaitClipboardLink(): String? {
        repeat(5) {
            val text = readClipboardText()
            val link = text?.let { LinkExtractor.extract(it).firstOrNull()?.url }
            if (link != null) return link
            delay(200)
        }
        val focusedText = focusReader.read()
        return focusedText?.let { LinkExtractor.extract(it).firstOrNull()?.url }
    }

    private fun refreshForeground() {
        if (FloatingAssistantBus.state.value.appForeground) {
            FloatingAssistantBus.setForeground(packageName)
            return
        }
        val rootPackage = runCatching {
            DouyinAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString()
        }.getOrNull()
        if (!rootPackage.isNullOrBlank() && rootPackage != packageName) {
            Log.d(TAG, "foreground root=$rootPackage")
            FloatingAssistantBus.setForeground(rootPackage)
        } else {
            Log.d(TAG, "foreground root unavailable")
        }
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
            queue?.enqueue(link)
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

        fun start(context: Context) {
            val intent = Intent(context, FloatingAssistantService::class.java)
                .setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingAssistantService::class.java))
        }
    }
}
