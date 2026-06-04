-- erp-platform MySQL init script (mounted to /docker-entrypoint-initdb.d).
-- The container's MYSQL_DATABASE creates masterdata-service's erp_db; this
-- script additionally creates read-model-service's own database
-- erp_read_model_db (CQRS read store — separate from erp_db) and grants the
-- shared application user (erp) access to it. Flyway in read-model-service owns
-- the schema inside this database (V1__init.sql). TASK-ERP-BE-007.
CREATE DATABASE IF NOT EXISTS erp_read_model_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON erp_read_model_db.* TO 'erp'@'%';
FLUSH PRIVILEGES;
