"""Cliente RTSP minimo para cameras Yoosee/GW.

Essas cameras NAO falam RTSP padrao: o DESCRIBE responde 400 quando o cliente
manda Authorization, entao players prontos (VLC/ffmpeg/GStreamer) nao leem o
SDP. Mas SETUP + PLAY funcionam sem autenticacao. Aqui fazemos so o necessario.

Paridade com o cliente Android (app/.../rtsp/RtspClient.kt):
- keepalive (GET_PARAMETER) a cada 10s: a HIipCamera derruba a sessao em ~15-20s
  sem ping, mesmo com video chegando;
- Digest auth (RFC 2617) quando a camera responde 401 e ha senha configurada;
- reconexao automatica com backoff quando a conexao cai.
"""

import hashlib
import re
import secrets
import socket
import threading
import time

from .hevc import HevcDepacketizer

UA = "LibVLC/2.2.6 (LIVE555 Streaming Media v2016.02.22)"
RTP_CLOCK = 90_000
KEEP_ALIVE_SECS = 10
MAX_RECONNECTS = 5


def _md5(text):
    return hashlib.md5(text.encode("utf-8")).hexdigest()


def _parse_challenge(header):
    """Transforma 'Digest realm="x", nonce="y", qop="auth"' em dict."""
    return {
        m.group(1).lower(): (m.group(2) if m.group(2) is not None else m.group(3))
        for m in re.finditer(r'(\w+)=(?:"([^"]*)"|([^,\s]+))', header or "")
    }


class RtspClient:
    """Conecta, recebe video HEVC e manda PTZ, tudo em sockets crus."""

    def __init__(
        self,
        host,
        port=554,
        path="onvif1",
        on_access_unit=None,
        on_state=None,
        password="",
        user="admin",
    ):
        self.host = host
        self.port = port
        self.path = path
        self.on_access_unit = on_access_unit or (lambda nals, pts_us: None)
        self.on_state = on_state or (lambda msg: None)
        self.user = user or "admin"
        self.password = password or ""

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
        self._auth_challenge = None
        self._auth_nc = 0
        self._cnonce = secrets.token_hex(16)

    @property
    def base_url(self):
        if self.port == 554:
            return f"rtsp://{self.host}/{self.path}"
        return f"rtsp://{self.host}:{self.port}/{self.path}"

    # mantido por compatibilidade com o resto do codigo
    @property
    def url(self):
        return self.base_url

    def start(self):
        if self._running:
            return
        self._running = True
        self._thread = threading.Thread(target=self._run, name="rtsp", daemon=True)
        self._thread.start()

    def stop(self):
        self._running = False
        self._close_socket()

    # ---------------------------------------------------------------- interno

    def _close_socket(self):
        try:
            if self._sock:
                self._sock.close()
        except OSError:
            pass

    def _run(self):
        """Conecta e, se cair, tenta de novo com backoff."""
        attempt = 0
        while self._running:
            started = time.time()
            try:
                self.on_state(f"Conectando a {self.host}…")
                self._connect_and_stream()
                if not self._running:
                    break
                self.on_state("Conexão caiu")
            except Exception as e:  # noqa: BLE001
                if not self._running:
                    break
                self.on_state(f"Erro: {e}")
            finally:
                self._close_socket()

            # sessao longa = a conexao estava boa; zera o contador de tentativas
            if time.time() - started > 30:
                attempt = 0
            attempt += 1
            if not self._running:
                break
            if attempt > MAX_RECONNECTS:
                self.on_state("Desisti de reconectar (tente de novo)")
                break
            delay = min(2 * attempt, 10)
            self.on_state(f"Reconectando em {delay}s… ({attempt}/{MAX_RECONNECTS})")
            end = time.time() + delay
            while self._running and time.time() < end:
                time.sleep(0.2)
        self._running = False

    def _connect_and_stream(self):
        self.on_state(f"Conectando a {self.host}…")
        s = socket.socket()
        s.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        s.settimeout(5)
        s.connect((self.host, self.port))
        self._sock = s
        self._input = s.makefile("rb", buffering=0)
        self._output = s.makefile("wb", buffering=0)
        self._session = ""
        self._first_ts = None
        self._depack = HevcDepacketizer()

        resp = self._request(
            f"SETUP {self.base_url}/track1 RTSP/1.0",
            [f"User-Agent: {UA}", "Transport: RTP/AVP/TCP;unicast;interleaved=0-1"],
        )
        if " 200 " not in resp:
            raise RuntimeError("SETUP falhou: " + resp.split("\r\n")[0])
        m = re.search(r"Session:\s*([0-9a-zA-Z]+)", resp)
        self._session = m.group(1) if m else ""

        # a camera pode nao devolver resposta textual ao PLAY e ja mandar frame
        self._send(f"PLAY {self.base_url} RTSP/1.0", [f"User-Agent: {UA}"])
        self.on_state("Conectado — video HEVC")
        self._stream_loop()

    def _auth_header(self, method, uri):
        if not (self._auth_challenge and self.password):
            return None
        ch = self._auth_challenge
        realm = ch.get("realm", "")
        nonce = ch.get("nonce", "")
        qop = ch.get("qop", "")
        algo = ch.get("algorithm", "MD5").upper()
        if algo != "MD5":  # as cameras usam MD5; SHA-256 fica de fora
            return None
        ha1 = _md5(f"{self.user}:{realm}:{self.password}")
        ha2 = _md5(f"{method}:{uri}")
        self._auth_nc += 1
        nc = f"{self._auth_nc:08x}"
        if "auth" in qop:
            response = _md5(f"{ha1}:{nonce}:{nc}:{self._cnonce}:auth:{ha2}")
            return (
                f'Digest username="{self.user}", realm="{realm}", nonce="{nonce}", '
                f'uri="{uri}", response="{response}", qop=auth, nc={nc}, '
                f'cnonce="{self._cnonce}"'
            )
        response = _md5(f"{ha1}:{nonce}:{ha2}")
        return (
            f'Digest username="{self.user}", realm="{realm}", nonce="{nonce}", '
            f'uri="{uri}", response="{response}"'
        )

    def _send(self, line, headers):
        with self._write_lock:
            self._cseq += 1
            parts = [line, f"CSeq: {self._cseq}"]
            if self._session:
                parts.append(f"Session: {self._session}")
            parts.extend(headers)
            parts = [p for p in parts if p]
            try:
                method, uri = line.split(" ")[0], line.split(" ")[1]
                auth = self._auth_header(method, uri)
                if auth:
                    parts.append(f"Authorization: {auth}")
            except IndexError:
                pass
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
        resp = self._read_header()
        if " 401 " in resp and self.password and self._auth_challenge is None:
            m = re.search(r"WWW-Authenticate:\s*(.+)", resp)
            if m:
                self._auth_challenge = _parse_challenge(m.group(1))
                self.on_state("Senha aceita — autenticando (Digest)")
                self._send(line, headers)
                resp = self._read_header()
        return resp

    def _stream_loop(self):
        s = self._sock
        s.settimeout(1.0)
        pending = b""
        last_alive = time.time()

        while self._running:
            # o ping vai mesmo com video chegando: a camera expira a sessao
            if time.time() - last_alive >= KEEP_ALIVE_SECS:
                self._keep_alive()
                last_alive = time.time()
            try:
                chunk = s.recv(65536)
            except socket.timeout:
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
                    length = (data[off + 2] << 8) | data[off + 3]
                    if off + 4 + length > n:
                        break
                    if data[off + 1] == 0:
                        self._handle_rtp(data[off + 4:off + 4 + length])
                    off += 4 + length
                else:
                    # resposta RTSP textual (ex.: do PLAY/keepalive) — pula o cabecalho
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
            self._send(f"GET_PARAMETER {self.base_url} RTSP/1.0", [])
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
                f"SET_PARAMETER {self.base_url} RTSP/1.0",
                [f"Content-type: ptzCmd: {command}"],
            )
        except OSError as e:
            self.on_state(f"PTZ {command}: {e}")
