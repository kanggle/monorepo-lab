# Naming Conventions

Platform-wide naming rules for code, files, and infrastructure.

---

# Java

## Classes

| Type | Convention | Example |
|---|---|---|
| Class / Interface | PascalCase | `UserRepository`, `LoginService` |
| Record (DTO) | PascalCase + suffix | `LoginRequest`, `SignupResponse` |
| Application-layer input | `{UseCase}Command` | `CreateOrderCommand` |
| Application-layer output | `{UseCase}Result` (mutation) or `{UseCase}View` / `{UseCase}Page` (read/query) | `CreateOrderResult`, `ArtistView`, `InboxPage` |
| HTTP-layer input/output | `{UseCase}Request` / `{UseCase}Response` | `LoginRequest`, `LoginResponse` |
| Exception | PascalCase + `Exception` | `InvalidCredentialsException` |
| Configuration | PascalCase + `Config` | `SecurityConfig`, `RedisConfig` |
| `@ConfigurationProperties` binding | PascalCase + `Properties` | `RateLimitOverrideProperties`, `WebPushProperties` |
| Filter | PascalCase + `Filter` | `JwtAuthenticationFilter` |
| Controller | PascalCase + `Controller` | `AuthController` |
| Service (application-layer use case, `@Service`) | PascalCase + `Service` or PascalCase + `UseCase` | `LoginService`, `CreateOrderUseCase` |
| Repository (interface) | PascalCase + `Repository` | `UserRepository` |
| Repository (impl) | PascalCase + `RepositoryImpl` | `UserRepositoryImpl` |
| Outbound port adapter (non-repository) | PascalCase + `Adapter` | `HttpCarrierTrackingAdapter`, `FeedCacheAdapter` |
| JPA Repository | PascalCase + `JpaRepository` | `UserJpaRepository` |
| Global/base exception-handler (`@RestControllerAdvice` or its shared base) | PascalCase + `ExceptionHandler` | `GlobalExceptionHandler`, `CommonGlobalExceptionHandler` |
| Idempotency/lock store | PascalCase + `Store` | `InMemoryIdempotencyStore`, `RedisSeenSignatureStore` |

Both application-layer output forms above are accepted — `{UseCase}Result` for a use case that mutates state, `{UseCase}View`/`{UseCase}Page` for a read/query use case returning a projection. Do not mechanically rename existing `*View`/`*Page` classes to `*Result`; this row documents the convention the fleet already converged on independently across every project (a 2026-07-29 monorepo-wide audit found effectively zero `*Result` classes and dozens of `*View`/`*Page` ones).

## Methods

| Type | Convention | Example |
|---|---|---|
| General method | camelCase verb | `findByEmail`, `generateAccessToken` |
| Boolean method | `is` / `has` / `exists` prefix | `isRevoked`, `existsByEmail` |
| Factory method | `create` / `of` / `from` | `User.create(...)`, `ErrorResponse.of(...)` |

## Variables / Fields

- camelCase for all variables and fields.
- Constants: `UPPER_SNAKE_CASE` with `static final`.

## Packages

- Lowercase, dot-separated.
- Structure: `com.example.{project}.{service}.{layer}` — the `{project}` segment (e.g. `erp`, `finance`, `fanplatform`, `scmplatform`) namespaces a service's classes against same-named services in other projects. `com.example.{service}.{layer}` (3 segments, no project namespace) remains valid for the two projects that predate this convention (ecommerce, iam) — do not force a repackage of existing code to add the segment.
- Layers: `domain`, `application`, `infrastructure`, `presentation` for Layered services; Hexagonal (Ports & Adapters) services instead use `domain`, `application`, `adapter` (with `adapter/in|out` or `adapter/inbound|outbound` sub-packages, per the service's declared convention), plus a top-level `config` package — both are valid per-service styles, declared in that service's `architecture.md`.
- Sub-package structure is defined per service in `specs/services/<service>/architecture.md`.

---

# Files

## Flyway Migrations

`V{sequential_number}__{snake_case_description}.sql`

Examples:
- `V1__create_users_table.sql`
- `V2__add_index_on_email.sql`

## Test Files

Per-test-type naming (unit/controller-slice/integration/e2e) is owned by
[testing-strategy.md](testing-strategy.md) § Naming Conventions — that table is authoritative and includes the
`*ControllerSliceTest` rename precedent (`TASK-MONO-461`). Do not restate a thinner rule here; follow that
table.

---

# API Endpoints

> **Naming only.** Path shape and versioning — the mandatory `/api/` prefix and when the `v{n}` segment is required — are defined by [`versioning-policy.md`](versioning-policy.md) § HTTP API Versioning. The `v1` in the examples below is illustrative, not an assertion that every endpoint carries an explicit version segment (TASK-MONO-411).

- Use `kebab-case` for URL path segments: `/api/v1/<resource>/refresh-token` (but prefer single words where possible).
- Use plural nouns for resource collections: `/api/v1/<resources>`.
- Use verbs only for action endpoints that don't map cleanly to resources, in one of two forms — pick one per endpoint family and stay consistent within it:
  - slash form: `/api/v1/<resource>/<action>` (e.g., `/deactivate`, `/refresh`)
  - colon form (AIP-136): `/api/v1/<resource>/{id}:<action>` (e.g., `/{id}:cancel`, `/{id}:retry`) — used where a contract already declares it (`specs/contracts/http/`); do not introduce it as a new pattern without a contract-first decision, since it changes the wire path.

---

# Redis Keys

- Pattern: `{service}:{entity}:{identifier}` in `kebab-case` segments
- Use `:` as namespace separator

- All keys must have a TTL. Do not create keys without expiration.
- Service-specific key patterns must be documented in the service's spec directory (e.g. `specs/services/<service>/redis-keys.md`).

---

# Environment Variables

- `UPPER_SNAKE_CASE`.
- Prefix with service context where ambiguous: `JWT_SECRET`, `DB_URL`, `REDIS_HOST`.

---

# Tasks

- Task IDs: `TASK-{SCOPE}-{NUMBER}` where SCOPE identifies the owning scope — `MONO` for monorepo-level shared work, a work-type such as `BE` / `FE` / `INT`, or a project-specific prefix declared in that project's `tasks/INDEX.md`. A sub-task may append a lowercase letter suffix (e.g. `TASK-MONO-046-7a`).
- Task file names: `TASK-{SCOPE}-{NUMBER}-{kebab-case-title}.md`
- The authoritative registry of active task IDs is the monorepo-level `tasks/INDEX.md` and each `projects/<name>/tasks/INDEX.md`.

---

# Change Rule

Changes to naming conventions must be documented here before applying to new code.
