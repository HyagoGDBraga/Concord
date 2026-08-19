"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { errorMessage } from "@/lib/apiClient";
import { conversationsApi, messagesApi } from "@/lib/chatApi";
import {
  useRealtime,
  useRealtimeEvent,
  type ReadReceipt,
  type TypingEvent,
} from "@/lib/realtime";
import { useCall } from "@/lib/callContext";
import { useSession } from "@/lib/session";
import type { ChatMessage, ConversationSummary } from "@/lib/types";
import { Alert, Badge, Button, Card, Input, Spinner } from "@/components/ui";

/**
 * Tela de conversa.
 *
 * As mensagens chegam pelo WebSocket. O endpoint `/messages/since` continua
 * existindo e cumpre outro papel: quando a conexao cai e volta, ele preenche a
 * lacuna do periodo desconectado — sem ele, as mensagens desse intervalo so
 * apareceriam ao recarregar a pagina.
 *
 * O envio continua por POST. O WebSocket entrega, nao escreve.
 */
const RECONNECT_POLL_MS = 8000;
const TYPING_TIMEOUT_MS = 3000;

export default function ConversationPage() {
  const params = useParams<{ id: string }>();
  const conversationId = params.id;
  const { user } = useSession();

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [summary, setSummary] = useState<ConversationSummary | null>(null);
  const [olderCursor, setOlderCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [draft, setDraft] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [peerTyping, setPeerTyping] = useState(false);

  const { connected, onlineUserIds, sendTyping } = useRealtime();
  const { start: startCall, phase: callPhase } = useCall();

  // Fora do estado de propósito: muda a cada consulta e nao deve provocar
  // renderizacao nem recriar o intervalo.
  const latestCursor = useRef<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const typingSentAt = useRef(0);
  const typingTimer = useRef<number | null>(null);

  const scrollToBottom = useCallback(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, []);

  /* --------------------------------------------------- carga inicial */

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const [page, list] = await Promise.all([
          conversationsApi.history(conversationId),
          conversationsApi.list(),
        ]);
        if (cancelled) {
          return;
        }
        setMessages(page.items);
        setOlderCursor(page.cursor);
        setHasMore(page.hasMore);
        latestCursor.current = page.latestCursor;
        setSummary(list.find((item) => item.id === conversationId) ?? null);

        const last = page.items[page.items.length - 1];
        if (last) {
          void conversationsApi.markRead(conversationId, last.id).catch(() => {
            // Marcar como lida e melhoria de experiencia; falhar aqui nao deve
            // interromper a leitura da conversa.
          });
        }
      } catch (err) {
        if (!cancelled) {
          setError(errorMessage(err));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, [conversationId]);

  /* -------------------------------------------------------- tempo real */

  const applyIncoming = useCallback(
    (message: ChatMessage) => {
      if (message.conversationId !== conversationId) {
        return;
      }
      setMessages((current) => mergeById(current, [message]));
      void conversationsApi.markRead(conversationId, message.id).catch(() => {});
    },
    [conversationId],
  );

  useRealtimeEvent<ChatMessage>("MESSAGE_CREATED", applyIncoming);

  useRealtimeEvent<ChatMessage>("MESSAGE_UPDATED", (message) => {
    if (message.conversationId !== conversationId) {
      return;
    }
    setMessages((current) =>
      current.map((item) => (item.id === message.id ? message : item)),
    );
  });

  useRealtimeEvent<ChatMessage>("MESSAGE_DELETED", (message) => {
    if (message.conversationId !== conversationId) {
      return;
    }
    setMessages((current) =>
      current.map((item) =>
        item.id === message.id
          ? { ...item, body: undefined, deleted: true }
          : item,
      ),
    );
  });

  useRealtimeEvent<TypingEvent>("TYPING", (event) => {
    if (event.conversationId !== conversationId) {
      return;
    }
    setPeerTyping(event.typing);
    if (event.typing) {
      // O sinal expira sozinho: se o outro lado fechar a aba sem enviar o
      // "parou de digitar", o indicador nao fica preso na tela.
      window.setTimeout(() => setPeerTyping(false), TYPING_TIMEOUT_MS + 1000);
    }
  });

  useRealtimeEvent<ReadReceipt>("MESSAGE_READ", () => {
    // Confirmacao de leitura ja chega; a exibicao de "lido" por mensagem entra
    // junto com o restante do refino visual.
  });

  /* ---------------------------------------- preenchimento apos reconexao */

  useEffect(() => {
    if (connected) {
      return;
    }
    const timer = window.setInterval(async () => {
      if (!latestCursor.current || document.hidden) {
        return;
      }
      try {
        const page = await conversationsApi.since(
          conversationId,
          latestCursor.current,
        );
        latestCursor.current = page.latestCursor;
        if (page.items.length > 0) {
          setMessages((current) => mergeById(current, page.items));
        }
      } catch {
        // A proxima tentativa acontece em segundos.
      }
    }, RECONNECT_POLL_MS);

    return () => window.clearInterval(timer);
  }, [conversationId, connected]);

  useEffect(() => {
    scrollToBottom();
  }, [messages.length, scrollToBottom]);

  /* ----------------------------------------------------------- acoes */

  async function loadOlder() {
    if (!olderCursor) {
      return;
    }
    try {
      const page = await conversationsApi.history(conversationId, olderCursor);
      setMessages((current) => mergeById(page.items, current));
      setOlderCursor(page.cursor);
      setHasMore(page.hasMore);
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  async function send(event: React.FormEvent) {
    event.preventDefault();
    const body = draft.trim();
    if (!body) {
      return;
    }
    setSending(true);
    setError(null);
    try {
      const created = await conversationsApi.send(
        conversationId,
        body,
        crypto.randomUUID(),
      );
      setMessages((current) => mergeById(current, [created]));
      setDraft("");
      notifyTyping(false);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSending(false);
    }
  }

  async function remove(messageId: string) {
    if (!window.confirm("Apagar esta mensagem?")) {
      return;
    }
    try {
      await messagesApi.remove(messageId);
      setMessages((current) =>
        current.map((message) =>
          message.id === messageId
            ? { ...message, body: undefined, deleted: true }
            : message,
        ),
      );
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  /**
   * Publica o sinal de digitacao, no maximo uma vez por segundo.
   *
   * Sem esse limite, seria um frame por tecla — barato individualmente, mas
   * desnecessario, e o indicador nao fica mais preciso por isso.
   */
  const notifyTyping = useCallback(
    (typing: boolean) => {
      const now = Date.now();
      if (typing && now - typingSentAt.current < 1000) {
        return;
      }
      typingSentAt.current = now;
      sendTyping(conversationId, typing);

      if (typingTimer.current) {
        window.clearTimeout(typingTimer.current);
      }
      if (typing) {
        typingTimer.current = window.setTimeout(
          () => sendTyping(conversationId, false),
          TYPING_TIMEOUT_MS,
        );
      }
    },
    [conversationId, sendTyping],
  );

  /* ------------------------------------------------------------ tela */

  if (loading) {
    return <Spinner label="Carregando conversa" />;
  }

  const bloqueado = summary?.peerBlocked ?? false;
  const semContato = summary ? !summary.stillContacts : false;

  return (
    <Card
      title={summary?.peer.displayName ?? "Conversa"}
      description={
        summary
          ? `@${summary.peer.username}${
              onlineUserIds.has(summary.peer.id) ? " · online" : ""
            }`
          : undefined
      }
    >
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <Link href="/conversations" className="text-sm text-muted hover:text-paper">
          ← Todas as conversas
        </Link>
        {summary && !bloqueado && !semContato && (
          <div className="flex gap-2">
            <Button
              variant="secondary"
              disabled={callPhase !== "idle" || !onlineUserIds.has(summary.peer.id)}
              onClick={() => void startCall(conversationId, false)}
            >
              Ligar
            </Button>
            <Button
              variant="secondary"
              disabled={callPhase !== "idle" || !onlineUserIds.has(summary.peer.id)}
              onClick={() => void startCall(conversationId, true)}
            >
              Video
            </Button>
          </div>
        )}
      </div>

      {hasMore && (
        <div className="mb-4 text-center">
          <Button variant="secondary" onClick={() => void loadOlder()}>
            Carregar mensagens anteriores
          </Button>
        </div>
      )}

      <ul className="max-h-[55vh] space-y-3 overflow-y-auto pr-1">
        {messages.map((message) => {
          const mine = message.senderId === user?.id;
          return (
            <li
              key={message.id}
              className={`flex ${mine ? "justify-end" : "justify-start"}`}
            >
              <div
                className={`max-w-[80%] rounded border px-3 py-2 ${
                  mine ? "border-amber/40 bg-ink" : "border-line bg-ink/60"
                }`}
              >
                {message.deleted ? (
                  <p className="text-sm italic text-muted">Mensagem apagada</p>
                ) : (
                  <p className="whitespace-pre-wrap break-words text-sm">
                    {message.body}
                  </p>
                )}
                <p className="mt-1 flex items-center gap-2 font-mono text-[11px] text-muted">
                  {new Date(message.createdAt).toLocaleTimeString("pt-BR", {
                    hour: "2-digit",
                    minute: "2-digit",
                  })}
                  {message.editedAt && <span>editada</span>}
                  {mine && !message.deleted && (
                    <button
                      type="button"
                      onClick={() => void remove(message.id)}
                      className="text-muted underline hover:text-coral"
                    >
                      apagar
                    </button>
                  )}
                </p>
              </div>
            </li>
          );
        })}
        <div ref={bottomRef} />
      </ul>

      {peerTyping && (
        <p className="mt-3 font-mono text-xs text-muted">
          {summary?.peer.displayName ?? "A outra pessoa"} esta digitando…
        </p>
      )}

      {error && (
        <div className="mt-4">
          <Alert tone="error">{error}</Alert>
        </div>
      )}

      {bloqueado || semContato ? (
        <div className="mt-5">
          <Alert tone="info">
            {bloqueado ? (
              <>
                Voce bloqueou esta pessoa. <Badge tone="bad">Bloqueado</Badge>{" "}
                Desbloqueie em Contatos para voltar a conversar.
              </>
            ) : (
              <>
                Voces nao sao mais contatos. O historico continua aqui, mas novas
                mensagens estao bloqueadas.
              </>
            )}
          </Alert>
        </div>
      ) : (
        <form onSubmit={send} className="mt-5 flex items-end gap-3">
          <div className="flex-1">
            <Input
              value={draft}
              onChange={(e) => {
                setDraft(e.target.value);
                notifyTyping(e.target.value.length > 0);
              }}
              placeholder="Escreva uma mensagem"
              maxLength={4000}
              aria-label="Mensagem"
            />
          </div>
          <Button type="submit" loading={sending}>
            Enviar
          </Button>
        </form>
      )}
    </Card>
  );
}

/**
 * Junta duas listas de mensagens sem duplicar e mantendo a ordem cronologica.
 *
 * Necessario porque a mesma mensagem pode chegar por dois caminhos: a resposta
 * do proprio envio e a consulta periodica seguinte.
 */
function mergeById(a: ChatMessage[], b: ChatMessage[]): ChatMessage[] {
  const byId = new Map<string, ChatMessage>();
  for (const message of [...a, ...b]) {
    byId.set(message.id, message);
  }
  return [...byId.values()].sort((left, right) =>
    left.createdAt === right.createdAt
      ? left.id.localeCompare(right.id)
      : left.createdAt.localeCompare(right.createdAt),
  );
}
