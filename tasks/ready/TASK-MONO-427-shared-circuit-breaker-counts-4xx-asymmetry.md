# TASK-MONO-427 — shared `ResilienceClientFactory` circuit breaker counts 4xx as failures (retry already ignores them) — a client error can open the circuit and cascade

- **Type**: TASK-MONO (monorepo-level — shared library `libs/java-common`)
- **Status**: ready
- **Scope path**: `libs/java-common/.../resilience/ResilienceClientFactory.java` (shared) + fleet impact
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (shared-lib resilience change, cross-service blast radius)

## Goal

Close the asymmetry in the shared `ResilienceClientFactory`: its **retry** config ignores
`HttpClientErrorException` (4xx — "a contract failure, never retry"), but its **circuit breaker**
config ignores nothing, so a burst of 4xx client errors counts toward the failure rate and **opens
the circuit** — after which unrelated calls through the same breaker fail-fast with
`CallNotPermittedException`. A downstream *client* error (4xx) is not a downstream *availability*
failure and should not trip the breaker.

## AC-0 — Finding (verified 2026-07-18)

- **The asymmetry (source):**
  - `standardCircuitBreakerConfig()` (`libs/java-common/.../ResilienceClientFactory.java:123-131`):
    `failureRateThreshold(50)`, TIME_BASED window 10, min 5 calls — **no `ignoreExceptions`**.
  - `standardRetryConfig()` (`:139-145`): `ignoreExceptions(HttpClientErrorException.class)` (`:143`)
    with the comment "4xx is a contract failure — never retry."
  - So the two halves of the same factory disagree on whether a 4xx is a fault: retry says no, the
    circuit says yes.
- **This bit in production (TASK-BE-517):** auth-service minted the SUPER_ADMIN token with
  `tenant_id='*'` and called `listEntitledDomains("*")`/`listAccountRoles("*",…)`; account-service
  correctly rejected `*` with **400** (`TenantId` validation). The 400 (raw `HttpClientErrorException`)
  reached the circuit **before** the client's 4xx→`AccountServiceUnavailableException` conversion, so
  the circuit counted it and opened → the *next* token minting (a valid tenant, acme-corp) got
  `CallNotPermittedException` → fail-soft → the token omitted `entitled_domains` → finance card 403.
  **BE-517 removed the specific `*` source; this task removes the amplifier** (any 4xx opening the
  circuit) so the class can't recur from a different 4xx.
- **Sibling context:** this is the programmatic-factory analogue of **TASK-BE-516**, which fixed the
  same "client/business 4xx must not trip the circuit" defect on admin-service's YAML-configured
  `accountService` breaker. admin-service uses Spring `@CircuitBreaker` + `application.yml`
  (`ignore-exceptions`), so BE-516 didn't touch this shared factory — the two mechanisms are separate,
  and the factory is the straggler.
- **Blast radius:** `ResilienceClientFactory.buildCircuitBreaker` is consumed by **3 iam-platform
  services** (auth/account/admin account-clients via the factory path) today; being in `libs/java-common`
  it governs every future consumer. Ignoring 4xx is **strictly safer** (fewer spurious opens) and matches
  the retry half's already-shipped intent.

## Scope

- **In**: add `.ignoreExceptions(HttpClientErrorException.class)` to `standardCircuitBreakerConfig()` so
  the circuit ignores 4xx (parity with retry). Genuine faults — 5xx (`HttpServerErrorException`),
  connection/timeout, `CallNotPermittedException` — still count and still open the circuit. A unit test
  proving: a burst of `HttpClientErrorException` keeps the breaker CLOSED; a burst of
  `HttpServerErrorException` opens it. Mutation-checked.
- **Out**: per-service circuit tuning (thresholds/windows); the admin-service YAML breaker (already fixed
  by BE-516); changing the retry config; any consumer-side code (the change is in the shared default so
  no consumer edit is required — verify each of the 3 consumers still builds/tests green in the same PR,
  atomic per the cross-project rule).

## Acceptance Criteria

- **AC-1**: `standardCircuitBreakerConfig()` ignores `HttpClientErrorException` (4xx never counts toward
  the failure rate).
- **AC-2**: a burst of `HttpClientErrorException` (> minimumNumberOfCalls at 100% rate if counted) leaves
  the breaker CLOSED and a subsequent call is permitted; a burst of `HttpServerErrorException` OPENS it
  (ignore is not over-broad — real faults still trip it). Tested against a breaker built from the actual
  factory method (not a re-implemented config).
- **AC-3**: mutation — removing the ignore turns the AC-2 CLOSED assertion RED.
- **AC-4**: `./gradlew :libs:java-common:check` + the 3 consuming iam services' `:check` green (atomic PR).
- **AC-5** *(optional, CI-authoritative)*: no regression in the iam integration lane / federation E2E.

## Related Specs / Contracts

- `libs/java-common/.../resilience/ResilienceClientFactory.java` (the asymmetry)
- Sibling: `projects/iam-platform/tasks/done/TASK-BE-516-*` (YAML breaker, admin-service), `TASK-BE-517-*` (the `*` source removed)
- `platform/shared-library-policy.md` (no project-specific content — this is a project-agnostic resilience default)

## Edge Cases

- `HttpClientErrorException` is 4xx only; `HttpServerErrorException` (5xx) is a sibling of
  `RestClientResponseException`, NOT a subtype of `HttpClientErrorException`, so ignoring 4xx does not
  ignore 5xx. Confirm in the test.
- Some callers convert 4xx to a domain exception *before* it reaches the breaker (e.g. a per-call catch);
  those already don't count — this change only affects callers whose raw `HttpClientErrorException`
  propagates through the decorator (the BE-517 path).

## Failure Scenarios

- Over-broad ignore (e.g. ignoring `RestClientResponseException`) would swallow 5xx → the breaker never
  opens on a real outage. Mitigated: AC-2 asserts 5xx still opens; ignore is scoped to
  `HttpClientErrorException`.
- Shared-lib change breaks a consumer's existing circuit test that asserted 4xx opens the breaker.
  Mitigated: AC-4 runs all 3 consumers' `:check` in the atomic PR; update any such test to the new
  (correct) posture.
