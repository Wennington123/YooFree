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
    var status by mutableStateOf("Parado")
    var connected by mutableStateOf(false)
    var recording by mutableStateOf(false)
    var zoom by mutableStateOf(1.0f)
    var rotation by mutableStateOf(0)
    var scanning by mutableStateOf(false)
    var foundHosts by mutableStateOf(listOf<String>())
    var audioOn by mutableStateOf(true)

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
            val s = CameraSession(getApplication(), target) { status = it }
            session = s
            // O Surface do TextureView normalmente ja existe antes de conectar.
            s.setSurface(currentSurface)
            s.setAudioEnabled(audioOn)
            s.connect()
            connected = true
        }
    }

    fun toggleRecording() {
        val s = session ?: return
        recording = s.toggleRecording()
    }

    fun ptz(command: String) {
        session?.ptz(command)
    }

    fun toggleAudio() {
        audioOn = !audioOn
        session?.setAudioEnabled(audioOn)
    }

    fun zoomBy(delta: Float) {
        zoom = (zoom + delta).coerceIn(1.0f, 5.0f)
    }

    /** Zoom por gesto (pinch): fator multiplicativo, 1.0 = sem mudanca. */
    fun zoomByFactor(factor: Float) {
        zoom = (zoom * factor).coerceIn(1.0f, 5.0f)
    }

    fun cycleRotation() {
        rotation = (rotation + 90) % 360
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
}
