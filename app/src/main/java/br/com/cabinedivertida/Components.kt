@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.cabinedivertida

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

// ---------- Botões e controles ----------

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    container: Color = Cores.Bordo,
    contentColor: Color = Color.White,
    height: Dp = 60.dp,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(height),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = contentColor),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun OutlineBigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = Cores.Bordo,
    height: Dp = 52.dp,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(height),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, color),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)?,
    color: Color = Cores.BordoEscuro,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar", tint = color)
            }
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Text(title, color = color, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = Cores.Texto2,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.background(Cores.AmareloClaro, RoundedCornerShape(12.dp)).padding(3.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Surface(
                onClick = { onSelect(i) },
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(9.dp),
                color = if (on) Color.White else Color.Transparent,
                contentColor = Cores.BordoEscuro,
                shadowElevation = if (on) 1.dp else 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun Stepper(label: String, value: Int, onChange: (Int) -> Unit, min: Int = 1, max: Int = 50) {
    Row(
        Modifier.fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = { onChange((value - 1).coerceAtLeast(min)) },
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Cores.Fundo, contentColor = Cores.BordoEscuro),
        ) { Icon(Icons.Rounded.Remove, contentDescription = "Menos") }
        Text("$value", Modifier.width(40.dp), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        FilledTonalIconButton(
            onClick = { onChange((value + 1).coerceAtMost(max)) },
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Cores.Fundo, contentColor = Cores.BordoEscuro),
        ) { Icon(Icons.Rounded.Add, contentDescription = "Mais") }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) Cores.BordoEscuro else Cores.Texto2)
            Text(subtitle, fontSize = 13.sp, color = Cores.Texto2)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Cores.Bordo, checkedThumbColor = Color.White),
        )
    }
}

/** Grade com os 6 tamanhos de papel. */
@Composable
fun PaperGrid(selected: PaperSize, onSelect: (PaperSize) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PaperSize.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p ->
                    val on = p == selected
                    Surface(
                        onClick = { onSelect(p) },
                        modifier = Modifier.weight(1f).height(58.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (on) Cores.Bordo else Color.White,
                        contentColor = if (on) Color.White else Cores.BordoEscuro,
                        border = BorderStroke(2.dp, if (on) Cores.Bordo else Cores.Linha),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(p.label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(p.hint, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Miniatura de moldura para escolher. */
@Composable
fun FrameThumb(style: FrameStyle, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color.White else Color.Transparent,
        border = BorderStroke(2.dp, if (selected) Cores.Bordo else Color.Transparent),
    ) {
        Column(
            Modifier.padding(8.dp).width(60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FramedBox(style, caption = null, modifier = Modifier.width(48.dp), small = true) {
                Box(Modifier.fillMaxSize().background(Color(0xFFF2A7B7)))
            }
            Text(style.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Cores.BordoEscuro, maxLines = 1)
        }
    }
}

// ---------- Moldura ao vivo (visor da câmera e miniaturas) ----------

@Composable
fun FramedBox(
    style: FrameStyle,
    caption: String?,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val k = if (small) 0.2f else 1f
    val pad = when (style) {
        FrameStyle.POLAROID -> 12.dp
        FrameStyle.CLASSICA -> 24.dp
        FrameStyle.FESTA -> 18.dp
        FrameStyle.NATUREZA -> 22.dp
        FrameStyle.SIMPLES -> 6.dp
    } * k
    val showCaption = style == FrameStyle.POLAROID || !caption.isNullOrBlank()
    Column(
        modifier
            .background(Color(style.background))
            .drawBehind { decorate(style, k) }
            .padding(pad),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clipToBounds(), content = content)
        if (showCaption) {
            Box(Modifier.fillMaxWidth().height(40.dp * k), contentAlignment = Alignment.Center) {
                if (!small && !caption.isNullOrBlank()) {
                    Text(caption, color = Color(style.captionColor), fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                }
            }
        }
    }
}

private fun DrawScope.decorate(style: FrameStyle, k: Float) {
    when (style) {
        FrameStyle.CLASSICA -> {
            val i = 7.dp.toPx() * k
            drawRect(
                Color(0xFFC9A56B),
                topLeft = Offset(i, i),
                size = Size(size.width - 2 * i, size.height - 2 * i),
                style = Stroke(4.dp.toPx() * k),
            )
        }
        FrameStyle.FESTA -> {
            val w = 6.dp.toPx() * k
            drawRect(
                Color(0xFFC2410C),
                topLeft = Offset(w / 2, w / 2),
                size = Size(size.width - w, size.height - w),
                style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18.dp.toPx() * k, 10.dp.toPx() * k))),
            )
        }
        FrameStyle.NATUREZA -> {
            val i = 8.dp.toPx() * k
            drawRoundRect(
                Color(0xFF4F7D62),
                topLeft = Offset(i, i),
                size = Size(size.width - 2 * i, size.height - 2 * i),
                cornerRadius = CornerRadius(8.dp.toPx() * k),
                style = Stroke(3.dp.toPx() * k),
            )
        }
        else -> Unit
    }
}

// ---------- Câmera ----------

@Composable
fun rememberCameraPermission(): Boolean {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }
    return granted
}

@Composable
fun CameraPreview(front: Boolean, flash: Boolean, onReady: (ImageCapture) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val capture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    LaunchedEffect(flash) {
        capture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }
    DisposableEffect(front, owner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                p.unbindAll()
                p.bindToLifecycle(owner, selector, preview, capture)
                onReady(capture)
            } catch (e: Exception) {
                Log.e("Cabine", "Erro ao abrir a câmera", e)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { provider?.unbindAll() }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

fun ImageCapture.takeBitmap(context: Context, mirror: Boolean, onResult: (Bitmap) -> Unit, onError: (String) -> Unit) {
    takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val raw = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees.toFloat()
            image.close()
            onResult(Images.scaleDown(Images.rotate(raw, rotation, mirror)))
        }

        override fun onError(exception: ImageCaptureException) {
            onError(exception.message ?: "Não foi possível tirar a foto")
        }
    })
}

/** Abre a galeria e devolve a foto escolhida já carregada. */
@Composable
fun rememberGalleryPicker(onPicked: (Bitmap) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val b = withContext(Dispatchers.IO) { runCatching { Images.load(ctx, uri) }.getOrNull() }
                if (b != null) onPicked(b)
            }
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** Botão redondo de disparo. */
@Composable
fun ShutterButton(onClick: () -> Unit, enabled: Boolean = true, size: Dp = 84.dp) {
    Box(
        Modifier.size(size)
            .background(Color.Transparent, CircleShape)
            .clickable(enabled = enabled, onClickLabel = "Tirar foto", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size).drawBehind {
            drawCircle(Color.White, style = Stroke(5.dp.toPx()))
        })
        Box(Modifier.size(size - 20.dp).background(if (enabled) Color.White else Color.White.copy(alpha = 0.5f), CircleShape))
    }
}

@Composable
fun PinDialog(pin: String, onDismiss: () -> Unit, onOk: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sair do modo evento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Digite o PIN para voltar às configurações.")
                OutlinedTextField(
                    value = typed,
                    onValueChange = { v -> typed = v.filter { it.isDigit() }.take(6); wrong = false },
                    singleLine = true,
                    isError = wrong,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (wrong) Text("PIN incorreto", color = Cores.Erro)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (typed == pin) onOk() else wrong = true }) { Text("Sair") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
