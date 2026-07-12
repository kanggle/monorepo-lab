# gateway-service — Architecture

This document declares the internal architecture of `fan-platform/apps/gateway-service`.
All implementation tasks targeting this service must follow this declaration,
`platform/api-gateway-policy.md`, and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `gateway-service` |
| Project | `fan-platform` |
| Service Type | `rest-api` (edge gateway role) |
| Architecture Style | **Layered** (no domain aggregates — see Rationale) |
| Primary language / stack | Java 21, Spring Boot 3.4, **Spring Cloud Gateway (reactive)** |
| Bounded Context | n/a — this service contains no domain logic |
| Deployable unit | `apps/gateway-service/` |
| Data store | none (stateless) |
| Event publication | none |
| Shared state | Redis — rate-limit counters only (ephemeral) |

### Service Type Composition

`gateway-service` is a single-type `rest-api` service per
`platform/service-types/INDEX.md`, in an edge gateway role — no
domain aggregates, no Kafka surface. Spring Cloud Gateway (reactive)
applies OAuth2 Resource Server JWT validation, RewritePath filtering,
and tenant isolation at the edge.

---

## Role

`gateway-service` is the **single external entry point** for all fan-platform traffic.
Per `platform/api-gateway-policy.md` it MUST:

- Route every `/api/v1/community/**`, `/api/v1/artists/**`, `/api/v1/artist-groups/**`,
  `/api/v1/fandoms/**` request (and future membership/notification/admin paths) to
  the owning service. RewritePath filters strip the `/api/v1/...` public prefix to
  the service-internal path (see § Routes — TASK-FAN-BE-005 머지로 production 적용).
- Validate JWT bearer tokens (OAuth2 Resource Server) against IAM's JWKS.
- Enforce tenant isolation: only `tenant_id=fan-platform` (or the SUPER_ADMIN
  wildcard `*`) is admitted; cross-tenant tokens are rejected at the edge with
  403 `TENANT_FORBIDDEN`.
- Strip client-supplied identity headers and set them from verified claims.
- Enforce rate limits per `(account, route)` for authenticated traffic and
  `(clientIp, route)` for unauthenticated traffic. Keys are project-prefixed
  (`rate:fan-platform:<route>:<id>`) to avoid collisions in shared Redis.
- Normalize gateway-level errors to the platform error envelope
  (`{ code, message, timestamp }`).
- Echo/generate `X-Request-Id` and propagate OTel trace context.

It MUST NOT own aggregates, persist domain state, or contain business logic.

---

## Architecture Style Rationale

Gateway services have no aggregates, repositories, or domain events. Hexagonal's
port/adapter separation adds ceremony without payoff here. Layered gives:

- `config/` — route, rate-limit, security wiring
- `filter/` — request/response transformation (header stripping, request-id, header enrichment, retry-after)
- `security/` — JWT validators (issuer allowlist, tenant claim)
- `ratelimit/` — fail-open Redis decorator
- `error/` — gateway-level error responses matching the platform envelope

All layers are small; complexity belongs in the filter pipeline, not in custom
business logic.

---

## Package Layout

**Most of this service now lives in `libs/java-gateway`** (ADR-MONO-048, TASK-MONO-351/355/356/357).
The classes below are what remains fan-specific — its routes, its property prefix, its
tenant-gate policy and its header policy:

```
com.example.fanplatform.gateway/
├── GatewayServiceApplication.java         ← scanBasePackages MUST name com.example.apigateway
└── config/
    ├── GatewayIdentityConfig.java         ← strip set (baseline) + enrichment mappings
    ├── OAuth2ResourceServerConfig.java    ← property prefix + tenantGate() + JWKS probe bean
    └── RateLimitConfig.java               ← key resolvers (account-keyed, rate:fan-platform:)
```

From `libs/java-gateway` (**not** re-implemented here): `SecurityConfig`,
`IdentityHeaderStripFilter`, `JwtHeaderEnrichmentFilter`, `RequestIdFilter`, `RetryAfterFilter`,
`TenantClaimValidator`, `AllowedIssuersValidator`, `GatewayJwtDecoders`, `JwtClaims`,
`FailOpenRateLimiter`, `JwksHealthProbe`, `ApiErrorEnvelope`, `GatewayErrorHandler`.

> `JwksHealthProbe` moved to the library in TASK-MONO-357 and is wired here as an **opt-in
> `@Bean`**. The library class deliberately carries no `@Component`: with one, every gateway that
> scans the library package would register it — including wms, which has never had a JWKS startup
> probe and would have silently gained a boot-time dependency on the IdP.

> **The component scan is load-bearing.** Omit `com.example.apigateway` from
> `scanBasePackages` and the gateway boots **without its security chain** — every unit test
> still passes, the build is green, and nothing says so. `GatewayComponentScanTest` asserts it.

> **Naming note**: TASK-FAN-BE-001 §Architecture lists `TenantGateFilter` and
> `HeaderEnrichmentFilter` as two separate components. The wms reference
> implementation collapses both responsibilities — JWT is decoded with a
> {@link com.example.fanplatform.gateway.security.TenantClaimValidator}
> that gates on `tenant_id` during signature verification (so cross-tenant
> tokens never reach the controller chain), while
> {@code JwtHeaderEnrichmentFilter} propagates verified headers downstream.
> Per the task's Implementation Notes ("wms-platform/apps/gateway-service 를 첫
> reference 로 복제"), we adopt the wms layering verbatim.

### Allowed dependencies

- `org.springframework.cloud:spring-cloud-starter-gateway`
- `org.springframework.boot:spring-boot-starter-{actuator,security,oauth2-resource-server,data-redis-reactive}`
- `io.micrometer:micrometer-registry-prometheus`, `micrometer-tracing-bridge-otel`
- shared libs: `libs:java-common`, `libs:java-web`, `libs:java-observability`, `libs:java-security`

### Forbidden dependencies

- `spring-boot-starter-web` (Servlet stack — conflicts with Gateway's WebFlux)
- any service-specific contract jar (gateway is service-agnostic)
- any persistence library beyond `data-redis-reactive` (gateway is stateless)

### Boundary rules

- `filter/` MUST NOT call `security/` or `error/` directly — both are wired via
  Spring beans declared in `config/`.
- `error/GatewayErrorHandler` is the only place that writes platform envelopes.
  Any new error path goes through it.
- `ratelimit/FailOpenRateLimiter` MUST NOT throw. Redis errors must be observed
  as a metric (`gateway_ratelimit_redis_unavailable_total`) and translated to a
  `Response(allowed=true)`.

---

## Routes (v1)

`application.yml` declares the route surface.

### RewritePath policy (TASK-FAN-BE-005)

The gateway exposes all API resources under the `/api/v1/` external namespace.
Downstream services do **not** use the `v1` prefix internally. The gateway
`RewritePath` filter strips the namespace before forwarding:

| External path (client-facing) | Internal path (downstream) | Target service |
|---|---|---|
| `/api/v1/community/**` | `/api/community/**` | `community-service:8080` |
| `/api/v1/artists/**` | `/api/artists/**` | `artist-service:8080` |
| `/api/v1/artist-groups/**` | `/api/artist-groups/**` | `artist-service:8080` |
| `/api/v1/fandoms/**` | `/api/fandoms/**` | `artist-service:8080` |
| `/api/v1/memberships/**` | `/api/fan/memberships/**` | `membership-service:8080` (spec: TASK-FAN-BE-008; route wired by TASK-FAN-BE-009) |

> **membership-service `/internal/**` is NOT gateway-exposed.** The internal
> access-check endpoint (`GET /internal/membership/access`) is reachable only on the
> internal `fan-platform-net` docker network, authenticated by IAM
> `client_credentials` workload identity (ADR-MONO-005). No gateway route admits
> `/internal/**`. See
> `projects/fan-platform/specs/services/membership-service/architecture.md`
> § Internal Access-Check Contract and `specs/contracts/http/membership-api.md`.

The artist-service routes are declared as **3 explicit routes** (one per
downstream base path) rather than a single `/api/v1/artist/**` catch-all.
This gives a 1:1 mapping between gateway predicate and downstream controller
base path, avoids complex regex, and makes downstream path changes explicit.

RewritePath filter syntax (Spring Cloud Gateway named-group capture):

```yaml
- RewritePath=/api/v1/community/(?<segment>.*), /api/community/${segment}
- RewritePath=/api/v1/artists/(?<segment>.*), /api/artists/${segment}
- RewritePath=/api/v1/artist-groups/(?<segment>.*), /api/artist-groups/${segment}
- RewritePath=/api/v1/fandoms/(?<segment>.*), /api/fandoms/${segment}
- RewritePath=/api/v1/memberships/(?<segment>.*), /api/fan/memberships/${segment}
```

> The membership route is forward-declared here (spec TASK-FAN-BE-008); the actual
> `application.yml` entry is added by TASK-FAN-BE-009 when membership-service is
> bootstrapped.

Path variables and query strings are preserved automatically by Spring Cloud
Gateway — only the path prefix is rewritten. The `segment` capture group
matches everything after the prefix (including nested path variables such as
`posts/{id}/reactions`).

### Route table

| External path prefix | Target | Auth | Rate Limit |
|---|---|---|---|
| `/api/v1/community/**` | `community-service:8080` | required | account/IP — 1 r/s replenish, 120 burst |
| `/api/v1/artists/**` | `artist-service:8080` | required | account/IP — 1 r/s replenish, 120 burst |
| `/api/v1/artist-groups/**` | `artist-service:8080` | required | account/IP — 1 r/s replenish, 120 burst |
| `/api/v1/fandoms/**` | `artist-service:8080` | required | account/IP — 1 r/s replenish, 120 burst |
| `/api/v1/memberships/**` | `membership-service:8080` | required | account/IP — 1 r/s replenish, 120 burst (wired by TASK-FAN-BE-009) |
| `/actuator/health` | local | none | n/a |
| `/actuator/info` | local | none | n/a |

All other paths return `404 NOT_FOUND`.

### Prometheus scrape endpoint — network isolation (TASK-FAN-BE-004)

`/actuator/prometheus` is **not routed through the gateway**. Each service
(`community-service`, `artist-service`, etc.) exposes its metrics endpoint
exclusively on the internal `fan-platform-net` docker network. Prometheus scrapes
them directly at `http://<service-name>:8080/actuator/prometheus` without passing
through the gateway. This avoids:

- Path collision between the gateway's own `/actuator/prometheus` and upstream
  service metrics on the same path.
- The need for an auth bypass rule in the gateway's security filter chain.

The isolation is enforced at the docker-compose level: backend services use
`expose:` (internal network only) without `ports:`, and Traefik labels are absent
from those containers. An external actor cannot reach the prometheus endpoint
without access to the docker network.

For Prometheus job configuration and scrape interval guidance, see
`projects/fan-platform/docs/operations/prometheus-scrape.md`.

---

## JWT Validation

Per `platform/security-rules.md` and `projects/fan-platform/specs/integration/iam-integration.md`:

- Decoder: `NimbusReactiveJwtDecoder` with `jwk-set-uri` pointing at IAM.
- Algorithm: RS256 only.
- Standard claims: `exp`, `nbf`, `iat` validated by `JwtTimestampValidator`.
- Issuer: `AllowedIssuersValidator` — accepts both the SAS issuer URL and the
  legacy `"iam-platform"` string (D2-b deprecation window).
- Tenant: `TenantClaimValidator` — only `tenant_id ∈ { fan-platform, * }`. The
  wildcard accommodates SUPER_ADMIN platform-scope tokens.
- Forwarded headers after successful validation:
  - `X-User-Id` ← `sub`
  - `X-Account-Id` ← `sub` (alias used by fan-platform downstream services)
  - `X-Actor-Id` ← `sub`
  - `X-User-Email` ← `email` (when present)
  - `X-User-Role` / `X-Roles` ← `role` / joined `roles` array (or empty string
    when neither claim is present)
  - `X-Tenant-Id` ← `tenant_id`
- `X-Request-Id` is generated (UUID v4) if absent; echoed verbatim if present.
- Client-supplied identity headers are stripped **before** the JWT filter runs.

---

## Rate Limiting

- Library: Spring Cloud Gateway's built-in `RedisRateLimiter` (token bucket),
  wrapped by `FailOpenRateLimiter` (decorator pattern).
- Key resolver: `accountKeyResolver` — produces
  `rate:fan-platform:<routeId>:acct:<sub>` for authenticated traffic and falls
  back to `rate:fan-platform:<routeId>:<clientIp>` for pre-auth / public paths.
- Default tier: replenish 1 token/s, burst capacity 120. (60 req/min/IP global,
  600 req/min/account when authenticated — adjusted via per-route filter args.)
- Project prefix `rate:fan-platform:` is mandatory — multiple projects may share
  one Redis instance, and unprefixed keys collide.
- Redis unavailable → **fail open**, increment
  `gateway_ratelimit_redis_unavailable_total`, log at WARN. Justified because
  rate limiting is a soft protection layer, not a correctness boundary.

---

## CORS

- Allowed origins: driven by `CORS_ALLOWED_ORIGINS` env var; no wildcards in
  prod.
- Allowed methods: `GET, POST, PUT, PATCH, DELETE, OPTIONS`.
- Allowed headers: `Authorization`, `Content-Type`, `X-Request-Id`,
  `Idempotency-Key`.
- Exposed headers: `X-Request-Id`, `ETag`, `Retry-After`.

---

## Observability

- Access log line per request: method, path, status, latency ms, `X-Request-Id`,
  user id (if authenticated), client IP. **No** `Authorization` value, **no**
  request/response body.
- Metrics: Micrometer `http.server.requests` (tags: method, uri, status), plus
  custom `gateway_ratelimit_redis_unavailable_total`.
- Trace: OTel context propagates downstream via `traceparent`/`tracestate`.

---

## Failure Modes

| Situation | Response |
|---|---|
| Missing / invalid JWT on protected route | 401 UNAUTHORIZED |
| Cross-tenant token (`tenant_id != fan-platform` and not `*`) | 403 TENANT_FORBIDDEN |
| JWT valid but missing required role (future) | 403 FORBIDDEN |
| Rate limit exceeded | 429 + `Retry-After: 1` |
| Downstream unreachable (community-service / artist-service not yet bootstrapped) | 503 SERVICE_UNAVAILABLE |
| Downstream 5xx / timeout | 502 / 504 |
| Redis unavailable for rate limit | fail-open + WARN log + metric |
| JWKS source unavailable | fail closed → 5xx (cannot validate tokens) |

---

## Testing Strategy

- **Unit**: validator classes in isolation
  (`TenantClaimValidatorTest`, `AllowedIssuersValidatorTest`), filter classes
  with mock `WebFilterChain` (`IdentityHeaderStripFilterTest`, `RequestIdFilterTest`,
  `JwtHeaderEnrichmentFilterTest`, `RetryAfterFilterTest`), rate-limit decorator
  (`FailOpenRateLimiterTest`), error handler (`GatewayErrorHandlerTest`).
- **Slice**: `OAuth2ResourceServerConfigTest` — wires the validator chain
  without a Spring context and confirms issuer + tenant validators are present
  and chained correctly.
- **Integration** (`@Tag("integration")`, Testcontainers + MockWebServer):
  - `GatewayHealthCheckIntegrationTest` — `/actuator/health` returns 200; an
    unauthenticated call to a protected route returns 401.
  - `GatewayBootstrapIntegrationTest` — full pipeline: valid `fan-platform`
    token → 200; cross-tenant `wms` token → 403 `TENANT_FORBIDDEN`; SUPER_ADMIN
    wildcard token → 200; tampered signature → 401.
  - `GatewayRateLimitIntegrationTest` — exhausting the burst capacity yields
    429.

---

## References

- `platform/api-gateway-policy.md`
- `platform/error-handling.md`
- `platform/service-types/rest-api.md`
- `projects/fan-platform/specs/integration/iam-integration.md`
- `projects/wms-platform/apps/gateway-service` (reference implementation pattern
  per TASK-FAN-BE-001 § Implementation Notes)
- `rules/traits/integration-heavy.md` (fail-open / circuit-breaker patterns)
- `rules/traits/multi-tenant.md` (tenant isolation enforcement)
