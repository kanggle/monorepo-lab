# order-service — Architecture

This document declares the internal architecture of `order-service`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `order-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `rest-api + event-consumer` (hybrid — see Service Type Composition below) |
| Architecture Style | **DDD-style Architecture** (4-layer + application/port) |
| Domain | ecommerce |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | Order lifecycle (confirm / ship / deliver / cancel / payment / refund + saga Category A) |
| Deployable unit | `apps/order-service/` |
| Data store | PostgreSQL (owned) |
| Event publication | Kafka via outbox (order.* lifecycle events, ADR-MONO-005 Category A) |
| Event consumption | `UserWithdrawn` from `user.user.withdrawn`, `StockChanged` from `product.product.stock-changed`, `PaymentCompleted` / `PaymentRefunded` from `payment.payment.*` (saga participation, ADR-MONO-005 Category A) |

### Service Type Composition

`order-service` is a hybrid service per
`platform/service-types/INDEX.md` § Hybrid Cases (REST service that also
consumes events for saga orchestration). Primary type is `rest-api`; the
secondary `event-consumer` capability subscribes to user / product / payment
lifecycle events to drive Order saga state transitions (ADR-MONO-005 Category
A multi-step saga, OrderStuckDetector + OrderStuckRecoveryHandler,
TASK-BE-138). The primary type determines the spec read order — applied
rules:
[platform/service-types/rest-api.md](../../../../../platform/service-types/rest-api.md).
The secondary capability is documented under "Events" below with topic /
consumer-group details.

---

## Why This Architecture
This service owns domain rules that are central to business behavior.

Order lifecycle, invariants, aggregate consistency, and domain transitions are important.

This service requires explicit domain modeling rather than a simple CRUD-oriented structure.

## Internal Structure Rule
This service uses a DDD-style internal structure.

Recommended internal areas:
- presentation or interface
- application
- domain
- infrastructure

Recommended domain concepts:
- aggregates
- entities
- value objects
- domain services
- repositories
- domain events

Package organization should preserve aggregate boundaries and domain ownership.

## Allowed Dependencies
- interface -> application
- application -> domain
- infrastructure -> domain
- infrastructure -> application ports if defined

## Forbidden Dependencies
- domain must not depend on framework code
- domain must not depend on persistence implementation details
- application layer must not contain domain rules that belong inside aggregates or domain services
- controllers must not bypass application services
- repositories must not contain business decisions that belong to the domain

## Boundary Rules
- application layer orchestrates use-cases and transaction boundaries
- domain layer protects invariants and business rules
- infrastructure layer implements repositories and external integrations
- aggregate boundaries must be respected during modification

## Outbox

- Pattern: Transactional Outbox
- Table: `outbox` (libs/java-messaging 표준 schema)
- Polling scheduler: `OutboxPollingScheduler` (libs `com.example.messaging.outbox.OutboxPollingScheduler` base 의 concrete subclass)
- Topic 매핑:
  - `OrderPlaced` → `order.order.placed`
  - `OrderCancelled` → `order.order.cancelled`

## Integration Rules
- outbound events must follow published event contracts
- HTTP APIs must follow published HTTP contracts
- cross-service orchestration must follow use-cases and ownership rules
- shared libraries must not absorb order-domain logic

## Testing Expectations
Required emphasis:
- aggregate/domain behavior tests
- application service tests
- repository integration tests
- contract tests for published APIs/events

## Saga / Long-running Flow (ADR-MONO-005)

Per [ADR-MONO-005](../../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md).

| Flow | Category | Current state | Status |
|---|---|---|---|
| order placement saga (`OrderPlaced → PaymentCompleted → OrderConfirmed`) | **A** (choreographed multi-step; no orchestrator row) | Pure event-driven across `order-service ↔ payment-service`. Order publishes `order.order.placed` via outbox; payment-service consumes and publishes `payment.payment.completed` via outbox (post-ADR-006, Scenario A — TASK-BE-136 PR #345); order-service consumes and confirms. Both producers are at-least-once. **Stuck-detector (TASK-BE-138):** `OrderStuckDetector` (60 s `@Scheduled`) + `OrderStuckRecoveryHandler` (REQUIRES_NEW per-order, AOP-split mirror of wms `SagaSweeper` + `SagaRecoveryHandler`) sweep `orders WHERE status='PENDING' AND payment_id IS NULL AND created_at < NOW() - 1800 s`. Cap 5 attempts → terminal `STUCK_RECOVERY_FAILED` + `order.alert.saga.recovery.exhausted` outbox event (T3 co-commit). 3 metrics (`order_stuck_detector_run_total`, `…_recovery_fired_total{from_state=PENDING}`, `…_exhausted_total{from_state=PENDING}`). Composite `idx_orders_status_created_at` supports the sweep query. All knobs externalised under `order.saga.stuck-detector.*`. | **Compliant** (post-TASK-BE-138) |

## Change Rule
Any architectural change to this service must be documented here first before implementation.