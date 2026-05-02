-- TASK-BE-249: Add tenant_id (and target_tenant_id) to admin tables for multi-tenant row-level isolation.
-- Two-step strategy (Failure Scenarios): NULL first → backfill → NOT NULL to avoid constraint violation.
-- ADR-002 documents the sentinel choice ('*' = SUPER_ADMIN platform scope) and backfill policy.

-- -----------------------------------------------------------------------
-- STEP 1: Add columns as NULL-allowed
-- -----------------------------------------------------------------------

ALTER TABLE admin_operators
    ADD COLUMN tenant_id VARCHAR(32) NULL AFTER operator_id;

ALTER TABLE admin_operator_roles
    ADD COLUMN tenant_id VARCHAR(32) NULL;

ALTER TABLE admin_actions
    ADD COLUMN tenant_id        VARCHAR(32) NULL,
    ADD COLUMN target_tenant_id VARCHAR(32) NULL;

-- -----------------------------------------------------------------------
-- STEP 2: Backfill admin_operators
-- Normal operators → 'fan-platform' (legacy single-tenant default per multi-tenancy.md §Migration).
-- SUPER_ADMIN operators → '*' (platform-scope sentinel per ADR-002).
-- -----------------------------------------------------------------------

-- All operators default to fan-platform first.
UPDATE admin_operators SET tenant_id = 'fan-platform' WHERE tenant_id IS NULL;

-- Override: operators assigned the SUPER_ADMIN role get the '*' sentinel.
UPDATE admin_operators o
    INNER JOIN admin_operator_roles aor ON aor.operator_id = o.id
    INNER JOIN admin_roles r             ON r.id            = aor.role_id
SET o.tenant_id = '*'
WHERE r.name = 'SUPER_ADMIN';

-- -----------------------------------------------------------------------
-- STEP 3: Backfill admin_operator_roles — copy tenant_id from the operator row.
-- -----------------------------------------------------------------------

UPDATE admin_operator_roles aor
    INNER JOIN admin_operators o ON o.id = aor.operator_id
SET aor.tenant_id = o.tenant_id
WHERE aor.tenant_id IS NULL;

-- -----------------------------------------------------------------------
-- STEP 4: Backfill admin_actions
-- tenant_id   = the acting operator's tenant_id.
-- target_tenant_id = same as tenant_id for legacy single-tenant rows
--                    (Edge Case: 'target_tenant_id defaults to operator's tenant_id' — spec §Edge Cases).
-- -----------------------------------------------------------------------

UPDATE admin_actions aa
    INNER JOIN admin_operators o ON o.id = aa.operator_id
SET aa.tenant_id        = o.tenant_id,
    aa.target_tenant_id = o.tenant_id
WHERE aa.tenant_id IS NULL;

-- Fallback for any action rows where operator_id is NULL (should not occur post-V0011,
-- but defensive guard to ensure NOT NULL constraint succeeds).
UPDATE admin_actions SET tenant_id = 'fan-platform', target_tenant_id = 'fan-platform'
WHERE tenant_id IS NULL;

-- -----------------------------------------------------------------------
-- STEP 5: Apply NOT NULL constraints
-- target_tenant_id stays NULL-allowed per spec (cross-tenant aware callers set it explicitly).
-- -----------------------------------------------------------------------

ALTER TABLE admin_operators
    MODIFY COLUMN tenant_id VARCHAR(32) NOT NULL;

ALTER TABLE admin_operator_roles
    MODIFY COLUMN tenant_id VARCHAR(32) NOT NULL;

ALTER TABLE admin_actions
    MODIFY COLUMN tenant_id VARCHAR(32) NOT NULL;

-- -----------------------------------------------------------------------
-- STEP 6: Drop old single-column unique index on email; add composite (tenant_id, email).
-- Edge Case: same email is allowed in different tenants.
-- -----------------------------------------------------------------------

ALTER TABLE admin_operators
    DROP INDEX uk_admin_operators_email,
    ADD UNIQUE INDEX uk_admin_operators_tenant_email (tenant_id, email);

-- -----------------------------------------------------------------------
-- STEP 7: Add index on admin_actions (tenant_id, created_at DESC) for tenant-scoped audit queries.
-- -----------------------------------------------------------------------

ALTER TABLE admin_actions
    ADD INDEX idx_admin_actions_tenant_created (tenant_id, started_at);
