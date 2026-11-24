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
    var format by mutableStateOf(OutputFormat.F10X15)
    var finalImage by mutableStateOf<Bitmap?>(null)

    // Opções de impressão do modo normal
    var paper by mutableStateOf(PaperSize.P10X15)
    var landscape by mutableStateOf(false)
    var fill by mutableStateOf(true)
    var copies by mutableIntStateOf(1)

    // Modo Evento: lista de eventos salvos e o evento aberto
    private val eventStore = EventStore(app)
    var events by mutableStateOf(eventStore.loadAll())
        private set
    var event by mutableStateOf(
        events.firstOrNull { it.id == eventStore.currentId() } ?: events.firstOrNull() ?: EventConfig()
    )
        private set
    var customFrame by mutableStateOf<Bitmap?>(null)
        private set

    /** true quando a tela "foto pronta" foi aberta pela galeria do evento (reenviar/reimprimir). */
    var reviewing by mutableStateOf(false)

    init {
        loadFrameFor(event)
    }

    private fun loadFrameFor(c: EventConfig) {
        customFrame = if (c.useCustomFrame) eventStore.loadCustomFrame(c.id) else null
    }

    fun openEvent(c: EventConfig) {
        event = c
        eventStore.saveCurrentId(c.id)
        loadFrameFor(c)
    }

    fun newEvent() {
        val n = events.size + 1
        val c = EventConfig(name = "Evento $n")
        events = listOf(c) + events
        eventStore.saveAll(events)
        openEvent(c)
    }

    fun duplicateEvent(c: EventConfig) {
        val copy = c.copy(id = java.util.UUID.randomUUID().toString(), createdAt = System.currentTimeMillis(), name = c.name + " (cópia)")
        eventStore.copyCustomFrame(c.id, copy.id)
        events = listOf(copy) + events
        eventStore.saveAll(events)
        openEvent(copy)
    }

    fun deleteEvent(c: EventConfig) {
        eventStore.delete(c.id)
        events = events.filter { it.id != c.id }
        eventStore.saveAll(events)
        if (event.id == c.id) {
            event = events.firstOrNull() ?: EventConfig()
            loadFrameFor(event)
        }
    }

    fun updateEvent(c: EventConfig) {
        event = c
        events = if (events.any { it.id == c.id }) events.map { if (it.id == c.id) c else it } else listOf(c) + events
        eventStore.saveAll(events)
        eventStore.saveCurrentId(c.id)
    }

    fun useCustomFrame(b: Bitmap) {
        eventStore.saveCustomFrame(event.id, b)
        customFrame = b
        updateEvent(event.copy(useCustomFrame = true))
    }

    fun eventPhotos(c: EventConfig): List<java.io.File> = eventStore.photos(c.id)

    /** Guarda a foto na pasta do evento aberto (chamar fora da thread principal). */
    fun saveEventPhoto(img: Bitmap) {
        eventStore.addPhoto(event.id, img)
    }

    private var lastRecent: Bitmap? = null

    fun addRecent(img: Bitmap) {
        if (img === lastRecent) return
        lastRecent = img
        Recents.add(getApplication(), img)
    }

    /** Escolhe o formato; os de foto já escolhem o papel correspondente. */
    fun chooseFormat(f: OutputFormat) {
        format = f
        f.paper?.let { paper = it }
    }

    suspend fun buildFinal(forEvent: Boolean, maxSide: Int = 3000): Bitmap? {
        val p = photo ?: return null
        val ev = event
        val cf = customFrame
        val style = frame
        val text = caption
        val fmt = format
        return withContext(Dispatchers.Default) {
            if (forEvent) {
                if (ev.useCustomFrame && cf != null) {
                    FrameRenderer.renderCustom(p, cf, ev.format, maxSide)
                } else {
                    FrameRenderer.render(p, ev.frame, ev.caption.ifBlank { null }, ev.format, maxSide)
                }
            } else {
                FrameRenderer.render(p, style, text.ifBlank { null }, fmt, maxSide)
            }
        }
    }
}
