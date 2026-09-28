package com.sagewire.rangewater.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Magnifier
import kotlin.math.max
import kotlin.math.min

internal object PrecisionLoupe {
    fun create(view: View): Controller? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        // MapLibre normally renders through a child SurfaceView. Attaching Android's
        // Magnifier to the MapView container copies the application window instead of
        // the GL surface on some Samsung devices, so map handles can stay under the
        // finger while disappearing from the loupe. Magnify the render child itself.
        val sourceView = findRenderView(view) ?: view
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val density = view.resources.displayMetrics.density
            Controller(
                mapView = view,
                sourceView = sourceView,
                magnifier = Magnifier.Builder(sourceView)
                .setSize((144 * density).toInt(), (112 * density).toInt())
                .setInitialZoom(2.2f)
                .setCornerRadius(18 * density)
                .setElevation(8 * density)
                .setOverlay(CrosshairDrawable(density))
                .build()
            )
        } else {
            @Suppress("DEPRECATION")
            Controller(view, sourceView, Magnifier(sourceView))
        }
    }

    private fun findRenderView(view: View): View? {
        if (view is SurfaceView || view is TextureView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findRenderView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    internal class Controller(
        private val mapView: View,
        private val sourceView: View,
        private val magnifier: Magnifier
    ) {
        private val density = mapView.resources.displayMetrics.density
        private val mapLocation = IntArray(2)
        private val sourceLocation = IntArray(2)

        /**
         * [sourceX]/[sourceY] are the exact map pixels represented by the crosshair.
         * [fingerX]/[fingerY] only choose a safe loupe-window position.
         */
        fun show(sourceX: Float, sourceY: Float, fingerX: Float, fingerY: Float) {
            mapView.getLocationInWindow(mapLocation)
            sourceView.getLocationInWindow(sourceLocation)
            val offsetX = (mapLocation[0] - sourceLocation[0]).toFloat()
            val offsetY = (mapLocation[1] - sourceLocation[1]).toFloat()
            val localSourceX = sourceX + offsetX
            val localSourceY = sourceY + offsetY

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val windowCenter = windowCenter(fingerX, fingerY)
                magnifier.show(
                    localSourceX,
                    localSourceY,
                    windowCenter.first + offsetX,
                    windowCenter.second + offsetY
                )
            } else {
                magnifier.show(localSourceX, localSourceY)
            }
            // MapLibre submits its overlay frame asynchronously. Refreshing on the next
            // display frame keeps a dragged post/handle visible inside the loupe instead
            // of leaving the inset one render behind the finger.
            sourceView.postOnAnimation { magnifier.update() }
        }

        fun dismiss() = magnifier.dismiss()

        private fun windowCenter(fingerX: Float, fingerY: Float): Pair<Float, Float> {
            val halfWidth = magnifier.width / 2f
            val halfHeight = magnifier.height / 2f
            val edgeMargin = 12f * density
            val horizontalGap = 32f * density
            val verticalGap = 48f * density

            val desiredX = if (fingerX < mapView.width / 2f) {
                fingerX + halfWidth + horizontalGap
            } else {
                fingerX - halfWidth - horizontalGap
            }
            val above = fingerY - halfHeight - verticalGap
            val desiredY = if (above - halfHeight >= edgeMargin) {
                above
            } else {
                fingerY + halfHeight + verticalGap
            }
            return Pair(
                min(max(desiredX, halfWidth + edgeMargin), mapView.width - halfWidth - edgeMargin),
                min(max(desiredY, halfHeight + edgeMargin), mapView.height - halfHeight - edgeMargin)
            )
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
