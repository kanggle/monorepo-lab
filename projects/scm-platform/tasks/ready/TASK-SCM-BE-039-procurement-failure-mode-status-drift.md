# TASK-SCM-BE-039 — Reconcile procurement Failure Modes #6 and #9 with actual HTTP status behavior

**Status:** ready

**Type:** TASK-SCM-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (one doc correction + one behavioral ruling)

> Filed from the 2026-07-21 reconciliation audit round-2 re-measurement (origin/main `aa535c22b`). Two audit claims for this
> project were **REFUTED** and are NOT in scope: the cancel state machine (`CONFIRMED→CANCELED` is allowed and cross-referenced
> in `PoStatusMachine.java:41-52`) and demand-planning ADR-050 D9 identifiers (already reconciled by `V2__adr050_d9_codes.sql`).
> Only the two Failure-Mode rows below are real.

---

## Goal

`projects/scm-platform/specs/services/procurement-service/architecture.md` Failure-Mode table (§ around `:692`/`:695`) states
two outcomes that the current code does not produce. #9 is a clean doc fix; #6 is a genuine fork (the documented behavior is
unreachable) that needs a ruling before editing.

## Scope

**In scope:**

1. **Failure Mode #9 — status drift (doc fix).** Spec `architecture.md:695` says
   `409 PO_STATUS_TRANSITION_INVALID`. Code returns **422**: `GlobalExceptionHandler.java:69-78` (`handleStatusInvalid` →
   `UNPROCESSABLE_ENTITY`), reinforced by its own javadoc (`:46-48`) and `PoStatusTransitionInvalidException.java:10`
   ("Mapped to HTTP 422"). Three code artifacts agree on 422; the spec row is the stale outlier → change `409` to `422`.
2. **Failure Mode #6 — unreachable documented outcome (RULING required).** Spec `architecture.md:692` says supplier 4xx →
   `502 SUPPLIER_REJECTED`. But: `SUPPLIER_REJECTED` exists nowhere in `procurement-service` main source (grep = 0 code hits);
   there is **no** `@ExceptionHandler(HttpClientErrorException.class)` in `GlobalExceptionHandler` (`:57-267`); and the
   resilience4j path swallows it first — `RestSupplierAdapter.java:48-51,69-79` wraps the call in `@CircuitBreaker`
   (`fallbackMethod=submitFallback`) with no `ignoreExceptions`, so a propagated `HttpClientErrorException` becomes
   `SupplierUnavailableException` → **503 SUPPLIER_UNAVAILABLE** (`GlobalExceptionHandler.java:116-121`), not 502. So the
   documented 502/`SUPPLIER_REJECTED` is currently impossible.
   **This task must DECIDE, not assume** (see AC-2), between:
   - **Option A (doc → reality):** rewrite Failure Mode #6 to describe what actually ships (supplier 4xx currently surfaces as
     503 `SUPPLIER_UNAVAILABLE` via the circuit-breaker fallback). Pure doc change.
   - **Option B (code → doc):** treat the 502/`SUPPLIER_REJECTED` mapping as intended-but-missing behavior — add an explicit
     handler and configure the circuit breaker to `ignoreExceptions` `HttpClientErrorException` so a 4xx propagates to 502
     instead of being masked as 503. Code + test change (Opus-recommended if chosen).

**Out of scope:** cancel state machine; demand-planning data model (both REFUTED).

## Acceptance Criteria

- **AC-0 (gate — re-measure; code wins)** — Re-confirm #9 returns 422 and #6's 502 path is still unreachable at current `main`
  (the resilience4j fallback wiring is the load-bearing fact — verify `submitFallback` still lacks a 4xx passthrough).
- **AC-1** — Failure Mode #9 row reads `422 PO_STATUS_TRANSITION_INVALID`.
- **AC-2 (the substantive one)** — Explicitly choose Option A or B for #6, record the rationale in the PR body, and implement
  it end-to-end. If B, add a web-slice/handler test proving supplier 4xx → 502 `SUPPLIER_REJECTED` and that the breaker no longer
  masks it as 503; if A, the doc must name 503 `SUPPLIER_UNAVAILABLE` as the real outcome, not hand-wave.
- **AC-3** — No unrelated behavior change; the Failure-Mode table's other rows are left as-is unless AC-0 finds them stale too.

## Related Specs
- `projects/scm-platform/specs/services/procurement-service/architecture.md` § Failure Modes
- `platform/error-handling.md` (procurement error codes — verify `SUPPLIER_REJECTED`/`SUPPLIER_UNAVAILABLE` catalog entries match the chosen option)

## Related Contracts
- None (no request/response schema change unless Option B introduces a new error-code surface, which is already cataloged semantics).

## Edge Cases
- Option B must set `ignoreExceptions` on the **circuit breaker**, not only add a handler — otherwise the fallback still masks
  the 4xx before the handler ever sees it (the exact reason 502 is unreachable today).
- Do not "fix" #9 to 409 in code; three artifacts (handler + two javadocs) treat 422 as deliberate.

## Failure Scenarios
- **F1 — editing #6 to 502 in the doc without touching code (Option-B-by-halves).** Leaves the doc lying again; AC-2 forces a
  real implementation of whichever option is chosen.
- **F2 — reading the spec table's 409 as truth and changing the handler.** Guarded by AC-0 and the three-artifact 422 evidence.
