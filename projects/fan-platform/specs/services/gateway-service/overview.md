# gateway-service — Overview

> 1-pager: responsibilities, public API surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `gateway-service` |
| Project | `fan-platform` |
| Service Type | `rest-api` (edge gateway role) |
| Architecture Style | **Layered** (no domain aggregates — see [architecture.md § Architecture Style Rationale](architecture.md)) |
| Stack | Java 21, Spring Boot 3.4, **Spring Cloud Gateway (reactive)**, Redis 7 (rate-limit counters only) |
| Deployable unit | `apps/gateway-service/` |
| Bounded Context | n/a — service contains no domain logic |
| Persistent stores | none (stateless); Redis used for ephemeral rate-limit counters |
| Event publication | none |

## Responsibilities

- **Single external entry point** — every `/api/v1/...` request for fan-platform routes through this service per [`platform/api-gateway-policy.md`](../../../../../platform/api-gateway-policy.md).
- **JWT validation** — OAuth2 Resource Server against GAP's JWKS (`/.well-known/jwks.json`); `AllowedIssuersValidator` accepts SAS issuer + legacy global-account-platform issuer.
- **Tenant isolation** — only `tenant_id=fan-platform` (or `*` SUPER_ADMIN wildcard) is admitted; cross-tenant tokens are rejected at the edge with 403 `TENANT_FORBIDDEN`.
- **Identity header pipeline** — strip client-supplied headers (`X-Account-Id`, `X-Tenant-Id`, `X-Roles`) before processing; re-set them from verified JWT claims via `JwtHeaderEnrichmentFilter`.
- **Rate limiting** — per `(account, route)` for authenticated traffic, `(clientIp, route)` for unauthenticated; keys are project-prefixed (`rate:fan-platform:<route>:<id>`) to avoid cross-project collision; Redis-backed but **fail-open** (`FailOpenRateLimiter`).
- **RewritePath** — public `/api/v1/...` namespace → service-internal `/api/...` (TASK-FAN-BE-005).
- **Error envelope normalize** — all gateway-level errors (401 / 403 / 429 / 5xx) emit the platform envelope `{ code, message, timestamp }`.
- **Observability** — generate / propagate `X-Request-Id` + OTel trace context to downstream services; emit `gateway_ratelimit_redis_unavailable_total` metric on Redis failure.

## Public API surface (routes)

자세한 스펙은 [architecture.md § Routes (v1)](architecture.md) 참조.

| External path (client-facing) | Internal path (downstream) | Target service |
|---|---|---|
| `/api/v1/community/**` | `/api/community/**` | `community-service:8080` |
| `/api/v1/artists/**` | `/api/artists/**` | `artist-service:8080` |
| `/api/v1/artist-groups/**` | `/api/artist-groups/**` | `artist-service:8080` |
| `/api/v1/fandoms/**` | `/api/fandoms/**` | `artist-service:8080` |

`/actuator/health`, `/actuator/info`, `/actuator/prometheus` 는 인증 없이 접근 가능 (Prometheus scrape; 네트워크 격리는 [TASK-FAN-BE-004](../../../tasks/done/TASK-FAN-BE-004-prometheus-rate-limit.md) 참조).

## Key invariants

1. **JWT validation 통과 없이는 어떠한 `/api/v1/...` 요청도 downstream 에 도달하지 않는다** — GAP JWKS 서명 검증 + issuer allowlist + tenant claim 모두 통과 필수.
2. **Tenant gate fail-closed** — `tenant_id` claim 부재 또는 `fan-platform`/`*` 외의 값 → 403 `TENANT_FORBIDDEN` (downstream 미도달).
3. **No business logic, no aggregates, no persistence** — 본 service 는 stateless. domain 로직은 community/artist 등 downstream service 소유.
4. **No domain event publication** — gateway 는 어떠한 outbox/Kafka topic 도 publish 안 함.
5. **Fail-open rate limit** — Redis 장애 시 `FailOpenRateLimiter` 가 `Response(allowed=true)` 반환 + 메트릭 발행 (`gateway_ratelimit_redis_unavailable_total`). 절대 throw 금지.
6. **Single error envelope** — 모든 gateway-level error 가 `GatewayErrorHandler` 통과 후 `{ code, message, timestamp }` 형식. 직접 envelope 작성 금지.

## Out of scope (v1)

- membership-service / notification-service / admin-service routing — v2 추가 시 별 route + downstream service 등록.
- business logic, domain aggregates, persistent state — 본 service 의 책임 아님.
- domain event publication — gateway 는 publish 0.
- 인증 외 IdP 통합 (예: SAML, SCIM) — GAP OIDC 만 지원.
- 다중 GAP issuer 의 multi-tenant 라우팅 — 본 service 는 `fan-platform` tenant 만 통과 (다른 project gateway 가 자기 tenant 격리).
