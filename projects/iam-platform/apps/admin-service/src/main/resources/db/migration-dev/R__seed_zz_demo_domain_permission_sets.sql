-- !!! DEV/DEMO ONLY — DO NOT run in production. Loaded via
-- spring.flyway.locations in non-prod profiles only (application.yml locations =
-- db/migration,db/migration-dev; application-prod.yml restricts to db/migration). !!!
--
-- TASK-BE-631 (ADR-MONO-083 P2 · OD-1 · OD-3) — the «이전 호환» domain permission set rows of the SEEDED demo
-- operators.
--
-- WHY THIS FILE EXISTS
-- ---------------------------------------------------------------------------
-- V0052 backfills (operator, membership tenant, legacy-compat, MIGRATION) for every membership and
-- (operator, '*', legacy-compat, MIGRATION) for every '*' operator that exists WHEN V0052 RUNS. R__ seeds run
-- AFTER every V migration (784 D-2 lesson), so on a fresh volume the demo operators below do not exist yet and
-- the backfill cannot see them. This file gives them exactly what the backfill would have given — no more:
--   demo-operator      → demo-corp · ecommerce
--   demo-requester     → demo-corp
--   demo-cs            → ecommerce
--   demo-viewer        → demo-viewer (its unregistered slug — the row is as inert as the membership)
--   demo-assigned-only → demo-corp
--   demo-platform      → '*' (a '*' operator: one «all tenants» row, OD-3)
--   demo-star-owner    → '*' (TASK-MONO-798 — the owner-only '*' SUPER_ADMIN; same shape as demo-platform)
-- On a long-lived dev DB where those operators already existed when V0052 ran, every statement below inserts 0
-- rows (the NOT EXISTS guard). Until P3 (TASK-BE-632) nothing reads these rows for a token.
--
-- WHY A SEPARATE FILE (and why the `zz`)
-- ---------------------------------------------------------------------------
-- auth-service demo-seed tests parse the operator seed files themselves (DemoSecondOperatorSeedTest asserts the
-- statements of R__seed_demo_operator.sql, DemoPlatformOperatorSeedTest asserts what R__seed_demo_platform_
-- operator.sql does NOT insert). Keeping these rows out of those files leaves those pins untouched. Flyway runs
-- repeatable migrations in DESCRIPTION order, and `seed zz …` sorts after every `seed demo …` file, so the
-- operators and their memberships exist when this runs.
--
-- Scoped to the seeded operator ids on purpose: a generic «every membership» statement here would, on its next
-- checksum change, hand legacy-compat to memberships created AFTER V0052 — which must start at zero sets.
--
-- DemoOperatorSeedIntegrationTest pins the resulting rows. Idempotent (INSERT … WHERE NOT EXISTS).

INSERT INTO operator_domain_set_grant
    (operator_id, tenant_id, set_id, source_key, group_origin, granted_by, granted_at)
SELECT a.operator_id, a.tenant_id, s.id, 'MIGRATION', NULL, NULL, NOW(6)
  FROM operator_tenant_assignment a
  JOIN admin_operators o ON o.id = a.operator_id
  JOIN domain_permission_set s ON s.system_key = 'legacy-compat'
 WHERE o.operator_id IN ('demo-operator', 'demo-requester', 'demo-cs', 'demo-viewer', 'demo-assigned-only')
   AND o.tenant_id <> '*'
   AND a.tenant_id <> '*'
   AND NOT EXISTS (
        SELECT 1
          FROM (SELECT operator_id, tenant_id, set_id, source_key FROM operator_domain_set_grant) g
         WHERE g.operator_id = a.operator_id
           AND g.tenant_id   = a.tenant_id
           AND g.set_id      = s.id
           AND g.source_key  = 'MIGRATION'
       );

INSERT INTO operator_domain_set_grant
    (operator_id, tenant_id, set_id, source_key, group_origin, granted_by, granted_at)
SELECT o.id, '*', s.id, 'MIGRATION', NULL, NULL, NOW(6)
  FROM admin_operators o
  JOIN domain_permission_set s ON s.system_key = 'legacy-compat'
 WHERE o.operator_id IN ('demo-platform', 'demo-star-owner')
   AND o.tenant_id = '*'
   AND NOT EXISTS (
        SELECT 1
          FROM (SELECT operator_id, tenant_id, set_id, source_key FROM operator_domain_set_grant) g
         WHERE g.operator_id = o.id
           AND g.tenant_id   = '*'
           AND g.set_id      = s.id
           AND g.source_key  = 'MIGRATION'
       );
