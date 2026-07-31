# Task ID

TASK-BE-567

# Title

ADR-MONO-058 D2 (wms-platform) — adopt `libs/java-web-servlet.CommonGlobalExceptionHandler` /
`libs/java-web.ErrorResponse` for the non-domain exception-handler tail across the 5 wms servlet
services, and resolve D2's wire-shape + status-code blockers as wms's own design decision

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

# Dependency Markers

- **선행 없음** — standalone; does not depend on `TASK-BE-568`/`569`/`570`/`571` (the sibling
  ADR-MONO-058 wms adoption tasks filed alongside this one) or on `TASK-MONO-500`.
- **관련 (비차단)**: `projects/fan-platform/tasks/done/TASK-FAN-BE-038-adr058-d2-error-envelope-shared-handler-adoption.md`
  is prior art for the *governance shape* of a D2 adoption task (one atomic PR touching the shared
  lib + every affected service, explicit wire-shape/status-code decisions recorded, before/after
  test-count table) — read it before starting. **Its concrete decisions do not transfer** — fan's
  envelope is a flat `{code,message,details,timestamp}` record; wms's is a nested
  `{error:{code,message,timestamp,details,traceId,requestId}}` record (see Goal). This is a materially
  different wire shape and must be re-decided from wms's own contracts, not copied from fan's.

---

# Goal

Close wms-platform's share of the **adoption gap** recorded as `ADR-MONO-058 § D2` (ACCEPTED
2026-07-30). `libs/java-web.ErrorResponse` and `libs/java-web-servlet.CommonGlobalExceptionHandler`
already ship the non-domain arms (`NoResourceFoundException`, `HttpRequestMethodNotSupportedException`
incl. RFC 7231 `Allow` header, `HttpMediaTypeNotSupportedException`, the catch-all `Exception` → 500,
`MethodArgumentNotValidException`) that each of wms-platform's 5 servlet services re-implements in a
service-local `GlobalExceptionHandler`. This is **not a new extraction** — no new shared type is created.

**Measured against the tree** (not the ADR's cross-project paraphrase): `GlobalExceptionHandler` exists
as a near-identical `@RestControllerAdvice` in **5** services —
`master-service`, `inventory-service`, `outbound-service`, `inbound-service`, `admin-service` — each
carrying the same non-domain arm set: a `Map<Class<? extends XDomainException>, HttpStatus>` domain
dispatch table, `MethodArgumentNotValidException` → 400 `VALIDATION_ERROR` (with field-level `details`),
`HttpMessageNotReadableException` → 400, `NoResourceFoundException` → 404, `HttpRequestMethodNotSupportedException`
→ 405 (with `Allow` header), `HttpMediaTypeNotSupportedException` → 415, and a catch-all `Exception` → 500
`INTERNAL_ERROR`. `gateway-service` is reactive (Spring Cloud Gateway) and out of scope — same reactive/
servlet boundary fan-platform's D2 task already respected (`libs:java-web-servlet` must never reach a
reactive classpath).

## Design decision 1 — wire shape: **wms's envelope is nested, not flat — this is a harder case than fan's**

`libs/java-web.ErrorResponse` = flat `record (String code, String message, String timestamp)`.

wms-platform's `ApiErrorEnvelope` (one copy per service, all 5 structurally identical) =
`record (ApiError error)` where `ApiError` = `record (String code, String message, Instant timestamp,
Map<String,Object> details, String traceId, String requestId)` — i.e. the entire error body is
**wrapped under a top-level `error` key**, not the flat top-level shape `ErrorResponse` provides.
This is a bigger divergence than fan-platform's `ApiErrorBody` (which was already flat, differing from
`ErrorResponse` only in field count/timestamp type). A wms service directly `extends
CommonGlobalExceptionHandler` and returning its arms' `ResponseEntity<ErrorResponse>` verbatim would
**silently change the top-level JSON shape** for every consumer (console-bff, e2e suites, any other
service reading a wms error body) from `{error:{code,...}}` to `{code,...}` — a real contract break, not
a mechanical swap.

`platform/error-handling.md § Error Response Format`'s "permitted to extend" clause (the same clause
fan-platform's task relied on) does not resolve this by itself here, because the divergence is
structural (wrapping key), not additive (extra field). **This task's implementer must decide, with wms's
own contracts in front of them** (`specs/contracts/http/*-service-api.md` § Error Envelope, all 5 of
which document the `{error:{...}}` shape), between at least:

- (a) do not `extends CommonGlobalExceptionHandler` at all — instead delegate to it internally (compose:
  call a shared static/instance helper for exception→status/code mapping, then wrap the result in wms's
  own `ApiErrorEnvelope` before returning), so the duplicated *mapping logic* goes away but the wire shape
  never changes; or
- (b) `extends` it and override every arm to re-wrap the return value — this defeats most of the
  duplication-reduction purpose the ADR wants; or
- (c) some other design.
- Widening `ErrorResponse` itself to add an `error` wrapper is explicitly **not** an option — that
  library type has other consumers (`iam-platform`, `ecommerce-microservices-platform`, per
  `TASK-FAN-BE-038`'s own blast-radius count) whose flat shape this task has no mandate to change.

This task does **not** pre-decide which option — that decision belongs to whoever implements it, made
against the actual current contracts (per `ADR-MONO-058 § 4`: "D2's wire-shape and status-code conflicts
are real per-project product decisions hiding inside what looks like pure technical duplication —
rushing past them to 'just adopt the lib' would silently change API contracts").

## Design decision 2 — status-code conflict: **measured as smaller than the ADR's summary suggested, verify before assuming**

`CommonGlobalExceptionHandler` maps `MethodArgumentNotValidException`/`IllegalArgumentException` to
**400** by default (with fan-platform's D2 task having already added a `protected HttpStatus
validationFailureStatus()` override hook for the 400-vs-422 split it needed). wms-platform's own
`GlobalExceptionHandler` (verified in `master-service`, representative of all 5) **already maps
`MethodArgumentNotValidException` to 400** — matching the shared handler's default, not diverging from
it the way fan-platform's 422 did. This suggests wms may **not** need the `validationFailureStatus()`
override at all for this arm. **Re-verify this for all 5 services before assuming it holds fleet-wide**
— the ADR's "at least one project (fan-platform) maps validation failures to 422" framing does not claim
wms is also a 422 outlier, and this task's own read of `master-service`'s handler found 400, but a
service-by-service check (not just master) is required before closing this as "no conflict here."

---

# Scope

## In Scope

- Read all 5 services' `GlobalExceptionHandler.java` + `ApiErrorEnvelope.java` in full (this task's
  investigation read `master-service`'s and `outbound-service`'s in detail; `inventory-service`,
  `inbound-service`, `admin-service` must be read equally carefully before implementing — do not assume
  structural identity from 2 of 5 samples) and confirm the arm-by-arm duplication count and the
  wire-shape/status findings above still hold.
- Resolve Design decisions 1 and 2 above as this task's own documented design record (mirroring
  `TASK-FAN-BE-038`'s "Design decision" sections), grounded in wms's actual `specs/contracts/http/*.md`
  error-envelope documentation — not inherited from the ADR's cross-project summary or from fan-platform's
  resolution.
- Whichever resolution is chosen for decision 1, land it as **one atomic PR** covering any shared-lib
  change (if decision 1 requires one, e.g. an additive hook in `CommonGlobalExceptionHandler` analogous
  to fan's `validationFailureStatus()`) plus all 5 wms service adaptations (`CLAUDE.md § Cross-Project
  Changes` — shared-path changes and every consuming project's adaptation land together).
- Each service's `build.gradle` gains `implementation project(':libs:java-web-servlet')` (and
  `libs:java-web` if not already present) if the chosen design pulls the shared type/handler in.
- Tests: rewrite each service's non-domain-arm coverage to drive requests through the real
  `RestControllerAdvice`/dispatcher path (MockMvc or the service's existing controller-slice harness),
  not direct method invocation — so an adoption that silently fails to register an inherited/delegated arm
  is caught (same reachability concern `TASK-FAN-BE-038` AC-7 existed for).
- Spec reconciliation: any `specs/services/<service>/dependencies.md` or `architecture.md § Dependencies`
  that should list `libs:java-web-servlet` (or the composed helper's owning module) once adopted.

## Out of Scope

- **Every other project.** `ADR-MONO-058 § 6` forbids a cross-project mega-PR; erp/scm/fan(done)/finance/
  ecommerce/iam D2 adoption are separate tasks, already filed or to be filed separately.
- **`gateway-service`** — reactive; `libs:java-web-servlet` must never reach a reactive classpath
  (`libs/java-web-servlet/README.md`).
- **`ApiErrorEnvelope`'s top-level `{error:...}` wrapping key itself** — untouched unless the chosen
  design decision explicitly requires it, in which case that is itself a contract change requiring a
  `specs/contracts/` update first, flagged prominently, not a silent side effect.
- Any new error code, or any change to an existing error `code` string.
- ADR-MONO-058 D1 / D3 / D4 / D5 / D6 / D7 / D8 — separate tasks (`D3`/`D4`/`D5`/`D7` filed alongside this
  one as `TASK-BE-568`/`569`/`570`/`571`).

---

# Acceptance Criteria

- [ ] **AC-1 (duplication removed, arm-by-arm)** — for each of the 5 services, the non-domain arms this
      task's investigation found duplicated (`NoResourceFoundException`, `HttpRequestMethodNotSupportedException`,
      `HttpMediaTypeNotSupportedException`, the catch-all `Exception` → 500, and — if decision 2 confirms
      no status conflict — `MethodArgumentNotValidException`) no longer contain independently-hand-written
      mapping logic; they are either inherited from `CommonGlobalExceptionHandler` or delegate to a shared
      helper, per whichever design decision 1 resolution was chosen.
- [ ] **AC-2 (wire shape preserved, proven not assumed)** — a test per service serialises a representative
      error response for each adopted arm and asserts the JSON is still `{"error":{"code":...,"message":...,
      "timestamp":...}}` (optionally `details`/`traceId`/`requestId`), byte-shape-identical to before this
      task, unless the PR body explicitly documents and justifies a contract change (with the corresponding
      `specs/contracts/` edit landed first).
- [ ] **AC-3 (design decisions recorded)** — this task's own Goal-section-style "Design decision 1/2"
      write-up is updated in the merged PR (or task file, per this repo's convention) with the actual
      resolution chosen and the evidence for it (grep counts, contract citations) — not left as the
      open question this ready-task version poses.
- [ ] **AC-4 (status codes preserved unless deliberately changed)** — every existing documented status code
      in `specs/contracts/http/*-service-api.md` for the arms this task touches is either preserved or the
      contract is updated first and the change is called out in the PR body as deliberate.
- [ ] **AC-5 (reachability, not just logic)** — coverage for each service's rewritten non-domain arms
      exercises the real dispatch path (MockMvc/controller-slice), not a direct method call, so a silently
      unregistered arm goes RED.
- [ ] **AC-6 (baseline parity)** — record each of the 5 services' test count before/after. No test may
      disappear. All 5 `:check`/`:test` tasks green, plus `:libs:java-web-servlet:test` if that module is
      touched. wms's CI `Integration` and `E2E` lanes (Testcontainers-backed, authoritative per
      `project_testcontainers_docker_desktop_blocker`) green.
- [ ] **AC-7 (no cross-project leakage)** — if a shared-lib change is required, confirm via
      `libs/java-web-servlet`'s existing consumers outside wms (`iam-platform`'s 4
      `extends CommonGlobalExceptionHandler` subclasses, and fan-platform's 4 post-`TASK-FAN-BE-038`
      subclasses) are unaffected — their own `:check` tasks pass against the modified lib without being
      edited.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load
> `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the
> declared classification (`domain: wms`, `traits: [transactional, integration-heavy]`). Unknown tags are
> a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § D2, § 4, § 6 (ACCEPTED)
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` — the tracking task this splits from.
- `platform/error-handling.md` § Error Response Format, § HTTP Status Code Mapping
- `platform/shared-library-policy.md` § Decision Rule, § Ownership Rule, § Change Rule
- `platform/service-types/rest-api.md` § Error Handling
- `specs/services/{master,inventory,outbound,inbound,admin}-service/architecture.md` § Dependencies
- `specs/contracts/http/{master,inventory,outbound,inbound,admin}-service-api.md` § Error Envelope —
  **read-only inputs, the load-bearing evidence for decision 1**; if implementation finds it cannot
  preserve the documented shape/status, that is a genuine contract change and must update the contract
  first, per `CLAUDE.md`.
- `libs/java-web-servlet/README.md`
- `projects/fan-platform/tasks/done/TASK-FAN-BE-038-adr058-d2-error-envelope-shared-handler-adoption.md`
  — prior art for governance shape only (see Dependency Markers — its concrete wire-shape resolution does
  not transfer to wms).

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`
- `.claude/skills/backend/testing-backend/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/master-service-api.md` § Error Envelope
- `specs/contracts/http/inventory-service-api.md` § Error Envelope
- `specs/contracts/http/outbound-service-api.md` § Error Envelope
- `specs/contracts/http/inbound-service-api.md` § Error Envelope
- `specs/contracts/http/admin-service-api.md` § Error Envelope

All five are **read-only inputs** unless implementation determines a genuine contract change is required
(see Design decision 1) — in which case update the contract first and flag it prominently in the PR body.

---

# Target Service

- `master-service`, `inventory-service`, `outbound-service`, `inbound-service`, `admin-service`
  (wms-platform)
- `libs/java-web-servlet` (shared — only if decision 1's chosen design requires a lib change; atomic,
  same PR if so)

---

# Architecture

Follow each target service's own `architecture.md`. 4 of 5 services are Hexagonal
(`GlobalExceptionHandler` lives in `adapter/in/web/advice`); `admin-service` is Layered per `PROJECT.md`
§ Overrides (`GlobalExceptionHandler` lives in `api/advice`). Neither package location changes as part of
this task.

---

# Implementation Notes

- Read `inventory-service`, `inbound-service`, and `admin-service`'s `GlobalExceptionHandler.java` in full
  before starting — this task's investigation confirmed the pattern in `master-service` and
  `outbound-service` closely (both read in full) but only sampled the other three by class-file existence
  and shared `ApiErrorEnvelope` shape, not a full arm-by-arm read. Re-verify the arm-count table before
  treating it as settled.
- `admin-service` additionally has a `RoleHierarchy`-aware `AccessDeniedException`/
  `AuthenticationCredentialsNotFoundException` handling pattern shared with the other 4 (all 5 have
  these two arms) — these are **not** in `CommonGlobalExceptionHandler` today and must stay service-local
  unless a future task promotes them separately (out of scope here, same posture as fan-platform's
  `DataIntegrityViolationException`/`IllegalStateException` arms staying local).
- `outbound-service`'s `GlobalExceptionHandler` additionally handles
  `DataIntegrityViolationException`/`OptimisticLockingFailureException`/`AuthorizationDeniedException` —
  confirm whether these exist identically in the other 4 before assuming they are outbound-only; if they
  repeat, that is itself evidence for a **future**, separate promotion candidate (log the finding, do not
  scope-creep this task to include it).
- Order of work that keeps the diff reviewable: (1) resolve and document the two design decisions;
  (2) implement the shared-lib change, if any, + its own tests; (3) one wms service end-to-end
  (`inbound-service` looks smallest by arm count from this task's sampling — re-confirm at
  implementation time); (4) replicate to the other four; (5) specs.

---

# Edge Cases

- A wire-shape test that only checks field *names* and not the top-level wrapping key (`error`) would
  pass even if the adoption silently flattened the envelope — AC-2 must assert the full JSON structure,
  not a field subset.
- `admin-service`'s `RoleHierarchy`-based `@PreAuthorize` failures surface as
  `AuthorizationDeniedException`/`AccessDeniedException` — confirm these still resolve to 403 through
  whichever handler owns them after adoption (they are not part of `CommonGlobalExceptionHandler`'s
  today's arm set, so they must remain service-local and correctly ordered relative to any newly-inherited
  arm to avoid Spring's `ExceptionHandlerMethodResolver` "Ambiguous @ExceptionHandler method" boot-time
  failure — see Failure Scenarios).
- `master-service`'s domain-exception dispatch table (`DOMAIN_STATUS`) is service-owned business-rule
  mapping, not a non-domain arm — must not be touched by this task.

---

# Failure Scenarios

- **Silent contract regression via wrapping-key flattening.** If an implementer treats this as a
  mechanical `extends CommonGlobalExceptionHandler` swap without addressing the `{error:{...}}` vs flat
  shape mismatch, every adopted arm's response body silently changes shape for every consumer. AC-2 exists
  specifically to catch this — it must be written and run, not assumed to pass.
- **Boot-time ambiguity.** Two `@ExceptionHandler` methods (one inherited/delegated, one still
  service-local, e.g. for `AccessDeniedException`) mapping the same exception type throws
  `IllegalStateException: Ambiguous @ExceptionHandler method` at context startup, not at compile time —
  caught only by AC-5's real-dispatch-path test, not a direct method call.
- **Widening `ErrorResponse` "just a little."** Adding an `error`-wrapper concept to the shared
  `ErrorResponse` type would change ~39+ files across `iam-platform` and `ecommerce-microservices-platform`
  (per `TASK-FAN-BE-038`'s measured blast radius) that this task has no mandate over — explicitly rejected,
  see Design decision 1.
- **Assuming fan-platform's resolution transfers.** Fan-platform's `validationFailureStatus()` hook and
  "compose, keep `details`" resolution were derived from fan's own flat-envelope, 422-vs-400 facts. wms's
  facts are different (nested envelope; `master-service` already at 400) — re-derive from wms's own
  contracts, do not copy the fan decision wholesale.
- **Scope leak into the other affected projects.** The same pattern exists fleet-wide
  (`ADR-MONO-058 § 1.1`); fixing it outside wms-platform here is explicitly forbidden by `§ 6`.

---

# Test Requirements

- Slice/unit (per service): rewritten non-domain-arm coverage driven through the real dispatch path
  (MockMvc or the service's existing controller-slice harness), covering the arms listed in AC-1.
- New per-service assertion for AC-2 (full JSON shape including the `error` wrapper) and AC-4 (status
  codes unchanged unless deliberately flagged).
- If a shared-lib change lands: unit tests in `libs/java-web-servlet` for the new hook/helper, plus a
  regression run of `iam-platform`'s and fan-platform's `extends CommonGlobalExceptionHandler` consumer
  suites (AC-7).
- All 5 services' `:check`/`:test` green; wms CI `Integration`/`E2E` lanes (Testcontainers) green,
  authoritative over local Windows Docker (`project_testcontainers_docker_desktop_blocker`).

---

# Definition of Done

- [ ] Design decisions 1 and 2 resolved and documented with evidence from wms's own contracts
- [ ] Implementation completed (shared-lib change if any + all 5 wms service adaptations, one atomic PR)
- [ ] Tests passing; per-service before/after counts recorded; no test lost
- [ ] Wire shape and status codes confirmed preserved (or contract updated first, flagged in PR body)
- [ ] `iam-platform`/fan-platform `CommonGlobalExceptionHandler` consumers verified unaffected (if lib
      touched)
- [ ] Specs updated if the chosen design requires it (`dependencies.md`/`architecture.md` shared-libs line)
- [ ] Ready for review
