# artist-service — Dependencies

## Runtime dependencies

| Dependency | Required | Purpose | Failure mode |
|---|---|---|---|
| Postgres 16 | YES | primary store (`fanplatform_artist` DB) | service returns 5xx; gateway surfaces 503 |
| Redis 7 | NO | directory search read-through cache | fail-open — directory query bypasses cache and emits `artist_directory_cache_unavailable_total` |
| Kafka 3.7 | YES (eventual) | outbox relay target | outbox rows accumulate as PENDING; metric `artist_outbox_publish_failures_total` increments; on broker recovery rows drain. Service writes still succeed. |
| GAP IdP (OIDC) | YES | JWKS for JWT signature verification | service returns 5xx on token validation (cannot validate without JWKS). 5-minute JWKS cache mitigates short-lived blips. |

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
  - `libs:java-web` — common web utilities
  - `libs:java-messaging` — `OutboxWriter`, `BaseEventPublisher`,
    `OutboxPollingScheduler`, `OutboxJpaEntity`, `ProcessedEventJpaEntity`
  - `libs:java-observability` — Micrometer / OTel auto-config helpers
  - `libs:java-security` — common security utilities (no per-service identity
    logic; gateway/community-service/artist-service replicate validators
    verbatim until rule-of-three justifies extraction)

## Cross-service contracts (consumed)

### GAP IdP — OIDC Resource Server

- Issuer: `${OIDC_ISSUER_URL}` (default `http://iam.local`).
- JWKS: `${OIDC_JWK_SET_URI}` or `${JWT_JWKS_URI}` or `${OIDC_ISSUER_URL}/.well-known/jwks.json`.
- Algorithm: RS256 only.
- Required claims: `iss` (∈ allowed-issuers), `sub`, `tenant_id` ∈ `{ fan-platform, * }`, `exp`, `nbf`, `iat`.
- Optional: `roles[]` or `role` string for admin authorization (`ADMIN` /
  `OPERATOR` / `SUPER_ADMIN`).

The allowed-issuers list MUST stay byte-identical to the gateway's
`fanplatform.oauth2.allowed-issuers` — issuer drift between gateway and
artist-service produces silent 401s on traffic the gateway accepted.

See `projects/fan-platform/specs/integration/iam-integration.md` for the full
integration contract.

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
