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

- Use `kebab-case` for URL path segments: `/api/auth/refresh-token` (but prefer single words where possible).
- Use plural nouns for resource collections: `/api/products`, `/api/orders`.
- Use verbs only for action endpoints that don't map cleanly to resources: `/api/auth/login`, `/api/auth/logout`.

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

- Task IDs: `TASK-{TYPE}-{NUMBER}` where TYPE is BE, FE, or INT.
- Task file names: `TASK-{TYPE}-{NUMBER}-{kebab-case-title}.md`

---

# Change Rule

Changes to naming conventions must be documented here before applying to new code.
