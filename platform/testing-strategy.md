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
- Must not use H2 or in-memory substitutes for persistence layers. The
  integration-test layer is Testcontainers-only — this prohibition is **not**
  relaxed by the H2 auxiliary-slice exception (see § Testcontainers Conventions),
  which applies only to non-authoritative `@DataJpaTest` slices.

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

## Frontend E2E Smoke (Playwright URL assertions)

A frontend with a Playwright `e2e-smoke/` suite gates every PR on **URL assertions** (`page.waitForURL(...)`, `page.url()`) — often glob patterns such as `**/login`. This is a CI gate **distinct from** `tsc` / `vitest` / `lint`: unit tests do not exercise navigation, so all three can be GREEN while the Playwright smoke is RED.

**Rule** — when changing any redirect / guard / navigation behavior (an unauthenticated bounce destination, a `?redirect=<dest>` query param, a BFF base-URL switch, etc.), update the corresponding `e2e-smoke` URL assertions **in the same change**. Prefer a regex that tolerates a query string (`/\/login(\?|$)/`) over a bare glob (`**/login`) so a destination-preserving parameter does not silently break the match. Local `tsc` + `vitest` + `lint` GREEN is **necessary but not sufficient** for a navigation/URL change — confirm it against the Playwright smoke before merge.

**Authed-flow verification needs a real browser, not `curl`.** Manually verifying a login-gated page (OIDC authorization-code + PKCE, `Secure` session cookies) cannot be done with `curl` — the PKCE round-trip and cookie handling require a real user-agent, so a `curl` probe stalls at the login redirect or returns 401. Drive authed local / manual verification with a headless browser (Playwright) that completes the login, not a raw HTTP client. (Agent personal-memory detail, this host: `env_console_demo_local_redeploy`.)

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

- Use real containers via Testcontainers. Do not use H2 or in-memory substitutes — except for the narrowly-scoped auxiliary-slice case below.
- Container image versions are specified in `.claude/skills/backend/testing-backend/SKILL.md`.

## H2 auxiliary-slice exception

The "no H2" rule above and under § Integration Tests is **absolute for the
integration-test layer** (`*IntegrationTest` / `@SpringBootTest` full-context
persistence tests stay Testcontainers-only, always). It is relaxed in exactly
one place: a **supplementary `@DataJpaTest` ORM-mapping slice** may run on an H2
`MODE=PostgreSQL` in-memory database **iff ALL** of the following hold.

- **A1 — slice, not IT.** The test is a `@DataJpaTest` slice that does NOT boot
  the full Spring context. An `*IntegrationTest` may never substitute H2.
- **A2 — authoritative IT exists.** A Testcontainers integration test covering
  the **same** persistence adapter exists and remains the source of truth for
  Flyway-migrated real-Postgres behavior. The H2 slice is additive coverage,
  never a replacement.
- **A3 — non-authoritative naming.** The slice is named so its non-authoritative
  status is obvious at a glance (convention: an `H2` marker in the class name,
  e.g. a `*H2Test` suffix paralleling the authoritative `*Test`).
- **A4 — portable assertions only.** The slice asserts only ORM/JPA-mapping
  behavior portable across the H2-`MODE=PostgreSQL` dialect and real Postgres.
  Postgres-specific SQL, native upserts, JSONB operators, and Flyway-migration
  assertions belong to the Testcontainers test, not the H2 slice.

**Rationale.** On dev hosts where Testcontainers is unavailable, a
`disabledWithoutDocker` IT clean-skips — leaving the ORM path unexercised on
every Docker-free CI run. An H2 slice keeps the JPA mapping under test there
while the Testcontainers IT remains authoritative on Docker-capable runners.
The exception **permits, it does not mandate** — do not retrofit H2 slices onto
services that do not need them (on-demand policy).

## Integration-test bootstrap pitfalls (full-context-only failures)

These two failures are invisible to Docker-free `:check` (unit + slice load no Spring `ApplicationContext`); only a Testcontainers `@SpringBootTest` integration test catches them. Apply both when bootstrapping a new service's IT base or adding a multi-dependency bean.

- **Start containers in a `static { }` initializer block, NOT `@BeforeAll`/`@BeforeEach`.** `@DynamicPropertySource` suppliers (e.g. `registry.add("spring.datasource.url", POSTGRES::getJdbcUrl)`) are evaluated at Spring **context-refresh** time, which runs **before** `@BeforeAll`. A container started in `@BeforeAll` is therefore not yet running when its mapped port is queried → `IllegalStateException: Mapped port can only be obtained after the container is started` → `DataSourceAutoConfiguration` fails → every IT errors at `initializationError`. Starting the container in a `static {}` block (class-load = before context-load) fixes it; `@Testcontainers(disabledWithoutDocker=true)` clean-skip is preserved (the skip condition is evaluated before the class is used). Note: a `disabledWithoutDocker` IT base with no dedicated CI job has likely **never actually run** (clean-skips on Docker-less dev hosts) — do not treat its past "passing" as evidence; run the full suite locally once before wiring it into CI.
- **A `@Component`/`@Service` with two or more constructors MUST mark the injection constructor with `@Autowired`** (or keep a single constructor and move the test-only one to a static factory / `@TestConfiguration` bean). Spring auto-injects a sole constructor, but with 2+ it looks for `@Autowired`, and failing that falls back to a no-arg default constructor → `UnsatisfiedDependencyException` / `NoSuchMethodException: <init>()` at context load. A common trigger is adding a secondary constructor that takes a dependency directly (e.g. `RandomGenerator`/`Clock`) for deterministic unit tests: the unit test passes via that constructor, but the full context cannot choose a constructor. Never conclude wiring is safe from `:check` alone — verify with a full-context IT.

## Integration lane serialisation (CI resource contention)

A CI job that runs the `integrationTest` task of **several modules in one Gradle invocation** boots several Testcontainers stacks at once, because the root `gradle.properties` sets `org.gradle.parallel=true`. On a small runner (2 CPU / 7 GB) that can exhaust memory and CPU and **sever the containers' DB / cache connections mid-run**. Whichever module loses the race then fails — as a *cluster* of unrelated-looking assertion failures, which is why the failure does not look like contention at all.

Serialising the lane (`--no-parallel`, exposed as the `gradle-args` input of `.github/workflows/_integration.yml`) runs one stack at a time and removes the contention.

**Rule — serialise on evidence, never on module count.**

- **Module count is a hypothesis, not evidence.** Lanes with many modules have run clean for months; lanes with few have starved. Serialising by count buys wall-clock and nothing else.
- **The evidence is the starvation signature in *that lane's own* CI history**: connections severed **while the tests are executing** — `Connection is not available`, `SQLTransientConnectionException`, `RedisConnectionFailureException`, `I/O error sending to the backend`. **Zero evidence is a result**: leave the lane alone, and record that you looked.
- **A job that died before running a line of code is not evidence.** A failure at `Set up job` or action resolution is an infrastructure outage — and an outage kills many jobs at once, so *several lanes failing in the same attempt* is more often an infrastructure signal than a contention one. Classify by **where** the job died, not by how many died.
- **The failure you are looking for may never have appeared as a red run.** A rerun-to-green hides it, and `gh run list` reports only the **latest attempt's** conclusion. Query the attempts (`.../runs/<id>/attempts/<n>/jobs`) — otherwise you measure zero and conclude, wrongly, that the lane is healthy.

**The mitigation is not free.** Serialising roughly doubles a lane's wall-clock, and a serialised lane has already crossed its job timeout and been CANCELLED. A CANCELLED job is not a test failure — but it is not green either, and nothing reports it as a failure. Raise `timeout-minutes` together with the flag, and re-check the margin whenever a module joins the lane.

**A fail-closed dependency changes what the symptom looks like.** When the severed dependency is one the service denies on (a token blacklist, a rate limiter), the outage does not surface as an infrastructure error — it surfaces as *rejection*, i.e. as a plausible security or authorisation defect. Assertions that **expect** a rejection still pass in that state, so an outage always looks like "only part of it broke". Before diagnosing such a failure, grep the failing test's own output window for the dependency's outage log.

Which lanes are serialised — and the evidence each one earned it with — is recorded at the caller in `.github/workflows/ci.yml`. (or keep a single constructor and move the test-only one to a static factory / `@TestConfiguration` bean). Spring auto-injects a sole constructor, but with 2+ it looks for `@Autowired`, and failing that falls back to a no-arg default constructor → `UnsatisfiedDependencyException` / `NoSuchMethodException: <init>()` at context load. A common trigger is adding a secondary constructor that takes a dependency directly (e.g. `RandomGenerator`/`Clock`) for deterministic unit tests: the unit test passes via that constructor, but the full context cannot choose a constructor. Never conclude wiring is safe from `:check` alone — verify with a full-context IT.

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
- **Gradle test-cache after a mocked constructor changes** — when you change the constructor dependencies of a class a test mocks (e.g. add a field/arg to an `@InjectMocks` target, or change what Mockito injects), run that module's `:test` once with `--rerun-tasks` to re-establish the baseline. A stale compiled-test cache can fail the first incremental run with confusing errors even though the source is correct; one `--rerun-tasks` run resolves it. Do not chase the failure as a logic bug before re-running clean.

---

# Change Rule

Changes to test standards must be reflected here before applying to services.
