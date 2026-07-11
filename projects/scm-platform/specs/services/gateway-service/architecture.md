# gateway-service — Architecture

This document declares the internal architecture of `scm-platform/apps/gateway-service`.
All implementation tasks targeting this service must follow this declaration,
`platform/api-gateway-policy.md`, and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `gateway-service` |
| Project | `scm-platform` |
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
`platform/service-types/INDEX.md`. The role is **edge gateway only**:
synchronous HTTP routing + JWT validation + tenant enforcement +
rate limiting + header normalization. No domain aggregates, no event
publication, no Kafka consumption. The Spring Cloud Gateway (reactive)
stack is a single-type rest-api specialization despite the absence of
REST controllers — the gateway IS the REST surface for the project.

---

## Responsibilities

`gateway-service` is the **single external entry point** for all scm-platform
traffic. Per `platform/api-gateway-policy.md` it MUST:

- Route every `/api/v1/procurement/**` and `/api/v1/inventory-visibility/**`
  request (and future supplier/demand/logistics/settlement/notification/admin
  paths declared in v2) to the owning service.
- Validate JWT bearer tokens (OAuth2 Resource Server) against IAM's JWKS.
- Enforce tenant isolation via **entitlement-trust dual-accept** (ADR-MONO-019
  § D5): a token is admitted when the legacy slug `tenant_id ∈ {scm, *}` (`*` =
  SUPER_ADMIN platform-scope) **or** the IAM-signed `entitled_domains` claim
  contains `scm`; cross-tenant tokens that satisfy neither branch are rejected
  at the edge with 403 `TENANT_FORBIDDEN`.
- Strip client-supplied identity headers and set them from verified claims.
- Enforce rate limits per `(account-or-client_id, route)` for authenticated
  traffic and `(clientIp, route)` for unauthenticated traffic. Keys are
  project-prefixed (`rate:scm-platform:<route>:<id>`) to avoid collisions in
  shared Redis.
- Normalize gateway-level errors to the platform error envelope
  (`{ code, message, timestamp }`).
- Echo/generate `X-Request-Id` and propagate OTel trace context.

It MUST NOT own aggregates, persist domain state, or contain business logic.

---

## Architecture Style Rationale

Gateway services have no aggregates, repositories, or domain events. Hexagonal's
port/adapter separation adds ceremony without payoff here — Spring Cloud
Gateway's routing-centric model already organises behaviour around filters and
configuration, not domain ports. Layered gives:

- `config/` — route, rate-limit, security wiring
- `filter/` — request/response transformation (header stripping, request-id, header enrichment, retry-after)
- `security/` — JWT validators (issuer allowlist, tenant claim) + JWKS startup probe
- `ratelimit/` — fail-open Redis decorator
- `error/` — gateway-level error responses matching the platform envelope

All layers are small; complexity belongs in the filter pipeline, not in custom
business logic.

This decision is documented per `platform/architecture-decision-rule.md` —
gateway is the single project service exempt from Hexagonal because the
trade-off (port/adapter ceremony with no domain aggregates) is unfavourable.
Acceptance Criterion 15 of TASK-SCM-BE-001 explicitly affirms this Layered
declaration.

---

## Package Layout

**Most of this service now lives in `libs/java-gateway`** (ADR-MONO-048, TASK-MONO-351/355/356/357).
The classes below are what remains scm-specific — its routes, its property prefix, its
tenant-gate policy and its header policy:

```
com.example.scmplatform.gateway/
├── GatewayServiceApplication.java         ← scanBasePackages MUST name com.example.apigateway
├── config/
│   ├── GatewayIdentityConfig.java         ← strip additions (X-Token-Type, X-Scopes) + enrichment mappings
│   ├── OAuth2ResourceServerConfig.java    ← property prefix + tenantGate() + JWKS probe bean
│   └── RateLimitConfig.java               ← key resolvers (account-keyed, rate:scm-platform:)
└── security/
    ├── JwksHealthProbe.java  → moved to libs/java-gateway (TASK-MONO-357); wired here as an
    │                            opt-in @Bean, because the library class carries no @Component —
    │                            with one, every gateway that scans the library package would
    │                            register it, including wms, which has never had a startup probe.
    └── ScmTokenType.java                  ← client_credentials heuristic for X-Token-Type (scm-only)
```

From `libs/java-gateway` (**not** re-implemented here): `SecurityConfig`,
`IdentityHeaderStripFilter`, `JwtHeaderEnrichmentFilter`, `RequestIdFilter`, `RetryAfterFilter`,
`TenantClaimValidator`, `AllowedIssuersValidator`, `GatewayJwtDecoders`, `JwtClaims`,
`FailOpenRateLimiter`, `JwksHealthProbe`, `ApiErrorEnvelope`, `GatewayErrorHandler`.

> **The component scan is load-bearing.** Omit `com.example.apigateway` from
> `scanBasePackages` and the gateway boots **without its security chain** — every unit test
> still passes, the build is green, and nothing says so. `GatewayComponentScanTest` asserts it.

> **Naming note**: TASK-SCM-BE-001 § Architecture lists `TenantGateFilter` and
> `HeaderEnrichmentFilter` as two separate components. The wms / fan-platform
> reference implementation (per the task's "fan-platform 패턴 답습" directive)
> collapses both responsibilities — JWT is decoded with a
> `TenantClaimValidator` that gates on `tenant_id` during signature verification
> (so cross-tenant tokens never reach the controller chain), while
> `JwtHeaderEnrichmentFilter` propagates verified headers downstream.

### Allowed dependencies

- `org.springframework.cloud:spring-cloud-starter-gateway`
- `org.springframework.boot:spring-boot-starter-{actuator,security,oauth2-resource-server,data-redis-reactive}`
- `io.micrometer:micrometer-registry-prometheus`, `micrometer-tracing-bridge-otel`
- shared libs: `libs:java-common`, `libs:java-web`, `libs:java-observability`, `libs:java-security`

### Forbidden dependencies

- `spring-boot-starter-web` (Servlet stack — conflicts with Gateway's WebFlux)
- any service-specific contract jar (gateway is service-agnostic)
- any persistence library beyond `data-redis-reactive` (gateway is stateless)
- any domain library — gateway is routing/auth/rate-limit only, no business logic

### Boundary rules

- `filter/` MUST NOT call `security/` or `error/` directly — both are wired via
  Spring beans declared in `config/`.
- `error/GatewayErrorHandler` is the only place that writes platform envelopes.
  Any new error path goes through it.
- `ratelimit/FailOpenRateLimiter` MUST NOT throw on Redis failure. Redis errors
  must be observed as a metric (`gateway_ratelimit_redis_unavailable_total`)
  and translated to a `Response(allowed=true)`. Non-Redis errors propagate
  (recorded under `gateway_ratelimit_unexpected_error_total`).

---

## Routes (v1 — placeholder)

`application.yml` declares the route surface. v1 of this gateway only
defines two **placeholder** routes; the downstream services (`procurement-service`,
`inventory-visibility-service`) are bootstrapped by the follow-up tasks
TASK-SCM-BE-002 and TASK-SCM-BE-003. Until those services exist, requests to
these paths bubble up as 503 (Spring Cloud Gateway default for unreachable
downstream) — see Failure Modes § Edge Case E4.

### RewritePath policy

The gateway exposes all API resources under the `/api/v1/` external namespace.
Downstream services do **not** use the `v1` prefix internally. The gateway
`RewritePath` filter strips the namespace before forwarding:

| External path (client-facing) | Internal path (downstream) | Target service |
|---|---|---|
| `/api/v1/procurement/**` | `/api/procurement/**` | `procurement-service:8080` (TASK-SCM-BE-002) |
| `/api/v1/inventory-visibility/**` | `/api/inventory-visibility/**` | `inventory-visibility-service:8080` (TASK-SCM-BE-003) |

RewritePath filter syntax (Spring Cloud Gateway named-group capture):

```yaml
- RewritePath=/api/v1/procurement/(?<segment>.*), /api/procurement/${segment}
- RewritePath=/api/v1/inventory-visibility/(?<segment>.*), /api/inventory-visibility/${segment}
```

Path variables and query strings are preserved automatically by Spring Cloud
Gateway — only the path prefix is rewritten.

### Route table

| External path prefix | Target | Auth | Rate Limit |
|---|---|---|---|
| `/api/v1/procurement/**` | `procurement-service:8080` (deferred) | required | account-or-client/IP — 1 r/s replenish, 120 burst |
| `/api/v1/inventory-visibility/**` | `inventory-visibility-service:8080` (deferred) | required | account-or-client/IP — 1 r/s replenish, 120 burst |
| `/actuator/health` | local | none | n/a |
| `/actuator/info` | local | none | n/a |

All other paths return `404 NOT_FOUND`.

### Prometheus scrape endpoint — network isolation

`/actuator/prometheus` is **not routed through the gateway** (mirrors fan-platform
TASK-FAN-BE-004 option c). Each service exposes its metrics endpoint exclusively
on the internal `scm-platform-net` docker network. Prometheus scrapes them
directly at `http://<service-name>:8080/actuator/prometheus` without passing
through the gateway. The gateway's own `/actuator/prometheus` is rejected by
Spring Security (path not in PUBLIC_PATHS).

---

## JWT Validation

Per `platform/security-rules.md` and
[iam-integration.md](../../integration/iam-integration.md):

- Decoder: `NimbusReactiveJwtDecoder` with `jwk-set-uri` pointing at IAM
  (`http://iam.local/oauth2/jwks` — Edge Case E2 alignment with V0013 SQL).
- Algorithm: RS256 only.
- Standard claims: `exp`, `nbf`, `iat` validated by `JwtTimestampValidator`.
- Issuer: `AllowedIssuersValidator` — accepts both the SAS issuer URL and the
  legacy `"iam-platform"` string (D2-b deprecation window).
- Tenant: `TenantClaimValidator` — **entitlement-trust dual-accept**
  (ADR-MONO-019 § D5). Accepts when the legacy slug `tenant_id ∈ { scm, * }`
  (`*` = SUPER_ADMIN platform-scope) **or** the IAM-signed `entitled_domains`
  claim (a list of domain keys) contains `scm`. Rejection requires **both**
  branches to fail (fail-closed; entitlement only widens). The
  `entitled_domains` claim is read only from an RS256/JWKS-verified token, so
  it is unforgeable — **IAM is the entitlement authority**; a non-list / null /
  empty / non-string-element claim degrades to "not entitled". The shared
  static `TenantClaimValidator.isEntitled(jwt, domain)` helper is the single
  source of truth for the entitlement branch. While IAM has not yet populated
  `entitled_domains` the claim is absent → only the legacy path applies →
  **production net-zero**. This is the ADR-MONO-019 **dual-accept window**; the
  legacy slug branch is removed in step 4 once IAM populates the claim
  (separate follow-up).
- Forwarded headers after successful validation:
  - `X-User-Id` ← `sub`
  - `X-Account-Id` ← `sub` (alias used by scm-platform downstream services;
    for client_credentials grant `sub == client_id`, see Edge Case E1)
  - `X-Actor-Id` ← `sub`
  - `X-User-Email` ← `email` (when present; absent on client_credentials)
  - `X-User-Role` / `X-Roles` ← `role` / joined `roles` array (or empty string
    when neither claim is present — Edge Case E3 client_credentials path)
  - `X-Tenant-Id` ← `tenant_id`
  - `X-Scopes` ← raw `scope` claim (space-delimited per RFC 6749) when present
  - `X-Token-Type` ← `client_credentials` when token shape matches a machine
    grant (no email + `azp == sub` or scope-only), `user` otherwise. This lets
    downstream services distinguish service-to-service callers from human
    users (Edge Case E1).
- `X-Request-Id` is generated (UUID v4) if absent; echoed verbatim if present.
- Client-supplied identity headers are stripped **before** the JWT filter runs.

### JWKS startup probe

`JwksHealthProbe` runs once on `ApplicationReadyEvent`, retries with exponential
backoff up to a configurable timeout (default 30s), and on final failure closes
the application context so Spring Boot exits non-zero. This surfaces a IAM
outage at boot rather than waiting for the first protected request to 401.
Disabled in tests via `gateway.jwks.startup-probe.enabled=false`.

---

## Rate Limiting

- Library: Spring Cloud Gateway's built-in `RedisRateLimiter` (token bucket),
  wrapped by `FailOpenRateLimiter` (decorator pattern).
- Key resolver: `accountKeyResolver` — produces
  `rate:scm-platform:<routeId>:acct:<sub>` for authenticated traffic
  (sub = user id for password grant, sub = client_id for client_credentials
  grant) and falls back to `rate:scm-platform:<routeId>:<clientIp>` for pre-auth
  / public paths.
- Default tier: replenish 1 token/s, burst capacity 120. (60 req/min/IP global,
  600 req/min/account when authenticated — adjusted via per-route filter args.)
- Project prefix `rate:scm-platform:` is mandatory — multiple projects may share
  one Redis instance, and unprefixed keys collide.
- Redis unavailable → **fail open**, increment
  `gateway_ratelimit_redis_unavailable_total`, log at WARN. Justified because
  rate limiting is a soft protection layer, not a correctness boundary.
  `rules/traits/integration-heavy.md` I3 / I8 mandate this fallback.
- Non-Redis errors propagate (do NOT fail open) and increment
  `gateway_ratelimit_unexpected_error_total` for ops visibility.

---

## CORS

- Allowed origins: driven by `CORS_ALLOWED_ORIGINS` env var; no wildcards in
  prod. v1 default = `http://scm.local` only (backend-only release; no
  frontend origin yet — `scm-platform-web` is deferred to v2).
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
  custom `gateway_ratelimit_redis_unavailable_total` and
  `gateway_ratelimit_unexpected_error_total`.
- Trace: OTel context propagates downstream via `traceparent`/`tracestate`.

---

## Failure Modes

| Situation | Response |
|---|---|
| Missing / invalid JWT on protected route | 401 UNAUTHORIZED |
| Cross-tenant token — `tenant_id ∉ {scm, *}` **and** signed `entitled_domains ∌ scm` (dual-accept both branches fail) | 403 TENANT_FORBIDDEN |
| JWT valid but missing required role/scope (future per-route enforcement) | 403 FORBIDDEN |
| Rate limit exceeded | 429 + `Retry-After: 1` |
| Downstream unreachable (procurement / inventory-visibility not yet bootstrapped) | 503 SERVICE_UNAVAILABLE |
| Downstream 5xx / timeout | 502 / 504 |
| Redis unavailable for rate limit | fail-open + WARN log + metric |
| JWKS source unavailable at startup | fail closed → application context shutdown (non-zero exit) |
| JWKS source unavailable post-startup | fail closed → 5xx (cannot validate tokens) |

---

## Testing Strategy

- **Unit**: validator classes in isolation
  (`TenantClaimValidatorTest`, `AllowedIssuersValidatorTest`), filter classes
  with mock `WebFilterChain` (`IdentityHeaderStripFilterTest`,
  `RequestIdFilterTest`, `JwtHeaderEnrichmentFilterTest`,
  `RetryAfterFilterTest`), rate-limit decorator (`FailOpenRateLimiterTest`),
  error handler (`GatewayErrorHandlerTest`), JWKS probe (`JwksHealthProbeTest`),
  test-helper self-test (`JwtTestHelperTest`).
- **Slice**: `OAuth2ResourceServerConfigTest` (validator-chain wiring without
  Spring context), `ClientIpKeyResolverTest` (RateLimit key shape).
- **Integration** (`@Tag("integration")`, Testcontainers + MockWebServer):
  - `GatewayHealthCheckIntegrationTest` — `/actuator/health` returns 200; an
    unauthenticated call to a protected route returns 401.
  - `GatewayBootstrapIntegrationTest` — full pipeline: valid `scm` token → 200;
    client_credentials token (V0013 internal client shape) → 200; cross-tenant
    `wms` token → 403 `TENANT_FORBIDDEN`; SUPER_ADMIN wildcard token → 200;
    tampered signature → 401.
  - `GatewayRateLimitIntegrationTest` — exhausting the burst capacity yields 429.
  - `GatewayPrometheusIsolationTest` — gateway's own `/actuator/prometheus` is
    rejected anonymously (network isolation contract).
  - `GatewayRouteRewriteTest` — `/api/v1/<svc>/**` paths rewritten to
    `/api/<svc>/**` before reaching downstream.

---

## References

- `platform/api-gateway-policy.md`
- `platform/error-handling.md`
- `platform/service-types/rest-api.md`
- `platform/architecture-decision-rule.md`
- [`iam-integration.md`](../../integration/iam-integration.md)
- [`gateway-public-routes.md`](../../contracts/http/gateway-public-routes.md)
- `projects/fan-platform/apps/gateway-service` — reference implementation
  (TASK-SCM-BE-001 explicitly directs "fan-platform 패턴 답습")
- `projects/wms-platform/apps/gateway-service` — original reference pattern
- `rules/traits/integration-heavy.md` (fail-open / circuit-breaker patterns)
- `rules/traits/transactional.md` (idempotency expectations on downstream paths)
- TASK-SCM-BE-001 — this service's bootstrap task
- TASK-MONO-040 / TASK-MONO-042 — scm-platform skeleton + IAM V0013 seed
