# Task ID

TASK-BE-330

# Title

ADR-MONO-021 § 3.3 step 2 (D2) — set `account_type` explicitly at provisioning. Self-service signup (`SignupUseCase`) sets `CONSUMER`; enterprise/operator provisioning (`ProvisionAccountUseCase`) sets `OPERATOR`. Carry the value on the `POST /internal/auth/credentials` internal call (mirror of the `tenant_id` carry-through, TASK-BE-313) so the credential row records the type at the moment the account is born rather than relying on the column default.

# Status

done

> **완료 (2026-06-02)**: impl PR #1012 (squash `18bf38d3`). ADR-MONO-021 §3.3 step 2(D2) — `account_type`(CONSUMER\|OPERATOR)을 프로비저닝 경로가 명시 결정해 `POST /internal/auth/credentials` 로 carry. `AuthServicePort.createCredential` 5번째 param `accountType` + `ACCOUNT_TYPE_CONSUMER`/`OPERATOR` 경계 상수 + `SignupUseCase`(self-service)→CONSUMER / `ProvisionAccountUseCase`(enterprise)→OPERATOR (jwt-standard-claims.md L14-19) + `AuthServiceClient` 조건부 body 포함(null→생략→CONSUMER 기본값) + `CreateCredentialRequest`/`Command` optional accountType(@Pattern CONSUMER\|OPERATOR, @Deprecated overload 보존) + `CreateCredentialUseCase`→step1 `Credential.create(..accountType..)` factory + auth-internal.md 계약. tenant_id carry-through(BE-313) 정확 미러. **3차원**(MERGED `18bf38d3`/tip 일치/pre-merge 0 — **Integration global-account-platform PASS = 실 MySQL IT** + E2E smoke gap docker-compose PASS; 전 체크 green). 분석=Opus 4.8 / 구현=Opus. **후속(ADR-021 §3.3)**: D4 step3 INT-023 e2e account_type=CONSUMER 단언.

# Owner

backend

# Task Tags

- code
- security
- multi-tenant

---

# Dependency Markers

- **depends on**: TASK-BE-329 (ADR-MONO-021 § 3.3 step 1, `ebd3d908`) — `credentials.account_type` column + `Credential.create(.., accountType, ..)` factory + `CreateCredentialUseCase` already on `origin/main`. dependency-correct base = current `origin/main`.
- **follow-up (separate task)**: D4 step 3 (TASK-INT-023 GAP-backed e2e `account_type=CONSUMER` assertion).

# Goal

The `account_type` on a freshly-provisioned credential is decided by the provisioning path (CONSUMER for self-service signup, OPERATOR for enterprise provisioning) and carried explicitly to auth-service, rather than depending on the `NOT NULL DEFAULT 'CONSUMER'` column default introduced in step 1.

# Scope

- **`AuthServicePort.createCredential(...)`** — add a 5th parameter `String accountType` + `ACCOUNT_TYPE_CONSUMER`/`ACCOUNT_TYPE_OPERATOR` boundary constants. javadoc the per-account-immutable framing.
- **`AuthServiceClient`** — include `accountType` in the `POST /internal/auth/credentials` JSON body (mirror the `tenantId` conditional-include; include when non-null).
- **`SignupUseCase`** — pass `AuthServicePort.ACCOUNT_TYPE_CONSUMER` (self-service signup = CONSUMER, contract L14-19).
- **`ProvisionAccountUseCase`** — pass `AuthServicePort.ACCOUNT_TYPE_OPERATOR` (company-provisioned enterprise account = OPERATOR, contract L14-19).
- **`CreateCredentialRequest`** (auth-service DTO) — add optional `accountType` field, `@Pattern` CONSUMER|OPERATOR, default CONSUMER when null.
- **`CreateCredentialCommand`** — add `accountType`; retain a `@Deprecated` 4-arg overload defaulting CONSUMER (backward-compat, mirror the tenantId overload).
- **`CreateCredentialUseCase`** — read `accountType` (default CONSUMER), pass to `Credential.create(accountId, tenantId, accountType, email, hash, now)` (the step-1 factory). Idempotent-retry path unchanged (account_type is immutable; a retry does not re-decide it).
- **`InternalCredentialController`** — map `request.accountType()` → command.
- **`specs/contracts/http/internal/auth-internal.md`** — document the optional `accountType` field (CONSUMER|OPERATOR, default CONSUMER, set by the provisioning path).
- **Tests** — `SignupUseCaseTest` (asserts CONSUMER passed), `ProvisionAccountUseCaseTest` (asserts OPERATOR passed), `AuthServiceClientUnitTest` (5-arg + body field), `CreateCredentialUseCase` unit (accountType → credential), `InternalCredentialControllerTest` (request→command mapping), relevant integration tests.

Out of scope: the column/customizer/provider (step 1, done); userinfo (D5, deferred); the consuming gateways (unchanged); e2e assertion (step 3, follow-up).

# Acceptance Criteria

- **AC-1** A self-service `SignupUseCase` flow carries `accountType=CONSUMER` to `/internal/auth/credentials` and the persisted credential row is CONSUMER.
- **AC-2** A `ProvisionAccountUseCase` (enterprise provisioning) flow carries `accountType=OPERATOR` and the persisted credential row is OPERATOR.
- **AC-3** `CreateCredentialRequest.accountType` absent → defaults to CONSUMER (backward-compat; no caller regresses).
- **AC-4** Invalid `accountType` (not CONSUMER|OPERATOR) → 400 (bean-validation `@Pattern`) and/or domain `IllegalArgumentException` — never persisted.
- **AC-5** Idempotent-retry path (existing `(accountId,email)`) does not change the stored `account_type`.
- **AC-6** All existing auth-service + account-service tests GREEN; `:auth-service:integrationTest` + `:account-service:integrationTest` GREEN.

# Related Specs

- `docs/adr/ADR-MONO-021-account-type-claim-source.md` § 2 D2 + § 3.3 step 2
- `platform/contracts/jwt-standard-claims.md` L14-19 (CONSUMER=self-registered / OPERATOR=company-provisioned)
- `projects/global-account-platform/specs/contracts/http/internal/auth-internal.md`

# Related Contracts

- `projects/global-account-platform/specs/contracts/http/internal/auth-internal.md` (POST /internal/auth/credentials — adds optional accountType).
- `platform/contracts/jwt-standard-claims.md` § account_type.

# Edge Cases

- Legacy callers omitting `accountType` → column/command default CONSUMER (backward-compat).
- Social signup (`SocialSignupUseCase`) creates NO credential (no password) → not in scope; social accounts get a credential only if they later set a password (separate path, defaults CONSUMER).
- `accountType` is per-account immutable — the idempotent-retry path must not mutate it.

# Failure Scenarios

- If `ProvisionAccountUseCase` did not pass OPERATOR, enterprise-provisioned operators would persist as CONSUMER and 403 at an OPERATOR-gated path → AC-2 + the explicit set guard it.
- Wrong-type carried (CONSUMER where OPERATOR meant) → that account 403s at its gateway; AC-1/AC-2 guard it.
