#!/bin/bash
# =============================================================================
# WMS Platform — Postgres Initialization Script
# =============================================================================
# Runs once on first container creation.
# Creates one database and one least-privilege user per wms service that owns
# state. Passwords are taken from environment variables with sensible defaults
# for local development.
#
# gateway-service and notification-service do not appear here because they are
# stateless (gateway) or event-consumer-only (notification) in v1.
# =============================================================================

set -euo pipefail

create_role_and_db() {
  local role="$1" password="$2" db="$3"

  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    DO \$\$
    BEGIN
      IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${role}') THEN
        CREATE ROLE ${role} LOGIN PASSWORD '${password}';
      END IF;
    END
    \$\$;

    SELECT 'CREATE DATABASE ${db} OWNER ${role}'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${db}')\gexec

    GRANT ALL PRIVILEGES ON DATABASE ${db} TO ${role};
EOSQL
}

create_role_and_db master    "${MASTER_DB_PASSWORD:-master}"        master_db
create_role_and_db inbound   "${INBOUND_DB_PASSWORD:-inbound}"      inbound_db
create_role_and_db inventory "${INVENTORY_DB_PASSWORD:-inventory}"  inventory_db
create_role_and_db outbound  "${OUTBOUND_DB_PASSWORD:-outbound}"    outbound_db
create_role_and_db admin     "${ADMIN_DB_PASSWORD:-admin}"          admin_db

echo "wms postgres init complete: master_db, inbound_db, inventory_db, outbound_db, admin_db"
