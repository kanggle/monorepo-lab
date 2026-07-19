# ADR-MONO-050 — scm → wms Inbound-Expected Loop (confirmed PO → warehouse inbound expectation)

**Status:** ACCEPTED
**Date:** 2026-07-19 (PROPOSED 2026-07-19 · ACCEPTED 2026-07-19 — user-explicit ADR-naming intent "ADR-MONO-050 ACCEPTED"; see §6)
**Decision driver:** User request (2026-07-19) — close the leg that [ADR-MONO-027](ADR-MONO-027-wms-scm-replenishment-loop.md) §1.3/§D5 deliberately left open: after scm procurement confirms a replenishment PO, the incoming goods should pre-create an **inbound expectation (ASN)** in wms so receiving is no longer a blind manual entry. Explicit scope constraint from the user: **must work for a single warehouse AND for multiple warehouses** (warehouse-addressed routing, not a single hard-coded destination).
**Supersedes:** none.
**Related:** [ADR-MONO-027](ADR-MONO-027-wms-scm-replenishment-loop.md) (the wms → scm *replenishment* loop this closes the return leg of — 027 §D5 terminates at an scm-internal DRAFT PO and explicitly does **not** connect back to wms; this ADR adds that connection), [ADR-MONO-022](ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) (the ecommerce → wms *forward* fulfillment loop — the precedent for a cross-project runtime coupling that **is** closed end-to-end when both systems are in-repo), [ADR-MONO-004](ADR-MONO-004-shared-messaging-scaffolding.md) (shared `libs/java-messaging` outbox/consumer scaffolding — the transport this rides on), [`platform/service-boundaries.md`](../../platform/service-boundaries.md) §「Asynchronous (Events) — cross-project allowed」, the live precedent **scm `inventory-visibility-service` ← wms inventory events**.

> **Why an ADR, not just a task.** This introduces the **first `scm → wms` runtime coupling direction**. Until now the wms↔scm coupling has been strictly one-way (`wms → scm`: inventory-visibility snapshots + the 027 low-stock alert). This ADR makes wms `inbound-service` the **first wms service to consume an scm-published topic**, i.e. the coupling becomes bidirectional. The direction, the trigger point (which PO state fires it), the warehouse-addressing model (single vs multi), the 3PL-destination boundary, and the idempotency/cancellation semantics are genuine architecture decisions that must be recorded before implementation. Authored PROPOSED so the user can review §2 before code; the §3 implementation plan does not start until ACCEPTED.

---

## 1. Context

### 1.1 The open leg (what 027 deliberately left out)

[ADR-MONO-027](ADR-MONO-027-wms-scm-replenishment-loop.md) built the replenishment loop up to — and stopping at — an scm-internal DRAFT PO:

```
wms low stock → wms.inventory.alert.v1 → scm demand-planning → reorder SUGGESTION
   → (operator approves) → scm procurement DRAFT PO → (operator submits) → external supplier
```

027 §1.3 and §D5 are explicit that the loop **terminates at scm's procurement boundary** and shares "no synchronous path" with wms. When the physical goods eventually arrive, they re-enter through wms's **normal manual inbound/ASN putaway path** — but nothing programmatically connects the scm PO to a wms inbound expectation. **That closing leg is the subject of this ADR.**

### 1.2 What already exists on each side (no new domain invention)

- **scm `procurement-service`** owns the PO lifecycle `DRAFT → SUBMITTED → ACKNOWLEDGED (supplier ack) → CONFIRMED → … → RECEIVED`. A confirmed PO already carries `supplierId`, lines (`skuCode`, `qty`), `currency`, and — via 027's `sku_supplier_map` — a `lead_time_days` (expected-arrival horizon) and the originating `warehouseId` (carried from the wms alert that seeded the suggestion).
- **wms `inbound-service`** already owns **입고 예정(ASN) 관리, 검수, 적치 지시** (PROJECT.md Service Map). Inbound expectations are authored **by a human** today; there is no programmatic "create an inbound expectation from an upstream PO" entry point. The physical receiving / putaway / stock-reflect flow downstream of an expectation is unchanged by this ADR.

So both the producing fact (a confirmed PO with a destination warehouse) and the consuming capability (create + process an inbound expectation) already exist. This ADR wires one to the other.

### 1.3 What this is NOT

- It does **not** move or reflect stock. Physical inventory remains wms-owned (027 §D7). An inbound-expected record is a *promise of future arrival*, not a stock mutation. Stock only changes when goods physically arrive and wms receives them — emitting the existing `wms.inventory.received.v1`, which scm inventory-visibility already consumes (that is how the loop visibly closes).
- It does **not** auto-submit or auto-confirm POs. The operator gate from 027 §D2 is untouched — a human still confirms the PO. This ADR fires *after* `CONFIRMED`.
- It does **not** manage 3PL-operated warehouses (see §D4).

### 1.4 The reconciliations that need an explicit decision

1. **Coupling direction reversal.** wms would, for the first time, depend at runtime on an scm fact. The async/event boundary must be chosen so wms's inbound TX is not coupled to scm uptime. → **D1**.
2. **Trigger point.** Firing on `SUBMITTED` risks ghost expectations if the supplier never acks; firing on `CONFIRMED` is accurate but slightly later. → **D2**.
3. **Single vs multi warehouse (user requirement).** The destination must be *addressed by the event*, not assumed, so one warehouse and N warehouses are the same code path. → **D3**.
4. **3PL destination.** A confirmed PO could nominate a 3PL node, which wms does not operate. → **D4**.
5. **Duplicate / cancelled / amended POs.** Redelivery, PO cancellation after the expectation exists, and quantity amendment each need a rule. → **D6**.

---

## 2. Decision

### D1 — Transport: scm publishes `scm.procurement.inbound-expected.v1`; wms `inbound-service` consumes (chosen)

scm `procurement-service` emits a new event via the shared transactional outbox (same TX as the PO state change → no lost events). wms `inbound-service` adds a Kafka consumer.

- Topic: `scm.procurement.inbound-expected.v1`; `aggregateType=purchase_order`, `aggregateId=PO id`.
- Consumer group: `wms-inbound-scm-expected-v1` (independent offsets).
- Idempotent on `eventId` (UUID v7) via wms's `processed_events`; at-least-once tolerated; retry 3× → `<topic>.DLT`.
- Authoritative payload schema lives in the **producing** service (scm `scm-procurement-events.md`); wms writes a **consumer-driven** subscription doc reproducing only the subset it reads. Mirrors the 027 / inventory-visibility contract-ownership pattern, only with the roles reversed (scm producer, wms consumer).

Payload (v1):

```jsonc
{
  "eventId": "uuid-v7",                 // idempotency key
  "occurredAt": "2026-07-19T04:12:00Z",
  "poId": "uuid",
  "poNumber": "SCM-PO-2026-00187",      // business dedup key (with line)
  "supplierId": "SUP-0043",
  "destinationWarehouseId": "WH-SEOUL-01",   // ★ addressed, not assumed — see D3
  "destinationNodeType": "WMS_WAREHOUSE",    // v1 only value accepted; see D4
  "expectedArrivalDate": "2026-07-24",       // from sku_supplier_map.lead_time_days
  "currency": "KRW",
  "lines": [
    { "skuCode": "SKU-A", "expectedQty": 100, "uom": "EA" }
  ]
}
```

> **Rejected alternative:** scm calls a wms REST endpoint synchronously. That couples scm's PO-confirm transaction to wms availability and inverts the async cross-project boundary `platform/service-boundaries.md` prescribes. The confirmed-PO fact-event is the correct seam — exactly the reasoning 027 §D1 used in the opposite direction.

### D2 — Trigger point: PO `CONFIRMED` (supplier-acknowledged), not `SUBMITTED` (chosen)

The event fires when the PO transitions to `CONFIRMED` (supplier has acknowledged the order), not on `SUBMITTED`.

| Option | Verdict |
|---|---|
| **D2-a (chosen)** Fire on `CONFIRMED`. | The arrival is real once the supplier acks; avoids ghost expectations from POs the supplier rejects/ignores. Slightly later but accurate. |
| D2-b Fire on `SUBMITTED`. | Rejected — a submitted-but-unacked PO may never ship; wms would hold a phantom expectation needing cleanup. |

In the portfolio (no live supplier), the `SUBMITTED → CONFIRMED` transition is operator-driven / simulated; the event binds to `CONFIRMED` regardless of how that state is reached, so the design is production-shaped.

### D3 — Warehouse-addressed routing: single AND multi warehouse on one code path (user requirement)

The destination warehouse is **carried in the event** (`destinationWarehouseId`), never assumed. Consequences:

- **Single-warehouse deployment** = the degenerate case: every event carries the same `destinationWarehouseId`. No special-casing.
- **Multi-warehouse deployment** = wms `inbound-service` resolves the expectation **under the addressed warehouse**; different POs route to different warehouses with zero code change. wms `master-service` already models `warehouse` as master data, so the destination is a valid resolvable reference.
- scm sources `destinationWarehouseId` from the `warehouseId` that seeded the reorder suggestion (027: the wms low-stock alert names the warehouse whose stock dropped) — so replenishment naturally targets the warehouse that ran low. A future scm distribution policy (which warehouse to replenish when several qualify) is an scm-side concern and does not change this contract.
- wms validation: an unknown/inactive `destinationWarehouseId` → reject to DLT + ops alert (fail-closed), never silently create an orphan expectation.

This is the decision that satisfies "단일창고도 멀티창고도 가능하도록": addressing (not assumption) makes warehouse-count a deployment fact, not a code branch.

### D4 — 3PL destination: out of scope for v1 (own warehouses only)

`destinationNodeType` is included for forward-compatibility, but **v1 accepts only `WMS_WAREHOUSE`**. A PO whose destination is a `THIRD_PARTY_LOGISTICS` node is **not** turned into a wms inbound expectation, because a 3PL warehouse is operated by the 3PL's own WMS — wms `inbound-service` does not manage its receiving. Rationale mirrors the existing project stance: 3PL inventory is modelled as an scm `inventory-visibility` node type (`THIRD_PARTY_LOGISTICS`) with **no active adapter** (v1), and carrier/3PL execution lives in scm's v2-deferred `logistics-service`.

- v1 behaviour for a 3PL-typed destination: scm does not emit the event for it (filtered producer-side); if one arrives at wms with `destinationNodeType != WMS_WAREHOUSE`, wms rejects it to DLT (defensive).
- v2 upgrade path: when a 3PL adapter exists, the inbound-expected for a 3PL destination routes to that adapter / to inventory-visibility, not to wms inbound. The contract field is already present, so no schema break.

### D5 — wms side: create an `InboundExpectation` (ASN), then reuse the existing inbound flow

wms `inbound-service` on consuming the event:

1. `eventId` dedup (D6.1) → warehouse resolve + `destinationNodeType` check (D3/D4) → business dedup (D6.2).
2. Create an **`InboundExpectation`** record: status `EXPECTED`, `source=SCM_PROCUREMENT`, preserving `poNumber` / `poId` (traceability back to scm), `supplierId`, `expectedArrivalDate`, and the lines.
3. **Downstream is the existing wms inbound flow, unchanged**: physical arrival → 검수(receiving) → 적치(putaway) → stock reflect → existing `wms.inventory.received.v1` emitted (which scm inventory-visibility already consumes, closing the visible loop).

The only genuinely new wms code is step 1–2 (event → expectation). Receiving/putaway/discrepancy handling already exists and is untouched — including partial receipt (received qty < expected), which wms's existing discrepancy path handles.

### D6 — Idempotency, cancellation, amendment

1. **Event dedup** — `eventId` (UUID v7) in wms `processed_events`; redelivery is a no-op.
2. **Business dedup** — even across distinct `eventId`s, wms must not create a second open expectation for the same `(poNumber, line)` while one is still open (`EXPECTED` / partially received). The `poNumber+line` is the business key.
3. **Cancellation** — a PO cancelled/withdrawn *after* the expectation exists would strand a phantom expectation. v1 **includes** a companion event `scm.procurement.inbound-expected.cancelled.v1` (same topic family) so wms can mark the expectation `CANCELLED` if not yet received. (Emitting it is cheap on the scm side — it already owns the PO-cancel transition.)
4. **Quantity amendment** — changing an already-confirmed PO's quantity is **v2-deferred**. v1 rule: an amendment is modelled as cancel + new confirmed PO (two events), not an in-place mutation. Recorded as a known limitation.

### D7 — Contract ownership & the one wms-side doc back-reference

- Authoritative schema: scm `projects/scm-platform/specs/contracts/events/scm-procurement-events.md` gains `inbound-expected.v1` (+ `.cancelled.v1`).
- Consumer-driven subscription doc: wms `projects/wms-platform/specs/contracts/events/scm-inbound-expected-subscriptions.md` reproduces only the consumed subset and defers the authoritative payload to scm. This is the mirror of 027's `replenishment-subscriptions.md`, roles reversed.
- scm `scm-procurement-events.md` gains **one line** naming wms `inbound-service` as a sanctioned cross-project consumer (documentation parity).

### D8 — Standalone-publish degradation (no hard dependency)

- **wms without scm**: the topic never arrives; the manual ASN path (unchanged) remains the only inbound-expectation source. No hard dependency.
- **scm without wms**: scm publishes to a topic no one consumes — harmless (same posture as any published event without a subscriber). The operator-facing procurement flow is unaffected.

Same non-coupling posture as the 027 and inventory-visibility precedents.

---

## 3. Implementation plan (tasks — start only after ACCEPTED)

Numbered against the live queues (root MONO max 429; scm SCM-BE max 033 / SCM-INT max 003; wms TASK-BE max 505).

**Phase 0 — decision (sequential gate)**

| Task | Queue | Content |
|---|---|---|
| TASK-MONO-430 | root | Author this ADR PROPOSED (this file). |
| TASK-MONO-431 | root | ADR-MONO-050 PROPOSED → ACCEPTED transition (**after user review** — not self-ACCEPT). Unblocks everything below. |

**Phase 1 — contract + impl (parallel across projects once ACCEPTED)**

The scm lane and the wms lane touch **disjoint file sets** (scm specs/apps vs wms specs/apps) with **no shared file** — the only cross-reference is the one doc line in D7, done once. They run in **separate worktrees, concurrently**.

| Task | Queue | Lane | Content | Depends on |
|---|---|---|---|---|
| TASK-SCM-BE-034 | scm | scm | `scm-procurement-events.md` — author `inbound-expected.v1` + `.cancelled.v1` authoritative schema; procurement `architecture.md` additive publish-on-CONFIRMED spec. | ACCEPTED |
| TASK-BE-506 | wms | wms | `scm-inbound-expected-subscriptions.md` consumer-driven contract; inbound-service `architecture.md` additive consumer + `InboundExpectation(source=SCM_PROCUREMENT)` spec. | ACCEPTED (schema shape fixed by this ADR §D1) |
| TASK-SCM-BE-035 | scm | scm | Impl: procurement publishes `inbound-expected` (+cancel) on PO `CONFIRMED`/cancel via outbox; 3PL-destination producer filter (D4). | SCM-BE-034 |
| TASK-BE-507 | wms | wms | Impl: inbound-service consumer → warehouse-addressed `InboundExpectation` (D3), eventId+`(poNumber,line)` dedup (D6), warehouse/nodeType fail-closed, cancel handling. Flyway. | BE-506 |

Within each lane the two tasks are sequential (contract → impl); **across lanes they are fully parallel**. Wall-clock ≈ one lane, not two.

**Phase 2 — proof (rejoin — needs both lanes)**

| Task | Queue | Content |
|---|---|---|
| TASK-SCM-INT-004 | scm | Testcontainers cross-service E2E: scm PO `CONFIRMED` → `inbound-expected.v1` → wms `InboundExpectation` appears (happy path, eventId idempotency, `(poNumber,line)` business-dedup, unknown-warehouse fail-closed, 3PL-destination reject, cancel path). Then wire the live leg into `tests/federation-hardening-e2e` (root) — real scm emits, real wms reacts — as the nightly federation proof. Add the PR-gated deterministic Testcontainers leg as the authoritative guard (federation-e2e is nightly, can regress silently — per the 027 §5 fed-e2e caveat). |

PR shape per each project's `tasks/INDEX.md`: spec PR ↔ impl PR ↔ chore PR separation.

---

## 4. Alternatives considered

- **Fire on `SUBMITTED`** (D2-b) — rejected: phantom expectations for unacked POs.
- **scm → wms synchronous REST** — rejected: couples scm's PO-confirm TX to wms uptime; inverts the async boundary. The confirmed-PO fact-event is the right seam.
- **Route the inbound-expected through erp as a hub** — rejected: erp-platform explicitly holds **no domain logic** (E5) and does not own the PO or inventory ledger; inserting a domain-blind service as a router is a nano-hop with no authority. (Discussed at length in the originating conversation — erp is a read-only projection, not a transaction hub.)
- **Assume a single hard-coded destination warehouse** — rejected: violates the user's single-AND-multi requirement; warehouse-addressing (D3) makes count a deployment fact.
- **Close the loop by having wms poll scm procurement** — rejected: pull inverts the freshness/ownership model and adds latency; the outbox push is the established pattern.

---

## 5. Consequences

**Positive**
- Closes the replenishment loop end-to-end: sell → ship → low stock → reorder → **confirmed PO → wms inbound expectation → receive → stock reflected → scm visibility updated**. Complements 027 (which built up to the PO) and 022 (the forward fulfillment loop).
- Receiving is no longer a blind manual entry — the expectation is pre-staged with PO traceability.
- Warehouse-addressing (D3) supports single and multi warehouse on one path; no scaling rework later.
- Minimal new code: scm = one outbox emit on an existing state transition; wms = one consumer creating an expectation, reusing the entire existing inbound flow.

**Negative / risk**
- **First `scm → wms` runtime coupling** — the wms↔scm relationship becomes bidirectional. Async + standalone-degradation (D8) keep it loose, but it is a genuine new dependency edge to operate and monitor.
- Cancellation correctness (D6.3) depends on the companion cancel event actually firing on every PO-cancel path — must be tested explicitly.
- Quantity amendment deferred (D6.4) — cancel+re-create is a coarser model than real ERPs; recorded as tech-debt.
- 3PL destinations unsupported (D4) — acceptable given no 3PL adapter exists, but means the loop only closes for own warehouses in v1.

**Neutral**
- scm `PROJECT.md` traits unchanged (`transactional` + `integration-heavy` already cover it). wms `PROJECT.md` traits unchanged (`transactional` + `integration-heavy`). No new domain/trait declaration on either side.
- No change to ecommerce or to 022/027's existing loops.

---

## 6. Status history

- **2026-07-19 PROPOSED** (TASK-MONO-430) — authored for user review of §2 (D1 event transport, D2 fire-on-CONFIRMED, D3 warehouse-addressed single+multi, D4 3PL-out-of-scope, D5 wms InboundExpectation reuse, D6 idempotency/cancel/amend, D7 contract ownership, D8 standalone degradation).
- **2026-07-19 ACCEPTED** (TASK-MONO-431) — user reviewed the PROPOSED document and gave explicit ADR-naming intent **"ADR-MONO-050 ACCEPTED"**, accepting the §2 decisions as authored. **NOT self-ACCEPT**: the user directed the transition (a bare "진행" was correctly rejected first per `platform/architecture-decision-rule.md` § The ACCEPTED Gate — the exact-form, ADR-naming intent arrived on the next turn). Phase 1 parallel tasks (SCM-BE-034/035 ∥ BE-506/507) and Phase 2 (SCM-INT-004) are now unblocked.

---

## 7. Amendment — D9 identifier semantics (post-Phase-1 reconciliation, user-directed 2026-07-19)

Phase 1 built the two lanes in parallel and each was internally consistent + tested, but a cross-lane reconciliation pass found the §D1 payload **under-specified identifier semantics**, so the lanes diverged and the loop would not have worked end-to-end (every event would fail-closed to DLT). Three mismatches:

1. **`expectedQty` string vs int** — scm emits a decimal string (`"100"`, `toPlainString()`, consistent with `totalAmount`); wms parsed it as a JSON number (`canConvertToInt()` → false on a text node → reject → DLT).
2. **`destinationWarehouseId` UUID vs code** — scm carried a **UUID** (worse: the wms low-stock alert delivers a **location** id, which scm demand-planning had aliased as the warehouse dimension), while wms resolves the destination by **warehouse code** (`findWarehouseByCode`).
3. **`supplierId` scm-ref vs wms partner code** — scm's `sku_supplier_map.supplier_id` is an scm-side reference; wms resolves suppliers against **its own** partner master by code.

Root cause: the boundary payload named fields but not whether they are **codes** (namespace-shareable, human-stable) or **system-internal UUIDs**, and it assumed a clean `warehouseId` flows from the alert when the alert actually carries a location.

### D9 (user-directed — Option A) — identifiers cross the boundary as CODES; the low-stock alert carries a warehouse code

- **All cross-service identifiers in `scm.procurement.inbound-expected.v1` are CODES**: `destinationWarehouseId` = warehouse **code**, `supplierId` = supplier **code** (resolvable in wms's partner master), `skuCode` = SKU code (already aligned). `expectedQty` stays a **decimal string** (scm authoritative); **wms parses the string** (the smaller fix).
- **`wms.inventory.alert.v1` is extended (additive) to carry `warehouseCode`** — the warehouse the low-stock inventory belongs to (resolved inventory → location → warehouse in wms `inventory-service`). scm demand-planning consumes `warehouseCode` and propagates it as the warehouse dimension (replacing the `locationId`-as-`warehouseId` alias) through `ReorderSuggestion` → PO → the inbound-expected event. This is an **additive** field on the alert (backward-compatible; no ADR-MONO-027 decision reversed — the alert's authoritative schema in wms `inventory-events.md` §7 gains one field).
- **Supplier code alignment**: scm's `sku_supplier_map.supplier_id` must be seeded with the supplier **code** wms's partner master knows. The SCM-INT-004 E2E seeds a shared supplier code across both sides as the forcing function.

Reconciliation tasks: **TASK-BE-508** (wms — alert `warehouseCode` extension: inventory-service producer + `inventory-events.md` §7) + refinements to the existing SCM-BE-035 (emit warehouse code) / BE-507 (parse decimal-string `expectedQty`; already resolves by code) / demand-planning warehouseCode propagation. Attribution: user chose Option A via AskUserQuestion on 2026-07-19; this amendment records that decision (not self-authored).
