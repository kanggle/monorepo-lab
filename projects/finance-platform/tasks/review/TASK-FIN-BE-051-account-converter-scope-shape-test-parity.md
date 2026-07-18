# TASK-FIN-BE-051 — account ActorContextJwtAuthenticationConverter: scope-shape unit-test parity with ledger

- **Type**: TASK-FIN-BE (test-only parity fix)
- **Status**: review
- **Service**: account-service (finance-platform)
- **Domain/traits**: saas / [transactional, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Sonnet (test-only, mechanical parity)

## Goal

Close a sibling-parity coverage gap in the account-service converter unit test. The account
`ActorContextJwtAuthenticationConverter.extractScopes` accepts **three** scope-claim shapes —
a JSON array (`scope: [...]`), an RFC-6749 space-delimited string (`scope: "a b"`), and the
`scp` alias — but the account unit test (`ActorContextJwtAuthenticationConverterTest`) exercises
only the array shape. The ledger sibling test already covers all three
(`spaceDelimitedScopeLifted`, `scpFallbackLifted`). The account test therefore under-covers its
own production code relative to its sibling: the `String.split` branch and the `scp`-fallback
branch of account's `extractScopes` are untested.

This is a classic N-1-wired-siblings straggler (memory `project_enforcement_straggler_sibling_parity`),
here on the **test** axis rather than the production axis: identical production logic in both
services, but only one service's test pins it.

## Scope

- **In scope**: add two `@Test` methods to
  `projects/finance-platform/apps/account-service/src/test/java/com/example/finance/account/infrastructure/security/ActorContextJwtAuthenticationConverterTest.java`
  mirroring ledger's `spaceDelimitedScopeLifted` and `scpFallbackLifted`.
- **Out of scope**: production code (no change — the behaviour already exists and is correct);
  the `convert()` error paths (missing `sub` / `tenant_id`) and role-string/`role`-alias shapes
  (untested in BOTH services — a separate parity concern, not this task); ledger test (already
  complete).

## Acceptance Criteria

- **AC-1**: account test has a `spaceDelimitedScopeLifted` test — a `scope: "finance.read finance.write"`
  string claim lifts to both `SCOPE_finance.read` and `SCOPE_finance.write`.
- **AC-2**: account test has a `scpFallbackLifted` test — a `scp: ["finance.write"]` claim (no `scope`
  claim) lifts to `SCOPE_finance.write`.
- **AC-3**: both new tests pass against the UNCHANGED account production converter (they pin existing
  behaviour, they do not drive a code change). `./gradlew :finance-platform:account-service:test`
  (or the module's equivalent) is GREEN.
- **AC-4**: no production-code change in this task.

## Related Specs / Related Contracts

- No contract change (authorization-internal, test-only).
- Production behaviour under test is unchanged since TASK-FIN-BE-046 (account scope-lifting).

## Edge Cases / Failure Scenarios

- The space-delimited assertion must check BOTH scopes are lifted (a split that keeps the whole
  string as one token would still contain neither `SCOPE_finance.read` nor `SCOPE_finance.write`).
- The `scp` test must NOT also set `scope`, so it exercises the fallback branch (`raw == null` →
  read `scp`), not the primary branch.

## Related

- **TASK-FIN-BE-046** (account scope-lifting — the production code these tests pin).
- **TASK-FIN-BE-047** (ledger scope-lifting — its test is the parity reference).
- Memory: `project_enforcement_straggler_sibling_parity` (line siblings up; N-1 wired + 1 unwired
  = straggler), `feedback_recount_population_dont_inherit_scope` (the ledger test count is a
  reference, not authority — re-read account's own `extractScopes` to confirm all three branches
  exist, which it does).
