package br.com.cabinedivertida

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /** Arquivo de evento aberto de fora do app (por exemplo, tocando nele no WhatsApp). */
    private val incomingEvent = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readIntent(intent)
        setContent {
            CabineTheme {
                val vm: AppViewModel = viewModel()
                App(vm)
                IncomingEventHandler(vm, incomingEvent.value) { incomingEvent.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) incomingEvent.value = intent.data
    }
}

@Composable
private fun IncomingEventHandler(vm: AppViewModel, uri: Uri?, done: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        val c = withContext(Dispatchers.IO) { runCatching { vm.importEvent(uri) }.getOrNull() }
        Toast.makeText(
            ctx,
            if (c != null) "Evento \"${c.name}\" importado" else "Esse arquivo não é um evento da Cabine Divertida",
            Toast.LENGTH_LONG,
        ).show()
        if (c != null) {
            vm.reset(Screen.Home)
            vm.go(Screen.Events)
        }
        done()
    }
}

@Composable
fun App(vm: AppViewModel) {
    val activity = LocalContext.current.findActivity()
    val screen = vm.screen
    val inEvent = screen == Screen.EventCamera || (screen == Screen.EventResult && !vm.reviewing)
    val dark = screen == Screen.Camera || screen == Screen.EventCamera

    // No modo evento o botão voltar não faz nada: só sai com o PIN.
    BackHandler {
        if (!inEvent && !vm.back()) activity?.finish()
    }

    Box(
        Modifier.fillMaxSize()
            .background(if (dark) Cores.BordoEscuro else Cores.Fundo)
            .safeDrawingPadding(),
    ) {
        when (screen) {
            Screen.Home -> HomeScreen(vm)
            Screen.Camera -> CameraScreen(vm)
            Screen.Frame -> FrameScreen(vm)
            Screen.Share -> ShareScreen(vm)
            Screen.Print -> PrintScreen(vm)
            Screen.EventConfig -> EventConfigScreen(vm)
            Screen.EventCamera -> EventCameraScreen(vm)
            Screen.EventResult -> EventResultScreen(vm)
            Screen.Events -> EventsScreen(vm)
            Screen.EventPhotos -> EventPhotosScreen(vm)
            Screen.Prompts -> PromptsScreen(vm)
        }
    }
}
