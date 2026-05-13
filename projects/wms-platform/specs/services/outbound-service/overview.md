# outbound-service — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `outbound-service` |
| Project | `wms-platform` |
| Service Type | `rest-api` + `event-consumer` (dual; saga orchestrator) |
| Architecture Style | **Hexagonal (Ports & Adapters)** — see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, PostgreSQL, Kafka (consumer + outbox via `libs/java-messaging`), Spring Data JPA, Resilience4j, TMS adapter |
| Deployable unit | `apps/outbound-service/` |
| Bounded Context | `Outbound` |
| Persistent stores | PostgreSQL (Order / PickingRequest / PickingConfirmation / PackingUnit / ShippingRecord / Saga state + master read-model cache) + Kafka outbox |
| Event publication | `outbound.picking.requested.v1`, `outbound.picking.confirmed.v1`, `outbound.shipping.confirmed.v1` (per [`outbound-events.md`](../../contracts/events/outbound-events.md)) |

## Responsibilities

- Single system of record for **order, picking, packing, shipping lifecycle**.
- Orchestrate 4-step picking saga: `Order → PickingRequest → Packing → ShippingConfirmation` (T4 ordering, ADR-MONO-005 § D6 Category A).
- Drive `inventory-service` reserve (W4) and confirm-consumed (W5) via saga reply events.
- Receive ERP order webhooks (HMAC-signed) and ops manual entry.
- Hand off shipment-ready notification to external TMS (Resilience4j wrap per ADR-MONO-005 § D6 Category B).

## Public surface

| Channel | Endpoint / Topic | Auth | Purpose |
|---|---|---|---|
| REST | `POST/GET /api/v1/outbound/orders/**` | JWT + ROLE | order CRUD |
| REST | `POST /api/v1/outbound/picking/**` | JWT + ROLE | picking confirmation |
| REST | `POST /api/v1/outbound/packing/**` | JWT + ROLE | packing confirmation |
| REST | `POST /api/v1/outbound/shipping/**` | JWT + ROLE | shipping confirmation |
| Webhook | `POST /webhooks/erp/order` | HMAC (gateway bypass) | ERP-driven order creation |
| Kafka consume | `inventory.reserved.v1`, `inventory.confirmed.v1` | — | saga reply channel |
| Kafka consume | `master.*` (6 aggregate types) | — | read-model cache refresh |
| Kafka publish | `outbound.picking.requested.v1`, `outbound.picking.confirmed.v1`, `outbound.shipping.confirmed.v1` | — | inventory + notification consumers |
| HTTP outbound | TMS adapter (R4j wrap) | — | shipment-ready notification |

자세한 spec 은 [`../../contracts/http/outbound-service-api.md`](../../contracts/http/outbound-service-api.md) + [`../../contracts/events/outbound-events.md`](../../contracts/events/outbound-events.md) + [`../../contracts/webhooks/erp-order-webhook.md`](../../contracts/webhooks/erp-order-webhook.md) 참조. saga policy: [`../../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md`](../../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md).

## Key invariants

1. **Saga state transitions strictly ordered with compensation** — failure 시 compensating action 발행 (T4, ADR-MONO-005 § D6 Category A).
2. **`outbound.picking.requested` only after Order CONFIRMED** — `PENDING` order 의 picking 요청 금지.
3. **`outbound.shipping.confirmed` only after physical pack + operator confirmation** — auto-emit 금지.
4. **eventId-based dedupe on all consumed events** (T8) — saga reply 중복 = no-op.
5. **Saga + outbox atomic** — saga state mutation 과 outbox row 가 한 TX (T3).
6. **TMS handover failure must not block shipping confirmation** — TMS 5xx → fallback + retry, ShippingRecord 자체는 commit (Category B fallback per ADR-MONO-005).

## Owned Data

- Order, PickingRequest, PickingConfirmation, PackingUnit, ShippingRecord aggregate rows.
- Saga aggregate rows (saga state machine).

## Published Interfaces

- [`../../contracts/http/outbound-service-api.md`](../../contracts/http/outbound-service-api.md) (HTTP)
- [`../../contracts/events/outbound-events.md`](../../contracts/events/outbound-events.md) — 3 events
- [`../../contracts/webhooks/erp-order-webhook.md`](../../contracts/webhooks/erp-order-webhook.md) (webhook)

## Dependent Systems

- PostgreSQL — outbound + saga persistence
- Kafka — event consumption + publication
- `master-service` (snapshots)
- `inventory-service` (saga reply events)
- ERP system (order webhook source)
- TMS (external HTTP, R4j wrap)

## Out of scope (v1)

- Inventory quantities — `inventory-service` (W4/W5 owner).
- Returns / RMA outbound — v2.
- Multi-warehouse cross-boundary picking — v2.
- Carrier rating / TMS quote — external TMS 책임.
