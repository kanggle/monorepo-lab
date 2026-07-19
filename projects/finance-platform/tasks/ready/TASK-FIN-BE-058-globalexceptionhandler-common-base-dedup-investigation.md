# TASK-FIN-BE-058 — investigate: finance GlobalExceptionHandler → CommonGlobalExceptionHandler dedup (DESIGN DECISION REQUIRED)

- **Type**: TASK-FIN-BE (INVESTIGATION-first — production dedup gated on a contract design decision)
- **Status**: ready
- **Service**: account-service + ledger-service (finance-platform) — and possibly `libs/java-web-servlet`
- **Domain/traits**: saas / [transactional, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: TBD after AC-0 (likely Opus — cross-cutting production + shared-lib)
- **⚠️ INVESTIGATION-FIRST / DO NOT IMPLEMENT YET**: this is NOT a mechanical dedup. It touches
  **production code** and is blocked on a contract design decision (below). AC-0 must pin the decision
  BEFORE any impl. Surfaced (not invented) by the 2026-07-19 finance test audit.

## The observed smell (from the audit — a real, verified finding)

`GlobalExceptionHandlerNotFoundTest` is **byte-identical** between account-service and ledger-service
(differs only in `package` + the `ApiErrorBody` import). It tests 4 generic framework exceptions
(`NoResourceFoundException`, `NoHandlerFoundException`, `HttpRequestMethodNotSupportedException`,
`HttpMediaTypeNotSupportedException`). Root cause: the 4 handler methods under test — in each service's
own `GlobalExceptionHandler` (`presentation/advice/`) — are **hand-copied boilerplate** that duplicates
`libs/java-web-servlet/.../CommonGlobalExceptionHandler` (which already implements identical
404/405/415/500 handling). The repo convention already exists: `iam-platform`'s account-service
`GlobalExceptionHandler extends CommonGlobalExceptionHandler`. Finance's two services do NOT — they
hand-duplicate it. So the **test** duplication mirrors a **production** duplication.

This is the one audit finding where "code shared in libs, test duplication is a real smell"
(`platform/shared-library-policy.md`) genuinely applies — unlike the Money / GlobalExceptionHandler
(domain-error) / FinanceTenantGatePolicy / ActorContext pairs, which are intentional per-service
duplication and were correctly NOT deduped.

## Why this is blocked (the design decision — AC-0)

A direct `extends CommonGlobalExceptionHandler` swap is **NOT drop-in behaviour-preserving**: finance's
`ApiErrorBody` (`dto/ApiErrorBody.java` — has a `details` field + an `Instant` timestamp) is
structurally different from the shared `ErrorResponse` (`code, message, String timestamp`, no
`details`). So adopting the shared handler either:
- (Option A) changes finance's error-response contract (drops `details`, `Instant`→`String` timestamp) —
  a **contract change** requiring `account-api.md` / `ledger-api.md` + `platform/error-handling.md`
  reconciliation; OR
- (Option B) makes `CommonGlobalExceptionHandler` generic over the body type (a **shared-lib change**
  affecting every consumer — iam/ecommerce/wms/… — so it must not regress their contracts); OR
- (Option C) leave production as-is and accept the duplication (close as WONTFIX with the rationale
  recorded).

## Acceptance Criteria

- **AC-0 (gate)**: pin the decision among Option A / B / C with the contract + shared-lib blast radius
  written out. This needs a human/ADR call — do NOT pick it unilaterally. INVESTIGATION-FIRST: verify
  the current `ApiErrorBody` vs `ErrorResponse` shapes and every `CommonGlobalExceptionHandler`
  consumer BEFORE proposing, not from this ticket's summary (which is a hypothesis — recount).
- **AC-1 (only if A or B chosen)**: finance account+ledger `GlobalExceptionHandler` stops hand-duplicating
  the 404/405/415/500 methods; the byte-identical `GlobalExceptionHandlerNotFoundTest` pair collapses
  (inherited/shared). Contract docs reconciled. CI green incl. finance Testcontainers lane.
- **AC-2 (if C chosen)**: record the WONTFIX rationale in this file + the finance INDEX, and close.

## Related

- 2026-07-19 finance test audit (the source of this finding).
- `libs/java-web-servlet` `CommonGlobalExceptionHandler`; `iam-platform` account-service
  `GlobalExceptionHandler extends CommonGlobalExceptionHandler` (the established pattern).
- `platform/shared-library-policy.md`, `platform/error-handling.md`.
- Memory: `feedback_workaround_becomes_the_contract` (a hand-copy of shared-lib boilerplate is the
  "workaround becomes the contract" shape — but here the blocker is a genuine body-shape divergence, so
  a design call, not a reflexive extends), `project_untickected_backlog_candidates_2026_06_19`
  (REAL-GAP tickets must re-verify implementation state before starting).
