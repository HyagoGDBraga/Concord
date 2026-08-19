"use client";

import { useCallback, useEffect, useState } from "react";
import { api, errorMessage } from "@/lib/apiClient";
import { useSession } from "@/lib/session";
import type { AdminUser, AppSettings, Page, UserStatus } from "@/lib/types";
import {
  Alert,
  Badge,
  Button,
  Card,
  Field,
  Input,
  Spinner,
} from "@/components/ui";

const STATUS_TONE: Record<UserStatus, "good" | "warn" | "bad" | "neutral"> = {
  ACTIVE: "good",
  PENDING_VERIFICATION: "warn",
  DISABLED: "bad",
  DELETED: "neutral",
};

export default function AdminUsersPage() {
  const { user: admin } = useSession();
  const [query, setQuery] = useState("");
  const [data, setData] = useState<Page<AdminUser> | null>(null);
  const [settings, setSettings] = useState<AppSettings | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async (search: string) => {
    try {
      const params = new URLSearchParams({ size: "20" });
      if (search.trim()) {
        params.set("query", search.trim());
      }
      setData(await api.get<Page<AdminUser>>(`/admin/users?${params}`));
    } catch (err) {
      setError(errorMessage(err));
    }
  }, []);

  useEffect(() => {
    void load("");
    api
      .get<AppSettings>("/admin/settings")
      .then(setSettings)
      .catch((err) => setError(errorMessage(err)));
  }, [load]);

  async function act(action: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await action();
      await load(query);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  function disable(target: AdminUser) {
    const reason = window.prompt(
      `Motivo da desativacao de ${target.username} (obrigatorio):`,
    );
    if (!reason || reason.trim().length < 3) {
      return;
    }
    void act(() =>
      api.post(`/admin/users/${target.id}/disable`, { reason: reason.trim() }),
    );
  }

  function remove(target: AdminUser) {
    const reason = window.prompt(
      `Excluir ${target.username} anonimiza a conta e nao pode ser desfeito.\nMotivo (obrigatorio):`,
    );
    if (!reason || reason.trim().length < 3) {
      return;
    }
    void act(() =>
      api.delete(`/admin/users/${target.id}`, { reason: reason.trim() }),
    );
  }

  return (
    <div className="space-y-6">
      <Card
        title="Cadastro"
        description="Fecha o cadastro publico sem reiniciar o sistema."
      >
        {!settings ? (
          <Spinner />
        ) : (
          <div className="flex items-center gap-4">
            <Badge tone={settings.registrationOpen ? "good" : "bad"}>
              {settings.registrationOpen ? "Aberto" : "Fechado"}
            </Badge>
            <Button
              variant="secondary"
              disabled={busy}
              onClick={() =>
                void act(async () => {
                  const updated = await api.patch<AppSettings>(
                    "/admin/settings",
                    { registrationOpen: !settings.registrationOpen },
                  );
                  setSettings(updated);
                })
              }
            >
              {settings.registrationOpen ? "Fechar cadastro" : "Abrir cadastro"}
            </Button>
          </div>
        )}
      </Card>

      <Card title="Usuarios">
        <form
          className="mb-5 flex items-end gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            void load(query);
          }}
        >
          <div className="flex-1">
            <Field label="Buscar">
              <Input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="usuario, e-mail ou nome"
              />
            </Field>
          </div>
          <Button type="submit" variant="secondary">
            Buscar
          </Button>
        </form>

        {error && <Alert tone="error">{error}</Alert>}

        {!data ? (
          <Spinner />
        ) : data.items.length === 0 ? (
          <p className="text-sm text-muted">Nenhum usuario encontrado.</p>
        ) : (
          <ul className="divide-y divide-line">
            {data.items.map((item) => (
              <li key={item.id} className="flex flex-wrap items-center gap-3 py-4">
                <div className="min-w-0 flex-1">
                  <p className="flex flex-wrap items-center gap-2 text-sm">
                    <span className="font-mono">{item.username}</span>
                    <Badge tone={STATUS_TONE[item.status]}>{item.status}</Badge>
                    {item.role === "ADMIN" && <Badge tone="warn">ADMIN</Badge>}
                    {item.temporarilyLocked && (
                      <Badge tone="bad">Bloqueio temporario</Badge>
                    )}
                  </p>
                  <p className="mt-1 font-mono text-xs text-muted">
                    {item.email ?? "sem e-mail"} · criado em{" "}
                    {new Date(item.createdAt).toLocaleDateString("pt-BR")}
                    {item.disabledReason && ` · motivo: ${item.disabledReason}`}
                  </p>
                </div>

                {item.status !== "DELETED" && item.id !== admin?.id && (
                  <div className="flex flex-wrap gap-2">
                    {item.status === "DISABLED" ? (
                      <Button
                        variant="secondary"
                        disabled={busy}
                        onClick={() =>
                          void act(() =>
                            api.post(`/admin/users/${item.id}/enable`),
                          )
                        }
                      >
                        Reativar
                      </Button>
                    ) : (
                      <Button
                        variant="secondary"
                        disabled={busy}
                        onClick={() => disable(item)}
                      >
                        Desativar
                      </Button>
                    )}
                    <Button
                      variant="secondary"
                      disabled={busy}
                      onClick={() =>
                        void act(() =>
                          api.post(`/admin/users/${item.id}/sessions/revoke`),
                        )
                      }
                    >
                      Encerrar sessoes
                    </Button>
                    <Button
                      variant="danger"
                      disabled={busy}
                      onClick={() => remove(item)}
                    >
                      Excluir
                    </Button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
