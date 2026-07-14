# Naming Conventions

Platform-wide naming rules for code, files, and infrastructure.

---

# Java

## Classes

| Type | Convention | Example |
|---|---|---|
| Class / Interface | PascalCase | `UserRepository`, `LoginService` |
| Record (DTO) | PascalCase + suffix | `LoginRequest`, `SignupResponse` |
| Exception | PascalCase + `Exception` | `InvalidCredentialsException` |
| Configuration | PascalCase + `Config` | `SecurityConfig`, `RedisConfig` |
| Filter | PascalCase + `Filter` | `JwtAuthenticationFilter` |
| Controller | PascalCase + `Controller` | `AuthController` |
| Service | PascalCase + `Service` | `LoginService`, `SignupService` |
| Repository (interface) | PascalCase + `Repository` | `UserRepository` |
| Repository (impl) | PascalCase + `RepositoryImpl` | `UserRepositoryImpl` |
| JPA Repository | PascalCase + `JpaRepository` | `UserJpaRepository` |

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
- Structure: `com.example.{service}.{layer}`
- Layers: `domain`, `application`, `infrastructure`, `presentation`
- Sub-package structure is defined per service in `specs/services/<service>/architecture.md`.

---

# Files

## Flyway Migrations

`V{sequential_number}__{snake_case_description}.sql`

Examples:
- `V1__create_users_table.sql`
- `V2__add_index_on_email.sql`

## Test Files

`{TestedClass}Test.java` or `{Feature}IntegrationTest.java`

---

# API Endpoints

> **Naming only.** Path shape and versioning — the mandatory `/api/` prefix and when the `v{n}` segment is required — are defined by [`versioning-policy.md`](versioning-policy.md) § HTTP API Versioning. The `v1` in the examples below is illustrative, not an assertion that every endpoint carries an explicit version segment (TASK-MONO-411).

- Use `kebab-case` for URL path segments: `/api/v1/<resource>/refresh-token` (but prefer single words where possible).
- Use plural nouns for resource collections: `/api/v1/<resources>`.
- Use verbs only for action endpoints that don't map cleanly to resources: `/api/v1/<resource>/<action>` (e.g., `/deactivate`, `/refresh`).

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
