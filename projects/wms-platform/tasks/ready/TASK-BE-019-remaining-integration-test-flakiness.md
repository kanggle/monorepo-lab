# Task ID

TASK-BE-019

# Title

Fix 3 remaining flaky integration tests (BE-017 partial — 2/5 fixed)

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

TASK-BE-017 fixed 2 of 5 flaky integration tests on CI Linux:

- ✅ `LocationIntegrationTest.createLocation_publishesEvent` (Kafka consumer offset-reset fix)
- ✅ `WarehouseIntegrationTest.MASTER_READ on a write endpoint returns 403` (AccessDeniedException handler)

3 remain failing on the new `integration-tests` CI job in
`kanggle/monorepo-lab` (portfolio repo unaffected — its CI excludes
the job per the monorepo-only `if:` condition):

1. `PublisherResilienceIntegrationTest.Kafka pause → outbox accumulates, counter rises; resume → rows drain`
2. `WarehouseIntegrationTest.prometheus actuator endpoint exposes the three outbox metrics`
3. `WarehouseIntegrationTest.create persists, writes outbox row, publishes envelope, idempotency replay returns cached response`

---

# Scope

## In Scope

Diagnose and fix each remaining test. Likely areas to investigate:

- **PublisherResilienceIntegrationTest**: Kafka `KafkaContainer.pause()` / `unpause()` behaviour on GitHub runner shared infra. May need longer wait after resume + more tolerant assertions (e.g., `Awaitility.await().pollInterval(...).atMost(Duration.ofSeconds(30))`).
- **prometheus actuator metrics**: `OutboxMetrics` TransactionTemplate fix landed in BE-017, but the CI assertion at line 159 still fails. Read the exact assertion — it may be checking for metrics that aren't registered until after first scrape, or checking gauge VALUES not just presence.
- **create_then_replay_then_event**: The Kafka consumer offset-reset fix should have solved this, but CI reports `AssertionFailedError at line 87` (aggregateId mismatch). Need to run locally in WSL2 to see the actual expected-vs-actual values.

## Out of Scope

- Touching the tests that BE-017 already fixed (LocationIntegrationTest, MASTER_READ)
- Changes to the outbox publisher production code (other than metrics if genuinely broken)
- Gating changes to the `integration-tests` CI job itself (keep it gated to monorepo-lab only per BE-017)

---

# Acceptance Criteria

- [ ] All 3 tests pass on CI Linux `integration-tests` job in 3 consecutive runs
- [ ] `./gradlew :projects:wms-platform:apps:master-service:integrationTest` passes locally in WSL2
- [ ] No new `@Disabled` or `@Tag("flaky")` — real fixes
- [ ] If the prometheus test's actual expected-vs-actual reveals a production bug, file a separate fix ticket; do NOT silently amend the assertion

---

# Related Specs

- `platform/testing-strategy.md`
- `platform/observability.md` (for Prometheus gauge expectations)

# Related Contracts

- None

---

# Target Service

- `master-service`

---

# Implementation Notes

- **Start by running locally in WSL2** to see the full assertion messages. GitHub Actions log only shows `AssertionFailedError at line N` — the actual expected-vs-actual values (which would tell us the real story) are in the test report HTML, uploaded as a CI artifact on failure.
- **For PublisherResilienceIntegrationTest**: adding `Awaitility.with().pollInterval(500ms).await().atMost(30s).untilAsserted(...)` around the drain assertion is safer than a fixed wait. Account for Kafka broker warm-up post-unpause.
- **For prometheus actuator**: if the test queries `/actuator/prometheus` and parses for specific metric names, the `OutboxMetrics` gauge names must register at context-startup. Check that `MeterRegistry` is injected correctly and the gauge references are held (weak references can be GC'd).
- **For create_then_replay_then_event**: Load the local test report, identify whether `aggregateId` is `null`, wrong UUID, or the "old one" pattern suggesting the consumer STILL gets a stale record despite offset=latest. If stale: check consumer.poll() semantics — maybe need to call `assign()` explicitly instead of `subscribe()`.

---

# Edge Cases

- GitHub runner vs WSL2 timing differences — WSL2 has more CPU headroom; tests may pass locally but fail on CI. In that case, widen Awaitility timeouts rather than tighten test logic.
- Multiple tests in the same suite share the MasterServiceIntegrationBase context cache — order dependencies can mask the root cause. Try running each failing test in isolation.

---

# Failure Scenarios

- If after 2-3 iterations the tests remain flaky, consider: (a) splitting `PublisherResilienceIntegrationTest` into its own CI job with retry logic, (b) marking it `@Tag("slow-integration")` and running on a scheduled workflow rather than every push.

---

# Test Requirements

- 3 consecutive CI runs must be green
- No skipping/suppression — genuine fixes

---

# Definition of Done

- [ ] 3 tests green on CI
- [ ] Test report HTML inspected locally for each to confirm root cause
- [ ] PR merged
- [ ] Ready for review
