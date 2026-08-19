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
