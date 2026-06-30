# Task ID

TASK-BE-460

# Title

Fix the iam gateway `JwtAuthenticationFilter` masking downstream failures as `200` (fail-open `onErrorResume` swallows the routing error) — so downstream connection failures map to `5xx`

# Status

review

# Owner

backend

# Task Tags

- code
- bug
- security

---

# Goal

`JwtAuthenticationFilter` wraps the downstream `chain.filter(...)` call inside the
same reactive error operators it uses for the Redis access-invalidation check and
for JWT-verification errors. As a result, **any downstream failure on an
authenticated route is swallowed by the filter and the client receives an empty
`200`** instead of a `5xx`:

- The Redis access-invalidation `.get(...).flatMap(v -> chain.filter(...))` is
  followed by `.onErrorResume(e -> { log "Redis unavailable"; return chain.filter(...); })`.
  When the downstream forward (inside the `flatMap`) errors (connection refused /
  reset / DNS failure), that error propagates to this `onErrorResume`, which
  **mis-classifies it as a Redis outage, logs the misleading "Redis unavailable"
  message, and re-invokes `chain.filter(...)`** on an already-routed exchange →
  empty `200`.
- `GatewayErrorConfig` (the `@Order(-1)` `ErrorWebExceptionHandler` that maps
  `ConnectException → 503`) is therefore **never invoked** for these failures.

Discovered while diagnosing TASK-BE-457 (the `@Disabled`
`downstream_connectionFault_returns5xx` test). Local `:integrationTest`
reproduction (clean Redis testcontainer) showed a deterministic empty `200` for
both a WireMock `CONNECTION_RESET_BY_PEER` fault and a deterministic
connection-refused route (`http://localhost:1`), with `[DIAG] handler invoked`
never logged from `GatewayErrorConfig`. The original BE-457 premise (a WireMock
fault-stub race) was wrong; this is a real production resilience + correctness bug
in the gateway auth filter, which is out of BE-457's test-only scope — hence this
task.

After this task, a downstream connection failure on an authenticated route returns
a `5xx` (503) via `GatewayErrorConfig`, and the previously-`@Disabled` BE-457 test
is re-enabled to gate the behaviour deterministically.

# Scope

## In Scope

- Restructure `JwtAuthenticationFilter.filter(...)` so the **authentication /
  authorization pipeline and its error handling cover only auth**, and the
  downstream `chain.filter(...)` is invoked afterwards so its errors propagate to
  `GatewayErrorConfig` (→ `5xx`).
- Scope the Redis fail-open `onErrorResume` to **only** the Redis
  `opsForValue().get(...)` call (treat a Redis outage as "no invalidation record",
  i.e. fail-open / proceed) — not the downstream forward.
- Re-enable the BE-457 test (`downstream_connectionFault_returns5xx`) with a
  **deterministic** downstream failure (a route to a dead port → connection
  refused) asserting `5xx`. Remove the `@Disabled`.

## Out of Scope

- `GatewayErrorConfig` mapping itself (it is correct — it just was never reached).
- Rate-limit / JWKS / other filters.
- Other gateway production behaviour.

---

# Acceptance Criteria

- [x] **AC-1** — Downstream **connection failure** on an authenticated route returns
  `5xx` (503 `SERVICE_UNAVAILABLE`) via `GatewayErrorConfig`, not an empty `200`.
  Verified locally: `GET /api/dead/x → 503` (was empty 200).
- [x] **AC-2** — Fail-open behaviour preserved: the Redis `onErrorResume` is scoped to
  the `opsForValue().get(...)` only (returns `""` ⇒ proceed).
  `GatewayResilienceIntegrationTest.forceInvalidatedKey_absent_failsOpen` (200) and
  force-invalidation `401` paths stay green.
- [x] **AC-3** — All existing gateway auth behaviour preserved: full
  `JwtAuthenticationFilterUnitTest` + `GatewayIntegrationTest` (18) +
  `GatewayResilienceIntegrationTest` (6) + `GatewayTenantPropagationIntegrationTest`
  (6) GREEN locally; `skipped=0`.
- [x] **AC-4** — BE-457's `downstream_connectionFault_returns5xx` re-enabled
  (`@Disabled` removed), deterministic (route to dead `127.0.0.1:1`, no WireMock fault
  race), green in isolation and in the full suite.
- [x] **AC-5** — `gateway-service` `:integrationTest` GREEN locally (4 classes, 0
  failures, real execution); CI `Integration (iam, Testcontainers)` lane to confirm on
  the PR.

---

# Resolution (2026-06-30)

**Root cause** (reproduced locally — TC 1.21.3 unblocked local `:integrationTest`):
`JwtAuthenticationFilter` returned the downstream `chain.filter(...)` from inside the
Redis access-invalidation pipeline, and wrapped that pipeline in
`.onErrorResume(e -> { log "Redis unavailable"; return chain.filter(...); })`. A
downstream failure propagated into that handler, was mis-classified as a Redis outage,
and the chain was **re-invoked on an already-routed exchange → empty 200**. The outer
`.onErrorResume(Exception.class, …)` (intended for JWT errors) likewise wrapped the
forward, so even with the inner handler scoped it would have mapped a downstream error
to `401`. `GatewayErrorConfig` (`ConnectException → 503`) was never reached.

**Fix** (`JwtAuthenticationFilter`, no contract change): split the filter into (1) an
auth pipeline — `authorizeRequest(...)` — that emits the enriched `ServerHttpRequest`
or writes a `401/403` and completes empty, with its error handling covering ONLY auth;
and (2) the downstream `chain.filter(...)` invoked AFTER that pipeline, so downstream
failures propagate to `GatewayErrorConfig` (→ `5xx`). The Redis fail-open
`onErrorResume` is scoped to the `get(...)` call (Redis outage ⇒ treat as no
invalidation record ⇒ proceed).

**Verification**: unit GREEN; `:integrationTest` 4 classes / 34 tests / `skipped=0` / 0
failures, incl. the re-enabled connection-fault test (`/api/dead/x → 503`). Closes
TASK-BE-457 (its deliverable — the re-enabled test — lands here).

---

# Related Specs

- `projects/iam-platform/specs/services/gateway-service/architecture.md` (downstream-fault / 5xx behavior)
- `platform/testing-strategy.md` (no-flaky-quarantine posture)

# Related Contracts

- 없음 (gateway error-mapping behaviour; no API/event contract change).

---

# Target Service

- `gateway-service` (iam-platform)

---

# Edge Cases

- Redis outage must remain fail-open (proceed), but a downstream outage must NOT be
  treated as fail-open — the fix must distinguish the two error sources.
- Denial paths (`401`/`403`) write the response and must NOT then forward downstream.
- The forwarded (enriched) request headers (`X-Account-ID`, `X-Tenant-Id`,
  `X-Device-Id`) must still be injected on the success path.

# Failure Scenarios

- If the auth error handlers still wrap `chain.filter(...)`, a downstream failure is
  mis-mapped (200 or 401). AC-1 + AC-4 guard this.

---

# Test Requirements

- Re-enabled `downstream_connectionFault_returns5xx` (deterministic connection-refused).
- Existing unit + integration suites unchanged and green.

---

# Definition of Done

- [ ] AC-1…AC-5 satisfied
- [ ] Root-cause note in close summary
- [ ] BE-457 closed (its deliverable — the re-enabled test — lands here)
- [ ] Ready for review
