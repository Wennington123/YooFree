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
    }

    companion object {
        const val TAG = "YooFree-RTSP"
        private const val UA = "LibVLC/2.2.6 (LIVE555 Streaming Media v2016.02.22)"
        private const val RTP_CLOCK = 90_000L
    }

    @Volatile private var running = false
    private val writeLock = Any()
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    @Volatile private var session = ""
    private var cseq = 0
    private var depacketizer = HevcDepacketizer()
    private var firstTs = -1L
    private var auCount = 0

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

            val setup = request(
                "SETUP $baseUrl/track1 RTSP/1.0",
                listOf("Transport: RTP/AVP/TCP;unicast;interleaved=0-1", "User-Agent: $UA")
            )
            Log.i(TAG, "SETUP -> " + setup.replace("\r\n", " | "))
            if (!setup.contains(" 200 ")) fail("SETUP", setup)
            session = Regex("Session:\\s*([0-9a-zA-Z]+)").find(setup)?.groupValues?.get(1).orEmpty()

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
        throw RuntimeException("$stage falhou: $first")
    }

    /** Envia uma requisicao e (opcionalmente) espera a resposta de cabecalho. */
    private fun request(line: String, headers: List<String>, readResponse: Boolean = true): String {
        synchronized(writeLock) {
            cseq += 1
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
                    if (channel == 0) handleRtp(data, off + 4, len)
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
                    if (code != null && code >= 400) {
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
     * A resposta chega pelo streamLoop e e apenas registrada.
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
