package br.com.cabinedivertida

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Cores {
    val Fundo = Color(0xFFFFF8EC)
    val Bordo = Color(0xFFB3122E)
    val BordoEscuro = Color(0xFF12372A)
    val Texto2 = Color(0xFF4E6B5C)
    val Amarelo = Color(0xFFE8B53D)
    val AmareloClaro = Color(0xFFF6E3B4)
    val AmareloSuave = Color(0xFFFBEFD0)
    val Linha = Color(0xFFE2C98A)
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
