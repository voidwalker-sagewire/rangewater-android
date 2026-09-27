package com.sagewire.rangewater.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.widget.Magnifier

internal object PrecisionLoupe {
    fun create(view: View): Magnifier? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val density = view.resources.displayMetrics.density
            Magnifier.Builder(view)
                .setSize((144 * density).toInt(), (112 * density).toInt())
                .setInitialZoom(2.2f)
                .setCornerRadius(18 * density)
                .setElevation(8 * density)
                .setOverlay(CrosshairDrawable(density))
                .build()
        } else {
            @Suppress("DEPRECATION")
            Magnifier(view)
        }
    }

    private class CrosshairDrawable(density: Float) : Drawable() {
        private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x18000000 }
        private val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 4f * density
            style = Paint.Style.STROKE
        }
        private val light = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 2f * density
            style = Paint.Style.STROKE
        }
        private val radius = 11f * density
        private val arm = 18f * density

        override fun draw(canvas: Canvas) {
            canvas.drawRect(bounds, shade)
            val x = bounds.exactCenterX()
            val y = bounds.exactCenterY()
            listOf(dark, light).forEach { paint ->
                canvas.drawCircle(x, y, radius, paint)
                canvas.drawLine(x - arm, y, x + arm, y, paint)
                canvas.drawLine(x, y - arm, x, y + arm, paint)
            }
        }

        override fun setAlpha(alpha: Int) {
            dark.alpha = alpha
            light.alpha = alpha
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            dark.colorFilter = colorFilter
            light.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in the Android Drawable API")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
