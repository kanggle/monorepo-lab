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
- **External carrier integration — outbound pull (TASK-BE-293)**: an admin-triggered
  `refresh-tracking` reads the shipment's carrier status via `CarrierTrackingPort` and
  advances it forward. Default `shipping.carrier.mode=mock` (no-op = the v1 admin-driven
  baseline, net-zero); `mode=http` uses the real provider adapter (`integration-heavy`
  pattern: RestClient + resilience timeouts, best-effort/never-throw).
  **Provider = a logistics aggregator** (물류 중개 플랫폼, ADR-007 D2) — not a single
  carrier, not a contract-partner-direct carrier API: `base-url`/`api-key` point at the
  aggregator (one endpoint / one key / one unified status scheme that `CarrierStatusMapper`
  maps; the `carrier` value is the aggregator-internal carrier code). A non-blank aggregator
  status that fails to map is **observable** (TASK-BE-362) — `carrier_status_unmapped`
  counter + WARN log (raw value) — so a new/changed aggregator code never silently stalls a
  shipment.
- **External carrier integration — inbound webhook (TASK-BE-294)**: the aggregator POSTs a
  tracking delivery to `carrier-webhook`; it is **HMAC-SHA256 signature-authenticated**
  (`shipping.carrier.webhook.secret`, blank default = fail-closed/net-zero), **idempotent**
  (dedup by carrier `deliveryId`), and advances the shipment forward (shared
  `ShippingForwardAdvancer`, forward-only). The carrier-driven **auto-collect scheduler**
  (poll all in-flight shipments) remains a later increment.

## Public surface

| Channel | Endpoint / Topic | Auth | Purpose |
|---|---|---|---|
| REST | `GET /api/shipping?orderId={id}` | JWT (owner / ROLE_ADMIN) | shipping status |
| REST | `PUT /api/admin/shipping/{id}/status` | JWT + ROLE_ADMIN | manual status transition (v1) |
| REST | `PUT /api/admin/shipping/{id}/tracking` | JWT + ROLE_ADMIN | set carrier + tracking number |
| REST | `POST /api/shippings/{id}/refresh-tracking` | `X-User-Role: ADMIN` | carrier-driven status refresh (TASK-BE-293, best-effort) |
| REST | `POST /api/shippings/carrier-webhook` | HMAC sig (`X-Carrier-Signature`) | inbound carrier tracking webhook (TASK-BE-294, idempotent, best-effort) |
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
- External carrier API 통합 — **outbound pull done (TASK-BE-293)** + **inbound webhook done (TASK-BE-294)** + **aggregator 매핑·credential 배선 done (TASK-BE-362, ADR-007 D2)**: admin-triggered `refresh-tracking` + `CarrierTrackingPort` (mock/http, provider=물류 중개 aggregator) 및 signature-authenticated `carrier-webhook` (idempotent), 중개사 통일 상태코드 매핑표 + 미매핑 가시화(`carrier_status_unmapped`). 잔여 v2 = `carrier-webhook` gateway 공개 노출(TASK-BE-359) + 무인 자동수집 스케줄러/poll(TASK-BE-360) + webhook dedup 보존 cleanup sweep(TASK-BE-361) + 실 중개사 sandbox 계정 배선.
- Shipping cost calculation — order-service / promotion-service 가 처리.
