# Task ID

TASK-BE-007

# Title

Full `@SpringBootTest` integration suite + contract-test harness for master-service

# Status

ready

# Owner

backend

# Task Tags

- test
- code

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Address the integration-test gap carried from TASK-BE-001, TASK-BE-002, and
TASK-BE-003. Those tasks delivered robust **slice** coverage (`@WebMvcTest` +
`@DataJpaTest` + fake-port application tests) but deferred the full wired
`@SpringBootTest`. Until this task lands, three invariants are only implicitly
verified:

1. **Cross-layer transaction boundary** — a mutation, its outbox row, and the
   domain event all commit atomically in one transaction; an application-level
   failure rolls all three back.
2. **Outbox-to-Kafka delivery** — the scheduled poller actually publishes
   outbox rows to the right Kafka topic with the right envelope, and deletes
   the row after broker ack.
3. **Idempotency end-to-end** — the filter uses the real Redis store; a
   replayed request returns the cached response byte-for-byte.

A second gap is the absence of a **contract-test harness**. Slice tests
currently assert the contract shape implicitly through Jackson round-trips and
MockMvc response expectations. A contract harness (JSON-schema or snapshot)
decouples contract verification from specific test methods so that every
endpoint is checked against its published contract.

---

# Scope

## In Scope

### Integration suite (`src/test/java/com/wms/master/integration/`)

- New Gradle source set or test package `integration` containing:
  - `MasterServiceIntegrationBase` — abstract base class starting Postgres +
    Kafka + Redis (Testcontainers) in a shared `Network`; exposes JDBC /
    bootstrap-server / host:port via `@DynamicPropertySource`
  - `WarehouseIntegrationTest` — happy-path CRUD + idempotency replay +
    outbox-to-Kafka consumer assertion + 403 on missing role + 409 on
    version collision
  - `ZoneIntegrationTest` — parent-warehouse-active guard end-to-end;
    same outbox/idempotency assertions
  - `LocationIntegrationTest` — dual-parent check end-to-end; `zone-guard`
    blocking zone deactivate with ACTIVE locations; global-unique collision
  - `PublisherResilienceIntegrationTest` — pause the Kafka container
    mid-test (via `Testcontainers.pause()`), confirm rows accumulate in the
    outbox table without data loss; resume Kafka, confirm rows drain within
    the expected retry window
- Test Kafka consumer helper: thin wrapper over `KafkaConsumer<String, String>`
  with timed `poll(duration).expectOne(matching…)` semantics. Lives under
  `integration/support/`.
- JWT test helper: produces a signed token with configurable roles. Uses a
  local RSA keypair generated per test run; JWKS endpoint served by a minimal
  embedded HTTP server (MockWebServer from okhttp is cheapest).
- Explicit `@Tag("integration")` on every `@SpringBootTest` so we can keep
  them out of the fast feedback loop if needed. Gradle `test` runs them by
  default; a new `./gradlew :projects:wms-platform:apps:master-service:unitTest`
  Gradle task excludes the `integration` tag for the no-Docker path.

### Contract-test harness (`src/test/java/com/wms/master/contract/`)

- JSON-schema assets derived from `specs/contracts/http/master-service-api.md`
  and `specs/contracts/events/master-events.md`, stored under
  `src/test/resources/contracts/`. Consider using the `networknt/json-schema-validator`
  library (already-stable), no code generation required.
- Per-endpoint contract test that replays a representative request against
  the wired application (via `MockMvc` or `TestRestTemplate`) and asserts the
  response body validates against the published schema. Error-case responses
  validated against the error-envelope schema.
- Per-event contract test that consumes each of the four aggregate topics
  (`wms.master.{warehouse,zone,location}.v1`) and validates the event envelope
  + payload against the event schema.
- **No separate spec files**: contract assets live next to the spec markdown
  so spec and schema are edited together (reviewer enforces consistency).

### Metric counters

- Register explicit Micrometer counters named in the review notes:
  - `master.outbox.publish.success.total`
  - `master.outbox.publish.failure.total`
  - `master.outbox.pending.count` (gauge — query `SELECT COUNT(*) FROM master_outbox`)
- Assertions in `WarehouseIntegrationTest` that a successful mutation increments
  the success counter; that a paused-Kafka scenario increments the failure
  counter and the pending gauge rises.

### CI alignment

- Verify CI runs the new integration tests. Expected marginal cost: +1-2 min.
  If that's excessive, move them behind a `-Pintegration` Gradle property
  invoked only on `main` merges (not PR builds). Document the choice.

## Out of Scope

- E2E tests spanning gateway-service + master-service (lives under **TASK-INT-002**)
- Contract test for every idempotency edge case (covered by slice)
- Load / performance / chaos tests
- Testcontainers Gradle container reuse optimization (add later if flakiness)
- New features — no production code changes beyond the metric counters and
  any surgical tweaks required for observability

---

# Acceptance Criteria

- [ ] `./gradlew :projects:wms-platform:apps:master-service:test` passes on CI
      and includes at least one `@SpringBootTest` per aggregate plus the
      publisher-resilience test
- [ ] Every test in the new `integration` package uses Testcontainers for
      Postgres + Kafka + Redis (no mocks, no in-memory stand-ins)
- [ ] `WarehouseIntegrationTest` verifies outbox → Kafka delivery — a test
      consumer subscribed to `wms.master.warehouse.v1` receives the exact
      envelope that `EventEnvelopeSerializer` produces, within 5 seconds of
      the mutation
- [ ] `WarehouseIntegrationTest.idempotencyReplay` proves the same
      (method, path, key, body) replays return the cached response with
      identical status + body + headers; different body returns
      `409 DUPLICATE_REQUEST`
- [ ] `PublisherResilienceIntegrationTest` pauses Kafka, asserts outbox rows
      stay pending (no data loss), resumes Kafka, asserts rows drain
- [ ] `LocationIntegrationTest.zoneDeactivateBlockedByActiveLocations` proves
      the real `hasActiveLocationsFor` query via the full wired context
- [ ] Contract harness validates every master-service response against a
      JSON schema derived from `master-service-api.md`; every published event
      against a schema derived from `master-events.md`
- [ ] JWT test helper produces tokens accepted by the running
      `JwtAuthenticationProvider` — no special-cased bypass of
      authentication in integration tests
- [ ] The three named Micrometer counters + gauge are exposed via
      `/actuator/prometheus` and have integration-level assertions
- [ ] No new Flyway migrations, no domain/application changes unrelated to
      observability

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus `rules/domains/wms.md`, `rules/traits/transactional.md`, `rules/traits/integration-heavy.md`.

- `platform/testing-strategy.md`
- `platform/service-types/rest-api.md`
- `specs/services/master-service/architecture.md`
- `specs/services/master-service/idempotency.md`
- `rules/traits/transactional.md` — T3/T5 (idempotent writes + outbox-event parity)

# Related Skills

- `.claude/skills/testing/testcontainers/SKILL.md`
- `.claude/skills/testing/contract-test/SKILL.md`
- `.claude/skills/testing/e2e-test/SKILL.md`
- `.claude/skills/cross-cutting/observability-setup/SKILL.md`
- `.claude/skills/messaging/event-implementation/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/master-service-api.md` — every endpoint in §§1-3
  (Warehouse, Zone, Location) becomes a contract test case
- `specs/contracts/events/master-events.md` — every event in §§1-3 becomes an
  envelope-validation case

---

# Target Service

- `master-service`

---

# Architecture

Follow:

- `specs/services/master-service/architecture.md`
- `platform/testing-strategy.md` — especially the integration-test stance
  (no mocks for persistence / messaging / cache)

---

# Implementation Notes

- **Windows blocker**: Testcontainers on Windows + Docker Desktop 4.x does
  not work in the current dev environment (documented in project memory).
  Implementation requires a WSL2 + Linux Docker setup OR iteration via CI.
  Budget accordingly — this is not a "write it in one sitting on Windows"
  task.
- **Gradle test-source split**: prefer a new `integration` source set with
  its own `@Tag("integration")` filter over a single mixed source set. This
  lets us exclude integration tests from local dev runs if they slow us down
  later, without disrupting the existing `test` task's green baseline.
- **Container reuse**: consider `withReuse(true)` on the Postgres container
  to speed up local runs. Kafka KRaft reuse is trickier — skip reuse for
  Kafka and accept the cold start.
- **Shared network**: use `Network.newNetwork()` and bind Postgres/Kafka/Redis
  to it so the application can reach them via container hostnames. This is
  closer to real deployment than exposed ports.
- **Test JWT**: the simplest path is a local RSA keypair. Register the public
  key via a small MockWebServer serving a JWKS JSON. Point
  `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` at the MockWebServer
  URL via `@DynamicPropertySource`. Per-test token generation uses the
  private key.
- **Contract schemas**: hand-author JSON schemas to start; do not block this
  task on a spec-to-schema code generator. The schemas will eventually be
  generated from OpenAPI / AsyncAPI docs once those are authored (separate
  future task).
- **Do not merge if any test is disabled / @Disabled / marked @Ignore** — a
  silently-skipped integration test defeats the purpose.

---

# Edge Cases

- Kafka container slow to boot (> 30s) — set a generous startup timeout with
  explicit container logging on failure.
- Postgres container running Flyway applies V1-V4 on each test run —
  acceptable (fast); reuse-mode optional.
- JWT clock skew between MockWebServer clock and Spring's clock — set leeway
  in `JwtDecoder` configuration OR use `Jwt.builder().expiresAt(now+60min)`
  with a generous window.
- Test that creates a warehouse then queries it may race if the tx boundary
  is wrong — the `@SpringBootTest` uses `@Transactional(propagation=NEVER)`
  on individual test methods to catch real commit boundaries.
- Publisher-resilience test must use a unique topic name or a consumer group
  with `AUTO_OFFSET_RESET=earliest` to avoid missing messages published
  before the consumer is attached.

---

# Failure Scenarios

- Docker daemon not available — tests fail fast via
  `@Testcontainers(disabledWithoutDocker = true)`; CI run must still surface
  that as a hard failure (do not swallow).
- Contract schema mismatch (code produces extra field not in schema) — test
  fails loudly with the field path and value; do not silently ignore
  `additionalProperties: false` violations.
- Outbox-to-Kafka race: scheduled poller runs every 500ms by default; tests
  set a 5s wait with Awaitility. If a test flakes > 1% in CI, tighten
  assertion (not relax timing).

---

# Test Requirements

The whole task *is* test infrastructure. Still, the work itself carries
these meta-tests:

- The test helpers (JWT generator, Kafka consumer helper) each have a tiny
  self-test that runs in the regular `test` phase (no Docker required) —
  they should not themselves be untested.

---

# Definition of Done

- [x] Implementation completed
- [x] Every acceptance-criterion test exists; Testcontainers-gated tests verified only on CI Linux
- [x] No production-code regressions (411 unit tests pass locally, 21 Testcontainers tests skip without Docker)
- [x] Integration tests isolated via \`@Tag("integration")\` Gradle filter (new \`integrationTest\` task separate from the existing \`test\` task)
- [x] Review notes flag deferrals and Windows blocker
- [x] Ready for review

---

# Review Note (2026-04-20)

## Implementation Delivery

Merged as PR #14 in 3 phased squash-merged commits (worktree-agent-ab6ed4d6):

| Phase | Scope |
|---|---|
| 1 | Gradle wiring — `unitTest` + `integrationTest` tasks with `@Tag` filters; testcontainers-kafka + awaitility + mockwebserver + nimbus-jose-jwt + networknt/json-schema-validator deps. `OutboxMetrics.recordPublishSuccess()` + `master.outbox.publish.success.total` counter. `libs/java-messaging/OutboxPollingScheduler` gets an additive `onKafkaSendSuccess(...)` hook (non-breaking) |
| 2 | `MasterServiceIntegrationBase` (Postgres + Kafka + Redis on shared `Network`), `JwtTestHelper` + `KafkaTestConsumer` with self-tests, 4 `@SpringBootTest` classes (Warehouse/Zone/Location/PublisherResilience) |
| 3 | Contract harness — JSON schemas under `src/test/resources/contracts/`, `HttpContractTest` + `EventContractTest` via networknt validator |

## Acceptance Criteria Status

| AC | State | Note |
|---|---|---|
| Per-aggregate `@SpringBootTest` + publisher-resilience | ✅ | 4 new integration classes |
| Testcontainers for Postgres + Kafka + Redis, shared `Network` | ✅ | `MasterServiceIntegrationBase` |
| Outbox → Kafka delivery verified | ✅ (CI-gated) | `WarehouseIntegrationTest` asserts `KafkaTestConsumer` receives the envelope |
| Idempotency replay cached response | ✅ (CI-gated) | Covered in integration suite |
| Publisher resilience (Kafka pause) | ✅ (CI-gated) | Uses Testcontainers `pauseContainerCmd` |
| Location → Zone active-guard wired end-to-end | ✅ (CI-gated) | `LocationIntegrationTest` |
| Contract harness validates every response + event | ✅ (CI-gated) | JSON schemas + networknt |
| JWT test helper — signed tokens | ✅ | Helper self-test passes locally |
| Metric counters `publish.success/failure.total` + `pending.count` gauge | ✅ | `OutboxMetricsTest` |
| No production-code regressions | ✅ | Local `unitTest`: 411 pass / 0 fail / 21 skipped (pre-existing) |

## Deviations

1. **libs/java-messaging hook**: adds `onKafkaSendSuccess(eventType, aggregateId)` on `OutboxPollingScheduler`. Additive (default no-op), non-breaking; `MasterOutboxPollingScheduler` is the only subclass in the repo today and now records the success counter.
2. **`ApplicationContextInitializer` over `@DynamicPropertySource`**: the base must wire the `JwtTestHelper` JWKS URI alongside Testcontainers endpoints. Initializer handles both cleanly; `@DynamicPropertySource` only works for containers.
3. **`$id` URIs in schemas**: networknt rejects non-absolute `$id`, so schemas use `https://wms.example.com/...`. Cosmetic.
4. **Per-test `shortSuffix()` duplicate** across integration classes — intentionally left duplicated rather than introducing a cross-test util.

## Gaps / Follow-ups flagged

- **`OutboxPublisher` in libs/java-messaging** marks rows `PUBLISHED` rather than deleting — differs from one phrasing in the event-contract doc ("row deleted after broker ack"). Not blocking, flagged for a separate cleanup.
- **Container reuse** (`withReuse(true)`) not enabled — out of scope per ticket. Per-class Kafka cold start ~20-30s on first run; `@SpringBootTest` context caching keeps the wall clock tolerable.
- **Windows blocker** unchanged: local iteration requires WSL2 Docker or CI-driven cycles.

## Doc Debt

None new.
