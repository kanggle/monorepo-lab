-- erp-platform MySQL init script (mounted to /docker-entrypoint-initdb.d,
-- runs after 01-*). approval-service and notification-service each get their
-- OWN database instead of sharing masterdata's erp_db.
--
-- Why (TASK-ERP-BE-035): masterdata / approval / notification each ship their
-- own V1__init.sql plus generic `outbox` / `processed_events` /
-- `idempotency_keys` tables. Sharing one erp_db collided on BOTH the default
-- `flyway_schema_history` (checksum mismatch — whichever migrated first won,
-- the others crash-looped) AND those identically-named tables. Separate
-- databases give each service its own Flyway history and its own tables —
-- mirroring read-model-service's erp_read_model_db (01-*). Flyway inside each
-- service owns the schema in its database.
CREATE DATABASE IF NOT EXISTS erp_approval_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS erp_notification_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON erp_approval_db.* TO 'erp'@'%';
GRANT ALL PRIVILEGES ON erp_notification_db.* TO 'erp'@'%';
FLUSH PRIVILEGES;
