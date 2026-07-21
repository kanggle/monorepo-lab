# TASK-FIN-BE-061 — Ledger-service dedup: actor-identity, currency-parse, upsert-audit (behavior-preserving)

**Status:** ready

**Type:** TASK-FIN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (mechanical extraction, behavior-preserving)

> Filed from the 2026-07-21 reconciliation-audit refactoring rescan of finance-platform (candidates F1 + F2 + F3, all
> intra-`ledger-service`). Grep-verified. Behavior-preserving only — no contract change.

---

## Goal

`ledger-service` has three grep-verified duplication clusters (rule of 3 met). Extract each, with **zero behavior change**.

## Scope

**In scope (ledger-service):**

1. **F1 — `actorIdentity()` helper, 5× byte-identical** across controllers (`PeriodController`, `JournalController`,
   `RevaluationController`, `ReconciliationController`, `SettlementController`): `actor.subject() != null ? actor.subject() :
   actor.tenantId()`. Move onto the `ActorContext` record itself as `ActorContext.identity()` and delete the 5 copies.
2. **F2 — currency-parse-to-VALIDATION_ERROR, 3×** (`GetFxRateOverrideUseCase`, `SetFxRateOverrideUseCase`,
   `SettlementController.parseCurrencyOrValidationError`): same `Currency.of(code)` + `UnsupportedCurrencyException` →
   `*InvalidException` (both map to `VALIDATION_ERROR`). Extract a neutral helper (e.g. `Currency.ofOrThrow(code, factory)`
   or a `domain.money` `CurrencyParsing`) taking the exception factory so each call site keeps its own exception type.
3. **F3 — "validate → save → audit → return" upsert skeleton, 4×** (`SetFxCostFlowAccountConfigUseCase`,
   `SetFxCostFlowConfigUseCase`, `SetFxRateOverrideUseCase`, `SetFxToleranceUseCase`) — the javadoc already says "mirrors …".
   Extract a small template (e.g. `AuditedUpsert`) taking a validator, save fn, and audit-field mapper; each class keeps its
   own validation/mapping specifics. (The `DeleteFxCostFlowAccountConfigUseCase` variant — conditional audit, no validate —
   is deliberately NOT folded in.)

**Out of scope:** the 3× thin JPA repository adapters (candidate F4 — **declined**: marginal savings and folding them into a
generic base blurs the intentional ports-and-adapters seam); any cross-service infra boilerplate (`ApiEnvelope`,
`GlobalExceptionHandler`, `ActorContext`/`SecurityConfig` duplicated between account-service and ledger-service — intentional
per HARDSTOP-03).

## Acceptance Criteria

- **AC-0 (gate — re-measure; code wins)** — Re-confirm at current `main` the counts (5 / 3 / 4) and that each cluster is
  still near-identical. If a member has diverged (e.g. a controller now records a different actor, or an upsert grew a
  side-effect), narrow to the members that still match.
- **AC-1** — `ActorContext.identity()` exists; the 5 `actorIdentity()` copies are deleted; call sites use it.
- **AC-2** — One currency-parse helper; the 3 sites use it, each preserving its own thrown exception type (so the emitted
  error code/message is unchanged).
- **AC-3** — One upsert-audit template; the 4 use-cases use it and keep their exact validation, saved aggregate, audit event
  type, and returned view.
- **AC-4 (behavior-preserving)** — No change to error codes, audit event types/fields, or persisted data. Existing
  ledger-service tests stay GREEN unchanged.

## Related Specs
- `projects/finance-platform/specs/services/ledger-service/architecture.md`

## Related Contracts
- None (behavior-preserving; `ledger-api.md` responses/error codes unchanged).

## Edge Cases
- F2: the two use-cases throw `FxRateOverrideInvalidException` and the controller throws `FxToleranceInvalidException` — both
  map to `VALIDATION_ERROR` but are DISTINCT types; the helper must let each site keep its own type (pass a factory), not
  unify them.
- F3: preserve each use-case's exact audit `EVENT_TYPE` and `AGGREGATE_TYPE` — these are not interchangeable.

## Failure Scenarios
- **F1 — unifying F2's exception types** would change which exception (and potentially which message) surfaces. Guarded by AC-2.
- **F2 — extracting before re-measuring.** Guarded by AC-0.
