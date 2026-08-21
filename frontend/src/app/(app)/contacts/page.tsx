"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { errorMessage } from "@/lib/apiClient";
import { contactsApi, conversationsApi } from "@/lib/chatApi";
import { useRealtime, useRealtimeEvent } from "@/lib/realtime";
import type { ContactsOverview } from "@/lib/types";
import {
  Alert,
  Badge,
  Button,
  Card,
  Field,
  Input,
  Spinner,
} from "@/components/ui";

export default function ContactsPage() {
  const router = useRouter();
  const [data, setData] = useState<ContactsOverview | null>(null);
  const [username, setUsername] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const { onlineUserIds } = useRealtime();

  const load = useCallback(async () => {
    try {
      setData(await contactsApi.overview());
    } catch (err) {
      setError(errorMessage(err));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  // Pedido recebido ou aceite do outro lado aparecem sem recarregar a pagina.
  useRealtimeEvent("CONTACT_REQUEST", () => void load());
  useRealtimeEvent("CONTACT_ACCEPTED", () => void load());

  async function run(action: () => Promise<unknown>, successMessage?: string) {
    setBusy(true);
    setError(null);
    setStatus(null);
    try {
      await action();
      if (successMessage) {
        setStatus(successMessage);
      }
      await load();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function openConversation(userId: string) {
    try {
      const { id } = await conversationsApi.open(userId);
      router.push(`/conversations/${id}`);
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  return (
    <div className="space-y-6">
      <Card
        title="Adicionar contato"
        description="Informe o nome de usuario exato. Nao existe busca por parte do nome — com cadastro aberto, isso permitiria varrer a base de usuarios."
      >
        <form
          className="flex items-end gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            void run(
              () => contactsApi.request(username.trim()),
              "Pedido enviado.",
            ).then(() => setUsername(""));
          }}
        >
          <div className="flex-1">
            <Field
              label="Nome de usuário ou e-mail"
              hint="Ex.: maria_silva ou maria@exemplo.com"
            >
              <Input
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                // O pattern aceita os dois formatos numa expressao so: quem
                // digita nao deveria precisar declarar o que esta digitando.
                pattern="([A-Za-z0-9_]{3,20}|[^@\s]+@[^@\s]+\.[^@\s]+)"
                required
                maxLength={120}
                placeholder="usuário ou e-mail"
              />
            </Field>
          </div>
          <Button type="submit" loading={busy}>
            Enviar pedido
          </Button>
        </form>
        {status && (
          <div className="mt-4">
            <Alert tone="success">{status}</Alert>
          </div>
        )}
        {error && (
          <div className="mt-4">
            <Alert tone="error">{error}</Alert>
          </div>
        )}
      </Card>

      {!data ? (
        <Spinner />
      ) : (
        <>
          {data.incoming.length > 0 && (
            <Card title="Pedidos recebidos">
              <ul className="divide-y divide-line">
                {data.incoming.map((request) => (
                  <li
                    key={request.id}
                    className="flex flex-wrap items-center gap-3 py-3"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="text-sm">{request.user.displayName}</p>
                      <p className="font-mono text-xs text-muted">
                        {request.user.username}
                      </p>
                    </div>
                    <Button
                      disabled={busy}
                      onClick={() => void run(() => contactsApi.accept(request.id))}
                    >
                      Aceitar
                    </Button>
                    <Button
                      variant="secondary"
                      disabled={busy}
                      onClick={() =>
                        void run(() => contactsApi.declineOrCancel(request.id))
                      }
                    >
                      Recusar
                    </Button>
                  </li>
                ))}
              </ul>
            </Card>
          )}

          {data.outgoing.length > 0 && (
            <Card title="Pedidos enviados">
              <ul className="divide-y divide-line">
                {data.outgoing.map((request) => (
                  <li
                    key={request.id}
                    className="flex flex-wrap items-center gap-3 py-3"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="font-mono text-sm text-muted">
                        {request.user.username}
                      </p>
                    </div>
                    <Badge>Aguardando</Badge>
                    <Button
                      variant="ghost"
                      disabled={busy}
                      onClick={() =>
                        void run(() => contactsApi.declineOrCancel(request.id))
                      }
                    >
                      Cancelar
                    </Button>
                  </li>
                ))}
              </ul>
            </Card>
          )}

          <Card title="Contatos">
            {data.contacts.length === 0 ? (
              <p className="text-sm text-muted">
                Nenhum contato ainda. Voce so consegue conversar com quem aceitou
                seu pedido.
              </p>
            ) : (
              <ul className="divide-y divide-line">
                {data.contacts.map((contact) => (
                  <li
                    key={contact.id}
                    className="flex flex-wrap items-center gap-3 py-3"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="flex items-center gap-2 text-sm">
                        {onlineUserIds.has(contact.user.id) && (
                          <span
                            aria-label="online"
                            title="Online"
                            className="inline-block h-2 w-2 rounded-full bg-mint"
                          />
                        )}
                        {contact.user.displayName}
                        {contact.blockedByMe && <Badge tone="bad">Bloqueado</Badge>}
                      </p>
                      <p className="font-mono text-xs text-muted">
                        {contact.user.username}
                      </p>
                    </div>
                    {!contact.blockedByMe && (
                      <Button
                        variant="secondary"
                        onClick={() => void openConversation(contact.user.id)}
                      >
                        Conversar
                      </Button>
                    )}
                    <Button
                      variant="ghost"
                      disabled={busy}
                      onClick={() =>
                        void run(() =>
                          contact.blockedByMe
                            ? contactsApi.unblock(contact.user.id)
                            : contactsApi.block(contact.user.id),
                        )
                      }
                    >
                      {contact.blockedByMe ? "Desbloquear" : "Bloquear"}
                    </Button>
                    <Button
                      variant="ghost"
                      disabled={busy}
                      onClick={() => {
                        if (
                          window.confirm(
                            `Remover ${contact.user.username} dos contatos? O historico da conversa permanece.`,
                          )
                        ) {
                          void run(() => contactsApi.remove(contact.user.id));
                        }
                      }}
                    >
                      Remover
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </>
      )}
    </div>
  );
}
