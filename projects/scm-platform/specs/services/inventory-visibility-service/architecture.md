# inventory-visibility-service — Architecture

## Identity

| Field | Value |
|---|---|
| Service Name | `inventory-visibility-service` |
| Service Type | `rest-api` + `event-consumer` |
| Architecture Style | **Hexagonal** |
| Domain | scm |
| Traits | transactional, integration-heavy, batch-heavy |
| Primary language / stack | Java 21, Spring Boot 3.4 (Servlet stack) |
| Bounded Context | Inventory Visibility (cross-node read-model for SCM portfolio) |
| Deployable unit | `apps/inventory-visibility-service/` |
| Data store | PostgreSQL `scm_inventory_visibility` schema (Flyway) + Redis aggregation cache |
| Event publication | Kafka `scm.inventory.alert.v1` (best-effort, no outbox per ADR-MONO-005 Cat C) |
| Event consumption | Kafka 3 topics: `wms.inventory.received.v1` / `wms.inventory.adjusted.v1` / `wms.inventory.transferred.v1` (cross-project, EventDedupe idempotency) |

### Service Type Composition

`inventory-visibility-service` combines two service types in one deployable unit:

- `rest-api` for synchronous **read-only** queries (cross-node inventory snapshot, staleness metadata).
  4 read endpoints, no mutating REST (S5 — "Not for procurement decisions").
- `event-consumer` for asynchronous **inbound** event subscription:
  - `wms.inventory.received.v1` → upsert quantity snapshot
  - `wms.inventory.adjusted.v1` → delta apply on snapshot
  - `wms.inventory.transferred.v1` → dual-row update (source + destination nodes)

Both surfaces share the same domain model (`InventoryNode` + `InventorySnapshot` +
`NodeStaleness`) and persistence. Read both
`platform/service-types/rest-api.md` and `platform/service-types/event-consumer.md`
when implementing — documented exception to the "read exactly one service-type
file" rule, justified by the CQRS read-model role.

## Responsibilities

Cross-node inventory read-model. Consumes wms-platform inventory events (cross-project) and maintains a SKU × Node quantity snapshot. Exposes read-only REST API for operators and buyers (not for PO decisions — S5).

## Architecture Style Rationale

Hexagonal chosen because:
1. Multiple inbound adapters coexist naturally: Kafka consumers (3 topics) and REST controllers share the same domain core without coupling.
2. Domain logic (staleness evaluation, idempotency check) is framework-free and fully testable.
3. Outbound adapters for JPA, Redis, Kafka alert publisher are interchangeable — important for testing (H2 slice tests) and future migration.

## Layer Structure

```
domain/         ← Pure Java: InventoryNode, InventorySnapshot, NodeStaleness, EventDedupeRecord
application/    ← Use cases + port interfaces (no Spring annotations in domain)
adapter/
  inbound/
    web/        ← REST controllers (@RestController)
    messaging/  ← Kafka @KafkaListener consumers
  outbound/
    persistence/ ← JPA entities + Spring Data repositories + adapters
    messaging/  ← KafkaTemplate alert publisher
    cache/      ← Redis aggregation cache
    batch/      ← @Scheduled staleness detection (ShedLock)
config/         ← Spring @Configuration beans only
```

## Service Type Compliance

### rest-api
- Stateless JWT auth (OAuth2 RS, GAP JWKS)
- `tenant_id=scm` fail-closed at gateway + service level
- Read-only endpoints (no mutating REST)
- Standard error envelope `{ code, message }`
- Paginated list endpoints

### REST endpoints (v1)

All 4 read-only endpoints share the `/api/inventory-visibility` base path
(rewritten from `/api/v1/inventory-visibility/**` by gateway-service). All
require a JWT with `tenant_id ∈ {scm, *}`; no per-endpoint role/scope
differentiation in v1 (defense-in-depth via `TenantClaimEnforcer` filter).
All are **gateway-routed (public)** and listed in
[`gateway-public-routes.md`](../../contracts/http/gateway-public-routes.md).
Formal request / response shapes live in
[`inventory-visibility-api.md`](../../contracts/http/inventory-visibility-api.md).

| Method | Path | Public/Internal | Controller | Purpose |
|---|---|---|---|---|
| GET | `/api/inventory-visibility/snapshot` | public | `InventoryVisibilityController#getSnapshot` | cross-node paginated snapshot list (or single-node when `?nodeId=` passed) |
| GET | `/api/inventory-visibility/sku/{sku}` | public | `InventoryVisibilityController#getSkuSnapshot` | per-SKU cross-node breakdown with Redis cache (`X-Cache: HIT/MISS/UNAVAILABLE`) |
| GET | `/api/inventory-visibility/staleness` | public | `NodeStalenessController#getStaleness` | node-by-node staleness status (FRESH / STALE / UNREACHABLE) |
| GET | `/api/inventory-visibility/nodes` | public | `InventoryVisibilityController#getNodes` | node list with status (id, externalId, type, name, status) |

`/nodes` exposure decision (TASK-SCM-BE-008): **public**. Rationale:
1. The controller method has no `@PreAuthorize` and no role guard — code-level
   security is identical to the other 3 endpoints.
2. `inventory-visibility-api.md` already enumerates `/nodes` with the same
   response envelope shape as the other endpoints, treating it as part of the
   public contract.
3. Use case = ops dashboards that need to render the node list before letting
   an operator drill into per-node `/snapshot?nodeId=` or `/staleness` —
   public access is required for the dashboard flow to work end-to-end.
4. No PII or credential exposure (the response only includes `id`,
   `nodeExternalId`, `nodeType`, `name`, `status`).

All 4 endpoints carry the **S5 warning** (`meta.warning: "Not for procurement
decisions (S5)"`) so that consumers cannot mistake the eventually-consistent
read-model for an authoritative source.

### Local management endpoints

| Path | Auth | Description |
|---|---|---|
| `GET /actuator/health` | none | liveness/readiness probe |
| `GET /actuator/info` | none | build info |
| `GET /actuator/prometheus` | network-isolated | metrics scrape (internal docker network only) |

### event-consumer
- Consumer group: `scm-inventory-visibility-v1`
- 3 topics from wms-platform (cross-project): `wms.inventory.received.v1`, `wms.inventory.adjusted.v1`, `wms.inventory.transferred.v1`
- Manual ACK mode
- Retry: 3 attempts + DLT
- Idempotency: `event_dedupe` table keyed on `eventId` (UUID v7)

## batch-heavy First Code

`StalenessDetectionScheduler` — `@Scheduled` fixedDelay 5 minutes, ShedLock-protected.
Evaluates all node staleness records, updates status, publishes SNAPSHOT_STALE alerts.
This is the first `batch-heavy` trait code in scm-platform (TASK-SCM-BE-003).

## Security

- OAuth2 Resource Server (RS256)
- JWKS: `${OIDC_ISSUER_URL}/oauth2/jwks`
- Validators: JwtTimestampValidator + AllowedIssuersValidator + TenantClaimValidator (tenant_id=scm)
- Service-level TenantClaimEnforcer filter (defense-in-depth)
- Public paths: `/actuator/health`, `/actuator/info`, `/actuator/prometheus`

## Dependencies

| Direction | Target | Protocol | Notes |
|---|---|---|---|
| In | scm-platform gateway-service | HTTP `/api/v1/inventory-visibility/**` | tenant-validated JWT 통과 후 라우팅 |
| In | wms-platform Kafka | Consumer subscribed to `wms.inventory.{received,adjusted,transferred}.v1` | EventDedupe 멱등; cross-project 첫 사례 |
| Out | PostgreSQL (inventory-visibility schema) | JDBC | InventoryNode / InventorySnapshot / NodeStaleness / EventDedupe |
| Out | Redis | TCP | read-model cache (fail-OPEN) |
| Out | GAP `/oauth2/jwks` | HTTPS | JWT 서명 검증 (libs/java-security) |
