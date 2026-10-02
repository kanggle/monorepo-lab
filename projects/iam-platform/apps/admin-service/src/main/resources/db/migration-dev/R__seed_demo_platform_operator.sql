-- !!! DEV/DEMO ONLY — DO NOT run in production. Loaded via
-- spring.flyway.locations in non-prod profiles only (application.yml locations =
-- db/migration,db/migration-dev; application-prod.yml restricts to db/migration). !!!
--
-- TASK-MONO-751 — the PLATFORM demo operator: `platform@demo.com`.
--
-- WHAT IT IS FOR
-- ---------------------------------------------------------------------------
-- ADR-MONO-079 D4-A opened the fan directory (agencies · artists · groups in
-- artist-service) to PLATFORM operators — `admin_operators.tenant_id = '*'` — and rider
-- R3 keeps it closed to customer-tenant operators (TASK-MONO-750). `demo@demo.com` is a
-- customer (`demo-corp`) operator, so it can never open the console's fan screens, and
-- TASK-MONO-750 measured 0 platform-scope operators in this directory. Owner decision
-- 2026-10-03 (TASK-MONO-751): «플랫폼 운영자 데모 계정 시드» — R3 stays as is,
-- `demo@demo.com` stays a customer operator, and ONE platform-operator identity is seeded.
--
-- WHY A SEPARATE FILE (same reasoning as R__seed_demo_viewer_operator.sql)
-- ---------------------------------------------------------------------------
-- auth-service `DemoSecondOperatorSeedTest` parses R__seed_demo_operator.sql and asserts
-- EXACTLY two operators, both in demo-corp (the ERP Separation-of-Duties pair). This
-- operator is not part of that pair, so it gets its own artifact and its own pin
-- (auth-service `DemoPlatformOperatorSeedTest`). Repeatable (R__), not the V9000+ band, for
-- the ordering reason that file's header measures.
--
-- WHAT EACH CHOICE MEANS
-- ---------------------------------------------------------------------------
-- 1. Home tenant '*' — the platform-scope sentinel (ADR-002, V0025). It is the WHOLE point:
--    `OperatorAssignmentCheckUseCase` lets only a '*' operator assume `fan-platform`
--    (step 2b), and `ConsoleRegistryUseCase` lists `fan-platform` for '*' operators only.
--    At assume time the token gets `tenant_id=fan-platform` + `roles=[FAN_OPERATOR]`, derived
--    from that tenant's `fan` subscription (account-service V0031) — no role row is needed.
--
-- 2. NO admin_operator_roles row. Login and the token exchange need only an ACTIVE row
--    whose `oidc_subject` matches (TokenExchangeService); the registry read carries no
--    @RequiresPermission; the fan directory's FAN_OPERATOR is derived per assumption, not
--    stored. So every `@RequiresPermission` admin screen answers 403 for this identity —
--    it is a fan-directory operator, not a second SUPER_ADMIN.
--
-- 3. NO operator_tenant_assignment row — '*' already covers every tenant.
--
-- 🔴 KNOWN LIMIT — '*' is wider than the fan directory. A platform-scope operator may
--    assume ANY registered tenant (assignment check: '*' ⇒ assigned=true), and each
--    assumption derives that tenant's domain OPERATOR roles from its subscriptions. On the
--    public demo this identity can therefore also operate the domain tenants (ecommerce,
--    wms, scm, erp, finance, demo-corp, the e2e customer tenants). That is what «platform
--    operator» means in this codebase (R3 defines the fan path by exactly that sentinel);
--    narrowing it would need a new scope concept, not a seed. Recorded, not fixed.
--
-- 🔵 The link key: `oidc_subject` MUST equal the account_id of the matching `iam`-tenant
--    credential (auth-service migration-dev R__seed_demo_platform_operator_credential.sql).
--    Operator resolution is account_id-only (TASK-MONO-299); a mismatch fail-closes to a
--    console 401 that renders as `operator_exchange_unavailable`. auth-service
--    `DemoPlatformOperatorSeedTest` compares the two files.
--
-- Idempotent: INSERT ... ON DUPLICATE KEY UPDATE (safe for R__ re-runs).

INSERT INTO admin_operators (
    operator_id, tenant_id, email, password_hash, display_name, status,
    oidc_subject, created_at, updated_at, version
) VALUES (
    'demo-platform', '*', 'platform@demo.com',
    '$argon2id$v=16$m=65536,t=3,p=1$NR1Seql5fgXB0hQ7CmpFL6RyiXvL86lxeZCobfiBdRxzRlTkkcv6iIZDJq9eQ32QmKQMylwsG+IP25S1aaw9vw$kTFrCq8cQG4HVUKioosaD88eiXZkQesTp5Xc8yylaSM',
    'Demo Platform Operator (팬 디렉터리)', 'ACTIVE',
    -- == credentials.account_id of the platform operator's `iam`-tenant row
    --    (auth-service migration-dev R__seed_demo_platform_operator_credential.sql).
    '0199de70-0000-7000-8000-00000000ad06',
    NOW(6), NOW(6), 0
)
ON DUPLICATE KEY UPDATE
    tenant_id    = VALUES(tenant_id),
    oidc_subject = VALUES(oidc_subject),
    status       = 'ACTIVE',
    updated_at   = NOW(6);

-- Intentionally NO admin_operator_roles and NO operator_tenant_assignment rows
-- (see "WHAT EACH CHOICE MEANS" above). DemoPlatformOperatorSeedTest asserts both are absent.
