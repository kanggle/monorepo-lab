# TASK-BE-484 — Signup outer read-timeout budget inversion (auth→account) surfaces a false "인증 서비스가 일시적으로 불가합니다"

- **Status**: ready
- **Project**: iam-platform
- **Service**: auth-service (SAS browser signup surface)
- **Type**: bug fix (config)
- **Analysis model**: Opus 4.8 / **구현 권장**: Sonnet (single-line config default + comment)

## Goal

Browser signup (`SignupPageController` `POST /signup`) intermittently surfaces
**"잠시 후 다시 시도해 주세요. 인증 서비스가 일시적으로 불가합니다."** while the account is
**actually created** (backend 201) — so a retry then hits **"이미 가입된 이메일입니다"** (409).
Root cause is a **timeout budget inversion**, not an outage.

Signup is a 3-hop synchronous chain:

```
browser → auth  POST /signup
        → account POST /api/accounts/signup   (@Transactional)
        → auth  POST /internal/auth/credentials  (createCredential → Argon2id)
```

- The **inner** account→auth leg (`account.auth-service.read-timeout-ms`) was raised from 5s to
  **15s** in `TASK-BE-247` to absorb cold-start / GC pauses.
- The **outer** auth→account leg (`auth.account-service.read-timeout-ms`, env
  `ACCOUNT_SERVICE_READ_TIMEOUT`) was **left at 5s**.

So the outer call abandons the request (5s) long before the inner budget (15s + resilience4j
retries) can complete. The account-side `@Transactional` chain goes on to commit
(account + profile + credential), leaving the user with a false failure and a created account.

Measured on the fed-e2e demo host: steady-state signup ≈ **0.35s warm**; a first-request-after-idle
**cold spike ≈ 10s** (cold DB/HTTP keep-alive re-establishment; a cold auth JVM alone adds only
~0.7s, so Argon2id JIT is not the dominant factor). The 5s outer default is exceeded by the cold
spike; the 15s inner default is not.

## Scope

- `projects/iam-platform/apps/auth-service/src/main/resources/application.yml` **only**: raise the
  `auth.account-service.read-timeout-ms` default from `${ACCOUNT_SERVICE_READ_TIMEOUT:5000}` to
  `${ACCOUNT_SERVICE_READ_TIMEOUT:20000}`, with a comment mirroring the TASK-BE-247 rationale and
  documenting the outer ≥ inner budget relationship.
- **Out of scope**: removing the synchronous cross-service Argon2id chain (would require an **async
  signup redesign** — the explicit "beyond that" stance already recorded in the TASK-BE-247 comment);
  idle-spike mitigations (Argon2 startup warmup / HikariCP keepalive).

## Acceptance Criteria

- The `auth.account-service.read-timeout-ms` default is `≥ 15000` (aligned above the inner
  account→auth 15s ceiling); env `ACCOUNT_SERVICE_READ_TIMEOUT` still overrides.
- Browser `POST /signup` on the fed-e2e demo redirects to `/login?registered` without surfacing
  "인증 서비스가 일시적으로 불가합니다", including the first-request-after-idle cold path.
- No auth-service test asserts the previous `5000` default (verified: none does), so no test change
  is required.

## Related Specs

- `projects/iam-platform/specs/features/signup.md` (§User Flow — server-side proxy write path)

## Related Contracts

- `projects/iam-platform/specs/contracts/http/account-api.md` — `POST /api/accounts/signup`
  (201 on success; 409 ACCOUNT_ALREADY_EXISTS on the misleading retry)

## Edge Cases

- Env `ACCOUNT_SERVICE_READ_TIMEOUT` explicitly set (e.g. CI) → override still wins; the default
  change only affects environments that rely on the default (all fed-e2e demos, local dev).
- A genuine account-service outage → the outer call still fails after 20s and the honest
  "일시적으로 불가" message is shown (correct behavior preserved; only the false-positive is removed).
- Inner createCredential hitting its full 15s read timeout → outer 20s still exceeds it, so a slow
  (but succeeding) inner call no longer trips the outer timeout.

## Failure Scenarios

- Outer set **below** the inner 15s again (future regression) → reintroduces the inversion. Mitigation:
  the comment documents the outer ≥ inner invariant at the property.
- Outer set **too high** → a real outage ties up a Tomcat worker longer before failing. Mitigation:
  20s is bounded just above the inner 15s ceiling, not open-ended.
