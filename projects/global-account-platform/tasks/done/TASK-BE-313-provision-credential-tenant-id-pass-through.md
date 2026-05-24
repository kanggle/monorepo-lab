# Task ID

TASK-BE-313

# Title

account-service `ProvisionAccountUseCase` → `AuthServiceClient.createCredential` omits `tenantId` — credential row in `auth_db.credentials` always falls back to `"fan-platform"` regardless of the `accounts.tenant_id` of the just-provisioned row. Surfaces as `TenantProvisioningE2ETest` 4 failures (`step2_wms_login` returns 401 + steps 3-5 cascade-skip) — has been failing for ~100+ consecutive nightlies (`@Tag("full")` so not surfaced in PR CI). Fix: thread `tenantId` from `ProvisionAccountCommand.tenantId()` → `AuthServicePort.createCredential(...)` body so the credential row matches the account row's tenant scope.

# Status

done

# Owner

backend

# Task Tags

- code
- fix
- e2e

---

# Dependency Markers

- **depends on**: TASK-BE-229 (auth-service credential tenant_id column + `CreateCredentialRequest.tenantId` field; the field exists, just unused from the account-service side).
- **prerequisite of**: nightly main GREEN restoration of GAP docker-compose e2e job. After fix, `TenantProvisioningE2ETest` 5/5 PASS; combined with PC-FE-031 fix this leaves nightly cron 100% GREEN.

---

# Goal

After this fix lands, `TenantProvisioningE2ETest` 5/5 PASS:
- step1 (PASSES today): WMS tenant provisioning returns 201 ✓
- **step2** (FAILS today, 401): WMS login via gateway returns 200 + accessToken
- **step3** (cascade-fail today): JWT decoded payload contains `tenant_id="wms"` + `tenant_type="B2B_ENTERPRISE"`
- **step4** (cascade-fail today): protected endpoint returns 200 + X-Tenant-Id propagated
- **step5** (cascade-fail today): WMS JWT on fan-platform endpoint returns 403 TENANT_SCOPE_DENIED

## Root cause evidence

Static call-graph trace (no diagnostic dispatch needed — root cause obvious from code):

1. `ProvisionAccountUseCase.execute(command)` (line 94): `authServicePort.createCredential(account.getId(), account.getEmail(), command.password())` — **3 args, no tenantId**.
2. `AuthServicePort.createCredential` interface signature (line 23 of `application/port/AuthServicePort.java`): `void createCredential(String accountId, String email, String password)` — **no tenantId parameter**.
3. `AuthServiceClient.doCreateCredential` (line 89-94): builds body `Map.of("accountId", ..., "email", ..., "password", ...)` — **no tenantId field**.
4. `InternalCredentialController.createCredential` (line 47-54): `new CreateCredentialCommand(request.accountId(), request.email(), request.password(), request.tenantId())` — `request.tenantId()` is null (DTO field exists but caller never populates).
5. `CreateCredentialUseCase.execute(command)` (line 52): `String tenantId = command.tenantId() != null ? command.tenantId() : "fan-platform"` — null fallback to `"fan-platform"`.
6. Credential row in `auth_db.credentials` written with `tenant_id="fan-platform"`.
7. Login attempt with email/password (no tenantId): cross-tenant lookup finds 1 credential (only one matches the unique e2e email) with `tenant_id="fan-platform"`. Password verifies ✓. But then `accountServicePort.getAccountStatus(accountId)` back-call (LoginUseCase line 121) — likely the failure surface (account-service's tenant scope check on the back-call fails because credential's resolvedTenantId="fan-platform" but account's tenant="wms").

The root cause is the missing tenantId pass-through. Once credentials.tenant_id matches accounts.tenant_id, the back-call's tenant scope check passes, login succeeds, JWT carries the correct tenant claim.

## Hypothesis pool — narrowed by static evidence

Single confirmed hypothesis (no diagnostic dispatch needed): **AuthServiceClient omits tenantId field, causing credential row to default to "fan-platform" instead of inheriting from provisioning request's tenantId**. Confident-fix path.

---

# Scope

## In Scope

- `projects/global-account-platform/apps/account-service/src/main/java/com/example/account/application/port/AuthServicePort.java` — add `tenantId` parameter to `createCredential` signature.
- `projects/global-account-platform/apps/account-service/src/main/java/com/example/account/application/service/ProvisionAccountUseCase.java` — pass `command.tenantId()` to `authServicePort.createCredential(...)`.
- `projects/global-account-platform/apps/account-service/src/main/java/com/example/account/application/service/SignupUseCase.java` — sibling call site (line 56); pass tenantId (signup is public/fan-platform — same flow but with default tenant).
- `projects/global-account-platform/apps/account-service/src/main/java/com/example/account/infrastructure/client/AuthServiceClient.java` — extend body map with `tenantId` field; update method signature.
- Any tests / mocks of `AuthServicePort` that need signature update (likely 1-2 sites).
- This task md + project `tasks/INDEX.md` ready entry.

## Out of Scope

- `auth-service` production code (CreateCredentialRequest DTO + CreateCredentialUseCase ALREADY accept tenantId; defaults to "fan-platform" only when null — fix is purely caller-side).
- TenantProvisioningE2ETest spec change (test is correct; production code is wrong).
- LoginController sunset (separate TASK-BE-3xx for 2026-08-01).
- Other 6 producers + console-bff + workflow + docker-compose (zero-retrofit).

---

# Acceptance Criteria

- [ ] **AC-1 (functional, primary)** — Next nightly `workflow_dispatch` run on the fix branch results in GAP docker-compose e2e job SUCCESS; `TenantProvisioningE2ETest` 5/5 PASS (steps 1-5 all PASS). Verified via `gh run view <id>` JUnit reporter output.
- [ ] **AC-2 (functional, secondary)** — Credential row written by `ProvisionAccountUseCase` carries `tenant_id` matching the account row's `tenant_id` (verified by Step 3 JWT payload assertion `tenant_id="wms"`).
- [ ] **AC-3 (regression check — SignupUseCase)** — Public signup flow (`/api/auth/signup`) continues to work; existing IT cases (`SignupIntegrationTest` or similar) continue to pass. The SignupUseCase's command.tenantId() may be null (public signup defaults to `"fan-platform"` per the original architecture); the fix preserves null → fallback default behavior in `CreateCredentialUseCase` line 52.
- [ ] **AC-4 (hard invariant — auth-service byte-unchanged)** — `git diff --stat origin/main -- projects/global-account-platform/apps/auth-service/` = empty. The CreateCredentialRequest DTO + CreateCredentialUseCase already support tenantId; no auth-service change needed.
- [ ] **AC-5 (hard invariant — 5 other producers byte-unchanged)** — `git diff --stat origin/main -- 'projects/{wms,scm,erp,fan,ecommerce-microservices,finance,platform-console}-platform/'` = empty (**29th zero-retrofit**).
- [ ] **AC-6 (hard invariant — workflow + docker-compose byte-unchanged)** — `git diff --stat origin/main -- .github/workflows/ projects/global-account-platform/docker-compose*.yml` = empty.
- [ ] **AC-7 (regression check — push CI)** — push CI `Integration (global-account-platform, Testcontainers)` GREEN; existing IT for ProvisionAccountUseCase + SignupUseCase + AuthServiceClient continue to pass; new IT case anchors the tenantId pass-through invariant.
- [ ] **AC-8 (BE-303 3-dim merge verification)** — close chore PR authored only after impl PR's 3-dim verification passes.

---

# Related Specs

- [`projects/global-account-platform/specs/contracts/http/internal/auth-internal.md`](../../specs/contracts/http/internal/auth-internal.md) — `POST /internal/auth/credentials` contract (tenantId field already defined per TASK-BE-229).
- [`projects/global-account-platform/specs/services/account-service/`](../../specs/services/account-service/) — provisioning + signup write-path.
- [`projects/global-account-platform/specs/services/auth-service/`](../../specs/services/auth-service/) — credential lookup + login.
- [`projects/global-account-platform/tests/e2e/src/test/java/com/example/e2e/TenantProvisioningE2ETest.java`](../../tests/e2e/src/test/java/com/example/e2e/TenantProvisioningE2ETest.java) — failing test (will PASS after fix).

# Related Contracts

- `auth-internal.md` `POST /internal/auth/credentials` request body — tenantId field already documented; this fix makes the caller actually populate it.

# Related Skills

- None additional.

---

# Edge Cases

- **SignupUseCase null tenantId** — public signup creates accounts in the default tenant; `command.tenantId()` may be null. Fix preserves the null → "fan-platform" fallback in `CreateCredentialUseCase` line 52 (no change to that line).
- **Idempotency on retry** — `CreateCredentialUseCase` line 56-69 finds existing row by accountId; same accountId + new tenantId would NOT trigger CredentialAlreadyExistsException (only email mismatch does). Pre-existing rows from before this fix would remain `tenant_id="fan-platform"`; this is an in-place migration concern but acceptable for e2e (each test uses a fresh UUID email).

# Failure Scenarios

- **AC-1 fails after fix** — back-call from auth-service to account-service has additional tenant scope enforcement that fails on the new tenant_id. Add diagnostic logging to LoginUseCase line 121 to surface the failure mode. Likely 1-iter investigation.
- **AC-3 SignupUseCase regression** — null tenantId pass-through breaks signup flow. Defensive: keep signup call site unchanged (pass null explicitly) OR update SignupUseCase IT to assert null tenantId path.

---

# Test Requirements

- Existing IT (`ProvisionAccountUseCaseIT` / `AuthServiceClientIT` / `SignupUseCaseIT`) continue to pass.
- Add 1 new IT case asserting that a credential created via ProvisionAccountUseCase carries the matching tenant_id (read DB after provision call; assert credentials.tenant_id == accounts.tenant_id).
- AC-1 verification = `workflow_dispatch on fix branch` (~30 min).

---

# Definition of Done

- [ ] Three PRs landed in order: spec PR, impl PR, close-chore PR.
- [ ] AC-1 through AC-8 all checked off in close-chore PR description.
- [ ] Nightly main GREEN restored on next scheduled cron after merge.

---

# 메타 (intended)

① **First "non-cycle-pattern" task post-cycle TERMINAL** — TASK-MONO-014 cycle chain ended at PC-FE-031 (13 layers, both classes); this task addresses a pre-existing nightly regression unrelated to the cycle chain. Classified as a "long-standing backlog drain" rather than a cycle layer.

② **`@Tag("full")` invisibility surface** — TenantProvisioningE2ETest has been failing for 100+ consecutive nightlies but never blocked PR CI because of the `@Tag("full")` filter. Lesson: any test that exercises critical production flow (auth + tenant + JWT chain) should have at least one PR-time smoke variant; nightly-only tests can rot silently. Future refactor candidate: split this test into a PR-time smoke (steps 1-2) + nightly full (steps 3-5).

③ **Static-evidence confident-fix (PC-FE-031 ㉛ pattern carry-over)** — call graph trace from `ProvisionAccountUseCase.execute` → `AuthServiceClient.doCreateCredential` → `InternalCredentialController.createCredential` → `CreateCredentialUseCase.execute` line 52 fallback gives 100% confidence on root cause. No diagnostic dispatch needed; iter 1 = fix + tests.

④ **29회째 zero-retrofit invariant** — fix lives entirely within `projects/global-account-platform/apps/account-service/`. AC-4 (auth-service byte-unchanged) preserved because the CreateCredentialRequest DTO + CreateCredentialUseCase already support tenantId (added in TASK-BE-229).

⑤ **Memory update post-cycle** — after BE-313 close, audit-memory cycle to capture: nightly cron 100% GREEN restored, `@Tag("full")` invisibility surface lesson, post-cycle backlog drain pattern.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical 1-arg pass-through across 4 files + 1 new IT case; no architectural decisions).
