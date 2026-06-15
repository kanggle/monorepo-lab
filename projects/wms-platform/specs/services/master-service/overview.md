# master-service — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `master-service` |
| Project | `wms-platform` |
| Service Type | `rest-api` |
| Architecture Style | **Hexagonal (Ports & Adapters)** — see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, PostgreSQL, Kafka, Spring Data JPA, `libs/java-messaging` (transactional outbox) |
| Deployable unit | `apps/master-service/` |
| Bounded Context | `Master Data` |
| Persistent stores | PostgreSQL (Warehouse / Zone / Location / SKU / Partner / Lot identity tables) + Kafka outbox table |
| Event publication | `master.warehouse.*`, `master.zone.*`, `master.location.*`, `master.sku.*`, `master.partner.*`, `master.lot.*` (per [`master-events.md`](../../contracts/events/master-events.md)) |

## Responsibilities

- Single system of record for all WMS reference data — 6 aggregate types (W/Z/L/S/P/Lot).
- Enforce **referential integrity** before delete — 활성 참조 존재 시 deletion 차단 (W6).
- Enforce **location code uniqueness** globally across the entire system at all times (W3).
- Publish snapshot events on every mutation — consumed by sibling services as read-model cache source.
- Single source for SKU tracking type, Partner classification, Lot identity (balance 는 `inventory-service` 가 소유).

## Public surface

| Channel | Endpoint / Topic | Auth | Purpose |
|---|---|---|---|
| REST | `GET/POST/PUT/DELETE /api/v1/master/warehouses/**` | JWT + ROLE | warehouse CRUD |
| REST | `… /api/v1/master/zones/**` | JWT + ROLE | zone CRUD |
| REST | `… /api/v1/master/locations/**` | JWT + ROLE | location CRUD |
| REST | `… /api/v1/master/skus/**` | JWT + ROLE | SKU CRUD |
| REST | `… /api/v1/master/partners/**` | JWT + ROLE | partner CRUD |
| REST | `… /api/v1/master/lots/**` | JWT + ROLE | lot identity CRUD |
| Kafka publish | `master.{warehouse,zone,location,sku,partner,lot}.{created,updated,deactivated}` | — | sibling services 의 read-model cache 갱신 |

자세한 spec 은 [`../../contracts/http/master-service-api.md`](../../contracts/http/master-service-api.md) + [`../../contracts/events/master-events.md`](../../contracts/events/master-events.md) 참조.

## Key invariants

1. **Location code globally unique** — W3, 위반 시 `LocationCodeConflict` 예외.
2. **Cannot delete entity with active references** — W6, sibling service 가 참조하는 경우 deletion 차단.
3. **Only master-service may mutate these 6 entity types** — 다른 service 의 직접 mutation 금지; HTTP / event 만 통과.
4. **Lot identity here, Lot balance in inventory-service** — `master-service` 는 lot 정체성만; 수량 변동은 `inventory-service` 가 소유.
5. **Outbox atomic with aggregate write** — T3, mutation 과 outbox row 가 한 TX 안에서 atomic; dual-write 금지.

## Owned Data

- Warehouse, Zone, Location, SKU, Partner, Lot identity rows (balance 제외).

## Published Interfaces

- [`../../contracts/http/master-service-api.md`](../../contracts/http/master-service-api.md) (HTTP)
- [`../../contracts/events/master-events.md`](../../contracts/events/master-events.md) — 6 aggregate snapshot events

## Dependent Systems

- PostgreSQL — master data persistence
- Kafka — event publication
- (no outbound dependency — upstream anchor)

## Out of scope (v1)

- Inventory quantities — `inventory-service`.
- ASN / order lifecycle — `inbound-service` / `outbound-service`.
- ERP / PIM sync adapters — v2.
- Lot balance tracking — `inventory-service` (T6 lot balance invariant).
