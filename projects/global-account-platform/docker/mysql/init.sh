#!/bin/bash
# =============================================================================
# Global Account Platform — MySQL Initialization Script
# =============================================================================
# This script runs once when the MySQL container is first created.
# It creates 4 databases and corresponding service users.
#
# Service passwords are injected via environment variables.
# If unset, defaults are used for local development convenience.
# =============================================================================

AUTH_DB_PASSWORD="${AUTH_DB_PASSWORD:-auth_pass}"
ACCOUNT_DB_PASSWORD="${ACCOUNT_DB_PASSWORD:-account_pass}"
SECURITY_DB_PASSWORD="${SECURITY_DB_PASSWORD:-security_pass}"
ADMIN_DB_PASSWORD="${ADMIN_DB_PASSWORD:-admin_pass}"
COMMUNITY_DB_PASSWORD="${COMMUNITY_DB_PASSWORD:-community_pass}"
MEMBERSHIP_DB_PASSWORD="${MEMBERSHIP_DB_PASSWORD:-membership_pass}"

SERVICE_PRIVILEGES="SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES, TRIGGER, CREATE ROUTINE, ALTER ROUTINE, EXECUTE"

mysql -u root -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
-- ---------------------------------------------------------------------------
-- Databases
-- ---------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS \`auth_db\`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS \`account_db\`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS \`security_db\`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS \`admin_db\`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS \`community_db\`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS \`membership_db\`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Service Users (least-privilege for each service)
-- ---------------------------------------------------------------------------

-- auth-service user
CREATE USER IF NOT EXISTS 'auth_user'@'%' IDENTIFIED BY '${AUTH_DB_PASSWORD}';
GRANT ${SERVICE_PRIVILEGES} ON \`auth_db\`.* TO 'auth_user'@'%';

-- account-service user
CREATE USER IF NOT EXISTS 'account_user'@'%' IDENTIFIED BY '${ACCOUNT_DB_PASSWORD}';
GRANT ${SERVICE_PRIVILEGES} ON \`account_db\`.* TO 'account_user'@'%';

-- security-service user
CREATE USER IF NOT EXISTS 'security_user'@'%' IDENTIFIED BY '${SECURITY_DB_PASSWORD}';
GRANT ${SERVICE_PRIVILEGES} ON \`security_db\`.* TO 'security_user'@'%';

-- admin-service user
CREATE USER IF NOT EXISTS 'admin_user'@'%' IDENTIFIED BY '${ADMIN_DB_PASSWORD}';
GRANT ${SERVICE_PRIVILEGES} ON \`admin_db\`.* TO 'admin_user'@'%';

-- community-service user
CREATE USER IF NOT EXISTS 'community_user'@'%' IDENTIFIED BY '${COMMUNITY_DB_PASSWORD}';
GRANT ${SERVICE_PRIVILEGES} ON \`community_db\`.* TO 'community_user'@'%';

-- membership-service user
CREATE USER IF NOT EXISTS 'membership_user'@'%' IDENTIFIED BY '${MEMBERSHIP_DB_PASSWORD}';
GRANT ${SERVICE_PRIVILEGES} ON \`membership_db\`.* TO 'membership_user'@'%';

FLUSH PRIVILEGES;
EOSQL
