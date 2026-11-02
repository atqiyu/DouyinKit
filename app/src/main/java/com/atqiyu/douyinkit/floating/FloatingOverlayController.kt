package com.atqiyu.douyinkit.floating

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.SweepGradient
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
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.atqiyu.douyinkit.R
import com.atqiyu.douyinkit.core.util.LinkExtractor
import com.atqiyu.douyinkit.data.prefs.FloatingMode

/**
 * Floating orb + radial actions + independent info panel. The orb is the only anchor;
 * action buttons are tiny overlay windows so they can visibly pop out from it and follow drags
 * without turning a large transparent rectangle into a touch blocker.
 */
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
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var state = FloatingUiState()
    private val orbRoot = FrameLayout(context)
    private val orbCircle = FrameLayout(context)
    private val orbImage = ImageView(context)
    private val statusDot = View(context)
    private val statusOnline = statusDotDrawable(0xFF39D98A.toInt())
    private val statusIdle = statusDotDrawable(0xFF87909E.toInt())
    private val statusUnknown = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.TRANSPARENT)
        setStroke(dp(2), Color.argb(180, 135, 144, 158))
    }
    private val modeBadge = TextView(context)
    private val progressRing = OrbProgressRing()
    private var orbParams: WindowManager.LayoutParams = orbLayoutParams(dp(48))
    private var orbAttached = false
    private var orbSizePx = dp(48)
    private var orbBaseAlpha = 0.88f
    private var downRawX = 0f
    private var downRawY = 0f
    private var downOrbX = 0
    private var downOrbY = 0
    private var draggingOrb = false

    private val panelRoot = FrameLayout(context)
    private val panelCard = FrameLayout(context)
    private val panelMessage = TextView(context)
    private val panelProgress = ProgressBar(
        context,
        null,
        android.R.attr.progressBarStyleHorizontal,
    )
    private val panelQueue = TextView(context)
    private val panelLink = TextView(context)
    private val panelClose = TextView(context)
    private val panelDragArea = FrameLayout(context)
    private val panelDragHandle = View(context)
    private val panelResizeHandle = TextView(context)
    private var panelParams = WindowManager.LayoutParams(
        dp(294),
        dp(128),
        overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = (screenWidth - dp(294) - dp(10)).coerceAtLeast(dp(4))
        y = (screenHeight * 0.62f).toInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
    private var panelVisible = false
    private var panelAttached = false
    private var panelPositioned = false
    private var panelWidthPx = dp(294)
    private var panelHeightPx = dp(128)

    private val actionWindows = mutableMapOf<ActionKind, ActionWindow>()
    private var actionsVisible = false
    private var inputWindow: View? = null
    private var inputAttached = false

    private var lastPanelStyleKey: String? = null

    init {
        buildOrb()
        buildPanel()
        bindOrbTouch()
        bindPanelGestures()
    }

    fun showOrb(): Boolean {
        if (orbAttached) return true
        return runCatching {
            applyOrbSize(orbSize())
            ensurePanelWindow()
            orbBaseAlpha = state.orbAlpha.coerceIn(0.35f, 1f)
            orbRoot.alpha = orbBaseAlpha
            orbRoot.scaleX = 0.7f
            orbRoot.scaleY = 0.7f
            windowManager.addView(orbRoot, orbParams)
            orbAttached = true
            orbRoot.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220L)
                .setInterpolator(DecelerateInterpolator())
                .start()
            true
        }.onFailure { Log.w(TAG, "add orb failed", it) }
            .getOrDefault(false)
    }

    fun hideOrb() {
        if (!orbAttached) return
        hideActions()
        hidePanel(notify = false)
        hideInput()
        runCatching { windowManager.removeViewImmediate(orbRoot) }
        orbAttached = false
    }

    fun render(newState: FloatingUiState) {
        state = newState
        if (!newState.enabled) {
            hideOrb()
            return
        }
        if (!orbAttached && !showOrb()) return
        if (orbSize() != orbSizePx) applyOrbSize(orbSize())
        orbBaseAlpha = newState.orbAlpha.coerceIn(0.35f, 1f)
        orbRoot.alpha = orbBaseAlpha
        modeBadge.text = if (newState.mode == FloatingMode.AUTO) "连" else "轻"
        modeBadge.contentDescription =
            if (newState.mode == FloatingMode.AUTO) "连播模式" else "轻享模式"
        val douyinForeground = !newState.appForeground && newState.foreground.isDouyin
        statusDot.background = when {
            douyinForeground -> statusOnline
            newState.accessibilityReady -> statusIdle
            else -> statusUnknown
        }
        statusDot.contentDescription = when {
            douyinForeground -> "抖音在前台"
            newState.accessibilityReady -> "抖音不在前台"
            else -> "无法检测前台应用，请开启无障碍服务"
        }
        val focused = isBusy()
        progressRing.update(
            newState.progress,
            focused,
            newState.orbAlpha < 0.6f,
        )

        renderActions()
        renderPanel()
    }

    fun showPanel(notify: Boolean = false) {
        if (!orbAttached || panelVisible) return
        ensurePanelWindow()
        if (!panelAttached) return
        panelVisible = true
        panelRoot.animate().cancel()
        if (!panelPositioned) {
            val orbCenterX = orbParams.x + orbParams.width / 2
            panelParams.x = if (orbCenterX < screenWidth / 2) {
                (screenWidth - panelWidthPx - dp(12)).coerceAtLeast(dp(8))
            } else {
                dp(12)
            }
            panelParams.y = (screenHeight - navBar - panelHeightPx - dp(60))
                .coerceAtLeast(statusBar + dp(12))
        }
        panelParams.width = panelWidthPx
        panelParams.height = panelHeightPx
        panelParams.flags = panelBaseFlags()
        panelParams = applyPanelStyle(panelParams, state, force = true)
        panelRoot.visibility = View.VISIBLE
        runCatching { windowManager.updateViewLayout(panelRoot, panelParams) }
        panelRoot.alpha = 0f
        panelRoot.scaleX = 0.94f
        panelRoot.scaleY = 0.94f
        panelRoot.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(190L)
            .setInterpolator(DecelerateInterpolator())
            .start()
        if (notify) onExpandedChanged(true)
    }

    fun hidePanel(notify: Boolean = true) {
        if (!panelVisible) {
            if (notify) onExpandedChanged(false)
            return
        }
        panelVisible = false
        if (notify) onExpandedChanged(false)
        if (!panelAttached) return
        panelRoot.animate().cancel()
        panelRoot.animate()
            .alpha(0f)
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(140L)
            .withEndAction {
                if (!panelVisible && panelAttached) {
                    panelRoot.visibility = View.GONE
                    panelRoot.alpha = 1f
                    panelRoot.scaleX = 1f
                    panelRoot.scaleY = 1f
                    runCatching {
                        windowManager.updateViewLayout(panelRoot, hiddenPanelParams())
                    }
                }
            }
            .start()
    }

    fun showInput(prefill: String?) {
        if (inputAttached) return
        actionsVisible = false
        hideActions()
        hidePanel(notify = true)
        val root = FrameLayout(context).apply {
            setBackgroundColor(0x8C000000.toInt())
            isFocusableInTouchMode = true
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.argb(245, 14, 18, 26))
                setStroke(dp(1), Color.argb(55, 255, 255, 255))
            }
        }
        val title = TextView(context).apply {
            text = "解析链接"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }
        val hint = TextView(context).apply {
            text = "粘贴抖音分享链接或文案"
            setTextColor(0xFFAFB6C2.toInt())
            textSize = 11f
            setPadding(0, dp(2), 0, dp(7))
        }
        val input = EditText(context).apply {
            this.hint = "https://v.douyin.com/…"
            setHintTextColor(0xFF727B88.toInt())
            setTextColor(Color.WHITE)
            textSize = 13f
            minLines = 2
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedBackground(
                Color.argb(255, 30, 37, 48),
                0x33FFFFFF,
                12,
            )
            setText(prefill.orEmpty())
            setSelection(text.length)
        }
        val error = TextView(context).apply {
            setTextColor(0xFFFFB4AB.toInt())
            textSize = 11f
            visibility = View.GONE
            setPadding(0, dp(5), 0, 0)
        }
        input.doAfterTextChanged { error.visibility = View.GONE }
        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(10), 0, 0)
        }
        val cancel = textButton("取消", false)
        val submit = textButton("解析", true)
        cancel.setOnClickListener {
            hideInput()
            onInputCancel()
        }
        submit.setOnClickListener {
            val raw = input.text.toString().trim()
            if (LinkExtractor.extract(raw).isEmpty()) {
                error.text = "没有识别到抖音作品链接"
                error.visibility = View.VISIBLE
                return@setOnClickListener
            }
            hideInput()
            if (state.mode == FloatingMode.AUTO) {
                actionsVisible = true
            }
            onInputSubmit(raw)
        }
        buttons.addView(
            cancel,
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) },
        )
        buttons.addView(submit, LinearLayout.LayoutParams(0, dp(42), 1f))
        card.addView(title)
        card.addView(hint)
        card.addView(input)
        card.addView(error)
        card.addView(buttons)
        root.addView(
            card,
            FrameLayout.LayoutParams(dp(310), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
                leftMargin = dp(16)
                rightMargin = dp(16)
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
                Log.w(TAG, "add input failed", it)
                return
            }
        inputAttached = true
        inputWindow = root
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
        hideActions()
        hidePanel(notify = false)
        hideOrb()
        if (panelAttached) {
            runCatching { windowManager.removeViewImmediate(panelRoot) }
            panelAttached = false
        }
    }

    private fun ensurePanelWindow() {
        if (panelAttached) return
        runCatching {
            windowManager.addView(panelRoot, hiddenPanelParams())
            panelAttached = true
            panelRoot.visibility = View.GONE
        }.onFailure { Log.w(TAG, "prepare panel failed", it) }
    }

    private fun panelBaseFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private fun hiddenPanelParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            1,
            1,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = -4
            y = -4
        }

    private fun buildOrb() {
        orbRoot.setBackgroundColor(Color.TRANSPARENT)
        orbRoot.addView(progressRing, FrameLayout.LayoutParams(-1, -1))
        orbCircle.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFFFE2C55.toInt(), 0xFF25F4EE.toInt()),
        ).apply { shape = GradientDrawable.OVAL }
        orbImage.setImageResource(R.drawable.ic_launcher_foreground)
        orbImage.setPadding(dp(7), dp(7), dp(7), dp(7))
        orbImage.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xF20F141C.toInt())
        }
        orbCircle.addView(orbImage, FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER))
        statusDot.background = statusIdle
        orbCircle.addView(
            statusDot,
            FrameLayout.LayoutParams(dp(11), dp(11), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(1)
                marginEnd = dp(1)
            },
        )
        modeBadge.setTextColor(Color.WHITE)
        modeBadge.textSize = 8.5f
        modeBadge.gravity = Gravity.CENTER
        modeBadge.typeface = Typeface.DEFAULT_BOLD
        modeBadge.setPadding(dp(3), 0, dp(3), 0)
        modeBadge.background = GradientDrawable().apply {
            cornerRadius = dp(7).toFloat()
            setColor(Color.argb(185, 8, 12, 18))
            setStroke(dp(1), Color.argb(80, 255, 255, 255))
        }
        orbCircle.addView(
            modeBadge,
            FrameLayout.LayoutParams(
                dp(17),
                dp(12),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply { bottomMargin = dp(1) },
        )
        orbRoot.addView(
            orbCircle,
            FrameLayout.LayoutParams(orbSize(), orbSize(), Gravity.CENTER),
        )
    }

    private fun buildPanel() {
        panelRoot.setBackgroundColor(Color.TRANSPARENT)
        panelCard.setPadding(dp(12), dp(9), dp(12), dp(8))
        val header = FrameLayout(context)
        panelDragHandle.background = GradientDrawable().apply {
            cornerRadius = dp(2).toFloat()
            setColor(Color.argb(120, 255, 255, 255))
        }
        panelDragArea.setBackgroundColor(Color.TRANSPARENT)
        panelDragArea.addView(
            panelDragHandle,
            FrameLayout.LayoutParams(dp(32), dp(3), Gravity.CENTER),
        )
        header.addView(
            panelDragArea,
            FrameLayout.LayoutParams(
                dp(104),
                dp(28),
                Gravity.CENTER_HORIZONTAL or Gravity.TOP,
            ),
        )
        panelClose.text = "×"
        panelClose.setTextColor(0xFFD6DCE5.toInt())
        panelClose.textSize = 17f
        panelClose.gravity = Gravity.CENTER
        panelClose.contentDescription = "关闭浮窗"
        panelClose.setShadowLayer(3f, 0f, 1f, Color.argb(180, 0, 0, 0))
        panelClose.setOnClickListener { hidePanel(notify = true) }
        header.addView(
            panelClose,
            FrameLayout.LayoutParams(dp(34), dp(30), Gravity.END or Gravity.TOP),
        )
        panelCard.addView(
            header,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(28)),
        )
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(10))
        }
        panelMessage.setTextColor(Color.argb(235, 235, 240, 246))
        panelMessage.textSize = 11f
        panelMessage.maxLines = 3
        panelMessage.setPadding(0, 0, 0, dp(3))
        panelMessage.setShadowLayer(3f, 0f, 1f, Color.argb(170, 0, 0, 0))
        column.addView(panelMessage)
        panelProgress.max = 1000
        panelProgress.progressTintList = ColorStateList.valueOf(0xFFFE2C55.toInt())
        panelProgress.progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
        panelProgress.visibility = View.GONE
        column.addView(
            panelProgress,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(3),
            ).apply {
                topMargin = dp(2)
                bottomMargin = dp(3)
            },
        )
        panelQueue.setTextColor(Color.argb(185, 185, 193, 204))
        panelQueue.textSize = 9.5f
        panelQueue.visibility = View.GONE
        panelQueue.setShadowLayer(3f, 0f, 1f, Color.argb(160, 0, 0, 0))
        column.addView(panelQueue)
        panelLink.setTextColor(Color.argb(185, 143, 205, 212))
        panelLink.textSize = 9.5f
        panelLink.maxLines = 1
        panelLink.visibility = View.GONE
        panelLink.setPadding(0, dp(3), 0, 0)
        panelLink.setShadowLayer(3f, 0f, 1f, Color.argb(160, 0, 0, 0))
        column.addView(panelLink)
        scroll.addView(column, FrameLayout.LayoutParams(-1, -2))
        panelCard.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ).apply {
                topMargin = dp(25)
                bottomMargin = dp(4)
            },
        )
        panelResizeHandle.text = "◢"
        panelResizeHandle.setTextColor(Color.argb(150, 220, 228, 238))
        panelResizeHandle.textSize = 11f
        panelResizeHandle.gravity = Gravity.CENTER
        panelResizeHandle.contentDescription = "调整浮窗大小"
        panelResizeHandle.setShadowLayer(3f, 0f, 1f, Color.argb(160, 0, 0, 0))
        panelCard.addView(
            panelResizeHandle,
            FrameLayout.LayoutParams(dp(36), dp(30), Gravity.END or Gravity.BOTTOM),
        )
        panelRoot.addView(
            panelCard,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun bindOrbTouch() {
        orbCircle.isClickable = true
        orbCircle.isFocusable = true
        orbCircle.contentDescription = "Kit 浮球"
        orbCircle.setOnClickListener { toggleActions() }
        orbCircle.setOnTouchListener { _, event ->
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
                    if (!draggingOrb &&
                        (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)
                    ) {
                        draggingOrb = true
                    }
                    if (draggingOrb) {
                        orbParams.x = (downOrbX + dx.toInt()).coerceIn(
                            dp(48),
                            (screenWidth - orbParams.width - dp(84)).coerceAtLeast(dp(48)),
                        )
                        orbParams.y = (downOrbY + dy.toInt()).coerceIn(
                            statusBar + dp(48),
                            (screenHeight - navBar - orbParams.height - dp(84))
                                .coerceAtLeast(statusBar + dp(48)),
                        )
                        runCatching { windowManager.updateViewLayout(orbRoot, orbParams) }
                        updateActionPositions()
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!draggingOrb) orbCircle.performClick()
                    draggingOrb = false
                    true
                }

                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindPanelGestures() {
        panelDragArea.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    panelPositioned = true
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downOrbX = panelParams.x
                    downOrbY = panelParams.y
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    panelParams.x = (downOrbX + (event.rawX - downRawX).toInt()).coerceIn(
                        dp(2),
                        screenWidth - panelWidthPx - dp(2),
                    )
                    panelParams.y = (downOrbY + (event.rawY - downRawY).toInt()).coerceIn(
                        statusBar + dp(2),
                        screenHeight - navBar - panelHeightPx - dp(2),
                    )
                    runCatching { windowManager.updateViewLayout(panelRoot, panelParams) }
                    true
                }

                else -> false
            }
        }
        panelResizeHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downOrbX = panelWidthPx
                    downOrbY = panelHeightPx
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    panelWidthPx = (downOrbX + (event.rawX - downRawX).toInt())
                        .coerceIn(dp(210), screenWidth - dp(8))
                    panelHeightPx = (downOrbY + (event.rawY - downRawY).toInt())
                        .coerceIn(dp(96), (screenHeight * 0.55f).toInt())
                    panelParams.width = panelWidthPx
                    panelParams.height = panelHeightPx
                    panelParams.x =
                        panelParams.x.coerceAtMost(screenWidth - panelWidthPx - dp(2))
                    panelParams.y = panelParams.y.coerceIn(
                        statusBar + dp(2),
                        (screenHeight - navBar - panelHeightPx - dp(2))
                            .coerceAtLeast(statusBar + dp(2)),
                    )
                    runCatching { windowManager.updateViewLayout(panelRoot, panelParams) }
                    true
                }

                else -> false
            }
        }
    }

    private fun toggleActions() {
        actionsVisible = !actionsVisible
        orbCircle.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(90L)
            .withEndAction {
                orbCircle.animate().scaleX(1f).scaleY(1f).setDuration(140L).start()
            }
            .start()
        renderActions()
        if (actionsVisible && !panelVisible && hasPanelInfo(state)) {
            showPanel(notify = true)
        }
    }

    private fun renderActions() {
        if (!orbAttached || !actionsVisible) {
            hideActions()
            return
        }
        val specs = linkedMapOf<ActionKind, ActionSpec>()
        when {
            state.needsVerification -> specs[ActionKind.PRIMARY] = ActionSpec(
                label = "完成验证",
                icon = R.drawable.ic_float_analyze,
                accent = true,
                onClick = onPrimaryAction,
            )

            state.needsLogin -> specs[ActionKind.PRIMARY] = ActionSpec(
                label = "打开登录",
                icon = R.drawable.ic_float_open,
                accent = true,
                onClick = onPrimaryAction,
            )

            state.promptVisible && !state.clipboardLink.isNullOrBlank() ->
                specs[ActionKind.PRIMARY] = ActionSpec(
                    label = "解析剪贴板",
                    icon = R.drawable.ic_float_analyze,
                    accent = true,
                    onClick = onPrimaryAction,
                )

            state.mode == FloatingMode.AUTO &&
                !state.appForeground &&
                state.foreground.isDouyin &&
                !isBusy() -> specs[ActionKind.PRIMARY] = ActionSpec(
                label = "分析当前视频",
                icon = R.drawable.ic_float_analyze,
                accent = true,
                onClick = onAutoAction,
            )
        }
        specs[ActionKind.INPUT] = ActionSpec(
            label = null,
            icon = R.drawable.ic_float_input,
            accent = false,
            onClick = { onOpenInput(state.clipboardLink) },
        )
        specs[ActionKind.OPEN_APP] = ActionSpec(
            label = null,
            icon = R.drawable.ic_float_open,
            accent = false,
            onClick = onOpenApp,
        )
        if (state.mode == FloatingMode.AUTO &&
            !state.automationBusy &&
            (isBusy() || state.queueSize > 0)
        ) {
            specs[ActionKind.NEXT] = ActionSpec(
                label = "下一个",
                icon = R.drawable.ic_float_next,
                accent = false,
                onClick = onAutoAction,
            )
        }
        if (isBusy()) {
            specs[ActionKind.STOP] = ActionSpec(
                label = "停止",
                icon = R.drawable.ic_float_stop,
                accent = false,
                danger = true,
                onClick = onStop,
            )
        }
        if (state.resultReady) {
            specs[ActionKind.RESULT] = ActionSpec(
                label = "结果",
                icon = R.drawable.ic_float_result,
                accent = false,
                onClick = onOpenApp,
            )
        }
        actionWindows.keys.filter { it !in specs }.forEach { kind ->
            actionWindows[kind]?.hide()
        }
        specs.entries.forEachIndexed { index, (kind, spec) ->
            val window = actionWindows.getOrPut(kind) { ActionWindow(kind) }
            window.configure(spec)
            if (!window.isShown) window.show(index * 35L)
            window.moveTo(positionFor(kind, window.widthPx, window.heightPx))
        }
        updateActionPositions()
    }

    private fun updateActionPositions() {
        actionWindows.forEach { (kind, window) ->
            window.moveTo(positionFor(kind, window.widthPx, window.heightPx))
        }
    }

    private fun hideActions() {
        actionWindows.values.forEach { it.hide() }
    }

    private fun positionFor(kind: ActionKind, width: Int, height: Int): Pair<Int, Int> {
        val centerX = orbParams.x + orbParams.width / 2
        val centerY = orbParams.y + orbParams.height / 2
        val angle = when (kind) {
            ActionKind.PRIMARY -> -90.0
            ActionKind.INPUT -> 180.0
            ActionKind.OPEN_APP -> 0.0
            ActionKind.NEXT -> 142.0
            ActionKind.STOP -> 38.0
            ActionKind.RESULT -> 90.0
        }
        val radius = orbParams.width / 2f + dp(8) + kotlin.math.max(width, height) / 2f
        val rad = Math.toRadians(angle)
        val x = (centerX + kotlin.math.cos(rad) * radius - width / 2).toInt()
        val y = (centerY + kotlin.math.sin(rad) * radius - height / 2).toInt()
        return x.coerceIn(dp(4), (screenWidth - width - dp(4)).coerceAtLeast(dp(4))) to
            y.coerceIn(
                statusBar + dp(4),
                (screenHeight - navBar - height - dp(4)).coerceAtLeast(statusBar + dp(4)),
            )
    }

    private fun renderPanel() {
        applyPanelStyle(panelParams, state, force = false)
        val focused = isBusy()
        panelMessage.text = state.message
        panelMessage.visibility = if (state.message.isBlank()) View.GONE else View.VISIBLE
        val progress = state.progress
        val showProgress = focused || (progress != null && progress < 0.999f)
        panelProgress.visibility = if (showProgress) View.VISIBLE else View.GONE
        if (progress != null) {
            panelProgress.isIndeterminate = false
            panelProgress.progress = (progress.coerceIn(0f, 1f) * 1000).toInt()
        } else {
            panelProgress.isIndeterminate = focused
        }
        panelQueue.visibility = if (state.queueSize > 0) View.VISIBLE else View.GONE
        panelQueue.text = if (state.queueSize > 0) "队列 ${state.queueSize}" else ""
        val link = if (state.showClipboard) state.clipboardLink else null
        panelLink.visibility = if (link.isNullOrBlank()) View.GONE else View.VISIBLE
        panelLink.text = link?.let { "剪贴板 ${it.take(46)}" }.orEmpty()
        if (state.expanded && !panelVisible) {
            showPanel(notify = false)
        } else if (!state.expanded && panelVisible) {
            hidePanel(notify = false)
        }
    }

    private fun hasPanelInfo(state: FloatingUiState): Boolean =
        (state.message.isNotBlank() && state.message != "待命") ||
            state.phase != FloatingPhase.IDLE ||
            state.queueSize > 0 ||
            state.promptVisible ||
            state.resultReady ||
            state.needsVerification ||
            state.needsLogin ||
            (state.showClipboard && !state.clipboardLink.isNullOrBlank())

    private fun isBusy(): Boolean =
        state.phase == FloatingPhase.PARSING ||
            state.phase == FloatingPhase.WAITING ||
            state.phase == FloatingPhase.VERIFYING ||
            state.phase == FloatingPhase.DOWNLOADING ||
            state.automationBusy

    private fun applyOrbSize(sizePx: Int) {
        orbSizePx = sizePx
        val windowSize = sizePx + dp(18)
        orbParams.width = windowSize
        orbParams.height = windowSize
        orbParams.x = orbParams.x.coerceIn(
            dp(4),
            (screenWidth - windowSize - dp(4)).coerceAtLeast(dp(4)),
        )
        orbParams.y = orbParams.y.coerceIn(
            statusBar + dp(4),
            (screenHeight - navBar - windowSize - dp(4)).coerceAtLeast(statusBar + dp(4)),
        )
        orbRoot.layoutParams = FrameLayout.LayoutParams(windowSize, windowSize)
        (orbCircle.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.width = sizePx
            it.height = sizePx
            orbCircle.layoutParams = it
        }
        (orbImage.layoutParams as? FrameLayout.LayoutParams)?.let {
            val imageSize = sizePx - dp(10)
            it.width = imageSize
            it.height = imageSize
            orbImage.layoutParams = it
            orbImage.setPadding(dp(7), dp(7), dp(7), dp(7))
        }
        if (orbAttached) {
            runCatching { windowManager.updateViewLayout(orbRoot, orbParams) }
        }
        updateActionPositions()
    }

    private fun orbSize(): Int = dp(state.orbSizeDp.coerceIn(38, 72))

    private fun orbLayoutParams(sizePx: Int): WindowManager.LayoutParams {
        val windowSize = sizePx + dp(18)
        return WindowManager.LayoutParams(
            windowSize,
            windowSize,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - windowSize - dp(84)).coerceAtLeast(dp(48))
            y = ((screenHeight * 0.62f).toInt() - windowSize / 2)
                .coerceIn(
                    statusBar + dp(48),
                    (screenHeight - navBar - windowSize - dp(84))
                        .coerceAtLeast(statusBar + dp(48)),
                )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun applyPanelStyle(
        params: WindowManager.LayoutParams,
        state: FloatingUiState,
        force: Boolean,
    ): WindowManager.LayoutParams {
        val key =
            "${state.panelStyle}:${state.panelOpacity}:${state.panelColorArgb}:${state.panelBlur}"
        if (!force && key == lastPanelStyleKey) return params
        lastPanelStyleKey = key
        panelCard.background = FrostedPanelDrawable(
            baseColor = state.panelColorArgb.toInt(),
            opacity = state.panelOpacity,
            style = state.panelStyle,
            frost = state.panelBlur,
            cornerRadius = dp(20).toFloat(),
            density = density,
        )
        if (panelAttached && panelVisible) {
            runCatching { windowManager.updateViewLayout(panelRoot, params) }
        }
        return params
    }

    private fun textButton(label: String, primary: Boolean): TextView {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            isClickable = true
            setTextColor(if (primary) Color.WHITE else Color.argb(230, 230, 236, 242))
            background = if (primary) {
                RippleDrawable(
                    ColorStateList.valueOf(0x40FFFFFF),
                    roundedBackground(0xFFFE2C55.toInt(), 0x00FFFFFF, 21),
                    null,
                )
            } else {
                RippleDrawable(
                    ColorStateList.valueOf(0x30FFFFFF),
                    roundedBackground(0x22FFFFFF, 0x33FFFFFF, 21),
                    null,
                )
            }
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(8), 0, dp(8), 0)
        }
    }

    private fun actionButtonBackground(accent: Boolean, danger: Boolean): GradientDrawable {
        val color = when {
            danger -> Color.argb(235, 185, 54, 70)
            accent -> Color.argb(240, 230, 45, 82)
            else -> Color.argb(225, 20, 26, 36)
        }
        return GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(color)
            setStroke(dp(1), Color.argb(70, 255, 255, 255))
        }
    }

    private fun roundedBackground(color: Int, stroke: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(radiusDp).toFloat()
            setColor(color)
            setStroke(dp(1), stroke)
        }

    private fun statusDotDrawable(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(2), Color.argb(220, 12, 16, 22))
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

    private data class ActionSpec(
        val label: String?,
        val icon: Int,
        val accent: Boolean,
        val danger: Boolean = false,
        val enabled: Boolean = true,
        val onClick: () -> Unit,
    )

    private enum class ActionKind { PRIMARY, INPUT, OPEN_APP, NEXT, STOP, RESULT }

    private inner class ActionWindow(private val kind: ActionKind) {
        val root = FrameLayout(context)
        private val content = LinearLayout(context)
        private val icon = ImageView(context)
        private val label = TextView(context)
        private var spec: ActionSpec? = null
        var isShown = false
            private set
        private var attached = false
        var widthPx = dp(42)
            private set
        var heightPx = dp(42)
            private set
        private var params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        init {
            root.setBackgroundColor(Color.TRANSPARENT)
            content.orientation = LinearLayout.HORIZONTAL
            content.gravity = Gravity.CENTER
            content.isClickable = true
            content.isFocusable = true
            content.setOnClickListener { spec?.onClick?.invoke() }
            root.addView(
                content,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
        }

        fun configure(newSpec: ActionSpec) {
            val previous = spec
            spec = newSpec
            if (previous != null &&
                previous.label == newSpec.label &&
                previous.icon == newSpec.icon &&
                previous.accent == newSpec.accent &&
                previous.danger == newSpec.danger &&
                previous.enabled == newSpec.enabled
            ) {
                content.isEnabled = newSpec.enabled
                content.alpha = if (newSpec.enabled) 1f else 0.5f
                return
            }
            content.background = if (kind == ActionKind.PRIMARY) {
                null
            } else {
                actionButtonBackground(newSpec.accent, newSpec.danger)
            }
            content.isEnabled = newSpec.enabled
            content.alpha = if (newSpec.enabled) 1f else 0.5f
            content.contentDescription = newSpec.label ?: when (kind) {
                ActionKind.INPUT -> "输入链接"
                ActionKind.OPEN_APP -> "打开 DouyinKit"
                else -> "浮球操作"
            }
            (icon.parent as? android.view.ViewGroup)?.removeView(icon)
            (label.parent as? android.view.ViewGroup)?.removeView(label)
            content.removeAllViews()
            val labeled = !newSpec.label.isNullOrBlank()
            val size = dp(42)
            if (labeled && kind == ActionKind.PRIMARY) {
                icon.setImageResource(newSpec.icon)
                icon.setColorFilter(Color.WHITE)
                val circle = FrameLayout(context).apply {
                    background = actionButtonBackground(newSpec.accent, newSpec.danger)
                    addView(
                        icon,
                        FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER),
                    )
                }
                content.addView(circle, LinearLayout.LayoutParams(size, size))
                label.text = newSpec.label
                label.setTextColor(Color.WHITE)
                label.textSize = 10f
                label.typeface = Typeface.DEFAULT_BOLD
                label.maxLines = 1
                label.setPadding(dp(6), 0, dp(2), 0)
                label.setShadowLayer(4f, 0f, 1f, Color.argb(220, 0, 0, 0))
                content.addView(label, LinearLayout.LayoutParams(-2, size))
                heightPx = size
            } else if (labeled) {
                icon.setImageResource(newSpec.icon)
                icon.setColorFilter(Color.WHITE)
                content.addView(
                    icon,
                    LinearLayout.LayoutParams(dp(17), dp(17)).apply {
                        marginStart = dp(10)
                    },
                )
                label.text = newSpec.label
                label.setTextColor(Color.WHITE)
                label.textSize = 10f
                label.typeface = Typeface.DEFAULT_BOLD
                label.maxLines = 1
                label.setPadding(dp(5), 0, dp(10), 0)
                content.addView(label, LinearLayout.LayoutParams(-2, size))
                heightPx = size
            } else {
                icon.setImageResource(newSpec.icon)
                icon.setColorFilter(Color.WHITE)
                content.addView(icon, LinearLayout.LayoutParams(dp(20), dp(20)))
                heightPx = size
            }
            content.minimumWidth = if (labeled) 0 else size
            content.minimumHeight = size
            widthPx = if (labeled) dp(110) else size
            root.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            widthPx = root.measuredWidth.coerceAtLeast(widthPx)
            heightPx = root.measuredHeight.coerceAtLeast(heightPx)
        }

        fun show(startDelay: Long) {
            if (isShown) return
            if (attached) {
                isShown = true
                root.animate().cancel()
                content.animate().cancel()
                root.alpha = 1f
                content.scaleX = 1f
                content.scaleY = 1f
                content.translationX = 0f
                content.translationY = 0f
                return
            }
            isShown = true
            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START }
            val pos = positionFor(kind, widthPx, heightPx)
            params.x = pos.first
            params.y = pos.second
            runCatching { windowManager.addView(root, params) }
                .onFailure {
                    Log.w(TAG, "add action $kind failed", it)
                    isShown = false
                    return
                }
            attached = true
            val orbCenterX = orbParams.x + orbParams.width / 2f
            val orbCenterY = orbParams.y + orbParams.height / 2f
            val buttonCenterX = params.x + widthPx / 2f
            val buttonCenterY = params.y + heightPx / 2f
            content.pivotX = widthPx / 2f
            content.pivotY = heightPx / 2f
            content.scaleX = 0.55f
            content.scaleY = 0.55f
            content.translationX = orbCenterX - buttonCenterX
            content.translationY = orbCenterY - buttonCenterY
            root.alpha = 0f
            root.animate().alpha(1f).setStartDelay(startDelay).setDuration(120L).start()
            content.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .translationY(0f)
                .setStartDelay(startDelay)
                .setDuration(220L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        fun hide() {
            if (!isShown) return
            isShown = false
            root.animate().alpha(0f).setDuration(120L).start()
            content.animate()
                .scaleX(0.6f)
                .scaleY(0.6f)
                .setDuration(120L)
                .withEndAction {
                    if (!isShown && attached) {
                        runCatching { windowManager.removeViewImmediate(root) }
                        attached = false
                    }
                }
                .start()
        }

        fun moveTo(position: Pair<Int, Int>) {
            if (!isShown) return
            if (params.x == position.first && params.y == position.second) return
            params.x = position.first
            params.y = position.second
            runCatching { windowManager.updateViewLayout(root, params) }
        }
    }

    private inner class OrbProgressRing : View(context) {
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
            color = Color.argb(50, 255, 255, 255)
        }
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2.4f.toInt()).toFloat()
            strokeCap = Paint.Cap.ROUND
        }
        private val rect = RectF()
        private val ringColors = intArrayOf(
            0xFFFE2C55.toInt(),
            0xFF25F4EE.toInt(),
            0xFFFE2C55.toInt(),
        )
        private var ringShader: SweepGradient? = null
        private var progress: Float? = null
        private var active = false
        private var rotation = -90f
        private val animator = android.animation.ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1200L
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                rotation = -90f + it.animatedValue as Float
                invalidate()
            }
        }

        fun update(progress: Float?, active: Boolean, reduced: Boolean) {
            val nextProgress = progress?.coerceIn(0f, 1f)
            val nextAlpha = if (reduced) 0.55f else 1f
            if (this.progress == nextProgress && this.active == active && alpha == nextAlpha) return
            this.progress = nextProgress
            this.active = active
            alpha = nextAlpha
            if (active && this.progress == null) {
                if (!animator.isStarted) animator.start()
            } else {
                if (animator.isStarted) animator.cancel()
            }
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            ringShader = SweepGradient(w / 2f, h / 2f, ringColors, null)
        }

        override fun onDetachedFromWindow() {
            animator.cancel()
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!active) return
            val width = width.toFloat()
            val height = height.toFloat()
            val radius = minOf(width, height) / 2f - trackPaint.strokeWidth
            if (radius <= 0f) return
            val cx = width / 2f
            val cy = height / 2f
            rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
            progressPaint.shader = ringShader ?: return
            canvas.drawArc(rect, 0f, 360f, false, trackPaint)
            val value = progress
            if (value != null) {
                canvas.drawArc(rect, -90f, 360f * value, false, progressPaint)
            } else {
                canvas.drawArc(rect, rotation, 70f, false, progressPaint)
                canvas.drawArc(rect, rotation + 180f, 36f, false, progressPaint)
            }
        }
    }

    companion object {
        private const val TAG = "FloatingOverlay"
    }
}
