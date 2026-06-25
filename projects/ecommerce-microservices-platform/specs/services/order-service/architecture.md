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
| Event consumption | `UserWithdrawn` from `user.user.withdrawn`, `StockChanged` from `product.product.stock-changed` (`reason=ORDER_RESERVED` → confirm), `OrderReservationFailed` from `product.product.reservation-failed` (→ `PENDING → BACKORDERED`, TASK-BE-428), `PaymentCompleted` / `PaymentRefunded` from `payment.payment.*` (saga participation, ADR-MONO-005 Category A); `wms.outbound.order.cancelled.v1` (WmsOutboundCancelledConsumer, consumer group `order-service-wms`, ADR-022 §D4); **IAM `account.deleted` (AccountDeletedConsumer, consumer group `order-service-account-sync`, ADR-MONO-037 P3-B / TASK-BE-401)** — `anonymized=true` → order-held PII (shipping-address snapshot) anonymization cascade across all of the subject's orders; the GDPR TASK-BE-258 obligation for the order store |
| Order state machine | `PENDING → CONFIRMED → SHIPPED → DELIVERED`; `BACKORDERED` (TASK-BE-428: payment-reservation short → held; `BACKORDERED → CONFIRMED` on restock re-reservation, `BACKORDERED → CANCELLED` on operator cancel); `CANCELLED` (from `PENDING\|CONFIRMED\|BACKORDERED`, with a `cancelReason` of `OPERATOR` or `PAYMENT_TIMEOUT` — TASK-BE-435). **Stuck-detector terminal: `CANCELLED(PAYMENT_TIMEOUT)` (primary)** — a payment-pending order past grace × max-attempts is auto-cancelled with `cancelReason = PAYMENT_TIMEOUT` (publishes `OrderCancelled` + informational `OrderSagaRecoveryExhausted`), per ADR-MONO-005 § 2.3 D3 auto-resolving-terminal refinement (TASK-MONO-306); `STUCK_RECOVERY_FAILED` is **retained as a defensive fallback** (R4), no longer the primary terminal. Operator-initiated cancel via internal API (`/api/internal/orders/{orderId}/cancel`). |

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

## Multi-Tenancy & Marketplace (ADR-MONO-030)

> 모델 SoT = [specs/features/multi-tenancy-and-marketplace.md](../../features/multi-tenancy-and-marketplace.md) (ADR-MONO-030 Step 1). 본 섹션은 order-service 적용분만 선언한다. 컬럼/마이그레이션/셀러 귀속 구현 = Step 2(바깥 축)·Step 3(안쪽 축).

- **바깥 축 (tenant, M1-M7)**: `Order` / `OrderItem` 등 **모든 영속 row 에 `tenant_id` (NOT NULL)**. read = `WHERE tenant_id = <요청 컨텍스트>`(gateway `X-Tenant-Id`); cross-tenant 조회 = 404 (M3). 참조 = scm/erp.
- **안쪽 축 (seller) — order-line 단위 귀속**: order 헤더 = `tenant_id`. **각 `OrderItem` 은 그 상품의 `seller_id` 로 귀속** — 한 주문이 여러 셀러 상품을 포함 가능(공유 카탈로그/장바구니). 소비자(`CONSUMER`)는 셀러가 아니며 `seller_id` 권위 없음 — 항목의 귀속 속성일 뿐.
- **M5 (async 전파)**: 본 서비스의 outbox 이벤트(`order.order.placed`/`order.order.cancelled`) **봉투에 `tenant_id`**, 페이로드 항목에 `seller_id`. 소비 이벤트(`product.product.stock-changed`·`payment.payment.*`·`user.user.withdrawn`)의 tenant 컨텍스트 전파 — saga 상태전이가 테넌트 경계를 안 넘도록. **단, ADR-022 이행 루프 이벤트(`ecommerce.fulfillment.requested.v1` 등) `tenant_id` 스레딩 = Step 4 보류**(슬라이스 범위 밖, cross-project).
- **degradation (D8)**: default-tenant + default-seller 시드 → 단일 스토어 동작 byte-identical; standalone `tenant_id` 부재 → default tenant.
- **회귀 (M6)**: cross-tenant leak IT 필수 — 테넌트 A 주문이 B 토큰으로 안 보임.
- **saga 상호작용**: §"Saga / Long-running Flow" 의 stuck-detector sweep(`OrderStuckDetector`)은 **테넌트 무관 전역 sweep 유지 가능**(시스템 운영성); 단 복구가 주문을 변이할 때 그 주문의 `tenant_id` 컨텍스트를 보존. (상세 = Step 2 구현.)
- **M7 unbounded-query cap (TASK-BE-405)**: list endpoint(`GET /api/orders`, `GET /api/admin/orders`)는 **max page size = 100**(`OrderControllerUtils.MAX_PAGE_SIZE` → 공유 `PageQuery`(MAX_SIZE=100)) — `size` 가 100 초과 시 100 으로 clamp, `size < 1` 은 기본 20; 정상 size 는 그대로(backward-compatible). 단일 테넌트의 `LIMIT`-less / 과도 list 차단(M7 line 86). per-tenant API rate limit 은 gateway-edge(M2 layer-1, gateway-service/architecture.md § Per-tenant Rate Limit).
- **PROJECT.md `multi-tenant` trait**: Step 2(코드)와 함께 추가 (ADR-030 §D7 타이밍).

## Change Rule
Any architectural change to this service must be documented here first before implementation.