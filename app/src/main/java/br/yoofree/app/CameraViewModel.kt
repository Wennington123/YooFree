package br.yoofree.app

import android.app.Application
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

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

    fun zoomBy(delta: Float) {
        zoom = (zoom + delta).coerceIn(1.0f, 5.0f)
    }

    fun cycleRotation() {
        rotation = (rotation + 90) % 360
    }

    fun onSurfaceReady(): Boolean = connected

    override fun onCleared() {
        session?.disconnect()
        session = null
    }
}
