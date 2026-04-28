# gateway-service — Architecture

This document declares the internal architecture of `gateway-service`.
All implementation tasks targeting this service must follow this declaration,
`platform/api-gateway-policy.md`, and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `gateway-service` |
| Service Type | `rest-api` (edge gateway role) |
| Architecture Style | **Layered** (no domain aggregates — see Rationale) |
| Primary language / stack | Java 21, Spring Boot 3.x, **Spring Cloud Gateway (reactive)** |
| Bounded Context | n/a — this service contains no domain logic |
| Deployable unit | `apps/gateway-service/` |
| Data store | none (stateless) |
| Event publication | none |
| Shared state | Redis — rate-limit counters only (ephemeral) |

---

## Role

`gateway-service` is the **single external entry point** for all wms traffic.
Per `platform/api-gateway-policy.md` it MUST:

- Route every `/api/v1/**` request to the owning service.
- Validate JWT bearer tokens (OAuth2 Resource Server).
- Strip client-supplied identity headers and set them from verified claims.
- Enforce rate limits per `(clientIp, routeId)`.
- Normalize gateway-level errors to the platform error envelope.
- Echo/generate `X-Request-Id` and propagate OTel trace context.

It MUST NOT own aggregates, persist domain state, or contain business logic.

---

## Architecture Style Rationale

Gateway services have no aggregates, repositories, or domain events. Hexagonal's
port/adapter separation adds ceremony without payoff here. Layered gives:

- `config/` — route and filter wiring
- `filter/` — request/response transformation (header stripping, request-id)
- `security/` — JWT decoder and SecurityWebFilterChain
- `error/` — gateway-level error responses matching the platform envelope

All layers are small; complexity belongs in the filter pipeline, not in custom
business logic.

---

## Package Layout

```
com.wms.gateway/
├── GatewayServiceApplication.java
├── config/
│   ├── RouteConfig.java           ← route definitions (YAML-driven primarily)
│   ├── RateLimitConfig.java       ← Redis-backed key resolver + fail-open wrapper
│   └── CorsConfig.java            ← allowed origins/methods/headers
├── filter/
│   ├── IdentityHeaderStripFilter  ← global filter, HIGHEST precedence
│   └── RequestIdFilter            ← generate/echo X-Request-Id
├── security/
│   └── SecurityConfig             ← OAuth2 Resource Server + authorization rules
└── error/
    └── GatewayErrorHandler        ← 401/403/429/503 → platform envelope
```

---

## Routes (v1)

`routes.yml` (inline in `application.yml`) declares the route surface.

| Path prefix | Target | Auth | Rate Limit |
|---|---|---|---|
| `/api/v1/master/**` | `master-service:8081` | required (any authenticated role) | standard (100 rpm/IP) |
| `/api/v1/inventory/**` | `inventory-service:8083` | required (any authenticated role) | standard (100 rpm/IP) |
| `/api/v1/inbound/**` | `inbound-service:8082` | required (any authenticated role) | standard (100 rpm/IP) |
| `/webhooks/erp/asn` | `inbound-service:8082` | **HMAC-only** (no JWT, no auth filter) | webhook tier (300 rpm/IP, separate key resolver) |
| `/actuator/health` | local | none | n/a |
| `/actuator/info` | local | none | n/a |

All other paths return `404 NOT_FOUND`. Routes for outbound/admin/
notification-service arrive in subsequent `TASK-INT-*` tickets.

### Inbound Service Route

The `/api/v1/inbound/**` prefix routes manual ASN entry, inspection
recording, putaway confirmation, and admin endpoints to `inbound-service`
per [`specs/contracts/http/inbound-service-api.md`](../../contracts/http/inbound-service-api.md).
JWT enforcement, header stripping, and rate-limiting follow the same pattern
as `/api/v1/master/**`.

### ERP Webhook Route

The `/webhooks/erp/asn` route is **deliberately separate** from the
`/api/v1/inbound/**` route because:

- **No JWT**: HMAC signature on the body replaces token-based auth — the
  `SecurityConfig` permits this path through `permitAll()` and the
  `IdentityHeaderStripFilter` is skipped.
- **No identity headers**: `X-User-Id`, `X-User-Role` etc. are NOT injected
  on this route — webhook origin is `system:erp-webhook`, set by
  inbound-service on inbox-row write.
- **Higher rate-limit tier**: ERP retries can be bursty; the webhook route
  uses key resolver `(clientIp, "webhook:erp")` with replenish 300/min,
  burst 600. Per `platform/api-gateway-policy.md` defaults for webhook
  endpoints.
- **Path passthrough**: gateway forwards body bytes verbatim — no
  re-serialization of the JSON payload, since the ERP signature is over the
  raw bytes (`specs/contracts/webhooks/erp-asn-webhook.md` § Signature
  Computation).
- **CORS**: `OPTIONS` preflight not exposed (ERP is a server-to-server
  caller, not a browser).

The route is configured in `application.yml`:

```yaml
spring.cloud.gateway.routes:
  - id: erp-asn-webhook
    uri: http://inbound-service:8082
    predicates:
      - Path=/webhooks/erp/asn
      - Method=POST
    filters:
      - StripPrefix=0  # /webhooks/erp/asn → /webhooks/erp/asn (no prefix strip)
      - name: RequestRateLimiter
        args:
          redis-rate-limiter.replenishRate: 300
          redis-rate-limiter.burstCapacity: 600
          key-resolver: "#{@webhookKeyResolver}"
```

`WebhookKeyResolver` returns `webhook:erp:{clientIp}` so the bucket is
isolated from regular API traffic.

Authentication enforcement is set in `SecurityConfig` to permit
`/webhooks/erp/asn` without auth — HMAC verification happens at
inbound-service:

```java
http.authorizeExchange(spec -> spec
    .pathMatchers("/actuator/health", "/actuator/info").permitAll()
    .pathMatchers("/webhooks/erp/asn").permitAll()  // HMAC at downstream
    .anyExchange().authenticated());
```

---

## JWT Validation

Per `platform/security-rules.md` and master-service's config:

- Decoder: `NimbusReactiveJwtDecoder` with `jwk-set-uri` (static URL in v1; an
  auth-service emitting a rotating JWKS is a separate initiative).
- Required claims: `sub`, `role` / `roles`, `exp`. Missing `sub` → 401.
- Forwarded headers after successful validation:
  - `X-User-Id` ← `sub`
  - `X-User-Role` ← `role` (string) or joined `roles` (array) with commas
  - `X-User-Email` ← `email` (if present)
  - `X-Actor-Id` ← same as `sub` unless overridden by a future delegation claim
- `X-Request-Id` is generated (UUID v4) if absent; echoed verbatim if present.
- Client-supplied identity headers are stripped **before** the JWT filter runs.

---

## Rate Limiting

- Library: Spring Cloud Gateway's built-in `RedisRateLimiter` (token bucket).
- Key resolver: `(clientIp, routeId)`; client IP from `X-Forwarded-For` if a
  trusted proxy is in front (trust config documented in `application.yml`).
- Standard tier: replenish 100/min, burst 200. Values per
  `platform/api-gateway-policy.md` defaults.
- Redis unavailable → **fail open**, log at WARN, emit metric. Justified because
  rate limiting is a soft protection layer, not a correctness boundary (opposite
  of the idempotency policy on master-service, which fails closed).

---

## CORS

- Allowed origins: driven by `app.cors.allowed-origins` env var; no wildcards in
  prod.
- Allowed methods: `GET, POST, PUT, PATCH, DELETE, OPTIONS`.
- Allowed headers: `Authorization`, `Content-Type`, `X-Request-Id`,
  `Idempotency-Key`.
- Exposed headers: `X-Request-Id`, `ETag`, `Retry-After`.
- Preflight `OPTIONS` is served by the CORS filter; the JWT filter is skipped.

---

## Observability

- Access log line per request: method, path, status, latency ms, `X-Request-Id`,
  user id (if authenticated), client IP, user-agent. **No** `Authorization`
  value, **no** request/response body.
- Metrics: Micrometer `http.server.requests` (tags: method, uri, status) plus
  custom `gateway.rate_limit.rejected.total`.
- Trace: Spring observability autoconfig puts the gateway as the root span; OTel
  context propagates to downstream via `traceparent`/`tracestate`.

---

## Failure Modes

| Situation | Response |
|---|---|
| Missing / invalid JWT on protected route | 401 UNAUTHORIZED |
| JWT valid but missing required role (future, not in v1) | 403 FORBIDDEN |
| Rate limit exceeded | 429 RATE_LIMIT_EXCEEDED + `Retry-After` |
| Downstream unreachable (connect refused) | 503 SERVICE_UNAVAILABLE |
| Downstream 5xx / timeout | 502 DOWNSTREAM_ERROR |
| Circuit breaker open (integration-heavy I1) | 503 CIRCUIT_OPEN |
| Redis unavailable for rate limit | fail open + WARN log |
| JWKS source unavailable | fail closed → 503 (can't validate tokens) |

---

## Testing Strategy

- Unit: filter classes in isolation with `WebFilterChain` mocks.
- Slice: `@WebFluxTest` with `@Import(SecurityConfig.class)` + `@MockitoBean
  ReactiveJwtDecoder` — verify route protection, header enrichment, error
  envelope.
- Integration: Testcontainers (Redis + a fake downstream) for rate-limit and
  routing end-to-end. Marked `@EnabledIfDockerAvailable` for Docker-less CI.
- Contract replay (deferred): master-service contract tests are replayed through
  the gateway to assert transparency (status codes + bodies unchanged).

---

## References

- `platform/api-gateway-policy.md`
- `platform/error-handling.md`
- `platform/service-types/rest-api.md`
- `specs/services/master-service/architecture.md` (downstream target)
- `specs/services/inventory-service/architecture.md` (downstream target)
- `specs/services/inbound-service/architecture.md` (downstream target — `/api/v1/inbound/**` and `/webhooks/erp/asn`)
- `specs/contracts/webhooks/erp-asn-webhook.md` (webhook wire-level contract)
- `rules/traits/integration-heavy.md` (circuit-breaker / retry patterns)
