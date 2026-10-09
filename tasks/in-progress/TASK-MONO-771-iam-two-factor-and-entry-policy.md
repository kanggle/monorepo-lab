# Task ID

TASK-MONO-771

# Title

`ADR-MONO-080` 단계 2 (D4 · R2 · R3) — **IAM 로그인에 2단계 인증(TOTP)** + 운영자 토큰 교환 · 회사 테넌트 진입에서 그 사실을 본다

# Status

in-progress

# Owner

monorepo

# Task Tags

- iam
- platform-console
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (인증 경로 · 토큰 클레임 계약)

---

# Dependency Markers

- **선행**: `TASK-MONO-770` `done/` (ADR-080 D2 — 앞 단계 없이 뒤 단계가 나가지 않는다).
- **후속**: `TASK-MONO-772`.

# Goal

지금은 **플랫폼 관리자조차 주 경로로는 2단계 인증 없이** 콘솔에 들어온다 — 토큰 교환이 «운영자 행 + `ACTIVE`» 만 보고(`TokenExchangeService.java:72-106`), `require_2fa` 는 break-glass 로컬 로그인(`AdminLoginService.java:103-135`)에서만 문다. IAM(OIDC) 로그인에 TOTP 를 두고, 토큰에 «2단계를 거쳤다» 를 싣고, 진입 지점이 그것을 정책과 비교한다.

라이더(기본값): R2 — 회사 정책 = **테넌트 단위 플래그**(«이 테넌트 진입은 2단계 필수»), 역할 플래그 `require_2fa` 는 플랫폼 관리자용으로 남겨 **주 경로에서도** 물게 · R3 — 수단 = **TOTP**(admin-service 기존 구현을 계정 평면으로).

# Scope

## In Scope

- 계약 먼저: `platform/contracts/jwt-standard-claims.md` 에 `amr`(RFC 8176) — 첫 일
- auth-service: 계정 TOTP 등록 · 검증 · 복구 코드 · 로그인 흐름에 2단계 · `amr` 발급
- admin-service: 운영자 토큰 교환이 `require_2fa` 역할 보유자에게 `amr` 의 2단계를 요구 · assume-tenant 가 테넌트 정책 플래그와 비교
- 테넌트 정책 플래그(account-service `tenants` 또는 admin 평면 — 착수 시 결정, 계약 먼저) + 콘솔에서 켜고 끄는 자리
- 기존 운영자 전이: 정책을 켜는 순간 잠기지 않게 유예 기간 또는 등록 유도(ADR-080 § 새로 생기는 위험)

## Out of Scope

- 패스키 · SMS(R3 밖) · 회사 SSO(ADR-080 D4 범위 밖)

# Acceptance Criteria

- [x] **AC-0** — 착수 시 재측정: `anyRoleRequires2fa` 호출자 = `AdminLoginService` 하나 · `require_2fa = TRUE` 역할(`SUPER_ADMIN` · `SECURITY_ANALYST`) · auth-service 의 TOTP 참조 0. 정책 플래그의 집을 정하고 이유를 적는다.
- [ ] **AC-1** — 🔴 «전» 상태를 먼저 단언하는 시험: `require_2fa` 역할 운영자가 OIDC 토큰 교환으로 2단계 없이 운영자 토큰을 **받는다**(현재) → 구현 뒤 **받지 못한다**.
- [ ] **AC-2** — «2단계 필수» 테넌트로 assume 할 때 `amr` 에 2단계가 없으면 거절 · 다른 테넌트는 무영향(대조군).
- [ ] **AC-3** — 소비자 로그인은 TOTP 를 등록하지 않으면 지금과 같다.
- [ ] **AC-4** — 기존 운영자 전이 경로(유예 또는 등록 유도)가 시험 또는 라이브로 확인된다.

# Related Specs

- `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` D1(분리 ≠ 2FA 면제) · D4 · R2 · R3
- `docs/adr/ADR-MONO-020`(assume-tenant) · `docs/adr/ADR-MONO-014`(토큰 교환)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` (`amr`) · `projects/iam-platform/specs/contracts/http/admin-api.md`(토큰 교환 · assume-tenant 거절 코드)

# Edge Cases

- break-glass 로컬 로그인의 기존 TOTP — 계정 평면 TOTP 와 둘이 되지 않게 이전 또는 연결 방침을 정한다.
- TOTP 기기 분실 — 복구 코드 · 관리자 리셋 경로.

# Failure Scenarios

1. `amr` 없이 정책 검사만 넣는다 — 모든 진입이 거절된다.
2. 정책을 켜는 순간 기존 운영자가 전부 잠긴다(전이 경로 없음).

---

# AC-0 기록 (2026-10-08 UTC)

> 분석=Opus 5.5 (architect) · **코드 변경 0** · 정적 읽기, worktree `feat/mono-771-iam-2fa`(origin/main `65c2e3234`). 라이브 ⚪ — DB · 데모 미기동.
> 🔵 AC-0 체크박스는 건드리지 않았다(오케스트레이터 판단). 「정책 플래그의 집」은 티켓이 «착수 시 결정» 으로 둔 **구현자 결정**으로 § 2 에 적었고, 라이더 R2 · R3 가 정하지 않은 것만 § 6 **소유자 결정(OD)** 으로 분리했다.

## 0. 선행

| 잰 것 | 결과 | 출처 |
|---|---|---|
| `TASK-MONO-770` `done/` (ADR-080 D2) | ✅ | `tasks/done/TASK-MONO-770-verification-mail-and-email-gate.md` |
| 이 티켓을 막는 다른 마커 | 없음 — `tasks/INDEX.md:185` 은 «AC-0 = 770 `done/`» 하나 | `tasks/INDEX.md:185` |

## 1. 티켓 AC-0 주장 대조

| # | 티켓 주장 | 판정 | 실측 (file:line) |
|---|---|---|---|
| 1 | `anyRoleRequires2fa` 호출자 = `AdminLoginService` 하나 | ✅ 맞다 | main 호출 **1곳** `admin-service/.../application/AdminLoginService.java:103`. 선언 `application/port/AdminOperatorPort.java:197`(javadoc `:193-196`) · 구현 `infrastructure/persistence/rbac/JpaAdminOperatorAdapter.java:246-251`. 🔵 정정: ADR-080 표의 `AdminOperatorPort.java:185` 는 선언 줄이 아니다(선언은 `:197`). 테스트 쪽 호출 0. `TokenExchangeService.java:72-106` 은 «행 있음 + `ACTIVE`»(`:81-96`)만 본다 — 재확인 |
| 2 | `require_2fa = TRUE` = `SUPER_ADMIN` · `SECURITY_ANALYST` | ✅ 맞다 | `V0006__seed_admin_rbac.sql:5,8` · `V0009__admin_rbac_seed_idempotent.sql:7,10` 이 FALSE 로 넣고 `V0013__operator_totp_and_require_2fa.sql:76-78` 이 둘만 TRUE. 나머지 FALSE: `SUPPORT_READONLY` · `SUPPORT_LOCK`(`V0006:6-7`) · `TENANT_ADMIN` · `TENANT_BILLING_ADMIN`(`V0033:20-21`) · `ORG_ADMIN`(`V0041:29`) |
| 2+ | (티켓 미기재) 환경별 덮어쓰기 | 🔴 덧붙임 | ① console e2e 픽스처가 `SUPER_ADMIN` 을 FALSE 로 **완화** — `projects/platform-console/apps/console-web/tests/e2e/fixtures/seed.sql:121-127`(e2e 한정 · 근거 문구 `:40-44` 가 «토큰 교환은 2FA 를 안 본다» 를 전제). ② 데모 dev 시드는 **완화 안 함** — `admin-service/.../db/migration-dev/R__seed_demo_operator.sql:47-50`, 그리고 데모 운영자 둘 다 `SUPER_ADMIN`(`:77-81` demo-operator · `:207-211` demo-requester, 근거 `:183-186` 도 같은 전제). 이 R__ 는 **기본 프로필에서도** 돈다(`:15-21`) ⇒ S4 강제 순간 데모 로그인과 개발자 로컬 로그인이 함께 바뀐다(→ OD-5) |
| 3 | auth-service 의 TOTP 참조 0 | ✅ 맞다 | `(?i)totp\|2fa\|two.?factor\|\bamr\b\|\bmfa\b` — `auth-service/src/main` **0건**. `amr` 저장소 전체: 테스트 고정 한 곳 `libs/java-security-servlet/src/test/.../CallerTokenPropagationTest.java:47,114`(`TASK-MONO-778` AC-3 — 토큰 원문 전달이 `amr` 을 보존함을 고정) + 문서뿐 |
| 4 | (티켓 미기재) `amr` 이 **발급될** 자리 | 실측 | 아래 § 1.1 |
| 5 | (티켓 미기재) `amr` 을 **읽을** 자리 | 실측 | 아래 § 1.2 |

### 1.1 `amr` 발급 자리 (auth-service, 계약 `jwt-standard-claims.md` 범위)

| 토큰 | 지금 클레임을 만드는 곳 | `amr` 의 원천 | 주의 |
|---|---|---|---|
| `authorization_code` access · id_token | `TenantClaimTokenCustomizer.customizeForAuthorizationCode` `:383-461` (id_token 도 같은 분기 `:192-197`) | principal `details` 맵 — `PrincipalDetailKeys.java:20-47` 에 새 키. 생산자 **둘**: `CredentialAuthenticationProvider.java:446-456`(폼) · `SocialLoginBrowserController.java:219-245`(소셜) | 🔴 생산자가 둘이다 — 2단계를 폼에만 넣으면 소셜 로그인이 옆문(F6). `extractTenantAttribute` 는 String 전용(`:401`) — 목록 추출기 필요 |
| `refresh_token` access | 같은 메서드 재사용 `:222-237` — 저장된 `OAuth2Authorization` 의 principal | 같은 `details` 맵 | 🔴 콘솔은 IAM refresh 로 회전한 토큰으로 **매번 재교환**한다(`admin-api.md:541-543`, ADR-014 D2). `amr` 이 refresh 에서 빠지면 2단계를 거친 운영자도 access TTL(5~15분) 뒤 잠긴다. 직렬화는 `SecurityJackson2Modules` 허용목록 — 불변 리스트 금지, 가변 `ArrayList`(`:832-838` 주석의 같은 함정) |
| assume-tenant (운영자) | `customizeForAssumeTenant` `:742-842` | subject token — `AssumeTenantAuthenticationProvider.java:133-134` 에서 이미 decode. `resolvedGrant`(`:203-205`)에 실어 복사 | 판정 자체는 provider 에서(§ 2), 클레임은 하류 가시성용 복사 |
| assume-tenant (workload) · `client_credentials` | `customizeForWorkloadAssumeTenant` `:717-740` · `customizeForClientCredentials` `:278-299` | — | **발급하지 않는다**(workload 는 신원이 아니다 — 계약 `:161, :169`) |
| admin-service operator token (`iss=admin-service`) | `OperatorAccessTokenIssuer` | — | 계약 범위 밖. 이번에 싣지 않는다(OD-3 의 대안에서만 등장) |
| 계약 가드 | `scripts/check-jwt-claims-registry.sh:21-22` — **code → doc 한 방향** | — | `amr` 행이 계약에 먼저 없으면 S2b 가 CI 빨강 ⇒ 「계약 먼저」가 기계로 강제된다 |

### 1.2 `amr` 판정 자리

| 진입 | 판정하는 프로세스 | 지금 코드 | 바뀌어야 할 것 |
|---|---|---|---|
| 운영자 토큰 교환 `POST /api/admin/auth/token-exchange` | admin-service | `TokenExchangeService.java:75` — `validateAndExtractSubject` 가 **`sub` 문자열만** 돌려준다(`IamOidcSubjectTokenValidator.java:33`, 구현 `IamOidcJwksSubjectTokenValidator.java:78`) | 포트 반환형을 «sub + amr» 로. 🔴 같은 포트를 `OnboardingController.java:57` 도 쓴다 — 773 의 «테넌트 생성» 입구와 같은 포트 |
| assume-tenant `POST /oauth2/token` (token-exchange) | 🔴 **auth-service**(발급자) — admin-service 는 게이트 **입력** | `AssumeTenantAuthenticationProvider.java:160-176` → admin `OperatorAssignmentCheckUseCase.check` `:98-181` | 🔵 정정: 티켓 Scope 의 «admin-service: assume-tenant 가 정책 비교» 는 반쯤 맞다 — 비교(subject `amr` 대 요구)는 auth-service 가, 요구 여부 산출은 admin-service 가 한다(§ 2) |

### 1.3 티켓이 적지 않은 것 (새로 찾음)

| # | 무엇 | 출처 | 귀결 |
|---|---|---|---|
| F1 | 🔴 콘솔 콜백이 토큰 교환 **401 을 «운영자 아님 → `/onboarding`»** 으로 읽는다 | `console-web/src/app/api/auth/callback/route.ts:235-262` · 계약의 401 은 `TOKEN_INVALID` 하나(`admin-api.md:566-570`) | 2단계 거절을 기존 401 로 내면 `SUPER_ADMIN` 이 온보딩(773 뒤엔 «테넌트 생성» 셸)으로 간다 — 잠금이 아니라 **오도**. 별도 코드 필수(HS-B) |
| F2 | 🔴 assume-tenant 의 옆문 — 플랫폼 범위 운영자는 **모든 테넌트에 `assigned=true`**, assume 는 base OIDC 토큰만 본다(운영자 토큰 불필요) | `OperatorAssignmentCheckUseCase.java:142-144` · `AssumeTenantAuthenticationProvider.java:130-176` | AC-1 이 토큰 교환만 막으면 `SUPER_ADMIN` 은 BFF 의 assume 로 **모든 테넌트의 도메인 토큰**을 2단계 없이 계속 받는다(→ OD-2) |
| F3 | assume 거절이 **전부 `invalid_grant` 한 종류** | `auth-api.md:189-193, :266` | 콘솔이 «2단계 필요» 와 «미할당» 을 구별 못 한다 — 단계 상승 화면으로 보낼 근거가 없다(HS-C) |
| F4 | «토큰 교환은 2FA 를 안 본다» 를 **사실로** 적은 곳 셋 — S4 에서 거짓이 된다 | `admin-service/security.md:230-231`(O4) · e2e `seed.sql:40-44, 121-124` · `R__seed_demo_operator.sql:47-50, 183-186` | S1(명세) · S4(시드 주석) 에서 고친다 |
| F5 | admin TOTP 검증기에 **재생 방지가 없다** — ±1 창 안에서 같은 코드를 다시 쓸 수 있다 | `admin-service/.../security/TotpGenerator.java:65-75` | 계정 평면은 «마지막 사용 카운터» 로 막는다(S2b). break-glass 쪽은 이 티켓 밖(기록만) |
| F6 | 로그인 생산자가 둘(폼 · 소셜) | `PrincipalDetailKeys.java:7-9` | 2단계 단계는 **두 생산자 뒤 공통 지점**에 둔다 — 등록된 계정이 소셜로 들어오면 소셜 뒤에도 TOTP |

## 2. 결정 — 정책 플래그의 집: **admin 평면** (admin_db 새 표, 구현자 결정)

| 기준 | account-service `tenants` (`V0009__create_tenants.sql:4-12`) | **admin 평면** (`tenant_entry_policy`) |
|---|---|---|
| assume 시점에 누가 읽나 | auth-service customizer 가 account-service 를 불러야 한다 — 그 호출은 **fail-soft 로 설계**(`TenantClaimTokenCustomizer.java:853-857, 879-893`). 정책 판정은 fail-closed 여야 하므로 assume 발급이 account-service 가용성에 **새로** 묶인다. `auth-to-admin.md:119` 가 두 정책을 «정반대» 라 적은 그 경계를 넘는다 | 이미 fail-closed 인 게이트 `GET /internal/operator-assignments/check` **같은 요청 안**의 로컬 읽기. 새 서비스 간 호출 0 |
| 토큰 교환(admin-service)이 읽을 수 있나 | ❌ — admin-service 의 hot-path 교차 조회는 금지(`OperatorAssignmentCheckUseCase.java:206-212` «a hot-path cross-service fetch is forbidden» · ADR-020 § 3.1 «no per-request … callback») | ✅ 같은 서비스 로컬 |
| 같은 종류의 «진입 규칙» 선례 | — | 전부 admin 평면: `confined_tenant_id`(`V0046`, `:128-138`) · 플랫폼 전용 테넌트(`:146-157`) · 파트너십(`V0039`) |
| 의미 | 테넌트 속성 — 소비자 사이트 로그인 쪽 독자가 «이 테넌트 로그인은 2FA» 로 오독할 여지(AC-3 위험) | «이 테넌트에 **운영자로** 들어오는 조건» = 운영자 평면 |
| 대가 | — | 테넌트 SoT 와 다른 DB 에 `tenant_id` 키 행(FK 없음) — `operator_tenant_assignment` · `tenant_partnership` 과 같은 형태. 테넌트는 삭제 없이 `SUSPENDED` 만(`admin-api.md:1822-1830`) ⇒ 고아 위험 낮음. 쓰기 때 테넌트 존재 확인(admin → account, 비 hot-path)으로 막는다 |

- **모양(초안, S1 에서 확정)**: `tenant_entry_policy(tenant_id VARCHAR(32) PK, require_mfa BOOLEAN NOT NULL, updated_at DATETIME(6), updated_by BIGINT NULL, version BIGINT)` — **행 없음 = OFF**(net-zero, 마이그레이션은 빈 표). admin 다음 번호 `V0047`(현재 마지막 `V0046__add_admin_operator_confined_tenant_id.sql`).
- **assume 시점에 읽는 순서**: ① admin-service `OperatorAssignmentCheckUseCase.check` 가 `assigned=true` 인 경우 `mfaRequired = policy(선택 테넌트) ∨ anyRoleRequires2fa(운영자)`(뒤 항은 OD-2) 를 계산해 응답에 **항상** 싣는다(`auth-to-admin.md:46-47` 표에 additive 행) ② auth-service `AssumeTenantAuthenticationProvider` 가 `mfaRequired ∧ "mfa" ∉ subject.amr` 이면 거절(판별자는 HS-C). 🔴 필드 **부재 = 거절**(fail-closed) — admin · auth 를 한 PR(S4)에 넣어 버전 어긋남이 없게 한다.
- **경로 불문**: 선택 테넌트의 정책은 assignment · 플랫폼 `'*'` · 파트너십 host reach(`:170-180`) 어느 길로 들어와도 같다(정책은 «들어가는 곳» 의 것).
- **토큰 교환 시점**: admin-service `TokenExchangeService` 가 `anyRoleRequires2fa`(R2) — 테넌트 정책을 여기서도 볼지는 OD-3.
- **`amr` 판정 술어 = `"mfa" ∈ amr` 하나**(RFC 8176). 수단 이름(`otp`)을 읽지 않는다 — 패스키가 들어와도(R3 «나중») 독자 코드가 안 바뀐다.

## 3. break-glass TOTP 와 계정 평면 TOTP — 권고: **분리 유지**(이전 · 연결 안 함), **계산 코드만 공유**

| 이유 | 근거 |
|---|---|
| break-glass 는 **IdP(auth-service) 불가용** 때 쓰는 길이다. 비밀을 auth_db 로 옮기거나 거기서 검증하게 연결하면 break-glass 가 IdP 장애와 **함께 죽는다** | `AdminLoginService.java:74-83` · `admin-service/security.md:214-224`(ADR-035 O2 · O6 가용성 불변식) |
| ADR-035 가 «TOTP 합치기» 를 un-bisectable 한 big-bang 으로 이미 기각했다 | `ADR-MONO-035:99, 183` (O2-C) |
| 비밀의 키 공간이 다르다 — admin 은 AAD = 운영자 BIGINT PK, 계정 평면은 `account_id`. 이전 = admin 키로 복호화 → auth 키로 재암호화하는 **서비스 간 비밀 이동**(새 노출 경로) | `V0013:3-8` · `admin-service/security.md:11-65` |
| «둘이 되는 사람» 의 모집단이 작다 — break-glass TOTP 는 `password_hash` 가 있는 운영자에게만 의미(`NULL` 이면 로컬 로그인 자체가 닫힘) | `AdminLoginService.java:94-98` · 정적: 데모 2명(`R__seed_demo_operator.sql:60, 193`) + 실 운영 `SUPER_ADMIN`(라이브 ⚪) |

- Edge Case 1 의 «둘이 되지 않게» 의 해석: 같은 목적의 수단이 둘이 아니라 **목적이 다른 둘** — 주 경로 = 계정 TOTP, 비상 = break-glass TOTP. S1 이 `admin-service/security.md` 와 콘솔 안내 문구에 그렇게 적는다.
- 코드 공유: `TotpGenerator`(RFC 6238 · Base32, 프레임워크 무관 계산부)를 `libs/java-security` 로 승격 — 정책상 «shared security helpers» 허용(`platform/shared-library-policy.md:42`), 카탈로그 행 `:26` 의 Contents 갱신을 같은 PR 에(`:234`). 새 모듈이 아니라 기존 모듈 내용 추가라 ADR 불요로 본다 — 🔵 S2a AC-0 에서 이 판단을 다시 잰다. 암호화기(`TotpSecretCipher`)는 키 설정 · AAD 가 서비스마다 달라 서비스별 유지(승격 여부는 S2a AC-0).
- 뒤집기 대안 «연결»(break-glass 로그인이 계정 TOTP 를 내부 호출로 검증) — IdP 장애 시 break-glass 무력화, 비추천.

## 4. 기존 운영자 전이 — 권고: **등록 유도**(거절 = 등록 화면으로 가는 길), **시간 유예 없음** (→ OD-4)

| 무엇 | 왜 |
|---|---|
| 거절이 잠금이 되지 않는 세 조건: ① 거절 응답이 구별된다(F1 · F3) ② 콘솔이 IAM 의 2단계 등록/검증 화면으로 보낸다(S3) ③ 등록은 **비밀번호 세션만으로 셀프** | 비밀번호로 로그인할 수 있는 사람은 아무도 잠기지 않는다 — ADR-080 § 새로 생기는 위험 3번째 줄의 답 |
| 시간 유예를 고르지 않는 이유 | 유예 동안 정책은 «켜졌는데 무효» — 구멍이 그대로다. 그리고 «만료 시각» 이라는 새 상태 · 스케줄이 생긴다(이 호스트에서 날짜 게이트는 영속 스케줄이 없다). 등록 유도는 **첫 접속에서** 닫힌다 |
| 남는 위험 — 먼저 등록하기(TOFU): 비밀번호만 가진 공격자가 피해자보다 먼저 TOTP 를 등록 | 완화 ①: 등록 전제 = **인증된 이메일**(770 의 공용 술어 재사용 — 공격자에게 메일함까지 요구) · 완화 ②: 등록 시 인증된 메일로 «새 2단계 수단 등록됨» 통지(770 발송 어댑터). 둘 다 OD-4 에 포함 |
| 기기 분실 | 복구 코드 10개 일회용(admin 구현과 같은 모양 — `admin-api.md:666-697, 737-767`) · 둘 다 잃으면 관리자 리셋(OD-6, S6) |
| 켜기 전 사전 점검(선택) | 정책 토글 화면이 «이 테넌트 운영자 중 2단계 미등록 N명» 을 보여 준다 — 비 hot-path 조회(admin → auth). S5 의 선택 AC |

## 5. 슬라이스 계획

머지 순서: **S1 → S2a(S1 과 병렬 가능) → S2b → S3 → S4 → S5 → S6**. 🔴 S3(콘솔 분기)는 S4(강제) **앞**이다 — 반대면 S4 머지 순간 `SUPER_ADMIN` 이 길 없이 거절되거나(F1) 온보딩으로 오도된다. S4 는 S2b 뒤다 — `amr` 이 없으면 Failure Scenario 1.

| S | 무엇 (한 PR) | 서비스 / 경로 | 이 슬라이스의 AC | 티켓 AC | 모델 |
|---|---|---|---|---|---|
| **S1** 계약 · 명세 (코드 0) | `platform/contracts/jwt-standard-claims.md` **`amr` 행 + Change log** (발급: `authorization_code` · `refresh_token` · id_token, assume-tenant 은 subject 복사, `client_credentials` · workload 없음 · 값 어휘 RFC 8176 `pwd`/`otp`/`mfa` · 판정 술어 `"mfa" ∈ amr`) · `auth-api.md`(로그인 2단계 화면 · 등록 · 복구 · assume 거절 판별자) · `internal/auth-to-admin.md`(`mfaRequired`) · `admin-api.md`(token-exchange `403 MFA_REQUIRED` · entry-policy GET/PUT · 리셋) · admin `data-model.md`(`tenant_entry_policy`) · auth `data-model.md`(계정 TOTP 표) · `admin-service/security.md:230-231` 정정 + subject-token 검증 표(`:152-161`)에 `amr` 추출 행 · `console-integration-contract.md` § 2.6 실패 분기 · `ADR-MONO-035` O4 · `ADR-MONO-032` D4-B 에 «080 D4 가 집행» **덧붙임**(본문 불변) | 문서만 (root `platform/` + iam · platform-console specs) | ① HS-A~D 해소 ② `check-jwt-claims-registry.sh` green(아직 mint 0) ③ OD-1~7 의 답이 계약에 반영 | AC-0 의 결정 고정 | **Opus** (계약 설계) |
| **S2a** TOTP 계산기 승격 | `TotpGenerator` → `libs/java-security` · admin-service 가 그것을 쓰게 · 카탈로그 행 갱신 | `libs/java-security` · admin-service | admin 2FA 기존 시험 전부 그대로 green(동작 불변) · RFC 6238 시험 벡터 lib 로 이동 | — (기반) | **Sonnet** (기계적 이동) |
| **S2b** 계정 평면 TOTP + `amr` 발급 | auth `V0043` 계정 TOTP 표(`account_id` 키 — 772 의 풀 이동 뒤에도 그대로) · 등록(전제 OD-4) · 검증(재생 방지 F5) · 복구 코드 · 재발급 · 폼 **과** 소셜 공통 뒤의 2단계(F6) · `details` 에 `amr` → customizer 발급(+refresh 유지) · assume 복사. **강제 없음** | auth-service | ① 등록 안 한 계정: 로그인 흐름 · 허용 표면 불변(OD-7) ② 등록 계정: 2단계 요구 · `amr ∋ mfa` ③ 🔴 refresh 뒤 `amr` 유지 시험 ④ 소셜 경로 2단계 시험 ⑤ workload 토큰에 `amr` 없음 | **AC-3** | **Opus** (인증 경로) |
| **S3** 콘솔 단계 상승 분기 | 토큰 교환 `403 MFA_REQUIRED` · assume 판별자 → IAM 2단계 화면 → 재인가 → 재교환. 401 `fail_closed` 만 «운영자 아님» 유지 | console-web | ① 각 거절 → 올바른 목적지(단위) ② 401 분기 회귀 불변 ③ e2e 디렉터리 grep + 머지 뒤 nightly 확인 | AC-4 의 길 | **Opus** (콜백 분기 · 재로그인 고리 위험) |
| **S4** 강제 | admin `V0047` `tenant_entry_policy`(빈 표) · `TokenExchangeService` R2(+OD-3) · `check` 의 `mfaRequired` · auth provider 비교 · 거절 · 데모 시드(OD-5) · F4 주석 정정 | admin-service · auth-service · (dev 시드) | **AC-1**: 🔴 «전» 단언 먼저(지금: `require_2fa` 운영자가 `amr` 없이 운영자 토큰을 **받는다**) → 같은 시험이 구현 뒤 **거절** · AC-1b(OD-2): 같은 운영자의 assume 도 거절 · **AC-2**: 정책 행 있는 테넌트 assume 거절 · 정책 없는 테넌트 같은 운영자 성공(대조군) · `amr ∋ mfa` 면 둘 다 성공 · 필드 부재 = 거절 | **AC-1 · AC-2** | **Opus** |
| **S5** 정책 관리 + 전이 확인 | admin entry-policy API(권한 OD-1 · 감사 · 테넌트 존재 확인) · 콘솔 토글 자리(OD-1) · (선택) 미등록 N명 사전 점검 | admin-service · console-web | ① 권한 대조군(권한 없는 운영자 403) ② 켜기 · 끄기 감사 행 ③ **전이**: 정책 ON → 미등록 운영자 거절 → 등록 → 재진입 성공(시험 또는 라이브) | **AC-4** | **Opus** (권한 카탈로그 변경) |
| **S6** 리셋 경로 | admin → auth internal «계정 TOTP 리셋»(사유 헤더 · 감사) | admin-service · auth-service · `internal/` 계약 | 권한 대조군(OD-6) · 리셋 뒤 다음 로그인 = 등록 유도 | Edge Case 2 | **Opus** |

- **772 · 773 을 막지 않는 조건**: ① 계정 TOTP 를 `account_id` 로 키잉(S2b) — 772 AC-5 «같은 `sub`» 이동 뒤에도 등록이 산다. ② 773 의 «운영자 아님» 셸 분기는 **401 `fail_closed` 만** 읽는다 — `403 MFA_REQUIRED` 를 «운영자 아님» 으로 읽으면 플랫폼 관리자가 테넌트 생성 셸로 떨어진다(S3 가 이 술어를 시험으로 고정). ③ `IamOidcSubjectTokenValidator` 반환형 변경(S4)이 `OnboardingController.java:57` 에도 닿는다 — 773 이 그 입구를 바꾸므로 S4 가 먼저 머지되면 773 은 새 반환형 위에서 시작한다. ④ 772 의 «771 `done/`» 게이트가 S6 에 묶여 늦어지면 S6 을 별도 후속 티켓으로 떼는 것이 대안(Edge Case 라 AC 가 아님) — 오케스트레이터 판단.

## 6. 소유자 결정 (OD) — ADR-080 R2 · R3 기본값이 정하지 **않은** 것

| OD | 질문 | 왜 R2/R3 가 답하지 않나 | 권고 (구현자 선호 — 소유자 결정 아님) | 대안 |
|---|---|---|---|---|
| **OD-1** | 테넌트 정책을 누가 켜고 끄나 · 콘솔 자리 | R2 는 «테넌트 단위 플래그» 까지만 | **그 테넌트의 `TENANT_ADMIN` + `SUPER_ADMIN`**, 새 권한(가칭 `tenant.security.manage`) — ADR-080 위험 ② 의 제목이 «**회사가** 보안 정책을 걸 수 없다». 자리: 테넌트 상세(`(console)/tenants/[tenantId]/page.tsx`, 지금 `SUPER_ADMIN` 전용) + `TENANT_ADMIN` 용 «보안 설정» 새 자리(지금 `(console)` 아래 `tenants` · `operators` 뿐) | `SUPER_ADMIN` 만(기존 테넌트 PATCH 와 같은 권한 `admin-api.md:1945`) — 더 작지만 회사가 직접 못 건다 |
| **OD-2** | `require_2fa` 역할의 주 경로 강제 범위 | R2 원문은 «주 경로(**토큰 교환**)에서도» — assume 를 말하지 않는다 | **토큰 교환 + assume-tenant 둘 다** — F2: 플랫폼 운영자는 모든 테넌트에 assume 된다. 토큰 교환만 막으면 AC-1 은 초록인데 구멍은 열려 있다 | 토큰 교환만 — 남는 위험을 이 티켓 닫기 기록에 «수용» 으로 적는다 |
| **OD-3** | 토큰 교환이 **테넌트 정책**도 보나 | ADR D4 는 «assume-tenant 및 운영자 토큰 교환이 정책과 비교» — 운영자 토큰의 어느 테넌트와인지 미정 | 운영자의 admin 범위(홈 ∪ assignment 행, 파트너십 제외 — admin 범위는 넓혀지지 않는다)에 정책 ON 테넌트가 **하나라도** 있으면 요구 — 운영자 토큰은 `/api/admin/**` 에서 그 테넌트들의 운영자를 관리한다 | 역할 플래그만(정책은 assume 에서만) — 운영자 관리 API 가 2단계 없이 정책 테넌트를 다룬다 / 운영자 토큰에 `mfa` 를 싣고 요청마다 `X-Tenant-Id` 로 판정(가장 정확, 가장 큼) |
| **OD-4** | 전이 방식 | ADR 은 «유예 **또는** 등록 유도» | **등록 유도 · 유예 없음** + 등록 전제 = 인증된 이메일 + 등록 통지 메일(§ 4) | N일 유예(구멍이 N일 열림 · 날짜 상태 신설) |
| **OD-5** | 데모 · 로컬 운영자(둘 다 `SUPER_ADMIN`, 시드 비완화) | 데모 모양은 ADR 범위 밖 | **(a) 데모 dev 시드에서 `SUPER_ADMIN` `require_2fa=FALSE`** — e2e 픽스처 선례(`seed.sql:121-127`) 와 같은 모양, 주석에 «데모 한정 · 2FA 는 테넌트 정책 토글로 시연» | (b) 그대로 강제 — 면접관이 인증 앱으로 등록해야 콘솔 진입 · (c) 데모 운영자 역할을 `require_2fa` 아닌 역할로(콘솔 화면 범위가 바뀐다) |
| **OD-6** | 계정 TOTP 리셋 권한 | Edge Case 2 는 «관리자 리셋 경로» 만 | **플랫폼만**(`SUPER_ADMIN` · `SECURITY_ANALYST`), `TENANT_ADMIN` 불가 — 772 뒤 계정은 **개인 풀 계정**이고, 리셋은 그 사람의 쇼핑 · 팬 로그인 보안까지 바꾼다(ADR-080 D5 «회수는 측면만» 과 같은 이유) | 그 테넌트 운영자 범위 안에서 `TENANT_ADMIN` 도 |
| **OD-7** (경미) | `amr` 을 모든 로그인에 싣나, 2단계 때만 싣나 | AC-3 «지금과 같다» 의 해석 | **항상**(`pwd` / `pwd,otp,mfa`; 소셜은 수단 값 없이 생략) — AC-3 은 «흐름 · 허용 표면 불변» 으로 판정하고 토큰 바이트 차이는 클레임 하나 추가(계약 `:78` «모르는 클레임은 무시») | 2단계 때만 — 바이트 불변이지만 «없음» 이 «pwd» 와 «미상» 을 섞는다 |

### 6.1 소유자 결정 기록 (2026-10-08 UTC, 선택창)

> OWNER DECISION: OD-1 «테넌트 관리자 + 플랫폼 (추천)» · OD-2·3 «교환 + assume-tenant 둘 다 (추천)» · OD-5 «데모 시드만 완화 (추천)» · OD-4·6·7 «전부 추천대로 (추천)».

| OD | 결정 |
|---|---|
| OD-1 | 그 테넌트의 `TENANT_ADMIN` + `SUPER_ADMIN` 이 새 권한으로 켜고 끈다 · 콘솔 = 테넌트 상세 + 테넌트 관리자용 «보안 설정» |
| OD-2 | `require_2fa` 역할은 **토큰 교환 + assume-tenant 둘 다**에서 문다(F2 옆문 닫힘 → HS-E 해소, AC-1b 채택) |
| OD-3 | 토큰 교환도 테넌트 정책을 본다 — 운영자의 홈 + 배정 테넌트 중 하나라도 정책 ON 이면 2단계 요구 |
| OD-4 | 등록 유도 · 시간 유예 없음 · 등록 전제 = 인증된 이메일(MONO-770 게이트 재사용) · 등록 시 알림 메일 |
| OD-5 | 데모 dev 시드에서만 `SUPER_ADMIN` `require_2fa` 완화(e2e 픽스처와 같은 방식) · 데모의 2단계는 테넌트 정책 토글로 보인다 |
| OD-6 | 타인 계정 TOTP 리셋 = 플랫폼만(`SUPER_ADMIN` · `SECURITY_ANALYST`), `TENANT_ADMIN` 아님 |
| OD-7 | `amr` 은 모든 로그인에 싣는다(`pwd` / `pwd`+`otp`+`mfa`) — AC-3 는 로그인 흐름·열리는 표면으로 판정 |

## 7. HARDSTOP 성격 노트 — 명세 충돌 · 계약 부재

| ID | 무엇 | 어디 | 왜 막나 | 해소 |
|---|---|---|---|---|
| **HS-A** (명세 충돌 — 해소 경로 있음) | «TOTP/2FA 는 admin-service-internal 불변 · OIDC base 로그인 불변» | `admin-service/security.md:230-231` · `ADR-MONO-035:107, 127, 147` (O4) | 이대로면 S2b 의 auth-service TOTP 는 명세 위반 코드다. 다만 O4 는 «step 4 범위» 의 문장이고 OIDC 측 단계 상승을 `ADR-MONO-032` D4-B(`:81, 113, 126`) 후속으로 **미뤘다** — ADR-080 D4(ACCEPTED 2026-10-07)가 그 후속이므로 진짜 모순은 아니다 | S1 이 security.md 정정 + 두 ADR 에 덧붙임 기록. **S1 머지 전 S2b 착수 금지** |
| **HS-B** (계약 부재) | 토큰 교환의 2단계 거절 코드 | `admin-api.md:564-575`(401 `TOKEN_INVALID` 하나) ↔ `callback/route.ts:235-262` | 기존 401 로 거절하면 콘솔이 «미프로비저닝» 으로 읽어 온보딩으로 보낸다(F1) — Failure Scenario 2 의 변형 | S1: `403 MFA_REQUIRED` + `console-integration-contract.md` § 2.6 분기. **S3 · S4 착수 전** |
| **HS-C** (계약 부재) | assume-tenant 의 2단계 거절 판별자 | `auth-api.md:189-193, 266` | 콘솔이 단계 상승을 시작할 근거가 없다(F3). 문구 매칭은 취약 — 고정 상수로 계약에 적는다(RFC 6749 § 5.2 의 `invalid_grant` 유지 + 고정 `error_description` 상수, RFC 9470 어휘 `insufficient_user_authentication` 권고) | S1 |
| **HS-D** (계약 부재) | 계정 평면 TOTP 의 명세 0 | auth `overview.md:43` · `dependencies.md:76` 는 «미래 · 미정» 뿐 | 집은 이미 정해져 있다 — `account-service/data-model.md:43` 이 `2fa_secret` 을 **auth-service 소유**(S1 규칙)로 못박는다. 표 · 화면 · 엔드포인트 명세가 없다 | S1 |
| **HS-E** (AC 빈틈 — 명세 충돌 아님) | AC-1 단독은 구멍을 닫지 않는다(F2) | 티켓 AC-1 · ADR-080 R2 원문 | OD-2 의 답 없이 S4 를 닫으면 «초록인데 열린» 상태 | OD-2 답 → S4 AC-1b 추가 또는 수용 기록 |

---

## S1 기록 (2026-10-08 UTC)

> 분석 · 작성 = Opus 5.5 (api-designer) · **코드 · 마이그레이션 0** — 문서만. AC 체크박스는 건드리지 않았다. `scripts/check-jwt-claims-registry.sh` 는 이 세션에서 **실행하지 못했다**(셸 없음) — 아래 «가드» 줄은 스크립트 원문을 읽고 판정한 것이다.

### 바꾼 파일 · 절

| 파일 | 절 |
|---|---|
| `platform/contracts/jwt-standard-claims.md` | § Standard Claims — **`amr` 행** 추가(`org_scope` 다음) · § Change Rule › Change log 맨 위 2026-10-08 항목 |
| `projects/iam-platform/specs/contracts/http/auth-api.md` | `GET /oauth2/authorize` 파라미터 표(`acr_values`) · Assume-Tenant Exchange(2단계 게이트 bullet · Assumed Token Claims `amr` 행 · Assume-Tenant Errors 표에 `insufficient_user_authentication` 행 + 판별자 규칙) · Token Claims 표 `amr` 행 · Scope ↔ Claim 표 · `/oauth2/token` Errors 표 · **신규 § IdP 브라우저 화면 — 2단계 인증 (TOTP)**(폼·소셜 공통 2단계 · `/mfa/challenge` · 단계 상승 `acr_values=mfa` · `/mfa/setup` 등록(OD-4 전제 + 알림 메일) · `/mfa` · `/mfa/recovery-codes` · 관리자 리셋 포인터) |
| `projects/iam-platform/specs/contracts/http/internal/auth-to-admin.md` | `GET /internal/operator-assignments/check` — TASK-MONO-771 머리 노트 · 응답 예 · 필드 표 `mfaRequired` 행 · 판정 규칙 6 · 열거 방어 문장 · Caller Constraints fail-closed bullet |
| `projects/iam-platform/specs/contracts/http/admin-api.md` | Authentication › Exceptions 표(token-exchange 행) · § POST /api/admin/auth/token-exchange(Errors 표 `403 MFA_REQUIRED` · `500 INTERNAL_ERROR` 행 + «2단계 요구» 규칙 · 401 과의 분리 · 적용 시점) · **신규 § POST /api/admin/accounts/{accountId}/2fa/reset**(OD-6) · **신규 § Tenant Entry Policy**(GET · PUT `/api/admin/tenants/{tenantId}/entry-policy`, OD-1 · OD-3 · OD-4) |
| `projects/iam-platform/specs/services/admin-service/rbac.md` | § Permission Keys 표(`tenant.security.manage` · `account.2fa_reset`) · § Seed Roles(SUPER_ADMIN · SECURITY_ANALYST · TENANT_ADMIN 행) · § Seed Matrix 2행 + TASK-MONO-771 노트 |
| `projects/iam-platform/specs/services/admin-service/security.md` | § IAM OIDC Subject-Token Validation 표 7번 행(`amr` 추출) · 신규 § Second-Factor Requirement · § Operator Credential Convergence 의 O4 문장(구 :230-231) 취소선 + 정정(HS-A) |
| `projects/iam-platform/specs/services/admin-service/data-model.md` | 신규 § `tenant_entry_policy` · § Migration Strategy TASK-MONO-771 항목 · § Data Classification Summary |
| `projects/iam-platform/specs/services/auth-service/data-model.md` | 신규 § `account_totp`(HS-D) · § Migration Strategy V0043 · § Data Classification Summary |
| `projects/iam-platform/specs/services/auth-service/overview.md` · `dependencies.md` | Change Drivers 3 포인터 · 외부 provider 표의 «2FA (미래, 미정)» 행 정정 |
| `projects/platform-console/specs/contracts/console-integration-contract.md` | § 2.6 Fail-closed mapping(`403 MFA_REQUIRED` → `/api/auth/step-up` · «401 만 운영자 아님» · 루프 상한) · § 2.6.1 재교환 403 행 · § 2.7 Default active tenant 문장 · § 2.7 Fail-closed switch(`mfa_required` 행, `denied` 보다 먼저) · 신규 § 2.4.3.3 진입 정책 토글(OD-1 두 자리) |
| `platform/error-handling.md` | Admin `[domain: saas]` — `MFA_REQUIRED`(403) 신규 · `TOTP_NOT_ENROLLED` 설명 확장 · Auth / Token — `OAUTH_INVALID_GRANT` 행에 고정 `error_description` 판별자 규칙(`insufficient_user_authentication`) |
| `rules/domains/saas.md` | Standard Error Codes › Admin 목록 — `MFA_REQUIRED` 추가 · `TOTP_NOT_ENROLLED` 설명 확장 |
| `docs/adr/ADR-MONO-035-…md` | § Amendments 맨 위 2026-10-08 항목(O4 의 deferred 후속을 080 D4 가 집행 · break-glass TOTP 불변). Status 불변 |
| `docs/adr/ADR-MONO-032-…md` | § 6 끝 «Note 2026-10-08» 인용 블록(D4-B 를 080 D4 가 집행). Status 불변 |

**가드**: 새 행은 `| \`amr\` | string[] | Conditional | … |` 로 시작한다 — `check-jwt-claims-registry.sh` 의 문서 쪽 파서(`^\| *\`[a-z_]+\` *\|` → 첫 칸 추출)가 `amr` 을 등록으로 센다. 코드 쪽은 아직 `amr` 을 mint 하지 않으므로 지금도 초록이고, S2b 가 `.claim("amr", …)` 또는 `private static final String X = "amr";` 상수로 mint 해도 초록이다.

### 슬라이스 행과 다른 것 · 이유

1. **rbac.md · `platform/error-handling.md` · `rules/domains/saas.md` 를 더 고쳤다** — S1 행에 없다. rbac.md 는 «다른 키는 본 문서 업데이트 없이 도입 금지», error-handling.md 는 «Error codes must be registered in this document before use», saas.md 는 그 교차참조 의무다. 새 권한 키 둘과 새 코드 `MFA_REQUIRED` 를 계약에 쓰는 순간 이 셋이 같은 PR 에 있어야 한다.
2. **auth-service `overview.md` · `dependencies.md` 한 줄씩** — HS-D 가 «미래 · 미정» 이라 지적한 두 줄.
3. **`mfaRequired` 부재 = «요구» 로 읽기**(AC-0 § 2 의 «부재 = 거절» 을 좁힘) — 결과는 같다(2단계 없는 subject 는 거절), 다만 2단계를 거친 subject 까지 거절하지는 않는다.
4. **토큰 교환의 요구 판정 읽기 실패 = `500 INTERNAL_ERROR`** — 401 · 403 어느 쪽으로 메워도 콘솔이 엉뚱한 화면(온보딩 · 단계 상승)으로 보낸다.
5. **`tenant_entry_policy.version` = INT**(초안은 BIGINT) — 형제 표(`tenant_partnership.version`) 규약. `CHECK (tenant_id <> '*')` 추가.
6. **`account_totp` 에 `tenant_id` 컬럼**(조회 키 아님) — multi-tenant M1 «신규 테이블 = `tenant_id` NOT NULL». 키 · 조회는 HS-D 대로 `account_id` 하나.
7. **리셋의 하류 내부 계약(admin → auth)은 쓰지 않았다** — 슬라이스 표가 S6 에 `internal/` 계약을 둔다. 공개 표면(`POST /api/admin/accounts/{accountId}/2fa/reset`)만 S1.

### 소유자가 볼 만한 문구 선택

- **`amr` 값**: 소셜 로그인 = **`[]`**(이 계약에서 유일하게 `[]` 를 내는 자리 — OD-7 «항상» 과 «소셜은 수단 값 없이» 를 함께 지키려면 이것뿐), 복구 코드 = `["pwd","mfa"]`(`otp` 아님 — 인증 앱 코드가 아니므로). 판정은 `"mfa" ∈ amr` 하나라 어느 쪽도 진입 결과를 바꾸지 않는다.
- **단계 상승 수단 = `acr_values=mfa`**(RFC 9470 모양) + 콘솔 경로 `GET /api/auth/step-up`. `acr` 클레임은 싣지 않는다.
- **새 권한 키 이름**: `tenant.security.manage`(OD-1 가칭 그대로) · `account.2fa_reset`(기존 감사 어휘 `auth.2fa_enroll` 과 맞춤). 에러 코드 `MFA_REQUIRED`(HS-B 그대로).
- **OD-3 와 홈 `'*'`**: 홈이 `'*'` 인 플랫폼 운영자는 OD-3 항이 assignment 행만 본다(`'*'` 는 정책을 가질 수 없다) — 플랫폼 운영자의 2단계는 역할 플래그와 assume 게이트가 문다. 데모(OD-5, `SUPER_ADMIN` 완화 + `confined_tenant_id=fan-platform`)에서 `fan-platform` 정책을 켜면 **토큰 교환은 통과하고 assume 이 거절**된다 — 데모의 «2단계는 정책 토글로 보인다» 는 assume 거절 → 스위처의 단계 상승 제안으로 나타난다.
- **`/mfa/challenge` 5회 실패 → 1단계부터 다시**(계정 잠금 없음 — BE-599 소유자 결정과 같은 이유). 무차별 대입 상한으로 고른 값.
- **등록 알림 메일 발송 실패는 등록을 되돌리지 않는다**(WARN 로그만).
- **리셋은 세션을 끊지 않는다** · 리셋 대상에 등록이 없으면 `404 TOTP_NOT_ENROLLED`.
- **콘솔 기본 테넌트 assume 이 `mfa_required` 로 거절돼도 자동 단계 상승은 하지 않는다**(로그인은 활성 테넌트 없이 성공, 스위처가 제안).

### S1 에 넣지 않은 것

- 셀프 2단계 **해제** 경로(Edge Case · OD 어디에도 없음) — 재등록은 관리자 리셋 뒤에만.
- 등록 · 리셋 **이벤트**(`auth-events.md` · `admin-events.md` 의 새 이벤트 타입) — 소비자 없음. 감사는 `admin_actions`(리셋 · 정책)로 남는다.
- `features/authentication.md` · `features/oauth-social-login.md` 의 흐름 서술 갱신 — 계약(auth-api)이 정본이고, 기능 문서 갱신은 S2b 에서.
- 진입 정책 토글의 «미등록 N명» 사전 점검(S5 선택 AC) — 생산자 읽기가 아직 없다.
- admin → auth 리셋 내부 계약(S6) · 데모 시드 완화(OD-5, S4) · 시드 주석 F4 정정(S4).

---

## S2b 기록 (2026-10-09 UTC)

> 구현 = Opus 5.5 (backend-engineer) · worktree `feat/mono-771-s2b-account-totp-amr`(origin/main `c5ea39a02`). **강제 없음** — 토큰 교환 · assume 어디에서도 거절을 넣지 않았다(S4). AC-1 · AC-2 · AC-4 체크박스는 건드리지 않았다. AC-3 도 체크하지 않았다(아래 증거는 단위 · 슬라이스 수준, 라이브 ⚪ — 오케스트레이터 판단).

### 바꾼 파일

| 층 | 파일 | 무엇 |
|---|---|---|
| 마이그레이션 | `auth-service/.../db/migration/V0043__create_account_totp.sql` | `account_totp`(data-model 그대로 · `account_id` PK · `tenant_id` M1 · 대기/확정 · `last_used_step` · JSON 복구 코드 해시) · 빈 표 ⇒ net-zero |
| domain | `domain/mfa/AccountTotp` · `domain/repository/AccountTotpRepository` · `domain/session/AuthenticationMethods`(RFC 8176 어휘 + `"mfa" ∈ amr`) · `PrincipalDetailKeys.AMR` | 재생 방지 술어 `isStepFresh` 는 이 한 곳 |
| application | `AccountSecondFactorService` · port `TotpCodeCalculator`(🔵 S2a 머지 뒤 `libs/java-security` 계산기로 어댑터 교체 — 주석 명기) · port `TotpSecretCipher` · `EmailSenderPort.sendSecondFactorEnrolledNotice` · `AccountServicePort.getEmailVerificationState` | 등록 · 검증(±1, F5) · 복구 코드(Argon2id `PasswordHasher`) · 재발급 · 알림 메일(실패해도 등록 유지) |
| infrastructure | `totp/Rfc6238TotpCodeCalculator`(로컬 RFC 6238 — admin 내부 복사 아님) · `totp/AesGcmTotpSecretCipher` + `TotpProperties` + `config/TotpConfig`(`auth.totp.*`, AAD=`account_id`, 키 회전 dual-read + lazy re-encrypt) · `persistence/AccountTotp{JpaEntity,JpaRepository,RepositoryImpl}`(낙관적 락) · `client/AccountServiceClient`(인증 이메일 읽기) · `email/{Smtp,Logging}EmailSender` · `security/SecondFactorSession` · `oauth2/AuthorizeSecondFactorGate` + `AuthorizationServerConfig`(테넌트 게이트 **뒤**) · `config/WebLoginSecurityConfig`(`/mfa/**` 폼 체인) | |
| amr 발급 | `security/CredentialAuthenticationProvider`(`["pwd"]`) · `presentation/SocialLoginBrowserController`(`[]`) · `oauth2/TenantClaimTokenCustomizer`(`CLAIM_AMR` — code · id_token · refresh, assume 복사) · `oauth2/AssumeTenantAuthentication{Provider,Token}`(subject `amr` 운반) | workload · `client_credentials` 미발급 |
| presentation | `MfaPageController` · `templates/mfa-challenge.html` · `mfa-setup.html` · `mfa.html` | |
| 설정 · 데모 | `application.yml`(`auth.totp.*`, dev 전용 placeholder 키) · `infra/demo/iam-traefik.override.yml`(`PathPrefix(/mfa)`) | |
| 명세 | `specs/contracts/http/internal/auth-to-account.md`(새 § 인증된 이메일 여부 — 기존 두 계약 읽기의 사용 기록) · `specs/features/authentication.md`(S1 이 S2b 로 미룬 흐름 서술) | |

### 설계 요지

- **F6**: 2단계 판정은 생산자가 아니라 **`/oauth2/authorize` 앞 한 곳**(`AuthorizeSecondFactorGate`, `AuthorizeSessionTenantGate` 바로 뒤)이다. 폼 · 소셜 두 생산자 모두 그 엔드포인트로 끝나므로 어느 1단계로 와도 확정 등록이 있으면 `/mfa/challenge`. authorize 요청은 **표준** `HttpSessionRequestCache` 에 보관(로그인 continuation — 5회 실패 뒤 재로그인이 같은 요청 · 같은 client 테넌트로 재개되도록).
- **refresh 의 amr 유지**: 커스터마이저는 `details.amr` 을 읽고, refresh 컨텍스트의 principal 은 authorize 시점에 `OAuth2Authorization` 에 저장된 그 `Authentication` 이다 ⇒ 로그인 때 값이 그대로 나간다. 2단계 통과는 세션 principal 을 **교체**(amr 확장 + 세션 id 회전)한 뒤 보관된 authorize 를 재개하므로 새 인가에 확장된 값이 저장된다.
- **AC-3**: 확정 등록이 없고 `acr_values=mfa` 도 없으면 게이트는 아무것도 하지 않는다 — 같은 화면 · 같은 리다이렉트. 바뀌는 것은 토큰의 `amr` 클레임 하나(OD-7).

### 검증 (rc 는 `cmd > file 2>&1; echo rc=$?`)

| 무엇 | 결과 |
|---|---|
| 🔴 «전» 단언 | `TenantClaimAmrTest` 를 커스터마이저 변경 **전에** 실행 — 5건 중 4건 RED(code · id_token · refresh · 소셜 `[]`: `amr` 클레임 없음), «details 에 amr 없음 → 생략» 1건 GREEN. 변경 뒤 전부 GREEN |
| 신규 · 변경 시험 9 클래스(105건) | rc=0 — `TenantClaimAmrTest` 8 · `Rfc6238TotpCodeCalculatorTest` 8(RFC 6238 부록 B SHA-1) · `AesGcmTotpSecretCipherTest` 4 · `AccountTotpTest` 4 · `AccountSecondFactorServiceTest` 13 · `AuthorizeSecondFactorGateTest` 9 · `MfaPageSliceTest` 15 · `SocialLoginBrowserControllerTest` 15 · `CredentialAuthenticationProviderTest` 29 |
| 🔴 bite (F5) | `AccountSecondFactorService.matchingFreshStep` 의 `isStepFresh` 검사를 지움 → S2b 시험 61건 중 **`challenge_replayRefused` 1건만 RED** → 복원 |
| `./gradlew :projects:iam-platform:apps:auth-service:test` (단위 레인 전체) | rc=0 — 146 클래스 · 1151건 · 실패 0 · skip 33(Docker 조건부). 전체 컨텍스트 H2 `OAuth2AuthorizationServerSliceTest` 17건 포함(새 게이트 · 엔티티 · `auth.totp.*` 빈 배선이 부팅함을 확인) |
| `compileTestJava`(통합 소스 포함) | rc=0 |
| `bash scripts/check-jwt-claims-registry.sh` | rc=0 — «all 7 claims … registered»(`amr` 포함) |
| `bash infra/demo/verify-demo-wrapper.sh` | 가드 (p): 라우터 변경 **전** FAIL «`/mfa`» → 변경 뒤 ok. 전체 실행 결과: rc=0 — «정적 검증 PASS» 63칸 · FAIL 0. `--live`(실기동)는 Docker 없음으로 미실행 ⚪ |
| 필수 가드 3종(스테이지 뒤) | `check-index-queue-drift.sh` · `check-task-id-collision.sh` · `check-walkthrough-ledger-drift.sh` 전부 rc=0. `scripts/` 추가 · 삭제 없음 |

### ⚪ 열린 것

- ⚪ **`AccountTotpRepositoryIntegrationTest`**(`@Tag("integration")`, MySQL Testcontainers — V0043 × `ddl-auto=validate` 왕복 · JSON · 낙관적 락 · 대기 교체): 이 호스트에 Docker 데몬 없음 ⇒ **CI 첫 실행**.
- ⚪ 라이브(데모 · 브라우저 · 실제 인증 앱) 미실행 — 폼/소셜 → challenge → code → 토큰 `amr` 의 실제 왕복은 시험 수준 증거뿐.
- ⚪ **QR 이미지 없음** — 계약(§ /mfa/setup)은 «QR + 수동 입력 키» 인데, auth-service 에 QR 라이브러리가 없다. 새 서드파티 의존성 선택은 이 슬라이스에서 하지 않았고, 수동 입력 키(4자 묶음) + `otpauth://` 링크만 그린다. 라이브러리 추가(예: zxing) 여부는 소유자 · 후속 판단.
- ⚪ S2a 머지 뒤: `Rfc6238TotpCodeCalculator` → `libs/java-security` 계산기 어댑터로 교체(포트 불변, RFC 벡터 시험이 동치 검사).

### 명세와 다르게 · 명세가 말하지 않아 고른 것 (오케스트레이터 확인 요망)

1. **인증된 이메일 읽기 경로** — 계약(OD-4)은 술어(`email_verified_at IS NOT NULL`)만 정하고 auth-service 가 그것을 **어떻게 읽는지**는 정하지 않았다(`auth-to-account.md` 에 해당 호출 없음, 기존 `getAccountProfile` 이 부르는 `/internal/accounts/{id}/profile` 은 account-service 에 **존재하지 않는다**). 새 엔드포인트를 만들지 않고 이미 계약된 두 읽기(`status-with-tenant` → `GET /internal/tenants/{t}/accounts/{id}` 의 `emailVerifiedAt`)를 조합했고, 그 사용을 `auth-to-account.md` 새 절에 기록했다. `NOT_APPLICABLE`(accounts 행 없음)도 «미인증» 으로 막는다.
2. **키 설정 이름** — data-model 은 `auth.totp.encryption-key` 라 적었지만 `secret_key_id` 회전을 지원하려고 admin 과 같은 모양 `auth.totp.encryption-key-id` + `auth.totp.encryption-keys.<kid>` 로 했다. 기본값은 dev 전용 placeholder(admin 과 같은 관행) — 실 환경은 `AUTH_TOTP_V1_KEY` 를 덮어써야 한다.
3. **`prompt=none` · POST authorize** — 계약이 말하지 않는다. 화면을 보일 수 없으므로 그 요청의 principal 만 비워 SAS 가 `login_required` 로 답하게 했다(code 없음, 세션은 유지).
4. **«취소»** — `/mfa/setup` 의 «취소» 의미는 계약에 없다. `/mfa/challenge` 의 취소와 같게(보관 authorize 의 등록 redirect_uri 로 `access_denied` · `mfa_cancelled`) 했다.
5. **복구 코드 «2개 이하면 다음 화면에 재발급 안내»** — 재개 전에 안내 화면 + «계속» 을 한 번 보인다.
6. 마이그레이션의 «`INFORMATION_SCHEMA` 존재 가드» 는 `CREATE TABLE IF NOT EXISTS` 로 했다.
