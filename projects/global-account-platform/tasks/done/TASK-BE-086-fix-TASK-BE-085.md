---
id: TASK-BE-086
title: "Fix issues found in TASK-BE-085 (operator password change API)"
status: ready
area: backend
service: admin-service
---

## Goal

Fix issues found in TASK-BE-085 review:

1. Register `CURRENT_PASSWORD_MISMATCH` and `PASSWORD_POLICY_VIOLATION` error codes in `platform/error-handling.md` (required before use per the platform error handling change rule).
2. Add unit tests for `OperatorAdminUseCase.changeMyPassword()` covering the happy path, current password mismatch, and policy violation scenarios.
3. Add integration test coverage for `PATCH /api/admin/operators/me/password` in `OperatorAdminIntegrationTest`.

## Scope

### In

1. **`platform/error-handling.md`** — Add `CURRENT_PASSWORD_MISMATCH` and `PASSWORD_POLICY_VIOLATION` to the `Admin Operations [domain: saas]` section.
2. **`apps/admin-service/src/test/java/.../application/OperatorAdminUseCaseTest.java`** — Add unit tests for `changeMyPassword`:
   - Happy path: valid current password + policy-compliant new password → saved
   - `CurrentPasswordMismatchException` when `passwordHasher.verify()` returns false
   - `PasswordPolicyViolationException` when new password is too short (< 8 chars)
   - `PasswordPolicyViolationException` when new password meets length but only 2 character categories
   - `PasswordPolicyViolationException` when new password exceeds 128 chars
3. **`apps/admin-service/src/test/java/.../integration/OperatorAdminIntegrationTest.java`** — Add integration test for `PATCH /operators/me/password`:
   - Success: valid current password + valid new password → 204 No Content
   - Current password mismatch → 400 `CURRENT_PASSWORD_MISMATCH`

### Out

- Modifying production implementation code (no bugs found; only test and doc gaps)
- Email-equals-password policy check (not in original task scope)

## Acceptance Criteria

- [ ] `CURRENT_PASSWORD_MISMATCH` listed in `platform/error-handling.md` under `Admin Operations [domain: saas]`
- [ ] `PASSWORD_POLICY_VIOLATION` listed in `platform/error-handling.md` under `Admin Operations [domain: saas]`
- [ ] `OperatorAdminUseCaseTest` contains ≥ 3 new test methods covering `changeMyPassword`
- [ ] `OperatorAdminIntegrationTest` contains ≥ 1 new test for the `PATCH /operators/me/password` endpoint
- [ ] All unit tests pass (slice tests + unit tests: `./gradlew :apps:admin-service:test`)

## Related Specs

- `specs/features/password-management.md`
- `specs/features/operator-management.md`

## Related Contracts

- `specs/contracts/http/admin-api.md` — PATCH /api/admin/operators/me/password
- `platform/error-handling.md` — error code registry

## Edge Cases

- `OperatorAdminUseCaseTest.changeMyPassword_policy_violation_128_char_boundary`: password of exactly 129 chars → `PasswordPolicyViolationException`
- `OperatorAdminUseCaseTest.changeMyPassword_policy_violation_two_categories_only`: password with exactly 2 of 4 character categories → `PasswordPolicyViolationException`

## Failure Scenarios

- Integration test: wrong current password is supplied → `400 CURRENT_PASSWORD_MISMATCH` (verify the operator's stored hash is not mutated)
