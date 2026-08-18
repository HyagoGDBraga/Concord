"use client";

import { useCallback, useEffect, useState } from "react";
import { config } from "@/lib/config";

/**
 * Painel de diagnostico da Fase 1.
 *
 * Esta pagina existe para provar, visualmente, que a cadeia inteira esta ligada:
 * navegador -> Caddy -> Spring Boot -> PostgreSQL. Ela sera substituida pela
 * tela de login na Fase 2.
 *
 * O diagnostico e feito com uma unica requisicao a /api/actuator/health, e o
 * estado de cada elo e deduzido do resultado — inclusive de COMO a requisicao
 * falhou, que e o que diferencia "Caddy fora do ar" de "backend fora do ar".
 */

type LinkStatus = "checking" | "up" | "down" | "unknown";

interface ChainLink {
  id: string;
  label: string;
  detail: string;
  status: LinkStatus;
}

interface HealthPayload {
  status?: string;
  components?: Record<string, { status?: string }>;
}

const INITIAL_CHAIN: ChainLink[] = [
  { id: "browser", label: "Navegador", detail: "Next.js 15 · React 19", status: "up" },
  { id: "caddy", label: "Caddy", detail: "proxy reverso · porta 80", status: "checking" },
  { id: "backend", label: "Spring Boot", detail: "Java 21 · /api", status: "checking" },
  { id: "postgres", label: "PostgreSQL", detail: "16 · schema concord", status: "checking" },
];

const STATUS_STYLE: Record<LinkStatus, { dot: string; text: string; label: string }> = {
  checking: { dot: "bg-amber", text: "text-amber", label: "verificando" },
  up: { dot: "bg-mint", text: "text-mint", label: "no ar" },
  down: { dot: "bg-coral", text: "text-coral", label: "fora do ar" },
  unknown: { dot: "bg-muted", text: "text-muted", label: "sem informacao" },
};

export default function DiagnosticsPage() {
  const [chain, setChain] = useState<ChainLink[]>(INITIAL_CHAIN);
  const [checkedAt, setCheckedAt] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [running, setRunning] = useState(false);

  const setStatus = useCallback((id: string, status: LinkStatus) => {
    setChain((current) =>
      current.map((link) => (link.id === id ? { ...link, status } : link)),
    );
  }, []);

  const runCheck = useCallback(async () => {
    setRunning(true);
    setNote(null);
    setChain(INITIAL_CHAIN);

    try {
      const response = await fetch(`${config.apiUrl}/actuator/health`, {
        cache: "no-store",
      });

      // Houve resposta HTTP: quem respondeu foi o proxy, entao o Caddy esta de pe.
      setStatus("caddy", "up");

      if (!response.ok) {
        // 502/503 vem do Caddy quando o upstream nao responde.
        setStatus("backend", "down");
        setStatus("postgres", "unknown");
        setNote(
          `O proxy respondeu ${response.status}, mas o backend nao. Veja os logs com: docker compose logs -f backend`,
        );
        return;
      }

      const payload = (await response.json()) as HealthPayload;
      setStatus("backend", payload.status === "UP" ? "up" : "down");

      const dbStatus = payload.components?.db?.status;
      if (dbStatus) {
        setStatus("postgres", dbStatus === "UP" ? "up" : "down");
      } else {
        // Sem show-details (perfil de producao), o detalhe por componente nao e
        // exposto. O status agregado ja contempla o banco, mas o painel nao deve
        // afirmar mais do que sabe.
        setStatus("postgres", payload.status === "UP" ? "up" : "unknown");
        setNote(
          "O backend nao expoe o detalhe por componente neste perfil. O estado do banco foi deduzido do status agregado.",
        );
      }
    } catch {
      // Falha de rede antes de qualquer resposta: nada atendeu na porta 80.
      setStatus("caddy", "down");
      setStatus("backend", "unknown");
      setStatus("postgres", "unknown");
      setNote(
        "Nenhuma resposta na porta 80. Confirme se os containers estao rodando: docker compose ps",
      );
    } finally {
      setCheckedAt(
        new Intl.DateTimeFormat("pt-BR", { timeStyle: "medium" }).format(new Date()),
      );
      setRunning(false);
    }
  }, [setStatus]);

  useEffect(() => {
    void runCheck();
  }, [runCheck]);

  return (
    <main className="mx-auto flex min-h-dvh max-w-3xl flex-col justify-center px-6 py-16">
      <header className="mb-12">
        <p className="font-mono text-xs uppercase tracking-[0.25em] text-muted">
          Fase 1 · fundacao
        </p>
        <h1 className="mt-3 text-4xl font-semibold tracking-tight">Concord</h1>
        <p className="mt-3 max-w-lg text-sm leading-relaxed text-muted">
          Cadeia de sinal do ambiente de desenvolvimento. Cada elo abaixo precisa
          estar no ar antes da Fase 2 comecar.
        </p>
      </header>

      <ol className="space-y-0" aria-label="Estado dos servicos">
        {chain.map((link, index) => {
          const style = STATUS_STYLE[link.status];
          const isLast = index === chain.length - 1;
          const pulsing = link.status === "checking";

          return (
            <li key={link.id}>
              <div className="flex items-center gap-4 rounded border border-line bg-panel px-5 py-4">
                <span
                  className={`size-2.5 shrink-0 rounded-full ${style.dot}`}
                  aria-hidden="true"
                />
                <div className="min-w-0 flex-1">
                  <p className="font-mono text-sm text-paper">{link.label}</p>
                  <p className="mt-0.5 font-mono text-xs text-muted">{link.detail}</p>
                </div>
                <span className={`font-mono text-xs ${style.text}`}>
                  {style.label}
                </span>
              </div>

              {!isLast && (
                <div className="ml-[1.85rem] flex h-6 items-center" aria-hidden="true">
                  <span
                    className={`relative block h-px w-full overflow-hidden bg-line ${
                      pulsing ? "link-pulse" : ""
                    }`}
                  />
                </div>
              )}
            </li>
          );
        })}
      </ol>

      {note && (
        <p
          role="status"
          className="mt-8 rounded border border-line bg-panel px-5 py-4 font-mono text-xs leading-relaxed text-amber"
        >
          {note}
        </p>
      )}

      <div className="mt-10 flex items-center justify-between gap-4 border-t border-line pt-5">
        <p className="font-mono text-xs text-muted">
          {checkedAt ? `ultima verificacao ${checkedAt}` : "verificando..."}
        </p>
        <button
          type="button"
          onClick={() => void runCheck()}
          disabled={running}
          className="rounded border border-line px-4 py-2 font-mono text-xs text-paper transition-colors hover:border-amber hover:text-amber disabled:cursor-not-allowed disabled:opacity-40"
        >
          Verificar de novo
        </button>
      </div>
    </main>
  );
}
