# gateway-service — Architecture

This document declares the internal architecture of `gateway-service`.
All implementation tasks targeting this service must follow this declaration,
`platform/api-gateway-policy.md`, and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `gateway-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `rest-api` (single — see Service Type Composition below) |
| Architecture Style | **Layered Architecture** (WebFlux-based edge gateway) |
| Domain | ecommerce |
| Primary language / stack | Java 21, Spring Boot, **Spring WebFlux (reactive)** |
| Bounded Context | n/a — edge gateway, no domain logic |
| Deployable unit | `apps/gateway-service/` |
| Data store | none (stateless) |
| Event publication | none |
| Shared state | Redis (rate-limit counters, ephemeral) |

### Service Type Composition

`gateway-service` is a single-type `rest-api` service per
`platform/service-types/INDEX.md`. WebFlux-based API gateway — 퍼블릭 HTTP
트래픽 진입점. JWT validation + routing + rate limiting + header normalization.
적용되는 규칙:
[platform/service-types/rest-api.md](../../../../../platform/service-types/rest-api.md).

---

## Why This Architecture
This service is a WebFlux-based API gateway.

Its primary responsibilities are request routing, rate limiting, JWT authentication filtering, and request logging.

There is no domain logic — all behavior is infrastructure-oriented (routing rules, filters, security).

A layered structure keeps filter chains, configuration, and security concerns cleanly separated without the overhead of domain-driven design.

## Internal Structure Rule
This service uses a layered internal structure.

Recommended internal layers:
- filter (request/response processing pipeline)
- config (Spring configuration, bean definitions)
- security (JWT validation, authentication logic)

Package organization follows package-by-layer.

## Allowed Dependencies
- filter -> config
- filter -> security (JWT validation utilities)
- config -> framework and external libraries

## Forbidden Dependencies
- This service must not contain business/domain logic
- Filters must not call downstream services directly — routing is declarative via configuration
- This service must not own persistent data (stateless gateway)
- Security layer must not duplicate IAM (iam-platform) authorization rules — only validate RS256 signatures, `aud=ecommerce`, and `tenant_id=ecommerce` claims

## Boundary Rules
- Filters handle cross-cutting concerns: authentication, logging, rate limiting
- Configuration declares routing rules, CORS, and rate limiter settings
- Security handles JWT token parsing and validation only
- All downstream service calls are handled by Spring Cloud Gateway routing, not application code

## Per-tenant Rate Limit (M7, TASK-BE-405 / ADR-MONO-030 Step 4 facet e)

Realizes `rules/traits/multi-tenant.md` M7 at the gateway edge (M2 layer-1 home).

- **Key shape = `(tenant_id, route_id)` tuple** — `tenantRouteKeyResolver` (`config/TenantRouteRateLimitConfig`) produces `rate:ecommerce-gw:<routeId>:t:<tenantId>`. Each route's `RequestRateLimiter` filter (application.yml) references it via `key-resolver: "#{@tenantRouteKeyResolver}"` (replaced the legacy IP-only `ipKeyResolver`). One tenant's burst cannot consume another tenant's bucket.
- **Tenant source** — the JWT `tenant_id` claim, read from the **reactive security context** (`ReactiveSecurityContextHolder`), not the `X-Tenant-Id` header (whose injection order relative to the rate-limit filter is unspecified) and not a ThreadLocal (the resolver runs reactively / non-blocking).
- **Pre-auth vs post-auth keying** — authenticated requests key on `<tenant>:<route>`. Anonymous / pre-auth requests (public `GET /api/products/**`, `/api/search/**`, the carrier webhook) have no security context, so they fall back to the **default tenant `'ecommerce'` qualified by client IP** (`...:t:ecommerce:ip:<ip>`). This preserves the IP-based DoS/brute-force bounding the legacy `ipKeyResolver` provided — without it every anonymous caller on a public route would share one default-tenant bucket. The key is **never null** (default-tenant guard).
- **Limit source** — per-route default `replenishRate`/`burstCapacity` in the route filter args; an optional per-tenant override map is a future additive increment (no coupling to `tenant_domain_subscription` — entitlement plane stays decoupled). Breach → **429 `TOO_MANY_REQUESTS`**.
- **Degrade** — `tenant_id` absent (standalone / no IAM) → default-tenant single bucket (D8 net-zero). **Redis unavailable → fail-open** via `FailOpenRateLimiter` (`ratelimit/FailOpenRateLimiter`, the scm/wms/fan precedent): only Redis-class errors are allowed through with sentinel `X-RateLimit-Remaining: -1` + `gateway_ratelimit_redis_unavailable_total`; non-Redis errors propagate (5xx) + `gateway_ratelimit_unexpected_error_total`. Rate limiting is additive, never a hard dependency.
- **No contract change** — 429 is a standard HTTP status; no external API/event contract is altered.

## Integration Rules
- Routing targets must match published service URLs
- JWT validation must follow the IAM OIDC token contract (RS256 via JWKS, `aud=ecommerce`, `tenant_id=ecommerce`); see [`../../integration/iam-integration.md`](../../integration/iam-integration.md)
- Rate limiting configuration must use Redis for distributed state
- Shared libraries may be used only under shared-library policy

## Testing Expectations
Required emphasis:
- Filter unit tests (JWT validation, request logging behavior)
- Integration tests with Redis (rate limiting)
- Route configuration tests (correct path-to-service mapping)
- Security filter chain tests

## Change Rule
Any architectural change to this service must be documented here first before implementation.
