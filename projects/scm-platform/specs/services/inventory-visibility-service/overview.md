# inventory-visibility-service — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `inventory-visibility-service` |
| Project | `scm-platform` |
| Service Type | `rest-api` (read-only) + `event-consumer` |
| Architecture Style | **Hexagonal (Ports & Adapters)** — see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, PostgreSQL, Redis (read-model cache, fail-open), Kafka consumer, **ShedLock** (multi-replica scheduler safety), Flyway |
| Deployable unit | `apps/inventory-visibility-service/` |
| Bounded Context | `Inventory Visibility` (cross-node SKU × Node quantity read-model) |
| Persistent stores | PostgreSQL `inventory-visibility` schema (InventoryNode + InventorySnapshot + NodeStaleness + EventDedupe) + Redis (aggregation cache, fail-open) |
| Event publication | `scm.inventory.alert.v1` — SNAPSHOT_STALE alerts only (internal) |

## Responsibilities

- Consume **`wms-platform` inventory events** (cross-project) to maintain SKU × Node snapshot read-model.
- Expose **read-only REST API** for operators / buyers — explicitly NOT for PO decisions (S5).
- Idempotent event deduplication via `event_dedupe` table keyed on UUID v7 `eventId`.
- Batch **staleness detection** every 5 min via ShedLock-protected `@Scheduled` job — FRESH / STALE / UNREACHABLE classification.
- Publish `SNAPSHOT_STALE` Kafka alerts when nodes go STALE / UNREACHABLE.

## Public surface

| Channel | Endpoint / Topic | Auth | Purpose |
|---|---|---|---|
| REST | `GET /api/inventory-visibility/snapshot` | JWT + ROLE | paginated cross-node list (filter `?nodeId=`) |
| REST | `GET /api/inventory-visibility/sku/{sku}` | JWT + ROLE | per-SKU breakdown (`X-Cache: HIT/MISS/UNAVAILABLE`) |
| REST | `GET /api/inventory-visibility/staleness` | JWT + ROLE | FRESH / STALE / UNREACHABLE per node |
| REST | `GET /api/inventory-visibility/nodes` | JWT + ROLE | node list |
| Kafka consume | `wms.inventory.{received,adjusted,transferred}.v1` (consumer group `scm-inventory-visibility-v1`) | — | snapshot 갱신 |
| Kafka publish | `scm.inventory.alert.v1` (SNAPSHOT_STALE) | — | 내부 alert |
| Scheduler | `StalenessDetectionScheduler` (`@Scheduled(fixedDelay = 5 min)`, ShedLock-protected) | — | 5분 주기 staleness 판정 |

자세한 spec 은 [`../../contracts/http/inventory-visibility-api.md`](../../contracts/http/inventory-visibility-api.md) + [`../../contracts/events/inventory-visibility-subscriptions.md`](../../contracts/events/inventory-visibility-subscriptions.md) 참조.

## Key invariants

1. **All 4 REST endpoints carry S5 warning** — response `meta.warning: "Not for procurement decisions (S5)"`. procurement-service 가 본 read-model 을 PO 결정에 사용 금지.
2. **Event deduplication mandatory** — same `eventId` 두 번 적용해도 snapshot 동일 (event_dedupe table guard).
3. **Manual ACK + 3 retries + DLT** — Kafka consumer 의 silent discard 금지; retry 실패 시 DLT 라우팅.
4. **Read-only REST** — mutating HTTP endpoint 0; 쓰기 권한은 `wms-platform` 만 (write authority).
5. **`StalenessDetectionScheduler` MUST be ShedLock-guarded** — multi-replica 환경에서 같은 job 동시 실행 방지.
6. **Redis cache fail-open** — cache miss / Redis outage 시 fallback to DB; cache 장애로 5xx 발생 금지 (`X-Cache: UNAVAILABLE` 헤더 표시).

## Owned Data

- InventoryNode, InventorySnapshot, NodeStaleness, EventDedupe rows.
- **Read-model only** — source of truth 는 `wms-platform` 의 inventory aggregates.

## Published Interfaces

- [`../../contracts/http/inventory-visibility-api.md`](../../contracts/http/inventory-visibility-api.md) (HTTP, read-only)
- [`../../contracts/events/inventory-visibility-subscriptions.md`](../../contracts/events/inventory-visibility-subscriptions.md) — consumed topics + alert publish envelope

## Dependent Systems

- `wms-platform` Kafka (3 topics: `wms.inventory.{received,adjusted,transferred}.v1`)
- PostgreSQL — `inventory-visibility` schema
- Redis — aggregation cache (fail-open)
- GAP (JWKS) — JWT validation (via gateway-service)
- `gateway-service` — front entry

## Out of scope (v1)

- Mutating inventory state — write authority 는 `wms-platform` 소유.
- Procurement decision support — explicitly forbidden by S5 (response meta warning 으로 표시).
- Per-endpoint role / scope differentiation — v2 (현재는 JWT + ROLE 균일).
- EDI / external system ingest — v1 은 wms Kafka 만 inbound channel.
