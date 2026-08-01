# Task ID

TASK-SCM-BE-055

# Title

Adopt ADR-MONO-058 D2 — error envelope + generic exception-handler tail (`libs/java-web.ErrorResponse` / `libs/java-web-servlet.CommonGlobalExceptionHandler`)

# Status

done

# Owner

backend

# Task Tags

- code

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

`ADR-MONO-058` (ACCEPTED 2026-07-30) § 2 D2 found `ApiErrorBody`/`ApiEnvelope` + the non-domain half of `GlobalExceptionHandler` duplicated across all 8 projects, with `libs/java-web.ErrorResponse` and `libs/java-web-servlet.CommonGlobalExceptionHandler` already covering the same generic arms (`NoResourceFound`, `HttpMediaTypeNotSupported`, `HttpRequestMethodNotSupported`, the catch-all 500) unimported. Adopt the shared types across scm-platform's four servlet REST services, resolving the ADR's two flagged conflicts as **scm's own design decision** rather than a silent mechanical swap (§ 4: "rushing past them to 'just adopt the lib' would silently change API contracts for existing clients").

---

# Scope

## In Scope

Grep across `projects/scm-platform/apps/` (2026-07-31) confirms local `ApiErrorBody` + `GlobalExceptionHandler` pairs in exactly four services — `gateway-service` has neither (reactive edge gateway, no domain exception handling of this shape):

| Service | `ApiErrorBody` shape (current) |
|---|---|
| `procurement-service` | `record ApiErrorBody(String code, String message, Map<String,Object> details, Instant timestamp)` |
| `inventory-visibility-service` | `record ApiErrorBody(String code, String message, Map<String,Object> details, String timestamp)` |
| `logistics-service` | `record ApiErrorBody(String code, String message)` — no `details`, no `timestamp` |
| `demand-planning-service` | `record ApiErrorBody(String code, String message)` — no `details`, no `timestamp` |

For each of these four services: replace the local `ApiErrorBody` + the **generic (non-domain)** tail of the local `GlobalExceptionHandler` (`NoResourceFoundException`/`NoHandlerFoundException`, `HttpMediaTypeNotSupportedException`, `HttpRequestMethodNotSupportedException`, `HttpMessageNotReadableException`, missing-header/-parameter, the catch-all `Exception.class` handler) by extending `libs/java-web-servlet.CommonGlobalExceptionHandler` and adopting `libs/java-web.ErrorResponse`. Each service's **domain-specific** exception handlers (e.g. procurement's `PoNotFoundException`/`PoStatusTransitionInvalidException`/etc.) stay local, unchanged in shape — only the generic tail moves.

**Status-code conflict (already partially resolved in the shared library — verify, don't re-litigate):** `CommonGlobalExceptionHandler` already exposes a `protected HttpStatus validationFailureStatus()` override hook (added citing `ADR-MONO-058 § D2`, confirmed present as of 2026-07-31), defaulting to `400 Bad Request` and covering the `@Valid`/`IllegalArgumentException` arms. `procurement-service`'s own `GlobalExceptionHandler` javadoc documents `422` for `VALIDATION_ERROR` as scm's domain convention (`rules/domains/scm.md § Standard Error Codes` — see `PO_STATUS_TRANSITION_INVALID`, `422`), matching what `logistics-service`/`demand-planning-service`/`inventory-visibility-service` already do for their own `VALIDATION_ERROR` mappings (confirmed by reading each `GlobalExceptionHandler`). **Every scm service overrides `validationFailureStatus()` to return `422`** — this is a straightforward apply, not an open design question, since scm's domain-level error-code convention already documents 422 uniformly.

**Wire-shape conflict (genuinely open — this task must decide and record it):** `libs/java-web.ErrorResponse` is currently `{code, message, timestamp: String}` — no `details` field. Two of scm's four services (`procurement`, `inventory-visibility`) actively populate `details` today (e.g. `PO_STATUS_TRANSITION_INVALID` carries `from`/`to`/`actor`); the other two (`logistics`, `demand-planning`) have never populated it. This task must choose and document one of:

1. **Widen `libs/java-web.ErrorResponse`** with an optional/nullable `details: Map<String,Object>` field (a shared-library change, additive, non-breaking for the two services that don't use it) — the `ErrorResponse` field-widening is itself shared-library content and must be reviewed against `platform/shared-library-policy.md § Change Rule` before landing, same as `TASK-MONO-500`/`-501`'s own gate, since it changes a type in `libs/`.
2. **Compose a scm-specific envelope** around the shared base (e.g. a `ScmErrorResponse` wrapping `ErrorResponse` plus scm's own `details`) — keeps `libs/java-web` untouched but reintroduces a scm-local wrapper type, and if adopted, `procurement`/`inventory-visibility` still diverge from each other on `timestamp` type (`Instant` vs `String`) unless normalized to `ErrorResponse`'s existing `String` shape as part of this same change.

The ADR (§ 2 D2, § 5) explicitly defers this choice to "whoever implements D2, as a small design note in that implementation's own task" — this task's Implementation Notes section is where that decision must be recorded, with the rationale, before touching any service.

## Out of Scope

- `gateway-service` — grep-confirmed to have no `ApiErrorBody`/`GlobalExceptionHandler` of this shape; nothing to adopt.
- D1, D3, D4, D5 — filed as separate tasks (`TASK-SCM-BE-054`, `-056`, `-057`).
- Any domain-specific exception-handler method (e.g. `handlePoNotFound`, `handleAsnOverreceipt`) — these stay local; only the generic/non-domain tail adopts the shared base.
- Changing which HTTP status any *domain* exception maps to (e.g. `409` for `CONCURRENT_MODIFICATION`, `503` for `SUPPLIER_UNAVAILABLE`) — untouched, only the generic-arm mapping and the `@Valid`/`IllegalArgumentException` status (via the existing `validationFailureStatus()` hook) are in scope.

---

# Acceptance Criteria

- [ ] The `details`-field wire-shape design decision (widen `ErrorResponse` vs. compose a scm wrapper) is explicitly recorded in this task's own PR description before any service is touched, with the rationale for the chosen option.
- [ ] If option 1 (widen `ErrorResponse`) is chosen: the `libs/java-web` change is additive/non-breaking (existing consumers of the 3-field shape unaffected), and is itself gated by `platform/shared-library-policy.md § Change Rule` — flag for review the same way `TASK-MONO-500`/`-501` were filed as their own reviewable units, even though this task doesn't require a separate root task (this is a small additive field, not a new type).
- [ ] All four services (`procurement`, `logistics`, `inventory-visibility`, `demand-planning`) extend `CommonGlobalExceptionHandler` for the generic tail; each overrides `validationFailureStatus()` to return `422 UNPROCESSABLE_ENTITY`.
- [ ] Each service's domain-specific exception handlers are unmodified in behavior (same status codes, same error codes, same messages) — only the generic-arm plumbing changes.
- [ ] `procurement-service`'s `PO_STATUS_TRANSITION_INVALID` response (and any other `details`-carrying error) still carries `from`/`to`/`actor` in its response body after adoption, regardless of which wire-shape option is chosen.
- [ ] `GlobalExceptionHandlerTest`/`GlobalExceptionHandlerNotFoundTest` (present in `procurement-service`, `inventory-visibility-service`, `demand-planning-service`) are updated to assert against the new shape where the wire format genuinely changes, and pass; where behavior is unchanged, assertions are unchanged.
- [ ] `projects/scm-platform/specs/contracts/http/{procurement-api,inventory-visibility-api,demand-planning-api}.md` are checked for a documented error-response shape and updated if this task's chosen wire-shape option changes any published field (per `CLAUDE.md` "Specs win over tasks... update them first").
- [ ] scm-platform Build & Test CI lane GREEN for all four touched services.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § 2 D2, § 4 (contract-decision framing), § 6 item 6
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` (origin)
- `platform/shared-library-policy.md` (Decision Rule, Change Rule — relevant if the `ErrorResponse` widening option is chosen)
- `platform/error-handling.md` (§ HTTP Status Code Mapping, referenced directly by `CommonGlobalExceptionHandler`'s own javadoc)
- `rules/domains/scm.md` § Standard Error Codes (scm's own 422-for-VALIDATION_ERROR domain convention, already documented independently of this ADR)
- `projects/scm-platform/specs/services/procurement-service/architecture.md`
- `projects/scm-platform/specs/services/logistics-service/architecture.md`
- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md`
- `projects/scm-platform/specs/services/demand-planning-service/architecture.md`

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

- `projects/scm-platform/specs/contracts/http/procurement-api.md`
- `projects/scm-platform/specs/contracts/http/inventory-visibility-api.md`
- `projects/scm-platform/specs/contracts/http/demand-planning-api.md`
- `projects/scm-platform/specs/contracts/http/gateway-public-routes.md` — **platform-console external read consumer**: per `PROJECT.md § IAM IdP Integration`, `platform-console-web` consumes scm's read surface (procurement PO read + inventory-visibility) server-side. If the chosen wire-shape option changes the error-response shape for any endpoint platform-console proxies, check that consumer before merging, not after.
- `logistics-service` has no published HTTP contract carrying its `ApiErrorBody` shape beyond internal use — verify against `specs/services/logistics-service/architecture.md` before assuming no external consumer.

---

# Target Service

- `procurement-service`, `logistics-service`, `inventory-visibility-service`, `demand-planning-service`
- `gateway-service` — explicitly out of scope (no local error-envelope duplication found)

---

# Architecture

Follow each touched service's own architecture doc (listed under Related Specs above).

---

# Implementation Notes

**Record the wire-shape decision here once made** (this task's own design call, per the ADR's explicit deferral):

- Evidence favors option 1 (widen `ErrorResponse` with an optional `details`) over option 2 (a scm-local wrapper): 2 of scm's 4 services already need `details` today, and a scm-local wrapper would still need to solve `procurement` (`Instant`) vs `inventory-visibility` (`String`) `timestamp`-type divergence internally — the shared type already standardizes on `String`, which both services can conform to without losing information (`Instant.toString()` round-trips). This is a recommendation for the implementer to weigh, not a pre-decided outcome — the ADR explicitly leaves the final call to whoever picks this task up, with the actual consumers (platform-console) in front of them.
- If option 1 is chosen, the `libs/java-web.ErrorResponse` change itself is small (one additive nullable field + a new `of(code, message, details)` factory overload) but is still a `libs/` touch — read `platform/shared-library-policy.md § Change Rule` before landing it in the same PR as the four service adoptions, and confirm it doesn't require its own separate root-level task (a genuinely additive, backward-compatible field change on an already-shared type is a smaller step than the D4/D6 promotions and does not need a new type or module, but re-verify this judgment against the policy text before proceeding).
- `logistics-service`/`demand-planning-service` currently have zero `details` usage — confirm this task does not introduce new `details` payloads for them as a side effect of the adoption; they should keep emitting 2-field-equivalent bodies (`details` absent/null) unless a genuine need surfaces.
- `CommonGlobalExceptionHandler.validationFailureStatus()`'s javadoc already documents the override pattern with a code sample — follow it directly rather than re-deriving.

---

# Edge Cases

- `procurement-service`'s `ResponseStatusException` pass-through handler (for inline webhook signature failures) and its `handleResponseStatus` code-mapping (`401`→`UNAUTHORIZED`, `403`→`PERMISSION_DENIED`, else `REQUEST_ERROR`) is domain/service-specific plumbing, not part of `CommonGlobalExceptionHandler`'s generic tail — must stay local, verify it isn't accidentally shadowed by extending the shared base class.
- `procurement-service`'s `DataIntegrityViolationException` handler (unique-violation → 409 `CONFLICT` vs. FK/NOT NULL/CHECK → 500 `INTERNAL_ERROR`, from `TASK-MONO-450`) is domain-specific runtime discrimination logic — stays local, unaffected by this adoption.
- If the `ErrorResponse` widening is chosen, verify no other project's already-landed D2 adoption (fan-platform, per the `validationFailureStatus()` hook's existing presence) silently regresses — the hook's javadoc explicitly names fan-platform's three HTTP contracts as already relying on the 422 override; a same-file field addition should not touch that behavior, but verify `libs/java-web`'s own test suite still passes.

---

# Failure Scenarios

- Silently choosing the `details`-widening approach without documenting the design call (or without checking `shared-library-policy.md § Change Rule`) would repeat the exact failure the ADR calls out in § 4 — a wire-format decision disguised as pure technical duplication. Hard Stop if the decision isn't recorded in the PR before implementation.
- Mapping `@Valid`/`IllegalArgumentException` failures to `400` (the shared default) instead of overriding to `422` would silently break every existing scm client relying on the documented `422` convention (`rules/domains/scm.md`) — verify the override is applied in all four services, not assumed inherited.
- Dropping `details` from `procurement-service`'s `PO_STATUS_TRANSITION_INVALID` response (or any other `details`-carrying error) during adoption would be an undisclosed contract regression for any consumer parsing `from`/`to`/`actor` — verify via `GlobalExceptionHandlerTest` before merging.
- Changing the inventory-visibility or procurement error-response shape without checking platform-console's BFF proxy for that read surface would risk an undisclosed break for the external operator-read consumer — check `gateway-public-routes.md` and platform-console's own proxy code before merging if the shape actually changes.

---

# Test Requirements

- Existing `GlobalExceptionHandlerTest`/`GlobalExceptionHandlerNotFoundTest` in `procurement-service`, `inventory-visibility-service`, `demand-planning-service` updated only where the wire shape genuinely changes (per the recorded design decision), all passing.
- `logistics-service` gets equivalent generic-tail coverage added if none currently exists for the arms `CommonGlobalExceptionHandler` newly covers (confirm current coverage first — do not assume it's untested).
- No change to any domain-specific exception-handler test.

---

# Definition of Done

- [ ] Wire-shape design decision recorded and implemented consistently across all four services
- [ ] `validationFailureStatus()` overridden to `422` in all four services
- [ ] Domain-specific exception handlers unmodified in behavior
- [ ] Contracts (`procurement-api.md`, `inventory-visibility-api.md`, `demand-planning-api.md`) updated if the wire shape changed
- [ ] platform-console external-consumer impact checked if the wire shape changed
- [ ] scm-platform Build & Test CI lane GREEN for all four touched services
- [ ] Task moved `ready → done`, referencing `TASK-MONO-495` as origin
