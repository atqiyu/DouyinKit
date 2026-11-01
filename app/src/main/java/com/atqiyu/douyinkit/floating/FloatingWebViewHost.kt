package com.atqiyu.douyinkit.floating

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.webkit.WebView

/**
 * Hosts the parsing WebView in a 2x2 off-screen overlay while the floating assistant works.
 * If Douyin asks for a captcha or login, the same WebView is expanded full-screen so the user
 * can finish the page without switching back to the app.
 */
class FloatingWebViewHost(
    private val context: Context,
    private val webView: WebView,
    private val onClosed: () -> Unit,
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val root = FrameLayout(context)
    private val topBar: LinearLayout
    private var attached = false
    private var expanded = false

    init {
        root.setBackgroundColor(Color.BLACK)
        webView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        root.addView(webView)

        topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10) + statusBarHeight(), dp(10), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(235, 14, 18, 24), Color.argb(205, 14, 18, 24)),
            )
            visibility = View.GONE
            val hint = TextView(context).apply {
                text = "完成验证后会自动继续"
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(
                hint,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            val close = TextView(context).apply {
                text = "收起"
                setTextColor(Color.WHITE)
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(6), dp(14), dp(6))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(Color.argb(70, 255, 255, 255))
                    setStroke(dp(1), Color.argb(70, 255, 255, 255))
                }
                setOnClickListener {
                    hidePage()
                    onClosed()
                }
            }
            addView(close)
        }
        root.addView(
            topBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ),
        )
    }

    fun attach(): Boolean {
        if (attached) return true
        return runCatching {
            windowManager.addView(root, hiddenParams())
            attached = true
            webView.onResume()
            webView.resumeTimers()
            true
        }.getOrDefault(false)
    }

    fun showPage() {
        if (!attach()) return
        expanded = true
        topBar.visibility = View.VISIBLE
        webView.onResume()
        webView.resumeTimers()
        windowManager.updateViewLayout(root, expandedParams())
        root.alpha = 0f
        root.animate().alpha(1f).setDuration(180L).start()
        webView.requestFocus()
    }

    fun hidePage() {
        if (!attached || !expanded) return
        expanded = false
        topBar.visibility = View.GONE
        webView.clearFocus()
        root.alpha = 1f
        windowManager.updateViewLayout(root, hiddenParams())
    }

    fun destroy() {
        if (!attached) return
        runCatching { windowManager.removeViewImmediate(root) }
        attached = false
        expanded = false
    }

    private fun hiddenParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            2,
            2,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = -4
            y = -4
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun expandedParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun statusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(24)
    }
}
