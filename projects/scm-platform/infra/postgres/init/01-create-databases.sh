#!/bin/bash
# scm-platform: create per-service databases on first Postgres startup.
# TASK-SCM-BE-002: scm_procurement
# TASK-SCM-BE-003: scm_inventory_visibility
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE scm_procurement;
    GRANT ALL PRIVILEGES ON DATABASE scm_procurement TO $POSTGRES_USER;

    CREATE DATABASE scm_inventory_visibility;
    GRANT ALL PRIVILEGES ON DATABASE scm_inventory_visibility TO $POSTGRES_USER;
EOSQL
