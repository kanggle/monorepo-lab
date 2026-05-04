---
status: placeholder
last_updated: 2026-05-05
owners: scm-platform/backend
---

# scm-platform — Public Gateway Routes

> **Status**: v1 placeholder catalogue. The gateway-service (TASK-SCM-BE-001)
> declares two routes; the downstream services are not yet bootstrapped
> (TASK-SCM-BE-002 procurement-service, TASK-SCM-BE-003 inventory-visibility-service).
> Until those land, calls to these paths receive 503 Service Unavailable
> (Spring Cloud Gateway default for unreachable downstream).

This document enumerates every externally reachable HTTP route on
`http://scm.local/` (Traefik-routed). All routes are owned by
[gateway-service](../../services/gateway-service/architecture.md) at the
edge; the listed downstream services own the actual handlers once they
exist.

## Authentication contract

Every route under `/api/**` requires:

- `Authorization: Bearer <RS256 JWT>` issued by GAP.
- JWT signature verifies against GAP's JWKS (`http://gap.local/oauth2/jwks`).
- `iss` claim is one of: SAS issuer URL (default `http://gap.local`), legacy
  `global-account-platform` (D2-b deprecation window).
- `tenant_id` claim is `scm` or `*` (SUPER_ADMIN wildcard). Any other tenant
  → 403 `TENANT_FORBIDDEN`.

scm v1 = backend only — the **primary** authentication shape is
`grant_type=client_credentials` against the V0013-seeded
`scm-platform-internal-services-client`. Human-user (PKCE / authorization_code)
flow is deferred to v2 when a frontend ships.

## Error envelope

All gateway-emitted errors follow `platform/error-handling.md`:

```json
{
  "code": "TENANT_FORBIDDEN",
  "message": "tenant_id 'wms' is not allowed",
  "timestamp": "2026-05-05T00:00:00Z"
}
```

| HTTP | code | When |
|---|---|---|
| 401 | UNAUTHORIZED | missing / expired / invalid signature; tampered token |
| 403 | TENANT_FORBIDDEN | `tenant_id` claim does not match `scm` (and is not `*`) |
| 403 | FORBIDDEN | authorised token but lacks scope/role for the operation (downstream-emitted) |
| 429 | RATE_LIMIT_EXCEEDED | per-account-or-IP quota exhausted; `Retry-After: 1` set |
| 503 | SERVICE_UNAVAILABLE | downstream service unreachable (v1 placeholder routes) |

## Route catalogue

### `procurement-service` (placeholder — TASK-SCM-BE-002)

| Field | Value |
|---|---|
| External path predicate | `Path=/api/v1/procurement/**` |
| Internal target | `${PROCUREMENT_SERVICE_URI:http://procurement-service:8080}` |
| RewritePath | `/api/v1/procurement/(?<segment>.*) → /api/procurement/${segment}` |
| Auth | required |
| Rate limit | `replenishRate=1`, `burstCapacity=120`, key = `accountKeyResolver` |
| Status | placeholder — downstream not implemented in v1 of the gateway |

Anticipated v1 endpoints (formal contract lands with TASK-SCM-BE-002):

- `POST /api/v1/procurement/po` — create PO (purchase order)
- `POST /api/v1/procurement/po/{poId}/confirm` — confirm PO
- `POST /api/v1/procurement/po/{poId}/cancel` — cancel PO
- `POST /api/v1/procurement/po/{poId}/asn` — receive supplier ASN
- `GET /api/v1/procurement/po/{poId}` — fetch PO

### `inventory-visibility-service` (placeholder — TASK-SCM-BE-003)

| Field | Value |
|---|---|
| External path predicate | `Path=/api/v1/inventory-visibility/**` |
| Internal target | `${INVENTORY_VISIBILITY_SERVICE_URI:http://inventory-visibility-service:8080}` |
| RewritePath | `/api/v1/inventory-visibility/(?<segment>.*) → /api/inventory-visibility/${segment}` |
| Auth | required |
| Rate limit | `replenishRate=1`, `burstCapacity=120`, key = `accountKeyResolver` |
| Status | placeholder — downstream not implemented in v1 of the gateway |

Anticipated v1 endpoints (formal contract lands with TASK-SCM-BE-003):

- `GET /api/v1/inventory-visibility/snapshot` — cross-node snapshot read-model
- `GET /api/v1/inventory-visibility/sku/{sku}` — per-SKU visibility

### Local management endpoints

| Path | Auth | Description |
|---|---|---|
| `GET /actuator/health` | none | liveness/readiness probe (Spring Boot defaults) |
| `GET /actuator/info` | none | build info |

`/actuator/prometheus` is **not** publicly exposed — Prometheus scrapes each
service on the internal `scm-platform-net` docker network directly. An
anonymous external request to gateway's `/actuator/prometheus` returns
401 (network isolation contract).

## v2 / deferred routes

These appear once the v2 services bootstrap (separate tasks):

| External path | Owner | Bootstrap task |
|---|---|---|
| `/api/v1/suppliers/**` | supplier-service | deferred |
| `/api/v1/demand/**` | demand-planning-service | deferred |
| `/api/v1/logistics/**` | logistics-service | deferred |
| `/api/v1/settlement/**` | settlement-service | deferred |
| `/api/v1/admin/**` | admin-service | deferred |

## References

- [`gateway-service/architecture.md`](../../services/gateway-service/architecture.md)
- [`gap-integration.md`](../../integration/gap-integration.md)
- `platform/api-gateway-policy.md`
- `platform/error-handling.md`
- TASK-SCM-BE-001 — gateway-service bootstrap (this catalogue's authoring task)
- TASK-SCM-BE-002 / TASK-SCM-BE-003 — downstream service bootstraps
