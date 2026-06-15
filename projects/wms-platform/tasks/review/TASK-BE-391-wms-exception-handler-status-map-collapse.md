# TASK-BE-391 — Collapse WMS per-entity exception handlers to a `Class → HttpStatus` map (behavior-preserving)

**Status:** review
**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (mechanical, contained refactor — but status-mapping exactness is load-bearing)

> **IMPLEMENTED (review).** All 5 WMS `GlobalExceptionHandler`s collapsed their per-concrete-domain-exception `@ExceptionHandler` methods into a single `@ExceptionHandler(<Service>DomainException.class)` dispatcher backed by a `Map<Class<?>, HttpStatus>`, preserving exact status + body. **Two default-status variants kept verbatim**: master/admin → 500 + "unmapped domain exception" warn (domain exceptions are explicitly mapped; unmapped = bug); inbound/outbound/inventory → 422 (business-rule default; the table lists only the 404/409/400 overrides). Special framework handlers (bean-validation, AccessDenied/Auth, MissingCredentials, NoResource, OptimisticLock, DataIntegrity, outbound `ExternalServiceUnavailable` 503, the `Exception` fallback) left **untouched**. inventory's explicit-422 handlers (TransferSameLocation/StateTransition/ReservationQtyMismatch/InsufficientStock/MasterRefInactive) were redundant with the 422 default → collapsed away (their imports dropped). inbound `GlobalExceptionHandlerTest` rerouted its 5 direct per-type-method calls to the dispatcher (same assertions). **Net −153 LOC** across the 5 handlers; no domain/contract/code-behavior change. **Verified**: `:test` GREEN for all 5 services (Docker-free; the H2 web-slice tests exercise the mappings via Spring dispatch). Testcontainers `@Tag("integration")` web ITs are CI-Linux-authoritative.

---

## Goal

Reduce the repeated per-entity `@ExceptionHandler` boilerplate in the 5 WMS `GlobalExceptionHandler`s (master / admin / inbound / outbound / inventory) without changing any HTTP behavior. Each handler currently declares one 3–4-line `@ExceptionHandler` method per concrete domain exception (e.g. `WarehouseNotFoundException → 404`, `WarehouseCodeDuplicateException → 409`), all delegating to the same `build(status, ex)`. The only thing that varies per method is `(exceptionClass → HttpStatus)`. Centralize that into a single `@ExceptionHandler(<Service>DomainException.class)` dispatcher backed by an explicit `Map<Class<? extends …DomainException>, HttpStatus>`. **Pure adapter-layer change** — no domain hierarchy change, no contract change, no status change.

This is the F3 finding from the 2026-06-15 WMS code-refactor recon (codebase otherwise verified clean — no layering violations, no god-methods, no dead code; F1 idempotency-filter + F4 envelope-header dedup deferred as shared-lib cross-project efforts; F2 declined under HARDSTOP-03).

## Scope

For each of the 5 services' `GlobalExceptionHandler`:

1. Replace the granular per-concrete-domain-exception `@ExceptionHandler` methods with **one** `@ExceptionHandler(<Service>DomainException.class)` method that resolves the status via a static `Map<Class<?>, HttpStatus>` (default `INTERNAL_SERVER_ERROR` + the existing "unmapped domain exception" `log.warn`, preserving the old catch-all behavior).
2. The map carries the **exact** current mappings, including the documented special cases (e.g. master `InvalidStateTransitionException`/`ImmutableFieldException → 422`, `ReferenceIntegrityViolationException`/`ConcurrencyConflictException → 409`, `ValidationException → 400`). Move the per-mapping rationale comments onto the map entries verbatim.
3. **Leave untouched** every non-domain-exception handler (bean validation `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, `MissingServletRequestParameterException`, `MethodArgumentTypeMismatchException`, `AccessDeniedException`, `AuthenticationCredentialsNotFoundException`, `NoResourceFoundException`, `DataScopeForbiddenException`, the `Exception` fallback, etc.) — they have bespoke bodies/statuses and are out of scope.

## Acceptance Criteria

- **AC-1** Every concrete domain exception in each service maps to the **same** HTTP status + same `ApiErrorEnvelope.of(code, message)` body as before (verified by the existing handler unit/web tests staying green, unchanged).
- **AC-2** The single domain-exception dispatcher returns `INTERNAL_SERVER_ERROR` + the "unmapped domain exception" warn log for any domain exception not in the map (same as the prior `@ExceptionHandler(<Service>DomainException.class)` catch-all).
- **AC-3** No change to: domain exception classes, `ApiErrorEnvelope`, contracts (`specs/contracts/http/*`), error codes, or the non-domain handlers.
- **AC-4** Net LOC reduction across the 5 handlers; no new dependency.
- **AC-5** `:test` GREEN for all 5 services (Docker-free). Testcontainers web/IT layer is CI-Linux-authoritative.

## Related Specs

- `platform/error-handling.md` (status taxonomy — `STATE_TRANSITION_INVALID → 422` etc., must be preserved)
- `projects/wms-platform/specs/contracts/http/master-service-api.md` (e.g. `REFERENCE_INTEGRITY_VIOLATION → 409` — preserved)
- `projects/wms-platform/specs/services/<service>/architecture.md` (each handler is in the `adapter/in/web/advice` inbound-web adapter; admin-service is the documented Layered exception — its handler lives at `api/advice`)

## Related Contracts

- No contract change — the error envelope shape, codes, and statuses are all preserved. This is an internal adapter restructuring.

## Edge Cases

- **Most-specific dispatch removed** — Spring currently picks the specific `@ExceptionHandler(WarehouseNotFoundException.class)` over the `@ExceptionHandler(MasterDomainException.class)` catch-all. After collapse, all subtypes route to the single dispatcher; the map keyed on `ex.getClass()` (exact concrete class) reproduces the same status. Verify each concrete exception is a **direct** subclass of the service domain base (no intermediate that would break exact-class lookup).
- **Unmapped domain exception** — must still log + 500 (AC-2), not silently 200/NPE.
- **admin-service Layered + different package** (`com.wms.admin.api.advice`) — same collapse, mind the package.
- A service whose handler maps a domain exception to a **non-default** status the others don't share (e.g. inventory-specific) must keep that exact entry.

## Failure Scenarios

- **F1 — a dropped or wrong map entry** silently changes a status (e.g. 404→500). Guarded by AC-1 + the existing per-type handler tests (kept unchanged): a wrong mapping flips a test red.
- **F2 — accidentally collapsing a non-domain handler** (e.g. folding `AccessDeniedException` into the domain map) would break 403/401/400 framework mappings. Guarded by the explicit out-of-scope list (scope item 3).
- **F3 — touching the domain exception hierarchy** (re-parenting) would widen blast radius beyond the adapter. Out of scope by design — the map lives in the adapter handler; domain stays HTTP-agnostic.
