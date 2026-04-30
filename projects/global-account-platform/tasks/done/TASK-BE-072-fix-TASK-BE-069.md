# Task ID

TASK-BE-072

# Title

fix(auth-service): Remove HTTP calls from inside @Transactional in OAuthLoginTransactionalStep (follow-up to TASK-BE-069)

# Status

ready

# Owner

backend

# Task Tags

- code
- fix

# depends_on

- TASK-BE-069

---

# Goal

TASK-BE-069 restructured `OAuthLoginUseCase#callback` to move external provider HTTP (token exchange + userinfo) outside `@Transactional`. However, `OAuthLoginTransactionalStep#persistLogin` — which owns the `@Transactional` boundary — still makes two internal HTTP calls to account-service via `AccountServicePort`:

1. `accountServicePort.socialSignup(...)` — HTTP POST to account-service (new identity path)
2. `accountServicePort.getAccountStatus(...)` — HTTP GET to account-service (status check)

Both calls hold the Hikari DB connection open during network I/O, reproducing the same connection-pinning pattern that TASK-BE-069 intended to eliminate. This task removes those HTTP calls from inside the `@Transactional` scope.

---

# Scope

## In Scope

1. **Move HTTP calls out of `OAuthLoginTransactionalStep#persistLogin`**:
   - `accountServicePort.socialSignup(...)` (only called on new identity path)
   - `accountServicePort.getAccountStatus(...)`
   Both must be invoked by `OAuthLoginUseCase#callback` (the orchestrator) BEFORE calling `oAuthLoginTransactionalStep.persistLogin(...)`.

2. **Extend `OAuthCallbackTxnCommand`** to carry the pre-fetched results:
   - `accountId` (resolved either from existing `SocialIdentityJpaEntity` or from `socialSignup` response)
   - `isNewAccount` (boolean — from `socialSignup` result or `false` for existing identity)
   - `accountStatus` (String — from `getAccountStatus`, nullable/Optional if account-service returned empty)

   Alternative approach: introduce a separate pre-resolution step / intermediate result object. Implementation choice is left to the implementor, but the constraint is that no HTTP call to `AccountServicePort` occurs inside `persistLogin`.

3. **Adjust `OAuthLoginUseCase#callback` orchestration**:
   - Look up `SocialIdentityJpaEntity` existence — NOTE: this is a DB read. Determine whether moving the identity lookup to the use-case (outside txn) is safe and document the decision. Acceptable alternatives:
     a. Keep the identity lookup inside the txn step (DB read without external HTTP is acceptable), and perform `socialSignup` HTTP in the use-case only when needed (requires a pre-check or a two-phase design).
     b. Perform `socialSignup` speculatively/idempotently from the use-case, always passing the resolved `accountId` into the txn command.
   - After HTTP calls complete, pass all resolved data via the extended `OAuthCallbackTxnCommand` to `persistLogin`.

4. **Account status check**: `getAccountStatus` HTTP call moves to `OAuthLoginUseCase#callback`. The resolved status (or empty Optional) is passed to `persistLogin` which performs the status guard check using the pre-fetched value — no additional HTTP inside the txn.

5. **Unit tests** — update existing tests and add new cases:
   - `OAuthLoginUseCaseTest`: verify `accountServicePort.socialSignup` and `accountServicePort.getAccountStatus` are called BEFORE `oAuthLoginTransactionalStep.persistLogin` (use `InOrder`).
   - `OAuthLoginTransactionalStepTest`: verify `AccountServicePort` is no longer a dependency (remove mock; assert no HTTP calls from inside `persistLogin`).

6. **Architecture note** in `specs/services/auth-service/architecture.md`: update the TASK-BE-069 note to reflect that both external provider HTTP and internal account-service HTTP are performed outside `@Transactional`.

## Out of Scope

- Testcontainers setup changes (TASK-BE-070)
- Re-enabling `OAuthLoginIntegrationTest` (TASK-BE-071)
- OAuth provider changes
- Other use-cases

---

# Acceptance Criteria

- [ ] `OAuthLoginTransactionalStep#persistLogin` has NO calls to `AccountServicePort` (neither `socialSignup` nor `getAccountStatus`)
- [ ] `AccountServicePort` is NOT a constructor-injected dependency of `OAuthLoginTransactionalStep`
- [ ] `OAuthCallbackTxnCommand` (or a replacement) carries `accountId`, `isNewAccount`, and pre-resolved account status
- [ ] `OAuthLoginUseCase#callback` invokes `accountServicePort.socialSignup` (when applicable) and `accountServicePort.getAccountStatus` BEFORE calling `oAuthLoginTransactionalStep.persistLogin`
- [ ] `OAuthLoginUseCaseTest` has `InOrder` verification that HTTP calls (provider + account-service) precede `persistLogin`
- [ ] `OAuthLoginTransactionalStepTest` has NO `AccountServicePort` mock — the dependency does not exist in the bean
- [ ] `./gradlew :apps:auth-service:test` green (all unit/slice tests pass)
- [ ] Architecture note updated in `specs/services/auth-service/architecture.md`

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/features/authentication.md`
- `platform/event-driven-policy.md`

---

# Related Contracts

- `specs/contracts/events/auth-events.md` (auth.login.succeeded timing — must not change)

---

# Target Service

- `apps/auth-service`

---

# Architecture

Layered 4-layer. Change scope: application layer only (`OAuthLoginUseCase`, `OAuthLoginTransactionalStep`, `OAuthCallbackTxnCommand`). Infrastructure layer (`AccountServiceClient`) unchanged.

---

# Edge Cases

- **Existing identity path**: `socialSignup` is NOT called. The `accountId` is read from `SocialIdentityJpaEntity`. If this DB read moves outside the txn, TOCTOU risk between read and txn write must be documented and accepted (upsert semantics in the txn step guard against duplicate inserts).
- **`socialSignup` idempotency**: account-service `socialSignup` is idempotent for the same email+provider combination. If the use-case calls it before the txn and the txn fails, calling `socialSignup` again on retry is safe.
- **`getAccountStatus` returns empty**: treat as account unavailable — propagate the same error as the current implementation.
- **Account status check timing**: status is fetched before txn begins; a concurrent status change between the HTTP call and txn commit is tolerable (same risk as current implementation).

---

# Failure Scenarios

- `socialSignup` or `getAccountStatus` HTTP fails before txn starts → exception propagates; no DB writes performed (correct fail-closed behaviour).
- DB txn fails after HTTP calls succeed → user sees login failure; provider token already issued (documented in compensation note — unchanged from TASK-BE-069).
- `AccountServicePort` still injected into `OAuthLoginTransactionalStep` → unit test will fail (no mock provided; bean construction will error or mock verification will catch it).

---

# Test Requirements

- **Unit** (`OAuthLoginUseCaseTest`): `InOrder` verifying `oAuthClient.exchangeCodeForUserInfo` → `accountServicePort.socialSignup` (new account path) or `accountServicePort.getAccountStatus` → `oAuthLoginTransactionalStep.persistLogin`
- **Unit** (`OAuthLoginTransactionalStepTest`): `AccountServicePort` removed from `@Mock` list; assert bean can be instantiated and `persistLogin` runs without any `AccountServicePort` call

---

# Definition of Done

- [ ] No `AccountServicePort` call inside `@Transactional` scope of `OAuthLoginTransactionalStep`
- [ ] All unit tests pass (`./gradlew :apps:auth-service:test` green)
- [ ] Architecture note updated
- [ ] Ready for review
