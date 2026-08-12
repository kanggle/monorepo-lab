# community-service — Dependencies

## Runtime dependencies

| Dependency | Required | Purpose | Failure mode |
|---|---|---|---|
| Postgres 16 | YES | primary store (`fanplatform_community` DB) | service returns 5xx; gateway surfaces 503 |
| Redis 7 | NO | feed read-through cache | fail-open — feed query bypasses cache and emits `community_feed_cache_unavailable_total` |
| Kafka 3.7 | YES (eventual) | outbox relay target | outbox rows accumulate as PENDING; metric `community_outbox_publish_failures_total` increments; on broker recovery rows drain. Service writes still succeed. |
| IAM IdP (OIDC) | YES | JWKS for JWT signature verification | service returns 5xx on token validation (cannot validate without JWKS). 5-minute JWKS cache mitigates short-lived blips. |
| **membership-service** | YES (v1, FAN-BE-010) | `HttpMembershipChecker` — `MembershipChecker.hasAccess` over workload-identity HTTP | **fail-closed**: on error/timeout/unreachable, MEMBERS_ONLY & PREMIUM reads are denied (403 `MEMBERSHIP_REQUIRED`). 🔴 **No opt-out.** TASK-FAN-INT-006 deleted the `community.membership-service.enabled` escape hatch and the inert `AlwaysAllowMembershipChecker` fallback; the live e2e now runs membership-service for real. A deployment that cannot reach membership-service denies every gated read — which is the correct direction for this dependency to fail. |
| **artist-service** | YES (v1, FAN-BE-045 / ADR-004 A) | `HttpArtistAccountChecker` — `GET /internal/artists/exists` over workload-identity HTTP, validating every follow target | **fail-closed**: token-acquisition failure, timeout, unreachable, non-2xx or malformed body all deny the follow. 🔴 **No opt-out.** The `community.artist-service.enabled` switch was deleted by TASK-FAN-INT-005 once the e2e stack gained a real IAM to mint the token from; there is no property value that selects a permissive checker. A stack that carries this service must also carry a token issuer. |

> Row added by TASK-FAN-INT-005. It was missing since FAN-BE-045 introduced the
> dependency — tolerable while the gate had an opt-out, wrong now that it does not:
> a deployer reading this table would not have known an IAM token endpoint is a
> hard prerequisite for follows to work at all.

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
  - `libs:java-web` — `ErrorResponse` (`{code, message, timestamp}`), the platform error envelope returned by every handler arm that carries no structured `details`
  - `libs:java-web-servlet` — `CommonGlobalExceptionHandler`. **Adopted by ADR-MONO-058 § D2 / TASK-FAN-BE-038**: `AbstractDomainExceptionHandler extends CommonGlobalExceptionHandler`, inheriting the framework arms (400 malformed-body / missing-header / missing-parameter, 404 `NoResourceFound`/`NoHandlerFound`, 405 + RFC 7231 `Allow`, 409 `ObjectOptimisticLockingFailureException`, 415, catch-all 500) instead of hand-copying them. fan-platform's published **422** for `@Valid` / `IllegalArgumentException` is carried by overriding the base's `validationFailureStatus()` hook. The service-local `ApiErrorBody` survives **only** as the `details`-carrying envelope extension `platform/error-handling.md § Error Response Format` permits, used by the two arms whose `details` payload `community-api.md` documents (`MEMBERSHIP_REQUIRED`, `POST_STATUS_TRANSITION_INVALID`)
  - `libs:java-messaging` — `AbstractOutboxPublisher`, `OutboxRowEntity` (`@MappedSuperclass`), `SpringDataOutboxRowRepository`, `TopicResolver`, `OutboxMetrics` / `MicrometerOutboxMetrics`. (The v1 `OutboxWriter` / `BaseEventPublisher` / `OutboxPollingScheduler` / `OutboxJpaEntity` were deleted by TASK-MONO-312 and `ProcessedEventJpaEntity` by TASK-MONO-406 — the library ships no `@Entity`.)
  - `libs:java-observability` — Micrometer / OTel auto-config helpers
  - `libs:java-security` — common security utilities (no per-service identity logic; gateway/community-service replicate validators verbatim until rule-of-three justifies extraction)

## Cross-service contracts (consumed)

### IAM IdP — OIDC Resource Server

- Issuer: `${OIDC_ISSUER_URL}` (default `http://iam.local`).
- JWKS: `${OIDC_JWK_SET_URI}` or `${JWT_JWKS_URI}` or `${OIDC_ISSUER_URL}/oauth2/jwks`.
- Algorithm: RS256 only.
- Required claims: `iss` (∈ allowed-issuers), `sub`, `tenant_id` ∈ `{ fan-platform, * }`, `exp`, `nbf`, `iat`.
- Optional: `roles[]` or `role` string for OPERATOR / SUPER_ADMIN authorization.

See `projects/fan-platform/specs/integration/iam-integration.md` for the full
integration contract.

## Cross-service contracts (produced)

### Kafka events

| Topic | Producer SLA | Consumers |
|---|---|---|
| `community.post.published.v1` | at-least-once | *(none live)* — notification-service push fanout is **blocked** on a follow graph + a `community.follow.added` event that does not exist yet; search-service indexing planned |
| `community.post.status_changed.v1` | at-least-once | *(none live)* — search-service (re-index on HIDDEN/DELETED) + audit pipeline, both planned |
| `community.comment.added.v1` | at-least-once | **live**: notification-service `CommunityEventConsumer` → REPLY / MENTION alerts (group `notification-service-community-events`, TASK-FAN-BE-026) |
| `community.reaction.added.v1` | at-least-once | **live**: notification-service `CommunityEventConsumer` → REACTION_BADGE alert (same group); analytics-service engagement metrics planned (v3) |

The two live subscriptions route **only** from the event payload — notification-service
makes no synchronous call back into community-service. That is precisely why the two
interaction payloads carry `postAuthorAccountId` / `mentionedAccountIds`
(TASK-FAN-BE-026); community-service gains **no new inbound dependency** from this
wiring.

Event envelope and payloads are declared in
`projects/fan-platform/specs/contracts/events/community-events.md`.

## Local-dev runtime

`projects/fan-platform/docker-compose.yml` provisions Postgres, Redis, Kafka,
and the community-service container. Hostname routing through Traefik exposes
`/api/community/*` via `http://fan-platform.local/`.
