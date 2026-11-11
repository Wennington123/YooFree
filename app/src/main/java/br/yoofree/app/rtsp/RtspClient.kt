package br.yoofree.app.rtsp

import android.util.Log
import br.yoofree.app.diag.YooLog
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger

/**
 * Cliente RTSP minimo, feito sob medida para cameras Yoosee/GW.
 *
 * Essas cameras NAO falam RTSP padrao: o DESCRIBE responde 400 quando o cliente
 * manda Authorization Basic, entao players prontos nao conseguem ler o SDP. Mas
 * SETUP + PLAY funcionam sem autenticacao (ou com HTTP Digest, suportado aqui).
 * Aqui fazemos apenas o necessario.
 */
class RtspClient(
    private val host: String,
    private val port: Int = 554,
    private val path: String = "onvif1",
    private val password: String = "",
    private val username: String = "admin",
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
    @Volatile private var lastKeepAliveCseq = -1
    private var depacketizer = HevcDepacketizer()
    private var firstTs = -1L
    private var auCount = 0
    @Volatile private var lastVideoTarget = ""

    private val transports = listOf(
        "RTP/AVP/TCP;unicast;interleaved=0-1",
        "RTP/AVP/TCP;unicast;interleaved=0-1;mode=\"PLAY\"",
    )

    // Auth Digest (RFC 2617): aprendida no primeiro 401 e reusada nas proximas
    // requisicoes. Sem senha configurada, nada e enviado (comportamento antigo).
    @Volatile private var authHeader: String? = null
    private val authNc = AtomicInteger(0)
    private val cnonce = BigInteger(64, SecureRandom()).toString(16)

    /** Canais intercalados negociados no SETUP (a camera pode devolver outros). */
    @Volatile private var videoChannel = 0
    @Volatile private var audioChannel = 2

    private val baseUrl
        get() = if (port == 554) "rtsp://$host/$path" else "rtsp://$host:$port/$path"

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
            connect()

            // 1) SETUP direto, SEM DESCRIBE: em firmwares Yoosee/GW (HIipCamera) o
            //    DESCRIBE cobra auth e empoisona a conexao - depois do 401, o SETUP
            //    leva reset. OPTIONS -> SETUP -> PLAY sem auth funciona: o video vem
            //    em /track1 e o audio em /track2 (G.711 tambem chega multiplexado).
            //    DESCRIBE vira plano B, so pra achar tracks de firmwares exoticas.
            var videoUrl: String? = null
            var audioUrl: String? = null
            val directTargets = listOf("$baseUrl/track1", baseUrl, "rtsp://$host/media/video1")
            var (setupCode, setupResponse) = runSetup(directTargets)

            if (setupCode != 200) {
                // 2) Plano B: DESCRIBE em conexao NOVA (a atual pode ter sido fechada
                //    pelos SETUPS recusados; e o proprio DESCRIBE pode empoisonar).
                reconnect("SETUP direto falhou")
                runCatching {
                    val (code, sdp) = describe()
                    Log.i(TAG, "DESCRIBE -> $code")
                    if (code == 200 && sdp.isNotBlank()) {
                        val tracks = parseSdpTracks(sdp)
                        videoUrl = tracks.video
                        audioUrl = tracks.audio
                        Log.i(TAG, "SDP video=$videoUrl audio=$audioUrl")
                    } else if ((code == 401 || code == 403) && password.isEmpty()) {
                        // Sem o aviso aqui, o usuario so via "SETUP falhou: " vazio.
                        throw RuntimeException(
                            "A camera exige senha RTSP (Digest) - preencha o campo de senha e conecte de novo"
                        )
                    }
                }.onFailure { throwIfAuthError(it); Log.i(TAG, "DESCRIBE indisponivel: ${it.message}") }

                val sdpTargets = (listOfNotNull(videoUrl) + directTargets).distinct()
                reconnect("antes do SETUP com SDP")
                runSetup(sdpTargets).let { (code, resp) ->
                    setupCode = code
                    setupResponse = resp
                }
                if (setupCode != 200) fail("SETUP", setupResponse)
            }
            session = Regex("Session:\\s*([0-9a-zA-Z]+)").find(setupResponse)?.groupValues?.get(1).orEmpty()
            val videoTarget = lastVideoTarget

            // 3) Audio: trilha do SDP quando anunciada; sem SDP nenhum, chuta
            //    /track2 (a HIipCamera aceita: 200). Se o SETUP do audio FECHAR a
            //    conexao (firmware estrita), reconecta e refaz so o video: o G.711
            //    continua chegando multiplexado no canal do video.
            val audioTargets = when {
                audioUrl != null -> listOf(audioUrl)
                videoUrl == null -> listOf("$baseUrl/track2")
                else -> emptyList()
            }
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
                if (audioResp.isEmpty()) {
                    Log.i(TAG, "SETUP audio fechou a conexao; refazendo so o video")
                    reconnect("apos o audio fechar a conexao")
                    runSetup(listOf(videoTarget)).let { (code, resp) ->
                        if (code != 200) fail("SETUP", resp)
                        session = Regex("Session:\\s*([0-9a-zA-Z]+)").find(resp)?.groupValues?.get(1).orEmpty()
                    }
                    break
                }
            }

            // A camera pode nao devolver resposta textual ao PLAY e comecar a mandar
            // frames na hora. Nao exigimos a resposta: o streamLoop trata os dois casos.
            request("PLAY $baseUrl RTSP/1.0", listOf("User-Agent: $UA"), readResponse = false)

            listener.onConnected()
            YooLog.add("INFO", "RTSP", "conectado (SETUP OK, PLAY enviado)")
            streamLoop()
        } catch (t: Throwable) {
            Log.e(TAG, "erro: ${t.message}", t)
            YooLog.add("ERROR", "RTSP", "erro: ${t.message}")
            if (running) listener.onError(t.message ?: t.javaClass.simpleName, t)
        } finally {
            runCatching { request("TEARDOWN $baseUrl RTSP/1.0", emptyList(), readResponse = false) }
            runCatching { socket?.close() }
            running = false
            Log.i(TAG, "sessao encerrada")
            YooLog.add("INFO", "RTSP", "sessao encerrada")
            listener.onDisconnected()
        }
    }

    private fun throwIfAuthError(t: Throwable) {
        // O aviso de senha precisa atravessar o runCatching do DESCRIBE.
        if (t.message?.contains("exige senha RTSP") == true) throw t
    }

    private fun connect() {
        val s = Socket()
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(host, port), 5000)
        socket = s
        input = s.getInputStream()
        output = s.getOutputStream()
        s.soTimeout = 5000
    }

    private fun reconnect(reason: String) {
        Log.i(TAG, "reconectando ($reason)")
        runCatching { socket?.close() }
        session = ""
        connect()
    }

    /** Tenta SETUP em alvos x transportes; devolve (codigo, resposta bruta). */
    private fun runSetup(targets: List<String>): Pair<Int, String> {
        var code = -1
        var resp = ""
        outer@ for (target in targets) {
            for (transport in transports) {
                val r = try {
                    request(
                        "SETUP $target RTSP/1.0",
                        listOf("Transport: $transport", "User-Agent: $UA")
                    )
                } catch (e: Exception) {
                    // Camera fechou a conexao no meio: sem sentido tentar os
                    // demais alvos neste socket; o plano B reconecta.
                    Log.i(TAG, "SETUP $target: conexao caiu (${e.message})")
                    break@outer
                }
                Log.i(TAG, "SETUP $target -> " + r.replace("\r\n", " | "))
                resp = r
                code = statusCode(r)
                if (code == 200) {
                    interleaved(r)?.let { (rtp, _) -> videoChannel = rtp }
                    lastVideoTarget = target
                    break@outer
                }
                // 401/403 nao melhoram tentando outro alvo/transporte. Resposta
                // vazia (conexao fechada) segue pro proximo alvo/para o plano B.
                if (code == 401 || code == 403) break@outer
                if (code !in 400..499 && code != -1) fail("SETUP $target", r)
            }
        }
        return code to resp
    }

    private fun fail(stage: String, response: String) {
        val first = response.lineSequence().firstOrNull()?.trim().orEmpty()
        val hint = when {
            first.isEmpty() -> when {
                authHeader != null -> " (a camera fechou a conexao; a senha esta correta?)"
                password.isEmpty() ->
                    " (a camera fechou a conexao sem responder; se ela tiver senha, preencha o campo de senha)"
                else -> " (a camera fechou a conexao sem responder)"
            }
            statusCode(response) == 401 || statusCode(response) == 403 ->
                " (a camera exige senha no RTSP)"
            statusCode(response) == 404 -> " (esse URI de SETUP nao existe no firmware)"
            statusCode(response) == 405 -> " (firmware nao aceita SETUP nesse URI)"
            statusCode(response) == 501 -> " (firmware nao implementa SETUP)"
            statusCode(response) == 400 && authHeader != null -> " (senha incorreta?)"
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

    /**
     * Envia uma requisicao e (opcionalmente) espera a resposta de cabecalho.
     * Em 401 com desafio Digest (e senha configurada), calcula o Authorization
     * e reenvia a mesma requisicao uma vez.
     */
    private fun request(line: String, headers: List<String>, readResponse: Boolean = true): String {
        val first = send(line, headers)
        if (!readResponse) return ""
        if (statusCode(first) == 401 && password.isNotEmpty()) {
            val challenge = responseHeader(first, "WWW-Authenticate")?.trim()
            if (challenge != null && challenge.startsWith("Digest", ignoreCase = true)) {
                buildDigestHeader(line, challenge)?.let {
                    Log.i(TAG, "401 Digest: reenviando com Authorization")
                    authHeader = it
                    return send(line, headers)
                }
                Log.w(TAG, "desafio Digest nao suportado: $challenge")
            }
        }
        return first
    }

    private fun send(line: String, headers: List<String>): String {
        synchronized(writeLock) {
            cseq += 1
            lastRequestCseq = cseq
            // Guarda o CSeq do PTZ para o streamLoop reconhecer a resposta.
            if (line.startsWith("SET_PARAMETER")) lastPtzCseq = cseq
            if (line.startsWith("GET_PARAMETER")) lastKeepAliveCseq = cseq
            val sb = StringBuilder()
            sb.append(line).append("\r\n")
            sb.append("CSeq: ").append(cseq).append("\r\n")
            if (session.isNotEmpty()) sb.append("Session: ").append(session).append("\r\n")
            authHeader?.let { sb.append("Authorization: ").append(it).append("\r\n") }
            headers.forEach { sb.append(it).append("\r\n") }
            sb.append("\r\n")
            output?.write(sb.toString().toByteArray(Charsets.US_ASCII))
            output?.flush()
        }
        return readHeader()
    }

    /** Monta o header Authorization: Digest para o metodo/URI da requisicao. */
    private fun buildDigestHeader(requestLine: String, challenge: String): String? {
        // (?:^|[,\s]) antes da chave: "nonce" nao pode casar dentro de "cnonce".
        fun value(key: String): String? {
            val m = Regex("(?i)(?:^|[,\\s])$key\\s*=\\s*(\"([^\"]*)\"|([^,\\s]+))").find(challenge)
                ?: return null
            return m.groupValues[2].ifEmpty { m.groupValues[3] }
        }
        val algorithm = value("algorithm") ?: "MD5"
        if (!algorithm.equals("MD5", true)) return null // MD5-sess: fora do escopo
        val realm = value("realm") ?: return null
        val nonce = value("nonce") ?: return null
        val qop = value("qop")
            ?.split(',')?.map { it.trim() }
            ?.firstOrNull { it.equals("auth", true) } ?: ""
        val opaque = value("opaque")
        val method = requestLine.substringBefore(' ').uppercase()
        val uri = requestLine.split(' ').getOrNull(1) ?: return null

        val ha1 = md5("$username:$realm:$password")
        val ha2 = md5("$method:$uri")
        val nc = String.format("%08x", authNc.incrementAndGet())
        val response = if (qop.isEmpty()) md5("$ha1:$nonce:$ha2")
        else md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2")

        val sb = StringBuilder("Digest ")
            .append("username=\"").append(username).append("\", ")
            .append("realm=\"").append(realm).append("\", ")
            .append("nonce=\"").append(nonce).append("\", ")
            .append("uri=\"").append(uri).append("\", ")
            .append("response=\"").append(response).append("\", ")
            .append("algorithm=MD5")
        if (qop.isNotEmpty()) {
            sb.append(", qop=auth, nc=").append(nc).append(", cnonce=\"").append(cnonce).append("\"")
        }
        if (opaque != null) sb.append(", opaque=\"").append(opaque).append("\"")
        return sb.toString()
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

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
            // Keepalive por TEMPO, nao so no timeout de leitura: com video
            // fluindo o read nunca estoura, e sem cliente->servidor o servidor
            // corta a sessao (60s no gortsplib).
            if (System.currentTimeMillis() - lastKeepAlive > 20_000) {
                sendKeepAlive()
                lastKeepAlive = System.currentTimeMillis()
            }
            val n = try {
                stream.read(chunk)
            } catch (_: SocketTimeoutException) {
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
                    } else if (code != null && code >= 400 && respCseq == lastKeepAliveCseq) {
                        // Keepalive recusado (nonce expirado etc.): loga e segue;
                        // derrubar o stream por isso e pior do que perder um ping.
                        Log.w(TAG, "keepalive respondeu $code; seguindo")
                    } else if (code != null && code >= 400) {
                        YooLog.add("ERROR", "RTSP", "camera respondeu: $firstLine")
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
