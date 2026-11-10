package com.jamesjones.sunrise

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * Draws the sky. progress 0 = pitch black night, 1 = warm full daylight.
 * A low red glow appears at the bottom first, then the sun rises up the screen.
 */
class SunriseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var progress = 0f
    private val skyPaint = Paint().apply { isDither = true }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setProgress(p: Float) {
        if (p != progress) {
            progress = p
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val p = progress

        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(sample(TOP, p), sample(MID, p), sample(BOTTOM, p)),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, skyPaint)

        // The sun starts below the bottom edge and climbs to about 58% down the screen.
        val sunT = ((p - 0.15f) / 0.85f).coerceIn(0f, 1f)
        val ease = 1f - (1f - sunT) * (1f - sunT)
        val radius = minOf(w, h) * 0.14f
        val startY = h + radius * 1.3f
        val endY = h * 0.58f
        val cx = w / 2f
        val cy = startY + (endY - startY) * ease
        val sunColor = sample(SUN, p)

        val glowAlpha = (minOf(1f, p / 0.35f) * 200f).toInt()
        val glowRadius = maxOf(w, h) * (0.4f + 0.6f * p)
        glowPaint.shader = RadialGradient(
            cx, cy, glowRadius,
            intArrayOf(withAlpha(sunColor, glowAlpha), withAlpha(sunColor, glowAlpha / 3), Color.TRANSPARENT),
            floatArrayOf(0f, 0.35f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, glowRadius, glowPaint)

        if (sunT > 0f) {
            sunPaint.color = withAlpha(sunColor, (255f * minOf(1f, sunT * 3f)).toInt())
            canvas.drawCircle(cx, cy, radius, sunPaint)
        }
    }

    companion object {
        private val STOPS = floatArrayOf(0f, 0.12f, 0.30f, 0.50f, 0.70f, 0.85f, 1f)
        private val TOP = intArrayOf(
            0xFF000000.toInt(), 0xFF030208.toInt(), 0xFF0E0718.toInt(), 0xFF2E1838.toInt(),
            0xFF6B3A52.toInt(), 0xFFD99A72.toInt(), 0xFFFFEBD0.toInt(),
        )
        private val MID = intArrayOf(
            0xFF000000.toInt(), 0xFF0A0306.toInt(), 0xFF2A0C14.toInt(), 0xFF7A2A22.toInt(),
            0xFFD2602A.toInt(), 0xFFF7AE5E.toInt(), 0xFFFFF1D8.toInt(),
        )
        private val BOTTOM = intArrayOf(
            0xFF000000.toInt(), 0xFF1A0508.toInt(), 0xFF5A1410.toInt(), 0xFFB8401A.toInt(),
            0xFFF28A2E.toInt(), 0xFFFFC872.toInt(), 0xFFFFF6E2.toInt(),
        )
        private val SUN = intArrayOf(
            0xFF8A1A08.toInt(), 0xFFB02A0C.toInt(), 0xFFE0441A.toInt(), 0xFFFF6A22.toInt(),
            0xFFFF9A3C.toInt(), 0xFFFFD58A.toInt(), 0xFFFFF8E8.toInt(),
        )

        private fun sample(colors: IntArray, p: Float): Int {
            if (p <= STOPS[0]) return colors[0]
            for (i in 1 until STOPS.size) {
                if (p <= STOPS[i]) {
                    val t = (p - STOPS[i - 1]) / (STOPS[i] - STOPS[i - 1])
                    return lerpColor(colors[i - 1], colors[i], t)
                }
            }
            return colors[colors.size - 1]
        }

        fun lerpColor(a: Int, b: Int, t: Float): Int {
            val u = t.coerceIn(0f, 1f)
            fun ch(x: Int, y: Int) = (x + (y - x) * u).toInt()
            return Color.argb(
                ch(Color.alpha(a), Color.alpha(b)),
                ch(Color.red(a), Color.red(b)),
                ch(Color.green(a), Color.green(b)),
                ch(Color.blue(a), Color.blue(b)),
            )
        }

        private fun withAlpha(color: Int, alpha: Int): Int =
            Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }
}
