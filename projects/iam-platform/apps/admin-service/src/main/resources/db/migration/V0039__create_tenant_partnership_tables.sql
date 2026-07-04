-- TASK-BE-477 / ADR-MONO-045 D1/D4 (§ 3.4 step 2) — cross-org partnership aggregate.
--
-- Two tables model the first cross-org privilege-origination primitive: a host
-- tenant A delegates a bounded {domains}×{roles} slice to a partner tenant B
-- (tenant_partnership), and B binds its OWN operators as participants
-- (tenant_partnership_participant). Forward-only (relationship state carries audit
-- value — no down migration). delegated_scope / participant_scope are MySQL JSON
-- columns whose shapes byte-match the @JdbcTypeCode(SqlTypes.JSON) entity mappings
-- (ddl-auto=validate fails otherwise).
--
-- *** NET-ZERO: both tables are created EMPTY and seed NOTHING. *** With zero rows,
-- OperatorAssignmentCheckUseCase.findActivePartnership always returns null → no
-- operator derives any cross-org reach. The feature is dormant until an operator
-- creates + accepts the first partnership (ADR-045 §3.4 step 3 / live use requires
-- step 2b auth-service consumption). Existing assume-tenant behavior is byte-identical.
--
-- Highest existing prod version is V0038; migration-dev holds V0014/V0023/V0028 only
-- (no CompositeMigrationResolver collision).

CREATE TABLE tenant_partnership (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    partnership_id    VARCHAR(36)  NOT NULL,              -- external UUID v7 (HTTP path / event partitionKey)
    host_tenant_id    VARCHAR(32)  NOT NULL,              -- delegating tenant A (delegated_scope author); '*' forbidden (app invariant)
    partner_tenant_id VARCHAR(32)  NOT NULL,              -- delegated-to tenant B; '*' forbidden (app invariant)
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING → ACTIVE → SUSPENDED/TERMINATED
    delegated_scope   JSON         NOT NULL,              -- {"domains":[...],"roles":[...]}; no admin role (app cap)
    invited_by        BIGINT       NULL,                  -- host TENANT_ADMIN who invited
    accepted_by       BIGINT       NULL,                  -- partner TENANT_ADMIN who accepted
    invited_at        DATETIME(6)  NOT NULL,
    accepted_at       DATETIME(6)  NULL,
    terminated_at     DATETIME(6)  NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    version           INT          NOT NULL DEFAULT 0,    -- optimistic lock (concurrent accept/terminate)
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_partnership_partnership_id (partnership_id),
    UNIQUE KEY uk_tenant_partnership_pair (host_tenant_id, partner_tenant_id),  -- one relationship per ordered pair
    KEY idx_tenant_partnership_partner (partner_tenant_id, status),             -- B-side list + status filter
    KEY idx_tenant_partnership_host (host_tenant_id, status),                   -- A-side list + status filter
    CONSTRAINT fk_partnership_invited_by  FOREIGN KEY (invited_by)  REFERENCES admin_operators(id) ON DELETE SET NULL,
    CONSTRAINT fk_partnership_accepted_by FOREIGN KEY (accepted_by) REFERENCES admin_operators(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tenant_partnership_participant (
    partnership_id    BIGINT       NOT NULL,              -- FK → tenant_partnership.id (surrogate)
    operator_id       BIGINT       NOT NULL,              -- FK → admin_operators.id (B-owned; home tenant == partner)
    participant_scope JSON         NULL,                  -- {"domains":[...],"roles":[...]}; NULL ⟺ whole delegated_scope
    assigned_at       DATETIME(6)  NOT NULL,
    assigned_by       BIGINT       NULL,                  -- partner TENANT_ADMIN who assigned
    PRIMARY KEY (partnership_id, operator_id),
    KEY idx_partnership_participant_operator (operator_id),                     -- reverse: partnerships an operator participates in
    CONSTRAINT fk_participant_partnership FOREIGN KEY (partnership_id) REFERENCES tenant_partnership(id) ON DELETE CASCADE,
    CONSTRAINT fk_participant_operator    FOREIGN KEY (operator_id)    REFERENCES admin_operators(id)    ON DELETE CASCADE,
    CONSTRAINT fk_participant_assigned_by FOREIGN KEY (assigned_by)    REFERENCES admin_operators(id)    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
