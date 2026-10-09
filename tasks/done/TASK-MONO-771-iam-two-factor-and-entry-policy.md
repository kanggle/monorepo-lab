# Task ID

TASK-MONO-771

# Title

`ADR-MONO-080` 단계 2 (D4 · R2 · R3) — **IAM 로그인에 2단계 인증(TOTP)** + 운영자 토큰 교환 · 회사 테넌트 진입에서 그 사실을 본다

# Status

done

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
- [x] **AC-1** — 🔴 «전» 상태를 먼저 단언하는 시험: `require_2fa` 역할 운영자가 OIDC 토큰 교환으로 2단계 없이 운영자 토큰을 **받는다**(현재) → 구현 뒤 **받지 못한다**.
- [x] **AC-2** — «2단계 필수» 테넌트로 assume 할 때 `amr` 에 2단계가 없으면 거절 · 다른 테넌트는 무영향(대조군).
- [x] **AC-3** — 소비자 로그인은 TOTP 를 등록하지 않으면 지금과 같다. ⚪→✅ 25차 데모 창(2026-10-09 UTC) 라이브: 소비자 스토어 로그인(demo@demo.com, 이어서 shopper1@demo.com)이 2단계 화면 없이 그대로 진행됐다(이전과 동일) — 아래 § 25차 창 측정 기록.
- [x] **AC-4** — 기존 운영자 전이 경로(유예 또는 등록 유도)가 시험 또는 라이브로 확인된다. *(S5 — 「시험」으로 확인: 아래 § S5 기록 › AC-4 증거 사슬. 라이브 ⚪)*

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

---

## S2c 기록 (2026-10-09 UTC)

> 구현 = Opus 5.5 (backend-engineer) · worktree `feat/mono-771-s2c-qr-lib-calculator`(origin/main `1e54a2cb0`, S2a #4258 · S2b #4260 포함). 범위 = auth-service 뿐(platform-console 은 S3 가 별도 worktree 에서 다룬다 — 이 PR 은 건드리지 않았다). AC 체크박스는 건드리지 않았다.

> OWNER DECISION (2026-10-09 UTC, FINAL): (1) QR 을 zxing(`com.google.zxing:core`, 서버 측 PNG)으로 등록 화면에 추가한다. (2) S2b 의 인증된 이메일 읽기 방식(기존 계약 둘을 체인, fail-closed)을 그대로 수용한다.

### 바꾼 파일

| 층 | 파일 | 무엇 |
|---|---|---|
| infrastructure | `infrastructure/totp/Rfc6238TotpCodeCalculator.java` | S2b 의 손수 HMAC-SHA1 · Base32 구현을 지우고 `libs/java-security` `TotpCodeGenerator` 로 위임하는 얇은 어댑터로 교체(포트 `TotpCodeCalculator` 불변 — AC-0 § 3, S2b 의 예고대로). 클래스 이름은 유지했다(`AccountSecondFactorServiceTest:66` 이 이 클래스를 직접 `new` 한다 — 이름을 바꾸면 그 시험도 바꿔야 해서 교체 자체와 무관한 diff 가 늘어난다) |
| infrastructure (신규) | `infrastructure/totp/QrCodePngEncoder.java` | `otpauth://` URI → QR PNG → `data:image/png;base64,...`. zxing **`core` 만**(BitMatrix) — BitMatrix→BufferedImage 변환은 이 클래스가 직접 한다(zxing `javase` 는 test 전용, 아래 설계 요지) |
| presentation | `presentation/MfaPageController.java` | `pendingView` 가 `qrDataUri` 모델 속성을 추가로 싣는다. QR 인코딩 실패는 `safePendingView` 로 감싸 기존 fail-closed 관행(`"UNAVAILABLE"` · 503)과 같게 — 기존 503/read-failure 분기들과 같은 모양 |
| 템플릿 | `templates/mfa-setup.html` | PENDING·WRONG_CODE 분기에 `<img id="mfa-setup-qr">`(한국어 alt, `data:` URI) 추가, 안내 문구를 «QR 스캔, 안 되면 수동 키»로 조정. 수동 입력 키 · `otpauth://` 링크는 그대로(폴백) |
| build | `apps/auth-service/build.gradle` | `implementation 'com.google.zxing:core:3.5.4'`(운영) · `testImplementation 'com.google.zxing:javase:3.5.4'`(시험 전용 — QR 디코드) |
| 시험 | `infrastructure/totp/Rfc6238TotpCodeCalculatorTest.java` | RFC 6238 벡터는 그대로 — 이제 «교체 전후 동일» 의 증거문. 설명만 교체 완료 시점으로 갱신 |
| 시험 | `presentation/MfaPageSliceTest.java` | 신규 3건: QR PNG 를 zxing(`javase`)으로 디코드해 `otpauth://` URI 와 바이트 그대로 일치 · 계정 둘의 QR 이 서로 다른 비밀을 담고 서로의 시크릿 문자열을 포함하지 않음(격리) · 세션 없음 → `/login`(서비스 호출 0). 기존 `setup_unverifiedEmail` 에 `doesNotContain("data:image/png;base64")` 단언 추가(«대기 없음 → QR 도 없다») |

### 설계 요지 — QR 전달 방식과 그 보안 근거

**선택: 같은 `/mfa/setup` 응답에 인라인 `data:` URI로 삽입. 별도 엔드포인트를 만들지 않았다.**

과업이 제시한 두 선택지(인라인 `data:` URI ↔ 현재 세션의 대기 등록만 내려주는 전용 `GET` 엔드포인트) 중 인라인을 고른 이유 — 요구된 세 속성이 **설계로 증명**되지, 별도로 다시 구현해 맞출 필요가 없다:

1. **임의 계정의 비밀을 절대 안 준다** — QR 은 그 요청의 `AccountSecondFactorService.startEnrollment` 호출이 방금 만든 `otpauthUri` 를 그대로 인코딩한다. 계정을 고르는 파라미터·캐시 키 자체가 없다 — 공격 표면이 "없음"이지 "막음"이 아니다. (시험로 보강: `setup_pending_qrIsolatedPerAccount`.)
2. **캐싱 헤더를 따로 신경 쓸 필요가 없다** — Spring Security 의 기본 헤더 writer 가 이 `@Order(0)` 체인의 모든 응답에 `Cache-Control: no-cache, no-store` 를 이미 찍는다(S1/S2b 가 깐 체인, 다른 설정 없음). 둘째 응답이 없으니 둘째로 표시할 것도 없다.
3. **페이지와 같은 보안 체인 · CSRF 규칙** — 새 요청이 아니므로 이미 그 페이지가 지키는 규칙(세션 검사, CSRF — 이 GET 자체는 CSRF 대상이 아니지만 폼의 POST 는 그대로) 그 자체다. 전용 엔드포인트였다면 "같은 체인에 올리고, 같은 404/캐시 규칙을 새로 만들고, 그 전부가 맞는지 또 시험한다"를 다시 해야 했다.

대가: 이미지가 매 `GET`/오답 재시도마다 base64 로 다시 인코딩되어 응답 본문에 들어간다(정적 리소스처럼 따로 캐시되지 않는다) — 이 페이지의 방문 빈도(계정당 사실상 1회 등록)에서는 무시할 비용으로 판단했다.

**"대기 등록 없음 → 404/페이지 관행" 의 해석**: 이 아키텍처의 기존 관행은 404 가 아니라 **리다이렉트·에러 뷰**다(세션 없음 → `/login`, 이미 등록됨 → 안내 뷰 등 — `MfaPageController` 전체가 그렇다). QR 도 같다: PENDING_CREATED 가 아닌 모든 분기(`ALREADY_ENROLLED` · `EMAIL_NOT_VERIFIED` · `UNAVAILABLE` · 세션 없음)는 `qrDataUri` 자체를 모델에 올리지 않으므로 화면에 QR 이 없다 — 새 404 분기를 만들지 않고 기존 관행을 그대로 이어받았다(시험: `setup_unverifiedEmail`, `setup_noSession_redirectsLogin`).

**zxing 버전 — `3.5.4`**: Maven Central `core`/`javase` 의 `maven-metadata.xml` 을 이 세션에서 직접 조회해 확인한 **현재 최신 릴리스**(`<release>3.5.4</release>`, 두 아티팩트 동일) — 추정이 아니다. `core` 만 운영 의존성으로 선언했다(과업 지시 그대로); `javase` 는 AWT 데스크톱 헬퍼 모음이라 운영 클래스패스에 올릴 이유가 없고, QR 을 다시 텍스트로 디코드해 왕복을 증명하는 **시험에서만** 쓴다. BitMatrix→PNG 변환은 `QrCodePngEncoder` 가 `java.awt.image.BufferedImage` + `ImageIO`(JDK 표준)로 직접 한다.

**QR 인코딩 실패의 처리**: 거의 도달하지 않는 경로(짧고 유효한 `otpauth://` 문자열의 인코딩/PNG 기록 실패)지만, 다른 모든 읽기/쓰기 실패와 같은 모양으로 fail-closed 시켰다 — `safePendingView` 가 `RuntimeException` 을 잡아 `"UNAVAILABLE"`/503 으로 떨어뜨린다(비밀을 노출하는 대신 "지금은 확인할 수 없습니다").

### 명세와 다르게 · 명세가 말하지 않아 고른 것 (오케스트레이터 확인 요망)

1. 계약(`auth-api.md` § /mfa/setup)은 "QR + 수동 입력 키"만 말하고 QR 의 **전달 방식**(인라인 vs 전용 엔드포인트)은 말하지 않는다 — 위 설계 요지의 선택은 과업 지시의 선택지 안에서 구현자가 고른 것이다.
2. QR 이미지 크기(240×240px)·오류 정정 레벨(M)·여백(zxing 기본 4모듈)은 계약에 없다 — 실제 폰 카메라로 스캔 가능한 표준값을 썼다(라이브 스캔은 미검증 — 아래 ⚪).

### 검증 (rc 는 `cmd > file 2>&1; echo rc=$?`)

| 무엇 | 결과 |
|---|---|
| `./gradlew :libs:java-security:test` | rc=0 |
| `./gradlew :projects:iam-platform:apps:auth-service:compileTestJava` | rc=0 |
| `./gradlew :projects:iam-platform:apps:auth-service:test`(단위 레인 전체) | rc=0 — `MfaPageSliceTest` 18건(기존 15 + 신규 3) 전부 통과, 전체 레인 그대로 green(이전 S2b 기록의 1151건 모집단에 신규 3건 추가, 실패 0) |
| `bash scripts/check-jwt-claims-registry.sh` | rc=0 |
| Maven Central `core`/`javase` `maven-metadata.xml` 조회(버전 확인용, 가드 아님) | 둘 다 `<release>3.5.4</release>` 확인 |
| 가드 3종(스테이지 뒤) | `check-index-queue-drift.sh` · `check-task-id-collision.sh` · `check-walkthrough-ledger-drift.sh` — 아래 실행 |

### ⚪ 열린 것

- ⚪ 라이브 QR 스캔(실제 인증 앱 카메라) 미검증 — zxing 왕복(인코드→디코드)은 시험으로 고정했지만, 실제 카메라 조건(초점·조명·앱별 디코더)에서의 스캔은 라이브 데모/브라우저 세션에서만 확인 가능하다.
- ⚪ `AccountTotpRepositoryIntegrationTest`(S2b 가 연 Docker 의존 ⚪) — 이 슬라이스에서 변경 없음, 여전히 CI 첫 실행 대기.
- ⚪ 새 third-party 의존성(`zxing`) 에 대한 라이선스/의존성 중앙 가드 — 저장소에 그런 가드가 없음을 확인했다(`scripts/` 전체를 훑어 의존성·라이선스 이름의 가드 0건, 버전 카탈로그 파일(`gradle/libs.versions.toml`) 없음 — 각 모듈 `build.gradle` 에 정확한 버전을 직접 박는 것이 기존 관행, 이 PR 도 그 관행을 따랐다).


---

## S4 기록 (2026-10-09 UTC)

> 구현 = Opus 5.5 (backend-engineer) · worktree `feat/mono-771-s4-enforce`(origin/main `1a9b42333`, S1 · S2a · S2b · S2c · S3 포함). 범위 = S4 행 그대로 — 정책 관리 API/UI 는 S5. AC-1 · AC-2 는 **로컬에서 통과한 단위 · 슬라이스 증거**로 체크했다(아래). 통합 시험(Testcontainers)은 이 호스트에 Docker 가 없어 **CI 첫 실행** ⚪.

### 바꾼 파일

| 층 | 파일 | 무엇 |
|---|---|---|
| admin 마이그레이션 | `db/migration/V0047__create_tenant_entry_policy.sql` | 빈 표(행 없음 = 꺼짐) · `CHECK (tenant_id <> '*')` · `updated_by` FK · `version INT` |
| admin port · JPA | `application/port/TenantEntryPolicyPort` · `infrastructure/persistence/TenantEntryPolicy{JpaEntity,JpaRepository,PortImpl}` | 읽기 전용(`IN` 한 번). `'*'`·공백은 묻지 않는다. 읽기 실패는 «꺼짐» 으로 삼키지 않고 전파 |
| admin 판정 | `application/OperatorSecondFactorRequirement`(신규) | 두 진입이 **같은 한 곳**에서 요구를 계산: 교환 = 역할 `require_2fa` ∨ (홈 ∪ assignment 행) 중 정책 ON(파트너십 미독 · 홈 `'*'` 기여 없음) / assume = 선택 테넌트 정책 ∨ 역할. 모든 읽기 실패 → `SecondFactorRequirementUnavailableException` |
| admin 교환 | `TokenExchangeService` · `IamOidcSubjectTokenValidator`(+`IamOidcJwksSubjectTokenValidator`) · `MfaRequiredException` · `AdminExceptionHandler` | 포트에 `validate()` → `ValidatedSubject(sub, amr)`(추출 — 부재 · 배열 아님 · 비문자열 = 빈 집합). 운영자 해석 **뒤** `"mfa" ∉ amr ∧ 요구` → `403 MFA_REQUIRED`, 읽기 실패 → `500 INTERNAL_ERROR`. `amr ∋ mfa` 면 요구 읽기 자체를 하지 않는다. 401 의미 불변 |
| admin assume 게이트 | `OperatorAssignmentCheckUseCase` · `OperatorAssignmentCheckController` | `assigned=true` 뒤에만 `mfaRequired` 계산(규칙 6, 경로 불문 — 플랫폼 `'*'` 포함 ⇒ F2 닫힘). `assigned=false` ⇒ `false`, 요구 읽기 0. 응답 필드는 primitive — 생략 없음 |
| 온보딩 | 코드 변경 0 | `OnboardingController` 는 그대로 `validateAndExtractSubject`(이제 포트의 default = `validate().subject()`) — `amr` 을 보지 않는다 |
| auth | `OperatorAssignmentPort.AssignmentResult`(+`mfaRequired`) · `AdminAssignmentClient` · `AssumeTenantAuthenticationProvider` | 클라이언트: 명시적 JSON `false` 만 «불요», 부재 · null · 비불리언 = `true`(fail-closed). provider: 배정 게이트 뒤 `mfaRequired ∧ ¬hasSecondFactor(amr)` → `invalid_grant` + `error_description=insufficient_user_authentication`(고정 상수) |
| 데모(OD-5) | `db/migration-demo/R__demo_relax_super_admin_require_2fa.sql`(신규 위치) · `infra/demo/iam-traefik.override.yml`(admin-service `SPRING_FLYWAY_LOCATIONS`) | 아래 § OD-5 |
| F4 문구 | `db/migration-dev/R__seed_demo_operator.sql`(주석 2곳, 문장 불변) · console e2e `fixtures/seed.sql`(주석 2곳) | «토큰 교환은 2FA 를 안 본다» → S4 이후 사실로 정정. 세 번째(`admin-service/security.md:230-231`)는 S1 이 이미 정정 |

### OD-5 — 완화가 닿는 곳 (데모 전용 seam)

AC-0 § 1 대로 `db/migration-dev` 는 **기본 프로필에서도** 돈다 — 거기서 완화하면 개발자 로컬 · CI e2e 까지 완화된다. 그래서 **새 Flyway 위치 `db/migration-demo`** 에 R__ 한 파일(SUPER_ADMIN `require_2fa=FALSE`)을 두고, 그 위치를 **데모 오버레이만** 싣는다(`wms-devseed.override.yml` 의 `SPRING_FLYWAY_LOCATIONS` 와 같은 기전).

| 프로필 / 실행 | flyway locations | SUPER_ADMIN `require_2fa` |
|---|---|---|
| 포트폴리오 데모 (`infra/demo/iam-traefik.override.yml`, e2e 프로필 위 env 덮어쓰기) | migration + migration-dev + **migration-demo** | **FALSE** (완화) |
| 기본 프로필 (개발자 로컬) · `dev` | migration + migration-dev | TRUE |
| `e2e` (CI · nightly 의 IAM 컨테이너) | migration + migration-dev | TRUE — 단 console · federation e2e 하네스는 **자기 픽스처**에서 런타임 완화(기존 선례, 불변) |
| `test` (admin IT) | migration + migration-dev | TRUE |
| `prod` | migration | TRUE |

정책 표(V0047)는 데모에서도 그대로 문다 — 데모의 2단계는 테넌트 정책 토글(S5)로 보인다. 고정: `DemoOnlyRequire2faRelaxationTest`(4건 — V0013 TRUE · migration/migration-dev 에 완화문 0 · 어떤 프로필 yml 도 `db/migration-demo` 를 싣지 않음 · prod = `db/migration` 단독 · 그 위치를 싣는 저장소 파일은 데모 오버레이뿐이고 CI 하네스 compose 는 아님). 🔴 결과: 데모 오버레이 없이 로컬 기본 프로필로 띄운 개발자는 `demo@demo.com` 으로 콘솔에 들어가려면 2단계를 등록해야 한다(의도 — OD-5 는 «데모 시드만»).

### 검증 (rc 는 `cmd > file 2>&1; echo rc=$?`)

| 무엇 | 결과 |
|---|---|
| 🔴 AC-1 «전» | 변경 **전** 코드에 임시 시험(`require_2fa` 역할 운영자 · 토큰 `amr` 에 mfa 없음 → `exchange()` 가 토큰을 **돌려준다** 단언) — rc=0, 1/1 통과(= 구멍이 실재). 변경 **뒤** 같은 단언(새 포트 API 로만 맞춤) → rc=1, `MfaRequiredException` 으로 RED. 그 뒤 임시 시험을 지우고 «거절» 단언(`TokenExchangeServiceTest` S4 절)으로 대체 |
| `:admin-service:test` | rc=0 — 153 클래스 · 979건 · 실패 0 · skip 58(Docker 조건부) |
| `:auth-service:test` | rc=0 — 146 클래스 · 1163건 · 실패 0 · skip 33 |
| `compileTestJava` (admin · auth, 통합 소스 포함) | rc=0 |
| `bash scripts/check-jwt-claims-registry.sh` | rc=0 («all 7 claims … registered») |
| `bash infra/demo/verify-demo-wrapper.sh` (정적) | rc=0 — «정적 검증 PASS», FAIL 0. `--live` 미실행 ⚪ |
| 🔴 bite 1 — 교환 게이트 통째 제거(`if (false)`) | admin 전체에서 **`TokenExchangeServiceTest` 의 S4 시험 7건만** RED(AC-1 ×2 · OD-3 ×3 · 읽기 실패 ×2) → 복원 |
| 🔴 bite 1b — 교환에서 `amr` 항만 제거(요구면 무조건 거절) | **2건만** RED(`amr ∋ mfa → 발급` · OD-3 의 mfa 재교환) → 복원 |
| 🔴 bite 2 — auth provider 비교 제거 | auth 전체에서 **AC-1b 2건만** RED(`amr=[pwd]` · `amr` 부재) → 복원. AC-2 «대조군 발급» 은 비교가 없어도 초록이 정상(거절 쪽 단언이 무는 자리) |
| 🔴 bite 3 — OD-5 완화문을 `migration-dev/R__seed_demo_operator.sql` 에 덧붙임 | `DemoOnlyRequire2faRelaxationTest` 1건 RED → 복원(주석 변경만 남음 확인) |
| 필수 가드 3종(`git add` 뒤) | `check-index-queue-drift.sh` · `check-task-id-collision.sh` · `check-walkthrough-ledger-drift.sh` 전부 rc=0. `scripts/` 추가 · 삭제 없음 |

### 증거 지도 (AC ↔ 시험)

- **AC-1**: `TokenExchangeServiceTest`(역할 + `[pwd]` / `amr` 부재 → `MfaRequiredException` · `amr ∋ mfa` → 발급 · 대조군 발급 · 401 불변 ×2) + `AdminLoginControllerSliceTest`(HTTP 매핑: `403 MFA_REQUIRED` · `500 INTERNAL_ERROR` · `401 TOKEN_INVALID` 불변) + `IamOidcJwksSubjectTokenValidatorTest`(추출 3건) — 로컬 통과. IT `TokenExchangeIntegrationTest`(SUPER_ADMIN 실제 V0013 플래그 × `amr` 왕복 · 홈 정책 행) ⚪ CI.
- **AC-1b (OD-2 · F2)**: `OperatorAssignmentCheckMfaRequiredTest`(플랫폼 `'*'` SUPER_ADMIN → `assigned ∧ mfaRequired`) + `AssumeTenantAuthenticationProviderTest`(거절 · 고정 판별자 전체 일치 · mfa 면 발급). IT `OperatorAssignmentCheckIntegrationTest` · `AssumeTenantExchangeIntegrationTest`(실 `/oauth2/token` 응답 본문) ⚪ CI.
- **AC-2**: `OperatorAssignmentCheckMfaRequiredTest`(정책 ON 테넌트 true · 같은 운영자 대조 테넌트 false · 플랫폼/파트너십 경로 불문) + provider(대조군 `mfaRequired=false` → 발급) + `OperatorAssignmentCheckControllerSliceTest`(필드 항상 존재 · 읽기 실패 500). IT(V0047 실표 · `require_mfa=FALSE` 행 = 꺼짐 · `CHECK '*'`) ⚪ CI.
- **OD-3**: 배정 테넌트 정책 ON → 403 · 홈 `'*'` 기여 없음(질의 인자 캡처) · 파트너십 host 미질의.
- **fail-closed**: `mfaRequired` 부재 · null · 비불리언 → true(`AdminAssignmentClientUnitTest` 4건) · 요구 읽기 실패 → 500(교환) / 5xx(내부 체크).
- **온보딩 불변**: `OnboardingControllerSecondFactorRegressionTest` — 포트의 실제 구현(람다)을 거쳐 `amr=[pwd]` 인 호출자가 그대로 201.

### e2e / nightly 영향 (grep 으로 판정 — 실행 아님)

| 스위트 | 로그인 신원 | S4 뒤 |
|---|---|---|
| console e2e(nightly) — `projects/platform-console/.../tests/e2e/fixtures/seed.sql` | `e2e-super-admin`(`'*'`, SUPER_ADMIN) · `e2e-target-operator`(역할 없음) | 픽스처 § 5 가 SUPER_ADMIN 을 이미 완화 → 역할 항 거짓, 정책 표 비어 있음 ⇒ 교환 · assume 모두 이전과 같다. 픽스처는 **주석만** 정정 |
| federation-hardening e2e — `tests/federation-hardening-e2e/fixtures/seed.sql` | SUPER_ADMIN(`'*'`) + 위임 TENANT_ADMIN 들 | 같은 완화가 있고(§ 5), TENANT_ADMIN 은 `require_2fa=FALSE` ⇒ 무영향. 변경 없음 |
| `scripts/console-demo/seed/01-iam.sql`(로컬 콘솔 데모) | SUPER_ADMIN | 이미 완화 ⇒ 무영향 |
| 포트폴리오 데모 `infra/demo/seed/lib.sh` `operator_token`(assume `demo-corp` · demo@/requester@) | SUPER_ADMIN 둘 | OD-5 완화(데모 오버레이)로 통과. 오버레이가 빠지면 이 시드가 `invalid_grant insufficient_user_authentication` 으로 실패한다 — 주석에 명기 |
| e2e 스펙 디렉터리에서 `demo@demo.com` · `requester@` · `platform@` 사용 | — | 0건(grep) |

### 명세와 다르게 · 명세가 말하지 않아 고른 것

1. **V0047 존재 가드** — data-model 은 «`INFORMATION_SCHEMA` 존재 가드(V0027/V0029 패턴, `@var` 금지)» 라 적었는데 V0029 패턴 자체가 `@var` 를 쓴다. 표 생성이라 `CREATE TABLE IF NOT EXISTS` 로 했다(S2b 의 V0043 과 같은 선택).
2. **`amr ∋ mfa` 인 교환은 요구 읽기를 하지 않는다** — 결과는 계약과 같고(요구 여부와 무관하게 발급), 읽기 실패로 2단계를 거친 운영자까지 500 을 받는 일을 없앤다.
3. **auth `AssignmentResult` 의 back-compat 생성자(2·3 인자)는 `mfaRequired=false`** — 시험이 직접 만드는 결과용. 운영 경로(`AdminAssignmentClient`)는 항상 응답에서 읽고 부재를 `true` 로 읽는다.
4. **데모 완화의 집** — OD-5 문구는 «데모 dev 시드» 인데 그 파일(`migration-dev`)은 기본 프로필에서도 돈다(AC-0). 그래서 데모 전용 위치를 새로 만들었다(위 § OD-5). 소유자 결정의 의도(«데모만»)를 지키는 해석이며, 결과로 로컬 기본 프로필의 데모 신원은 완화되지 **않는다**.

### ⚪ 열린 것

- ⚪ CI 첫 실행: `TokenExchangeIntegrationTest`(S4 3건) · `OperatorAssignmentCheckIntegrationTest`(S4 5건 — V0047 · CHECK) · `AssumeTenantExchangeIntegrationTest`(S4 1건, 실 HTTP 본문).
- ⚪ 라이브(데모 실기동 · 콘솔 단계 상승 왕복 · 데모 재부팅 뒤 R__ 적용) 미실행. 머지 뒤 nightly e2e 1회 확인 권장(console · federation).
- ⚪ AC-3 · AC-4 는 이 슬라이스 밖(S2b 증거 · S5).

---

## S5 기록 (2026-10-09 UTC)

> 구현 = Opus 5.5 (backend-engineer) · worktree `feat/mono-771-s5-entry-policy`(origin/main `7753bb5a8`, S1 · S2a · S2b · S2c · S3 · S4 포함). **AC-4 를 체크했다** — 티켓 문구가 «시험 **또는** 라이브» 이고, 아래 사슬의 모든 고리가 이 세션에서 로컬 통과한 시험이다(라이브 ⚪). 다른 AC 체크박스는 건드리지 않았다. Status 는 `in-progress` 유지.

### 바꾼 파일

| 층 | 파일 | 무엇 |
|---|---|---|
| admin 권한 · 감사 | `domain/rbac/Permission`(`TENANT_SECURITY_MANAGE` + 카탈로그) · `application/ActionCode`(`TENANT_ENTRY_POLICY_SET`) · `AdminActionPermissionRegistry`(target `TENANT`, permission `tenant.security.manage`) · `RequiresPermissionAspect`(PUT 의 DENIED 행 action code) | 새 키는 `tenant.manage` 와 분리(OD-1 — TENANT_ADMIN 에게 생성 · 정지를 열지 않는다) |
| admin seed | `db/migration/V0048__seed_tenant_security_manage_permission.sql` | `SUPER_ADMIN` · `TENANT_ADMIN` 매핑만(inert). `account.2fa_reset` 은 **S6 로 미룸**(아래 § 명세와 다르게 1) |
| admin port · JPA | `application/port/TenantEntryPolicyManagementPort`(신규 — find · save · `EntryPolicyView`) · `TenantEntryPolicyPort`(S4 읽기 그대로, 1-메서드 유지) · `infrastructure/persistence/TenantEntryPolicy{JpaEntity,PortImpl}`(두 포트를 한 어댑터가 구현 ⇒ 관리 API 가 쓴 행 = 진입 판정이 읽는 행) | 첫 쓰기 = `persist`(PK 지정 · primitive `@Version` 이라 Spring Data `save` 는 merge 로 간다), 첫 쓰기 경합 · 갱신 경합 모두 `ObjectOptimisticLockingFailureException` → 409 |
| admin 관리 API | `application/TenantEntryPolicyUseCase` · `presentation/TenantEntryPolicyController` | GET/PUT 계약 그대로. 순서: id 검증(`'*'`·정규식 → 400) → `TenantScopeGuard`(PUT=DENIED 행 · GET=행 없음) → account-service 존재 확인(404/503, **안 씀**) → 쓰기 → 감사 `TENANT_ENTRY_POLICY_SET` «requireMfa 이전→이후»(이전 없음 = `none`, no-op 포함 매번) |
| admin 사전 점검 | `application/TenantEntryPolicyPrecheckUseCase` · port `SecondFactorEnrolmentPort` · `infrastructure/client/SecondFactorEnrolmentAdapter`(500개 분할) · `AuthServiceClient.enrolledAmong`(fail-closed) · `AdminOperatorJpaRepository.findActiveOidcSubjectsInTenantScope`(운영자 목록과 같은 홈 ∪ 배정 술어, ACTIVE) | `GET .../entry-policy/enrolment-summary` → `{operators, enrolled, notEnrolled, unlinked}` |
| auth 사전 점검 | `application/SecondFactorEnrolmentStatusQuery`(500 상한 · 확정만) · `presentation/InternalSecondFactorStatusController`(`POST /internal/auth/second-factor/enrolment-status`) · `AccountTotpRepository.findConfirmedAccountIds`(default 루프) + `AccountTotpRepositoryImpl`/`AccountTotpJpaRepository`(IN 한 번, `confirmed_at IS NOT NULL`) | `/internal/auth/**` 의 기존 `internal.invoke` JWT 게이트 아래 |
| console | `features/tenant-entry-policy/**`(client profile `tenant_entry_policy` · api · SSR state · `EntryPolicyPanel` · `EntryPolicySection` · hooks) · BFF `app/api/tenants/[tenantId]/entry-policy/{route.ts, enrolment-summary/route.ts}` · `(console)/tenants/[tenantId]/page.tsx`(상세 아래 토글) · 신규 `(console)/security-settings/page.tsx` | 아래 § 콘솔 |
| console 원장 | `console-nav-config.ts`(「조직 설정」 끝에 보안 설정) · `permission-map.ts`(행 + `RBAC_SEED_MATRIX` `tenant.security.manage`) · `iam-guide/data.ts`(키 · 두 역할 · 메뉴 · 접근 매트릭스) · `sample/coverage.ts`(surface · screen 모두 `pending`) · `shared/api/errors.ts`(`OPTIMISTIC_LOCK_CONFLICT` 문구) | 드리프트 가드들이 요구하는 사본 |
| 계약 · 명세 (먼저) | `admin-api.md`(§ enrolment-summary 신규 · PUT 409 행 · GET 403 감사 문장 정밀화) · `internal/admin-to-auth.md`(§ enrolment-status 신규) · `console-integration-contract.md` § 2.4.3.3(사전 점검 · 콘솔 표면) · `rbac.md`(키 엔드포인트 · seed 노트) · `admin-service/data-model.md`(seed 두 장으로) · `admin-service/dependencies.md`(auth 읽기 + Idempotency-Key 는 명령에만) | |
| 시험 | admin `TenantEntryPolicyUseCaseTest`(12) · `TenantEntryPolicyControllerSliceTest`(10) · `TenantEntryPolicyPrecheckUseCaseTest`(5) · `TenantEntryPolicyTransitionChainTest`(1, AC-4) · `SecondFactorEnrolmentAdapterTest`(2) · `AdminActionPermissionRegistryTest`(+1) · IT `TenantEntryPolicyIntegrationTest`(`@Tag("integration")`, 7) · `TenantAdminRoleSeedIntegrationTest`(기대 집합 + `tenant.security.manage`) / auth `SecondFactorEnrolmentStatusQueryTest`(4) · `InternalSecondFactorStatusControllerSliceTest`(3) · `AccountTotpRepositoryIntegrationTest`(+1) / console `entry-policy-{panel,proxy,client,state}` · `security-settings-page` · `tenants-detail-page`(+1) · `sidebar-iam-group`(조직 설정 순서) · `sidebar-role-subscription`(+5 역할 노출) | |

### AC-4 증거 사슬 (OD-4 «등록 유도 · 유예 없음»)

| 고리 | 무엇을 증명 | 시험 (전부 이 세션 로컬 통과) |
|---|---|---|
| ① 켜기 | 관리 API 가 쓴 행을 진입 판정이 그대로 읽는다 | `TenantEntryPolicyTransitionChainTest`(실 `TenantEntryPolicyUseCase` · `OperatorSecondFactorRequirement` · `TokenExchangeService`, 공유 인메모리 표) |
| ② 거절 = 구별된 응답 | 정책 ON 직후 `amr=[pwd]` 교환 → `MfaRequiredException`(403 `MFA_REQUIRED`, 401 아님) · assume 요구 ON(형제 테넌트는 OFF — 대조군) | 같은 시험 + S4 `AdminLoginControllerSliceTest`(HTTP 매핑) |
| ③ 거절 → 단계 상승 | 콘솔이 403 `MFA_REQUIRED` 를 `/api/auth/step-up` → `authorize?acr_values=mfa` 로(온보딩 아님) | S3 `mfa-step-up-routes.test.ts`(🔴 403 → step-up · 🔴🔴 401 만 온보딩) |
| ④ 단계 상승 → 등록 | `acr_values=mfa` · 확정 등록 없음 → `/mfa/setup` | S2b `AuthorizeSecondFactorGateTest`(«등록 없음 → /mfa/setup») |
| ⑤ 등록 전제 | 인증된 이메일 없으면 등록 안 됨(OD-4 TOFU 완화) | S2b/S2c `MfaPageSliceTest`(`setup_unverifiedEmail`) |
| ⑥ 등록 뒤 | `amr=[pwd,otp,mfa]` → 같은 운영자 발급 · 끄면 `[pwd]` 로 다시 발급 | `TenantEntryPolicyTransitionChainTest` |
| (CI) 실 쓰기 경로 | 실 MySQL: PUT ON → 교환 403 `MFA_REQUIRED` → mfa 200 → PUT OFF → 200 | `TenantEntryPolicyIntegrationTest.ac4_transitionThroughTheRealWritePath` ⚪ CI 첫 실행 |

🔵 사슬은 **한 프로세스의 한 시험이 아니다** — 서비스 경계마다 그 서비스의 시험이 문다(①②⑥ admin · ③ console · ④⑤ auth). 고리 사이의 접합(admin 403 코드 ↔ 콘솔이 읽는 코드, `acr_values=mfa` 문자열)은 S1 계약과 각 슬라이스의 시험이 같은 상수를 고정한다.

### 콘솔

- **테넌트 상세**(`SUPER_ADMIN`): 상세 아래 «운영자 진입 2단계 인증» 패널. 테넌트가 해소된 뒤에만 정책을 읽고, 그 403/503 은 패널만 바꾼다(상세 불변).
- **«보안 설정»** `/security-settings`(「조직 설정」 그룹 끝, `TENANT_ADMIN`): 활성 테넌트 = path. 테넌트 없음 → 테넌트 게이트, `*` → «테넌트 상세로» 안내(정책 읽기 0).
- **노출**: nav 는 역할 seed 행렬로 숨김(`TENANT_ADMIN` · `SUPER_ADMIN` 보임 / `TENANT_BILLING_ADMIN` · `ORG_ADMIN` · CS 역할 숨김). 403 이면 토글 자체를 그리지 않고 «권한 없음».
- **확인창**: 사유 필수 · `Idempotency-Key` 없음. 켜기 = 계약 고정 문구(«잠긴다» 없음) + 사전 점검 «운영자 N명 중 2단계 미등록 M명» — **읽기 실패면 숫자 없이**(0 을 지어내지 않음, 토글은 막지 않음) + 지금 세션 `amr` 에 `mfa` 가 없으면 «본인도 다음 진입 때» 경고 + IAM 2단계 ≠ break-glass 2단계 한 줄. 끄기 = 사전 점검 없음.

### 검증 (rc 는 `cmd > file 2>&1; echo rc=$?`)

| 무엇 | 결과 |
|---|---|
| `./gradlew :projects:iam-platform:apps:admin-service:test` | rc=0 — 158 클래스 · 1010건 · 실패 0 · skip 58(Docker 조건부) |
| `./gradlew :projects:iam-platform:apps:auth-service:test` | rc=0 — 148 클래스 · 1170건 · 실패 0 · skip 33 |
| `compileTestJava`(admin · auth, 통합 소스 포함) | rc=0 |
| console `pnpm install --frozen-lockfile` | rc=0 |
| console `npx tsc --noEmit` | rc=0 |
| console `npm run lint` | rc=0 («No ESLint warnings or errors») |
| console 대상 12 파일(신규 5 + 영향 7) `npx vitest run …` | rc=0 — 175건 |
| console 전체 `npx vitest run` | 1회차 rc=1 — 무관한 3 파일 5건이 **5초 타임아웃**(`OperatorsScreen` · `AccountLookup` · `TenantsScreen`, 이 PR 이 건드리지 않은 화면) → 그 3 파일 단독 rc=0(28건) → 전체를 `--maxWorkers=4 --minWorkers=1` 로 재실행 **rc=0 — 361 파일 · 4099건**. 호스트 부하 타임아웃(기존 함정)으로 판정 |
| `bash scripts/check-dev-seed-migration-band.sh` · `check-flyway-unresolvable-placeholder.sh` · `check-flyway-version-collision.sh` (스테이지 뒤) | 셋 다 rc=0 |
| e2e / nightly 영향(grep, 실행 아님) | console `tests/e2e` · `e2e-smoke` · `tests/federation-hardening-e2e/specs` 에서 `/tenants/` · `tenant-detail` · `nav-partnerships` · `조직 설정` · `security-settings` · `entry-policy` 참조 0건 |
| 🔴 bite 1 — PUT 의 `requireTenantInScope` 제거 | admin `*TenantEntryPolicy*` 중 **`otherTenant_denied` 1건만** RED → 복원 → GREEN. (처음엔 엄격 stub 이 «쓰이지 않은 범위 stub» 으로 6건을 함께 붉혔다 — 양성 경로의 범위 stub 을 전제(lenient)로, 거절 쪽만 엄격으로 나눠 bite 가 정확히 한 시험만 물게 했다) |
| 🔴 bite 2 — 콘솔 사전 점검 실패 문구를 «미등록 0명» 으로 | `entry-policy-panel.test.tsx` 중 **«503 → 숫자 없음» 1건만** RED → 복원 → GREEN |
| 필수 가드 3종(`git add` 뒤) | `check-index-queue-drift.sh` · `check-task-id-collision.sh` · `check-walkthrough-ledger-drift.sh` — 커밋 직전 실행, 결과는 PR 본문. `scripts/` 추가 · 삭제 없음 |

### 명세와 다르게 · 명세가 말하지 않아 고른 것 (오케스트레이터 확인 요망)

1. **seed 를 두 장으로** — data-model 은 `tenant.security.manage` + `account.2fa_reset` 한 장(S5·S6)이라 적었다. S5 는 `V0048` 에 `tenant.security.manage` 만 넣었다: 엔드포인트 · 코드 상수 없이 `account.2fa_reset` 을 먼저 시드하면 `GET /api/admin/roles` 가 `GET /api/admin/permissions`(코드 카탈로그)에 없는 키를 보인다. data-model · rbac 노트를 그에 맞게 고쳤다.
2. **GET 의 403 감사** — 계약은 «403 은 best-effort DENIED 행» 한 문장. 구현은 `PERMISSION_DENIED`(키 없음)=DENIED 행(aspect), `TENANT_SCOPE_DENIED`(범위 밖)=행 없음(MONO-737 읽기 규약 `requireTenantReadable`). 계약 문장을 이 구분으로 정밀화했다.
3. **사전 점검 = 새 계약 두 개** — S1 은 사전 점검을 «생산자 읽기가 아직 없다» 로 미뤘고 콘솔 계약도 «producer 가 정의할 때까지 계약 아님» 이었다. 이 PR 이 **계약을 먼저 쓰고** 구현했다: admin `GET .../entry-policy/enrolment-summary` + admin→auth `POST /internal/auth/second-factor/enrolment-status`. 모집단 = 테넌트의 ACTIVE 운영자(홈 ∪ 배정 — 운영자 목록과 같은 술어), 플랫폼 `'*'` · 파트너십 참여자 제외, `unlinked`(계정 연결 없음) 별도. auth 읽기 실패 = 503(숫자를 지어내지 않음).
4. **포트 분리** — S4 의 1-메서드 `TenantEntryPolicyPort` 에 쓰기를 더하면 그것을 람다로 만드는 기존 시험 4개가 깨진다. 진입 판정 읽기(`TenantEntryPolicyPort`)와 관리 읽기/쓰기(`TenantEntryPolicyManagementPort`)를 나누고 한 JPA 어댑터가 둘 다 구현한다(판정은 쓰기 핸들을 갖지 않는다).
5. **PUT 트랜잭션 안의 존재 확인** — 원격 호출이 DB 트랜잭션 안에 있다(`UpdateTenantUseCase` 선례). 순서상 존재 확인이 쓰기보다 먼저라 실패 시 아무것도 쓰이지 않는다. `TenantOrgNodePlacementUseCase` 처럼 트랜잭션 밖으로 빼려면 쓰기+감사를 별도 빈으로 떼야 한다 — 이 슬라이스에선 하지 않았다.
6. **«보안 설정» 의 nav 자리** — 계약은 «새 `(console)` 페이지» 까지만. 「조직 설정」(회사가 정하는 것 — 구독 · 파트너십 옆)에 두었다. IAM 드릴의 7개 고정 순서 시험을 건드리지 않는 자리이기도 하다.
7. **샘플 모드** — 새 surface `tenant_entry_policy` · 화면 `/security-settings` 를 `pending` 으로 등록(fixture 없음). 샘플 방문자는 테넌트 상세에서 패널만 «불러올 수 없음» 을 본다.

### ⚪ 열린 것

- ⚪ CI 첫 실행: `TenantEntryPolicyIntegrationTest`(7 — V0048 holder 집합 · 실 쓰기 → 실 교환 AC-4 · 범위 밖 403 · `'*'` 400 · 없는 테넌트 404 · 사전 점검 실 roster 질의 × WireMock auth · auth 500 → 503) · `TenantAdminRoleSeedIntegrationTest`(기대 집합 변경) · `AccountTotpRepositoryIntegrationTest.findConfirmedAccountIds_confirmedOnly`. 이 호스트에 Docker 없음.
- ⚪ 라이브(데모 실기동 · 실제 토글 → 실제 등록 왕복) 미실행. 머지 뒤 nightly e2e 1회 확인 권장.
- ⚪ 샘플 fixture(`tenant_entry_policy`) — `pending` 으로 둠.
- ~~⚪ S6(계정 TOTP 리셋 · `account.2fa_reset` seed) 미착수.~~ → S6 구현됨 — 아래 § S6 기록.

### S5 CI 1회차 (PR #4266, run 37917869840 «Integration (iam A, Testcontainers)») — 3 실패 진단 · 조치

| # | 실패 | 원인 (로그 근거) | 조치 |
|---|---|---|---|
| 1 | `TenantEntryPolicyIntegrationTest` GET off → `$.updatedAt` 없음 | 응답 본문 `{"tenantId":"s5-tenant-x","requireMfa":false}` — `application-test.yml:47` `default-property-inclusion: non_null` 이 null 키를 지웠다. 계약은 «키 항상 존재, 미설정이면 null». slice 시험은 test 프로필이 아니라 못 잡았다 | **코드 수정**: `EntryPolicyResponse` 에 `@JsonInclude(ALWAYS)` — 전역 설정과 무관하게 계약 모양. slice 시험에 `spring.jackson.default-property-inclusion=non_null` 을 걸어 같은 조건을 재현. bite(애노테이션 제거) → slice 의 GET-off 1건만 RED → 복원 GREEN |
| 2 | `unknownTenant_404` 가 403 | 응답 `TENANT_SCOPE_DENIED … 's5-ghost'`. `admin_operator_roles` PK 가 `(operator_id, role_id)`(V0004:48) — ADMIN_X 에 TENANT_ADMIN 두 번째 행(`s5-ghost`)을 `INSERT IGNORE` 한 것이 **조용히 버려졌다**. 시험의 전제가 스키마상 불가능 | **시험 수정**(시험이 틀렸다): `s5-ghost` 에 grant 된 별도 TENANT_ADMIN 운영자로 PUT → 범위 통과 → account-service 404 → 404 |
| 3 | `TokenExchangeIntegrationTest` BE-377 로컬 로그인 401 기대 → 500 `INTERNAL_ERROR` | `Resolved Exception = jakarta.servlet.ServletException` — DispatcherServlet 이 **`Exception` 이 아닌 `Throwable`(Error)** 을 감쌀 때만 나오는 형태(`RuntimeException` 이면 그 타입이 그대로 찍힌다). 로그 · 업로드 보고서 어디에도 스택 없음. 그 경로(null 해시 → Argon2 m=64MiB 더미 verify → `InvalidCredentialsException`)에 S5 코드는 없다. main 의 같은 잡은 base `7753bb5a8` 포함 최근 7회 모두 success. S5 가 이 JVM 에 더한 것은 상주 Spring 컨텍스트 하나(`TenantEntryPolicyIntegrationTest`, 고유 WireMock `@DynamicPropertySource` — 알파벳 순서상 TokenExchange 바로 앞 클래스군). 추정 = 캐시된 컨텍스트로 힙이 차 있는 상태에서 64MiB Argon2 할당이 `OutOfMemoryError` (예전 이 시험의 5분 타임아웃 flake 와 같은 계열 — GC 압박). **증명 아님**(스택 없음) | ① `TenantEntryPolicyIntegrationTest` 에 `@DirtiesContext(AFTER_CLASS)` — S5 이전의 상주 컨텍스트 수로 되돌림. ② BE-377 시험에 **진단만** 추가(5xx 일 때 힙 used/total/max 와 resolved exception 스택을 stdout 으로) — 단언 불변. 재발 시 Error 이름이 로그에 남는다 |

검증: admin `compileTestJava` rc=0 · `:admin-service:test` rc=0(1010건 · 실패 0 · skip 58). IT 는 Docker 없음 → CI 재실행 대기.

---

## S6 기록 (2026-10-09 UTC)

> 구현 = Opus 5.5 (backend-engineer) · worktree `feat/mono-771-s6-totp-reset`(origin/main `b7c389421`, S1~S5 포함). 범위 = 슬라이스 행 S6(티켓 Edge Case 2 · OD-6). **AC 체크박스는 건드리지 않았다**(S6 는 Edge Case 라 티켓 AC 가 없다). Status 는 `in-progress` 유지.

### 계약 먼저 — 쓴 것 · 고친 것

| 파일 | 무엇 |
|---|---|
| `specs/contracts/http/internal/admin-to-auth.md` | **신규 § `POST /internal/auth/accounts/{accountId}/second-factor/reset`** — 인증 = 기존 `/internal/auth/**` `internal.invoke` 워크로드 JWT(없음 · 무효 · 사용자 토큰 → `401 UNAUTHORIZED`) · 헤더 `Idempotency-Key` · `X-Operator-ID`(상관용) · `X-Tenant-Id` 안 읽음 · body `{reason, operatorId}` · **판정 순서**: 행 있음(확정 · 대기 불문) → 삭제 → `200 {accountId, resetAt, wasConfirmed}` / 행 없음 → 그때만 account-service `status-with-tenant` → `404 ACCOUNT_NOT_FOUND` · `404 TOTP_NOT_ENROLLED` · 읽기 실패 `503 SERVICE_UNAVAILABLE` · «멱등» 절(상태 멱등 · 응답 비멱등 — 재시도가 «이미 지운» 뒤 닿으면 `404 TOTP_NOT_ENROLLED`) · Caller fail-closed 매핑 · 이벤트 · 메일 없음 |
| `specs/contracts/http/admin-api.md` § `POST /api/admin/accounts/{accountId}/2fa/reset` | S1 공개 계약을 **정밀화**(아래 «S1 과 다른 것») — 사유 두 개 필수 · 처리 순서 · 하류 링크 · `downstream_detail` · Errors 표의 `400 VALIDATION_ERROR` 행과 404 · 503 행 주석. 경로 · 메서드 · 권한 · 상태 코드 집합 · 응답 모양은 **불변** |
| `specs/contracts/http/internal/auth-to-account.md` | 신규 § «계정 존재 여부 — 2단계 인증 리셋의 지울 것 없음 구별»(새 엔드포인트 아님 — 기존 `status-with-tenant` 의 이 호출자 사용 기록, S2b 의 이메일 절과 같은 형식) |
| `specs/services/admin-service/rbac.md` | TASK-MONO-771 노트에 S6 seed 파일 이름(`V0049`) · 키 상수 이름. Permission Keys · Seed Roles · Seed Matrix 는 S1 이 이미 맞게 적어 둠(불변) |
| `specs/services/admin-service/data-model.md` | Migration Strategy 의 S6 seed 파일 이름 |

### 바꾼 코드

| 층 | 파일 | 무엇 |
|---|---|---|
| admin seed | `db/migration/V0049__seed_account_2fa_reset_permission.sql` | `SUPER_ADMIN` · `SECURITY_ANALYST` → `account.2fa_reset` (`INSERT IGNORE`, 매핑만 — inert/net-zero). 다음 빈 번호 실측: `db/migration` 마지막 `V0048`, `migration-dev` 는 `V0014/V0023/V0028` + R__ |
| admin 권한 · 감사 | `domain/rbac/Permission.java:110,143`(상수 + 카탈로그 끝 — rbac.md 순서) · `application/ActionCode`(`ACCOUNT_2FA_RESET`) · `AdminActionPermissionRegistry`(target `ACCOUNT`, permission `account.2fa_reset`) · `RequiresPermissionAspect`(DENIED 행 action code) | S5 의 `tenant.security.manage` 추가와 같은 다섯 자리 |
| admin 표면 | `presentation/AccountSecondFactorAdminController.java:41`(`@RequiresPermission(ACCOUNT_2FA_RESET)`) · `:48`(헤더 사유) · `:51`(body 사유) · dto 2 | `AccountAdminController` 와 같은 경로 접두지만 **분리** — 그쪽은 `QueryTenantScopeGate` + 하류 `X-Tenant-Id` 이고 이쪽은 플랫폼 전용 · `X-Tenant-Id` 미독 |
| admin 유스케이스 | `application/AccountSecondFactorResetUseCase.java` — `:52` 플랫폼 범위(OD-6 2차 게이트, `OperatorLookupPort` · 아니면 `TENANT_SCOPE_DENIED` + DENIED 행) → `:56` 키 재사용 409 → `:63` IN_PROGRESS → 하류 → SUCCESS(`wasConfirmed=…`) / FAILURE · `:78` auth 404 코드 → 공개 404 둘 | `AccountAdminUseCase`(lock) 의 A10 모양 그대로 |
| admin 포트 · 클라이언트 | `application/port/AccountSecondFactorResetPort` · `infrastructure/client/AccountSecondFactorResetAdapter` · `AuthServiceClient.resetSecondFactor`(Bearer · `Idempotency-Key` · `X-Operator-ID`, 4xx 는 status + body `code` 를 싣는 `NonRetryableDownstreamException` — 두 404 를 가르려고) | 형제 `forceLogout` 의 클라이언트 · 시스템 자격 · 헤더 패턴 복사. 🔵 `SessionAdminUseCase` 처럼 클라이언트를 유스케이스가 직접 import 하지 않고 포트를 뒀다(S5 `SecondFactorEnrolmentPort` 와 같음) |
| admin 예외 | `AccountSecondFactorNotEnrolledException extends TotpNotEnrolledException` + `AdminExceptionHandler` 핸들러 | 같은 공개 코드 `TOTP_NOT_ENROLLED`, 다른 문구(부모 핸들러 문구가 «재발급 전 등록 필요» 로 고정돼 있다) |
| auth 유스케이스 | `application/AccountSecondFactorResetUseCase.java:57`(행 조회) · `:60`(삭제) · `:66`(행 없을 때만 존재 확인) | 권한 재판정 없음 — 워크로드 게이트가 호출자를 가른다 |
| auth 표면 | `presentation/InternalSecondFactorResetController.java:44-48`(결과 → 200 / 404 둘, 503 은 기존 `AuthExceptionHandler`) | `/internal/auth/**` 기존 체인 아래 — `SecurityConfig` 변경 0 |
| console 원장 사본 | `shared/guide/permission-map.ts`(`RBAC_SEED_MATRIX` 행) · `features/iam-guide/data.ts`(`PERMISSION_KEYS` 항목 · `SUPER_ADMIN` · `SECURITY_ANALYST` 권한 목록) | 아래 «슬라이스 행과 다른 것 2» |

### S1 공개 계약 · 슬라이스 행과 다른 것 (이유)

1. **사유 = 헤더 와 body 둘 다 필수** — S1 의 «`X-Operator-Reason` 또는 body `reason` 누락 → 400» 을 문자 그대로(어느 한쪽이라도 없으면) 읽었다. lock 은 «헤더 또는 body 중 하나» 로 구현돼 있어 같은 문장이 두 뜻으로 읽힌다 — 그래서 계약에 «둘 다» 를 명시했다. 감사 `reason` = body(상세 · 본인 확인 근거), bulk-lock 과 같은 모양.
2. **console 원장 사본 갱신** — 슬라이스 행은 admin · auth · `internal/` 계약뿐이다. 그러나 `permission-map.ts` 머리 주석이 «rbac.md 를 바꾸는 PR 은 이 파일도 함께» 이고, seed 가 실재하게 된 순간 사본이 SUPER_ADMIN · SECURITY_ANALYST 의 실제 권한을 덜 말한다. 화면 · nav 변경 0(이 키로 게이트되는 nav 항목 없음).
3. **`ACCOUNT_NOT_FOUND` 의 판정 자리** — S1 은 «계정 미존재» 만 적었다. 판정은 auth-service 가, **지울 행이 없을 때만** 한다(행이 있으면 계정 존재와 무관하게 지운다 — 삭제된 계정에 남은 비밀은 지워지는 편이 옳다). admin 쪽에 존재 확인을 두지 않은 이유: admin → account 계약에 «어느 테넌트든» 계정 읽기가 없다(`/status` 는 헤더 없으면 `fan-platform` 고정 — 풀 계정을 못 찾는다). auth 는 그 읽기(`status-with-tenant`)를 이미 계약으로 갖고 있다(S2b).
4. **읽기 실패 = 503**(행 없음 + account-service 장애) — «계정 없음» · «등록 없음» 중 하나로 메우지 않는다. 어느 쪽이든 아무것도 지워지지 않았다.
5. **재시도 뒤의 404** — admin 의 `@Retry`(5xx · 타임아웃에만)가 «첫 시도가 지웠는데 응답만 잃은» 경우에 닿으면 `404 TOTP_NOT_ENROLLED` + `FAILURE` 행이 된다. 서버가 `Idempotency-Key` 로 첫 응답을 재생하게 만들지 않았다 — 리셋은 드문 수동 명령이고 틀리는 것은 응답 하나, 상태(등록 없음)는 옳다. 계약 «멱등» 절에 적었다.
6. **플랫폼 범위 판정 = 운영자 홈 `'*'`** — rbac.md 는 «플랫폼 범위 grant(`tenant_id='*'`)» 라 적었다. 코드의 기존 «플랫폼 운영자» 술어는 전부 운영자 홈(`AdminOperator.isPlatformScope` · `QueryTenantScopeGate`)이다 — 계약이 «`tenant.manage` 의 inline platform-scope 검사와 같은 모양» 이라 지시하므로 그 술어를 그대로 썼다. grant 행의 `tenant_id` 를 따로 읽지 않는다: `V0026` 이 기존 행을 홈으로 맞췄지만 그 뒤의 grant 가 언제나 홈과 같은지는 **재지 않았다**(홈 `'*'` 인데 grant 가 테넌트인 `SECURITY_ANALYST` 는 여기서 통과한다 — `tenant.manage` 와 같은 판정). 그 경우를 막아야 한다면 rbac.md 문구 쪽을 grant 기준으로 바꾸는 별도 결정이다.

### 검증 (rc 는 `cmd > file 2>&1; echo rc=$?`)

| 무엇 | 결과 |
|---|---|
| `compileTestJava`(admin · auth, 통합 소스 포함) | rc=0 |
| `./gradlew :projects:iam-platform:apps:admin-service:test` | rc=0 — 160 클래스 · 1032건 · 실패 0 · skip 58(Docker 조건부) |
| `./gradlew :projects:iam-platform:apps:auth-service:test` | rc=0 — 151 클래스 · 1184건 · 실패 0 · skip 33 |
| 신규 · 변경 시험 (admin) | `AccountSecondFactorResetUseCaseTest` 9 · `AccountSecondFactorAdminControllerSliceTest` 9 · `AuthServiceClientUnitTest` +3(13) · `AdminActionPermissionRegistryTest` +1(24) · `PermissionCatalogTest`(카탈로그 = 반사 상수 집합, 그대로 통과) — 전부 통과 |
| 신규 · 변경 시험 (auth) | `AccountSecondFactorResetUseCaseTest` 5 · `InternalSecondFactorResetControllerSliceTest` 5 · `InternalSecondFactorResetAuthSliceTest` 3(Bearer 없음 401 · `internal.invoke` 없는 토큰 401 — 둘 다 유스케이스 미호출 · 워크로드 토큰 200) · `AccountSecondFactorServiceTest` +1(14) — 전부 통과 |
| 🔴 «리셋 뒤 = 등록 유도» | `AccountSecondFactorServiceTest.afterAdminReset_accountIsNotEnrolled_andCanEnrolAgain` — 실제 서비스 + 실제 리셋 유스케이스 + 같은 인메모리 표: 리셋 뒤 `hasConfirmedEnrollment=false`(= `AuthorizeSecondFactorGate` 가 읽는 술어 — `false` + `acr_values=mfa` → `/mfa/setup` 은 S2b `AuthorizeSecondFactorGateTest.stepUp_notEnrolled_goesToSetup`) · `status().enrolled=false` · 잃은 앱의 코드 `NOT_ENROLLED` · 잃은 복구 코드 거절 · `startEnrollment` 다시 `PENDING_CREATED`(OD-4 전제 재질의) |
| 🔴 bite — admin 플랫폼 범위 게이트 제거 | 처음엔 S6 유스케이스 시험 9건 중 8건이 RED — 원인은 STRICT_STUBS «쓰이지 않은 stub»(S5 bite 1 과 같은 현상). 양성 경로의 운영자 조회 stub 을 전제(lenient)로 바꾼 뒤 재실행 → **`tenantScopedHolder_denied` · `unknownOperator_denied` 2건만** RED → 복원 → GREEN |
| console `pnpm install --frozen-lockfile` · `npx tsc --noEmit` | 둘 다 rc=0 |
| console `npx vitest run` permission-map-drift · IamGuideScreen · sidebar-role-subscription | rc=0 — 3 파일 · 51건 |
| 가드(스테이지 뒤) | 커밋 직전 실행 — 결과는 PR 본문. `scripts/` 추가 · 삭제 없음 |

### 권한 대조군 (OD-6) — 어디서 무는가

| 행위자 | 기대 | 시험 |
|---|---|---|
| `SUPER_ADMIN`(`'*'`) | 200 · auth 1회 · SUCCESS 행 | IT `platformRoles_allowed` ⚪ CI |
| `SECURITY_ANALYST`(`'*'`) | 200 · auth 1회 · SUCCESS 행 | 같은 IT ⚪ CI |
| `TENANT_ADMIN` · `SUPPORT_READONLY` · `SUPPORT_LOCK` · `TENANT_BILLING_ADMIN` | 403 `PERMISSION_DENIED` · auth 0회 · DENIED 행(`permission_used=account.2fa_reset`) | IT `otherRoles_denied` ⚪ CI + slice `noPermission_403`(키 판정이 `account.2fa_reset` 으로 일어남) 로컬 통과 |
| 고객 테넌트 홈 `SECURITY_ANALYST` | 403 `TENANT_SCOPE_DENIED` · auth 0회 | IT `tenantHomedAnalyst_scopeDenied` ⚪ CI + 유스케이스 `tenantScopedHolder_denied` 로컬 통과 |
| seed 보유 집합 | 정확히 `{SECURITY_ANALYST, SUPER_ADMIN}` | IT `seed_holders` ⚪ CI |
| `ORG_ADMIN` | 403(키 미보유) | 대조군 IT 에 넣지 않았다 — 노드 grant(`org_node_id`)가 필요한 역할이라 시드가 다르다. seed 보유 집합 단언이 «ORG_ADMIN 미보유» 를 이미 덮는다 |

### ⚪ 열린 것

- ⚪ **CI 첫 실행**: `AccountSecondFactorResetIntegrationTest`(`@Tag("integration")`, 실 MySQL V0049 + 실 `PermissionEvaluator` + WireMock auth — 권한 대조군 8칸 · seed 보유 집합 · auth 404 → FAILURE 행). 이 호스트에 Docker 데몬 없음.
- ⚪ 라이브(데모 · 실제 리셋 → 실제 재등록) 미실행.
- ⚪ **콘솔 리셋 화면 없음** — 공개 API 만 있다. 계정 상세의 «2단계 인증 리셋» 버튼은 콘솔 계약(`console-integration-contract.md`)에 자리가 없어 이 슬라이스에서 만들지 않았다(소유자 · 후속 판단).
- ⚪ **리셋 알림 메일 없음** — 계약(S1 «등록 · 리셋 이벤트 — 소비자 없음»)대로. 본인이 모르는 리셋을 알리는 메일(등록 알림 메일과 대칭)은 보안상 권할 만하나 계약 밖이라 넣지 않았다 — 소유자 판단.

### CI 1차 — BE-377 재발 (S6 와 무관한 기존 시험)

- `a86559f86` 의 iam A: 195 중 1 실패 — `TokenExchangeIntegrationTest` «BE-377 … local-login → 401». 이번엔 500 이 아니라 **응답은 맞는 401 인데 5분 타임아웃**(`12:14:59` 직전 시험 통과 → `12:22:04` 응답). S5 에서 둔 `@DirtiesContext(AFTER_CLASS)` 완화는 **원인이 아니었다**(증상이 살아남음).
- 가설: Gradle 테스트 JVM 기본 힙 512 MiB(어디서도 `maxHeapSize` 미설정 — `gradle.properties` 의 `-Xmx2048m` 은 데몬 것) × 이 요청의 Argon2id 더미 검증 `m=65536`(64 MiB). 천장 근처에서 Error(→500, 스택 없음) 또는 GC 스래싱(→타임아웃) — 두 증상을 하나로 설명한다.
- 조치: `projects/iam-platform/build.gradle` `integrationTest` 에 `maxHeapSize = '1536m'` · 진단을 **항상** 찍게(요청 전 힙 · 응답 시간). 🔴 초록 한 번은 판정이 아니다 — 진단 줄의 «before login heap used/max» 가 천장 근처였는지로 가설을 판정한다(다음 런 로그).
- 판정(`e6d04a528` iam A, 초록): `before login heap used=725MiB total=957MiB max=1536MiB` · `login answered 401 in 1269ms`. 요청 직전 사용량이 **옛 천장 512 MiB 를 넘었다** — 가설을 강하게 지지한다. 🔴 단 `used` 는 수거 전 쓰레기를 포함하므로 «살아 있는 집합 > 512» 의 증명은 아니다. 재발하면 이 진단 줄이 다시 판정한다.

# review 이동 기록 (2026-10-09 UTC)

- 슬라이스 전부 머지: S1 #4257 · S2a #4258 · S2b #4260 · S2c #4261 · S3 #4262 · S4 #4263 · S5 #4266(`b7c389421`) · S6 #4268(`7f401f764`). 각 PR 머지 시점 CI 실패 0(S6 은 두 번째 헤드 `e6d04a528` 에서 26 pass · 0 fail).
- AC: AC-0 · AC-1 · AC-2 · AC-4 ✅(AC-4 는 «시험» 칸). ⚪ **AC-3**(소비자 로그인은 TOTP 미등록이면 지금과 같다) — 라이브 창에서 판정. ⚪ S5 · S6 라이브(콘솔 토글 · 실제 리셋 → 재등록).
- ⏳ `done/` 게이트 = 다음 데모 창(25차 재굽기 뒤) AC-3 라이브. `TASK-MONO-772` 는 이 티켓 `done/` 을 선행으로 둔다.
- 소유자 판단 대기(AC 아님): 콘솔 리셋 화면 · 리셋 알림 메일.

# 25차 데모 창 측정 기록 (2026-10-09 UTC, AMI `ami-03cc7efda4b0a7809`) — AC-3 닫음, `done/` 게이트 충족

- 소유자 측정: 소비자 스토어 로그인(demo@demo.com, 이어서 신규 계정 shopper1@demo.com)이 2단계(TOTP) 화면 없이 그대로 진행됐다 — S2b 가 전제한 «등록 안 한 계정은 흐름·허용 표면 불변» 이 라이브로 확인됐다.
- AC: AC-0 · AC-1 · AC-2 · AC-3 · AC-4 **전부 ✅**. S5(콘솔 토글 UI — 보안 설정 · demo-corp · 사용 안 함 · 켜기, 토글 자체는 안 함)·S6(실 리셋)는 AC 가 아니라 ⚪ 라이브 미측정으로 남는다(TOTP 등록에 인증된 이메일 + Mailpit UI 자격이 필요해 이 창에서 미실행) — 소유자 판단 대기, `done/` 게이트에는 영향 없음.
- `done/` 게이트 충족 → Status `done`. `TASK-MONO-772` 선행 해소.
