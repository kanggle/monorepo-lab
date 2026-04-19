# Task ID

TASK-INT-004

# Title

E2E scenario-2 guard + Kafka port correction — fix silent false-negatives in the gateway↔master e2e suite

# Status

ready

# Owner

integration

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

Fix three defects in the TASK-INT-002 e2e suite flagged by review that
make the test either silently pass or silently break production:

1. **Master-service `metrics` actuator endpoint not exposed.**
   `master-service/application.yml` includes only `health,info,prometheus`
   in the actuator exposure list. The e2e test's `masterRequestCount()`
   hits `/actuator/metrics/http.server.requests`, which returns 404
   because the `metrics` endpoint is closed. This means `masterRequestCount()`
   always returns 0 — **scenario 2 (unauthorized → master did not receive
   request) always passes regardless of actual master behavior**. Silent
   false-negative.
2. **Kafka bootstrap port mismatch in `E2EBase`.** The master-service
   container is wired with `KAFKA_ALIAS + ":9093"`, but Testcontainers'
   `KafkaContainer` advertises the listener for peer containers on 9092
   (KRaft mode). The master will fail to connect to Kafka silently
   during e2e startup, meaning outbox events never publish. Scenario 1
   (happy POST) still returns 201, but the outbox-to-Kafka verification
   the harness relies on for event contract tests does not fire.
3. **Counter check in scenario 2 is racy.** `masterRequestCount()` is
   read once, immediately after the 401, without an `await`. Micrometer
   counters increment asynchronously; a regression where master DID
   receive the request may surface with a tiny delay.

---

# Scope

## In Scope

- `master-service/src/main/resources/application.yml` OR a new
  integration-only profile (`application-integration.yml`) — expose the
  `metrics` actuator endpoint. Prefer profile-scoped to avoid exposing
  the metrics endpoint in production.
- `gateway-service/src/e2eTest/java/com/wms/gateway/e2e/E2EBase.java` —
  change the master-service env `SPRING_KAFKA_BOOTSTRAP_SERVERS` to
  use port `9092` (not 9093). Verify the KafkaContainer image and
  advertised port.
- `gateway-service/src/e2eTest/java/com/wms/gateway/e2e/GatewayMasterE2ETest.java`
  — wrap the post-401 counter read in Awaitility's `await().during(...)`
  to confirm counter stability (not just a one-shot read).
- Tighten the rate-limit assertion: `ok >= 190` (tolerating replenish
  jitter) and `rateLimited >= 40` instead of the current `>= 100` /
  `>= 1`.
- Fix the warehouseCode generator in HappyPath — switch from
  `new java.util.Random()` with a 3-digit range to `java.util.UUID`-derived
  short form (e.g., `WH{6-digit suffix of UUID}`), avoiding cross-run
  collisions.

## Out of Scope

- Rebuilding the e2e framework
- Adding new scenarios (the 5 existing scenarios are comprehensive)
- Changing the CI workflow structure

---

# Acceptance Criteria

- [ ] `master-service` actuator exposes the `metrics` endpoint when the
      integration profile is active (via either global config or
      profile override)
- [ ] `E2EBase` wires the master container with Kafka port `9092`, not
      `9093`; verified by observing master logs "Connected to Kafka"
      during e2e runs
- [ ] Scenario 2's `masterRequestCount()` post-401 check uses Awaitility
      (`await().during(Duration.ofSeconds(2))` or equivalent) to confirm
      counter stability
- [ ] Rate-limit assertion tightened to `ok >= 190, rateLimited >= 40`
- [ ] HappyPath warehouseCode uses UUID-derived generation
- [ ] New smoke assertion: the e2e suite verifies the outbox→Kafka path
      fires on at least one mutation (consumer receives the expected
      envelope). This is the acid test for the Kafka port fix.
- [ ] CI `e2e-tests` job still passes

---

# Related Specs

- `platform/observability.md` (metrics actuator conventions)
- `platform/testing-strategy.md`

# Related Contracts

- None

---

# Target Service

- `master-service` (config), `gateway-service` (e2e test)

---

# Implementation Notes

- **Profile-scoped metrics exposure**: create
  `projects/wms-platform/apps/master-service/src/main/resources/application-integration.yml`
  overriding only the actuator `exposure.include` list. Activate via
  `-Dspring.profiles.active=integration` in the Testcontainers-launched
  master container (E2EBase already injects env for the integration
  profile). Keep the production `application.yml` locked down.
- **Kafka port verification**: the `KafkaContainer` exposes bootstrap
  via `getBootstrapServers()` for host-side clients. For peer-container
  access, the listener port for the network alias is documented on
  Testcontainers Kafka module as `9092`. Cross-reference the official
  Testcontainers Kafka docs for the image in use (`apache/kafka:3.7.0`).
- **Awaitility wait**: prefer `.during(Duration.ofSeconds(2))` over
  `.atMost(...)` for stability checks — asserts the value does NOT
  change, which is what we want for "master did not receive".
- **UUID short form**: `"WH" + UUID.randomUUID().toString().replace("-","").substring(0,6).toUpperCase()`
  gives a 6-char suffix well beyond 3 digits' collision surface.

---

# Edge Cases

- Kafka port 9093 might still work in some Testcontainers versions —
  verify against the exact module version, not assumptions.
- Awaitility cross-scenario interference — if scenario 1 is still running
  async work when scenario 2 starts, the counter might change. Ensure
  the test method body waits for the prior scenario to fully complete.
  JUnit's sequential default on `@Nested` usually handles this.

---

# Failure Scenarios

- Metrics endpoint exposed in production config by accident — profile
  scoping prevents this; add a guardrail comment and, optionally, a
  smoke test asserting production `application.yml` does NOT include
  `metrics` in the default exposure list.
- Kafka port wrong but happened to work — remove the assumption by
  confirming the advertised listener explicitly via container logs.

---

# Test Requirements

- Modify existing scenario 2 to use Awaitility `during(...)`
- Add an outbox-to-Kafka smoke assertion to scenario 1 (HappyPath POST
  → consumer receives event on `wms.master.warehouse.v1`)
- Unit test not required — this is test-infrastructure-only

---

# Definition of Done

- [ ] Three defects fixed
- [ ] CI `e2e-tests` job still passes
- [ ] Scenario 2 now detects a real regression (verify by deliberately
      breaking SecurityConfig.java briefly — master receives request →
      test fails)
- [ ] Ready for review
