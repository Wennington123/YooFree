package br.yoofree.app.media

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * Muxer MP4 proprio para HEVC (uma faixa de video, moov no fim).
 *
 * Feito na mao porque o MediaMuxer do Android crasha nativamente com HEVC em
 * alguns aparelhos (Samsung). Aqui controlamos os boxes e escrevemos direto.
 */
class Mp4Muxer(
    private val file: File,
    private val vps: ByteArray,
    private val sps: ByteArray,
    private val pps: ByteArray,
    private val width: Int = 1920,
    private val height: Int = 1080,
    private val onError: (String) -> Unit = {}
) {
    private val out: RandomAccessFile
    private val sizes = ArrayList<Int>()
    private val times = ArrayList<Long>()
    private val syncs = ArrayList<Int>()
    private val mdatDataStart: Long
    private var firstTs = -1L
    private var closed = false

    var sampleCount = 0
        private set

    init {
        file.parentFile?.mkdirs()
        out = RandomAccessFile(file, "rw")
        out.setLength(0)
        out.write(
            box(
                "ftyp",
                "isom".toByteArray() + u32(0x200) +
                    "isom".toByteArray() + "iso2".toByteArray() +
                    "hvc1".toByteArray() + "mp41".toByteArray()
            )
        )
        out.write(u32(0)) // tamanho do mdat (corrigido no fim)
        out.write("mdat".toByteArray())
        mdatDataStart = out.filePointer
    }

    fun addAccessUnit(nals: List<ByteArray>, ptsUs: Long) {
        if (closed) return
        val chunk = buildSample(nals) ?: return
        sizes.add(chunk.size)
        if (firstTs < 0) firstTs = ptsUs
        var ts = (ptsUs - firstTs) * TS / 1_000_000L
        if (times.isNotEmpty() && ts <= times.last()) ts = times.last() + 1
        times.add(ts)
        if (isKeyframe(nals)) syncs.add(sizes.size)
        try {
            out.write(chunk)
            sampleCount++
        } catch (t: Throwable) {
            onError("gravacao: ${t.message}")
        }
    }

    /** Junta os NALs do access unit em length-prefixed, sem os parametros (vão no hvcC). */
    private fun buildSample(nals: List<ByteArray>): ByteArray? {
        val baos = ByteArrayOutputStream()
        for (nal in nals) {
            if (nal.size < 2) continue
            val type = (nal[0].toInt() ushr 1) and 0x3F
            if (type == 32 || type == 33 || type == 34) continue
            baos.write(u32(nal.size.toLong()))
            baos.write(nal)
        }
        val data = baos.toByteArray()
        return if (data.isEmpty()) null else data
    }

    private fun isKeyframe(nals: List<ByteArray>): Boolean {
        for (nal in nals) {
            if (nal.size < 2) continue
            val type = (nal[0].toInt() ushr 1) and 0x3F
            if (type in 16..21) return true
        }
        return false
    }

    fun stop(): String? {
        if (closed) return null
        closed = true
        return try {
            val mdatEnd = out.filePointer
            // box mdat = 8 bytes de cabecalho (size + type) + dados
            out.seek(mdatDataStart - 8)
            out.write(u32(mdatEnd - mdatDataStart + 8))
            out.seek(mdatEnd)
            out.write(buildMoov())
            out.close()
            null
        } catch (t: Throwable) {
            runCatching { out.close() }
            "fechar mp4: ${t.message}"
        }
    }

    val fileBytes: Long get() = file.length()

    // ---------------- montagem dos boxes ----------------

    private fun buildMoov(): ByteArray {
        val duration = times.lastOrNull() ?: 0L

        val hvcc = ByteArrayOutputStream()
        hvcc.write(1)
        val ptl = ByteArray(12)
        System.arraycopy(sps, 3, ptl, 0, 12.coerceAtMost(sps.size - 3))
        hvcc.write(ptl)
        hvcc.write(u16(0xF000))
        hvcc.write(0xFC)
        hvcc.write(0xFC or 1)
        hvcc.write(0xF8)
        hvcc.write(0xF8)
        hvcc.write(u16(0))
        val subLayerByte = if (sps.size > 2) sps[2].toInt() and 0xFF else 0
        val numLayers = ((subLayerByte shr 3) and 0x07) + 1
        val nested = subLayerByte and 1
        hvcc.write((numLayers and 7) shl 3 or (nested shl 2) or 3)
        hvcc.write(3)
        for ((type, nal) in listOf(32 to vps, 33 to sps, 34 to pps)) {
            hvcc.write(0x80 or type)
            hvcc.write(u16(1))
            hvcc.write(u16(nal.size))
            hvcc.write(nal)
        }

        val visual = ByteArrayOutputStream()
        visual.write(ByteArray(6))
        visual.write(u16(1))
        visual.write(ByteArray(16))
        visual.write(u16(width))
        visual.write(u16(height))
        visual.write(u32(0x00480000))
        visual.write(u32(0x00480000))
        visual.write(u32(0))
        visual.write(u16(1))
        visual.write(ByteArray(32))
        visual.write(u16(0x0018))
        visual.write(u16(0xFFFF))
        visual.write(box("hvcC", hvcc.toByteArray()))

        val stsd = fullBox("stsd", 0, 0, u32(1) + box("hvc1", visual.toByteArray()))

        // stts com run-length
        val entries = ArrayList<Pair<Int, Long>>()
        for (i in times.indices) {
            val d = if (i + 1 < times.size) times[i + 1] - times[i] else (times.getOrNull(i) ?: 3000L) - (times.getOrNull(i - 1) ?: 0L)
            val delta = if (d <= 0) 3000L else d
            if (entries.isNotEmpty() && entries.last().second == delta) {
                entries[entries.size - 1] = entries.last().first + 1 to delta
            } else {
                entries.add(1 to delta)
            }
        }
        val sttsBaos = ByteArrayOutputStream()
        sttsBaos.write(u32(entries.size.toLong()))
        for ((count, delta) in entries) {
            sttsBaos.write(u32(count.toLong())); sttsBaos.write(u32(delta))
        }
        val stts = fullBox("stts", 0, 0, sttsBaos.toByteArray())

        val stsc = fullBox("stsc", 0, 0, u32(1) + u32(1) + u32(sizes.size.toLong()) + u32(1))

        val stszBaos = ByteArrayOutputStream()
        stszBaos.write(u32(0)); stszBaos.write(u32(sizes.size.toLong()))
        for (s in sizes) stszBaos.write(u32(s.toLong()))
        val stsz = fullBox("stsz", 0, 0, stszBaos.toByteArray())

        val stco = fullBox("stco", 0, 0, u32(1) + u32(mdatDataStart))

        val stssBaos = ByteArrayOutputStream()
        stssBaos.write(u32(syncs.size.toLong()))
        for (s in syncs) stssBaos.write(u32(s.toLong()))
        val stss = fullBox("stss", 0, 0, stssBaos.toByteArray())

        val stbl = box("stbl", stsd + stts + stsc + stsz + stco + stss)
        val dinf = box("dinf", fullBox("dref", 0, 0, u32(1) + fullBox("url ", 0, 1, ByteArray(0))))
        val vmhd = fullBox("vmhd", 0, 1, u16(0) + u16(0) + u16(0) + u16(0))
        val minf = box("minf", vmhd + dinf + stbl)

        val mdhd = fullBox("mdhd", 0, 0, u32(0) + u32(0) + u32(TS) + u32(duration) + u16(0x55C4) + u16(0))
        val hdlr = fullBox("hdlr", 0, 0, u32(0) + "vide".toByteArray() + ByteArray(12) + "VideoHandler\u0000".toByteArray())
        val mdia = box("mdia", mdhd + hdlr + minf)

        val durMs = duration * 1000 / TS
        val matrix = matrix()
        val tkhd = fullBox(
            "tkhd", 0, 7,
            u32(0) + u32(0) + u32(1) + u32(0) + u32(durMs) + ByteArray(8) +
                u16(0) + u16(0) + u16(0) + u16(0) + matrix +
                u32((width.toLong() shl 16)) + u32((height.toLong() shl 16))
        )
        val trak = box("trak", tkhd + mdia)

        val mvhd = fullBox(
            "mvhd", 0, 0,
            u32(0) + u32(0) + u32(1000) + u32(durMs) + u32(0x10000) + u16(0x100) +
                ByteArray(10) + matrix + ByteArray(24) + u32(2)
        )
        return box("moov", mvhd + trak)
    }

    private fun matrix(): ByteArray {
        val baos = ByteArrayOutputStream()
        for (v in intArrayOf(0x10000, 0, 0, 0, 0x10000, 0, 0, 0, 0x40000000)) baos.write(u32(v.toLong()))
        return baos.toByteArray()
    }

    private fun box(type: String, payload: ByteArray): ByteArray =
        u32((8 + payload.size).toLong()) + type.toByteArray() + payload

    private fun fullBox(type: String, version: Int, flags: Int, payload: ByteArray): ByteArray =
        box(type, byteArrayOf(version.toByte()) + byteArrayOf(((flags shr 16) and 0xFF).toByte(), ((flags shr 8) and 0xFF).toByte(), (flags and 0xFF).toByte()) + payload)

    private fun u32(v: Long): ByteArray = byteArrayOf(
        ((v shr 24) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte()
    )

    private fun u16(v: Int): ByteArray = byteArrayOf(((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte())

    private companion object {
        const val TS = 90_000L
    }
}
