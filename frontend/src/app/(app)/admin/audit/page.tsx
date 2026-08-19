"use client";

import { useCallback, useEffect, useState } from "react";
import { api, errorMessage } from "@/lib/apiClient";
import type { AuditCategory, AuditEntry, Page } from "@/lib/types";
import { Alert, Badge, Button, Card, Spinner } from "@/components/ui";

const CATEGORIES: Array<AuditCategory | "TODAS"> = [
  "TODAS",
  "SECURITY",
  "ADMIN",
  "PRIVACY",
];

export default function AdminAuditPage() {
  const [category, setCategory] = useState<AuditCategory | "TODAS">("TODAS");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<AuditEntry> | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const params = new URLSearchParams({
        page: String(page),
        size: "50",
      });
      if (category !== "TODAS") {
        params.set("category", category);
      }
      setData(await api.get<Page<AuditEntry>>(`/admin/audit?${params}`));
    } catch (err) {
      setError(errorMessage(err));
    }
  }, [category, page]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Card
      title="Auditoria"
      description="Eventos de seguranca, acoes administrativas e exercicio de direitos do titular. Nenhum conteudo de conversa e registrado aqui."
    >
      <div className="mb-5 flex flex-wrap gap-2">
        {CATEGORIES.map((option) => (
          <Button
            key={option}
            variant={option === category ? "primary" : "secondary"}
            onClick={() => {
              setCategory(option);
              setPage(0);
            }}
          >
            {option}
          </Button>
        ))}
      </div>

      {error && <Alert tone="error">{error}</Alert>}

      {!data ? (
        <Spinner />
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-line font-mono text-xs uppercase tracking-wider text-muted">
                  <th className="py-2 pr-4">Quando</th>
                  <th className="py-2 pr-4">Categoria</th>
                  <th className="py-2 pr-4">Acao</th>
                  <th className="py-2 pr-4">Resultado</th>
                  <th className="py-2 pr-4">Ator</th>
                  <th className="py-2">IP</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((entry) => (
                  <tr key={entry.id} className="border-b border-line/50">
                    <td className="py-2 pr-4 font-mono text-xs text-muted">
                      {new Date(entry.createdAt).toLocaleString("pt-BR")}
                    </td>
                    <td className="py-2 pr-4">
                      <Badge>{entry.category}</Badge>
                    </td>
                    <td className="py-2 pr-4 font-mono text-xs">
                      {entry.action}
                    </td>
                    <td className="py-2 pr-4">
                      <Badge
                        tone={
                          entry.outcome === "SUCCESS"
                            ? "good"
                            : entry.outcome === "DENIED"
                              ? "warn"
                              : "bad"
                        }
                      >
                        {entry.outcome}
                      </Badge>
                    </td>
                    <td className="py-2 pr-4 font-mono text-xs">
                      {entry.actorLabel ?? "—"}
                    </td>
                    <td className="py-2 font-mono text-xs text-muted">
                      {entry.ipAddress ?? "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="mt-5 flex items-center justify-between text-sm text-muted">
            <span className="font-mono text-xs">
              {data.totalItems} eventos · pagina {data.page + 1} de{" "}
              {Math.max(data.totalPages, 1)}
            </span>
            <div className="flex gap-2">
              <Button
                variant="secondary"
                disabled={data.page === 0}
                onClick={() => setPage((p) => Math.max(p - 1, 0))}
              >
                Anterior
              </Button>
              <Button
                variant="secondary"
                disabled={data.page + 1 >= data.totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Proxima
              </Button>
            </div>
          </div>
        </>
      )}
    </Card>
  );
}
