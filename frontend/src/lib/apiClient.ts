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
  return match?.[1] === undefined ? null : decodeURIComponent(match[1]);
}

function clearCsrfToken(): void {
  if (typeof document !== "undefined") {
    document.cookie = "XSRF-TOKEN=; Max-Age=0; Path=/";
  }
}

const MUTATING_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

/** Garante que a primeira mutacao tambem tenha o cookie publico do CSRF. */
async function ensureCsrfToken(): Promise<string | null> {
  const existing = readCsrfToken();
  if (existing) {
    return existing;
  }

  try {
    // Mesmo uma resposta 401 materializa o cookie XSRF-TOKEN no backend.
    await fetch(`${config.apiUrl}/auth/me`, {
      method: "GET",
      headers: { Accept: "application/json" },
      credentials: "same-origin",
    });
  } catch {
    // A mutacao seguinte devolve o erro real se a API estiver indisponivel.
  }

  return readCsrfToken();
}

/** FormData vai como esta; o resto vira JSON. */
function serializar(body: unknown): BodyInit | undefined {
  if (body === undefined) {
    return undefined;
  }
  if (body instanceof FormData) {
    return body;
  }
  return JSON.stringify(body);
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };

  // FormData fica de fora: o navegador precisa gerar o boundary do multipart
  // sozinho, e definir o cabecalho na mao produz um corpo que o servidor nao
  // consegue separar em partes.
  const ehFormulario = body instanceof FormData;

  if (body !== undefined && !ehFormulario) {
    headers["Content-Type"] = "application/json";
  }
  if (MUTATING_METHODS.has(method)) {
    const csrf = await ensureCsrfToken();
    if (csrf) {
      headers["X-XSRF-TOKEN"] = csrf;
    }
  }

  let response = await fetch(`${config.apiUrl}${path}`, {
      method,
      headers,
      // Mesma origem via Caddy: o cookie de sessao viaja sem CORS.
      credentials: "same-origin",
      body: serializar(body),
    });

  // Um cookie CSRF antigo pode sobreviver a reinícios do ambiente. Renova-o
  // uma vez quando o backend rejeitar a mutacao, antes de expor o 403.
  if (response.status === 403 && MUTATING_METHODS.has(method)) {
    clearCsrfToken();
    const csrf = await ensureCsrfToken();
    const retryHeaders = { ...headers };
    if (csrf) {
      retryHeaders["X-XSRF-TOKEN"] = csrf;
    } else {
      delete retryHeaders["X-XSRF-TOKEN"];
    }
    response = await fetch(`${config.apiUrl}${path}`, {
      method,
      headers: retryHeaders,
      credentials: "same-origin",
      body: serializar(body),
    });
  }

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
  /**
   * Envio de formulario multipart (upload de arquivo).
   *
   * Nao define Content-Type de proposito: o navegador precisa gerar o boundary
   * do multipart. Definir o cabecalho na mao quebra a separacao das partes no
   * servidor. O token CSRF continua indo no header, como nas demais mutacoes.
   */
  postForm: <T>(path: string, body: FormData): Promise<T> =>
    request<T>("POST", path, body),

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
