/**
 * Cliente HTTP unico do Concord.
 *
 * Nenhum componente chama `fetch` diretamente. Isso nao e estilo: e o que
 * garante que o token CSRF seja enviado em toda mutacao e que os erros da API
 * cheguem sempre no mesmo formato. Um `fetch` solto em um componente e um
 * caminho por onde uma requisicao sai sem protecao.
 */

import { config } from "./config";

/** Formato de erro devolvido pelo backend (GlobalExceptionHandler). */
export interface ApiErrorBody {
  code: string;
  message: string;
  timestamp: string;
  requestId: string;
  fieldErrors?: Record<string, string>;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly requestId: string;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, body: Partial<ApiErrorBody>) {
    super(body.message ?? "Erro inesperado");
    this.name = "ApiError";
    this.status = status;
    this.code = body.code ?? "UNKNOWN";
    this.requestId = body.requestId ?? "-";
    this.fieldErrors = body.fieldErrors ?? {};
  }

  /** O usuario nao esta autenticado (ou a sessao foi revogada). */
  get isUnauthenticated(): boolean {
    return this.status === 401;
  }
}

/**
 * Le o cookie XSRF-TOKEN.
 *
 * Este cookie e legivel por JavaScript de proposito — ele e a metade publica do
 * double submit. O cookie de sessao, esse sim, e HttpOnly e nunca aparece aqui.
 */
function readCsrfToken(): string | null {
  if (typeof document === "undefined") {
    return null;
  }
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

const MUTATING_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };

  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (MUTATING_METHODS.has(method)) {
    const csrf = readCsrfToken();
    if (csrf) {
      headers["X-XSRF-TOKEN"] = csrf;
    }
  }

  const response = await fetch(`${config.apiUrl}${path}`, {
    method,
    headers,
    // Mesma origem via Caddy: o cookie de sessao viaja sem CORS.
    credentials: "same-origin",
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const parsed = text ? (JSON.parse(text) as unknown) : null;

  if (!response.ok) {
    throw new ApiError(response.status, (parsed ?? {}) as Partial<ApiErrorBody>);
  }
  return parsed as T;
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body),
  patch: <T>(path: string, body?: unknown) => request<T>("PATCH", path, body),
  delete: <T>(path: string, body?: unknown) => request<T>("DELETE", path, body),
};

/** Traduz um erro qualquer em texto exibivel, sem vazar detalhe interno. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  if (error instanceof Error) {
    return "Nao foi possivel completar a operacao. Verifique sua conexao.";
  }
  return "Erro inesperado.";
}
