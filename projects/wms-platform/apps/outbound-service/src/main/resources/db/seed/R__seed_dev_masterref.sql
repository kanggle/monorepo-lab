-- Dev/standalone seed: master-service read-model snapshots that mirror the
-- baseline rows seeded by master-service / inventory-service / inbound-service.
-- Activated via spring.flyway.locations in application-dev.yml and by
-- infra/demo/wms-devseed.override.yml.
--
-- 🔴 REPEATABLE (R__), NOT VERSIONED — TASK-MONO-531. Do not renumber this back.
-- This was V99__seed_dev_masterref.sql, and outbound production is only at V18,
-- so once V99 applied it became the highest APPLIED version of outbound_db and
-- every later production migration would resolve BELOW it — an out-of-order
-- migration, which Flyway rejects by default. wms admin-service crash-looped on
-- exactly that on 2026-08-14 (TASK-BE-584 AC-0); iam auth-service on 2026-08-11
-- (TASK-MONO-524). A repeatable carries no version, so it cannot be out of order.
-- 🔵 outbound was the closest of the four to the cliff: its timeline had already
-- reached V18 and TASK-BE-586 is about to add more.
-- Every statement here must stay idempotent — a repeatable re-runs on every
-- checksum change, over live data. Renaming an already-applied repeatable makes
-- Flyway reject the database, so this name is final.
--
-- TASK-BE-580 — this file existed but had never executed, anywhere, ever:
--
--   * it lived in `db/dev/` while master / inbound / inventory all use
--     `db/seed/`, and every `spring.flyway.locations` in the repo names
--     `classpath:db/seed`. A repo-wide search found **zero** references to
--     `db/dev` — no profile, no override, no test.
--   * the header above used to claim activation "via application-dev.yml /
--     application-standalone.yml". outbound-service had **neither file**.
--
-- Consequence: `outbound_db`'s master read-model stayed at 0 rows forever, so
-- `POST /api/v1/outbound/orders` was structurally impossible in dev and demo
-- (PARTNER_INVALID_TYPE / WAREHOUSE_NOT_FOUND). master-service's V103 header
-- had been claiming alignment with "the inbound + outbound
-- V99__seed_dev_masterref.sql baseline (SUP-001 / CUST-001)" the whole time —
-- a reference to a file that was on disk but unreachable.
--
-- Fixed by moving the file to `db/seed/` (the convention its three siblings
-- already follow) and adding the missing `application-dev.yml`. Contents are
-- unchanged: outbound resolves a CUSTOMER partner, so CUST-001 is what it
-- needs — SUP-001 belongs to inbound's ASN check, not here.

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

INSERT INTO location_snapshot (
    id, location_code, warehouse_id, zone_id, location_type, status,
    cached_at, master_version
) VALUES (
    '01910000-0000-7000-8000-000000001002',
    'WH01-A-01-01-02',
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

INSERT INTO sku_snapshot (
    id, sku_code, tracking_type, status, cached_at, master_version
) VALUES (
    '01910000-0000-7000-8000-000000000404',
    'SKU-APPLE-002',
    'NONE',
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
    '01910000-0000-7000-8000-000000000901',
    'CUST-001',
    'CUSTOMER',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
)
ON CONFLICT (id) DO NOTHING;
