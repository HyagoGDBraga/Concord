/**
 * Processo principal do Concord Desktop.
 *
 * Este arquivo é a razão técnica da decisão D-02 (Electron em vez de Tauri). O
 * Tauri usa a webview do sistema — WKWebView no macOS, WebKitGTK no Linux — e o
 * suporte a `getDisplayMedia` e `setSinkId` varia entre elas. O Electron
 * embarca um Chromium único, igual nas três plataformas, e expõe
 * `desktopCapturer` + `setDisplayMediaRequestHandler`, que é o que permite
 * compartilhar tela sem depender do que o SO oferece.
 */

import {
  BrowserWindow,
  Menu,
  Notification,
  app,
  desktopCapturer,
  ipcMain,
  session,
  shell,
} from "electron";
import { join } from "node:path";
import { ALLOWED_ORIGIN, SERVER_URL, isAllowed } from "./config";
import { escolherFonte } from "./screen-picker";

let janelaPrincipal: BrowserWindow | null = null;

/**
 * Instância única.
 *
 * Duas janelas do Concord abertas significariam duas conexões WebSocket para o
 * mesmo usuário e dois destinos possíveis para o mesmo convite de chamada.
 */
const conseguiuTrava = app.requestSingleInstanceLock();
if (!conseguiuTrava) {
  app.quit();
}

app.on("second-instance", () => {
  if (janelaPrincipal) {
    if (janelaPrincipal.isMinimized()) {
      janelaPrincipal.restore();
    }
    janelaPrincipal.focus();
  }
});

function criarJanela(): void {
  janelaPrincipal = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 900,
    minHeight: 600,
    // Combina com o fundo da aplicação, para não haver um flash branco na
    // abertura.
    backgroundColor: "#0b1220",
    show: false,
    webPreferences: {
      preload: join(__dirname, "preload.js"),
      // As três linhas que definem a postura de segurança do aplicativo.
      // Nenhuma delas deve ser afrouxada por conveniência.
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
      // Sem isso, o Chromium suspende timers e conexões em janela minimizada —
      // e o WebSocket cairia sempre que o usuário guardasse a janela.
      backgroundThrottling: false,
    },
  });

  // Só exibe quando houver conteúdo pronto.
  janelaPrincipal.once("ready-to-show", () => janelaPrincipal?.show());

  janelaPrincipal.loadURL(SERVER_URL);

  janelaPrincipal.on("closed", () => {
    janelaPrincipal = null;
  });

  aplicarRestricoesDeNavegacao(janelaPrincipal);
}

/**
 * Impede que a janela vire um navegador sem barra de endereço.
 *
 * Uma janela do Electron que possa navegar para qualquer lugar é justamente
 * isso: o usuário não teria como perceber que saiu do Concord, e uma página
 * hostil herdaria o preload.
 */
function aplicarRestricoesDeNavegacao(janela: BrowserWindow): void {
  janela.webContents.on("will-navigate", (evento, url) => {
    if (!isAllowed(url)) {
      evento.preventDefault();
      // Link externo abre no navegador padrão, onde o usuário tem barra de
      // endereço, extensões e as próprias defesas.
      if (url.startsWith("https://")) {
        void shell.openExternal(url);
      }
    }
  });

  janela.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith("https://")) {
      void shell.openExternal(url);
    }
    // Nenhuma janela nova é aberta dentro do aplicativo.
    return { action: "deny" };
  });

  // Um <webview> aninhado herdaria privilégios e não é usado em lugar nenhum.
  janela.webContents.on("will-attach-webview", (evento) => evento.preventDefault());
}

/**
 * Permissões de mídia.
 *
 * Concedidas apenas para a origem do Concord, e apenas as necessárias. O padrão
 * do Electron é conceder tudo que a página pedir; deixar assim significaria que
 * qualquer conteúdo carregado ali teria microfone e câmera.
 */
function configurarPermissoes(): void {
  const PERMITIDAS = new Set(["media", "display-capture", "notifications"]);

  session.defaultSession.setPermissionRequestHandler(
    (webContents, permission, callback) => {
      const origem = webContents.getURL();
      callback(isAllowed(origem) && PERMITIDAS.has(permission));
    },
  );

  session.defaultSession.setPermissionCheckHandler(
    (_webContents, permission, requestingOrigin) =>
      requestingOrigin === ALLOWED_ORIGIN && PERMITIDAS.has(permission),
  );
}

/**
 * Captura de tela.
 *
 * No navegador, `getDisplayMedia` abre o seletor nativo do Chrome. No Electron
 * não existe seletor embutido: se este handler não for registrado, a chamada
 * simplesmente falha. É aqui que o compartilhamento de tela da Fase 6 passa a
 * funcionar no desktop.
 */
function configurarCapturaDeTela(): void {
  session.defaultSession.setDisplayMediaRequestHandler(
    async (request, callback) => {
      // Só a origem do Concord pode capturar a tela do usuário.
      if (!isAllowed(request.frame?.url ?? "")) {
        callback({});
        return;
      }

      try {
        const fontes = await desktopCapturer.getSources({
          types: ["screen", "window"],
          thumbnailSize: { width: 320, height: 180 },
          fetchWindowIcons: true,
        });

        const escolhida = await escolherFonte(fontes, janelaPrincipal);

        if (!escolhida) {
          // Cancelamento devolve vazio. A aplicação trata isso como AbortError
          // e não mostra erro — cancelar não é falha.
          callback({});
          return;
        }

        // audio: nunca. Capturar a saída de som da máquina inteira é a forma
        // mais fácil de transmitir sem querer uma notificação ou outra
        // conversa (mesma decisão da Fase 6).
        callback({ video: escolhida });
      } catch (erro) {
        console.error("Falha ao listar fontes de captura", erro);
        callback({});
      }
    },
    // Em plataformas que oferecem seletor nativo do sistema, ele é preferível:
    // é o que o usuário já reconhece e não depende do nosso código.
    { useSystemPicker: true },
  );
}

/** Chamada recebida com a janela em segundo plano. */
function configurarNotificacoes(): void {
  ipcMain.handle("app:version", () => app.getVersion());

  ipcMain.on("call:incoming", (_evento, nome: string) => {
    if (!janelaPrincipal) {
      return;
    }
    // Destaca na barra de tarefas sem roubar o foco: trazer a janela à frente
    // durante o que a pessoa estava fazendo é agressivo.
    janelaPrincipal.flashFrame(true);

    if (Notification.isSupported()) {
      const notificacao = new Notification({
        title: "Chamada recebida",
        body: `${nome} está ligando`,
        urgency: "critical",
      });
      notificacao.on("click", () => {
        janelaPrincipal?.show();
        janelaPrincipal?.focus();
      });
      notificacao.show();
    }
  });

  ipcMain.on("call:ended", () => {
    janelaPrincipal?.flashFrame(false);
  });
}

app.whenReady().then(() => {
  configurarPermissoes();
  configurarCapturaDeTela();
  configurarNotificacoes();

  // Menu padrão removido: ele expõe DevTools e recarregamento em produção.
  if (app.isPackaged) {
    Menu.setApplicationMenu(null);
  }

  criarJanela();

  app.on("activate", () => {
    // Convenção do macOS: clicar no ícone com o app aberto reabre a janela.
    if (BrowserWindow.getAllWindows().length === 0) {
      criarJanela();
    }
  });
});

app.on("window-all-closed", () => {
  // No macOS o aplicativo permanece ativo sem janelas, por convenção da
  // plataforma.
  if (process.platform !== "darwin") {
    app.quit();
  }
});

/**
 * Recusa certificado inválido, sempre.
 *
 * O comportamento padrão já é esse; o handler existe para deixar explícito que
 * ninguém deve "resolver" um erro de TLS em desenvolvimento chamando
 * `event.preventDefault()` aqui. Seria abrir o aplicativo a interceptação.
 */
app.on("certificate-error", (evento, _webContents, _url, _erro, _cert, callback) => {
  evento.preventDefault();
  callback(false);
});
