# Task ID

TASK-BE-615

# Status

in-progress

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

# 착수 시 결정 (2026-10-01 UTC)

- **소유자 결정 — 로그아웃 범위 = 전체**(소유자 «진행», 2026-10-01): 어느 소비자 사이트에서 로그아웃하든 **IAM 브라우저 세션도 끝난다** — 공용 기기에서 한 사이트만 로그아웃하고 다른 사이트 로그인이 살아 있는 상태를 만들지 않는다. 이미 발급된 다른 사이트의 앱 세션(그 사이트의 토큰)은 그 사이트의 만료·자체 로그아웃을 따른다 — 이 범위를 시험으로 고정하고 사용자 문구에 적는다(AC-4).
- **소유자 결정 — 운영자 생성 확인은 옛 규칙 유지**(같은 메시지): admin-service `CreateOperatorUseCase` 의 «대상 테넌트에 계정이 있나» 확인은 **풀 멤버를 포함하지 않는다**(`TASK-BE-614` 가 넓힌 조회를 이 확인만 옛 범위로). 직원 계정 규칙은 `ADR-MONO-080` 후보(`TASK-MONO-746`)의 몫이다(AC-8).
- 🔴 **정정 — 플래그를 켜는 것은 이 티켓이 아니라 `TASK-BE-616`** (착수 시 정정): 이 티켓만으로는 **첫 방문 동의 화면이 없다**. 켜면 스토어에서 풀로 가입한 사람이 팬에 처음 갈 때 들어갈 길이 없다. 이 티켓은 «풀 계정이 **이미 멤버인** 사이트로 재입력 없이 토큰을 받는다» 까지를 만들고, 멤버가 아닌 사이트에서는 **토큰을 만들지 않는다**(616 의 동의 화면이 그 자리를 채운다). `iam.consumer-pool.enabled` 는 기본 꺼짐 그대로 둔다(AC-9).
# Acceptance Criteria

- [ ] **AC-1** — 풀 계정으로 팬 로그인 → 스토어 authorize: 폼 없이 토큰, `sub` 동일, `tenant_id=ecommerce`, 역할에 `FAN` 이 **없다**(역할 평탄화 금지).
- [ ] **AC-2** — 🔴 **대조군**: 묶이지 않은 **기존 사이트 계정** 세션은 지금처럼 재인증한다(`TASK-BE-605` 동작 유지) — 풀 계정만 통과한다.
- [ ] **AC-3** — 콘솔: 풀 계정 세션으로 콘솔에 가면 `TASK-BE-610` 규칙 그대로(운영자 자격이 없으면 들어가지 못한다). 콘솔 로그인의 이메일 교차 조회에서 같은 이메일이 팬·스토어에 **둘 다** 있어 나던 `LOGIN_TENANT_AMBIGUOUS` 가 풀 계정에서 어떻게 되는지 정하고 시험으로 고정(`ADR-MONO-078` D5 콘솔 칸).
- [ ] **AC-4** — 로그아웃 범위(한 사이트 / 전체 IdP 세션)를 정하고 기록, 시험으로 고정(`ADR-MONO-078` § 새로 생기는 위험).
- [ ] **AC-5** — 갱신 토큰: 스토어에서 받은 refresh 로 팬 토큰을 받지 못한다(`TOKEN_TENANT_MISMATCH` 규칙 유지).
- [ ] **AC-6** — `SsoTenantGateIntegrationTest` 등 기존 SSO 시험이 초록이고, 바뀐 기대값은 하나하나 이유를 적는다.
- [ ] **AC-8** — `CreateOperatorUseCase` 의 대상 테넌트 계정 확인이 풀 멤버를 **포함하지 않는다** — 대조 시험: 풀 가입 쇼핑객 이메일로 `ecommerce` 운영자 생성 → 지금처럼 «계정 없음» 거절. 콘솔 계정 운영 목록(§ 5)은 계속 풀 멤버를 포함한다.
- [ ] **AC-9** — `iam.consumer-pool.enabled` 기본값이 여전히 `false` 다. 이 티켓의 풀 로그인 경로는 풀 자격이 있을 때만 타므로 플래그와 무관하게 안전하다는 것을 시험으로 보인다(풀 자격 없는 기존 계정의 로그인·SSO·refresh 기대값 무변경).
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
