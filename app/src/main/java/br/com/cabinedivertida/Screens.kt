@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.cabinedivertida

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun copiesText(n: Int) = if (n == 1) "1 cópia" else "$n cópias"

// =============== 1. Início ===============

@Composable
fun HomeScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val openGallery = rememberGalleryPicker { b ->
        vm.photo = b
        vm.go(Screen.Frame)
    }
    var recents by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    LaunchedEffect(Unit) {
        recents = withContext(Dispatchers.IO) {
            Recents.list(ctx).take(3).mapNotNull { Images.loadFile(it, 400) }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "OLÁ!",
                modifier = Modifier.background(Cores.Amarelo, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
                color = Cores.BordoEscuro,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
            )
            Text("Cabine Divertida", color = Cores.Bordo, fontSize = 38.sp, fontWeight = FontWeight.Bold, lineHeight = 42.sp)
            Text(
                "Tire a foto, escolha a moldura e envie pelo WhatsApp ou imprima.",
                color = Cores.Texto2,
                fontSize = 16.sp,
            )
        }

        // Cartão principal
        Column(
            Modifier.fillMaxWidth()
                .border(2.dp, Cores.Bordo, RoundedCornerShape(24.dp))
                .background(Color.White, RoundedCornerShape(24.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().height(190.dp).background(Cores.AmareloClaro, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                FramedBox(FrameStyle.POLAROID, caption = null, modifier = Modifier.width(120.dp).rotate(-4f).shadow(8.dp)) {
                    val first = recents.firstOrNull()
                    if (first != null) {
                        Image(first.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize().background(Color(0xFFF2A7B7)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = Cores.Bordo, modifier = Modifier.size(40.dp))
                        }
                    }
                }
            }
            BigButton("Tirar foto", { vm.go(Screen.Camera) }, icon = Icons.Rounded.PhotoCamera)
            OutlineBigButton("Escolher da galeria", openGallery, icon = Icons.Rounded.PhotoLibrary)
        }

        // Modo Evento
        Surface(
            onClick = { vm.go(Screen.EventConfig) },
            shape = RoundedCornerShape(16.dp),
            color = Cores.Amarelo,
            contentColor = Cores.BordoEscuro,
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Celebration, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Modo Evento", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Moldura e tamanho fixos para festas", fontSize = 13.sp, color = Cores.Bordo)
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        }

        if (recents.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Recentes", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Cores.BordoEscuro)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    recents.forEach { b ->
                        Image(
                            b.asImageBitmap(),
                            contentDescription = "Foto recente",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).aspectRatio(0.8f).shadow(3.dp).background(Color.White),
                        )
                    }
                    repeat(3 - recents.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// =============== 2. Câmera ===============

@Composable
fun CameraScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val granted = rememberCameraPermission()
    var front by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var capture by remember { mutableStateOf<androidx.camera.core.ImageCapture?>(null) }
    var busy by remember { mutableStateOf(false) }
    val openGallery = rememberGalleryPicker { b ->
        vm.photo = b
        vm.go(Screen.Frame)
    }

    Column(Modifier.fillMaxSize().background(Cores.BordoEscuro).padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) {
                Icon(Icons.Rounded.Close, contentDescription = "Fechar câmera", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Moldura: ${vm.frame.label}",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { flash = !flash },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (flash) Cores.Amarelo else Color.White.copy(alpha = 0.14f),
                    contentColor = if (flash) Cores.BordoEscuro else Color.White,
                ),
            ) {
                Icon(if (flash) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff, contentDescription = if (flash) "Desligar flash" else "Ligar flash")
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            val w = minOf(maxWidth, (maxHeight - 90.dp) * 0.75f)
            FramedBox(vm.frame, vm.caption.ifBlank { null }, Modifier.width(w)) {
                if (granted) {
                    CameraPreview(front, flash, onReady = { capture = it }, modifier = Modifier.fillMaxSize())
                } else {
                    Text("Permita o uso da câmera para tirar fotos.", color = Cores.BordoEscuro, modifier = Modifier.align(Alignment.Center).padding(16.dp))
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FrameStyle.entries.forEach { f ->
                val on = f == vm.frame
                Surface(
                    onClick = { vm.frame = f },
                    shape = RoundedCornerShape(22.dp),
                    color = if (on) Cores.Amarelo else Color.Transparent,
                    contentColor = if (on) Cores.BordoEscuro else Color.White,
                    border = BorderStroke(1.5.dp, if (on) Cores.Amarelo else Color.White.copy(alpha = 0.4f)),
                ) {
                    Text(f.label, Modifier.padding(horizontal = 16.dp, vertical = 11.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = openGallery, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Abrir galeria", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            ShutterButton(
                enabled = capture != null && !busy,
                onClick = {
                    val c = capture ?: return@ShutterButton
                    busy = true
                    c.takeBitmap(ctx, mirror = front, onResult = { b ->
                        busy = false
                        vm.photo = b
                        vm.go(Screen.Frame)
                    }, onError = { msg ->
                        busy = false
                        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                    })
                },
            )
            IconButton(onClick = { front = !front; capture = null }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.Cameraswitch, contentDescription = "Trocar câmera", tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
    }
}

// =============== 3. Escolher a moldura ===============

@Composable
fun FrameScreen(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(vm.photo, vm.frame, vm.caption) {
        preview = vm.buildFinal(forEvent = false, width = 900)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Escolha a moldura", onBack = { vm.back() })
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            val p = preview
            if (p != null) {
                Image(p.asImageBitmap(), contentDescription = "Prévia da foto com moldura", modifier = Modifier.fillMaxSize().shadow(0.dp), contentScale = ContentScale.Fit)
            } else {
                CircularProgressIndicator(color = Cores.Bordo)
            }
        }
        SectionLabel("Molduras", Modifier.padding(horizontal = 24.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FrameStyle.entries.forEach { f -> FrameThumb(f, f == vm.frame) { vm.frame = f } }
        }
        OutlinedTextField(
            value = vm.caption,
            onValueChange = { vm.caption = it.take(40) },
            label = { Text("Legenda (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlineBigButton(
                "Girar",
                { vm.photo = vm.photo?.let { Images.rotate(it, 90f) } },
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.RotateRight,
                height = 60.dp,
            )
            BigButton(
                "Continuar",
                {
                    scope.launch {
                        busy = true
                        vm.finalImage = vm.buildFinal(forEvent = false)
                        busy = false
                        vm.go(Screen.Share)
                    }
                },
                modifier = Modifier.weight(1.6f),
                enabled = !busy && vm.photo != null,
            )
        }
    }
}

// =============== 4. Enviar ou imprimir ===============

@Composable
fun ShareScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val img = vm.finalImage
    LaunchedEffect(img) {
        if (img != null) withContext(Dispatchers.IO) { vm.addRecent(img) }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Ficou pronta!", onBack = { vm.back() }, color = Cores.Bordo)
        Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            if (img != null) {
                Image(img.asImageBitmap(), contentDescription = "Foto com moldura", modifier = Modifier.fillMaxSize().rotate(-2f), contentScale = ContentScale.Fit)
            }
        }
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Enviar pelo WhatsApp", { img?.let { Sharing.whatsapp(ctx, it) } }, icon = Icons.AutoMirrored.Rounded.Chat, container = Cores.Whats)
            BigButton("Imprimir", { vm.go(Screen.Print) }, icon = Icons.Rounded.Print, container = Cores.Amarelo, contentColor = Cores.BordoEscuro)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlineBigButton("Salvar", {
                    val ok = img != null && Sharing.saveToGallery(ctx, img)
                    Toast.makeText(ctx, if (ok) "Salva na galeria" else "Não foi possível salvar", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f), icon = Icons.Rounded.Download)
                OutlineBigButton("Outros apps", { img?.let { Sharing.other(ctx, it) } }, modifier = Modifier.weight(1f), icon = Icons.Rounded.Share)
            }
        }
    }
}

// =============== 5. Tamanho da impressão ===============

@Composable
fun PaperPreview(img: Bitmap?, paper: PaperSize, landscape: Boolean, fill: Boolean) {
    val pw = if (landscape) paper.heightCm else paper.widthCm
    val ph = if (landscape) paper.widthCm else paper.heightCm
    val k = minOf(140f / ph, 230f / pw)
    val w = (pw * k).dp
    val h = (ph * k).dp
    Column(
        Modifier.fillMaxWidth().height(190.dp).background(Cores.AmareloClaro, RoundedCornerShape(18.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(w, h).shadow(4.dp).background(Color.White).padding(if (fill) 0.dp else minOf(w, h) * 0.08f),
        ) {
            if (img != null) {
                Image(
                    img.asImageBitmap(),
                    contentDescription = "Prévia no papel",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = if (fill) ContentScale.Crop else ContentScale.Fit,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "${paper.label} cm · ${if (landscape) "paisagem" else "retrato"} · ${if (fill) "preenche o papel" else "com margem branca"}",
            fontSize = 13.sp,
            color = Cores.Texto2,
        )
    }
}

@Composable
fun PrintScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val img = vm.finalImage
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("Imprimir", onBack = { vm.back() })
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PaperPreview(img, vm.paper, vm.landscape, vm.fill)
            SectionLabel("Tamanho do papel")
            PaperGrid(vm.paper) { vm.paper = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Segmented(listOf("Retrato", "Paisagem"), if (vm.landscape) 1 else 0, { vm.landscape = it == 1 }, Modifier.weight(1f))
                Segmented(listOf("Preencher", "Inteira"), if (vm.fill) 0 else 1, { vm.fill = it == 0 }, Modifier.weight(1f))
            }
            Stepper("Cópias", vm.copies, { vm.copies = it })
            BigButton(
                "Imprimir ${copiesText(vm.copies)} · ${vm.paper.label}",
                {
                    val a = ctx.findActivity()
                    if (img != null && a != null) Printing.print(a, img, vm.paper, vm.landscape, vm.fill, vm.copies)
                },
                icon = Icons.Rounded.Print,
                enabled = img != null,
            )
            Text(
                "Na próxima tela você escolhe a impressora (Wi-Fi) ou salva em PDF.",
                fontSize = 13.sp,
                color = Cores.Texto2,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

// =============== 6. Configurar evento ===============

@Composable
fun EventConfigScreen(vm: AppViewModel) {
    val c = vm.event
    val pickFrame = rememberGalleryPicker { b -> vm.setCustomFrame(b) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("Configurar evento", onBack = { vm.back() })
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                "Você define tudo uma vez. Os convidados só tiram a foto e escolhem imprimir ou receber no WhatsApp.",
                color = Cores.Texto2,
                fontSize = 15.sp,
            )
            OutlinedTextField(
                value = c.name,
                onValueChange = { vm.updateEvent(c.copy(name = it.take(40))) },
                label = { Text("Nome do evento") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = c.caption,
                onValueChange = { vm.updateEvent(c.copy(caption = it.take(40))) },
                label = { Text("Texto na moldura") },
                placeholder = { Text("Ex.: Festa da Primavera · 12.12") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Moldura do evento")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FrameStyle.entries.forEach { f ->
                    FrameThumb(f, !c.useCustomFrame && c.frame == f) { vm.updateEvent(c.copy(frame = f, useCustomFrame = false)) }
                }
                val custom = vm.customFrame
                if (custom != null) {
                    Surface(
                        onClick = { vm.updateEvent(c.copy(useCustomFrame = true)) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (c.useCustomFrame) Color.White else Color.Transparent,
                        border = BorderStroke(2.dp, if (c.useCustomFrame) Cores.Bordo else Color.Transparent),
                    ) {
                        Column(Modifier.padding(8.dp).width(60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.width(48.dp).aspectRatio(custom.width.toFloat() / custom.height).background(Color(0xFFF2A7B7))) {
                                Image(custom.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                            }
                            Text("Minha arte", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Cores.BordoEscuro, maxLines = 1)
                        }
                    }
                }
            }
            OutlineBigButton("Usar minha arte (PNG)", pickFrame, icon = Icons.Rounded.Upload)
            Text(
                "Use um PNG com o meio transparente: a foto aparece por trás da sua arte.",
                fontSize = 13.sp,
                color = Cores.Texto2,
            )

            SectionLabel("Impressão")
            PaperGrid(c.paper) { vm.updateEvent(c.copy(paper = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Segmented(listOf("Retrato", "Paisagem"), if (c.landscape) 1 else 0, { vm.updateEvent(c.copy(landscape = it == 1)) }, Modifier.weight(1f))
                Segmented(listOf("Preencher", "Inteira"), if (c.fill) 0 else 1, { vm.updateEvent(c.copy(fill = it == 0)) }, Modifier.weight(1f))
            }
            Stepper("Cópias por foto", c.copies, { vm.updateEvent(c.copy(copies = it)) }, max = 10)

            SectionLabel("O que o convidado pode fazer")
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp))) {
                SwitchRow("Imprimir", "No tamanho definido acima", c.allowPrint, { vm.updateEvent(c.copy(allowPrint = it)) })
                HorizontalDivider(color = Cores.Fundo)
                SwitchRow(
                    "Imprimir assim que tirar",
                    "Abre a impressão sozinho quando a foto fica pronta",
                    c.autoPrint,
                    { vm.updateEvent(c.copy(autoPrint = it)) },
                    enabled = c.allowPrint,
                )
                HorizontalDivider(color = Cores.Fundo)
                SwitchRow("Receber no WhatsApp", "O convidado digita o número dele", c.allowWhats, { vm.updateEvent(c.copy(allowWhats = it)) })
                HorizontalDivider(color = Cores.Fundo)
                SwitchRow(
                    "Postar no Instagram",
                    "Na conta do evento, com o convidado como colaborador",
                    c.allowInstagram,
                    { vm.updateEvent(c.copy(allowInstagram = it)) },
                )
            }
            if (c.allowInstagram) {
                OutlinedTextField(
                    value = c.igCaption,
                    onValueChange = { vm.updateEvent(c.copy(igCaption = it.take(300))) },
                    label = { Text("Legenda do post") },
                    placeholder = { Text("Ex.: Festa da Primavera! #FestaDaPrimavera") },
                    supportingText = {
                        Text("Faça login no app do Instagram deste celular com a conta do evento. O @ do convidado é acrescentado no fim da legenda.")
                    },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = c.pin,
                onValueChange = { v -> vm.updateEvent(c.copy(pin = v.filter { it.isDigit() }.take(6))) },
                label = { Text("PIN para sair do modo evento") },
                supportingText = { Text("De 4 a 6 números. Assim ninguém muda a configuração durante a festa.") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )

            BigButton(
                "Iniciar evento",
                { vm.reset(Screen.EventCamera) },
                icon = Icons.Rounded.PlayArrow,
                enabled = c.pin.length >= 4,
            )
        }
    }
}

// =============== 7. Evento — câmera ===============

@Composable
fun EventCameraScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val c = vm.event
    val scope = rememberCoroutineScope()
    val granted = rememberCameraPermission()
    var front by remember { mutableStateOf(true) }
    var capture by remember { mutableStateOf<androidx.camera.core.ImageCapture?>(null) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var askPin by remember { mutableStateOf(false) }
    val custom = if (c.useCustomFrame) vm.customFrame else null
    val customImage = remember(custom) { custom?.asImageBitmap() }

    Column(Modifier.fillMaxSize().background(Cores.BordoEscuro).padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.name, color = Cores.Amarelo, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("Impressão ${c.paper.label} · ${copiesText(c.copies)}", color = Cores.AmareloSuave, fontSize = 13.sp)
            }
            IconButton(onClick = { front = !front; capture = null }) {
                Icon(Icons.Rounded.Cameraswitch, contentDescription = "Trocar câmera", tint = Color.White)
            }
            IconButton(onClick = { askPin = true }) {
                Icon(Icons.Rounded.Lock, contentDescription = "Sair do modo evento", tint = Color.White)
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Box(contentAlignment = Alignment.Center) {
                if (custom != null && customImage != null) {
                    val ratio = custom.width.toFloat() / custom.height
                    val w = minOf(maxWidth, maxHeight * ratio)
                    Box(Modifier.width(w).aspectRatio(ratio).background(Color.White)) {
                        if (granted) CameraPreview(front, false, onReady = { capture = it }, modifier = Modifier.fillMaxSize())
                        Image(customImage, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    }
                } else {
                    val w = minOf(maxWidth, (maxHeight - 90.dp) * 0.75f)
                    FramedBox(c.frame, c.caption.ifBlank { null }, Modifier.width(w)) {
                        if (granted) CameraPreview(front, false, onReady = { capture = it }, modifier = Modifier.fillMaxSize())
                    }
                }
                val n = countdown
                if (n != null) {
                    Box(Modifier.size(120.dp).background(Cores.Amarelo.copy(alpha = 0.92f), CircleShape), contentAlignment = Alignment.Center) {
                        Text("$n", color = Cores.BordoEscuro, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Text(
            if (granted) "Toque no botão e sorria!" else "Permita o uso da câmera para começar.",
            color = Cores.AmareloSuave,
            fontSize = 16.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 14.dp),
        )
        Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
            ShutterButton(
                size = 92.dp,
                enabled = capture != null && !busy,
                onClick = {
                    val cap = capture ?: return@ShutterButton
                    busy = true
                    scope.launch {
                        for (i in 3 downTo 1) {
                            countdown = i
                            delay(1000)
                        }
                        countdown = null
                        cap.takeBitmap(ctx, mirror = front, onResult = { b ->
                            vm.photo = b
                            scope.launch {
                                vm.finalImage = vm.buildFinal(forEvent = true)
                                busy = false
                                vm.go(Screen.EventResult)
                            }
                        }, onError = { msg ->
                            busy = false
                            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                        })
                    }
                },
            )
        }
    }

    if (askPin) {
        PinDialog(c.pin, onDismiss = { askPin = false }, onOk = {
            askPin = false
            vm.reset(Screen.Home)
            vm.go(Screen.EventConfig)
        })
    }
}

// =============== 8. Evento — foto pronta ===============

@Composable
fun EventResultScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val c = vm.event
    val img = vm.finalImage
    var printed by remember { mutableStateOf(false) }
    var showPhone by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var showIg by remember { mutableStateOf(false) }
    var handle by remember { mutableStateOf("") }

    fun doPrint() {
        val a = ctx.findActivity()
        if (img != null && a != null) {
            Printing.print(a, img, c.paper, c.landscape, c.fill, c.copies)
            printed = true
        }
    }

    LaunchedEffect(img) {
        if (img != null) {
            withContext(Dispatchers.IO) { vm.addRecent(img) }
            if (c.allowPrint && c.autoPrint) doPrint()
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Text("Que foto linda!", color = Cores.Bordo, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(c.name, color = Cores.Texto2, fontSize = 14.sp)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (img != null) {
                Image(img.asImageBitmap(), contentDescription = "Foto com a moldura do evento", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        if (printed) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Cores.AmareloSuave).padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Cores.Bordo)
                Spacer(Modifier.width(8.dp))
                Text("Enviada para a impressora · ${c.paper.label}", color = Cores.Bordo, fontWeight = FontWeight.SemiBold)
            }
        }
        if (showPhone) {
            Column(
                Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { v -> phone = v.filter { it.isDigit() || it in " ()-+" }.take(20) },
                        label = { Text("Seu WhatsApp com DDD") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            val p = Sharing.normalizePhone(phone)
                            if (p == null || img == null) {
                                Toast.makeText(ctx, "Confira o número (com DDD)", Toast.LENGTH_SHORT).show()
                            } else {
                                Sharing.whatsapp(ctx, img, p)
                                phone = ""
                                showPhone = false
                            }
                        },
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Cores.Whats),
                    ) { Text("Enviar", fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (showIg) {
            val h = Sharing.normalizeHandle(handle)
            Column(
                Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = handle,
                    onValueChange = { handle = it.take(31) },
                    label = { Text("Seu @ no Instagram") },
                    placeholder = { Text("@seuperfil") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (h.isNotEmpty()) {
                        "No Instagram: escolha Publicação › Avançar › segure no campo da legenda e toque em Colar › Marcar pessoas › Convidar colaborador › digite @$h. Depois é só aceitar o convite no seu perfil."
                    } else {
                        "No Instagram: escolha Publicação › Avançar › segure no campo da legenda e toque em Colar › Compartilhar."
                    },
                    fontSize = 13.sp,
                    color = Cores.Texto2,
                )
                BigButton(
                    "Abrir o Instagram",
                    {
                        val text = listOf(c.igCaption.trim(), if (h.isNotEmpty()) "Com @$h" else "")
                            .filter { it.isNotEmpty() }
                            .joinToString("\n\n")
                        if (text.isNotEmpty()) Sharing.copyText(ctx, "Legenda", text)
                        val ok = img != null && Sharing.instagram(ctx, img)
                        if (!ok) {
                            Toast.makeText(ctx, "Instale e entre no Instagram neste celular", Toast.LENGTH_LONG).show()
                        } else {
                            handle = ""
                            showIg = false
                        }
                    },
                    icon = Icons.Rounded.CameraAlt,
                    container = Cores.Bordo,
                    height = 54.dp,
                )
            }
        }
        if (c.allowPrint) {
            BigButton("Imprimir", { doPrint() }, icon = Icons.Rounded.Print, container = Cores.Amarelo, contentColor = Cores.BordoEscuro, height = 60.dp)
        }
        if (c.allowWhats || c.allowInstagram) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (c.allowWhats) {
                    BigButton("WhatsApp", { showPhone = !showPhone; showIg = false }, modifier = Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.Chat, container = Cores.Whats, height = 60.dp)
                }
                if (c.allowInstagram) {
                    BigButton("Instagram", { showIg = !showIg; showPhone = false }, modifier = Modifier.weight(1f), icon = Icons.Rounded.CameraAlt, container = Cores.Bordo, height = 60.dp)
                }
            }
        }
        OutlineBigButton("Tirar outra foto", {
            vm.photo = null
            vm.finalImage = null
            vm.reset(Screen.EventCamera)
        }, icon = Icons.Rounded.PhotoCamera)
    }
}
