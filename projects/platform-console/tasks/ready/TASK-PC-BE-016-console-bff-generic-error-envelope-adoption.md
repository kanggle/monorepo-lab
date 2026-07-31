# Task ID

TASK-PC-BE-016

# Title

ADR-MONO-058 D2 — adopt `libs/java-web-servlet.CommonGlobalExceptionHandler` for console-bff's generic (non-domain) exception-handler tail

# Status

ready

# Owner

backend

# Task Tags

- code

---

# Required Sections (must exist)

- Goal
- Scope
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`ADR-MONO-058` (ACCEPTED 2026-07-30) § 2 D2 found the generic (non-domain) exception-handler tail — `HttpMediaTypeNotSupportedException`, `HttpRequestMethodNotSupportedException`, the catch-all 500 — duplicated across all 8 projects, and identifies `libs/java-web-servlet.CommonGlobalExceptionHandler` as the existing shared adoption target. `console-bff`'s `GlobalExceptionHandler` (`apps/console-bff/src/main/java/.../adapter/inbound/web/GlobalExceptionHandler.java`) has this exact generic tail (`handleMethodNotSupported`, `handleMediaTypeNotSupported`, `handleGeneric`) alongside substantial domain-specific handlers (`MissingCredentialException`, `MissingTenantException`, `UpstreamUnauthorizedException`, `UnknownNotificationDomainException`, `HttpStatusCodeException`-based downstream-status mapping) that must NOT move — only the generic arms are adoption candidates.

---

# Scope

## In Scope

- Replace `GlobalExceptionHandler`'s generic-tail methods (`handleMethodNotSupported`, `handleMediaTypeNotSupported`, `handleGeneric`, and `NoResourceFoundException` if console-bff hits that path — verify) with `libs/java-web-servlet.CommonGlobalExceptionHandler` adoption, per whatever mechanism that class exposes (delegation, `@ControllerAdvice` ordering, or composition — read the library class first).
- Preserve the existing `{code, message, timestamp}` wire shape exactly — console-bff's envelope already matches `libs/java-web.ErrorResponse`'s base shape per this handler's own class Javadoc, so this should be a low-risk swap, but verify field names/types byte-for-byte via a contract test before/after.
- Preserve the existing `basePackages = "com.kanggle.platformconsole.bff.adapter.inbound.web"` scoping — this handler's own Javadoc documents why unscoped `@RestControllerAdvice` breaks `/actuator/prometheus` (PR #669 incident). `CommonGlobalExceptionHandler` adoption must not regress this.
- Preserve all domain-specific `@ExceptionHandler` methods unchanged (`MissingCredentialException`, `MissingTenantException`, `UpstreamUnauthorizedException`, `UnknownNotificationDomainException`, `HttpStatusCodeException`, `ResourceAccessException`, `IllegalArgumentException`) — these are console-bff's own composition/passthrough policy, not the D2 target.

## Out of Scope

- D7 (`ResilienceClientFactory` adoption) — **already done** for console-bff (`TASK-PC-BE-015`, confirmed present: `RestClientConfig.java`'s Javadoc documents `Resilience4jLegResilienceAdapter` built from `libs/java-common.ResilienceClientFactory`, plus explicit 2s connect/read timeouts on every per-domain `RestClient`). The ADR's § 1.1 table lists `console-bff` under this pattern, but that finding appears stale against current code (`TASK-PC-BE-015` landed 2026-07-22, before the 2026-07-29 audit) — do not re-open it here; if re-verification during this task's implementation finds an actual gap, file it as its own separate task rather than silently expanding this one's scope.
- Any other D-item — platform-console has no confirmed D1/D3/D4/D5/D6/D8 duplication per the ADR's table.
- `console-web` (frontend) — this task is `console-bff` only.

---

# Acceptance Criteria

- [ ] `handleMethodNotSupported`/`handleMediaTypeNotSupported`/`handleGeneric` are replaced by (or delegate to) `libs/java-web-servlet.CommonGlobalExceptionHandler`.
- [ ] `/actuator/prometheus` still returns the Prometheus exposition format under an actuator-endpoint exception (regression test for the PR #669 incident this handler's Javadoc documents).
- [ ] Wire shape `{code, message, timestamp}` unchanged for all three generic arms (contract test).
- [ ] All existing domain-specific handler tests still pass unmodified.
- [ ] `./gradlew :projects:platform-console:apps:console-bff:test` passes.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `projects/platform-console/PROJECT.md`, then load `rules/common.md` plus matching `rules/domains/<domain>.md` and `rules/traits/<trait>.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § 2 D2
- `platform/error-handling.md`
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` (origin)
- `projects/platform-console/specs/services/console-bff/architecture.md`

---

# Related Contracts

- console-bff's inbound error-response wire contract (implicit — `{code, message, timestamp}`; no formal contract file found, verify before implementation).

---

# Target Service

- `console-bff` (`projects/platform-console/apps/console-bff`)

---

# Architecture

Per `projects/platform-console/specs/services/console-bff/architecture.md`'s declared `Service Type` — read and follow the matching `platform/service-types/<type>.md` before implementing.

---

# Implementation Notes

- Read `libs/java-web-servlet.CommonGlobalExceptionHandler`'s actual API first — the ADR's § 2 D2 does not prescribe HOW a service composes with it (extend, delegate, or a Spring `@Order`-based coexistence of two `@RestControllerAdvice` beans), and console-bff's existing handler is scoped to a specific `basePackages` for a documented reason (actuator). Whatever composition mechanism the shared class supports must preserve that scoping.
- No `details` field or status-code conflict applies to console-bff specifically (its existing envelope has no 4th field, and its status codes are already service-specific business mappings for the domain handlers — only the 3 generic arms are in scope, and 405/415/500 are not the status codes the ADR's § 2 D2 conflict note is about).

---

# Edge Cases

- If `CommonGlobalExceptionHandler` turns out to be an auto-configured bean rather than an explicitly-composed class, this would violate `shared-library-policy.md § No context-wide annotations` from the *library* side — flag and stop rather than working around it, since fixing that is out of this task's scope (it would be a `libs/java-web-servlet` fix, a separate task).

---

# Failure Scenarios

- Adopting `CommonGlobalExceptionHandler` without preserving the `basePackages` scoping would reproduce the exact `/actuator/prometheus` 500 regression this handler's own Javadoc documents (PR #669) — verify with a live actuator-endpoint-exception test, not just unit tests of the handler class in isolation.
- Silently changing any domain-specific handler's status code or `code` string while "cleaning up" would be an undocumented API contract change for console-web (this handler's consumer) — do not touch anything outside the three generic arms named in Scope.

---

# Test Requirements

- Regression test: actuator endpoint exception still bypasses this advice.
- Contract test: generic-arm wire shape unchanged.
- Full existing `GlobalExceptionHandler*Test` suite green.

---

# Definition of Done

- [ ] Generic tail delegates to/uses `libs/java-web-servlet.CommonGlobalExceptionHandler`
- [ ] Domain-specific handlers unchanged
- [ ] Actuator scoping regression test added and passing
- [ ] Task moved to `review` with PR link
