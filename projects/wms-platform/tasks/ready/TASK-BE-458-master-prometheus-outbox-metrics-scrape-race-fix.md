# Task ID

TASK-BE-458

# Title

Fix the WMS master-service Prometheus outbox-metrics scrape-body race and remove the `@DisabledIfEnvironmentVariable(CI)` guard

# Status

ready

# Owner

backend

# Task Tags

- code
- test

---

# Goal

`WarehouseIntegrationTest.prometheusEndpoint_exposesOutboxMetrics` is `@DisabledIfEnvironmentVariable(named="CI", matches="true")`. TASK-BE-020 (done) identified the real cause — the `/actuator/prometheus` scrape-body composition races with **micrometer-kafka re-attaching its client meters to the restarted broker** after a Kafka pause/unpause earlier in the same suite — and explicitly "deferred to a follow-up." The 2026-06-29 discovery sweep found **no such follow-up ticket exists**. This task files + closes it: fix the scrape-body race so the test runs green on CI and the `@DisabledIfEnvironmentVariable` guard is removed.

# Scope

## In Scope

- Diagnose the micrometer-kafka meter re-attach vs scrape-body race in `master-service` (`WarehouseIntegrationTest` ~166–186).
- Fix via test-suite ordering / `CompositeMeterRegistry` isolation / dedicated suite for the Kafka pause-unpause scenario / scrape-stability barrier — so the outbox-metrics scrape is deterministic on CI.
- Remove `@DisabledIfEnvironmentVariable(named="CI", matches="true")` from the test.

## Out of Scope

- Production metrics wiring (the `*.outbox.pending.count` gauge + outbox metrics are correct; only the CI scrape timing is flaky).
- Other tests in the suite.

---

# Acceptance Criteria

- [ ] **AC-1** — `@DisabledIfEnvironmentVariable(CI)` removed; `prometheusEndpoint_exposesOutboxMetrics` asserts the outbox metrics are present in the `/actuator/prometheus` scrape.
- [ ] **AC-2** — Root cause (micrometer-kafka meter re-attach after broker restart) addressed in the harness, documented in the close note.
- [ ] **AC-3** — Test passes deterministically on CI (≥20 runs / CI loop green, no flake), including when it runs after the Kafka pause/unpause scenario in the same suite.
- [ ] **AC-4** — `master-service` `:integrationTest` (Testcontainers) GREEN on CI (the `Integration (master-service + …)` lane).

---

# Related Specs

- `projects/wms-platform/specs/services/master-service/architecture.md` (outbox + actuator/prometheus)
- `platform/testing-strategy.md` (no-flaky-quarantine posture)

# Related Contracts

- 없음 (observability/test-harness only).

---

# Target Service

- `master-service` (wms-platform)

---

# Edge Cases

- The race only manifests when the Kafka-pause scenario runs earlier in the same JVM/suite — the fix must hold under that ordering, not just in isolation.
- micrometer-kafka may register meters lazily on reconnect — assert on the stable outbox gauges, not transient kafka client meters.

# Failure Scenarios

- Removing the guard without fixing the re-attach race → CI flake returns on the master-service lane. AC-3 guards this.

---

# Test Requirements

- The re-enabled `prometheusEndpoint_exposesOutboxMetrics`; deterministic repeat-run validation under the full suite ordering.

---

# Definition of Done

- [ ] AC-1…AC-4 satisfied
- [ ] Root-cause note in close summary
- [ ] Ready for review
