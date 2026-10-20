package br.yoofree.app.net

import android.util.Log
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Descobre cameras IP na rede local.
 *
 * 1. acha a /24 do proprio aparelho (sem permissao extra, so NetworkInterface);
 * 2. testa a porta RTSP (554) de cada host em paralelo;
 * 3. confirma que o host responde RTSP com OPTIONS (evita falso positivo).
 *
 * Callbacks podem vir de threads de trabalho; quem consome deve postar na main.
 */
class LanScanner(
    private val onFound: (String) -> Unit,
    private val onDone: () -> Unit
) {
    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
    }

    fun start() {
        Thread(::run, "lan-scanner").apply { isDaemon = true }.start()
    }

    private fun run() {
        try {
            val hosts = localHosts()
            if (hosts.isEmpty()) {
                Log.w(TAG, "nenhuma rede IPv4 local encontrada")
                return
            }
            val pool = Executors.newFixedThreadPool(64)
            try {
                for (host in hosts) {
                    if (cancelled.get()) break
                    pool.execute {
                        if (cancelled.get()) return@execute
                        if (looksLikeCamera(host)) onFound(host)
                    }
                }
            } finally {
                pool.shutdown()
                while (!pool.awaitTermination(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    if (cancelled.get()) pool.shutdownNow()
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "falha ao varrer rede", t)
        } finally {
            onDone()
        }
    }

    /** Todos os IPs da(s) /24 local(is), exceto o proprio aparelho. */
    private fun localHosts(): List<String> {
        val prefixes = LinkedHashSet<String>()
        val selfIps = HashSet<String>()
        try {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    val ip = addr as? Inet4Address ?: continue
                    if (addr.isLoopbackAddress || addr.isLinkLocalAddress) continue
                    val octets = addr.address.map { it.toInt() and 0xFF }
                    selfIps.add(octets.joinToString("."))
                    prefixes.add("${octets[0]}.${octets[1]}.${octets[2]}.")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "erro lendo interfaces", t)
        }
        val out = ArrayList<String>()
        for (prefix in prefixes) {
            for (last in 1..254) {
                val ip = "$prefix$last"
                if (ip !in selfIps) out.add(ip)
            }
        }
        return out
    }

    private fun looksLikeCamera(ip: String): Boolean {
        val socket = Socket()
        return try {
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(ip, RTSP_PORT), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            val out = socket.getOutputStream()
            val request = "OPTIONS rtsp://$ip/ RTSP/1.0\r\n" +
                "CSeq: 1\r\n" +
                "User-Agent: YooFree\r\n\r\n"
            out.write(request.toByteArray(Charsets.US_ASCII))
            out.flush()
            val buf = ByteArray(256)
            val n = socket.getInputStream().read(buf)
            if (n <= 0) return false
            val head = String(buf, 0, n, Charsets.US_ASCII)
            head.startsWith("RTSP/")
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { socket.close() }
        }
    }

    private companion object {
        const val TAG = "YooFree-SCAN"
        const val RTSP_PORT = 554
        const val CONNECT_TIMEOUT_MS = 350
        const val READ_TIMEOUT_MS = 700
    }
}
