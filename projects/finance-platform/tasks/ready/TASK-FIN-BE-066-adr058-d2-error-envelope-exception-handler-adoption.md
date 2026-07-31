# Task ID

TASK-FIN-BE-066

# Title

ADR-MONO-058 D2 — adopt `libs/java-web`/`libs/java-web-servlet`'s shared error envelope + generic exception-handler tail (`ErrorResponse` / `CommonGlobalExceptionHandler`) in `account-service` + `ledger-service`, resolving the `details`-field wire-shape decision

# Status

ready

# Owner

backend

# Task Tags

- code
- test
- adr
- contract

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § 2 **D2**
(ACCEPTED 2026-07-30) directs adopting the already-existing `libs/java-web.ErrorResponse` /
`libs/java-web-servlet.CommonGlobalExceptionHandler` in every service that currently hand-rolls the
non-domain arms of its `GlobalExceptionHandler` (`NoResourceFound`, `HttpMediaTypeNotSupported`,
`HttpRequestMethodNotSupported`, the catch-all 500) — this is explicitly framed as an **adoption gap**,
not a new extraction. The ADR names **finance-platform by name** as one of the services whose wire
shape must be reconciled before adoption: *"one — finance-platform's — flips `timestamp` between
`String` and `Instant` across its own two services"* (§ 2 D2), and defers the `details`-field /
status-code design trade-off to whichever service's task implements adoption (§ 2 D2, § 4).

**This task is the finance-platform adoption.** It is separate from, and must not be confused with,
`TASK-FIN-BE-064` (`ADR-003`, DONE) — that task consolidated `Money`/`Currency` **domain value
objects**, a different axis entirely (already closed, do not reopen). It is also the direct sequel to
`TASK-FIN-BE-058` (DONE, CLOSED WONTFIX 2026-07-19, Option C) — see § "Relationship to TASK-FIN-BE-058"
below for why that WONTFIX does not block this task.

---

# Relationship to TASK-FIN-BE-058 (prior WONTFIX) — why this task is not a re-litigation

`TASK-FIN-BE-058` investigated this exact duplication (finance's `GlobalExceptionHandler` hand-copying
`CommonGlobalExceptionHandler`'s 404/405/415/500 boilerplate) on 2026-07-19 and closed **WONTFIX
(Option C)** at the *project* level, reasoning that:

- `ErrorResponse` was, at that time, **poorer** than finance's `ApiErrorBody` (`{code, message,
  details?, Instant timestamp}` vs `{code, message, String timestamp}` — no `details` field at all).
- Finance deliberately maps `IllegalArgumentException` to different statuses per service (account
  → 422 `AMOUNT_INVALID`, ledger → 400 `VALIDATION_ERROR`), which the shared handler's fixed 400
  mapping could not express without an override hook.
- Adopting as-is (Option A) would have been a **public error-contract regression** (dropping
  `details`, narrowing `Instant`→`String`).

**What has changed since, making this a different call now:**

1. `ADR-MONO-058` is a **fleet-wide, human-ACCEPTED** decision (`platform/shared-library-policy.md
   § Change Rule` gate) that explicitly re-opens this exact question at the fleet level and directs
   every project (finance named specifically) to resolve it — a fleet ADR outranks a prior
   project-local WONTFIX investigation's conclusion; it does not retroactively make that
   investigation's evidence wrong, but it does authorize revisiting the call with a wider mandate and
   (per the point below) a materially changed shared library.
2. **`CommonGlobalExceptionHandler` now ships the status-code override hook** TASK-FIN-BE-058's
   Option-A rejection needed and didn't have: `protected HttpStatus validationFailureStatus()`
   (`libs/java-web-servlet/.../CommonGlobalExceptionHandler.java`), whose javadoc states *"Added by
   ADR-MONO-058 § D2, which requires the shared handler to expose this mapping rather than force one
   status on every adopter"*. This closes TASK-FIN-BE-058's second blocker (the 422-vs-400 split)
   without finance having to keep a fully hand-rolled handler.
3. **The `details`-field blocker is still open** (`ErrorResponse` still has no `details` field) — this
   task must resolve it, per the ADR's explicit deferral ("does `ErrorResponse` gain an optional
   `details` field... or does a service that need `details` compose its own envelope around the shared
   base? This ADR does not decide that trade-off — it is deferred to whoever implements D2").
   `TASK-FIN-BE-058`'s finding that `details` is a **documented contract field** (`account-api.md`,
   `ApiErrorBody.of(code, message, details)`) is still true and still real evidence — it must inform
   which of the two options this task picks, not be discarded.

---

# Scope

## In Scope

### a. Design decision (must be made and recorded in the PR, per ADR § 4/§ 6 item 6)

Resolve, with the actual current code and both contract docs in front of you (not this task's
summary):

- **`details` field.** Choose one:
  - **Option A — widen `ErrorResponse`**: add an optional `Map<String, Object> details` field to
    `libs/java-web.ErrorResponse` (nullable, `@JsonInclude(NON_NULL)` so it is invisible on the wire
    for every other consumer that never sets it — additive, non-breaking for the fleet's other
    `ErrorResponse` consumers). Finance's `ApiErrorBody` is then retired in favor of `ErrorResponse`
    directly.
  - **Option B — finance composes its own envelope around the shared base**: keep a finance-owned
    `ApiErrorBody` type (or a thin wrapper) that carries `details`, while the *handler* logic (the
    404/405/415/500 arms) delegates to/extends `CommonGlobalExceptionHandler`'s mechanism. This keeps
    the shared `ErrorResponse` type untouched for every other consumer.
  - Record the choice and its reasoning in the PR description — this is a wire-format design call
    the ADR deliberately left to this task, not a mechanical default.
- **`timestamp` type.** This task's own investigation (2026-07-31) found **no current String-vs-Instant
  divergence** between `account-service` and `ledger-service`'s `ApiErrorBody` — both currently declare
  `Instant timestamp` identically (verified by reading both files directly). The ADR's claim may
  predate a fix, or may be inaccurate — **re-verify at implementation time** (code can have moved
  again since 2026-07-31) rather than assuming either this task's finding or the ADR's original claim
  is current truth. Whichever the state, the two services' timestamp handling must end this task
  **identical to each other** — that invariant is the actual thing D2 requires, independent of which
  claim was accurate on which date.
- **Status-code override.** Use `CommonGlobalExceptionHandler`'s `validationFailureStatus()` hook —
  override it to `HttpStatus.UNPROCESSABLE_ENTITY` in `account-service`'s handler (matching its
  documented `422 AMOUNT_INVALID` for `IllegalArgumentException`) and leave it at the default `400`
  in `ledger-service`'s handler (matching its documented `400 VALIDATION_ERROR`). This is exactly the
  asymmetry `TASK-FIN-BE-058` found deliberate — the override hook now lets it be expressed without a
  hand-rolled handler, closing that blocker without erasing the asymmetry.

### b. Both services' `GlobalExceptionHandler` adopt the shared tail

- `account-service` `presentation/advice/GlobalExceptionHandler.java` and `ledger-service`
  `presentation/advice/GlobalExceptionHandler.java` — extend `CommonGlobalExceptionHandler` (or
  compose it, per whichever § a option is chosen) so the four boilerplate arms
  (`NoResourceFoundException`, `NoHandlerFoundException`, `HttpRequestMethodNotSupportedException`,
  `HttpMediaTypeNotSupportedException`) and the catch-all `Exception` → 500 handler are no longer
  hand-duplicated. Each service's `FinanceDomainException`/`LedgerDomainException` mapping,
  `STATUS_BY_CODE` table, and every other domain-specific `@ExceptionHandler` stay exactly as they are
  — this task touches only the generic tail named in the ADR, never the domain error mapping.
- `account-service` — override `validationFailureStatus()` → `422`. `ledger-service` — leave at the
  `400` default (no override needed).
- The byte-identical `GlobalExceptionHandlerNotFoundTest` pair (the test-level symptom
  `TASK-FIN-BE-058` originally flagged) collapses once the production duplication it mirrors is
  removed — either deleted in favor of the shared library's own `CommonGlobalExceptionHandlerTest`
  coverage, or reduced to asserting only finance-specific wiring (e.g. that the envelope still carries
  `details` when present).
- Both `account-api.md` and `ledger-api.md` (and `reconciliation-api.md`, which documents the same
  error-envelope shape) — reconcile the documented error envelope to match whichever § a option is
  chosen. If Option A (widen `ErrorResponse`), no documented shape actually changes (`details` stays
  optional, same as today). If Option B, document precisely what changed (should also be nothing
  client-visible, if the wrapper preserves the current wire shape).

## Out of Scope

- Any domain-specific exception mapping (`FinanceDomainException`/`LedgerDomainException` handlers,
  `STATUS_BY_CODE` tables, the `Currency.UnsupportedCurrencyException`/`Money.CurrencyMismatchException`
  handlers, the `DataIntegrityViolationException` unique-vs-non-unique split, `IllegalStateException`
  handling) — none of this is the "generic, non-domain tail" D2 targets; it stays untouched.
- The deliberate `IllegalArgumentException` 422-vs-400 asymmetry itself — preserved via the override
  hook, not removed or unified (removing it would be an undocumented contract change neither the ADR
  nor this task authorizes).
- `libs/java-web`/`libs/java-web-servlet` changes beyond the `details`-field decision in § a Option A
  — if that option is chosen, the change to `ErrorResponse` must be minimal (one optional field) and
  must not alter behavior for any other consumer (iam-platform's account/admin/auth/security services,
  which already `extends CommonGlobalExceptionHandler`). Verify no other consumer's test suite breaks.
- Re-litigating `TASK-FIN-BE-058`'s underlying evidence — that evidence is still accurate input to
  § a's decision, not something this task disproves; only the *conclusion* ("leave it duplicated") is
  superseded by the ADR's fleet-level direction.
- `TASK-FIN-BE-064`'s `Money`/`Currency` axis — unrelated, already done, not reopened here.
- D1 (actor/JWT cluster) and D3 (pagination) — separate tasks (`TASK-FIN-BE-065`, `TASK-FIN-BE-067`).

---

# Acceptance Criteria

- [ ] The § a design decision (`details` field: widen `ErrorResponse` vs. finance-composed wrapper) is
      made, recorded with reasoning in the PR, and implemented consistently across both services.
- [ ] `account-service` and `ledger-service`'s `GlobalExceptionHandler` no longer hand-duplicate the
      404/405/415/500 + catch-all-500 arms — they delegate to `CommonGlobalExceptionHandler`.
- [ ] `account-service` publishes `422` for the arms `validationFailureStatus()` governs (matching its
      current documented behavior); `ledger-service` publishes `400` (matching its current documented
      behavior) — i.e., **zero client-visible status-code change** for existing documented behavior.
- [ ] The error envelope's wire shape (`code`, `message`, `details?`, `timestamp`) is unchanged from a
      client's perspective for both services — verified by re-running (or re-asserting) any existing
      test that snapshots the JSON body shape, not just checking the Java type compiles.
- [ ] `account-api.md` / `ledger-api.md` / `reconciliation-api.md` accurately describe the resulting
      envelope shape (no stale doc left behind if § a's choice changes anything documentable).
- [ ] `GlobalExceptionHandlerNotFoundTest`'s byte-identical duplication between the two services is
      resolved (deleted in favor of shared coverage, or reduced to finance-specific assertions only —
      no byte-identical pair survives unexplained).
- [ ] `./gradlew :projects:finance-platform:apps:account-service:check :projects:finance-platform:apps:ledger-service:check`
      GREEN, before/after test counts recorded.
- [ ] `./gradlew :projects:finance-platform:apps:account-service:integrationTest :projects:finance-platform:apps:ledger-service:integrationTest`
      GREEN (CI Testcontainers lane authoritative).
- [ ] If § a Option A is chosen (widening `libs/java-web.ErrorResponse`): every other current consumer
      of `CommonGlobalExceptionHandler`/`ErrorResponse` (iam-platform's account/admin/auth/security
      services, at minimum) still builds and its own test suite is unaffected by the additive field.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus `rules/domains/fintech.md` and `rules/traits/transactional.md` / `rules/traits/regulated.md` / `rules/traits/audit-heavy.md`. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § 2 D2, § 4
  ("D2's wire-shape and status-code conflicts... are real per-project product decisions hiding inside
  what looks like pure technical duplication"), § 6 item 6
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` (the root split origin)
- `projects/finance-platform/tasks/done/TASK-FIN-BE-058-globalexceptionhandler-common-base-dedup-investigation.md`
  (the prior WONTFIX this task supersedes at the fleet-policy level — read in full; its evidence about
  `details` being a documented contract field is still true and must inform § a)
- `projects/finance-platform/tasks/done/TASK-FIN-BE-064-finance-common-money-currency-module-extraction.md`
  (different axis — Money/Currency domain value objects — explicitly NOT this task's concern; read
  only to confirm the boundary, not as a template for what to touch)
- `platform/error-handling.md` § Error Response Format ("Services that return additional context...
  structured `details`... are permitted to extend this envelope, but the three fields above must
  always be present" — the platform-level permission this task's `details` decision operates under),
  § HTTP Status Code Mapping
- `platform/shared-library-policy.md` (governs any `libs/java-web` change under § a Option A)

---

# Related Contracts

- `specs/contracts/http/account-api.md` — error envelope section (`{code, message, details?,
  timestamp}`) must be reconciled to whatever § a produces; **no client-visible shape change is
  authorized** by this task beyond what § a's chosen option already permits (details stays optional
  either way).
- `specs/contracts/http/ledger-api.md` — same reconciliation.
- `specs/contracts/http/reconciliation-api.md` — documents the same error envelope shape; reconcile if
  affected.
- This task must **not** change the success envelope (`ApiEnvelope` / `{data, meta}`) — that is not
  named in D2 and is out of scope; do not conflate the two envelopes during implementation.

---

# Target Service

- `account-service`
- `ledger-service`
- `libs/java-web` (only if § a Option A is chosen — `ErrorResponse` widening)

---

# Architecture

- `account-service`: Hexagonal. `GlobalExceptionHandler` lives in `presentation/advice/` — this task
  changes only that layer's generic-tail implementation, not the layering itself.
- `ledger-service`: Hexagonal + DDD. Same note.
- Any `libs/java-web` change (§ a Option A) is a leaf library change — no internal layering of its own
  to preserve beyond keeping `ErrorResponse` framework-neutral (no Spring dependency added to
  `libs/java-web`, which is deliberately framework-agnostic per its existing role as the reactive/servlet-neutral
  base for `libs/java-web-servlet` and any reactive counterpart).

---

# Implementation Notes

- **Read `CommonGlobalExceptionHandler`'s current source in full before implementing** — it already
  contains the `validationFailureStatus()` override hook this task depends on; do not assume it needs
  to be added (verify by reading `libs/java-web-servlet/src/main/java/com/example/web/exception/CommonGlobalExceptionHandler.java`
  directly, since the hook's own javadoc already cites `ADR-MONO-058 § D2` as its origin — some other
  project's adoption task may have landed it before this one, which is fine and expected; do not
  duplicate it).
- **`ApiEnvelope` (success envelope) is untouched** — both services' `presentation/dto/ApiEnvelope.java`
  already carry `meta.timestamp` as an ISO-8601 string (`Instant.now().toString()`), byte-identical
  between the two services today. D2 is about the *error* envelope and the generic exception-handler
  tail only.
- **The `STATUS_BY_CODE` maps and every domain `@ExceptionHandler` method are the majority of both
  files and are completely out of scope** — the diff this task produces should be small: remove the
  four generic-tail handler methods + the catch-all, add `extends CommonGlobalExceptionHandler` (or
  the composition equivalent), add the `validationFailureStatus()` override in account-service only.
- If § a Option B (finance-composed wrapper) is chosen, prefer the smallest wrapper that still lets
  `GlobalExceptionHandler` delegate the generic tail to `CommonGlobalExceptionHandler` — e.g. a
  `finance-common`-hosted (or per-service, if not shared logic) subtype/converter between
  `ErrorResponse` and `ApiErrorBody` rather than hand-rolling the four generic handlers a second time,
  which would defeat the purpose of adopting the shared tail at all.

---

# Edge Cases

- **`details` must remain absent (not `null`-serialized) when unset**, matching today's
  `@JsonInclude(Include.NON_NULL)` behavior on `ApiErrorBody` — whichever § a option is chosen must
  preserve this, since a client relying on the field's *absence* (vs. explicit `null`) to detect "no
  structured details" would see a wire-format change otherwise.
- **`IllegalArgumentException` status asymmetry** — confirm the override hook actually produces `422`
  for account-service's `IllegalArgumentException` arm specifically (not just the `@Valid`
  constraint-violation arm) — `CommonGlobalExceptionHandler.handleIllegalArgument` already routes
  through `validationFailureStatus()`, so this should be automatic, but verify with a live test rather
  than assuming the hook covers exactly the two arms its javadoc claims.
- **Re-verify the timestamp-type claim at implementation time** (see § a) — do not implement a "fix"
  for a String-vs-Instant divergence that may not currently exist; implement the adoption, and let the
  adoption itself make both services' timestamp handling identical as a natural consequence, rather
  than chasing a possibly-stale claim as a separate fix.

---

# Failure Scenarios

- Widening `ErrorResponse` (§ a Option A) in a way that is NOT purely additive (e.g. reordering record
  components, changing `timestamp`'s type) would break every other current consumer
  (`iam-platform`'s account/admin/auth/security services) — this is the exact "changes finance's error
  contract while claiming to be a mechanical adoption" risk `ADR-MONO-058 § 4` warns about, generalized
  to the shared type itself. Verify with the other consumers' own test suites, not just finance's.
- Dropping the `422`/`400` asymmetry (unifying both services to one status) instead of using the
  override hook would silently change account-service's or ledger-service's published HTTP contract —
  a regression `TASK-FIN-BE-058` explicitly flagged as unacceptable and this task must not reintroduce.
- Leaving the byte-identical `GlobalExceptionHandlerNotFoundTest` pair in place after the production
  duplication it mirrors is removed would mean the test-level smell survives the fix that was supposed
  to close it — the AC requires resolving this, not just the production code.

---

# Test Requirements

- Existing `GlobalExceptionHandlerTest` suites (both services) — domain-exception-mapping assertions
  must pass unchanged (out of scope for this task, so should require no edits).
- `GlobalExceptionHandlerNotFoundTest` (both services) — either deleted in favor of the shared
  library's own coverage, or narrowed to finance-specific wiring assertions; whichever is chosen,
  the four generic-tail behaviors (404 on unmapped path, 404 on no-handler, 405 with `Allow` header,
  415 on unsupported media type) must still be covered by *some* test — either finance's own (if
  narrowed, not deleted) or `CommonGlobalExceptionHandlerTest` in the shared library (already exists —
  confirm it covers all four before deleting finance's copy).
- If § a Option A is chosen: `ErrorResponseTest` (`libs/java-web`) gets a new case covering the
  optional `details` field's presence/absence serialization.
- Contract-shape assertions (JSON body structure) for the error envelope in both services' controller
  slice tests — confirm they still pass without modification, proving no wire-format regression.

---

# Definition of Done

- [ ] § a design decision made, recorded, and implemented consistently in both services
- [ ] Both `GlobalExceptionHandler`s adopt the shared generic tail; domain-specific handlers untouched
- [ ] Status-code asymmetry preserved via `validationFailureStatus()` override (account 422, ledger 400 default)
- [ ] `account-api.md` / `ledger-api.md` / `reconciliation-api.md` reconciled
- [ ] Byte-identical `GlobalExceptionHandlerNotFoundTest` pair resolved
- [ ] Tests passing (unit + Testcontainers integration, CI-authoritative), before/after counts recorded
- [ ] If `libs/java-web` touched: other consumers verified unaffected
- [ ] Ready for review
