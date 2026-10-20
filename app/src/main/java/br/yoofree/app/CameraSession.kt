package br.yoofree.app

import android.content.Context
import android.view.Surface
import br.yoofree.app.media.AudioPlayer
import br.yoofree.app.media.Mp4Muxer
import br.yoofree.app.media.VideoDecoder
import br.yoofree.app.rtsp.RtspClient
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Junta cliente RTSP + decoder + audio + muxer MP4 numa sessao so. */
class CameraSession(
    private val context: Context,
    private val host: String,
    private val onState: (String) -> Unit
) : RtspClient.Listener {

    private val client = RtspClient(host, listener = this)
    private val decoder = VideoDecoder { onState("Erro: $it") }
    private val audio = AudioPlayer()
    private var muxer: Mp4Muxer? = null
    @Volatile private var sawError = false

    private var vps: ByteArray? = null
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null

    fun connect() {
        sawError = false
        onState("Conectando a $host…")
        decoder.start()
        client.start()
    }

    fun disconnect() {
        client.stop()
        stopRecording()
        decoder.stop()
        audio.release()
    }

    fun setSurface(surface: Surface?) {
        decoder.setSurface(surface)
    }

    fun setAudioEnabled(enabled: Boolean) {
        audio.enabled = enabled
        if (!enabled) audio.release()
    }

    fun ptz(command: String) = client.sendPtz(command)

    override fun onAudio(payload: ByteArray, payloadType: Int, rtpTimestamp: Long) {
        audio.feed(payload, payloadType, rtpTimestamp)
    }

    override fun onPtzResult(code: Int) {
        if (code >= 400) onState("PTZ recusado pela camera ($code)")
    }

    fun isRecording(): Boolean = muxer != null

    fun toggleRecording(): Boolean {
        val current = muxer
        if (current != null) {
            val error = current.stop()
            val samples = current.sampleCount
            val file = current.fileBytes
            muxer = null
            onState(
                if (error != null) "ERRO: $error"
                else "Gravacao salva ($samples quadros, ${file / 1024} KB)"
            )
            return false
        }
        val v = vps
        val s = sps
        val p = pps
        if (v == null || s == null || p == null) {
            onState("Aguarde o video iniciar para gravar")
            return false
        }
        val dir = File(context.getExternalFilesDir(null), "YooFree").apply { mkdirs() }
        val name = "YooFree_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        return try {
            muxer = Mp4Muxer(File(dir, name), v, s, p, onError = { onState(it) })
            onState("Gravando: $name")
            true
        } catch (t: Throwable) {
            muxer = null
            onState("Erro ao gravar: ${t.message}")
            false
        }
    }

    private fun stopRecording() {
        muxer?.stop()
        muxer = null
    }

    override fun onConnected() = onState("Conectado — video HEVC")

    override fun onAccessUnit(nals: List<ByteArray>, ptsUs: Long) {
        if (vps == null || sps == null || pps == null) {
            for (nal in nals) {
                if (nal.size < 2) continue
                when ((nal[0].toInt() ushr 1) and 0x3F) {
                    32 -> if (vps == null) vps = nal
                    33 -> if (sps == null) sps = nal
                    34 -> if (pps == null) pps = nal
                }
            }
        }
        decoder.feed(nals, ptsUs)
        muxer?.addAccessUnit(nals, ptsUs)
    }

    override fun onError(message: String, cause: Throwable?) {
        sawError = true
        onState("Erro: $message")
    }

    override fun onDisconnected() {
        if (!sawError) onState("Desconectado")
    }
}
