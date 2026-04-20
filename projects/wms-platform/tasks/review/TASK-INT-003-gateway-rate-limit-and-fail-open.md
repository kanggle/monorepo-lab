# Task ID

TASK-INT-003

# Title

Gateway rate-limit key `(clientIp, routeId)`, Redis fail-open, empty role header

# Status

ready

# Owner

integration

# Task Tags

- code
- api
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Fix three gateway-service defects flagged in the TASK-INT-001 review:

1. **Rate-limit key is IP-only; spec requires `(clientIp, routeId)`.**
   `RateLimitConfig.clientIpKeyResolver` returns only the client IP. A
   future `/api/v1/inventory/**` route would bleed into the
   `/api/v1/master/**` bucket.
2. **Redis fail-closed instead of fail-open.** `platform/api-gateway-policy.md`
   and `specs/services/gateway-service/architecture.md` mandate fail-open
   when Redis is unavailable for rate-limit counters. Spring Cloud Gateway's
   `RedisRateLimiter` fails closed by default — wrap it with a decorator
   that catches reactive errors and returns `Response.isAllowed = true` with
   a WARN log.
3. **Missing `role` claim produces no `X-User-Role` header.** Task edge
   case explicitly says populate as empty string; current implementation
   skips the header entirely.

---

# Scope

## In Scope

- `RateLimitConfig.clientIpKeyResolver` — include `routeId` in the key.
  Pull the route ID from `ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR`
  (cast to `org.springframework.cloud.gateway.route.Route`).
- `RateLimitConfig` (or new `FailOpenRateLimiter`) — decorator around
  `RedisRateLimiter` that catches reactive errors (Redis connection
  failure) and returns an allowed response, logging at WARN.
- `JwtHeaderEnrichmentFilter` — always set `X-User-Role` header; default
  to `""` (empty string) when `role` / `roles` claim is absent.
- `application.yml` — if current default for `JWT_JWKS_URI` is
  `http://localhost:8080/.well-known/jwks.json` (self-referential if
  gateway binds to 8080), remove the default or change to a port that
  cannot loop (e.g. `:8088`).
- Unit tests:
  - `clientIpKeyResolver` test with a mock exchange carrying a route
    attribute — verify key format `"{ip}:{routeId}"`
  - Fail-open test — decorator receives a `Mono.error(...)` from the
    wrapped limiter, returns allowed
  - `JwtHeaderEnrichmentFilter` test — JWT without `role` / `roles`
    claim → `X-User-Role` header is `""`, present on the forwarded
    request

## Out of Scope

- Rewriting `SecurityConfig` or CORS
- Adding new filter types
- Any circuit-breaker work (out of scope; separate future task)
- E2E test updates — TASK-INT-002's e2e suite is already in done/;
  these fixes should be covered by its next run

---

# Acceptance Criteria

- [ ] `clientIpKeyResolver` returns a key including both clientIp and
      routeId; a test with two mock routes proves they produce different
      keys even from the same IP
- [ ] Redis-failure path returns allowed; test exercises the decorator
      directly with a failing inner limiter
- [ ] `JwtHeaderEnrichmentFilter` test asserts the header is always
      present on the forwarded request (value `""` if no role claim)
- [ ] `application.yml` JWKS default removed or updated
- [ ] `./gradlew :projects:wms-platform:apps:gateway-service:test` passes
- [ ] CI `e2e-tests` job (landed in TASK-INT-002) still green after the
      fixes

---

# Related Specs

- `platform/api-gateway-policy.md` §Rate Limiting
- `specs/services/gateway-service/architecture.md` §Rate Limiting
- `specs/services/gateway-service/architecture.md` §Header Enrichment

# Related Contracts

- None (implementation detail; no contract change)

---

# Target Service

- `gateway-service`

---

# Implementation Notes

- **Route attribute**: after SCG's routing filter runs, the exchange
  carries `ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR` (key
  `org.springframework.cloud.gateway.support.ServerWebExchangeUtils.gatewayRoute`).
  `KeyResolver` runs inside the route's filter chain, so the attribute
  is present.
- **Fail-open decorator**: implement as a `RateLimiter` bean that
  delegates to `RedisRateLimiter` and, on reactive error, returns
  `Mono.just(new Response(true, Map.of("X-RateLimit-Remaining", "-1")))`.
  Keep the wrapped `RedisRateLimiter` as the primary bean so existing
  config keeps working.
- **Empty role default**: prefer `String role = extractRole(jwt).orElse("")`
  (not null). Downstream master-service treats empty role same as
  "no role" — authorization still rejects.
- **Testing the fail-open**: use a mocked `RateLimiter` that returns
  `Mono.error(new RedisConnectionFailureException(...))`; assert the
  decorator returns `isAllowed = true`.

---

# Edge Cases

- Exchange with no route attribute (pre-routing filter) — key resolver
  returns `{ip}:unknown` with a WARN log; don't NPE.
- Missing / blank JWT `sub` claim — out of scope here; unrelated to the
  three defects.
- Multiple `role` claim formats: string, array, missing — normalize
  consistently.

---

# Failure Scenarios

- Redis completely unreachable — all requests pass, rate-limiting
  disabled until Redis recovers. This is the documented fail-open
  policy.
- Route attribute missing because the route didn't match — the request
  wouldn't reach the filter chain in the first place; no code path.
- JWT with both `role` and `roles` claims — defensive: prefer `roles`
  (array), fall back to `role` (string), fall back to `""`.

---

# Test Requirements

- `RateLimitConfigTest` or `ClientIpKeyResolverTest` — covers the
  `(ip, routeId)` compound key
- `FailOpenRateLimiterTest` — decorator behavior under Redis failure
- Extend `JwtHeaderEnrichmentFilterTest` — empty-role edge case

---

# Definition of Done

- [ ] Three defects fixed
- [ ] Tests added and green
- [ ] CI passes
- [ ] Review note summarizes the fixes against the TASK-INT-001 review findings
- [ ] Ready for review
