---
name: test-strategy
description: Test type selection and coverage strategy
category: testing
---

# Skill: Test Strategy

Guidance for choosing test types and coverage targets.

Prerequisite: read `specs/platform/testing-strategy.md` for the full specification.

---

## Test Pyramid

```
        [E2E / Contract]       ← minimal, slow, high-cost
      [Integration Tests]      ← Testcontainers, real DB/cache
    [Slice / Component Tests]  ← controller-level isolation
  [Unit Tests]                 ← pure logic, no framework
```

Every service must have coverage at all four levels unless explicitly not applicable.

---

## Backend Test Types

| Type | Scope | Tools | Naming |
|---|---|---|---|
| Unit | Service/domain class | Mockito, JUnit 5 | `*Test.java` |
| Controller Slice | HTTP mapping, validation | `@WebMvcTest`, MockMvc | `*ControllerTest.java` |
| Integration | Real DB/cache interaction | `@SpringBootTest`, Testcontainers | `*IntegrationTest.java` |
| Event | Producer/consumer with Kafka | Testcontainers Kafka | `*EventTest.java` |

---

## Frontend Test Types

| Type | Scope | Tools | Location |
|---|---|---|---|
| Hook | React Query hooks | Vitest, Testing Library | `__tests__/features/*/hooks/` |
| Component | UI behavior | Vitest, Testing Library | `__tests__/features/*/components/` |
| Page | Page-level rendering | Vitest, Testing Library | `__tests__/app/` |

---

## What to Test per Task Type

| Task Type | Required Tests |
|---|---|
| New API endpoint | Unit + controller slice + integration |
| New event publisher | Unit + event integration |
| New event consumer | Unit + event integration + idempotency |
| New frontend page | Hook + component tests |
| Bug fix | Regression test that reproduces the bug |
| Refactoring | Existing tests must pass (no new tests needed unless coverage gap) |

---

## Coverage Targets

- All service/domain logic: unit tested.
- All controllers: slice tested.
- All repository interactions: integration tested.
- All event flows: producer and consumer tested.
- All frontend hooks: tested with mocked API.
- All interactive components: tested with user events.

---

## Rules

- No H2 in integration tests — use Testcontainers with PostgreSQL/Redis/Kafka.
- Each test must be independent — no shared mutable state.
- Test names describe business behavior, not implementation.
- `@DisplayName` uses Korean for readability.
