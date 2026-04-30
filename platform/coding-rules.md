# Coding Rules

Platform-wide coding standards for all services and libraries.

---

# Language

- Backend services: Java 21
- Frontend: TypeScript (strict mode)
- Build system: Gradle (JVM), pnpm + Turborepo (Node)

---

# Java Rules

## General

- Use Java 21 features where appropriate: records, sealed classes, pattern matching, text blocks.
- Prefer immutability: make fields `final` wherever possible.
- Avoid `null` returns; use `Optional<T>` for optional return values in domain/application layers.
- Do not use raw types.

## Dependencies

- Inject dependencies via constructor injection only.
- Do not use field injection.

## Exceptions

- Use unchecked exceptions (extend `RuntimeException`) for business rule violations.
- Do not catch `Exception` unless at the top-level handler.
- Do not swallow exceptions silently.
- Follow [error-handling.md](error-handling.md) for error codes and response format.

## Logging

- Use structured logging via SLF4J.
- Log at `INFO` for significant business events (login, signup, order placed).
- Log at `WARN` for recoverable issues.
- Log at `ERROR` for unexpected failures; include stack traces.
- Never log sensitive data: passwords, tokens, card numbers, PII.

## Database

- Use Flyway for all schema migrations.
- Do not modify existing migration files after deployment.
- Migration file naming follows `naming-conventions.md`.

## API

- Follow REST conventions.
- Validate all controller request bodies.
- Return consistent HTTP status codes per [error-handling.md](error-handling.md).
- Use DTOs for request/response. Do not expose domain entities directly.

## Packages

- Follow `naming-conventions.md` for package structure rules.

---

# TypeScript Rules

- Enable strict mode in `tsconfig.json`.
- No `any` types unless absolutely necessary (add a comment explaining why).
- Use `interface` for external shapes, `type` for unions and computed types.
- Prefer `const` over `let`; never use `var`.

---

# General Rules

- No dead code or commented-out code in production.
- No `TODO` comments without a linked task ID (e.g. `// TODO: TASK-BE-010`).
- Do not hard-code environment-specific values (URLs, secrets, ports). Use environment variables.

---

# Change Rule

Changes to these rules require team agreement and must be updated here before applying.
