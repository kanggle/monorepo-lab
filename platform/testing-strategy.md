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
- The web layer is exercised through a MockMvc web slice — either `@WebMvcTest`
  or `MockMvcBuilders.standaloneSetup(...)`. A controller test that instantiates
  the controller directly with Mockito (no MockMvc) is a **unit** test, not a
  slice, and keeps the unit naming (`{ClassName}Test`).

**Naming:** `*ControllerSliceTest.java`

The `Slice` marker is load-bearing, not decoration: a bare `{Controller}Test`
does not say whether it is a `@WebMvcTest` slice or a full-context controller IT,
and it collides with the unit/`{ClassName}Test` and integration-infrastructure
`{ClassName}Test` forms below. `*ControllerSliceTest` encodes the isolation level
in the name. (Canonicalised fleet-wide in TASK-MONO-461, which also added the CI
guard that enforces it — see § CI Guards. Prior to that the fleet carried three
drifted forms: `*ControllerTest`, `*ControllerSliceTest`, `*ControllerWebMvcTest`.)

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

## Never bind a `java.sql.Timestamp` in a fixture — bind an explicit-UTC `LocalDateTime`

A fixture that binds a `java.sql.Timestamp` (or a `Date`) makes the test's correctness depend on the
**host's timezone**, because the two ends of the round-trip disagree about what the stored value means:

- the JDBC connector formats the `Timestamp` using the **JVM default timezone**, while
- the ORM reads the naive `DATETIME`/`TIMESTAMP` column back as **UTC**.

The result is a **fixed offset skew** equal to the host's UTC offset. Any assertion computed from the stored
instant (an age, an elapsed duration, a "not older than" window) is wrong by exactly that offset.

**Bind `LocalDateTime.ofInstant(instant, ZoneOffset.UTC)` instead** — it pins the meaning at the fixture
rather than inheriting it from the host.

**Why this one is worth a rule:** the failure is *invisible to the lane that is supposed to be
authoritative*. Runners are UTC, so the offset is zero and the test passes — **on CI, both the broken
fixture and the fixed one are green**. It goes RED only on a developer's non-UTC host, where it reads as a
local-environment annoyance rather than a defect. So CI cannot prove the fix, and the host that *can* see it
is the one whose signal gets dismissed. This is the § G4 axis (*a threshold calibrated on your host is a
proposition about your host*) pointing the other way: here the **runner** is the host that cannot see the
property.

Corollary for triage: an integration failure whose delta is *exactly* a whole-hour UTC offset is this bug
until proven otherwise — check the fixture's binding before suspecting the code under test.

## Integration-test bootstrap pitfalls (full-context-only failures)

These two failures are invisible to Docker-free `:check` (unit + slice load no Spring `ApplicationContext`); only a Testcontainers `@SpringBootTest` integration test catches them. Apply both when bootstrapping a new service's IT base or adding a multi-dependency bean.

- **Start containers in a `static { }` initializer block, NOT `@BeforeAll`/`@BeforeEach`.** `@DynamicPropertySource` suppliers (e.g. `registry.add("spring.datasource.url", POSTGRES::getJdbcUrl)`) are evaluated at Spring **context-refresh** time, which runs **before** `@BeforeAll`. A container started in `@BeforeAll` is therefore not yet running when its mapped port is queried → `IllegalStateException: Mapped port can only be obtained after the container is started` → `DataSourceAutoConfiguration` fails → every IT errors at `initializationError`. Starting the container in a `static {}` block (class-load = before context-load) fixes it; `@Testcontainers(disabledWithoutDocker=true)` clean-skip is preserved (the skip condition is evaluated before the class is used). Note: a `disabledWithoutDocker` IT base with no dedicated CI job has likely **never actually run** (clean-skips on Docker-less dev hosts) — do not treat its past "passing" as evidence; run the full suite locally once before wiring it into CI.
- **A `@Component`/`@Service` with two or more constructors MUST mark the injection constructor with `@Autowired`** (or keep a single constructor and move the test-only one to a static factory / `@TestConfiguration` bean). Spring auto-injects a sole constructor, but with 2+ it looks for `@Autowired`, and failing that falls back to a no-arg default constructor → `UnsatisfiedDependencyException` / `NoSuchMethodException: <init>()` at context load. A common trigger is adding a secondary constructor that takes a dependency directly (e.g. `RandomGenerator`/`Clock`) for deterministic unit tests: the unit test passes via that constructor, but the full context cannot choose a constructor. Never conclude wiring is safe from `:check` alone — verify with a full-context IT.

## Integration lane serialisation (CI resource contention)

A CI job that runs the `integrationTest` task of **several modules in one Gradle invocation** boots several Testcontainers stacks at once, because the root `gradle.properties` sets `org.gradle.parallel=true`. That can exhaust memory and CPU and **sever the containers' DB / cache connections mid-run**. Whichever module loses the race then fails — as a *cluster* of unrelated-looking assertion failures, which is why the failure does not look like contention at all.

**The runner, measured** (2026-07-20, `TASK-MONO-445` — two independent runs, one `ci.yml` job and one `federation-hardening-e2e.yml` job): **4 CPU / 15 GiB** on `ubuntu-latest`. **Quote it with its date.** A runner spec is an observation about a fleet at a point in time, not a constant — this paragraph previously read *"On a small runner (2 CPU / 7 GB)"*, which is the **private**-repo figure. This repository is public. The wrong number had gone unchecked long enough to be repeated in five other files, while three other files simultaneously said 16 GB; nothing reconciled them because no job ever printed `nproc`.

**The size was never the argument, and the corrected — larger — figure is not grounds to unserialise a lane.** Serialisation is earned by the starvation signature in a lane's *own* history (see the rule below), not by arithmetic against the box. If anything a bigger box makes the contention story more legible: at the same date the committed federation stack measured 24 containers / 6.87 GiB with 7.1 GiB still available — i.e. ~45% of the machine already spent before a Gradle lane starts.

Serialising the lane (`--no-parallel`, exposed as the `gradle-args` input of `.github/workflows/_integration.yml`) runs one stack at a time and removes the contention.

**Rule — serialise on evidence, never on module count.**

- **Module count is a hypothesis, not evidence.** Lanes with many modules have run clean for months; lanes with few have starved. Serialising by count buys wall-clock and nothing else.
- **The evidence is the starvation signature in *that lane's own* CI history**: connections severed **while the tests are executing** — `Connection is not available`, `SQLTransientConnectionException`, `RedisConnectionFailureException`, `I/O error sending to the backend`. **Zero evidence is a result**: leave the lane alone, and record that you looked.
- **A job that died before running a line of code is not evidence.** A failure at `Set up job` or action resolution is an infrastructure outage — and an outage kills many jobs at once, so *several lanes failing in the same attempt* is more often an infrastructure signal than a contention one. Classify by **where** the job died, not by how many died.
- **The failure you are looking for may never have appeared as a red run.** A rerun-to-green hides it, and `gh run list` reports only the **latest attempt's** conclusion. Query the attempts (`.../runs/<id>/attempts/<n>/jobs`) — otherwise you measure zero and conclude, wrongly, that the lane is healthy.

**The mitigation is not free.** Serialising roughly doubles a lane's wall-clock, and a serialised lane has already crossed its job timeout and been CANCELLED. A CANCELLED job is not a test failure — but it is not green either, and nothing reports it as a failure. Raise `timeout-minutes` together with the flag, and re-check the margin whenever a module joins the lane.

**A fail-closed dependency changes what the symptom looks like.** When the severed dependency is one the service denies on (a token blacklist, a rate limiter), the outage does not surface as an infrastructure error — it surfaces as *rejection*, i.e. as a plausible security or authorisation defect. Assertions that **expect** a rejection still pass in that state, so an outage always looks like "only part of it broke". Before diagnosing such a failure, grep the failing test's own output window for the dependency's outage log.

Which lanes are serialised — and the evidence each one earned it with — is recorded at the caller in `.github/workflows/ci.yml`.

## A test that bypasses the enforcement layer proves nothing

When a rule is enforced by a **specific layer** (an HTTP filter, a decoder/validator, a DB constraint), a test
that reaches the behaviour *below* that layer cannot see whether the layer exists. It passes identically
whether the enforcement is wired or missing — so the suite reports green over an unenforced contract.

*Worked shape:* a service declared `Idempotency-Key` replay semantics and wired **no filter at all**. The
integration test called the application service directly (HTTP filter bypassed) with a fresh random key per
call, so *"same key twice, through the web layer"* — the only input that could have failed — was not
constructible. The defect shipped behind a green lane.

**Therefore, when writing or reviewing a test for an enforced property:**

- **Establish which layer enforces it, and route the test through that layer.** A test one layer too low is
  not a weaker test; it is a test of a different proposition.
- **The existence of supporting infrastructure is not evidence of enforcement.** A store bean, a column, a
  config class prove only that someone intended the mechanism. Find the code that *consumes* them — the
  filter registration, the `@PreAuthorize`, the `UNIQUE` constraint. A proxy indicator is not the property.
- **Confirm by mutation** (§ G3): break the wiring — the filter registration, the validator — and require the
  test to go RED. A test that stays green through that mutation was never covering the rule.

---

# Naming Conventions

| Test Type | Naming Pattern | Example (generic) |
|---|---|---|
| Unit (service) | `{ServiceName}Test` | `<ApplicationServiceClass>Test` |
| Unit (entity) | `{EntityName}Test` | `<DomainEntityClass>Test` |
| Unit (infrastructure) | `{ClassName}UnitTest` | `<InfrastructureClass>UnitTest` |
| Controller slice | `{ControllerName}SliceTest` | `<RestControllerClass>SliceTest` |
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

# CI Guards / Drift Detectors — Authoring Rules

A **guard** is a CI assertion that a property still holds (`scripts/check-*-drift.sh`, `infra/demo/verify-demo-wrapper.sh`, pinned-set unit tests, workflow gating). Guards are the CI-side counterpart of the pyramid above, and this repo has paid for every rule below with a real incident.

**These rules had no canonical home until `TASK-MONO-404`.** They were restated inline in ~14 places (`scripts/`, `.github/workflows/ci.yml`, `libs/*/build.gradle`, guard scripts). Those restatements are *correct and load-bearing* — keep writing them where the local specifics matter. But an author writing their **first** guard had no discoverable list, and the newest rule (G4) existed in exactly **one** file. That is the same "declared ↔ true" drift class the guards themselves exist to catch.

### G1 — A guard that cannot run is not a guard. Make the trigger follow the defect's *arrival path*.

Before writing the guard, ask: **what diff does this defect arrive as?**

- Arrives as our code/doc change → path trigger. **Never AND it with a `code-changed` filter** — a drift that arrives as a docs-only diff (`*.md`, `PROJECT.md`) then silently skips, **and a skipped job reports green**.
- Arrives with **no diff at all** (upstream image deleted, certificate expired, a date passing) → **no paths-filter can reach it.** Only a **time trigger** (cron/nightly) can.

*Incidents:* `TASK-MONO-359` (a guard against an externally-deleted image was gated on repo paths — the one defect class it could never see), `TASK-MONO-360` (measured: a markdown-only PR skipped 25 code jobs), `TASK-MONO-389` (the demo-wrapper guard's target arrives as markdown; the `code-changed` AND turned the guard off on exactly the change it policed).

### G2 — False positives cost as much as misses. A guard that is RED on day one gets switched off — and a switched-off job's skip reports green.

Prefer a narrower, exactly-enumerated predicate over a clever regex. Don't call a policy-permitted structure a violation. Allowlists are for **deliberate, justified** deviations — record *why* and *when it goes away* — and **mutate the allowlist too** (empty it; if nothing goes RED, the allowlist is decoration).

*Incidents:* `TASK-MONO-360` (a name-guessing predicate matched `allowSuperAdminWildcard` because it contains `all`), `TASK-MONO-368` (a false positive on iam — the guard was wrong, not the code: **widen the predicate, don't allowlist the innocent**).

### G3 — Prove it bites (mutation) — and prove the mutation *applied* before reading the result.

Fill all four cells: no-signal / no-signal-with-`--require-coverage` / healthy / defect-injected. A guard that only ever FAILs is indistinguishable from a correct one if you only look at the FAIL.

**Print the substitution count first and abort if it is not what you expected.** A silently-unapplied mutation reads exactly like *"the guard doesn't bite"* — this repo has been fooled by it **at least 7 times** (`perl` + CRLF, `perl` + Korean literals without `use utf8`, backtick escaping).

And **a fix that only removes false positives is indistinguishable from switching the guard off** — test the correction **symmetrically** (the false positive is gone ↔ the true positive still bites).

*Incidents:* `TASK-MONO-357`, `TASK-MONO-360`, `TASK-MONO-376`, `TASK-MONO-388`, `TASK-MONO-389`.

### G4 — 🔴 Prove it bites **on the runner it executes on**. A threshold calibrated on your host is a proposition about *your host*.

Local mutation proves *"the guard's logic works."* It does **not** prove *"the guard catches the defect."* Only the runner can prove that.

**Procedure (from `TASK-MONO-360`) — the measurement-only PR:** after the impl merges, open a PR that reverts **only the defect** and changes nothing else. Watch the job. **Close it unmerged.** One real run proves reachability *and* bite together.

*Incident — `TASK-MONO-397` got this wrong **twice in one ticket**:*

| predicate | laptop (WSL2), 512M | **ubuntu runner**, 512M |
|---|---|---|
| container RSS ≤ 75% | 83.2% → RED | **38.2% → PASSED** |
| in-container JVM `MaxHeapSize` | 128 MiB → RED | **3998 MiB → PASSED** |

The same container, limit and load account **2.2× differently** for RSS across hosts; and on the runner a JVM inside a 512 MiB cgroup **does not see the cgroup limit at all**. Both guards were decoration on CI, and both would have merged with a *"verified"* note. The third predicate — **compare a declared constant read from the compose file** — is not clever, and it works.

⇒ **If two host-dependent predicates fail, the environment cannot measure that axis. Stop being clever; compare a constant.** **A simple guard that works beats a clever guard that does not.**
⇒ **Host-dependent values (RSS, JVM heap, timings) may be *printed* as observations. They must not be *asserted*.**

⇒ **And one measurement is not a constant.** A value observed once is a sample, not the property — re-running
the identical job moves it. Before writing a measured number into a durable surface (a threshold, a ticket, a
comment, a projection), state how many times it was measured and over what range; a projection built on a
single anchor inherits that anchor's variance without showing it. *Incident: `TASK-MONO-438` predicted a lane
at ~5m01s from one prior sample and observed 5m42s — the anchor lane had itself moved +18% (370s → 436s) on
**identical code** between two runs.* Where a number must be quoted from one observation, mark it as such in
the code comment too, not only in the PR description — the PR is not where the next reader looks.

### G5 — Reachability is not only about CI triggers. A runtime `default` / fallback that nothing can reach is not a guard either.

Follow the value back to **whoever creates the condition**, and ask: *does an input that actually triggers this default exist in production?*

*Incident:* `TASK-MONO-389` — the demo's idle-stop had a perfect safe default (`_get(BEAT_PARAM, now)`) that **never once executed**, because terraform always creates the parameter with `value = "0"`. The unreachable default let the guard stop every warming instance five minutes after `apply`. The fix re-anchored on a fact nobody can forge (EC2 `LaunchTime`), not on a value someone might forget to write.

### G6 — Ask the question the *failure mode* asks, not the question that is easy to ask.

*Incident:* `TASK-MONO-397` — an under-provisioned broker container. The obvious guard is *"RestartCount == 0 after boot."* **It passes the defect.** Started in isolation the container survives its load, passes its healthcheck and reports `healthy`; it only dies once the rest of the fleet attaches as clients. The question was never *"did it die"* — it was ***"how much headroom is left."*** A component that cannot fail alone will not fail in the guard that starts it alone.

### G7 — Don't re-enumerate the source of truth. Derive from it, and execute both sides.

A guard that hardcodes the list it checks **drifts with the thing it guards**. Read the population from its owner (`projects.sh`, the rendered compose, the builder's method set).

*Incident:* `TASK-MONO-389` guard (t) does not list hostname forms — it runs `demo-boot.sh`'s derivation **and** the page's `demoHost()` **on the same IP** and compares. Either side changing is caught.

### G8 — Write down what the guard does **not** cover.

A known hole is a different thing from an unknown hole: the second is the path by which the next person believes *"CI has this covered."*

### G9 — Merging is half. Ask how the fix reaches the place it runs.

*Incident:* `TASK-MONO-397` fixed `docker-compose.yml`; compose is **baked into the demo AMI**, so the fix did not reach the live demo at all (`TASK-MONO-399` AC-6). **A green `main` is not evidence that the deployed thing is fixed.** Where a subsystem has more than one deployment layer, the layer boundary must be documented at the point of change (see `infra/demo/aws/README.md` § "코드를 고쳤다 — 그게 데모에 도달하는가?").

---

# Change Rule

Changes to test standards must be reflected here before applying to services.
Changes to guard-authoring rules (§ CI Guards) belong here too — a rule that lives only in one guard's comments is a rule the next guard's author will not find.
