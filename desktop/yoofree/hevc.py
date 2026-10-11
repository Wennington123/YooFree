"""Depacketizer HEVC/H.265 sobre RTP (RFC 7798).

A camera manda H.265 em unidades RTP com payload type dinamico:
  - tipo <= 47: NAL unit unico
  - tipo 48  : AP (aggregation packet)
  - tipo 49  : FU (fragmentation unit)
  - tipo 50  : PACI (ignorado)

O bit marker do RTP fecha um access unit.
"""


class AccessUnit:
    __slots__ = ("nals", "rtp_ts")

    def __init__(self, nals, rtp_ts):
        self.nals = nals
        self.rtp_ts = rtp_ts


class HevcDepacketizer:
    def __init__(self):
        self._au = []
        self._fubuf = bytearray()

    def on_rtp_payload(self, payload, marker, ts):
        """Devolve um AccessUnit quando o AU fecha (bit marker), senao None."""
        n = len(payload)
        if n < 2:
            return None
        t = (payload[0] >> 1) & 0x3F

        if t <= 47:
            self._au.append(bytes(payload))
        elif t == 48:  # AP: [size16][NAL]...
            i = 2
            while i + 2 <= n:
                l = (payload[i] << 8) | payload[i + 1]
                i += 2
                if i + l > n:
                    break
                self._au.append(bytes(payload[i:i + l]))
                i += l
        elif t == 49:  # FU: header[0..1], FU header[2], dados de [3]
            fu = payload[2]
            start = fu & 0x80
            end = fu & 0x40
            fu_type = fu & 0x3F
            if start:
                self._fubuf = bytearray([(payload[0] & 0x81) | (fu_type << 1), payload[1]])
            if n > 3:
                self._fubuf += payload[3:]
            if end:
                self._au.append(bytes(self._fubuf))
                self._fubuf = bytearray()

        if marker:
            if not self._au:
                return None
            au = AccessUnit(self._au, ts)
            self._au = []
            return au
        return None

    @staticmethod
    def annexb(nals):
        out = bytearray()
        for n in nals:
            out += b"\x00\x00\x00\x01"
            out += n
        return bytes(out)

    @staticmethod
    def nal_type(nal):
        return (nal[0] >> 1) & 0x3F if len(nal) >= 2 else -1

    @staticmethod
    def is_keyframe(nals):
        for n in nals:
            if len(n) >= 2 and 16 <= ((n[0] >> 1) & 0x3F) <= 21:
                return True
        return False
