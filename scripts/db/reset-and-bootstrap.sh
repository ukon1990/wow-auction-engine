#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "" ]]; then
  echo "Usage: $0 <env>"
  echo "  env: local | staging | production"
  exit 1
fi

ENV_NAME="$1"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_DIR="${BACKUP_DIR:-backups/${ENV_NAME}/${TIMESTAMP}}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-dbo}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-root}"

if [[ "${ENV_NAME}" == "production" && "${ALLOW_PRODUCTION_RESET:-false}" != "true" ]]; then
  echo "Refusing production reset without ALLOW_PRODUCTION_RESET=true."
  exit 1
fi

mkdir -p "${BACKUP_DIR}"

echo "Creating backups in ${BACKUP_DIR}..."
mysqldump -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USER}" -p"${DB_PASSWORD}" --single-transaction --routines --triggers "${DB_NAME}" > "${BACKUP_DIR}/full.sql"
mysqldump -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USER}" -p"${DB_PASSWORD}" --no-data "${DB_NAME}" > "${BACKUP_DIR}/schema.sql"
mysqldump -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USER}" -p"${DB_PASSWORD}" --no-create-info --skip-triggers "${DB_NAME}" flyway_schema_history > "${BACKUP_DIR}/flyway_schema_history.sql" || true

echo "Dropping and recreating database ${DB_NAME}..."
mysql -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USER}" -p"${DB_PASSWORD}" -e "DROP DATABASE IF EXISTS \`${DB_NAME}\`; CREATE DATABASE \`${DB_NAME}\` CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci;"

echo "Database reset complete. Start application to apply Flyway bootstrap migration."
echo "Example:"
echo "  BLIZZARD_CLIENT_ID=... BLIZZARD_CLIENT_SECRET=... WAE_BLIZZARD_REGIONS=Europe ./mvnw spring-boot:run"
