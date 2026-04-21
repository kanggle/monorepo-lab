# Service Type: Event Consumer

Normative requirements for any service whose `Service Type` is `event-consumer`.

This document extends the Core platform specs. It does not replace them.

---

# Scope

An `event-consumer` service primarily reacts to domain events published by other services. It may expose a small set of admin or query endpoints, but its primary role is asynchronous event processing.

Candidate services in this monorepo: `notification-service` (reacts to user/order events), `search-service` (reacts to catalog events for index sync).

---

# Mandatory Requirements

## Subscription Ownership
- Every consumed topic MUST be declared in `specs/services/<service>/architecture.md` under "Subscribed Topics"
- A topic MUST have exactly one team-owned consumer for any given consumer group
- Consumer group naming: `<service>-<purpose>` (e.g., `notification-service-order-events`)

## Idempotency
- Every consumer MUST be idempotent — duplicate event delivery is expected
- Idempotency strategies (in priority order):
  1. Natural idempotency (e.g., upsert by event-derived key)
  2. Idempotency table keyed by `eventId` (24h+ TTL)
  3. Optimistic concurrency on the target aggregate
- See `messaging/idempotent-consumer.md`

## Retry and DLQ
- Transient failures: in-process exponential backoff with jitter (max 3 retries by default)
- Persistent failures: route to dead-letter queue with full event payload + failure reason
- DLQ topic name: `<topic>.dlq`
- Operator MUST be alerted when DLQ depth > 0
- See `messaging/consumer-retry-dlq.md`

## Schema Versioning
- Consumers MUST tolerate unknown fields (forward compatibility)
- Consumers MUST branch on `schemaVersion` if semantics changed
- Events with unsupported `schemaVersion` MUST be routed to DLQ, not silently dropped

## Trace Propagation
- OTel context MUST be propagated from Kafka headers via `KafkaPropagator`
- Each consumed event creates a span linked to the producer span

## Ordering Guarantees
- If ordering matters, document the partition key in `architecture.md`
- Use Kafka partition key tied to the aggregate identity (e.g., `orderId`, `userId`)
- Cross-aggregate ordering is not guaranteed and must not be relied upon

## Observability
- Per-topic metrics: `consumer_lag`, `messages_processed_total`, `messages_failed_total`, `dlq_depth`
- Alerts: lag > 1 minute, DLQ growth, processing error rate > 1%
- See `cross-cutting/observability-setup.md`

---

# Allowed Patterns

- Subscribing to one or more topics from other services
- Materializing read models from event streams
- Triggering external side effects (email, push, webhook)
- Exposing a small admin or query REST endpoint as a secondary capability

---

# Forbidden Patterns

- Synchronous request/response from inside an event handler (couples consumer to other service health) — use bounded retries with circuit breaker
- Holding open a transaction across the entire batch — commit per message or in small chunks
- Silently swallowing parse errors — always route to DLQ
- Auto-resetting consumer offset to "latest" on startup — explicit operator action only

---

# Testing Requirements

- Unit tests for each handler with idempotent re-delivery
- Integration tests with Testcontainers Kafka exercising at-least-once delivery
- Contract tests against `specs/contracts/events/<producer>-events.md`
- DLQ routing test: simulate persistent failure, assert event lands in DLQ with correct metadata

---

# Default Skill Set

`messaging/event-implementation`, `messaging/idempotent-consumer`, `messaging/consumer-retry-dlq`, matched architecture skill, `cross-cutting/observability-setup`, `backend/testing-backend`, `testing/testcontainers`, `service-types/event-consumer-setup`

---

# Acceptance for a New Event Consumer Service

- [ ] `Subscribed Topics` declared in `architecture.md`
- [ ] Consumer group name follows `<service>-<purpose>` convention
- [ ] Idempotency strategy implemented and tested
- [ ] Retry/DLQ wired and tested
- [ ] OTel propagation verified across producer/consumer
- [ ] Lag and DLQ alerts configured
- [ ] Schema-version branching documented
