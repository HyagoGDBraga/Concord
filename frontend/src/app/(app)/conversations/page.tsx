"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "@/lib/apiClient";
import { conversationsApi } from "@/lib/chatApi";
import { useRealtime, useRealtimeEvent } from "@/lib/realtime";
import type { ChatMessage, ConversationSummary } from "@/lib/types";
import { Alert, Badge, Card, Spinner } from "@/components/ui";

export default function ConversationsPage() {
  const [items, setItems] = useState<ConversationSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { connected, onlineUserIds } = useRealtime();

  const load = useCallback(async () => {
    try {
      setItems(await conversationsApi.list());
    } catch (err) {
      setError(errorMessage(err));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  // Mensagem nova em qualquer conversa reordena a lista e atualiza a previa.
  useRealtimeEvent<ChatMessage>("MESSAGE_CREATED", () => {
    void load();
  });

  // Rede de seguranca para quando a conexao cai: enquanto reconecta, volta a
  // consultar o servidor. Assim que o tempo real retorna, o intervalo para.
  useEffect(() => {
    if (connected) {
      return;
    }
    const timer = window.setInterval(() => void load(), 15_000);
    return () => window.clearInterval(timer);
  }, [connected, load]);

  return (
    <Card title="Conversas">
      {error && <Alert tone="error">{error}</Alert>}
      {!items ? (
        <Spinner />
      ) : items.length === 0 ? (
        <p className="text-sm text-muted">
          Nenhuma conversa ainda. Comece por{" "}
          <Link href="/contacts" className="text-amber hover:brightness-110">
            Contatos
          </Link>
          .
        </p>
      ) : (
        <ul className="divide-y divide-line">
          {items.map((conversation) => (
            <li key={conversation.id}>
              <Link
                href={`/conversations/${conversation.id}`}
                className="flex items-center gap-3 py-3 hover:bg-ink/40"
              >
                <div className="min-w-0 flex-1">
                  <p className="flex items-center gap-2 text-sm">
                    {onlineUserIds.has(conversation.peer.id) && (
                      <span
                        aria-label="online"
                        title="Online"
                        className="inline-block h-2 w-2 rounded-full bg-mint"
                      />
                    )}
                    {conversation.peer.displayName}
                    {conversation.peerBlocked && <Badge tone="bad">Bloqueado</Badge>}
                    {!conversation.stillContacts && !conversation.peerBlocked && (
                      <Badge tone="warn">Sem contato</Badge>
                    )}
                  </p>
                  <p className="mt-0.5 truncate text-xs text-muted">
                    {conversation.lastMessagePreview ?? "Sem mensagens"}
                  </p>
                </div>
                {conversation.unreadCount > 0 && (
                  <Badge tone="warn">{conversation.unreadCount}</Badge>
                )}
                <span className="font-mono text-[11px] text-muted">
                  {conversation.lastMessageAt
                    ? new Date(conversation.lastMessageAt).toLocaleString("pt-BR", {
                        day: "2-digit",
                        month: "2-digit",
                        hour: "2-digit",
                        minute: "2-digit",
                      })
                    : ""}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
