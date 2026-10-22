package br.yoofree.app.rtsp

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Cliente RTSP minimo, feito sob medida para cameras Yoosee/GW.
 *
 * Essas cameras NAO falam RTSP padrao: o DESCRIBE responde 400 quando o cliente
 * manda Authorization, entao players prontos nao conseguem ler o SDP. Mas
 * SETUP + PLAY funcionam sem autenticacao. Aqui fazemos apenas o necessario.
 */
class RtspClient(
    private val host: String,
    private val port: Int = 554,
    private val path: String = "onvif1",
    private val listener: Listener
) {
    interface Listener {
        fun onConnected()
        fun onAccessUnit(nals: List<ByteArray>, ptsUs: Long)
        fun onError(message: String, cause: Throwable?)
        fun onDisconnected()

        /** Payload RTP de audio (G.711 etc). payloadType segue a RFC 3551. */
        fun onAudio(payload: ByteArray, payloadType: Int, rtpTimestamp: Long) {}

        /** Codigo RTSP da resposta ao ultimo PTZ (200 = aceito). */
        fun onPtzResult(code: Int) {}
    }

    companion object {
        const val TAG = "YooFree-RTSP"
        private const val UA = "LibVLC/2.2.6 (LIVE555 Streaming Media v2016.02.22)"
        private const val RTP_CLOCK = 90_000L
        private const val PT_PCMU = 0
        private const val PT_PCMA = 8
    }

    @Volatile private var running = false
    private val writeLock = Any()
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    @Volatile private var session = ""
    private var cseq = 0
    @Volatile private var lastRequestCseq = -1
    @Volatile private var lastPtzCseq = -1
    private var depacketizer = HevcDepacketizer()
    private var firstTs = -1L
    private var auCount = 0

    /** Canais intercalados negociados no SETUP (a camera pode devolver outros). */
    @Volatile private var videoChannel = 0
    @Volatile private var audioChannel = 2

    private val baseUrl get() = "rtsp://$host/$path"

    fun start() {
        if (running) return
        running = true
        Thread(::runLoop, "rtsp-client").apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
    }

    private fun runLoop() {
        try {
            Log.i(TAG, "conectando tcp $host:$port")
            val s = Socket()
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), 5000)
            socket = s
            input = s.getInputStream()
            output = s.getOutputStream()
            s.soTimeout = 5000

            // 1) DESCRIBE: se a camera responder SDP, usamos os tracks reais
            //    (evita chutar /track1 e tomar 405/404 de firmwares diferentes).
            var videoUrl: String? = null
            var audioUrl: String? = null
            runCatching {
                val (code, sdp) = describe()
                Log.i(TAG, "DESCRIBE -> $code")
                if (code == 200 && sdp.isNotBlank()) {
                    val tracks = parseSdpTracks(sdp)
                    videoUrl = tracks.video
                    audioUrl = tracks.audio
                    Log.i(TAG, "SDP video=$videoUrl audio=$audioUrl")
                }
            }.onFailure { Log.i(TAG, "DESCRIBE indisponivel: ${it.message}") }

            // 2) SETUP do video: tracks do SDP primeiro, depois os alvos conhecidos.
            var setupCode = 0
            var setupResponse = ""
            val setupTargets = (listOfNotNull(videoUrl) +
                listOf("$baseUrl/track1", baseUrl, "rtsp://$host/media/video1")).distinct()
            val transports = listOf(
                "RTP/AVP/TCP;unicast;interleaved=0-1",
                "RTP/AVP/TCP;unicast;interleaved=0-1;mode=\"PLAY\"",
            )
            outer@ for (target in setupTargets) {
                for (transport in transports) {
                    val resp = request(
                        "SETUP $target RTSP/1.0",
                        listOf("Transport: $transport", "User-Agent: $UA")
                    )
                    Log.i(TAG, "SETUP $target -> " + resp.replace("\r\n", " | "))
                    setupResponse = resp
                    setupCode = statusCode(resp)
                    if (setupCode == 200) {
                        interleaved(resp)?.let { (rtp, _) -> videoChannel = rtp }
                        break@outer
                    }
                    // 401/403 nao melhoram tentando outro alvo/transporte.
                    if (setupCode == 401 || setupCode == 403) break@outer
                    if (setupCode !in 400..499 && setupCode != 200) fail("SETUP $target", resp)
                }
            }
            if (setupCode != 200) fail("SETUP", setupResponse)
            session = Regex("Session:\\s*([0-9a-zA-Z]+)").find(setupResponse)?.groupValues?.get(1).orEmpty()

            // 3) Audio: trilha do SDP ou /track2. Se nao existir, o audio pode vir
            //    multiplexado no RTP do video (handleRtp cuida disso).
            val audioTargets = (listOfNotNull(audioUrl) + "$baseUrl/track2").distinct()
            for (target in audioTargets) {
                val audioResp = runCatching {
                    request(
                        "SETUP $target RTSP/1.0",
                        listOf("Transport: RTP/AVP/TCP;unicast;interleaved=2-3", "User-Agent: $UA")
                    )
                }.getOrNull() ?: continue
                Log.i(TAG, "SETUP audio $target -> " + audioResp.replace("\r\n", " | "))
                if (statusCode(audioResp) == 200) {
                    interleaved(audioResp)?.let { (rtp, _) -> audioChannel = rtp }
                    Log.i(TAG, "trilha de audio separada (canal $audioChannel)")
                    break
                }
            }

            // A camera pode nao devolver resposta textual ao PLAY e comecar a mandar
            // frames na hora. Nao exigimos a resposta: o streamLoop trata os dois casos.
            request("PLAY $baseUrl RTSP/1.0", listOf("User-Agent: $UA"), readResponse = false)

            listener.onConnected()
            streamLoop()
        } catch (t: Throwable) {
            Log.e(TAG, "erro: ${t.message}", t)
            if (running) listener.onError(t.message ?: t.javaClass.simpleName, t)
        } finally {
            runCatching { request("TEARDOWN $baseUrl RTSP/1.0", emptyList(), readResponse = false) }
            runCatching { socket?.close() }
            running = false
            Log.i(TAG, "sessao encerrada")
            listener.onDisconnected()
        }
    }

    private fun fail(stage: String, response: String) {
        val first = response.lineSequence().firstOrNull()?.trim().orEmpty()
        val hint = when (statusCode(response)) {
            401, 403 -> " (a camera exige senha no RTSP)"
            404 -> " (esse URI de SETUP nao existe no firmware)"
            405 -> " (firmware nao aceita SETUP nesse URI)"
            501 -> " (firmware nao implementa SETUP)"
            else -> ""
        }
        throw RuntimeException("$stage falhou: $first$hint")
    }

    private fun statusCode(response: String): Int =
        Regex("RTSP/\\d\\.\\d (\\d{3})").find(response)
            ?.groupValues?.get(1)?.toIntOrNull() ?: -1

    private fun responseHeader(response: String, name: String): String? =
        Regex("(?im)^" + Regex.escape(name) + ":\\s*(.+)$").find(response)
            ?.groupValues?.get(1)?.trim()

    /** Par RTP/RTCP de canais intercalados devolvido no header Transport. */
    private fun interleaved(response: String): Pair<Int, Int>? {
        val transport = responseHeader(response, "Transport") ?: return null
        val m = Regex("interleaved=(\\d+)(?:-(\\d+))?").find(transport) ?: return null
        val rtp = m.groupValues[1].toIntOrNull() ?: return null
        val rtcp = m.groupValues[2].toIntOrNull() ?: (rtp + 1)
        return rtp to rtcp
    }

    /** DESCRIBE + corpo SDP (Content-Length), quando o firmware suportar. */
    private fun describe(): Pair<Int, String> {
        val head = request(
            "DESCRIBE $baseUrl RTSP/1.0",
            listOf("Accept: application/sdp", "User-Agent: $UA")
        )
        val code = statusCode(head)
        if (code != 200) return code to ""
        val length = responseHeader(head, "Content-Length")?.toIntOrNull() ?: 0
        return code to readExactly(length)
    }

    private fun readExactly(n: Int): String {
        if (n <= 0) return ""
        val stream = input ?: return ""
        val buf = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = stream.read(buf, read, n - read)
            if (r < 0) break
            read += r
        }
        return String(buf, 0, read, Charsets.US_ASCII)
    }

    private class Tracks(val video: String?, val audio: String?)

    private fun parseSdpTracks(sdp: String): Tracks {
        var media = ""
        var video: String? = null
        var audio: String? = null
        for (raw in sdp.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("m=video") -> media = "video"
                line.startsWith("m=audio") -> media = "audio"
                line.startsWith("a=control:") -> {
                    val url = absoluteUrl(line.removePrefix("a=control:").trim()) ?: continue
                    if (media == "video" && video == null) video = url
                    if (media == "audio" && audio == null) audio = url
                }
            }
        }
        return Tracks(video, audio)
    }

    private fun absoluteUrl(control: String): String? {
        if (control.isEmpty() || control == "*") return null
        return when {
            control.startsWith("rtsp://") -> control
            control.startsWith("/") -> "rtsp://$host$control"
            else -> "$baseUrl/${control.trimStart('/')}"
        }
    }

    /** Envia uma requisicao e (opcionalmente) espera a resposta de cabecalho. */
    private fun request(line: String, headers: List<String>, readResponse: Boolean = true): String {
        synchronized(writeLock) {
            cseq += 1
            lastRequestCseq = cseq
            // Guarda o CSeq do PTZ para o streamLoop reconhecer a resposta.
            if (line.startsWith("SET_PARAMETER")) lastPtzCseq = cseq
            val sb = StringBuilder()
            sb.append(line).append("\r\n")
            sb.append("CSeq: ").append(cseq).append("\r\n")
            if (session.isNotEmpty()) sb.append("Session: ").append(session).append("\r\n")
            headers.forEach { sb.append(it).append("\r\n") }
            sb.append("\r\n")
            output?.write(sb.toString().toByteArray(Charsets.US_ASCII))
            output?.flush()
        }
        return if (readResponse) readHeader() else ""
    }

    private fun readHeader(): String {
        val stream = input ?: return ""
        val sb = StringBuilder()
        while (running || sb.isEmpty()) {
            val b = stream.read()
            if (b < 0) break
            sb.append(b.toChar())
            if (sb.endsWith("\r\n\r\n")) break
            if (sb.length > 16_384) break
        }
        return sb.toString()
    }

    private fun streamLoop() {
        val s = socket ?: return
        s.soTimeout = 1000
        val stream = input ?: return
        val chunk = ByteArray(65_536)
        var pending = ByteArray(0)
        var lastKeepAlive = System.currentTimeMillis()

        while (running) {
            val n = try {
                stream.read(chunk)
            } catch (_: SocketTimeoutException) {
                if (System.currentTimeMillis() - lastKeepAlive > 20_000) {
                    sendKeepAlive()
                    lastKeepAlive = System.currentTimeMillis()
                }
                continue
            }
            if (n < 0) break
            if (n == 0) continue

            val data = if (pending.isEmpty()) chunk.copyOf(n) else pending + chunk.copyOf(n)
            var off = 0
            while (off + 4 <= data.size) {
                if (data[off] == 0x24.toByte()) {
                    val channel = data[off + 1].toInt() and 0xFF
                    val len = ((data[off + 2].toInt() and 0xFF) shl 8) or (data[off + 3].toInt() and 0xFF)
                    if (off + 4 + len > data.size) break // frame incompleto
                    // canais negociados no SETUP (video e audio, quando separado)
                    if (channel == videoChannel || channel == audioChannel) {
                        handleRtp(data, off + 4, len)
                    }
                    off += 4 + len
                } else {
                    // resposta RTSP textual (ex.: do PLAY), pula ate o fim do cabecalho
                    val end = indexOfHeaderEnd(data, off)
                    if (end < 0) break
                    val text = String(data, off, end - off, Charsets.US_ASCII)
                    val firstLine = text.lineSequence().firstOrNull().orEmpty()
                    Log.i(TAG, "resp -> " + text.replace("\r\n", " | "))
                    val code = Regex("RTSP/\\d\\.\\d (\\d{3})").find(firstLine)
                        ?.groupValues?.get(1)?.toIntOrNull()
                    val respCseq = Regex("CSeq:\\s*(\\d+)", RegexOption.IGNORE_CASE).find(text)
                        ?.groupValues?.get(1)?.toIntOrNull()
                    if (code != null && respCseq != null && respCseq == lastPtzCseq) {
                        // Resposta de um SET_PARAMETER de PTZ: informa sem derrubar o stream.
                        listener.onPtzResult(code)
                    } else if (code != null && code >= 400) {
                        throw RuntimeException("camera respondeu: $firstLine")
                    }
                    off = end
                }
            }
            pending = if (off >= data.size) ByteArray(0) else data.copyOfRange(off, data.size)
        }
    }

    private fun handleRtp(data: ByteArray, off: Int, len: Int) {
        if (len < 12) return
        val b0 = data[off].toInt() and 0xFF
        if ((b0 ushr 6) != 2) return // versao RTP
        val csrc = b0 and 0x0F
        val hasExt = (b0 and 0x10) != 0
        val b1 = data[off + 1].toInt() and 0xFF
        val marker = (b1 and 0x80) != 0
        val payloadType = b1 and 0x7F

        val ts = ((data[off + 4].toLong() and 0xFF) shl 24) or
                 ((data[off + 5].toLong() and 0xFF) shl 16) or
                 ((data[off + 6].toLong() and 0xFF) shl 8) or
                 (data[off + 7].toLong() and 0xFF)

        var headerLen = 12 + 4 * csrc
        if (hasExt) {
            if (len < headerLen + 4) return
            val extWords = ((data[off + headerLen + 2].toInt() and 0xFF) shl 8) or
                           (data[off + headerLen + 3].toInt() and 0xFF)
            headerLen += 4 + 4 * extWords
        }
        if (headerLen >= len) return

        val payloadLen = len - headerLen
        val payload = data.copyOfRange(off + headerLen, off + len)

        // G.711 (PCMA/PCMU): audio veio multiplexado no mesmo canal RTP do video.
        if (payloadType == PT_PCMU || payloadType == PT_PCMA) {
            listener.onAudio(payload, payloadType, ts)
            return
        }

        val unit = depacketizer.onRtpPayload(payload, payloadLen, marker, ts) ?: return
        if (auCount++ == 0) Log.i(TAG, "primeiro access unit recebido (${unit.nals.size} NALs)")

        if (firstTs < 0) firstTs = unit.rtpTimestamp
        var delta = unit.rtpTimestamp - firstTs
        if (delta < 0) delta += 0x1_0000_0000L
        val ptsUs = delta * 1_000_000L / RTP_CLOCK
        listener.onAccessUnit(unit.nals, ptsUs)
    }

    private fun indexOfHeaderEnd(data: ByteArray, from: Int): Int {
        var i = from
        while (i + 4 <= data.size) {
            if (data[i] == 0x0D.toByte() && data[i + 1] == 0x0A.toByte() &&
                data[i + 2] == 0x0D.toByte() && data[i + 3] == 0x0A.toByte()
            ) return i + 4
            i++
        }
        return -1
    }

    private fun sendKeepAlive() {
        runCatching { request("GET_PARAMETER $baseUrl RTSP/1.0", emptyList(), readResponse = false) }
    }

    /**
     * PTZ pela MESMA sessao do stream (a camera aceita poucas sessoes simultaneas).
     * A resposta chega pelo streamLoop e e reportada em onPtzResult.
     */
    fun sendPtz(command: String) {
        if (!running) {
            listener.onError("PTZ $command: sem conexao", null)
            return
        }
        Thread({
            runCatching {
                request(
                    "SET_PARAMETER $baseUrl RTSP/1.0",
                    listOf("Content-type: ptzCmd: $command"),
                    readResponse = false
                )
                Log.i(TAG, "PTZ enviado: $command")
            }.onFailure { listener.onError("PTZ $command: ${it.message}", it) }
        }, "rtsp-ptz").apply { isDaemon = true }.start()
    }
}
