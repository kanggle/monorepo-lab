# TASK-FIN-BE-053 — finance converter unit tests: structural refactor (@Nested + shared builder), no behaviour change

- **Type**: TASK-FIN-BE (test refactor — behaviour-preserving)
- **Status**: review
- **Service**: account-service + ledger-service (finance-platform)
- **Domain/traits**: saas / [transactional, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Sonnet (test refactor, mechanical)

## Goal

Refactor the two finance `ActorContextJwtAuthenticationConverterTest` classes for readability and
report structure, **without changing behaviour or coverage** (same 15 tests each, same assertions).
Both files are refactored symmetrically to preserve account/ledger parity.

Refactorings (file-internal only):

1. **`@Nested` grouping** — replace the `// ----` section comments with JUnit5 `@Nested` inner
   classes (ScopeLifting / EntitlementTrust / SuperAdminWildcard / ConvertGuards / RoleShapes), so
   the test report is grouped by concern.
2. **Shared JWT builder** — extract a `base()` builder (header + fixed timestamps, no `sub`/`tenant_id`)
   used by both `jwt(Consumer)` (adds sub + tenant_id=finance) and the two guard tests (which today
   duplicate the full builder boilerplate inline).
3. **Fixed `Instant`** — replace `Instant.now()` with a fixed `IAT`/`EXP` constant (deterministic
   fixture; the converter does not validate expiry, so timestamps are inert).
4. **Static-import the role constants** — `VIEWER_ROLE` / `SUPERADMIN_READ_ROLE` static-imported so
   assertions read `contains(VIEWER_ROLE)` instead of the fully-qualified reference.
5. **Class Javadoc refresh** — the current Javadoc names only two concerns (scope-lifting +
   entitlement); update it to also mention the wildcard READ authority and the convert() guards.

## Scope

- **In scope**: the two test files only.
- **Out of scope**: production code (zero change); cross-module dedup into `libs/java-test-support`
  (deliberately rejected — the converters are intentionally per-service; sharing the test without
  sharing the code would be asymmetric); parameterisation (rejected — the per-test Korean
  `@DisplayName`s document distinct security properties and read better as discrete tests).

## Acceptance Criteria

- **AC-1**: both test files use `@Nested` groups; no `// ----` section comments remain.
- **AC-2**: the two guard tests build their JWTs via the shared `base()` helper (no duplicated
  full-builder boilerplate).
- **AC-3**: role-constant assertions use static-imported `VIEWER_ROLE` / `SUPERADMIN_READ_ROLE`.
- **AC-4**: fixtures use a fixed `Instant` (no `Instant.now()`).
- **AC-5**: each converter test still runs **15 tests, 0 skipped / 0 failures / 0 errors** against
  the UNCHANGED production converter; test count and assertion semantics are identical.
- **AC-6**: no production-code change.

## Related Specs / Related Contracts

- No contract change (test-internal refactor).

## Edge Cases / Failure Scenarios

- `@Nested` inner classes must remain non-static so they can access the outer instance's `converter`
  field and `authorities(...)` method (a `static` nested class would not compile against them).
- The `base()` helper must NOT set `sub`/`tenant_id`, so the guard tests can each omit exactly one
  and still exercise the intended guard (AC per TASK-FIN-BE-052).

## Related

- **TASK-FIN-BE-051 / 052** (the coverage this refactor restructures — must stay green, unchanged).
- Memory: `feedback_repo_knows_what_it_does_not_say` (no reflexive dedup — cross-module extraction
  deliberately declined), `project_console_web_godfile_split_series` (behaviour-preserving structural
  refactor discipline: same tests, only structure moves).
