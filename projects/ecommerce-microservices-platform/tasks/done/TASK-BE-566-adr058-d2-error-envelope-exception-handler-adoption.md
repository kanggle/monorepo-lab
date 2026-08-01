# Task ID

TASK-BE-566

# Title

ADR-MONO-058 D2 — adopt `libs/java-web-servlet.CommonGlobalExceptionHandler` for the non-domain exception-handler tail (10 services)

# Status

done

# Owner

backend

# Task Tags

- code
- refactor
- cleanup

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

`ADR-MONO-058` § 2 D2 found that `libs/java-web.ErrorResponse` and
`libs/java-web-servlet.CommonGlobalExceptionHandler` already exist and already cover
the non-domain exception arms (`NoResourceFoundException`, `NoHandlerFoundException`,
`HttpRequestMethodNotSupportedException`, `HttpMediaTypeNotSupportedException`, the
catch-all `Exception` → 500) that every ecommerce service re-implements by hand in its
own `GlobalExceptionHandler`. This is an **adoption gap**, not a missing type — grep
confirms all 10 REST-facing ecommerce services already `import com.example.web.dto.ErrorResponse`
directly for their response body (`order-service`'s handler is representative:
`com.example.web.dto.ErrorResponse` is imported and used in every `@ExceptionHandler`
return), but none of them extend `CommonGlobalExceptionHandler` — each hand-rolls the
five generic arms alongside its domain-specific ones in a flat `@RestControllerAdvice`
class.

After this task, each of the 10 services' `GlobalExceptionHandler` extends
`CommonGlobalExceptionHandler` and deletes its own copies of the five generic arms,
keeping only its domain-specific `@ExceptionHandler` methods.

## D2's two blockers — status for ecommerce specifically

The ADR flags two blockers that must be resolved as a per-adopting-project design
decision, not silently worked around:

1. **Wire-shape (`details` field) conflict** — **does not apply to ecommerce.**
   Grepped all 10 services' `GlobalExceptionHandler.java` plus their DTO packages: none
   declares a 4th `details: Map<String,Object>` field or a separate `ApiErrorBody` type
   — every service already returns the shared 3-field `com.example.web.dto.ErrorResponse
   {code, message, timestamp}` directly. Nothing to widen or reconcile here.
2. **Status-code (400 vs 422) conflict** — **already resolved at the library level**,
   not something this task needs to design. `CommonGlobalExceptionHandler` already
   ships a `protected HttpStatus validationFailureStatus()` hook (default `400`,
   overridable to `422`) added specifically per this ADR's own D2 section (see the
   class's javadoc, which cites `ADR-MONO-058 § D2` and fan-platform's 422 contracts by
   name). ecommerce's `@Valid`/`MethodArgumentNotValidException`/`IllegalArgumentException`
   arms are already `400` in all 10 services (grepped) — the default is a no-op swap.
   Several services (`promotion-service`, at minimum) use `422 UNPROCESSABLE_ENTITY` for
   **domain-specific** exceptions (e.g. `PromotionQueryService`'s business-rule
   rejections) that are NOT the generic Bean Validation arm this hook covers — those
   handlers are untouched by this task (they stay as the service's own
   `@ExceptionHandler` methods, per Scope below). Confirm per-service during
   implementation that no service's *generic* validation arm is intentionally 422
   before relying on the 400 default silently — none were found to be, but re-verify
   against current code, not this task's snapshot.

---

# Scope

## In Scope

- Change each of these 10 services' `GlobalExceptionHandler` to `extends
  CommonGlobalExceptionHandler`, deleting the now-redundant local
  `@ExceptionHandler` methods for: `MethodArgumentNotValidException`,
  `HttpMessageNotReadableException`, `MissingRequestHeaderException`,
  `MissingServletRequestParameterException`, `IllegalArgumentException` (only if its
  local body is a byte-for-byte behavioral match to the shared arm — see Edge Cases),
  `NoResourceFoundException`, `NoHandlerFoundException`,
  `HttpRequestMethodNotSupportedException`, `HttpMediaTypeNotSupportedException`, and
  the catch-all `Exception` handler:
  - `user-service` (`presentation/exception/GlobalExceptionHandler.java`)
  - `product-service` (`presentation/advice/GlobalExceptionHandler.java`)
  - `search-service` (`adapter/inbound/web/GlobalExceptionHandler.java`)
  - `order-service` (`presentation/GlobalExceptionHandler.java`)
  - `payment-service` (`adapter/in/rest/GlobalExceptionHandler.java`)
  - `promotion-service` (`interfaces/rest/controller/GlobalExceptionHandler.java`)
  - `settlement-service` (`presentation/GlobalExceptionHandler.java`)
  - `shipping-service` (`interfaces/rest/controller/GlobalExceptionHandler.java`)
  - `notification-service` (`adapter/in/rest/GlobalExceptionHandler.java`)
  - `review-service` (`interfaces/advice/GlobalExceptionHandler.java`)
- Add `libs/java-web-servlet` as a declared dependency in each service's `build.gradle`
  where it is not already present (verify — several likely already depend on
  `libs/java-web` for `ErrorResponse`; `java-web-servlet` may be new to some).
- Per-service `validationFailureStatus()` override **only** where grep of that
  service's *own* existing generic-validation arm shows it answering something other
  than 400 (none confirmed as of this task's audit — re-verify at implementation time).
- Update each service's existing `GlobalExceptionHandlerNotFoundTest.java` /
  `GlobalExceptionHandlerDataIntegrityTest.java` (present in most of the 10 — grep
  confirms `NotFoundTest` siblings for `user`, `shipping`, `settlement`, `search`,
  `review`, `product`, `payment`, `order`, `notification`) so they keep passing against
  the inherited behavior — the wire response for each covered exception type must stay
  externally byte-identical (same `code`/`message`/status), since this is a pure
  internal refactor, not a contract change.

## Out of Scope

- `gateway-service` — not in this batch (reactive service type, already has its own
  unified error envelope via `libs/java-gateway` per `PROJECT.md`'s Service Map; D2's
  servlet-side `CommonGlobalExceptionHandler` does not apply there).
- `auth-service` — RETIRED (`TASK-BE-132`), source preserved for history only, not
  built by CI (`TASK-BE-549` precedent: zero observation value in touching dead code).
- `batch-worker`, `web-store` — no REST-facing `GlobalExceptionHandler` (batch-job /
  frontend-app service types).
- Any service's domain-specific `@ExceptionHandler` methods (e.g. `OrderNotFoundException`,
  `DuplicateOrderPlacementException`, `PromotionQueryService`'s 422 business-rule
  arms) — these stay per-service; D2 only promotes the non-domain generic tail.
- Introducing a `details` field to `ErrorResponse` — not needed for ecommerce (see
  Goal § "D2's two blockers").
- Any change to `libs/java-web` / `libs/java-web-servlet` themselves — both classes
  already exist and already ship the `validationFailureStatus()` hook this task needs;
  this is adoption-only, no library change.

---

# Acceptance Criteria

- [ ] All 10 in-scope services' `GlobalExceptionHandler` extends
      `CommonGlobalExceptionHandler`; the five generic arms are no longer
      locally declared in any of the 10.
- [ ] Each service's `build.gradle` declares `libs/java-web-servlet` as a dependency
      (added where missing).
- [ ] Existing `GlobalExceptionHandlerNotFoundTest` / `GlobalExceptionHandlerDataIntegrityTest`
      / equivalent slice tests remain GREEN with no assertion changes (same status
      code, same `code` string, same `message` shape) — a red/changed assertion here
      would mean the swap silently changed a published contract, which this task must
      not do.
- [ ] No service's externally observable error response for any of the five generic
      exception types changes shape or status code as a result of this task (verify by
      diffing the pre-change vs post-change handler behavior per service, not just by
      "it compiles").
- [ ] `./gradlew :projects:ecommerce-microservices-platform:apps:<service>:test` GREEN
      for each of the 10 touched services.
- [ ] Domain-specific `@ExceptionHandler` methods in all 10 services are untouched
      (same method bodies, same status codes) except for the mechanical removal of the
      five generic arms.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read
> `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and
> `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a
> Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § 2
  D2 (the decision + the two blockers this task resolves for ecommerce specifically)
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` (origin — this task
  is one of the per-project splits it required)
- `platform/error-handling.md` (HTTP status code mapping — the same document
  `CommonGlobalExceptionHandler`'s javadoc references for the 400 default)

---

# Related Contracts

- Each of the 10 services' `specs/contracts/http/<service>-api.md` — the generic error
  responses (404/405/415/500/400-validation) documented there must remain byte-identical
  after this task; if any contract doc currently states a different status/shape than
  what the shared handler produces, that is a Hard Stop (HARDSTOP-06/08) requiring a
  contract-vs-code reconciliation decision before implementation, not a silent swap.

---

# Target Service

- `user-service`, `product-service`, `search-service`, `order-service`,
  `payment-service`, `promotion-service`, `settlement-service`, `shipping-service`,
  `notification-service`, `review-service` (10 services, one PR recommended per
  `CLAUDE.md`'s task-driven workflow, or a single PR touching all 10 mechanically
  identical changes — either is acceptable per this repo's "PR 묶음 케이스별 자유"
  convention; keep the diff mechanical and reviewable either way).

---

# Architecture

Follow, per touched service:

- `specs/services/user-service/architecture.md`
- `specs/services/product-service/architecture.md`
- `specs/services/search-service/architecture.md`
- `specs/services/order-service/architecture.md`
- `specs/services/payment-service/architecture.md`
- `specs/services/promotion-service/architecture.md`
- `specs/services/settlement-service/architecture.md`
- `specs/services/shipping-service/architecture.md`
- `specs/services/notification-service/architecture.md`
- `specs/services/review-service/architecture.md`

---

# Implementation Notes

- `CommonGlobalExceptionHandler` (`libs/java-web-servlet/src/main/java/com/example/web/exception/CommonGlobalExceptionHandler.java`)
  is `public abstract class ... { ... }` with `@ExceptionHandler`-annotated methods —
  a service's `GlobalExceptionHandler` becomes `@RestControllerAdvice public class
  GlobalExceptionHandler extends CommonGlobalExceptionHandler { /* domain arms only */ }`.
  Spring resolves `@ExceptionHandler` methods from superclasses normally; no special
  wiring needed beyond the `extends`.
- Order-service's current handler is representative of the pattern across all 10 —
  read `apps/order-service/src/main/java/com/example/order/presentation/GlobalExceptionHandler.java`
  as the worked example before starting the other 9; its generic arms
  (`NoResourceFoundException`, `NoHandlerFoundException`,
  `HttpRequestMethodNotSupportedException`, `HttpMediaTypeNotSupportedException`, the
  catch-all) are already byte-identical in message text to `CommonGlobalExceptionHandler`'s
  versions, so the swap should be a pure deletion for those four arms in most services
  — diff each service's local arm against the shared one before deleting, since a
  service with divergent message text would be a (silent, undocumented) contract change
  if collapsed without notice.
- `DataIntegrityViolationException` handling (present in `order-service` and others,
  routing unique-violation → 409 vs other integrity errors → 500 via
  `com.example.common.persistence.DataIntegrityViolations.isUniqueViolation`) is
  **not** one of `CommonGlobalExceptionHandler`'s covered arms — it stays as each
  service's own handler, unaffected by this task.
- `ObjectOptimisticLockingFailureException` **is** covered by the shared handler (409
  CONFLICT) — several services (`order-service`) already hand-roll an equivalent arm
  for `OptimisticLockingFailureException` (note: different exception type,
  `org.springframework.dao.OptimisticLockingFailureException` vs
  `org.springframework.orm.ObjectOptimisticLockingFailureException` — verify which
  one each service's persistence layer actually throws before assuming the shared
  arm's coverage is a drop-in match; JPA typically throws the `orm` subtype).

---

# Edge Cases

- A service whose local generic-arm message text differs from
  `CommonGlobalExceptionHandler`'s (e.g. a custom 404 message instead of "The
  requested resource was not found") is an undocumented micro-contract difference —
  collapsing it into the shared arm silently changes client-visible text. Diff each
  arm before deleting; if a service's contract doc pins the exact message text,
  keep that service's local override (do not force uniformity where a contract
  requires divergence).
- `IllegalArgumentException` handling varies more than the other four arms across
  services — `order-service`'s local arm returns `INVALID_ORDER_REQUEST` (a
  domain-flavored code), not the shared handler's generic `VALIDATION_ERROR`. Do
  **not** delete a service's `IllegalArgumentException` arm if its `code` string
  differs from the shared handler's — that is effectively domain-specific framing
  riding on a generic exception type, and collapsing it would change the wire `code`.
- A service using JPA's `ObjectOptimisticLockingFailureException` under a *different*
  local `code` string than the shared handler's `"CONFLICT"` (verify per service) must
  either keep its own arm or accept the code-string change as an explicit, documented
  contract update (update the contract doc, do not silently drift it).

---

# Failure Scenarios

- Deleting a generic arm whose local message/status diverges from the shared
  handler's, without noticing, silently changes a published error contract for
  existing clients — exactly the risk `ADR-MONO-058 § 4` names as this ADR's own
  "Negative / risks" for D2. Diff before delete, not after.
- Adding `libs/java-web-servlet` as a dependency without verifying it does not also
  transitively pull in something that perturbs a service's existing security chain
  or bean wiring (the library is exception-handling-only and framework-light, but
  verify per `platform/shared-library-policy.md`'s Dependency Rule before assuming).

---

# Test Requirements

- Existing `GlobalExceptionHandlerNotFoundTest.java` / `GlobalExceptionHandlerDataIntegrityTest.java`
  / `GlobalExceptionHandlerTest.java` (naming varies per service — see Scope's file
  list) must stay GREEN with unchanged assertions.
- No new test types required — this is a refactor preserving existing externally
  observable behavior; if any existing test needs an assertion changed to pass, that
  is a signal the refactor accidentally changed behavior — investigate before
  "fixing" the test.

---

# Definition of Done

- [ ] All 10 services' `GlobalExceptionHandler` extends `CommonGlobalExceptionHandler`
- [ ] Generic arms removed from all 10 (except explicitly-diverged arms kept per
      Edge Cases, with the divergence noted in a code comment)
- [ ] `build.gradle` dependency added where missing
- [ ] All 10 services' existing exception-handler tests GREEN, unchanged assertions
- [ ] Contracts verified unchanged (or updated, if an intentional divergence was
      found and accepted as a contract update — not expected, but possible)
- [ ] Ready for review
