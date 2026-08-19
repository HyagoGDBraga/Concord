"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError, api, errorMessage } from "@/lib/apiClient";
import { useSession } from "@/lib/session";
import type { Me, SessionInfo } from "@/lib/types";
import {
  Alert,
  Badge,
  Button,
  Card,
  Field,
  Input,
  Spinner,
  Textarea,
} from "@/components/ui";

export default function SettingsPage() {
  return (
    <div className="space-y-6">
      <ProfileSection />
      <DataSection />
      <PasswordSection />
      <EmailSection />
      <SessionsSection />
      <DangerSection />
    </div>
  );
}

/* --------------------------------------------------------------- perfil */

function ProfileSection() {
  const { user, setUser } = useSession();
  const [displayName, setDisplayName] = useState(user?.displayName ?? "");
  const [bio, setBio] = useState(user?.bio ?? "");
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setStatus(null);
    setLoading(true);
    try {
      const updated = await api.patch<Me>("/users/me", { displayName, bio });
      setUser(updated);
      setStatus("Perfil atualizado.");
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card title="Perfil" description="Como voce aparece para outras pessoas.">
      <form onSubmit={submit} className="space-y-4">
        <Field label="Nome de exibicao">
          <Input
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            required
            maxLength={50}
          />
        </Field>
        <Field label="Bio" hint="Ate 200 caracteres.">
          <Textarea
            rows={3}
            value={bio}
            onChange={(e) => setBio(e.target.value)}
            maxLength={200}
          />
        </Field>
        {status && <Alert tone="success">{status}</Alert>}
        {error && <Alert tone="error">{error}</Alert>}
        <Button type="submit" loading={loading}>
          Salvar
        </Button>
      </form>
    </Card>
  );
}

/* ------------------------------------------------------- meus dados */

/**
 * Direito de acesso e portabilidade (Art. 18 da LGPD).
 *
 * O download acontece no navegador, sem intermediario: a resposta e convertida
 * em Blob e salva. Nao ha arquivo gerado no servidor que pudesse ficar
 * esquecido em disco.
 */
function DataSection() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function download() {
    setLoading(true);
    setError(null);
    try {
      const data = await api.get<unknown>("/users/me/export");
      const blob = new Blob([JSON.stringify(data, null, 2)], {
        type: "application/json",
      });
      const url = URL.createObjectURL(blob);

      const link = document.createElement("a");
      link.href = url;
      link.download = `concord-meus-dados-${new Date().toISOString().slice(0, 10)}.json`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card
      title="Meus dados"
      description="Baixe tudo o que o Concord guarda sobre voce, em JSON."
    >
      <p className="text-sm text-muted">
        O arquivo traz seu perfil, contatos, conversas, registros de chamada e o
        historico de aceites. Nao inclui sua senha, que e guardada apenas como
        hash e nao pode ser revertida.
      </p>
      <p className="mt-2 text-sm text-muted">
        Limite de um pedido por dia. O arquivo contem dados pessoais — guarde-o
        com cuidado.
      </p>
      {error && (
        <div className="mt-4">
          <Alert tone="error">{error}</Alert>
        </div>
      )}
      <div className="mt-4">
        <Button variant="secondary" loading={loading} onClick={() => void download()}>
          Baixar meus dados
        </Button>
      </div>
    </Card>
  );
}

/* ---------------------------------------------------------------- senha */

function PasswordSection() {
  const [currentPassword, setCurrent] = useState("");
  const [newPassword, setNew] = useState("");
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setStatus(null);
    setLoading(true);
    try {
      await api.post("/users/me/password", { currentPassword, newPassword });
      setCurrent("");
      setNew("");
      setStatus("Senha alterada. Os outros dispositivos foram desconectados.");
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card
      title="Senha"
      description="Trocar a senha encerra as outras sessoes, mas mantem esta."
    >
      <form onSubmit={submit} className="space-y-4">
        <Field label="Senha atual">
          <Input
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrent(e.target.value)}
            autoComplete="current-password"
            required
          />
        </Field>
        <Field label="Nova senha" hint="Minimo de 12 caracteres.">
          <Input
            type="password"
            value={newPassword}
            onChange={(e) => setNew(e.target.value)}
            autoComplete="new-password"
            required
            minLength={12}
            maxLength={128}
          />
        </Field>
        {status && <Alert tone="success">{status}</Alert>}
        {error && <Alert tone="error">{error}</Alert>}
        <Button type="submit" loading={loading}>
          Alterar senha
        </Button>
      </form>
    </Card>
  );
}

/* --------------------------------------------------------------- e-mail */

function EmailSection() {
  const { user } = useSession();
  const [currentPassword, setPassword] = useState("");
  const [newEmail, setNewEmail] = useState("");
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setStatus(null);
    setLoading(true);
    try {
      await api.post("/users/me/email", { currentPassword, newEmail });
      setPassword("");
      setNewEmail("");
      setStatus(
        "Se o endereco puder ser usado, enviamos um link de confirmacao a ele. O e-mail atual continua valendo ate la.",
      );
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card title="E-mail" description={`Atual: ${user?.email ?? "-"}`}>
      <form onSubmit={submit} className="space-y-4">
        <Field label="Novo e-mail">
          <Input
            type="email"
            value={newEmail}
            onChange={(e) => setNewEmail(e.target.value)}
            required
            maxLength={254}
          />
        </Field>
        <Field label="Senha atual">
          <Input
            type="password"
            value={currentPassword}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </Field>
        {status && <Alert tone="success">{status}</Alert>}
        {error && <Alert tone="error">{error}</Alert>}
        <Button type="submit" loading={loading}>
          Solicitar troca
        </Button>
      </form>
    </Card>
  );
}

/* ------------------------------------------------------------- sessoes */

function SessionsSection() {
  const [sessions, setSessions] = useState<SessionInfo[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setSessions(await api.get<SessionInfo[]>("/users/me/sessions"));
    } catch (err) {
      setError(errorMessage(err));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function revoke(id: string) {
    setBusy(true);
    try {
      await api.delete(`/users/me/sessions/${id}`);
      await load();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function revokeOthers() {
    setBusy(true);
    try {
      await api.delete("/users/me/sessions");
      await load();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card
      title="Dispositivos conectados"
      description="Se voce nao reconhecer um acesso, encerre-o e troque sua senha."
    >
      {error && <Alert tone="error">{error}</Alert>}
      {!sessions ? (
        <Spinner />
      ) : (
        <>
          <ul className="divide-y divide-line">
            {sessions.map((session) => (
              <li
                key={session.id}
                className="flex flex-wrap items-center gap-3 py-3"
              >
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm">
                    {session.userAgent ?? "Dispositivo desconhecido"}
                  </p>
                  <p className="mt-0.5 font-mono text-xs text-muted">
                    {session.ipAddress ?? "IP desconhecido"} ·{" "}
                    {new Date(session.lastAccessedAt).toLocaleString("pt-BR")}
                  </p>
                </div>
                {session.current ? (
                  <Badge tone="good">Esta sessao</Badge>
                ) : (
                  <Button
                    variant="secondary"
                    disabled={busy}
                    onClick={() => revoke(session.id)}
                  >
                    Encerrar
                  </Button>
                )}
              </li>
            ))}
          </ul>
          {sessions.length > 1 && (
            <div className="mt-4">
              <Button variant="secondary" disabled={busy} onClick={revokeOthers}>
                Encerrar todas as outras
              </Button>
            </div>
          )}
        </>
      )}
    </Card>
  );
}

/* ------------------------------------------------------------- exclusao */

function DangerSection() {
  const router = useRouter();
  const { setUser } = useSession();
  const [currentPassword, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await api.delete("/users/me", { currentPassword, confirmation });
      setUser(null);
      router.replace("/login");
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors.confirmation) {
        setError(err.fieldErrors.confirmation);
      } else {
        setError(errorMessage(err));
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card title="Excluir conta">
      <p className="text-sm text-muted">
        Seus dados pessoais sao removidos e a conta deixa de existir. As
        mensagens que voce enviou permanecem nas conversas dos destinatarios,
        exibidas como &quot;Usuario removido&quot; — elas tambem sao o historico
        deles.
      </p>
      {!open ? (
        <div className="mt-4">
          <Button variant="danger" onClick={() => setOpen(true)}>
            Quero excluir minha conta
          </Button>
        </div>
      ) : (
        <form onSubmit={submit} className="mt-4 space-y-4">
          <Field label="Senha atual">
            <Input
              type="password"
              value={currentPassword}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
          </Field>
          <Field label="Digite EXCLUIR para confirmar">
            <Input
              value={confirmation}
              onChange={(e) => setConfirmation(e.target.value)}
              required
            />
          </Field>
          {error && <Alert tone="error">{error}</Alert>}
          <div className="flex gap-3">
            <Button type="submit" variant="danger" loading={loading}>
              Excluir definitivamente
            </Button>
            <Button
              type="button"
              variant="ghost"
              onClick={() => setOpen(false)}
            >
              Cancelar
            </Button>
          </div>
        </form>
      )}
    </Card>
  );
}
