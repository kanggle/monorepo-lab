# gateway-service — Architecture

This document declares the internal architecture of `erp-platform/apps/gateway-service`.
All implementation tasks targeting this service must follow this declaration,
`platform/api-gateway-policy.md`, and `platform/architecture-decision-rule.md`.

Bootstrapped by **TASK-MONO-357** (ADR-MONO-048 D7 step 4), which resolves
**TASK-MONO-347 direction A**: the policy said every project has a gateway, erp did not,
and the code was made to match the policy rather than the policy relaxed to match the code.

---

## Identity

| Field | Value |
|---|---|
| Service name | `gateway-service` |
| Project | `erp-platform` |
| Service Type | `rest-api` (edge gateway role) |
| Architecture Style | **Layered** (no domain aggregates — see Rationale) |
| Primary language / stack | Java 21, Spring Boot 3.4, **Spring Cloud Gateway (reactive)** |
| Bounded Context | n/a — this service contains no domain logic |
| Deployable unit | `apps/gateway-service/` |
| Data store | none (stateless) |
| Event publication | none |
| Shared state | Redis — rate-limit counters only (ephemeral) |

### Service Type Composition

Single-type `rest-api` per `platform/service-types/INDEX.md`. The role is **edge gateway
only**: synchronous HTTP routing + JWT validation + tenant enforcement + rate limiting +
identity-header normalization. No aggregates, no events, no Kafka.

---

## What this gateway is actually for

Erp's downstream services **read no identity headers at all** (verified by census in
TASK-MONO-357: zero `X-*` identity headers consumed across `masterdata-service`, `approval-service`,
`notification-service` and `read-model-service` — they derive the actor from the verified JWT via
`ActorContextJwtAuthenticationConverter`). So this gateway's value is **not** header
enrichment. It is:

1. **Stripping client-supplied identity headers.** Nothing reads them today, and that is
   exactly why the strip must exist *now*: the first service that starts reading one must
   not inherit a forged value. This is the `TASK-BE-501` lesson, and the `X-Seller-Scope`
   lesson from `TASK-MONO-356` — where a header three services *did* trust from the request
   went unstripped for as long as nobody was looking.
2. **A single external entry point.** Before this service, Traefik routed `erp.local`
   **directly at the four backend services** by `PathPrefix` rule — a
   reverse proxy with no JWT validation, no rate limit and no error envelope, in plain
   violation of `api-gateway-policy.md` L13/L14. The gateway now owns `erp.local`; the
   backend services drop to `expose:` only.
3. **Rate limiting and a uniform error envelope** at the edge.

Defence-in-depth is preserved, not replaced: each backend keeps its
`ServiceLevelOAuth2Config`, so a token is validated at the edge **and** at the service.

---

## Responsibilities

Per `platform/api-gateway-policy.md` this service MUST:

- Route `/api/erp/masterdata/**` → `masterdata-service`, `/api/erp/approval/**` → `approval-service`,
  `/api/erp/notifications/**` → `notification-service`, `/api/erp/read-model/**` → `read-model-service`.
- Validate JWT bearer tokens (OAuth2 Resource Server) against IAM's JWKS.
- Enforce tenant isolation via **entitlement-trust dual-accept** (ADR-MONO-019 § D5):
  admitted when `tenant_id ∈ { erp, * }` (`*` = SUPER_ADMIN platform-scope) **or** the
  IAM-signed `entitled_domains` claim contains `erp`. Rejection requires **both** to
  fail. This is **not a new decision** — it is the gate erp's own services already
  declare in `ServiceLevelOAuth2Config` (`erpplatform.oauth2.required-tenant-id:erp`,
  wildcard + entitlement). A gateway with a different gate than the service behind it would
  mean the two disagree about which tokens are valid.
- Strip client-supplied identity headers **before** the JWT filter runs, and set them from
  verified claims.
- Rate-limit per `(account, route)` for authenticated traffic, falling back to
  `(clientIp, route)`, with keys prefixed `rate:erp-platform:` — an unprefixed key
  collides if projects ever share a Redis.
- Normalize gateway-level errors to the platform envelope (`{ code, message, timestamp }`).
- Echo/generate `X-Request-Id` and propagate OTel trace context.

It MUST NOT own aggregates, persist domain state, or contain business logic.

---

## Architecture Style Rationale

Gateways have no aggregates, repositories or domain events; Hexagonal's port/adapter
separation buys nothing here. Spring Cloud Gateway already organises behaviour around
filters and configuration. **Layered**, matching the wms / scm / fan / ecommerce gateways —
this is the project's single service exempt from Hexagonal, and the exemption is the same
one `platform/architecture-decision-rule.md` grants those four.

---

## Package Layout

Almost everything a gateway needs now lives in **`libs/java-gateway`** (ADR-MONO-048). What
remains here is only what is genuinely erp's:

```
com.example.erp.gateway/
├── GatewayServiceApplication.java     ← scanBasePackages MUST name com.example.apigateway
└── config/
    ├── GatewayIdentityConfig.java     ← strip additions + enrichment mappings (erp's header policy)
    ├── OAuth2ResourceServerConfig.java← property prefix + tenantGate() + JWKS probe bean
    └── RateLimitConfig.java           ← key resolvers (account-keyed, project-prefixed)
```

From `libs/java-gateway` (**not** re-implemented here): `SecurityConfig`,
`IdentityHeaderStripFilter`, `JwtHeaderEnrichmentFilter`, `RequestIdFilter`,
`RetryAfterFilter`, `TenantClaimValidator`, `AllowedIssuersValidator`, `GatewayJwtDecoders`,
`JwtClaims`, `FailOpenRateLimiter`, `JwksHealthProbe`, `ApiErrorEnvelope`,
`GatewayErrorHandler`.

> **The component scan is load-bearing.** The library's beans sit outside
> `com.example.erp.gateway`, so `@SpringBootApplication`'s default scan cannot see them.
> Omit `com.example.apigateway` from `scanBasePackages` and the gateway boots **without its
> security chain** — every unit test still passes, the build is green, and nothing says so.
> `GatewayComponentScanTest` asserts the list.

### Allowed dependencies

`spring-cloud-starter-gateway`, `spring-boot-starter-{actuator,security,oauth2-resource-server,data-redis-reactive}`,
`micrometer-registry-prometheus`, `micrometer-tracing-bridge-otel`; shared libs
`libs:java-common`, `libs:java-gateway`, `libs:java-observability`.

### Forbidden dependencies

- `spring-boot-starter-web` (Servlet stack — conflicts with Gateway's WebFlux)
- any service contract jar, any persistence beyond `data-redis-reactive`, any domain library
- **`api` configuration on `libs:java-gateway`** — it would leak WebFlux/SCG transitively
  (the mirror of the TASK-MONO-044a incident)

---

## Routes

Downstream controllers already serve the `/api/erp/**` namespace, so **no `RewritePath`
is needed** — the gateway forwards 1:1. (wms/scm/fan rewrite `/api/v1/x` → `/api/x`; erp
never adopted the `v1` external prefix, and introducing one here would be a client-visible
contract change, not a gateway bootstrap.)

| External path | Target | Auth | Rate limit |
|---|---|---|---|
| `/api/erp/masterdata/**` | `masterdata-service:8080` | required | account/IP — 1 r/s replenish, 120 burst |
| `/api/erp/approval/**` | `approval-service:8080` | required | account/IP — 1 r/s replenish, 120 burst |
| `/api/erp/notifications/**` | `notification-service:8080` | required | account/IP — 1 r/s replenish, 120 burst |
| `/api/erp/read-model/**` | `read-model-service:8080` | required | account/IP — 1 r/s replenish, 120 burst |
| `/actuator/health`, `/actuator/info` | local | none | n/a |

All other paths → 404.

`/actuator/prometheus` is **not** routed through the gateway; Prometheus scrapes each service
directly on the internal network (same isolation contract as scm/fan).

---

## JWT Validation

- Decoder: `NimbusReactiveJwtDecoder` with `jwk-set-uri` at IAM (`/oauth2/jwks`).
- Chain (assembled by the shared `GatewayJwtDecoders.validatorChain`, in order): timestamps →
  `AllowedIssuersValidator` (SAS issuer URL + legacy `iam` string, D2-b window) →
  `TenantClaimValidator` (erp's gate, above) → Spring defaults.
- An **empty** issuer allowlist is a misconfiguration, not "accept any issuer":
  `AllowedIssuersValidator` refuses to be constructed from one, so a missing
  `erpplatform.oauth2.allowed-issuers` fails the boot.
- Forwarded headers after successful validation: `X-User-Id` ← `sub`, `X-Actor-Id` ← `sub`,
  `X-User-Email` ← `email` (when present), `X-User-Role` ← `roles`/`role` (**always** — an
  empty value means "no authorized role" and must deny; an absent header would let a service
  default open), `X-Tenant-Id` ← `tenant_id`.
- Client-supplied identity headers are stripped **before** the JWT filter runs. The strip set
  is the library baseline and is **add-only** — a domain may add headers, never remove one.

### JWKS startup probe

`JwksHealthProbe` (opt-in `@Bean`, from the library) runs on `ApplicationReadyEvent`, retries
with exponential backoff, and closes the context on final failure so an IdP outage surfaces at
boot rather than as the first caller's 401. Disable in tests via
`gateway.jwks.startup-probe.enabled=false`.

---

## Rate Limiting

- `RedisRateLimiter` (token bucket) wrapped by the library's `FailOpenRateLimiter`.
- Key resolver: `accountKeyResolver` → `rate:erp-platform:<routeId>:acct:<sub>` for
  authenticated traffic, falling back to `rate:erp-platform:<routeId>:<clientIp>`.
- **Why account-keyed and project-prefixed**: an authenticated caller is individually
  identifiable, and bucketing them by IP throws that away — everyone behind one NAT shares a
  bucket while an abuser rotating IPs is never throttled per account. The prefix keeps two
  domains from colliding if they ever share a Redis. This is what
  `platform/api-gateway-policy.md` § Rate Limiting > Key shape now requires of every gateway.
  > **Correction (TASK-MONO-370).** This section used to say wms's IP-only keying carried
  > "no documented rationale". It did: policy L92 declared `(clientIp, routeId)` as the
  > platform *default* at the time, so wms was the gateway that **conformed**. TASK-MONO-368
  > raised the rule; TASK-MONO-370 aligned wms. The claim was false when written — it asserted
  > a fleet convention from a head-count instead of reading the policy.
- Redis unavailable → **fail open** + `gateway_ratelimit_redis_unavailable_total` + WARN.
  Non-Redis errors **propagate** + `gateway_ratelimit_unexpected_error_total`. Narrowing
  fail-open to Redis-class failures is `TASK-BE-502`; do not widen it back.

---

## CORS

Origins from `CORS_ALLOWED_ORIGINS` (default `http://erp.local`); no wildcards in prod.
Methods `GET, POST, PUT, PATCH, DELETE, OPTIONS`. Allowed headers `Authorization`,
`Content-Type`, `X-Request-Id`, `Idempotency-Key`. Exposed `X-Request-Id`, `ETag`,
`Retry-After`.

---

## Failure Modes

| Situation | Response |
|---|---|
| Missing / invalid JWT on protected route | 401 UNAUTHORIZED |
| `tenant_id ∉ {erp, *}` **and** `entitled_domains ∌ erp` | 403 TENANT_FORBIDDEN |
| Rate limit exceeded | 429 + `Retry-After: 1` |
| Downstream unreachable | 503 |
| Downstream 5xx / timeout | 502 / 504 |
| Redis unavailable (rate limit) | fail-open + WARN + metric |
| JWKS unavailable at startup | fail closed → context shutdown (non-zero exit) |
| JWKS unavailable post-startup | fail closed → 5xx |

---

## Testing Strategy

The library owns the mechanism and tests it once (`libs/java-gateway`). This service tests
**its policy**:

- `GatewayComponentScanTest` — the library package is in `scanBasePackages`. Removing it
  fails the build instead of silently disarming the edge.
- `TenantClaimValidatorTest` — built from the production `OAuth2ResourceServerConfig#tenantGate()`,
  so a change to the wiring turns this red. Asserts both what the gate **accepts** (erp,
  `*`, entitled) and what it **refuses** (another tenant with no entitlement, blank/absent
  claim). Asserting only the accepts is how TASK-MONO-355 found wms's no-wildcard policy had
  zero coverage.
- `IdentityHeaderStripFilterTest` / `JwtHeaderEnrichmentFilterTest` — built from the
  production `GatewayIdentityConfig` beans, so deleting a mapping turns them red.
- `GatewayFilterOrderingTest` — strip → requestId → retryAfter. The invariant spans the
  library/service boundary and can only be asserted from here.
- `ClientIpKeyResolverTest` — rate-limit key shape (project prefix present).

---

## References

- `platform/api-gateway-policy.md` · `platform/error-handling.md` · `platform/service-types/rest-api.md`
- [`ADR-MONO-048`](../../../../../docs/adr/ADR-MONO-048-shared-reactive-gateway-library.md) — the shared gateway library
- ADR-MONO-019 § D5 — entitlement-trust dual-accept
- `TASK-MONO-347` — the drift this service closes · `TASK-MONO-357` — this service's bootstrap task
- `projects/scm-platform/apps/gateway-service` — reference implementation
