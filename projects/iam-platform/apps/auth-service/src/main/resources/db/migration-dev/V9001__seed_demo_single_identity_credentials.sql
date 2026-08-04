-- !!! DEV/DEMO ONLY — never reaches production. !!!
-- Loaded via spring.flyway.locations ONLY under the `e2e` profile
-- (application-e2e.yml); application.yml pins production to db/migration alone.
-- This directory is NEW in TASK-BE-571 — auth-service had no dev-seed location
-- before (account-service and admin-service already had one).
--
-- TASK-BE-571 — the portfolio-demo single identity. ONE email + ONE password the
-- interviewer types on all three surfaces:
--
--     demo@demo.com / Demo1234!
--
-- WHY THREE ROWS FOR ONE "ACCOUNT" (this is the model, not a workaround)
-- ---------------------------------------------------------------------------
-- `credentials` is tenant-scoped: UNIQUE (tenant_id, email) since V0007. The
-- tenant a login resolves to is decided by the OIDC client the user came through
-- (SavedRequestTenantResolver reads the saved /oauth2/authorize `client_id` and
-- takes that client's `custom.tenant_id`), and CredentialAuthenticationProvider
-- looks the credential up **scoped to that tenant first**, falling back to a
-- cross-tenant lookup only on a miss (TASK-BE-507 D1-a).
--
-- Two consequences drive the shape below:
--
--   1. The `roles` claim seed fires ONLY when the principal's own tenant EQUALS
--      the client's platform (TenantClaimTokenCustomizer#seedFor, TASK-MONO-381).
--      So the storefront credential MUST live in `ecommerce` to get CUSTOMER, and
--      the fan credential MUST live in `fan-platform` to get FAN. A single shared
--      row cannot produce both.
--
--   2. The cross-tenant fallback fails CLOSED on ambiguity (an email in >1 tenant
--      with no initiating client → BadCredentialsException). With three rows the
--      scoped lookup must HIT on every surface, which is why the console row is
--      seeded under `iam` — the platform-console-web client's own tenant (V0024
--      renamed it from `gap`). Seeding it anywhere else would miss the scoped
--      lookup, reach the fallback, find three rows, and fail closed.
--
--      ⚠️ Corollary worth knowing when demoing: logging in at the IAM login page
--      DIRECTLY (no /oauth2/authorize first) has no initiating client, so the
--      fallback sees three rows and rejects. Always start from the app.
--
-- account_id is the OIDC `sub` (TenantClaimTokenCustomizer#alignSubToAccountId),
-- and `credentials.account_id` carries a GLOBAL unique index (V0001) — hence
-- three distinct UUIDs, not one shared value. The `iam` one is the link key that
-- admin_operators.oidc_subject must equal (see the admin-service repeatable seed);
-- operator resolution is account_id-only since TASK-MONO-299, with no email
-- fallback, so a mismatch here is a silent 401 at the console.
--
-- The hash is Argon2id(`Demo1234!`) produced by the SAME
-- com.example.security.password.Argon2idPasswordHasher the app verifies with
-- (m=65536,t=3,p=1). DemoSeedCredentialTest re-verifies it on every build, so a
-- hash/password drift turns a test red instead of a demo login.
--
-- Idempotent: INSERT IGNORE (re-runs and pre-existing rows are a no-op).

INSERT IGNORE INTO credentials (
    tenant_id, account_id, email,
    credential_hash, hash_algorithm, created_at, updated_at, version
) VALUES
-- storefront (web-store) — client `ecommerce-web-store-client`, tenant `ecommerce`
-- → roles [CUSTOMER] via RoleSeedPolicy.
(
    'ecommerce', '0199de70-0000-7000-8000-00000000ec01', 'demo@demo.com',
    '$argon2id$v=16$m=65536,t=3,p=1$NR1Seql5fgXB0hQ7CmpFL6RyiXvL86lxeZCobfiBdRxzRlTkkcv6iIZDJq9eQ32QmKQMylwsG+IP25S1aaw9vw$kTFrCq8cQG4HVUKioosaD88eiXZkQesTp5Xc8yylaSM',
    'argon2id', NOW(6), NOW(6), 0
),
-- fan web — client `fan-platform-user-flow-client`, tenant `fan-platform`
-- → roles [FAN] via RoleSeedPolicy.
(
    'fan-platform', '0199de70-0000-7000-8000-00000000fa02', 'demo@demo.com',
    '$argon2id$v=16$m=65536,t=3,p=1$NR1Seql5fgXB0hQ7CmpFL6RyiXvL86lxeZCobfiBdRxzRlTkkcv6iIZDJq9eQ32QmKQMylwsG+IP25S1aaw9vw$kTFrCq8cQG4HVUKioosaD88eiXZkQesTp5Xc8yylaSM',
    'argon2id', NOW(6), NOW(6), 0
),
-- console — client `platform-console-web`, tenant `iam`. Seeds NO domain roles
-- (correct: a base operator token carries none; domain roles are derived at
-- assume-tenant from the selected tenant's entitled domains, TASK-BE-376).
(
    'iam', '0199de70-0000-7000-8000-00000000ad03', 'demo@demo.com',
    '$argon2id$v=16$m=65536,t=3,p=1$NR1Seql5fgXB0hQ7CmpFL6RyiXvL86lxeZCobfiBdRxzRlTkkcv6iIZDJq9eQ32QmKQMylwsG+IP25S1aaw9vw$kTFrCq8cQG4HVUKioosaD88eiXZkQesTp5Xc8yylaSM',
    'argon2id', NOW(6), NOW(6), 0
);
