# TASK-BE-031 — inbound-service: Putaway Lifecycle + ASN Close

| Field | Value |
|---|---|
| **Task ID** | TASK-BE-031 |
| **Title** | inbound-service: Putaway Instruction + Confirm/Skip + ASN Close |
| **Status** | review |
| **Owner** | backend |
| **Tags** | inbound, putaway, state-machine, cross-service, event |

---

## Goal

Implement Phases 3–5 of the inbound flow:

- **Phase 3 — Putaway Instruction**: planner issues a `PutawayInstruction` assigning destination locations per ASN line.
- **Phase 4 — Putaway Confirmation / Skip**: operators confirm each line (actual placement) or skip it (location unavailable). When the last line is resolved, the ASN transitions to `PUTAWAY_DONE` and the cross-service event `inbound.putaway.completed` is emitted to drive `inventory-service` stock receipt.
- **Phase 5 — ASN Close**: finalise the ASN ledger (`PUTAWAY_DONE → CLOSED`) and emit `inbound.asn.closed`.

Database tables (`putaway_instruction`, `putaway_line`, `putaway_confirmation`) are already created by **V4 Flyway migration** — no new migration is required.

---

## Scope

### In scope

**Domain layer (`domain/model/`, `domain/event/`)**

- `PutawayInstruction.java` — aggregate root
  - Fields: `id`, `asnId`, `status` (PENDING / IN_PROGRESS / COMPLETED / PARTIALLY_COMPLETED), `plannedBy`, `version`, `createdAt`, `updatedAt`
  - `instructPutaway()` — factory / initial state
  - `confirmLine(lineId)` — marks line CONFIRMED; if last line → status COMPLETED; returns `isLastLine`
  - `skipLine(lineId)` — marks line SKIPPED; if last line → status COMPLETED or PARTIALLY_COMPLETED; returns `isLastLine`
- `PutawayLine.java` — child entity
  - Fields: `id`, `putawayInstructionId`, `asnLineId`, `skuId`, `lotId`, `lotNo`, `destinationLocationId`, `qtyToPutaway`, `status` (PENDING / CONFIRMED / SKIPPED), `createdAt`, `updatedAt`
- `PutawayConfirmation.java` — append-only value record
  - Fields: `id`, `putawayLineId`, `plannedLocationId`, `actualLocationId`, `qtyConfirmed`, `confirmedBy`, `confirmedAt`
- Add to `Asn.java`:
  - `instructPutaway()` — INSPECTED → IN_PUTAWAY; throws `StateTransitionInvalidException` otherwise
  - `completePutaway()` — IN_PUTAWAY → PUTAWAY_DONE; throws `StateTransitionInvalidException` otherwise
  - `close()` — PUTAWAY_DONE → CLOSED; throws `StateTransitionInvalidException` otherwise
- Domain events (records):
  - `PutawayInstructedEvent` — carries `putawayInstructionId`, `asnId`, `warehouseId`, `plannedBy`, `lines[]`
  - `PutawayCompletedEvent` — carries `putawayInstructionId`, `asnId`, `warehouseId`, `completedAt`, `confirmedLines[]` (SKIPPED lines excluded)
  - `AsnClosedEvent` — carries `asnId`, `asnNo`, `warehouseId`, `closedAt`, `closedBy`, `summary{...}`

**Application layer (`application/port/`, `application/service/`, `application/command/`, `application/result/`)**

- In-ports (use-case interfaces):
  - `InstructPutawayUseCase` + `InstructPutawayCommand`
  - `ConfirmPutawayLineUseCase` + `ConfirmPutawayLineCommand`
  - `SkipPutawayLineUseCase` + `SkipPutawayLineCommand`
  - `CloseAsnUseCase` + `CloseAsnCommand`
  - `GetPutawayInstructionUseCase`
- Out-port: `PutawayPersistencePort`
  - `save(PutawayInstruction)`, `findByIdOrThrow(id)`, `findByAsnIdOrThrow(asnId)`, `findLineByIdOrThrow(lineId)`, `saveConfirmation(PutawayConfirmation)`, `findConfirmationByLineIdOrThrow(lineId)`
- Services:
  - `InstructPutawayService` — guard: Asn INSPECTED; no unacknowledged discrepancies; location ACTIVE + same warehouse; if SKU lot-tracked (`lotId != null` on SKU snapshot) then `AsnLine.lotId` must be set; `qtyToPutaway ≤ InspectionLine.qtyPassed`; creates PutawayInstruction + lines in single TX; calls `Asn.instructPutaway()`; outbox: `PutawayInstructedEvent`
  - `ConfirmPutawayLineService` — guard: line PENDING; actual location ACTIVE; creates `PutawayConfirmation`; calls `instruction.confirmLine(lineId)`; if last line: calls `asn.completePutaway()`, outbox: `PutawayCompletedEvent`; all in single TX with `Propagation.MANDATORY` on `InboundEventPort`
  - `SkipPutawayLineService` — guard: line PENDING; marks line SKIPPED (no confirmation row); calls `instruction.skipLine(lineId)`; if last line: calls `asn.completePutaway()`, outbox: `PutawayCompletedEvent` (only confirmed lines included); single TX
  - `CloseAsnService` — guard: Asn PUTAWAY_DONE; computes `summary` from Inspection + PutawayConfirmation aggregates; calls `asn.close()`; outbox: `AsnClosedEvent`; single TX
  - `PutawayQueryService` — `getByInstructionId`, `getByAsnId`, `getConfirmationByLineId`

**Adapter layer — out**

- JPA entities: `PutawayInstructionJpaEntity`, `PutawayLineJpaEntity`, `PutawayConfirmationJpaEntity` (package-private, `adapter/out/persistence/putaway/`)
- JPA repositories: `PutawayInstructionJpaRepository`, `PutawayLineJpaRepository`, `PutawayConfirmationJpaRepository`
- `PutawayPersistenceAdapter` (package-private mappers; `@Lock(PESSIMISTIC_WRITE)` on `findByIdOrThrow` for confirmation race)
- Update `InboundEventEnvelopeSerializer` to handle `PutawayInstructedEvent`, `PutawayCompletedEvent`, `AsnClosedEvent` matching the payload schemas in `specs/contracts/events/inbound-events.md` §4–§6

**Adapter layer — in**

- `PutawayController` (`adapter/in/rest/`) with 5 endpoints:
  - `POST /api/v1/inbound/asns/{asnId}/putaway:instruct` (INBOUND_WRITE)
  - `POST /api/v1/inbound/putaway/{instructionId}/lines/{lineId}:confirm` (INBOUND_WRITE)
  - `POST /api/v1/inbound/putaway/{instructionId}/lines/{lineId}:skip` (INBOUND_WRITE)
  - `GET /api/v1/inbound/putaway/{instructionId}` (INBOUND_READ)
  - `GET /api/v1/inbound/asns/{asnId}/putaway` (INBOUND_READ)
- Add to `AsnController`:
  - `POST /api/v1/inbound/asns/{id}:close` (INBOUND_WRITE)
- Add to `PutawayController` (or separate `PutawayLineController`):
  - `GET /api/v1/inbound/putaway/lines/{lineId}/confirmation` (INBOUND_READ)
- Request DTOs: `InstructPutawayRequest`, `ConfirmPutawayLineRequest`, `SkipPutawayLineRequest`, `CloseAsnRequest`
- Response DTOs: `PutawayInstructionResponse`, `PutawayLineResponse`, `PutawayConfirmationResponse`, `CloseAsnResponse`
- Add exception mappings to `GlobalExceptionHandler`:
  - `PUTAWAY_INSTRUCTION_NOT_FOUND` → 404
  - `PUTAWAY_LINE_NOT_FOUND` → 404
  - `PUTAWAY_QUANTITY_EXCEEDED` → 422
  - `LOT_REQUIRED` → 422 (new: SKU is lot-tracked but AsnLine has no lotId)
  - `WAREHOUSE_MISMATCH` → 422 (if not already mapped)

### Out of scope

- Reverse putaway / un-confirm (Not In v1)
- Partial confirmation (`qtyConfirmed < qtyToPutaway`) (Not In v1)
- Auto-putaway planner endpoint (Not In v1)
- New Flyway migrations (V4 already covers all tables)
- `admin-service` or `notification-service` consumers (separate services)

---

## Acceptance Criteria

1. `POST .../putaway:instruct` returns 201 with `PutawayInstructionResponse`; Asn status transitions to `IN_PUTAWAY`; outbox row written for `inbound.putaway.instructed`; idempotent on same `Idempotency-Key`.
2. `instructPutaway` fails with `STATE_TRANSITION_INVALID` (422) if Asn is not `INSPECTED`.
3. `instructPutaway` fails with `LOCATION_INACTIVE` (422) if any destination location snapshot is `INACTIVE`.
4. `instructPutaway` fails with `WAREHOUSE_MISMATCH` (422) if any location's warehouseId ≠ Asn.warehouseId.
5. `instructPutaway` fails with `PUTAWAY_QUANTITY_EXCEEDED` (422) if `line.qtyToPutaway > InspectionLine.qtyPassed` for any line.
6. `instructPutaway` fails with `LOT_REQUIRED` (422) if the SKU snapshot has `lotTracking=true` and the AsnLine has no `lotId`.
7. `POST .../lines/{lineId}:confirm` creates a `PutawayConfirmation` row; line status → `CONFIRMED`; returns 200 with confirmation details; idempotent.
8. Confirming the **last pending line** transitions PutawayInstruction → `COMPLETED` and Asn → `PUTAWAY_DONE`; outbox row written for `inbound.putaway.completed` (only confirmed lines in payload; SKIPPED lines absent).
9. `POST .../lines/{lineId}:skip` marks line `SKIPPED`; returns 200; skipping the last line triggers same `PUTAWAY_DONE` + `inbound.putaway.completed` as AC-8; skipped lines excluded from event payload.
10. `POST /asns/{id}:close` transitions Asn `PUTAWAY_DONE → CLOSED`; response includes ledger `summary`; outbox row for `inbound.asn.closed`; idempotent.
11. `close` fails with `STATE_TRANSITION_INVALID` (422) if Asn is not `PUTAWAY_DONE`.
12. All three new events (`putaway.instructed`, `putaway.completed`, `asn.closed`) serialise to the exact envelope + payload schema in `specs/contracts/events/inbound-events.md`; an `EventContractTest` assertion validates the shape.
13. `InboundEventEnvelopeSerializer` handles all six event types without a default `IllegalArgumentException` branch firing.
14. `PutawayCompletedEvent` payload `lines[]` contains only `CONFIRMED` lines. `SKIPPED` lines are absent regardless of whether `all-skipped` or `partial-skip` path was taken. If ALL lines are skipped the payload `lines[]` is empty — inventory-service treats this as a no-op.
15. Optimistic locking on `PutawayInstruction.version`: concurrent last-line confirmation attempts result in exactly one success and one `CONFLICT` (409); no duplicate `inbound.putaway.completed` outbox rows.
16. Role enforcement: `INBOUND_WRITE` required for instruct/confirm/skip/close; `INBOUND_READ` for GET endpoints; enforced in the application layer (command carries caller roles), not in controllers.
17. Unit tests: `PutawayInstruction` domain model (all transitions + guard failures), `InstructPutawayService`, `ConfirmPutawayLineService`, `SkipPutawayLineService`, `CloseAsnService` with fakes.
18. Integration test (`PutawayLifecycleIntegrationTest`): full golden-path from INSPECTED → putaway:instruct → confirm last line → verify `putaway.completed` emitted on Kafka → close → verify `asn.closed` emitted.
19. `@WebMvcTest` for `PutawayController` and the `:close` addition to `AsnController`: 201/200 happy paths, 422 guard failures, 409 idempotency replay.

---

## Related Specs

- `specs/services/inbound-service/architecture.md`
- `specs/services/inbound-service/domain-model.md` — PutawayInstruction / PutawayLine / PutawayConfirmation definitions
- `specs/services/inbound-service/state-machines/asn-status.md` — INSPECTED → IN_PUTAWAY → PUTAWAY_DONE → CLOSED transitions
- `specs/services/inbound-service/workflows/inbound-flow.md` — Phases 3–5 narrative
- `specs/services/inbound-service/idempotency.md` — REST Idempotency-Key strategy (reuse existing `RedisIdempotencyStore`)
- `specs/services/inbound-service/external-integrations.md` — Kafka producer guarantees

---

## Related Contracts

- `specs/contracts/http/inbound-service-api.md` §3 (putaway endpoints) + §1.5 (close)
- `specs/contracts/events/inbound-events.md` §4 (`inbound.putaway.instructed`), §5 (`inbound.putaway.completed` ⚠️ cross-service), §6 (`inbound.asn.closed`)

---

## Edge Cases

1. **All lines skipped**: `PutawayInstruction` → `PARTIALLY_COMPLETED`; `inbound.putaway.completed` fires with empty `lines[]`; `inventory-service` receives a no-op.
2. **Mixed confirm + skip**: last confirmed line fires the event; SKIPPED lines are absent from the payload.
3. **Stale location snapshot after instruction issued**: operator tries to confirm at an already-deactivated location → `LOCATION_INACTIVE` (422); the putaway instruction remains `IN_PROGRESS`.
4. **Optimistic lock race on last-line**: two threads both detect `isLastLine=true` and attempt to complete. The `PutawayInstruction.version` check ensures exactly one wins; loser gets `CONFLICT` (409). Only one `inbound.putaway.completed` outbox row is written.
5. **Idempotent confirm replay**: same `Idempotency-Key` on `:confirm` → returns cached 200 response; no second `PutawayConfirmation` row written; no second outbox row.
6. **`LOT_REQUIRED` guard**: if `SkuSnapshot.lotTracking == true` and `AsnLine.lotId == null`, `instructPutaway` fails immediately — prevents orphan inventory rows without a lot key.
7. **`PUTAWAY_QUANTITY_EXCEEDED`**: `qtyToPutaway` may equal `qtyPassed` (typical) or be less (partial early putaway). It must never exceed `qtyPassed`.
8. **Confirm with different actual location**: `actualLocationId` may differ from `destinationLocationId` (operator override). Both IDs are stored in `PutawayConfirmation`; `inbound.putaway.completed` uses `actualLocationId` (what matters for inventory stock placement).

---

## Failure Scenarios

1. **`InboundEventPort` throws** during `instructPutaway`: entire TX rolls back; no outbox row, no instruction persisted. Client receives 500; retry with same `Idempotency-Key` re-runs cleanly.
2. **`PutawayPersistencePort.save` fails** mid-confirm: TX rolls back; `PutawayConfirmation` not persisted; Kafka not touched; safe to retry.
3. **Redis unavailable** for idempotency check: `IdempotencyStore` fails-closed → 503 (per idempotency.md §Resilience). Instruction/confirm are not executed.
4. **Kafka producer outage** (outbox publisher, not in-TX): outbox row remains unpublished; publisher retries with exponential backoff up to SLA; no data loss.
5. **`completePutaway()` called on wrong Asn status** (defensive): `StateTransitionInvalidException` → 422; instruction state unchanged.
6. **Close called before all lines resolved**: `Asn.status` is still `IN_PUTAWAY` → `STATE_TRANSITION_INVALID` (422).

---

## Implementation Notes

- **No new Flyway migration needed.** `V4__init_putaway_tables.sql` already defines `putaway_instruction`, `putaway_line`, and `putaway_confirmation` with correct constraints.
- **`Propagation.MANDATORY` on `InboundEventPort.publish`** (already enforced in `OutboxWriterAdapter` from BE-030) — confirm/skip/close services must be `@Transactional` and call the event port within the same TX boundary.
- **`PutawayConfirmation` is append-only** — no UPDATE/DELETE issued against that table. The JPA entity must only support persist, never merge.
- **`PutawayCompletedEvent` `aggregateType = "putaway_instruction"`, `aggregateId = PutawayInstruction.id`** — matches envelope spec in `inbound-events.md` §5.
- **`AsnClosedEvent` `summary`** requires joining `Inspection` (for `qtyPassed/Damaged/Short`) and `PutawayConfirmation` (for `putawayConfirmedTotal`). Load these in `CloseAsnService` before calling `asn.close()`.
- **Role enforcement pattern**: follow BE-028 pattern — command record carries `Set<String> callerRoles`; use-case service checks the set; controller populates from `Authentication.getAuthorities()`. Do not authorise in the controller itself.
- **`PutawayPersistenceAdapter.findByIdOrThrow`** for the `PutawayInstruction` load during confirm/skip should use `@Lock(LockModeType.PESSIMISTIC_WRITE)` to prevent the last-line race at the DB level (supplement to optimistic lock on `version`).

---

## Target Service

`projects/wms-platform/apps/inbound-service`

## Architecture

Hexagonal (Ports & Adapters) — per `specs/services/inbound-service/architecture.md`.

## Test Requirements

- Unit tests with fakes (no Spring context, no Testcontainers): domain model, all five services
- `@WebMvcTest` slices: `PutawayController`, `AsnController` `:close` method
- Integration test (`PutawayLifecycleIntegrationTest`): Testcontainers Postgres + Kafka + Redis — golden-path from INSPECTED through close, verifying Kafka payloads match contract schema

## Definition of Done

- All 19 acceptance criteria satisfied
- `./gradlew :apps:inbound-service:test` passes (zero failures, zero skips)
- `InboundEventEnvelopeSerializer` handles all six event types
- `EventContractTest` asserts payload shapes for the three new events
- No TODO / FIXME left in production code
- Task moved to `review/` with this file updated to `Status: review`
