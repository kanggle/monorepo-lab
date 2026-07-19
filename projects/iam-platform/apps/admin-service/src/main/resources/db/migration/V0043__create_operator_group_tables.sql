-- TASK-BE-520 / ADR-MONO-046 D1/D3/D5 (§ 4 step 2) — the operator-group aggregate.
--
-- Three tables model the workforce-grouping primitive (AWS IAM User Group / Google Group
-- parity): a tenant-scoped named unit of `admin_operators` (operator_group) with its
-- membership edges (operator_group_member) and the grant templates fanned out to members
-- (operator_group_grant). Forward-only (grouping/relationship state carries audit value —
-- no down migration). MySQL-only (admin-service pins mysql:8.0).
--
-- *** NET-ZERO: all three tables are created EMPTY and seed NOTHING. *** With zero rows no
-- fan-out ever runs, so no `admin_operator_roles` / `operator_tenant_assignment` row is
-- materialised and the evaluator / perm-cache / confinement axes are byte-identical until
-- the first group is created + granted (ADR-046 D2-A fan-out, rbac.md § Operator Group
-- Fan-Out). The `group_origin` marker on the substrate is added by V0044.
--
-- Idempotent via CREATE TABLE IF NOT EXISTS (the house form for whole-table creation —
-- MySQL has no ADD COLUMN IF NOT EXISTS, hence V0044 uses the INFORMATION_SCHEMA guard
-- pattern instead). Highest existing prod version is V0042; migration-dev holds
-- V0014/V0023/V0028 only (no CompositeMigrationResolver collision).

-- ------------------------------------------------------------------------
-- operator_group — the tenant-scoped named unit (D1/D3). `tenant_id <> '*'`: a group is a
-- unit of ONE real tenant's operators; platform-global groups are out of v1 scope (same as
-- tenant_partnership forbidding '*'). CHECK enforced at the DB so hand-written seed / ops
-- SQL cannot create a platform-global group.
-- ------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS operator_group (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    group_id    VARCHAR(36)  NOT NULL,              -- external UUID v7 (HTTP path / event partitionKey)
    tenant_id   VARCHAR(32)  NOT NULL,              -- owning tenant (TenantScopeGuard target); '*' forbidden
    name        VARCHAR(120) NOT NULL,              -- display name; (tenant_id, name) unique within a tenant
    description VARCHAR(255) NULL,
    created_by  BIGINT       NULL,                  -- operator who created the group; seed/system path NULL
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    version     INT          NOT NULL DEFAULT 0,    -- optimistic lock (concurrent rename/grant/member)
    PRIMARY KEY (id),
    UNIQUE KEY uk_operator_group_group_id (group_id),
    UNIQUE KEY uk_operator_group_tenant_name (tenant_id, name),  -- group name unique within a tenant
    KEY idx_operator_group_tenant (tenant_id),                   -- per-tenant list (GET /groups, D3 read confine)
    CONSTRAINT ck_operator_group_tenant_not_platform CHECK (tenant_id <> '*'),
    CONSTRAINT fk_operator_group_created_by FOREIGN KEY (created_by) REFERENCES admin_operators(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------
-- operator_group_member — group ↔ operator membership edge (D1/D5). NOT read at evaluation
-- time (fan-out; evaluation reads only the flat substrate rows). Member operator's home
-- tenant MUST equal the group's tenant (app invariant → 422 GROUP_MEMBER_TENANT_MISMATCH).
-- ------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS operator_group_member (
    group_id    BIGINT      NOT NULL,               -- FK → operator_group.id (surrogate)
    operator_id BIGINT      NOT NULL,               -- FK → admin_operators.id (home tenant == group tenant)
    added_at    DATETIME(6) NOT NULL,
    added_by    BIGINT      NULL,                    -- operator who added the member; seed/system path NULL
    PRIMARY KEY (group_id, operator_id),             -- one membership per (group, operator) → 409 on dup
    KEY idx_operator_group_member_operator (operator_id),  -- reverse: groups an operator belongs to
    CONSTRAINT fk_operator_group_member_group    FOREIGN KEY (group_id)    REFERENCES operator_group(id)  ON DELETE CASCADE,
    CONSTRAINT fk_operator_group_member_operator FOREIGN KEY (operator_id) REFERENCES admin_operators(id) ON DELETE CASCADE,
    CONSTRAINT fk_operator_group_member_added_by FOREIGN KEY (added_by)    REFERENCES admin_operators(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------
-- operator_group_grant — the grant TEMPLATE (ROLE or TENANT_ASSIGNMENT) fanned out to each
-- member (D5). Persists group-level grants independently of members so add-member can
-- re-fan the group's current grants and a member-0 group keeps its grants. The CHECK pins
-- exactly one reference filled per grant_type.
--
-- NOTE (MySQL NULL semantics): uk_operator_group_grant_natural includes the nullable
-- role_id / tenant_id; because MySQL treats NULLs as DISTINCT in a UNIQUE index, this index
-- is a best-effort backstop only. The authoritative duplicate guard (→ 409
-- GROUP_GRANT_ALREADY_EXISTS) is the application-level pre-check in GroupAdminUseCase.
-- ------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS operator_group_grant (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    grant_id   VARCHAR(36) NOT NULL,                 -- external UUID v7 (HTTP path)
    group_id   BIGINT      NOT NULL,                 -- FK → operator_group.id (surrogate)
    grant_type VARCHAR(20) NOT NULL,                 -- ROLE | TENANT_ASSIGNMENT (fan-out substrate selector)
    role_id    BIGINT      NULL,                      -- grant_type=ROLE: the granted role
    tenant_id  VARCHAR(32) NULL,                      -- grant_type=TENANT_ASSIGNMENT: the ASSIGNED tenant
    granted_by BIGINT      NULL,                      -- operator who added the grant; seed/system path NULL
    granted_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_operator_group_grant_grant_id (grant_id),
    UNIQUE KEY uk_operator_group_grant_natural (group_id, grant_type, role_id, tenant_id),  -- dup grant backstop
    KEY idx_operator_group_grant_group (group_id),   -- group's current grants (add-member fan-out, GET /grants)
    CONSTRAINT ck_operator_group_grant_type CHECK (
        (grant_type = 'ROLE'              AND role_id IS NOT NULL AND tenant_id IS NULL)
     OR (grant_type = 'TENANT_ASSIGNMENT' AND tenant_id IS NOT NULL AND role_id IS NULL)
    ),
    CONSTRAINT fk_operator_group_grant_group      FOREIGN KEY (group_id)   REFERENCES operator_group(id)  ON DELETE CASCADE,
    CONSTRAINT fk_operator_group_grant_role       FOREIGN KEY (role_id)    REFERENCES admin_roles(id)     ON DELETE RESTRICT,
    CONSTRAINT fk_operator_group_grant_granted_by FOREIGN KEY (granted_by) REFERENCES admin_operators(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
