# inbound-service — Inbound Workflow

End-to-end inbound lifecycle narrative. Required artifact per
`rules/domains/wms.md` § Required Artifacts (3) "입고 워크플로".

This document complements `architecture.md` and `state-machines/asn-status.md`
by walking the **happy path** and the most common deviations through every
service involved (master, gateway, inbound, inventory, admin, notification).

---

## Actors

| Actor | Role | Identifier in events |
|---|---|---|
| **ERP** | External — emits ASN webhooks | `system:erp-webhook` |
| **Operator** | Human — manual ASN entry, inspection, putaway confirmation | JWT `sub` (UUID) |
| **inbound-service** | This service | `inbound-service` (event `producer`) |
| **inventory-service** | Receives `inbound.putaway.completed` | `inventory-service` |
| **master-service** | Authoritative source for Partner / Location / SKU / Lot | `master-service` |
| **admin-service** | Read-only KPI projections | `admin-service` |
| **notification-service** | Optional alerting on discrepancies | `notification-service` |

---

## Phase 1 — ASN Reception

Two entry paths produce an ASN in `CREATED` state. The downstream lifecycle
is identical from this point.

### 1a. ERP Webhook Path

```
ERP                gateway-service          inbound-service             Postgres
 │                       │                        │                        │
 │── POST /webhooks/erp/asn ────────────────────▶ │                        │
 │   (signed payload)    │ (HMAC-only route,      │                        │
 │                       │  no JWT, no rate-limit)│                        │
 │                       │                        │── verify HMAC ────────▶│
 │                       │                        │── verify timestamp ──▶ │
 │                       │                        │── insert erp_webhook_dedupe (event_id)
 │                       │                        │── insert erp_webhook_inbox (raw_payload, status=PENDING)
 │                       │                        │                        │
 │◀── 200 {accepted, eventId} ────────────────────│                        │
 │                       │                        │                        │

       (background processor, every 1s)
                                 │
                                 │── SELECT * FROM erp_webhook_inbox WHERE status=PENDING LIMIT 50
                                 │── for each: ReceiveAsnUseCase
                                 │      ├─ resolve supplier_partner from MasterReadModel
                                 │      ├─ create Asn (status=CREATED) + AsnLines
                                 │      ├─ write outbox: inbound.asn.received
                                 │      └─ update erp_webhook_inbox.status=APPLIED
                                 ▼
                          Kafka: wms.inbound.asn.received.v1
```

**Why ack-then-process**: ERP HTTP timeout is short (~10s) and ERP retries
aggressively on timeout. Persisting raw payload + acking immediately decouples
ingestion latency from domain validation. If domain validation fails, the row
moves to `status=FAILED` with `failure_reason` populated; ops sees it on the
admin dashboard.

### 1b. Manual Path

```
Operator             gateway-service          inbound-service          Postgres
 │                       │                        │                        │
 │── POST /api/v1/inbound/asns ──────────────────▶│                        │
 │   (Bearer JWT, Idempotency-Key)                │                        │
 │                       │── JWT decode ─────────▶│                        │
 │                       │── X-User-Id, X-User-Role headers                │
 │                       │                        │── ReceiveAsnUseCase    │
 │                       │                        │      ├─ idempotency check (Redis)
 │                       │                        │      ├─ validate supplier (MasterReadModel)
 │                       │                        │      ├─ validate SKU(s) ACTIVE (MasterReadModel)
 │                       │                        │      ├─ insert Asn + AsnLines
 │                       │                        │      └─ insert outbox: inbound.asn.received
 │◀── 201 {asnId, asnNo, status=CREATED} ─────────│                        │
 │                       │                        │                        │

                          Kafka: wms.inbound.asn.received.v1
```

**Differences from webhook path**:

- Synchronous validation: invalid input returns 422 immediately to the operator.
- No `erp_webhook_inbox` row written (no separation of ingest vs. domain).
- `actorId = JWT.sub` (operator id), `source = MANUAL`.

---

## Phase 2 — Inspection

Triggered when goods physically arrive at the receiving dock. The operator
opens the ASN, presses "Start Inspection", inspects each line, and submits
results.

```
Operator                gateway-service           inbound-service          Postgres
 │                            │                          │                      │
 │── POST /api/v1/inbound/asns/{id}/inspection:start ───▶│                      │
 │                            │                          │── Asn.startInspection()
 │                            │                          │   (CREATED → INSPECTING)
 │◀── 200 {asn.status=INSPECTING} ──────────────────────│                      │
 │                            │                          │                      │
 │   (operator inspects physical goods at dock,          │                      │
 │    counts pass/damaged/short per line)                │                      │
 │                            │                          │                      │
 │── POST /api/v1/inbound/asns/{id}/inspection ─────────▶│                      │
 │   {lines: [{asnLineId, qtyPassed, qtyDamaged,         │                      │
 │             qtyShort, lotNo (if LOT-tracked)}, ...]}  │                      │
 │                            │                          │── RecordInspectionUseCase
 │                            │                          │      ├─ validate sums ≤ expected
 │                            │                          │      ├─ resolve lot_id from MasterReadModel
 │                            │                          │      │  (if lot_no provided)
 │                            │                          │      ├─ create Inspection + InspectionLines
 │                            │                          │      ├─ create InspectionDiscrepancy where actual < expected
 │                            │                          │      ├─ Asn.completeInspection()
 │                            │                          │      │   (INSPECTING → INSPECTED)
 │                            │                          │      └─ outbox: inbound.inspection.completed
 │◀── 201 {inspectionId, lines, discrepancies} ─────────│                      │

                              Kafka: wms.inbound.inspection.completed.v1
```

**Discrepancy handling** (in-line):

- `qtyPassed + qtyDamaged + qtyShort > expectedQty` → reject with
  `INSPECTION_QUANTITY_MISMATCH` (422), no Inspection saved.
- `qtyPassed + qtyDamaged + qtyShort < expectedQty` → accept; create
  `InspectionDiscrepancy(type=QUANTITY_MISMATCH, acknowledged=false)`.
- LOT-tracked SKU with `lot_id = null` and `lot_no = null` → reject with
  `LOT_REQUIRED` (422).

**Discrepancy resolution** (separate call before close):

```
Operator
 │── POST /api/v1/inbound/inspections/{id}/discrepancies/{discrepancyId}:acknowledge ─▶
 │     {notes: "공급사와 confirm 완료 — 단순 누락. 차회 입고 시 보충 합의"}
 │      → marks discrepancy.acknowledged=true, ASN may now close
```

`completeInspection` checks all `InspectionDiscrepancy` rows are acknowledged
before allowing the transition; otherwise → `INSPECTION_INCOMPLETE` (422).

---

## Phase 3 — Putaway Instruction

A planner (in v1: ops manual; in v2: auto-planner adapter) assigns destination
locations for the inspected-pass quantities.

```
Operator                gateway-service           inbound-service       master-service-readmodel
 │                            │                          │                      │
 │── POST /api/v1/inbound/asns/{id}/putaway:instruct ───▶│                      │
 │   {lines: [{asnLineId, destinationLocationId,         │                      │
 │             qtyToPutaway}, ...]}                      │                      │
 │                            │                          │── InstructPutawayUseCase
 │                            │                          │      ├─ for each line: resolve Location from MasterReadModel
 │                            │                          │      │     ├─ status == ACTIVE? else LOCATION_INACTIVE
 │                            │                          │      │     └─ warehouse_id matches ASN? else WAREHOUSE_MISMATCH
 │                            │                          │      ├─ qtyToPutaway ≤ inspectionLine.qtyPassed
 │                            │                          │      │     else PUTAWAY_QUANTITY_EXCEEDED
 │                            │                          │      ├─ create PutawayInstruction (status=PENDING) + PutawayLines
 │                            │                          │      ├─ Asn.instructPutaway()
 │                            │                          │      │   (INSPECTED → IN_PUTAWAY)
 │                            │                          │      └─ outbox: inbound.putaway.instructed
 │◀── 201 {putawayInstructionId, lines, status=PENDING} ─│                      │

                              Kafka: wms.inbound.putaway.instructed.v1
```

`inbound.putaway.instructed` is an **operational signal** — picked up by
admin-service to update operator dashboards. inventory-service does NOT
consume it.

**Lines that cannot be planned**: if not every `inspectionLine.qtyPassed` has
a destination, the operator may issue a partial `PutawayInstruction` and add
remaining lines later via a follow-up `PATCH /putaway/{id}` (v2). v1 requires
all pass-quantity to be assigned in one call (or remainder skipped).

**Damaged-bucket destinations**: `qtyDamaged` is putaway to a `DAMAGED`-type
location separately. The instruction MAY include damaged-line entries pointing
at `location_type = DAMAGED`. v1 advisory only — no hard guard.

---

## Phase 4 — Putaway Confirmation

The operator (or future scanner adapter) physically places goods at locations
and confirms each one.

```
Operator                gateway-service           inbound-service          inventory-service
 │                            │                          │                          │
 │── POST /api/v1/inbound/putaway/{instructionId}/lines/{lineId}:confirm ──▶│        │
 │   {actualLocationId (may differ from planned),        │                          │
 │    qtyConfirmed}                                      │                          │
 │                            │                          │── ConfirmPutawayUseCase  │
 │                            │                          │     ├─ verify actualLocation ACTIVE + same warehouse
 │                            │                          │     ├─ qtyConfirmed == putawayLine.qtyToPutaway (v1 no partial)
 │                            │                          │     ├─ insert PutawayConfirmation
 │                            │                          │     ├─ PutawayLine.status = CONFIRMED
 │                            │                          │     ├─ if last line: PutawayInstruction.status = COMPLETED
 │                            │                          │     │                  + Asn.completePutaway()
 │                            │                          │     │                  + outbox: inbound.putaway.completed
 │                            │                          │     └─ else: no outbox (instruction still IN_PROGRESS)
 │◀── 200 {confirmationId, instruction.status=...} ─────│                          │
                              │                          │                          │
                              Kafka: wms.inbound.putaway.completed.v1               │
                                                                  │                  │
                                                                  ▼                  │
                                                          PutawayCompletedConsumer  │
                                                          ├─ EventDedupePort.process │
                                                          ├─ for each line:          │
                                                          │   ReceiveStockUseCase   │
                                                          │   (master-ref validation │
                                                          │    via MasterReadModel) │
                                                          ├─ Inventory.receive(qty) │
                                                          │   creates row if absent │
                                                          ├─ InventoryMovement(W2) │
                                                          └─ outbox: inventory.received
```

**Cross-service contract**: `wms.inbound.putaway.completed.v1` is the **only**
event in the inbound→inventory direction. inventory-service treats
`qtyReceived` per `(locationId, skuId, lotId)` as authoritative. The event
carries every line of the entire instruction in one envelope so that
`ReceiveStockUseCase` is one logical receive, even if confirmation arrived
line-by-line.

**Line-by-line vs. batch confirmation**: each REST `:confirm` call confirms
exactly one PutawayLine. The outbox fires once when the LAST line of the
instruction transitions to `CONFIRMED`/`SKIPPED`. The fired event carries all
lines (full state snapshot).

**Skipping a line**:

```
Operator
 │── POST /api/v1/inbound/putaway/{instructionId}/lines/{lineId}:skip ─▶
 │     {reason: "Location WH01-A-01-01-01 lift broken — handled in TASK-OPS-1234"}
 │      → PutawayLine.status = SKIPPED (no PutawayConfirmation written)
```

Skipped lines do NOT contribute to `inbound.putaway.completed` payload. If all
remaining lines are SKIPPED, the instruction transitions to
`PARTIALLY_COMPLETED` and the event still fires (with the confirmed lines
only).

---

## Phase 5 — ASN Close

After putaway is done, an authorised actor closes the ASN.

```
Operator (INBOUND_WRITE/ADMIN)    gateway-service       inbound-service
 │                                       │                       │
 │── POST /api/v1/inbound/asns/{id}:close ──────────────────────▶│
 │                                       │                       │── CloseAsnUseCase
 │                                       │                       │     ├─ verify Asn.status == PUTAWAY_DONE
 │                                       │                       │     │   else STATE_TRANSITION_INVALID
 │                                       │                       │     ├─ Asn.close()
 │                                       │                       │     │   (PUTAWAY_DONE → CLOSED)
 │                                       │                       │     └─ outbox: inbound.asn.closed
 │◀── 200 {asn.status=CLOSED, closedAt} ─────────────────────────│
                                         │                       │
                                         Kafka: wms.inbound.asn.closed.v1
```

`inbound.asn.closed` is mainly for admin-service KPI projection (cycle-time
calculation: `closedAt - asn.createdAt`). inventory-service ignores it.

---

## Phase 6 — Cancellation (Off-Path)

```
Operator (INBOUND_ADMIN)         inbound-service
 │                                       │
 │── POST /api/v1/inbound/asns/{id}:cancel ─▶│
 │   {reason: "공급사 출하 취소 통보 — 차주 재발송"}
 │                                       │── CancelAsnUseCase
 │                                       │     ├─ verify Asn.status ∈ {CREATED, INSPECTING}
 │                                       │     │   else ASN_ALREADY_CLOSED (NOT STATE_TRANSITION_INVALID)
 │                                       │     ├─ Asn.cancel(reason)
 │                                       │     │   (* → CANCELLED)
 │                                       │     └─ outbox: inbound.asn.cancelled
 │◀── 200 {asn.status=CANCELLED} ────────│
                                         │
                                         Kafka: wms.inbound.asn.cancelled.v1
```

Cancellation is **forbidden** once `IN_PUTAWAY` is entered (W1 ledger
integrity — putaway has already touched inventory). To correct an
incorrectly-received ASN at that point, ops creates an
`inventory-service` adjustment after-the-fact.

---

## Failure Modes (per `rules/traits/transactional` Required Artifact 5)

| Scenario | Behavior |
|---|---|
| Webhook signature invalid | 401, no row written to inbox or dedupe |
| Webhook event-id duplicate | 200 `{status: ignored_duplicate}`, single domain effect |
| Two operators race on same ASN inspection | 1st commits; 2nd hits `OptimisticLockingFailureException` → 409 `CONFLICT`. Caller refetches and decides |
| Operator submits inspection summing > expected | 422 `INSPECTION_QUANTITY_MISMATCH`, nothing saved |
| Putaway destination deactivated mid-flow | 422 `LOCATION_INACTIVE` at confirm time. Operator picks alternate location; partial putaway is acceptable |
| `master-service` snapshot stale (newer master event not yet consumed) | Best-effort — guard runs against current snapshot. Newer master state catches up and would have rejected the operation; v1 accepts the brief window per W6 (local-only) |
| inventory-service down when `inbound.putaway.completed` published | Event sits on Kafka topic; inventory-service catches up on restart. Outbox already delivered — inbound TX is committed |
| ERP webhook arrives after operator manually creates same ASN (different `eventId`, same `asnNo`) | First commit wins (`asnNo` unique constraint); second errors with 409 `CONFLICT`. Webhook inbox row marked FAILED with reason; ops investigates |

---

## Sequence — Happy Path Cycle Time Reference

For SLO baseline (admin-service `inbound.asn.cycle.time.seconds` p50 target):

| Step | Typical duration |
|---|---|
| ASN reception → goods arrive at dock | hours (external — supplier transport) |
| Inspection start → inspection submit | 5–15 min per ASN |
| Inspection submit → putaway instructed | 1–5 min (planner reviews) |
| Putaway instructed → all lines confirmed | 10–60 min (operator walking aisles) |
| Putaway done → close | seconds (auto-close planned for v2) |
| **End-to-end (excluding transit)** | typically 30–90 min |

Inbound-service contributes ~5 min of compute / IO; the rest is human time.

---

## Workflow Invariants (cross-phase)

| Invariant | Phase | Source |
|---|---|---|
| Every ASN has a unique `asnNo` | reception | architecture.md `asn_no unique` |
| AsnLines immutable from `INSPECTING` onwards | inspection | domain-model.md §1 |
| `qtyPassed + qtyDamaged + qtyShort ≤ expectedQty` | inspection | domain-model.md §2 |
| Total `qtyToPutaway` per AsnLine ≤ `qtyPassed` | putaway | domain-model.md §3 |
| `qtyConfirmed == putawayLine.qtyToPutaway` (v1 no partial) | confirmation | domain-model.md §4 |
| Cancellation only from `CREATED`/`INSPECTING` | any | state-machines/asn-status.md |
| One outbox row per use-case `@Transactional` | every | architecture.md § Persistence |

---

## Out of Scope (v1)

- Returns / RMA inbound flow
- Cross-warehouse split-receiving (multi-warehouse ASN)
- Auto-putaway planner (FEFO / velocity-driven destination assignment)
- Scanner / RFID line-confirm adapter
- ERP outbound ack push (we don't notify ERP we received their ASN; they poll
  `GET /asns?status=CLOSED`)
- Reverse putaway (un-confirming a `PutawayLine`)
- Partial confirmation per PutawayLine

---

## References

- `architecture.md` § Inbound Workflow + § Webhook Reception
- `domain-model.md` — entity field details
- `state-machines/asn-status.md` — formal transition table
- `external-integrations.md` — ERP-side timeouts, circuit breakers (Open Item)
- `idempotency.md` — REST + webhook idempotency strategy (Open Item)
- `specs/contracts/http/inbound-service-api.md` — REST endpoint shapes (Open Item)
- `specs/contracts/webhooks/erp-asn-webhook.md` — webhook payload + headers (Open Item)
- `specs/contracts/events/inbound-events.md` — outbox event schemas (Open Item)
- `specs/services/inventory-service/architecture.md` — counterpart consumer
- `rules/domains/wms.md` — Inbound bounded context, W1, W2, W6
- `rules/traits/transactional.md` — T1, T3, T4, T5, T8
- `rules/traits/integration-heavy.md` — I3, I5, I6, I10
