"""Player de video: GStreamer decodifica o H.265 e entrega frames RGB pro Qt.

Pipeline: appsrc -> h265parse -> avdec_h265 -> videoconvert -> RGB -> appsink
Nada de DESCRIBE: o stream cru vem do nosso RtspClient.
"""

import gi

gi.require_version("Gst", "1.0")
from gi.repository import Gst  # noqa: E402

from PySide6.QtCore import QObject, QTimer, Signal  # noqa: E402
from PySide6.QtGui import QImage  # noqa: E402

Gst.init(None)

PIPELINE = (
    "appsrc name=src is-live=true block=true format=time do-timestamp=false "
    "max-bytes=16777216 "
    "caps=video/x-h265,stream-format=byte-stream,alignment=au ! "
    "h265parse ! avdec_h265 ! videoconvert ! "
    "video/x-raw,format=RGB ! "
    "appsink name=sink sync=false max-buffers=3 drop=true emit-signals=true"
)


class VideoPlayer(QObject):
    """Recebe access units (Annex-B) e emite frames prontos pra tela."""

    frameReady = Signal(QImage)
    error = Signal(str)

    def __init__(self, parent=None):
        super().__init__(parent)
        self._pipeline = None
        self._src = None
        self._sink = None

    def start(self):
        if self._pipeline is not None:
            return
        self._pipeline = Gst.parse_launch(PIPELINE)
        self._src = self._pipeline.get_by_name("src")
        self._sink = self._pipeline.get_by_name("sink")
        self._sink.connect("new-sample", self._on_sample)
        self._pipeline.set_state(Gst.State.PLAYING)

        self._bus = self._pipeline.get_bus()
        self._timer = QTimer(self)
        self._timer.timeout.connect(self._poll_bus)
        self._timer.start(200)

    def stop(self):
        if self._pipeline is None:
            return
        self._timer.stop()
        self._pipeline.set_state(Gst.State.NULL)
        self._pipeline = None
        self._src = None
        self._sink = None

    def push(self, annexb, pts_us):
        if self._src is None or not annexb:
            return
        buf = Gst.Buffer.new_allocate(None, len(annexb), None)
        buf.fill(0, annexb)
        buf.pts = pts_us * 1000  # ns
        buf.duration = 40_000_000
        try:
            self._src.emit("push-buffer", buf)
        except Exception:  # noqa: BLE001
            pass

    def _on_sample(self, sink):
        sample = sink.emit("pull-sample")
        if sample is None:
            return Gst.FlowReturn.ERROR
        buf = sample.get_buffer()
        caps = sample.get_caps().get_structure(0)
        w = caps.get_value("width")
        h = caps.get_value("height")
        ok, mi = buf.map(Gst.MapFlags.READ)
        if ok:
            try:
                img = QImage(mi.data, w, h, w * 3, QImage.Format_RGB888).copy()
                self.frameReady.emit(img)
            finally:
                buf.unmap(mi)
        return Gst.FlowReturn.OK

    def _poll_bus(self):
        if self._bus is None:
            return
        while True:
            msg = self._bus.pop_filtered(
                Gst.MessageType.ERROR | Gst.MessageType.EOS
            )
            if msg is None:
                return
            if msg.type == Gst.MessageType.ERROR:
                err, dbg = msg.parse_error()
                self.error.emit(f"{err.message} ({dbg or ''})")
