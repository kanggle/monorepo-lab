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
--
-- TASK-BE-588 — two rows below used to disagree with master-service's own
-- R__03_seed_dev_locations.sql / R__04_seed_dev_skus.sql (copy-paste drift in
-- this file, flagged by TASK-MONO-675 AC-2 while building admin-service's
-- mirror; admin's file deliberately did NOT reproduce either row — see the
-- note at its bottom). Fixed here to match master exactly:
--   * location ...1002 now carries master's actual code/zone
--     (WH01-C-01-01-01 / zone ...0102, was WH01-A-01-01-02 / zone ...0101).
--     zone_id ...0102 (Z-C) has no corresponding zone_snapshot row seeded in
--     this file (only Z-A ...0101 is seeded here) — pre-existing, harmless:
--     location_snapshot.zone_id carries no FK and no outbound-service code
--     path joins location_snapshot to zone_snapshot (grepped 2026-09-16).
--   * the sku_snapshot row for ...0404 'SKU-APPLE-002' is removed — that SKU
--     does not exist anywhere in master-service's baseline. TASK-BE-588 AC-1
--     found zero live references to either ...1002's old code or ...0404
--     anywhere in outbound-service (its own db/seed/* has no other file, no
--     order-line seed exists, and its src/test has zero matches against a
--     working positive control). The two ERP webhook contract *example*
--     payloads (specs/contracts/webhooks/erp-{order,asn}-webhook.md) use
--     SKU-APPLE-002 as illustrative second-line JSON, but that is advisory
--     documentation, not executable seed/test data, and predates this fix —
--     inbound-service's own webhook contract shows the same code even though
--     inbound's seed never carried SKU-APPLE-002 either.
--
-- 🔴 Existing (already-seeded) local volumes: this is a REPEATABLE migration,
-- so a checksum change makes Flyway re-run it, but every statement below uses
-- ON CONFLICT (id) DO NOTHING — a row that was already inserted with the old,
-- wrong values is NOT corrected by the re-run. A volume that seeded before
-- this fix must be dropped (`docker compose down -v` / fresh volume) to pick
-- up the corrected row; only a Kafka-projected real master.* event can correct
-- it in place (the runtime consumer's ON CONFLICT (id) DO UPDATE ... WHERE
-- master_version < EXCLUDED.master_version in MasterReadModelRepositoryImpl
-- always wins over this seed's master_version=0, so a real event will still
-- self-heal an existing wrong row without a volume drop).

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
    'WH01-C-01-01-01',
    '01910000-0000-7000-8000-000000000001',
    '01910000-0000-7000-8000-000000000102',
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
    '01910000-0000-7000-8000-000000000901',
    'CUST-001',
    'CUSTOMER',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
)
ON CONFLICT (id) DO NOTHING;
