# payment-service — Architecture

This document declares the internal architecture of `payment-service`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `payment-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `rest-api + event-consumer` (hybrid — see Service Type Composition below) |
| Architecture Style | **Hexagonal Architecture** (Ports & Adapters) |
| Domain | ecommerce |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | Payment authorize / confirm / refund (PG vendor integration) |
| Deployable unit | `apps/payment-service/` |
| Data store | PostgreSQL (owned) |
| Event publication | Kafka via outbox (payment.* lifecycle events) |
| Event consumption | `OrderPlaced` from `order.order.placed`, `OrderCancelled` from `order.order.cancelled` (saga participation, ADR-MONO-005 Category B) |

### Service Type Composition

`payment-service` is a hybrid service per
`platform/service-types/INDEX.md` § Hybrid Cases (REST service that also
consumes events for saga orchestration). Primary type is `rest-api`; the
secondary `event-consumer` capability subscribes to order lifecycle events to
drive payment saga state transitions. PG vendor integration via
TossPaymentsAdapter (Resilience4j CB + Retry + Bulkhead, ADR-MONO-005
Category B, TASK-BE-139). Hexagonal 으로 vendor adapter 격리. The primary
type determines the spec read order — applied rules:
[platform/service-types/rest-api.md](../../../../../platform/service-types/rest-api.md).
The secondary capability is documented under "Events" below with topic /
consumer-group details.

---

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

## Outbox
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

## Saga / Long-running Flow (ADR-MONO-005)

Per [ADR-MONO-005](../../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md).

| Flow | Category | Resilience4j config | Exception classification | Status |
|---|---|---|---|---|
| payment confirm (Toss Payments `confirmPayment` ↔ `payments` row) | **B** (synchronous external) | `@CircuitBreaker(toss-payments)` 50 % / 10-call TIME_BASED window · `@Retry(toss-payments)` 3 attempts exp+jitter (4xx + `PgConfirmFailedException` in `ignore-exceptions`) · `@Bulkhead(toss-payments)` semaphore, 10 concurrent · connect 5 s + read 10 s timeouts | **4xx → `PgConfirmFailedException`** (PG-side definitive rejection, payment row → `FAILED`); **5xx / timeout / circuit-open / bulkhead-full → `PgGatewayUnavailableException`** (transport failure, payment row unchanged, caller may idempotently retry) | **Compliant** (TASK-BE-139, PR #__) |
| payment refund (Toss Payments `cancelPayment` ↔ `payments` row + downstream `payment.payment.refunded` outbox) | **B** (synchronous external) | Same R4j config as confirm | Same exception classification — on `PgGatewayUnavailableException` the refund row stays `COMPLETED` and the caller's retry / DLT mechanism re-drives | **Compliant** (TASK-BE-139, PR #__) |

Source: `TossPaymentsAdapter` (`@CircuitBreaker(name="toss-payments", fallbackMethod="confirmFallback"/"cancelFallback")`). Fallback translates `CallNotPermittedException` / `BulkheadFullException` / retry-exhausted `HttpServerErrorException` / `ResourceAccessException` (timeout) uniformly to `PgGatewayUnavailableException`. `PaymentConfirmService` / `PaymentRefundService` distinguish the two exception kinds — only `PgConfirmFailedException` transitions the row state. Caller error codes: 502 `PG_CONFIRM_FAILED` vs 503 `PG_GATEWAY_UNAVAILABLE`.

## Change Rule
Any architectural change to this service must be documented here first before implementation.