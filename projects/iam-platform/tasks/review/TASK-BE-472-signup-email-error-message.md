# TASK-BE-472 — Signup error message misattributes an invalid-email 400 to the password

- **Status**: review
- **Project**: iam-platform
- **Service**: auth-service (SAS browser signup surface)
- **Type**: bug fix
- **Analysis model**: Opus 4.8 / **구현 권장**: Sonnet (single-file presentation fix)

## Goal

When a user submits the browser signup form (`POST /signup`, `SignupPageController`) with a
**malformed email** (e.g. `test@test`, `justusername`), account-service rejects it with **400**
(the `Email` value-object regex `^…@…\.[a-zA-Z]{2,}$` requires a real TLD). The SAS proxy maps
every 400/422 to `SignupInvalidException` and renders a **hardcoded message that only blames the
password**:

> 입력값을 확인해 주세요. 패스워드는 8자 이상, 대문자·소문자·숫자·특수문자 중 3종 이상이어야 합니다.

So a user who typed a valid password (e.g. `test1234!` — accepted end-to-end, proven with a live
201 / 302) but a bad email is told their **password** is wrong. Fix the misattribution.

## Scope

- `SignupPageController.submitSignup` only. Two changes:
  1. Add an **email-format pre-check** mirroring account-service's `Email` regex, so a malformed
     email produces a precise message ("이메일 형식이 올바르지 않습니다.") and never reaches the proxy.
  2. Make the `SignupInvalidException` catch-all message **honest** — a residual 400/422 can be an
     email *or* password problem; the message must name both rather than blaming the password only.
- Update `SignupPageControllerTest`.
- **Out of scope (→ TASK-BE-473)**: wiring `PasswordPolicy` (8자 + 3종) into the signup write path.
  The spec (`specs/features/signup.md` §5, Business Rules) requires it but `CreateCredentialUseCase`
  never called it, so weak passwords were silently *accepted* at signup. Handled by the sibling
  follow-up TASK-BE-473 (account-service boundary enforcement → 422 VALIDATION_ERROR).

## Acceptance Criteria

- `POST /signup` with a malformed email (no `@`, no TLD, whitespace) re-renders `signup` with
  "이메일 형식이 올바르지 않습니다." and **does not** call `AccountServicePort.signup`.
- `POST /signup` with a valid email + valid password still redirects to `/login?registered`.
- The `SignupInvalidException` branch message references both email format and password policy.
- A valid password (`test1234!`, `Str0ng!pass`) is never reported as invalid when the real fault is
  the email.

## Related Specs

- `projects/iam-platform/specs/features/signup.md` (§User Flow 4–5, §Business Rules, §Edge Cases)

## Related Contracts

- `projects/iam-platform/specs/contracts/http/account-api.md` — `POST /api/accounts/signup`
  (400 VALIDATION_ERROR on malformed email; 409 ACCOUNT_ALREADY_EXISTS)

## Edge Cases

- Email valid but already registered → 409 → "이미 가입된 이메일입니다." (unchanged).
- Email malformed **and** password short → email pre-check fires first (order preserved: empty →
  email format → password length → confirm match).
- Unicode/IDN domains → out of scope (account-service is ASCII-only per spec); pre-check regex stays
  ASCII to match account-service exactly (no false accept that the backend would then 400).

## Failure Scenarios

- Pre-check stricter than account-service → would reject an email the backend accepts. Mitigation:
  reuse the **exact** account-service `Email` regex.
- Pre-check more lenient → a bad email slips to the proxy and hits the (now honest) generic 400
  message — acceptable degradation, still names the email.
