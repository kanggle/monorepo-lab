# TASK-BE-516 — subscription SUSPEND returns `503 CIRCUIT_OPEN` in federation E2E (business 409 storm likely trips the account circuit)

- **Type**: TASK-BE
- **Status**: ready
- **Service**: admin-service (iam-platform), account-service downstream
- **Domain/traits**: saas / [transactional, integration-heavy, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (resilience config + failure-classification correctness)
- **⚠️ INVESTIGATION-FIRST**: AC-0 must reproduce and confirm the mechanism before any config change — the root cause below is a **grounded hypothesis**, not a verified fact.

## Goal

Fix the `subscription-plane-separation.spec.ts:183` federation-E2E failure, which reproduces
deterministically across two runs (post-merge nightly-dispatch + a clean re-run). This failure was
**newly revealed by TASK-BE-515** — before that fix, every real-tenant spec died at the OIDC login
fixture, so the subscription-mutation path was never exercised in nightly. It is **not** a BE-515
regression (BE-515 only added a scope to internal tokens; internal calls now succeed, returning
business `409`/`200`, not `401`) and **not** the finance user-token axis (TASK-PC-BE-013).

## AC-0 — Finding (observed, 2026-07-18; mechanism is a hypothesis to confirm)

- **Symptom**: the spec's `setStatus(...)` helper `PATCH /api/admin/subscriptions/{tenant}/{domain}/status`
  (admin-service) expects `200` but receives **`503`** with body
  `{"code":"CIRCUIT_OPEN","message":"Downstream circuit is open; try again shortly"}`.
  (`tests/federation-hardening-e2e/specs/subscription-plane-separation.spec.ts:133-155`).
- **Determinism**: failed in **both** federation runs (29622417063, 29623388121). `entitlement-trust:66`
  (finance forbidden = TASK-PC-BE-013) also deterministic; `tenant-switch-rescope:106` = flake (retry-passed).
- **CIRCUIT_OPEN source**: admin-service resilience4j `CallNotPermittedException` → 503
  (`AccountAdminUseCase.java:122-126`, `BulkLockAccountUseCase.java:141-143` show the same
  `CIRCUIT_OPEN` mapping). The circuit is on the admin→account client (`@CircuitBreaker(name = CB_NAME)`
  on `AccountServiceTenantClient` methods).
- **🔎 Leading hypothesis (verify, do not assume)**: `AccountServiceTenantClient` maps **any 4xx**
  (including the subscription-transition `409 SUBSCRIPTION_TRANSITION_INVALID`) to
  `NonRetryableDownstreamException` (`AccountServiceTenantClient.java:143-146`). If that exception is
  **not** in the circuit breaker's `ignore-exceptions`
  (`admin-service/src/main/resources/application.yml:201-268`), resilience4j records each business `409`
  as a **circuit failure**. The spec's own **defensive baseline + resume** logic issues repeated SUSPENDs
  on already-suspended subscriptions — the run log shows **11× `returned 409 (SUBSCRIPTION_TRANSITION_INVALID)`**
  for initech/umbrella finance — which, against a `failure-rate-threshold: 50`, would **open the circuit**,
  after which the real SUSPEND assertion gets `CIRCUIT_OPEN` 503. **A business 409 is a legitimate
  conflict, not a downstream fault — it should not count toward the failure rate.**

## Scope

- **In**: confirm the mechanism (AC-0), then — if confirmed — ensure business 4xx conflicts
  (`NonRetryableDownstreamException` / `409 SUBSCRIPTION_TRANSITION_INVALID`) are **excluded from the
  circuit-breaker failure count** (`ignore-exceptions` or a `recordFailurePredicate`) for the
  admin→account client(s). A test-only fix that merely stops the spec's baseline 409 storm is
  **insufficient** if the underlying mis-classification remains (any real 409 burst would trip the circuit
  in production).
- **Out**: BE-515 (internal scope — done); the finance user-token forbidden card (TASK-PC-BE-013);
  the `tenant-switch-rescope:106` flake (separate, retry-passing — triage only if it recurs).

## Acceptance Criteria

- **AC-1**: reproduce the `503 CIRCUIT_OPEN` and confirm (via metrics/log/test) whether the
  subscription-transition `409` is what opens the circuit. Record the verdict either way.
- **AC-2** *(if AC-1 confirms mis-classification)*: business 4xx conflicts do not count as circuit
  failures on the admin→account subscription path; verified by a test that fires N business 409s and
  asserts the circuit stays CLOSED (and a subsequent valid call returns 200, not 503).
- **AC-3**: mutation — reverting the fix (re-recording 409 as a failure) turns the AC-2 guard RED.
- **AC-4**: admin-service fast lane green.
- **AC-5** *(CI-authoritative, post-merge)*: `subscription-plane-separation:183` goes GREEN on the
  Federation Hardening E2E (dispatch or nightly). Recorded in the close-chore.

## Related Specs / Contracts

- `projects/iam-platform/apps/admin-service/src/main/resources/application.yml` (§ resilience4j `circuitbreaker` / `ignore-exceptions`)
- `projects/iam-platform/apps/admin-service/.../infrastructure/client/AccountServiceTenantClient.java`
- `tests/federation-hardening-e2e/specs/subscription-plane-separation.spec.ts` (the failing spec; ADR-MONO-023 D2 proof)
- Predecessor context: `projects/iam-platform/tasks/done/TASK-BE-515-iam-workload-tokens-omit-internal-invoke-scope.md` (revealed this failure)

## Edge Cases

- The spec's baseline/resume tolerates 409 on already-in-state rows (spec `tryResume`) — the fix must
  not change that business semantics, only whether the CB counts it as a fault.
- Genuine 5xx / connection failures MUST still open the circuit (do not over-broaden `ignore-exceptions`
  to swallow real downstream faults).
- Other admin→account paths that map 4xx to `NonRetryableDownstreamException` (bulk lock, GDPR) may share
  the same classification — check whether the fix should apply per-path or to the shared CB.

## Failure Scenarios

- "Fix" only silences the spec's baseline 409 storm → production 409 bursts still trip the circuit.
  Mitigated: AC-2 asserts the CB classification, not the spec's call pattern.
- Over-broad `ignore-exceptions` swallows real 5xx → circuit never opens on genuine outages. Mitigated:
  AC edge case + a retained 5xx-opens-circuit test.
