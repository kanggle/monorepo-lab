-- !!! DEV/DEMO ONLY — DO NOT run in production. Loaded via
-- spring.flyway.locations in non-prod profiles only (application.yml locations =
-- db/migration,db/migration-dev; application-prod.yml restricts to db/migration). !!!
--
-- TASK-BE-571 — the console side of the portfolio-demo single identity:
-- `demo@demo.com` becomes an operator whose home tenant is `demo-corp`
-- (the all-five-domain tenant seeded by account-service migration-dev V9005).
--
-- WHY REPEATABLE (R__) AND NOT A VERSIONED MIGRATION — this is load-bearing
-- ---------------------------------------------------------------------------
-- Unlike account-service, admin-service loads `db/migration-dev` in the DEFAULT
-- profile too, i.e. against developers' long-lived local admin_db instances. Its
-- versioned timeline is also CONTIGUOUS (V0001..V0045 with no gaps left — the
-- earlier dev seeds V0014/V0023/V0028 took the last of them), and it is still
-- growing.
--
-- So a dev seed placed in a high band (the V9000+ trick account-service uses)
-- would be applied at, say, V9001, and then the NEXT production migration
-- (V0046) would be resolved BELOW the highest applied version — an out-of-order
-- migration, which Flyway rejects by default (no `out-of-order: true` is
-- configured in this repo). That would not break CI or the demo; it would break
-- every developer's existing local database the day someone adds V0046.
--
-- A repeatable migration sidesteps the ordering question entirely: Flyway runs
-- R__ scripts AFTER all versioned ones and they take no part in version
-- ordering, so no future production migration can be made out-of-order by this
-- file. Re-running on checksum change is harmless because every statement below
-- is idempotent.
--
-- THE LINK KEY (the failure mode this seed exists to get right)
-- ---------------------------------------------------------------------------
-- `oidc_subject` MUST equal the OIDC `sub` of the console login, which is the
-- account UUID carried by the matching `iam`-tenant credential row
-- (auth-service migration-dev V9001). Operator resolution has been
-- account_id-ONLY since TASK-MONO-299 (ADR-MONO-040 Phase 3 part B removed the
-- email fallback), so an email-shaped or mismatched value here does not degrade
-- — it fail-closes to 401 at the console, which the UI renders as
-- `operator_exchange_unavailable`, the SAME text a load-induced 5s timeout
-- produces. Keep the two values literal and identical.
--
-- 2FA: not relaxed here, and not needed. `require_2fa` (TRUE for SUPER_ADMIN
-- since V0013) gates the password+TOTP admin login path; the console reaches
-- the operator plane through the OIDC token exchange, which enforces only
-- `status = 'ACTIVE'` (TokenExchangeService).

-- ---------------------------------------------------------------------------
-- 1. The operator. Home tenant = demo-corp.
-- ---------------------------------------------------------------------------
INSERT INTO admin_operators (
    operator_id, tenant_id, email, password_hash, display_name, status,
    oidc_subject, created_at, updated_at, version
) VALUES (
    'demo-operator', 'demo-corp', 'demo@demo.com',
    '$argon2id$v=16$m=65536,t=3,p=1$NR1Seql5fgXB0hQ7CmpFL6RyiXvL86lxeZCobfiBdRxzRlTkkcv6iIZDJq9eQ32QmKQMylwsG+IP25S1aaw9vw$kTFrCq8cQG4HVUKioosaD88eiXZkQesTp5Xc8yylaSM',
    'Demo Operator', 'ACTIVE',
    -- == credentials.account_id of the `iam`-tenant row (auth V9001).
    '0199de70-0000-7000-8000-00000000ad03',
    NOW(6), NOW(6), 0
)
ON DUPLICATE KEY UPDATE
    tenant_id    = VALUES(tenant_id),
    oidc_subject = VALUES(oidc_subject),
    status       = 'ACTIVE',
    updated_at   = NOW(6);

-- ---------------------------------------------------------------------------
-- 2. SUPER_ADMIN binding — console-shell reachability + the IAM screens.
--    NOTE the domain-ops pages are gated by the tenant's ENTITLEMENT, not by
--    this RBAC role (ADR-MONO-035); the role does not grant domain access.
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
SELECT o.id, r.id, o.tenant_id, NOW(6), NULL
  FROM admin_operators o
  JOIN admin_roles r ON r.name = 'SUPER_ADMIN'
 WHERE o.operator_id = 'demo-operator';

-- ---------------------------------------------------------------------------
-- 3. Assume-tenant assignment → demo-corp. The assume path resolves the
--    assignment explicitly (AssumeTenantAuthenticationProvider step 2 →
--    admin-service resolveAssignment), so the home tenant is listed here
--    rather than relied on implicitly. permission_set_id / org_scope NULL =
--    inherit the operator's roles / whole-tenant scope.
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO operator_tenant_assignment (operator_id, tenant_id, granted_at, granted_by, permission_set_id)
SELECT o.id, 'demo-corp', NOW(6), NULL, NULL
  FROM admin_operators o WHERE o.operator_id = 'demo-operator';
