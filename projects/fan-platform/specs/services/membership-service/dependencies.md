# membership-service — Dependencies

> Spec authored by **TASK-FAN-BE-008**. Build + infra wiring = **TASK-FAN-BE-009**.

## Runtime dependencies

| Dependency | Required | Purpose | Failure mode |
|---|---|---|---|
| Postgres 16 | YES | primary store (`fanplatform_membership` DB) | service returns 5xx; gateway surfaces 503. Internal access-check → **fail-closed deny** (`allowed=false`). |
| Kafka 3.7 | YES (eventual) | outbox relay target | outbox rows accumulate as PENDING; metric `membership_outbox_publish_failures_total` increments; on broker recovery rows drain. Service writes still succeed. |
| GAP IdP (OIDC) | YES | JWKS for JWT signature verification — end-user tokens AND workload-identity (`client_credentials`) tokens on `/internal/**` | service returns 5xx on token validation (cannot validate without JWKS). 5-minute JWKS cache mitigates short-lived blips. |
| Payment Gateway | NO (mock) | `PaymentGatewayPort` authorize | v1 ships `MockPaymentGatewayAdapter` (deterministic). No real external PG; no network dependency. A real adapter is a future increment. |

> No Redis — membership-service has no read-cache case in v1 (access-check is a
> single indexed point read).

## Build dependencies

Declared in `apps/membership-service/build.gradle` (authored by FAN-BE-009):

- `org.springframework.boot:spring-boot-starter-{web,data-jpa,validation,actuator,security,oauth2-resource-server}`
- `org.springframework.kafka:spring-kafka`
- `org.flywaydb:{flyway-core,flyway-database-postgresql}`
- `org.postgresql:postgresql` (runtime only)
- `io.micrometer:micrometer-registry-prometheus`, `micrometer-tracing-bridge-otel`, `io.opentelemetry:opentelemetry-exporter-otlp`
- `net.logstash.logback:logstash-logback-encoder` (prod profile)
- shared libs:
  - `libs:java-common` — `UuidV7`, `PageQuery/PageResult`, `ClockPort`
  - `libs:java-web` — `ErrorResponse`, `CommonGlobalExceptionHandler` (membership-service's `GlobalExceptionHandler` uses the project envelope shape, matching community-service)
  - `libs:java-messaging` — `OutboxWriter`, `BaseEventPublisher`, `OutboxPollingScheduler`, `OutboxJpaEntity`, `ProcessedEventJpaEntity`
  - `libs:java-observability` — Micrometer / OTel auto-config helpers
  - `libs:java-security` — common security utilities (no per-service identity logic; gateway/community/membership-service replicate validators verbatim until rule-of-three justifies extraction)

## Cross-service contracts (consumed)

### GAP IdP — OIDC Resource Server (end-user tokens)

- Issuer: `${OIDC_ISSUER_URL}` (default `http://iam.local`).
- JWKS: `${JWT_JWKS_URI}` or `${OIDC_ISSUER_URL}/oauth2/jwks`.
- Algorithm: RS256 only.
- Required claims: `iss` (∈ allowed-issuers), `sub`, `tenant_id` ∈ `{ fan-platform, * }`, `exp`, `nbf`, `iat`.
- Optional: `roles[]` or `role` string.

### GAP IdP — workload identity (`/internal/**` inbound)

- The `/internal/membership/access` endpoint is authenticated by a GAP
  `client_credentials` JWT (ADR-MONO-005). membership-service is the **receiver**;
  community-service is the **caller**.
- The internal security chain validates issuer + signature + a recognized internal
  client identity/role; end-user tokens and missing tokens are rejected 401/403.
- See `projects/fan-platform/specs/integration/iam-integration.md` and
  ADR-MONO-005.

## Cross-service contracts (produced)

### Internal HTTP — access-check (consumed by community-service)

| Endpoint | Caller | Auth | Contract |
|---|---|---|---|
| `GET /internal/membership/access?accountId=&tier=&tenantId=` → `{ allowed }` | community-service `HttpMembershipChecker` (FAN-BE-010) | workload-identity `client_credentials` JWT | 1:1 with `MembershipChecker.hasAccess(accountId, tier, tenantId) → boolean`; fail-closed |

### Kafka events

| Topic | Producer SLA | Consumers (planned) |
|---|---|---|
| `fan.membership.activated.v1` | at-least-once | notification-service (subscription welcome / fanout, v2) |
| `fan.membership.canceled.v1` | at-least-once | notification-service (cancellation notice, v2) |
| `fan.membership.expired.v1` | **forward-declared, NOT emitted in v1** (read-time expiry; no scheduler) | notification-service (v2 — pending a future sweeper increment) |

Event envelope and payloads are declared in
`projects/fan-platform/specs/contracts/events/fan-membership-events.md`.

## Local-dev runtime

`projects/fan-platform/docker-compose.yml` (extended by FAN-BE-009) provisions
Postgres, Kafka, and the membership-service container. Hostname routing through
Traefik exposes `/api/v1/memberships/*` via `http://fan-platform.local/`. The
`/internal/**` surface is bound to the internal `fan-platform-net` docker network
only — never Traefik/gateway-routed.
