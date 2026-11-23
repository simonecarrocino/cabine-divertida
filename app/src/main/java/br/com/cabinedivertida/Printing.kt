package br.com.cabinedivertida

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object Printing {
    /**
     * Abre a tela de impressão do Android já com o papel escolhido.
     * Cada cópia vira uma página do documento.
     */
    fun print(activity: Activity, bmp: Bitmap, paper: PaperSize, landscape: Boolean, fill: Boolean, copies: Int) {
        val pm = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val media = paper.mediaSize().let { if (landscape) it.asLandscape() else it.asPortrait() }
        val attrs = PrintAttributes.Builder()
            .setMediaSize(media)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        pm.print("Cabine Divertida", PhotoPrintAdapter(activity, bmp, fill, copies.coerceAtLeast(1)), attrs)
    }
}

private class PhotoPrintAdapter(
    private val ctx: Context,
    private val bmp: Bitmap,
    private val fill: Boolean,
    private val copies: Int,
) : PrintDocumentAdapter() {

    private var attributes: PrintAttributes? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        attributes = newAttributes
        val info = PrintDocumentInfo.Builder("cabine-divertida.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO)
            .setPageCount(copies)
            .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        val attrs = attributes
        if (attrs == null) {
            callback.onWriteFailed("Sem configuração de página")
            return
        }
        val doc = PrintedPdfDocument(ctx, attrs)
        try {
            for (i in 0 until copies) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }
                val page = doc.startPage(i)
                val canvas = page.canvas
                val pw = canvas.width.toFloat()
                val ph = canvas.height.toFloat()
                // Gira a foto se o papel estiver na orientação contrária.
                val img = if ((pw > ph) != (bmp.width > bmp.height)) Images.rotate(bmp, 90f) else bmp
                val scale = if (fill) max(pw / img.width, ph / img.height) else min(pw / img.width, ph / img.height)
                val w = img.width * scale
                val h = img.height * scale
                val left = (pw - w) / 2f
                val top = (ph - h) / 2f
                canvas.drawBitmap(img, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
                doc.finishPage(page)
            }
            FileOutputStream(destination.fileDescriptor).use { doc.writeTo(it) }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        } finally {
            doc.close()
        }
    }
}
