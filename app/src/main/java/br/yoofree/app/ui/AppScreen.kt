package br.yoofree.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
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

    // Mantem a tela acordada enquanto ha stream rodando. Sem isso o display apaga
    // no timeout do sistema e o video congela/pauza (nada mais mantinha vivo: nem
    // WakeLock, nem keepScreenOn). Flag nativo da janela — independe da versao do
    // Compose BOM; volta a seguir o timeout normal quando desconecta.
    LaunchedEffect(vm.connected) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        if (vm.connected) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

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
                                description = stringResource(if (vm.recording) R.string.desc_record_stop else R.string.desc_record_start),
                                enabled = vm.connected,
                                size = 44.dp,
                                container = if (vm.recording) Bad else PanelHi,
                                onClick = { vm.toggleRecording() },
                            )
                            RoundButton(
                                glyph = "♪",
                                description = stringResource(if (vm.audioOn) R.string.desc_audio_off else R.string.desc_audio_on),
                                size = 44.dp,
                                content = if (vm.audioOn) Accent else TextLow,
                                onClick = { vm.toggleAudio() },
                            )
                            RoundButton(
                                glyph = "⟳",
                                description = stringResource(R.string.desc_rotate),
                                size = 44.dp,
                                onClick = { vm.cycleRotation() },
                            )
                            RoundButton(
                                glyph = "⚙",
                                description = stringResource(R.string.desc_settings),
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
            description = stringResource(R.string.desc_settings),
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
                            // NAO libera o codec aqui: na rotacao/recomposicao o
                            // TextureView destroi o antigo ANTES de publicar o novo;
                            // se matarmos o decoder nesse instante, a tela fica preta
                            // mesmo com stream rodando. O novo onSurfaceTextureAvailable
                            // ja sobrescreve a surface por dentro do decoder sem parar.
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
                        label = { Text(stringResource(R.string.label_camera_ip)) },
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
                            text = stringResource(if (vm.scanning) R.string.action_stop_search else R.string.action_search),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                OutlinedTextField(
                    value = vm.password,
                    onValueChange = { vm.password = it },
                    label = { Text(stringResource(R.string.label_rtsp_password)) },
                    supportingText = { Text(stringResource(R.string.help_rtsp_password)) },
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
                                    text = stringResource(R.string.action_camera_at, ip),
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
            description = stringResource(if (vm.connected) R.string.action_disconnect else R.string.action_connect),
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
            text = stringResource(if (vm.connected) R.string.action_disconnect else R.string.action_connect),
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
                    description = stringResource(if (vm.recording) R.string.desc_record_stop else R.string.desc_record_start),
                    enabled = vm.connected,
                    size = 52.dp,
                    container = if (vm.recording) Bad else PanelHi,
                    content = if (vm.recording) Bg else Good,
                    onClick = { vm.toggleRecording() },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundButton(
                        glyph = "♪",
                        description = stringResource(if (vm.audioOn) R.string.desc_audio_off else R.string.desc_audio_on),
                        size = 44.dp,
                        content = if (vm.audioOn) Accent else TextLow,
                        onClick = { vm.toggleAudio() },
                    )
                    RoundButton(
                        glyph = "⟳",
                        description = stringResource(R.string.desc_rotate),
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
            description = stringResource(R.string.desc_zoom_in),
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
            description = stringResource(R.string.desc_zoom_out),
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
            description = stringResource(R.string.desc_ptz_up),
            enabled = vm.connected,
            size = size,
            onClick = { vm.ptz("UP") },
        )
        Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)) {
            RoundButton(
                glyph = "←",
                description = stringResource(R.string.desc_ptz_left),
                enabled = vm.connected,
                size = size,
                onClick = { vm.ptz("LEFT") },
            )
            RoundButton(
                glyph = "↓",
                description = stringResource(R.string.desc_ptz_down),
                enabled = vm.connected,
                size = size,
                onClick = { vm.ptz("DOWN") },
            )
            RoundButton(
                glyph = "→",
                description = stringResource(R.string.desc_ptz_right),
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
    "RTSP" to R.string.glossary_rtsp,
    "RTP" to R.string.glossary_rtp,
    "HEVC / H.265" to R.string.glossary_hevc,
    "G.711" to R.string.glossary_g711,
    "PTZ" to R.string.glossary_ptz,
    "ONVIF" to R.string.glossary_onvif,
    "SDP" to R.string.glossary_sdp,
    "Digest (HTTP)" to R.string.glossary_digest,
    "IDR / keyframe" to R.string.glossary_idr,
    "VPN (WireGuard/Tailscale)" to R.string.glossary_vpn,
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
                text = stringResource(R.string.settings_title),
                color = TextHigh,
                style = MaterialTheme.typography.titleLarge,
            )

            Text(stringResource(R.string.theme_title), color = TextLow, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = {
                            selectedMode = mode
                            onThemeMode(mode)
                        },
                        label = { Text(stringResource(mode.labelRes)) },
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
                    Text(stringResource(R.string.ptz_invert_title), color = TextHigh, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.ptz_invert_desc),
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

            Text(stringResource(R.string.support_title), color = TextHigh, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.support_body),
                color = TextLow,
                style = MaterialTheme.typography.bodySmall,
            )
            Image(
                painter = painterResource(R.drawable.pix_qrcode),
                contentDescription = stringResource(R.string.pix_qr_desc),
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
                    text = stringResource(R.string.pix_label, pixKey),
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
                            context, context.getString(R.string.pix_copied), Toast.LENGTH_SHORT
                        ).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                ) { Text(stringResource(R.string.action_copy), style = MaterialTheme.typography.labelMedium) }
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
            ) { Text(stringResource(R.string.support_kofi)) }

            HorizontalDivider(color = Hairline)

            Text(stringResource(R.string.glossary_title), color = TextHigh, style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Glossary.forEach { (term, desc) ->
                    Column {
                        Text(term, color = Accent, style = MaterialTheme.typography.labelMedium)
                        Text(stringResource(desc), color = TextLow, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            HorizontalDivider(color = Hairline)

            Text(stringResource(R.string.diagnostics_title), color = TextHigh, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.diagnostics_body),
                color = TextLow,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = {
                    val report = br.yoofree.app.diag.YooLog.buildReport(
                        context,
                        mapOf(
                            context.getString(R.string.report_connected) to vm.connected.toString(),
                            context.getString(R.string.report_recording) to vm.recording.toString(),
                        ),
                    )
                    val url = android.net.Uri.parse(
                        "https://github.com/Wennington123/YooFree/issues/new"
                    ).buildUpon()
                        .appendQueryParameter(
                            "title",
                            context.getString(
                                R.string.report_app_issue_title,
                                context.packageManager.getPackageInfo(context.packageName, 0).versionName,
                            ),
                        )
                        .appendQueryParameter("body", report)
                        .build()
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, url))
                    }.onFailure {
                        Toast.makeText(context, context.getString(R.string.toast_no_browser), Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_send_report)) }

            HorizontalDivider(color = Hairline)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.version_line, version), color = TextHigh, style = MaterialTheme.typography.labelMedium)
                Text(
                    stringResource(R.string.license_line),
                    color = TextLow,
                    style = MaterialTheme.typography.bodySmall,
                )
                LinkRow(
                    iconRes = R.drawable.ic_globe,
                    label = stringResource(R.string.link_site),
                    url = "https://yoofree.qd.je/",
                )
                LinkRow(
                    iconRes = R.drawable.ic_github,
                    label = stringResource(R.string.link_source),
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
