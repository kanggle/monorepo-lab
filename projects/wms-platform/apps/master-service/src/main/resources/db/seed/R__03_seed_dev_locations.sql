-- Dev/standalone seed: one location under each seeded zone under WH01 so
-- manual walk-throughs exercise the flat location routes. Activated via
-- spring.flyway.locations in application-dev.yml and by
-- infra/demo/wms-devseed.override.yml.
--
-- 🔴 REPEATABLE (R__), NOT VERSIONED — TASK-MONO-531; full rationale in
-- R__01_seed_dev_warehouse.sql. This was V101__seed_dev_locations.sql.
--
-- 🔴 Depends on R__01 (warehouses) and R__02 (zones) having seeded
-- WH01 + Z-A / Z-C / Z-R. Flyway runs repeatables in DESCRIPTION order, so the
-- `01` < `02` < `03` prefixes are the FK ordering — load-bearing, not decoration.
--
-- Idempotent: ON CONFLICT DO NOTHING keeps re-runs safe (a repeatable re-runs on
-- every checksum change, over live data). Fixed UUIDs so the id is stable across
-- runs and referenceable from other seed files.

INSERT INTO locations (
    id, warehouse_id, zone_id, location_code,
    aisle, rack, level, bin,
    location_type, capacity_units, status, version,
    created_at, created_by, updated_at, updated_by
) VALUES
(
    '01910000-0000-7000-8000-000000001001',
    '01910000-0000-7000-8000-000000000001',
    '01910000-0000-7000-8000-000000000101',
    'WH01-A-01-01-01',
    '01', '01', '01', NULL,
    'STORAGE', 500, 'ACTIVE', 0,
    '2026-04-18T00:00:00Z', 'seed-dev',
    '2026-04-18T00:00:00Z', 'seed-dev'
),
(
    '01910000-0000-7000-8000-000000001002',
    '01910000-0000-7000-8000-000000000001',
    '01910000-0000-7000-8000-000000000102',
    'WH01-C-01-01-01',
    '01', '01', '01', NULL,
    'STORAGE', 300, 'ACTIVE', 0,
    '2026-04-18T00:00:00Z', 'seed-dev',
    '2026-04-18T00:00:00Z', 'seed-dev'
),
(
    '01910000-0000-7000-8000-000000001003',
    '01910000-0000-7000-8000-000000000001',
    '01910000-0000-7000-8000-000000000103',
    'WH01-R-01-01-01',
    '01', '01', '01', NULL,
    'QUARANTINE', 100, 'ACTIVE', 0,
    '2026-04-18T00:00:00Z', 'seed-dev',
    '2026-04-18T00:00:00Z', 'seed-dev'
)
ON CONFLICT (location_code) DO NOTHING;
