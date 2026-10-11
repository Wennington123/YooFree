"""Cliente RTSP minimo para cameras Yoosee/GW.

Essas cameras NAO falam RTSP padrao: o DESCRIBE responde 400 quando o cliente
manda Authorization, entao players prontos (VLC/ffmpeg/GStreamer) nao leem o
SDP. Mas SETUP + PLAY funcionam sem autenticacao. Aqui fazemos so o necessário.
"""

import re
import socket
import threading
import time

from .hevc import HevcDepacketizer

UA = "LibVLC/2.2.6 (LIVE555 Streaming Media v2016.02.22)"
RTP_CLOCK = 90_000


class RtspClient:
    """Conecta, recebe video HEVC e manda PTZ, tudo em sockets crus."""

    def __init__(self, host, port=554, path="onvif1", on_access_unit=None, on_state=None):
        self.host = host
        self.port = port
        self.path = path
        self.on_access_unit = on_access_unit or (lambda nals, pts_us: None)
        self.on_state = on_state or (lambda msg: None)

        self._sock = None
        self._input = None
        self._output = None
        self._session = ""
        self._cseq = 0
        self._write_lock = threading.Lock()
        self._running = False
        self._thread = None
        self._first_ts = None
        self._depack = HevcDepacketizer()

    @property
    def url(self):
        return f"rtsp://{self.host}/{self.path}"

    def start(self):
        if self._running:
            return
        self._running = True
        self._thread = threading.Thread(target=self._run, name="rtsp", daemon=True)
        self._thread.start()

    def stop(self):
        self._running = False
        try:
            if self._sock:
                self._sock.close()
        except OSError:
            pass

    # ---------------------------------------------------------------- interno

    def _run(self):
        try:
            self.on_state(f"Conectando a {self.host}…")
            s = socket.socket()
            s.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            s.settimeout(5)
            s.connect((self.host, self.port))
            self._sock = s
            self._input = s.makefile("rb", buffering=0)
            self._output = s.makefile("wb", buffering=0)

            resp = self._request(
                f"SETUP rtsp://{self.host}/{self.path}/track1 RTSP/1.0",
                [f"User-Agent: {UA}", "Transport: RTP/AVP/TCP;unicast;interleaved=0-1"],
            )
            if " 200 " not in resp:
                raise RuntimeError("SETUP falhou: " + resp.split("\r\n")[0])
            m = re.search(r"Session:\s*([0-9a-zA-Z]+)", resp)
            self._session = m.group(1) if m else ""

            # a camera pode nao devolver resposta textual ao PLAY e ja mandar frame
            self._send(f"PLAY {self.url} RTSP/1.0", [f"User-Agent: {UA}"])
            self.on_state("Conectado — video HEVC")
            self._stream_loop()
        except Exception as e:  # noqa: BLE001
            if self._running:
                self.on_state(f"Erro: {e}")
        finally:
            self._running = False
            try:
                if self._sock:
                    self._sock.close()
            except OSError:
                pass

    def _send(self, line, headers):
        with self._write_lock:
            self._cseq += 1
            parts = [line, f"CSeq: {self._cseq}"]
            if self._session:
                parts.append(f"Session: {self._session}")
            parts.extend(headers)
            self._output.write(("\r\n".join(parts) + "\r\n\r\n").encode("ascii"))

    def _read_header(self):
        buf = bytearray()
        while len(buf) < 16384:
            b = self._input.read(1)
            if not b:
                break
            buf += b
            if buf.endswith(b"\r\n\r\n"):
                break
        return buf.decode("ascii", "replace")

    def _request(self, line, headers):
        self._send(line, headers)
        return self._read_header()

    def _stream_loop(self):
        s = self._sock
        s.settimeout(1.0)
        pending = b""
        last_alive = time.time()

        while self._running:
            try:
                chunk = s.recv(65536)
            except socket.timeout:
                if time.time() - last_alive > 20:
                    self._keep_alive()
                    last_alive = time.time()
                continue
            except OSError:
                break
            if not chunk:
                break

            data = pending + chunk
            off = 0
            n = len(data)
            while off + 4 <= n:
                if data[off] == 0x24:  # '$' interleaved
                    channel = data[off + 1]
                    length = (data[off + 2] << 8) | data[off + 3]
                    if off + 4 + length > n:
                        break
                    if channel == 0:
                        self._handle_rtp(data[off + 4:off + 4 + length])
                    off += 4 + length
                else:
                    # resposta RTSP textual (ex.: do PLAY) — pula o cabecalho
                    end = data.find(b"\r\n\r\n", off)
                    if end < 0:
                        break
                    off = end + 4
            pending = data[off:]

    def _handle_rtp(self, pkt):
        if len(pkt) < 12:
            return
        b0 = pkt[0]
        if (b0 >> 6) != 2:
            return
        csrc = b0 & 0x0F
        has_ext = (b0 & 0x10) != 0
        marker = (pkt[1] & 0x80) != 0
        ts = int.from_bytes(pkt[4:8], "big")

        header = 12 + 4 * csrc
        if has_ext:
            if len(pkt) < header + 4:
                return
            ext_words = int.from_bytes(pkt[header + 2:header + 4], "big")
            header += 4 + 4 * ext_words
        if header >= len(pkt):
            return

        au = self._depack.on_rtp_payload(pkt[header:], marker, ts)
        if au is None:
            return
        if self._first_ts is None:
            self._first_ts = au.rtp_ts
        delta = (au.rtp_ts - self._first_ts) & 0xFFFFFFFF
        pts_us = delta * 1_000_000 // RTP_CLOCK
        self.on_access_unit(au.nals, pts_us)

    def _keep_alive(self):
        try:
            self._send(f"GET_PARAMETER {self.url} RTSP/1.0", [])
        except OSError:
            pass

    def send_ptz(self, command):
        if not self._running:
            self.on_state("Sem conexao para PTZ")
            return
        threading.Thread(
            target=self._ptz,
            args=(command,),
            name="ptz",
            daemon=True,
        ).start()

    def _ptz(self, command):
        try:
            self._send(
                f"SET_PARAMETER {self.url} RTSP/1.0",
                [f"Content-type: ptzCmd: {command}"],
            )
        except OSError as e:
            self.on_state(f"PTZ {command}: {e}")
