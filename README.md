# YooFree

Cliente **Android aberto e sem anúncios** para câmeras **Yoosee / GW (Gwell)**.

Fala direto com a câmera na sua rede local: **sem nuvem, sem conta, sem IA, sem telemetria**.
Só você e a sua câmera.

<p align="center">
  <img src="assets/icon.svg" width="128" alt="YooFree">
</p>

---

## O que ele faz

| Recurso | Descrição |
|---|---|
| **Vídeo ao vivo** | HEVC/H.265 1080p direto da câmera, via RTSP |
| **PTZ** | Mover a câmera: cima, baixo, esquerda, direita |
| **Zoom** | Zoom digital de 1x a 5x na imagem |
| **Paisagem** | Vira o celular e o vídeo ocupa a tela toda |
| **Girar** | Rotaciona a imagem em 90°, se a câmera estiver de cabeça pra baixo |
| **Gravação local** | Salva MP4 no próprio celular, sem re-encode |

Tudo roda **100% na sua rede**. Nada sai da sua casa.

---

## Requisitos

- Uma câmera **Yoosee / GW (Gwell)** (o app oficial é o `yoosee.app`)
- Celular **Android 7.0+**, na **mesma rede Wi‑Fi** da câmera
- Saber o **IP local da câmera** (veja abaixo)

---

## Como descobrir o IP da câmera

1. Abra o app oficial da Yoosee e configure a câmera normalmente.
2. Veja a lista de dispositivos conectados no seu roteador — a câmera aparece com um IP tipo `192.168.1.2`.
3. Ou, no app oficial, veja em **Configurações da câmera → Informações da rede**.

> Dica: fixe o IP da câmera no roteador (DHCP reservado) pra ele não mudar depois.

---

## Instalação

1. Baixe o APK mais recente na aba **[Releases](../../releases)**.
2. No celular, permita "instalar de fontes desconhecidas" quando o Android pedir.
3. Instale e pronto.

---

## Como usar

1. **Conecte na rede**: o celular precisa estar no mesmo Wi‑Fi da câmera.
2. Digite o **IP da câmera** no campo **IP da câmera** (ex.: `192.168.1.2`).
3. Toque em **Conectar**. O vídeo aparece em alguns segundos.
4. **PTZ**: use as setas `^ v < >` para mover a câmera.
5. **Zoom**: os botões **−** e **+** (1x até 5x).
6. **Gravar**: toque em **Gravar** para começar e **Parar** para finalizar.
   O MP4 fica em:
   ```
   Android/data/br.yoofree.app/files/YooFree/
   ```
7. **Girar a tela**: vire o celular na horizontal — o vídeo ocupa a tela toda.
   Se a imagem ficar de cabeça pra baixo, use o botão **Girar**.

---

## Perguntas frequentes

**Preciso da senha da câmera?**
Não. O YooFree usa o RTSP local da câmera, que nessa linha não exige autenticação
para `SETUP`/`PLAY`. Só o IP basta.

**O app fala com a nuvem da Yoosee?**
Não. A conexão é direta (`IP:554`), na sua rede local.

**Funciona fora de casa?**
Não por enquanto — o acesso remoto está fora do escopo. Use uma VPN
(ex.: WireGuard/Tailscale) se quiser ver de fora, e mantenha o celular na VPN.

**Por que o app tem um cliente RTSP próprio?**
Porque essas câmeras **não falam RTSP padrão**: o `DESCRIBE` responde
`400 Bad Request` quando o cliente manda autenticação, então players prontos
(ExoPlayer, VLC, ffmpeg) não conseguem ler o SDP. O YooFree implementa o
mínimo que a câmera aceita (`SETUP` + `PLAY`, e `SET_PARAMETER` para PTZ) e faz
a depacketização HEVC na mão.

**Por que o MP4 não usa o MediaMuxer do Android?**
Porque em vários aparelhos (ex.: Samsung) o `MediaMuxer` **crasha nativamente**
com HEVC. O YooFree tem um **muxer MP4 próprio** (`Mp4Muxer.kt`), que escreve os
boxes na mão — estável em qualquer aparelho.

**Posso usar em mais de uma câmera?**
Hoje o app conecta em uma por vez (essas câmeras costumam aceitar poucas sessões
RTSP simultâneas).

---

## Estrutura do projeto

```
app/src/main/java/br/yoofree/app/
  MainActivity.kt      interface (Compose), retrato e paisagem
  CameraViewModel.kt   mantém a sessão viva ao girar a tela
  CameraSession.kt     junta cliente + decoder + gravador
  rtsp/
    RtspClient.kt      cliente RTSP mínimo (SETUP/PLAY + PTZ)
    HevcDepacketizer.kt  RTP -> access units HEVC (NAL único/AP/FU)
  media/
    VideoDecoder.kt    MediaCodec HEVC -> Surface
    Mp4Muxer.kt        muxer MP4 próprio (HEVC)
assets/
  icon.svg             ícone (câmera + "foss")
scripts/               toolchain local e geração de ícones
```

---

## Compilar do zero

O toolchain fica **isolado dentro do projeto** (não mexe no sistema):

```bash
bash scripts/setup-toolchain.sh   # JDK 17 + Android SDK
bash scripts/install-gradle.sh    # Gradle 8.9
source scripts/env.sh
./gradlew assembleDebug           # ou assembleRelease
```

Também dá pra compilar pelo **GitHub Actions** (`.github/workflows/build.yml`):
o APK sai como artifact de cada push.

---

## Segurança

- O app é **somente leitura** sobre a câmera (vídeo + PTZ). Não altera firmware.
- A câmera é acessada apenas na **rede local**.
- Recomendado: bloqueie o acesso da câmera à internet no seu roteador — ela
  tenta falar com servidores na nuvem que não são necessários pra nada aqui.
- Use apenas em câmeras **suas**.

---

## Licença

**GPL-3.0-or-later** — veja [LICENSE](LICENSE). Livre pra usar, estudar e
modificar; derivados continuam abertos.

---

## Versão Linux (desktop)

Também tem a versão para **Linux**, em `desktop/` — mesma ideia, feita em
**Python + PySide6** com o vídeo decodificado pelo **GStreamer**.

Instalação por comando (qualquer distro):

```bash
curl -fsSL https://raw.githubusercontent.com/Wennington123/YooFree/main/desktop/packaging/install.sh | bash
```

O instalador detecta o gerenciador de pacotes (pacman, apt, dnf, zypper, apk,
xbps, emerge), instala o que faltar, e cria o comando **`yoofree`**:

```bash
yoofree                       # abre a janela
yoofree --host 192.168.1.2    # já conecta
```

Detalhes em [desktop/README.md](desktop/README.md).
