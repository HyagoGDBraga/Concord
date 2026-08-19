/**
 * Configuracao do aplicativo desktop.
 *
 * A URL do servidor vem, nesta ordem: argumento de linha de comando, variavel
 * de ambiente, valor compilado. Isso permite apontar o mesmo binario para o
 * ambiente de desenvolvimento sem recompilar.
 */

const PADRAO = "https://concord.exemplo.com";

function daLinhaDeComando(): string | null {
  const argumento = process.argv.find((valor) => valor.startsWith("--url="));
  return argumento ? argumento.slice("--url=".length) : null;
}

export const SERVER_URL: string =
  daLinhaDeComando() ?? process.env.CONCORD_URL ?? PADRAO;

/**
 * Origem permitida.
 *
 * Tudo no aplicativo e comparado contra ela: navegacao, permissao de midia,
 * abertura de janela. Uma janela do Electron que possa navegar para qualquer
 * lugar e um navegador sem barra de endereco — o usuario nao teria como saber
 * que saiu do Concord.
 */
export const ALLOWED_ORIGIN: string = new URL(SERVER_URL).origin;

export function isAllowed(url: string): boolean {
  try {
    return new URL(url).origin === ALLOWED_ORIGIN;
  } catch {
    return false;
  }
}
