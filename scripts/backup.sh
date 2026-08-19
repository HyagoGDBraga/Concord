#!/usr/bin/env bash
#
# Backup do PostgreSQL do Concord.
#
# Formato custom (-Fc): comprimido, restaurável seletivamente e independente da
# versão do servidor. Um .sql em texto puro seria maior e mais frágil.
#
# ATENÇÃO: o arquivo gerado contém MENSAGENS EM TEXTO CLARO. É o dado mais
# sensível que o sistema produz. Trate-o como tal: permissão 600, disco
# cifrado, e destino externo também cifrado.
#
# Uso:
#   ./scripts/backup.sh                 # backup local
#   RETENTION_DAYS=30 ./scripts/backup.sh
#
# Em cron (diário às 4h, fora da janela dos jobs de retenção às 3h):
#   0 4 * * * cd /opt/concord && ./scripts/backup.sh >> /var/log/concord-backup.log 2>&1

set -euo pipefail

cd "$(dirname "$0")/.."

BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"

# shellcheck disable=SC1091
[ -f .env ] && set -a && source .env && set +a

DB_NAME="${POSTGRES_DB:-concord}"
DB_USER="${POSTGRES_USER:-concord}"

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

ARQUIVO="$BACKUP_DIR/concord-$TIMESTAMP.dump"

echo "[$(date -Is)] Iniciando backup de $DB_NAME"

# --clean e --if-exists tornam o dump restaurável sobre um banco existente.
docker compose exec -T postgres \
	pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc --clean --if-exists \
	> "$ARQUIVO"

chmod 600 "$ARQUIVO"

TAMANHO="$(du -h "$ARQUIVO" | cut -f1)"
echo "[$(date -Is)] Backup gerado: $ARQUIVO ($TAMANHO)"

# Verificação imediata. Backup que nunca foi lido não é backup — é um arquivo.
# pg_restore --list falha se o dump estiver truncado ou corrompido.
if ! docker compose exec -T postgres pg_restore --list < "$ARQUIVO" > /dev/null 2>&1; then
	echo "[$(date -Is)] ERRO: o dump não passou na verificação de integridade" >&2
	rm -f "$ARQUIVO"
	exit 1
fi
echo "[$(date -Is)] Integridade verificada"

# Rotação
APAGADOS="$(find "$BACKUP_DIR" -name 'concord-*.dump' -mtime "+$RETENTION_DAYS" -print -delete | wc -l)"
[ "$APAGADOS" -gt 0 ] && echo "[$(date -Is)] Backups antigos removidos: $APAGADOS"

# Cópia externa opcional.
#
# Backup que mora no mesmo disco do banco protege contra 'DROP TABLE', e contra
# nada mais. Se a VM for perdida, os dois somem juntos.
if [ -n "${BACKUP_REMOTE_DEST:-}" ]; then
	echo "[$(date -Is)] Enviando para $BACKUP_REMOTE_DEST"
	if command -v rclone > /dev/null; then
		rclone copy "$ARQUIVO" "$BACKUP_REMOTE_DEST" --no-traverse
	elif command -v scp > /dev/null; then
		scp "$ARQUIVO" "$BACKUP_REMOTE_DEST"
	else
		echo "[$(date -Is)] AVISO: nem rclone nem scp disponíveis" >&2
	fi
fi

echo "[$(date -Is)] Concluído"
