# Service Architecture

## Service
`payment-service`

## Service Type
`rest-api`

## Architecture Style
`Hexagonal Architecture`

## Why This Architecture
This service depends heavily on external systems and integration boundaries.

Payment processing requires clear separation between business logic and external adapters.

Testability, adapter isolation, and boundary control are critical.

## Internal Structure Rule
This service uses a ports-and-adapters structure.

Recommended internal areas:
- inbound adapters
- application
- domain
- outbound ports
- outbound adapters

Business logic must remain independent from specific framework or vendor integrations.

## Allowed Dependencies
- inbound adapters -> application
- application -> domain
- application -> ports
- outbound adapters -> ports
- outbound adapters -> external SDKs or infrastructure

## Forbidden Dependencies
- domain must not depend on framework or vendor SDK code
- application must not depend directly on external payment vendor implementations
- adapters must not own business policy
- controllers or message consumers must not bypass application services

## Boundary Rules
- inbound adapters translate external input into application commands
- application layer coordinates use-cases through ports
- domain contains payment rules and decision logic
- outbound adapters implement external gateway, database, messaging, or notification integrations

## Integration Rules
- external payment provider integration must be isolated behind outbound ports
- HTTP and event contracts must follow published contracts
- retry, timeout, and failure behavior must be implemented through adapter/application coordination
- shared libraries may support technical concerns only

## Testing Expectations
Required emphasis:
- application service tests
- port contract tests
- outbound adapter tests
- integration tests for provider interaction boundaries
- failure and retry scenario tests

## Event Publication
Domain events (`PaymentCompleted`, `PaymentRefunded`) are published via the
**transactional outbox** pattern (`libs/java-messaging` —
[ADR-006](../../../docs/adr/ADR-006-at-least-once-delivery-policy.md), Scenario
A; impl TASK-BE-136). The outbound flow:

1. The use-case service (`PaymentConfirmService` / `PaymentRefundService`) runs
   inside a `@Transactional` boundary. The state mutation on `payments` and
   the envelope row on `outbox` commit atomically.
2. `PaymentEventOutboxWriter` (`adapter.out.event`) serializes the event
   record and calls `OutboxWriter.save(...)`. It does NOT touch Kafka.
3. `PaymentEventOutboxRelay extends OutboxPollingScheduler` polls the
   `outbox` table at `outbox.polling.interval-ms` (default 1s), resolves the
   target Kafka topic (`PaymentCompleted` → `payment.payment.completed`,
   `PaymentRefunded` → `payment.payment.refunded`), and marks the row
   `PUBLISHED` only after broker ack.
4. On Kafka send failure, the relay invokes
   `PaymentMetricRecorder.incrementEventPublishFailure(eventType)` —
   preserving the original `event_publish_failure_total{service=payment-service,event_type=...}`
   metric label semantics so existing dashboards / alerts continue to work.

**Delivery semantic:** at-least-once. The relay retries until broker ack;
consumers must already be idempotent on `event_id` (existing dedupe layer).

**Latency envelope:** up to one polling interval (default 1s) between
transaction commit and broker dispatch. Consumers should not assume
sub-second consistency.

## Change Rule
Any architectural change to this service must be documented here first before implementation.