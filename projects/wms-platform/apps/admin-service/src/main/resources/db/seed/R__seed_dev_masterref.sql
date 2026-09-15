-- Dev/demo seed: admin-service master read-model rows that mirror the baseline
-- rows seeded by master-service's R__01–R__05 (+ the one Lot inventory-service /
-- inbound-service / outbound-service already carry). Activated via
-- spring.flyway.locations in application-dev.yml and by
-- infra/demo/wms-devseed.override.yml (both already open `classpath:db/seed`
-- for admin-service — TASK-BE-587 moved admin's seed location there before this
-- file existed, so opening it is not part of this change).
--
-- 🔴 REPEATABLE (R__), NOT VERSIONED — same rule as every other seed in this
-- directory (TASK-MONO-531) and as the four sibling `R__seed_dev_masterref.sql`
-- files in inbound-service / inventory-service / outbound-service. A repeatable
-- carries no version, so it can never resolve out of order against admin's
-- production `db/migration` timeline (currently V4). Every statement here MUST
-- stay idempotent (`ON CONFLICT ... DO NOTHING`) — a repeatable re-runs on every
-- checksum change, over live data.
--
-- =============================================================================
-- WHY THIS FILE EXISTS — TASK-MONO-675
-- =============================================================================
-- admin-service's `admin_*_ref` tables (V2__init_readmodel.sql) are a Kafka
-- projection: `MasterProjectionConsumer` subscribes to
-- `wms.master.{warehouse,zone,location,sku,partner,lot}.v1` and
-- `MasterProjectionService` upserts each event into the matching table. That
-- consumer exists and is correctly wired (TASK-MONO-675 AC-1 measured this —
-- the earlier "admin has no consumer" hypothesis was REJECTED).
--
-- 🔴 The reason the tables were empty on the demo is upstream of admin
-- entirely: master-service's own dev seed (`R__01..R__05_seed_dev_*.sql`)
-- INSERTs warehouses/zones/locations/skus/partners **directly into its own
-- tables**. It does not go through `OutboxDomainEventAdapter` /
-- `MasterOutboxPublisher` — there is no outbox row, so no `master.*` event is
-- ever published for a seeded row. admin's consumer has nothing to consume.
-- (The write API can't backfill this either: `MASTER_WRITE` is not reachable
-- by any demo credential — TASK-MONO-514 AC-8, `seed-wms.sh:17-23`.)
--
-- The three sibling wms services that also consume `master.*` (inbound,
-- inventory, outbound) hit the exact same gap and each carry their own
-- `db/seed/R__seed_dev_masterref.sql` that pre-loads a snapshot of the same
-- master rows under the same fixed UUIDs, with a header explaining they "boot
-- with an empty master read-model and wait for `master.*` consumer events ...
-- we pre-load". admin-service was the one wms master-ref consumer without that
-- file. This is that file for admin.
--
-- 🔴 UUIDs MUST STAY IN SYNC WITH master-service's seeds (and with the sibling
-- masterref seeds below, where they overlap). If master-service's
-- `R__01..R__05` ever rotates an id or a code, this file drifts silently until
-- someone notices the demo showing two different names for the same UUID.
-- See TASK-MONO-675 AC-2 for the drift-risk discussion (a fourth copy of the
-- same UUIDs, and the sibling copies already disagree with each other on two
-- rows that are NOT reproduced here — see note at the bottom).
--
-- 🔴 `last_event_at` IS DELIBERATELY OLD (matches master-service's seed
-- `created_at`, 2026-04-18), not `now()`. `MasterProjectionService` only
-- applies an incoming event when the existing row's `last_event_at` is NOT
-- after the event's `occurredAt` (last-write-wins guard). A seeded row with a
-- recent timestamp would make every subsequent real `master.*` event look
-- "late" and get silently dropped as `IGNORED_DUPLICATE_LATE`. Backdating the
-- seed timestamp keeps the seed row overridable by the very consumer this
-- table exists for.
--
-- 🔵 Only the 6 ref types admin's consumer actually projects are seeded here
-- (warehouse / zone / location / sku / lot / partner) — admin's other 9
-- read-model tables (asn/order/shipment summaries, inventory snapshot,
-- adjustment audit, alert log, throughput) are fed by inbound/outbound/
-- inventory events, not master events, and are out of scope for this file.

-- ===========================================================================
-- 1. admin_warehouse_ref — mirrors master-service R__01_seed_dev_warehouse.sql
-- ===========================================================================
INSERT INTO admin_warehouse_ref (
    id, warehouse_code, name, timezone, status, last_event_at, version
) VALUES (
    '01910000-0000-7000-8000-000000000001',
    'WH01',
    'Seoul Main Warehouse',
    'Asia/Seoul',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
)
ON CONFLICT (id) DO NOTHING;

-- ===========================================================================
-- 2. admin_zone_ref — mirrors master-service R__02_seed_dev_zones.sql
-- ===========================================================================
INSERT INTO admin_zone_ref (
    id, warehouse_id, zone_code, name, zone_type, status, last_event_at, version
) VALUES
(
    '01910000-0000-7000-8000-000000000101',
    '01910000-0000-7000-8000-000000000001',
    'Z-A',
    'Ambient A',
    'AMBIENT',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
),
(
    '01910000-0000-7000-8000-000000000102',
    '01910000-0000-7000-8000-000000000001',
    'Z-C',
    'Chilled C',
    'CHILLED',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
),
(
    '01910000-0000-7000-8000-000000000103',
    '01910000-0000-7000-8000-000000000001',
    'Z-R',
    'Returns R',
    'RETURNS',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
)
ON CONFLICT (id) DO NOTHING;

-- ===========================================================================
-- 3. admin_location_ref — mirrors master-service R__03_seed_dev_locations.sql
-- ===========================================================================
INSERT INTO admin_location_ref (
    id, location_code, warehouse_id, zone_id, location_type, status,
    last_event_at, version
) VALUES
(
    '01910000-0000-7000-8000-000000001001',
    'WH01-A-01-01-01',
    '01910000-0000-7000-8000-000000000001',
    '01910000-0000-7000-8000-000000000101',
    'STORAGE',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
),
(
    '01910000-0000-7000-8000-000000001002',
    'WH01-C-01-01-01',
    '01910000-0000-7000-8000-000000000001',
    '01910000-0000-7000-8000-000000000102',
    'STORAGE',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
),
(
    '01910000-0000-7000-8000-000000001003',
    'WH01-R-01-01-01',
    '01910000-0000-7000-8000-000000000001',
    '01910000-0000-7000-8000-000000000103',
    'QUARANTINE',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
)
ON CONFLICT (id) DO NOTHING;

-- ===========================================================================
-- 4. admin_sku_ref — mirrors master-service R__04_seed_dev_skus.sql
-- ===========================================================================
INSERT INTO admin_sku_ref (
    id, sku_code, name, base_uom, tracking_type, status, last_event_at, version
) VALUES
(
    '01910000-0000-7000-8000-000000000401',
    'SKU-BOX-001',
    'Cardboard Box 20kg',
    'BOX',
    'NONE',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
),
(
    '01910000-0000-7000-8000-000000000402',
    'SKU-EA-001',
    'Generic Each Unit',
    'EA',
    'NONE',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
),
(
    '01910000-0000-7000-8000-000000000403',
    'SKU-APPLE-001',
    'Gala Apple 1kg',
    'EA',
    'LOT',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
)
ON CONFLICT (id) DO NOTHING;

-- ===========================================================================
-- 5. admin_lot_ref — mirrors inventory-service / inbound-service / outbound-
--    service R__seed_dev_masterref.sql (master-service itself seeds no lots —
--    lots are not part of its R__01..R__05 baseline).
-- ===========================================================================
INSERT INTO admin_lot_ref (
    id, sku_id, lot_no, expiry_date, status, last_event_at, version
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

-- ===========================================================================
-- 6. admin_partner_ref — mirrors master-service R__05_seed_dev_partners.sql
-- ===========================================================================
INSERT INTO admin_partner_ref (
    id, partner_code, name, partner_type, status, last_event_at, version
) VALUES
(
    '01910000-0000-7000-8000-000000000801',
    'SUP-001',
    'ACME Supplier Co.',
    'SUPPLIER',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
),
(
    '01910000-0000-7000-8000-000000000901',
    'CUST-001',
    'Best Buyer Inc.',
    'CUSTOMER',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
),
(
    '01910000-0000-7000-8000-000000000951',
    'BOTH-001',
    'Omni Trading Partner',
    'BOTH',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
)
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- 🔴 NOT REPRODUCED HERE — a drift already found between the sibling seeds
-- themselves (TASK-MONO-675 AC-2 drift-risk note, not fixed by this ticket):
--   · outbound-service's masterref seed carries a SECOND location under id
--     ...1002 with code 'WH01-A-01-01-02' / zone Z-A — this does not match
--     master-service's own ...1002 row (code 'WH01-C-01-01-01', zone Z-C,
--     reproduced above).
--   · outbound-service's masterref seed also carries a sku_snapshot row
--     ...404 'SKU-APPLE-002' that does not exist anywhere in master-service's
--     R__04 seed.
-- Both look like copy-paste drift in outbound's own file, not master-service
-- baseline data. Reproducing them here would inject rows into admin's ref
-- tables that master-service itself will never emit an event for — this file
-- mirrors master-service's actual seed instead.
-- Tracked in projects/wms-platform/tasks/ready/TASK-BE-588-outbound-masterref-seed-disagrees-with-master-seed.md
-- =============================================================================
