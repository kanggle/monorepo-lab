-- =============================================================================
-- TASK-MONO-132 — platform-console e2e harness finance seed (phase 2.5)
-- =============================================================================
-- Sibling fixture of seed.sql. Contains the subset of e2e seed data that
-- requires the finance-account-service Flyway V1__init.sql to have already
-- created the finance_db.accounts + finance_db.balances tables.
--
-- The CI workflow applies this fixture at phase 2.5 — AFTER
-- finance-account-service is healthy (Flyway finished) and BEFORE Playwright
-- launches. See `.github/workflows/nightly-e2e.yml` step
-- `Apply seed-finance.sql`.
--
-- For the GAP-side seed data (operators, OIDC tweaks, finance_db database
-- creation) which can be applied at phase 1.5 (before finance-account-service
-- starts), see seed.sql.
--
-- AC-3 / AC-5 / AC-7 byte-diff invariant: this file lives in the
-- platform-console project and does runtime INSERTs against finance-platform
-- schemas. `projects/finance-platform/` is not touched on disk.
--
-- Re-runnable: every statement is idempotent (INSERT IGNORE). The CI workflow
-- runs seed-finance.sql exactly once per spin-up; idempotency is defensive
-- against re-attempts.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- finance_db — single account + balance row.
--
--    TASK-BE-312: tenant_id='*' (platform-scope sentinel). The SUPER_ADMIN
--    operator's JWT carries tenant_id='*' (from auth_db.credentials row in
--    seed.sql + admin_db.admin_operators row — all aligned on the
--    platform-scope sentinel). finance-account-service applies the JWT
--    claim verbatim to BOTH layers:
--      1. TenantClaimValidator — accepts '*' (wildcard) OR 'finance';
--      2. data-layer (`AccountJpaRepository.findByIdAndTenantId` +
--         `BalanceJpaRepository.findByAccountIdAndTenantId`) — filters
--         literally by the JWT claim. Hence the row's tenant_id must also
--         be '*' for the wildcard JWT to read it.
--
--    The console-web TENANT_COOKIE ('fan-platform') drives only the
--    OUTBOUND `X-Tenant-Id` header (D6.A forward-verbatim). It does NOT
--    modify the JWT claim, so the finance leg evaluates JWT-claim='*' on
--    both validator + data-layer axes.
--
--    UUID matches the value the 2 specs Save into MyProfileForm /
--    OperatorProfileEditDialog (operators-profile.spec.ts +
--    operators-admin-profile.spec.ts).
-- ---------------------------------------------------------------------------
USE `finance_db`;

INSERT IGNORE INTO accounts (
    id, tenant_id, owner_ref, status, kyc_level, currency,
    created_at, updated_at, version
) VALUES (
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8a000',
    '*',
    'e2e-test-owner-ref',
    'ACTIVE',
    'FULL',
    'KRW',
    NOW(6), NOW(6), 0
);

INSERT IGNORE INTO balances (
    id, account_id, tenant_id, currency,
    ledger_minor, held_minor,
    created_at, updated_at, version
) VALUES (
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8b001',
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8a000',
    '*',
    'KRW',
    1000000, 0,
    NOW(6), NOW(6), 0
);
