/**
 * Ponte entre o processo principal e a pagina.
 *
 * Expoe o MINIMO. O renderer roda com `contextIsolation` e `sandbox` ligados e
 * sem acesso ao Node — a unica coisa que ele pode pedir ao processo principal e
 * o que esta declarado aqui.
 *
 * A tentacao de expor `ipcRenderer` inteiro e grande e o erro e classico: um
 * XSS na aplicacao web passaria a ter acesso ao sistema de arquivos da maquina.
 */

import { contextBridge, ipcRenderer } from "electron";

contextBridge.exposeInMainWorld("concordDesktop", {
  /** Identifica que a aplicacao roda no desktop, nao no navegador. */
  isDesktop: true,

  /** Versao do aplicativo, exibida em Conta. */
  getVersion: (): Promise<string> => ipcRenderer.invoke("app:version"),

  /**
   * Sinaliza chamada recebida ao sistema operacional.
   *
   * No desktop, a janela pode estar minimizada quando alguem liga — sem isso, o
   * convite tocaria em uma janela que ninguem esta vendo.
   */
  notifyIncomingCall: (nome: string): void => {
    ipcRenderer.send("call:incoming", nome);
  },

  /** Limpa o destaque quando a chamada termina. */
  clearCallNotification: (): void => {
    ipcRenderer.send("call:ended");
  },
});
