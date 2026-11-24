package br.com.cabinedivertida

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Junta a foto e a moldura numa imagem final, no formato escolhido. */
object FrameRenderer {

    fun cropToAspect(src: Bitmap, aspect: Float): Bitmap {
        val w = src.width
        val h = src.height
        return if (w.toFloat() / h > aspect) {
            val nw = (h * aspect).toInt().coerceIn(1, w)
            Bitmap.createBitmap(src, (w - nw) / 2, 0, nw, h)
        } else {
            val nh = (w / aspect).toInt().coerceIn(1, h)
            Bitmap.createBitmap(src, 0, (h - nh) / 2, w, nh)
        }
    }

    /** Tamanho em pixels do formato, limitado para não estourar a memória do celular. */
    fun targetSize(format: OutputFormat, maxSide: Int): Pair<Int, Int> {
        val s = min(1f, maxSide.toFloat() / max(format.widthPx, format.heightPx))
        return (format.widthPx * s).roundToInt().coerceAtLeast(1) to (format.heightPx * s).roundToInt().coerceAtLeast(1)
    }

    /** Margem da moldura em fração da largura. */
    private fun sideFraction(style: FrameStyle) = when (style) {
        FrameStyle.POLAROID -> 0.05f
        FrameStyle.CLASSICA -> 0.09f
        FrameStyle.FESTA -> 0.07f
        FrameStyle.NATUREZA -> 0.08f
        FrameStyle.SIMPLES -> 0.025f
    }

    /** Faixa da legenda embaixo, em fração da largura. */
    private fun captionFraction(style: FrameStyle, caption: String?) = when {
        style == FrameStyle.POLAROID -> 0.16f
        !caption.isNullOrBlank() -> 0.11f
        else -> 0f
    }

    /** Proporção (largura/altura) da área da foto dentro da moldura — usada no visor da câmera. */
    fun photoAspect(style: FrameStyle, caption: String?, format: OutputFormat): Float {
        val s = sideFraction(style)
        val c = captionFraction(style, caption)
        val pw = 1f - 2 * s
        val ph = 1f / format.aspect - 2 * s - c
        return (pw / ph).coerceIn(0.2f, 5f)
    }

    fun render(photo: Bitmap, style: FrameStyle, caption: String?, format: OutputFormat, maxSide: Int = 3000): Bitmap {
        val (width, height) = targetSize(format, maxSide)
        val w = width.toFloat()
        val h = height.toFloat()
        val side = w * sideFraction(style)
        val captionArea = w * captionFraction(style, caption)

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(style.background.toInt())

        val dst = RectF(side, side, w - side, h - side - captionArea)
        canvas.drawBitmap(cropToAspect(photo, dst.width() / dst.height()), null, dst, Paint(Paint.FILTER_BITMAP_FLAG))

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
            val maxText = w - 2 * side
            while (text.measureText(caption) > maxText && text.textSize > w * 0.025f) {
                text.textSize *= 0.92f
            }
            val centerY = dst.bottom + (h - dst.bottom) / 2f
            canvas.drawText(caption, w / 2f, centerY - (text.descent() + text.ascent()) / 2f, text)
        }
        return out
    }

    /**
     * Moldura própria (PNG com o meio transparente): a foto fica por trás da arte.
     * Se a arte tiver outra proporção, ela é recortada pelo centro para caber no formato.
     */
    fun renderCustom(photo: Bitmap, frame: Bitmap, format: OutputFormat, maxSide: Int = 3000): Bitmap {
        val (width, height) = targetSize(format, maxSide)
        val aspect = width.toFloat() / height
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(0xFFFFFFFF.toInt())
        val full = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(cropToAspect(photo, aspect), null, full, paint)
        canvas.drawBitmap(cropToAspect(frame, aspect), null, full, paint)
        return out
    }
}
