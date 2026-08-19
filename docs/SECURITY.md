# Concord — Segurança

Documento de referência do que está implementado, por que, e o que ainda não está.
Atualizado ao fim da Fase 7.

---

## 1. Modelo de ameaça

Antes dos controles, contra o que eles existem.

| Adversário | O que quer | Defesa principal |
|---|---|---|
| Automação varrendo a internet | Contas para spam, relay de TURN aberto | Cadastro com verificação de e-mail, rate limit por IP, honeypot, credencial de TURN efêmera |
| Atacante mirando um usuário | Entrar na conta dele | Argon2id, bloqueio exponencial por conta, sessão revogável, aviso por e-mail em toda mudança sensível |
| Usuário legítimo mal-intencionado | Ler conversa alheia, assediar | Autorização por participação em toda leitura, bloqueio recíproco, 404 em vez de 403 |
| Conta de administrador comprometida | Ler mensagens de todos | **Não existe caminho de código** que entregue conteúdo privado sob `/admin` |
| Quem obtiver um dump do banco | Senhas, links de recuperação, endereços | Senha em Argon2id, token só em hash, e-mail suprimido só em hash |
| Operador da infraestrutura | Escutar chamadas | Mídia P2P com DTLS-SRTP; o TURN encaminha bytes que não consegue ler |

Fora do escopo, declarado: adversário com acesso ao dispositivo do usuário, e adversário estatal com capacidade de análise de tráfego.

---

## 2. Autenticação

| Controle | Implementação |
|---|---|
| Hash de senha | Argon2id, 19 MiB, 2 iterações, paralelismo 1 (parâmetros OWASP) |
| Migração de algoritmo | `DelegatingPasswordEncoder` grava o prefixo; trocar não invalida senhas existentes |
| Política de senha | Mínimo 12 caracteres, lista local de senhas comuns, proibido conter username ou e-mail. **Sem regra de composição** — elas produzem `Senha@123` |
| Força bruta por conta | Backoff exponencial 1→2→4→8→15 min a partir da 5ª falha |
| Força bruta por IP | 5 tentativas/min no login |
| Enumeração por resposta | Cadastro e recuperação sempre respondem 202 |
| Enumeração por tempo | `DaoAuthenticationProvider` executa verificação descartável quando o usuário não existe |

**Ordem que importa:** o bloqueio temporário é avaliado **antes** da senha (senão não conteria força bruta); o estado da conta é avaliado **depois** (senão revelaria que a conta existe a quem só chutou o username).

---

## 3. Sessão

Detalhamento completo em `CONCORD-02-SESSAO-E-AUTENTICACAO.md`. Resumo:

- Cookie opaco, `HttpOnly` + `SameSite=Lax` + `Secure` em produção. O identificador é ponteiro, não credencial: toda informação vive no PostgreSQL.
- Expiração: 7 dias de inatividade, teto absoluto de 30 dias.
- Revogação instantânea — apagar a linha encerra a sessão no próximo request.
- `ChangeSessionIdAuthenticationStrategy` chamado explicitamente no login (o login é JSON, não `formLogin`, e não receberia a proteção automaticamente).
- CSRF por double-submit: cookie `XSRF-TOKEN` legível + header `X-XSRF-TOKEN`, centralizado no `apiClient`.
- **O WebSocket usa a mesma sessão.** O handshake é uma requisição HTTP; sem token em query string, e revogar a sessão derruba a conexão em até 30 s.

---

## 4. Autorização

Três invariantes que valem para o sistema inteiro:

1. **O alvo é sempre o principal autenticado.** Não existe `PATCH /users/{id}` — só `/users/me`. Elimina IDOR por construção, não por verificação.
2. **Participação é verificada na consulta**, não em filtro posterior. `ConversationRepository.findAllOf` faz JOIN com participantes; não há como esquecer o filtro.
3. **404, nunca 403**, em `/admin/**`, conversa alheia, chamada alheia e sessão alheia. Um 403 confirma existência.

O painel administrativo tem autorização dupla — `SecurityConfig` mais `@PreAuthorize` em cada método. Redundância intencional: uma rota nova mal mapeada continua protegida.

---

## 5. Dados em repouso

| Dado | Como é guardado |
|---|---|
| Senha | Argon2id, irreversível |
| Token de verificação e reset | Apenas SHA-256. Um dump do banco não permite usar um link de recuperação |
| E-mail suprimido | Apenas SHA-256. A lista responde "está suprimido?" sem manter cadastro de endereços |
| Mensagem | Texto claro no banco — **ver limitação em §9** |
| SDP e ICE | Não são guardados |
| Mídia | Nunca chega ao servidor |

Invariantes de privacidade impostas pelo **banco**, não pelo serviço:

```sql
CHECK (status <> 'DELETED' OR (email IS NULL AND anonymized_at IS NOT NULL))
CHECK (deleted_at IS NULL OR body IS NULL)
```

Um bug futuro que esqueça de limpar o campo faz a transação falhar, em vez de deixar dado pessoal para trás.

---

## 6. Superfície pública

Endpoints acessíveis sem sessão, e o que protege cada um:

| Rota | Proteção |
|---|---|
| `POST /auth/register` | 3/h por IP, honeypot, verificação de e-mail obrigatória |
| `POST /auth/login` | 5/min por IP, bloqueio por conta |
| `POST /auth/password/forgot` | 3/h por IP, resposta sempre idêntica |
| `POST /auth/password/reset` | Token de 256 bits, uso único, TTL 30 min |
| `GET /auth/username-available` | 20/min por IP |
| `POST /webhooks/email` | **HMAC-SHA256 com comparação em tempo constante**, desligado sem segredo, 120/min |
| `GET /api/ws` | Recusa handshake sem sessão |

O webhook é o mais sensível: sem verificação de assinatura, qualquer pessoa poderia suprimir o e-mail de qualquer usuário e impedi-lo de recuperar a senha.

---

## 7. Cabeçalhos

**Frontend** (governa o HTML — é onde XSS acontece): CSP com `frame-ancestors 'none'`, `object-src 'none'`, `base-uri 'self'`; `Permissions-Policy` liberando câmera, microfone e captura de tela apenas para a própria origem e negando o resto; COOP e CORP `same-origin`.

**Backend** (só devolve JSON): `default-src 'none'; frame-ancestors 'none'`, HSTS com `includeSubDomains` em produção.

---

## 8. WebRTC

- Credencial de TURN efêmera por HMAC-SHA1, validade 1 h, com o id do usuário embutido para rastreabilidade. **O segredo do coturn nunca chega ao navegador.**
- `denied-peer-ip` bloqueia todas as faixas privadas: sem isso, o TURN alcançaria o PostgreSQL e o Mailpit do próprio servidor.
- `max-bps` por sessão e `bps-capacity` agregado impedem que uma sessão consuma o link.
- Mídia cifrada por DTLS-SRTP, obrigatório na especificação.

---

## 9. Limitações conhecidas

Declaradas porque omitir seria pior:

1. **Não há criptografia ponta a ponta nas mensagens.** O servidor lê o texto. E2EE exigiria gerenciamento de chaves por dispositivo, e histórico que não se recupera ao trocar de aparelho. Está fora do MVP — e a política de privacidade diz isso com todas as letras, em vez de sugerir uma proteção inexistente.
2. **`'unsafe-inline'` em `script-src`.** O Next injeta o payload de hidratação inline; eliminar exige nonce por requisição e renderização dinâmica em todas as páginas.
3. **Rate limit em memória.** Perdido no restart, não compartilhado entre instâncias. Adequado à escala; a substituição é local a uma classe.
4. **Broker STOMP em memória.** Obriga instância única.
5. **Sem 2FA.** É o item de maior retorno para uma fase futura.
6. **Sem varredura de dependências.** `mvn dependency-check` e `npm audit` no CI é trabalho da Fase 8.
7. **Nada disso foi verificado por execução.** Nenhum build foi rodado até aqui.

---

## 10. Resposta a incidente

Se uma conta for comprometida:

1. Admin desativa a conta → todas as sessões caem e o WebSocket é derrubado.
2. `GET /admin/audit?userId=` reconstrói o que aconteceu, com IP.
3. O titular redefine a senha → todas as sessões são revogadas, inclusive a do atacante.

Se o banco vazar: senhas resistem (Argon2id), links de recuperação não são utilizáveis (só o hash está lá), mas **mensagens estão em texto claro** — ver §9.1.
