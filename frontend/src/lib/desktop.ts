/**
 * Integracao opcional com o aplicativo desktop.
 *
 * O mesmo codigo roda no navegador e no Electron. Quando `concordDesktop` nao
 * existe — que e o caso no navegador — todas as funcoes daqui viram no-op, e
 * nenhuma tela precisa saber onde esta rodando.
 */

interface ConcordDesktopBridge {
  isDesktop: true;
  getVersion(): Promise<string>;
  notifyIncomingCall(nome: string): void;
  clearCallNotification(): void;
}

declare global {
  interface Window {
    concordDesktop?: ConcordDesktopBridge;
  }
}

function ponte(): ConcordDesktopBridge | null {
  if (typeof window === "undefined") {
    return null;
  }
  return window.concordDesktop ?? null;
}

export function isDesktop(): boolean {
  return ponte() !== null;
}

/**
 * Avisa o sistema operacional que ha uma chamada entrando.
 *
 * No desktop a janela pode estar minimizada — sem isso, o convite tocaria em
 * uma janela que ninguem esta vendo. No navegador, nao faz nada: a aba ja tem
 * as proprias formas de chamar atencao.
 */
export function notifyIncomingCall(nome: string): void {
  ponte()?.notifyIncomingCall(nome);
}

export function clearCallNotification(): void {
  ponte()?.clearCallNotification();
}

export async function desktopVersion(): Promise<string | null> {
  const bridge = ponte();
  return bridge ? bridge.getVersion() : null;
}
