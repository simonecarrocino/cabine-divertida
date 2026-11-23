package br.com.cabinedivertida

import android.print.PrintAttributes
import kotlin.math.roundToInt

/** Molduras prontas. As cores são ARGB (0xAARRGGBB). */
enum class FrameStyle(val label: String, val background: Long, val captionColor: Long) {
    POLAROID("Polaroid", 0xFFFFFFFF, 0xFFB3122E),
    CLASSICA("Clássica", 0xFF3B2A1E, 0xFFC9A56B),
    FESTA("Festa", 0xFFFFF4D6, 0xFFC2410C),
    NATUREZA("Natureza", 0xFFDCE8D5, 0xFF2E5A40),
    SIMPLES("Simples", 0xFF12372A, 0xFFFFFFFF),
}

/** Tamanhos de impressão oferecidos. */
enum class PaperSize(val label: String, val hint: String, val widthCm: Float, val heightCm: Float) {
    P10X15("10×15", "Foto padrão", 10f, 15f),
    P13X18("13×18", "Porta-retrato", 13f, 18f),
    P15X21("15×21", "Média", 15f, 21f),
    P20X25("20×25", "Quadro", 20f, 25f),
    P20X30("20×30", "Pôster", 20f, 30f),
    A4("A4", "21×29,7", 21f, 29.7f);

    fun mediaSize(): PrintAttributes.MediaSize =
        if (this == A4) {
            PrintAttributes.MediaSize.ISO_A4
        } else {
            PrintAttributes.MediaSize("cabine_$name", "$label cm", cmToMils(widthCm), cmToMils(heightCm))
        }

    private fun cmToMils(cm: Float): Int = (cm / 2.54f * 1000f).roundToInt()
}

/** Configuração do Modo Evento, definida uma vez pela organizadora. */
data class EventConfig(
    val name: String = "Meu evento",
    val caption: String = "",
    val frame: FrameStyle = FrameStyle.POLAROID,
    val useCustomFrame: Boolean = false,
    val paper: PaperSize = PaperSize.P10X15,
    val landscape: Boolean = false,
    val fill: Boolean = true,
    val copies: Int = 1,
    val allowPrint: Boolean = true,
    val autoPrint: Boolean = false,
    val allowWhats: Boolean = true,
    val allowInstagram: Boolean = false,
    val igCaption: String = "",
    val pin: String = "1234",
)

sealed interface Screen {
    data object Home : Screen
    data object Camera : Screen
    data object Frame : Screen
    data object Share : Screen
    data object Print : Screen
    data object EventConfig : Screen
    data object EventCamera : Screen
    data object EventResult : Screen
}
