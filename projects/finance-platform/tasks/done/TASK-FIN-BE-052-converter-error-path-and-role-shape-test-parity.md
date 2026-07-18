# TASK-FIN-BE-052 — finance converter unit tests: convert() error-path + role-shape coverage (account + ledger)

- **Type**: TASK-FIN-BE (test-only coverage fix, both siblings)
- **Status**: done
- **Service**: account-service + ledger-service (finance-platform)
- **Domain/traits**: saas / [transactional, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Sonnet (test-only, mechanical)

## Goal

Close the remaining converter unit-test coverage gaps common to BOTH finance converters
(`ActorContextJwtAuthenticationConverter`), pinning behaviour that today has zero test:

1. **`convert()` guard clauses** — a JWT with a missing/blank `sub` throws
   `IllegalStateException("sub claim is missing …")` (converter L47-50); a missing/blank
   `tenant_id` throws `IllegalStateException("tenant_id claim is missing …")` (L51-54). Neither
   guard is exercised by any test, so a regression that dropped or reordered them would ship green.
2. **`extractRoles` claim shapes** — the converter reads `roles`, falls back to the `role` singular
   alias, and accepts a `roles` value as either a JSON array OR a `[,\s]+`-delimited string
   (L104-118). Only the array-under-`roles` shape is tested; the `role` alias branch and the
   delimited-string branch are untested.

This is the follow-on parity concern noted (out of scope) in TASK-FIN-BE-051 — there the gap was
account-vs-ledger on the scope axis; here it is both siblings under-covering their shared guard and
role-extraction logic.

## Scope

- **In scope**: add tests to BOTH
  `account-service/.../ActorContextJwtAuthenticationConverterTest.java` and
  `ledger-service/.../ActorContextJwtAuthenticationConverterTest.java`:
  - `missingSubClaimThrows` — no `sub` (but `tenant_id` present) → `IllegalStateException`, message
    names `sub` (so the assertion pins the sub guard, not the tenant guard).
  - `missingTenantIdClaimThrows` — `sub` present, no `tenant_id` → `IllegalStateException`, message
    names `tenant_id`.
  - `roleSingularAliasLifted` — a `role` (singular) claim lifts to `ROLE_*`.
  - `delimitedRolesLifted` — a `roles` value as a space/comma-delimited string lifts to multiple
    `ROLE_*`.
- **Out of scope**: production code (no change — behaviour exists and is correct). The
  `String.split` / `scp` scope shapes for account (closed by TASK-FIN-BE-051).

## Acceptance Criteria

- **AC-1**: both converter tests assert the `sub` guard throws `IllegalStateException` with a
  `sub`-naming message when `sub` is absent.
- **AC-2**: both converter tests assert the `tenant_id` guard throws `IllegalStateException` with a
  `tenant_id`-naming message when `tenant_id` is absent.
- **AC-3**: both converter tests exercise the `role` singular-alias branch and the delimited-string
  `roles` branch.
- **AC-4**: `./gradlew :account-service:test :ledger-service:test` (module-equivalent) GREEN against
  UNCHANGED production converters.
- **AC-5**: no production-code change in this task.

## Related Specs / Related Contracts

- No contract change (authorization-internal, test-only).

## Edge Cases / Failure Scenarios

- The missing-`sub` test MUST keep `tenant_id` present (and vice-versa) so the asserted message
  distinguishes WHICH guard fired — an `IllegalStateException`-only assertion would pass on the
  wrong guard.
- The delimited-roles test asserts BOTH split tokens become `ROLE_*` (a non-splitting regression
  would leave a single `ROLE_OPERATOR ADMIN` authority, failing the multi-value `contains`).

## Related

- **TASK-FIN-BE-051** (account scope-shape parity — this task's sibling; the out-of-scope note there
  named exactly these gaps).
- **TASK-FIN-BE-046 / 047** (the scope-lifting production code whose surrounding guard/role logic
  these tests pin).
- Memory: `project_enforcement_straggler_sibling_parity`, `env_test_fixture_impossible_input_proves_nothing`
  (assert the property from the guard message, not a proxy).
