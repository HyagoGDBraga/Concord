# Concord — WebSocket

STOMP sobre WebSocket nativo. Endpoint: `wss://dominio/api/ws`.

---

## 1. Regra que organiza tudo

**O WebSocket entrega, não escreve.**

Mensagens continuam sendo enviadas por `POST`. Aceitar escrita por STOMP exigiria duplicar autorização, rate limit, idempotência e validação em dois transportes — e a segunda cópia é a que diverge. Um caminho de escrita, um lugar onde acertar.

Duas exceções, ambas de coisas efêmeras que nunca tocam o banco:

- `/app/conversations/{id}/typing` — indicador de digitação
- `/app/calls/{id}/signal` — SDP e candidatos ICE

---

## 2. Autenticação

O handshake **é uma requisição HTTP**. O navegador anexa o cookie `concord_session` sozinho, o filtro do Spring Session resolve a sessão e o Spring Security popula o contexto — tudo antes do `AuthHandshakeInterceptor` rodar. Sem autenticação, a conexão não abre.

**Não há token na query string.** Ele ficaria registrado em log de proxy, histórico e cabeçalho `Referer`.

**Revogar a sessão derruba a conexão.** O `RevokedSessionSweeper` varre a cada 30 s e fecha com código `4401`; o cliente reconhece esse código e para de tentar reconectar. Sem isso, um usuário desativado por um administrador continuaria recebendo mensagens.

---

## 3. Destinos

| Destino | Direção | Uso |
|---|---|---|
| `/user/queue/events` | servidor → cliente | **Única** assinatura |
| `/app/conversations/{id}/typing` | cliente → servidor | Digitação |
| `/app/calls/{id}/signal` | cliente → servidor | SDP e ICE |

**Só destinos de usuário. Nenhum `/topic/`.**

Um tópico por conversa exigiria autorizar cada subscrição por destino, e um erro nessa autorização entregaria conversa alheia. Com destino de usuário, o Spring resolve o principal da sessão e ninguém consegue assinar a fila de outra pessoa.

O `StompInboundInterceptor` recusa qualquer `SUBSCRIBE` fora de `/user/queue/` — inclusive no destino interno `/queue/events-<sufixo>` para onde o Spring reescreve as filas. Sem essa regra, acertar o sufixo daria acesso a eventos alheios.

O preço é enviar uma cópia por destinatário. Em conversa direta, duas.

---

## 4. Eventos

Envelope único, com `type` discriminando:

```json
{ "type": "MESSAGE_CREATED", "payload": { }, "at": "2026-01-15T14:03:11Z" }
```

| Tipo | Quando | Vai para |
|---|---|---|
| `MESSAGE_CREATED` | Mensagem enviada | Todos os participantes, **inclusive quem enviou** |
| `MESSAGE_UPDATED` · `MESSAGE_DELETED` | Edição · exclusão | Participantes |
| `MESSAGE_READ` | Leitura marcada | Só o interlocutor |
| `TYPING` | Digitando | Só o interlocutor |
| `PRESENCE` | Entrou/saiu | Só contatos aceitos |
| `CONTACT_REQUEST` · `CONTACT_ACCEPTED` | Pedido · aceite | O outro lado |
| `CALL_INVITE` · `CALL_ACCEPTED` · `CALL_ENDED` | Ciclo da chamada | Participantes |
| `CALL_SIGNAL` | SDP/ICE | O outro lado, sem interpretação |

Um envelope só, em vez de um destino por tipo: adicionar evento novo não exige mexer no broker nem no cliente já conectado.

`MESSAGE_CREATED` volta também para quem enviou — é o que sincroniza as outras abas e dispositivos da mesma pessoa sem lógica adicional no cliente.

---

## 5. Emissão após o commit

Todo evento passa por `AfterCommit.run(...)`. Emitir dentro da transação abriria a janela em que o destinatário recebe "chegou mensagem", consulta o servidor e não encontra nada — porque o commit ainda não ocorreu, ou porque houve rollback e a mensagem nunca existiu.

---

## 6. Reconexão

Heartbeat de 10 s nos dois sentidos, para detectar conexão morta por queda de rede em que nenhum lado recebe o fechamento. Reconexão automática a cada 3 s.

**A lacuna do período desconectado é preenchida por `/messages/since`**, com o `latestCursor` da última mensagem conhecida. Sem isso, as mensagens do intervalo só apareceriam ao recarregar a página.

Enquanto `connected === false`, a tela volta a consultar o servidor periodicamente — rede de segurança, não caminho principal.

---

## 7. Limitação conhecida

**Broker `SimpleBroker` em memória.** O estado das assinaturas vive na JVM, o que obriga instância única. Adequado à escala do Concord; se um dia houver mais de uma instância, a troca é por RabbitMQ e fica contida no `WebSocketConfig`.

É também o motivo de o projeto não rodar em plataforma que hiberne ou escale a zero — ver §7 do `DEPLOY.md`.
