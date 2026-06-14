# Task ID

TASK-BE-368

# Title

account-service internal **roles read EP** + auth-service `AccountServicePort` extension — the roles source for JWT issuance (ADR-MONO-033 S2 / ADR-MONO-032 D5 step 2, sub-step 1).

# Status

done

> **완료 (2026-06-14)**: PR #1528 squash `b8780c54` (CI 전건 green incl. iam Testcontainers IT, 3-dim verified). account-service `GET /internal/tenants/{tid}/accounts/{aid}/roles`(least-data, read-only, missing/foreign→200 {roles:[]}, 403 tenant-mismatch) + `GetAccountRolesUseCase`+`AccountRolesResponse`; auth `AccountServicePort.listAccountRoles`+`AccountServiceClient`(listEntitledDomains 미러, 4xx/5xx→AccountServiceUnavailableException). additive net-zero(커스터마이저 미배선). 계약-first. close-chore(ready→done)=후속 TASK-BE-369 PR 과 동반. 구현=Sonnet 서브에이전트 + 오케스트레이터 검증.

# Owner

backend

# Task Tags

- backend
- iam
- security
- additive

---

# Dependency Markers

- **implements**: ADR-MONO-033 (ACCEPTED) S2 + § 3.3 execution roadmap task 1 — the account-service read surface + auth-service port plumbing that later sub-steps (BE customizer `roles` leg) consume.
- **child of**: ADR-MONO-032 D5 step 2 (roles-only issuance). This sub-step is **additive / net-zero**: a new read EP + a new (as-yet-uncalled-by-the-customizer) port method. No issuance behavior changes until the customizer leg lands (separate task).
- **contract-first**: `projects/iam-platform/specs/contracts/http/internal/account-internal-provisioning.md` § `GET .../roles` (added in this task, precedes the code per the repo contract rule).
- **mirrors**: the `entitled_domains` keystone edge (`AccountServiceClient.listEntitledDomains` + `account-tenant-domain-subscriptions.md`) — same resilience config, same `AccountServiceUnavailableException` surface, caller decides fail-soft.

# Goal

Give auth-service a **least-data** way to read an account's role names at token-issue time, so the (subsequent) `TenantClaimTokenCustomizer` `roles` leg can populate the signed `roles` claim from the authoritative `account_roles` store (ADR-033 S2) rather than over-fetching the full account (PII) or deriving roles blindly.

# Scope

**Contract (first):**
- `account-internal-provisioning.md` — add `GET /internal/tenants/{tenantId}/accounts/{accountId}/roles` → `{ accountId, tenantId, roles: string[] }`. Read-only (no audit, no outbox). 403 `TENANT_SCOPE_DENIED` / 401 only; missing/foreign account → `200 {roles: []}` (no 404). **DONE in this task.**

**account-service (apps/account-service):**
- `GetAccountRolesUseCase` (application/service) — `List<String> execute(TenantId, accountId)` via `AccountRoleRepository.findByTenantIdAndAccountId`, mapping `AccountRole` → role name. Empty list when none / foreign account (tenant-scoped read).
- `AccountRolesResponse` DTO (`accountId`, `tenantId`, `roles`).
- `@GetMapping("/roles")` on `AccountRoleController` (base path `/internal/tenants/{tenantId}/accounts/{accountId}`), reusing `validateTenantScope` (403 on mismatch). Returns the response.
- Unit/web-slice test: returns roles for an account; `[]` for an account with no roles / foreign tenant; 403 on tenant-scope mismatch.

**auth-service (apps/auth-service):**
- `AccountServicePort.listAccountRoles(String tenantId, String accountId)` → `List<String>` (javadoc: issuance roles source; throws `AccountServiceUnavailableException` on failure — caller fail-softs).
- `AccountServiceClient.listAccountRoles` — `GET /internal/tenants/{tid}/accounts/{aid}/roles`, IAM `client_credentials` Bearer (`tokenProvider.currentBearer()`), resilience (retry + circuit-breaker, identical to `listEntitledDomains`), extract `roles[]`, 4xx/5xx → `AccountServiceUnavailableException`.
- Client unit test mirroring the `listEntitledDomains` test shape (happy path + failure → exception).

**Out of scope (separate tasks):** the `TenantClaimTokenCustomizer` `roles` leg (S4 base token), assume-tenant augmentation (S4), the aud-scoping convention + seed (S3 — lands with the customizer leg), the `account_roles` aud column (S3 deferred).

# Acceptance Criteria

- **AC-1** Contract `GET .../roles` documented before code (contract-first), least-data shape, read-only, no-404 semantics.
- **AC-2** account-service serves `GET /internal/tenants/{tid}/accounts/{aid}/roles` returning the account's role names; `[]` for none/foreign; 403 on tenant-scope mismatch; no audit row / outbox event emitted.
- **AC-3** auth-service `AccountServicePort.listAccountRoles` + `AccountServiceClient` impl exist, authenticate via IAM `client_credentials` Bearer, and surface `AccountServiceUnavailableException` on failure (caller fail-soft contract).
- **AC-4** Net-zero: the new port method is **not yet wired into the customizer** — no token's claims change as a result of this task.
- **AC-5** `./gradlew :iam-platform:account-service:check :iam-platform:auth-service:check` (or the project's equivalent module paths) GREEN locally for the Docker-free unit/slice layer. (Testcontainers `@SpringBootTest` IT is host-Docker-blocked locally — left to CI Linux, per the standing pattern.)

# Related Specs

- `platform/contracts/jwt-standard-claims.md` (§ Standard Claims `roles` — the target the issuance must satisfy)
- `docs/adr/ADR-MONO-033-roles-issuance-resolution-model.md` (S2 store + read EP, S5 fail-soft)
- `projects/iam-platform/specs/services/account-service/architecture.md` (the internal provisioning surface owner)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/internal/account-internal-provisioning.md` § `GET .../roles` (added here)
- `projects/iam-platform/specs/contracts/http/internal/account-tenant-domain-subscriptions.md` (the `entitled_domains` sibling edge mirrored for resilience + fail-soft)

# Edge Cases

- Account with no roles → `200 {roles: []}` (not 404).
- Foreign account (accountId belongs to another tenant) → tenant-scoped repository read returns nothing → `200 {roles: []}` (enumeration-safe; the path `{tenantId}` already gated by `validateTenantScope` when the caller carries a tenant scope).
- Caller tenant-scope mismatch (`X-Tenant-Id` ≠ path) → 403 `TENANT_SCOPE_DENIED` (defense-in-depth, mirrors sibling EPs).
- account-service down / circuit-open / timeout → `AccountServiceUnavailableException` (the customizer leg, a later task, fail-softs to the aud-default seed).

# Failure Scenarios

- If the read EP returns the full account (email/displayName) instead of the least-data roles projection → over-fetches PII into the issuance hot path (violates ADR-033 S2 least-data intent). Return only `{accountId, tenantId, roles}`.
- If the EP 404s on a missing account → breaks the fail-soft read semantics (caller would treat 404 as failure, which is acceptable, but the contract specifies `200 {roles: []}` for enumeration defense). Return empty.
- If the new port method is wired into the customizer in this task → violates the net-zero staging (S6 sub-step 1 is additive only). Defer the customizer leg.
- If the EP writes an audit row / outbox event → it is read-only (no side effect). Must not emit.
