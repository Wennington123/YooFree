"""Player de video via PyAV (FFmpeg empacotado). Usado no Windows.

Decodifica os access units HEVC direto (sem container), entao tambem nao
precisa de DESCRIBE nem de GStreamer.
"""

from PySide6.QtCore import QObject, Signal
from PySide6.QtGui import QImage


class VideoPlayer(QObject):
    frameReady = Signal(QImage)
    error = Signal(str)

    def __init__(self, parent=None):
        super().__init__(parent)
        self._codec = None
        self._av = None

    def start(self):
        if self._codec is not None:
            return
        try:
            import av  # import tardio: so carrega quando vai usar
        except ImportError as e:  # pragma: no cover
            self.error.emit(f"PyAV nao instalado: {e}")
            return
        self._av = av
        self._codec = av.CodecContext.create("hevc", "r")

    def stop(self):
        self._codec = None

    def push(self, annexb, pts_us):
        if self._codec is None or not annexb:
            return
        try:
            pkt = self._av.Packet(annexb)
            pkt.pts = pts_us
            pkt.dts = pts_us
            for frame in self._codec.decode(pkt):
                arr = frame.to_ndarray(format="rgb24")
                h, w = arr.shape[0], arr.shape[1]
                img = QImage(arr.data, w, h, w * 3, QImage.Format_RGB888).copy()
                self.frameReady.emit(img)
        except Exception as e:  # noqa: BLE001
            self.error.emit(f"decode: {e}")
