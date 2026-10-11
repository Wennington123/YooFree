"""Muxer MP4 proprio para HEVC (uma faixa de video, moov no fim).

Port do prototipo validado com ffprobe. Escreve os boxes na mao, entao nao
depende de nenhuma lib de muxing.
"""

import struct

START = b"\x00\x00\x00\x01"
TS = 90_000  # timescale do video (mesmo clock do RTP)


def _box(t, payload):
    return struct.pack(">I", 8 + len(payload)) + t + payload


def _full(t, version, flags, payload):
    return _box(t, bytes([version]) + struct.pack(">I", flags)[1:] + payload)


def _u32(v):
    return struct.pack(">I", v & 0xFFFFFFFF)


def _u16(v):
    return struct.pack(">H", v & 0xFFFF)


def _matrix():
    return b"".join(_u32(v) for v in (0x10000, 0, 0, 0, 0x10000, 0, 0, 0, 0x40000000))


class Mp4Muxer:
    """Grava MP4 sem re-encode: os NALs da camera vao direto pro container."""

    def __init__(self, path, vps, sps, pps, width=1920, height=1080):
        self.path = str(path)
        self.vps, self.sps, self.pps = vps, sps, pps
        self.width, self.height = width, height

        self._sizes = []
        self._times = []
        self._syncs = []
        self._first_ts = None
        self._last_ts = -1
        self._closed = False

        self._fh = open(self.path, "w+b")
        self._fh.write(_box(b"ftyp", b"isom" + _u32(0x200) + b"isomiso2hvc1mp41"))
        self._fh.write(_u32(0) + b"mdat")  # tamanho corrigido no fim
        self._mdat_start = self._fh.tell()

    @property
    def sample_count(self):
        return len(self._sizes)

    def add_access_unit(self, nals, pts_us):
        if self._closed:
            return
        chunk = self._sample(nals)
        if not chunk:
            return
        self._sizes.append(len(chunk))
        if self._first_ts is None:
            self._first_ts = pts_us
        ts = (pts_us - self._first_ts) * TS // 1_000_000
        if ts <= self._last_ts:
            ts = self._last_ts + 1
        self._last_ts = ts
        self._times.append(ts)
        if self._is_keyframe(nals):
            self._syncs.append(len(self._sizes))
        self._fh.write(chunk)

    def close(self):
        if self._closed:
            return
        self._closed = True
        if not self._sizes:
            self._fh.close()
            return
        mdat_end = self._fh.tell()
        self._fh.seek(self._mdat_start - 8)
        self._fh.write(_u32(mdat_end - self._mdat_start + 8))
        self._fh.seek(mdat_end)
        self._fh.write(self._moov())
        self._fh.close()

    # ------------------------------------------------------------- amostras

    @staticmethod
    def _sample(nals):
        out = bytearray()
        for n in nals:
            if len(n) < 2:
                continue
            t = (n[0] >> 1) & 0x3F
            if t in (32, 33, 34):  # parametros vao no hvcC
                continue
            out += _u32(len(n)) + n
        return bytes(out)

    @staticmethod
    def _is_keyframe(nals):
        return any(len(n) >= 2 and 16 <= ((n[0] >> 1) & 0x3F) <= 21 for n in nals)

    # ---------------------------------------------------------------- boxes

    def _moov(self):
        duration = self._times[-1] if self._times else 0

        # hvcC
        hvcc = bytearray([1])
        hvcc += self.sps[3:15]
        hvcc += _u16(0xF000)
        hvcc += bytes([0xFC, 0xFC | 1, 0xF8, 0xF8])
        hvcc += _u16(0)
        sub = self.sps[2] if len(self.sps) > 2 else 0
        layers = ((sub >> 3) & 0x07) + 1
        nested = sub & 1
        hvcc += bytes([((0 & 3) << 6) | ((layers & 7) << 3) | ((nested & 1) << 2) | 3])
        hvcc += bytes([3])
        for t, nal in ((32, self.vps), (33, self.sps), (34, self.pps)):
            hvcc += bytes([0x80 | t]) + _u16(1) + _u16(len(nal)) + nal

        visual = (
            b"\x00" * 6 + _u16(1) + b"\x00" * 16
            + _u16(self.width) + _u16(self.height)
            + _u32(0x00480000) + _u32(0x00480000) + _u32(0)
            + _u16(1) + b"\x00" * 32 + _u16(0x0018) + _u16(0xFFFF)
            + _box(b"hvcC", bytes(hvcc))
        )
        stsd = _full(b"stsd", 0, 0, _u32(1) + _box(b"hvc1", visual))

        entries = []
        for i, t in enumerate(self._times):
            d = (self._times[i + 1] - t) if i + 1 < len(self._times) else (t - self._times[i - 1] if i else 3000)
            d = d if d > 0 else 3000
            if entries and entries[-1][1] == d:
                entries[-1][0] += 1
            else:
                entries.append([1, d])
        stts = _full(b"stts", 0, 0, _u32(len(entries)) + b"".join(_u32(c) + _u32(d) for c, d in entries))
        stsc = _full(b"stsc", 0, 0, _u32(1) + _u32(1) + _u32(len(self._sizes)) + _u32(1))
        stsz = _full(b"stsz", 0, 0, _u32(0) + _u32(len(self._sizes)) + b"".join(_u32(s) for s in self._sizes))
        stco = _full(b"stco", 0, 0, _u32(1) + _u32(self._mdat_start))
        stss = _full(b"stss", 0, 0, _u32(len(self._syncs)) + b"".join(_u32(s) for s in self._syncs))

        stbl = _box(b"stbl", stsd + stts + stsc + stsz + stco + stss)
        dinf = _box(b"dinf", _full(b"dref", 0, 0, _u32(1) + _full(b"url ", 0, 1, b"")))
        vmhd = _full(b"vmhd", 0, 1, _u16(0) * 4)
        minf = _box(b"minf", vmhd + dinf + stbl)

        mdhd = _full(b"mdhd", 0, 0, _u32(0) + _u32(0) + _u32(TS) + _u32(duration) + _u16(0x55C4) + _u16(0))
        hdlr = _full(b"hdlr", 0, 0, _u32(0) + b"vide" + b"\x00" * 12 + b"VideoHandler\x00")
        mdia = _box(b"mdia", mdhd + hdlr + minf)

        dur_ms = duration * 1000 // TS
        m = _matrix()
        tkhd = _full(
            b"tkhd", 0, 7,
            _u32(0) + _u32(0) + _u32(1) + _u32(0) + _u32(dur_ms) + b"\x00" * 8
            + _u16(0) * 4 + m + _u32(self.width << 16) + _u32(self.height << 16),
        )
        trak = _box(b"trak", tkhd + mdia)

        mvhd = _full(
            b"mvhd", 0, 0,
            _u32(0) + _u32(0) + _u32(1000) + _u32(dur_ms) + _u32(0x10000) + _u16(0x100)
            + b"\x00" * 10 + m + b"\x00" * 24 + _u32(2),
        )
        return _box(b"moov", mvhd + trak)
