# Event Contract — product-service subscriptions to wms inventory (cross-project: ← wms)

Implements **ADR-MONO-022 §D4 v2(b)** (inventory reconciliation, Option B — delta).

`product-service` subscribes to **wms-platform** events to narrow the dual-bookkeeping drift
between its sellable `stock` (the order-time gate) and wms physical inventory (the fulfillment
SoT). It applies **warehouse-origin available-quantity deltas** to its own stock — it does **not**
become a projection of wms (the order-time reservation gate is kept; that was Option A, rejected).

> **wms is untouched.** This consumer reads only events wms **already publishes** (the same topics
> scm `inventory-visibility-service` consumes). All wms↔ecommerce vocabulary resolution lives on the
> ecommerce side (ADR-022 §D6 — wms domain stays pure).

Authoritative producer schema: `projects/wms-platform/specs/contracts/events/inventory-events.md`
(inventory) + `master-events.md` (sku).

---

## Consumer Group

`product-service-wms` — distinct from any ecommerce-internal product-service group, so the wms
reconciliation leg has independent offsets/rebalancing.

## Subscribed Topics

| Topic | wms event | Handler (new) | Effect on ecommerce product stock |
|---|---|---|---|
| `wms.master.sku.v1` | `master.sku.*` | `WmsMasterSkuConsumer` | Upsert a local `skuId → skuCode` snapshot (the reverse-identity table). No stock effect. |
| `wms.inventory.received.v1` | `inventory.received` | `WmsInventoryReconciliationConsumer` | Per line: resolve `skuId → skuCode → variantId`; apply `(availableQtyAfter − last-known)` to the variant's sellable stock. |
| `wms.inventory.adjusted.v1` | `inventory.adjusted` | `WmsInventoryReconciliationConsumer` | Resolve `skuId`; apply `(inventory.availableQty − last-known)` to the variant's sellable stock (correct for any reason/bucket — damage, loss, manual). |

## Excluded topics (by design — NOT consumed)

| Topic | Why excluded |
|---|---|
| `wms.inventory.reserved.v1` / `.released.v1` / `.confirmed.v1` | The reservation lifecycle is **caused by ecommerce orders**, which product-service already decremented at order time. Re-applying would **double-count**. |
| `wms.inventory.transferred.v1` | An intra-warehouse location move leaves the SKU total invariant — no sellable change. |
| `wms.inventory.reserve.failed.v1` / `.alert.v1` | Backorder/alert signals — handled by the order/shipping return-leg (v2(a)) and ops, not stock reconciliation. |

## Envelope — **wms convention (camelCase)**

wms events use camelCase `eventId`/`eventType`/`occurredAt`/`aggregateType`/`aggregateId`/`payload`
(see `inventory-events.md` § Global Envelope). The consumer DTOs map the wms shape and dedupe on
the camelCase `eventId` (T8, via `libs/java-messaging` processed-events — product-service's first
inbound consumer infrastructure).

## Reverse identity resolution (the ADR-flagged cost)

wms inventory events carry `skuId` (uuid), **not** `skuCode`. Resolution is two-hop, ecommerce-side:

1. `wms.master.sku.v1` → `WmsSkuSnapshot(skuId, skuCode)` (upserted as SKU master flows; out-of-order
   tolerant via `version`).
2. `skuCode → variantId`: ecommerce **variant SKU == wms skuCode** (the forward `FulfillmentAcl`
   identity). product-service looks up the variant whose SKU equals the resolved `skuCode`.

A `skuId` with no snapshot yet (master event not arrived) or a `skuCode` mapping to no ecommerce
variant (a wms SKU not sold on the storefront) → **skip silently** (log at debug). No fabricated stock.

## Correctness mechanism — available-trajectory delta

product-service stores `WmsInventoryAvailable(inventoryId, skuId, availableQty)`. On each consumed
event it computes `delta = newAvailableQty − storedAvailableQty` (stored defaults to the new value on
first sight → first event for an unseen inventory row applies **0**, establishing the baseline without
a phantom jump), applies `delta` to the variant's stock, then stores `newAvailableQty`. This:

- is correct for any adjustment reason/bucket (tracks *available*, never decodes wms bucket math),
- is naturally idempotent on re-delivery (same `availableQty` → delta 0) **and** dedupe-guarded,
- doubles as the reconciliation ledger — a periodic full re-sum backstop (named v2(b) follow-up).

> **Multi-location note:** one ecommerce variant = the sum over that SKU's wms inventory rows
> (multiple `inventoryId` across locations/lots). The per-`inventoryId` trajectory deltas sum into the
> single variant stock correctly. Cross-location transfers net to zero (and are excluded anyway).

## Idempotency / Retry / DLT

- Dedupe on envelope `eventId` (`processed_events`, T8) — re-delivery is a no-op. The trajectory delta
  is *also* self-idempotent (re-applying the same availableQty yields 0).
- Retry then `<topic>.DLT`. Unparseable / null payload → non-retryable → DLT + log.
- Stock never goes below 0: a delta that would underflow clamps at 0 + logs a reconciliation warning
  (indicates the order-time gate and wms have diverged beyond the delta window — a backstop signal).

## Emitted on reconciliation

Each applied non-zero delta emits the existing `product.product.stock-changed`
(`StockChangedPayload`) with `reason = WMS_RECONCILIATION` and `orderId = null` — so existing
stock-change consumers (search index, etc.) see the corrected quantity through the normal path.

## Standalone-publish degradation (ADR-022 §D8)

Without wms present, these topics never arrive; product-service stock stays purely order-driven
(today's behavior). No hard dependency.

## Not in v2(b) v1 (named follow-ups)

- Periodic full re-sum backstop job (the snapshot table is the ledger; the job is a re-sum).
- Reconciling the **initial** absolute seed difference (v1 syncs *deltas* going forward, not the
  historical seed gap).
- Per-warehouse / multi-tenant stock partitioning (v1 sums all wms rows for a SKU into one variant).
