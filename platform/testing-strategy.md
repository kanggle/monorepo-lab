# Testing Strategy

Defines the platform-wide testing requirements and patterns for all services.

---

# Test Pyramid

```
       [E2E (full) / Contract]   ← nightly + push to main; edge / resilience / long flow
       [E2E (smoke)]             ← every PR; cross-service happy-path contract
      [Integration Tests]        ← Testcontainers, real DB/cache
    [Slice / Component Tests]    ← controller-level isolation
  [Unit Tests]                   ← pure logic, no framework
```

Every service must have coverage at all four levels unless the level is explicitly not applicable. The E2E layer is split into **smoke** (every PR) and **full** (nightly + push to main) — see § E2E Smoke vs Full below for the taxonomy and the ADR-MONO-010 rubric.

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

## E2E Smoke vs Full

E2E tests are partitioned into two JUnit 5 tags. The partition is the canonical decision recorded in [ADR-MONO-010](../docs/adr/ADR-MONO-010-e2e-tag-taxonomy.md).

| Tag | Semantics | Cost budget | Frequency |
|---|---|---|---|
| `@Tag("smoke")` | Happy-path cross-service contract assertion. Passes when boot + JWT issuance + routing + first-hop persistence + (where present) outbox → Kafka emission succeeds. | ≤ 30 s per test method on a warmed runner (cold-start excluded). | Every PR. |
| `@Tag("full")` | Edge case / resilience / long flow / cross-project consumer / DLQ / circuit breaker / multi-step state lifecycle. | No upper bound. | Nightly cron + push to main. |

**Classification rubric.** A test is `smoke` IFF all of the following hold:

- **S1** — exercises the **happy path** of a primary cross-service flow.
- **S2** — uses **deterministic** inputs (no burst > ~20, no container pauses, no `Thread.sleep` > 5 s, no awaitility timeout > 30 s).
- **S3** — failure mode is **regression-shaped**, not stress-shaped.
- **S4** — completes (excluding cold-start) within ~30 s on a warmed runner.

Otherwise the test is `full`. Any of the following pulls a test into `full`:

- **F1** — Burst / rate-limit / load assertion.
- **F2** — Container-pause or other lifecycle-injection assertion.
- **F3** — Multi-step state lifecycle (≥ 3 state transitions).
- **F4** — Cross-project event consumption.
- **F5** — DLQ / error-routing / refresh-reuse / circuit-breaker state transitions.
- **F6** — Membership / authorization / visibility-tier edge cases that require bean-wiring acrobatics.

**Granularity rules**:

1. **Class-level by default.** Apply `@Tag("smoke")` or `@Tag("full")` on the test class alongside the existing `@Tag("e2e")` umbrella from the base class.
2. **Method-level where the class is mixed.** When one class contains scenarios that span both buckets (e.g. wms `GatewayMasterE2ETest`'s 5 nested scenarios — 3 smoke + 2 full), apply method-level `@Tag` on each `@Test` method and OMIT the class-level `smoke` / `full`.
3. **Un-tagged = `full`** (conservative default). A test that carries only `@Tag("e2e")` and no `smoke` / `full` is treated as `full` — it runs nightly, never silently in the PR fast lane. Such tests SHOULD be classified explicitly in a follow-up PR.
4. The umbrella `@Tag("e2e")` is preserved; `smoke` / `full` is additive.

**Gradle tasks per e2e module**:

- `e2eSmokeTest` — runs `@Tag("smoke")` only. CI PR-time invocation.
- `e2eFullTest` — runs `@Tag("full")` only. Nightly + push-to-main invocation.
- `e2eTest` — runs `@Tag("e2e")` umbrella (smoke + full). Back-compat for local dev.

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

| Test Type | Naming Pattern | Example (generic) |
|---|---|---|
| Unit (service) | `{ServiceName}Test` | `<ApplicationServiceClass>Test` |
| Unit (entity) | `{EntityName}Test` | `<DomainEntityClass>Test` |
| Unit (infrastructure) | `{ClassName}UnitTest` | `<InfrastructureClass>UnitTest` |
| Controller slice | `{ControllerName}Test` | `<RestControllerClass>Test` |
| Integration (infrastructure) | `{ClassName}Test` | `<PersistenceAdapter>Test` |
| Integration (full flow) | `{Feature}IntegrationTest` | `<FeatureName>IntegrationTest` |
| Event (unit) | `{EventName}EventTest` | `<DomainEvent>EventTest` |
| Event (integration) | `{Feature}EventIntegrationTest` | `<FeatureName>EventIntegrationTest` |
| E2E smoke (recommended suffix) | `{Feature}SmokeE2ETest` | `<FeatureName>SmokeE2ETest` |
| E2E full (recommended suffix) | `{Feature}FullE2ETest` | `<FeatureName>FullE2ETest` |

---

# Rules

- Tests must not share mutable state across test methods.
- Each test method must be independent and idempotent.
- Test method names must describe the scenario: `{scenario}_{condition}_{expectedResult}`.
- Production code must not contain test-only annotations or conditionals.
- Testcontainers tests must clean up or use isolated data per test (unique emails, IDs, etc.).
- Use `@DisplayName` with Korean descriptions for test readability.
- **E2E tag rule (ADR-MONO-010 D4)** — Every test class extending an e2e base class (`*E2ETestBase` or equivalent) MUST carry either `@Tag("smoke")` or `@Tag("full")` directly on the class, OR carry method-level `@Tag("smoke")` / `@Tag("full")` on each `@Test` / `@Nested` method. Tests that carry only `@Tag("e2e")` are treated as `full` (conservative default) and SHOULD be classified explicitly in a follow-up PR. The naming suffixes (`*SmokeE2ETest` / `*FullE2ETest`) are recommended but not required — `@Tag` is the authoritative classifier.

---

# Change Rule

Changes to test standards must be reflected here before applying to services.
