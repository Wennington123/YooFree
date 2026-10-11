"""Escolhe o backend de video disponivel.

- Linux com GStreamer: usa GStreamer (mais leve, aproveita plugins do sistema)
- Windows / sem GStreamer: usa PyAV (FFmpeg empacotado, pip)

Forca um backend com a variavel de ambiente YOOFREE_PLAYER=av|gst.
"""

import os


def create_player():
    choice = os.environ.get("YOOFREE_PLAYER", "").lower()

    if choice == "av":
        from .player_av import VideoPlayer
        return VideoPlayer()

    if choice != "gst":
        try:
            from .player_gst import VideoPlayer
            return VideoPlayer()
        except Exception:  # noqa: BLE001  (sem gi/GStreamer -> cai pro PyAV)
            pass

    from .player_av import VideoPlayer
    return VideoPlayer()
