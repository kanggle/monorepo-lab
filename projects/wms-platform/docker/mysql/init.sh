#!/bin/bash
# =============================================================================
# WMS Platform — MySQL Initialization Script
# =============================================================================
# This script runs once when the MySQL container is first created.
# It creates 5 databases and corresponding service users.
#
# Service passwords are injected via environment variables.
# If unset, defaults are used for local development convenience.
# =============================================================================

INBOUND_DB_PASSWORD="${INBOUND_DB_PASSWORD:-inbound_pass}"
INVENTORY_DB_PASSWORD="${INVENTORY_DB_PASSWORD:-inventory_pass}"
OUTBOUND_DB_PASSWORD="${OUTBOUND_DB_PASSWORD:-outbound_pass}"
MASTER_DB_PASSWORD="${MASTER_DB_PASSWORD:-master_pass}"
ADMIN_DB_PASSWORD="${ADMIN_DB_PASSWORD:-admin_pass}"

SERVICE_PRIVILEGES="SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES, TRIGGER, CREATE ROUTINE, ALTER ROUTINE, EXECUTE"

mysql -u root -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
-- ---------------------------------------------------------------------------
-- Databases
-- ---------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS \`inbound_db\`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS \`inventory_db\`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS \`outbound_db\`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS \`master_db\`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS \`admin_db\`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Service Users (least-privilege for each service)
-- ---------------------------------------------------------------------------

-- inbound-service user
CREATE USER IF NOT EXISTS 'inbound_user'@'%' IDENTIFIED BY '${INBOUND_DB_PASSWORD}';
GRANT ${SERVICE_PRIVILEGES} ON \`inbound_db\`.* TO 'inbound_user'@'%';

-- inventory-service user
CREATE USER IF NOT EXISTS 'inventory_user'@'%' IDENTIFIED BY '${INVENTORY_DB_PASSWORD}';
GRANT ${SERVICE_PRIVILEGES} ON \`inventory_db\`.* TO 'inventory_user'@'%';

-- outbound-service user
CREATE USER IF NOT EXISTS 'outbound_user'@'%' IDENTIFIED BY '${OUTBOUND_DB_PASSWORD}';
GRANT ${SERVICE_PRIVILEGES} ON \`outbound_db\`.* TO 'outbound_user'@'%';

-- master-service user
CREATE USER IF NOT EXISTS 'master_user'@'%' IDENTIFIED BY '${MASTER_DB_PASSWORD}';
GRANT ${SERVICE_PRIVILEGES} ON \`master_db\`.* TO 'master_user'@'%';

-- admin-service user
CREATE USER IF NOT EXISTS 'admin_user'@'%' IDENTIFIED BY '${ADMIN_DB_PASSWORD}';
GRANT ${SERVICE_PRIVILEGES} ON \`admin_db\`.* TO 'admin_user'@'%';

FLUSH PRIVILEGES;
EOSQL
