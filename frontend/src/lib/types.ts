/** Contratos da API. Espelham os DTOs do backend. */

export type UserRole = "USER" | "ADMIN";

export type UserStatus =
  | "PENDING_VERIFICATION"
  | "ACTIVE"
  | "DISABLED"
  | "DELETED";

export interface Me {
  id: string;
  username: string;
  email: string | null;
  displayName: string;
  avatarUrl: string | null;
  bio: string | null;
  role: UserRole;
  status: UserStatus;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface SessionInfo {
  id: string;
  createdAt: string;
  lastAccessedAt: string;
  ipAddress: string | null;
  userAgent: string | null;
  current: boolean;
}

export interface AdminUser {
  id: string;
  username: string;
  email: string | null;
  displayName: string;
  role: UserRole;
  status: UserStatus;
  emailVerifiedAt: string | null;
  lastLoginAt: string | null;
  disabledAt: string | null;
  disabledReason: string | null;
  temporarilyLocked: boolean;
  lockedUntil: string | null;
  failedLoginCount: number;
  createdAt: string;
}

export type AuditCategory = "SECURITY" | "ADMIN" | "PRIVACY";
export type AuditOutcome = "SUCCESS" | "FAILURE" | "DENIED";

export interface AuditEntry {
  id: number;
  createdAt: string;
  category: AuditCategory;
  action: string;
  outcome: AuditOutcome;
  actorUserId: string | null;
  actorLabel: string | null;
  targetUserId: string | null;
  ipAddress: string | null;
  metadata: Record<string, unknown>;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface AppSettings {
  registrationOpen: boolean;
  adminBootstrapCompleted: boolean;
}

/* ------------------------------------------------------- contatos e chat */

export interface PublicUser {
  id: string;
  username: string;
  displayName: string;
  avatarUrl: string | null;
  bio: string | null;
}

export interface ContactEntry {
  id: string;
  user: PublicUser;
  since: string | null;
  blockedByMe: boolean;
}

export interface ContactRequestEntry {
  id: string;
  user: PublicUser;
  createdAt: string;
}

export interface ContactsOverview {
  contacts: ContactEntry[];
  incoming: ContactRequestEntry[];
  outgoing: ContactRequestEntry[];
}

export interface ConversationSummary {
  id: string;
  peer: PublicUser;
  createdAt: string;
  lastMessageAt: string | null;
  lastMessagePreview: string | null;
  unreadCount: number;
  peerBlocked: boolean;
  stillContacts: boolean;
}

export interface ChatMessage {
  id: string;
  conversationId: string;
  senderId: string;
  /** Ausente quando a mensagem foi apagada. */
  body?: string;
  clientMessageId: string;
  createdAt: string;
  editedAt: string | null;
  deleted: boolean;
  /** Arquivos anexados. Vazio na maioria das mensagens. */
  attachments?: {
    id: string;
    name: string;
    contentType: string;
    sizeBytes: number;
    image: boolean;
    url: string;
    expiresAt: string | null;
  }[];
}

/**
 * Pagina de mensagens.
 *
 * Dois cursores porque a conversa cresce nas duas pontas: `cursor` pede o
 * historico anterior, `latestCursor` pede o que chegou depois.
 */
export interface MessagePage {
  items: ChatMessage[];
  cursor: string | null;
  latestCursor: string | null;
  hasMore: boolean;
}
