# Task ID

TASK-BE-605

# Status

done (2026-09-26 UTC — 4차원 검증 · 아래 § CORRECTION)

# Title

세션이 **만들어질 때** 테넌트가 갈린다 — SSO 로 소비자 client 교차 세션이 생기고, 소셜 로그인은 client 테넌트를 찍는다

# Owner

iam-platform

# Task Tags

- auth-service
- multi-tenant
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — authorize 시점 게이트는 SSO 동작 전체에 걸리는 설계 결정이다.

---

# Goal

`TASK-BE-604`(소유자 결정 D, 2026-09-26 UTC)는 **폼 로그인**의 교차 테넌트 폴백을 콘솔 client(`iam`)로 한정했다. 그러나 같은 티켓이 두 경로를 남겼다(`TASK-BE-604` § ⑩ 1·3·6):

1. 🔴 **SSO 우회** — 이미 IAM 브라우저 세션이 있으면 비밀번호 없이 다른 client 의 authorize 를 통과한다. 로컬 측정(BE-604 ⑧): 콘솔 로그인 뒤 같은 세션으로
   ecommerce client authorize → `tenant_id=fan-platform`, 역할 없음 토큰 발급. ⇒ 결정 D 가 막으려던 «소비자 client 교차 세션» 이 여전히 만들어진다(역할이 없어 쓸모없는 세션).
2. **소셜 스탬프** — `SocialLoginBrowserController:185` 는 세션 테넌트를 **client 테넌트**로 찍는다(폼은 계정 테넌트). 같은 사람이 폼으로는 쓸모없는 세션,
   소셜로는 CUSTOMER 로 입장한다 — 두 경로가 갈린다. `TASK-BE-602` 가 소셜 경로에 계정의 실제 테넌트(account-service `status-with-tenant`)를 들여왔으므로 출처는 있다.
3. web-store 역할 가드(`account_type_mismatch`)의 e2e 커버리지 — BE-604 가 `account-type-guard.spec.ts` 를 «IAM 이 로그인을 거부» 로 바꾸며 잃었다. 1 을 고치면 SSO 로 다시 구성하는 길도 사라진다.

# Scope

## 포함

- **AC-0 (🔴 소유자 결정)**: ① authorize 시점 게이트 — 세션의 principal 테넌트 ≠ client 테넌트이면 (a) 거부 · (b) 재인증 요구 · (c) 현행 유지(쓸모없는 세션 허용) — 단 콘솔(`iam`) client 는 BE-604 D 대로 허용.
  ② 소셜 스탬프를 계정 테넌트로 맞출지. ③ 역할 가드 e2e 를 어떤 경로로 복원할지(또는 단위 테스트로 대체).
- 결정대로 구현 + `multi-tenancy.md` 의 «어느 계정이 어느 client 로 로그인할 수 있나» 절(BE-604 신설)에 SSO · 소셜 행 추가.

## 제외

- 폼 로그인 폴백(BE-604 에서 결정·구현됨).

# Acceptance Criteria

- [x] **AC-0** — 위 ①②③ 소유자 결정. → 2026-09-26 UTC 결정: ① **(b) 재인증** · ② **(iii) 소셜 신원 조회를 client 테넌트로 한정 — 단 모집단 먼저 측정, 이 티켓에서 코드 불변** · ③ **β + α**. 전문·근거: 아래 § AC-0 소유자 결정.
- [x] **AC-1** — IT: 콘솔 로그인 세션으로 소비자 client authorize → 결정대로(거부/재인증) · 콘솔 client 는 계속 통과(대조군).
  → 신규 `SsoTenantGateIntegrationTest` (a) 스토어(ecommerce) 세션 → 팬 client authorize = `/login`(코드 아님) → 팬 자격 재로그인 → 코드(루프 없음) → `tenant_id=fan-platform` · `sub`=팬 계정 · `roles ⊇ FAN` · 같은 세션의 스토어 authorize 는 대조군으로 코드 · 스토어로 돌아가면 다시 `/login`(문서화한 UX 대가) / (b) fan-platform 세션 → 콘솔 client = 재로그인 없이 코드 · `tenant_id=fan-platform`(면제 대조군). 🔴 AC 문구의 «콘솔 로그인 세션 → 소비자 client» 모양은 (a) 의 일반형(세션 테넌트 ≠ client 테넌트)으로 덮고, 콘솔 로그인 세션 자체의 `'*'`/교차 모양은 단위(`AuthorizeSessionTenantGateTest` 11)로 덮었다. ⚪ **IT 는 로컬 Docker 부재로 건너뜀(7/7 skipped) — CI `Integration (iam …)` 판정.** 로컬 대체: 실제 SAS 체인 H2 슬라이스 3칸(`OAuth2AuthorizationServerSliceTest` `@Order(12..14)`, 비권위) 통과.
- [x] **AC-2** — 소셜: 결정대로 스탬프 · 폼과 같은 사람에게 같은 결과(IT 또는 단위).
  → **세션 테넌트 축은 닫힘**: 게이트 이후 폼(범위 조회 = client 테넌트 자격)과 소셜(client 테넌트 스탬프)이 **어느 소비자 client 에서든 같은 세션 테넌트**를 만든다 — `SocialLoginSasBrowserIntegrationTest#socialSession_openingClientOfAnotherTenant_reauthenticatesSocially`(스토어 소셜 세션 → 팬 client = `/login` → 소셜 재로그인 → 코드 · `tenant_id=fan-platform`) + 폼 쪽 AC-1 (a). ⚪ **계정 축(«어느 계정 행으로 들어가나»)은 이관** — 결정 ② (iii) 가 구현을 측정 뒤로 미뤘다. 🔵 **선택: 이 티켓에 미체크 AC 를 남기지 않고 «측정 뒤 별도 티켓»** — 보유처 = 루트 `TASK-MONO-672` **항목 18**(측정 + ② 구현 티켓 **기안 의무**). IT 는 그 계정을 일부러 단언하지 않는다(오늘의 불일치를 핀으로 얼리지 않기 위해).
- [x] **AC-3** — 역할 가드 커버리지 복원 또는 대체의 근거.
  → **β 복원 + α 단위** 둘 다. β: `account-type-guard.spec.ts` 에 «CUSTOMER 없는 **같은 테넌트**(ecommerce) 계정 → IAM 로그인 성공 → web-store `/login?error=account_type_mismatch`» 테스트 복원(시드 `iam-consumer-seed.sql` + `account-mock.nginx.conf` 의 그 계정 roles 1줄 = `["ECOMMERCE_OPERATOR"]`, 저장 역할이 시드를 이긴다 `TenantClaimTokenCustomizer.java:872-874`) · BE-604 의 «`'*'` → IAM 거부» 테스트는 여전히 참이라 **유지**(같은 파일 2 테스트). α: `web-store/src/__tests__/auth-callbacks.test.ts` 에 `signInCallback` 5 케이스. ⚪ **풀스택 Playwright 는 로컬 미실행**(Docker 없음 · nightly 전용 레인) → AC-5. ⚪ **vitest 도 로컬 기동 불가**(Node 24 × vitest 4.1.0 `#module-evaluator` — 이 호스트의 기지 한계, CI = Node 20) → 로컬 판정은 `tsc --noEmit` rc=0 · `next lint` 무경고뿐, 단언은 CI `Frontend unit tests` 의 그 파일 `(N tests)` 줄로 확인할 것.
- [x] **AC-4** — 🔴 **머지 후 첫 nightly `Frontend E2E full-stack (web-store …)` 확인** — BE-604(#4033, `06031b053`)가 바꾼 `account-type-guard.spec.ts` 가 nightly 에서 초록인가. BE-604 는 `done/` 으로 닫혔으므로 **이 확인은 이 티켓이 들고 있다.**
  → 🟢 **초록** (오케스트레이터 확인, 2026-09-26 UTC): nightly run **36237545006** (그리고 BE-604 머지 06:37Z 이후 앞선 4회) — `Frontend E2E full-stack (web-store, …)` success · `account-type-guard.spec.ts` ✓ **1 passed** · 전체 **12 passed**.
- [ ] **AC-5** — 🔴 **이 변경 머지 후 첫 nightly `Frontend E2E full-stack (web-store …)`** — `account-type-guard.spec.ts` 가 **2 passed**(역할 가드 복원 + IAM 거부)인가, 그리고 `assert-specs-ran` 이 OK 인가. nightly 전용 레인이라 PR CI 초록으로는 안 보인다. **`review → done` 전에 닫는다**(AC-4 와 같은 모양 — 이 티켓이 들고 있다). 빨강이면 먼저 볼 곳: (1) web-store 바운스 URL 호스트(`helpers/auth.ts` `loginAndExpectRoleGuardRejection` 은 `localhost` 를 요구) (2) account-mock 의 roles `location =` 이 실제로 맞았는가(auth-service `TenantClaimTokenCustomizer` 의 `injected roles=… source=stored` — **DEBUG 레벨**이라 기본 로그엔 없다; 없으면 nginx 접근 로그에서 그 경로의 200 을 찾아라).

# Related Specs

- `specs/features/multi-tenancy.md` § BE-604 신설 절 · `specs/features/oauth-social-login.md`
- `TASK-BE-604`(발견 · § ⑧ SSO 측정 · § ⑩) · `TASK-BE-602`(소셜의 계정 테넌트 출처) · ADR-MONO-044 D5(콘솔 교차 로그인의 근거)

# Related Contracts

- 없음(토큰 claim 의미가 바뀌면 `auth-events.md` · `auth-api.md` 먼저).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 셀프 온보딩 운영자(소비자 자격 · 콘솔) | 콘솔은 계속 통과(BE-604 D) |
| 테넌트별 자격을 여럿 가진 계정(`demo@demo.com`) | 각 client 에서 그 테넌트 자격으로 — 게이트가 이 계정을 막으면 안 된다 |

# Failure Scenarios

1. **authorize 게이트가 콘솔도 막는다** → 셀프 온보딩 운영자 전원 콘솔 불가(ADR-MONO-044 D5 위반).
2. **소셜만 고치고 폼과 비교하지 않는다** → 두 경로가 다른 방향으로 다시 갈린다.
3. **nightly 를 안 본다** → BE-604 의 e2e 변경이 검증 없이 남는다(AC-4).

---

# AC-0 소유자 결정 (2026-09-26 UTC)

**착수 판독 (main `eaa9eae0b`, file:line 재확인)** — SAS 체인 커스터마이즈는 `AuthorizationServerConfig.java:213-316` 뿐이고 authorize 엔드포인트에 테넌트
게이트가 없다. 세션 principal details `tenant_id`: 폼 = 자격 행 테넌트(`CredentialAuthenticationProvider.java:314-316` · `:389`), 소셜 = client 테넌트
(`SocialLoginBrowserController.java:185`). `TenantClaimTokenCustomizer.java:359-374` 가 세션 테넌트를 찍고 역할은 `listAccountRoles(sessionTenant)`,
시드는 principal 테넌트 = client 플랫폼일 때만(`:872-874` · `:891-903`) ⇒ 교차 테넌트 SSO = 역할 없는 토큰(BE-604 § ⑧). `demo@demo.com`
(ecommerce · fan-platform · iam 자격)은 스토어에 로그인한 뒤 팬 client 를 열면 `tenant_id=ecommerce` · 역할 없음 — **팬 자격을 한 번도 못 쓴다.**
SAS 1.4.1 은 `prompt=login` 을 **구현하지 않는다**(javap `OAuth2AuthorizationCodeRequestAuthenticationProvider`: `login` 은 `none` 과의 충돌 검사에만
등장, 미인증 + `none` → `login_required`).

| # | 결정 | 이 티켓에서 |
|---|---|---|
| ① | **(b) 재인증** — `/oauth2/authorize` 에서 세션 테넌트 ≠ client 테넌트이고 client 가 콘솔(`iam`)이 아니면 인증을 비우고 그 client 의 `/login` 으로. `'*'` 세션 → 소비자 client = 재인증. 콘솔 = 항상 통과(ADR-MONO-044 D5). 같은 테넌트 = 그대로. 루프 없음을 테스트로 증명. 다른 client 의 세션은 가능한 한 유지 · UX 영향 문서화 | 구현 |
| ② | **(iii) 소셜 신원 조회를 client 테넌트로 한정 — 모집단 먼저** · 이 티켓에서 조회 코드 불변. 스펙↔코드 불일치 기록 + `TASK-MONO-672` 항목 18 에 측정 | 결정·불일치 기록 + 항목 18. 구현 = **측정 뒤 별도 티켓**(항목 18 이 기안 의무 보유) |
| ③ | **β + α** — β: web-store e2e 에 같은 테넌트(ecommerce) 자격 1행 + account-mock 에 그 계정의 CUSTOMER 없는 roles → `account-type-guard.spec.ts` 가 실제 `account_type_mismatch` 를 풀스택으로. α: `signInCallback` vitest | 구현 |
| ④ | AC-4 는 오케스트레이터가 확인(nightly 36237545006 외 4회) | 체크 |

**② 의 스펙↔코드 불일치 (기록)** — `multi-tenancy.md:178`(`social_identities` unique `(tenant_id, provider, provider_user_id)`, «소셜 식별자도 테넌트별 분리»)
와 `V0007__add_tenant_id_to_auth_tables.sql:49` 는 **테넌트별**인데, 조회는 **전역**이다 — `OAuthLoginUseCase.java:250` · `SocialLoginSteps.java:47`
(`findByProviderAndProviderUserId`). 같은 제공자 사용자가 두 테넌트에 신원 행을 가지면 전역 조회는 결과가 둘이다(BE-602 review 행의 «동시 첫 로그인
경쟁 → `IncorrectResultSizeDataAccessException`» 과 같은 모양). 그리고 세션 테넌트는 신원 행이 아니라 시작 client 로 찍힌다 ⇒ 다른 테넌트에서 만든
신원으로 들어오면 **그 계정이 client 테넌트 세션**으로 들어간다.

---

# 구현 기록 (2026-09-26 UTC · worktree `mlab-605` · 분석=Opus 5.5 / 구현=Opus 5.5)

## ① 설계 — authorize 시점 세션 테넌트 게이트

| 변경 | 위치 | 요지 |
|---|---|---|
| 게이트 필터 (신규) | `AuthorizeSessionTenantGate.java:119-142` `requiresReauthentication` · `:102-114` `doFilterInternal` · `:97-99` `shouldNotFilter` | authorize 경로만. 인증된 비익명 principal + `client_id` + 등록 client + client `custom.tenant_id`(trim, `SavedRequestTenantResolver` 와 같은 출처)가 있을 때만 판정. 콘솔(`TenantContext.CONSOLE_TENANT_ID`) 면제(`:132`). 세션 테넌트 = `AuthorizationSessionTenant.of(principal, clientTenant)`(`:135`) ≠ client 테넌트 → **이 요청의** `SecurityContextHolder` 를 빈 컨텍스트로 **교체**(`:111`) |
| 세션 테넌트 규칙 공유 | `AuthorizationSessionTenant.java:40-66` | 기존 `of(OAuth2Authorization, …)` 를 새 `of(Authentication, …)` 로 위임 — refresh 비교 · 토큰 claim · 게이트가 **한 함수**를 쓴다(두 번째 사본 금지) |
| 배선 | `AuthorizationServerConfig.java:290-295` · `:376-403` `AuthorizeSessionTenantGateConfigurer` · 파라미터 `:180` | `addFilterBefore(gate, OAuth2AuthorizationEndpointFilter.class)` 를 **SAS 설정기 뒤에 추가한 커스텀 설정기의 `configure`** 에서 — 체인 빌더에서 직접 부르면 그 필터 클래스의 순서가 아직 등록 전이라 던진다(SAS 1.4.1 이 자기 `configure` 에서 `AbstractPreAuthenticatedProcessingFilter` 앞에 등록 — javap 확인). 엔드포인트 URI 는 `AuthorizationServerSettings` 에서 |

**재인증이 정확히 하는 일** — 컨텍스트를 비우면 SAS 엔드포인트가 미인증으로 보고 체인을 넘기고, `authenticated()` 가 거부 → `ExceptionTranslationFilter`
가 **이 authorize 를 저장**하고 진입점(`/login`, 가입 힌트면 `/signup`)으로. **HTTP 세션은 무효화하지 않았다**(판단): 무효화하면 재개에 필요한 저장 요청이
사라지고, 떠나온 client 의 SSO 까지 이득 없이 끊긴다. 로그인 화면을 떠나면 원래 세션 그대로, 로그인하면 새 principal 이 세션을 대체(세션 id 회전).
🔴 **교체이지 변경이 아니다** — 홀더의 컨텍스트 객체가 곧 세션에 저장된 객체라 `setAuthentication(null)` 로 비우면 원래 client 에서도 로그아웃된다
(bite C 로 확인, 코드 주석에 남김).

**루프 없음** — 재로그인 뒤 세션 테넌트 = client 테넌트: 폼은 저장 요청(= 게이트에 걸린 authorize)의 client 테넌트로 범위 조회(BE-604), 소셜은 그 client
테넌트를 스탬프. 없으면 로그인 실패(재시도 대상 없음). IT 두 곳이 «재로그인 → 재개된 authorize → 코드» 를 단언한다.

**UX 대가 (스펙에 기록)** — 테넌트가 다른 서비스로 옮기면 그 서비스 로그인이 **한 번** 뜬다. IAM 브라우저 세션은 principal 하나라 팬 → 스토어로 돌아가면
스토어 로그인이 다시 뜬다(IT (a) 마지막 단언). 이미 발급된 토큰(각 앱 세션)은 건드리지 않는다. `prompt=create`(스토어 가입 버튼)는 교차 세션에서
이제 `/signup` 으로 간다(전에는 기존 세션으로 코드가 나가 가입 화면이 안 떴다).

**게이트가 판정하지 않는 것** — client 없음 · 모르는 client · 테넌트 설정 없는 client · principal details 에 `tenant_id`+`tenant_type` 쌍이 없는 세션(토큰
claim 규칙상 client 테넌트로 찍히므로 불일치가 없다).

## ③ 역할 가드 e2e (β) + 단위 (α)

- 시드 `web-store/e2e/fixtures/iam-consumer-seed.sql` — `ecommerce` 자격 `e2e-no-customer-role@example.com`(`…e003`, 같은 해시).
- `account-mock.nginx.conf` — `location = /internal/tenants/ecommerce/accounts/01928c4a-7e9f-7c00-9a40-d2b1f5e8e003/roles` → `{"accountId","tenantId","roles":["ECOMMERCE_OPERATOR"]}`
  (응답 모양 = `AccountServiceClient.java:790-808` 이 `roles` 키로 읽는 그것). 정확 일치라 정규식·catch-all 보다 먼저, 다른 계정은 여전히 404 → 시드.
- `account-type-guard.spec.ts` 2 테스트 — (1) 복원: IAM 로그인 성공 → web-store 원점 `/login?error=account_type_mismatch`(`helpers/auth.ts`
  `loginAndExpectRoleGuardRejection`, 호스트 `localhost` 까지 본다 — IAM 의 `/login?error` 와 섞이지 않게) (2) 유지: `'*'` → IAM `/login?error`.
  **유지한 이유**: 여전히 참이고, 소비자 쪽 BE-604 결정 D 의 유일한 e2e 증거다. `assert-specs-ran.mjs` 는 파일 단위라 변경 불필요(주석만).
- α `web-store/src/__tests__/auth-callbacks.test.ts` — `signInCallback` 5 케이스(CUSTOMER 입장 · 다른 역할과 함께 입장 · 운영자 전용 거부 · 부재/빈 배열/profile 없음 거부 · 대소문자 정확 일치).

## 테스트 · bite

- `./gradlew :projects:iam-platform:apps:auth-service:test` → **rc=0 · 867 / 실패 0 / 오류 0 / 건너뜀 31** (JUnit XML 합산, 112 클래스). 이 태스크는 `@Tag("integration")` 을 돌리지 않는다.
  새/바뀐: `AuthorizeSessionTenantGateTest` **11** · `OAuth2AuthorizationServerSliceTest` **17**(+3, 실제 SAS 체인 · H2 · 비권위).
- `… :integrationTest --tests "*SsoTenantGateIntegrationTest" --tests "*AccountLockedSessionRevocationIntegrationTest" --tests "*SocialLoginSasBrowserIntegrationTest"` → rc=0, **7 / 7 건너뜀**(로컬 Docker 없음) — ⚪ **CI 판정**.
- **bite** (코드를 깨고 → 실패 확인 → 복원):
  A. 배선 제거(`addFilterBefore` 주석) → 슬라이스 `@Order(13)` 실패(단위는 통과 — 배선은 슬라이스만 잰다).
  B. 콘솔 면제 제거 → 단위 2(콘솔 · `'*'`→콘솔) 실패 · (배선 복원 상태에서) 슬라이스 `@Order(14)` 실패.
  C. 교체 대신 `getContext().setAuthentication(null)` → 단위 `reauthentication_leavesTheSessionContextIntact` 실패 · 슬라이스 `@Order(13)` 의 «세션 컨텍스트 여전히 인증됨» 실패(이 단언은 C 를 보고 추가).
- web-store: `pnpm install --frozen-lockfile --filter "web-store..."`(worktree 에 설치) · `npx tsc --noEmit` rc=0(`tsconfig.include` = `**/*.ts` → e2e · 단위 포함) · `npx next lint` 3 파일 무경고. ⚪ `vitest` 는 로컬 기동 불가(위 AC-3).

## 이 변경이 바꾼 기존 IT (판단)

- `AccountLockedSessionRevocationIntegrationTest#signIn` — `ecommerce` 계정을 **`demo-spa-client`(fan-platform)** 로 서명시키고 있었다 = 게이트가 막는 바로 그
  모양(교차 테넌트 세션 재사용). 그 계정은 이제 `ecommerce-web-store-client`(기밀 · Basic `ecommerce-dev` · V0012/V0024)로 들어간다. 단언(«같은 이메일의
  다른 테넌트 계정은 잠금에서 살아남는다»)은 불변.
- `SocialLoginSasBrowserIntegrationTest` — 테스트가 둘이 되어 static WireMock 저널을 `@BeforeEach` 에서 비운다(BE-604 CORRECTION 과 같은 함정 선제).
- 교차 테넌트 세션을 만드는 다른 IT 없음(판독: `.with(user(...))` 는 details 가 없어 판정 대상이 아니고, details 를 싣는 나머지는 같은 테넌트). e2e:
  콘솔 · federation 하네스는 콘솔 client 뿐(면제) · 데모 시드 `infra/demo/seed/lib.sh` `user_token` 은 호출마다 새 쿠키 자(jar)라 SSO 를 안 쓴다.

## 스펙

- `specs/features/multi-tenancy.md` § 로그인 가능한 계정과 client — **SSO 표**(콘솔/소비자 × 일치/불일치) · 세션 테넌트 정의 · 루프 없음 · UX 대가 ·
  `prompt` 와의 관계 · `identity-platform` § SSO Scope Rules 와의 정합 + **소셜 로그인** 절(현재 동작 · 스펙↔코드 불일치 · 결정 (iii) · 측정 뒤 별도 티켓).
  BE-604 의 «이 표가 막지 않는 것» 불릿은 취소선 + 포인터.
- `specs/features/oauth-social-login.md` § tenant 귀속 규칙 — SSO 게이트와의 관계 · 알려진 불일치/결정.
- `specs/services/auth-service/architecture.md` § infrastructure/ — 게이트 한 줄.
- 계약 불변 — 토큰 claim 의미는 바뀌지 않았다(교차 테넌트 세션에서 코드가 안 나갈 뿐).

## 이탈 · 후속

- AC-1 문구는 «콘솔 로그인 세션 → 소비자 client» 인데, IT 는 소비자 → 소비자(스토어 → 팬)로 짰다 — 결정 ① 의 판정 축은 «세션 테넌트 ≠ client 테넌트» 이고
  그 모양이 `demo@demo.com` 사용자 경로다. 콘솔 세션(`iam` · `'*'` · D5 소비자 테넌트)에서 소비자 client 로 가는 칸은 단위가 덮는다.
- **후속 (파일 만들지 않음)**: ② 구현은 `TASK-MONO-672` 항목 18 이 기안한다. 그 외 발견 없음.

## CORRECTION (2026-09-26 UTC) — AC-5 확인 → close

- impl PR **#4041** · 스쿼시 `ee8ee25ba` · 머지 전 실패 체크 0 · CI `Integration (iam B)`: SSO 게이트 IT **2 PASSED** · 소셜 IT **2 PASSED** · `AccountLockedSessionRevocationIntegrationTest` **3 PASSED**(표시 이름으로 셈) · `Frontend unit tests` 의 `auth-callbacks.test.ts` 통과.
- **AC-5 🟢** — 머지 뒤 첫 nightly run **36241578298**(`ee8ee25ba`, success) `Frontend E2E full-stack (web-store …)`: `account-type-guard.spec.ts` **2 passed / 0 failed**(CUSTOMER 없는 ecommerce 계정 → web-store 가 거절 · `'*'` principal → IAM 거절), 전체 13 passed.
- ② 소셜 신원 조회 범위 구현은 `TASK-MONO-672` 항목 18(모집단 측정 → 구현 티켓 기안 의무)이 들고 있다.
