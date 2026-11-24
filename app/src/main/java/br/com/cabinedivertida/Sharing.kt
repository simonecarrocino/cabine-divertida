package br.com.cabinedivertida

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
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
    fun whatsapp(ctx: Context, bmp: Bitmap, phone: String? = null, text: String? = null) {
        val base = baseIntent(ctx, bmp)
        if (phone != null) base.putExtra("jid", "$phone@s.whatsapp.net")
        // O WhatsApp usa esse texto como legenda da foto
        if (!text.isNullOrBlank()) base.putExtra(Intent.EXTRA_TEXT, text)
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

    private const val INSTAGRAM = "com.instagram.android"

    /**
     * Pergunta ao Instagram instalado para quais destinos ele aceita uma foto
     * (Story, Reels, Post) e devolve a tela de cada um. Mensagens diretas ficam de fora.
     * A lista depende da versão do Instagram do celular.
     */
    fun instagramTargets(ctx: Context): Map<IgTarget, ComponentName> {
        val probe = Intent(Intent.ACTION_SEND).setType("image/jpeg").setPackage(INSTAGRAM)
        val found = runCatching {
            @Suppress("DEPRECATION")
            ctx.packageManager.queryIntentActivities(probe, 0)
        }.getOrDefault(emptyList())
        val out = linkedMapOf<IgTarget, ComponentName>()
        for (ri in found) {
            val ai = ri.activityInfo ?: continue
            val name = ai.name.lowercase()
            val label = ri.loadLabel(ctx.packageManager).toString().lowercase()
            val target = when {
                "direct" in name || "direct" in label || "chat" in label || "mensag" in label || "message" in label || "conversa" in label -> null
                "story" in name || "stories" in name || "story" in label || "stories" in label -> IgTarget.STORY
                "reel" in name || "clips" in name || "reel" in label -> IgTarget.REELS
                else -> IgTarget.POST
            }
            if (target != null && target !in out) out[target] = ComponentName(ai.packageName, ai.name)
        }
        return IgTarget.entries.mapNotNull { t -> out[t]?.let { t to it } }.toMap()
    }

    /**
     * Abre o Instagram com a foto, direto no destino escolhido (Story, Reels ou Post).
     * Sem destino, o Instagram decide. Posta na conta logada no Instagram deste celular.
     * Retorna false se o Instagram não estiver instalado.
     */
    fun instagram(ctx: Context, bmp: Bitmap, target: ComponentName? = null): Boolean {
        val intent = Intent(baseIntent(ctx, bmp))
        if (target != null) intent.component = target else intent.setPackage(INSTAGRAM)
        return try {
            ctx.startActivity(intent)
            true
        } catch (_: Exception) {
            if (target == null) return false
            try {
                ctx.startActivity(Intent(baseIntent(ctx, bmp)).setPackage(INSTAGRAM))
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
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
