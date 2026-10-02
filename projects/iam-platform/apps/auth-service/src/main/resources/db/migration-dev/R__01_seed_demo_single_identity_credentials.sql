-- =============================================================================
-- REPEATABLE (R__) — this file was `V9001__…` until TASK-MONO-524 (2026-08-12).
-- =============================================================================
-- The V9000+ band was chosen (TASK-MONO-207) to stop dev seeds COLLIDING with
-- production version numbers. It did that, and in exchange it poisoned ORDERING:
-- once 9001 is the highest APPLIED version, every later production migration
-- resolves BELOW it = out-of-order, which Flyway rejects by default. auth_db hit
-- exactly that when V0032 landed (2026-08-11) and crash-looped on every host
-- with an existing volume. CI never saw it — CI always starts from a fresh
-- volume, where everything applies in order.
--
-- R__ has neither problem: a repeatable carries NO version (nothing to collide
-- with, nothing to be out of order against) and always runs AFTER every
-- versioned migration — which is the order seed data wants anyway.
--
-- Editing rules this buys, all three load-bearing:
--   · it re-runs whenever its checksum changes ⇒ every statement MUST stay
--     idempotent (this file: INSERT IGNORE only)
--   · repeatables run in DESCRIPTION order ⇒ the `NN_` prefix is the ordering
--     contract between the seeds in this directory. Do not drop it.
--   · never rename an already-applied R__ file — the description is its
--     identity, and a rename reads as "applied migration not resolved locally",
--     which Flyway does NOT tolerate. (A missing VERSIONED file it *does*
--     tolerate, silently, as `future` — that asymmetry is measured in the doc.)
--
-- Rationale in full + how to recover an already-poisoned database:
--   projects/iam-platform/docs/flyway-dev-seed-migrations.md
-- =============================================================================
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
-- WHY TWO ROWS FOR ONE PERSON (this is the model, not a workaround)
-- ---------------------------------------------------------------------------
-- 🔵 TASK-MONO-744 (2026-10-02 UTC, ADR-MONO-078 A) — this was THREE rows until
-- then: `ecommerce` (…ec01) + `fan-platform` (…fa02) + `iam` (…ad03). The two
-- consumer rows are now ONE consumer-POOL credential (tenant `consumer-pool`,
-- account `…ec01`), the console row is unchanged (D1 — the console stays an
-- operator login). Why ec01 survives is in account-service R__05's header.
--
-- `credentials` is tenant-scoped: UNIQUE (tenant_id, email) since V0007. The
-- tenant a login resolves to is decided by the OIDC client the user came through
-- (SavedRequestTenantResolver reads the saved /oauth2/authorize `client_id` and
-- takes that client's `custom.tenant_id`), and CredentialAuthenticationProvider
-- resolves the credential per client (multi-tenancy.md § 소비자 계정 풀 § 4):
--
--   1. A CONSUMER-site client (store `ecommerce-web-store-client`, fan
--      `fan-platform-user-flow-client`) looks for the POOL credential FIRST
--      (TASK-BE-615). One row therefore logs the person into both sites, and
--      the token says `sub = …ec01`, `tenant_id = <that site>`, roles = the
--      site's seed (store CUSTOMER / fan FAN) ∪ its consumer_site_roles — never
--      `consumer-pool`, never the other site's roles. The issuer mints the token
--      only for a site the account is an ACTIVE member of; R__05 makes it a
--      member of both, so there is no first-visit consent screen for the demo.
--
--   2. The CONSOLE client (`platform-console-web`, tenant `iam`) never maps a
--      pool principal (D1): its scoped lookup hits the `iam` row below. Seeding
--      that row anywhere else would miss the scoped lookup, reach the
--      cross-tenant fallback, find two rows, and fail closed.
--
--      ⚠️ Corollary worth knowing when demoing: logging in at the IAM login page
--      DIRECTLY (no /oauth2/authorize first) has no initiating client, so the
--      fallback sees two rows and rejects. Always start from the app.
--
-- 🔴 A pool credential and a SITE credential for the same email must never
-- coexist (§ 2/§ 3 — the pool one would win and the site one would be
-- unreachable). This file seeds none: the old `ecommerce`/`fan-platform` rows are
-- gone. On an EXISTING local volume they are still there and the pool row below
-- is ignored (its account_id collides with the old `ecommerce` row on the global
-- unique index) — that volume keeps the old three-row shape, consistently. No
-- DELETE here, deliberately; start from a fresh auth_db + account_db volume to get
-- the pool shape (the demo server always starts fresh — R__05's header).
--
-- account_id is the OIDC `sub` (TenantClaimTokenCustomizer#alignSubToAccountId),
-- and `credentials.account_id` carries a GLOBAL unique index (V0001) — hence
-- distinct UUIDs per row. The pool one MUST equal account-service R__05's pool
-- account id (DemoSeedCredentialTest compares them). The `iam` one is the link
-- key that admin_operators.oidc_subject must equal (see the admin-service
-- repeatable seed); operator resolution is account_id-only since TASK-MONO-299,
-- with no email fallback, so a mismatch here is a silent 401 at the console.
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
-- consumer pool — store (`ecommerce-web-store-client`) AND fan
-- (`fan-platform-user-flow-client`) both resolve here: pool credential first
-- (TASK-BE-615). Roles per site at issuance: store [CUSTOMER] · fan [FAN].
(
    'consumer-pool', '0199de70-0000-7000-8000-00000000ec01', 'demo@demo.com',
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
