package br.yoofree.app.media

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.util.ArrayDeque

/**
 * Decodifica HEVC e joga no Surface (TextureView).
 *
 * Um UNICO lock protege tudo (fila + codec). Evita inversao de ordem de locks
 * entre a thread do RTSP e a thread de decode.
 */
class VideoDecoder(
    private val onError: (String) -> Unit
) {
    private val lock = Object()
    private val queue = ArrayDeque<Pair<ByteArray, Long>>()
    private var codec: MediaCodec? = null
    private var configured = false
    private var surface: Surface? = null
    private var vps: ByteArray? = null
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null
    @Volatile private var running = false
    private var firstFrame = true

    private val startCode = byteArrayOf(0, 0, 0, 1)

    fun start() {
        if (running) return
        running = true
        Thread(::decodeLoop, "hevc-decoder").apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
        synchronized(lock) {
            queue.clear()
            releaseCodec()
            lock.notifyAll()
        }
    }

    private fun releaseCodec() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        configured = false
    }

    fun setSurface(newSurface: Surface?) {
        synchronized(lock) {
            if (surface === newSurface) return
            surface = newSurface
            releaseCodec()
            firstFrame = true
            if (newSurface != null && vps != null && sps != null && pps != null) {
                configureInternal()
            }
            lock.notifyAll()
        }
    }

    fun feed(nals: List<ByteArray>, ptsUs: Long) {
        synchronized(lock) {
            var newCsd = false
            for (nal in nals) {
                if (nal.size < 2) continue
                when ((nal[0].toInt() ushr 1) and 0x3F) {
                    32 -> if (vps == null) { vps = nal; newCsd = true }
                    33 -> if (sps == null) { sps = nal; newCsd = true }
                    34 -> if (pps == null) { pps = nal; newCsd = true }
                }
            }
            if (!configured && surface != null && (newCsd || (vps != null && sps != null && pps != null))) {
                if (vps != null && sps != null && pps != null) configureInternal()
            }
            if (!configured) return
            queue.addLast(buildAnnexB(nals) to ptsUs)
            lock.notifyAll()
        }
    }

    private fun buildAnnexB(nals: List<ByteArray>): ByteArray {
        var size = 0
        for (nal in nals) size += startCode.size + nal.size
        val out = ByteArray(size)
        var pos = 0
        for (nal in nals) {
            System.arraycopy(startCode, 0, out, pos, startCode.size); pos += startCode.size
            System.arraycopy(nal, 0, out, pos, nal.size); pos += nal.size
        }
        return out
    }

    private fun configureInternal() {
        var c: MediaCodec? = null
        try {
            c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, WIDTH, HEIGHT)
            format.setByteBuffer("csd-0", ByteBuffer.wrap(withStartCode(vps!!)))
            format.setByteBuffer("csd-1", ByteBuffer.wrap(withStartCode(sps!!)))
            format.setByteBuffer("csd-2", ByteBuffer.wrap(withStartCode(pps!!)))
            c.configure(format, surface, null, 0)
            c.start()
            codec = c
            configured = true
            Log.i(TAG, "decoder HEVC configurado")
        } catch (t: Throwable) {
            runCatching { c?.release() }
            codec = null
            configured = false
            Log.e(TAG, "falha ao configurar", t)
            onError("Falha ao configurar decodificador: ${t.message}")
        }
    }

    private fun withStartCode(nal: ByteArray): ByteArray {
        val out = ByteArray(startCode.size + nal.size)
        System.arraycopy(startCode, 0, out, 0, startCode.size)
        System.arraycopy(nal, 0, out, startCode.size, nal.size)
        return out
    }

    private fun decodeLoop() {
        val info = MediaCodec.BufferInfo()
        while (running) {
            synchronized(lock) {
                val c = if (configured) codec else null
                if (c == null) {
                    runCatching { lock.wait(200) }
                    return@synchronized
                }

                try {
                    runDecodeCycle(c, info)
                } catch (e: IllegalStateException) {
                    // Codec em estado Released (morreu ou foi liberado por outro fluxo):
                    // descarta o codec; o proximo feed com CSD reconfigura do zero.
                    Log.e(TAG, "MediaCodec em estado invalido, liberando para reconfigurar", e)
                    releaseCodec()
                }
            }
        }
    }

    private fun runDecodeCycle(c: MediaCodec, info: MediaCodec.BufferInfo) {
        cycle@ while (true) {
            val inIndex = c.dequeueInputBuffer(10_000)
            if (inIndex >= 0) {
                val item = queue.pollFirst()
                val input = c.getInputBuffer(inIndex)
                if (input != null) {
                    input.clear()
                    if (item != null) {
                        input.put(item.first)
                        c.queueInputBuffer(inIndex, 0, item.first.size, item.second, 0)
                    } else {
                        c.queueInputBuffer(inIndex, 0, 0, 0, 0)
                    }
                }
            }
            while (true) {
                val outIndex = c.dequeueOutputBuffer(info, 0)
                when {
                    outIndex >= 0 -> {
                        if (firstFrame) {
                            Log.i(TAG, "primeiro frame decodificado")
                            firstFrame = false
                        }
                        runCatching { c.releaseOutputBuffer(outIndex, true) }
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        runCatching { c.outputFormat }.getOrNull()
                            ?.let { Log.i(TAG, "formato de saida: $it") }
                    }
                    else -> break@cycle
                }
            }
        }
    }

    private companion object {
        const val TAG = "YooFree-DEC"
        const val WIDTH = 1920
        const val HEIGHT = 1080
    }
}
