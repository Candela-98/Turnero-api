#!/usr/bin/env bash

set -euo pipefail

if [[ "${SPRING_PROFILES_ACTIVE:-dev}" != "dev" ]]; then
  echo "Refusing to provision outside the dev profile." >&2
  exit 1
fi

if [[ "${DB_HOST:-localhost}" != "localhost" && "${DB_HOST:-localhost}" != "127.0.0.1" ]]; then
  echo "Refusing to provision a non-local database." >&2
  exit 1
fi

: "${GOOGLE_OWNER_SUB:?Set GOOGLE_OWNER_SUB to the verified Google subject.}"
: "${GOOGLE_OWNER_EMAIL:?Set GOOGLE_OWNER_EMAIL to the verified Google email.}"

PGHOST="${DB_HOST:-localhost}" \
PGPORT="${DB_PORT:-5432}" \
PGDATABASE="${DB_NAME:-turnero_dev}" \
PGUSER="${DB_USERNAME:-turnero}" \
PGPASSWORD="${DB_PASSWORD:-turnero}" \
psql --set=ON_ERROR_STOP=1 \
  --set=owner_sub="$GOOGLE_OWNER_SUB" \
  --set=owner_email="$GOOGLE_OWNER_EMAIL" <<'SQL'
WITH updated AS (
  UPDATE users
  SET auth_subject = :'owner_sub', email = :'owner_email', updated_at = NOW()
  WHERE id = 1 AND auth_provider = 'GOOGLE' AND role = 'OWNER'
  RETURNING id
)
SELECT CASE WHEN COUNT(*) = 1 THEN 1 ELSE 1 / 0 END AS owner_updated FROM updated;
SQL

echo "Local demo OWNER provisioned. No credentials were stored in the repository."
