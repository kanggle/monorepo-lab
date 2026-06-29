# Task ID

TASK-BE-457

# Title

Re-enable the `@Disabled` gateway WireMock-fault test (`downstream_connectionFault_returns5xx`) — diagnose + fix the sporadic fault-stub race

# Status

review

# Owner

backend

# Task Tags

- code
- test

---

# Goal

`GatewayRateLimitIntegrationTest.downstream_connectionFault_returns5xx` is `@Disabled("TASK-MONO-044c-1 RC#3: sporadic WireMock fault stub race; tracked for nightly via TASK-MONO-044 § AC #8")`. TASK-MONO-044c-1 and the nightly infra (TASK-MONO-079) are done, but **TASK-MONO-044 § AC #8 — the promised "별도 task 발행" for this sporadic regression — was never filed** (discovery sweep 2026-06-29: no `ready/` task references it; the test stays disabled). This task files + closes that gap: diagnose the WireMock fault + Redis-restart race and re-enable the test deterministically.

After this task, `gateway-service` has no `@Disabled` integration test for the connection-fault → 5xx path; the gateway's downstream-fault behavior is gated by a green, non-flaky test.

# Scope

## In Scope

- Diagnose the sporadic race in `GatewayRateLimitIntegrationTest` (lines ~248–253): WireMock `Fault` stub (connection-reset) racing with the test-suite's Redis restart / context state.
- Make the fault stub deterministic (e.g. stub-ready barrier, isolate the fault scenario from the rate-limit Redis manipulation, or move to its own slice) and **remove the `@Disabled`**.

## Out of Scope

- Other tests in the suite (only the one disabled method).
- Gateway production behavior changes (the 5xx-on-fault behavior is correct; only the test is flaky).

---

# Acceptance Criteria

- [ ] **AC-1** — `@Disabled` removed from `downstream_connectionFault_returns5xx`; the test asserts a 5xx (502/503) on a downstream connection fault.
- [ ] **AC-2** — Root cause of the stub race identified and addressed in the test harness (documented in the task close note); no `Thread.sleep`-only band-aid.
- [ ] **AC-3** — The test passes deterministically: ≥20 consecutive local/CI runs green (or a CI loop), no flake.
- [ ] **AC-4** — `gateway-service` `:integrationTest` (Testcontainers) GREEN on CI.

---

# Related Specs

- `projects/iam-platform/specs/services/gateway-service/architecture.md` (downstream-fault / 5xx behavior)
- `platform/testing-strategy.md` (no-flaky-quarantine posture)

# Related Contracts

- 없음 (test-harness only).

---

# Target Service

- `gateway-service` (iam-platform)

---

# Edge Cases

- WireMock `Fault.CONNECTION_RESET_BY_PEER` vs `EMPTY_RESPONSE` may surface as different gateway status codes — assert the documented mapping, not a single hardcoded code.
- The race involves shared Redis state from the rate-limit tests in the same class — ensure ordering/isolation so the fault test does not inherit a throttled state.

# Failure Scenarios

- Re-enabling without fixing the root cause → nightly/CI flake returns. AC-3 (repeat-run) guards this.

---

# Test Requirements

- The re-enabled `downstream_connectionFault_returns5xx` itself; deterministic ≥20-run validation.

---

# Definition of Done

- [ ] AC-1…AC-4 satisfied
- [ ] Root-cause note recorded in the close summary
- [ ] Ready for review
