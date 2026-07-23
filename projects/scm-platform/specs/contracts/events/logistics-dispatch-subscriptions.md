# Event Contract — logistics-service (cross-project: ← wms)

Implements **[ADR-MONO-053](../../../../../docs/adr/ADR-MONO-053-logistics-service-multimodal-fulfillment.md)** (logistics-service Phase 1), realising the wms↔transport seam of **[ADR-MONO-052](../../../../../docs/adr/ADR-MONO-052-transport-context-map.md)** §D5.

scm `logistics-service` subscribes to one **wms-platform** topic to drive carrier
dispatch. The wms producer is **unchanged** — `outbound-service` already publishes
`outbound.shipping.confirmed` for its inventory-service / ecommerce return-leg
consumers; this contract only declares the **new cross-project scm consumer**. **No
wms schema/payload change** (additive consumer registration, mirroring the
`replenishment-subscriptions.md` precedent).

The authoritative envelope + payload schema lives in the **producing service** (wms):
`projects/wms-platform/specs/contracts/events/outbound-events.md` §7. This file
reproduces only the **consumed subset** (consumer-driven contract).

> **Not a published-events doc.** `logistics-service` emits no domain event in
> Phase 1 (no outbox — ADR-053 §D2). In-transit / tracking emissions are Phase 2/3.
> This file is subscription-only.

---

## Subscriptions (cross-project)

### Consumer Group

`scm-logistics-v1` — distinct from `scm-demand-planning-v1` and
`scm-inventory-visibility-v1`, so this third scm consumer of wms events keeps
independent offsets and rebalancing.

### Subscribed Topic

| Topic | Event Type | Handler Class | Effect on scm |
|---|---|---|---|
| `wms.outbound.shipping.confirmed.v1` | `outbound.shipping.confirmed` | `ShippingConfirmedConsumer` | Create a `Dispatch(PENDING)` for the shipment, route a carrier (`CarrierRouter`), and push to the vendor (`ShipmentDispatchPort`) → `DISPATCHED` / `DISPATCH_FAILED`. |

> ⚠️ `logistics-service` subscribes to **only** the `shipping.confirmed` topic — the
> single fact "goods left a wms warehouse". The other outbound topics
> (`outbound.picking.*`, `outbound.packing.completed`, `outbound.order.*`) are wms
> saga-internal / other-consumer concerns and MUST NOT be consumed here.

### Envelope — **wms convention (camelCase)**

> wms events use the camelCase envelope `eventId`/`eventType`/`eventVersion`/
> `occurredAt`/`producer`/`aggregateType`/`aggregateId`/`tenantId`/`payload` (see
> wms `outbound-events.md` § Global Envelope) — **not** the scm `BaseEventPublisher`
> shape. The consumer DTO maps the wms shape (reuse the envelope-mapping approach
> proven in `inventory-visibility-subscriptions.md`).

### Consumed subset (`outbound.shipping.confirmed` payload)

The consumer reads only these fields and MUST ignore unknown payload fields
(forward compatibility). Authoritative shape: wms `outbound-events.md` §7.

| Field | Type | Use |
|---|---|---|
| `eventId` | UUID v7 (envelope) | Idempotency key (T8). Stored in `processed_events`. |
| `tenantId` | string / null (envelope) | scm `TenantContext` for the dispatch record (echoed ecommerce tenant for `FULFILLMENT_ECOMMERCE`; null for B2B — logistics does not interpret it, only stores it). |
| `occurredAt` | ISO-8601 (envelope) | Dispatch provenance timestamp. |
| `payload.shipmentId` | UUID | **Dispatch identity.** The `dispatch.shipment_id` unique key, the `Idempotency-Key` toward the vendor, and the `dispatch_request_dedupe.request_id` (ADR-052 §2.7). |
| `payload.shipmentNo` | string | Business identifier surfaced to the operator. |
| `payload.orderId` / `payload.orderNo` | UUID / string | Reference / correlation only (not the dispatch key). |
| `payload.warehouseId` | UUID | Origin node — the **from**-address derivation source and a `CarrierRouter` region hint. |
| `payload.carrierCode` | string / **null** | Primary `CarrierRouter` signal → vendor (e.g. `CJ-LOGISTICS` → 굿스플로; an international carrier → EasyPost). **Nullable** — see § Known input gap. |
| `payload.shippedAt` | ISO-8601 | Provenance. |
| `payload.lines[]` (`skuId`, `qtyConfirmed`, `lotId`, `locationId`, `orderLineId`) | array | Parcel contents summary carried to the vendor request (weight/dims are not in the event — vendor defaults / master lookup at BE-042). |

### Known input gap — destination address (honest limitation, not silently widened)

The seam event carries shipment identity + `carrierCode` + line summary but **no
destination address** — matching the retired wms→TMS contract, which
**deliberately forbade** `pickupAddress` / `deliveryAddress` / `warehouseCode` as v1
fields (ADR-052 §A2). A full carrier **label purchase** (EasyPost/굿스플로 `POST
/shipments`) needs the to-address. Phase 1 therefore treats dispatch as a
**carrier handover / tracking-registration** using what the seam carries plus a
warehouse-derived from-address (config), **not** a full address-bearing label buy.

Enriching the dispatch with a real destination address is **out of Phase-1 scope**
and requires **either** an additive wms contract field **or** an order-detail read
dependency — a **separate follow-up task**, not a silent widening here (per this
task's Edge Cases and ADR-052 §D5 "no new contract"). Recorded so a future reader
does not mistake Phase-1 dispatch for full label fulfilment.

### `CarrierRouter` selection

`carrierCode` present → map to the owning vendor (domestic carrier → 굿스플로;
international → EasyPost). `carrierCode` **null** → route to the **configured
default vendor** and log `CARRIER_UNROUTABLE` degrade — never silently drop the
shipment (see `architecture.md` § Failure Modes). Exactly one vendor per shipment.

### Idempotency (T8)

Dedupe on envelope `eventId` (UUID v7) via `processed_events`. Duplicate `eventId`
→ skipped without mutation. **Plus** a business guard: `dispatch.shipment_id` is
unique — a redelivered event (new or same `eventId`) finds the existing dispatch
and no-ops. The same `eventId` may be redelivered by Kafka or by the wms outbox
retry on the publisher side.

### Retry + DLT

- Retry: 3 attempts with exponential backoff `[1s, 2s, 4s]`.
- DLT: `wms.outbound.shipping.confirmed.v1.DLT`.
- **Non-retryable → immediate DLT + ops alert (never silently dropped):** null
  `eventId` / null `payload` (malformed envelope).
- **A vendor dispatch failure is NOT a consume failure** — the event is ack'd, the
  dispatch is persisted `DISPATCH_FAILED`, and recovery is the operator
  `:retry` endpoint (a failed carrier does not block or DLT the seam). Transient
  DB faults exhaust the 3 attempts then DLT.

### Schema Compatibility

Uses wms v1 envelope + payload fields (above). If wms introduces
`wms.outbound.shipping.confirmed.v2`, this consumer continues on v1 during the
grace period; a separate follow-up migrates to v2 (scm↔wms precedent).

### Standalone-publish degradation

Without wms present (scm published standalone), the topic never arrives;
`logistics-service` holds an empty dispatch list and the `StandaloneDispatchAdapter`
serves local rehearsal. **No hard dependency** — same posture as the
`inventory-visibility` / `demand-planning` precedents and ADR-052 §D5.

---

## References

- [ADR-MONO-053](../../../../../docs/adr/ADR-MONO-053-logistics-service-multimodal-fulfillment.md) §D2 (dispatch), §D3 (CarrierRouter), §D8 (wms retirement)
- [ADR-MONO-052](../../../../../docs/adr/ADR-MONO-052-transport-context-map.md) §D5 (fact-event seam, no new contract), §A2 (the TMS contract forbade address fields)
- [`replenishment-subscriptions.md`](./replenishment-subscriptions.md) — sibling scm←wms cross-project subscription doc (structure mirrored)
- [`projects/wms-platform/specs/contracts/events/outbound-events.md`](../../../../wms-platform/specs/contracts/events/outbound-events.md) §7 `outbound.shipping.confirmed` — **authoritative producing-side schema**
- [`../../services/logistics-service/architecture.md`](../../services/logistics-service/architecture.md) — consumer wiring, `CarrierRouter`, dispatch state machine
- [`../../services/logistics-service/external-integrations.md`](../../services/logistics-service/external-integrations.md) — vendor dispatch policy
- `platform/event-driven-policy.md` — consumer idempotency (T8) · `rules/domains/scm.md` S2/S5 · `rules/traits/transactional.md` T8
- TASK-SCM-BE-041 — this subscription contract's authoring task
