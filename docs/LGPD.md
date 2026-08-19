# Concord — LGPD

Mapeamento entre o que a Lei 13.709/2018 exige e o que o sistema faz.
Atualizado ao fim da Fase 7.

> **Este documento não é parecer jurídico.** Ele descreve com precisão o
> comportamento do software — o que já é útil, porque a causa mais comum de
> política de privacidade inválida é descrever um tratamento que o sistema não
> pratica. A validação das bases legais e a redação final precisam de advogado.
> Os pontos que dependem disso estão na §7.

---

## 1. Papéis

- **Controlador:** quem opera a instância do Concord e decide sobre o tratamento.
- **Operadores:** provedor de VPS, provedor de e-mail transacional. Ambos exigem contrato com cláusula de tratamento de dados.
- **Encarregado (DPO):** **pendente de definição.** Art. 41 exige a indicação e a divulgação do contato.

---

## 2. Inventário de dados

| Dado | Origem | Onde vive | Retenção |
|---|---|---|---|
| Username, nome de exibição, bio | Titular | `users` | Enquanto a conta existir |
| E-mail | Titular | `users` | Anulado na exclusão |
| Senha (hash Argon2id) | Titular | `users` | Idem |
| Mensagens | Titular e interlocutor | `messages` | Enquanto a conversa existir |
| Contatos e bloqueios | Titular | `contacts`, `blocks` | Idem |
| Registro de chamada (sem conteúdo) | Sistema | `calls` | 180 dias |
| Sessões ativas (IP, user-agent) | Sistema | `SPRING_SESSION_ATTRIBUTES` | Morre com a sessão |
| Eventos de segurança (com IP) | Sistema | `audit_log` | 6 meses; IP anulado no mesmo prazo |
| Ações administrativas | Sistema | `audit_log` | 24 meses |
| Exercício de direitos | Sistema | `audit_log` | 60 meses, **já nasce sem IP** |
| Aceite de termos (versão + IP) | Titular | `user_consents` | Enquanto a conta existir; IP anulado em 6 meses |
| E-mails suprimidos | Provedor | `email_suppressions` | Permanente (hard bounce), 30 dias (soft) |

**O que deliberadamente não existe:** SDP, candidatos ICE, gravação de mídia, metadados de mensagem no `audit_log`, grafo social no `audit_log`, rastreadores, analytics de terceiros.

---

## 3. Minimização

Decisões tomadas em que se optou por guardar menos:

- **`audit_log` não registra mensagens nem contatos.** Metadados de conversa costumam revelar mais que o conteúdo, e a auditoria tem retenção de meses a anos.
- **Sem `user_agent` no `audit_log`.** É vetor de fingerprinting; para investigação, o IP basta.
- **Presença só em memória.** Persistir criaria histórico de quando cada pessoa esteve online.
- **Supressão de e-mail por hash.** A lista responde à única pergunta necessária sem manter cadastro de endereços de quem nem tem conta.
- **`audit_log` da categoria PRIVACY nasce sem IP.** É prova de atendimento a um direito; não precisa de dado de rede para cumprir essa função.

---

## 4. Direitos do titular (Art. 18)

| Inciso | Direito | Como é atendido | Estado |
|---|---|---|---|
| I | Confirmação de tratamento | Política de privacidade + tela de Conta | ✅ |
| II | Acesso aos dados | `GET /users/me/export` — JSON completo, sem intermediário | ✅ |
| III | Correção | `PATCH /users/me`, troca de e-mail com confirmação | ✅ |
| IV | Anonimização / eliminação | `DELETE /users/me` — anonimiza preservando o histórico do interlocutor | ✅ |
| V | Portabilidade | Mesmo endpoint do inciso II, formato aberto e legível | ✅ |
| VI | Eliminação de dados consentidos | Idem inciso IV | ✅ |
| VII | Informação sobre compartilhamento | §5 deste documento | ✅ |
| VIII | Informação sobre não consentir | Política de privacidade | ⚠️ texto a revisar |
| IX | Revogação do consentimento | Exclusão da conta | ⚠️ ver §7.2 |

**Tudo é autoatendimento.** Nenhum direito depende de abrir chamado, o que elimina o prazo de 15 dias do Art. 19 como risco operacional — e elimina também o próprio controlador como gargalo.

### Escopo da exportação

A regra é: **exporta exatamente o que o titular já vê no aplicativo.**

Isso resolve o dilema das mensagens, que têm dois titulares. A conversa inteira já está na tela dele; entregá-la em JSON não revela nada novo. Já do interlocutor entra apenas o mínimo que a interface mostra — username e nome de exibição — nunca o e-mail dele, que o titular jamais viu.

Fora da exportação: hash de senha (é credencial, não dado a devolver) e `audit_log` (contém eventos sobre terceiros e é registro de segurança do controlador).

Limite de um pedido por dia. Não é economia de recurso: a exportação é o arquivo mais sensível que o sistema produz, e uma conta comprometida poderia usá-la para exfiltrar tudo de uma vez.

---

## 5. Exclusão por anonimização

A linha de `users` **nunca é removida**. Apagá-la quebraria as chaves estrangeiras de `messages` e destruiria o histórico legítimo do interlocutor — mensagem é dado com dois titulares, e o direito de eliminação de um não anula o do outro.

Procedimento, em transação única:

1. Sessões revogadas e tokens apagados
2. Notificação enviada **antes** de o endereço sair
3. `username` → `removido_<8 hex>`, `email` → `NULL`, nome, bio e avatar limpos
4. `password_hash` recebe valor aleatório inutilizável — nunca vazio
5. `audit_log`: `actor_user_id` **preservado**, `actor_label` **pseudonimizado**

O item 5 é a parte que resolve o conflito entre auditoria e eliminação: o identificador legível sai, e o vínculo pseudônimo entre eventos permanece. A trilha de segurança sobrevive sem manter dado pessoal.

---

## 6. Compartilhamento com terceiros

| Terceiro | O que recebe | Observação |
|---|---|---|
| Provedor de VPS | Tudo, por hospedar | Exige contrato de operador |
| Provedor de e-mail | Endereço e conteúdo dos e-mails transacionais | Idem. **Pendente:** D-07 |
| STUN público (dev) | IP público de quem faz chamada | **Em produção, usar STUN próprio** — um STUN de terceiro vê o IP de todos os participantes |

Sem CAPTCHA de terceiros, deliberadamente: implicaria transferência internacional de dados no cadastro.

---

## 7. Pendências que exigem decisão jurídica

**7.1 — Bases legais.** As propostas do documento de arquitetura (execução de contrato para a conta, legítimo interesse para segurança) precisam de validação. Em especial: legítimo interesse para o `audit_log` demanda avaliação de impacto documentada.

**7.2 — Revogação do consentimento sem excluir a conta.** Hoje só há tudo ou nada. Se a base legal for consentimento, isso é insuficiente; se for execução de contrato, é adequado. A resposta muda o produto.

**7.3 — Marco Civil.** O Art. 15 pode obrigar a guarda de registros de acesso por 6 meses, o que compete com a eliminação imediata. A retenção do `audit_log` foi fixada em 6 meses justamente para caber nessa hipótese, mas se a instância não é "aplicação de internet" no sentido da lei, guardar por 6 meses passa a ser excesso.

**7.4 — Encarregado.** Nome e contato precisam ser publicados.

**7.5 — Menores de idade.** O Art. 14 exige consentimento de responsável. Hoje não há verificação de idade nem no cadastro nem em lugar algum.

**7.6 — Incidente de segurança.** O Art. 48 exige comunicação à ANPD e aos titulares. Não existe procedimento escrito — apenas os meios técnicos (§10 do `SECURITY.md`).

**7.7 — Contratos de operador** com VPS e provedor de e-mail.

---

## 8. Onde os controles estão no código

| Controle | Arquivo |
|---|---|
| Exportação | `privacy/DataExportService.java` |
| Anonimização | `privacy/AccountDeletionService.java` |
| Consentimento versionado | `legal/ConsentService.java` |
| Retenção | `job/CleanupJobs.java`, `call/CallReaper.java` |
| Minimização na auditoria | `audit/AuditLog.java`, `audit/AuditService.java` |
| Supressão de e-mail | `email/EmailSuppressionService.java` |
| Invariantes no banco | `db/migration/V1__init.sql`, `V2__chat.sql` |
