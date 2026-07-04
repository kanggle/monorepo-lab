# Task ID

TASK-BE-474-fix-001

# Title

셀프서비스 온보딩 operator 에 `oidc_subject` 설정 — 온보딩 후 콘솔 로그인(token-exchange) 가능하게

# Status

done

# Owner

backend

# Task Tags

- code
- fix
- security

---

# Goal

TASK-BE-474(셀프서비스 테넌트 온보딩)의 **라이브 검증 중 발견된 갭** 수정.

**발견**: 실제 OIDC 토큰으로 온보딩을 완주(201, 새 테넌트 + operator + `TENANT_ADMIN`/`TENANT_BILLING_ADMIN`)했으나, 그 계정이 새 테넌트 operator가 됐는데도 **token-exchange가 401** — 즉 온보딩은 됐지만 그 계정으로 **콘솔에 못 들어감**. 원인: `FirstAdminProvisioner`가 `NewOperator`로 만든 operator의 **`oidc_subject`가 NULL**(NewOperator에 그 필드 없음)이라, token-exchange의 `findByOidcSubject(sub)` 매핑이 실패. CI IT는 DB 롤만 확인해 못 잡았고, 라이브 완주가 잡음.

**수정**: 민팅한 operator에 `oidc_subject` = 호출자 account_id(OIDC `sub`, 컨트롤러가 이미 앎)를 `AdminOperatorPort.updateOidcSubject`로 설정. 그럼 token-exchange가 매핑 → 온보딩 직후 콘솔 로그인 가능.

선행: TASK-BE-474 (done, #2198).

---

# Scope

## In scope
- `FirstAdminProvisioner.provision(newTenantId, callerAccountId, email, displayName)` — createOperator 직후 `operatorPort.updateOidcSubject(internalId, callerAccountId, now)`.
- `SelfServiceOnboardingUseCase.onboard(...)` + `OnboardingController` — callerAccountId(=검증된 sub) 스레딩.
- 단위 테스트: `updateOidcSubject` 호출 검증. IT: `oidc_subject` = 호출자 account_id DB 검증 + **token-exchange → 200 end-to-end**(fix 증명).

## Out of scope
- 멀티-org(한 계정이 여러 테넌트 온보딩) — `oidc_subject` 플랫폼-글로벌 UNIQUE라 두 번째 온보딩은 별도 설계(ADR-044 D7 deferred). 최초 온보딩(비-운영자)만 대상.
- UI, D4 이메일 게이트.

---

# Acceptance Criteria

- [ ] **AC-1**: 온보딩이 민팅한 operator의 `admin_operators.oidc_subject` = 호출자 account_id.
- [ ] **AC-2 (end-to-end)**: 온보딩 직후 호출자의 동일 OIDC 토큰으로 `POST /api/admin/auth/token-exchange` → 200(operator 토큰) = 콘솔 로그인 가능.
- [ ] **AC-3**: 기존 D2 confinement/D3 보상/D5 불변. `:admin-service:test` 컴파일 + 단위 GREEN, IT는 CI Linux 권위.
- [ ] **AC-4 (라이브 재검증)**: fed-e2e 스택 재배포 후 실제 OIDC 토큰 온보딩 → token-exchange 200 확인.

---

# Related Specs / Contracts

- [ADR-MONO-044](../../../../docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md), TASK-BE-474
- token-exchange: ADR-MONO-014 (`findByOidcSubject` 매핑), `admin_operators.oidc_subject` V0027 (platform-global UNIQUE)

# Edge Cases / Failure Scenarios

- callerAccountId 공백/null → oidc_subject 설정 스킵(방어), 나머지 정상.
- 이미 operator인 계정(oidc_subject 중복) → UNIQUE 위반 → 온보딩 실패(D3 보상). 멀티-org는 out of scope(D7).
