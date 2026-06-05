-- TASK-BE-289 WI-2: backfill admin_operator_roles.tenant_id to the bound
-- operator's tenant_id (per-tenant binding invariant — data-model.md
-- §admin_operator_roles; ADR-002).
--
-- Why: before TASK-BE-288/289, PatchOperatorRoleUseCase created role bindings
-- via the legacy 4-arg AdminOperatorRoleJpaEntity.create overload, which
-- hardcoded tenant_id = 'fan-platform' regardless of the target operator's
-- real tenant. For any non-fan-platform operator (WMS-tenant operators;
-- SUPER_ADMIN whose tenant_id = '*') whose roles were patched, the binding row
-- carries the wrong tenant scope — inconsistent with CreateOperatorUseCase
-- (which always stamped the operator's real tenant) and a multi-tenant
-- isolation regression (TASK-BE-288 review Finding 1).
--
-- This restores the V0025 STEP 3 invariant ("copy tenant_id from the operator
-- row") for every binding, correcting rows mis-stamped after V0025. SUPER_ADMIN
-- operators (tenant_id = '*') get their bindings set to '*' — matching the
-- operator row and CreateOperatorUseCase semantics. Already-correct rows are
-- excluded by the WHERE clause (no-op; the statement is re-run safe).

UPDATE admin_operator_roles aor
    INNER JOIN admin_operators o ON o.id = aor.operator_id
SET aor.tenant_id = o.tenant_id
WHERE aor.tenant_id <> o.tenant_id;
