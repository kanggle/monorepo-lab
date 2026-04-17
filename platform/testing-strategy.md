# Testing Strategy

Defines the platform-wide testing requirements and patterns for all services.

---

# Test Pyramid

```
        [E2E / Contract]       ← minimal, slow, high-cost
      [Integration Tests]      ← Testcontainers, real DB/cache
    [Slice / Component Tests]  ← controller-level isolation
  [Unit Tests]                 ← pure logic, no framework
```

Every service must have coverage at all four levels unless the level is explicitly not applicable.

---

# Test Types

## Unit Tests

- Test a single class in isolation.
- Mock dependencies.
- Must not start the Spring context.
- Coverage target: all service/domain logic.

**Naming:** `*Test.java`

## Controller Slice Tests

- Test HTTP request/response mapping, validation, and exception handling.
- Use controller-level isolation (mock all service dependencies).
- Must verify security configuration and global exception handling behavior.

**Naming:** `*ControllerTest.java`

## Integration Tests

- Test real interactions with DB and cache using Testcontainers.
- Start full Spring context.
- Must not use H2 or in-memory substitutes for persistence layers.

**Naming:** `*IntegrationTest.java`

## Event Consumer / Producer Tests

- Test event publishing and consuming with Testcontainers Kafka.
- Producer tests: verify the correct event envelope is published after a business action completes.
- Consumer tests: verify that consuming a valid event triggers the expected side effect.
- Idempotency tests: verify that consuming the same event twice produces the same result.
- DLQ tests: verify that a malformed event is routed to the dead-letter queue, not silently dropped.

**Naming:** `*EventTest.java` (unit), `*EventIntegrationTest.java` (with Testcontainers Kafka)

## Contract Tests (future)

- Verify that HTTP API responses match published contracts.
- Tool: Spring Cloud Contract or Pact (to be decided per service).

---

# Required Tests Per Task

Every backend task with `code` tag must include:

| Layer | Test Type |
|---|---|
| Domain entity | Unit |
| Application service | Unit |
| Controller | Slice |
| Full flow | Integration |

For implementation details (annotations, imports, container images, setup code), see `.claude/skills/backend/testing-backend/SKILL.md`.

---

# Testcontainers Conventions

- Use real containers via Testcontainers. Do not use H2 or in-memory substitutes.
- Container image versions are specified in `.claude/skills/backend/testing-backend/SKILL.md`.

---

# Naming Conventions

| Test Type | Naming Pattern | Example |
|---|---|---|
| Unit (service) | `{ServiceName}Test` | `LoginServiceTest` |
| Unit (entity) | `{EntityName}Test` | `UserTest` |
| Unit (infrastructure) | `{ClassName}UnitTest` | `RedisUserSessionRegistryUnitTest` |
| Controller slice | `{ControllerName}Test` | `AuthControllerTest` |
| Integration (infrastructure) | `{ClassName}Test` | `RedisUserSessionRegistryTest` |
| Integration (full flow) | `{Feature}IntegrationTest` | `AuthSignupLoginIntegrationTest` |
| Event (unit) | `{EventName}EventTest` | `UserSignedUpEventTest` |
| Event (integration) | `{Feature}EventIntegrationTest` | `AuthEventPublishIntegrationTest` |

---

# Rules

- Tests must not share mutable state across test methods.
- Each test method must be independent and idempotent.
- Test method names must describe the scenario: `{scenario}_{condition}_{expectedResult}`.
- Production code must not contain test-only annotations or conditionals.
- Testcontainers tests must clean up or use isolated data per test (unique emails, IDs, etc.).
- Use `@DisplayName` with Korean descriptions for test readability.

---

# Change Rule

Changes to test standards must be reflected here before applying to services.
