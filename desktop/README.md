# YooFree — desktop (Linux e Windows)

Cliente **aberto e sem anúncios** para câmeras **Yoosee / GW (Gwell)** no
Linux e no Windows 10/11. Fala direto com a câmera na rede local: **sem nuvem,
sem conta, sem IA**.

Feito em **Python + PySide6**. O vídeo é decodificado por:

- **Linux:** GStreamer (`avdec_h265`) — se não tiver, cai pro PyAV
- **Windows:** PyAV (FFmpeg empacotado no wheel)

---

## Linux

```bash
curl -fsSL https://raw.githubusercontent.com/Wennington123/YooFree/main/desktop/packaging/install.sh | bash
```

Ou baixando o pacote:

```bash
tar xzf yoofree-desktop.tar.gz
cd desktop
./packaging/install.sh --deps      # --deps instala o que faltar
```

O instalador detecta o gerenciador de pacotes (pacman, apt, dnf, zypper, apk,
xbps, emerge), instala em `~/.local` (sem root para o app), e cria o comando
**`yoofree`** + atalho no menu.

```bash
yoofree                       # abre a janela
yoofree --host 192.168.1.2    # já conecta
```

Desinstalar: `bash packaging/uninstall.sh`

## Windows 10 / 11

Baixe o **`YooFree-windows-x64.zip`** no [release](../../releases), descompacte
e rode **`YooFree.exe`**. É portátil (não instala nada, não mexe no registro).

> O SmartScreen pode avisar na primeira execução (o exe não tem assinatura
> paga): **Mais informações → Executar assim mesmo**.

---

## Dependências (rodando do código)

| Pacote | Para quê |
|---|---|
| Python 3.9+ | runtime |
| **PySide6** | interface |
| **av** (PyAV) | decodificar HEVC no Windows |
| **GStreamer** + `gst-plugins-bad`/`gst-libav` | opcional, no Linux (mais leve) |

```bash
pip install -r requirements.txt
python3 main.py --host 192.168.1.2
```

Se quiser forçar um backend de vídeo:

```bash
YOOFREE_PLAYER=av  python3 main.py    # PyAV / FFmpeg
YOOFREE_PLAYER=gst python3 main.py    # GStreamer
```

---

## Uso

1. Aparelho na **mesma rede Wi‑Fi** da câmera.
2. Digite o **IP da câmera** e clique em **Conectar**.
3. **PTZ**: `^ v < >`. **Zoom**: `−` / `+` (1x–5x). **Girar**: rotaciona 90°.
4. **Gravar** salva MP4 em `~/Videos/YooFree/` (Linux) ou
   `%USERPROFILE%\Videos\YooFree\` (Windows).

---

## Por que cliente RTSP próprio?

As câmeras Yoosee **não falam RTSP padrão**: o `DESCRIBE` responde `400` quando
o cliente manda autenticação, então VLC/ffmpeg/GStreamer não conseguem ler o
SDP. O YooFree faz o mínimo que a câmera aceita (`SETUP` + `PLAY`, e
`SET_PARAMETER` para PTZ) e entrega o H.265 cru pro decodificador.

O muxer MP4 também é próprio (`yoofree/muxer.py`) — mesma lógica usada no Android,
porque o `MediaMuxer` de lá quebra com HEVC.

---

## Estrutura

```
yoofree/
  rtsp.py        cliente RTSP mínimo (SETUP/PLAY + PTZ)
  hevc.py        depacketizer RTP -> HEVC
  muxer.py       muxer MP4 próprio
  player.py      escolhe o backend
  player_gst.py  backend GStreamer (Linux)
  player_av.py   backend PyAV/FFmpeg (Windows)
  ui.py          janela PySide6
packaging/       install/uninstall, build do tar.gz e workflow do Windows
```

## Licença

GPL-3.0-or-later.
