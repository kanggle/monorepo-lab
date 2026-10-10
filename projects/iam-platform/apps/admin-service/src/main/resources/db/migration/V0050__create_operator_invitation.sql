-- TASK-MONO-772 S2 (ADR-MONO-080 D6 · rider R4 · implementer decisions D-1 · D-2 · S1-14) — `operator_invitation`:
-- a company (non-'*') operator is created only by «invite → the verified owner accepts while logged in».
-- data-model.md § `operator_invitation` is the canonical shape; admin-api.md § Operator Invitation the surface.
--
-- *** NET-ZERO: the table is created EMPTY and seeds nothing. *** Rows are written only by the management API
-- (POST /api/admin/operator-invitations · :cancel · :resend) and, from S3, by the acceptance.
--
-- Shape notes:
--   * token_hash = SHA-256 hex of the token. The raw token is stored NOWHERE (R4). A resend overwrites the hash,
--     so the old link dies at that instant.
--   * status ∈ {PENDING, ACCEPTED, CANCELLED}. EXPIRED is NOT a value — expiry is judged at read time from
--     expires_at (no scheduler, no clean-up job).
--   * pending_key — a STORED generated column that is non-NULL only while status = 'PENDING'. Its UNIQUE key is
--     how the DB keeps «at most one PENDING per (tenant_id, email)» (MySQL has no partial unique index, and a
--     UNIQUE index admits many NULLs). The application pre-check answers 409 first; this key stops the race two
--     concurrent INSERTs would otherwise win together (S1-14 — an optimistic lock cannot see another row).
--     The column is not mapped by JPA (ddl-auto=validate checks mapped columns only).
--   * tenant_id is an opaque reference to account-service `tenants.tenant_id` — NO FK (other DB), the same form
--     as tenant_entry_policy / operator_group. CHECK (tenant_id <> '*'): the platform scope is never invited
--     (ADR-MONO-080 D1).
--   * invited_by is the operator who issued the LIVE token (a resend replaces it). Operators are never hard
--     deleted, so the FK is RESTRICT; the two optional actor columns are SET NULL like the sibling tables.
--   * version INT — the sibling-table convention for optimistic locking (resend · cancel · accept race).
-- Forward-only. `CREATE TABLE IF NOT EXISTS` is the existence guard. MySQL 8 enforces CHECK (8.0.16+) and
-- supports indexed STORED generated columns.

CREATE TABLE IF NOT EXISTS operator_invitation (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    invitation_id        VARCHAR(36)   NOT NULL,              -- external UUID v7 (API invitationId)
    tenant_id            VARCHAR(32)   NOT NULL,              -- the inviting tenant; '*' forbidden
    email                VARCHAR(255)  NOT NULL,              -- trimmed + lower-cased; the expected email
    display_name         VARCHAR(120)  NOT NULL,              -- display_name of the operator acceptance creates
    roles                VARCHAR(512)  NOT NULL,              -- comma-separated sorted role names (re-judged at accept)
    token_hash           CHAR(64)      NOT NULL,              -- SHA-256 hex; the raw token is never stored
    status               VARCHAR(16)   NOT NULL,
    expires_at           DATETIME(6)   NOT NULL,              -- issue/resend time + admin.operator-invitation.ttl
    invited_by           BIGINT        NOT NULL,              -- admin_operators.id of the live token's issuer
    last_delivery_status VARCHAR(20)   NULL,                  -- SENT | FAILED_TRANSIENT | FAILED_PERMANENT
    last_delivery_at     DATETIME(6)   NULL,
    accepted_at          DATETIME(6)   NULL,
    accepted_account_id  VARCHAR(36)   NULL,                  -- the accepting pool account (= its oidc_subject)
    accepted_operator_id BIGINT        NULL,
    cancelled_at         DATETIME(6)   NULL,
    cancelled_by         BIGINT        NULL,
    pending_key          VARCHAR(300)
        GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN CONCAT(tenant_id, '|', email) END) STORED,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    version              INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_operator_invitation_invitation_id (invitation_id),
    UNIQUE KEY uk_operator_invitation_token_hash (token_hash),
    UNIQUE KEY uk_operator_invitation_pending_key (pending_key),
    KEY idx_operator_invitation_tenant_status_created (tenant_id, status, created_at),
    CONSTRAINT ck_operator_invitation_tenant_not_platform CHECK (tenant_id <> '*'),
    CONSTRAINT ck_operator_invitation_status CHECK (status IN ('PENDING', 'ACCEPTED', 'CANCELLED')),
    CONSTRAINT fk_operator_invitation_invited_by FOREIGN KEY (invited_by)
        REFERENCES admin_operators(id) ON DELETE RESTRICT,
    CONSTRAINT fk_operator_invitation_accepted_operator FOREIGN KEY (accepted_operator_id)
        REFERENCES admin_operators(id) ON DELETE SET NULL,
    CONSTRAINT fk_operator_invitation_cancelled_by FOREIGN KEY (cancelled_by)
        REFERENCES admin_operators(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
