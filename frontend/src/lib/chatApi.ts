/**
 * Chamadas de contatos e chat.
 *
 * Concentradas aqui em vez de espalhadas pelos componentes: quando o WebSocket
 * chegar na Fase 4, o envio de mensagem passa a ter dois caminhos e este e o
 * unico arquivo que precisa saber disso.
 */

import { api } from "./apiClient";
import type {
  ChatMessage,
  ContactsOverview,
  ConversationSummary,
  MessagePage,
} from "./types";

export const contactsApi = {
  overview: () => api.get<ContactsOverview>("/contacts"),
  request: (username: string) =>
    api.post<void>("/contacts/requests", { username }),
  accept: (requestId: string) =>
    api.post<void>(`/contacts/requests/${requestId}/accept`),
  declineOrCancel: (requestId: string) =>
    api.delete<void>(`/contacts/requests/${requestId}`),
  remove: (userId: string) => api.delete<void>(`/contacts/${userId}`),
  block: (userId: string) => api.post<void>(`/contacts/${userId}/block`),
  unblock: (userId: string) => api.delete<void>(`/contacts/${userId}/block`),
};

export const conversationsApi = {
  list: () => api.get<ConversationSummary[]>("/conversations"),

  open: (userId: string) =>
    api.post<{ id: string }>("/conversations", { userId }),

  history: (conversationId: string, cursor?: string | null, size = 50) => {
    const params = new URLSearchParams({ size: String(size) });
    if (cursor) {
      params.set("cursor", cursor);
    }
    return api.get<MessagePage>(
      `/conversations/${conversationId}/messages?${params}`,
    );
  },

  since: (conversationId: string, cursor: string) =>
    api.get<MessagePage>(
      `/conversations/${conversationId}/messages/since?cursor=${encodeURIComponent(cursor)}`,
    ),

  /**
   * Envia uma mensagem.
   *
   * O `clientMessageId` e gerado aqui e torna o envio idempotente: se a
   * requisicao for repetida por instabilidade de rede, o backend devolve a
   * mensagem ja gravada em vez de criar outra.
   */
  send: (conversationId: string, body: string, clientMessageId: string) =>
    api.post<ChatMessage>(`/conversations/${conversationId}/messages`, {
      body,
      clientMessageId,
    }),

  markRead: (conversationId: string, messageId: string) =>
    api.post<void>(`/conversations/${conversationId}/read`, { messageId }),
};

export const messagesApi = {
  edit: (messageId: string, body: string) =>
    api.patch<ChatMessage>(`/messages/${messageId}`, { body }),
  remove: (messageId: string) => api.delete<void>(`/messages/${messageId}`),
};

/* ------------------------------------------------------------- servidores */

/**
 * Membro do servidor.
 *
 * O backend aninha o perfil em `user` (ServerDtos.MemberResponse). Eu tinha
 * escrito uma versao achatada e o `members.get(id)` nunca casava — era por isso
 * que a sala de voz mostrava "Membro 2b24ae" em vez do nome.
 */
export interface ServerMember {
  user: {
    id: string;
    username: string;
    displayName: string;
    avatarUrl: string | null;
    bio: string | null;
  };
  role: "OWNER" | "MODERATOR" | "MEMBER";
  nickname?: string | null;
}

export interface ServerSummary {
  id: string;
  name: string;
  iconUrl: string | null;
}

export interface ChannelSummary {
  id: string;
  name: string;
  type: "TEXT" | "VOICE";
}

export const serversApi = {
  list: () => api.get<ServerSummary[]>("/servers"),
  channels: (serverId: string) =>
    api.get<ChannelSummary[]>(`/servers/${serverId}/channels`),
  /**
   * Membros do servidor.
   *
   * A sala de voz precisa disto para mostrar NOME ao lado do avatar. Antes ela
   * so tinha os ids que a sinalizacao entrega, e id nao diz nada a ninguem.
   */
  members: (serverId: string) =>
    api.get<ServerMember[]>(`/servers/${serverId}/members`),
};

/* --------------------------------------------------------------- anexos */

export interface AttachmentResponse {
  id: string;
  name: string;
  contentType: string;
  sizeBytes: number;
  image: boolean;
  url: string;
  expiresAt: string | null;
}

/** Teto de 5 MB, o mesmo do servidor e do banco. */
export const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

export const attachmentsApi = {
  /**
   * Troca a foto de perfil.
   *
   * Usa FormData e NAO define Content-Type: o navegador precisa gerar o
   * boundary do multipart sozinho. Definir o cabecalho na mao produz um corpo
   * que o servidor nao consegue separar.
   */
  uploadAvatar: (file: File) => {
    const dados = new FormData();
    dados.append("file", file);
    return api.postForm<AttachmentResponse>("/users/me/avatar", dados);
  },

  removeAvatar: () => api.delete<void>("/users/me/avatar"),
};
