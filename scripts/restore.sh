#!/usr/bin/env bash
#
# Restauração do backup do Concord.
#
# DESTRUTIVO: substitui o conteúdo atual do banco. Pede confirmação explícita.
#
# Uso:
#   ./scripts/restore.sh backups/concord-20260115-040000.dump

set -euo pipefail

cd "$(dirname "$0")/.."

ARQUIVO="${1:-}"
if [ -z "$ARQUIVO" ] || [ ! -f "$ARQUIVO" ]; then
	echo "Uso: $0 <arquivo.dump>" >&2
	exit 1
fi

# shellcheck disable=SC1091
[ -f .env ] && set -a && source .env && set +a

DB_NAME="${POSTGRES_DB:-concord}"
DB_USER="${POSTGRES_USER:-concord}"

echo "Isto vai SUBSTITUIR o conteúdo de '$DB_NAME' pelo backup:"
echo "  $ARQUIVO ($(du -h "$ARQUIVO" | cut -f1), de $(date -r "$ARQUIVO" -Is))"
echo
read -r -p "Digite RESTAURAR para confirmar: " CONFIRMACAO
[ "$CONFIRMACAO" = "RESTAURAR" ] || { echo "Cancelado."; exit 1; }

# O backend precisa estar parado: restaurar com a aplicação escrevendo produz
# um estado que não corresponde nem ao backup nem ao que havia antes.
echo "Parando o backend..."
docker compose stop backend

echo "Restaurando..."
docker compose exec -T postgres \
	pg_restore -U "$DB_USER" -d "$DB_NAME" --clean --if-exists --no-owner \
	< "$ARQUIVO"

echo "Subindo o backend..."
docker compose start backend

echo "Pronto. Confira as migrations com:"
echo "  docker compose exec postgres psql -U $DB_USER -d $DB_NAME -c 'SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;'"
