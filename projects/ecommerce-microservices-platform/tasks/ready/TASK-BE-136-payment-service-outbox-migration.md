# Task ID

TASK-BE-136

# Title

payment-service transactional outbox migration (PaymentCompleted + PaymentRefunded)

# Status

ready

# Owner

backend

# Task Tags

- code
- event

---

# Goal

Migrate `payment-service` event publishing from direct `KafkaTemplate.send` to the **transactional outbox** pattern (`libs/java-messaging`), closing the at-least-once delivery gap that ADR-006 classified as **Scenario A**.

After this task: `payment.payment.completed` and `payment.payment.refunded` are at-least-once-delivered. Producer-side silent loss on `KafkaException` is impossible — the outbox row persists in the same transaction as the payment state mutation, and the polling relay retries until broker ack.

---

# Scope

## In Scope

- New Flyway migration `V<n>__init_outbox_and_processed_events.sql` — `outbox` + `processed_events` tables matching `libs/java-messaging` `OutboxJpaEntity` / `ProcessedEventJpaEntity` schemas.
- New publisher `PaymentEventOutboxWriter` (or extend `BaseEventPublisher`) — replaces `KafkaPaymentEventPublisher.publishXxx` body; persists envelope to outbox inside the existing payment use-case `@Transactional` boundary.
- New `PaymentEventOutboxRelay extends OutboxPollingScheduler` — `resolveTopic` switch for the 2 event types (`PaymentCompleted` → `payment.payment.completed`, `PaymentRefunded` → `payment.payment.refunded`); `onKafkaSendFailure` → existing `paymentMetricRecorder.incrementEventPublishFailure(eventType)`.
- Wire the relay's polling cadence config (`outbox.polling.interval-ms`, `batch-size`, `enabled`).
- Integration test (Testcontainers Postgres + Kafka): payment use-case commits → outbox row PENDING → relay polls within N seconds → row → PUBLISHED → consumer receives.
- Update `payment-service/architecture.md` § Event Publication (or equivalent) to reference the new outbox path.
- Update `payment-service/dependencies.md § Notes` — remove the "currently best-effort" warning, mark Scenario A as completed.
- Move ADR-006 status from PROPOSED → ACCEPTED in the same PR (or follow-up amendment PR if scope grows).

## Out of Scope

- user-service / notification-service migration (ADR-006 = Scenario B for those).
- Schema change to existing `payments` table (only new outbox / processed_events tables added).
- Consumer-side retry/DLQ rework (consumer behavior unchanged).
- Other event types beyond Completed / Refunded.

---

# Acceptance Criteria

- [ ] Flyway migration creates `outbox` + `processed_events` tables; `./gradlew :apps:payment-service:integrationTest` Flyway validation passes.
- [ ] `KafkaPaymentEventPublisher` no longer calls `kafkaTemplate.send` directly — replaced by outbox write inside the use-case transaction.
- [ ] Polling relay extends `libs/java-messaging` `OutboxPollingScheduler`.
- [ ] Integration test demonstrates: payment commit → row appears in `outbox` (status=PENDING) → relay polls → row updated to PUBLISHED → Kafka topic receives the envelope (Testcontainers Kafka consumer assert).
- [ ] Existing `payment.completed` / `payment.refunded` consumer tests continue to pass (envelope shape unchanged — only the publish path changes).
- [ ] `payment-service/architecture.md` + `payment-service/dependencies.md` reflect the new path.
- [ ] ADR-006 status flipped to ACCEPTED.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0.

- `projects/ecommerce-microservices-platform/PROJECT.md`
- `projects/ecommerce-microservices-platform/docs/adr/ADR-006-at-least-once-delivery-policy.md` (this task is the impl half of the ADR's payment-service decision)
- `projects/ecommerce-microservices-platform/specs/services/payment-service/architecture.md` (target — Event Publication section)
- `projects/ecommerce-microservices-platform/specs/services/payment-service/dependencies.md` (target — Notes update)
- `projects/ecommerce-microservices-platform/specs/contracts/events/payment-events.md`
- `platform/event-driven-policy.md`
- `rules/traits/transactional.md` § T2 (atomic state-change + outbox) / T3 (outbox table + polling)
- Reference impl in same project: `order-service` outbox path (`OrderEventOutboxRelay`, `OrderEvent OutboxWriter`); copy the pattern.

# Related Skills

- `.claude/skills/messaging/outbox-pattern/SKILL.md`
- `.claude/skills/database/flyway-migration/SKILL.md` (if exists)

---

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/events/payment-events.md` — envelope shape MUST stay unchanged across migration

---

# Target Service

- payment-service

---

# Architecture

Hexagonal (per existing `payment-service/architecture.md`). The outbox writer is an **outbound port** implementation; the relay is an **outbound adapter scheduler**. `KafkaTemplate` reference moves from publisher to relay.

---

# Implementation Notes

- Mirror `order-service`'s outbox layout: `infrastructure/event/OrderEventOutboxWriter` + `infrastructure/event/OrderEventOutboxRelay`. Naming: `PaymentEventOutboxWriter` / `PaymentEventOutboxRelay`.
- Existing `KafkaPaymentEventPublisher` can be **deleted** or kept as a thin wrapper that delegates to `OutboxWriter` (cleaner = delete; rename callers to inject `PaymentEventOutboxWriter` directly).
- Existing `PaymentMetricRecorder.incrementEventPublishFailure(String)` is the failure metric; wire it into `PaymentEventOutboxRelay.onKafkaSendFailure` so the metric label semantics stay identical (no monitoring breakage).
- `outbox.polling.enabled=false` should disable the scheduler in slice/unit tests (mirror `procurement-service` `ProcurementOutboxPollingScheduler` `@ConditionalOnProperty`).
- Ensure the use-case service's `@Transactional` propagation includes the outbox write — the writer should NOT open a new transaction.
- Verify processed_events is required by the application autoconfiguration (libs/java-messaging may scan-conditionally).

---

# Edge Cases

- Existing payment rows may not have outbox sibling rows — that's fine, this task only changes future publishes; historical rows are not retroactively published.
- Polling interval default (1 second from libs) means up to 1s consumer delay. Document in architecture.md if SLA-relevant.
- Two Kafka publishes from a single transaction (Completed + Refunded never co-occur in same Tx in practice, but verify) — both rows poll independently in arrival order.

---

# Failure Scenarios

- Migration applied in production but relay not deployed → outbox rows accumulate as PENDING forever. Mitigation: add a startup smoke test (`relay.runOnce()` on `ApplicationReadyEvent`) or simply verify deploy order.
- Existing best-effort consumer was tolerant of missing events; post-migration the consumer suddenly receives "missing" events delivered late. Verify consumer idempotency (T8 dedupe on eventId) — should already be correct per spec, but integration test confirms.

---

# Test Requirements

- **Unit**: `PaymentEventOutboxWriterTest` — given a `PaymentCompletedEvent`, then `OutboxWriter.save` is invoked with correct `aggregateType` / `aggregateId` / `eventType` / serialized envelope.
- **Slice**: `PaymentEventOutboxRelayTest` — `resolveTopic` switch covers both event types + throws on unknown.
- **Integration** (Testcontainers Kafka + Postgres, `@Tag("integration")`): full round-trip — payment use-case commit → outbox PENDING → relay polls → PUBLISHED → consumer asserts envelope.
- **Regression**: existing consumer tests + saga integration tests pass without modification.

---

# Definition of Done

- [ ] Migration + writer + relay implemented
- [ ] All test classes added + green
- [ ] `./gradlew :apps:payment-service:check` green; `:integrationTest` green
- [ ] architecture.md + dependencies.md updated
- [ ] ADR-006 → ACCEPTED in same PR (or amendment PR)
- [ ] Ready for review

---

# Provenance

Filed by ADR-006 (this PR's parent) — Scenario A decision for payment-service. The audit + decision land in the spec PR (BE-135); the impl is this task.

분석=Opus 4.7 / 구현 권장=Opus (multi-file impl with Flyway migration, polling scheduler wiring, integration test against Testcontainers Kafka + Postgres; non-trivial)
