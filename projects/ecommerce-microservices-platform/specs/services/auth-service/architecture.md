# auth-service — Architecture

This document declares the internal architecture of `auth-service`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `auth-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `rest-api + event-consumer` (hybrid — see Service Type Composition below) |
| Architecture Style | **DDD-style Architecture** (4-layer + domain/repository ports) |
| Domain | ecommerce |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | Authentication / Identity (email-password login / OAuth 2.0 social login / JWT access + refresh token rotation / session registry / signup / user withdrawal / audit log) |
| Deployable unit | `apps/auth-service/` |
| Data store | PostgreSQL (owned) — `auth_db` |
| Event publication | Kafka via outbox (`auth.user.signed-up`, `auth.login.*`, `auth.token.*` lifecycle events) |
| Event consumption | `UserWithdrawn` from `user.user.withdrawn` (deactivates User + revokes refresh tokens on downstream withdrawal) |

### Service Type Composition

`auth-service` is a hybrid service per
`platform/service-types/INDEX.md` § Hybrid Cases (REST service that also
consumes events). Primary type is `rest-api` for login / signup / OAuth
callback / token refresh / logout endpoints; the secondary `event-consumer`
capability subscribes to `user.user.withdrawn` to deactivate User aggregates
and revoke active refresh tokens when user-service signals account withdrawal.
The primary type determines the spec read order — applied rules:
[platform/service-types/rest-api.md](../../../../../platform/service-types/rest-api.md).
The secondary capability is documented under "Events" / "Integration Rules"
below with topic / consumer-group details.

---

## Why This Architecture

Authentication is the security boundary for the entire ecommerce platform.
Domain rules (email validity, password hashing policy, refresh token rotation
invariants, OAuth state TTL, role assignment, session lifecycle) require
explicit modeling rather than CRUD scaffolding. JWT issuance + refresh token
rotation + multi-provider OAuth + audit log are all distinct ports with
strict invariants.

DDD-style 4-layer with framework-free domain entities + domain repository
ports (`UserRepository`, `RefreshTokenStore`, `OAuthStateStore`,
`UserSessionRegistry`, `AuthEventPublisher`, etc.) isolates these invariants
from Spring Security / Spring Data JPA / Spring Kafka adapters.

## Internal Structure Rule

This service uses a DDD-style internal structure.

Internal areas:
- `presentation` — HTTP controllers + DTOs + exception advice + filters
- `application` — use-case services (Login / Signup / RefreshToken / Logout /
  OAuth / UserWithdrawal / AuditLog) + application DTOs + application exceptions
- `domain` — framework-free entities (`User`, `AuditLog`, `Email`) + repository
  ports (`UserRepository`, `RefreshTokenStore`, `OAuthStateStore`,
  `UserSessionRegistry`) + domain services (`TokenParser`, `TokenGenerator`,
  `OAuthProvider`, `AuthEventPublisher`, `AuthMetricsRecorder`) + domain
  events (`AuthEvent`, `UserSignedUp`)
- `infrastructure` — JPA entity mappers + Spring Security configuration +
  OAuth provider adapters (Google / Naver / Kakao etc.) + Kafka adapters +
  metrics adapters + outbox writer + KafkaListener consumers

Package organization preserves aggregate boundaries — `domain/entity/User`
owns identity invariants, never JPA-mapped directly.

## Allowed Dependencies

- presentation → application
- application → domain
- infrastructure → domain
- infrastructure → application ports (where ports defined)

## Forbidden Dependencies

- domain must not depend on Spring framework, Jakarta Persistence, Jackson,
  or any infrastructure detail
- domain must not depend on persistence implementation classes
- application layer must not contain domain rules that belong inside
  aggregates or domain services (e.g. password hash format, JWT claim shape,
  email validation rules live in `domain/`)
- controllers must not bypass application services
- repositories (ports) must not contain business decisions — adapters
  implement persistence only

## Boundary Rules

- presentation layer handles HTTP mapping, JWT issuance via cookie / header,
  and request validation entry
- application layer coordinates use-cases and transaction boundaries; saga
  participation (e.g. user withdrawal cleanup) happens here
- domain layer owns User aggregate, Email value object, AuditLog entity, and
  token/session invariants
- infrastructure layer implements JPA repositories, Spring Security adapters,
  OAuth provider HTTP clients, Kafka producers/consumers, and outbox writer

## Domain Scope

- User aggregate (id / email / passwordHash / name / role / oauthProvider /
  active / timestamps) with factory + reconstitute + deactivate
- Email value object (validation invariant)
- Refresh token (token / userId / issuedAt / expiresAt / revoked) with
  rotation invariant
- OAuth state (one-time CSRF token with 10-minute TTL)
- User session registry (concurrent session limit per user)
- AuditLog entity (immutable record of authentication events, REQUIRES_NEW
  transaction so audit persists even when caller rolls back)

## Domain Constraints

- auth-service is the ONLY owner of credentials (passwordHash, OAuth provider
  refresh tokens). Other services (user-service, order-service, etc.) must
  NOT replicate or cache credentials
- User identity (UUID) is issued exclusively by auth-service; downstream
  services receive it via `auth.user.signed-up` event
- Refresh token rotation invariant: every refresh issues a new token and
  marks the previous as revoked (single-use)
- OAuth state must be one-time-use, with 10-minute TTL, single-tenant
- AuditLog writes use `Propagation.REQUIRES_NEW` so that an audit row persists
  even when the parent transaction fails (e.g. login attempt rejection still
  records the attempt)
- Deactivated users (post-`UserWithdrawn`) cannot login; all active refresh
  tokens are revoked synchronously

## Outbox

- Pattern: Transactional Outbox
- Table: `outbox` (libs/java-messaging 표준 schema)
- Polling scheduler: `OutboxPollingScheduler` (libs `com.example.messaging.outbox.OutboxPollingScheduler` base 의 concrete subclass)
- Topic 매핑:
  - `UserSignedUp` → `auth.user.signed-up`
  - `LoginSucceeded` → `auth.login.succeeded`
  - `LoginFailed` → `auth.login.failed`
  - Other lifecycle events under `auth.*` topic namespace

## Integration Rules

- HTTP behavior must follow published contracts in
  `specs/contracts/http/auth-service-api.md` (if published; otherwise the
  controllers + DTOs are authoritative until contracts are extracted)
- Domain events must follow published event contracts in
  `specs/contracts/events/auth-events.md` (if published)
- `UserSignedUp` consumed by `user-service` (creates initial user profile)
  and by `notification-service` (welcome notification)
- `auth.login.succeeded` consumed by `iam-platform/security-service`
  (login history) and by `iam-platform/account-service`
  (`last_login_succeeded_at` projection)
- `user.user.withdrawn` consumed FROM `user-service` (downstream cleanup —
  deactivate User + revoke refresh tokens)
- Shared libraries (`libs/java-messaging`, `libs/java-common`, etc.) may be
  used only under shared-library policy

## Events

- Publishes: `UserSignedUp`, `LoginSucceeded`, `LoginFailed` (and other
  `auth.*` lifecycle events as the service grows)
- Consumes: `UserWithdrawn` (from user-service via `user.user.withdrawn`,
  groupId `auth-service`)

## Testing Expectations

Required emphasis:
- domain entity tests (User aggregate invariants, Email value object,
  refresh token rotation)
- application service tests (Login / Signup / RefreshToken / Logout / OAuth
  / UserWithdrawal flows with mocked ports)
- repository integration tests (PostgreSQL Testcontainers)
- OAuth provider integration tests (WireMock for upstream HTTP)
- KafkaListener integration tests (`UserWithdrawnEventConsumer` end-to-end)
- contract tests for published HTTP + event surfaces (when contracts are
  published)
- audit log REQUIRES_NEW behavior verified by transactional integration test

## Change Rule

Any architectural change to this service must be documented here first
before implementation.
