# shipping-service — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `shipping-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `rest-api` |
| Architecture Style | **DDD-style** — see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, PostgreSQL, Kafka, `libs/java-messaging` (transactional outbox) |
| Deployable unit | `apps/shipping-service/` |
| Bounded Context | `Shipping` |
| Persistent stores | PostgreSQL (shipping aggregate + status history) + Kafka outbox table |
| Event publication | `shipping.shipping.status-changed` (ShippingStatusChanged) |

## Responsibilities

- Create shipping record on `OrderConfirmed` event (idempotent by `orderId` — duplicate event = no-op).
- Enforce **strict unidirectional status transitions**: `PREPARING → SHIPPED → IN_TRANSIT → DELIVERED`.
- Provide shipping status query by `orderId`.
- Publish `ShippingStatusChanged` on every transition (consumed by `notification-service`).
- **External carrier integration (first increment, TASK-BE-293)**: an admin-triggered
  `refresh-tracking` reads the shipment's carrier status via `CarrierTrackingPort` and
  advances it forward. Default `shipping.carrier.mode=mock` (no-op = the v1 admin-driven
  baseline, net-zero); `mode=http` uses the real provider adapter (`integration-heavy`
  pattern: RestClient + resilience timeouts, best-effort/never-throw). The carrier-driven
  **auto-collect scheduler** (poll all in-flight shipments) remains a later increment.

## Public surface

| Channel | Endpoint / Topic | Auth | Purpose |
|---|---|---|---|
| REST | `GET /api/shipping?orderId={id}` | JWT (owner / ROLE_ADMIN) | shipping status |
| REST | `PUT /api/admin/shipping/{id}/status` | JWT + ROLE_ADMIN | manual status transition (v1) |
| REST | `PUT /api/admin/shipping/{id}/tracking` | JWT + ROLE_ADMIN | set carrier + tracking number |
| REST | `POST /api/shippings/{id}/refresh-tracking` | `X-User-Role: ADMIN` | carrier-driven status refresh (TASK-BE-293, best-effort) |
| Kafka consume | `order.order.confirmed` | — | shipping record creation |
| Kafka publish | `shipping.shipping.status-changed` | — | notification consumers |

자세한 spec 은 [`../../contracts/http/shipping-api.md`](../../contracts/http/shipping-api.md) + [`../../contracts/events/shipping-events.md`](../../contracts/events/shipping-events.md) 참조.

## Key invariants

1. **Status transitions strictly unidirectional** — `PREPARING → SHIPPED → IN_TRANSIT → DELIVERED`; backward / skip 금지 (`IllegalShippingTransition`).
2. **One shipping record per order** — `orderId` unique constraint + domain check.
3. **Idempotent creation on duplicate `OrderConfirmed`** — same `orderId` 재수신 시 새 record 생성 안 함 (no-op + WARN log).
4. **No order / payment ownership** — shipping-service 는 shipping aggregate 만 소유.
5. **Domain ↛ framework** — domain layer Spring / JPA / Kafka SDK 직접 import 금지.

## Owned Data

- shipping (`shippingId`, `orderId`, `userId`, `status`, `trackingNumber`, `carrier`, status history, timestamps).

## Published Interfaces

- [`../../contracts/http/shipping-api.md`](../../contracts/http/shipping-api.md) (HTTP)
- [`../../contracts/events/shipping-events.md`](../../contracts/events/shipping-events.md) — `ShippingStatusChanged`

## Dependent Systems

- PostgreSQL — shipping persistence
- Kafka — event consumption + publication
- `order-service` (events: `OrderConfirmed`)

## Out of scope (v1)

- Order processing — `order-service`.
- Payment processing — `payment-service`.
- Notification delivery — `notification-service`.
- External carrier API 통합 — **first increment done (TASK-BE-293)**: admin-triggered `refresh-tracking` + `CarrierTrackingPort` (mock/http). 잔여 v2 = 무인 자동수집 스케줄러(poll) + 실 제공사(CJ대한통운 / Lotte) 어댑터 배선 + webhook 수신.
- Shipping cost calculation — order-service / promotion-service 가 처리.
