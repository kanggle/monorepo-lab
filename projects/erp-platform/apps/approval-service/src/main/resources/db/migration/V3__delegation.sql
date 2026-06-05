-- erp-platform approval-service v2.1 — 대결/위임 (delegation/substitution)
-- (TASK-ERP-BE-013, architecture.md § v2.1 amendment). Pure-additive +
-- backward-compatible: a new delegation_grant table + a nullable on_behalf_of
-- column on approval_action. NO change to existing tables/data — every existing
-- request and transition behaves exactly as v2.0. MySQL 8 (InnoDB, utf8mb4) —
-- H2 forbidden (Testcontainers MySQL is the gate).

-- ---------------------------------------------------------------------------
-- delegation_grant — standing windowed delegation A→D. While status=ACTIVE and
-- now ∈ [valid_from, valid_to ?? +∞], the delegate (delegate_id, D) may act for
-- the delegator (delegator_id, A) at any stage where A is the approver. revoke
-- moves ACTIVE→REVOKED (terminal). @Version gives optimistic locking (T5).
-- ---------------------------------------------------------------------------
CREATE TABLE delegation_grant (
    id            VARCHAR(64)  NOT NULL,
    tenant_id     VARCHAR(64)  NOT NULL,
    delegator_id  VARCHAR(64)  NOT NULL,
    delegate_id   VARCHAR(64)  NOT NULL,
    valid_from    DATETIME(6)  NOT NULL,
    valid_to      DATETIME(6),
    reason        VARCHAR(512),
    status        VARCHAR(16)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    created_by    VARCHAR(64)  NOT NULL,
    revoked_at    DATETIME(6),
    revoked_by    VARCHAR(64),
    version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_delegation_status CHECK (status IN ('ACTIVE','REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- active-grant lookup (transition-time resolution: A→D + status + window).
CREATE INDEX idx_delegation_active_lookup
    ON delegation_grant (tenant_id, delegator_id, delegate_id, status);
-- list endpoint (the caller's grants as delegate / as delegator).
CREATE INDEX idx_delegation_delegate ON delegation_grant (tenant_id, delegate_id);
CREATE INDEX idx_delegation_delegator ON delegation_grant (tenant_id, delegator_id);

-- ---------------------------------------------------------------------------
-- approval_action — record the stage approver a delegate acted for. Nullable;
-- set only when a delegate performed the transition on behalf of A (대결); null
-- for direct approver actions and for submit/withdraw. Pure-additive.
-- ---------------------------------------------------------------------------
ALTER TABLE approval_action
    ADD COLUMN on_behalf_of VARCHAR(64) NULL;
