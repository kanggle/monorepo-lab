-- RBAC core tables for admin-service (TASK-BE-028a).
-- NOTE: this increment keeps admin_operators.id as VARCHAR(36). The data-model.md spec
-- declares BIGINT PK — the BIGINT migration is deferred to TASK-BE-028b so that
-- admin_actions.id (VARCHAR(36) UUID) and operator_id (FK target) share the same
-- string type for this increment.

CREATE TABLE admin_operators (
    id                       VARCHAR(36)   NOT NULL,
    email                    VARCHAR(255)  NOT NULL,
    password_hash            VARCHAR(255)  NOT NULL,
    display_name             VARCHAR(120)  NOT NULL,
    status                   VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    totp_secret_encrypted    VARBINARY(255) NULL,
    totp_enrolled_at         DATETIME(6)   NULL,
    last_login_at            DATETIME(6)   NULL,
    created_at               DATETIME(6)   NOT NULL,
    updated_at               DATETIME(6)   NOT NULL,
    version                  BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE INDEX uk_admin_operators_email (email),
    INDEX idx_admin_operators_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin_roles (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    name         VARCHAR(40)   NOT NULL,
    description  VARCHAR(255)  NOT NULL,
    require_2fa  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE INDEX uk_admin_roles_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin_role_permissions (
    role_id         BIGINT       NOT NULL,
    permission_key  VARCHAR(80)  NOT NULL,
    PRIMARY KEY (role_id, permission_key),
    INDEX idx_admin_role_permissions_permission (permission_key),
    CONSTRAINT fk_admin_role_permissions_role
        FOREIGN KEY (role_id) REFERENCES admin_roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin_operator_roles (
    operator_id  VARCHAR(36)  NOT NULL,
    role_id      BIGINT       NOT NULL,
    granted_at   DATETIME(6)  NOT NULL,
    granted_by   VARCHAR(36)  NULL,
    PRIMARY KEY (operator_id, role_id),
    INDEX idx_admin_operator_roles_role (role_id),
    CONSTRAINT fk_admin_operator_roles_operator
        FOREIGN KEY (operator_id) REFERENCES admin_operators(id) ON DELETE CASCADE,
    CONSTRAINT fk_admin_operator_roles_role
        FOREIGN KEY (role_id) REFERENCES admin_roles(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
