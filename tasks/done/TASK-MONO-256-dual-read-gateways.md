# Task ID

TASK-MONO-256

# Title

**ADR-MONO-032 D5 step 1 (dual-read gateways)** — change the gateway account-type enforcement to **role-based admission** that accepts **either** a valid role **or** the legacy `account_type` claim, so legacy tokens and roles-only tokens coexist with zero mis-authorization window. Cross-project (ecommerce + wms). Discovery: only ecommerce + wms gateways actually enforce `account_type`; scm/fan gateways do not gate on it (inject-only), and erp has no gateway (backend-only) — so the "5 gateways" reduces to **2 enforcement filters**.

# Status

done

> **완료 (2026-06-14)**: impl PR #<this>. ADR-MONO-032 D5 step 1. **발견**: account_type enforcement 는 5 게이트웨이가 아니라 **2개만** — ecommerce `AccountTypeEnforcementFilter`(path-based) + wms `AccountTypeValidationFilter`. scm/fan 게이트웨이는 account_type 검사 안 함(JwtHeaderEnrichmentFilter 주입만; spec 은 검사한다 했으나 코드 미구현 = ADR-021 §1.2 documented drift), erp 는 게이트웨이 자체 없음(backend-only). **변경(dual-read)**: ecommerce admin path = `ADMIN` role OR legacy `account_type=OPERATOR` / consumer path = `CUSTOMER` role OR `account_type=CONSUMER`; wms = non-empty roles(operator-only platform) OR `account_type=OPERATOR`. legacy claim 부재해도 role 로 통과(roles+account_type 둘 다 없으면 여전히 403). 각 필터 unit test 에 dual-read 케이스 추가(ecommerce: customer/admin role no-account_type 통과 + dual-capability CUSTOMER+ADMIN 양 표면 + customer-only admin 403 / wms: operator role no-account_type 통과 + no-role-no-type 403). `./gradlew :…ecommerce…:gateway-service:test :…wms…:gateway-service:test` BUILD SUCCESSFUL(Docker-free unit, 32s). X-Account-Type 주입 제거는 step 4(drop legacy) 로 보류. 후속 = D5 step 2 roles-only issuance(iam TenantClaimTokenCustomizer). 분석=Opus 4.8 / 구현=Opus.

# Owner

backend

# Task Tags

- backend
- security
- cross-project

---

# Dependency Markers

- **executes**: ADR-MONO-032 § 3.3 / D5 **step 1** (dual-read gateways). Depends on D5 step 0 (TASK-MONO-255, contract rewrite, #1517 `307db6cb`).
- **contract**: `platform/contracts/jwt-standard-claims.md` § Gateway Enforcement (role-based admission) + § Migration Compatibility (dual-read window) — landed at step 0.
- **unblocks**: D5 step 2 (roles-only issuance — iam `TenantClaimTokenCustomizer` stops emitting account_type, seeds CUSTOMER/FAN roles).

# Goal

Make every account-type-enforcing gateway admit on role presence OR the legacy `account_type` claim, so the issuance side (step 2) can switch to roles-only without any window where a valid token is 403'd.

# Scope

- `projects/ecommerce-microservices-platform/apps/gateway-service/.../filter/AccountTypeEnforcementFilter.java` — admin path: `ADMIN` role OR `account_type=OPERATOR`; consumer path: `CUSTOMER` role OR `account_type=CONSUMER`. `hasRole` helper.
- `projects/wms-platform/apps/gateway-service/.../filter/AccountTypeValidationFilter.java` — non-empty roles (wms = operator-only platform) OR `account_type=OPERATOR`. `isOperator` helper.
- Both filters' unit tests — add dual-read cases (role, no account_type; dual-capability; negative cases).
- **No change** to scm/fan gateways (they do not enforce account_type) or erp (no gateway).
- **No change** to `X-Account-Type` injection (deferred to D5 step 4 — drop legacy).

# Acceptance Criteria

- **AC-1** ecommerce: a token with `roles:["CUSTOMER"]` (no account_type) passes consumer paths; `roles:["ADMIN"]` passes admin paths; `roles:["CUSTOMER","ADMIN"]` passes both; `roles:["CUSTOMER"]` on an admin path is 403.
- **AC-2** wms: a token with a non-empty `roles` (no account_type) passes; no role + no account_type is 403.
- **AC-3** Legacy compatibility preserved: `account_type=OPERATOR`/`CONSUMER` tokens still admit exactly as before (existing tests unchanged and green).
- **AC-4** Public-route pass-through (no security context) unchanged.
- **AC-5** `./gradlew :…:gateway-service:test` green for both ecommerce + wms (Docker-free unit).
- **AC-6** No change to scm/fan/erp; no change to X-Account-Type injection.

# Related Specs

- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` D3 (role-based admission) + D5 (dual-read).
- `platform/contracts/jwt-standard-claims.md` § Gateway Enforcement + § Migration Compatibility.

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` (the contract this implements; landed at step 0).

# Edge Cases

- A token with neither a valid role nor a legacy `account_type` is **403** (both filters) — dual-read widens admission, it does not open it.
- wms is operator-only: any non-empty `roles` on an `aud=wms` token is an operator role (there is no consumer surface on wms), so role-presence is the correct operator check.
- scm/fan gateways were found NOT to enforce account_type (inject-only) — no change needed; the contract's fan rule was a documented spec/code drift (ADR-021 § 1.2), not a regression introduced here.

# Failure Scenarios

- If a filter required BOTH role AND account_type → legacy tokens (role absent) would 403 → mis-authorization window. Dual-read is OR, not AND.
- If X-Account-Type injection were removed here → downstream services still reading it during migration would break; that removal is correctly deferred to D5 step 4.
