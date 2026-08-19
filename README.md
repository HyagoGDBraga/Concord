# Concord

Aplicativo de comunicacao privado: mensagens em tempo real, chamadas de voz e
video e compartilhamento de tela, para um grupo pequeno de usuarios.

**Estado atual: Fase 1 — fundacao.** Nao ha autenticacao nem funcionalidade de
produto ainda. O que existe roda de ponta a ponta: navegador → Caddy → Spring
Boot → PostgreSQL.

| Camada | Tecnologia |
| --- | --- |
| Frontend | Next.js 15, React 19, TypeScript, Tailwind CSS v4 |
| Backend | Java 21, Spring Boot 3.5, JPA/Hibernate, Flyway |
| Banco | PostgreSQL 16 |
| Proxy | Caddy 2 |
| Tempo real | STOMP sobre WebSocket (Fase 4) |
| Midia | WebRTC P2P + coturn (Fase 5) |
| Desktop | Electron (Fase 10) |

Documento de arquitetura e decisoes:
`docs/CONCORD-00-VISAO-GERAL-E-ARQUITETURA.md`.

---

## Pre-requisitos

- Docker Engine 24+ e Docker Compose v2 (`docker compose version`)
- 4 GB de RAM livres
- Portas livres no host: **80**, **5432**, **8025**

Nada mais precisa estar instalado. Java, Maven e Node rodam dentro dos
containers.

---

## Subir o ambiente

```bash
git clone <url-do-repositorio> concord
cd concord

cp .env.example .env      # o .env NUNCA e commitado

docker compose up -d --build
```

A primeira execucao demora alguns minutos: o Maven baixa as dependencias e o
npm instala o frontend. As seguintes sobem em segundos, porque o repositorio
Maven e os `node_modules` ficam em volumes.

Acompanhe ate o backend ficar pronto:

```bash
docker compose logs -f backend
# aguarde a linha: Started ConcordApplication in X seconds
```

| Endereco | O que e |
| --- | --- |
| <http://localhost> | Aplicacao (painel de diagnostico da Fase 1) |
| <http://localhost/api/actuator/health> | Health check do backend |
| <http://localhost:8025> | Mailpit — caixa de entrada de dev |
| `localhost:5432` | PostgreSQL, para DBeaver/psql |

**Criterio de conclusao da Fase 1:** abrir <http://localhost> e ver os quatro
elos da cadeia em verde.

---

## Primeiro acesso (Fase 2)

O sistema nasce sem nenhum administrador. Para criar o primeiro:

1. Preencha `CONCORD_BOOTSTRAP_ADMIN_EMAIL` no `.env` com o e-mail que voce vai
   usar e suba o ambiente.
2. Acesse <http://localhost/register> e crie a conta com **esse** e-mail.
3. Abra o Mailpit em <http://localhost:8025> e clique no link de confirmacao.
4. Ao confirmar, a conta e promovida a `ADMIN` e o evento fica registrado no
   `audit_log`.
5. **Esvazie a variavel** `CONCORD_BOOTSTRAP_ADMIN_EMAIL` do `.env`.

O estado "bootstrap concluido" e persistente (tabela `app_settings`). Depois que
existir um administrador, repor a variavel nao promove mais ninguem — nem apos
reiniciar o backend.

> Nenhuma senha de administrador existe em migration, seed ou codigo versionado.
> A conta e criada pelo fluxo normal de cadastro.

### Telas disponiveis

| Rota | O que faz |
|---|---|
| `/login` · `/register` | Entrar e criar conta |
| `/verify-email` · `/reset-password` · `/confirm-email-change` | Destinos dos links enviados por e-mail |
| `/forgot-password` | Pedir redefinicao de senha |
| `/` | Inicio da area autenticada |
| `/contacts` | Adicionar contato por nome de usuario, aceitar pedidos, bloquear |
| `/conversations` | Lista de conversas com previa e nao lidas |
| `/conversations/{id}` | Conversa por texto em tempo real, com chamada de voz e video |
| `/settings` | Perfil, senha, e-mail, dispositivos, exportacao de dados, exclusao |
| `/termos` · `/privacidade` | Documentos legais, publicos |
| `/diagnostics` | Estado da cadeia navegador -> Caddy -> backend -> banco |
| `/admin/users` · `/admin/audit` | Painel administrativo (somente `ADMIN`) |

Todo e-mail em desenvolvimento cai no Mailpit. **Nada sai para a internet.**

## Chamadas de voz e video

Em desenvolvimento na mesma maquina ou na mesma rede local, o STUN resolve e o
TURN nem entra em acao — as chamadas funcionam sem subir nada a mais.

Para exercitar o caminho com TURN (obrigatorio em producao, onde ha NAT
simetrico e redes que bloqueiam UDP):

```bash
# gere um segredo e coloque em TURN_SECRET no .env
openssl rand -hex 32

# suba o coturn, que fica fora do ciclo normal
docker compose --profile turn up -d
```

O segredo do coturn **nunca** chega ao navegador. O backend deriva dele
credenciais validas por uma hora e as entrega em `GET /api/webrtc/ice`.
Credencial estatica embutida no frontend transformaria o servidor em relay
aberto para qualquer um.

O navegador so libera microfone, camera e captura de tela em contexto seguro.
`http://localhost` conta como seguro por decisao dos proprios navegadores; em
qualquer outro endereco, e preciso HTTPS.

### Compartilhamento de tela

Durante uma chamada ativa, o botao **Compartilhar tela** substitui o video
enviado. A troca usa `replaceTrack` na conexao que ja existe — mesmo codec,
mesmo transporte, sem renegociacao — e por isso e instantanea.

O audio do sistema **nao** e capturado, por escolha: transmitir a saida de som
da maquina inteira e a forma mais facil de vazar sem querer uma notificacao ou
uma outra conversa.

No Firefox, cada troca de tela reabre o seletor do navegador; e comportamento do
proprio navegador, nao da aplicacao.

## Aplicativo desktop

```bash
cd desktop
npm install
npm run dev                                    # contra o ambiente local
npm start -- --url=https://seu.dominio.com     # contra producao
npm run dist:linux                             # gera AppImage e .deb
```

O desktop carrega a mesma interface do servidor — nao ha tela duplicada. Ele
acrescenta o que o navegador nao da: seletor de tela proprio, notificacao de
chamada no sistema operacional e conexao que sobrevive a janela minimizada.

## Comandos do dia a dia

```bash
docker compose ps                 # estado dos containers
docker compose logs -f backend    # logs de um servico
docker compose restart backend    # reiniciar um servico
docker compose down               # parar tudo (dados preservados)
docker compose down -v            # parar e APAGAR o banco
docker compose up -d --build      # reconstruir apos mudar Dockerfile ou pom.xml
```

### Backend

Alteracoes em `backend/src` sao recompiladas automaticamente pelo Spring
DevTools; o container nao precisa ser reiniciado. Mudanca em `pom.xml` exige
`docker compose up -d --build backend`.

Testes (precisam de Docker rodando na maquina, por causa do Testcontainers):

```bash
docker compose exec backend mvn test
```

### Frontend

Alteracoes em `frontend/src` recarregam sozinhas. Instalar uma dependencia:

```bash
docker compose exec frontend npm install <pacote>
docker compose restart frontend
```

Verificacoes:

```bash
docker compose exec frontend npm run typecheck

# Testes ponta a ponta (exigem o ambiente no ar)
cd frontend && npx playwright install --with-deps chromium && npm run test:e2e
docker compose exec frontend npm run lint
docker compose exec frontend npm run format
```

### Banco

```bash
docker compose exec postgres psql -U concord -d concord
```

O schema pertence ao Flyway (`backend/src/main/resources/db/migration`). O
Hibernate roda com `ddl-auto: validate` e nunca altera tabela. As migrations
comecam na Fase 2.

---

## Estrutura

```
concord/
├── docker-compose.yml     ambiente de desenvolvimento
├── Caddyfile              roteamento / e /api na mesma origem
├── coturn/                configuracao do servidor TURN
├── .github/workflows/     integracao continua
├── docker-compose.prod.yml  override de producao
├── Caddyfile.prod         TLS automatico
├── scripts/               backup, restore, deploy, preparacao da VM
├── desktop/               aplicativo Electron
├── .env.example           modelo de configuracao (copie para .env)
├── backend/               Spring Boot, organizado por feature
│   ├── Dockerfile         estagios dev e producao
│   ├── pom.xml
│   └── src/
│       ├── main/java/app/concord/
│       │   ├── auth/      login, cadastro, sessao, politica de senha
│       │   ├── contact/   contatos e bloqueio
│       │   ├── conversation/ conversas diretas e participacao
│       │   ├── message/   mensagens, cursor de paginacao
│       │   ├── ws/        WebSocket/STOMP, eventos, presenca de conexao
│       │   ├── presence/  quem esta online agora
│       │   ├── call/      ciclo de vida da chamada e sinalizacao
│       │   ├── webrtc/    credenciais efemeras de STUN/TURN
│       │   ├── legal/     consentimento versionado
│       │   ├── privacy/   exclusao e exportacao de dados
│       │   ├── user/      entidade, conta, perfil
│       │   ├── admin/     painel administrativo e bootstrap do 1o admin
│       │   ├── audit/     audit_log (SECURITY, ADMIN, PRIVACY)
│       │   ├── token/     tokens de acao enviados por e-mail
│       │   ├── email/     EmailService -> EmailProvider
│       │   ├── privacy/   exclusao de conta por anonimizacao
│       │   ├── settings/  configuracao alteravel em runtime
│       │   ├── job/       retencao e limpeza agendadas
│       │   ├── common/    erros, rate limit, request id
│       │   └── config/    seguranca, propriedades, filtros
│       ├── main/resources/  application*.yml, db/migration/, email/, security/
│       └── test/java/app/concord/  testes unitarios e de integracao
└── frontend/              Next.js
    ├── Dockerfile         estagios dev e producao
    └── src/
        ├── app/
        │   ├── (auth)/    login, cadastro, verificacao, reset de senha
        │   └── (app)/     area autenticada, contatos, conversas, conta, /admin
        ├── components/    kit de UI proprio
        └── lib/           apiClient, sessao, tipos, configuracao
```

---

## Configuracao

Toda configuracao vem de variaveis de ambiente. Nenhum segredo entra no
codigo-fonte ou no `application.yml`. Veja `.env.example`, onde cada variavel
esta marcada com a fase em que passa a ser necessaria.

Duas regras que valem desde agora:

1. **`.env` nunca e commitado.** Ja esta no `.gitignore`.
2. **`NEXT_PUBLIC_*` e publico.** Essas variaveis vao para dentro do bundle
   JavaScript e sao legiveis por qualquer usuario. Segredo nenhum ali —
   inclusive as credenciais de TURN, que serao efemeras e emitidas pelo backend
   em tempo de execucao.

---

## Solucao de problemas

**A porta 80 ja esta em uso.**
Descubra quem ocupa (`sudo lsof -i :80`) ou troque a publicacao do servico
`caddy` para `"8080:80"` e acesse <http://localhost:8080>.

**O painel mostra "Caddy fora do ar".**
Nada respondeu na porta 80. Rode `docker compose ps` e veja se o container
`concord-caddy` esta de pe.

**O painel mostra "Spring Boot fora do ar".**
O proxy respondeu, mas o backend nao. Normalmente e a primeira subida ainda em
andamento — o Maven leva alguns minutos. Acompanhe com
`docker compose logs -f backend`.

**O backend nao conecta no banco.**
Confirme que `DATABASE_URL`, `POSTGRES_USER` e `POSTGRES_PASSWORD` no `.env`
batem com as do servico `postgres`. Se voce trocou a senha depois de o volume
ja existir, o Postgres mantem a senha antiga: `docker compose down -v` recria
o banco do zero (apaga os dados).

**O hot reload nao funciona no Windows/macOS.**
`WATCHPACK_POLLING=true` ja esta no compose para o frontend. Se o backend nao
recarregar, confirme que a montagem de `./backend/src` aparece em
`docker compose config`.

---

## Documentacao

| Arquivo | Conteudo |
|---|---|
| `docs/CONCORD-00-VISAO-GERAL-E-ARQUITETURA.md` | Requisitos, ADRs, decisoes D-01 a D-09, roadmap |
| `docs/CONCORD-02-SESSAO-E-AUTENTICACAO.md` | Modelo de sessao, CSRF, autenticacao do WebSocket |
| `docs/SECURITY.md` | Modelo de ameaca, controles, limitacoes conhecidas |
| `docs/LGPD.md` | Inventario de dados, direitos do titular, pendencias juridicas |
| `docs/DATABASE.md` | Schema, invariantes, retencao, operacao |
| `docs/API.md` | Referencia dos endpoints REST |
| `docs/DEPLOY.md` | Producao em VM Oracle Cloud Always Free |
| `docs/WEBSOCKET.md` | STOMP, destinos, eventos, reconexao |
| `docs/WEBRTC.md` | Sinalizacao, TURN, compartilhamento de tela |
| `docs/DESKTOP.md` | Aplicativo Electron, empacotamento, limitacoes |

## Roadmap

| Fase | Escopo | Estado |
| --- | --- | --- |
| 1 | Fundacao: Docker, Caddy, esqueletos, health check | **concluida** |
| 2 | Banco, autenticacao por sessao, cadastro com verificacao de e-mail | **concluida** |
| 3 | Contatos e chat via REST | **concluida** |
| 4 | WebSocket/STOMP, tempo real, presenca | **concluida** |
| 5 | WebRTC: voz, depois video, TURN | **concluida** |
| 6 | Compartilhamento de tela | **concluida** |
| 7 | Seguranca e LGPD | **concluida** |
| 8 | Testes | **concluida** |
| 9 | Deploy em producao | **concluida** |
| 10 | Aplicativo desktop com Electron | **concluida** |
