# Task ID

TASK-BE-615

# Status

ready

# Title

전역 소비자 계정 3단계 — 풀 계정의 authorize · **토큰 테넌트 = client** · 세션 게이트 · 로그아웃 범위 (`ADR-MONO-078` A)

# Owner

iam-platform

# Task Tags

- auth-service
- oidc
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (세션·토큰 발급 — 잘못되면 다른 사이트 권한이 실린다)

---

# Dependency Markers

- **선행**: `TASK-MONO-742` · `TASK-BE-614`
- **후속**: `TASK-BE-616`(동의 화면이 이 흐름에 끼어든다) · `TASK-MONO-743`

# Goal

풀 계정으로 로그인한 사람이 팬·스토어 어느 client 로 와도 **비밀번호 재입력 없이** 그 client 테넌트의 토큰을 받게 한다. `sub` 는 같고 `tenant_id` 는 client 의 테넌트, 역할은 그 사이트 역할만.

# Scope

## In Scope

- `CredentialAuthenticationProvider` — 소비자 client 로그인 조회에 풀 계정 추가(기존 사이트 계정과 공존하는 동안의 선택 규칙 포함)
- `TenantClaimTokenCustomizer` — 풀 principal 이면 `tenant_id` = client 테넌트, 역할 = 그 사이트 역할만(`TASK-MONO-742` AC-5)
- `AuthorizeSessionTenantGate` — 풀 principal 은 소비자 client 사이에서 재인증하지 않는다(멤버십이 없으면 `TASK-BE-616` 동의로)
- 로그아웃 범위

## Out of Scope

- 동의 화면 자체(`TASK-BE-616`), 소셜(`TASK-BE-617`)

# Acceptance Criteria

- [ ] **AC-1** — 풀 계정으로 팬 로그인 → 스토어 authorize: 폼 없이 토큰, `sub` 동일, `tenant_id=ecommerce`, 역할에 `FAN` 이 **없다**(역할 평탄화 금지).
- [ ] **AC-2** — 🔴 **대조군**: 묶이지 않은 **기존 사이트 계정** 세션은 지금처럼 재인증한다(`TASK-BE-605` 동작 유지) — 풀 계정만 통과한다.
- [ ] **AC-3** — 콘솔: 풀 계정 세션으로 콘솔에 가면 `TASK-BE-610` 규칙 그대로(운영자 자격이 없으면 들어가지 못한다). 콘솔 로그인의 이메일 교차 조회에서 같은 이메일이 팬·스토어에 **둘 다** 있어 나던 `LOGIN_TENANT_AMBIGUOUS` 가 풀 계정에서 어떻게 되는지 정하고 시험으로 고정(`ADR-MONO-078` D5 콘솔 칸).
- [ ] **AC-4** — 로그아웃 범위(한 사이트 / 전체 IdP 세션)를 정하고 기록, 시험으로 고정(`ADR-MONO-078` § 새로 생기는 위험).
- [ ] **AC-5** — 갱신 토큰: 스토어에서 받은 refresh 로 팬 토큰을 받지 못한다(`TOKEN_TENANT_MISMATCH` 규칙 유지).
- [ ] **AC-6** — `SsoTenantGateIntegrationTest` 등 기존 SSO 시험이 초록이고, 바뀐 기대값은 하나하나 이유를 적는다.
- [ ] **AC-7 (대조군 — 고치기 전에 먼저 잰다)** — 🔴 **지금 셀러 계정은 스토어에서 쇼핑을 못 하는가.** 코드 읽기로는 그렇다: 셀러 계정은 `account_roles(ecommerce, acct, SELLER)` 를 갖고(`AccountServiceSellerProvisioner.java:196`), 발급 경로가 «저장 역할이 있으면 그것만, 없으면 시드» 라(`TenantClaimTokenCustomizer` `roles = stored.isEmpty() ? seed : stored`) 스토어 토큰이 `["SELLER"]` → web-store `signInCallback` 이 `CUSTOMER` 없음으로 `account_type_mismatch`. 🔴 **실측 아님** — 이 티켓의 구현 **전에** 통합 시험으로 이 동작을 단언해 기록한다(2026-10-01 분석, `ADR-MONO-078` 후속 결정). 풀 principal 의 역할 합치기(시드 ∪ 사이트 역할)를 넣은 뒤, 풀로 옮긴 셀러(`TASK-MONO-745`)의 스토어 토큰이 `["CUSTOMER","SELLER"]` 가 되는 시험은 745 의 AC 다 — 이 티켓은 «고치기 전» 칸만 소유한다.

# Related Specs

- `projects/iam-platform/specs/features/multi-tenancy.md` § SSO(`TASK-MONO-742` 갱신본)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`(`TASK-MONO-742` 갱신본)

# Edge Cases

- 같은 이메일로 풀 계정 + 기존 사이트 계정이 공존 — 로그인 폼에서 어느 쪽이 맞는가.
- 스토어 쇼핑객이 `CUSTOMER` 역할이 없는 상태로 도착 — 스토어 `signInCallback` 이 `account_type_mismatch` 를 낸다. 동의(`TASK-BE-616`)가 역할을 주기 전에 토큰이 발급되지 않게 한다.

# Failure Scenarios

1. 풀 principal 의 저장 역할 전부(FAN+CUSTOMER)가 두 토큰에 다 실린다 — 계약 위반, 사이트 권한 누출.
2. 게이트를 풀 계정만이 아니라 **모든** 소비자 세션에 대해 끈다 — 묶이지 않은 기존 계정이 남의 사이트 토큰을 받는다.
