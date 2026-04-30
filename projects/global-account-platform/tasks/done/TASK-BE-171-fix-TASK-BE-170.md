# TASK-BE-171: Fix naming convention violation found in TASK-BE-170

## Goal
Fix issue found in TASK-BE-170: rename `RedisEmailVerificationTokenStoreTest` to `RedisEmailVerificationTokenStoreUnitTest` to comply with the platform naming convention for infrastructure unit tests (`platform/testing-strategy.md` — `Unit (infrastructure)` → `{ClassName}UnitTest`).

## Scope
- Rename `apps/account-service/src/test/java/com/example/account/infrastructure/redis/RedisEmailVerificationTokenStoreTest.java` → `RedisEmailVerificationTokenStoreUnitTest.java`
- Update the class name inside the file accordingly
- Update `@DisplayName` to reflect the class rename if needed
- Confirm the tests still pass after the rename

## Acceptance Criteria
- [ ] File renamed to `RedisEmailVerificationTokenStoreUnitTest.java`
- [ ] Class declaration updated to `class RedisEmailVerificationTokenStoreUnitTest`
- [ ] All 9 existing tests continue to pass
- [ ] No other files reference the old class name (verify with project-wide search)

## Related Specs
- `platform/testing-strategy.md` (Naming Conventions table — `Unit (infrastructure)`)
- `specs/services/account-service/architecture.md`

## Related Contracts
- None

## Edge Cases
- The rename is a pure refactor; no logic changes are needed.
- If any test runner configuration or CI report filter references the old class name by pattern, update it.

## Failure Scenarios
- If the renamed file causes a Gradle compilation error (e.g., inner class reference mismatch), fix the reference before committing.
