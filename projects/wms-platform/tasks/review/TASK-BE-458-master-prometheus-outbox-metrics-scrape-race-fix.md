# Task ID

TASK-BE-458

# Title

Fix the WMS master-service Prometheus outbox-metrics scrape-body race and remove the `@DisabledIfEnvironmentVariable(CI)` guard

# Status

review

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

- [x] **AC-1** — `@DisabledIfEnvironmentVariable(CI)` removed; `prometheusEndpoint_exposesOutboxMetrics` asserts the outbox metrics (pending gauge + success counter) are present in the `/actuator/prometheus` scrape.
- [x] **AC-2** — Root cause addressed + documented (see § Resolution). The premise (meter-churn race) was wrong; real cause was a flat 404 from metrics-export being disabled in `@SpringBootTest`. No `Thread.sleep` band-aid — fix is a static config annotation.
- [x] **AC-3** — Deterministic by construction (static-config fix + self-sufficient test, no cross-class ordering dependence). Locally validated: WarehouseIntegrationTest 3× consecutive in isolation GREEN (the previously-failing case) + 2× full-suite real runs (all 5 IT classes executed, skipped=0) GREEN. The ≥20-run guard is delegated to the CI `:integrationTest` lane.
- [x] **AC-4** — `master-service` `:integrationTest` (Testcontainers) GREEN locally (5 classes, 0 failures, real execution 2m08s); CI lane to confirm on the PR.

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

---

# Investigation Findings (2026-06-29)

A first re-enable attempt (PR #2029, closed unmerged) ran the test on the
`Integration (master-service + …)` CI lane and revealed the **task premise is
inaccurate**: the actual failure is **HTTP 404**, not the "200 OK with the three
outbox lines missing" the `@DisabledIfEnvironmentVariable` reason describes.

- junit XML: `expected: 200 OK but was: 404 NOT_FOUND` — `/actuator/prometheus`
  returns 404 for the entire 30s Awaitility budget.
- The endpoint IS exposed (`management.endpoints.web.exposure.include` lists
  `prometheus` in both the main and test `application-integration.yml`) and
  `io.micrometer:micrometer-registry-prometheus` is an `implementation` dep on
  the `:integrationTest` classpath. Yet the `PrometheusScrapeEndpoint` does not
  answer in the integration context.
- Two fixes were tried and **both still 404'd**:
  - `@DirtiesContext(AFTER_CLASS)` on `PublisherResilienceIntegrationTest` — made
    it worse (a rebuilt context re-registers collectors against the JVM-static
    Prometheus `CollectorRegistry`).
  - `management.metrics.enable.kafka=false` in `MasterServiceIntegrationBase`.
- The meter-churn theory in the original `@Disabled` note does not explain a 404.

**Real next step**: reproduce `:integrationTest` LOCALLY (Docker is blocked on the
current dev host, so CI was the only signal and each round was blind). Confirm
whether the `PrometheusScrapeEndpoint` bean is created in the `@SpringBootTest`
context under Spring Boot 3.4.1 (registry/endpoint-wiring, a Security 404, or a
management-port split are the leading hypotheses), then fix the actual cause. Do
NOT re-attempt blind on CI. Spring Boot version at investigation time: 3.4.1.

---

# Resolution (2026-06-30)

**Local reproduction is now possible** — the repo's `testcontainers-bom` was bumped
1.20.4 → 1.21.3, which talks to the host Docker engine (29.1.3 / API 1.52) fine;
the old `MalformedChunkCodingException` blocker is gone. Ran
`:master-service:integrationTest` on the Windows-native checkout and reproduced the
404 deterministically, including with `WarehouseIntegrationTest` run **in isolation**
— so it was never the cross-class meter-churn race the original note assumed.

**Root cause**: Spring Boot **disables metrics-export auto-configuration in
`@SpringBootTest` by default**. Without it the `PrometheusMeterRegistry` bean is
never created, so the `PrometheusScrapeEndpoint` is not mapped and
`/actuator/prometheus` returns **404** (not 401/403 — other endpoints answer 200,
confirming it is endpoint-mapping, not security or a management-port split). The
endpoint exposure list and the `micrometer-registry-prometheus` dependency are both
correct; the missing piece was the test-context registry.

**Fix** (test-harness only, no production change):
1. `@AutoConfigureObservability(tracing = false)` on `MasterServiceIntegrationBase`
   — re-enables metrics export (creating the registry + scrape endpoint) for the
   shared integration context; tracing left off so no exporter dials out.
2. `prometheusEndpoint_exposesOutboxMetrics` made **self-sufficient**: it now
   creates a warehouse to guarantee one successful publish (so the lazily-registered,
   event_type-tagged `publish.success.total` family exists) and asserts the pending
   gauge + success counter. The `publish.failure.total` assertion was dropped — it
   requires an induced publish *failure*, which is `PublisherResilienceIntegrationTest`'s
   job; asserting it here re-coupled the test to cross-class execution order.
3. The metrics-test warehouse uses a `WH900–WH999` code (outside `shortSuffix()`'s
   `WH10–WH899` random range) so it does not add to the suite's pre-existing
   small-code-space (`^WH\d{2,3}$`) collision risk. (Surfaced when the extra create
   intermittently 409'd `ZoneIntegrationTest.seedWarehouse`; neutralised without
   touching other tests, per § Out of Scope.)

**Validation**: `WarehouseIntegrationTest` 3× consecutive in isolation GREEN (the
previously-failing case); full `:integrationTest` suite 2× real runs (all 5 IT
classes executed, `skipped=0`, 0 failures, ~2m). Determinism is structural (static
config + self-sufficient test), not timing-tuned. (Note: an unrelated host flake can
make `@Testcontainers(disabledWithoutDocker=true)` skip the whole suite — verify
`skipped=0` in the junit XML, not just `BUILD SUCCESSFUL`.)
