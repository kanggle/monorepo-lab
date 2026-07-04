# TASK-BE-473 — Enforce password complexity (8자 + 3종) at signup, per spec

- **Status**: review
- **Project**: iam-platform
- **Service**: account-service (signup boundary) — follow-up to TASK-BE-472
- **Type**: bug fix (spec-vs-code gap: under-enforcement)
- **Analysis model**: Opus 4.8 / **구현 권장**: Opus (cross-service flow + contract semantics)

## Goal

`specs/features/signup.md` (§User Flow step 5, §Business Rules) requires signup to enforce the
`PasswordPolicy` (최소 8자, 대소문자·숫자·특수 중 3종 이상), and `account-api.md` documents a **422
VALIDATION_ERROR** for "패스워드 복잡도 미달". But the implementation only enforced `@Size(min=8)` —
`CreateCredentialUseCase` never calls `PasswordPolicy`, so a weak password (e.g. `lowercaseonly`,
`12345678`) was **silently accepted** at signup, contradicting both the spec and the message the
SAS signup page shows users (TASK-BE-472). Close the gap: enforce complexity at signup.

## Scope

- **account-service (the signup owner) validates at its boundary**, before the account row is
  created — matching the spec's step ordering (validate at step 5, create at step 6):
  - `domain/account/PasswordPolicy` — mirrors auth-service's
    `domain/credentials/PasswordPolicy` (8..128, ≥3 of {upper,lower,digit,special}, no email
    containment).
  - `domain/account/PasswordPolicyViolationException` — mapped to **422 VALIDATION_ERROR** in
    `GlobalExceptionHandler`.
  - `SignupUseCase.execute` calls `PasswordPolicy.validate(password, email)` after the email-dup
    check.
- **Not** enforced only in auth-service's `CreateCredentialUseCase`, deliberately:
  1. auth-service returns 400 for a policy violation, but account-service maps every non-409 4xx
     from auth-service to **503** — a weak password would be misreported as "service unavailable".
  2. auth-service is called at step 7 (after account+profile+identity creation), so validating
     there relies on transaction rollback and leaks a REQUIRES_NEW identity row.
  3. A weak-password 400 would count against the auth-service **circuit breaker**
     (`ResilienceClientFactory` CB does not ignore 4xx) — routine password typos could trip it.
  Validating at the account-service boundary avoids all three (weak password never calls
  auth-service). auth-service's `PasswordPolicy` remains the credential-write authority for the
  change/reset paths.

## Acceptance Criteria

- `POST /api/accounts/signup` with an 8+char password that has < 3 character classes (e.g.
  `lowercaseonly`) → **422 VALIDATION_ERROR**, no account row created, no auth-service call.
- A valid 3-of-4 password (`test1234!`, `Password1!`) still → 201.
- A password containing the email, or > 128 chars → 422.
- SAS `/signup` (TASK-BE-472 honest message) already renders the 422 as the email-or-password
  guidance — a weak password now shows the (correct) password-policy text.

## Related Specs

- `projects/iam-platform/specs/features/signup.md` (§User Flow 5, §Business Rules, §Edge Cases —
  "패스워드가 이메일과 동일 → 422")

## Related Contracts

- `projects/iam-platform/specs/contracts/http/account-api.md` — `POST /api/accounts/signup`
  (422 VALIDATION_ERROR: 이메일 형식, 패스워드 복잡도 미달)

## Edge Cases

- Length < 8 → still caught first by `SignupRequest @Size(min=8)` as 400 (fast bean-validation
  gate); the boundary policy also covers length as defense.
- Duplicate email + weak password → 409 wins (dup check precedes complexity, per spec ordering).
- `command.email()` null/blank → email-containment check is skipped (defensive).

## Failure Scenarios

- Policy drift between account-service and auth-service copies → both kept byte-identical; a future
  refactor may promote a single `PasswordPolicy` to `libs/java-security` (next to `PasswordHasher`),
  which is a monorepo-level change (root task).
- Message leaks plaintext password → guarded: `PasswordPolicy` messages are rule text only (R4).
