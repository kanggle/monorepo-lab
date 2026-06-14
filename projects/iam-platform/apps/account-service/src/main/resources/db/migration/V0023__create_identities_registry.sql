-- TASK-BE-371 (ADR-MONO-034 U6 step 3a / ADR-MONO-032 D5 step 3):
-- Central `identities` registry — the canonical per-person identity that the
-- account/credential unification (ADR-034) links the consumer account and the
-- operator extension to. This is U1-A: a NEW registry layered ABOVE `accounts`,
-- NOT a reuse of accounts.id as the person id (the person-id and the
-- consumer-account-id spaces are kept distinct).
--
-- Additive + net-zero:
--   * one identity is backfilled per existing account;
--   * accounts.identity_id is NULLABLE — no account-creation path is wired to it
--     yet (that lands in ADR-034 step 3d, unified provisioning). New accounts
--     created in the window carry NULL identity_id (the FK permits NULL);
--   * accounts.identity_id is deliberately NOT mapped on AccountJpaEntity in this
--     step, so Hibernate never writes it on an account update — the backfilled
--     value is preserved (the column is invisible to JPA until a later step reads
--     it).

CREATE TABLE identities (
    identity_id   VARCHAR(36)  NOT NULL,
    tenant_id     VARCHAR(32)  NOT NULL,
    primary_email VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    version       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (identity_id),
    UNIQUE INDEX uk_identities_tenant_email (tenant_id, primary_email),
    INDEX idx_identities_status (status),
    CONSTRAINT fk_identities_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE accounts
    ADD COLUMN identity_id VARCHAR(36) NULL AFTER id;

-- Backfill: one fresh identity per existing account. identity_id is a NEW UUID
-- (NOT account.id) per ADR-034 U1-A. (tenant_id, email) is unique on accounts,
-- so the join below is strictly 1:1 — no account is linked to two identities and
-- no identity is shared by two accounts.
INSERT INTO identities (identity_id, tenant_id, primary_email, status, created_at, updated_at, version)
SELECT UUID(), a.tenant_id, a.email, 'ACTIVE', a.created_at, a.updated_at, 0
FROM accounts a;

UPDATE accounts a
JOIN identities i ON i.tenant_id = a.tenant_id AND i.primary_email = a.email
SET a.identity_id = i.identity_id;

ALTER TABLE accounts
    ADD CONSTRAINT fk_accounts_identity_id FOREIGN KEY (identity_id) REFERENCES identities(identity_id);
