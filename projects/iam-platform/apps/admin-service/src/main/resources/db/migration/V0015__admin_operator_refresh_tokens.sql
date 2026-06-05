-- TASK-BE-040: operator refresh token registry.
--
-- Stores only the JWT `jti` (not the token itself; the JWT is self-contained
-- and verified by signature/exp). Rows are mutable for the lifetime of the
-- token: insert at issuance, update `revoked_at` + `revoke_reason` on
-- rotation/logout/reuse-detection. `rotated_from` keeps the previous jti so
-- the chain is auditable post-mortem.
--
-- Reuse detection (specs/services/admin-service/security.md §Session Lifecycle):
-- presenting an already-revoked jti triggers bulk-revocation of every
-- non-revoked refresh token belonging to the same operator with reason
-- REUSE_DETECTED.

CREATE TABLE admin_operator_refresh_tokens (
    jti           CHAR(36)     NOT NULL,
    operator_id   BIGINT       NOT NULL,
    issued_at     DATETIME(6)  NOT NULL,
    expires_at    DATETIME(6)  NOT NULL,
    rotated_from  CHAR(36)     NULL,
    revoked_at    DATETIME(6)  NULL,
    revoke_reason VARCHAR(64)  NULL,
    PRIMARY KEY (jti),
    CONSTRAINT fk_admin_refresh_tokens_operator
        FOREIGN KEY (operator_id) REFERENCES admin_operators(id) ON DELETE CASCADE,
    INDEX idx_admin_refresh_tokens_operator_issued
        (operator_id, issued_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
