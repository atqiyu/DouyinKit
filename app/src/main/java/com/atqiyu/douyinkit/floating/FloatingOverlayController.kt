package com.atqiyu.douyinkit.floating

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.atqiyu.douyinkit.R
import com.atqiyu.douyinkit.core.util.LinkExtractor
import com.atqiyu.douyinkit.data.prefs.FloatingMode

class FloatingOverlayController(
    private val context: Context,
    private val onPrimaryAction: () -> Unit,
    private val onAutoAction: () -> Unit,
    private val onOpenInput: (String?) -> Unit,
    private val onOpenApp: () -> Unit,
    private val onExpandedChanged: (Boolean) -> Unit,
    private val onStop: () -> Unit,
    private val onInputSubmit: (String) -> Unit,
    private val onInputCancel: () -> Unit,
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density
    private val screenWidth = context.resources.displayMetrics.widthPixels
    private val screenHeight = context.resources.displayMetrics.heightPixels
    private val statusBar = statusBarHeight()
    private val navBar = navigationBarHeight()

    private val orbRoot: FrameLayout
    private val orbImage: ImageView
    private val statusDot: View
    private val panel: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var activeTitle: TextView
    private lateinit var messageText: TextView
    private lateinit var linkText: TextView
    private lateinit var queueText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var primaryButton: TextView
    private lateinit var inputButton: TextView
    private lateinit var autoButton: TextView
    private lateinit var stopButton: TextView
    private lateinit var closeButton: TextView
    private var inputWindow: View? = null

    private var orbParams: WindowManager.LayoutParams
    private var panelParams: WindowManager.LayoutParams
    private var orbAttached = false
    private var panelAttached = false
    private var inputAttached = false
    private var panelVisible = false
    private var lastPromptVisible = false
    private var lastRenderedState: FloatingUiState? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downRawX = 0f
    private var downRawY = 0f
    private var downOrbX = 0
    private var downOrbY = 0
    private var draggingOrb = false

    init {
        orbRoot = FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFFFE2C55.toInt(), 0xFF25F4EE.toInt()),
            ).apply { shape = GradientDrawable.OVAL }
            contentDescription = "Kit 浮球"
            isClickable = true
            isFocusable = true
            setPadding(dp(5), dp(5), dp(5), dp(5))
            setOnClickListener { togglePanel() }
        }
        orbImage = ImageView(context).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xF20F141C.toInt())
            }
        }
        orbRoot.addView(
            orbImage,
            FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER),
        )
        statusDot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF87909E.toInt())
                setStroke(dp(2), 0xE60F141C.toInt())
            }
        }
        orbRoot.addView(
            statusDot,
            FrameLayout.LayoutParams(dp(13), dp(13), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(2)
                marginEnd = dp(2)
            },
        )

        panel = buildPanel()
        statusText = panel.findViewWithTag("status") as TextView
        activeTitle = panel.findViewWithTag("activeTitle") as TextView
        messageText = panel.findViewWithTag("message") as TextView
        linkText = panel.findViewWithTag("link") as TextView
        queueText = panel.findViewWithTag("queue") as TextView
        progressBar = panel.findViewWithTag("progress") as ProgressBar
        primaryButton = panel.findViewWithTag("primary") as TextView
        inputButton = panel.findViewWithTag("input") as TextView
        autoButton = panel.findViewWithTag("auto") as TextView
        stopButton = panel.findViewWithTag("stop") as TextView
        closeButton = panel.findViewWithTag("close") as TextView

        orbParams = orbLayoutParams()
        panelParams = panelLayoutParams()
        orbRoot.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downOrbX = orbParams.x
                    downOrbY = orbParams.y
                    draggingOrb = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!draggingOrb && (kotlin.math.abs(dx) > touchSlop ||
                            kotlin.math.abs(dy) > touchSlop)
                    ) {
                        draggingOrb = true
                    }
                    if (draggingOrb) {
                        orbParams.x = (downOrbX + dx.toInt()).coerceIn(
                            dp(2),
                            screenWidth - orbParams.width - dp(2),
                        )
                        orbParams.y = (downOrbY + dy.toInt()).coerceIn(
                            statusBar + dp(2),
                            screenHeight - navBar - orbParams.height - dp(2),
                        )
                        runCatching { windowManager.updateViewLayout(orbRoot, orbParams) }
                        if (panelVisible) updatePanelPosition()
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!draggingOrb) orbRoot.performClick()
                    draggingOrb = false
                    true
                }

                else -> false
            }
        }
    }

    fun showOrb(): Boolean {
        if (orbAttached) return true
        return runCatching {
            orbRoot.alpha = 0f
            orbRoot.scaleX = 0.65f
            orbRoot.scaleY = 0.65f
            windowManager.addView(orbRoot, orbParams)
            orbAttached = true
            orbRoot.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220L)
                .start()
            true
        }.getOrDefault(false)
    }

    fun hideOrb() {
        if (!orbAttached) return
        hidePanel()
        runCatching { windowManager.removeViewImmediate(orbRoot) }
        orbAttached = false
    }

    fun render(state: FloatingUiState) {
        lastRenderedState = state

        if (!orbAttached && state.enabled) showOrb()
        if (!state.enabled) {
            hideOrb()
            return
        }

        val mode = when (state.mode) {
            FloatingMode.STANDARD -> "轻享模式"
            FloatingMode.AUTO -> "连播模式"
        }
        val foregroundText = state.foreground.label
        statusText.text = when {
            !state.overlayReady -> "需要悬浮窗权限"
            !state.accessibilityReady && state.mode == FloatingMode.AUTO ->
                "无障碍未开启 · 连播模式不可用"

            else -> "$foregroundText · $mode"
        }
        statusText.setTextColor(
            when {
                !state.overlayReady -> 0xFFFFB4AB.toInt()
                state.foreground.isDouyin -> 0xFF8BE9C1.toInt()
                else -> 0xFFB8C0CC.toInt()
            },
        )

        activeTitle.text = state.title.orEmpty()
        activeTitle.visibility = if (state.title.isNullOrBlank()) View.GONE else View.VISIBLE
        messageText.text = state.message
        messageText.visibility = if (state.message.isBlank()) View.GONE else View.VISIBLE

        val link = state.clipboardLink
        linkText.visibility = if (link.isNullOrBlank()) View.GONE else View.VISIBLE
        linkText.text = link?.let { "剪贴板  ${it.take(64)}" }.orEmpty()

        val queue = state.queueSize
        queueText.visibility = if (queue > 0 || state.resultReady) View.VISIBLE else View.GONE
        queueText.text = when {
            queue > 1 -> "等待队列 · $queue 个作品"
            queue == 1 -> "等待队列 · 1 个作品"
            state.resultReady -> "解析完成 · 结果已保存"
            else -> ""
        }

        val focused = state.phase == FloatingPhase.PARSING ||
            state.phase == FloatingPhase.WAITING ||
            state.phase == FloatingPhase.VERIFYING ||
            state.phase == FloatingPhase.DOWNLOADING
        progressBar.visibility = if (state.progress != null || focused) View.VISIBLE else View.GONE
        if (state.progress != null) {
            progressBar.isIndeterminate = false
            progressBar.progress = (state.progress.coerceIn(0f, 1f) * 1000).toInt()
        } else {
            progressBar.isIndeterminate = focused
        }

        val primary = when {
            state.needsVerification -> "打开验证页面"
            state.needsLogin -> "打开登录页面"
            state.automationBusy -> "正在点击分享…"
            state.phase == FloatingPhase.PARSING ||
                state.phase == FloatingPhase.WAITING ||
                state.phase == FloatingPhase.DOWNLOADING -> "正在处理…"
            state.promptVisible &&
                !state.clipboardLink.isNullOrBlank() -> "解析剪贴板链接"
            state.resultReady -> "打开 DouyinKit 查看结果"
            state.mode == FloatingMode.AUTO &&
                !state.accessibilityReady -> "开启无障碍服务"
            state.mode == FloatingMode.AUTO -> "分析当前视频"
            else -> "打开 DouyinKit"
        }
        primaryButton.text = primary
        val primaryEnabled = when {
            state.automationBusy -> false
            state.phase == FloatingPhase.PARSING ||
                state.phase == FloatingPhase.WAITING ||
                state.phase == FloatingPhase.DOWNLOADING -> false
            state.promptVisible && !state.clipboardLink.isNullOrBlank() -> true
            state.resultReady -> true
            state.mode == FloatingMode.AUTO -> state.accessibilityReady && !state.appForeground
            else -> true
        }
        primaryButton.isEnabled = primaryEnabled
        primaryButton.alpha = if (primaryEnabled) 1f else 0.45f

        inputButton.visibility = View.VISIBLE
        inputButton.text = "输入链接"
        autoButton.visibility = if (state.mode == FloatingMode.AUTO) View.VISIBLE else View.GONE
        autoButton.isEnabled = state.accessibilityReady && !state.automationBusy &&
            !state.appForeground
        autoButton.alpha = if (autoButton.isEnabled) 1f else 0.45f
        autoButton.text = when {
            state.automationBusy -> "正在分享…"
            state.phase == FloatingPhase.PARSING ||
                state.phase == FloatingPhase.WAITING ||
                state.phase == FloatingPhase.DOWNLOADING -> "分析下一个"
            else -> "分析当前视频"
        }
        stopButton.visibility = if (
            state.phase == FloatingPhase.PARSING ||
            state.phase == FloatingPhase.WAITING ||
            state.phase == FloatingPhase.DOWNLOADING ||
            state.automationBusy
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }

        statusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                when {
                    state.needsVerification || state.needsLogin -> 0xFFFFB020.toInt()
                    state.promptVisible && !state.clipboardLink.isNullOrBlank() -> 0xFFFE2C55.toInt()
                    focused -> 0xFF25F4EE.toInt()
                    state.foreground.isDouyin -> 0xFF39D98A.toInt()
                    else -> 0xFF87909E.toInt()
                },
            )
            setStroke(dp(2), 0xE60F141C.toInt())
        }

        if (state.expanded && !panelVisible) showPanel()
        if (!state.expanded && panelVisible) hidePanel()

        if (state.promptVisible && !lastPromptVisible) pulseOrb()
        lastPromptVisible = state.promptVisible

        if (panelVisible) {
            panel.post { updatePanelPosition() }
        }
    }

    fun showPanel() {
        if (!orbAttached) showOrb()
        if (panelVisible) return
        if (!panelAttached) {
            runCatching { windowManager.addView(panel, panelParams) }
                .onSuccess { panelAttached = true }
                .onFailure { return }
        }
        panelVisible = true
        panel.animate().cancel()
        panel.visibility = View.VISIBLE
        panel.alpha = 0f
        panel.scaleX = 0.92f
        panel.scaleY = 0.92f
        panel.post {
            updatePanelPosition()
            panel.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(190L)
                .start()
        }
        onExpandedChanged(true)
    }

    fun hidePanel() {
        if (!panelVisible) return
        panelVisible = false
        onExpandedChanged(false)
        if (!panelAttached) return
        panel.animate()
            .alpha(0f)
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(140L)
            .withEndAction {
                if (!panelVisible && panelAttached) {
                    runCatching { windowManager.removeViewImmediate(panel) }
                    panelAttached = false
                }
            }
            .start()
    }

    fun togglePanel() {
        if (panelVisible) hidePanel() else showPanel()
    }

    fun showInput(prefill: String?) {
        Log.d(TAG, "showInput attached=$inputAttached")
        if (inputAttached) return
        hidePanel()
        val root = FrameLayout(context).apply {
            setBackgroundColor(0x8C000000.toInt())
            isFocusableInTouchMode = true
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = panelBackground()
            elevation = dp(14).toFloat()
        }
        val title = TextView(context).apply {
            text = "解析链接"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        val hintText = TextView(context).apply {
            text = "粘贴抖音分享链接或文案"
            setTextColor(0xFFAFB6C2.toInt())
            textSize = 12f
            setPadding(0, dp(3), 0, dp(8))
        }
        val input = EditText(context).apply {
            this.hint = "https://v.douyin.com/…"
            setHintTextColor(0xFF7E8794.toInt())
            setTextColor(Color.WHITE)
            textSize = 14f
            minLines = 2
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(0xFF202733.toInt(), 0x33FFFFFF, 14)
            setText(prefill.orEmpty())
            setSelection(text.length)
        }
        val error = TextView(context).apply {
            setTextColor(0xFFFFB4AB.toInt())
            textSize = 12f
            visibility = View.GONE
            setPadding(0, dp(6), 0, 0)
        }
        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(12), 0, 0)
        }
        val cancel = textButton("取消", false)
        val submit = textButton("解析", true)
        cancel.setOnClickListener {
            hideInput()
            onInputCancel()
        }
        submit.setOnClickListener {
            val raw = input.text.toString().trim()
            val links = LinkExtractor.extract(raw)
            if (links.isEmpty()) {
                error.text = "没有识别到抖音作品链接"
                error.visibility = View.VISIBLE
                return@setOnClickListener
            }
            hideInput()
            onInputSubmit(raw)
        }
        buttons.addView(cancel, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginEnd = dp(8)
        })
        buttons.addView(submit, LinearLayout.LayoutParams(0, dp(44), 1f))
        card.addView(title)
        card.addView(hintText)
        card.addView(input)
        card.addView(error)
        card.addView(buttons)
        root.addView(
            card,
            FrameLayout.LayoutParams(dp(320), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
                leftMargin = dp(16)
                rightMargin = dp(16)
                bottomMargin = navBar + dp(16)
            },
        )
        root.setOnClickListener { /* consume outside taps */ }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            dimAmount = 0.42f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        }
        runCatching { windowManager.addView(root, params) }
            .onFailure {
                Log.w(TAG, "add input window failed", it)
                return
            }
        inputAttached = true
        inputWindow = root
        input.requestFocus()
        input.postDelayed({
            input.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 120L)
    }

    fun hideInput() {
        val root = inputWindow ?: return
        inputWindow = null
        inputAttached = false
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(root.windowToken, 0)
        runCatching { windowManager.removeViewImmediate(root) }
    }

    fun dismiss() {
        hideInput()
        hidePanel()
        hideOrb()
    }

    private fun buildPanel(): LinearLayout {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = panelBackground()
            elevation = dp(16).toFloat()
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(context).apply {
            text = "Kit 浮球"
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
        }
        val mode = TextView(context).apply {
            text = "本地"
            setTextColor(0xFF9BE9F1.toInt())
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = roundedBackground(0x3325F4EE, 0x5525F4EE, 10)
        }
        closeButton = TextView(context).apply {
            text = "×"
            setTextColor(0xFFB8C0CC.toInt())
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(4), 0)
            contentDescription = "收起浮球面板"
            setOnClickListener { hidePanel() }
            tag = "close"
        }
        header.addView(title)
        header.addView(
            mode,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(24)).apply {
                marginStart = dp(8)
            },
        )
        header.addView(
            View(context),
            LinearLayout.LayoutParams(0, 1, 1f),
        )
        header.addView(closeButton)
        root.addView(header)

        val statusRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        val dot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF87909E.toInt())
            }
        }
        statusRow.addView(dot, LinearLayout.LayoutParams(dp(8), dp(8)))
        statusText = TextView(context).apply {
            setTextColor(0xFFB8C0CC.toInt())
            textSize = 12f
            setPadding(dp(7), 0, 0, 0)
            tag = "status"
        }
        statusRow.addView(
            statusText,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        root.addView(statusRow)

        activeTitle = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            setPadding(0, dp(9), 0, 0)
            visibility = View.GONE
            tag = "activeTitle"
        }
        root.addView(activeTitle)
        messageText = TextView(context).apply {
            setTextColor(0xFFE6EAF0.toInt())
            textSize = 13f
            maxLines = 3
            setPadding(0, dp(7), 0, 0)
            tag = "message"
        }
        root.addView(messageText)
        linkText = TextView(context).apply {
            setTextColor(0xFF8ECDD6.toInt())
            textSize = 11f
            maxLines = 1
            setPadding(0, dp(7), 0, 0)
            tag = "link"
            visibility = View.GONE
        }
        root.addView(linkText)
        queueText = TextView(context).apply {
            setTextColor(0xFFB8C0CC.toInt())
            textSize = 11f
            setPadding(0, dp(7), 0, 0)
            tag = "queue"
            visibility = View.GONE
        }
        root.addView(queueText)
        progressBar = ProgressBar(
            context,
            null,
            android.R.attr.progressBarStyleHorizontal,
        ).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(0xFFFE2C55.toInt())
            progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
            visibility = View.GONE
            tag = "progress"
        }
        root.addView(
            progressBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)).apply {
                topMargin = dp(10)
            },
        )

        val primary = textButton("打开 DouyinKit", true).apply { tag = "primary" }
        primary.setOnClickListener { onPrimaryAction() }
        primaryButton = primary
        val input = textButton("输入链接", false).apply { tag = "input" }
        input.setOnClickListener {
            Log.d(TAG, "input button clicked")
            onOpenInput(lastRenderedState?.clipboardLink)
        }
        inputButton = input
        val open = textButton("打开 App", false)
        open.setOnClickListener { onOpenApp() }
        val firstRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }
        firstRow.addView(primary, LinearLayout.LayoutParams(0, dp(44), 1.35f))
        firstRow.addView(
            input,
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(8) },
        )
        root.addView(firstRow)
        val secondRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        val auto = textButton("分析当前视频", false).apply {
            tag = "auto"
            setOnClickListener { onAutoAction() }
            visibility = View.GONE
        }
        autoButton = auto
        stopButton = textButton("停止当前", false).apply {
            tag = "stop"
            setOnClickListener { onStop() }
            visibility = View.GONE
        }
        secondRow.addView(auto, LinearLayout.LayoutParams(0, dp(40), 1.3f))
        secondRow.addView(
            open,
            LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(8) },
        )
        secondRow.addView(
            stopButton,
            LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(8) },
        )
        root.addView(secondRow)
        return root
    }

    private fun textButton(label: String, primary: Boolean): TextView {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            isClickable = true
            setTextColor(if (primary) Color.WHITE else 0xFFE6EAF0.toInt())
            background = if (primary) {
                RippleDrawable(
                    ColorStateList.valueOf(0x40FFFFFF),
                    roundedBackground(0xFFFE2C55.toInt(), 0x00FFFFFF, 22),
                    null,
                )
            } else {
                RippleDrawable(
                    ColorStateList.valueOf(0x30FFFFFF),
                    roundedBackground(0x22FFFFFF, 0x33FFFFFF, 22),
                    null,
                )
            }
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(8), 0, dp(8), 0)
        }
    }

    private fun panelBackground(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(24).toFloat()
        setColor(0xF20F141C.toInt())
        setStroke(dp(1), 0x33FFFFFF)
    }

    private fun roundedBackground(color: Int, stroke: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(radiusDp).toFloat()
            setColor(color)
            setStroke(dp(1), stroke)
        }

    private fun pulseOrb() {
        if (!orbAttached) return
        orbRoot.animate().cancel()
        orbRoot.animate()
            .scaleX(1.12f)
            .scaleY(1.12f)
            .setDuration(120L)
            .withEndAction {
                orbRoot.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180L)
                    .start()
            }
            .start()
    }

    private fun orbLayoutParams(): WindowManager.LayoutParams {
        val size = dp(62)
        return WindowManager.LayoutParams(
            size,
            size,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - size - dp(12)).coerceAtLeast(dp(4))
            y = ((screenHeight * 0.62f).toInt() - size / 2)
                .coerceIn(statusBar + dp(20), screenHeight - navBar - size - dp(20))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun panelLayoutParams(): WindowManager.LayoutParams {
        val width = dp(316)
        return WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - width - dp(12)).coerceAtLeast(dp(4))
            y = statusBar + dp(80)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun updatePanelPosition() {
        if (!panelAttached) return
        val width = panel.width.takeIf { it > 0 } ?: panelParams.width
        val height = panel.height.takeIf { it > 0 } ?: dp(330)
        val gap = dp(10)
        val margin = dp(8)
        val left = orbParams.x - width - gap
        val right = orbParams.x + orbParams.width + gap
        val x = if (left >= margin) left else right
        val minY = statusBar + margin
        val maxY = (screenHeight - navBar - height - margin).coerceAtLeast(minY)
        val y = (orbParams.y + orbParams.height / 2 - height / 2).coerceIn(minY, maxY)
        panelParams.x = x.coerceIn(margin, (screenWidth - width - margin).coerceAtLeast(margin))
        panelParams.y = y
        runCatching { windowManager.updateViewLayout(panel, panelParams) }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun dp(value: Int): Int = (value * density).toInt()

    private fun statusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun navigationBarHeight(): Int {
        val id = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(48)
    }

    companion object {
        private const val TAG = "FloatingOverlay"
    }
}
