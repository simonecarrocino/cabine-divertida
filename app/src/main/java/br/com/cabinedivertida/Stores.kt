package br.com.cabinedivertida

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
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

/**
 * Guarda os eventos no aparelho: a configuração de cada um (eventos.json),
 * a moldura própria (molduras/<id>.png) e as fotos tiradas (eventos/<id>/).
 */
class EventStore(private val ctx: Context) {
    private val listFile = File(ctx.filesDir, "eventos.json")
    private val prefs = ctx.getSharedPreferences("evento", Context.MODE_PRIVATE)

    private fun frameFile(id: String) = File(File(ctx.filesDir, "molduras").apply { mkdirs() }, "$id.png")
    private fun photoDir(id: String) = File(File(ctx.filesDir, "eventos"), id).apply { mkdirs() }

    fun loadAll(): List<EventConfig> {
        if (!listFile.exists()) return migrateOld()
        return runCatching {
            val arr = JSONArray(listFile.readText())
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun saveAll(list: List<EventConfig>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        listFile.writeText(arr.toString())
    }

    fun saveCurrentId(id: String) = prefs.edit().putString("currentId", id).apply()
    fun currentId(): String? = prefs.getString("currentId", null)

    fun saveCustomFrame(id: String, bmp: Bitmap) {
        frameFile(id).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun loadCustomFrame(id: String): Bitmap? {
        val f = frameFile(id)
        return if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }

    fun hasCustomFrame(id: String) = frameFile(id).exists()

    fun copyCustomFrame(from: String, to: String) {
        val src = frameFile(from)
        if (src.exists()) src.copyTo(frameFile(to), overwrite = true)
    }

    fun addPhoto(id: String, bmp: Bitmap) {
        val f = File(photoDir(id), "foto_${System.currentTimeMillis()}.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }

    fun photos(id: String): List<File> =
        photoDir(id).listFiles()?.filter { it.extension == "jpg" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun delete(id: String) {
        File(File(ctx.filesDir, "eventos"), id).deleteRecursively()
        frameFile(id).delete()
    }

    /** Traz a configuração da versão anterior (um evento só) para a lista nova. */
    private fun migrateOld(): List<EventConfig> {
        if (!prefs.contains("name")) return emptyList()
        val d = EventConfig()
        val old = EventConfig(
            name = prefs.getString("name", null) ?: d.name,
            caption = prefs.getString("caption", null) ?: d.caption,
            frame = enumOr(prefs.getString("frame", null), d.frame),
            format = enumOr(prefs.getString("format", null), d.format),
            paper = enumOr(prefs.getString("paper", null), d.paper),
            landscape = prefs.getBoolean("landscape", d.landscape),
            fill = prefs.getBoolean("fill", d.fill),
            copies = prefs.getInt("copies", d.copies),
            allowPrint = prefs.getBoolean("allowPrint", d.allowPrint),
            autoPrint = prefs.getBoolean("autoPrint", d.autoPrint),
            allowWhats = prefs.getBoolean("allowWhats", d.allowWhats),
            allowInstagram = prefs.getBoolean("allowInstagram", d.allowInstagram),
            igCaption = prefs.getString("igCaption", null) ?: d.igCaption,
            pin = prefs.getString("pin", null) ?: d.pin,
        )
        val oldFrame = File(ctx.filesDir, "moldura_evento.png")
        val withFrame = if (prefs.getBoolean("custom", false) && oldFrame.exists()) {
            oldFrame.copyTo(frameFile(old.id), overwrite = true)
            old.copy(useCustomFrame = true)
        } else old
        val list = listOf(withFrame)
        saveAll(list)
        saveCurrentId(withFrame.id)
        return list
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        runCatching { enumValueOf<T>(name ?: "") }.getOrDefault(fallback)

    private fun toJson(c: EventConfig) = JSONObject().apply {
        put("id", c.id)
        put("createdAt", c.createdAt)
        put("name", c.name)
        put("frontCamera", c.frontCamera)
        put("caption", c.caption)
        put("frame", c.frame.name)
        put("useCustomFrame", c.useCustomFrame)
        put("format", c.format.name)
        put("paper", c.paper.name)
        put("landscape", c.landscape)
        put("fill", c.fill)
        put("copies", c.copies)
        put("allowPrint", c.allowPrint)
        put("autoPrint", c.autoPrint)
        put("allowWhats", c.allowWhats)
        put("allowInstagram", c.allowInstagram)
        put("igCaption", c.igCaption)
        put("pin", c.pin)
    }

    private fun fromJson(o: JSONObject): EventConfig {
        val d = EventConfig()
        val id = o.optString("id", d.id)
        return EventConfig(
            id = id,
            createdAt = o.optLong("createdAt", d.createdAt),
            name = o.optString("name", d.name),
            frontCamera = o.optBoolean("frontCamera", d.frontCamera),
            caption = o.optString("caption", d.caption),
            frame = enumOr(o.optString("frame"), d.frame),
            useCustomFrame = o.optBoolean("useCustomFrame", false) && hasCustomFrame(id),
            format = enumOr(o.optString("format"), d.format),
            paper = enumOr(o.optString("paper"), d.paper),
            landscape = o.optBoolean("landscape", d.landscape),
            fill = o.optBoolean("fill", d.fill),
            copies = o.optInt("copies", d.copies),
            allowPrint = o.optBoolean("allowPrint", d.allowPrint),
            autoPrint = o.optBoolean("autoPrint", d.autoPrint),
            allowWhats = o.optBoolean("allowWhats", d.allowWhats),
            allowInstagram = o.optBoolean("allowInstagram", d.allowInstagram),
            igCaption = o.optString("igCaption", d.igCaption),
            pin = o.optString("pin", d.pin),
        )
    }
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
