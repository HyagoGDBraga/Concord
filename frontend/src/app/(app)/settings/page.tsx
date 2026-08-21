"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError, api, errorMessage } from "@/lib/apiClient";
import { useSession } from "@/lib/session";
import { useTheme } from "@/lib/theme";
import { attachmentsApi, MAX_UPLOAD_BYTES } from "@/lib/chatApi";
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
      <AvatarSection />
      <AppearanceSection />
      <ProfileSection />
      <DataSection />
      <PasswordSection />
      <EmailSection />
      <SessionsSection />
      <DangerSection />
    </div>
  );
}

/* ---------------------------------------------------------------- avatar */

function AvatarSection() {
  const { user, refresh } = useSession();
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  async function escolher(evento: React.ChangeEvent<HTMLInputElement>) {
    const arquivo = evento.target.files?.[0];
    // Limpa o input: sem isso, escolher o MESMO arquivo de novo nao dispara
    // change e a pessoa acha que o botao quebrou.
    evento.target.value = "";
    if (!arquivo) {
      return;
    }

    // Verificacao no cliente e cortesia, nao seguranca — o servidor confere de
    // novo, e por conteudo. Aqui ela evita subir 40 MB para receber erro.
    if (arquivo.size > MAX_UPLOAD_BYTES) {
      setErro("A imagem precisa ter no máximo 5 MB.");
      return;
    }

    setEnviando(true);
    setErro(null);
    try {
      await attachmentsApi.uploadAvatar(arquivo);
      await refresh();
    } catch (err) {
      setErro(errorMessage(err));
    } finally {
      setEnviando(false);
    }
  }

  async function remover() {
    setEnviando(true);
    setErro(null);
    try {
      await attachmentsApi.removeAvatar();
      await refresh();
    } catch (err) {
      setErro(errorMessage(err));
    } finally {
      setEnviando(false);
    }
  }

  const iniciais = (user?.displayName ?? "?").slice(0, 2).toUpperCase();

  return (
    <Card
      title="Foto de perfil"
      description="PNG, JPEG, GIF ou WebP, até 5 MB."
    >
      <div className="flex flex-wrap items-center gap-5">
        <span className="flex h-20 w-20 items-center justify-center overflow-hidden rounded-full bg-elevated text-lg font-bold">
          {user?.avatarUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={user.avatarUrl}
              alt="Sua foto de perfil"
              className="h-full w-full object-cover"
            />
          ) : (
            iniciais
          )}
        </span>

        <div className="flex flex-col gap-2">
          <div className="flex gap-2">
            <Button
              variant="secondary"
              loading={enviando}
              onClick={() => inputRef.current?.click()}
            >
              {user?.avatarUrl ? "Trocar foto" : "Enviar foto"}
            </Button>
            {user?.avatarUrl && (
              <Button variant="secondary" loading={enviando} onClick={() => void remover()}>
                Remover
              </Button>
            )}
          </div>
          <p className="text-xs text-muted">
            A imagem fica visível para seus contatos e para os membros dos
            servidores em que você está.
          </p>
        </div>

        <input
          ref={inputRef}
          type="file"
          accept="image/png,image/jpeg,image/gif,image/webp"
          onChange={(evento) => void escolher(evento)}
          className="hidden"
        />
      </div>

      {erro && (
        <div className="mt-4">
          <Alert tone="error">{erro}</Alert>
        </div>
      )}
    </Card>
  );
}

/* ------------------------------------------------------------- aparencia */

const ROTULOS_DE_COR: Record<string, string> = {
  ink: "Fundo do chat",
  panel: "Barra de canais",
  elevated: "Superfícies",
  line: "Bordas",
  paper: "Texto",
  muted: "Texto secundário",
  accent: "Destaque",
  mint: "Sucesso / falando",
  coral: "Erro / sair",
};

function AppearanceSection() {
  const { theme, setTheme, palette, setPaletteColor, resetPalette } = useTheme();

  const temas = [
    { id: "classic" as const, nome: "Classic", desc: "Cinza-azul, o padrão" },
    { id: "terminal" as const, nome: "Terminal", desc: "Bitmap sobre grafite" },
    { id: "light" as const, nome: "Claro", desc: "Fundo claro, texto chumbo" },
    { id: "custom" as const, nome: "Personalizado", desc: "Você escolhe as cores" },
  ];

  return (
    <Card title="Aparência" description="A escolha fica salva neste navegador.">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {temas.map((opcao) => (
          <button
            key={opcao.id}
            type="button"
            onClick={() => setTheme(opcao.id)}
            className={`rounded border p-4 text-left transition ${
              theme === opcao.id
                ? "border-amber bg-ink"
                : "border-line bg-ink/40 hover:border-muted"
            }`}
          >
            <span className="display block text-sm">{opcao.nome}</span>
            <span className="mt-1 block text-xs text-muted">{opcao.desc}</span>
          </button>
        ))}
      </div>

      {theme === "custom" && (
        <div className="mt-6 border-t border-line pt-5">
          <div className="mb-3 flex items-center justify-between gap-3">
            <p className="text-sm font-semibold">Suas cores</p>
            <Button variant="secondary" onClick={resetPalette}>
              Restaurar padrão
            </Button>
          </div>

          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {(Object.keys(palette) as (keyof typeof palette)[]).map((chave) => (
              <label key={chave} className="flex items-center gap-3">
                {/* input type=color e o seletor nativo do sistema: nao precisa
                    de biblioteca e ja e acessivel por teclado. */}
                <input
                  type="color"
                  value={palette[chave]}
                  onChange={(evento) => setPaletteColor(chave, evento.target.value)}
                  className="h-9 w-12 cursor-pointer rounded border border-line bg-transparent"
                  aria-label={ROTULOS_DE_COR[chave] ?? chave}
                />
                <span className="flex min-w-0 flex-col">
                  <span className="truncate text-sm">
                    {ROTULOS_DE_COR[chave] ?? chave}
                  </span>
                  <span className="font-mono text-[11px] text-muted">
                    {palette[chave]}
                  </span>
                </span>
              </label>
            ))}
          </div>

          <p className="mt-4 text-xs text-muted">
            As cores valem só neste navegador e mudam na hora. Se algo ficar
            ilegível, use &quot;Restaurar padrão&quot;.
          </p>
        </div>
      )}
    </Card>
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
