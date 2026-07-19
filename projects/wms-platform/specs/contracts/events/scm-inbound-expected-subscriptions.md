# Event Contract — inbound-service subscriptions (cross-project: ← scm)

Implements **ADR-MONO-050** (scm → wms inbound-expected loop), D1/D5/D6/D7.

`inbound-service` subscribes to an **scm-platform** procurement event so that a supplier-
acknowledged (`CONFIRMED`) purchase order pre-creates an **inbound expectation (ASN)** in wms —
turning receiving from a blind manual entry into a PO-traceable, pre-staged expectation.

> **First `scm → wms` runtime coupling (ADR-MONO-050 D7).** Until now the wms↔scm relationship
> was strictly one-way (`wms → scm`: inventory-visibility snapshots + the ADR-027 low-stock alert).
> This subscription makes `inbound-service` the **first wms service to consume an scm-published
> topic**, so the coupling becomes bidirectional. It stays loose: async transport (no synchronous
> path), independent consumer offsets, and standalone-degradation (D8) — without scm the topic
> simply never arrives and the manual ASN path is unchanged.

> **Scope boundary (ADR-MONO-050 §1.3).** This does NOT move or reflect stock. An inbound
> expectation is a **promise of future arrival**, not a stock mutation. Physical inventory only
> changes when goods physically arrive and wms receives → inspects → puts them away, emitting the
> **existing** `wms.inventory.received.v1` (which scm inventory-visibility already consumes — that
> is how the loop visibly closes). This consumer adds **no** new downstream inventory path.

**Authoritative producer schema** (payload of record): `projects/scm-platform/specs/contracts/events/scm-procurement-events.md`
(authored by TASK-SCM-BE-034). This file is a **consumer-driven** subscription doc: it reproduces
only the subset of fields `inbound-service` reads and defers the full/authoritative payload schema
to the producing service. On any field-shape disagreement, **scm is authoritative**. Mirrors the
ADR-027 `replenishment-subscriptions.md` / inventory-visibility contract-ownership pattern, only
with the roles reversed (scm producer, wms consumer).

---

## Consumer Group

`wms-inbound-scm-expected-v1` — an **independent** consumer group (separate offsets from the
`inbound-service` master-snapshot consumers). Set explicitly on the listeners so this new coupling
cannot disturb the existing `wms.master.*` projection offsets.

## Subscribed Topics

| Topic | scm event | Handler (new) | Effect |
|---|---|---|---|
| `scm.procurement.inbound-expected.v1` | PO `CONFIRMED` (supplier-acknowledged, ADR-050 D2) | `ScmInboundExpectedConsumer#onInboundExpected` | Validate (D3/D4) → dedup (D6.1/D6.2) → create an `Asn` inbound expectation (`source = SCM_PROCUREMENT`) addressed to the event's warehouse → publish the existing `inbound.asn.received`. |
| `scm.procurement.inbound-expected.cancelled.v1` | PO cancelled/withdrawn after CONFIRMED (ADR-050 D6.3) | `ScmInboundExpectedConsumer#onInboundExpectedCancelled` | Mark the matching **open** (not-yet-received) expectation `CANCELLED`; **no-op** if already received or never created. |

## Envelope

scm emits these cross-project events in the **standard wms envelope convention** (camelCase
`eventId` / `eventType` / `occurredAt` / `aggregateType` / `aggregateId` / `payload`), the same
shape the `wms.master.*` and `ecommerce.fulfillment.*` consumers already parse. `aggregateType =
purchase_order`, `aggregateId = PO id`. The business fields below live under `payload`.

## Payload consumed (subset — authoritative schema in scm `scm-procurement-events.md`)

```jsonc
{
  "eventId": "uuid-v7",                    // envelope idempotency key (D6.1)
  "eventType": "scm.procurement.inbound-expected",
  "occurredAt": "2026-07-19T04:12:00Z",
  "aggregateType": "purchase_order",
  "aggregateId": "<poId>",
  "payload": {
    "poId": "uuid",                        // traceability back to scm
    "poNumber": "SCM-PO-2026-00187",       // business dedup key, with line (D6.2)
    "supplierId": "SUP-0043",              // supplier partner CODE (resolved → wms partner uuid)
    "destinationWarehouseId": "WH-SEOUL-01",// ★ warehouse CODE — addressed, not assumed (D3)
    "destinationNodeType": "WMS_WAREHOUSE", // v1 accepts ONLY this value (D4)
    "expectedArrivalDate": "2026-07-24",   // → Asn.expectedArriveDate
    "currency": "KRW",                     // read but not persisted on the expectation (v1)
    "lines": [
      { "skuCode": "SKU-A", "expectedQty": 100, "uom": "EA" }
    ]
  }
}
```

The `.cancelled.v1` payload carries at least `{ poId, poNumber }` (+ envelope `eventId`); wms keys
the cancellation on `poNumber`.

## Processing semantics (ADR-050 D5/D6)

wms realises the ADR's conceptual **`InboundExpectation(status = EXPECTED)`** as its **existing
`Asn` aggregate in its initial not-yet-received state (`CREATED`)**, with the new `source =
SCM_PROCUREMENT` and the additive `poNumber` / `poId` traceability columns. This is a deliberate
reuse, not a new aggregate: wms's `Asn` (입고 예정 / ASN) *is* the inbound expectation, and its
`CREATED` status already means "an inbound is expected, notification recorded, awaiting physical
arrival/inspection". Reusing it is what makes ADR-050 D5's "reuse the existing inbound flow,
**unchanged**" literally true — the downstream 검수(receive) → 적치(putaway) → stock-reflect →
`wms.inventory.received.v1` flow picks the SCM-sourced ASN up from `CREATED` with **zero code
change**. (See § Status mapping note.)

On `scm.procurement.inbound-expected.v1`:

1. **Event dedup (D6.1)** — the envelope `eventId` (UUID v7) is inserted into wms's existing
   `inbound_event_dedupe` (trait T8) in the same transaction; a redelivered `eventId` is a no-op.
2. **Node-type gate (D4)** — `payload.destinationNodeType != WMS_WAREHOUSE` → **reject to DLT**
   (defensive; scm filters 3PL destinations producer-side, so this should never arrive).
3. **Warehouse resolve (D3, fail-closed)** — resolve `payload.destinationWarehouseId` (code)
   against the local warehouse read-model (populated by `wms.master.warehouse.v1`). Unknown or
   inactive → **reject to DLT + ops signal**. Never silently create an orphan expectation.
4. **Business dedup (D6.2)** — if an **open** (not `CLOSED`/`CANCELLED`) `Asn` already exists for
   this `poNumber`, skip creation (record the eventId as processed, no second expectation). The
   `poNumber` (+ line) is the business key even across distinct `eventId`s.
5. **Supplier / SKU resolve** — resolve `payload.supplierId` (code → wms partner, must be an
   ACTIVE supplier) and each `lines[].skuCode` (code → wms SKU, must be ACTIVE). Unknown/inactive
   → **reject to DLT + ops signal** (mirrors the ERP-webhook / ecommerce-fulfillment resolver).
6. **Create the expectation (D5)** — a new `Asn`: `status = CREATED`, `source = SCM_PROCUREMENT`,
   `warehouseId` = the **resolved** destination (single AND multi warehouse on ONE code path —
   no branching on warehouse count), preserving `poNumber` / `poId` (traceability), `supplierId`,
   `expectedArrivalDate`, and the lines. A wms `asnNo` is minted from the existing sequence. The
   existing `inbound.asn.received` is published (parity with the manual/webhook create path) —
   this is an "expectation created" notification, **not** a stock event.

On `scm.procurement.inbound-expected.cancelled.v1` (D6.3):

1. Event dedup on the envelope `eventId` (T8), as above.
2. Look up the **open** `Asn` for `payload.poNumber`.
   - Found and **not yet received** (`CREATED` / `INSPECTING`) → mark `CANCELLED`, publish the
     existing `inbound.asn.cancelled`.
   - Already received (`IN_PUTAWAY` / `PUTAWAY_DONE` / `CLOSED`) or none open → **no-op** (goods
     already physically in flow; cancelling would violate the physical invariant). Never a DLT.

## Warehouse addressing — single AND multi warehouse, one path (ADR-050 D3, user requirement)

The destination warehouse is **carried in the event** (`destinationWarehouseId`), never assumed:

- **Single-warehouse deployment** = the degenerate case — every event carries the same code; the
  consumer resolves it and addresses the expectation to that one warehouse. No special-casing.
- **Multi-warehouse deployment** = different POs carry different codes; each resolves to its own
  warehouse and the expectation is addressed there. **Zero code change** between the two — the
  consumer never branches on how many warehouses exist. Warehouse-count is a deployment fact, not
  a code path. An unknown/inactive code fails closed (§ step 3).

## Idempotency / Retry / DLT

- **Layer 1 — event dedup**: envelope `eventId` in `inbound_event_dedupe` (T8) — redelivery no-op.
- **Layer 2 — business dedup**: `(poNumber, line)` open-expectation guard — no second open
  expectation for the same PO even across distinct `eventId`s.
- Retry up to 3× (shared `DefaultErrorHandler`) then `<topic>.DLT`. Deterministic rejects
  (malformed envelope, unknown node type, unknown/inactive warehouse/supplier/SKU) are
  **non-retryable** → straight to DLT + ops signal.

## Status mapping note (ADR-050 D5 conceptual EXPECTED ↔ wms Asn CREATED)

ADR-050 D5 names the created record `InboundExpectation(status = EXPECTED)`. wms does **not**
introduce a new aggregate or a new status: it reuses the `Asn` (ASN / 입고 예정) whose existing
initial status `CREATED` **is** the "expected, not-yet-received" state. This preserves D5's
requirement that the **existing inbound flow is reused unchanged** (the flow enters at `CREATED`),
and it maps the ADR's semantics 1:1:

| ADR-050 concept | wms realisation |
|---|---|
| `InboundExpectation` | `Asn` aggregate (`source = SCM_PROCUREMENT`) |
| status `EXPECTED` (created, awaiting arrival) | `AsnStatus.CREATED` |
| "not yet received" (cancellable) | `CREATED` / `INSPECTING` |
| "already received" (cancel = no-op) | `IN_PUTAWAY` / `PUTAWAY_DONE` / `CLOSED` |

## Standalone-publish degradation (ADR-050 D8)

Without scm present, these topics never arrive; wms runs on the manual/ERP-webhook ASN intake
exactly as today. No hard dependency.
