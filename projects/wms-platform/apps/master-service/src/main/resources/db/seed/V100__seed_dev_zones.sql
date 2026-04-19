-- Dev/standalone seed: three zones under WH01 so manual walk-throughs exercise
-- the nested routes. Activated via spring.flyway.locations in application-dev.yml.
-- Depends on V99 having seeded the WH01 warehouse row; ON CONFLICT DO NOTHING
-- keeps this idempotent across re-runs.

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
