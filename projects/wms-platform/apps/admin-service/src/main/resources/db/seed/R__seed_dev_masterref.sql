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
-- 🔵 HISTORICAL NOTE — TASK-MONO-675 AC-2 flagged a drift here between the
-- sibling seeds themselves; TASK-BE-588 fixed it (2026-09-16):
--   · outbound-service's masterref seed used to carry a SECOND location under
--     id ...1002 with code 'WH01-A-01-01-02' / zone Z-A — this did not match
--     master-service's own ...1002 row (code 'WH01-C-01-01-01', zone Z-C,
--     reproduced above). Now fixed to match.
--   · outbound-service's masterref seed also used to carry a sku_snapshot row
--     ...404 'SKU-APPLE-002' that does not exist anywhere in master-service's
--     R__04 seed. Now removed.
-- Both were copy-paste drift in outbound's own file, not master-service
-- baseline data — admin's ref tables were never meant to reproduce them
-- (reproducing them here would have injected rows that master-service itself
-- never emits an event for). This file continues to mirror master-service's
-- actual seed only.
-- See TASK-BE-588 (projects/wms-platform/tasks/) for the fix.
-- =============================================================================

-- =============================================================================
-- 7. TASK-MONO-768 (2026-10-07) — WH-MAIN + its zone/location + the 86 ecommerce
--    SKUs + the ECOMMERCE-STORE partner. Added as new statements; every row
--    above is untouched.
-- =============================================================================
-- Why admin needs them: admin's projections DENORMALISE codes at event time from
-- these ref tables, and the console renders those codes —
--   · InventoryProjectionService.applySnapshot copies warehouse_code /
--     location_code / sku_code into admin_inventory_snapshot
--     (console WmsInventoryDataTable · WmsInventoryDetailPanel render them);
--   · InboundProjectionService.resolveWarehouseCode stamps the ASN summary's
--     warehouse_code (console WmsAsnDataTable);
--   · MasterRefController lists every *_ref table on the console master screen
--     (wms-master-helpers: warehouses / zones / locations / skus / partners).
-- infra/demo/seed/seed-wms.sh now receives 86 ecommerce SKUs at WH-MAIN through
-- the real inbound API; without these rows every one of those inventory rows
-- and ASN summaries would be projected with NULL codes and show raw UUIDs.
-- 🔴 The projection copies the code ONCE, when the event arrives — a ref row
-- added after the event does not repair the snapshot. These rows must exist
-- before seed-wms.sh runs, which they do (Flyway runs at admin boot).
--
-- ECOMMERCE-STORE (TASK-MONO-765, master R__05 `…0902`) was never mirrored
-- here; the console partner list showed master's three seed partners but not
-- the one every ecommerce outbound order names. Added for the same reason.
--
-- UUIDs equal master-service R__01/R__02/R__03/R__05 and the inbound /
-- inventory / outbound mirrors — pinned by inventory-service
-- `EcommerceSeedParityTest`, which reads this file too.
-- SKU `name` is NOT NULL here and wms has no product name for these variants
-- (the code IS the ecommerce variant id), so the name is a neutral label.
-- last_event_at stays backdated (see the header) so real master.* events win.

INSERT INTO admin_warehouse_ref (
    id, warehouse_code, name, timezone, status, last_event_at, version
) VALUES (
    '01910000-0000-7000-8000-000000000002',
    'WH-MAIN',
    'Ecommerce Fulfillment Warehouse',
    'Asia/Seoul',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO admin_zone_ref (
    id, warehouse_id, zone_code, name, zone_type, status, last_event_at, version
) VALUES (
    '01910000-0000-7000-8000-000000000201',
    '01910000-0000-7000-8000-000000000002',
    'Z-A',
    'Ecommerce Ambient A',
    'AMBIENT',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO admin_location_ref (
    id, location_code, warehouse_id, zone_id, location_type, status,
    last_event_at, version
) VALUES (
    '01910000-0000-7000-8000-000000001101',
    'WH-MAIN-A-01-01-01',
    '01910000-0000-7000-8000-000000000002',
    '01910000-0000-7000-8000-000000000201',
    'STORAGE',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO admin_sku_ref (
    id, sku_code, name, base_uom, tracking_type, status, last_event_at, version
) VALUES
    ('01910000-0000-7000-8000-000000002001', 'c0000000-0000-0000-0000-000000000001', 'Ecommerce variant #001', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002002', 'c0000000-0000-0000-0000-000000000002', 'Ecommerce variant #002', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002003', 'c0000000-0000-0000-0000-000000000003', 'Ecommerce variant #003', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002004', 'c0000000-0000-0000-0000-000000000004', 'Ecommerce variant #004', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002005', 'c0000000-0000-0000-0000-000000000005', 'Ecommerce variant #005', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002006', 'c0000000-0000-0000-0000-000000000006', 'Ecommerce variant #006', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002007', 'c0000000-0000-0000-0000-000000000007', 'Ecommerce variant #007', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002008', 'c0000000-0000-0000-0000-000000000008', 'Ecommerce variant #008', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002009', 'c0000000-0000-0000-0000-000000000009', 'Ecommerce variant #009', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002010', 'c0000000-0000-0000-0000-000000000010', 'Ecommerce variant #010', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002011', 'c0000000-0000-0000-0000-000000000011', 'Ecommerce variant #011', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002012', 'c0000000-0000-0000-0000-000000000012', 'Ecommerce variant #012', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002013', 'c0000000-0000-0000-0000-000000000013', 'Ecommerce variant #013', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002014', 'c0000000-0000-0000-0000-000000000014', 'Ecommerce variant #014', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002015', 'c0000000-0000-0000-0000-000000000015', 'Ecommerce variant #015', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002016', 'c0000000-0000-0000-0000-000000000016', 'Ecommerce variant #016', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002017', 'c0000000-0000-0000-0000-000000000017', 'Ecommerce variant #017', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002018', 'c0000000-0000-0000-0000-000000000018', 'Ecommerce variant #018', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002019', 'c0000000-0000-0000-0000-000000000019', 'Ecommerce variant #019', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002020', 'c0000000-0000-0000-0000-000000000020', 'Ecommerce variant #020', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002021', 'c0000000-0000-0000-0000-000000000021', 'Ecommerce variant #021', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002022', 'c0000000-0000-0000-0000-000000000022', 'Ecommerce variant #022', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002023', 'c0000000-0000-0000-0000-000000000023', 'Ecommerce variant #023', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002024', 'c0000000-0000-0000-0000-000000000024', 'Ecommerce variant #024', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002025', 'c0000000-0000-0000-0000-000000000025', 'Ecommerce variant #025', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002026', 'c0000000-0000-0000-0000-000000000026', 'Ecommerce variant #026', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002027', 'c0000000-0000-0000-0000-000000000027', 'Ecommerce variant #027', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002028', 'c0000000-0000-0000-0000-000000000028', 'Ecommerce variant #028', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002029', 'c0000000-0000-0000-0000-000000000029', 'Ecommerce variant #029', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002030', 'c0000000-0000-0000-0000-000000000030', 'Ecommerce variant #030', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002031', 'c0000000-0000-0000-0000-000000000031', 'Ecommerce variant #031', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002032', 'c0000000-0000-0000-0000-000000000032', 'Ecommerce variant #032', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002033', 'c0000000-0000-0000-0000-000000000033', 'Ecommerce variant #033', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002034', 'c0000000-0000-0000-0000-000000000034', 'Ecommerce variant #034', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002035', 'c0000000-0000-0000-0000-000000000035', 'Ecommerce variant #035', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002036', 'c0000000-0000-0000-0000-000000000036', 'Ecommerce variant #036', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002037', 'c0000000-0000-0000-0000-000000000037', 'Ecommerce variant #037', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002038', 'c0000000-0000-0000-0000-000000000038', 'Ecommerce variant #038', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002039', 'c0000000-0000-0000-0000-000000000039', 'Ecommerce variant #039', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002040', 'c0000000-0000-0000-0000-000000000040', 'Ecommerce variant #040', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002041', 'c0000000-0000-0000-0000-000000000041', 'Ecommerce variant #041', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002042', 'c0000000-0000-0000-0000-000000000042', 'Ecommerce variant #042', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002043', 'c0000000-0000-0000-0000-000000000043', 'Ecommerce variant #043', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002044', 'c0000000-0000-0000-0000-000000000044', 'Ecommerce variant #044', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002045', 'c0000000-0000-0000-0000-000000000045', 'Ecommerce variant #045', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002046', 'c0000000-0000-0000-0000-000000000046', 'Ecommerce variant #046', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002047', 'c0000000-0000-0000-0000-000000000047', 'Ecommerce variant #047', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002048', 'c0000000-0000-0000-0000-000000000048', 'Ecommerce variant #048', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002049', 'c0000000-0000-0000-0000-000000000049', 'Ecommerce variant #049', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002050', 'c0000000-0000-0000-0000-000000000050', 'Ecommerce variant #050', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002051', 'c0000000-0000-0000-0000-000000000051', 'Ecommerce variant #051', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002052', 'c0000000-0000-0000-0000-000000000052', 'Ecommerce variant #052', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002053', 'c0000000-0000-0000-0000-000000000053', 'Ecommerce variant #053', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002054', 'c0000000-0000-0000-0000-000000000054', 'Ecommerce variant #054', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002055', 'c0000000-0000-0000-0000-000000000055', 'Ecommerce variant #055', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002056', 'c0000000-0000-0000-0000-000000000056', 'Ecommerce variant #056', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002057', 'c0000000-0000-0000-0000-000000000057', 'Ecommerce variant #057', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002058', 'c0000000-0000-0000-0000-000000000058', 'Ecommerce variant #058', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002059', 'c0000000-0000-0000-0000-000000000059', 'Ecommerce variant #059', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002060', 'c0000000-0000-0000-0000-000000000060', 'Ecommerce variant #060', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002061', 'c0000000-0000-0000-0000-000000000061', 'Ecommerce variant #061', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002062', 'c0000000-0000-0000-0000-000000000062', 'Ecommerce variant #062', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002063', 'c0000000-0000-0000-0000-000000000063', 'Ecommerce variant #063', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002064', 'c0000000-0000-0000-0000-000000000064', 'Ecommerce variant #064', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002065', 'c0000000-0000-0000-0000-000000000065', 'Ecommerce variant #065', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002066', 'c0000000-0000-0000-0000-000000000066', 'Ecommerce variant #066', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002067', 'c0000000-0000-0000-0000-000000000067', 'Ecommerce variant #067', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002068', 'c0000000-0000-0000-0000-000000000068', 'Ecommerce variant #068', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002069', 'c0000000-0000-0000-0000-000000000069', 'Ecommerce variant #069', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002070', 'c0000000-0000-0000-0000-000000000070', 'Ecommerce variant #070', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002071', 'c0000000-0000-0000-0000-000000000071', 'Ecommerce variant #071', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002072', 'c0000000-0000-0000-0000-000000000072', 'Ecommerce variant #072', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002073', 'c0000000-0000-0000-0000-000000000073', 'Ecommerce variant #073', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002074', 'c0000000-0000-0000-0000-000000000074', 'Ecommerce variant #074', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002075', 'c0000000-0000-0000-0000-000000000075', 'Ecommerce variant #075', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002076', 'c0000000-0000-0000-0000-000000000076', 'Ecommerce variant #076', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002077', 'c0000000-0000-0000-0000-000000000077', 'Ecommerce variant #077', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002078', 'c0000000-0000-0000-0000-000000000078', 'Ecommerce variant #078', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002079', 'c0000000-0000-0000-0000-000000000079', 'Ecommerce variant #079', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002080', 'c0000000-0000-0000-0000-000000000080', 'Ecommerce variant #080', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002081', 'c0000000-0000-0000-0000-000000000081', 'Ecommerce variant #081', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002082', 'c0000000-0000-0000-0000-000000000082', 'Ecommerce variant #082', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002083', 'c0000000-0000-0000-0000-000000000083', 'Ecommerce variant #083', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002084', 'c0000000-0000-0000-0000-000000000084', 'Ecommerce variant #084', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002085', 'c0000000-0000-0000-0000-000000000085', 'Ecommerce variant #085', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002086', 'c0000000-0000-0000-0000-000000000086', 'Ecommerce variant #086', 'EA', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO admin_partner_ref (
    id, partner_code, name, partner_type, status, last_event_at, version
) VALUES (
    '01910000-0000-7000-8000-000000000902',
    'ECOMMERCE-STORE',
    'Ecommerce Storefront (cross-project customer)',
    'CUSTOMER',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
)
ON CONFLICT (id) DO NOTHING;
