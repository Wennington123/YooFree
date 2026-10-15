# Pesquisa PTZ — apps de referência (engenharia reversa)

Análise estática (RE.a / JADX headless) dos APKs puxados por ADB de um Galaxy A32 (SM-A325M), 
em 2026-10-15. Nada aqui executa os apps; tudo vem de decompilação + strings nativas.

APKs e hashes (base):
- `com.intelbras.isiclite` 2.8.6 (2026041518) — minSDK 23 / tgt 35
- `com.tuya.smart` 7.11.4 (866) — minSDK 24 / tgt 36
- `com.xm.csee` (iCSee) 7.9.4_G (79430) — minSDK 24 / tgt 37

---

## 1. iCSee (XiongMai / XMeye, `libFunSDK.so`) == DVRIP/Sofia (NetSurveillance)

A **mesma** família de protocolo das câmeras Yoosee/GW (porta LAN **34567**, TCP,
JSON/XML com header binário de 20 bytes).

- Java: `FunSDK.DevPTZControl(user, devId, chnId, cmd, stop, speed, seq)`
  - `stop`: 1 = soltar movimento, 0 = mover
  - `speed`: UI manda `max(1, dpi*2.0)` ou `*4.0` (buffer circular)
  - códigos `cmd` observados na UI (`MonitorFragment.onPtzDirection` +
    `MonitorBottomPresenter.ptzCtrl`): **0..3 = Up/Down/Left/Right** (com swap
    por espelho/flip conforme ability do device), zoom/focus por outros caminhos
- Wire (lan nativo): `H264_DVR_PTZControl` → msg **`OPPTZControl`**, MsgID **1400**
  (comm. + `PROTOCOL_NOTES.md` do ha-xmeye-nvr). Canais **a partir de 0**.
- Strings nativas confirmam nomes de comando: `Up/Down/Left/Right/Stop/Preset/
  Tour/FocusNear/FocusFar/IrisOpen/IrisClose`/Zoom
- Config PTZ serial (UART/RS485) vem por `DevCmdGeneral(..., 1042, UART_PTZ_CMD, ...)`
- Extras do protocolo: login MsgID 1000 com hash `sofia_hash` (MD5→base62 8 chars);
  canais: RTSP conta a partir de 1, DVRIP a partir de 0; limite de conexões TCP
  (geralmente 10) e `Ret=100` mesmo em payload inválido em alguns firmwares.

**Implicação YooFree:** podemos migrar o PTZ pra sessão DVRIP na porta 34567
(carece de sala própria de socket) — mais confiável que `SET_PARAMETER` RTSP,
que hoje os GLES? Respondem 405 em setup mas imprimem PTZ implausível.

## 2. Tuya Smart (thingclips) — DataPoints na nuvem

- Interface: `IThingIPCPTZ` (addCollectionPoint, setCruiseMode, publishDps...)
- Direções (enum `PTZDirection`, campo dpValue):
  `0=UP 1=RIGHT_UP 2=RIGHT 3=RIGHT_DOWN 4=DOWN 5=LEFT_DOWN 6=LEFT 7=LEFT_UP
   8=ROTATE 9=CALIBRATING -1=UNKNOW`
- dp codes (strings confirmadas nos dexes): `ptz_control`, `ptz_stop`,
  `zoom_control`, `zoom_stop`, `motion_tracking`, `memory_point_set`, cruise
- Envio via `publishDps(dpCode, valor, callback)` — **nuvem MQTT**, não LAN.
- Vídeo usa stack P2P própria (split com .so dedicados) — fora do escopo curto.

**Implicação YooFree:** suportar câmeras Tuya exigiria conta+MQTT cloud (não cabe
no modelo "app fala direto com a câmera"). Alcançável só com Tuya Cloud SDK
próprio. Registrar como "não suportado" declarado, pelo escopo.

## 3. Intelbras iSIC Lite — Dahua NetSDK (`libnetsdk.so`, porta 37777)

- `com.company.NetSDK` (convencional SDK Dahua), `INetSDK.SDKPTZControlEx(
  loginId, channelIndex, cmd, speed, speed, 0, false)`
- Direções codificadas no app (`DahuaPtzController.move`, bitfield direction):
  **cmd 0=UP 1=DOWN 2=LEFT 3=RIGHT** (presets/tour: 54,55,56,57,58,59,60,61,
  10,13,47,48,43,44,39,40 conforme menu)
- Throttle de 200 ms por comando de movimento.

---

## Backlog resultante (próximas mudanças)

1. **PTZ YooFree:** manter RTSP SET_PARAMETER na sessão de streaming + fallback
   DVRIP porta 34567 (`OPPTZControl` MsgID 1400; login 1000 com sofia_hash).
2. Botão "Conectar" aceitar path/URI alternativos de SETUP (feito em RtspClient).
3. Normalização de códigos de direção num módulo único de PTZ
   (ex.: enum Directions up/down/left/right com espelho/flip por device, como o
   iCSee faz).
4. Câmeras Dahua/Intelbras Luca: ponto de entrada futuro (RTSP ONVIF já cobre
   vídeo; PTZ via SDK da Dahua só com conta de biblioteca nativa — risco alto).
5. Tuya: fora do alvo inicial.

## Reprodução da análise

```bash
# REA:  npx -y rea-agents@latest setup --client commandcode --yes
export REA_JADX_MCP_JAR=$HOME/Projetos/rea-tools/jadx-headless-mcp-0.7.1-all.jar
export REA_JADX_HEAP_MIB=3500        # Tuya precisa de >2 GB
npx -y rea-agents@latest search-android-classes <apk> PTZ
npx -y rea-agents@latest inspect-android-method <apk> <classe> <metodo> --format json
```
