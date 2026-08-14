-- Dev/standalone seed: single ACTIVE warehouse (WH01) for manual walkthroughs.
-- Activated via spring.flyway.locations in application-dev.yml and by
-- infra/demo/wms-devseed.override.yml.
--
-- 🔴 REPEATABLE (R__), NOT VERSIONED — TASK-MONO-531. Do not renumber this back.
-- This was V99__seed_dev_warehouse.sql. `db/seed` merges into the SAME Flyway
-- sequence as `db/migration` whenever it is opened, so once V99 applied it became
-- the highest APPLIED version of master_db (production is only at V8) and every
-- production migration added afterwards would resolve BELOW it — an out-of-order
-- migration, which Flyway rejects by default. wms admin-service crash-looped on
-- exactly that on 2026-08-14 (TASK-BE-584 AC-0), iam auth-service on 2026-08-11
-- (TASK-MONO-524). A repeatable carries no version, so it cannot be out of order.
--
-- 🔴 THE `01_` PREFIX IS THE FK ORDER. Flyway runs repeatables in DESCRIPTION
-- order (alphabetical), not file-discovery order, so the numeric prefix is the
-- only thing keeping warehouse → zone → location correct:
--   R__01 warehouse · R__02 zones · R__03 locations · R__04 skus · R__05 partners
-- Renaming any of them re-orders the run and the FKs fail.
--
-- 🔴 A repeatable re-runs on every checksum change, over live data — so every
-- statement here MUST stay idempotent (ON CONFLICT DO NOTHING).
--
-- 🔴 Renaming an ALREADY-APPLIED repeatable makes Flyway reject the database
-- ("applied migration not resolved locally"). This name is final.

INSERT INTO warehouses (
    id, warehouse_code, name, address, timezone, status, version,
    created_at, created_by, updated_at, updated_by
) VALUES (
    '01910000-0000-7000-8000-000000000001',
    'WH01',
    'Seoul Main Warehouse',
    'Seoul, Korea',
    'Asia/Seoul',
    'ACTIVE',
    0,
    '2026-04-18T00:00:00Z',
    'seed-dev',
    '2026-04-18T00:00:00Z',
    'seed-dev'
)
ON CONFLICT (warehouse_code) DO NOTHING;
