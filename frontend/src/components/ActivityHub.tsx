"use client";

/**
 * Painel inicial.
 *
 * Antes era uma tela de boas-vindas com três atalhos — bonita e inútil depois
 * do primeiro dia. Agora é um painel de atividade: o que está acontecendo
 * agora, e o que espera resposta.
 *
 * A ordem dos blocos segue urgência, não estética:
 *
 *   1. chamadas acontecendo agora  — perde-se se não entrar já
 *   2. pedidos de amizade          — alguém esperando por você
 *   3. conversas recentes          — continua existindo daqui a uma hora
 *
 * Tudo vem de dados que já existem. Não inventei "Jogando: X" nem feed de
 * destaques: exigiriam modelo novo, e um card que nunca preenche é pior que
 * card nenhum.
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useSession } from "@/lib/session";
import { useRealtime, useRealtimeEvent } from "@/lib/realtime";
import {
  contactsApi,
  conversationsApi,
  serversApi,
  type ChannelSummary,
  type ServerSummary,
} from "@/lib/chatApi";
import type { ContactsOverview, ConversationSummary } from "@/lib/types";
import { errorMessage } from "@/lib/apiClient";
import {
  HashIcon,
  MicIcon,
  PlusIcon,
  ScreenShareIcon,
  SpeakerIcon,
} from "@/components/icons";

interface SalaAtiva {
  serverId: string;
  serverName: string;
  channelId: string;
  channelName: string;
  participantIds: string[];
}

export function ActivityHub() {
  const { user } = useSession();
  const router = useRouter();
  const { onlineUserIds, voiceParticipantsByChannel } = useRealtime();

  const [servers, setServers] = useState<ServerSummary[]>([]);
  const [canaisPorServidor, setCanaisPorServidor] = useState<
    Map<string, ChannelSummary[]>
  >(new Map());
  const [conversas, setConversas] = useState<ConversationSummary[]>([]);
  const [contatos, setContatos] = useState<ContactsOverview | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [processando, setProcessando] = useState<string | null>(null);

  /* ------------------------------------------------------------- carga */

  const carregar = useCallback(async () => {
    try {
      const [listaServidores, listaConversas, visaoContatos] = await Promise.all([
        serversApi.list(),
        conversationsApi.list(),
        contactsApi.overview(),
      ]);
      setServers(listaServidores);
      setConversas(listaConversas);
      setContatos(visaoContatos);

      // Canais de todos os servidores, em paralelo. Sem isto não há como saber
      // o NOME do canal onde a chamada está acontecendo — os eventos de voz só
      // trazem o id.
      const pares = await Promise.all(
        listaServidores.map(async (servidor) => {
          try {
            return [servidor.id, await serversApi.channels(servidor.id)] as const;
          } catch {
            return [servidor.id, [] as ChannelSummary[]] as const;
          }
        }),
      );
      setCanaisPorServidor(new Map(pares));
    } catch (err) {
      setErro(errorMessage(err));
    }
  }, []);

  useEffect(() => {
    void carregar();
  }, [carregar]);

  // Mensagem nova muda a ordem das conversas recentes.
  useRealtimeEvent("MESSAGE_CREATED", () => {
    void conversationsApi.list().then(setConversas).catch(() => {});
  });

  useRealtimeEvent("CONTACT_REQUEST", () => {
    void contactsApi.overview().then(setContatos).catch(() => {});
  });

  /* ------------------------------------------------- chamadas acontecendo */

  const salasAtivas = useMemo<SalaAtiva[]>(() => {
    const resultado: SalaAtiva[] = [];

    for (const [channelId, participantes] of voiceParticipantsByChannel) {
      if (participantes.size === 0) {
        continue;
      }
      for (const servidor of servers) {
        const canal = (canaisPorServidor.get(servidor.id) ?? []).find(
          (item) => item.id === channelId,
        );
        if (canal) {
          resultado.push({
            serverId: servidor.id,
            serverName: servidor.name,
            channelId,
            channelName: canal.name,
            participantIds: Array.from(participantes),
          });
          break;
        }
      }
    }
    return resultado;
  }, [voiceParticipantsByChannel, servers, canaisPorServidor]);

  /* ---------------------------------------------------------- pedidos */

  async function responderPedido(id: string, aceitar: boolean) {
    setProcessando(id);
    try {
      if (aceitar) {
        await contactsApi.accept(id);
      } else {
        await contactsApi.declineOrCancel(id);
      }
      await carregar();
    } catch (err) {
      setErro(errorMessage(err));
    } finally {
      setProcessando(null);
    }
  }

  const pedidos = contatos?.incoming ?? [];
  const conversasRecentes = conversas.slice(0, 5);

  return (
    <div className="hub">
      <header className="hub-header">
        <p className="eyebrow">Seu espaço de comunicação</p>
        <h1 className="hub-title">Olá, {user?.displayName ?? ""}</h1>
        <p className="hub-subtitle">
          Converse, compartilhe tela e mantenha seu time no mesmo lugar.
        </p>
      </header>

      {erro && (
        <p className="hub-error" role="alert">
          {erro}
        </p>
      )}

      {/* ------------------------------------------- chamadas acontecendo */}
      <section className="hub-section">
        <h2 className="hub-section-title">
          <span className="hub-live-dot" aria-hidden="true" />
          Atividade em tempo real
        </h2>

        {salasAtivas.length === 0 ? (
          <p className="hub-empty">
            Nenhuma chamada acontecendo agora. Entre num canal de voz e comece
            uma.
          </p>
        ) : (
          <div className="hub-cards">
            {salasAtivas.map((sala) => (
              <Link
                key={sala.channelId}
                href={`/servers/${sala.serverId}/channels/${sala.channelId}`}
                className="hub-call-card"
              >
                <span className="hub-call-server">{sala.serverName}</span>
                <span className="hub-call-channel">
                  <SpeakerIcon size={15} />
                  {sala.channelName}
                </span>
                <span className="hub-call-count">
                  {sala.participantIds.length}{" "}
                  {sala.participantIds.length === 1 ? "pessoa" : "pessoas"}
                </span>
                <span className="hub-call-join">Entrar na conversa →</span>
              </Link>
            ))}
          </div>
        )}
      </section>

      {/* -------------------------------------------------------- pedidos */}
      {pedidos.length > 0 && (
        <section className="hub-section">
          <h2 className="hub-section-title">
            Pedidos de amizade
            <span className="hub-badge">{pedidos.length}</span>
          </h2>
          <ul className="hub-list">
            {pedidos.map((pedido) => (
              <li key={pedido.id} className="hub-row">
                <span className="hub-avatar">
                  {pedido.user.displayName.slice(0, 2).toUpperCase()}
                </span>
                <span className="hub-row-main">
                  <strong>{pedido.user.displayName}</strong>
                  <span className="hub-row-sub">@{pedido.user.username}</span>
                </span>
                <span className="hub-row-actions">
                  <button
                    type="button"
                    disabled={processando === pedido.id}
                    onClick={() => void responderPedido(pedido.id, true)}
                    className="hub-btn hub-btn-primary"
                  >
                    Aceitar
                  </button>
                  <button
                    type="button"
                    disabled={processando === pedido.id}
                    onClick={() => void responderPedido(pedido.id, false)}
                    className="hub-btn"
                  >
                    Recusar
                  </button>
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* ------------------------------------------------------- conversas */}
      <section className="hub-section">
        <h2 className="hub-section-title">Conversas recentes</h2>

        {conversasRecentes.length === 0 ? (
          <p className="hub-empty">
            Você ainda não tem conversas.{" "}
            <Link href="/contacts" className="hub-link">
              Adicione um contato
            </Link>{" "}
            para começar.
          </p>
        ) : (
          <ul className="hub-list">
            {conversasRecentes.map((conversa) => (
              <li key={conversa.id} className="hub-row">
                <span className="hub-avatar">
                  {conversa.peer.displayName.slice(0, 2).toUpperCase()}
                  <span
                    className={`hub-presence ${
                      onlineUserIds.has(conversa.peer.id) ? "is-online" : ""
                    }`}
                  />
                </span>
                <span className="hub-row-main">
                  <strong>{conversa.peer.displayName}</strong>
                  <span className="hub-row-sub">
                    {conversa.lastMessagePreview ?? "Sem mensagens ainda"}
                  </span>
                </span>
                {conversa.unreadCount > 0 && (
                  <span className="hub-badge">{conversa.unreadCount}</span>
                )}
                <Link
                  href={`/conversations/${conversa.id}`}
                  className="hub-btn hub-btn-primary"
                >
                  Abrir
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* ---------------------------------------------------------- ações */}
      <section className="hub-actions">
        <button
          type="button"
          onClick={() => router.push("/contacts")}
          className="hub-action"
        >
          <MicIcon size={18} />
          Iniciar chamada
          <span className="hub-action-sub">Ligue para um contato</span>
        </button>

        <Link href="/conversations" className="hub-action">
          <HashIcon size={18} />
          Conversas
          <span className="hub-action-sub">Retome de onde parou</span>
        </Link>

        <Link href="/settings" className="hub-action">
          <ScreenShareIcon size={18} />
          Áudio e vídeo
          <span className="hub-action-sub">Preferências e dispositivos</span>
        </Link>

        <Link href="/contacts" className="hub-action">
          <PlusIcon size={18} />
          Adicionar contato
          <span className="hub-action-sub">Encontre pessoas</span>
        </Link>
      </section>
    </div>
  );
}
