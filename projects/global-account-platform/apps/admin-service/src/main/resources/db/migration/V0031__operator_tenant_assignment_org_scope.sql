-- TASK-BE-338 / ADR-MONO-020 D3 amendment (2026-06-05) — per-assignment
-- data-scope (`org_scope`) on operator_tenant_assignment.
--
-- Adds a NULLABLE `org_scope` JSON column to the V0030 N:M operator↔tenant
-- assignment store. Value = a JSON array of department SUBTREE-ROOT id strings
-- the operator may act under *within that assigned tenant*. It is the
-- data-scope sibling of `permission_set_id` (D5, which narrows the role-set):
-- `org_scope` narrows the department subtree-set.
--
-- *** NET-ZERO: NULL ⟺ ["*"] = whole tenant. ***
-- NULL = unset = "all departments of the assigned tenant" — every existing and
-- newly-created assignment row defaults to NULL, so the assume-tenant token's
-- org_scope claim stays `["*"]` exactly as the TASK-BE-337 v1 bridge produced
-- (the auth-service customizer maps NULL/absent → ["*"]). No backfill, no seed —
-- behavior is byte-identical to pre-BE-338 until a row carries a concrete
-- subtree-root array.
--
-- Semantic distinction (NULL ≠ []):
--   NULL  = unset       → whole tenant (["*"], net-zero default)
--   ["d"] = scoped      → only the "d" department subtree
--   []    = explicit    → zero-scope (no department) — distinct from NULL;
--                          a present empty array is NOT widened to ["*"].
--
-- GAP stores/propagates subtree ROOTS only — it does not know erp's department
-- tree. erp masterdata-service expands roots → descendants for its containment
-- check (TASK-ERP-BE-008). erp is the only org_scope consumer (repo-verified).

ALTER TABLE operator_tenant_assignment
    ADD COLUMN org_scope JSON NULL AFTER permission_set_id;
