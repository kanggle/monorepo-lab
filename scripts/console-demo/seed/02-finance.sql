-- =============================================================================
-- TASK-MONO-170 — console full-stack DEMO seed — Finance (acme-corp)
-- =============================================================================
-- Applied against the per-project `finance-platform-mysql` (finance_db) AFTER
-- finance account-service is healthy (Flyway done):
--
--   docker exec -i finance-platform-mysql mysql -uroot -proot finance_db < 02-finance.sql
--
-- Rows reuse tests/federation-hardening-e2e/fixtures/seed-domains.sql (finance
-- section). The acme-corp account (a200) matches the multi-operator's
-- admin_operators.finance_default_account_id, so the **Finance 운영** page —
-- account-id driven (getAccount/getBalances/listTransactions) — renders live
-- balance data when the active tenant is acme-corp (entitled_domains ∋ finance).
-- The '*' row supports the SUPER_ADMIN overview card.
--
-- owner_ref is the AES-256-GCM opaque blob from the fixture (same key as the
-- finance compose overlay); the UI surfaces balance, not a decrypted owner_ref.
-- Re-runnable: INSERT IGNORE.
-- =============================================================================
USE `finance_db`;

INSERT IGNORE INTO accounts (
    id, tenant_id, owner_ref, status, kyc_level, currency,
    created_at, updated_at, version
) VALUES
(
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8a000', '*',
    'v1:Al7AbOFq84oJ2wYqG+RB7CulHFYrnpNnNjp55iEWoJqqvscRZPN9mW46xrgq4w==',
    'ACTIVE', 'FULL', 'KRW', NOW(6), NOW(6), 0
),
(
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8a200', 'acme-corp',
    'v1:Al7AbOFq84oJ2wYqG+RB7CulHFYrnpNnNjp55iEWoJqqvscRZPN9mW46xrgq4w==',
    'ACTIVE', 'FULL', 'KRW', NOW(6), NOW(6), 0
);

INSERT IGNORE INTO balances (
    id, account_id, tenant_id, currency, ledger_minor, held_minor,
    created_at, updated_at, version
) VALUES
(
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8b001',
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8a000', '*', 'KRW', 1000000, 0,
    NOW(6), NOW(6), 0
),
(
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8b201',
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8a200', 'acme-corp', 'KRW', 500000, 0,
    NOW(6), NOW(6), 0
);
