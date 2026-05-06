# inventory-visibility-service — Architecture

## Service Identity

| Field | Value |
|---|---|
| Service Name | `inventory-visibility-service` |
| Service Type | `rest-api` + `event-consumer` |
| Architecture Style | **Hexagonal** |
| Domain | scm |
| Traits | transactional, integration-heavy, batch-heavy |

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

See `dependencies.md`.
