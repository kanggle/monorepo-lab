-- TASK-BE-326 / ADR-MONO-020 D1 + D5 (§ 3.3 step 1).
--
-- N:M operator↔tenant assignment table. Models the multi-tenant scope an
-- operator may be GRANTED beyond its single home tenant
-- (admin_operators.tenant_id). Mirrors the admin_operator_roles shape exactly
-- (composite PK, BIGINT surrogate FK → admin_operators.id ON DELETE CASCADE,
-- granted_at / granted_by, tenant_id index) per the BE-326 blueprint.
--
-- *** NET-ZERO: this migration creates an EMPTY table and seeds NOTHING. ***
-- With zero rows, the dual-read effective tenant scope
-- (assignment rows ∪ {legacy admin_operators.tenant_id}) collapses to the
-- legacy single value for EVERY operator → all catalog / gating behavior is
-- byte-identical to pre-BE-326. Multi-assignment only takes effect once real
-- assignment rows are seeded in a later step (ADR-020 § 3.3 step 3).
--
-- D5: permission_set_id (nullable FK → admin_roles.id) carries a per-assignment
-- permission set. NULL = inherit the operator-level role grants. ON DELETE
-- RESTRICT so a referenced role cannot be silently dropped.
--
-- IMPORTANT semantic distinction: this table's `tenant_id` is the *ASSIGNED*
-- tenant (a tenant the operator may additionally act within) — it is NOT the
-- operator's home tenant. This differs from the V0026
-- admin_operator_roles.tenant_id invariant, where tenant_id MUST equal the
-- bound operator's home admin_operators.tenant_id (per-tenant role binding).

CREATE TABLE operator_tenant_assignment (
    operator_id       BIGINT       NOT NULL,            -- FK → admin_operators.id (surrogate)
    tenant_id         VARCHAR(32)  NOT NULL,            -- the ASSIGNED tenant (NOT the operator's home tenant)
    granted_at        DATETIME(6)  NOT NULL,
    granted_by        BIGINT       NULL,
    permission_set_id BIGINT       NULL,                -- D5 per-assignment permission-set; NULL = inherit operator-level roles
    PRIMARY KEY (operator_id, tenant_id),
    INDEX idx_ota_tenant_id (tenant_id),
    CONSTRAINT fk_ota_operator   FOREIGN KEY (operator_id)       REFERENCES admin_operators(id) ON DELETE CASCADE,
    CONSTRAINT fk_ota_granted_by FOREIGN KEY (granted_by)        REFERENCES admin_operators(id) ON DELETE SET NULL,
    CONSTRAINT fk_ota_perm_set   FOREIGN KEY (permission_set_id) REFERENCES admin_roles(id)     ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
