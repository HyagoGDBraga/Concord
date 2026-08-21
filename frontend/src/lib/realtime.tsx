"use client";

/**
 * Conexao em tempo real (STOMP sobre WebSocket).
 *
 * Modelo: o WebSocket ENTREGA, nao escreve. Mensagens continuam sendo enviadas
 * por POST — um unico caminho de escrita, com uma unica implementacao de
 * autorizacao, rate limit e idempotencia. A excecao e o indicador de digitacao,
 * que e efemero e nao toca o banco.
 *
 * O cookie de sessao viaja no handshake automaticamente, como em qualquer
 * requisicao HTTP. Nao ha token na URL.
 */

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { Client, type IMessage } from "@stomp/stompjs";
import { api } from "./apiClient";
import { useSession } from "./session";
import type { ChatMessage, PublicUser } from "./types";

export type RealtimeEventType =
  | "MESSAGE_CREATED"
  | "MESSAGE_UPDATED"
  | "MESSAGE_DELETED"
  | "MESSAGE_READ"
  | "TYPING"
  | "PRESENCE"
  | "PRESENCE_SNAPSHOT"
  | "CONTACT_REQUEST"
  | "CONTACT_ACCEPTED"
  | "CHANNEL_MESSAGE_CREATED"
  | "VOICE_ROOM_STATE"
  | "VOICE_USER_JOINED"
  | "VOICE_USER_LEFT"
  | "CALL_INVITE"
  | "CALL_ACCEPTED"
  | "CALL_ENDED"
  | "CALL_SIGNAL"
  | "VOICE_SIGNAL"
  | "VOICE_ERROR"
  | "VOICE_USER_STATE"
  | "SERVER_MEMBER_JOINED";

export interface RealtimeEvent<T = unknown> {
  type: RealtimeEventType;
  payload: T;
  at: string;
}

export interface PresenceEvent {
  userId: string;
  online: boolean;
  at: string;
}

export interface TypingEvent {
  conversationId: string;
  userId: string;
  typing: boolean;
}

export type SignalType =
  | "OFFER"
  | "ANSWER"
  | "ICE_CANDIDATE"
  | "RENEGOTIATE"
  | "SCREEN_SHARE";

export interface CallSignal {
  callId: string;
  fromUserId: string;
  type: SignalType;
  payload: unknown;
}

export interface CallInfo {
  id: string;
  conversationId: string;
  callerId: string;
  calleeId: string;
  type: "AUDIO" | "VIDEO";
  status: "RINGING" | "ACTIVE" | "ENDED";
  endReason: string | null;
  createdAt: string;
  answeredAt: string | null;
  endedAt: string | null;
  durationSeconds: number;
  peer: PublicUser | null;
}

export interface ReadReceipt {
  conversationId: string;
  readerId: string;
  messageId: string;
}

type Listener = (event: RealtimeEvent) => void;

interface RealtimeState {
  connected: boolean;
  /** Registra um ouvinte; devolve a funcao que o remove. */
  subscribe: (listener: Listener) => () => void;
  /** Publica o sinal de digitacao. Silencioso se nao houver conexao. */
  sendTyping: (conversationId: string, typing: boolean) => void;
  /**
   * Publica um sinal WebRTC (SDP ou candidato ICE).
   *
   * Vai pelo WebSocket, nao por HTTP: sao dezenas de mensagens nos primeiros
   * segundos de cada chamada, e nada disso e gravado no servidor.
   */
  sendCallSignal: (callId: string, type: SignalType, payload: unknown) => void;
  sendVoicePresence: (serverId: string, channelId: string, joining: boolean) => void;
  /** Anuncia mudo, camera e compartilhamento de tela aos demais da sala. */
  sendVoiceState: (serverId: string, channelId: string,
    state: { muted: boolean; camera: boolean; screen: boolean }) => void;
  sendVoiceSignal: (serverId: string, channelId: string, targetUserId: string,
                    type: SignalType, payload: unknown) => void;
  onlineUserIds: Set<string>;
  voiceParticipantsByChannel: Map<string, Set<string>>;
}

const RealtimeContext = createContext<RealtimeState | null>(null);

function brokerUrl(): string {
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${window.location.host}/api/ws`;
}

export function RealtimeProvider({ children }: { children: React.ReactNode }) {
  const { user } = useSession();
  const [connected, setConnected] = useState(false);
  const [onlineUserIds, setOnlineUserIds] = useState<Set<string>>(new Set());
  const [voiceParticipantsByChannel, setVoiceParticipantsByChannel] = useState<Map<string, Set<string>>>(new Map());

  const clientRef = useRef<Client | null>(null);
  // Ouvintes fora do estado: mudam a cada montagem de tela e nao devem
  // reconectar o WebSocket.
  const listenersRef = useRef<Set<Listener>>(new Set());

  const dispatch = useCallback((event: RealtimeEvent) => {
    // Estado inicial: substitui o conjunto inteiro. Quem conecta primeiro
    // nao recebe evento de transicao de ninguem, e sem isto ficaria
    // achando que esta sozinho — o que desabilitava o botao de ligar.
    if (event.type === "PRESENCE_SNAPSHOT") {
      const payload = event.payload as { onlineContactIds: string[] };
      setOnlineUserIds(new Set(payload.onlineContactIds ?? []));
      return;
    }

    if (event.type === "PRESENCE") {
      const presence = event.payload as PresenceEvent;
      setOnlineUserIds((current) => {
        const next = new Set(current);
        if (presence.online) {
          next.add(presence.userId);
        } else {
          next.delete(presence.userId);
        }
        return next;
      });
    }
    if (event.type === "VOICE_ROOM_STATE") {
      const payload = event.payload as { channelId: string; participantIds?: string[]; selfUserId?: string };
      setVoiceParticipantsByChannel((current) => {
        const next = new Map(current);
        next.set(payload.channelId, new Set([
          ...(payload.participantIds ?? []),
          ...(payload.selfUserId ? [payload.selfUserId] : []),
        ]));
        return next;
      });
    }
    if (event.type === "VOICE_USER_JOINED") {
      const payload = event.payload as { channelId: string; userId: string };
      setVoiceParticipantsByChannel((current) => {
        const next = new Map(current);
        next.set(payload.channelId, new Set([...(next.get(payload.channelId) ?? []), payload.userId]));
        return next;
      });
    }
    if (event.type === "VOICE_USER_LEFT") {
      const payload = event.payload as { channelId: string; userId: string };
      // A later room-state event will restore this if other participants remain.
      setVoiceParticipantsByChannel((current) => {
        const next = new Map(current);
        const participants = new Set(next.get(payload.channelId) ?? []);
        participants.delete(payload.userId);
        if (participants.size === 0) {
          next.delete(payload.channelId);
        } else {
          next.set(payload.channelId, participants);
        }
        return next;
      });
    }
    for (const listener of listenersRef.current) {
      listener(event);
    }
  }, []);

  const sendVoicePresence = useCallback(
    (serverId: string, channelId: string, joining: boolean) => {
      const client = clientRef.current;
      if (!client?.connected) {
        return;
      }
      client.publish({
        destination: `/app/servers/${serverId}/channels/${channelId}/voice`,
        body: JSON.stringify({ type: joining ? "JOIN" : "LEAVE" }),
      });
    },
    [],
  );

  const sendVoiceState = useCallback(
    (serverId: string, channelId: string,
     state: { muted: boolean; camera: boolean; screen: boolean }) => {
      const client = clientRef.current;
      if (!client?.connected) {
        return;
      }
      client.publish({
        destination: `/app/servers/${serverId}/channels/${channelId}/voice`,
        body: JSON.stringify({ type: "STATE", payload: state }),
      });
    },
    [],
  );

  const sendVoiceSignal = useCallback(
    (serverId: string, channelId: string, targetUserId: string,
     type: SignalType, payload: unknown) => {
      const client = clientRef.current;
      if (!client?.connected) {
        return;
      }
      client.publish({
        destination: `/app/servers/${serverId}/channels/${channelId}/voice`,
        body: JSON.stringify({ type: "SIGNAL", targetUserId, signalType: type, payload }),
      });
    },
    [],
  );

  useEffect(() => {
    if (!user) {
      return;
    }

    const client = new Client({
      brokerURL: brokerUrl(),
      // Reconexao automatica. A lacuna de mensagens durante a queda e
      // preenchida pela tela de conversa via /messages/since.
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setConnected(true);
        client.subscribe("/user/queue/events", (frame: IMessage) => {
          try {
            dispatch(JSON.parse(frame.body) as RealtimeEvent);
          } catch {
            // Frame malformado nao deve derrubar a conexao.
          }
        });

        // Estado inicial de presenca, buscado DEPOIS de assinar a fila.
        //
        // O servidor tambem envia um PRESENCE_SNAPSHOT quando o WebSocket abre,
        // mas isso acontece antes de o cliente mandar o SUBSCRIBE — o evento ia
        // para uma fila sem ninguem escutando e se perdia. Resultado: quem
        // conectava primeiro achava que estava sozinho, o botao de ligar ficava
        // desabilitado, e so um dos lados conseguia iniciar chamada.
        //
        // Aqui a ordem e garantida: assinamos e so entao perguntamos.
        void api
          .get<{ onlineContactIds: string[] }>("/presence")
          .then((estado) => setOnlineUserIds(new Set(estado.onlineContactIds ?? [])))
          .catch(() => {
            // Sem o instantaneo, a presenca ainda chega pelos eventos de
            // transicao — so demora mais a ficar correta.
          });
      },
      onDisconnect: () => setConnected(false),
      onWebSocketClose: (event) => {
        setConnected(false);
        setOnlineUserIds(new Set());
        // 4401 e o codigo que o servidor usa quando a sessao foi revogada:
        // insistir em reconectar seria inutil.
        if (event?.code === 4401) {
          client.deactivate().catch(() => {});
          window.location.href = "/login";
        }
      },
      onStompError: (frame) => {
        console.error("Erro STOMP", frame.headers["message"]);
      },
    });

    clientRef.current = client;
    client.activate();

    return () => {
      clientRef.current = null;
      client.deactivate().catch(() => {});
      setConnected(false);
    };
  }, [user, dispatch]);

  const subscribe = useCallback((listener: Listener) => {
    listenersRef.current.add(listener);
    return () => {
      listenersRef.current.delete(listener);
    };
  }, []);

  const sendTyping = useCallback((conversationId: string, typing: boolean) => {
    const client = clientRef.current;
    if (!client?.connected) {
      return;
    }
    client.publish({
      destination: `/app/conversations/${conversationId}/typing`,
      body: JSON.stringify({ typing }),
    });
  }, []);

  const sendCallSignal = useCallback(
    (callId: string, type: SignalType, payload: unknown) => {
      const client = clientRef.current;
      if (!client?.connected) {
        return;
      }
      client.publish({
        destination: `/app/calls/${callId}/signal`,
        body: JSON.stringify({ type, payload }),
      });
    },
    [],
  );

  const value = useMemo<RealtimeState>(
    () => ({ connected, subscribe, sendTyping, sendCallSignal, sendVoicePresence,
      sendVoiceState, sendVoiceSignal, onlineUserIds, voiceParticipantsByChannel }),
    [connected, subscribe, sendTyping, sendCallSignal, sendVoicePresence,
      sendVoiceState, sendVoiceSignal, onlineUserIds, voiceParticipantsByChannel],
  );

  return (
    <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>
  );
}

export function useRealtime(): RealtimeState {
  const context = useContext(RealtimeContext);
  if (!context) {
    throw new Error("useRealtime precisa estar dentro de RealtimeProvider");
  }
  return context;
}

/** Assina os eventos de um tipo especifico durante a vida do componente. */
export function useRealtimeEvent<T>(
  type: RealtimeEventType,
  handler: (payload: T) => void,
) {
  const { subscribe } = useRealtime();
  const handlerRef = useRef(handler);
  handlerRef.current = handler;

  useEffect(
    () =>
      subscribe((event) => {
        if (event.type === type) {
          handlerRef.current(event.payload as T);
        }
      }),
    [subscribe, type],
  );
}

export type { ChatMessage, PublicUser };
