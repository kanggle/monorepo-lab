# Event Contract — demand-planning-service (cross-project: ← wms)

Implements **ADR-MONO-027** (wms → scm stock-replenishment loop), D1 transport.

scm `demand-planning-service` subscribes to one **wms-platform** topic to drive
replenishment reorder-suggestion decisioning. The wms producer is **unchanged** —
`inventory-service` already publishes `inventory.low-stock-detected` for its
internal `notification-service` / `admin-service` consumers; this contract only
declares the new cross-project scm consumer.

The authoritative envelope + payload schema lives in the **producing service** (wms):
`projects/wms-platform/specs/contracts/events/inventory-events.md` §7. This file
reproduces only the **consumed subset** (consumer-driven contract), mirroring the
sibling `inventory-visibility-subscriptions.md`.

> **Not a published-events doc.** `demand-planning-service`'s own emissions (the
> intra-scm suggestion → procurement DRAFT-PO leg, ADR-027 D5) are decided in
> TASK-SCM-BE-023 and documented with that service's contracts — not here. This
> file is subscription-only.

---

## Subscriptions (cross-project)

### Consumer Group

`scm-demand-planning-v1` — distinct from `scm-inventory-visibility-v1` (the
group used by `inventory-visibility-service`), so the two scm consumers of wms
events keep independent offsets and rebalancing.

### Subscribed Topic

| Topic | Event Type | Handler Class | Effect on scm |
|---|---|---|---|
| `wms.inventory.alert.v1` | `inventory.low-stock-detected` | `WmsLowStockAlertConsumer` | Evaluate the scm reorder policy for the SKU; if `availableQty <= reorder_point`, raise a `reorder_suggestion` (status `SUGGESTED`) — subject to the open-suggestion guard (D6). |

> ⚠️ `demand-planning-service` subscribes to **only** the `alert` topic. The raw
> mutation topics (`wms.inventory.{received,adjusted,transferred}.v1`) are consumed
> by `inventory-visibility-service` for a **different** purpose (read-model
> visibility, S5) and MUST NOT be consumed here. The `alert` topic is the
> purpose-built, threshold-aware, debounced replenishment trigger.

### Envelope — **wms convention (camelCase)**

> wms events use the camelCase envelope `eventId`/`eventType`/`eventVersion`/
> `occurredAt`/`producer`/`aggregateType`/`aggregateId`/`payload` (see wms
> `inventory-events.md` § Global Envelope) — **not** the scm
> `BaseEventPublisher` shape. The consumer DTO maps the wms shape. Reuse the
> envelope-mapping approach already proven in `inventory-visibility-subscriptions.md`.

### Consumed subset (`inventory.low-stock-detected` payload)

The consumer reads only these fields and MUST ignore unknown payload fields
(forward compatibility). Authoritative shape: wms `inventory-events.md` §7.

| Field | Type | Use |
|---|---|---|
| `eventId` | UUID v7 (envelope) | Idempotency key (T8). Stored in `processed_events`. |
| `occurredAt` | ISO-8601 (envelope) | Suggestion provenance timestamp. |
| `payload.skuId` | UUID | SKU identity. |
| `payload.skuCode` | string | **Join key** to `sku_supplier_map` and `reorder_policy` (the SKU coding shared with procurement). |
| `payload.locationId` / `payload.locationCode` | UUID / string | Warehouse/location dimension of the suggestion key `(sku_code, warehouse)`. `locationId` (UUID) is retained scm-side as the reorder-suggestion dedup-key dimension. |
| `payload.warehouseCode` | string | **ADR-MONO-050 D9 (additive).** The warehouse **business CODE** (e.g. `"WH-SEOUL-01"`). scm threads it → `reorder_suggestion.warehouse_code` → the PO `destinationWarehouseId` so wms's `findWarehouseByCode` resolves the inbound-expected destination (Option A — cross-service identifiers are codes). Required on the live replenishment path (fail-closed to DLT if absent). |
| `payload.availableQty` | int | Compared against the scm `reorder_point`. |
| `payload.threshold` | int | The wms alert threshold — informational only; **NOT** the scm reorder decision (D4 — scm owns its own reorder point). |
| `payload.triggeringEventType` | string | Diagnostic provenance (which mutation crossed the wms threshold). |

### Idempotency (T8)

Dedupe on envelope `eventId` (UUID v7) via the `processed_events` table.
Duplicate `eventId` → event skipped without mutation. The same `eventId` may be
redelivered by Kafka or by the wms outbox retry on the publisher side.

> **Business-duplicate suppression is separate.** wms debounces the alert ~1h per
> inventory row, but a drop-then-redrop across the debounce window re-fires with a
> *new* `eventId`. Event dedup does **not** cover that. The authoritative guard
> against piling up suggestions for the same `(sku_code, warehouse)` while one is
> still open is the **open-suggestion guard** (ADR-027 D6), specified with the
> `reorder_suggestion` data model in TASK-SCM-BE-023.

### Retry + DLT

- Retry: 3 attempts with exponential backoff.
- DLT: `wms.inventory.alert.v1.DLT`.
- **Non-retryable → immediate DLT + ops alert (never silently dropped):**
  - null `eventId` or null `payload` (malformed envelope), or
  - `skuCode` with no row in `sku_supplier_map` (unmapped SKU — fail-closed, scm S2 posture; cannot reorder from an unknown supplier).
- Retryable (transient) faults (DB down, etc.) exhaust the 3 attempts then DLT.

### Schema Compatibility

wms v1 envelope fields used by this consumer (above). If wms introduces
`wms.inventory.alert.v2`, this consumer continues on v1 during the grace period;
a separate follow-up task migrates to v2 (scm↔wms `inventory-visibility`
precedent).

### Standalone-publish degradation (ADR-027 D8)

Without wms present (scm published standalone), the `wms.inventory.alert.v1`
topic never arrives; `demand-planning-service` holds an empty suggestion list.
The nightly batch sweep over the inventory-visibility read-model (TASK-SCM-BE-024)
still runs and is also empty without wms data. **No hard dependency** — same
posture as the `inventory-visibility-service` precedent and ADR-MONO-022 §D8.

---

## References

- [ADR-MONO-027](../../../../../docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md)
  D1 (transport), D4 (scm-owned reorder policy vs wms threshold), D6 (idempotency
  + open-suggestion guard), D8 (degradation)
- [`inventory-visibility-subscriptions.md`](./inventory-visibility-subscriptions.md)
  — sibling scm←wms cross-project subscription doc (structure mirrored)
- [`scm-procurement-events.md`](./scm-procurement-events.md) — envelope / consumer-rules conventions
- [`projects/wms-platform/specs/contracts/events/inventory-events.md`](../../../../wms-platform/specs/contracts/events/inventory-events.md)
  §7 `inventory.low-stock-detected` — **authoritative producing-side schema**
- `platform/event-driven-policy.md` — consumer idempotency (T8)
- `rules/domains/scm.md` S2 (idempotency keys on outbound), S5 (eventual consistency)
- `rules/traits/transactional.md` T8 (consumer idempotency)
- TASK-SCM-BE-022 — this subscription contract authoring task
- TASK-SCM-BE-023 — `demand-planning-service` spec suite (consumes this contract)
