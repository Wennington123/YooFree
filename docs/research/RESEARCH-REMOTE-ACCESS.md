# Acesso remoto (fora do Wi‑Fi local) — estudo

Pergunta: dá para o YooFree ver a câmera fora de casa **sem** usar o app/nuvem do
fabricante?

Fontes: engenharia reversa pública do app Yoosee 6.46.1 e do protocolo das
câmeras (projeto [TianEye](https://github.com/andrewloable/TianEye), MIT, com
anotações de captura em hardware real), e o SDK público Gwell P2P.

## O que se sabe das câmeras

- **Geração Gwell (legado):** P2P proprietário por UDP (família "PPPP"/Gwell
  P2P). Existe SDK de referência da Gwell, mas sem documentação aberta de
  protocolo nem licença compatível para reimplementação no app.
- **Geração nova (IoTVideo):** o handshake P2P é **autenticado por token emitido
  pela nuvem**. Sem esse token não há sessão P2P; com apenas a senha do
  dispositivo **não é possível** falsificar a nuvem. No hardware testado, PTZ e
  reprodução do cartão SD no app oficial dependem justamente dessa sessão P2P
  token-gated.
- **RTSP local:** `rtsp://ip:554/onvif1` (principal) e `/onvif2` (sub). Em
  firmware medido, o RTSP usa **HTTP Digest** (realm `HIipCamera`, usuário
  `admin`, senha definida na tela "Conectar NVR" do app). Áudio real medido:
  **G.711 A-law 16 kHz** (a ONVIF anuncia 8 kHz, errado).
- ONVIF na porta **5000** (SOAP; streams funcionam sem auth no firmware testado;
  Events/PTZ anunciados mas não funcionam). WS‑Discovery não responde no
  firmware 32.01.37 — descoberta tem que ser por sondagem de porta, como o
  YooFree já faz.

## Opções avaliadas

| Opção | Como funciona | Esforço | Veredito |
|---|---|---|---|
| **A. VPN (WireGuard/Tailscale)** | Celular entra na mesma VPN da rede de casa | zero código | ✅ **recomendado agora** |
| **B. Port forwarding / UPnP IGD** | Mapear 554 no roteador e acessar pelo IP público | baixo | ⚠️ só com IP público (CGNAT quebra); RTSP exposto e (no firmware testado) com Digest — alto risco de segurança |
| **C. Túnel embutido no app** | WireGuard em userspace + relay próprio | alto (infra + app) | 🔬 viável a médio prazo, mas troca o modelo "sem nuvem" por "sem nuvem do fabricante" |
| **D. Reimplementar o P2P/nuvem do fabricante** | Falar o protocolo P2P deles | altíssimo | ❌ inviável: token-gated na geração nova; sem doc/licença na antiga |

Observação: até o TianEye (projeto dedicado a substituir o app Yoosee) declara
"acesso remoto fora da LAN" como **não-objetivo**, recomendando VPN. Isso
corrobora o veredito acima em vez de ser só opinião deste estudo.

## Decisão

1. **Curto prazo:** documentar e recomendar VPN (WireGuard/Tailscale). Nada de
   abrir porta no roteador por padrão.
2. **Pré-requisito técnico** caso avancemos: suportar **RTSP com Digest auth**
   (hoje o cliente assume câmera sem senha; com senha definida o `SETUP`
   receberia 401).
3. **Médio prazo (se houver demanda):** avaliar túnel embutido (WireGuard
   userspace) com um relay simples, mantendo a câmera só na LAN.
4. **Não faremos:** impersonar a nuvem/P2P do fabricante.

## Se for tentar funcionar hoje, sem app novo

- Tailscale na rede de casa (ex.: num Raspberry Pi ou no próprio roteador) e no
  celular → abrir o YooFree apontando para o IP Tailscale da câmera.
- Ou WireGuard num servidor em casa com rota para a sub-rede das câmeras.
