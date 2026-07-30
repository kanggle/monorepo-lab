# Task ID

TASK-FAN-BE-038

# Title

ADR-MONO-058 D2 (fan-platform only) — adopt `libs/java-web-servlet.CommonGlobalExceptionHandler` + `libs/java-web.ErrorResponse` in the 4 fan services, and resolve D2's two deferred blockers

# Status

done

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

Close fan-platform's share of the **adoption gap** recorded as `ADR-MONO-058 § D2` (ACCEPTED 2026-07-30).
`libs/java-web.ErrorResponse` and `libs/java-web-servlet.CommonGlobalExceptionHandler` already ship the
non-domain arms that each of fan-platform's four servlet services re-implements in a service-local
`AbstractDomainExceptionHandler`. This is **not a new extraction** — no new shared type is created.

Measured against the tree (not the ADR's cross-project paraphrase): `AbstractDomainExceptionHandler`
exists in **four** near-identical copies —
`community-service` (118 lines), `artist-service` (118), `membership-service` (124),
`notification-service` (115) — carrying **11–12 `@ExceptionHandler` methods each**, of which
**7 are byte-equivalent to arms the shared base already implements**
(`MethodArgumentNotValidException`, `IllegalArgumentException`, `NoResourceFoundException`,
`NoHandlerFoundException`, `HttpRequestMethodNotSupportedException` incl. the RFC 7231 `Allow` header,
`HttpMediaTypeNotSupportedException`, and the catch-all `Exception` → 500), plus
`ObjectOptimisticLockingFailureException` (also in the base) and — in `membership-service` only —
`MissingRequestHeaderException` (also in the base).

`ADR-MONO-058 § D2` explicitly defers two blockers to this implementation. Both are **resolved below with
evidence read off the code and the contracts**, not off the ADR's summary; § 6 of the ADR forbids folding
this into a cross-project mega-PR, so this task is **fan-platform only** (the other four projects the audit
found the same pattern in are separate future tasks).

---

## Design decision 1 — wire shape (`details`): **do NOT widen the shared `ErrorResponse`; fan-platform composes**

**What the code actually shows** (the ADR's summary said "several services carry a 4th field"; recounted here):

- `libs/java-web.ErrorResponse` = `record (String code, String message, String timestamp)`, timestamp
  produced as `Instant.now().toString()`.
- fan-platform's `ApiErrorBody` (one copy per service, all four identical) =
  `record (String code, String message, Map<String,Object> details, Instant timestamp)` annotated
  `@JsonInclude(NON_NULL)`.
- `details` is a **documented contract field**, not an incidental extra:
  - `specs/contracts/http/community-api.md` — error envelope example carries `"details"`;
    `403 MEMBERSHIP_REQUIRED` documents `details.requiredTier`;
    `422 POST_STATUS_TRANSITION_INVALID` documents `details {from,to,actor}`.
  - `specs/contracts/http/artist-api.md` — `422 STATE_TRANSITION_INVALID` documents `details.from`,
    `details.to`.
  - `specs/contracts/http/membership-api.md` — error envelope example carries `"details"`.

**Blast radius of widening `ErrorResponse`** (measured, `com.example.web.dto.ErrorResponse` repo-wide):
**39 files** across **two other projects** — `iam-platform` (account/admin/auth/security/gateway) and
`ecommerce-microservices-platform` (~13 services + gateway) — plus the lib's own test. Adding a 4th record
component changes the record's canonical constructor arity for every one of them and puts a new key in the
serialized error contract of ~20 services in projects this task has no mandate over. A fan-platform-only
adoption must not change what two other projects already rely on.

**Resolution.** `platform/error-handling.md § Error Response Format` already settles it in fan-platform's
favour without touching `libs/`:

> "Services that return additional context (trace/request ids, structured `details`) are permitted to
> **extend** this envelope, but the three fields above must always be present."

So: the shared `ErrorResponse` is the **base envelope** and fan-platform's `ApiErrorBody` is retained
**strictly as the platform-sanctioned `details`-carrying extension**. Concretely:

- every handler arm that does **not** carry `details` returns the shared `ErrorResponse`;
- only the **four** arms whose contract documents `details` keep `ApiErrorBody`
  (community `MEMBERSHIP_REQUIRED` + `POST_STATUS_TRANSITION_INVALID`, artist `STATE_TRANSITION_INVALID`,
  membership `MEMBERSHIP_STATE_INVALID`);
- `notification-service` has **zero** `details` arms → its `ApiErrorBody` is deleted outright;
- the 2-argument `ApiErrorBody.of(code, message)` factory is removed from the three survivors so the type
  cannot silently re-become a full duplicate of `ErrorResponse`.

**Why this is wire-preserving — and the assumption that turned out to be false.** A `details`-less
`ApiErrorBody` serialises to `{code, message, timestamp}` — `details` is dropped by
`@JsonInclude(NON_NULL)` — which is exactly `ErrorResponse`'s shape. The remaining difference was the
`timestamp` *field type*: `ApiErrorBody` held an `Instant`, `ErrorResponse` holds a pre-formatted
`String`.

The first draft of this task asserted those were wire-equivalent because "Jackson serialises `Instant` as
ISO-8601". **AC-6 was written to verify that rather than assume it, and it failed — the assumption is
wrong.** Measured:

- A bare `ObjectMapper`, and even `Jackson2ObjectMapperBuilder.json().build()` (spring-web), emit
  `"timestamp": 1.785370282333E9`. ISO-8601 text is **not** a Jackson or a spring-web default.
- Only Spring Boot's `JacksonAutoConfiguration` disables `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS`.
- **artist-service does not get that.** `config/RedisCacheConfig` contributes an `ObjectMapper` `@Bean`
  and Boot's is `@ConditionalOnMissingBean`, so Boot's backs off. Probed against the real config
  (`ApplicationContextRunner` + `RedisCacheConfig` + `JacksonAutoConfiguration`): a single `ObjectMapper`
  bean named `objectMapper`, rendering `Instant` as `1785370282.333000000`.

So **artist-service has been emitting a numeric error `timestamp`** — contradicting
`platform/error-handling.md` ("timestamp: string (ISO 8601)"), `artist-api.md`'s own envelope example
(`"timestamp": "2026-05-03T00:00:00Z"`), and the frontend's `ApiErrorBody.timestamp?: string`
(`web/fan-platform-web/src/shared/api/errors.ts`). Had the arms simply been moved to `ErrorResponse`,
artist would have ended up with ISO strings on 11 arms and a number on the 12th.

**Resolution:** change `ApiErrorBody.timestamp` from `Instant` to a pre-formatted `String`
(`Instant.now().toString()`) in all three surviving copies. The envelope then equals `ErrorResponse` **by
construction rather than by ObjectMapper configuration**, and AC-6 asserts it under both a Boot-configured
mapper and a bare one. Consequences:

- community / membership / notification — **no observable change** (their Boot-configured mapper already
  emitted the identical ISO string).
- artist-service — `timestamp` changes from a JSON **number** to an ISO-8601 **string** on every error
  response. This is a **fix of a pre-existing defect**, moving the service *onto* its already-documented
  contract, so `specs/contracts/` needs no edit — but it is an observable change, is flagged in the PR
  body, and is pinned by `envelopeTimestampIsIsoStringUnderArtistsOwnObjectMapper`, which drives artist's
  real (shadowing) mapper rather than a hand-built one.
- **Out of scope, recorded as a separate finding:** the `RedisCacheConfig` mapper shadowing itself. It
  still affects any other artist response DTO holding a raw `java.time` value and the Redis
  directory-cache payload. The Kafka event contract is *not* affected — `ArtistEventPublisherAdapter`
  already pre-formats `occurredAt` with `.toString()` — but repairing the mapper would change the cache
  payload format, a different change with a different blast radius that deserves its own task.

## Design decision 2 — status conflict (422 vs 400): **add one minimal, backward-compatible hook to the shared handler**

**Verified, not inherited from the ADR:**

| exception | fan-platform today | `CommonGlobalExceptionHandler` today | fan contract |
|---|---|---|---|
| `MethodArgumentNotValidException` | **422** `VALIDATION_ERROR` | **400** `VALIDATION_ERROR` | community-api L47 / membership-api L55 / artist-api L65 all say **422** for `@Valid` |
| `IllegalArgumentException` | **422** `VALIDATION_ERROR` | **400** `VALIDATION_ERROR` | same 422 family |
| `HttpMessageNotReadableException` | community/membership **400**; **artist 422** | **400** | artist-api L55: "422 VALIDATION_ERROR — malformed JSON / unknown enum value" (deliberate, pinned by `ArtistGroupControllerSliceTest`) |

`CommonGlobalExceptionHandler` today has **no** override mechanism — both statuses are hard-coded
`HttpStatus.BAD_REQUEST`. The ADR requires one ("the shared handler must expose this as a
configurable/overridable mapping"), so adding it is in scope for D2.

**Resolution — the smallest thing that works:** add a single `protected HttpStatus
validationFailureStatus()` returning `HttpStatus.BAD_REQUEST`, consumed by exactly the two arms above
(`handleValidation`, `handleIllegalArgument`). Not a property (an error status is a per-service contract
decision, not a deployment knob), not a generic type parameter (that was `TASK-FIN-BE-058`'s rejected
Option B — it breaks all four `iam-platform` subclasses for no gain here).

- **Backward compatibility, measured:** the only `extends CommonGlobalExceptionHandler` consumers repo-wide
  are four `iam-platform` classes — `account-service.GlobalExceptionHandler`,
  `admin-service.AdminExceptionHandler`, `auth-service.AuthExceptionHandler`,
  `security-service.QueryExceptionHandler`. None overrides the hook, so all four keep 400 unchanged, and
  the existing `CommonGlobalExceptionHandlerTest` 400 assertions must pass **unmodified**.
- fan-platform overrides the hook once per service (in `AbstractDomainExceptionHandler`) → 422.
- artist's `HttpMessageNotReadableException` → 422 divergence is handled by **overriding the base method**
  in `artist-service`'s own handler (re-annotated `@ExceptionHandler`), not by a second shared hook — one
  service's deliberate divergence does not earn shared-library surface.

**Behaviour change this adoption *does* introduce (flagged deliberately, not silent).** Three arms the
shared base has and fan-platform lacks currently fall through fan's catch-all `@ExceptionHandler(Exception.class)`
and answer **500 INTERNAL_ERROR**; after adoption they answer **400 VALIDATION_ERROR**:

- `MissingServletRequestParameterException` — all four services (reachable: `membership-service`
  `InternalAccessController` has three `@RequestParam` with no default).
- `MissingRequestHeaderException` — community / artist / notification (membership already maps it to 400).
- `HttpMessageNotReadableException` — artist/community/membership already map it; **notification-service**
  does not, so a malformed body there goes 500 → 400.

This is a **defect fix in the client's favour**, is consistent with the existing contract rows
("400 VALIDATION_ERROR — malformed JSON / type mismatch"), and **no existing test or contract row asserts
500 for any of these** (verified). It is therefore NOT treated as a contract change — but it must be
stated in the PR body and pinned by tests (AC-5) rather than discovered later.

---

# Scope

## In Scope

- `libs/java-web-servlet/.../CommonGlobalExceptionHandler.java` — add `protected HttpStatus
  validationFailureStatus()` (default `BAD_REQUEST`) and route `handleValidation` +
  `handleIllegalArgument` through it. No other behaviour change. **Because this touches a shared path, the
  lib change and all four fan-platform adaptations land in ONE atomic PR** (`CLAUDE.md § Cross-Project
  Changes`), commit scope `refactor(lib)`.
- `libs/java-web-servlet/.../CommonGlobalExceptionHandlerTest.java` — add coverage for the hook (default
  400 preserved; an overriding subclass gets 422). Existing assertions unmodified.
- `libs/java-web-servlet/README.md` — document the hook in the hosted-classes row.
- Each of `community-service`, `artist-service`, `membership-service`, `notification-service`:
  - `AbstractDomainExceptionHandler extends com.example.web.exception.CommonGlobalExceptionHandler`;
    delete the 7–9 arms the base now supplies; keep only the four genuinely fan-owned arms
    (`DataIntegrityViolationException` selective 409/500 per TASK-MONO-450,
    `MethodArgumentTypeMismatchException` → 400, `IllegalStateException` → 422 `ILLEGAL_STATE`,
    jakarta `OptimisticLockException`); override `validationFailureStatus()` → 422.
  - `build.gradle` += `implementation project(':libs:java-web-servlet')`.
  - Domain arms in `GlobalExceptionHandler` that carry no `details` switch from `ApiErrorBody` to
    `ErrorResponse`; the four `details` arms keep `ApiErrorBody`.
  - community + membership delete their local `HttpMessageNotReadableException` handler (base covers it at
    400); artist **overrides** it to keep 422.
  - `notification-service`: delete `presentation/dto/ApiErrorBody.java` (no remaining consumer).
- Tests: rewrite each service's `GlobalExceptionHandlerNotFoundTest` from direct method calls into a
  MockMvc `standaloneSetup(...).setControllerAdvice(new GlobalExceptionHandler())` test, so it proves the
  **inherited** arms are actually registered and reached through Spring's real
  `ExceptionHandlerExceptionResolver` (the current form calls the methods directly and would keep passing
  even if inheritance registered nothing).
- Spec reconciliation: `specs/services/{community,membership}-service/dependencies.md` currently assert
  "does not extend `CommonGlobalExceptionHandler`" and mis-attribute that class to `libs:java-web` (it
  lives in `libs:java-web-servlet`) — both corrected; `artist`/`notification` `dependencies.md` +
  each service's `architecture.md § Dependencies` shared-libs line gain `libs:java-web-servlet`.

## Out of Scope

- **Every other project.** `ADR-MONO-058 § 6` forbids a cross-project mega-PR; finance/erp/scm/wms/
  ecommerce/iam D2 adoption are separate future tasks. The lib hook added here is additive and
  backward-compatible so those can adopt later without a second lib change.
- **`ErrorResponse`'s shape** — deliberately untouched (decision 1).
- **`ApiEnvelope`** (the `{data, meta}` *success* envelope). `libs/` ships no shared success envelope, so
  there is nothing to adopt; the ADR's "ApiErrorBody/ApiEnvelope" phrasing is a cross-project summary and
  does not apply to fan-platform's `ApiEnvelope`. Untouched.
- `gateway-service` — reactive (Spring Cloud Gateway). `libs:java-web-servlet` must **never** reach a
  reactive classpath (`libs/java-web-servlet/README.md`; TASK-MONO-044a incident). Its
  `ApiErrorEnvelope` stays as-is.
- ADR-MONO-058 D1 / D3 / D4 / D5 / D6 / D7 / D8 — separate tasks.
- Any new error code or any change to an existing code string.

---

# Acceptance Criteria

- [ ] **AC-1 (adoption)** — all four services' `AbstractDomainExceptionHandler`
      `extends CommonGlobalExceptionHandler`; each service's `build.gradle` declares
      `implementation project(':libs:java-web-servlet')`. Repo-wide grep shows the seven duplicated arms
      (`NoResourceFoundException`, `NoHandlerFoundException`, `HttpRequestMethodNotSupportedException`,
      `HttpMediaTypeNotSupportedException`, `MethodArgumentNotValidException`, `IllegalArgumentException`,
      `@ExceptionHandler(Exception.class)`) are declared **zero** times under
      `projects/fan-platform/apps/*/src/main` (the `ObjectOptimisticLockingFailureException` arm likewise).
- [ ] **AC-2 (shared change is minimal + backward compatible)** — the `libs/java-web-servlet` diff is
      confined to `validationFailureStatus()` + its two call sites + javadoc + README + new tests. The four
      `iam-platform` subclasses are **not edited**, and `iam-platform`'s account/admin/auth/security test
      suites pass unchanged (they are the live proof the default stayed 400).
- [ ] **AC-3 (422 preserved)** — `@Valid` constraint violations and `IllegalArgumentException` still answer
      **422 `VALIDATION_ERROR`** in all four fan services; `IllegalStateException` still answers **422
      `ILLEGAL_STATE`**; artist's malformed body still answers **422** (`ArtistGroupControllerSliceTest`
      passes unmodified).
- [ ] **AC-4 (`details` preserved)** — the four documented `details` arms still emit `details` with the
      same keys (`requiredTier`; `from`/`to`/`actor`; `from`/`to`), proven by a MockMvc/jsonPath assertion
      per arm, and `details` is still **absent** (not `null`) from arms that do not set it.
- [ ] **AC-5 (the 500 → 400 arms are pinned, not incidental)** — a test per affected service asserts the
      new 400 `VALIDATION_ERROR` for missing required request parameter / missing required header /
      malformed body, so the improvement is a fixed behaviour rather than a side effect nobody guards.
- [ ] **AC-6 (timestamp wire format)** — a test serialises both an `ErrorResponse` and a `details`-less
      `ApiErrorBody` and asserts the emitted JSON key set is `{code,message,timestamp}` for both and that
      `timestamp` is an ISO-8601 **string** in both, **under a Spring-Boot-auto-configured `ObjectMapper`
      AND a bare `new ObjectMapper()`** — the mapper must come out of Boot, not be hand-tuned with
      `WRITE_DATES_AS_TIMESTAMPS` by the test, or the test asserts a property it configured itself. This
      is the load-bearing evidence for "the swap is wire-preserving"; it is what caught the false
      assumption recorded in decision 1, and an assumed answer here is exactly the failure mode
      `ADR-MONO-058 § 4` warns about.
- [ ] **AC-6b (artist's real mapper)** — a test resolves artist-service's *effective* `ObjectMapper` from
      its own `RedisCacheConfig` + `JacksonAutoConfiguration` (not a hand-built one), asserts that mapper
      really is the shadowing one (a raw `Instant` does **not** render ISO), and then asserts the error
      envelope's `timestamp` is an ISO-8601 string anyway.
- [ ] **AC-7 (reachability, not just logic)** — each service's rewritten `GlobalExceptionHandlerNotFoundTest`
      drives the request through MockMvc's real resolver, so a subclass that silently failed to register
      the inherited arms goes RED (a direct method call cannot tell the difference).
- [ ] **AC-8 (baseline parity)** — record each service's test count **before** and **after**. No test may
      disappear; the four `check` tasks and `:libs:java-web-servlet:test` are GREEN, and CI's
      `Integration (fan-platform, Testcontainers)` lane is GREEN (authoritative — local Windows Docker is
      not).
- [ ] **AC-9 (specs reconciled)** — the two `dependencies.md` statements that now read false are corrected
      in the same PR, including the `libs:java-web` → `libs:java-web-servlet` mis-attribution.
- [ ] **AC-10 (no contract change)** — `specs/contracts/http/*.md` need **no** edit; the PR body states
      explicitly which observable behaviours changed (the three 500 → 400 arms, and artist's numeric →
      ISO-string `timestamp`) and which did not. Both changes move the services *onto* what the contracts
      already document, so no contract text is edited — that claim is checked against the contract files,
      not assumed.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load
> `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the
> declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § D2, § 4, § 6 (ACCEPTED)
- `platform/error-handling.md` § Error Response Format (the "permitted to extend" clause that decision 1
  rests on), § HTTP Status Code Mapping, § General → `ILLEGAL_STATE`, `DATA_INTEGRITY_VIOLATION`
- `platform/shared-library-policy.md` § Decision Rule, § Ownership Rule, § Change Rule
- `platform/service-types/rest-api.md` § Error Handling
- `projects/fan-platform/specs/services/{community,artist,membership,notification}-service/architecture.md`
- `projects/fan-platform/specs/services/{community,artist,membership,notification}-service/dependencies.md`
- `libs/java-web-servlet/README.md`
- `projects/finance-platform/tasks/done/TASK-FIN-BE-058-globalexceptionhandler-common-base-dedup-investigation.md`
  — **prior art, read before starting.** It closed the same swap as WONTFIX for finance-platform. Its
  reasoning is not contradicted here: it rejected *Option A* (adopt and drop `details`) and *Option B*
  (make the base generic). This task takes neither — it composes (`details` kept, base type reused for the
  arms that have none) and adds the override hook that did not exist when FIN-BE-058 was written, which
  `ADR-MONO-058 § D2` has since mandated. finance-platform's own adoption remains a separate future task
  and is NOT re-decided here.

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`
- `.claude/skills/backend/testing-backend/SKILL.md`

---

# Related Contracts

- `projects/fan-platform/specs/contracts/http/community-api.md` § Envelope shapes / Common error codes
- `projects/fan-platform/specs/contracts/http/artist-api.md` § Envelope shapes / Common error codes
- `projects/fan-platform/specs/contracts/http/membership-api.md` § Envelope shapes / Common error codes

All three are **read-only inputs** — this task must not require editing them. If implementation finds it
cannot preserve a documented status/shape, that is a genuine contract change: stop, update the contract
first per `CLAUDE.md`, and flag it prominently in the PR body.

---

# Target Service

- `community-service`, `artist-service`, `membership-service`, `notification-service` (fan-platform)
- `libs/java-web-servlet` (shared — atomic, same PR)

---

# Architecture

Follow each target service's own `architecture.md`. The advice classes stay in the presentation/adapter-in
layer; no layer boundary moves. `AbstractDomainExceptionHandler` keeps its **name** — it is referenced by
name from `platform/error-handling.md § General → ILLEGAL_STATE`, so renaming it would create doc drift
this task is not scoped to fix.

---

# Implementation Notes

- **Ambiguity hazard (will fail at bean-creation time, not compile time).** Spring's
  `ExceptionHandlerMethodResolver` throws `IllegalStateException: Ambiguous @ExceptionHandler method
  mapped for …` when **two different methods** in one advice map the same exception type. So a subclass
  must **override** (same signature) a base arm it wants to change — declaring a *differently named*
  method for the same exception type is a boot failure. This applies to artist's
  `HttpMessageNotReadableException` (override) and to the optimistic-lock pair: keep only the jakarta
  `OptimisticLockException` locally under a distinct name, and let the base own
  `ObjectOptimisticLockingFailureException`.
- Overridden arms should be re-annotated with their `@ExceptionHandler(...)` for readability; Spring
  de-duplicates on the most-specific method so this does not create the ambiguity above.
- `libs:java-web-servlet` declares `libs:java-web` as `implementation`, so `ErrorResponse` does **not**
  arrive transitively — each service already declares `implementation project(':libs:java-web')` and must
  keep it.
- Order of work that keeps the diff reviewable: (1) lib hook + lib tests; (2) one service end-to-end
  (`notification-service` — smallest, and the only `ApiErrorBody` deletion); (3) replicate to the other
  three; (4) specs.

---

# Edge Cases

- A `details`-less `ApiErrorBody` and an `ErrorResponse` must serialise identically — if AC-6 shows they do
  not, the swap is a contract change and the task stops for a contract update rather than shipping it.
- `notification-service` is declared `event-consumer` but exposes a REST inbox; its HTTP error surface is
  in scope exactly like the three `rest-api` services.
- `MethodArgumentTypeMismatchException` is **not** in the shared base. Deleting fan's local handler would
  send it to the catch-all and regress a documented **400** into a 500 — it must stay service-local.
- Same for the selective `DataIntegrityViolationException` mapping (TASK-MONO-450 / `platform/error-handling.md`
  `DATA_INTEGRITY_VIOLATION`) and `IllegalStateException` → 422: not in the base, must stay.
- `membership-service`'s `MissingRequestHeaderException` arm is the `Idempotency-Key` guard — the base's
  identical 400 mapping covers it, but its message string must stay byte-identical
  (`"Missing required header: " + headerName`).
- The `Allow` header on 405 is contractual (RFC 7231 / `platform/error-handling.md` `METHOD_NOT_ALLOWED`) —
  the base emits it, but assert it rather than assume.

---

# Failure Scenarios

- **Silent contract regression.** Blindly `extends` the base and every `@Valid` failure flips 422 → 400
  across three contract files. The hook + AC-3 exist for exactly this.
- **Green-wash by direct method call.** Keeping the existing `GlobalExceptionHandlerNotFoundTest` shape
  makes it a test of the *library* reached through a subclass reference; it would stay green even if fan's
  advice registered nothing. AC-7 replaces the predicate with the artifact.
- **Widening the shared type "just a little".** Adding `details` to `ErrorResponse` changes ~20 services'
  error JSON in two projects that did not ask for it, and cannot be reviewed inside a fan-platform PR.
  Rejected in decision 1; if an implementer reaches for it, stop and re-read the blast-radius count.
- **Breaking iam-platform.** `CommonGlobalExceptionHandler` has four live subclasses. Any change beyond an
  additive `protected` hook with an unchanged default is out of this task's mandate — verify by running
  their suites, not by reading the diff.
- **Scope leak into the other seven projects.** The same duplication exists fleet-wide; fixing it here is
  explicitly forbidden by `ADR-MONO-058 § 6`. One project, one PR.
- **Boot-time ambiguity** (see Implementation Notes) — this passes `compileJava` and fails at context
  startup, so it is caught only by a test that builds the advice through Spring, i.e. AC-7's MockMvc form.

---

# Test Requirements

- Unit (lib): `CommonGlobalExceptionHandlerTest` — existing assertions unmodified + hook default (400) +
  overriding-subclass (422).
- Slice/unit (per service): rewritten `GlobalExceptionHandlerNotFoundTest` as MockMvc standalone advice
  test (404 `NoResourceFound` / 404 `NoHandlerFound` / 405 + `Allow` / 415), existing
  `GlobalExceptionHandlerDataIntegrityTest` unchanged and still green.
- New per-service assertions for AC-3 (422 `@Valid`), AC-4 (`details` keys), AC-5 (500 → 400 arms),
  AC-6 (timestamp wire format — one place is enough, plus per-service key-set assertions via jsonPath).
- All existing controller slice tests pass **unmodified** — they are the regression net for the domain arms
  whose return type changes from `ApiErrorBody` to `ErrorResponse`.
- `./gradlew :libs:java-web-servlet:test`, the four fan `:check` tasks, and the four `iam-platform`
  services' `:check` tasks GREEN. CI `Integration (fan-platform, Testcontainers)` GREEN is authoritative.

---

# Verification Record

## Test counts (local, Docker-free `:check` / `:test`)

| module | before | after | delta |
|---|---|---|---|
| `community-service` | 115 | 127 | +12 |
| `artist-service` | 116 | 126 | +10 |
| `membership-service` | 118 | 127 | +9 |
| `notification-service` | 93 | 101 | +8 |
| `libs:java-web-servlet` | 40 | 43 | +3 |

0 failures / 0 errors / 0 skipped in every module, before and after. No test was removed — the four
`GlobalExceptionHandlerNotFoundTest` files kept their 4 cases each and changed only their *method* (direct
call → MockMvc through the real resolver).

## Guard mutation-checks (the guards were verified to bite, not merely to pass)

- Reverting `validationFailureStatus()` to `BAD_REQUEST` in notification-service →
  `GlobalExceptionHandlerEnvelopeContractTest` RED on exactly the two 422 cases
  (`@Valid` → 422, `IllegalArgumentException` → 422), 8 tests / 2 failed. Reverted.
- AC-6 caught a live wrong assumption on its first run (see decision 1) rather than confirming it — that
  is the check doing its job, and it is why the envelope now pre-formats its timestamp.

## Cross-project (shared-lib) blast radius

- `libs/java-web-servlet` change = one `protected` hook + its two call sites; default unchanged.
- The four `extends CommonGlobalExceptionHandler` consumers (`iam-platform` account / admin / auth /
  security) were **not edited**; their `:check` tasks were run against the modified lib.
- `libs/java-web.ErrorResponse` **untouched**, so the 39 files referencing it across `iam-platform` and
  `ecommerce-microservices-platform` are unaffected by construction.
- `gateway-service` (reactive) untouched — `libs:java-web-servlet` must never reach a reactive classpath.

## Observable behaviour deltas (deliberate, both toward the already-documented contract)

1. `MissingServletRequestParameterException` / `MissingRequestHeaderException` /
   `HttpMessageNotReadableException` on services that lacked a local arm: **500 INTERNAL_ERROR → 400
   VALIDATION_ERROR**.
2. artist-service error `timestamp`: **JSON number → ISO-8601 string** (decision 1).

Neither required a `specs/contracts/` edit — both move the services onto what the contracts already say.

## Known-remaining, out of scope (separate finding, not fixed here)

`artist-service/config/RedisCacheConfig` contributes an `ObjectMapper` `@Bean` that shadows Spring Boot's
auto-configured one (Boot's is `@ConditionalOnMissingBean`), so artist runs with
`WRITE_DATES_AS_TIMESTAMPS` enabled. The error envelope is now immune to that, but any other artist
response DTO holding a raw `java.time` value, and the Redis directory-cache payload, are not. The Kafka
event contract is unaffected (`ArtistEventPublisherAdapter` pre-formats `occurredAt`). Repairing the
mapper changes the cache payload format and belongs in its own task.

---

# Definition of Done

- [x] Implementation completed (lib hook + 4 service adoptions, one atomic PR)
- [x] Tests passing; per-service before/after counts recorded; no test lost
- [x] `iam-platform` `CommonGlobalExceptionHandler` consumers verified unaffected
- [x] Contracts unchanged (verified); behaviour deltas stated explicitly in the PR body
- [x] Specs updated (`dependencies.md` × 3 + `architecture.md` shared-libs line × 4, lib README)
- [x] Ready for review
