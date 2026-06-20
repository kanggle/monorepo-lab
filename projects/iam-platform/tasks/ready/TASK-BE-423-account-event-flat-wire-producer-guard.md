# TASK-BE-423 — Producer-side flat-wire guard for account.status.changed / account.deleted

**Status:** ready
**Type:** test hardening (contract boundary regression guard)
**Parent:** TASK-BE-422 edge-case (confirm the real on-wire shape) + TASK-BE-421.

## Goal

TASK-BE-422 fixed the ecommerce account.* consumers to the **flat** IAM wire and added
consumer-side real-wire deserialization tests. The symmetric guard was missing on the
**producer** side: nothing asserted that `account.status.changed` / `account.deleted` are
actually serialized flat (only `account.locked` had a captured-JSON shape test). Add
producer-side assertions so IAM can never silently regress these two events to a nested
envelope and re-break the consumers.

This is the offline equivalent of "capture a real federation-e2e message" (the BE-422
edge-case): `AccountEventPublisherTest` captures the exact string `saveEvent` writes to the
outbox — which `OutboxPublisher` relays to Kafka **verbatim** — i.e. the real on-wire message,
produced by the real `AccountEventPublisher` + `AccountEventFactory` + `ObjectMapper`.

## Scope

- `projects/iam-platform/apps/account-service/src/test/java/com/example/account/application/event/AccountEventPublisherTest.java` — add two tests capturing the serialized outbox payload and asserting **flat** shape:
  - `publishStatusChanged_flatWireShape`: top-level `accountId`/`tenantId`/`previousStatus`/`currentStatus`/`reasonCode`/`actorType`/`actorId`/`occurredAt`; assert `payload`/`eventType`/`source` keys are ABSENT (no envelope).
  - `publishAccountDeleted_flatWireShape`: top-level `accountId`/`tenantId`/`reasonCode`/`actorType`/`actorId`/`deletedAt`/`gracePeriodEndsAt`/`anonymized`; assert NO `eventId` (account.deleted carries none — the reason BE-422 re-keyed order-service dedup) and NO `payload` wrapper.

Test-only; no production code change.

## Acceptance Criteria

- [ ] **AC-1** — Both events' captured outbox JSON is asserted FLAT (top-level fields, no `payload`/envelope wrapper).
- [ ] **AC-2** — `account.deleted` asserted to carry no `eventId` (documents/guards the dedup-rekey rationale).
- [ ] **AC-3** — account-service build + tests GREEN.

## Related Specs / Contracts

- `projects/iam-platform/specs/contracts/events/account-events.md` (flat payloads — authoritative)
- `projects/ecommerce-microservices-platform/specs/contracts/events/account-lifecycle-subscriptions.md` § Envelope
- Impl refs: `AccountEventPublisher.save → BaseEventPublisher.saveEvent`, `OutboxPublisher` (verbatim relay)

## Edge Cases / Failure Scenarios

- If a future change routes these events through `writeEvent` (nested envelope) or an `AbstractOutboxPublisher`, these tests fail — the intended guard.
- True end-to-end Kafka-transport confirmation (live federation-e2e) remains optional belt-and-suspenders; the transport moves bytes verbatim, so producer-flat + consumer-flat-deser tests meet at the same JSON and prove the chain offline.
