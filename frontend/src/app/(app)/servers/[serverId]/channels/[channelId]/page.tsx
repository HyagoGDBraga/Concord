"use client";

import { useParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { api, errorMessage } from "@/lib/apiClient";
import { serversApi, type ServerMember } from "@/lib/chatApi";
import { useSession } from "@/lib/session";
import { useRealtime, useRealtimeEvent } from "@/lib/realtime";
import { Alert, Button, Input, Spinner } from "@/components/ui";
import { VoiceChannelCard } from "@/components/VoiceChannelCard";
import { VoiceStage } from "@/components/VoiceStage";
import { AttachmentList, AttachmentPicker } from "@/components/AttachmentPicker";
import type { AttachmentResponse } from "@/lib/chatApi";
import { useVoiceChannel } from "@/lib/voiceChannel";

type ChannelMessage = {
  id: string;
  channelId: string;
  senderId: string;
  attachments?: AttachmentResponse[];
  body: string;
  createdAt: string;
};

type Channel = { id: string; name: string; type: string };
type Server = { id: string; name: string; channels: Channel[] };

export default function ChannelPage() {
  const params = useParams<{ serverId: string; channelId: string }>();
  const { user } = useSession();
  const [server, setServer] = useState<Server | null>(null);
  /**
   * Membros do servidor, para resolver o NOME de quem escreveu.
   *
   * A mensagem so traz senderId. Sem esta lista, toda mensagem de outra pessoa
   * aparecia como "Membro" — o rotulo estava literal no codigo.
   */
  const [membros, setMembros] = useState<Map<string, ServerMember>>(new Map());

  const [messages, setMessages] = useState<ChannelMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [anexos, setAnexos] = useState<AttachmentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const { connected } = useRealtime();
  const { estaAtivo } = useVoiceChannel();

  useEffect(() => {
    serversApi
      .members(params.serverId)
      .then((lista) => setMembros(new Map(lista.map((m) => [m.user.id, m]))))
      .catch(() => {
        // Sem a lista, cai no id abreviado. Nao vale derrubar o canal por causa
        // do rotulo.
      });
  }, [params.serverId]);

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
    // Mensagem so com arquivo e legitima. O servidor aceita corpo vazio
    // quando ha anexo.
    if (!body && anexos.length === 0) {
      return;
    }
    setSending(true);
    setError(null);
    try {
      const message = await api.post<ChannelMessage>(
        `/channels/${params.channelId}/messages`,
        {
          body,
          clientMessageId: crypto.randomUUID(),
          attachmentIds: anexos.map((anexo) => anexo.id),
        },
      );
      setAnexos([]);
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

      {/* O cartao so aparece enquanto voce NAO esta na sala. Depois de entrar,
          quem manda e o palco — os dois juntos mostravam dois "Sair da sala". */}
      {channel?.type === "VOICE" && !estaAtivo(params.channelId) && (
        <VoiceChannelCard
          serverId={params.serverId}
          serverName={server?.name ?? "Servidor"}
          channelId={params.channelId}
          channelName={channel.name}
        />
      )}

      {/* O palco so aparece no canal em que voce esta conectado. */}
      {channel?.type === "VOICE" && estaAtivo(params.channelId) && <VoiceStage />}

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
              {/* Um avatar so. Havia dois: este, com as iniciais, e outro que
                  eu acrescentei ao lado do nome — resultado, icone duplicado em
                  cada mensagem. A foto entra AQUI, no que ja existia. */}
              <div className="channel-message-avatar">
                {(mine ? user?.avatarUrl : membros.get(message.senderId)?.user.avatarUrl) ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={(mine
                      ? user?.avatarUrl
                      : membros.get(message.senderId)?.user.avatarUrl)!}
                    alt=""
                  />
                ) : (
                  (mine
                    ? (user?.displayName ?? "?")
                    : (membros.get(message.senderId)?.user.displayName ??
                       membros.get(message.senderId)?.user.username ??
                       "?")
                  ).slice(0, 2).toUpperCase()
                )}
              </div>
              <div>
                <div className="channel-message-meta">
                  <strong>
                    {mine
                      ? (user?.displayName ?? "Você")
                      : (membros.get(message.senderId)?.nickname ??
                         membros.get(message.senderId)?.user.displayName ??
                         membros.get(message.senderId)?.user.username ??
                         `Membro ${message.senderId.slice(0, 6)}`)}
                  </strong>
                  <time>{new Date(message.createdAt).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}</time>
                </div>
                {message.body?.trim() && <p>{message.body}</p>}
                <AttachmentList anexos={message.attachments ?? []} />
              </div>
            </article>
          );
        })}
        <div ref={bottomRef} />
      </div>

      <form className="channel-composer" onSubmit={send}>
        <AttachmentPicker
          destino={{ channelId: params.channelId }}
          anexos={anexos}
          onChange={setAnexos}
          disabled={sending}
        />
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