package br.com.cabinedivertida

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/** Junta a foto e a moldura numa imagem final em alta resolução. */
object FrameRenderer {

    private const val PHOTO_ASPECT = 3f / 4f // largura / altura

    fun cropToAspect(src: Bitmap, aspect: Float): Bitmap {
        val w = src.width
        val h = src.height
        return if (w.toFloat() / h > aspect) {
            val nw = (h * aspect).toInt().coerceAtLeast(1)
            Bitmap.createBitmap(src, (w - nw) / 2, 0, nw, h)
        } else {
            val nh = (w / aspect).toInt().coerceAtLeast(1)
            Bitmap.createBitmap(src, 0, (h - nh) / 2, w, nh)
        }
    }

    fun render(photo: Bitmap, style: FrameStyle, caption: String?, width: Int = 1800): Bitmap {
        val w = width.toFloat()
        val side = when (style) {
            FrameStyle.POLAROID -> w * 0.05f
            FrameStyle.CLASSICA -> w * 0.09f
            FrameStyle.FESTA -> w * 0.07f
            FrameStyle.NATUREZA -> w * 0.08f
            FrameStyle.SIMPLES -> w * 0.025f
        }
        val captionArea = when {
            style == FrameStyle.POLAROID -> w * 0.16f
            !caption.isNullOrBlank() -> w * 0.11f
            else -> 0f
        }
        val photoW = w - 2 * side
        val photoH = photoW / PHOTO_ASPECT
        val h = side * 2 + photoH + captionArea

        val out = Bitmap.createBitmap(width, h.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(style.background.toInt())

        val dst = RectF(side, side, side + photoW, side + photoH)
        canvas.drawBitmap(cropToAspect(photo, PHOTO_ASPECT), null, dst, Paint(Paint.FILTER_BITMAP_FLAG))

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE }
        when (style) {
            FrameStyle.CLASSICA -> {
                stroke.color = 0xFFC9A56B.toInt()
                stroke.strokeWidth = w * 0.012f
                val i = w * 0.025f
                canvas.drawRect(i, i, w - i, h - i, stroke)
                stroke.strokeWidth = w * 0.006f
                val g = w * 0.015f
                canvas.drawRect(dst.left - g, dst.top - g, dst.right + g, dst.bottom + g, stroke)
            }
            FrameStyle.FESTA -> {
                stroke.color = 0xFFC2410C.toInt()
                stroke.strokeWidth = w * 0.025f
                stroke.pathEffect = DashPathEffect(floatArrayOf(w * 0.05f, w * 0.03f), 0f)
                val i = stroke.strokeWidth / 2
                canvas.drawRect(i, i, w - i, h - i, stroke)
            }
            FrameStyle.NATUREZA -> {
                stroke.color = 0xFF4F7D62.toInt()
                stroke.strokeWidth = w * 0.01f
                val i = w * 0.03f
                canvas.drawRoundRect(RectF(i, i, w - i, h - i), w * 0.02f, w * 0.02f, stroke)
            }
            else -> Unit
        }

        if (!caption.isNullOrBlank()) {
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.captionColor.toInt()
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = w * 0.05f
            }
            // Diminui a letra se o texto for comprido demais.
            val maxText = w - 2 * side
            while (text.measureText(caption) > maxText && text.textSize > w * 0.025f) {
                text.textSize *= 0.92f
            }
            val centerY = dst.bottom + (h - dst.bottom) / 2f
            canvas.drawText(caption, w / 2f, centerY - (text.descent() + text.ascent()) / 2f, text)
        }
        return out
    }

    /** Moldura própria (PNG com o meio transparente): a foto fica por trás da arte. */
    fun renderCustom(photo: Bitmap, frame: Bitmap, width: Int = 1800): Bitmap {
        val h = (width.toFloat() * frame.height / frame.width).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(0xFFFFFFFF.toInt())
        val full = RectF(0f, 0f, width.toFloat(), h.toFloat())
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(cropToAspect(photo, width.toFloat() / h), null, full, paint)
        canvas.drawBitmap(frame, null, full, paint)
        return out
    }
}
