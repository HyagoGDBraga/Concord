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
├── .env.example           modelo de configuracao (copie para .env)
├── backend/               Spring Boot, organizado por feature
│   ├── Dockerfile         estagios dev e producao
│   ├── pom.xml
│   └── src/main/
│       ├── java/app/concord/
│       └── resources/     application*.yml, db/migration/
└── frontend/              Next.js
    ├── Dockerfile         estagios dev e producao
    └── src/
        ├── app/           rotas (App Router)
        ├── lib/           clientes de API, WebSocket, configuracao
        └── ...            features/, components/, hooks/ chegam na Fase 3
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

## Roadmap

| Fase | Escopo | Estado |
| --- | --- | --- |
| 1 | Fundacao: Docker, Caddy, esqueletos, health check | **concluida** |
| 2 | Banco, autenticacao por sessao, cadastro com verificacao de e-mail | a seguir |
| 3 | Contatos e chat via REST |  |
| 4 | WebSocket/STOMP, tempo real, presenca |  |
| 5 | WebRTC: voz, depois video, TURN |  |
| 6 | Compartilhamento de tela |  |
| 7 | Seguranca e LGPD |  |
| 8 | Testes |  |
| 9 | Deploy em producao |  |
| 10 | Aplicativo desktop com Electron |  |
