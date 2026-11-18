package br.yoofree.app.rtsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HevcDepacketizerTest {

    private fun fuPayload(start: Boolean, end: Boolean, type: Int, data: ByteArray): ByteArray {
        val p = ByteArray(3 + data.size)
        p[0] = (49 shl 1).toByte() // payload header: tipo 49 (FU)
        p[1] = 0x01
        p[2] = (((if (start) 0x80 else 0) or (if (end) 0x40 else 0)) or type).toByte()
        data.copyInto(p, 3)
        return p
    }

    private fun rtp(payload: ByteArray, marker: Boolean, ts: Long = 1000) =
        dep.onRtpPayload(payload, payload.size, marker, ts)

    private val dep = HevcDepacketizer()

    @Test
    fun `nal unico com marker vira access unit`() {
        val nal = byteArrayOf(0x02, 0x01, 0x0A, 0x0B)
        val au = rtp(nal, marker = true)
        assertEquals(1, au!!.nals.size)
        assertEquals(0x02.toByte(), au.nals[0][0])
        assertEquals(4, au.nals[0].size)
    }

    @Test
    fun `dois nals sem marker agrupam no marker final`() {
        dep.onRtpPayload(byteArrayOf(0x02, 0x01, 0x01), 3, false, 1000)
        val au = dep.onRtpPayload(byteArrayOf(0x02, 0x01, 0x02), 3, true, 1000)
        assertEquals(2, au!!.nals.size)
    }

    @Test
    fun `fu remonta nal com header original`() {
        assertNull(rtp(fuPayload(start = true, end = false, type = 1, data = "ABC".toByteArray()), false))
        val au = rtp(fuPayload(start = false, end = true, type = 1, data = "DEF".toByteArray()), true)
        val nal = au!!.nals.single()
        assertEquals((1 shl 1).toByte(), nal[0]) // tipo 1 reconstruido
        assertEquals(0x01.toByte(), nal[1])
        assertEquals("ABCDEF".toByteArray().toList(), nal.drop(2).toList())
    }

    @Test
    fun `fu sem start e descartado e nao corrompe o fluxo`() {
        // Fragmento de continuacao que chegou sem o start (desync): tem que ser
        // descartado, e o proximo FU completo tem que sair inteiro.
        assertNull(rtp(fuPayload(start = false, end = false, type = 1, data = "LIXO".toByteArray()), false))
        assertNull(rtp(fuPayload(start = true, end = false, type = 1, data = "ABC".toByteArray()), false))
        val au = rtp(fuPayload(start = false, end = true, type = 1, data = "DEF".toByteArray()), true)
        assertEquals(1, au!!.nals.size)
        assertEquals("ABCDEF".toByteArray().toList(), au.nals.single().drop(2).toList())
    }

    @Test
    fun `end sem start nao emite nal vazio`() {
        assertNull(rtp(fuPayload(start = false, end = true, type = 1, data = "XYZ".toByteArray()), true))
    }

    @Test
    fun `ap extrai os nals agregados`() {
        val nal1 = byteArrayOf(0x02, 0x01, 0x11)
        val nal2 = byteArrayOf(0x02, 0x01, 0x22, 0x23)
        val p = ByteArray(2 + 2 + nal1.size + 2 + nal2.size)
        var i = 0
        p[i++] = (48 shl 1).toByte()
        p[i++] = 0x01 // segundo byte do header do AP (o depacketizer ignora)
        fun put16(v: Int) { p[i++] = (v ushr 8).toByte(); p[i++] = v.toByte() }
        fun putNal(n: ByteArray) { put16(n.size); n.copyInto(p, i); i += n.size }
        putNal(nal1)
        putNal(nal2)
        val au = rtp(p.copyOf(i), marker = true)
        assertEquals(listOf(nal1.toList(), nal2.toList()), au!!.nals.map { it.toList() })
    }
}
