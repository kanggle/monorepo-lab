-- !!! DEV/DEMO ONLY — DO NOT run in production. Loaded via
-- spring.flyway.locations in non-prod profiles only (application.yml locations =
-- db/migration,db/migration-dev; application-prod.yml restricts to db/migration). !!!
--
-- TASK-BE-628 — an ASSIGNED-ONLY demo operator: `assigned-only@demo.com`.
--
-- WHAT IT IS FOR
-- ---------------------------------------------------------------------------
-- The console's operator-group «멤버 추가» picker greys out an operator whose HOME
-- tenant is not the group's tenant but who is ASSIGNED to it («<home> 소속 · 배정만 됨»
-- — platform-console TASK-PC-FE-319, iam TASK-BE-626 `homeTenantId`). No seeded operator
-- had that shape inside a tenant the demo operator may administer:
--   - demo@demo.com / requester@demo.com — HOME demo-corp (selectable in a demo-corp group)
--   - viewer@demo.com — no assignment at all
--   - demo@demo.com IS assigned-only in `ecommerce`, but its SUPER_ADMIN grant row is
--     bound to demo-corp, so it cannot create an `ecommerce` group (TENANT_SCOPE_DENIED —
--     group mutations check the ADMIN grant scope, not the assignment scope).
-- This row fills exactly that gap: HOME `ecommerce`, ASSIGNED to `demo-corp`. In a
-- demo-corp group it renders «ecommerce 소속 · 배정만 됨», disabled.
--
-- WHY IT CANNOT BE USED TO LOG IN — each choice is load-bearing
-- ---------------------------------------------------------------------------
-- 1. password_hash NULL — AdminLoginService fail-closes the break-glass password login.
-- 2. oidc_subject NULL — the console token exchange has no link key, so no IAM login
--    resolves to this operator (fail-closed default, data-model.md § oidc_subject).
-- 3. NO admin_operator_roles row — even an issued token would carry no permission.
-- The assignment to demo-corp therefore grants nothing anyone can exercise; it exists
-- only so the list API returns this operator for demo-corp (HOME ∪ ASSIGNED).
--
-- 🔴 Same KNOWN LIMIT as R__seed_demo_viewer_operator.sql: demo@demo.com is SUPER_ADMIN
--    with published credentials and could grant this operator a role or a profile.
--    It still has no login path (no password, no oidc_subject), and re-applying this
--    file does not undo such edits (INSERT IGNORE / ON DUPLICATE KEY never deletes).
--
-- Separate file (not a section in R__seed_demo_operator.sql): auth-service
-- `DemoSecondOperatorSeedTest` asserts that file holds EXACTLY two demo-corp operators
-- (the ERP Separation-of-Duties pair). DemoOperatorSeedIntegrationTest pins this one.
--
-- display_name is ASCII on purpose — the same rows were inserted once into a live demo
-- window over SSM (2026-10-08, owner-approved) and a multibyte literal can be mangled on
-- that path; keeping one spelling means the live row and the seeded row are identical.
--
-- Idempotent: INSERT ... ON DUPLICATE KEY UPDATE / INSERT IGNORE (safe for R__ re-runs).

INSERT INTO admin_operators (
    operator_id, tenant_id, email, password_hash, display_name, status,
    oidc_subject, created_at, updated_at, version
) VALUES (
    'demo-assigned-only', 'ecommerce', 'assigned-only@demo.com', NULL,
    'Store Staff (demo-corp assignment only)', 'ACTIVE',
    NULL, NOW(6), NOW(6), 0
)
ON DUPLICATE KEY UPDATE
    tenant_id  = VALUES(tenant_id),
    status     = 'ACTIVE',
    updated_at = NOW(6);

INSERT IGNORE INTO operator_tenant_assignment (operator_id, tenant_id, granted_at, granted_by, permission_set_id)
SELECT o.id, 'demo-corp', NOW(6), NULL, NULL
  FROM admin_operators o WHERE o.operator_id = 'demo-assigned-only';

-- Intentionally NO admin_operator_roles row (see 3 above). DemoOperatorSeedIntegrationTest
-- asserts it is empty and that both login columns are NULL.
