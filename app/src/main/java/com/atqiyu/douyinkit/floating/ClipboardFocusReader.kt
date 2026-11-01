package com.atqiyu.douyinkit.floating

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Android 10+ blocks clipboard reads for background apps even when a foreground service is
 * running. A tiny focusable overlay briefly makes DouyinKit the focused window so the copied
 * share link can still be read, then removes itself and returns focus to Douyin.
 */
class ClipboardFocusReader(private val context: Context) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    suspend fun read(): String? = withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(context)
        ) {
            return@withContext null
        }
        val edit = EditText(context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            showSoftInputOnFocus = false
            inputType = InputType.TYPE_NULL
            setTextColor(Color.TRANSPARENT)
            setBackgroundColor(Color.TRANSPARENT)
        }
        val root = FrameLayout(context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            alpha = 0.01f
            addView(
                edit,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            1,
            1,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        }
        val added = runCatching { windowManager.addView(root, params); true }
            .getOrDefault(false)
        if (!added) return@withContext null
        edit.post {
            edit.requestFocus()
            edit.setSelection(0, 0)
        }
        delay(260)
        val text = runCatching {
            val manager = context.getSystemService(ClipboardManager::class.java)
            manager?.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(context)?.toString()
        }.getOrNull()
        runCatching { windowManager.removeViewImmediate(root) }
        Log.d(TAG, if (text == null) "focus clipboard read failed" else "focus clipboard read ok")
        text
    }

    companion object {
        private const val TAG = "ClipboardFocusReader"
    }
}
