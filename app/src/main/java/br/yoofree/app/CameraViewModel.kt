package br.yoofree.app

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import br.yoofree.app.net.LanScanner

/**
 * Guarda a sessao da camera fora da Activity, para sobreviver a rotacao de tela
 * (a Activity recria na rotacao; o stream continua aqui).
 */
class CameraViewModel(app: Application) : AndroidViewModel(app) {

    var host by mutableStateOf("")
    var password by mutableStateOf("")
    var status by mutableStateOf("Parado")
    var connected by mutableStateOf(false)
    var recording by mutableStateOf(false)
    var zoom by mutableStateOf(1.0f)
    var offsetX by mutableStateOf(0f)
    var offsetY by mutableStateOf(0f)
    var rotation by mutableStateOf(0)
    var scanning by mutableStateOf(false)
    var foundHosts by mutableStateOf(listOf<String>())
    var audioOn by mutableStateOf(true)

    /** Câmera de cabeça pra baixo: inverte os comandos PTZ. */
    var ptzInvert by mutableStateOf(false)

    // Tamanho do quadro do video na tela (nao e estado de UI: so geometria).
    var viewW = 0f
    var viewH = 0f

    private var scanner: LanScanner? = null
    private val main = Handler(Looper.getMainLooper())

    private var session: CameraSession? = null
    private var currentSurface: Surface? = null

    fun setSurface(surface: Surface?) {
        currentSurface = surface
        session?.setSurface(surface)
    }

    fun toggleConnection() {
        if (connected) {
            session?.disconnect()
            session = null
            connected = false
            recording = false
        } else {
            val target = host.trim()
            if (target.isEmpty()) return
            val (h, p) = parseHostPort(target)
            val s = CameraSession(getApplication(), h, p, password.trim()) { status = it }
            session = s
            // O Surface do TextureView normalmente ja existe antes de conectar.
            s.setSurface(currentSurface)
            s.setAudioEnabled(audioOn)
            s.connect()
            connected = true
        }
    }

    /** Aceita "192.168.1.2" ou "192.168.1.2:8554" (tunel/port-forward). */
    private fun parseHostPort(target: String): Pair<String, Int> {
        val i = target.lastIndexOf(':')
        if (i > 0) {
            val port = target.substring(i + 1).toIntOrNull()
            if (port != null && port in 1..65535) return target.substring(0, i) to port
        }
        return target to 554
    }

    fun toggleRecording() {
        val s = session ?: return
        recording = s.toggleRecording()
    }

    fun ptz(command: String) {
        val cmd = if (ptzInvert) when (command) {
            "UP" -> "DOWN"
            "DOWN" -> "UP"
            "LEFT" -> "RIGHT"
            "RIGHT" -> "LEFT"
            else -> command
        } else command
        session?.ptz(cmd)
    }

    fun toggleAudio() {
        audioOn = !audioOn
        session?.setAudioEnabled(audioOn)
    }

    /** Zoom pelos botoes +/-: centrado no meio do quadro. */
    fun zoomBy(delta: Float) {
        if (viewW <= 0f || viewH <= 0f) {
            zoom = (zoom + delta).coerceIn(1f, MAX_ZOOM)
            return
        }
        applyGesture((zoom + delta) / zoom, 0f, 0f, viewW / 2f, viewH / 2f)
    }

    /**
     * Zoom por gesto (pinch): fator multiplicativo ancorado no ponto sob os
     * dedos (centroid), com arrasto (pan) junto, travado pra nao mostrar borda.
     */
    fun applyGesture(zoomChange: Float, panX: Float, panY: Float, cx: Float, cy: Float) {
        val w = viewW
        val h = viewH
        if (w <= 0f || h <= 0f) return
        val newZoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
        val f = newZoom / zoom
        val dx = cx - w / 2f
        val dy = cy - h / 2f
        val nx = offsetX * f + dx * (1f - f) + panX
        val ny = offsetY * f + dy * (1f - f) + panY
        zoom = newZoom
        val swapped = rotation == 90 || rotation == 270
        val contentW = if (swapped) newZoom * h else newZoom * w
        val contentH = if (swapped) newZoom * w else newZoom * h
        val maxX = ((contentW - w) / 2f).coerceAtLeast(0f)
        val maxY = ((contentH - h) / 2f).coerceAtLeast(0f)
        offsetX = nx.coerceIn(-maxX, maxX)
        offsetY = ny.coerceIn(-maxY, maxY)
    }

    /** Toque duplo: alterna entre 1x e 2x ancorado no ponto tocado. */
    fun onDoubleTap(x: Float, y: Float) {
        if (zoom > 1.01f) {
            resetView()
        } else {
            applyGesture(2f, 0f, 0f, x, y)
        }
    }

    fun resetView() {
        zoom = 1f
        offsetX = 0f
        offsetY = 0f
    }

    fun cycleRotation() {
        rotation = (rotation + 90) % 360
        offsetX = 0f
        offsetY = 0f
    }

    fun onSurfaceReady(): Boolean = connected

    /** Varre a rede local atras de cameras RTSP. Resultado vai para foundHosts. */
    fun scanNetwork() {
        if (scanning) {
            scanner?.cancel()
            scanner = null
            scanning = false
            status = "Busca cancelada"
            return
        }
        foundHosts = emptyList()
        scanning = true
        status = "Procurando camera na rede..."
        val s = LanScanner(
            onFound = { ip ->
                main.post {
                    foundHosts = foundHosts + ip
                    status = "Camera encontrada: $ip"
                }
            },
            onDone = {
                main.post {
                    scanning = false
                    scanner = null
                    if (foundHosts.isEmpty()) status = "Nenhuma camera encontrada na rede" else
                        status = "Clique em um IP para conectar"
                }
            }
        )
        scanner = s
        s.start()
    }

    override fun onCleared() {
        scanner?.cancel()
        session?.disconnect()
        session = null
    }

    private companion object {
        const val MAX_ZOOM = 5f
    }
}
