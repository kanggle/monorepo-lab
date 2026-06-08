# Task ID

TASK-MONO-198

# Title

ADR-MONO-022 §D4 v2(b) — ecommerce↔wms inventory reconciliation (Option B, delta): product-service consumes warehouse-origin wms inventory events + the `wms.master.sku.v1` reverse-identity stream, applying available-quantity deltas to its sellable stock to narrow the dual-bookkeeping drift.

# Status

done

# Owner

(unassigned) — ecommerce-internal code (product-service gains its first inbound consumer infrastructure) realizing a monorepo ADR §D4 decision. 분석=Opus 4.8 / 구현 권장=Opus (cross-project read-model consumption + reverse-identity resolution + delta-trajectory reconciliation = complex domain work per CLAUDE.md model-routing).

# Task Tags

- feat
- test

---

# Dependency Markers

- **선행**: TASK-MONO-195/196/197 (the fulfillment arc — this is the last named §D4 item). wms `wms.inventory.*.v1` + `wms.master.sku.v1` already published (no wms change in this task).
- **맥락**: ADR-MONO-022 §D4 v2(b) (sync policy = **Option B**, chosen by AskUserQuestion 2026-06-08), §D6 (ACL/vocab resolution on ecommerce side — wms domain pure), §D8 (degradation). scm `inventory-visibility-subscriptions.md` (the cross-project wms-inventory consumption precedent this mirrors).

# Goal

Narrow the **dual-bookkeeping drift** ADR-022 §D4 accepted as a v1 limitation: ecommerce
`product-service` sellable `stock` (decremented at order time) drifts from wms physical inventory
(the fulfillment SoT). When the warehouse restocks (inbound putaway) or loses stock (damage / loss /
manual adjustment) **without an ecommerce order causing it**, ecommerce never learns → it oversells
(→ backorder via 196/197) or undersells. This task makes product-service consume the **warehouse-origin**
wms inventory events and apply the **available-quantity delta** to its stock, keeping the order-time
gate (Option B — not a wms-as-SoT projection).

# Background (current-state — verified 2026-06-08)

- ✅ wms publishes `wms.inventory.received.v1` / `.adjusted.v1` / `.transferred.v1` / reservation-lifecycle / `.master.sku.v1` (scm consumes a subset for visibility). **No wms change needed.**
- ✅ ecommerce `product-service` `Inventory` = per-`variantId` flat `stock` (sellable); `AdjustStockService` mutates + emits `product.product.stock-changed`.
- ❌ **GAP 0 — `ProductVariant` has no SKU business key** (verified: `product_variants` = UUID `id` + option_name + stock + price; zero `sku` anywhere in product-service). The forward `FulfillmentAcl` identity assumed `ecommerce-sku == wms skuCode`, but no ecommerce field backs it. **This task adds a `sku` business key to `ProductVariant`** (V12 migration + entity + unique + seed backfill) — the proper model fix (AskUserQuestion 2026-06-08, chosen over a brittle config-uuid map).
- ❌ **GAP 1 — no reverse identity**: wms inventory events carry `skuId` (uuid), not `skuCode`; nothing maps wms `skuId → variantId`. Resolved by the new `ProductVariant.sku` (== skuCode) + a `wms.master.sku.v1` skuId→skuCode snapshot.
- ❌ **GAP 2 — product-service has no inbound consumer infrastructure**: it is a pure producer today (no Kafka consumer config, no dedupe table). This task adds the first (via `libs/java-messaging`).
- ❌ **GAP 3 — no reconciliation**: warehouse-origin stock changes never reach ecommerce.

# Scope

## In Scope
0. **`ProductVariant.sku` business key** (GAP 0): V12 migration (add `sku VARCHAR`, unique, backfill the V8 seed variants to match wms seed skuCodes) + `ProductVariant.sku` field + JPA entity/mapper + a `findVariantBySku(skuCode)` repository query. Variant creation/API paths updated minimally to carry `sku` (nullable-tolerant where not yet provided).
1. **Reverse-identity stream**: `WmsMasterSkuConsumer` (`wms.master.sku.v1`, group `product-service-wms`) → upsert `WmsSkuSnapshot(skuId, skuCode, version)` (out-of-order tolerant). New table + migration.
2. **Reconciliation consumer**: `WmsInventoryReconciliationConsumer` for `wms.inventory.received.v1` + `wms.inventory.adjusted.v1`. Per event/line: resolve `skuId → skuCode (snapshot) → variantId (variant whose SKU == skuCode)`; compute `delta = newAvailableQty − stored` against `WmsInventoryAvailable(inventoryId, skuId, availableQty)` (defaults stored=new on first sight → baseline delta 0); apply delta to the variant's stock; store new availableQty; emit `product.product.stock-changed` (`reason = WMS_RECONCILIATION`) on non-zero delta.
3. **Excluded (asserted by NOT subscribing)**: reservation lifecycle (reserved/released/confirmed — double-count) + transferred (SKU-invariant) + reserve.failed/alert.
4. **Inbound infra**: Kafka consumer config + `processed_events` dedupe (libs/java-messaging) — product-service's first.
5. **Safety**: unresolved skuId/skuCode or no matching variant → skip + log (no fabricated stock); delta underflow → clamp stock at 0 + warn (backstop signal); dedupe on `eventId`.
6. **Tests**: unit (master-sku upsert; reconciliation delta — first-sight baseline, positive/negative delta, unresolved-sku skip, underflow clamp, dedupe) + a Testcontainers IT (Kafka → DB stock corrected) following the ecommerce IT-in-`:check` convention.
7. **Contracts**: new `wms-inventory-subscriptions.md` (this PR's spec) + ADR §D4 v2(b) ACCEPTED + ledger row.

## Out of Scope (named v2(b) follow-ups)
- Periodic full re-sum backstop job (the snapshot table is the ledger; the job is a re-sum).
- Reconciling the **initial absolute seed** difference (v1 syncs deltas going forward, not the historical seed gap).
- Per-warehouse / multi-tenant stock partitioning (v1 sums all wms rows for a SKU into one variant).
- Option A (wms-as-SoT projection) / Option C (periodic batch pull) — rejected by the §D4 v2(b) decision.
- Any wms-side change (wms stays pure — consume only already-published events).

# Acceptance Criteria

- AC-0: `ProductVariant` gains a `sku` business key (V12 migration, seed backfilled to wms seed skuCodes, unique where present); `findVariantBySku(skuCode)` resolves it. Existing product tests still pass.
- AC-1: A `wms.inventory.adjusted.v1`(skuId=S, inventory.availableQty drops by N) for a SKU mapped to ecommerce variant V reduces V's sellable stock by N (after the baseline is established). (unit + IT.)
- AC-2: A `wms.inventory.received.v1`(line availableQtyAfter up by N) increases the mapped variant's stock by N.
- AC-3: **First sight of an `inventoryId`** establishes the baseline (applies delta 0 — no phantom jump); the **second** event applies the real delta.
- AC-4: An event whose `skuId` has no snapshot, or whose `skuCode` maps to no ecommerce variant, is skipped with no stock mutation and no error.
- AC-5: **No double-count** — reserved/released/confirmed/transferred topics are not subscribed (verified by the consumer's `@KafkaListener` topic set + a test asserting a published reservation event leaves stock unchanged, i.e. there is no listener).
- AC-6: Idempotent — re-delivery of the same `eventId` is a no-op (dedupe) and the trajectory delta is self-idempotent (same availableQty → 0).
- AC-7: Reconciliation emits `product.product.stock-changed`(reason=WMS_RECONCILIATION) on non-zero delta; underflow clamps stock at 0 + logs a warning.
- AC-8: Standalone profile (no wms/Kafka) → consumers disabled, product-service unaffected (§D8).

# Related Specs

- `docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md` (§D4 v2(b) Option B, §D6, §D8).
- `projects/ecommerce-microservices-platform/specs/contracts/events/wms-inventory-subscriptions.md` (this task's spec — primary).
- `projects/wms-platform/specs/contracts/events/inventory-events.md` + `master-events.md` (producer, unchanged, referenced).

# Related Contracts

- Consumed (existing, unchanged wms producers): `wms.inventory.received.v1`, `wms.inventory.adjusted.v1`, `wms.master.sku.v1`.
- Produced (reused, unchanged): `product.product.stock-changed` (`StockChangedPayload`, new `reason=WMS_RECONCILIATION`).

# Edge Cases

- Master-sku event arrives after the inventory event referencing its skuId → inventory event skipped (no snapshot); a later identical-availableQty event re-establishes once the snapshot lands (or the periodic backstop, named, corrects). Logged at debug.
- Out-of-order master-sku events → `version`-guarded upsert (ignore older).
- Multiple wms inventory rows (locations/lots) for one variant → per-`inventoryId` trajectories sum into the single variant stock.
- Delta would drive stock below 0 → clamp at 0 + warn (seed divergence beyond the delta window).
- Re-delivery → dedupe no-op + self-idempotent delta.
- Unparseable / null payload → DLT + log.

# Failure Scenarios

- product-service down when events arrive → Kafka redelivers; dedupe + trajectory make reprocessing safe.
- Snapshot table lost/rebuilt → first events re-baseline (delta 0); the named backstop re-syncs absolute values.
- wms emits a malformed inventory envelope → DLT, no stock mutation.
- A flood of adjustments → each is an independent dedupe-guarded delta; no cross-event ordering assumption (per-inventoryId trajectory is monotonic-by-arrival, which is acceptable for eventual reconciliation; the backstop is the correctness floor).

# Impact on `projects/<name>/`

- `projects/ecommerce-microservices-platform/apps/product-service/` — **`ProductVariant.sku` business key (V12 migration + entity + seed backfill + findVariantBySku)** + 2 consumers + 2 snapshot entities/repos/migrations + dedupe infra + config + `WMS_RECONCILIATION` reason + tests (the only production code).
- `projects/ecommerce-microservices-platform/specs/contracts/events/` — new `wms-inventory-subscriptions.md`.
- `docs/adr/ADR-MONO-022-*` — §D4 v2(b) ACCEPTED + ledger row.
- **wms-platform — none** (consumes only already-published events; D6 preserved).

# Notes

Monorepo `TASK-MONO` (code is ecommerce-internal): realizes the monorepo ADR-022 §D4 v2(b) decision +
adds a cross-project subscription contract, continuing the 193/195/196/197 series. **Unlike v2(a), the
v2(b) sync policy was a genuine new architecture decision** (A/B/C) → resolved by AskUserQuestion (user
chose B) → the ACCEPTED transition is user-driven, not self-ACCEPT.

---

# Implementation Notes (DONE 2026-06-08)

**product-service's first inbound consumer infrastructure** — built entirely ecommerce-side (wms untouched, D6).

- **GAP 0 — `ProductVariant.sku` business key** (the mid-impl discovery, AskUserQuestion → add the field): `V12` migration adds `sku VARCHAR(64)` + UNIQUE (nulls allowed) + deterministic seed backfill (`SKU-EC-<last-12-hex-of-id>`); `ProductVariant.sku` (nullable; `reconstitute` extended, `create` unchanged → new variants are null, only seeded/SKU'd variants reconcile) + JPA column + `ProductVariantJpaRepository.findBySku` + `InventoryRepository.findVariantBySku` returning `VariantRef(variantId, productId, currentStock)` (resolve + productId for the event + stock for the clamp in one read).
- **Reverse identity**: `WmsMasterSkuConsumer` (`wms.master.sku.v1`, group `product-service-wms`) → `WmsSkuSnapshotEntity(skuId→skuCode)` (version-guarded upsert).
- **Reconciliation**: `WmsInventoryReconciliationConsumer` (`wms.inventory.received.v1` + `.adjusted.v1`, same group) → `WmsInventoryReconciliationService`. Per event resolves `skuId→skuCode (snapshot)→variant (findVariantBySku)`; computes `delta = newAvailableQty − stored` against `WmsInventoryAvailableEntity` (first sight = baseline, delta 0); applies to `product_variants.stock` via the existing `InventoryRepository.save(Inventory)`; emits `product.product.stock-changed`(reason=`WMS_RECONCILIATION`); underflow clamps at 0. **Reserved/released/confirmed + transferred NOT subscribed** (double-count / SKU-invariant).
- **Infra**: local `WmsProcessedEventEntity` dedupe (T8, `WmsReconciliationDedupe` MANDATORY-tx) — a local table, NOT libs/java-messaging (avoids the OutboxAutoConfiguration baggage product-service doesn't use); `WmsReconciliationConfig` (Clock + retry→DLQ error handler); `spring.kafka.consumer` block added (String deserializers, group `product-service-wms`); `@EnableJpaRepositories` extended to scan the reconciliation package.

**Verification (local, all GREEN)**:
- unit: `WmsInventoryReconciliationServiceTest` (9 — baseline/±delta/underflow-clamp/zero/unresolved-sku/no-variant/version-guard) + `WmsReconciliationConsumersTest` (6) + `AdjustStockServiceTest` (reconstitute-sig regression) + **full product-service `:test` (Docker-free CI-equivalent) — no regression**.
- **Testcontainers IT `WmsInventoryReconciliationIT`** (real Postgres, `-PrunIntegration`): V12 migration applies + JPA maps + seed variant stock `50 → 45` after a `-5` delta persisted to the DB (the §14 authoritative gate). The IT is `@Tag("integration")` → excluded from CI (ecommerce Docker-free convention); local-only.

**Two infra fixes surfaced while running the first product-service IT** (pre-existing warts, fixed here):
- ecommerce `build.gradle`: wired the documented-but-unimplemented `-PrunIntegration` flag (re-includes `@Tag("integration")`); CI never sets it → stays Docker-free.
- product-service `build.gradle`: removed a hardcoded stale `DOCKER_HOST=…docker_engine_linux` named pipe that overrode the portable root convention + broke local ITs (MalformedChunkCodingException).
- IT also needed `@SpringBootTest(classes = ProductServiceApplication.class)` — a `TestProductServiceApplication` (@WebMvcTest helper) created a second @SpringBootConfiguration → ambiguous default detection (latent; surfaced as product-service's first real IT).

**Accepted residue (named v2(b) follow-ups)**: periodic full re-sum backstop (the snapshot table is the ledger) / initial absolute seed-gap / multi-warehouse partitioning / live wms↔ecommerce seed skuCode alignment / variant-create-with-sku path. The full Kafka→DB e2e (booting product-service in the loop) is deferred — consumer unit tests + the DB IT cover the two halves.
