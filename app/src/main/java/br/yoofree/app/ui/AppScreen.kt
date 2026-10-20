package br.yoofree.app.ui

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import br.yoofree.app.CameraViewModel

@Composable
fun YooFreeApp() {
    val vm: CameraViewModel = viewModel()
    val context = LocalContext.current
    val prefs = remember {
        context.applicationContext.getSharedPreferences("yoofree", Context.MODE_PRIVATE)
    }
    if (vm.host.isEmpty()) vm.host = prefs.getString("host", "") ?: ""

    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    if (landscape) {
        LandscapeScreen(vm, prefs)
    } else {
        PortraitScreen(vm, prefs)
    }
}

@Composable
private fun PortraitScreen(
    vm: CameraViewModel,
    prefs: android.content.SharedPreferences,
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
        Header(vm)
        VideoSurface(vm, Modifier.fillMaxWidth().weight(1f)) { surfaceReady = it }
        ConnectArea(vm, prefs, surfaceReady)
        ControlCard(vm)
    }
}

@Composable
private fun LandscapeScreen(
    vm: CameraViewModel,
    prefs: android.content.SharedPreferences,
) {
    var surfaceReady by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(vm, Modifier.fillMaxSize()) { surfaceReady = it }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
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
                            glyph = "⟳",
                            description = "Girar imagem",
                            size = 44.dp,
                            onClick = { vm.cycleRotation() },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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

@Composable
private fun Header(vm: CameraViewModel) {
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
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val w = constraints.maxWidth
                    val h = constraints.maxHeight
                    val swapped = vm.rotation == 90 || vm.rotation == 270
                    val cw = ((if (swapped) h else w) * vm.zoom).toInt().coerceAtLeast(1)
                    val ch = ((if (swapped) w else h) * vm.zoom).toInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(cw, ch))
                    layout(w, h) { placeable.place((w - cw) / 2, (h - ch) / 2) }
                }
                .then(
                    if (vm.rotation == 0) Modifier
                    else Modifier.graphicsLayer { rotationZ = vm.rotation.toFloat() }
                ),
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
                prefs.edit().putString("host", vm.host).apply()
                vm.toggleConnection()
            },
        )
        return
    }
    Button(
        onClick = {
            prefs.edit().putString("host", vm.host).apply()
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
