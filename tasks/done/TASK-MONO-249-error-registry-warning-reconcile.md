# TASK-MONO-249 — Error-registry Warning reconciliation sweep

Status: done
Type: monorepo-level (shared `platform/error-handling.md` + cross-project spec adaptation)
Scope class: doc-only (zero production code)

## Goal

Resolve the Warning-class error-code consistency findings surfaced by the 5-project
audit (wms / scm / erp / fan-platform / platform-console) that followed the
Critical sweep (TASK-MONO-248). Each finding was re-verified against actual code
emitters and spec text (green-wash guard) before disposition — the majority of the
raw audit findings were over-statements and are deliberately **not** actioned
(documented below under "Refuted / No-action").

## Scope

Actionable doc-only edits only. Two buckets:

**A. Shared registry — `platform/error-handling.md`:**

1. `TRANSFER_SAME_LOCATION` (wms Inventory) — registry HTTP **400 → 422**. The
   emitter (`TransferSameLocationException` + `inventory-service`
   `GlobalExceptionHandler` → `UNPROCESSABLE_ENTITY`) **and** the contract
   (`inventory-service-api.md`) both say 422; the registry row was the lone outlier.
2. `DATA_SCOPE_FORBIDDEN` — add wms `master-service` to the cross-emitter note
   (emitted at 403 by `master-service` `GlobalExceptionHandler`, matching status).
3. `WEBHOOK_SIGNATURE_INVALID` (ecommerce Shipping row) — extend the cross-domain
   reuse note to also name scm `procurement-service` (emits 401, matching).
4. `PERMISSION_DENIED` (Admin `[domain: saas]` row) — add scm services
   (procurement / demand-planning / inventory-visibility) to the cross-emitter note
   (all emit 403 via Spring Security `AccessDeniedHandler`, matching).
5. `NO_ACTIVE_TENANT` — **register** (new `## Console BFF [domain: saas]` section,
   HTTP 400). Real HTTP wire code emitted by console-bff `GlobalExceptionHandler`
   (`MissingTenantException`), frontend-mapped + contract-documented, registry-missing.
6. `ARTIST_INVALID_STATE` (fan Artist) — add "no current emitter — reserved" note
   (designed code in the Artist family; specific transitions emit
   `ARTIST_NOT_PUBLISHED`/`ARTIST_ARCHIVED`; no v1 emitter for the generic guard).
7. `MEMBERSHIP_TIER_INSUFFICIENT` (fan Community) — add "no current emitter —
   content-gating emits `MEMBERSHIP_REQUIRED`; reserved" note (the live community
   content-gate uses `MEMBERSHIP_REQUIRED`; this finer-grained tier code is unemitted).
8. `SNAPSHOT_NOT_FOUND` (scm Inventory Visibility) — add "no current v1 emitter —
   reserved" note (designed read-surface code, no emitter in `inventory-visibility-service`).

**B. Project spec adaptation (atomic with A) — erp:**

9. `IDEMPOTENCY_STORE_UNAVAILABLE` — correct the "not v1-emitted" claim in
   erp `masterdata-api.md` + `masterdata-service/architecture.md`. The DB-fallback
   store's claim path (`DbIdempotencyStore`) **does** throw it in v1 on the
   unresolved-insert-race / `DataAccessException` branch — the registry row (503)
   is correct; the contract prose was the outlier. Re-word conservatively
   (rarely-but-genuinely v1-emittable on the fail-closed store path).

## Acceptance Criteria

- [ ] `platform/error-handling.md` items 1–8 applied exactly as scoped; no other rows touched.
- [ ] `TRANSFER_SAME_LOCATION` registry status reads 422 (matches code + contract).
- [ ] New `## Console BFF [domain: saas]` section present with `NO_ACTIVE_TENANT | 400`.
- [ ] Items 6/7/8 carry an evidence-accurate "no current emitter / reserved" note —
      no over-claim of *why* beyond grep-confirmed "zero emitter".
- [ ] erp masterdata `IDEMPOTENCY_STORE_UNAVAILABLE` prose no longer asserts "not v1-emitted".
- [ ] Zero production code changed (`*.java` / `*.ts` untouched). docs-only fast-lane CI.
- [ ] 3-dimension merge verification before close chore.

## Related Specs

- `platform/error-handling.md` (authoritative shared registry — Source-of-Truth layer 5)
- `projects/erp-platform/specs/services/masterdata-service/architecture.md`

## Related Contracts

- `projects/wms-platform/specs/contracts/http/inventory-service-api.md` (TRANSFER_SAME_LOCATION 422 — already correct, registry aligned to it)
- `projects/erp-platform/specs/contracts/http/masterdata-api.md`

## Edge Cases

- Items 6/7/8 are **annotation, not deletion** — matches the file's own
  "v2-planned / reserved" convention (deletion would be inconsistent and lossy).
- `NO_ACTIVE_TENANT` is the only net-new console-bff HTTP code; other console-bff
  surfaces reuse already-registered Platform-Common auth codes (`TOKEN_INVALID`,
  `TOKEN_REVOKED`) — the new section notes this rather than re-listing them.

## Failure Scenarios

- Editing a status without code/contract corroboration → drift in the other direction.
  Mitigated: every status edit is backed by the emitter `@ResponseStatus` + contract row.
- Registering a card-reason (composition envelope) as an HTTP code → namespace error.
  Mitigated: console `TIMEOUT` / `MISSING_PREREQUISITE` are **excluded** (they live in
  `console-integration-contract.md` as degraded-card reasons, a distinct namespace).

## Refuted / No-action (verified over-statements — recorded so they are not re-raised)

- wms `PICKING_QUANTITY_EXCEEDED` / `EXTERNAL_TIMEOUT` / `ORDER_NO_DUPLICATE` —
  already self-documented (forward-declared spec codes / deferred-housekeeping note).
- wms `RESERVATION_NOT_FOUND` outbound "422 echo" — no outbound emitter exists;
  registry (404) matches the sole real emitter (inventory). Harmless spec echo.
- erp `notification-api.md` "not yet registered" note — **accurate**: the erp-domain
  `NOTIFICATION_NOT_FOUND` is a proposed/unimplemented code, genuinely unregistered.
- console `TIMEOUT` / `MISSING_PREREQUISITE` — composition card-reasons, not HTTP
  error codes; correctly governed by `console-integration-contract.md`, out of registry scope.
- console `UNAUTHORIZED` frontend message-map "gap" — correct as-is: BFF remaps any
  upstream 401 to `TOKEN_INVALID` before it reaches the frontend.
- console `CIRCUIT_OPEN` in the degraded-reason enum — forward-compat reserved
  (downstream IAM admin-service can surface it directly); defensible, left as-is.

## Deferred (out of doc-only scope — needs a code task if pursued)

- console-bff `DOWNSTREAM_ERROR` classifies all `HttpClientErrorException` (4xx, after
  401/403 peeled) as `degraded/DOWNSTREAM_ERROR` with a `"5xx"` metric tag
  (`OperatorOverviewCompositionUseCase` / `DomainHealthCompositionUseCase`). A 404/422/429
  from a downstream is mislabeled. `console-integration-contract.md` currently documents
  this as an accepted limitation — changing it is a behavioral code change requiring tests.
