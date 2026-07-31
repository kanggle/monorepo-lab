# Task ID

TASK-ERP-BE-038

# Title

ADR-MONO-058 D2 (erp-platform, all four servlet services) — adopt
`libs/java-web.ErrorResponse` / `libs/java-web-servlet.CommonGlobalExceptionHandler`, and resolve
the `details`-field / status-code design questions the ADR defers to this task

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- test

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

Close erp-platform's share of the adoption gap recorded as `ADR-MONO-058 § D2` (ACCEPTED
2026-07-30). `libs/java-web.ErrorResponse` and `libs/java-web-servlet.CommonGlobalExceptionHandler`
already ship the non-domain arms every one of erp's four servlet services
(`approval-service`, `masterdata-service`, `notification-service`, `read-model-service`)
re-implements in a service-local `GlobalExceptionHandler`. This is **not a new extraction** — no
new shared type is created.

`§ D2` explicitly defers two design questions to whichever project's task resolves them first — do
not inherit fan-platform's answers (`TASK-FAN-BE-038`) without re-checking against erp's own code;
the two projects' facts differ in ways that change the resolution.

## Measured against the tree — what erp actually has (not the ADR's cross-project paraphrase, not fan-platform's answers)

All four services' `GlobalExceptionHandler` carry between 9 and 12 `@ExceptionHandler` arms. Of
these, the following are byte-equivalent (modulo the response type: `ApiErrorBody` vs.
`ErrorResponse`) to arms `CommonGlobalExceptionHandler` already implements: `NoResourceFoundException`,
`NoHandlerFoundException`, `HttpRequestMethodNotSupportedException` (incl. the RFC 7231 `Allow`
header), `HttpMediaTypeNotSupportedException`, `MethodArgumentNotValidException`,
`IllegalArgumentException`, the `Exception` catch-all → 500, and (masterdata/approval only)
`ObjectOptimisticLockingFailureException`. `HttpMessageNotReadableException` and
`MissingRequestHeaderException` are also present in the base but erp's four copies word their
message strings slightly differently from the base's (`"Malformed request body"` vs. base's
identical string — verify exact byte match per arm before assuming zero-diff adoption).

### Design decision 1 — wire shape (`details`): **erp's field is present but currently dead — do not compose a details-carrying extension by default; verify per-arm before deciding**

- `libs/java-web.ErrorResponse` = `record(String code, String message, String timestamp)`,
  `timestamp` a pre-formatted ISO-8601 string via `Instant.now().toString()`.
- erp's `ApiErrorBody` (one copy per service, all four structurally identical) =
  `record(String code, String message, Map<String,Object> details, Instant timestamp)`,
  `@JsonInclude(NON_NULL)`. **`timestamp` is already `Instant`, not a pre-formatted `String`** —
  Jackson's default `Instant` serialization is ISO-8601 text only under a Boot-configured
  `ObjectMapper` (`JacksonAutoConfiguration` disables `WRITE_DATES_AS_TIMESTAMPS`); verify each of
  the four services actually gets Boot's mapper unshadowed (fan-platform's `TASK-FAN-BE-038` found
  one of its four services silently shadowing Boot's mapper via a `RedisCacheConfig` bean — check
  erp's four services for an equivalent `@Bean ObjectMapper` before assuming ISO-string output;
  do not assume "erp has no Redis cache config" without grepping).
- **Repo-wide grep for the 3-argument `ApiErrorBody.of(code, message, details)` factory call
  across all four erp services returns zero hits** — every call site in all four
  `GlobalExceptionHandler`s uses the 2-argument `ApiErrorBody.of(code, message)` form. **The
  `details` field is currently dead code in erp — always `null`, always omitted by
  `@JsonInclude(NON_NULL)`.**
- **However, the contract documents it as populated, and that documentation is currently false.**
  `masterdata-api.md` § Error envelope: `{ "code": …, "message": …, "details": <object?>, …}`, and
  its `DELETE` department/employee/etc. endpoints explicitly state "409
  `MASTERDATA_REFERENCE_VIOLATION` (employees / cost-centers / child departments still reference
  this row; `details` enumerates the referencer kinds)" — but the actual `handleDomain` arm never
  populates `details` for any code, including that one. **This is a pre-existing contract/code
  drift, independent of ADR-MONO-058** — the contract has been over-promising a field the service
  never fills. Check the other three services' contracts (`approval-api.md`, `notification-api.md`,
  `read-model-api.md`) for the same pattern before assuming it is masterdata-specific.
- **Resolution to make, not inherit:** given `details` is (a) currently unpopulated everywhere in
  erp and (b) contract-documented as populated in at least one place that is currently false, this
  task must choose one of:
  1. Adopt `ErrorResponse` outright for every arm (drop `ApiErrorBody`/`details` entirely) and
     correct the contract's `details` documentation to remove the now-inaccurate promise — the
     simplest option, but it is a **contract change** (removing a documented field), which per
     `CLAUDE.md` requires updating `specs/contracts/` *before* implementation, not silently.
  2. Compose per fan-platform's shape (keep `ApiErrorBody` as a `details`-carrying extension for
     the codes the contract documents as carrying it, e.g. `MASTERDATA_REFERENCE_VIOLATION`) **and
     actually populate `details`** for those codes as part of this task — turning the dead field
     into a real one and closing the contract/code drift as a side effect, which is more work but
     resolves a genuine defect rather than papering over it by deleting the contract's promise.
  This task's own implementer must make this call explicitly and record the reasoning in the PR
  body (per `§ D2`'s "this is a real per-project product decision" framing) — it is not
  pre-decided here, and it is not obligated to match fan-platform's answer (fan-platform's
  `details` field was actively populated and documented across multiple endpoints; erp's is not).
  If option 2 is chosen, populating `details` for `MASTERDATA_REFERENCE_VIOLATION` (and any other
  code the contracts document it for) is itself new observable behavior — a client that previously
  received `details: undefined` now receives real data — and must be called out in the PR body even
  though it moves the service *onto* what the contract already promised.

### Design decision 2 — status-code conflict: **verify before assuming one exists — erp's `VALIDATION_ERROR` already matches the shared default**

Unlike fan-platform (which mapped `@Valid` failures to 422 against the base's 400 default), **erp's
four `STATUS_BY_CODE` tables all map `VALIDATION_ERROR` → `HttpStatus.BAD_REQUEST` (400)** —
already identical to `CommonGlobalExceptionHandler.validationFailureStatus()`'s default. Measured
directly from `masterdata-service`/`approval-service`'s `STATUS_BY_CODE` maps and
`notification-service`/`read-model-service`'s hard-coded `HttpStatus.BAD_REQUEST` in
`handleValidation`/`handleIllegalArgument`. **There is no D2 status-code conflict to resolve for
erp's `@Valid`/`IllegalArgumentException` arms** — the base's hook (added by fan-platform's
`TASK-FAN-BE-038`, `protected HttpStatus validationFailureStatus()`) needs no override in any of
erp's four services; the default value is correct as-is.

`ILLEGAL_STATE` (approval/masterdata only, `IllegalStateException` → 422 `UNPROCESSABLE_ENTITY`)
has **no equivalent arm in `CommonGlobalExceptionHandler`** — it stays a service-local arm exactly
as fan-platform kept its own non-base arms local; this is not a status conflict, it is an arm the
base does not carry at all.

---

# Scope

## In Scope

- Each of `approval-service`, `masterdata-service`, `notification-service`, `read-model-service`:
  `GlobalExceptionHandler extends com.example.web.exception.CommonGlobalExceptionHandler`; delete
  the arms the base now supplies (`NoResourceFoundException`, `NoHandlerFoundException`,
  `HttpRequestMethodNotSupportedException`, `HttpMediaTypeNotSupportedException`,
  `MethodArgumentNotValidException`, `IllegalArgumentException`, `Exception` catch-all, and —
  approval/masterdata — `ObjectOptimisticLockingFailureException`); keep only the genuinely
  erp-owned arms (the domain-exception dispatch via `STATUS_BY_CODE`/`respond(...)`,
  `MethodArgumentTypeMismatchException`, `OptimisticLockException` (jakarta, approval/masterdata),
  `IllegalStateException` (approval/masterdata), the two `notification`/`read-model`-specific
  `NotFound`/`ReadAccessDenied` domain arms).
- `build.gradle` for all four services — add `implementation project(':libs:java-web-servlet')` if
  not already present (verify; do not assume).
- Resolve **design decision 1** (`details`) per the two options above, explicitly, for all four
  services consistently (do not resolve it one way for masterdata and a different way for the
  other three without a stated reason — the contract data suggests the drift may be
  masterdata-specific, in which case a per-service resolution with a documented reason is
  legitimate, but that must be a finding, not an oversight).
- Confirm **design decision 2** requires no override: no `validationFailureStatus()` override
  needed in any of the four services (verify against the base's actual default before shipping —
  do not skip the verification just because this task's own Goal section already did it once).
- `HttpMessageNotReadableException` / `MissingRequestHeaderException` message-string byte-parity
  check (see Goal) — if erp's wording differs from the base's, either accept the wording change
  (flag it, since it is observable to a client parsing the message field, though the message field
  is documented as human-readable prose, not a machine-matched value — check
  `platform/error-handling.md` for whether message text is contract-pinned before treating a
  wording change as a break) or override the arm to preserve erp's exact wording.
- Tests: rewrite each service's existing `GlobalExceptionHandlerNotFoundTest` from a direct method
  call into a MockMvc `standaloneSetup(...).setControllerAdvice(new GlobalExceptionHandler())`
  form, so the test proves the **inherited** arms are actually registered and reached through
  Spring's real resolver (a direct method call would pass even if inheritance registered nothing).
- Spec reconciliation: each service's `architecture.md § Dependencies`/security section gains
  `libs:java-web-servlet` if the module was not already documented there.

## Out of Scope

- **Every other project** — `§ 6` forbids a cross-project mega-PR; the other seven projects'
  D2 adoption (including fan-platform's, already done) are separate tasks.
- **`ErrorResponse`'s shape** — do not widen it with a `details` field; the base module is shared
  by services in `iam-platform` and `ecommerce-microservices-platform` this task has no mandate
  over (verify current consumer count with
  `grep -rl 'com.example.web.dto.ErrorResponse'` before ruling this out entirely, per the same
  blast-radius discipline `TASK-FAN-BE-038` applied).
- **`ApiEnvelope`** (the success `{data, meta}` envelope, if erp has one distinct from the error
  envelope) — `libs/` ships no shared success envelope; nothing to adopt there.
- **`gateway-service`** — reactive; `libs:java-web-servlet` must never reach a reactive classpath.
  Untouched.
- **ADR-MONO-058 D1 / D3 / D4 / D5** — separate tasks.
- Any new error code.

---

# Acceptance Criteria

- [ ] **AC-1 (adoption)** — all four services' `GlobalExceptionHandler extends
      CommonGlobalExceptionHandler`; each `build.gradle` declares
      `implementation project(':libs:java-web-servlet')`. Repo-wide grep shows the base-supplied
      arms declared **zero** times under `projects/erp-platform/apps/*/src/main`.
- [ ] **AC-2 (design decision 1 resolved and stated)** — the PR body states explicitly which of the
      two `details` options was chosen, for which services/codes, and why — referencing the actual
      grep evidence (zero populated call sites; `masterdata-api.md`'s currently-false `details`
      promise) rather than assuming fan-platform's answer applies.
- [ ] **AC-3 (design decision 2 verified, not assumed)** — a test confirms
      `validationFailureStatus()`'s unmodified default (400) already matches all four services'
      `@Valid`/`IllegalArgumentException` behavior; no override is added unless the verification
      finds a real discrepancy (in which case the AC changes to require the override + a stated
      reason).
- [ ] **AC-4 (ILLEGAL_STATE / erp-owned arms preserved)** — `approval-service`/`masterdata-service`
      still answer 422 `ILLEGAL_STATE` for `IllegalStateException`; `MethodArgumentTypeMismatchException`
      still answers 400 `VALIDATION_ERROR` in all four; the jakarta `OptimisticLockException` arm
      (approval/masterdata) still answers 409 `CONCURRENT_MODIFICATION` (note: the shared base's
      own `ObjectOptimisticLockingFailureException` arm answers `CONFLICT` with code `"CONFLICT"`,
      not erp's `"CONCURRENT_MODIFICATION"` — verify this collision explicitly: if erp adopts the
      base's `ObjectOptimisticLockingFailureException` arm as-is, the emitted `code` string changes
      from `CONCURRENT_MODIFICATION` to `CONFLICT`, which **is** an observable contract change
      unless erp overrides that one arm too to keep its own code string. Resolve deliberately, not
      by accident).
- [ ] **AC-5 (reachability, not just logic)** — each service's rewritten
      `GlobalExceptionHandlerNotFoundTest` drives the request through MockMvc's real resolver.
- [ ] **AC-6 (baseline parity)** — before/after test counts recorded per module; no test lost;
      all four `:check` GREEN; CI `Integration (erp-platform, Testcontainers)` GREEN authoritative.
- [ ] **AC-7 (specs reconciled)** — `architecture.md` dependency/security sections corrected where
      they omitted `libs:java-web-servlet`.
- [ ] **AC-8 (contract impact stated)** — if decision 1 or the `ObjectOptimisticLockingFailureException`
      code-string collision (AC-4) produces any observable change, `specs/contracts/http/*.md` is
      updated **before** implementation ships (per `CLAUDE.md`), and the PR body states the delta
      explicitly; if neither produces a change, the PR body states that too.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`,
> then load `rules/common.md` plus `rules/domains/erp.md` and `rules/traits/{internal-system,
> transactional,audit-heavy}.md`. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § D2, § 4, § 6
  (ACCEPTED 2026-07-30)
- `platform/error-handling.md` § Error Response Format, § HTTP Status Code Mapping, § General
- `platform/shared-library-policy.md` § Decision Rule, § Ownership Rule, § Change Rule
- `platform/service-types/rest-api.md` § Error Handling
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` — origin tracking task
- `projects/fan-platform/tasks/done/TASK-FAN-BE-038-adr058-d2-error-envelope-shared-handler-adoption.md`
  — **prior art, read before starting, but do not inherit its answers.** It found fan-platform's
  `details` field actively populated/documented (unlike erp's, currently dead) and a real 422-vs-400
  conflict (unlike erp's, already aligned) — the two projects' facts genuinely differ, and this task
  must re-derive both design decisions from erp's own code and contracts, not copy fan-platform's
  resolution.
- `projects/erp-platform/tasks/done/TASK-MONO-450` (`DataIntegrityViolationException` selective
  mapping precedent, if a corresponding erp task exists — verify presence before citing) and
  `platform/error-handling.md § DATA_INTEGRITY_VIOLATION`
- `projects/erp-platform/specs/services/{approval,masterdata,notification,read-model}-service/architecture.md`
  § Dependencies / § Error Handling
- `projects/erp-platform/specs/contracts/http/{approval,masterdata,notification,read-model}-api.md`
  § Error envelope

---

# Related Contracts

- `projects/erp-platform/specs/contracts/http/masterdata-api.md` § Error envelope, § `DELETE`
  endpoints' `MASTERDATA_REFERENCE_VIOLATION` `details` promise — **read-write input**: if decision
  1 removes the `details` promise instead of fulfilling it, this file must be updated in the same
  PR, before implementation, per `CLAUDE.md`.
- `projects/erp-platform/specs/contracts/http/{approval,notification,read-model}-api.md` § Error
  envelope — read-only unless the same `details` pattern is found there too.

---

# Target Service

- `approval-service`, `masterdata-service`, `notification-service`, `read-model-service`
  (erp-platform)
- Consumes `libs/java-web` (`ErrorResponse`) / `libs/java-web-servlet`
  (`CommonGlobalExceptionHandler`) — no shared-library code authored by this task.

---

# Architecture

Follow each target service's own `architecture.md`. The advice class stays in its current
presentation/adapter-in package (`presentation.advice` for approval/masterdata/notification,
`adapter.inbound.web.advice` for read-model) and keeps its class name `GlobalExceptionHandler`.

---

# Implementation Notes

- Order of work that keeps the diff reviewable: (1) resolve decision 1 and decision 2 first, in
  writing, before touching any handler code — both are cheap to get wrong and expensive to unwind
  once four services have adopted a shape; (2) one service end-to-end
  (`notification-service` — smallest, no `ObjectOptimisticLockingFailureException` arm to reconcile);
  (3) replicate to `read-model-service`; (4) `approval-service`/`masterdata-service` last (they
  carry the `ObjectOptimisticLockingFailureException` code-string collision from AC-4 — resolve it
  once, apply identically to both since they are otherwise structurally identical); (5) specs.
- The `ObjectOptimisticLockingFailureException` code-string collision (`CONCURRENT_MODIFICATION` vs
  base's `CONFLICT`) is the one place this adoption is *not* a clean drop-in for
  approval/masterdata — plan to override that single arm in both services to preserve
  `CONCURRENT_MODIFICATION`, mirroring how fan-platform's `TASK-FAN-BE-038` kept its own
  domain-specific arms local rather than accepting the base's generic wording.

---

# Edge Cases

- A `details`-less `ApiErrorBody` (if decision 1's composition option is chosen) must serialise
  identically to `ErrorResponse` — verify under each service's actual `ObjectMapper` (Boot-default
  or shadowed), not a hand-tuned test mapper.
- `notification-service`/`read-model-service` are declared `event-consumer`+`rest-api` /
  `rest-api`+`event-consumer` respectively but both expose REST inboxes/queries; their HTTP error
  surface is in scope like the other two.
- If any of the four services' `HttpMessageNotReadableException`/`MissingRequestHeaderException`
  message string differs from the base's, decide per-arm whether to accept the base's wording or
  override — do not let this slip through unverified.

---

# Failure Scenarios

- **Silently changing an error code string.** The `ObjectOptimisticLockingFailureException`
  collision (AC-4) is the concrete risk: adopting the base's arm unmodified would flip
  `CONCURRENT_MODIFICATION` → `CONFLICT` in two services' wire output with no contract update.
- **Inheriting fan-platform's design-decision answers wholesale.** `§ D2` frames both questions as
  per-project product decisions "best made with the actual consumers in front of the implementer" —
  erp's `details` field is dead where fan's was live, and erp's validation status already matches
  the default where fan's did not. Assuming parity with fan-platform's resolution without
  re-deriving from erp's own code would silently pick the wrong answer for at least decision 1.
- **Green-wash by direct method call.** AC-5 exists because a direct-call test would stay green
  even if the `extends` registered nothing with Spring's resolver.
- **Widening `ErrorResponse`.** Rejected in decision 1 for the same blast-radius reason
  `TASK-FAN-BE-038` documented — other projects depend on the unwidened shape.
- **Scope leak into the other seven projects.** `§ 6` forbids a cross-project mega-PR.

---

# Test Requirements

- Slice/unit (per service, ×4): rewritten `GlobalExceptionHandlerNotFoundTest` (MockMvc standalone
  advice, real resolver); existing domain-arm tests pass unmodified.
- New per-service assertions for: `details` behavior per decision 1's resolution; validation status
  (400, confirming no override needed, or the override + reason if verification found otherwise);
  the `ObjectOptimisticLockingFailureException` code-string outcome (approval/masterdata).
- `./gradlew :libs:java-web-servlet:test` and the four erp `:check` tasks GREEN. CI
  `Integration (erp-platform, Testcontainers)` GREEN authoritative.

---

# Definition of Done

- [ ] Implementation completed (4 service adoptions; no shared-lib code change expected — flag if
      one turns out to be needed)
- [ ] Tests passing; per-service before/after counts recorded; no test lost
- [ ] Both design decisions resolved explicitly and stated in the PR body, derived from erp's own
      code/contracts rather than inherited from fan-platform
- [ ] `ObjectOptimisticLockingFailureException` code-string collision resolved deliberately
      (approval/masterdata)
- [ ] Contract impact stated; `masterdata-api.md`'s `details` promise reconciled one way or the
      other
- [ ] Specs updated (`architecture.md` dependency lines × 4 as needed)
- [ ] Ready for review
