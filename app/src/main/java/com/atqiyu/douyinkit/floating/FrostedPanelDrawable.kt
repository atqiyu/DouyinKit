package com.atqiyu.douyinkit.floating

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import com.atqiyu.douyinkit.data.prefs.FloatingPanelStyle
import kotlin.math.max
import kotlin.random.Random

/**
 * Self-contained frosted panel background. It never asks the window manager to blur the screen
 * behind the panel (some OEMs blur the whole display for that flag); all texture is drawn inside
 * the panel bounds so the rest of the screen stays sharp.
 */
internal class FrostedPanelDrawable(
    private val baseColor: Int,
    private val opacity: Float,
    private val style: FloatingPanelStyle,
    private val frost: Int,
    private val cornerRadius: Float,
    private val density: Float,
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1f, density)
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1f, density)
    }
    private val rect = RectF()
    private val path = Path()
    private var baseShader: Shader? = null
    private var glowShader: Shader? = null
    private var accentShader: Shader? = null
    private var grainShader: Shader? = null
    private val grainAlpha = (20 + frost.coerceIn(0, 25) * 1.4f).toInt().coerceIn(14, 58)
    private val baseAlpha = when (style) {
        FloatingPanelStyle.GLASS -> 0.24f + opacity * 0.44f
        FloatingPanelStyle.CLEAR -> 0.06f + opacity * 0.30f
        FloatingPanelStyle.TRANSPARENT -> opacity * 0.05f
        FloatingPanelStyle.SOLID -> opacity
    }.coerceIn(0f, 0.94f)

    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds)
        path.reset()
        path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        val width = rect.width().coerceAtLeast(1f)
        val height = rect.height().coerceAtLeast(1f)
        baseShader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            shaded(lighten(0.10f), (baseAlpha * 1.06f).coerceAtMost(0.98f)),
            shaded(darken(0.12f), (baseAlpha * 0.86f).coerceAtMost(0.98f)),
            Shader.TileMode.CLAMP,
        )
        if (style == FloatingPanelStyle.GLASS) {
            val glowRadius = max(width, height) * (0.66f + frost.coerceIn(0, 25) / 150f)
            glowShader = RadialGradient(
                rect.left + width * 0.16f,
                rect.top + height * 0.02f,
                glowRadius,
                Color.argb(52 + frost.coerceIn(0, 25), 255, 255, 255),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            accentShader = RadialGradient(
                rect.right - width * 0.08f,
                rect.bottom - height * 0.04f,
                glowRadius * 0.82f,
                shaded(lighten(0.28f), (0.10f + frost.coerceIn(0, 25) * 0.003f)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            grainShader = BitmapShader(noiseBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        } else {
            glowShader = null
            accentShader = null
            grainShader = null
        }
    }

    override fun draw(canvas: Canvas) {
        val shader = baseShader ?: return
        canvas.save()
        canvas.clipPath(path)
        paint.shader = shader
        paint.alpha = 255
        canvas.drawRect(rect, paint)
        glowShader?.let {
            paint.shader = it
            canvas.drawRect(rect, paint)
        }
        accentShader?.let {
            paint.shader = it
            canvas.drawRect(rect, paint)
        }
        grainShader?.let {
            paint.shader = it
            paint.alpha = grainAlpha
            canvas.drawRect(rect, paint)
        }
        canvas.restore()

        paint.shader = null
        paint.alpha = 255
        val borderAlpha = when (style) {
            FloatingPanelStyle.TRANSPARENT -> 42
            FloatingPanelStyle.SOLID -> 38
            else -> 58
        }
        borderPaint.color = Color.argb(borderAlpha, 255, 255, 255)
        canvas.drawPath(path, borderPaint)
        highlightPaint.color = Color.argb(
            if (style == FloatingPanelStyle.TRANSPARENT) 34 else 72,
            255,
            255,
            255,
        )
        canvas.drawLine(
            rect.left + cornerRadius,
            rect.top + borderPaint.strokeWidth / 2f,
            rect.right - cornerRadius,
            rect.top + borderPaint.strokeWidth / 2f,
            highlightPaint,
        )
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    private fun shaded(color: Int, alpha: Float): Int =
        Color.argb((alpha * 255).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun lighten(amount: Float): Int = blend(Color.WHITE, amount)

    private fun darken(amount: Float): Int = blend(Color.BLACK, amount)

    private fun blend(target: Int, amount: Float): Int {
        val ratio = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(baseColor) + (Color.red(target) - Color.red(baseColor)) * ratio).toInt(),
            (Color.green(baseColor) + (Color.green(target) - Color.green(baseColor)) * ratio).toInt(),
            (Color.blue(baseColor) + (Color.blue(target) - Color.blue(baseColor)) * ratio).toInt(),
        )
    }

    private companion object {
        val noiseBitmap: Bitmap by lazy {
            val size = 64
            val pixels = IntArray(size * size)
            val random = Random(0x5EED)
            pixels.indices.forEach { index ->
                val alpha = random.nextInt(0, 22)
                val tone = if (random.nextBoolean()) 255 else 0
                pixels[index] = Color.argb(alpha, tone, tone, tone)
            }
            Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        }
    }
}
