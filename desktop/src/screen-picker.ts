/**
 * Seletor de tela.
 *
 * O Electron entrega a LISTA de fontes captureis, mas nao uma interface para
 * escolher. Escolher automaticamente a primeira seria inaceitavel: o usuario
 * comecaria a transmitir uma tela que nao escolheu.
 *
 * Esta janela e a interface minima que resolve isso — miniaturas, um clique,
 * e cancelar como opcao de primeira classe.
 */

import { BrowserWindow, ipcMain } from "electron";
import type { DesktopCapturerSource } from "electron";
import { join } from "node:path";

interface FonteSerializada {
  id: string;
  nome: string;
  miniatura: string;
  tipo: "tela" | "janela";
}

export async function escolherFonte(
  fontes: DesktopCapturerSource[],
  pai: BrowserWindow | null,
): Promise<DesktopCapturerSource | null> {
  if (fontes.length === 0) {
    return null;
  }

  const seletor = new BrowserWindow({
    width: 760,
    height: 560,
    parent: pai ?? undefined,
    modal: true,
    resizable: false,
    minimizable: false,
    maximizable: false,
    title: "Escolher o que compartilhar",
    backgroundColor: "#0b1220",
    show: false,
    webPreferences: {
      preload: join(__dirname, "screen-picker-preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

  const serializadas: FonteSerializada[] = fontes.map((fonte) => ({
    id: fonte.id,
    nome: fonte.name,
    miniatura: fonte.thumbnail.toDataURL(),
    tipo: fonte.id.startsWith("screen:") ? "tela" : "janela",
  }));

  return new Promise((resolver) => {
    let resolvido = false;

    const concluir = (escolhida: DesktopCapturerSource | null) => {
      if (resolvido) {
        return;
      }
      resolvido = true;
      ipcMain.removeHandler("picker:sources");
      ipcMain.removeAllListeners("picker:choose");
      if (!seletor.isDestroyed()) {
        seletor.close();
      }
      resolver(escolhida);
    };

    ipcMain.handle("picker:sources", () => serializadas);

    ipcMain.on("picker:choose", (_evento, id: string | null) => {
      concluir(fontes.find((fonte) => fonte.id === id) ?? null);
    });

    // Fechar a janela no X equivale a cancelar.
    seletor.on("closed", () => concluir(null));

    void seletor.loadFile(join(__dirname, "screen-picker.html"));
    seletor.once("ready-to-show", () => seletor.show());
  });
}
