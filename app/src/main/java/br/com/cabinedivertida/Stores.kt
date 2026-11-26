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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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

    /**
     * Gera um arquivo .zip com a configuração (evento.json) e a moldura própria (moldura.png),
     * para mandar a colegas pelo WhatsApp.
     */
    fun export(c: EventConfig): File {
        val dir = File(ctx.cacheDir, "compartilhar").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val name = c.name.trim().filter { it.isLetterOrDigit() || it in " -_" }.take(50).trim().ifEmpty { "evento" }
        val out = File(dir, "Evento - $name.zip")
        ZipOutputStream(out.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("evento.json"))
            zip.write(toJson(c).toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            val frame = frameFile(c.id)
            if (c.useCustomFrame && frame.exists()) {
                zip.putNextEntry(ZipEntry("moldura.png"))
                frame.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return out
    }

    /**
     * Lê um evento exportado. Só aceita os dois arquivos esperados e ignora o resto.
     * Devolve o evento lido (com a moldura já guardada) ou null se o arquivo não for de evento.
     */
    fun import(input: java.io.InputStream): EventConfig? {
        var json: String? = null
        var frameBytes: ByteArray? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when (entry.name) {
                    "evento.json" -> json = readLimited(zip, 200_000)?.toString(Charsets.UTF_8)
                    "moldura.png" -> frameBytes = readLimited(zip, 30_000_000)
                }
                zip.closeEntry()
            }
        }
        val text = json ?: return null
        val parsed = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (!parsed.has("name")) return null
        val bytes = frameBytes
        val id = parsed.optString("id").ifBlank { java.util.UUID.randomUUID().toString() }
            .filter { it.isLetterOrDigit() || it == '-' }.take(64)
        parsed.put("id", id)
        if (bytes != null) {
            // Só guarda se for mesmo uma imagem
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth > 0) frameFile(id).writeBytes(bytes)
        }
        return fromJson(parsed)
    }

    private fun readLimited(input: java.io.InputStream, max: Int): ByteArray? {
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16_384)
        var total = 0
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            total += n
            if (total > max) return null
            buf.write(chunk, 0, n)
        }
        return buf.toByteArray()
    }

    private fun toJson(c: EventConfig) = JSONObject().apply {
        put("id", c.id)
        put("createdAt", c.createdAt)
        put("name", c.name)
        put("frontCamera", c.frontCamera)
        put("timer", c.timer)
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
            timer = o.optInt("timer", d.timer),
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

/**
 * Biblioteca de molduras: toda arte enviada (no evento ou na foto individual) fica guardada aqui
 * para usar de novo. Arquivos iguais são guardados uma vez só.
 */
class FrameLibrary(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "biblioteca").apply { mkdirs() }

    fun list(): List<File> =
        dir.listFiles()?.filter { it.extension == "png" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    private fun md5(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun store(bytes: ByteArray): File {
        val f = File(dir, md5(bytes) + ".png")
        if (!f.exists()) f.writeBytes(bytes) else f.setLastModified(System.currentTimeMillis())
        return f
    }

    fun add(b: Bitmap): File {
        val out = java.io.ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.PNG, 100, out)
        return store(out.toByteArray())
    }

    fun addFile(src: File): File? = if (src.exists()) store(src.readBytes()) else null

    /** Traz para a biblioteca as molduras que já estavam nos eventos. */
    fun importEventFrames() {
        File(ctx.filesDir, "molduras").listFiles()?.filter { it.extension == "png" }?.forEach { f ->
            val bytes = f.readBytes()
            val target = File(dir, md5(bytes) + ".png")
            if (!target.exists()) {
                target.writeBytes(bytes)
                target.setLastModified(f.lastModified())
            }
        }
    }

    fun delete(f: File) {
        if (f.parentFile == dir) f.delete()
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
