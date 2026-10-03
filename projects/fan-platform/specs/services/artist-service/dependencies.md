# artist-service — Dependencies

## Runtime dependencies

| Dependency | Required | Purpose | Failure mode |
|---|---|---|---|
| Postgres 16 | YES | primary store (`fanplatform_artist` DB) | service returns 5xx; gateway surfaces 503 |
| Redis 7 | NO | directory search read-through cache | fail-open — directory query bypasses cache and emits `artist_directory_cache_unavailable_total` |
| Kafka 3.7 | YES (eventual) | outbox relay target | outbox rows accumulate as PENDING; metric `artist_outbox_publish_failures_total` increments; on broker recovery rows drain. Service writes still succeed. |
| IAM IdP (OIDC) | YES | JWKS for JWT signature verification | service returns 5xx on token validation (cannot validate without JWKS). 5-minute JWKS cache mitigates short-lived blips. |

## Build dependencies

Declared in `apps/artist-service/build.gradle`:

- `org.springframework.boot:spring-boot-starter-{web,data-jpa,data-redis,validation,actuator,security,oauth2-resource-server}`
- `org.springframework.kafka:spring-kafka`
- `org.flywaydb:{flyway-core,flyway-database-postgresql}`
- `org.postgresql:postgresql` (runtime only)
- `io.micrometer:micrometer-registry-prometheus`, `micrometer-tracing-bridge-otel`, `io.opentelemetry:opentelemetry-exporter-otlp`
- `net.logstash.logback:logstash-logback-encoder` (prod profile)
- shared libs:
  - `libs:java-common` — `UuidV7`, `PageQuery/PageResult`
  - `libs:java-web` — `ErrorResponse` (`{code, message, timestamp}`), the platform error envelope returned by every handler arm that carries no structured `details`; other common web utilities
  - `libs:java-web-servlet` — `CommonGlobalExceptionHandler`. **Adopted by ADR-MONO-058 § D2 / TASK-FAN-BE-038**: `AbstractDomainExceptionHandler extends CommonGlobalExceptionHandler`, inheriting the framework arms (400 missing-header / missing-parameter, 404 `NoResourceFound`/`NoHandlerFound`, 405 + RFC 7231 `Allow`, 409 `ObjectOptimisticLockingFailureException`, 415, catch-all 500) instead of hand-copying them. Two deliberate divergences stay service-side: fan-platform's published **422** for `@Valid` / `IllegalArgumentException` (via the base's `validationFailureStatus()` hook) and artist-service's published **422** for a malformed body / unknown enum (`artist-api.md`), carried by overriding the base's `handleMalformedRequest`. The service-local `ApiErrorBody` survives **only** as the `details`-carrying envelope extension `platform/error-handling.md § Error Response Format` permits, used by the one arm whose `details` payload `artist-api.md` documents (`STATE_TRANSITION_INVALID`)
  - `libs:java-messaging` — `AbstractOutboxPublisher`, `OutboxRowEntity`
    (`@MappedSuperclass`), `SpringDataOutboxRowRepository`, `TopicResolver`,
    `OutboxMetrics` / `MicrometerOutboxMetrics`. (The v1 `OutboxWriter` /
    `BaseEventPublisher` / `OutboxPollingScheduler` / `OutboxJpaEntity` were deleted
    by TASK-MONO-312 and `ProcessedEventJpaEntity` by TASK-MONO-406 — the library
    ships no `@Entity`.)
  - `libs:java-observability` — Micrometer / OTel auto-config helpers
  - `libs:java-security` — common security utilities (no per-service identity
    logic; gateway/community-service/artist-service replicate validators
    verbatim until rule-of-three justifies extraction)

## Cross-service contracts (consumed)

### IAM IdP — OIDC Resource Server

- Issuer: `${OIDC_ISSUER_URL}` (default `http://iam.local`).
- JWKS: `${OIDC_JWK_SET_URI}` or `${JWT_JWKS_URI}` or `${OIDC_ISSUER_URL}/oauth2/jwks`.
- Algorithm: RS256 only.
- Required claims: `iss` (∈ allowed-issuers), `sub`, `tenant_id` ∈ `{ fan-platform, * }`, `exp`, `nbf`, `iat`.
- Optional: `roles[]` or `role` string for admin authorization (`ADMIN` /
  `OPERATOR` / `SUPER_ADMIN`).

The allowed-issuers list MUST stay byte-identical to the gateway's
`fanplatform.oauth2.allowed-issuers` — issuer drift between gateway and
artist-service produces silent 401s on traffic the gateway accepted.

See `projects/fan-platform/specs/integration/iam-integration.md` for the full
integration contract.

### ecommerce store — seller lookup (TASK-MONO-748 port · TASK-MONO-759 transport, `ADR-MONO-079` D2) — WIRED

Write-time verification of `agencies.store_seller_id` goes through the outbound port
`StoreSellerDirectory` (contract: `artist-api.md` § Store seller verification). The only
adapter is `HttpStoreSellerDirectory` (`adapter/out/store/`), declared by
`StoreSellerDirectoryConfig` as the single bean of that type — there is no permissive or
"unwired" alternative to fall back to.

Transport (owner decisions 2026-10-03: 갈래 A + reach path R1):

1. `client_credentials` token for **`artist-service-client`** with scope `store.seller.read`
   from the IdP (`IAM_TOKEN_URI`; registration = iam auth-service `V0042`).
2. RFC 8693 assume-tenant exchange of that token for tenant **`ecommerce`** (the only
   tenant `WorkloadTenantCatalog` lets this client assume) — cached per tenant until expiry.
3. `GET {STORE_SELLER_BASE_URL}/internal/sellers/{sellerId}` with the exchanged token —
   through the **ecommerce gateway** (`http://ecommerce.local` locally,
   `http://ecommerce.${DEMO_DOMAIN}` in the demo) to product-service
   (`product-api.md` § Internal seller read).

| Store / IdP answer | Port result | API result |
|---|---|---|
| 200 `{status}` with a known `SellerStatus` name | `Optional.of(status)` | ACTIVE / SUSPENDED / PENDING_PROVISIONING → saved · CLOSED → 422 |
| 404 with code `SELLER_NOT_FOUND` | `Optional.empty()` | 422 `STORE_SELLER_NOT_FOUND` |
| any other status (incl. a 404 without that code), malformed body, timeout, connection refused, token / exchange failure | `StoreSellerLookupUnavailableException` | 503 `STORE_SELLER_LOOKUP_UNAVAILABLE`, link NOT saved |

Timeouts: connect 2 s / read 3 s on both the IdP and the store hop
(`artist.store-seller.connect-timeout-ms` / `read-timeout-ms`).

## Cross-service contracts (produced)

### Kafka events

| Topic | Producer SLA | Consumers (planned) |
|---|---|---|
| `artist.registered.v1` | at-least-once | search-service (indexing, v2), audit pipeline |
| `artist.published.v1` | at-least-once | search-service (indexing, v2), notification-service (v2 broadcast) |
| `artist.updated.v1` | at-least-once | search-service (re-indexing) |
| `artist.archived.v1` | at-least-once | community-service (mark referenced posts/follows as archived-target), search-service |
| `artist.group_created.v1` | at-least-once | search-service |
| `artist.group_member_changed.v1` | at-least-once | search-service |

Event envelope and payloads are declared in
`projects/fan-platform/specs/contracts/events/artist-events.md`.

## Local-dev runtime

`projects/fan-platform/docker-compose.yml` provisions Postgres, Redis, Kafka,
gateway-service, community-service, and the artist-service container. Hostname
routing through Traefik exposes `/api/artists/*`, `/api/artist-groups/*`,
`/api/fandoms/*` via `http://fan-platform.local/`.
