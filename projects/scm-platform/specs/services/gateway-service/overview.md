# gateway-service — Overview

> 1-pager: responsibilities, public surface (routes), key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `gateway-service` |
| Project | `scm-platform` |
| Service Type | `rest-api` (edge gateway role) |
| Architecture Style | **Layered** (intentional exception — no domain aggregates) — see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, **Spring Cloud Gateway (reactive WebFlux)**, Redis 7 (rate-limit counters, ephemeral) |
| Deployable unit | `apps/gateway-service/` |
| Bounded Context | n/a — service contains no domain logic |
| Persistent stores | none (stateless); Redis for ephemeral rate-limit counters only |
| Event publication | none |

## Responsibilities

- **Single external entry point** — all `/api/v1/**` traffic for scm-platform routes through this service per [`platform/api-gateway-policy.md`](../../../../../platform/api-gateway-policy.md).
- **JWT validation** — OAuth2 Resource Server against GAP JWKS; validates RS256 signature + `aud=scm` + `tenant_id=scm` (or SUPER_ADMIN `*` wildcard).
- **Tenant isolation** — cross-tenant tokens rejected at the edge with `403 TENANT_FORBIDDEN`. Fail-closed (misconfigured tokens → 403, never 500).
- **Identity header pipeline** — `IdentityHeaderStripFilter` (highest precedence) strips client-supplied `X-Account-Id` / `X-Tenant-Id` / `X-Roles`; re-set from verified JWT claims.
- **Rate limiting** — per `(account/client_id, route)`; keys project-prefixed (`rate:scm-platform:<route>:<id>`) to avoid cross-project Redis collisions; **fail-open** on Redis outage.
- **Error envelope normalize** — all gateway-level errors (401 / 403 / 429 / 503) emit platform envelope `{ code, message, timestamp }`.
- **Trace propagation** — generate / echo `X-Request-Id` + OTel trace context to downstream services.

## Public surface (routes)

| External path | Auth | Downstream |
|---|---|---|
| `/api/v1/procurement/**` | JWT + ROLE | `procurement-service:8080` |
| `/api/v1/inventory-visibility/**` | JWT + ROLE | `inventory-visibility-service:8080` |
| `/api/procurement/webhooks/supplier-ack` | HMAC `X-Supplier-Signature` (gateway bypass) | `procurement-service:8080` |
| `/api/procurement/webhooks/asn` | HMAC `X-Supplier-Signature` (gateway bypass) | `procurement-service:8080` |
| `/actuator/health`, `/actuator/info` | none (local) | self |

자세한 spec 은 [`../../contracts/http/gateway-public-routes.md`](../../contracts/http/gateway-public-routes.md) 참조.

## Key invariants

1. **No business logic, no aggregates, no persistence** — Layered exception (intentional, architecture.md § Why This Architecture).
2. **`IdentityHeaderStripFilter` runs at HIGHEST precedence** — client-supplied identity headers 가 downstream service 까지 leak 금지.
3. **Rate-limit keys project-prefixed** — `rate:scm-platform:<route>:<id>` (cross-project Redis collision 방지).
4. **JWKS reachability probed at startup** — GAP 도달 불가 시 fail-fast (service start 실패).
5. **Tenant check fail-closed** — `tenant_id` claim 부재 또는 `scm`/`*` 외의 값 → 403 (downstream 미도달).
6. **Fail-open rate limit** — Redis outage 시 throw 금지; 통과 + WARN + 메트릭 (`platform/api-gateway-policy.md`).

## Owned Data

- None (stateless). Redis 상태는 ephemeral rate-limit counters only.

## Published Interfaces

- [`../../contracts/http/gateway-public-routes.md`](../../contracts/http/gateway-public-routes.md) (routing catalog only — downstream contracts live in each service's spec)

## Dependent Systems

- GAP (iam-platform) JWKS — JWT signature validation
- Redis — rate-limit store
- `procurement-service`, `inventory-visibility-service` — route targets

## Out of scope (v1)

- v2 routes — supplier-service / demand-planning / logistics / settlement / notification / admin (현 v1 미존재).
- Business logic of any kind.
- Persistent state beyond ephemeral Redis counters.
- Direct supplier / WMS Kafka 통신 — `procurement-service` / `inventory-visibility-service` 가 adapter 소유.
