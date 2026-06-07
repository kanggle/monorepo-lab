# payment-service — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `payment-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `rest-api` |
| Architecture Style | **Hexagonal** — domain + application + adapter, see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, PostgreSQL, Kafka, Toss Payments SDK (via outbound port), `libs/java-messaging` (transactional outbox), Resilience4j |
| Deployable unit | `apps/payment-service/` |
| Bounded Context | `Payment` |
| Persistent stores | PostgreSQL (payment aggregate) + Kafka outbox table (`libs/java-messaging`) |
| Event publication | `payment.payment.completed` (PaymentCompleted), `payment.payment.refunded` (PaymentRefunded) |

## Responsibilities

- Create `PENDING` payment record on `OrderPlaced` event consumption (idempotent on `orderId`).
- Confirm payment via external PG (Toss Payments) — `confirmPayment` outbound port wrapped in CB + retry + bulkhead per ADR-MONO-005 § D6 (TASK-BE-139).
- Process refund on `OrderCancelled` event — `cancelPayment` outbound port (same R4j wrap).
- Publish `PaymentCompleted` / `PaymentRefunded` via transactional outbox.
- Expose payment status query by `orderId`.

## Public surface

| Channel | Endpoint / Topic | Auth | Purpose |
|---|---|---|---|
| REST | `GET /api/payments/{orderId}` | JWT (owner / ROLE_ADMIN) | payment status |
| REST | `POST /api/webhooks/payment/toss` | IP allow-list (gateway bypass) | Toss webhook (confirmation callback) |
| Kafka consume | `order.order.placed` | — | PENDING record creation |
| Kafka consume | `order.order.cancelled` | — | refund trigger |
| Kafka publish | `payment.payment.completed`, `payment.payment.refunded` | — | order / notification consumers |

자세한 spec 은 [`../../contracts/http/payment-api.md`](../../contracts/http/payment-api.md) + [`../../contracts/events/payment-events.md`](../../contracts/events/payment-events.md) 참조.

## Key invariants

1. **State + outbox atomic commit** — payment row 와 outbox event 가 한 TX. dual-write 금지 (architecture.md § Forbidden Dependencies).
2. **External PG behind outbound port** — domain / application 은 `Toss Payments SDK` 직접 import 금지; `PgGatewayPort` interface 뒤로만 접근.
3. **Refund precondition** — `cancelPayment` 는 payment status = `COMPLETED` 일 때만 허용; 다른 상태 → `IllegalPaymentRefundState`.
4. **Adapter ↛ business policy** — adapter 는 PG 호출 + transport 변환만; "refund 금액 = 전액" 같은 결정은 application service.
5. **4xx vs 5xx 구분** — PG 4xx → `PgConfirmFailedException` (payment FAILED, retry 금지); 5xx/timeout/CB-OPEN → `PgGatewayUnavailableException` (retry 후 fallback, payment 상태 유지).

## Owned Data

- payment aggregate (`paymentId`, `orderId`, `userId`, `amount`, `status`, `paymentMethod`, `paymentKey`, `receiptUrl`, `createdAt`, `paidAt`, `refundedAt`).

## Published Interfaces

- [`../../contracts/http/payment-api.md`](../../contracts/http/payment-api.md) (HTTP)
- [`../../contracts/events/payment-events.md`](../../contracts/events/payment-events.md) — `PaymentCompleted`, `PaymentRefunded`

## Dependent Systems

- `order-service` (events: `OrderPlaced`, `OrderCancelled`)
- Toss Payments API (external PG, behind outbound port)
- PostgreSQL — payment persistence
- Kafka — event publication / consumption

## Out of scope (v1)

- Order management — `order-service`.
- Product catalog / inventory — `product-service`.
- Authentication — `auth-service` (deprecated) → IAM.
- PCI-DSS scope — card data never persisted; Toss widget delegates to PG (ecommerce `PROJECT.md` § Out of Scope, regulated).
