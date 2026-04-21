# Task ID

TASK-BE-017

# Title

Fix 5 flaky integration tests — master-service integrationTest task

# Status

ready

# Owner

backend

# Task Tags

- test
- flaky-fix

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

5 master-service integration tests (all `@Tag("integration")` extending
`MasterServiceIntegrationBase`) fail on CI Linux. Currently excluded from
default `test` task via `@Tag` filtering (PR #40) so CI badge is green,
but the tests themselves remain broken. This task fixes the flakiness so
`./gradlew integrationTest` passes reliably.

Failures observed on CI:

1. `WarehouseIntegrationTest.create_then_replay_then_event`
   AssertionFailedError at line 87 — `aggregateId` mismatch
2. `WarehouseIntegrationTest.MASTER_READ on a write endpoint returns 403`
   AssertionFailedError at line 111
3. `WarehouseIntegrationTest.prometheus actuator endpoint exposes the three outbox metrics`
   AssertionFailedError at line 159
4. `LocationIntegrationTest.createLocation_publishesEvent`
   AssertionFailedError at line 66 — `locationCode` mismatch in envelope
5. `PublisherResilienceIntegrationTest.Kafka pause → outbox accumulates, counter rises; resume → rows drain`
   AssertionError at line 73

The pattern across 3 of 5 is **Kafka consumer returning wrong / old
records**. Likely root causes to investigate:

- `KafkaTestConsumer` using a shared / default consumer group id across
  tests → receives events from earlier tests
- Consumer auto-offset behaviour consuming from beginning instead of latest
- Test isolation — topic not drained between tests

---

# Scope

## In Scope

- `projects/wms-platform/apps/master-service/src/test/java/com/wms/master/integration/support/KafkaTestConsumer.java` — audit consumer group id, auto offset reset, partition assignment
- `WarehouseIntegrationTest` (3 failing cases)
- `LocationIntegrationTest` (1 failing case)
- `PublisherResilienceIntegrationTest` (1 failing case)
- `MasterServiceIntegrationBase` — container lifecycle + per-test cleanup if needed

## Out of Scope

- The `gateway-master-e2e` suite (separate job, separate timeout issue)
- Unit / slice / H2 tests (all green)
- Changing the outbox publisher or the envelope schema
- New integration test scenarios

---

# Acceptance Criteria

- [ ] `./gradlew :projects:wms-platform:apps:master-service:integrationTest` passes locally (WSL2 + Docker) with all 3 test classes green
- [ ] CI `integrationTest` job passes (may require adding a separate job or modifying the existing `build-and-test` to run both tasks explicitly — coordinate with ci.yml)
- [ ] No flaky behaviour: 3 consecutive runs all green
- [ ] If test isolation required a `@BeforeEach` topic drain or unique consumer group per test, document why

---

# Related Specs

- `platform/testing-strategy.md`
- `specs/contracts/events/master-events.md` (envelope shape)

# Related Contracts

- None (internal test fix only)

---

# Target Service

- `master-service`

---

# Implementation Notes

- **Start by running the tests locally** in WSL2 (`./gradlew :projects:wms-platform:apps:master-service:integrationTest`). Observe actual failures with full stack traces — the CI log only shows `AssertionFailedError` without the specific values.
- **Most likely fix**: `KafkaTestConsumer` should use a **unique consumer group id per instance** (e.g., include `UUID.randomUUID()` in the group id) and set `auto.offset.reset=latest` so it only sees records arriving AFTER the consumer was constructed. Subscribing before the POST is the pattern the e2e suite already uses (per BE-INT-006 drain accumulation fix) — mirror it here.
- **For the Prometheus and MASTER_READ auth tests** — these may have different root causes (not Kafka-related). Read the assertion lines, investigate, fix.
- **CI integration** — after fixes, update `.github/workflows/ci.yml` to run `./gradlew :...:integrationTest` as part of the `build-and-test` job OR as a separate dependent job. Keep it on `kanggle/monorepo-lab` only (match the e2e pattern) since integration tests still involve Docker.

---

# Edge Cases

- A Kafka producer's first write after container start can take longer than expected — timeout should be ≥15s if not already
- Spring context caching across tests in the same JVM may leak state — `MasterServiceIntegrationBase` starts containers in a static block; this is already correct

---

# Failure Scenarios

- If one of the non-Kafka tests (Prometheus, auth) has a genuinely different bug from the others, triage it separately and potentially split into its own fix ticket

---

# Test Requirements

- Integration tests run 3× consecutively on WSL2 without failure
- No `@Disabled` or `@Tag("flaky")` applied — proper fix, not suppression

---

# Definition of Done

- [ ] 3 test classes green locally on WSL2
- [ ] CI integrationTest job wired (if not already) and green
- [ ] PR merged
- [ ] Ready for review
