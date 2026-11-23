package br.com.cabinedivertida

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import java.io.File

object Images {
    /** Abre uma foto da galeria já na orientação certa e num tamanho seguro. */
    fun load(ctx: Context, uri: Uri, maxSide: Int = 2400): Bitmap {
        val source = ImageDecoder.createSource(ctx.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val s = maxSide.toFloat() / maxOf(w, h)
            if (s < 1f) {
                decoder.setTargetSize((w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1))
            }
        }
    }

    fun scaleDown(b: Bitmap, maxSide: Int = 2400): Bitmap {
        val s = maxSide.toFloat() / maxOf(b.width, b.height)
        return if (s >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
    }

    fun rotate(b: Bitmap, degrees: Float, mirror: Boolean = false): Bitmap {
        if (degrees == 0f && !mirror) return b
        val m = Matrix()
        m.postRotate(degrees)
        if (mirror) m.postScale(-1f, 1f)
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    fun loadFile(f: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

/** Guarda a configuração do Modo Evento no aparelho. */
class EventStore(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("evento", Context.MODE_PRIVATE)
    private val frameFile: File get() = File(ctx.filesDir, "moldura_evento.png")

    fun load(): EventConfig {
        val d = EventConfig()
        return EventConfig(
            name = prefs.getString("name", null) ?: d.name,
            caption = prefs.getString("caption", null) ?: d.caption,
            frame = runCatching { FrameStyle.valueOf(prefs.getString("frame", null) ?: "") }.getOrDefault(d.frame),
            useCustomFrame = prefs.getBoolean("custom", false) && frameFile.exists(),
            paper = runCatching { PaperSize.valueOf(prefs.getString("paper", null) ?: "") }.getOrDefault(d.paper),
            landscape = prefs.getBoolean("landscape", d.landscape),
            fill = prefs.getBoolean("fill", d.fill),
            copies = prefs.getInt("copies", d.copies),
            allowPrint = prefs.getBoolean("allowPrint", d.allowPrint),
            autoPrint = prefs.getBoolean("autoPrint", d.autoPrint),
            allowWhats = prefs.getBoolean("allowWhats", d.allowWhats),
            pin = prefs.getString("pin", null) ?: d.pin,
        )
    }

    fun save(c: EventConfig) {
        prefs.edit()
            .putString("name", c.name)
            .putString("caption", c.caption)
            .putString("frame", c.frame.name)
            .putBoolean("custom", c.useCustomFrame)
            .putString("paper", c.paper.name)
            .putBoolean("landscape", c.landscape)
            .putBoolean("fill", c.fill)
            .putInt("copies", c.copies)
            .putBoolean("allowPrint", c.allowPrint)
            .putBoolean("autoPrint", c.autoPrint)
            .putBoolean("allowWhats", c.allowWhats)
            .putString("pin", c.pin)
            .apply()
    }

    fun saveCustomFrame(bmp: Bitmap) {
        frameFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun loadCustomFrame(): Bitmap? =
        if (frameFile.exists()) BitmapFactory.decodeFile(frameFile.path) else null
}

/** Últimas fotos feitas no app, para a lista "Recentes". */
object Recents {
    private fun dir(ctx: Context) = File(ctx.filesDir, "recentes").apply { mkdirs() }

    fun add(ctx: Context, bmp: Bitmap) {
        val f = File(dir(ctx), "foto_${System.currentTimeMillis()}.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        list(ctx).drop(12).forEach { it.delete() }
    }

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles()?.filter { it.extension == "jpg" }?.sortedByDescending { it.lastModified() } ?: emptyList()
}
