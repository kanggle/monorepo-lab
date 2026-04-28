-- Dev/standalone seed: master-service read-model snapshots that mirror the
-- baseline rows seeded by master-service's V99–V102 seeds. Activated via
-- spring.flyway.locations in application-dev.yml.
--
-- Rationale: inventory-service in v1 boots with an empty master read-model
-- and waits for `master.*` consumer events to populate it. For local-dev
-- walk-throughs without a running master-service we pre-load one Location,
-- one SKU (LOT-tracked), and one Lot so REST endpoints can be exercised
-- against a non-empty cache.
--
-- UUIDs match master-service seeds verbatim — keep these in sync if either
-- side rotates seed identifiers.

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
    id, sku_code, tracking_type, base_uom, status, cached_at, master_version
) VALUES (
    '01910000-0000-7000-8000-000000000403',
    'SKU-APPLE-001',
    'LOT',
    'EA',
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
