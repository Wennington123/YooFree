package br.yoofree.app.diag

import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import br.yoofree.app.BuildConfig

/**
 * Buffer in-memory dos eventos relevantes do app. Substitui a dependencia de
 * READ_LOGS (que nao funciona em build de usuario) e entrega um relato enxuto
 * de quem usa: ultimo IDR, backpressure, queda de conexao, trocas de surface.
 */
object YooLog {
    private const val MAX_LINES = 800
    private val lock = Any()
    private val lines = ArrayDeque<String>(MAX_LINES)

    fun add(level: String, tag: String, msg: String) {
        val ts = DateFormat.format("HH:mm:ss.SSS", System.currentTimeMillis())
        synchronized(lock) {
            lines.addLast("$ts $level/$tag: $msg")
            while (lines.size > MAX_LINES) lines.removeFirst()
        }
    }

    fun dump(): String = synchronized(lock) { lines.joinToString("\n") }

    /**
     * Monta o pacote final com cabecalho de dispositivo/versao. A secao de
     * eventos e aparada pelos finais mais recentes ate caber numa URL de
     * issue do GitHub (~8k chars): corpo de issue util tem que caber inteiro.
     */
    fun buildReport(context: Context, extra: Map<String, String>): String {
        val sb = StringBuilder()
        sb.append("Relatorio automatico do YooFree (bug/camera travando ou preta)\n")
        sb.append("\n**Versao:** ").append(BuildConfig.VERSION_NAME)
            .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
        sb.append("**Device:** ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
        sb.append("**Android:** ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        for ((k, v) in extra) sb.append("**").append(k).append(":** ").append(v).append("\n")
        sb.append("\n<details><summary>Log de eventos</summary>\n\n```\n")
        val events = dump()
        val budget = 5500
        val trimmed = if (events.length > budget) {
            // mantem os finais (mais recentes), corta pro primeiro \n pra nao rasgar linha
            var start = events.length - budget
            while (start < events.length && events[start] != '\n') start++
            "…(inicio aparado)…\n" + events.substring(minOf(start + 1, events.length))
        } else events
        sb.append(trimmed)
        sb.append("\n```\n</details>\n")
        return sb.toString()
    }
}
