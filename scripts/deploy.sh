#!/usr/bin/env bash
#
# Atualização do Concord em produção.
#
# Faz backup antes, sobe, espera o health check e avisa se não subiu. Não é
# deploy sem downtime: com instância única e broker em memória, uma janela de
# poucos segundos é inevitável — e tentar disfarçá-la com dois containers
# quebraria a presença e as chamadas.
#
# Uso:  ./scripts/deploy.sh

set -euo pipefail

cd "$(dirname "$0")/.."

COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml"

echo "==> Backup antes de qualquer alteração"
./scripts/backup.sh

echo "==> Atualizando o código"
git pull --ff-only

echo "==> Construindo imagens"
$COMPOSE build

echo "==> Subindo"
# As migrations do Flyway rodam na inicialização do backend. Se uma falhar, a
# aplicação não sobe — e o health check abaixo detecta.
$COMPOSE up -d

echo "==> Aguardando o backend responder"
for i in $(seq 1 90); do
	if curl -sf http://localhost/api/actuator/health > /dev/null 2>&1; then
		echo "==> No ar após ${i}s"
		$COMPOSE ps
		exit 0
	fi
	sleep 1
done

echo "==> FALHA: o backend não respondeu em 90s" >&2
echo "Últimas linhas do log:" >&2
$COMPOSE logs --tail=50 backend >&2
echo >&2
echo "Para voltar à versão anterior:" >&2
echo "  git reset --hard HEAD~1 && ./scripts/deploy.sh" >&2
echo "Se uma migration corrompeu o banco, restaure o backup feito no início:" >&2
echo "  ./scripts/restore.sh backups/\$(ls -t backups | head -1)" >&2
exit 1
