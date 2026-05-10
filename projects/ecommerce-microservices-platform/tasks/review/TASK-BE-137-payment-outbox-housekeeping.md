# Task ID

TASK-BE-137

# Title

payment-service outbox housekeeping (BE-136 W3 IT coverage + W4 JpaConfig location rationale)

# Status

ready

# Owner

backend

# Task Tags

- code
- test
- housekeeping

---

# Goal

Close the two non-blocker findings surfaced by TASK-BE-136 (PR #345)
self-review by the code-reviewer agent:

- **W3** — `PaymentEventPublishIntegrationTest` covers the
  `PaymentCompleted` round-trip (commit → outbox PENDING → relay polls
  → PUBLISHED → Kafka consumer receives envelope) but not the symmetric
  `PaymentRefunded` path. A regression in the refund topic mapping
  would currently only be caught by the unit test
  (`PaymentEventOutboxRelayTest.sendToKafka_paymentRefunded_*`); the
  integration layer has no symmetric coverage.

- **W4** — code-reviewer surfaced that `JpaConfig` lives in
  `com.example.payment.config` rather than
  `com.example.payment.infrastructure.config` as the
  `libs/java-messaging` `OutboxJpaConfig` javadoc recommends. This
  task records the **deliberate placement decision** —
  payment-service follows a Hexagonal layout (`adapter/`,
  `application/`, `domain/`) with no `infrastructure/` package; its
  cross-cutting Spring config beans (`KafkaConsumerConfig`,
  `StandaloneConfig`, `JpaConfig`) all live in `config/` for
  consistency. The libs javadoc's recommendation is followed by
  procurement-service (Layered) and similar architectures; Hexagonal
  services place cross-cutting config under their own conventions.

---

# Scope

## In Scope

- **W3 impl**: add a new test method
  `pollingRelay_refundedRoundTrip_publishesToKafkaAndMarksRowPublished()`
  to `PaymentEventPublishIntegrationTest`. Same harness as the existing
  Completed round-trip, exercising:
  1. `OrderPlacedEventConsumer.onMessage(...)` → PENDING Payment
  2. `PaymentConfirmService.confirm(...)` → COMPLETED + outbox row 1 (Completed)
  3. `PaymentRefundService.refundPayment(orderId)` → REFUNDED + outbox row 2 (Refunded)
  4. Poll `payment.payment.refunded` topic via `EmbeddedKafkaBroker`
     within the 15-second deadline.
  5. Assert envelope `event_type=PaymentRefunded`, `source=payment-service`,
     `payload.orderId/userId/amount/refundedAt` shape, Kafka record key
     = `payment_id`.
  6. Assert outbox row `status=PUBLISHED` + `published_at` non-null.

- **W4 decision NOTE**: add a class-level javadoc to `JpaConfig.java`
  explaining the rationale for `config/` placement (Hexagonal layout
  convention, sibling Spring config consistency, no `infrastructure/`
  package in payment-service). Cross-reference the
  `OutboxJpaConfig` javadoc's recommendation as
  architecture-conditional.

## Out of Scope

- The lib-level `OutboxPublisher` batch resilience (TASK-MONO-050,
  separate task, already done).
- Renaming `config/` to `infrastructure/config/` (intentionally
  rejected — Hexagonal layout convention).
- Other payment-service refactors not directly surfaced by BE-136 W3/W4.

---

# Acceptance Criteria

- [ ] `PaymentEventPublishIntegrationTest` has a new test method covering the `PaymentRefunded` full round-trip (commit → outbox PENDING → relay → PUBLISHED → Kafka consumer receives envelope unchanged).
- [ ] The new test consumes from the `payment.payment.refunded` topic via `EmbeddedKafkaBroker` + asserts envelope shape consistent with `specs/contracts/events/payment-events.md`.
- [ ] The new test runs within the existing 15-second deadline (matches the Completed test pattern).
- [ ] `JpaConfig.java` carries a class-level javadoc explaining the deliberate `config/` placement in payment-service (Hexagonal architecture rationale + cross-reference to lib `OutboxJpaConfig` javadoc's procurement-service-style recommendation).
- [ ] `:test` (98 unit tests) still green. Integration test deferred to CI Linux runner per known Windows paging blocker.

---

# Related Specs

- `projects/ecommerce-microservices-platform/specs/services/payment-service/architecture.md` § Event Publication (TASK-BE-136 footprint)
- `projects/ecommerce-microservices-platform/docs/adr/ADR-006-at-least-once-delivery-policy.md` § payment-service Scenario A
- `projects/ecommerce-microservices-platform/specs/contracts/events/payment-events.md` (envelope shape contract)
- `libs/java-messaging/src/main/java/com/example/messaging/outbox/OutboxJpaConfig.java` (javadoc cross-ref)

# Related Skills

- `.claude/skills/write-tests` (Testcontainers + EmbeddedKafka integration test pattern)

---

# Related Contracts

- `payment-events.md` envelope shape MUST remain unchanged (verified by the new test's envelope assertions).

---

# Target Service

- payment-service (test code + 1 javadoc edit)

---

# Architecture

No architecture change. The new IT exercises the existing outbox
relay → Kafka chain on a second event type; the W4 decision documents
the existing layout choice.

---

# Implementation Notes

- Mirror the existing
  `pollingRelay_publishesToKafkaAndMarksRowPublished()` method
  structure. Extract a small helper if the second test would otherwise
  copy 80%+ of the setup — keep refactor minimal (no over-DRYing).
- For the refund scenario, the Completed event will also be written to
  outbox first (same use-case sequence). The polling relay will publish
  both rows. Filter the consumer `poll()` loop for `event_type=PaymentRefunded`.
- Verify `PaymentRefundService.refundPayment(orderId)` does NOT require
  the PG cancellation step here — the existing test stubs
  `paymentGateway` via `@MockitoBean` (mirror `PaymentRefundIntegrationTest`).
- The `JpaConfig` javadoc should be terse (3-5 lines): a one-liner
  rationale + reference to the lib javadoc's
  architecture-conditional recommendation. Do not over-explain;
  Hexagonal vs Layered is a known idiom.

---

# Edge Cases

- **Both events in same batch**: relay publishes Completed first (lower
  `id`), then Refunded. The test only asserts on Refunded — Completed
  is fine to publish in passing.
- **Consumer offset lag**: `EmbeddedKafkaBroker` is fresh per test
  class; consumer subscription happens after both writes but before
  the relay polls. Same pattern as the existing Completed test —
  expected to work.

---

# Failure Scenarios

- New test flake on slow CI: extend the 15s deadline only if real
  flakes appear in CI. v1 uses identical timing as the existing test;
  no preemptive increase.

---

# Test Requirements

- **Integration (Testcontainers Postgres + embedded Kafka, `@Tag("integration")`)**:
  1 new test method on `PaymentEventPublishIntegrationTest` as scoped above.

---

# Definition of Done

- [ ] New Refunded round-trip test method green (CI Linux runner).
- [ ] `JpaConfig.java` javadoc updated.
- [ ] All existing tests still pass (`:test` green).
- [ ] Task ready → review (single PR — feedback_pr_bundling permits).

---

# Provenance

Filed by TASK-BE-136 (PR #345) self-review by code-reviewer agent —
W3 (Refunded IT coverage gap) and W4 (JpaConfig location).

Surface size: ~30 LoC test code + 5 LoC javadoc. No production
behavior change.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical test mirror + javadoc note).
