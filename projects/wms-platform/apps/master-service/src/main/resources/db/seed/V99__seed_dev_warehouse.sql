-- Dev/standalone seed: single ACTIVE warehouse (WH01) for manual walkthroughs.
-- Activated via spring.flyway.locations in application-dev.yml.
-- Zone/Location/SKU/Partner/Lot seeds ship with their respective tasks.
--
-- Idempotent: ON CONFLICT DO NOTHING so re-runs after a full migrate are safe.

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
