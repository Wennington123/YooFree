# YooFree — Linux

Cliente **aberto e sem anúncios** para câmeras **Yoosee / GW (Gwell)** no Linux.
Fala direto com a câmera na rede local: **sem nuvem, sem conta, sem IA**.

Feito em **Python + PySide6**, com o vídeo decodificado pelo **GStreamer**.

## Instalar (qualquer distro)

```bash
curl -fsSL https://raw.githubusercontent.com/Wennington123/YooFree/main/desktop/packaging/install.sh | bash
```

Ou baixando o pacote:

```bash
tar xzf yoofree-desktop.tar.gz
cd desktop
./packaging/install.sh --deps      # --deps instala o que faltar
```

O instalador:
- confere as dependências e **detecta o gerenciador de pacotes** (pacman, apt, dnf, zypper, apk, xbps, emerge)
- instala em `~/.local` (sem root para o app)
- cria o comando **`yoofree`** e um atalho no menu

Depois:

```bash
yoofree                       # abre a janela
yoofree --host 192.168.1.2    # já conecta
```

## Dependências

| Pacote | Para quê |
|---|---|
| `python3` (3.9+) | runtime |
| **PySide6** | interface |
| **PyGObject** (`python3-gi`) | bindings do GStreamer |
| **GStreamer** + `gst-plugins-bad` / `gst-libav` | decodificar HEVC (`avdec_h265`) |

## Uso

1. Celular/PC na **mesma rede Wi‑Fi** da câmera.
2. Digite o **IP da câmera** e clique em **Conectar**.
3. **PTZ**: `^ v < >`.  **Zoom**: `−` / `+` (1x–5x). **Girar**: rotaciona 90°.
4. **Gravar** salva MP4 em `~/Videos/YooFree/`.

## Rodar do código (sem instalar)

```bash
python3 main.py --host 192.168.1.2
```

## Por que cliente RTSP próprio?

As câmeras Yoosee **não falam RTSP padrão**: o `DESCRIBE` responde `400` quando
o cliente manda autenticação, então VLC/ffmpeg/GStreamer não conseguem ler o SDP.
O YooFree faz o mínimo que a câmera aceita (`SETUP` + `PLAY`, e `SET_PARAMETER`
para PTZ) e alimenta o GStreamer com o H.265 cru (via `appsrc`).

O muxer MP4 também é próprio (`yoofree/muxer.py`) — mesma lógica usada no Android.

## Estrutura

```
yoofree/
  rtsp.py    cliente RTSP minimo (SETUP/PLAY + PTZ)
  hevc.py    depacketizer RTP -> HEVC
  muxer.py   muxer MP4 proprio
  player.py  pipeline GStreamer (appsrc -> RGB -> Qt)
  ui.py      janela PySide6
packaging/   install/uninstall e build do tar.gz
```

## Licença

GPL-3.0-or-later.
