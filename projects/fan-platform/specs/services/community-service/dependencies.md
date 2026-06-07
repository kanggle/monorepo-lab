# community-service — Dependencies

## Runtime dependencies

| Dependency | Required | Purpose | Failure mode |
|---|---|---|---|
| Postgres 16 | YES | primary store (`fanplatform_community` DB) | service returns 5xx; gateway surfaces 503 |
| Redis 7 | NO | feed read-through cache | fail-open — feed query bypasses cache and emits `community_feed_cache_unavailable_total` |
| Kafka 3.7 | YES (eventual) | outbox relay target | outbox rows accumulate as PENDING; metric `community_outbox_publish_failures_total` increments; on broker recovery rows drain. Service writes still succeed. |
| IAM IdP (OIDC) | YES | JWKS for JWT signature verification | service returns 5xx on token validation (cannot validate without JWKS). 5-minute JWKS cache mitigates short-lived blips. |
| **membership-service** (v2) | NO (v1) | `MembershipChecker` real implementation | v1 ships with `AlwaysAllowMembershipChecker` (always true + WARN). v2 task replaces it via `@ConditionalOnMissingBean`. |

## Build dependencies

Declared in `apps/community-service/build.gradle`:

- `org.springframework.boot:spring-boot-starter-{web,data-jpa,data-redis,validation,actuator,security,oauth2-resource-server}`
- `org.springframework.kafka:spring-kafka`
- `org.flywaydb:{flyway-core,flyway-database-postgresql}`
- `org.postgresql:postgresql` (runtime only)
- `io.micrometer:micrometer-registry-prometheus`, `micrometer-tracing-bridge-otel`, `io.opentelemetry:opentelemetry-exporter-otlp`
- `net.logstash.logback:logstash-logback-encoder` (prod profile)
- shared libs:
  - `libs:java-common` — `UuidV7`, `PageQuery/PageResult`
  - `libs:java-web` — `ErrorResponse`, `CommonGlobalExceptionHandler` (community-service's `GlobalExceptionHandler` does not extend; uses its own envelope shape `ApiErrorBody`)
  - `libs:java-messaging` — `OutboxWriter`, `BaseEventPublisher`, `OutboxPollingScheduler`, `OutboxJpaEntity`, `ProcessedEventJpaEntity`
  - `libs:java-observability` — Micrometer / OTel auto-config helpers
  - `libs:java-security` — common security utilities (no per-service identity logic; gateway/community-service replicate validators verbatim until rule-of-three justifies extraction)

## Cross-service contracts (consumed)

### IAM IdP — OIDC Resource Server

- Issuer: `${OIDC_ISSUER_URL}` (default `http://iam.local`).
- JWKS: `${OIDC_JWK_SET_URI}` or `${JWT_JWKS_URI}` or `${OIDC_ISSUER_URL}/.well-known/jwks.json`.
- Algorithm: RS256 only.
- Required claims: `iss` (∈ allowed-issuers), `sub`, `tenant_id` ∈ `{ fan-platform, * }`, `exp`, `nbf`, `iat`.
- Optional: `roles[]` or `role` string for OPERATOR / SUPER_ADMIN authorization.

See `projects/fan-platform/specs/integration/iam-integration.md` for the full
integration contract.

## Cross-service contracts (produced)

### Kafka events

| Topic | Producer SLA | Consumers (planned) |
|---|---|---|
| `community.post.published.v1` | at-least-once | notification-service (push fanout, v2), search-service (indexing, v2) |
| `community.post.status_changed.v1` | at-least-once | search-service (re-indexing on HIDDEN/DELETED), audit pipeline |
| `community.comment.added.v1` | at-least-once | notification-service (mention/reply alerts) |
| `community.reaction.added.v1` | at-least-once | notification-service (interaction badges), analytics-service (engagement metrics, v3) |

Event envelope and payloads are declared in
`projects/fan-platform/specs/contracts/events/community-events.md`.

## Local-dev runtime

`projects/fan-platform/docker-compose.yml` provisions Postgres, Redis, Kafka,
and the community-service container. Hostname routing through Traefik exposes
`/api/community/*` via `http://fan-platform.local/`.
