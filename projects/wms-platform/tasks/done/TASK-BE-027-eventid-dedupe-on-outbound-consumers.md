# Task ID

TASK-BE-027

# Title

inventory-service — wire `EventDedupePort` into PickingRequested / PickingCancelled / ShippingConfirmed consumers

# Status

review

# Owner

backend

# Task Tags

- code
- event
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Close the single blocker raised in the TASK-BE-023 review (verdict 2026-04-28
FIX NEEDED).

The three outbound-saga event consumers currently bypass `EventDedupePort`
in favour of aggregate-state short-circuits ("if reservation is already
RESERVED, no-op"; "if already RELEASED, no-op"; etc.). That is functionally
idempotent for *redelivery with the same `pickingRequestId`*, but it
**violates** several contracts:

- **Trait T8** — eventId-based dedupe is mandatory for every Kafka consumer
  in this service. Aggregate-state short-circuits are a **second** safety
  net, not a substitute for the dedupe table.
- **architecture.md §Event Consumption** — declares the
  `inventory_event_dedupe(event_id, event_type, processed_at, outcome)` table
  as the canonical dedupe surface for every consumer.
- **AC-12 of TASK-BE-023** — explicitly requires `eventId`-based dedupe on
  all three outbound consumers; the resulting `outcome=APPLIED` /
  `IGNORED_DUPLICATE` rows are part of the observability requirement.

The `inventory_event_dedupe` schema, port, and persistence adapter were all
delivered in TASK-BE-021 and are unused on this surface today.

---

# Scope

## In Scope

- `PickingRequestedConsumer` — wrap the handler body with
  `EventDedupePort.process(eventId, eventType, …)` so the dedupe row is
  inserted in the same `@Transactional` boundary as the reservation
  mutation.
- `PickingCancelledConsumer` — same.
- `ShippingConfirmedConsumer` — same.
- Keep the existing aggregate-state short-circuits as the inner guard
  (defence-in-depth) — the pickingRequestId-uniqueness path remains the
  source of truth for true business idempotency, but eventId-dedupe must
  fire **first**, before the domain effect, exactly as in
  `PutawayCompletedConsumer` (BE-022 reference pattern).
- Update the three consumers' Javadoc to explain the layered idempotency
  (eventId outer, pickingRequestId / state inner) and why both exist.
- Unit tests per consumer:
  - first delivery → `outcome=APPLIED` row inserted, domain effect applied
  - duplicate delivery (same eventId) → `outcome=IGNORED_DUPLICATE` row
    written by the dedupe port (or no row, depending on the
    `EventDedupePersistenceAdapter` policy decided in BE-022 review — match
    whatever that policy is)
  - aggregate-state short-circuit (different eventId, terminal state) →
    eventId-dedupe inserts APPLIED, no domain mutation, no error
- Integration tests (Testcontainers Kafka): for at least one consumer
  (`PickingRequestedConsumer` is the highest-stakes), publish the same
  message twice and assert exactly one reservation row + exactly two
  `inventory_event_dedupe` rows (or one APPLIED row if dedupe-row policy is
  no-op-on-duplicate).

## Out of Scope

- Any change to the `EventDedupePort` interface or
  `EventDedupePersistenceAdapter` implementation.
- Any change to consumer retry / backoff settings.
- Any change to the `Reservation` state machine or the persistence adapter.
- The `PutawayCompletedConsumer` (already correct from BE-022).

---

# Acceptance Criteria

- [ ] `PickingRequestedConsumer.onMessage` calls `EventDedupePort.process`
      before invoking `ReserveStockUseCase`.
- [ ] `PickingCancelledConsumer.onMessage` calls `EventDedupePort.process`
      before invoking `ReleaseReservationUseCase`.
- [ ] `ShippingConfirmedConsumer.onMessage` calls `EventDedupePort.process`
      before invoking `ConfirmReservationUseCase`.
- [ ] The dedupe row is written in the same `@Transactional` boundary as
      the domain mutation; verified by integration test against
      Testcontainers Postgres + Kafka.
- [ ] Each consumer has a unit test for: APPLIED, IGNORED_DUPLICATE, and
      terminal-state short-circuit.
- [ ] One Testcontainers integration test (PickingRequested) publishes the
      same message twice and asserts at-most-one reservation effect.
- [ ] Existing tests that asserted state-based short-circuit behaviour
      continue to pass.
- [ ] `./gradlew :apps:inventory-service:check` is green.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, `rules/common.md`, `rules/domains/wms.md`, `rules/traits/transactional.md` (T8), `rules/traits/integration-heavy.md`.

- `platform/error-handling.md`
- `rules/traits/transactional.md` — T8 (eventId dedupe)
- `specs/services/inventory-service/architecture.md` — §Event Consumption, §Consumer Rules
- `specs/services/inventory-service/idempotency.md` — §Asynchronous (Kafka)

# Related Skills

- `.claude/skills/backend/messaging/kafka-consumer-dedupe`
- `.claude/skills/backend/architecture/hexagonal`

---

# Related Contracts

- `specs/contracts/events/inventory-events.md` — for envelope `eventId` field
- `specs/contracts/events/outbound-events.md` — picking.requested / picking.cancelled / shipping.confirmed schemas (the `eventId` is the dedupe key)

---

# Target Service

- `inventory-service`

---

# Architecture

Follow:

- `specs/services/inventory-service/architecture.md`

The reference pattern is `PutawayCompletedConsumer` (BE-022): the consumer
is `@Transactional`; the dedupe call happens before the use-case call;
`EventDedupePersistenceAdapter` uses `Propagation.MANDATORY` so the dedupe
write joins the consumer's TX.

---

# Implementation Notes

- The `eventId` should come from the message's envelope, not from a Kafka
  header. The envelope is the source of truth (per architecture.md
  §Envelope).
- The `eventType` parameter to `EventDedupePort.process` should be the
  full envelope `eventType` string (e.g., `outbound.picking.requested`),
  matching the convention in `PutawayCompletedConsumer`.
- After inserting the dedupe row but **before** the inner aggregate-state
  short-circuit, the domain mutation must be invoked. The aggregate-state
  short-circuit then becomes the inner guard for cross-consumer races
  (e.g., a manual REST cancel arriving between the eventId-dedupe row and
  the domain effect).
- Do not collapse the two layers into one — both are required by the
  spec.

---

# Edge Cases

- Message arrives with malformed envelope (missing `eventId`): consumer
  rejects via existing deserialiser → DLT. No dedupe write. Test exists in
  BE-022; do not duplicate.
- Two messages with the same `eventId` but different `pickingRequestId`
  (broker bug or producer error): the dedupe table treats them as the same
  message; the second is rejected as duplicate. This is correct — eventId
  is opaque to the consumer.
- Message arrives for a reservation that has already CANCELLED via REST:
  eventId-dedupe inserts APPLIED; aggregate-state short-circuit logs and
  no-ops. Both rows exist; test must cover this exact ordering.

---

# Failure Scenarios

- Dedupe insert fails for non-duplicate reason (DB connection drop):
  consumer rolls back the entire TX; Kafka redelivers; eventually retries
  exhaust → DLT. Existing pattern.
- Domain TX commits but dedupe-row commit phase fails: impossible because
  both are in the same TX. Verify by integration test (kill DB mid-TX is
  out of scope; rely on `Propagation.MANDATORY` correctness).

---

# Test Requirements

- Unit tests per consumer (3 cases each).
- One Testcontainers integration test (full path).
- Update existing consumer tests if they construct messages without an
  `eventId` field — add the field with a deterministic value.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests updated/added per AC
- [ ] Tests passing
- [ ] Consumer Javadocs updated to describe layered idempotency
- [ ] PR description references TASK-BE-023 review verdict
- [ ] Ready for review
