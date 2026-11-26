package br.com.cabinedivertida

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
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

    // Biblioteca de molduras (todas as artes já enviadas)
    private val library = FrameLibrary(app)
    var libraryFrames by mutableStateOf<List<Pair<java.io.File, Bitmap>>>(emptyList())
        private set

    // Moldura própria escolhida na foto individual (null = moldura pronta do app)
    var indivFrameFile by mutableStateOf<java.io.File?>(null)
        private set
    var indivFrame by mutableStateOf<Bitmap?>(null)
        private set

    init {
        loadFrameFor(event)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { library.importEventFrames() } }
            refreshLibrary()
        }
    }

    suspend fun refreshLibrary() {
        libraryFrames = withContext(Dispatchers.IO) {
            library.list().mapNotNull { f -> Images.loadFile(f, 320)?.let { f to it } }
        }
    }

    /** Foto individual: usar uma moldura pronta do app. */
    fun chooseStyle(s: FrameStyle) {
        frame = s
        indivFrameFile = null
        indivFrame = null
    }

    /** Foto individual: usar uma moldura da biblioteca. */
    fun chooseLibraryFrame(f: java.io.File) {
        viewModelScope.launch {
            val b = withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(f.path) } ?: return@launch
            indivFrameFile = f
            indivFrame = b
        }
    }

    /** Foto individual: subir uma moldura nova (fica guardada na biblioteca). */
    fun uploadIndividualFrame(b: Bitmap) {
        viewModelScope.launch {
            val ready = withContext(Dispatchers.Default) { FrameTools.autoTransparentWindow(b) }
            val f = withContext(Dispatchers.IO) { library.add(ready) }
            refreshLibrary()
            indivFrameFile = f
            indivFrame = ready
        }
    }

    /** Evento: usar uma moldura da biblioteca. */
    fun useLibraryFrameForEvent(f: java.io.File) {
        val id = event.id
        viewModelScope.launch {
            val b = withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(f.path) } ?: return@launch
            withContext(Dispatchers.IO) { eventStore.saveCustomFrame(id, b) }
            if (event.id == id) {
                customFrame = b
                updateEvent(event.copy(useCustomFrame = true))
            }
        }
    }

    fun deleteLibraryFrame(f: java.io.File) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { library.delete(f) }
            if (indivFrameFile == f) chooseStyle(frame)
            refreshLibrary()
        }
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

    /** Usa a arte enviada como moldura do evento. Se a janela vier em cinza liso, fica transparente sozinha. */
    fun useCustomFrame(b: Bitmap) {
        val id = event.id
        viewModelScope.launch {
            val ready = withContext(Dispatchers.Default) { FrameTools.autoTransparentWindow(b) }
            withContext(Dispatchers.IO) {
                eventStore.saveCustomFrame(id, ready)
                library.add(ready)
            }
            refreshLibrary()
            if (event.id == id) {
                customFrame = ready
                updateEvent(event.copy(useCustomFrame = true))
            }
        }
    }

    /** Arquivo .zip do evento, para compartilhar. */
    fun exportEvent(c: EventConfig): java.io.File = eventStore.export(c)

    /** Importa um evento recebido. Se já existir (mesmo evento), atualiza a configuração. */
    fun importEvent(uri: android.net.Uri): EventConfig? {
        val ctx = getApplication<Application>()
        val c = ctx.contentResolver.openInputStream(uri)?.use { eventStore.import(it) } ?: return null
        events = listOf(c) + events.filter { it.id != c.id }
        eventStore.saveAll(events)
        openEvent(c)
        return c
    }

    fun eventPhotos(c: EventConfig): List<java.io.File> = eventStore.photos(c.id)

    /** Guarda a foto na pasta do evento aberto (chamar fora da thread principal). */
    fun saveEventPhoto(img: Bitmap) {
        eventStore.addPhoto(event.id, img)
        // Também vai para a galeria, num álbum com o nome do evento (para enviar ao Drive depois)
        Sharing.saveToGallery(getApplication(), img, album = event.name)
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
        val indiv = indivFrame
        return withContext(Dispatchers.Default) {
            if (forEvent) {
                if (ev.useCustomFrame && cf != null) {
                    FrameRenderer.renderCustom(p, cf, ev.format, maxSide)
                } else {
                    FrameRenderer.render(p, ev.frame, ev.caption.ifBlank { null }, ev.format, maxSide)
                }
            } else if (indiv != null) {
                FrameRenderer.renderCustom(p, indiv, fmt, maxSide)
            } else {
                FrameRenderer.render(p, style, text.ifBlank { null }, fmt, maxSide)
            }
        }
    }
}
