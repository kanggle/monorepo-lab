-- Dev/standalone seed: three SKUs covering both tracking types so manual
-- walk-throughs exercise the SKU routes and the LOT path under TASK-BE-006.
-- Activated via spring.flyway.locations in application-dev.yml.
-- ON CONFLICT DO NOTHING keeps this idempotent across re-runs.
--
-- Mix:
--   - SKU-BOX-001 / BOX / NONE — bulk packaged item, no lot tracking
--   - SKU-EA-001 / EA / NONE — discrete item, no lot tracking
--   - SKU-APPLE-001 / EA / LOT / shelfLifeDays=30 — exercises lot-tracking

INSERT INTO skus (
    id, sku_code, name, description, barcode, base_uom, tracking_type,
    weight_grams, volume_ml, hazard_class, shelf_life_days, status, version,
    created_at, created_by, updated_at, updated_by
) VALUES
(
    '01910000-0000-7000-8000-000000000401',
    'SKU-BOX-001',
    'Cardboard Box 20kg',
    'Generic 20kg shipping box',
    '8801111111111',
    'BOX',
    'NONE',
    NULL,
    NULL,
    NULL,
    NULL,
    'ACTIVE',
    0,
    '2026-04-18T00:00:00Z',
    'seed-dev',
    '2026-04-18T00:00:00Z',
    'seed-dev'
),
(
    '01910000-0000-7000-8000-000000000402',
    'SKU-EA-001',
    'Generic Each Unit',
    'Baseline NONE-tracking SKU for load tests',
    NULL,
    'EA',
    'NONE',
    250,
    NULL,
    NULL,
    NULL,
    'ACTIVE',
    0,
    '2026-04-18T00:00:00Z',
    'seed-dev',
    '2026-04-18T00:00:00Z',
    'seed-dev'
),
(
    '01910000-0000-7000-8000-000000000403',
    'SKU-APPLE-001',
    'Gala Apple 1kg',
    'Fresh Gala apples, 1kg pack, exercises lot tracking',
    '8801234567890',
    'EA',
    'LOT',
    1000,
    NULL,
    NULL,
    30,
    'ACTIVE',
    0,
    '2026-04-18T00:00:00Z',
    'seed-dev',
    '2026-04-18T00:00:00Z',
    'seed-dev'
)
ON CONFLICT (sku_code) DO NOTHING;
