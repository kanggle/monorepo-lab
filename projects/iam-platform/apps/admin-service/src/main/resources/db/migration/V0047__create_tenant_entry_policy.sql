-- TASK-MONO-771 S4 (ADR-MONO-080 D4 · R2) — `tenant_entry_policy`: «entering this tenant AS AN OPERATOR
-- requires a second factor». data-model.md § `tenant_entry_policy` is the canonical shape.
--
-- *** NET-ZERO: the table is created EMPTY. *** Row absent ⟺ policy off, so with zero rows no assume
-- and no token exchange changes because of this table (the role flag `admin_roles.require_2fa` is the
-- other, independent term — see admin-api.md § token-exchange «2단계 요구»). Rows are written only by
-- the S5 management API (admin-api.md § Tenant Entry Policy) — this migration seeds nothing.
--
-- Shape notes:
--   * tenant_id is an opaque reference to account-service `tenants.tenant_id` — NO FK (other DB), the
--     same form as operator_tenant_assignment / tenant_partnership.
--   * CHECK (tenant_id <> '*') — the platform sentinel can never hold a policy, so a platform-scope
--     operator's home contributes nothing to the token-exchange term (admin-api.md OD-3 note).
--   * Turning a policy off keeps the row with require_mfa = FALSE (last changer/time preserved).
--   * version INT — the sibling-table convention (tenant_partnership.version) for optimistic locking.
-- Forward-only. `CREATE TABLE IF NOT EXISTS` is the existence guard (no cross-statement @var). MySQL 8
-- enforces the CHECK constraint (8.0.16+).

CREATE TABLE IF NOT EXISTS tenant_entry_policy (
    tenant_id    VARCHAR(32)  NOT NULL,
    require_mfa  BOOLEAN      NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    updated_by   BIGINT       NULL,
    version      INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id),
    CONSTRAINT ck_tenant_entry_policy_not_platform CHECK (tenant_id <> '*'),
    CONSTRAINT fk_tenant_entry_policy_updated_by FOREIGN KEY (updated_by)
        REFERENCES admin_operators(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
