-- Dev/standalone seed: three zones under WH01 so manual walk-throughs exercise
-- the nested routes. Activated via spring.flyway.locations in application-dev.yml
-- and by infra/demo/wms-devseed.override.yml.
--
-- 🔴 REPEATABLE (R__), NOT VERSIONED — TASK-MONO-531; full rationale in
-- R__01_seed_dev_warehouse.sql. This was V100__seed_dev_zones.sql.
--
-- 🔴 Depends on R__01 having seeded the WH01 warehouse row. Flyway runs
-- repeatables in DESCRIPTION order, so `01` < `02` is the only thing making that
-- true — the prefix is load-bearing, not decoration.
--
-- ON CONFLICT DO NOTHING keeps this idempotent; a repeatable re-runs on every
-- checksum change, over live data.

INSERT INTO zones (
    id, warehouse_id, zone_code, name, zone_type, status, version,
    created_at, created_by, updated_at, updated_by
) VALUES
(
    '01910000-0000-7000-8000-000000000101',
    '01910000-0000-7000-8000-000000000001',
    'Z-A',
    'Ambient A',
    'AMBIENT',
    'ACTIVE',
    0,
    '2026-04-18T00:00:00Z',
    'seed-dev',
    '2026-04-18T00:00:00Z',
    'seed-dev'
),
(
    '01910000-0000-7000-8000-000000000102',
    '01910000-0000-7000-8000-000000000001',
    'Z-C',
    'Chilled C',
    'CHILLED',
    'ACTIVE',
    0,
    '2026-04-18T00:00:00Z',
    'seed-dev',
    '2026-04-18T00:00:00Z',
    'seed-dev'
),
(
    '01910000-0000-7000-8000-000000000103',
    '01910000-0000-7000-8000-000000000001',
    'Z-R',
    'Returns R',
    'RETURNS',
    'ACTIVE',
    0,
    '2026-04-18T00:00:00Z',
    'seed-dev',
    '2026-04-18T00:00:00Z',
    'seed-dev'
)
ON CONFLICT (warehouse_id, zone_code) DO NOTHING;
