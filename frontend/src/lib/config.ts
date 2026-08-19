/**
 * Configuracao publica do cliente.
 *
 * Estas variaveis sao embutidas no bundle em tempo de build (prefixo
 * NEXT_PUBLIC_) e ficam visiveis para qualquer pessoa que abrir o DevTools.
 * Nenhum segredo pode entrar aqui — nem chave de TURN, nem credencial de
 * banco, nem token de API. As credenciais de TURN sao efemeras e serao
 * emitidas pelo backend em tempo de execucao (Fase 5).
 */
export const config = {
  /** Base da API REST. Mesma origem do frontend, via Caddy. */
  apiUrl: process.env.NEXT_PUBLIC_API_URL ?? "/api",
  /** Endpoint STOMP/WebSocket. Usado a partir da Fase 4. */
  wsUrl: process.env.NEXT_PUBLIC_WS_URL ?? "ws://localhost/api/ws",
} as const;
