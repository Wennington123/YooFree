package br.yoofree.app

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

private const val BG = 0xFF0E1116

@Composable
private fun App() {
    val context = LocalContext.current
    val vm: CameraViewModel = viewModel()
    val prefs = remember { context.getSharedPreferences("yoofree", Context.MODE_PRIVATE) }
    if (vm.host.isEmpty()) vm.host = prefs.getString("host", "") ?: ""

    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var surfaceReady by remember { mutableStateOf(false) }

    @Composable
    fun video(modifier: Modifier) {
        Box(modifier = modifier.background(Color.Black).clipToBounds(), contentAlignment = Alignment.Center) {
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
                                surfaceReady = true
                                vm.setSurface(Surface(st))
                            }

                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}

                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                surfaceReady = false
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
    fun zoomRow(compact: Boolean) {
        val sz = if (compact) 48.dp else 56.dp
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.zoomBy(-0.5f) }, enabled = vm.connected || surfaceReady, modifier = Modifier.size(sz)) {
                Text("-", fontSize = 20.sp)
            }
            Text("%.1fx".format(vm.zoom), color = Color(0xFF9BB0C4), fontSize = 13.sp)
            Button(onClick = { vm.zoomBy(0.5f) }, enabled = vm.connected || surfaceReady, modifier = Modifier.size(sz)) {
                Text("+", fontSize = 20.sp)
            }
        }
    }

    @Composable
    fun ptzPad(compact: Boolean) {
        val sz = if (compact) 46.dp else 64.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = { vm.ptz("UP") }, enabled = vm.connected, modifier = Modifier.size(sz)) { Text("^", fontSize = 18.sp) }
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.ptz("LEFT") }, enabled = vm.connected, modifier = Modifier.size(sz)) { Text("<", fontSize = 18.sp) }
                Button(onClick = { vm.ptz("DWON") }, enabled = vm.connected, modifier = Modifier.size(sz)) { Text("v", fontSize = 18.sp) }
                Button(onClick = { vm.ptz("RIGHT") }, enabled = vm.connected, modifier = Modifier.size(sz)) { Text(">", fontSize = 18.sp) }
            }
        }
    }

    if (landscape) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            video(Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xCC0E1116))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            prefs.edit().putString("host", vm.host).apply()
                            vm.toggleConnection()
                        }, enabled = vm.connected || (vm.host.isNotBlank() && surfaceReady)) {
                            Text(if (vm.connected) "Desconectar" else "Conectar", fontSize = 13.sp)
                        }
                        Button(onClick = { vm.toggleRecording() }, enabled = vm.connected) {
                            Text(if (vm.recording) "Parar" else "Gravar", fontSize = 13.sp)
                        }
                        Button(onClick = { vm.cycleRotation() }) { Text("Girar", fontSize = 13.sp) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        zoomRow(compact = true)
                        ptzPad(compact = true)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(vm.status, color = Color(0xFF9BB0C4), fontSize = 12.sp)
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(BG)).padding(12.dp)) {
        Text("YooFree", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = vm.host,
                onValueChange = { vm.host = it },
                label = { Text("IP da camera") },
                singleLine = true,
                enabled = !vm.connected,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { vm.scanNetwork() },
                enabled = !vm.connected,
                modifier = Modifier.height(56.dp)
            ) { Text(if (vm.scanning) "Parar" else "Buscar", fontSize = 12.sp) }
        }

        if (vm.foundHosts.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            vm.foundHosts.forEach { ip ->
                OutlinedButton(
                    onClick = { vm.host = ip },
                    enabled = !vm.connected,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) { Text("Camera em $ip", fontSize = 13.sp) }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    prefs.edit().putString("host", vm.host).apply()
                    vm.toggleConnection()
                },
                enabled = vm.connected || (vm.host.isNotBlank() && surfaceReady),
                modifier = Modifier.weight(1f)
            ) { Text(if (vm.connected) "Desconectar" else "Conectar") }
            Button(onClick = { vm.toggleRecording() }, enabled = vm.connected, modifier = Modifier.weight(1f)) {
                Text(if (vm.recording) "Parar" else "Gravar")
            }
            Button(onClick = { vm.cycleRotation() }) { Text("Girar", fontSize = 12.sp) }
        }

        Spacer(Modifier.height(10.dp))

        video(Modifier.fillMaxWidth().weight(1f))

        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                zoomRow(compact = false)
                Spacer(Modifier.height(8.dp))
                ptzPad(compact = false)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(vm.status, color = Color(0xFF9BB0C4), fontSize = 13.sp)
    }
}
