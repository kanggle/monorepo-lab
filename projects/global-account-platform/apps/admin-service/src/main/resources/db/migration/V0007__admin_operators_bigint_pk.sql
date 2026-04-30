-- TASK-BE-028b1: migrate admin_operators to BIGINT PK with a separate
-- external operator_id (UUID v7). Aligns schema with specs/services/admin-service/data-model.md.
--
-- V0006 seeds roles + role_permissions but NOT admin_operators rows, so the
-- simplest path is DROP + CREATE. admin_operator_roles is likewise empty of
-- operator bindings in dev (FK target did not yet have seeded rows), so
-- dropping the FK and rebuilding the column type is safe.

-- 1. Drop FKs pointing at admin_operators(id) so we can drop the table.
ALTER TABLE admin_operator_roles DROP FOREIGN KEY fk_admin_operator_roles_operator;

-- 2. Replace admin_operators with the canonical BIGINT-PK shape.
DROP TABLE admin_operators;

CREATE TABLE admin_operators (
    id                       BIGINT         NOT NULL AUTO_INCREMENT,
    operator_id              VARCHAR(36)    NOT NULL,
    email                    VARCHAR(255)   NOT NULL,
    password_hash            VARCHAR(255)   NOT NULL,
    display_name             VARCHAR(120)   NOT NULL,
    status                   VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    totp_secret_encrypted    VARBINARY(255) NULL,
    totp_enrolled_at         DATETIME(6)    NULL,
    last_login_at            DATETIME(6)    NULL,
    created_at               DATETIME(6)    NOT NULL,
    updated_at               DATETIME(6)    NOT NULL,
    version                  BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE INDEX uk_admin_operators_operator_id (operator_id),
    UNIQUE INDEX uk_admin_operators_email (email),
    INDEX idx_admin_operators_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Convert admin_operator_roles.operator_id and granted_by to BIGINT, re-add FKs.
ALTER TABLE admin_operator_roles
    MODIFY COLUMN operator_id BIGINT NOT NULL,
    MODIFY COLUMN granted_by  BIGINT NULL;

ALTER TABLE admin_operator_roles
    ADD CONSTRAINT fk_admin_operator_roles_operator
        FOREIGN KEY (operator_id) REFERENCES admin_operators(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_admin_operator_roles_granted_by
        FOREIGN KEY (granted_by) REFERENCES admin_operators(id) ON DELETE SET NULL;
