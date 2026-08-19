# Concord — API

Base: `/api`. Autenticação por cookie de sessão (`concord_session`).
Mutações exigem o header `X-XSRF-TOKEN`, lido do cookie `XSRF-TOKEN`.

---

## Convenções

**Erro** — formato único em toda falha:

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "Usuário ou senha inválidos",
  "timestamp": "2026-01-15T14:03:11Z",
  "requestId": "01J8...",
  "fieldErrors": { "email": "Formato inválido" }
}
```

Reaja ao `code`, nunca ao texto. Textos mudam; códigos não.

**Convenções de status que não são acidentais:**

| Situação | Status | Por quê |
|---|---|---|
| Cadastro, reenvio, "esqueci a senha" | `202` sempre | Um `404` ou `409` transformaria o endpoint em verificador de contas existentes |
| Recurso de terceiro (conversa, chamada, sessão, `/admin/**`) | `404`, nunca `403` | Um `403` confirma existência |
| Webhook com assinatura inválida | `404` | Não revela que o endpoint existe |

---

## Autenticação — público

| Método | Rota | Corpo | Sucesso | Limite |
|---|---|---|---|---|
| POST | `/auth/register` | `username`, `email`, `password`, `displayName`, `website`¹ | `202` | 3/h por IP |
| POST | `/auth/verify-email` | `token` | `204` | — |
| POST | `/auth/verify-email/resend` | `email` | `202` | 1/5min |
| POST | `/auth/login` | `usernameOrEmail`, `password` | `200` + cookie | 5/min por IP |
| POST | `/auth/password/forgot` | `email` | `202` | 3/h |
| POST | `/auth/password/reset` | `token`, `newPassword` | `204` | 5/h |
| POST | `/auth/email-change/confirm` | `token` | `204` | — |
| GET | `/auth/username-available?username=` | — | `200` | 20/min |

¹ `website` é honeypot. Fica oculto no formulário; se vier preenchido, a requisição é descartada em silêncio.

**Erros de login:** `INVALID_CREDENTIALS` (401, genérico), `EMAIL_NOT_VERIFIED` (403), `ACCOUNT_DISABLED` (403), `ACCOUNT_LOCKED` (429).

---

## Conta — autenticado

| Método | Rota | Efeito |
|---|---|---|
| GET | `/auth/me` | Perfil do autenticado |
| POST | `/auth/logout` | Invalida a sessão atual |
| PATCH | `/users/me` | `displayName`, `bio` |
| POST | `/users/me/password` | Exige senha atual; revoga as **outras** sessões |
| POST | `/users/me/email` | Envia confirmação ao novo endereço; o atual segue válido |
| DELETE | `/users/me` | Exige senha + `confirmation: "EXCLUIR"`; anonimiza |
| GET | `/users/me/sessions` | Dispositivos conectados |
| DELETE | `/users/me/sessions/{id}` | Encerra uma |
| DELETE | `/users/me/sessions` | Encerra todas menos a atual |
| GET | `/users/me/export` | JSON com todos os dados. **1/dia** |

---

## Contatos

| Método | Rota | Notas |
|---|---|---|
| GET | `/contacts` | Devolve `contacts`, `incoming`, `outgoing` |
| POST | `/contacts/requests` | Por **username exato**. Não existe busca parcial |
| POST | `/contacts/requests/{id}/accept` | Só o destinatário |
| DELETE | `/contacts/requests/{id}` | Recusa ou cancela |
| DELETE | `/contacts/{userId}` | Desfaz o contato; o histórico permanece |
| POST · DELETE | `/contacts/{userId}/block` | Bloqueia · desbloqueia |

Pedidos cruzados (A→B e B→A) viram contato aceito direto. Bloqueio é unidirecional no registro e **recíproco no efeito**.

---

## Conversas e mensagens

| Método | Rota | Notas |
|---|---|---|
| GET | `/conversations` | Com prévia e contagem de não lidas |
| POST | `/conversations` | `userId`. Exige contato aceito |
| GET | `/conversations/{id}/messages?cursor=&size=` | Keyset, mais recentes primeiro |
| GET | `/conversations/{id}/messages/since?cursor=` | Só o que chegou depois |
| POST | `/conversations/{id}/messages` | `body`, `clientMessageId`. **30/min por usuário** |
| POST | `/conversations/{id}/read` | `messageId` |
| PATCH · DELETE | `/messages/{id}` | Só o autor |

**Dois cursores.** `cursor` pede o histórico anterior; `latestCursor` pede o que chegou depois. O cliente não constrói cursor sozinho — e nem deve.

**`clientMessageId` é obrigatório** e torna o envio idempotente: reenviar devolve a mensagem já gravada em vez de duplicá-la.

---

## Chamadas

| Método | Rota | Notas |
|---|---|---|
| POST | `/calls` | `conversationId`, `type: AUDIO\|VIDEO` |
| POST | `/calls/{id}/accept` | Só o destinatário |
| POST | `/calls/{id}/reject` | Encerra com `REJECTED` |
| POST | `/calls/{id}/end` | `CANCELLED` se tocando, `HANGUP` se ativa |
| GET | `/calls/current` | Chamada aberta, ou vazio |
| GET | `/calls?conversationId=` | Histórico |
| GET | `/webrtc/ice` | Servidores ICE com credencial efêmera (1 h) |

SDP e candidatos ICE **não passam por aqui** — vão pelo WebSocket. Ver `WEBSOCKET.md`.

Erros: `CALLEE_UNAVAILABLE`, `CALLEE_BUSY`, `CALL_ALREADY_ACTIVE`, `CALL_NOT_RINGING`.

---

## Documentos legais

| Método | Rota |
|---|---|
| GET · POST | `/legal/consents` |
| GET | `/legal/consents/history` |

Aceitar uma versão que não é a vigente é recusado: produziria registro de algo que a pessoa não viu.

---

## Administração — exige `ROLE_ADMIN`

| Método | Rota |
|---|---|
| GET | `/admin/users?query=&status=&page=&size=` |
| GET | `/admin/users/{id}` |
| POST | `/admin/users/{id}/disable` (motivo obrigatório) |
| POST | `/admin/users/{id}/enable` |
| POST | `/admin/users/{id}/sessions/revoke` |
| DELETE | `/admin/users/{id}` (motivo obrigatório) |
| GET | `/admin/audit?category=&action=&userId=&from=&to=` |
| GET · PATCH | `/admin/settings` |

**Não existe rota administrativa que retorne mensagem, mídia ou lista de contatos de terceiros.** A ausência é o controle — e é verificada por `PrivacyBoundaryTest`, que falha se alguém importar esses pacotes dentro de `admin/`.

Salvaguardas: não se desativa nem exclui o último `ADMIN`, e nenhum admin age sobre a própria conta.

---

## Webhook — público, sem sessão

| Método | Rota | Proteção |
|---|---|---|
| POST | `/webhooks/email` | HMAC-SHA256 sobre o corpo bruto, header `X-Concord-Signature`, comparação em tempo constante |

Desligado quando `EMAIL_WEBHOOK_SECRET` está vazio. Sem essa verificação, qualquer pessoa poderia suprimir o e-mail de qualquer usuário e impedi-lo de recuperar a senha.

---

## Saúde

`GET /actuator/health` · `GET /actuator/info` — públicos. Em produção o health devolve apenas o status agregado.
