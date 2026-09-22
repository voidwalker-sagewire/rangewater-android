package com.sagewire.rangewater.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import org.maplibre.android.maps.Style

/** Device-local marker artwork. No glyph server or network sprite is required. */
internal object MapMarkerIcons {
    private const val SIZE = 48

    fun register(style: Style) {
        style.addImage("marker-herd-cow", cow())
        style.addImage("marker-water-trough", trough())
        style.addImage("marker-water-tank", tank())
        style.addImage("marker-water-spring", spring())
        style.addImage("marker-water-pond", pond())
        style.addImage("marker-water-hydrant", hydrant())
        style.addImage("marker-water-other", other())
        (2..99).forEach { count ->
            style.addImage("marker-herd-overflow-$count", overflow(count))
        }
    }

    private fun canvas(): Pair<Bitmap, Canvas> {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        return bitmap to Canvas(bitmap)
    }

    private fun fill() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private fun stroke(width: Float = 3f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(21, 21, 21)
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun cow(): Bitmap {
        val (bitmap, canvas) = canvas()
        val fill = fill()
        val stroke = stroke()
        val head = RectF(14f, 13f, 34f, 38f)
        canvas.drawOval(head, fill)
        canvas.drawOval(head, stroke)
        val ears = Path().apply {
            moveTo(15f, 18f); lineTo(5f, 13f); lineTo(8f, 23f); close()
            moveTo(33f, 18f); lineTo(43f, 13f); lineTo(40f, 23f); close()
        }
        canvas.drawPath(ears, fill)
        canvas.drawPath(ears, stroke)
        canvas.drawCircle(20f, 25f, 2f, stroke(2f))
        canvas.drawCircle(28f, 25f, 2f, stroke(2f))
        canvas.drawOval(RectF(18f, 30f, 30f, 36f), stroke(2f))
        return bitmap
    }

    private fun trough(): Bitmap {
        val (bitmap, canvas) = canvas()
        val outline = stroke(4f)
        canvas.drawRoundRect(RectF(7f, 18f, 41f, 34f), 4f, 4f, fill())
        canvas.drawRoundRect(RectF(7f, 18f, 41f, 34f), 4f, 4f, outline)
        canvas.drawLine(12f, 36f, 10f, 42f, outline)
        canvas.drawLine(36f, 36f, 38f, 42f, outline)
        return bitmap
    }

    private fun tank(): Bitmap {
        val (bitmap, canvas) = canvas()
        val outline = stroke(3f)
        canvas.drawRect(11f, 13f, 37f, 37f, fill())
        canvas.drawOval(RectF(11f, 8f, 37f, 18f), fill())
        canvas.drawOval(RectF(11f, 8f, 37f, 18f), outline)
        canvas.drawLine(11f, 13f, 11f, 37f, outline)
        canvas.drawLine(37f, 13f, 37f, 37f, outline)
        canvas.drawArc(RectF(11f, 32f, 37f, 42f), 0f, 180f, false, outline)
        return bitmap
    }

    private fun spring(): Bitmap {
        val (bitmap, canvas) = canvas()
        val drop = Path().apply {
            moveTo(24f, 5f)
            cubicTo(20f, 14f, 12f, 22f, 12f, 30f)
            cubicTo(12f, 39f, 17f, 44f, 24f, 44f)
            cubicTo(31f, 44f, 36f, 39f, 36f, 30f)
            cubicTo(36f, 22f, 28f, 14f, 24f, 5f)
            close()
        }
        canvas.drawPath(drop, fill())
        canvas.drawPath(drop, stroke())
        return bitmap
    }

    private fun pond(): Bitmap {
        val (bitmap, canvas) = canvas()
        val wave = stroke(5f).apply { color = Color.WHITE }
        listOf(15f, 25f, 35f).forEach { y ->
            val path = Path().apply {
                moveTo(5f, y)
                cubicTo(12f, y - 6f, 18f, y + 6f, 24f, y)
                cubicTo(30f, y - 6f, 36f, y + 6f, 43f, y)
            }
            canvas.drawPath(path, stroke(8f))
            canvas.drawPath(path, wave)
        }
        return bitmap
    }

    private fun hydrant(): Bitmap {
        val (bitmap, canvas) = canvas()
        val fill = fill()
        val outline = stroke(3f)
        canvas.drawRoundRect(RectF(17f, 10f, 31f, 42f), 4f, 4f, fill)
        canvas.drawRoundRect(RectF(17f, 10f, 31f, 42f), 4f, 4f, outline)
        canvas.drawRoundRect(RectF(10f, 17f, 38f, 27f), 3f, 3f, fill)
        canvas.drawRoundRect(RectF(10f, 17f, 38f, 27f), 3f, 3f, outline)
        canvas.drawLine(13f, 42f, 35f, 42f, outline)
        return bitmap
    }

    private fun other(): Bitmap {
        val (bitmap, canvas) = canvas()
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 38f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText("?", 24f, 38f, stroke(7f).apply {
            style = Paint.Style.STROKE
            textAlign = Paint.Align.CENTER
            textSize = 38f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        canvas.drawText("?", 24f, 38f, text)
        return bitmap
    }

    private fun overflow(count: Int): Bitmap {
        val (bitmap, canvas) = canvas()
        val label = "+$count"
        val textSize = if (count < 10) 29f else 23f
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(21, 21, 21)
            style = Paint.Style.STROKE
            strokeWidth = 6f
            textAlign = Paint.Align.CENTER
            this.textSize = textSize
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val fill = Paint(outline).apply { color = Color.WHITE; style = Paint.Style.FILL }
        canvas.drawText(label, 24f, 33f, outline)
        canvas.drawText(label, 24f, 33f, fill)
        return bitmap
    }
}
