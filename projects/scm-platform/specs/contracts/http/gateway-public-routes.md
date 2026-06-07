---
status: live
last_updated: 2026-05-19
owners: scm-platform/backend
---

# scm-platform — Public Gateway Routes

> **Status**: v1 live catalogue as of 2026-05-11. gateway-service
> (TASK-SCM-BE-001), procurement-service (TASK-SCM-BE-002), and
> inventory-visibility-service (TASK-SCM-BE-003) are all shipped. This file
> was reconciled against the live controller surfaces by TASK-SCM-BE-008
> (inventory-visibility) and TASK-SCM-BE-006 (procurement findings #1).

This document enumerates every externally reachable HTTP route on
`http://scm.local/` (Traefik-routed). All routes are owned by
[gateway-service](../../services/gateway-service/architecture.md) at the
edge; the listed downstream services own the actual handlers once they
exist.

## Authentication contract

Every route under `/api/**` requires:

- `Authorization: Bearer <RS256 JWT>` issued by IAM.
- JWT signature verifies against IAM's JWKS (`http://iam.local/oauth2/jwks`).
- `iss` claim is one of: SAS issuer URL (default `http://iam.local`), legacy
  `iam-platform` (D2-b deprecation window).
- `tenant_id` claim is `scm` or `*` (SUPER_ADMIN wildcard). Any other tenant
  → 403 `TENANT_FORBIDDEN`.

scm v1 = backend only — the **primary** authentication shape is
`grant_type=client_credentials` against the V0013-seeded
`scm-platform-internal-services-client`. Human-user (PKCE / authorization_code)
flow is deferred to v2 when a frontend ships.

### platform-console operator read consumer (ADR-MONO-013 Model B)

The "v1 = backend only / human-flow deferred to v2" statement above scopes
**scm hosting its own frontend** and **registering its own
`scm-platform-user-flow-client`** (V0013 table, DEFERRED). It does **not**
restrict authorized external API consumers. Per
[ADR-MONO-013](../../../../../docs/adr/ADR-MONO-013-platform-console-foundation.md)
(ACCEPTED — § D1 Model B, § D6 Phase 4), the `platform-console` project (a
separate, ADR-MONO-013-governed operator console — **not** an scm frontend) is
a **sanctioned external read consumer** of scm's existing read surface:

| Consumed (read-only) | scm contract |
|---|---|
| `GET /api/v1/procurement/po`, `GET /api/v1/procurement/po/{poId}` | [`procurement-api.md`](./procurement-api.md) |
| `GET /api/v1/inventory-visibility/{snapshot, sku/{sku}, staleness, nodes}` | [`inventory-visibility-api.md`](./inventory-visibility-api.md) |

- **Credential — existing capability, no change.** The console calls these
  **server-side** with a human operator's **IAM `platform-console-web` OIDC
  access token** (RS256, issued by IAM per ADR-001). This token is validated by
  the **already-existing** gateway chain exactly like any IAM RS256 token:
  `AllowedIssuersValidator` + `TenantClaimValidator` (`tenant_id ∈ { scm, * }`)
  + `JwtHeaderEnrichmentFilter` surfacing `X-Token-Type=user` (the human-caller
  shape already specified in [`iam-integration.md`](../../integration/iam-integration.md)
  Edge Case E1/E3). **No new scm OAuth client, no new gateway route, no new
  gateway code, no auth-model change** — this subsection is a reality-alignment
  acknowledgment, not a capability change.
- **Read-only.** The console consumes only the reads above. PO write
  (`/po/{poId}/submit|confirm|cancel`) and the procurement webhooks are
  buyer/machine paths and are **not** console-consumed.
- **Single-org preserved.** scm remains single-organization (the deliberate
  `multi-tenant` non-declaration in [`PROJECT.md`](../../../PROJECT.md) is
  **unaffected**). Tenant scoping stays the IAM `tenant_id` claim enforced by
  the **existing** producer-side `TenantClaimValidator`; cross-tenant tokens are
  rejected `403 TENANT_FORBIDDEN` exactly as today. The console's own
  multi-tenant/audit-heavy obligations are the console's, not scm's.
- **Consumer-only.** scm owns `procurement-api.md` / `inventory-visibility-api.md`
  (authoritative, unchanged). The console-side obligation is specified in
  platform-console
  [`console-integration-contract.md`](../../../../platform-console/specs/contracts/console-integration-contract.md)
  § 2.4.6 (authored by `TASK-PC-FE-008`), reusing its § 2.4.5 per-domain
  credential rule (IAM-token-direct for scm/wms, distinct from IAM's own
  operator-token exchange).
- The deferred `scm-platform-user-flow-client` (V0013 table) stays deferred and
  is **unrelated** — the console uses IAM's **own** `platform-console-web`
  client, never an scm-registered client.

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
| 503 | SERVICE_UNAVAILABLE | downstream service unreachable |

## Route catalogue

### `procurement-service` (TASK-SCM-BE-002, shipped)

| Field | Value |
|---|---|
| External path predicate | `Path=/api/v1/procurement/**` |
| Internal target | `${PROCUREMENT_SERVICE_URI:http://procurement-service:8080}` |
| RewritePath | `/api/v1/procurement/(?<segment>.*) → /api/procurement/${segment}` |
| Auth | required (JWT) for `/api/procurement/po/**`; **public** (shared-secret) for `/api/procurement/webhooks/**` |
| Rate limit | `replenishRate=1`, `burstCapacity=120`, key = `accountKeyResolver` |
| Status | live |

Live v1 endpoints (formal contract:
[`procurement-api.md`](./procurement-api.md)):

| Method | External path | Auth | Idempotency |
|---|---|---|---|
| POST | `/api/v1/procurement/po` | JWT | `Idempotency-Key` |
| GET | `/api/v1/procurement/po` | JWT | n/a (search) |
| GET | `/api/v1/procurement/po/{poId}` | JWT | n/a |
| POST | `/api/v1/procurement/po/{poId}/submit` | JWT | `Idempotency-Key` |
| POST | `/api/v1/procurement/po/{poId}/confirm` | JWT | `Idempotency-Key` |
| POST | `/api/v1/procurement/po/{poId}/cancel` | JWT | `Idempotency-Key` |
| POST | `/api/v1/procurement/webhooks/supplier-ack` | shared-secret `X-Supplier-Signature` | `(tenantId, poId)` semantic |
| POST | `/api/v1/procurement/webhooks/asn` | shared-secret `X-Supplier-Signature` | `(tenantId, supplierAsnRef)` UNIQUE |

> **Drift fixed**: the prior placeholder list claimed `POST /po/{poId}/asn`
> (buyer-side) but the shipped implementation delivers ASN via the
> `/api/v1/procurement/webhooks/asn` supplier webhook — caller asymmetry
> matters for clients reading this catalogue. Reconciled by
> TASK-SCM-BE-008 (extending TASK-SCM-BE-006 finding #1).

### `inventory-visibility-service` (TASK-SCM-BE-003, shipped)

| Field | Value |
|---|---|
| External path predicate | `Path=/api/v1/inventory-visibility/**` |
| Internal target | `${INVENTORY_VISIBILITY_SERVICE_URI:http://inventory-visibility-service:8080}` |
| RewritePath | `/api/v1/inventory-visibility/(?<segment>.*) → /api/inventory-visibility/${segment}` |
| Auth | required (JWT) for all endpoints |
| Rate limit | `replenishRate=1`, `burstCapacity=120`, key = `accountKeyResolver` |
| Status | live |

Live v1 endpoints (formal contract:
[`inventory-visibility-api.md`](./inventory-visibility-api.md)). All return
the `meta.warning: "Not for procurement decisions (S5)"` envelope:

| Method | External path | Purpose |
|---|---|---|
| GET | `/api/v1/inventory-visibility/snapshot` | cross-node paginated snapshot list (or single-node when `?nodeId=`) |
| GET | `/api/v1/inventory-visibility/sku/{sku}` | per-SKU cross-node breakdown (Redis cache, `X-Cache` header) |
| GET | `/api/v1/inventory-visibility/staleness` | node-by-node staleness status (FRESH / STALE / UNREACHABLE) |
| GET | `/api/v1/inventory-visibility/nodes` | node list with status (id, externalId, type, name, status) — **public** per TASK-SCM-BE-008 decision (ops dashboard prerequisite) |

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
- [`iam-integration.md`](../../integration/iam-integration.md)
- `platform/api-gateway-policy.md`
- `platform/error-handling.md`
- TASK-SCM-BE-001 — gateway-service bootstrap (this catalogue's authoring task)
- TASK-SCM-BE-002 / TASK-SCM-BE-003 — downstream service bootstraps
