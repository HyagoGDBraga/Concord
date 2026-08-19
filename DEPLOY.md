# Concord — Deploy

Alvo: **uma VM Oracle Cloud Always Free (ARM, Ubuntu 24.04)**, com domínio próprio e TLS automático.

Por que uma VM e não um PaaS: o Concord tem estado em memória por decisão — broker STOMP, presença e rate limit — e quatro tarefas agendadas. Qualquer plataforma que hiberne, reinicie por invocação ou rode múltiplas instâncias quebra isso. Ver §7.

---

## 1. A máquina

**Cota Always Free (verifique antes, ela mudou):** a Oracle reduziu a alocação Ampere A1 de 4 OCPU / 24 GB para **2 OCPU / 12 GB** em 15 de junho de 2026, sem anúncio público. Confirme os números atuais na documentação antes de dimensionar.

2 vCPU e 12 GB continuam folgados para o Concord. O consumo esperado, com os limites do `docker-compose.prod.yml`:

| Serviço | Memória |
|---|---|
| PostgreSQL | até 2 GB |
| Backend (JVM) | até 1,5 GB |
| Frontend | até 512 MB |
| Caddy + coturn | ~200 MB |
| **Total** | **~4,2 GB** de 12 |

**Ao criar a instância:**

- Imagem: **Canonical Ubuntu 24.04 Minimal aarch64** — o `aarch64` é obrigatório para o shape ARM
- Shape: `VM.Standard.A1.Flex`
- Região: escolha bem, porque **os recursos Always Free ficam presos à região inicial e ela não pode ser trocada depois**
- É comum receber `Out of capacity` na criação. Insista em horários diferentes ou tente outro *availability domain*

**ARM não é problema aqui:** todas as imagens do projeto (Temurin 21, Node 22, PostgreSQL 16, Caddy 2, coturn 4.6) publicam `arm64`. O build acontece na própria máquina, então não é preciso cross-compile.

---

## 2. Preparação

```bash
ssh ubuntu@SEU_IP
curl -fsSL https://raw.githubusercontent.com/SEU_USUARIO/concord/main/scripts/oracle-setup.sh -o setup.sh
bash setup.sh
exit && ssh ubuntu@SEU_IP   # reentrar para o grupo docker valer
```

### Os dois firewalls

**É o ponto que mais custa tempo.** A Oracle tem firewall em duas camadas e ambas negam por padrão:

1. **Security List da VCN** (painel web) — precisa ser feito à mão:
   `Networking > Virtual Cloud Networks > sua VCN > Security Lists > Default > Add Ingress Rules`

2. **iptables dentro da VM** — a imagem Ubuntu da Oracle já vem com regras que aceitam **só SSH**. O `oracle-setup.sh` cuida disso.

Liberar tudo no painel e o site continuar sem abrir é o sintoma clássico de ter esquecido a camada 2.

| Porta | Protocolo | Para quê |
|---|---|---|
| 80 | TCP | HTTP + desafio ACME |
| 443 | TCP | HTTPS |
| 443 | UDP | HTTP/3 |
| 3478 | TCP + UDP | TURN |
| 49160–49200 | UDP | Relay de mídia do TURN |

---

## 3. DNS

Aponte antes de subir:

```
A     concord.seudominio.com    →  SEU_IP_PUBLICO
```

Confirme a propagação: `dig +short concord.seudominio.com`

**Se o DNS ainda não propagou, o desafio ACME falha** e o Caddy fica em ciclo de tentativa. É a causa nº 1 de "subiu mas não abre".

---

## 4. Configuração

```bash
git clone https://github.com/SEU_USUARIO/concord.git /opt/concord
cd /opt/concord
cp .env.example .env

# Gere os segredos. Nenhum deles deve ser inventado à mão.
echo "POSTGRES_PASSWORD=$(openssl rand -base64 32 | tr -d '/+=')"
echo "TURN_SECRET=$(openssl rand -hex 32)"
echo "EMAIL_WEBHOOK_SECRET=$(openssl rand -hex 32)"

nano .env
chmod 600 .env
```

Valores que **precisam** mudar em relação ao desenvolvimento:

```bash
CONCORD_DOMAIN=concord.seudominio.com
ACME_EMAIL=voce@seudominio.com
APP_PUBLIC_URL=https://concord.seudominio.com

# Sem isto, o cookie de sessão viaja sem a flag Secure.
SESSION_COOKIE_SECURE=true

# Provedor transacional real. Mailpit não existe em produção.
SMTP_HOST=smtp.seuprovedor.com
SMTP_PORT=587
SMTP_USERNAME=...
SMTP_PASSWORD=...
SMTP_AUTH=true
SMTP_STARTTLS=true
MAIL_FROM=nao-responda@seudominio.com

# TURN próprio. Não use STUN de terceiro em produção: ele vê o IP público de
# todo mundo que faz chamada aqui.
TURN_ENABLED=true
TURN_STUN_URL=stun:concord.seudominio.com:3478
TURN_URLS=turn:concord.seudominio.com:3478?transport=udp,turn:concord.seudominio.com:3478?transport=tcp
TURN_REALM=concord.seudominio.com
TURN_EXTERNAL_IP=SEU_IP_PUBLICO

# Preencha, crie a conta com este e-mail, confirme — e ESVAZIE depois.
CONCORD_BOOTSTRAP_ADMIN_EMAIL=voce@seudominio.com
```

### E-mail

O provedor precisa de **SPF, DKIM e DMARC** configurados no DNS do domínio. Sem eles, os e-mails de verificação vão para spam — e cadastro que não confirma é cadastro que não existe, porque o job expurga em 7 dias.

---

## 5. Subida

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
docker compose logs -f backend    # aguarde "Started ConcordApplication"
curl -I https://concord.seudominio.com
```

O primeiro build leva de 5 a 15 minutos no ARM — Maven baixa o mundo uma vez.

### Primeiro administrador

1. Acesse `/register` e cadastre-se com o e-mail do `CONCORD_BOOTSTRAP_ADMIN_EMAIL`
2. Confirme pelo link recebido — a promoção acontece nesse momento
3. **Esvazie a variável** e reinicie o backend

O estado "bootstrap concluído" é persistente. Repor a variável depois não promove mais ninguém.

---

## 6. Operação

```bash
# Atualizar (faz backup, sobe, verifica saúde, orienta rollback se falhar)
./scripts/deploy.sh

# Backup manual
./scripts/backup.sh

# Restaurar
./scripts/restore.sh backups/concord-AAAAMMDD-HHMMSS.dump

# Logs
docker compose logs -f --tail=100 backend
```

**Backup diário no cron:**

```bash
crontab -e
# 4h — depois dos jobs de retenção, que rodam às 3h
0 4 * * * cd /opt/concord && ./scripts/backup.sh >> /var/log/concord-backup.log 2>&1
```

Duas coisas sobre backup que costumam ser aprendidas tarde:

1. **O script verifica o dump com `pg_restore --list` logo após gerá-lo.** Backup que nunca foi lido não é backup, é um arquivo.
2. **Backup no mesmo disco do banco protege contra `DROP TABLE`, e contra nada mais.** Configure `BACKUP_REMOTE_DEST` para um destino externo — a Oracle oferece Object Storage no próprio Always Free.

O dump contém **mensagens em texto claro**. Permissão 600 e destino cifrado.

---

## 7. Por que não um PaaS gratuito

| Recurso do Concord | O que a hibernação faz |
|---|---|
| `SimpleBroker` STOMP em memória | Assinaturas somem; tempo real para |
| `PresenceService` em memória | Todos aparecem offline; ninguém consegue ser chamado |
| `RateLimiter` em memória | Proteção contra força bruta zera |
| 4 jobs `@Scheduled` | Não rodam sem processo vivo — retenção da LGPD deixa de ser aplicada |
| coturn (UDP) | Nenhum PaaS serverless faz UDP |

Vercel especificamente: mesmo com o suporte nativo a WebSocket lançado em junho de 2026, a conexão é uma invocação de função com teto de duração, e estado em memória não sobrevive à reconexão. Separar frontend e backend também quebraria a premissa de **mesma origem** que sustenta o cookie `SameSite=Lax`, o CSRF e a autenticação do WebSocket (documento 02).

**Se quiser sair da Oracle:** Hetzner CX22 (~€4/mês, x86) roda tudo isto sem mudança nenhuma nos arquivos, e sem o risco de `Out of capacity`.

---

## 8. Diagnóstico

| Sintoma | Causa provável |
|---|---|
| Domínio não abre, sem erro no Caddy | iptables dentro da VM (§2) ou Security List (§2) |
| `unable to solve challenge` no Caddy | DNS não propagou, ou porta 80 fechada |
| Backend reinicia sozinho | Memória. Confira `JAVA_TOOL_OPTIONS` e `docker stats` |
| Login funciona, sessão não persiste | `SESSION_COOKIE_SECURE=true` sem HTTPS de verdade |
| E-mail não chega | SPF/DKIM/DMARC ausentes, ou endereço na lista de supressão |
| Chamada conecta e cai | TURN inacessível: portas 3478 e 49160–49200 |
| Chamada só funciona na mesma rede | `TURN_EXTERNAL_IP` errado ou TURN desligado |

```bash
docker compose ps
docker stats --no-stream
docker compose exec postgres psql -U concord -d concord -c '\dt'
curl -s https://concord.seudominio.com/api/actuator/health
```

---

## 9. Checklist antes de considerar no ar

- [ ] Segredos gerados com `openssl`, `.env` em `chmod 600`
- [ ] `SESSION_COOKIE_SECURE=true`
- [ ] HTTPS válido, HTTP redirecionando
- [ ] SPF, DKIM e DMARC no DNS
- [ ] E-mail de verificação chegando na caixa de entrada, não no spam
- [ ] Primeiro admin promovido e variável esvaziada
- [ ] Chamada testada **entre redes diferentes** (uma no Wi-Fi, outra no 4G) — é o único teste que exercita o TURN
- [ ] Backup no cron **e uma restauração já testada**
- [ ] Destino externo de backup configurado
- [ ] Termos e política de privacidade revisados por advogado (§7 do `LGPD.md`)
- [ ] `REGISTRATION_OPEN` ajustado para o que você quer

O item da restauração testada não é formalidade. A primeira vez que se descobre que o backup não presta é sempre no pior momento possível.
