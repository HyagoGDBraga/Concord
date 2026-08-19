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
