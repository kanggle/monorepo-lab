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

- [x] **AC-1** — `@Disabled` removed; the test asserts a 5xx on a downstream connection failure.
- [x] **AC-2** — Root cause identified — but it was NOT a test-harness stub race (premise wrong). It was a production bug in `JwtAuthenticationFilter` masking downstream failures as `200`; fixed under TASK-BE-460. No band-aid.
- [x] **AC-3** — Deterministic by construction (route to a dead `127.0.0.1:1`, not a WireMock fault race); green in isolation + full suite locally.
- [x] **AC-4** — `gateway-service` `:integrationTest` GREEN locally (4 classes, 0 fail); CI to confirm on the BE-460 PR.

---

# Resolution (2026-06-30) — superseded by TASK-BE-460

The premise (a sporadic WireMock `Fault.CONNECTION_RESET_BY_PEER` stub race) was
**wrong**. Local `:integrationTest` reproduction (TASK-BE-457 investigation, now
possible after the repo's testcontainers-bom 1.20.4→1.21.3 bump) showed the gateway
returned an **empty `200`** on a deterministic downstream failure — a real production
bug in `JwtAuthenticationFilter` (its Redis fail-open `onErrorResume` swallowed the
downstream error and re-invoked the chain). That is out of this task's test-only scope
(§ Out of Scope: "Gateway production behavior changes"), so the fix was filed and
implemented as **TASK-BE-460**. This task's deliverable — re-enabling
`downstream_connectionFault_returns5xx` deterministically (5xx) — lands in the BE-460
PR. Closed as delivered-by-BE-460.

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

---

# Investigation Findings (2026-06-29)

A first re-enable attempt (PR #2030, closed unmerged) ran the test on the
`Integration (iam, Testcontainers)` CI lane. Both deterministic-fault approaches
returned **HTTP 200**, not 5xx:

- **accept-then-RST TCP server**: resets mid-flight, after Spring Cloud Gateway
  has already committed a 200; `GatewayErrorConfig` will not override an
  already-committed status (its `isCommitted()` guard) — this is exactly the
  ~5-10% "200 OK leak" path the original note describes, hit deterministically.
- **refused connection (unbound loopback port)**: ALSO returned 200. Gateway logs
  reveal the lane has **Redis testcontainer instability** (`Connection refused` on
  the Lettuce client and the access-invalidation check), so
  `JwtAuthenticationFilter` fail-opens and the fault request is logged as
  `GET /api/fault 200`. junit XML: `expected:<SERVER_ERROR> but was:<SUCCESSFUL>`.

So the 5xx mapping is entangled with **Redis-down fail-open + response-commit
timing**, not the WireMock fault-stub race the original note assumes.
`GatewayErrorConfig` DOES map `ConnectException → 503`, but only when the response
is not already committed — establishing that ordering deterministically is the
real problem.

**Real next step**: reproduce `:integrationTest` LOCALLY (Docker blocked on the
current dev host). First stabilise the lane's Redis (the reconnect failures may be
an independent infra flake) so the fault behaviour is observable in isolation,
then decide whether the gateway returning 200 on a downstream connection failure
is a test-harness gap or a real `GatewayErrorConfig`/commit-ordering bug worth its
own fix task. Do NOT re-attempt blind on CI. Spring Boot version at investigation
time: 3.4.1.
