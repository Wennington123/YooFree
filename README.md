# YooFree

Cliente **aberto e sem anúncios** para câmeras **Yoosee / GW (Gwell)**, para
**Android, Linux e Windows**.

Fala direto com a câmera na sua rede local: **sem nuvem, sem conta, sem IA,
sem telemetria**. Só você e a sua câmera.

<p align="center">
  <img src="assets/icon.svg" width="128" alt="YooFree">
</p>

[![Licença: GPL-3.0](https://img.shields.io/badge/licen%C3%A7a-GPL--3.0-7C6CF0.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/Wennington123/YooFree)](../../releases)
[![Build](https://github.com/Wennington123/YooFree/actions/workflows/build.yml/badge.svg)](../../actions)

---

## Versões

| Plataforma | Pacote | Como instalar |
|---|---|---|
| **Android** 7+ | `app-debug.apk` (v0.2) | baixe na aba [Releases](../../releases) e instale |
| **Linux** (qualquer distro) | `yoofree-desktop.tar.gz` | um comando (veja abaixo) |
| **Windows** 10/11 (x64) | `YooFree-Setup-x64.exe` | instalador (com atalhos e desinstalador) |
| **Windows** 10/11 (ARM64) | `YooFree-Setup-arm64.exe` | instalador (com atalhos e desinstalador) |

Também tem as versões **portáteis** (`.zip`), pra quem não quer instalar.

Todas as versões fazem a mesma coisa:

- 📹 **Vídeo ao vivo** HEVC/H.265 1080p direto da câmera
- 🕹️ **PTZ**: mover a câmera (cima / baixo / esquerda / direita)
- 🔍 **Zoom** digital de 1x a 5x
- 🎥 **Gravação local** em MP4 (sem re-encode, sem nuvem)
- 🔄 **Girar** a imagem em 90° (útil se a câmera estiver "de cabeça pra baixo")

E o Android ainda tem **modo paisagem** (vira o celular e o vídeo ocupa a tela toda).

---

## Novidades da v0.2

- 🛠️ **Correção do crash ao Conectar** em alguns aparelhos (ex.: Redmi Note 12S,
  Android 15) — o decoder HEVC agora se recupera sozinho quando o codec cai
- 🛠️ **Câmeras com firmware novo**: resolve o `SETUP 405 Method Not Allowed`
  tentando URIs alternativas aceitas pelo RTSP dessas câmeras

---

## Android

1. Baixe o APK da versão atual na aba [Releases](../../releases).
2. Autorize "instalar de fontes desconhecidas" quando o Android pedir.
3. Abra o app, digite o **IP da câmera** (ex.: `192.168.1.2`) e toque em **Conectar**.
4. **PTZ** pelas setas, **zoom** no `−`/`+`, **girar** no botão, **gravar** no botão.
   Os vídeos ficam em `Android/data/br.yoofree.app/files/YooFree/`.

O celular precisa estar no **mesmo Wi‑Fi** da câmera.

---

## Linux

Instalação por comando, em qualquer distro:

```bash
curl -fsSL https://raw.githubusercontent.com/Wennington123/YooFree/main/desktop/packaging/install.sh | bash
```

O instalador:
- confere as dependências e **detecta o gerenciador de pacotes**
  (pacman, apt, dnf, zypper, apk, xbps, emerge)
- instala em `~/.local` (o app não precisa de root)
- cria o comando **`yoofree`** e um atalho no menu

Depois é só:

```bash
yoofree                       # abre a janela
yoofree --host 192.168.1.2    # já conecta na câmera
```

Os vídeos vão para `~/Videos/YooFree/`. Detalhes em
[desktop/README.md](desktop/README.md).

---

## Windows 10 / 11

Escolha a versão da sua máquina:

- **x64** (Intel/AMD): `YooFree-Setup-x64.exe`
- **ARM64** (Snapdragon, Surface Pro X, notebooks ARM): `YooFree-Setup-arm64.exe`

**Instalador** (recomendado):

1. Baixe o `YooFree-Setup-<arch>.exe` na aba [Releases](../../releases).
2. Rode o instalador — ele cria atalho no Menu Iniciar (e opcionalmente na
   Área de Trabalho), com ícone, e tem desinstalador normal pelo Painel de Controle.
3. Não precisa de admin (dá pra instalar só pro seu usuário).

**Portátil** (sem instalar): baixe `YooFree-windows-<arch>.zip`, descompacte e
rode `YooFree.exe`. Não escreve nada no registro.

> Na primeira execução o Windows pode mostrar o aviso azul do SmartScreen
> ("Windows protegeu o seu PC"), porque o executável não tem assinatura paga.
> Clique em **Mais informações → Executar assim mesmo**.
>
> Os vídeos vão para `%USERPROFILE%\Videos\YooFree\`.

Pra saber sua arquitetura: **Configurações → Sistema → Sobre** (procure
"Tipo de sistema").

---

## Como descobrir o IP da câmera

1. Configure a câmera normalmente no app oficial da Yoosee.
2. Veja os dispositivos conectados no seu roteador — a câmera aparece com um IP
   tipo `192.168.1.2`.
3. Ou, no app oficial: **Configurações da câmera → Informações da rede**.

> Dica: fixe o IP no roteador (reserva DHCP) pra ele não mudar depois.

---

## Perguntas frequentes

**Preciso da senha da câmera?**
Não. O YooFree usa o RTSP local, que nessa linha não exige autenticação para
`SETUP`/`PLAY`. Só o IP basta.

**Fala com a nuvem da Yoosee?**
Não. A conexão é direta (`IP:554`), na sua rede local.

**Funciona fora de casa?**
Não por enquanto — acesso remoto está nos planos (veja abaixo). Enquanto isso,
use uma VPN (WireGuard/Tailscale) e mantenha o aparelho na VPN.

**Por que um cliente RTSP próprio?**
Porque essas câmeras **não falam RTSP padrão**: o `DESCRIBE` responde
`400 Bad Request` quando o cliente manda autenticação, então players prontos
(VLC, ffmpeg, ExoPlayer, GStreamer) não conseguem ler o SDP. O YooFree faz o
mínimo que a câmera aceita (`SETUP` + `PLAY`, e `SET_PARAMETER` para PTZ) e
depacketiza o HEVC na mão.

**Por que o Android não usa o MediaMuxer?**
Porque em vários aparelhos (ex.: Samsung) o `MediaMuxer` **crasha nativamente**
com HEVC. O YooFree tem um **muxer MP4 próprio**, que escreve os boxes na mão.
Essa mesma lógica é usada no Linux e no Windows.

**Posso usar em mais de uma câmera?**
Hoje conecta em uma por vez (essas câmeras costumam aceitar poucas sessões RTSP
simultâneas). Suporte a múltiplas câmeras está nos planos.

---

## No futuro (implementações e sugestões)

Ideias que estão no radar, em ordem de prioridade — sugestões novas são bem-vindas
nas [issues](../../issues):

- 🕹️ **PTZ à prova de câmera**: mover o controle PTZ do canal RTSP para o
  protocolo nativo das câmeras dessa linha (DVRIP/XMeye, porta 34567 — o mesmo
  caminho que os apps oficiais usam), com fallback pro meio atual. Já temos o
  estudo pronto em [docs/research/RESEARCH-PTZ.md](docs/research/RESEARCH-PTZ.md)
- 🌐 **Acessar fora do Wi‑Fi local**: o cliente YooFree sem depender de VPN —
  pela sua própria nuvem (protocolo P2P das câmeras) ou por túnel embutido;
  é a funcionalidade mais pedida depois do PTZ
- 🎛️ **Direções PTZ normalizadas**: inverter esquerda/direita e cima/baixo
  automaticamente quando a câmera está montada de cabeça pra baixo (como o app
  oficial faz)
- 📷 **Suporte a mais câmeras**: iCSee/XMEye (afinal, é o mesmo protocolo nativo),
  Novus/Dahua/Intelbras (SDK dahua) e afins — o RTSP de vídeo já é o mesmo caminho
- 🔢 **Múltiplas câmeras** no mesmo app, com lista/grid
- 🗑️ (provavelmente **não** vai): câmeras Tuya — o PTZ delas só funciona pela
  nuvem da Tuya, que é oposta ao jeito "sem nuvem" do YooFree

---

## Apoiar o projeto

O YooFree é 100% gratuito, sem anúncio e open source. Se ele te economizou um
app antigo cheio de ad (ou umas semanas de dor de cabeça), tomo um café: ☕

### Pix

<p align="center">
  <img src="assets/pix-qrcode.png" width="220" alt="QR Code Pix">
</p>

- **Chave (telefone):** `+55 74 98821-7793`
- **Valor:** o que você escolher (QR também aceita valor livre no app do banco)
- Ou copie o código Pix: 

```
00020126360014br.gov.bcb.pix0114+55749882177935204000053039865802BR5910WENNINGTON6009BARREIRAS62070503***6304D509
```

### Buy Me a Coffee

Em breve — apoio internacional via cartão, pra quem não tem Pix.

---

## Estrutura do repositório

```
app/          versão Android (Kotlin + Compose)
desktop/      versões Linux e Windows (Python + PySide6)
  yoofree/
    rtsp.py       cliente RTSP mínimo (SETUP/PLAY + PTZ)
    hevc.py       depacketizer RTP -> HEVC
    muxer.py      muxer MP4 próprio
    player.py     escolhe o backend de vídeo
    player_gst.py backend GStreamer (Linux)
    player_av.py  backend PyAV/FFmpeg (Windows)
    ui.py         interface (PySide6)
  packaging/  instalador universal e build dos pacotes
assets/       ícone (SVG) + QR do Pix
docs/         pesquisa de protocolo (engenharia reversa dos apps oficiais)
scripts/      toolchain Android local
.github/      workflows (APK, Windows)
```

---

## Compilar do código

**Android** (toolchain isolado, não mexe no sistema):

```bash
bash scripts/setup-toolchain.sh   # JDK 17 + Android SDK
bash scripts/install-gradle.sh    # Gradle 8.9
source scripts/env.sh
./gradlew assembleDebug           # ou assembleRelease
```

**Linux / Windows** (Python 3.9+):

```bash
cd desktop
pip install -r requirements.txt   # PySide6 + av
python3 main.py --host 192.168.1.2
```

O APK e o instalador do Windows são gerados pelo GitHub Actions
(`.github/workflows/build.yml` e `desktop-windows.yml`).

---

## Segurança

- O app é **somente leitura** sobre a câmera (vídeo + PTZ). Não altera firmware.
- A câmera é acessada apenas na **rede local**.
- Recomendado: bloqueie o acesso da câmera à internet no roteador — ela tenta
  falar com servidores na nuvem que não são necessários pra nada aqui.
- Use apenas em câmeras **suas**.

---

## Licença

**GPL-3.0-or-later** — veja [LICENSE](LICENSE). Livre pra usar, estudar e
modificar; derivados continuam abertos.
