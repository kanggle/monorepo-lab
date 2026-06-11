#!/usr/bin/env bash
# Postgres init script — creates per-service databases on first container boot.
# Idempotent: skips creation if a database already exists. Runs as the
# POSTGRES_USER configured in docker-compose.yml.
set -euo pipefail

DEFAULT_USER="${POSTGRES_USER:-fanplatform}"

create_db_if_missing() {
    local dbname="$1"
    if [ -z "$dbname" ] || [ "$dbname" = "$POSTGRES_DB" ]; then
        return 0
    fi
    local exists
    exists=$(psql -tAc "SELECT 1 FROM pg_database WHERE datname='${dbname}'" -U "$DEFAULT_USER" -d postgres || true)
    if [ "$exists" = "1" ]; then
        echo "[init] database '$dbname' already exists, skipping."
        return 0
    fi
    echo "[init] creating database '$dbname' owned by '$DEFAULT_USER'."
    psql -U "$DEFAULT_USER" -d postgres -c "CREATE DATABASE \"${dbname}\" OWNER \"${DEFAULT_USER}\";"
}

create_db_if_missing "${POSTGRES_DB_COMMUNITY:-fanplatform_community}"
create_db_if_missing "${POSTGRES_DB_ARTIST:-fanplatform_artist}"
create_db_if_missing "${POSTGRES_DB_MEMBERSHIP:-fanplatform_membership}"
create_db_if_missing "${POSTGRES_DB_NOTIFICATION:-fanplatform_notification}"
