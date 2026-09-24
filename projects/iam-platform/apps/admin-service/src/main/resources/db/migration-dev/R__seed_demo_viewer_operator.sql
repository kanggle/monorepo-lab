-- !!! DEV/DEMO ONLY — DO NOT run in production. Loaded via
-- spring.flyway.locations in non-prod profiles only (application.yml locations =
-- db/migration,db/migration-dev; application-prod.yml restricts to db/migration). !!!
--
-- TASK-BE-597 — the RESTRICTED demo operator: `viewer@demo.com`.
--
-- WHAT IT IS FOR
-- ---------------------------------------------------------------------------
-- `demo@demo.com` (R__seed_demo_operator.sql) is SUPER_ADMIN, so almost every console
-- screen renders for it and the portfolio demo had no way to SHOW what a permission
-- denial looks like. This identity logs into the console like any operator and then
-- gets 403 PERMISSION_DENIED on every admin-gated screen. Owner decision 2026-09-24:
-- "one restricted account with minimal permissions to demonstrate the 권한 부족 state".
--
-- WHY A SEPARATE FILE AND NOT A §6 IN R__seed_demo_operator.sql
-- ---------------------------------------------------------------------------
-- auth-service `DemoSecondOperatorSeedTest` parses that file and asserts EXACTLY two
-- operators, both in demo-corp — a correct statement about the ERP Separation-of-Duties
-- pair (TASK-MONO-519) that must stay true. This operator is not part of that pair, so
-- it gets its own artifact and its own pin (`DemoViewerOperatorSeedTest`,
-- `DemoOperatorSeedIntegrationTest`). Repeatable for the same ordering reason that
-- file's header measures (V9000+ band → out-of-order on the next V00NN).
--
-- WHAT "MINIMAL" MEANS HERE — each of the three choices is load-bearing
-- ---------------------------------------------------------------------------
-- 1. NO admin_operator_roles row. Logging in does not need one: the console reaches
--    the operator plane through the OIDC token exchange, which checks only that an
--    admin_operators row matches the `sub` and is `status = 'ACTIVE'`
--    (TokenExchangeService). The console shell's own call
--    (`GET /api/admin/console/registry`) carries no @RequiresPermission either.
--    SUPPORT_READONLY was the alternative and was rejected: it would OPEN /accounts
--    (account.read) and /audit (audit.read) — consumer account data and the audit
--    trail — which is the opposite of what this identity is for. With no role, every
--    `@RequiresPermission` endpoint answers 403 PERMISSION_DENIED (the permission
--    union is empty — rbac.md § Permission Evaluation Algorithm step 4).
--
-- 2. Home tenant `demo-viewer`, which is deliberately NOT a registered tenant.
--    The home tenant is part of the operator's effective scope (TenantScopeResolver:
--    assignment rows ∪ {home}), and OperatorAssignmentCheckUseCase lets an operator
--    ASSUME any tenant in that scope. With home = demo-corp this identity could assume
--    demo-corp and receive ECOMMERCE/WMS/SCM/ERP/FINANCE_OPERATOR — write access to
--    every domain — which is not "minimal". An unregistered slug is dropped by the
--    registry's ACTIVE-tenant intersection (ConsoleRegistryUseCase), so every product
--    lists zero tenants, the switcher does not render, and each domain section shows
--    «테넌트를 먼저 선택하세요» (DomainTenantGate) instead of data.
--
-- 3. NO operator_tenant_assignment row, for the same reason as 2.
--
-- 🔴 KNOWN LIMIT — this identity's restriction is not tamper-proof on a public demo.
--    `demo@demo.com` is SUPER_ADMIN with published credentials and can grant this
--    operator any role (PATCH /api/admin/operators/demo-viewer/roles). A visitor could
--    also self-onboard a tenant whose slug is `demo-viewer` (the onboarding DTO checks
--    the pattern, not the reserved list) and administer this operator as its
--    TENANT_ADMIN. Re-applying this file does NOT undo either — it is only re-run on a
--    checksum change, and INSERT IGNORE never deletes. Recovery is a DB reset / fresh
--    volume. Recorded, not fixed: the first route already exists for every demo row.
--
-- 🔵 The link key: `oidc_subject` MUST equal the account_id of the matching
--    `iam`-tenant credential (auth-service migration-dev
--    R__seed_demo_viewer_operator_credential.sql). Operator resolution is
--    account_id-only (TASK-MONO-299); a mismatch fail-closes to a console 401 that
--    renders as `operator_exchange_unavailable`. auth-service
--    `DemoViewerOperatorSeedTest` compares the two files.
--
-- Idempotent: INSERT ... ON DUPLICATE KEY UPDATE (safe for R__ re-runs).

INSERT INTO admin_operators (
    operator_id, tenant_id, email, password_hash, display_name, status,
    oidc_subject, created_at, updated_at, version
) VALUES (
    'demo-viewer', 'demo-viewer', 'viewer@demo.com',
    '$argon2id$v=16$m=65536,t=3,p=1$NR1Seql5fgXB0hQ7CmpFL6RyiXvL86lxeZCobfiBdRxzRlTkkcv6iIZDJq9eQ32QmKQMylwsG+IP25S1aaw9vw$kTFrCq8cQG4HVUKioosaD88eiXZkQesTp5Xc8yylaSM',
    'Demo Viewer (권한 없음)', 'ACTIVE',
    -- == credentials.account_id of the viewer `iam`-tenant row (auth-service
    --    migration-dev R__seed_demo_viewer_operator_credential.sql).
    '0199de70-0000-7000-8000-00000000ad05',
    NOW(6), NOW(6), 0
)
ON DUPLICATE KEY UPDATE
    tenant_id    = VALUES(tenant_id),
    oidc_subject = VALUES(oidc_subject),
    status       = 'ACTIVE',
    updated_at   = NOW(6);

-- Intentionally NO admin_operator_roles and NO operator_tenant_assignment rows
-- (see "WHAT MINIMAL MEANS" above). DemoOperatorSeedIntegrationTest asserts both are
-- empty, so adding one argues with a red test rather than with this comment.
