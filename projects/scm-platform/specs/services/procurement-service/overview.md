# procurement-service — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `procurement-service` |
| Project | `scm-platform` |
| Service Type | `rest-api` (primary) + webhook receiver |
| Architecture Style | **Hexagonal (Ports & Adapters)** — see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4 (Servlet), PostgreSQL, Redis (idempotency / cache), Kafka (outbox via `libs/java-messaging`), Resilience4j 2.2, Flyway |
| Deployable unit | `apps/procurement-service/` |
| Bounded Context | `Procurement` (PO + ASN + Supplier master, v1) |
| Persistent stores | PostgreSQL `scm_procurement` schema (Flyway) + Redis (idempotency / cache) + Kafka outbox table |
| Event publication | `scm.procurement.po.*.v1` (PO lifecycle), `scm.procurement.asn.*.v1` (ASN events) via transactional outbox |

## Responsibilities

- Full **PO lifecycle**: `DRAFT → SUBMITTED → ACKNOWLEDGED → CONFIRMED → PARTIAL/RECEIVED → CLOSED/CANCELED`.
- v1 internal **Supplier master** with AES-GCM-encrypted credentials (S6).
- Supplier integration via `SupplierAdapterPort` outbound port — stable `Idempotency-Key` for deduplication (S2).
- Webhook inbound: `supplier-ack` (`SUBMITTED → ACKNOWLEDGED`) and ASN delivery (`CONFIRMED → PARTIAL/RECEIVED`) — `X-Supplier-Signature` HMAC verified.
- Immutable audit trail: `po_status_history` + `audit_log` per state transition.

## Public surface

| Channel | Endpoint / Topic | Auth | Purpose |
|---|---|---|---|
| REST | `POST /api/procurement/po` | JWT + `Idempotency-Key` | draft PO |
| REST | `POST /api/procurement/po/{poId}/{submit,confirm,cancel}` | JWT + ROLE | state transitions |
| REST | `GET /api/procurement/po`, `/api/procurement/po/{poId}` | JWT + ROLE | list / detail |
| Webhook | `POST /api/procurement/webhooks/supplier-ack` | HMAC `X-Supplier-Signature` (gateway bypass) | SUBMITTED → ACKNOWLEDGED |
| Webhook | `POST /api/procurement/webhooks/asn` | HMAC `X-Supplier-Signature` (gateway bypass) | CONFIRMED → PARTIAL/RECEIVED |
| Kafka publish | `scm.procurement.po.*.v1`, `scm.procurement.asn.*.v1` | — | downstream consumers |

자세한 spec 은 [`../../contracts/http/procurement-api.md`](../../contracts/http/procurement-api.md) + [`../../contracts/events/scm-procurement-events.md`](../../contracts/events/scm-procurement-events.md) 참조.

## Key invariants

1. **`PurchaseOrderApplicationService` is the ONLY `@Transactional` command boundary** — domain / adapter 의 직접 TX 시작 금지.
2. **`PoStatusMachine.ensureTransitionAllowed` enforces all state transitions** — self-transition 금지; `CANCELED` / `CLOSED` 는 terminal.
3. **Stable Idempotency-Key for supplier calls** (S2) — 같은 key 재호출 시 동일 결과; supplier 5xx retry 안전.
4. **Encrypted supplier credentials** (S6) — AES-GCM at rest; plain-text 저장 금지.
5. **MUST NOT consult `inventory-visibility-service` for PO decisions** (S5) — cross-service decision boundary, sync HTTP / event call 금지.
6. **Vendor SDKs confined to `infrastructure/supplier/`** (S8, I7, I8) — domain / application 은 supplier SDK 직접 import 금지.
7. **`domain/` MUST NOT import Spring** — JPA annotation on entity 이 유일 예외.

## Owned Data

- `PurchaseOrder`, `PurchaseOrderLine`, `Supplier`, `AdvanceShipmentNotice` aggregates.
- `po_status_history` (append-only state history) + `audit_log` (append-only audit trail).

## Published Interfaces

- [`../../contracts/http/procurement-api.md`](../../contracts/http/procurement-api.md) (HTTP)
- [`../../contracts/events/scm-procurement-events.md`](../../contracts/events/scm-procurement-events.md) — PO + ASN events

## Dependent Systems

- PostgreSQL — `scm_procurement` schema
- Redis — idempotency / cache
- Kafka — event publication
- GAP (JWKS) — JWT validation (via gateway-service)
- Supplier mock REST (v1) — supplier integration target
- `libs/java-messaging` (outbox) + `libs/java-security` (HMAC verification)

## Out of scope (v1)

- Settlement decisions — deferred to `settlement-service` (S3).
- Demand forecasting — deferred to `demand-planning-service` (S4).
- `inventory_visibility` read-model ownership — owned by `inventory-visibility-service` (S5).
- EDI / SFTP supplier adapters — v2.
