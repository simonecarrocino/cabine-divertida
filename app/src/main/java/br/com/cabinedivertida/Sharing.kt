package br.com.cabinedivertida

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

object Sharing {

    private fun cacheImage(ctx: Context, bmp: Bitmap): Uri {
        val dir = File(ctx.cacheDir, "compartilhar").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "cabine_${System.currentTimeMillis()}.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    }

    /** Aceita "(22) 99999-0000" e devolve "5522999990000". Retorna null se não parecer um celular. */
    fun normalizePhone(raw: String): String? {
        var d = raw.filter { it.isDigit() }
        val international = raw.trim().startsWith("+")
        if (!international) {
            // Contatos salvos como "0 21 99999-0000" ou "0 41 21 99999-0000" (com operadora)
            d = d.trimStart('0')
            if (d.length in 12..13 && !d.startsWith("55")) d = d.drop(2)
        }
        return when {
            d.length in 10..11 && !international -> "55$d"
            d.length in 12..13 && d.startsWith("55") -> d
            international && d.length in 8..15 -> d
            else -> null
        }
    }

    private fun baseIntent(ctx: Context, bmp: Bitmap): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, cacheImage(ctx, bmp))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /** Abre o WhatsApp com a foto anexada. Com número, já abre a conversa daquele contato. */
    fun whatsapp(ctx: Context, bmp: Bitmap, phone: String? = null) {
        val base = baseIntent(ctx, bmp)
        if (phone != null) base.putExtra("jid", "$phone@s.whatsapp.net")
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            try {
                ctx.startActivity(Intent(base).setPackage(pkg))
                return
            } catch (_: ActivityNotFoundException) {
                // tenta o próximo
            }
        }
        ctx.startActivity(Intent.createChooser(base, "Enviar foto"))
    }

    /**
     * Abre o Instagram com a foto carregada. A pessoa escolhe Feed, Stories ou Reels lá dentro.
     * Posta na conta que estiver logada no app do Instagram deste celular.
     * Retorna false se o Instagram não estiver instalado.
     */
    fun instagram(ctx: Context, bmp: Bitmap): Boolean =
        try {
            ctx.startActivity(Intent(baseIntent(ctx, bmp)).setPackage("com.instagram.android"))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }

    /** Limpa o @ digitado: "@ Maria.Silva " vira "maria.silva". */
    fun normalizeHandle(raw: String): String =
        raw.trim().removePrefix("@").filter { it.isLetterOrDigit() || it == '.' || it == '_' }.lowercase()

    fun copyText(ctx: Context, label: String, text: String) {
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun other(ctx: Context, bmp: Bitmap) {
        ctx.startActivity(Intent.createChooser(baseIntent(ctx, bmp), "Compartilhar foto"))
    }

    /** Salva na galeria, no álbum "Cabine Divertida". */
    fun saveToGallery(ctx: Context, bmp: Bitmap): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "cabine_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Cabine Divertida")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) } ?: false
    }
}
