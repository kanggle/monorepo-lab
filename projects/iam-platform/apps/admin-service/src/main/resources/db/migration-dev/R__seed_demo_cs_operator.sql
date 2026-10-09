-- !!! DEV/DEMO ONLY — DO NOT run in production. Loaded via
-- spring.flyway.locations in non-prod profiles only (application.yml locations =
-- db/migration,db/migration-dev; application-prod.yml restricts to db/migration). !!!
--
-- TASK-MONO-781 — the CS 2nd-line demo operator: `cs@demo.com`.
--
-- WHAT IT IS FOR
-- ---------------------------------------------------------------------------
-- platform-console TASK-PC-FE-326 lets an operator who holds SUPPORT_LOCK
-- (account.lock / account.unlock / account.force_logout / audit.read — and NOT
-- account.read) open «계정 운영» in an email-search-only mode. No seeded identity had
-- that shape, so the mode could not be shown on the demo:
--   - demo@demo.com is SUPER_ADMIN (it HAS account.read → the full list renders);
--   - viewer@demo.com has no role at all;
--   - platform@demo.com has no role and is confined to fan-platform.
-- Owner decision 2026-10-09 UTC: seed ONE dedicated identity for that role.
--
-- WHY A SEPARATE FILE (same reasoning as R__seed_demo_viewer_operator.sql)
-- ---------------------------------------------------------------------------
-- auth-service `DemoSecondOperatorSeedTest` parses R__seed_demo_operator.sql and asserts
-- EXACTLY two operators, both in demo-corp (the ERP Separation-of-Duties pair). This
-- operator is not part of that pair, so it gets its own artifact and its own pins
-- (auth-service `DemoCsOperatorSeedTest` + `DemoCsOperatorDerivedRolesTest`,
-- admin-service `DemoOperatorSeedIntegrationTest`).
--
-- WHAT EACH CHOICE MEANS
-- ---------------------------------------------------------------------------
-- 1. Home tenant `ecommerce`. The only demo consumer (`demo@demo.com`, consumer pool) is
--    a member of the SITE tenants `ecommerce` and `fan-platform`, and the console's email
--    search finds a pool account only under a site tenant it belongs to (multi-tenancy.md
--    § 5). `fan-platform` is platform-operator-only (ADR-MONO-079 rider R3), so `ecommerce`
--    is the one tenant in which a customer-side CS operator can find that consumer.
--
-- 2. SUPPORT_LOCK granted with tenant_id = 'ecommerce' — NOT '*'. A site operator's lock
--    is a «사이트 잠금»: on a pool member it locks only that site's membership
--    (scope = SITE_MEMBERSHIP, TASK-BE-621). A platform-scope grant would lock the whole
--    consumer account across every site — the opposite of CS 2nd-line.
--    require_2fa is FALSE for SUPPORT_LOCK (V0006/V0009; V0013 flips only SUPER_ADMIN and
--    SECURITY_ANALYST), and no tenant_entry_policy row is seeded, so this login needs no
--    second factor.
--
-- 3. confined_tenant_id = 'ecommerce' (Flyway V0046, the TASK-MONO-751 mechanism). The
--    effective scope is already {ecommerce} (home, no assignment row); the confinement keeps
--    it that way if a visitor adds an assignment through the public SUPER_ADMIN login — an
--    assignment to demo-corp would otherwise derive all five domain OPERATOR roles.
--
-- 4. NO operator_tenant_assignment row — the home tenant is already in the effective
--    scope (TenantScopeResolver: assignment rows ∪ {home}).
--
-- 🔴 DISCLOSED TRADE-OFF (owner decision A, 2026-10-09 UTC — TASK-MONO-781 AC-0 #1)
-- ---------------------------------------------------------------------------
-- The console ALWAYS assumes the selected tenant, and the assumed token's roles are
-- DERIVED from that tenant's ACTIVE subscriptions, not from admin_operator_roles
-- (auth-service TenantClaimTokenCustomizer → OperatorRoleDerivation, ADR-MONO-035).
-- `ecommerce` subscribes to `ecommerce` (account-service V0022) and `wms` (V0025), so
-- this CS identity, once it selects `ecommerce`, also holds ECOMMERCE_OPERATOR and the
-- WMS operator roles (INBOUND/OUTBOUND/INVENTORY _WRITE among them). Nothing narrows that
-- for a home tenant (permission_set_id is not read on the assume path). Rejected
-- alternatives: B = change the ADR-MONO-035 derivation (ADR-level, out of scope);
-- C = a fake site tenant with no subscriptions. auth-service
-- `DemoCsOperatorDerivedRolesTest` pins the exact role set, so a change to the
-- subscriptions or the derivation turns red instead of silently making the disclosure false.
--
-- 🔴 Same KNOWN LIMIT as the viewer seed: demo@demo.com is SUPER_ADMIN with published
--    credentials; re-applying this file never deletes what a visitor added.
--
-- 🔵 The link key: `oidc_subject` MUST equal the account_id of the matching `iam`-tenant
--    credential (auth-service migration-dev R__seed_demo_cs_operator_credential.sql).
--    Operator resolution is account_id-only (TASK-MONO-299); a mismatch fail-closes to a
--    console 401 that renders as `operator_exchange_unavailable`.
--
-- Idempotent: INSERT ... ON DUPLICATE KEY UPDATE / INSERT IGNORE (safe for R__ re-runs).

INSERT INTO admin_operators (
    operator_id, tenant_id, email, password_hash, display_name, status,
    oidc_subject, confined_tenant_id, created_at, updated_at, version
) VALUES (
    'demo-cs', 'ecommerce', 'cs@demo.com',
    '$argon2id$v=16$m=65536,t=3,p=1$NR1Seql5fgXB0hQ7CmpFL6RyiXvL86lxeZCobfiBdRxzRlTkkcv6iIZDJq9eQ32QmKQMylwsG+IP25S1aaw9vw$kTFrCq8cQG4HVUKioosaD88eiXZkQesTp5Xc8yylaSM',
    'Demo CS (CS 2선 · 계정 제어)', 'ACTIVE',
    -- == credentials.account_id of the CS operator's `iam`-tenant row
    --    (auth-service migration-dev R__seed_demo_cs_operator_credential.sql).
    '0199de70-0000-7000-8000-00000000ad08',
    -- confined_tenant_id — ecommerce ONLY (choice 3 above).
    'ecommerce',
    NOW(6), NOW(6), 0
)
ON DUPLICATE KEY UPDATE
    tenant_id          = VALUES(tenant_id),
    oidc_subject       = VALUES(oidc_subject),
    confined_tenant_id = VALUES(confined_tenant_id),
    status             = 'ACTIVE',
    updated_at         = NOW(6);

-- SUPPORT_LOCK, bound to the SITE tenant `ecommerce` (choice 2 above).
INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
SELECT o.id, r.id, 'ecommerce', NOW(6), NULL
  FROM admin_operators o
  JOIN admin_roles r ON r.name = 'SUPPORT_LOCK'
 WHERE o.operator_id = 'demo-cs';

-- Intentionally NO operator_tenant_assignment row (choice 4 above).
-- DemoOperatorSeedIntegrationTest asserts the exact grant set and the empty assignment set.
