-- Dev/standalone seed: master-service read-model snapshots that mirror the
-- baseline rows seeded by master-service's R__01–R__05 seeds. Activated via
-- spring.flyway.locations in application-dev.yml and by
-- infra/demo/wms-devseed.override.yml.
--
-- 🔴 REPEATABLE (R__), NOT VERSIONED — TASK-MONO-531. Do not renumber this back.
-- This was V99__seed_dev_masterref.sql, and inbound production is only at V9, so
-- once V99 applied it became the highest APPLIED version of inbound_db and every
-- later production migration would resolve BELOW it — an out-of-order migration,
-- which Flyway rejects by default. wms admin-service crash-looped on exactly that
-- on 2026-08-14 (TASK-BE-584 AC-0); iam auth-service on 2026-08-11
-- (TASK-MONO-524). A repeatable carries no version, so it cannot be out of order.
-- Every statement here must stay idempotent — a repeatable re-runs on every
-- checksum change, over live data. Renaming an already-applied repeatable makes
-- Flyway reject the database, so this name is final.
--
-- Rationale: inbound-service in v1 boots with an empty master read-model and
-- waits for `master.*` consumer events to populate it. For local-dev
-- walk-throughs without a running master-service we pre-load one Warehouse,
-- one Zone, one Location, one SKU (LOT-tracked), one Lot, and one Partner so
-- REST endpoints and webhook smoke calls can be exercised against a
-- non-empty cache.
--
-- UUIDs match master-service / inventory-service seeds verbatim — keep these
-- in sync if either side rotates seed identifiers.

INSERT INTO warehouse_snapshot (
    id, warehouse_code, status, cached_at, master_version
) VALUES (
    '01910000-0000-7000-8000-000000000001',
    'WH01',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO zone_snapshot (
    id, warehouse_id, zone_code, zone_type, status, cached_at, master_version
) VALUES (
    '01910000-0000-7000-8000-000000000101',
    '01910000-0000-7000-8000-000000000001',
    'Z-A',
    'AMBIENT',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO location_snapshot (
    id, location_code, warehouse_id, zone_id, location_type, status,
    cached_at, master_version
) VALUES (
    '01910000-0000-7000-8000-000000001001',
    'WH01-A-01-01-01',
    '01910000-0000-7000-8000-000000000001',
    '01910000-0000-7000-8000-000000000101',
    'STORAGE',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO sku_snapshot (
    id, sku_code, tracking_type, status, cached_at, master_version
) VALUES (
    '01910000-0000-7000-8000-000000000403',
    'SKU-APPLE-001',
    'LOT',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO lot_snapshot (
    id, sku_id, lot_no, expiry_date, status, cached_at, master_version
) VALUES (
    '01910000-0000-7000-8000-000000000601',
    '01910000-0000-7000-8000-000000000403',
    'L-20260418-A',
    '2026-05-18',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO partner_snapshot (
    id, partner_code, partner_type, status, cached_at, master_version
) VALUES (
    '01910000-0000-7000-8000-000000000801',
    'SUP-001',
    'SUPPLIER',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
)
ON CONFLICT (id) DO NOTHING;
