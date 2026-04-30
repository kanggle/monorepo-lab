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

## Container Lifecycle

- Infrastructure containers that every integration test needs (MySQL, Kafka)
  must extend `libs/java-test-support/.../AbstractIntegrationTest` rather than
  declaring their own `@Container` fields. The base class starts MySQL and
  Kafka **once per JVM** in a `static { }` block and registers their
  properties via `@DynamicPropertySource` — the containers outlive every
  `ApplicationContext` so Spring's test `ContextCache` rotations (new
  `HikariPool-N`) cannot invalidate the JDBC URL mid-run.
  - Rationale: TASK-BE-074/076 CI diagnosis — per-class `@Container` lifecycle
    let `ContextCache` recreate contexts whose `@DynamicPropertySource`
    lambdas pointed at already-stopped containers, producing
    `HikariPool-2 ... total=0 / CommunicationsException / Node 1 disconnected`
    9 tests ran into.
- Service-specific containers (Redis, WireMock, Elasticsearch, additional
  Kafka topics) are declared on the **subclass** with their own `@Container`
  + `@DynamicPropertySource` supplier. Spring merges every
  `@DynamicPropertySource` up the class hierarchy, so the base's MySQL/Kafka
  registrations stay active.
- Image versions live in `AbstractIntegrationTest` (`mysql:8.0`,
  `confluentinc/cp-kafka:7.6.0`). Bump there first; all subclasses pick it up
  automatically.
- JUnit's `@Testcontainers` extension is kept on subclasses so their
  subclass-declared `@Container` fields are managed. The shared base does
  not need it (its fields are managed by a static initializer block).
- Never hardcode container host ports. Rely on Testcontainers' randomly mapped
  ports and wire them into Spring via `@DynamicPropertySource`.
- `@DynamicPropertySource` suppliers are evaluated lazily when Spring builds
  the `ApplicationContext`. The container must already be started by then; if
  you need a value before context startup (e.g., WireMock base URL), start the
  auxiliary server in a `static { }` block or inside the
  `@DynamicPropertySource` method itself.

## Wait Strategy and Startup Timeout

The CI runner is frequently slower than a developer laptop. To avoid spurious
`ContainerLaunchException: ...timed out` failures, every container used in an
integration test must declare:

- `.withStartupTimeout(Duration.ofMinutes(3))` on MySQL, Kafka, and any other
  `GenericContainer` where default startup exceeds a minute on CI.
- `.waitingFor(Wait.forLogMessage(".*\\[KafkaServer id=\\d+\\] started.*", 1))`
  on `KafkaContainer`. Port-listening alone (`Wait.forListeningPort()`) does
  not guarantee broker metadata has been published; CI runs observed
  Producer/Consumer first-connect races where the broker port was open but
  advertised-listeners had not propagated, surfacing as `Node 1 disconnected,
  Connection could not be established` (TASK-BE-075 diagnosis from TASK-BE-074
  CI artifacts). The log pattern matches both `confluentinc/cp-kafka:7.5.x`
  and `7.6.x`. If a future image changes this line, fall back to
  `Wait.forListeningPort()` combined with an application-level `Awaitility`
  poll for metadata readiness — do not silently wait for a log pattern that
  never matches.

MySQL's default `Wait.forLogMessage` strategy is sufficient — do not override
it unless the test image changes.

## Producer / Consumer Retry Tuning (Test Profile Only)

Integration tests that publish to or consume from Kafka must run with the
following producer **and** consumer overrides so that a transient broker drop
(common under a heavily loaded CI runner, or random port assignment between
container restarts) does not fail the test before Kafka recovers:

```yaml
spring:
  kafka:
    consumer:
      properties:
        reconnect.backoff.ms: 500
        reconnect.backoff.max.ms: 10000
        request.timeout.ms: 60000
    producer:
      properties:
        reconnect.backoff.ms: 500
        reconnect.backoff.max.ms: 10000
        request.timeout.ms: 60000
```

Keep these in `src/test/resources/application-test.yml` (or equivalent). Do
**not** copy them into the production profile — tighter defaults are correct
for production.

Rationale for the tighter `reconnect.backoff.ms=500` vs the earlier 1000ms
default (TASK-BE-075): CI sees random port assignment between Testcontainers
restarts, so aggressive reconnect is the difference between a test passing on
the second metadata refresh and failing on a stale cached endpoint. The
`reconnect.backoff.max.ms=10000` cap prevents the client from drifting into
60s+ backoff once recovery succeeds.

## MySQL Hikari Validation (Test Profile Only)

Integration tests that share a JVM across multiple `@SpringBootTest` classes
must configure Hikari to validate every connection borrow and recycle idle
connections aggressively:

```yaml
spring:
  datasource:
    hikari:
      validation-timeout: 3000
      connection-test-query: SELECT 1
      max-lifetime: 60000
      keepalive-time: 30000
      leak-detection-threshold: 10000
```

Rationale (TASK-BE-075, root cause confirmed from TASK-BE-074 CI artifacts):
when a prior test class' MySQL Testcontainer is stopped and a new one is
started for the next class, Hikari may hand out a connection cached against
the stopped container. This surfaces as a `Communications link failure` inside
`OutboxPollingScheduler.pollAndPublish`, which fails the transaction and
produces HTTP 503 responses from otherwise healthy controllers.

- `validation-timeout: 3000` — cap the validation call at 3s so a dead
  connection is discarded quickly.
- `connection-test-query: SELECT 1` — provide a minimal query as a backup to
  the JDBC `isValid()` check (some MySQL driver versions return stale `true`).
- `max-lifetime: 60000` — force recycling during the short test lifetime.
- `keepalive-time: 30000` — proactively validate idle connections.
- `leak-detection-threshold: 10000` — surface accidental connection leaks
  before they starve the pool.

Invariant: `max-lifetime` must be strictly greater than `keepalive-time`
(60000 > 30000). Hikari refuses to start otherwise. Do **not** copy these
tight values into the production profile — the pool there should favour
long-lived connections and trust the DB to enforce server-side timeouts.

## Scheduler Thread Lifecycle (Test Context Rotation)

Schedulers that open JDBC transactions from `@Scheduled` methods must use
a **context-scoped** `TaskScheduler` bean, not Spring's default singleton
`TaskScheduler`. The default executor is a process-wide singleton whose
threads outlive any individual `ApplicationContext`; Testcontainers
integration tests that rotate Spring contexts therefore see `scheduling-1`
keep polling with a closure that captured the destroyed context's
HikariCP pool — surfacing as `HikariPool-N Connection is not available
... total=0` / `CommunicationsException` on every subsequent tick (TASK-BE-077
diagnosis from PR #44 / TASK-BE-076 CI artifacts).

Canonical pattern (see `libs/java-messaging/OutboxSchedulerConfig` +
`OutboxPollingScheduler`):

- Expose a dedicated `ThreadPoolTaskScheduler` bean with
  `destroyMethod = "shutdown"`,
  `setWaitForTasksToCompleteOnShutdown(true)`, and
  `setAwaitTerminationSeconds(5)`. The bean's lifetime is bound to the
  owning `ApplicationContext`, so context close terminates every
  scheduler thread.
- Drive the poll loop programmatically via `@PostConstruct` /
  `scheduleWithFixedDelay` / `@PreDestroy` / `ScheduledFuture.cancel()`
  instead of `@Scheduled`. `@PreDestroy` runs before the executor bean
  is destroyed, so in-flight ticks unwind deterministically.
- Gate the wiring with
  `@ConditionalOnProperty(name = "outbox.polling.enabled", havingValue
  = "true", matchIfMissing = true)` (or an equivalent per-scheduler
  flag). Production keeps the default (`true`); test profiles may
  opt out when they do not exercise the relay path. Tests that need the
  scheduler active leave the default or re-enable it via
  `@TestPropertySource(properties = "outbox.polling.enabled=true")`.

Do **not** rely on `@Scheduled` + an `AtomicBoolean running` guard alone
— the in-flight tick still completes before the guard takes effect, and
the next tick still fires from the orphaned singleton pool after the
context is gone. The context-scoped executor is the only fix that closes
both windows.

## Reuse Policy

Testcontainers supports container reuse across JVM runs via
`testcontainers.reuse.enable=true` in `~/.testcontainers.properties`.

- **Local development**: enabling reuse is recommended for fast feedback
  loops. Opt in per developer by editing `~/.testcontainers.properties` and
  adding `.withReuse(true)` (or `.withLabel("reusable", "true")`) on
  containers. This is a developer-local optimisation and does not need to be
  checked into the repository.
- **CI runners**: reuse must stay **disabled**. CI relies on a clean container
  per test session to avoid cross-test leakage and to match production
  startup behaviour. Do not add `.withReuse(true)` unconditionally in test
  source.

If you add reuse support behind a flag, scope it to a per-developer system
property so CI remains unaffected.

## Docker Availability Guard

For tests that must run on machines without Docker, gate the class with
`@EnabledIf("isDockerAvailable")` and check
`DockerClientFactory.instance().isDockerAvailable()` (or equivalent). This
lets `./gradlew test` stay green on developer machines that have no Docker
while the same tests execute on CI.

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
