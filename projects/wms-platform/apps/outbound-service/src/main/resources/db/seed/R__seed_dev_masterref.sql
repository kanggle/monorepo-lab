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
--
-- TASK-MONO-765 (2026-10-06) — three additions below fix
-- `ecommerce.fulfillment.requested.v1.DLT` (every cross-project fulfillment
-- request was dead-lettered: `IllegalArgumentException: Partner not found in
-- read model: code=ECOMMERCE-STORE`, then the same failure would have
-- recurred one field at a time for warehouse and every ecommerce SKU code —
-- see `FulfillmentRequestedConsumer.toCommand`,
-- apps/outbound-service/src/main/java/com/wms/outbound/adapter/in/messaging/
-- consumer/FulfillmentRequestedConsumer.java:143-201):
--
--   1. `warehouse_snapshot` WH-MAIN — ecommerce shipping-service's
--      `fulfillment.default-warehouse-code` (default `WH-MAIN`; see
--      ecommerce-microservices-platform apps/shipping-service
--      src/main/resources/application.yml:73). Mirrors the new WH-MAIN row
--      master-service's own R__01_seed_dev_warehouse.sql now seeds — WH01
--      (this file's existing warehouse_snapshot row, kept unchanged below)
--      is a *different* warehouse and is not renamed (out of scope; other
--      inbound/outbound fixtures key off its UUID).
--   2. `partner_snapshot` ECOMMERCE-STORE — the fixed customer-partner code
--      `FulfillmentAcl.CUSTOMER_PARTNER_CODE` stamps on every event
--      (ecommerce-microservices-platform apps/shipping-service
--      src/main/java/com/example/shipping/infrastructure/event/
--      FulfillmentAcl.java:32 — a Java constant, not configurable).
--      `partner_type=CUSTOMER` + `status=ACTIVE` is required for
--      `PartnerSnapshot.canReceive()` to return true (PartnerSnapshot.java:35).
--      Mirrors master-service's new R__05_seed_dev_partners.sql row.
--   3. 86 `sku_snapshot` rows, one per ecommerce product_variant currently
--      seeded by product-service's V8__seed_sample_data.sql (ids
--      c0000000-…-000000000001..028), V19__seed_more_sample_data.sql (ids
--      …029..065), and V21__seed_artist_goods.sql (ids …066..086) —
--      ecommerce-microservices-platform apps/product-service
--      src/main/resources/db/migration/{V8,V19,V21}__seed*.sql. 🔴 V21 is not
--      yet on the baked demo AMI as of this ticket (its own header: "데모
--      서버는 이 마이그레이션을 재굽기 전까지 갖지 않는다") but will be live by
--      the time this fix's own rebake lands, and omitting it here would
--      reproduce the exact SKU-drift failure mode this ticket's Edge Cases
--      section warns about — so all 86, not just the 65 live today, are
--      seeded.
--
--      Why the literal variant UUID *is* the sku_code: shipping-service's
--      `fulfillment.require-sku-mapping=false` (application.yml:74) makes
--      `FulfillmentAcl.resolveSkuCode` an identity passthrough
--      (FulfillmentAcl.java:81-92) of whatever order-service put in the
--      order line's `sku` field — and order-service's
--      `OrderConfirmationService.toLine` sets that field to the *variantId*
--      (falling back to productId only when a line has no variant;
--      every seeded product here has variants, so variantId always wins —
--      apps/order-service/src/main/java/com/example/order/application/
--      service/OrderConfirmationService.java:58-64). So the "SKU code" wms
--      receives for an ecommerce order is literally product-service's
--      `product_variants.id` as a lowercase UUID string, not a human-coded
--      SKU like `SKU-APPLE-001`.
--
--      🔴 This is why these 86 rows are NOT mirrored into master-service's
--      own `skus` table (unlike warehouse/partner above): master-service's
--      `skus.sku_code` carries `CHECK (sku_code = UPPER(sku_code))`
--      (apps/master-service/src/main/resources/db/migration/V5__init_sku.sql:35),
--      but the value this consumer actually looks up is the lowercase string
--      java.util.UUID.toString() / Jackson always produce. Uppercasing it to
--      satisfy master-service's constraint would make that row not match
--      what any real event carries, and this table
--      (outbound_db.sku_snapshot) carries no such constraint — it is the
--      table `FulfillmentRequestedConsumer.masterReadModel.findSkuByCode`
--      actually queries (MasterReadModelRepositoryImpl.java:102-104), so it
--      is the one that must hold the exact-case value. Tracking type is
--      NONE for all 86 — none of these ecommerce SKUs are lot-tracked, and
--      `FulfillmentAcl.toFulfillmentRequested` always sends a null `lotNo`
--      for this path (FulfillmentAcl.java:76), so no matching
--      `lot_snapshot` rows are needed.
--
--      🔴 Known residual gap (not fixed by this ticket — out of scope per
--      TASK-MONO-765 Scope, which is limited to `FulfillmentRequestedConsumer`):
--      inventory-service's own `apps/inventory-service/src/main/resources/
--      db/seed/R__seed_dev_masterref.sql` mirror does not (yet) carry these
--      86 SKU codes or a WH-MAIN warehouse row. The outbound order created by
--      this fix will therefore stop being dead-lettered (AC-3), but the
--      downstream reservation saga's effect on wms inventory / scm
--      visibility screens (AC-4, "762 연장 측정") may still be incomplete
--      for SKUs outside the pre-existing SKU-APPLE-001 baseline until a
--      follow-up extends inventory-service's mirror the same way.

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

-- TASK-MONO-765: WH-MAIN warehouse (mirrors master-service R__01's new row)
INSERT INTO warehouse_snapshot (
    id, warehouse_code, status, cached_at, master_version
) VALUES (
    '01910000-0000-7000-8000-000000000002',
    'WH-MAIN',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
)
ON CONFLICT (id) DO NOTHING;

-- TASK-MONO-765: ECOMMERCE-STORE partner (mirrors master-service R__05's new row)
INSERT INTO partner_snapshot (
    id, partner_code, partner_type, status, cached_at, master_version
) VALUES (
    '01910000-0000-7000-8000-000000000902',
    'ECOMMERCE-STORE',
    'CUSTOMER',
    'ACTIVE',
    '2026-04-18T00:00:00Z',
    0
)
ON CONFLICT (id) DO NOTHING;

-- TASK-MONO-765: 86 ecommerce product_variant ids as sku_code (identity passthrough,
-- require-sku-mapping=false). tracking_type=NONE — none of these are lot-tracked and
-- FulfillmentAcl always sends a null lotNo for this path, so no lot_snapshot rows needed.
INSERT INTO sku_snapshot (
    id, sku_code, tracking_type, status, cached_at, master_version
) VALUES
    ('01910000-0000-7000-8000-000000002001', 'c0000000-0000-0000-0000-000000000001', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002002', 'c0000000-0000-0000-0000-000000000002', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002003', 'c0000000-0000-0000-0000-000000000003', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002004', 'c0000000-0000-0000-0000-000000000004', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002005', 'c0000000-0000-0000-0000-000000000005', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002006', 'c0000000-0000-0000-0000-000000000006', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002007', 'c0000000-0000-0000-0000-000000000007', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002008', 'c0000000-0000-0000-0000-000000000008', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002009', 'c0000000-0000-0000-0000-000000000009', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002010', 'c0000000-0000-0000-0000-000000000010', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002011', 'c0000000-0000-0000-0000-000000000011', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002012', 'c0000000-0000-0000-0000-000000000012', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002013', 'c0000000-0000-0000-0000-000000000013', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002014', 'c0000000-0000-0000-0000-000000000014', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002015', 'c0000000-0000-0000-0000-000000000015', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002016', 'c0000000-0000-0000-0000-000000000016', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002017', 'c0000000-0000-0000-0000-000000000017', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002018', 'c0000000-0000-0000-0000-000000000018', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002019', 'c0000000-0000-0000-0000-000000000019', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002020', 'c0000000-0000-0000-0000-000000000020', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002021', 'c0000000-0000-0000-0000-000000000021', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002022', 'c0000000-0000-0000-0000-000000000022', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002023', 'c0000000-0000-0000-0000-000000000023', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002024', 'c0000000-0000-0000-0000-000000000024', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002025', 'c0000000-0000-0000-0000-000000000025', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002026', 'c0000000-0000-0000-0000-000000000026', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002027', 'c0000000-0000-0000-0000-000000000027', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002028', 'c0000000-0000-0000-0000-000000000028', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002029', 'c0000000-0000-0000-0000-000000000029', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002030', 'c0000000-0000-0000-0000-000000000030', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002031', 'c0000000-0000-0000-0000-000000000031', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002032', 'c0000000-0000-0000-0000-000000000032', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002033', 'c0000000-0000-0000-0000-000000000033', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002034', 'c0000000-0000-0000-0000-000000000034', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002035', 'c0000000-0000-0000-0000-000000000035', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002036', 'c0000000-0000-0000-0000-000000000036', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002037', 'c0000000-0000-0000-0000-000000000037', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002038', 'c0000000-0000-0000-0000-000000000038', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002039', 'c0000000-0000-0000-0000-000000000039', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002040', 'c0000000-0000-0000-0000-000000000040', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002041', 'c0000000-0000-0000-0000-000000000041', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002042', 'c0000000-0000-0000-0000-000000000042', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002043', 'c0000000-0000-0000-0000-000000000043', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002044', 'c0000000-0000-0000-0000-000000000044', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002045', 'c0000000-0000-0000-0000-000000000045', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002046', 'c0000000-0000-0000-0000-000000000046', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002047', 'c0000000-0000-0000-0000-000000000047', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002048', 'c0000000-0000-0000-0000-000000000048', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002049', 'c0000000-0000-0000-0000-000000000049', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002050', 'c0000000-0000-0000-0000-000000000050', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002051', 'c0000000-0000-0000-0000-000000000051', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002052', 'c0000000-0000-0000-0000-000000000052', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002053', 'c0000000-0000-0000-0000-000000000053', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002054', 'c0000000-0000-0000-0000-000000000054', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002055', 'c0000000-0000-0000-0000-000000000055', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002056', 'c0000000-0000-0000-0000-000000000056', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002057', 'c0000000-0000-0000-0000-000000000057', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002058', 'c0000000-0000-0000-0000-000000000058', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002059', 'c0000000-0000-0000-0000-000000000059', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002060', 'c0000000-0000-0000-0000-000000000060', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002061', 'c0000000-0000-0000-0000-000000000061', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002062', 'c0000000-0000-0000-0000-000000000062', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002063', 'c0000000-0000-0000-0000-000000000063', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002064', 'c0000000-0000-0000-0000-000000000064', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002065', 'c0000000-0000-0000-0000-000000000065', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002066', 'c0000000-0000-0000-0000-000000000066', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002067', 'c0000000-0000-0000-0000-000000000067', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002068', 'c0000000-0000-0000-0000-000000000068', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002069', 'c0000000-0000-0000-0000-000000000069', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002070', 'c0000000-0000-0000-0000-000000000070', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002071', 'c0000000-0000-0000-0000-000000000071', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002072', 'c0000000-0000-0000-0000-000000000072', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002073', 'c0000000-0000-0000-0000-000000000073', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002074', 'c0000000-0000-0000-0000-000000000074', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002075', 'c0000000-0000-0000-0000-000000000075', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002076', 'c0000000-0000-0000-0000-000000000076', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002077', 'c0000000-0000-0000-0000-000000000077', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002078', 'c0000000-0000-0000-0000-000000000078', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002079', 'c0000000-0000-0000-0000-000000000079', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002080', 'c0000000-0000-0000-0000-000000000080', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002081', 'c0000000-0000-0000-0000-000000000081', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002082', 'c0000000-0000-0000-0000-000000000082', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002083', 'c0000000-0000-0000-0000-000000000083', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002084', 'c0000000-0000-0000-0000-000000000084', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002085', 'c0000000-0000-0000-0000-000000000085', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0),
    ('01910000-0000-7000-8000-000000002086', 'c0000000-0000-0000-0000-000000000086', 'NONE', 'ACTIVE', '2026-04-18T00:00:00Z', 0)
ON CONFLICT (id) DO NOTHING;
