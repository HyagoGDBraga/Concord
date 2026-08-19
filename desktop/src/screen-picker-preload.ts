/**
 * Ponte do seletor de tela. Duas funcoes, nada mais.
 */

import { contextBridge, ipcRenderer } from "electron";

contextBridge.exposeInMainWorld("picker", {
  listar: () => ipcRenderer.invoke("picker:sources"),
  escolher: (id: string | null) => ipcRenderer.send("picker:choose", id),
});
