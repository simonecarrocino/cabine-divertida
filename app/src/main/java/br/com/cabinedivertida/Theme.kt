package br.com.cabinedivertida

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Cores {
    val Fundo = Color(0xFFFFF3C4)
    val Bordo = Color(0xFF7B1E3A)
    val BordoEscuro = Color(0xFF4A0E1F)
    val Texto2 = Color(0xFF7A3A48)
    val Amarelo = Color(0xFFFFC93C)
    val AmareloClaro = Color(0xFFFFE08A)
    val AmareloSuave = Color(0xFFFFE8A3)
    val Linha = Color(0xFFEBC96A)
    val Whats = Color(0xFF146C3A)
    val Erro = Color(0xFFB3261E)
}

@Composable
fun CabineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Cores.Bordo,
            onPrimary = Color.White,
            secondary = Cores.Amarelo,
            onSecondary = Cores.BordoEscuro,
            background = Cores.Fundo,
            onBackground = Cores.BordoEscuro,
            surface = Color.White,
            onSurface = Cores.BordoEscuro,
            outline = Cores.Linha,
        ),
        content = content,
    )
}
