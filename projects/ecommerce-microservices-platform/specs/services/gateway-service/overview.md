# gateway-service — Overview

> 1-pager: responsibilities, public route surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `gateway-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `rest-api` (edge gateway role) |
| Architecture Style | **Layered** — no domain aggregates, see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, **Spring Cloud Gateway (reactive WebFlux)**, Redis 7 (rate-limit counters only) |
| Deployable unit | `apps/gateway-service/` |
| Bounded Context | n/a — service contains no domain logic |
| Persistent stores | none (stateless); Redis used for ephemeral rate-limit state only |
| Event publication | none |

## Responsibilities

- **Single external entry point** — every `/api/...` request for ecommerce routes through this service per [`platform/api-gateway-policy.md`](../../../../../platform/api-gateway-policy.md).
- **JWT validation** — OAuth2 Resource Server against IAM JWKS (`/.well-known/jwks.json`); validates RS256 signature, `aud=ecommerce`, `tenant_id=ecommerce` (see [`../../integration/iam-integration.md`](../../integration/iam-integration.md)).
- **Identity header pipeline** — strip client-supplied `X-User-Id` / `X-User-Email` / `X-User-Role` before processing; re-set them from verified JWT claims via the JWT enrichment filter.
- **Per-IP + per-route rate limiting** — Redis-backed token bucket; sensitive routes (`/api/auth/login`, `/signup`, `/refresh`) have lower limits, see [`public-routes.md` § Rate-Limit Tiers](public-routes.md).
- **Public/private route enforcement** — only the routes in [`public-routes.md`](public-routes.md) skip authentication; every other path rejects with `401 UNAUTHORIZED` if JWT missing/invalid.
- **CORS + request/response logging** — cross-cutting filters apply uniformly to all downstream traffic.
- **Health endpoint exposure** — `/actuator/health` proxied without authentication for orchestrator probes.

## Public surface (routes)

자세한 spec 은 [`public-routes.md`](public-routes.md) + [`architecture.md`](architecture.md) 참조.

| Group | External path | Auth | Downstream |
|---|---|---|---|
| Auth (public subset) | `POST /api/auth/{signup,login,refresh}` | none | `auth-service` (deprecated; legacy) — see [auth-service-deprecated/](../auth-service-deprecated/) |
| Catalog read | `GET /api/products/**` | none | `product-service` |
| Search read | `GET /api/search/**` | none | `search-service` |
| Review read | `GET /api/reviews/products/**` | none | `review-service` |
| Health | `GET /actuator/health` | none | self |
| **All other** `/api/...` | varies | **JWT required** | corresponding service (order / payment / user / cart / promotion / shipping / notification / admin / …) |

Admin paths (`/api/admin/**`) are JWT-required + `ROLE_ADMIN`; admin RBAC is enforced **after** the JWT filter (not in `public-routes.md`).

## Key invariants

1. **JWT validation 통과 없이는 어떠한 private `/api/...` 요청도 downstream 에 도달하지 않는다** — IAM JWKS 서명 + `aud=ecommerce` + `tenant_id=ecommerce` 모두 통과 필수.
2. **Public route list is source of truth** — [`public-routes.md`](public-routes.md) 의 표 외 path 는 모두 인증 require. 코드/설정과 표가 불일치하면 표가 win.
3. **No business logic, no aggregates, no persistence** — 본 service 는 stateless. domain 로직은 downstream service 소유.
4. **No domain event publication** — gateway 는 어떠한 outbox / Kafka topic 도 publish 안 함.
5. **Fail-open rate limit** — Redis 장애 시 request 통과 + WARN 로그 + 메트릭 발행. rate limit 은 soft protection, correctness boundary 아님 (per `platform/api-gateway-policy.md`).
6. **Identity headers re-set after JWT validation** — 클라이언트가 직접 `X-User-Id` 등을 보내도 filter 에서 strip 후 JWT claim 기반으로 재설정.

## Owned Data

- None (stateless). Redis 상태는 ephemeral rate-limit counters only.

## Published Interfaces

- Routed proxy endpoints (downstream service HTTP APIs)
- Actuator health and metrics endpoints (`/actuator/health`, `/actuator/prometheus`)

## Dependent Systems

- IAM (iam-platform) JWKS — JWT signature validation
- Redis — rate limiting state
- all downstream backend services — routing targets

## Out of scope (v1)

- Business logic, domain aggregates, persistent ownership — downstream service 소유.
- Token issuance / refresh — `auth-service-deprecated` (v1 legacy) 또는 IAM 가 직접 (전환 진행 중).
- Admin RBAC role check — JWT filter 이후 layer 에서 처리.
- Webhook endpoints (`/api/webhooks/payment/toss` 등) — IP allow-list 기반, 별 spec 영역 (webhook-owning service 가 정의).
- Direct database access — stateless 원칙 위반.
