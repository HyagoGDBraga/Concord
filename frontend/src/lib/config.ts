/**
 * Configuracao publica do cliente.
 *
 * Estas variaveis sao embutidas no bundle em tempo de build (prefixo
 * NEXT_PUBLIC_) e ficam visiveis para qualquer pessoa que abrir o DevTools.
 * Nenhum segredo pode entrar aqui — nem chave de TURN, nem credencial de
 * banco, nem token de API. As credenciais de TURN sao efemeras e emitidas pelo
 * backend em tempo de execucao.
 */

/**
 * Endereco do WebSocket, derivado da pagina em tempo de execucao.
 *
 * Antes vinha de NEXT_PUBLIC_WS_URL, fixado em "ws://localhost/api/ws" no
 * momento do build. Isso funciona numa maquina so e quebra em qualquer outro
 * cenario: pelo ngrok, por IP de rede local ou em producao, o navegador do
 * outro usuario tentava abrir a conexao contra a PROPRIA maquina dele, onde
 * nao ha servidor nenhum. Sem WebSocket nao ha presenca, nem sinalizacao, nem
 * eventos de sala — as duas pessoas entram na chamada e nao se enxergam.
 *
 * Derivar de window.location resolve os tres casos de uma vez e ainda escolhe
 * o esquema certo: pagina em https exige wss, e um ws:// ali seria bloqueado
 * pelo navegador como conteudo misto — que e exatamente o que acontece com o
 * ngrok, que serve https.
 */
function resolveWsUrl(): string {
  // Valor explicito continua tendo prioridade, para quem separa o backend em
  // outro dominio.
  const configurado = process.env.NEXT_PUBLIC_WS_URL;
  if (configurado && configurado.trim() !== "") {
    return configurado;
  }

  // Durante a renderizacao no servidor nao existe window. O valor devolvido
  // aqui nunca chega a ser usado: a conexao so e aberta no navegador.
  if (typeof window === "undefined") {
    return "ws://localhost/api/ws";
  }

  const esquema = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${esquema}//${window.location.host}/api/ws`;
}

export const config = {
  /** Base da API REST. Mesma origem do frontend, via Caddy. */
  apiUrl: process.env.NEXT_PUBLIC_API_URL ?? "/api",
  /** Endpoint STOMP/WebSocket. Ver resolveWsUrl. */
  get wsUrl(): string {
    return resolveWsUrl();
  },
} as const;
