package br.yoofree.app.ui

import android.content.Intent
import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import br.yoofree.app.CameraViewModel
import br.yoofree.app.R

@Composable
fun YooFreeApp(onThemeMode: (ThemeMode) -> Unit = {}) {
    val vm: CameraViewModel = viewModel()
    val context = LocalContext.current
    val prefs = remember {
        context.applicationContext.getSharedPreferences("yoofree", Context.MODE_PRIVATE)
    }
    if (vm.host.isEmpty()) vm.host = prefs.getString("host", "") ?: ""
    if (vm.password.isEmpty()) vm.password = prefs.getString("pass", "") ?: ""
    vm.ptzInvert = prefs.getBoolean("ptzInvert", false)
    var showSettings by remember { mutableStateOf(false) }

    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    if (landscape) {
        LandscapeScreen(vm, prefs, onOpenSettings = { showSettings = true })
    } else {
        PortraitScreen(vm, prefs, onOpenSettings = { showSettings = true })
    }

    if (showSettings) {
        SettingsSheet(
            prefs = prefs,
            onThemeMode = onThemeMode,
            vm = vm,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun PortraitScreen(
    vm: CameraViewModel,
    prefs: android.content.SharedPreferences,
    onOpenSettings: () -> Unit,
) {
    var surfaceReady by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(vm, onOpenSettings)
        VideoSurface(vm, Modifier.fillMaxWidth().weight(1f)) { surfaceReady = it }
        ConnectArea(vm, prefs, surfaceReady)
        ControlCard(vm)
    }
}

@Composable
private fun LandscapeScreen(
    vm: CameraViewModel,
    prefs: android.content.SharedPreferences,
    onOpenSettings: () -> Unit,
) {
    var surfaceReady by remember { mutableStateOf(false) }
    var hudVisible by remember { mutableStateOf(true) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { hudVisible = !hudVisible }
            }
    ) {
        VideoSurface(vm, Modifier.fillMaxSize()) { surfaceReady = it }
        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = MaterialTheme.shapes.large,
                color = Panel.copy(alpha = 0.92f),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ConnectButton(vm, prefs, surfaceReady, compact = true)
                            RoundButton(
                                glyph = if (vm.recording) "■" else "●",
                                description = if (vm.recording) "Parar gravação" else "Gravar",
                                enabled = vm.connected,
                                size = 44.dp,
                                container = if (vm.recording) Bad else PanelHi,
                                onClick = { vm.toggleRecording() },
                            )
                            RoundButton(
                                glyph = "♪",
                                description = if (vm.audioOn) "Desligar som" else "Ligar som",
                                size = 44.dp,
                                content = if (vm.audioOn) Accent else TextLow,
                                onClick = { vm.toggleAudio() },
                            )
                            RoundButton(
                                glyph = "⟳",
                                description = "Girar imagem",
                                size = 44.dp,
                                onClick = { vm.cycleRotation() },
                            )
                            RoundButton(
                                glyph = "⚙",
                                description = "Configurações",
                                size = 44.dp,
                                onClick = onOpenSettings,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ZoomStepper(vm, compact = true)
                            PtzPad(vm, compact = true)
                        }
                    }
                    Text(
                        text = vm.status,
                        color = TextLow,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(vm: CameraViewModel, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "YooFree",
            color = TextHigh,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.weight(1f))
        StatusPill(vm)
        Spacer(Modifier.width(8.dp))
        RoundButton(
            glyph = "⚙",
            description = "Configurações",
            size = 40.dp,
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun StatusPill(vm: CameraViewModel) {
    val dotColor = when {
        vm.connected -> Good
        vm.scanning -> Accent
        else -> TextLow.copy(alpha = 0.6f)
    }
    val pulse by animateFloatAsState(if (vm.scanning) 0.55f else 1f, label = "dot")
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(PanelHi)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .alpha(pulse)
                .background(dotColor, CircleShape)
        )
        Text(
            text = vm.status,
            color = TextLow,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 190.dp),
        )
    }
}

@Composable
private fun VideoSurface(
    vm: CameraViewModel,
    modifier: Modifier,
    onSurfaceReady: (Boolean) -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(28.dp))
            .clipToBounds()
            .onSizeChanged { vm.viewW = it.width.toFloat(); vm.viewH = it.height.toFloat() }
            .pointerInput(vm) {
                detectTransformGestures { centroid, pan, zoomChange, _ ->
                    vm.applyGesture(zoomChange, pan.x, pan.y, centroid.x, centroid.y)
                }
            }
            .pointerInput(vm) {
                detectTapGestures(
                    onDoubleTap = { offset -> vm.onDoubleTap(offset.x, offset.y) }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = vm.zoom
                    scaleY = vm.zoom
                    translationX = vm.offsetX
                    translationY = vm.offsetY
                    rotationZ = vm.rotation.toFloat()
                },
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            onSurfaceReady(true)
                            vm.setSurface(Surface(st))
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}

                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            onSurfaceReady(false)
                            vm.setSurface(null)
                            return true
                        }

                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            }
        )
    }
}

@Composable
private fun ConnectArea(
    vm: CameraViewModel,
    prefs: android.content.SharedPreferences,
    surfaceReady: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(
            visible = !vm.connected,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = vm.host,
                        onValueChange = { vm.host = it },
                        label = { Text("IP da câmera") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = Hairline,
                            focusedLabelColor = Accent,
                            unfocusedLabelColor = TextLow,
                            cursorColor = Accent,
                            focusedTextColor = TextHigh,
                            unfocusedTextColor = TextHigh,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { vm.scanNetwork() },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PanelHi,
                            contentColor = TextHigh,
                        ),
                        modifier = Modifier.height(56.dp),
                    ) {
                        Text(
                            text = if (vm.scanning) "Parar" else "Buscar",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                OutlinedTextField(
                    value = vm.password,
                    onValueChange = { vm.password = it },
                    label = { Text("Senha do RTSP (opcional)") },
                    supportingText = { Text("Só se a câmera exigir senha (ex.: acesso por VPN). Usuário: admin") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Hairline,
                        focusedLabelColor = Accent,
                        unfocusedLabelColor = TextLow,
                        cursorColor = Accent,
                        focusedTextColor = TextHigh,
                        unfocusedTextColor = TextHigh,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                AnimatedVisibility(
                    visible = vm.foundHosts.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        vm.foundHosts.forEach { ip ->
                            OutlinedButton(
                                onClick = { vm.host = ip },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "Câmera em $ip",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextHigh,
                                )
                            }
                        }
                    }
                }
            }
        }
        ConnectButton(vm, prefs, surfaceReady)
    }
}

@Composable
private fun ConnectButton(
    vm: CameraViewModel,
    prefs: android.content.SharedPreferences,
    surfaceReady: Boolean,
    compact: Boolean = false,
) {
    if (compact) {
        RoundButton(
            glyph = if (vm.connected) "⏻" else "▶",
            description = if (vm.connected) "Desconectar" else "Conectar",
            enabled = vm.connected || (vm.host.isNotBlank() && surfaceReady),
            size = 44.dp,
            container = if (vm.connected) PanelHi else Accent,
            content = if (vm.connected) TextHigh else OnAccent,
            onClick = {
                prefs.edit().putString("host", vm.host).putString("pass", vm.password).apply()
                vm.toggleConnection()
            },
        )
        return
    }
    Button(
        onClick = {
            prefs.edit().putString("host", vm.host).putString("pass", vm.password).apply()
            vm.toggleConnection()
        },
        enabled = vm.connected || (vm.host.isNotBlank() && surfaceReady),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Accent,
            contentColor = OnAccent,
            disabledContainerColor = PanelHi,
            disabledContentColor = TextLow,
        ),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(
            text = if (vm.connected) "Desconectar" else "Conectar",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun ControlCard(vm: CameraViewModel) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = Panel,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ZoomStepper(vm, compact = false)
            PtzPad(vm, compact = false)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RoundButton(
                    glyph = if (vm.recording) "■" else "●",
                    description = if (vm.recording) "Parar gravação" else "Gravar",
                    enabled = vm.connected,
                    size = 52.dp,
                    container = if (vm.recording) Bad else PanelHi,
                    content = if (vm.recording) Bg else Good,
                    onClick = { vm.toggleRecording() },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundButton(
                        glyph = "♪",
                        description = if (vm.audioOn) "Desligar som" else "Ligar som",
                        size = 44.dp,
                        content = if (vm.audioOn) Accent else TextLow,
                        onClick = { vm.toggleAudio() },
                    )
                    RoundButton(
                        glyph = "⟳",
                        description = "Girar imagem",
                        size = 44.dp,
                        onClick = { vm.cycleRotation() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomStepper(vm: CameraViewModel, compact: Boolean) {
    val size = if (compact) 38.dp else 44.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RoundButton(
            glyph = "+",
            description = "Aumentar zoom",
            enabled = vm.zoom < 5f,
            size = size,
            onClick = { vm.zoomBy(0.5f) },
        )
        Text(
            text = "%.1fx".format(vm.zoom),
            color = TextLow,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(44.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        RoundButton(
            glyph = "−",
            description = "Diminuir zoom",
            enabled = vm.zoom > 1f,
            size = size,
            onClick = { vm.zoomBy(-0.5f) },
        )
    }
}

@Composable
private fun PtzPad(vm: CameraViewModel, compact: Boolean) {
    val size = if (compact) 40.dp else 48.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RoundButton(
            glyph = "↑",
            description = "Mover para cima",
            enabled = vm.connected,
            size = size,
            onClick = { vm.ptz("UP") },
        )
        Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)) {
            RoundButton(
                glyph = "←",
                description = "Mover para esquerda",
                enabled = vm.connected,
                size = size,
                onClick = { vm.ptz("LEFT") },
            )
            RoundButton(
                glyph = "↓",
                description = "Mover para baixo",
                enabled = vm.connected,
                size = size,
                onClick = { vm.ptz("DOWN") },
            )
            RoundButton(
                glyph = "→",
                description = "Mover para direita",
                enabled = vm.connected,
                size = size,
                onClick = { vm.ptz("RIGHT") },
            )
        }
    }
}

@Composable
private fun RoundButton(
    glyph: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Dp = 52.dp,
    container: Color = PanelHi,
    content: Color = TextHigh,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick,
            )
            .semantics { contentDescription = description }
            .alpha(if (enabled) 1f else 0.35f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = content,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Termos que o app permeia, pro glossario das configuracoes. */
private val Glossary = listOf(
    "RTSP" to "Protocolo que controla o stream: é o \"controle remoto\" do vídeo " +
        "pela rede (SETUP, PLAY, TEARDOWN). O YooFree fala direto com a câmera " +
        "nele, na sua rede local.",
    "RTP" to "Transporte dos pacotes de vídeo e áudio em tempo real, por cima do " +
        "RTSP (na mesma conexão TCP, canais intercalados).",
    "HEVC / H.265" to "Codec de vídeo da câmera: comprime 1080p usando pouca banda. " +
        "É decodificado direto no hardware do celular.",
    "G.711" to "Codec de áudio (A-law/µ-law) que essas câmeras transmitem junto " +
        "com o vídeo.",
    "PTZ" to "Pan/Tilt/Zoom — os comandos que giram a câmera (cima, baixo, " +
        "esquerda, direita).",
    "ONVIF" to "Padrão aberto de câmeras IP. Nessa linha, o stream vive no path " +
        "/onvif1 (e /onvif2 para o stream secundário).",
    "SDP" to "Descrição do stream que a câmera devolve no DESCRIBE: quais trilhas " +
        "existem (vídeo, áudio) e os URIs de cada uma.",
    "Digest (HTTP)" to "Autenticação por desafio usada quando a câmera tem senha " +
        "no RTSP: a senha nunca trafega em texto puro.",
    "IDR / keyframe" to "Quadro-chave que reinicia a decodificação. O app espera " +
        "um IDR antes de desenhar — é por isso que a imagem nasce limpa.",
    "VPN (WireGuard/Tailscale)" to "Túnel criptografado que coloca o celular " +
        "\"dentro\" da rede de casa, pra acessar a câmera de fora sem expor nada " +
        "na internet.",
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    vm: CameraViewModel,
    prefs: android.content.SharedPreferences,
    onThemeMode: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState()
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
    val pixKey = "8eb4ec02-4a67-42b1-b3dc-6243efcd426a"
    var selectedMode by remember { mutableStateOf(ThemeMode.fromId(prefs.getString("theme", null))) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Panel,
        contentColor = TextHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Configurações",
                color = TextHigh,
                style = MaterialTheme.typography.titleLarge,
            )

            Text("Tema", color = TextLow, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = {
                            selectedMode = mode
                            onThemeMode(mode)
                        },
                        label = { Text(mode.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent,
                            selectedLabelColor = OnAccent,
                            labelColor = TextHigh,
                        ),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Inverter comandos PTZ", color = TextHigh, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Câmera montada de cabeça pra baixo: cima vira baixo e " +
                            "esquerda vira direita.",
                        color = TextLow,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = vm.ptzInvert,
                    onCheckedChange = { on ->
                        vm.ptzInvert = on
                        prefs.edit().putBoolean("ptzInvert", on).apply()
                    },
                )
            }

            HorizontalDivider(color = Hairline)

            Text("Apoie o projeto", color = TextHigh, style = MaterialTheme.typography.titleMedium)
            Text(
                "O YooFree é 100% gratuito, sem anúncio e open source. Se ele te " +
                    "economizou o app antigo cheio de anúncio, tomo um café:",
                color = TextLow,
                style = MaterialTheme.typography.bodySmall,
            )
            Image(
                painter = painterResource(R.drawable.pix_qrcode),
                contentDescription = "QR Code Pix",
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(160.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Pix: $pixKey",
                    color = TextHigh,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(pixKey))
                        android.widget.Toast.makeText(
                            context, "Chave Pix copiada", Toast.LENGTH_SHORT
                        ).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Copiar", style = MaterialTheme.typography.labelMedium) }
            }
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/wenningtondaquino"))
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Apoiar no Ko-fi (cartão ou PayPal)") }

            HorizontalDivider(color = Hairline)

            Text("Glossário", color = TextHigh, style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Glossary.forEach { (term, desc) ->
                    Column {
                        Text(term, color = Accent, style = MaterialTheme.typography.labelMedium)
                        Text(desc, color = TextLow, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            HorizontalDivider(color = Hairline)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("YooFree v$version", color = TextHigh, style = MaterialTheme.typography.labelMedium)
                Text(
                    "GPL-3.0 — sem nuvem, sem conta, sem anúncios.",
                    color = TextLow,
                    style = MaterialTheme.typography.bodySmall,
                )
                LinkRow(
                    iconRes = R.drawable.ic_globe,
                    label = "Site oficial: yoofree.qd.je",
                    url = "https://yoofree.qd.je/",
                )
                LinkRow(
                    iconRes = R.drawable.ic_github,
                    label = "Código-fonte: GitHub/Wennington123/YooFree",
                    url = "https://github.com/Wennington123/YooFree",
                )
            }
        }
    }
}

@Composable
private fun LinkRow(iconRes: Int, label: String, url: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(TextHigh),
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            color = Accent,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
