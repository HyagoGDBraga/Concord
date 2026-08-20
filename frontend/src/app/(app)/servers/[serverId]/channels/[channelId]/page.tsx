"use client";

import { useParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { api, errorMessage } from "@/lib/apiClient";
import { useSession } from "@/lib/session";
import { useRealtime, useRealtimeEvent } from "@/lib/realtime";
import { Alert, Button, Input, Spinner } from "@/components/ui";

type ChannelMessage = {
  id: string;
  channelId: string;
  senderId: string;
  body: string;
  createdAt: string;
};

type Channel = { id: string; name: string; type: string };
type Server = { id: string; name: string; channels: Channel[] };

export default function ChannelPage() {
  const params = useParams<{ serverId: string; channelId: string }>();
  const { user } = useSession();
  const [server, setServer] = useState<Server | null>(null);
  const [messages, setMessages] = useState<ChannelMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const { connected } = useRealtime();

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const [servers, page] = await Promise.all([
          api.get<Server[]>("/servers"),
          api.get<{ items: ChannelMessage[] }>(`/channels/${params.channelId}/messages`),
        ]);
        if (!cancelled) {
          setServer(servers.find((item) => item.id === params.serverId) ?? null);
          setMessages(page.items);
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
  }, [params.channelId, params.serverId]);

  useRealtimeEvent<ChannelMessage>("CHANNEL_MESSAGE_CREATED", (message) => {
    if (message.channelId !== params.channelId) {
      return;
    }
    setMessages((current) => current.some((item) => item.id === message.id)
      ? current
      : [...current, message]);
  });

  useEffect(() => {
    if (connected) {
      return;
    }
    const timer = window.setInterval(() => {
      void api.get<{ items: ChannelMessage[] }>(
        `/channels/${params.channelId}/messages`,
      ).then((page) => setMessages(page.items)).catch(() => {});
    }, 8000);
    return () => window.clearInterval(timer);
  }, [connected, params.channelId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages.length]);

  async function send(event: React.FormEvent) {
    event.preventDefault();
    const body = draft.trim();
    if (!body) {
      return;
    }
    setSending(true);
    setError(null);
    try {
      const message = await api.post<ChannelMessage>(
        `/channels/${params.channelId}/messages`,
        { body, clientMessageId: crypto.randomUUID() },
      );
      setMessages((current) => current.some((item) => item.id === message.id)
        ? current
        : [...current, message]);
      setDraft("");
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSending(false);
    }
  }

  if (loading) {
    return <Spinner label="Carregando canal" />;
  }

  const channel = server?.channels.find((item) => item.id === params.channelId);

  return (
    <section className="channel-page">
      <header className="channel-page-header">
        <div>
          <p className="eyebrow">{server?.name ?? "Servidor"}</p>
          <h2><span className="channel-page-symbol">#</span>{channel?.name ?? "Canal"}</h2>
          <p>Conversa aberta para a comunidade.</p>
        </div>
        <span className="channel-member-count">{messages.length} mensagens</span>
      </header>

      {error && <Alert tone="error">{error}</Alert>}

      <div className="channel-message-list">
        {messages.length === 0 && (
          <div className="channel-empty">
            <span className="channel-empty-mark">#</span>
            <h3>Este é o começo de #{channel?.name ?? "canal"}</h3>
            <p>Abra a conversa e deixe a primeira mensagem.</p>
          </div>
        )}
        {messages.map((message) => {
          const mine = message.senderId === user?.id;
          return (
            <article key={message.id} className={`channel-message ${mine ? "is-mine" : ""}`}>
              <div className="channel-message-avatar">{mine ? "Você" : "M"}</div>
              <div>
                <div className="channel-message-meta">
                  <strong>{mine ? user?.displayName : "Membro"}</strong>
                  <time>{new Date(message.createdAt).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}</time>
                </div>
                <p>{message.body}</p>
              </div>
            </article>
          );
        })}
        <div ref={bottomRef} />
      </div>

      <form className="channel-composer" onSubmit={send}>
        <Input
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder={`Conversar em #${channel?.name ?? "canal"}`}
          maxLength={4000}
          disabled={sending}
        />
        <Button type="submit" loading={sending}>Enviar</Button>
      </form>
    </section>
  );
}