-- !!! DEV/DEMO ONLY — DO NOT run in production. Loaded via
-- spring.flyway.locations in non-prod profiles only (application.yml locations =
-- db/migration,db/migration-dev; application-prod.yml restricts to db/migration). !!!
--
-- TASK-MONO-798 — the OWNER-ONLY, unconfined platform-scope ('*') SUPER_ADMIN operator.
--
-- WHAT IT IS FOR
-- ---------------------------------------------------------------------------
-- Owner decision 2026-10-17 UTC (recorded verbatim in tasks/…/TASK-MONO-798-*.md): the demo
-- had no unconfined '*' SUPER_ADMIN with an IdP login — `platform@demo.com` is confined to
-- `fan-platform` (TASK-MONO-751) and the dev `admin` / dev1 rows carry a placeholder
-- oidc_subject and no credential — so four live checks of TASK-MONO-672 could not be run.
-- This row is that operator, in the exact form the owner decided:
--
--   1. Unconfined '*' scope, IdP (auth-service credential) login, NOT in any public doc.
--      `infra/demo/owner-operator.sh --check-undisclosed` fails if its email, operator id or
--      account id appears in any tracked file outside the short allowlist in that script.
--   2. NO fixed password anywhere in the repo. There is deliberately NO auth-service
--      credential seed for this account. The credential row is written at boot by
--      `infra/demo/owner-operator.sh provision` with an Argon2id hash of a password that is
--      generated on the instance and stored only in SSM Parameter Store (SecureString).
--      This row's own password_hash is NULL — the break-glass admin login has no way in.
--   3. SUSPENDED by default. Every boot (`provision`) and every re-run of this file puts it
--      back to SUSPENDED. Only the owner-run `owner-operator.sh activate` makes it ACTIVE,
--      and `deactivate` returns it to SUSPENDED (and revokes its admin refresh tokens).
--      A SUSPENDED row is refused by the token exchange (OperatorConsoleAdmission: status =
--      ACTIVE) and by the assume gate (OperatorAssignmentCheckUseCase: non-ACTIVE → refused).
--
-- WHAT EACH CHOICE MEANS
-- ---------------------------------------------------------------------------
-- · Home tenant '*' and confined_tenant_id NULL — the point of the row: the assignment check
--   lets a '*' operator assume any registered tenant (step 2b), which is what the checks need
--   (assume into a PENDING / SUSPENDED throwaway tenant, the overview card, `/tenants`).
-- · SUPER_ADMIN bound at '*'. The demo-only OD-5 relaxation (db/migration-demo) turns the
--   role's require_2fa off on the portfolio demo; elsewhere this row cannot log in at all
--   (no credential exists outside the demo host).
-- · NO operator_tenant_assignment row — '*' covers every tenant (ADR-082 D2).
-- · ONE legacy-compat ('*') domain set row, given in R__seed_zz_demo_domain_permission_sets.sql
--   exactly as demo-platform's — without it an assumed token carries no domain role, and the
--   subscription-cancel check («the re-exchange carries no role of that domain») has nothing
--   to lose.
--
-- 🔵 The link key: `oidc_subject` MUST equal the account_id the boot step writes into
--    auth_db.credentials (tenant `iam`) — `OWNER_ACCOUNT_ID` in infra/demo/owner-operator.sh.
--    `owner-operator.sh --self-test` compares the two files.
--
-- WHY A SEPARATE FILE
-- ---------------------------------------------------------------------------
-- Editing an existing R__ file changes its checksum and re-runs it; the existing demo seeds
-- are pinned file-by-file (DemoSecondOperatorSeedTest, DemoPlatformOperatorSeedTest). A new
-- file leaves every one of those pins and every existing row untouched.
--
-- Idempotent: INSERT ... ON DUPLICATE KEY UPDATE / INSERT IGNORE (safe for R__ re-runs).
-- 🔴 A re-run resets status to SUSPENDED and password_hash to NULL — the safe direction.

INSERT INTO admin_operators (
    operator_id, tenant_id, email, password_hash, display_name, status,
    oidc_subject, confined_tenant_id, created_at, updated_at, version
) VALUES (
    'demo-star-owner', '*', 'star-owner@demo.com',
    NULL,
    'Owner Operator (소유자 전용 · 측정용)', 'SUSPENDED',
    -- == the account_id owner-operator.sh writes into auth_db.credentials (tenant `iam`).
    '0199de70-0000-7000-8000-00000000ad09',
    -- confined_tenant_id — NULL: unconfined (choice 1 above).
    NULL,
    NOW(6), NOW(6), 0
)
ON DUPLICATE KEY UPDATE
    tenant_id          = VALUES(tenant_id),
    oidc_subject       = VALUES(oidc_subject),
    confined_tenant_id = NULL,
    password_hash      = NULL,
    status             = 'SUSPENDED',
    updated_at         = NOW(6);

-- SUPER_ADMIN, bound to the platform scope '*'.
INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
SELECT o.id, r.id, o.tenant_id, NOW(6), NULL
  FROM admin_operators o
  JOIN admin_roles r ON r.name = 'SUPER_ADMIN'
 WHERE o.operator_id = 'demo-star-owner';

-- Intentionally NO operator_tenant_assignment row (see "WHAT EACH CHOICE MEANS" above).
-- DemoOperatorSeedIntegrationTest asserts the exact role set, the absence of memberships,
-- SUSPENDED, NULL password_hash and NULL confinement.
