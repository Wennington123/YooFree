"""Interface do YooFree desktop (PySide6)."""

import datetime as dt
from pathlib import Path

from PySide6.QtCore import QObject, QPointF, Qt, Signal
from PySide6.QtGui import QImage, QPainter
from PySide6.QtWidgets import (
    QApplication,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QPushButton,
    QVBoxLayout,
    QWidget,
)

from .hevc import HevcDepacketizer
from .muxer import Mp4Muxer
from .player import create_player
from .rtsp import RtspClient

MIN_ZOOM, MAX_ZOOM, ZOOM_STEP = 1.0, 5.0, 0.5


def default_output_dir() -> Path:
    """Pasta de gravacao (~/Videos/YooFree), funcionando tambem no Windows."""
    videos = Path.home() / "Videos"
    base = videos if videos.is_dir() else Path.home()
    return base / "YooFree"


class VideoWidget(QWidget):
    """Mostra o frame com fit + zoom + rotacao."""

    def __init__(self):
        super().__init__()
        self._image = None
        self.zoom = 1.0
        self.rotation = 0
        self.setMinimumSize(480, 270)
        self.setStyleSheet("background:#000;")

    def set_image(self, img):
        self._image = img
        self.update()

    def clear(self):
        self._image = None
        self.update()

    def paintEvent(self, _event):  # noqa: N802
        painter = QPainter(self)
        painter.fillRect(self.rect(), Qt.black)
        if self._image is None:
            return
        iw, ih = self._image.width(), self._image.height()
        rot = self.rotation % 360
        rw, rh = (ih, iw) if rot in (90, 270) else (iw, ih)
        w, h = max(self.width(), 1), max(self.height(), 1)
        fit = min(w / rw, h / rh) * self.zoom
        painter.setRenderHint(QPainter.SmoothPixmapTransform, True)
        painter.translate(w / 2, h / 2)
        painter.rotate(rot)
        painter.scale(fit, fit)
        painter.drawImage(QPointF(-iw / 2, -ih / 2), self._image)


class Controller(QObject):
    """Ponte entre as threads do RTSP e a UI (sinais enfileirados)."""

    stateChanged = Signal(str)
    auReceived = Signal(list, int)

    def __init__(self):
        super().__init__()
        self.player = create_player()
        self.client = None
        self.muxer = None
        self.vps = self.sps = self.pps = None
        self.recording = False

    def start(self, host):
        self.vps = self.sps = self.pps = None
        self.player.start()
        self.client = RtspClient(
            host,
            on_access_unit=lambda nals, pts: self.auReceived.emit(nals, pts),
            on_state=lambda msg: self.stateChanged.emit(msg),
        )
        self.client.start()

    def stop(self):
        self.stop_recording()
        if self.client:
            self.client.stop()
        self.player.stop()
        self.client = None

    def stop_recording(self):
        if self.muxer:
            self.muxer.close()
            self.muxer = None
        self.recording = False

    def ptz(self, cmd):
        if self.client:
            self.client.send_ptz(cmd)

    def capture_csd(self, nals):
        if self.vps and self.sps and self.pps:
            return
        for n in nals:
            if len(n) < 2:
                continue
            t = HevcDepacketizer.nal_type(n)
            if t == 32 and not self.vps:
                self.vps = n
            elif t == 33 and not self.sps:
                self.sps = n
            elif t == 34 and not self.pps:
                self.pps = n

    def toggle_recording(self, out_dir: Path):
        if self.recording:
            self.muxer.close()
            name = Path(self.muxer.path).name
            count = self.muxer.sample_count
            self.muxer = None
            self.recording = False
            return f"Gravacao salva: {name} ({count} quadros)"
        if not (self.vps and self.sps and self.pps):
            return "Aguarde o video iniciar para gravar"
        out_dir.mkdir(parents=True, exist_ok=True)
        name = "YooFree_" + dt.datetime.now().strftime("%Y%m%d_%H%M%S") + ".mp4"
        self.muxer = Mp4Muxer(out_dir / name, self.vps, self.sps, self.pps)
        self.recording = True
        return f"Gravando: {name}"


class MainWindow(QWidget):
    def __init__(self, host=None):
        super().__init__()
        self.setWindowTitle("YooFree")
        self.resize(960, 720)
        self.setStyleSheet(
            "QWidget{background:#0E1116;color:#E7EAF0;font-size:13px;}"
            "QLineEdit{background:#161C24;border:1px solid #2A3543;border-radius:6px;padding:6px;}"
            "QPushButton{background:#7C6CF0;color:#0B0F14;border:0;border-radius:6px;padding:8px 12px;font-weight:600;}"
            "QPushButton:disabled{background:#2A3543;color:#6B7686;}"
            "QPushButton#ptz{background:#232C38;color:#E7EAF0;}"
            "QLabel#status{color:#9BB0C4;}"
        )
        self.controller = Controller()
        self.controller.stateChanged.connect(self._on_state)
        self.controller.auReceived.connect(self._on_au)
        self.controller.player.frameReady.connect(self._on_frame)
        self.controller.player.error.connect(self._on_state)
        self.out_dir = default_output_dir()

        self.video = VideoWidget()
        self.status = QLabel("Parado")
        self.status.setObjectName("status")
        self.ip = QLineEdit("")
        self.ip.setPlaceholderText("IP da camera (ex.: 192.168.1.2)")

        self.btn_conn = QPushButton("Conectar")
        self.btn_conn.clicked.connect(self._toggle_conn)
        self.btn_rec = QPushButton("Gravar")
        self.btn_rec.setEnabled(False)
        self.btn_rec.clicked.connect(self._toggle_rec)
        self.btn_rotate = QPushButton("Girar")
        self.btn_rotate.clicked.connect(self._rotate)
        self.btn_zoom_out = QPushButton("−")
        self.btn_zoom_in = QPushButton("+")
        self.btn_zoom_out.clicked.connect(lambda: self._zoom(-ZOOM_STEP))
        self.btn_zoom_in.clicked.connect(lambda: self._zoom(ZOOM_STEP))
        self.zoom_label = QLabel("1.0x")

        top = QHBoxLayout()
        top.addWidget(QLabel("IP:"))
        top.addWidget(self.ip, 1)
        top.addWidget(self.btn_conn)
        top.addWidget(self.btn_rec)
        top.addWidget(self.btn_rotate)
        top.addWidget(self.btn_zoom_out)
        top.addWidget(self.zoom_label)
        top.addWidget(self.btn_zoom_in)

        ptz = QHBoxLayout()
        self.ptz_buttons = {}
        for label, cmd in (("^", "UP"), ("v", "DWON"), ("<", "LEFT"), (">", "RIGHT")):
            b = QPushButton(label)
            b.setObjectName("ptz")
            b.setFixedWidth(48)
            b.setEnabled(False)
            b.clicked.connect(lambda _=False, c=cmd: self.controller.ptz(c))
            self.ptz_buttons[cmd] = b
            ptz.addWidget(b)
        ptz.addStretch(1)

        layout = QVBoxLayout(self)
        layout.addLayout(top)
        layout.addWidget(self.video, 1)
        layout.addLayout(ptz)
        layout.addWidget(self.status)

        if host:
            self.ip.setText(host)
            self._toggle_conn()

    # ------------------------------------------------------------------ acoes

    def _toggle_conn(self):
        if self.controller.client is not None:
            self.controller.stop()
            self.video.clear()
            self._set_connected(False)
            self.status.setText("Desconectado")
            return
        host = self.ip.text().strip()
        if not host:
            self.status.setText("Informe o IP da camera")
            return
        self.controller.start(host)
        self._set_connected(True)

    def _toggle_rec(self):
        self.status.setText(self.controller.toggle_recording(self.out_dir))

    def _rotate(self):
        self.video.rotation = (self.video.rotation + 90) % 360
        self.video.update()

    def _zoom(self, delta):
        self.video.zoom = min(MAX_ZOOM, max(MIN_ZOOM, self.video.zoom + delta))
        self.zoom_label.setText(f"{self.video.zoom:.1f}x")
        self.video.update()

    # ---------------------------------------------------------------- callbacks

    def _on_state(self, msg):
        self.status.setText(msg)

    def _on_frame(self, img):
        self.video.set_image(img)

    def _on_au(self, nals, pts):
        self.controller.capture_csd(nals)
        self.controller.player.push(HevcDepacketizer.annexb(nals), pts)
        if self.controller.recording and self.controller.muxer:
            self.controller.muxer.add_access_unit(nals, pts)

    def _set_connected(self, connected):
        self.btn_conn.setText("Desconectar" if connected else "Conectar")
        self.btn_rec.setEnabled(connected)
        self.ip.setEnabled(not connected)
        for b in self.ptz_buttons.values():
            b.setEnabled(connected)

    def closeEvent(self, event):  # noqa: N802
        self.controller.stop()
        super().closeEvent(event)


def run(host=None):
    app = QApplication([])
    win = MainWindow(host)
    win.show()
    app.exec()
