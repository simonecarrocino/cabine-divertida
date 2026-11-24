package br.com.cabinedivertida

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {

    // Navegação simples entre as telas
    private val history = ArrayDeque<Screen>()
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    fun go(s: Screen) {
        history.addLast(screen)
        screen = s
    }

    fun back(): Boolean {
        val previous = history.removeLastOrNull() ?: return false
        screen = previous
        return true
    }

    fun reset(s: Screen) {
        history.clear()
        screen = s
    }

    // Foto atual
    var photo by mutableStateOf<Bitmap?>(null)
    var frame by mutableStateOf(FrameStyle.POLAROID)
    var caption by mutableStateOf("")
    var finalImage by mutableStateOf<Bitmap?>(null)

    // Opções de impressão do modo normal
    var paper by mutableStateOf(PaperSize.P10X15)
    var landscape by mutableStateOf(false)
    var fill by mutableStateOf(true)
    var copies by mutableIntStateOf(1)

    // Modo Evento
    private val eventStore = EventStore(app)
    var event by mutableStateOf(eventStore.load())
        private set
    var customFrame by mutableStateOf<Bitmap?>(null)
        private set

    init {
        if (event.useCustomFrame) customFrame = eventStore.loadCustomFrame()
    }

    fun updateEvent(c: EventConfig) {
        event = c
        eventStore.save(c)
    }

    fun useCustomFrame(b: Bitmap) {
        eventStore.saveCustomFrame(b)
        customFrame = b
        updateEvent(event.copy(useCustomFrame = true))
    }

    private var lastRecent: Bitmap? = null

    fun addRecent(img: Bitmap) {
        if (img === lastRecent) return
        lastRecent = img
        Recents.add(getApplication(), img)
    }

    /** Monta a imagem final (foto + moldura). */
    suspend fun buildFinal(forEvent: Boolean, width: Int = 1800): Bitmap? {
        val p = photo ?: return null
        val ev = event
        val cf = customFrame
        val style = frame
        val text = caption
        return withContext(Dispatchers.Default) {
            if (forEvent) {
                if (ev.useCustomFrame && cf != null) {
                    FrameRenderer.renderCustom(p, cf, width)
                } else {
                    FrameRenderer.render(p, ev.frame, ev.caption.ifBlank { null }, width)
                }
            } else {
                FrameRenderer.render(p, style, text.ifBlank { null }, width)
            }
        }
    }
}
