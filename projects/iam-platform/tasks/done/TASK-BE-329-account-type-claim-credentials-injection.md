# Task ID

TASK-BE-329

# Title

ADR-MONO-021 § 3.3 step 1 (D1+D3) — emit the platform-required `account_type` (CONSUMER|OPERATOR) OIDC claim. Add `account_type` to `auth_db.credentials` (per-account, denormalized — mirrors `tenant_id`), read it in `CredentialAuthenticationProvider`, and inject it on the access + id token via `TenantClaimTokenCustomizer`. Un-breaks the ecommerce gateway `AccountTypeEnforcementFilter`, which 403s every authenticated request when the claim is absent.

# Status

done

> **완료 (2026-06-02)**: impl PR #1010 (squash `ebd3d908`; backend-engineer opus dispatch + dispatcher 검토·컴파일 재검증). ADR-MONO-021 §3.3 step 1(D1+D3) — GAP `account_type`(CONSUMER\|OPERATOR) OIDC 클레임 발급. V0022 `credentials.account_type VARCHAR(20) NOT NULL DEFAULT 'CONSUMER'`(per-account denormalize, tenant_id/V0007 패턴) + Credential 도메인(신규 canonical 10-arg + `@Deprecated` 9-arg 하위호환[기존 호출자 무파손] + `normalizeAccountType` CONSUMER\|OPERATOR 검증) + CredentialJpaEntity + CredentialAuthenticationProvider details-map + TenantClaimTokenCustomizer 주입(authcode/refresh access+id / **assume-tenant 보존**[operator OPERATOR 유지, AssumeTenantAuthenticationToken 6-arg] / **client_credentials 미주입**[workload≠account]) + operator 시드 OPERATOR(federation-e2e + console-web e2e fixtures). **동기**: ecommerce gateway `AccountTypeEnforcementFilter`가 클레임 부재 시 모든 인증요청 403(`"CONSUMER".equals(null)=false`) → net-positive un-break. unit 42 GREEN(Credential 8/Provider 2/Customizer 26/AssumeTenant 6) + IT 갱신(FormLoginIT account_type=CONSUMER access+id / CredentialJpaRepositoryTest 컬럼 default+OPERATOR round-trip). **3차원**(MERGED `ebd3d908`/tip 일치/pre-merge 0 — **Integration global-account-platform PASS = 실 MySQL IT 검증**; 전 IT green=console-bff 등 무회귀). 분석=Opus 4.8 / 구현=Opus. **후속(ADR-021 §3.3)**: D2 provisioning explicit-set(signup→CONSUMER/operator→OPERATOR) + D4 step3 INT-023 e2e account_type 단언.

# Owner

backend

# Task Tags

- code
- security
- multi-tenant

---

# Dependency Markers

- **depends on**: ADR-MONO-021 ACCEPTED (TASK-MONO-165 `6bbdca08`) § 2 D1 (per-account on `credentials`) + D3 (access+id-token injection) + § 3.3 step 1. dependency-correct base = current `origin/main`.
- **follow-ups (separate tasks)**: D2 provisioning (account-service signup → CONSUMER explicit / operator-provision → OPERATOR); D4 step 3 (TASK-INT-023 e2e `account_type=CONSUMER` assertion).

# Goal

GAP issues `account_type` on access + id tokens for credential (form-login) grants, sourced per-account from `credentials`, so the contract-required claim is present and the gateway account-type enforcement works.

# Scope

- **Flyway** `V0022__add_account_type_to_credentials.sql` — `ALTER TABLE credentials ADD COLUMN account_type VARCHAR(20) NOT NULL DEFAULT 'CONSUMER'`. (Default CONSUMER per ADR D4; OPERATOR rows are set by their provisioning/seed — see seed update below.)
- **Domain** `Credential` + **entity** `CredentialJpaEntity` — add `accountType` (immutable; default CONSUMER). Keep value-object discipline consistent with the existing fields.
- **`CredentialAuthenticationProvider`** — add `account_type` to the `Authentication.getDetails()` map (alongside `tenant_id`/`tenant_type`/`account_id`), read from the resolved credential.
- **`TenantClaimTokenCustomizer`** — inject `account_type` claim on BOTH access token and id_token for `authorization_code` + `refresh_token` grants, read from the principal details (exact mirror of the `tenant_id` injection). `client_credentials` (workload) → NO `account_type`. `token_exchange` (assume-tenant, ADR-020 D2) → PRESERVE the operator's `account_type` (carry it on the `AssumeTenantAuthenticationToken` / read from the source as tenant_id is).
- **Operator seed correctness** — `tests/federation-hardening-e2e/fixtures/seed.sql` operator credential INSERTs set `account_type='OPERATOR'` (else the column default makes seeded operators CONSUMER). Same for any other operator-credential seed.
- **Tests** — domain/entity unit (account_type round-trip + default); `CredentialAuthenticationProvider` details-map unit; `TenantClaimTokenCustomizer` injection unit (access+id present, client_credentials absent, assume-tenant preserved); update any existing token-claim IT that asserts the full claim set.

Out of scope: account-service signup explicit-set (D2, follow-up); userinfo (D5, deferred); the consuming gateways (unchanged — they already read the claim).

# Acceptance Criteria

- **AC-1** A CONSUMER credential's authorization_code token carries `account_type=CONSUMER` on access + id token.
- **AC-2** An OPERATOR-typed credential's token carries `account_type=OPERATOR`.
- **AC-3** `client_credentials` (workload) tokens carry NO `account_type`.
- **AC-4** assume-tenant (ADR-020 D2) token preserves the operator's `account_type=OPERATOR`.
- **AC-5** Existing auth-service tests GREEN; federation-e2e operator seed sets OPERATOR (no operator regresses to CONSUMER). `:auth-service:integrationTest` GREEN.
- **AC-6** Migration is forward-only, idempotent-safe (column add), `NOT NULL DEFAULT 'CONSUMER'`.

# Related Specs

- `platform/contracts/jwt-standard-claims.md` (account_type required; gateway rules)
- `projects/global-account-platform/specs/services/auth-service/architecture.md` + `data-model.md`

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` § account_type.

# Edge Cases

- Existing credential rows (none in prod; e2e/seed only) → default CONSUMER on migrate; operator seeds explicitly OPERATOR.
- account_type values restricted to CONSUMER|OPERATOR (contract) — validate on the domain/command path.
- assume-tenant token must NOT lose account_type (the operator stays OPERATOR while acting for a customer).

# Failure Scenarios

- If operator seed rows default to CONSUMER, operator-traversed gateways that enforce OPERATOR would 403 — the seed update prevents it. (Currently no operator-traversed gateway enforces account_type, so this is forward-safety, but the seed must still be correct.)
- Wrong claim value (CONSUMER where OPERATOR meant) → that group 403s at its gateway; AC-2/AC-5 + the seed update guard it.
