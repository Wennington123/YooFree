package br.yoofree.app.rtsp

import java.io.ByteArrayOutputStream

/**
 * Depacketizer HEVC / H.265 sobre RTP (RFC 7798).
 *
 * A câmera Yoosee manda H.265 em unidades RTP com payload type dinamico:
 *  - tipo <= 47: NAL unit unico
 *  - tipo 48  : AP (aggregation packet)
 *  - tipo 49  : FU (fragmentation unit)
 *  - tipo 50  : PACI (ignorado)
 *
 * O bit marker do RTP indica o fim de um access unit.
 */
class HevcDepacketizer {

    data class AccessUnit(val nals: List<ByteArray>, val rtpTimestamp: Long)

    private val au = ArrayList<ByteArray>()
    private var fu = ByteArrayOutputStream()

    fun onRtpPayload(payload: ByteArray, length: Int, marker: Boolean, ts: Long): AccessUnit? {
        if (length < 2) return null
        val type = (payload[0].toInt() ushr 1) and 0x3F

        when {
            type <= 47 -> au.add(payload.copyOf(length))

            type == 48 -> { // AP: [size16][NAL]...
                var i = 2
                while (i + 2 <= length) {
                    val l = ((payload[i].toInt() and 0xFF) shl 8) or (payload[i + 1].toInt() and 0xFF)
                    i += 2
                    if (i + l > length) break
                    au.add(payload.copyOfRange(i, i + l))
                    i += l
                }
            }

            type == 49 -> { // FU: payload[0..1] header, payload[2] FU header, dados a partir de [3]
                val fuHeader = payload[2].toInt() and 0xFF
                val start = (fuHeader and 0x80) != 0
                val end = (fuHeader and 0x40) != 0
                val fuType = fuHeader and 0x3F
                if (start) {
                    fu = ByteArrayOutputStream()
                    val h0 = ((payload[0].toInt() and 0x81) or (fuType shl 1))
                    fu.write(h0)
                    fu.write(payload[1].toInt())
                }
                if (length > 3) fu.write(payload, 3, length - 3)
                if (end) {
                    au.add(fu.toByteArray())
                    fu = ByteArrayOutputStream()
                }
            }
        }

        if (marker) {
            if (au.isEmpty()) return null
            val out = AccessUnit(au.toList(), ts)
            au.clear()
            return out
        }
        return null
    }
}
