# inventory-service — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `inventory-service` |
| Project | `wms-platform` |
| Service Type | `rest-api` + `event-consumer` (dual) |
| Architecture Style | **Hexagonal (Ports & Adapters)** — see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, PostgreSQL, Kafka (consumer + outbox via `libs/java-messaging`), Spring Data JPA |
| Deployable unit | `apps/inventory-service/` |
| Bounded Context | `Inventory` |
| Persistent stores | PostgreSQL (Inventory buckets + InventoryMovement history + StockAdjustment / StockTransfer + master read-model cache) + Kafka outbox |
| Event publication | `inventory.adjusted.v1`, `inventory.transferred.v1`, `inventory.reserved.v1`, `inventory.confirmed.v1` (per [`inventory-events.md`](../../contracts/events/inventory-events.md)) |

## Responsibilities

- Single system of record for **on-hand stock** — 3 buckets (`available` / `reserved` / `damaged`) per (location, sku, lot).
- Append-only **InventoryMovement** history for every quantity mutation (W2).
- **Atomic reserve → confirm two-phase protocol** for outbound picking saga (W4 reserve, W5 consume).
- Consume `inbound.putaway.completed` → credit `available` stock; `outbound.shipping.confirmed` → decrement `reserved`.
- Maintain local read-model cache of master-service reference data (sibling-pattern decoupling).

## Public surface

| Channel | Endpoint / Topic | Auth | Purpose |
|---|---|---|---|
| REST | `POST /api/v1/inventory/adjustments` | JWT + ROLE | manual stock adjustment |
| REST | `POST /api/v1/inventory/transfers` | JWT + ROLE | inter-location transfer |
| REST | `POST /api/v1/inventory/reserves` | JWT + ROLE | reserve for outbound (saga step 1) |
| REST | `POST /api/v1/inventory/confirms` | JWT + ROLE | confirm consumption (saga step 2) |
| REST | `GET /api/v1/inventory/**` | JWT + ROLE | query buckets / movements |
| Kafka consume | `inbound.putaway.completed`, `outbound.picking.requested`, `outbound.shipping.confirmed` | — | saga reply + putaway credit |
| Kafka consume | `master.location.*`, `master.sku.*` | — | read-model cache refresh |
| Kafka publish | `inventory.{adjusted,transferred,reserved,confirmed}.v1` | — | saga + analytics consumers |

자세한 spec 은 [`../../contracts/http/inventory-service-api.md`](../../contracts/http/inventory-service-api.md) + [`../../contracts/events/inventory-events.md`](../../contracts/events/inventory-events.md) 참조.

## Key invariants

1. **`available + reserved + damaged = on_hand`** at all times; no bucket can go negative (W1).
2. **Every quantity change appends ImmutableMovement record** in the same TX (W2).
3. **Reserve atomic** — validate → decrement `available` → increment `reserved` → outbox 가 한 TX (W4).
4. **eventId-based dedupe** on every inbound Kafka event (T8) — duplicate event = no-op.
5. **No direct cross-service DB join** — master data via local read-model cache only (architecture.md § Forbidden Dependencies).
6. **Outbox atomic with aggregate write** (T3) — dual-write 금지.

## Owned Data

- Inventory rows (location / sku / lot / `available` / `reserved` / `damaged`).
- InventoryMovement history (append-only).
- StockAdjustment + StockTransfer aggregates.

## Published Interfaces

- [`../../contracts/http/inventory-service-api.md`](../../contracts/http/inventory-service-api.md) (HTTP)
- [`../../contracts/events/inventory-events.md`](../../contracts/events/inventory-events.md) — 4 events

## Dependent Systems

- PostgreSQL — inventory + movement persistence
- Kafka — event consumption + publication
- `master-service` (snapshot events for cache)
- `inbound-service` (putaway events)
- `outbound-service` (picking / shipping events)

## Out of scope (v1)

- Master data identity — `master-service`.
- ASN / order lifecycle — `inbound-service` / `outbound-service`.
- Multi-warehouse cross-boundary transfer — v2.
- Lot identity management — `master-service`.
