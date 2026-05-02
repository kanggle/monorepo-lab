-- TASK-BE-255: Rebuild account_roles with composite natural PK + composite FK
-- ON DELETE CASCADE + tenant-scoped role lookup index.
--
-- The V0012 layout used a surrogate `id` PK with a UNIQUE on
-- (tenant_id, account_id, role_name) and a single-column FK to accounts(id).
-- TASK-BE-255 spec requires the natural composite PK (the row IS the fact),
-- a composite FK (tenant_id, account_id) -> accounts(tenant_id, id) so
-- referential integrity also enforces the tenant boundary, and an explicit
-- ON DELETE CASCADE so account deletion cleans up role rows automatically.
--
-- Strategy:
--   1. Add a UNIQUE INDEX on accounts(tenant_id, id) so the composite FK
--      target is satisfiable. (id is already PK so the new key is logically
--      redundant but MySQL requires an explicit index on the referenced
--      columns of a composite FK.)
--   2. Drop and recreate account_roles with the new shape. Copying existing
--      rows preserves any V0012-era assignments (none in production yet —
--      account_roles was introduced in TASK-BE-231 alongside the
--      provisioning API; only the WMS demo tenant uses it so far).
--
-- Forward-only: no down migration (R6).

-- Step 1: ensure (tenant_id, id) is a key on accounts so the composite FK below validates.
ALTER TABLE accounts
    ADD UNIQUE INDEX uk_accounts_tenant_id_id (tenant_id, id);

-- Step 2: stash existing rows so we can restore them after the rebuild.
CREATE TEMPORARY TABLE account_roles_backup AS
    SELECT tenant_id, account_id, role_name, assigned_at
      FROM account_roles;

-- Step 3: drop and recreate with the new shape.
DROP TABLE account_roles;

CREATE TABLE account_roles (
    tenant_id  VARCHAR(32)  NOT NULL,
    account_id VARCHAR(36)  NOT NULL,
    role_name  VARCHAR(64)  NOT NULL,
    granted_by VARCHAR(36)  NULL,
    granted_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (tenant_id, account_id, role_name),
    INDEX idx_account_roles_tenant_role (tenant_id, role_name),
    CONSTRAINT fk_account_roles_account
        FOREIGN KEY (tenant_id, account_id)
        REFERENCES accounts(tenant_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_account_roles_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES tenants(tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Step 4: restore any pre-existing assignments. granted_by is NULL because
-- V0012-era rows did not record the operator.
INSERT INTO account_roles (tenant_id, account_id, role_name, granted_by, granted_at)
SELECT tenant_id, account_id, role_name, NULL, assigned_at
  FROM account_roles_backup;

DROP TEMPORARY TABLE account_roles_backup;
