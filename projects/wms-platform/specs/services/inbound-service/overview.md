# inbound-service — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `inbound-service` |
| Project | `wms-platform` |
| Service Type | `rest-api` (primary) + webhook receiver |
| Architecture Style | **Hexagonal (Ports & Adapters)** — see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, PostgreSQL, Kafka (consumer + outbox), Spring Data JPA, ERP webhook HMAC validation |
| Deployable unit | `apps/inbound-service/` |
| Bounded Context | `Inbound` |
| Persistent stores | PostgreSQL (ASN / Inspection / PutawayInstruction + master read-model cache) + Kafka outbox |
| Event publication | `inbound.asn.received.v1`, `inbound.inspection.completed.v1`, `inbound.putaway.completed.v1` (per [`inbound-events.md`](../../contracts/events/inbound-events.md)) |

## Responsibilities

- Single system of record for **ASN identity, inspection results, putaway instructions**.
- Manage ASN state machine: `CREATED → INSPECTING → PUTAWAY → CLOSED` (strict T4 ordering).
- Accept ERP webhooks (HMAC-signed) and manual ops REST entries for ASN creation.
- Fire `inbound.putaway.completed` on confirmed putaway → `inventory-service` credits `available` stock.
- Maintain local read-model cache of master-service reference data.

## Public surface

| Channel | Endpoint / Topic | Auth | Purpose |
|---|---|---|---|
| REST | `POST/GET /api/v1/inbound/asns/**` | JWT + ROLE | ASN CRUD |
| REST | `POST /api/v1/inbound/inspections/**` | JWT + ROLE | inspection recording |
| REST | `POST /api/v1/inbound/putaway/**` | JWT + ROLE | putaway instruction + confirmation |
| Webhook | `POST /webhooks/erp/asn` | HMAC (gateway bypass) | ERP-driven ASN creation |
| Kafka consume | `master.{warehouse,zone,location,sku,partner,lot}.*` | — | read-model cache refresh |
| Kafka publish | `inbound.asn.received.v1`, `inbound.inspection.completed.v1`, `inbound.putaway.completed.v1` | — | inventory + notification consumers |

자세한 spec 은 [`../../contracts/http/inbound-service-api.md`](../../contracts/http/inbound-service-api.md) + [`../../contracts/events/inbound-events.md`](../../contracts/events/inbound-events.md) + [`../../contracts/webhooks/erp-asn-webhook.md`](../../contracts/webhooks/erp-asn-webhook.md) 참조.

## Key invariants

1. **ASN state transitions strictly ordered** — `CREATED → INSPECTING → PUTAWAY → CLOSED`; 역방향 / skip 금지 (T4, `IllegalAsnTransition`).
2. **Webhook replay protection** — ERP-supplied idempotency key 기반 dedupe (I6); 같은 key 재수신 = no-op.
3. **Putaway + outbox atomic** — putaway 확정 과 outbox row 가 한 TX (T3).
4. **Inspection mismatch must be recorded before putaway** — quantity / SKU mismatch 발견 시 `Inspection` 에 row 남기고 ops 검토; 자동 putaway 진행 금지.
5. **Only inbound-service owns ASN/inspection/putaway lifecycle** — 다른 service 의 직접 mutation 금지.

## Owned Data

- ASN, Inspection, PutawayInstruction rows.

## Published Interfaces

- [`../../contracts/http/inbound-service-api.md`](../../contracts/http/inbound-service-api.md) (HTTP)
- [`../../contracts/events/inbound-events.md`](../../contracts/events/inbound-events.md) — 3 events
- [`../../contracts/webhooks/erp-asn-webhook.md`](../../contracts/webhooks/erp-asn-webhook.md) (webhook)

## Dependent Systems

- PostgreSQL — ASN / inspection / putaway persistence
- Kafka — event consumption + publication
- `master-service` (snapshot events)
- ERP system (inbound webhook source)
- `inventory-service` (consumer of `inbound.putaway.completed`)

## Out of scope (v1)

- Inventory quantities — `inventory-service`.
- Returns / RMA inbound — v2.
- Multi-leg cross-warehouse receiving — v2.
- ERP outbound-order integration — `outbound-service`.
