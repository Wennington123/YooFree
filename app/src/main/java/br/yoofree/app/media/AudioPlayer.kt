package br.yoofree.app.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Toca o audio RTP das cameras Yoosee/GW.
 *
 * O audio chega multiplexado no RTP do video (payload type 8 = G.711 A-law,
 * 0 = mu-law) ou num canal intercalado proprio. Nao existe MediaCodec para
 * G.711, entao decodificamos para PCM 16-bit na mao.
 *
 * A taxa de amostragem varia por modelo (firmwares medidos: 8 kHz e 16 kHz).
 * Como essas cameras nao entregam SDP, inferimos pelo delta dos timestamps
 * RTP: pacotes de 20 ms valem 160 (8 kHz) ou 320 (16 kHz) ticks.
 */
class AudioPlayer {
    private val lock = Any()
    private var track: AudioTrack? = null
    private var sampleRate = 0
    private var lastTs = -1L

    @Volatile var enabled: Boolean = true

    fun feed(payload: ByteArray, payloadType: Int, rtpTimestamp: Long) {
        if (!enabled || payload.isEmpty()) return
        updateRate(rtpTimestamp)
        if (sampleRate == 0 && lastTs < 0) {
            // Primeiro pacote: guarda o timestamp e espera o proximo pra inferir.
            lastTs = rtpTimestamp
            return
        }
        val pcm = when (payloadType) {
            PT_PCMA -> alawToPcm(payload)
            PT_PCMU -> ulawToPcm(payload)
            else -> return
        }
        val t = trackOrCreate() ?: return
        try {
            if (t.playState != AudioTrack.PLAYSTATE_PLAYING) t.play()
            t.write(pcm, 0, pcm.size, AudioTrack.WRITE_NON_BLOCKING)
        } catch (t2: Throwable) {
            Log.e(TAG, "falha ao escrever audio", t2)
        }
    }

    private fun updateRate(ts: Long) {
        synchronized(lock) {
            if (sampleRate != 0) return
            if (lastTs >= 0) {
                val delta = (ts - lastTs) and 0xFFFF_FFFFL
                if (delta in 120..200) sampleRate = 8000
                else if (delta in 240..400) sampleRate = 16000
                else sampleRate = 8000
                Log.i(TAG, "taxa de audio inferida: ${sampleRate} Hz (delta $delta)")
            }
            lastTs = ts
        }
    }

    fun release() {
        synchronized(lock) {
            runCatching { track?.stop() }
            runCatching { track?.release() }
            track = null
            sampleRate = 0
            lastTs = -1
        }
    }

    private fun trackOrCreate(): AudioTrack? = synchronized(lock) {
        val rate = if (sampleRate > 0) sampleRate else 8000
        track ?: runCatching {
            val minBuf = AudioTrack.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(rate / 2)
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.onFailure { Log.e(TAG, "nao foi possivel criar AudioTrack", it) }
            .getOrNull()?.also { track = it }
    }

    private fun alawToPcm(src: ByteArray): ShortArray {
        val out = ShortArray(src.size)
        for (i in src.indices) out[i] = alawToPcm16(src[i].toInt() and 0xFF)
        return out
    }

    private fun ulawToPcm(src: ByteArray): ShortArray {
        val out = ShortArray(src.size)
        for (i in src.indices) out[i] = ulawToPcm16(src[i].toInt() and 0xFF)
        return out
    }

    private companion object {
        const val TAG = "YooFree-AUD"
        const val PT_PCMU = 0
        const val PT_PCMA = 8

        fun alawToPcm16(aLaw: Int): Short {
            val a = aLaw xor 0x55
            var t = (a and 0x0F) shl 4
            val seg = (a and 0x70) shr 4
            when (seg) {
                0 -> t += 8
                1 -> t += 0x108
                else -> {
                    t += 0x108
                    t = t shl (seg - 1)
                }
            }
            return (if ((a and 0x80) != 0) t else -t).toShort()
        }

        fun ulawToPcm16(uLaw: Int): Short {
            val u = uLaw.inv() and 0xFF
            var t = ((u and 0x0F) shl 3) + 0x84
            t = t shl ((u and 0x70) shr 4)
            return (if ((u and 0x80) != 0) (0x84 - t) else (t - 0x84)).toShort()
        }
    }
}
