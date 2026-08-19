# Concord — Desktop

Aplicativo Electron que carrega a mesma interface web. Não há código de tela duplicado: o desktop acrescenta o que o navegador não pode dar.

---

## 1. Por que Electron e não Tauri (decisão D-02)

Tauri produz binários muito menores e consome menos memória — vantagens reais. A decisão foi contra ele por um motivo específico deste produto:

**Tauri usa a webview do sistema operacional.** WKWebView no macOS, WebKitGTK no Linux, WebView2 no Windows. O suporte a `getDisplayMedia` e `setSinkId` varia entre elas, e varia também entre versões do mesmo SO. Um app de chamadas com compartilhamento de tela ficaria refém de qual versão de WebKitGTK a distribuição do usuário instalou.

O Electron embarca um Chromium único, igual nas três plataformas, e expõe `desktopCapturer` com `setDisplayMediaRequestHandler` — que é o que faz o compartilhamento da Fase 6 funcionar no desktop.

O custo é honesto: ~150 MB por instalador e ~200 MB de RAM. Para um app que fica aberto o dia todo em um desktop, é aceitável.

---

## 2. O que o desktop acrescenta

| Recurso | Navegador | Desktop |
|---|---|---|
| Seletor de tela | Nativo do Chrome | Janela própria (§4) |
| Notificação de chamada | Só se a aba permitir | Notificação do SO + destaque na barra de tarefas |
| Conexão com janela minimizada | Throttling do navegador pode derrubar | `backgroundThrottling: false` |
| Instância única | Várias abas possíveis | Uma janela, garantida |

**Nada é exclusivo do desktop.** O `lib/desktop.ts` do frontend detecta a ponte e vira no-op quando ela não existe — nenhuma tela sabe onde está rodando.

---

## 3. Segurança

Três configurações definem a postura, e nenhuma deve ser afrouxada:

```ts
contextIsolation: true,   // renderer e preload em contextos separados
nodeIntegration: false,   // a página não alcança o Node
sandbox: true,            // o renderer roda no sandbox do Chromium
```

O preload expõe **quatro funções**, nada mais. A tentação de expor `ipcRenderer` inteiro é grande e o erro é clássico: um XSS na aplicação web passaria a ter acesso ao sistema de arquivos da máquina.

**Navegação restrita à origem do Concord.** `will-navigate` e `setWindowOpenHandler` bloqueiam qualquer outro destino; links externos abrem no navegador padrão, onde o usuário tem barra de endereço e as próprias defesas. Uma janela Electron que navega para qualquer lugar é um navegador sem barra de endereço — o usuário não teria como perceber que saiu do Concord.

**Permissões concedidas por origem e por tipo.** O padrão do Electron é conceder o que a página pedir. Aqui, só `media`, `display-capture` e `notifications`, e só para a origem configurada.

**Certificado inválido é sempre recusado.** O handler existe explicitamente para que ninguém "resolva" um erro de TLS em desenvolvimento com `preventDefault()`.

---

## 4. Seletor de tela

O Electron entrega a **lista** de fontes capturáveis, não uma interface para escolher. Sem `setDisplayMediaRequestHandler`, `getDisplayMedia` simplesmente falha.

Escolher automaticamente a primeira fonte seria inaceitável: o usuário começaria a transmitir uma tela que não escolheu. Então há uma janela modal com miniaturas, agrupada em telas e janelas, com **cancelar como opção de primeira classe** e `Esc` funcionando.

Em plataformas com seletor nativo do sistema, `useSystemPicker: true` o prefere — é o que o usuário já reconhece e não depende do nosso código.

O nome da janela vem do título de um programa qualquer do sistema e é inserido com `textContent`, nunca `innerHTML`.

**Áudio do sistema não é capturado**, mesma decisão da Fase 6.

---

## 5. Desenvolvimento

```bash
cd desktop
npm install

# Contra o ambiente local
npm run dev

# Contra qualquer servidor
npm start -- --url=https://concord.seudominio.com
```

A URL vem, nesta ordem: `--url=`, variável `CONCORD_URL`, valor compilado em `src/config.ts`. Antes de gerar instaladores, ajuste o padrão em `config.ts` para o seu domínio.

---

## 6. Empacotamento

```bash
npm run dist:linux    # AppImage + .deb
npm run dist:win      # instalador NSIS
npm run dist:mac      # .dmg
```

Saída em `desktop/release/`.

**Cada plataforma precisa ser empacotada na própria plataforma** — ou em CI com os três runners. Não há cross-build confiável para macOS.

### Assinatura de código: não configurada

| Plataforma | O que o usuário vê |
|---|---|
| Windows | Aviso do SmartScreen; precisa de "Mais informações → Executar assim mesmo" |
| macOS | Gatekeeper bloqueia; precisa de clique com botão direito → Abrir |
| Linux | Nada; AppImage e .deb não exigem assinatura |

Resolver custa certificado pago em cada plataforma (~US$ 100–400/ano no Windows, US$ 99/ano no Apple Developer Program), mais notarização no macOS. **Decisão adiada.** Para uso entre pessoas conhecidas, o aviso é contornável e explicável; para distribuição pública, não é.

### macOS

`entitlements.mac.plist` declara microfone, câmera e rede de saída. Sem elas, o sistema nega a captura sem nem perguntar ao usuário.

A partir do macOS 15, a captura de tela exige permissão explícita em Ajustes do Sistema → Privacidade → Gravação de Tela, e o sistema a revalida periodicamente. É comportamento da plataforma, não do aplicativo.

---

## 7. Atualização automática

**Não implementada.** O `electron-updater` exige um servidor de atualização e, para valer alguma coisa, **assinatura de código** — sem ela, o mecanismo de atualização vira um vetor de instalação de binário arbitrário.

Enquanto isso, a atualização é manual: baixar e reinstalar. Como a interface vem do servidor, correções de tela chegam sem reinstalar nada — só mudanças no processo principal exigem novo instalador.

---

## 8. Limitações conhecidas

1. **Sem assinatura de código** (§6).
2. **Sem atualização automática** (§7).
3. **Sem bandeja do sistema.** Fechar a janela encerra o app no Windows e no Linux; chamadas não chegam com ele fechado.
4. **Sem início automático** com o sistema.
5. **Nada disso foi executado.** Como o restante do projeto, o código do desktop nunca foi compilado nem empacotado.
