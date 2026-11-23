package br.com.cabinedivertida

import android.content.ActivityNotFoundException
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
        val d = raw.filter { it.isDigit() }
        return when (d.length) {
            10, 11 -> "55$d"
            12, 13 -> d
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
