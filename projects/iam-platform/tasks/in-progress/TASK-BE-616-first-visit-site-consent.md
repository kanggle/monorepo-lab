# Task ID

TASK-BE-616

# Status

in-progress

# Title

전역 소비자 계정 4단계 — 사이트 **첫 방문 동의** 화면 + 사이트 멤버십·역할 · 로그인 화면 브랜드 문구 (`ADR-MONO-078` A · D3)

# Owner

iam-platform

# Task Tags

- auth-service
- ui
- oidc

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 (화면 + 멤버십 기록 — 흐름은 `TASK-BE-615` 가 만들었다)

---

# Dependency Markers

- **선행**: `TASK-BE-614` · `TASK-BE-615`

# Goal

풀 계정이 처음 가는 소비자 사이트에서 **이용 동의 한 화면**을 보고, 동의하면 그 사이트 멤버십과 사이트 역할(팬=FAN, 스토어=CUSTOMER)이 생긴 뒤 토큰이 발급되게 한다. 가입 폼도 비밀번호도 없다. 소유자가 고른 «가입 안내»(①)를 이 화면이 대체한다.

# Scope

## In Scope

- authorize 흐름 안의 동의 화면(풀 계정 + 그 사이트 멤버십 없음일 때)
- 동의 기록(사이트 테넌트 · 시각) + 사이트 역할 시드(`RoleSeedPolicy` 와 정합)
- 로그인·동의 화면의 브랜드 문구

## Out of Scope

- 약관 본문(저장소에 없다 — 데모 문구)

# 착수 시 결정 (2026-10-01 UTC)

- **AC-4 소유자 결정 — 계정 이름 하나로: «IAM 로그인»** (소유자 원문 «A. 계정 이름 하나로 통일 / IAM 로그인»). 🔵 착수 시 확인: 팬은 **이미** 2026-09-29 에 «GAP» → «IAM» 으로 바뀌어 있었다(`ADR-007` § 값 변경 2026-09-29) — 바꿀 것은 **스토어** 하나다(«Global Account» → «IAM»). 기록: `ADR-007` § 값 변경 (2026-10-01). 색·로고·설명 문구는 사이트별 유지, 이름만 하나.
- 🔴 **스토어 이름 변경과 플래그 켜기(AC-6)는 같은 PR** — «같은 이름인데 다른 계정» 기간을 만들지 않는다.
- **선행 확인 — security-service 자동 잠금과 `consumer-pool`**(`TASK-BE-615` 검토 절의 후속, 착수 전 확인): 자동 잠금은 `X-Tenant-Id` 를 **보내지 않는다**(`TASK-MONO-735`) → account-service 가 계정 id 로 찾으므로 풀 계정도 잠긴다 ✅. 탐지 규칙의 `(tenantId, accountId)` 는 풀 계정이 어느 사이트에서든 `consumer-pool` 로 찍혀 **사람 단위로** 일관되게 센다 ✅. ⚪ 공백: 콘솔에서 `ecommerce` 로 전환해 보안 이벤트를 조회하면 풀 계정 이벤트(`consumer-pool`)는 보이지 않는다 — 이 티켓 밖, 후속으로 기록.
# Acceptance Criteria

- [ ] **AC-1** — 풀 계정, 스토어 멤버십 없음 → 스토어 authorize 에서 동의 화면 → 동의 → `CUSTOMER` 역할 토큰. 거절하면 토큰 없이 스토어로 돌아간다(오류 문구).
- [ ] **AC-2** — 두 번째 방문부터는 동의 화면이 나오지 않는다.
- [ ] **AC-3** — 콘솔(`iam`)에는 동의 화면이 없다 — 운영자 계정은 자동으로 만들어지지 않는다(D1).
- [x] **AC-4 (소유자 결정 수령 — 위 «착수 시 결정»)** — 🔴 **소유자에게 묻는 AC**: iam `ADR-007` 은 팬=「GAP」 · 스토어=「Global Account」 로 로그인 화면 이름을 **일부러 달리** 했고, 근거가 «서로 다른 계정» 이었다(`projects/iam-platform/docs/adr/ADR-007-per-client-branding-of-the-shared-login-page.md`). `ADR-MONO-078` A 아래서는 같은 계정이다. 이름을 하나로 맞출지, 사이트 이름을 유지하되 «같은 계정» 을 알릴지 — 소유자 결정을 받아 기록한다(받기 전에는 007 그대로).
- [ ] **AC-5** — 동의 화면의 접근성: 키보드로 동의·거절, 400px 폭.
- [ ] **AC-7** — 스토어 로그인·가입 화면과 스토어 앱 진입 버튼이 «IAM 로그인» 이다(`ADR-007` § 값 변경 2026-10-01) — 색·로고·설명은 사이트별. 화면 문구를 단언하는 기존 시험(e2e 포함)을 같이 고친다.
- [ ] **AC-6** — 🔴 **이 티켓이 `iam.consumer-pool.enabled` 를 켠다**(`TASK-BE-615` 착수 시 정정 2026-10-01 — 동의 화면이 생겨야 풀 가입자가 다른 사이트에 처음 들어갈 길이 생긴다). 켜기 전 확인: 614(풀 가입) · 615(풀 로그인·토큰) · 616(동의)이 전부 main 에 있다. 켠 뒤 «스토어 풀 가입 → 팬 첫 방문 → 동의 → 팬 토큰» 을 한 시험으로 잇는다.

# Related Specs

- `docs/adr/ADR-MONO-078-one-consumer-login-across-fan-and-store.md` D3
- `projects/iam-platform/docs/adr/ADR-007-per-client-branding-of-the-shared-login-page.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/events/account-events.md`(`TASK-MONO-742` AC-3 이 동의 시점 이벤트를 골랐다면 여기서 발행)

# Edge Cases

- 동의 화면에서 브라우저 뒤로가기 — authorize 요청이 남아 있어야 한다.
- 이커머스 user-service 프로필이 이벤트/pull-through 중 어느 경로로 생기는지가 `TASK-MONO-742` 결정과 맞는가.

# Failure Scenarios

1. 동의 없이 역할을 먼저 시드해 «동의 안 한 사이트» 토큰이 발급된다.
2. 브랜드 문구를 소유자 결정 없이 바꾼다(AC-4).

---

# 구현 기록 (2026-10-01 UTC)

## 커밋

1. `feat(iam)` — 동의 화면 · 동의 쓰기 · 계약(auth-to-account.md · multi-tenancy.md § 4 · account-service architecture.md).
2. `feat(iam)` — `iam.consumer-pool.enabled` 켜기 + 스토어 브랜딩 `V0041` + 플래그 OFF 전제 시험의 기대값 갱신 + ADR-007 적용 대상 표.
3. `feat(ecommerce)` — 스토어 진입·가입 문구 «IAM 로그인» + 그 문구를 단언하는 단위·e2e 시험.
4. `chore(iam)` — 이 기록.

## 만든 것

**auth-service**
- `AuthorizeSessionTenantGate` — 결정을 `PASS / REAUTHENTICATE / CONSENT` 셋으로. 풀 principal · 소비자 사이트 · 멤버십 **행 없음**(`membershipStatus == null`) → `CONSENT`:
  `GET` 이면 authorize 요청을 `PendingSiteConsentStore` 에 보관하고 `302 /consent`(체인 중단 — 코드 없음). `prompt=none` 이면 화면 없이
  등록 redirect URI 로 `error=consent_required`(+`state`). 그 밖(`POST` authorize)은 615 그대로 통과(발급자가 `invalid_grant`).
  `LEFT` 멤버십 → 동의 화면 없이 통과(발급자가 거절). 콘솔(`iam`)·B2B 는 이 분기에 오지 않는다(615 규칙 그대로).
- `PendingSiteConsentStore`(신설, `infrastructure/security`) — **전용 세션 속성**(`IAM_PENDING_SITE_CONSENT`)의 `HttpSessionRequestCache`. 로그인 이어가기
  (`SPRING_SECURITY_SAVED_REQUEST`)와 섞이지 않는다. 읽어도 지우지 않는다(새로고침·뒤로가기 — Edge Case 1). 오류 리다이렉트 생성기
  `errorRedirect` — redirect URI 가 **등록값과 정확히 같을 때만**(없으면 등록값이 하나일 때만), 아니면 빈 값(리다이렉트 안 함).
- `SiteConsentPageController`(신설) + `templates/consent.html` — 로그인 화면과 같은 카드·스타일·브랜드 헤더(`fragments/auth-page`), 색·설명·로고는
  **보관된 client 의 등록 브랜딩**(ADR-007 불변 조건 1). 한 폼에 네이티브 `type=submit` 버튼 둘(«동의하고 계속» / «동의하지 않음») — 키보드만으로
  Tab·Enter/Space. 카드 360px · `max-width: calc(100vw - 32px)`(400px 화면에 맞음). 풀 세션이 아니거나 보관 요청이 없으면 «만료» 화면(400).
- `WebLoginSecurityConfig` — `/consent` GET·POST 를 `@Order(0)` 체인에(CSRF 켬, permitAll — 판정은 컨트롤러).
- `AccountServicePort#consentToConsumerSite` + `AccountServiceClient` 구현(PUT, 읽기와 같은 본문 파서 `toMembershipResult` 공유).

**account-service**
- `PUT /internal/tenants/{tenantId}/consumer-members/{accountId}` — `ConsumerSiteMembershipController#consent` → `ConsentToConsumerSiteUseCase`.
- `ConsumerSiteMembership.joinOnConsent(account, site, consentedAt)`.
- **플래그**: `application.yml` `${IAM_CONSUMER_POOL_ENABLED:true}` · `ConsumerPoolFlagProperties` `@Value(...:true)` · `ConsumerPoolFlag` 문서. env `false` 는 kill switch.

**auth-service 마이그레이션** `V0041__rename_store_login_branding_to_iam.sql`(새 파일, V0040 무변경) — `ecommerce-web-store-client`:
serviceName `IAM` · title `IAM 로그인` · description `쇼핑을 계속하려면 IAM 계정으로 로그인하세요.` · 색 `#1a1a2e` 유지. 가드: 서비스 이름이 아직
`Global Account` 인 행만(손으로 바꾼 행 · 재실행은 무변경).

**web-store** — `LoginForm`(설명 문구 · 버튼 «IAM 로그인»), `/signup`(«회원가입은 IAM 에서 진행합니다» · «IAM 으로 이동»).

## 계약이 남긴 선택 — 내가 정한 것

| # | 선택 | 정한 값 | 이유 |
|---|---|---|---|
| D-1 | 거절 결과 | client 의 **등록** redirect URI 로 `error=access_denied` + `error_description` + `state`. 쓰기 0 · IAM 세션 유지 | AC-1 «토큰 없이 스토어로 돌아간다(오류 문구)» — 스토어 `LoginForm` 은 이미 `access_denied` 문구가 있다. 루프 없음: 거절은 그 요청을 끝낸다(다시 누르면 다시 묻는다 — 사람의 선택). redirect URI 를 신뢰할 수 없으면 리다이렉트하지 않고 IAM 화면에 «동의하지 않았습니다» |
| D-2 | 엔드포인트 모양 | 615 의 읽기와 **같은 자원의 PUT**, 본문 없음, **항상 200**(읽기와 같은 문서) | 사이트가 범위의 첫 인자(615 와 같은 결). «쓰지 않았다» 를 200 본문(`membershipStatus` ≠ ACTIVE)으로 — 새 에러 코드 0, 404 = «엔드포인트 없음» 만(fail-closed). PUT 이 멱등이라 기존 재시도 파이프라인을 그대로 탄다 |
| D-3 | 멱등 | 멤버십 **행이 없을 때만** 쓰기 + 이벤트. ACTIVE/LEFT 행이 있으면 무변경 · 이벤트 0. PK 경합은 진 쪽 tx 통째 롤백 → 컨트롤러가 읽기로 답 | 계약 § 6 «사이트마다 한 번». `INSERT IGNORE`/`ON DUPLICATE` 는 FK 위반도 삼키거나(IGNORE) Connector/J 기본 found-rows 로 «1 행» 을 돌려준다 — 그래서 롤백 + 재읽기 |
| D-4 | `LEFT` | 동의 화면을 띄우지 않는다 · 동의 쓰기도 다시 열지 않는다(615 그대로 토큰 거절) | LEFT 의 작성자가 아직 없다. 재가입 규칙은 그 작성자의 결정 — 지금 열면 그 결정을 대신 내리는 것 |
| D-5 | 정지된 소비자 사이트 | 쓰기 0 → 화면은 client 로 `access_denied` | `ActiveTenantGuard`(가입)와 같은 결: 정지 테넌트에는 새로 들어가지 않는다 |
| D-6 | `prompt=none` | `consent_required` (OIDC Core § 3.1.2.1). redirect URI 를 신뢰할 수 없으면 615 그대로 통과 | 화면을 띄울 수 없는 요청 |
| D-7 | `POST` authorize | 동의 화면 없이 615 그대로(발급자 거절) | 보관한 요청은 `GET` 리다이렉트로만 재개된다 — POST 본문을 되살릴 수 없다 |
| D-8 | 보관 위치 | 전용 세션 속성, 읽기는 비파괴 · 답할 때 제거 | 로그인 이어가기를 오염시키지 않는다. 뒤로가기·새로고침에도 같은 요청(Edge Case 1) |
| D-9 | 동의 화면의 사이트 구분 | 로그인 화면과 같은 브랜딩 값(설명 문구 · 색 · 로고). 새 브랜딩 키를 만들지 않았다 | ADR-007 D2 의 키 집합 안에서. ADR-007 2026-10-01 절: «어느 사이트에 들어가는가 는 설명 문구와 색으로 보인다» |
| D-10 | 스토어 설명 문구 | «쇼핑을 계속하려면 IAM 계정으로 로그인하세요.» — IAM 로그인 화면(V0041)과 스토어 앱 진입 화면 같은 문구 | ADR-007 2026-10-01 표의 «구현이 정한다» 칸. 앱과 IAM 화면이 같은 말을 한다 |
| D-11 | 이벤트 `locale` | 그 계정의 프로필 locale(없으면 null) | 가입 이벤트와 같은 출처 |

## 플래그 OFF 를 전제로 하던 기대값 — 바꾼 것 하나하나 (AC-6)

플래그 기본값이 바뀌면서 **헤더 없는(= fan-platform) · 스토어 가입은 이제 풀 계정 + 그 사이트 멤버십**이다. 그 사실을 단언하도록 바꿨다.

| 시험 | 전 | 후 | 이유 |
|---|---|---|---|
| account `AccountSignupIntegrationTest#signup_createsActiveAccount` | `findByEmail(FAN_PLATFORM)` 있음 | `CONSUMER_POOL` 에 있음 · `FAN_PLATFORM` 없음 · fan-platform ACTIVE 멤버십 | 새 동작 그 자체(계약 § 2) |
| 〃 `#signup_thenLock_historyRecorded` | `findById(FAN_PLATFORM, id)` | `findById(CONSUMER_POOL, id)` | 헤더 없는 `/lock` 은 id 로 찾으므로(MONO-735) 풀 계정도 잠긴다 — 계정 행이 풀에 있다 |
| 〃 `#lockDeletedAccount_returns409` | 헤더 없는 가입 → `DELETE /api/accounts/me` | **erp 사이트 계정**으로(가입·삭제에 `X-Tenant-Id: erp`) | 이 시험의 주제는 «테넌트별 계정 수명주기». 풀 계정은 `/me` 가 사이트 헤더로 찾아 **404** — 그 공백은 아래 «후속» 으로 기록하고 이 시험에서 단언하지 않았다 |
| 〃 `SignupRollbackIntegrationTest` | `FAN_PLATFORM` 에 없음 | `CONSUMER_POOL` **과** `FAN_PLATFORM` 둘 다 없음 | 그대로 두면 **공허하게 초록**(풀에 생겼다 롤백돼도, 안 롤백돼도 fan-platform 엔 없다) |
| 〃 `SignupAuthServiceDelayIntegrationTest` ×2 | `FAN_PLATFORM` | `CONSUMER_POOL` | 타임아웃 동작은 같고 행의 자리만 바뀌었다 |
| 〃 `TenantProvisioningIntegrationTest#crossTenantUnique…` | fan-platform 에 있음 | 풀에 있음 · fan-platform 없음 · wms 있음 | «같은 이메일이 두 테넌트에» 라는 요점은 풀 vs wms 로 그대로 |
| 〃 `AccountRoleProvisioningIntegrationTest#replaceAll_crossTenantAccountId_returns404` | — | **단언 무변경**, 주석만 | 풀 계정도 wms 경로로는 404 — 교차 테넌트 요점 그대로 |
| auth `AuthorizeSessionTenantGatePoolTest` `…nonMemberConsumerSite_passes_noLoop` | 통과(발급자 거절) | `…redirectsToConsent`: 체인 중단 · 302 `/consent` · 요청 보관 | 616 의 동의 화면이 615 의 «코드 → invalid_grant» 자리에 들어왔다 |
| auth `ConsumerPoolSsoIntegrationTest` `storeOnlyPoolAccount_onFan_noTokenAndNoLoop` | 코드 → 토큰 400 | `…consentScreen_noCode_noLoop`: `/consent`(코드 없음 · `/login` 아님, 두 번째도 같다) | 같은 이유 |
| auth `LoginPageBrandingSeedIntegrationTest#storeBranding` | Global Account | IAM · 스토어 설명 · `#1a1a2e` | V0041 (ADR-007 값 변경) |
| web-store 단위 `login-form` · `signup-page`, e2e `helpers/auth` · `smoke` · `auth-redirect` · `account-type-guard` · `rp-initiated-logout` | «Global Account로 로그인» · «Global Account 로 이동» | «IAM 로그인» · «IAM 으로 이동» | AC-7 |

단위 시험(account `SignupUseCaseTest` · `AccountSearchQueryServiceTest` 등)은 `ConsumerAccountPool` 을 mock 해 플래그 기본값과 무관 — 바뀐 것 없다.
admin-service 는 플래그를 읽지 않는다 — 바뀐 것 없다(`check` rc=0).

## 추가한 시험

- auth 단위/슬라이스: `SiteConsentPageSliceTest` 10(실제 템플릿 렌더 · 실제 `PendingSiteConsentStore`) · `AccountServiceClientConsentTest` 4 ·
  `AuthorizeSessionTenantGatePoolTest` +5(동의 302 · `prompt=none` · 신뢰 못 할 redirect · POST · LEFT; 합계 11).
- account 단위/슬라이스: `ConsentToConsumerSiteUseCaseTest` 7 · `ConsumerSiteMembershipControllerSliceTest` +3(합계 6).
- 통합(⚪ 로컬 미실행 — Docker 없음, **CI 첫 실측**):
  - account `ConsumerSiteConsentIntegrationTest`(**`AbstractConsumerPoolIntegrationTest` 하위 클래스** — 새 컨텍스트 없음): 스토어 풀 가입 →
    팬 멤버십 없음 → PUT → 팬 ACTIVE(`consented_at`) · `account.created` = `[ecommerce, fan-platform]` · `consumer_site_roles` 0 · 재동의 무변화 · erp 동의는 쓰기 0.
  - auth `ConsumerPoolSsoIntegrationTest`(**기존 클래스에 추가** — `@DynamicPropertySource`/`@MockitoBean` 새로 선언 안 함):
    `storeSignup_fanFirstVisit_consent_fanToken`(AC-6 사슬: 스토어 `/signup` → 로그인 → 스토어 토큰 CUSTOMER → 팬 authorize `/consent` → 동의 →
    재개된 authorize 코드 → 팬 토큰 `sub` 동일 · `tenant_id=fan-platform` · `FAN` 있음 · `CUSTOMER` 없음 → 다음 팬 방문은 동의 없이 코드) ·
    `decline_returnsAccessDenied_noWrite_sessionKept` · `console_neverShowsConsent`.
  - 🔵 사슬은 **HTTP 경계에서 둘로 나뉜다**: auth 쪽은 account-service 를 WireMock 으로(가입 201 · 멤버십은 PUT 이 오기 전 null / 뒤 ACTIVE 인 시나리오),
    풀 자격은 account-service 가 `POST /internal/auth/credentials` 로 쓸 것을 시험이 직접 쓴다. account 쪽 반은 위 account IT. 두 서비스를 한 프로세스로
    띄우는 시험은 없다.

## 게이트 (각각 단독 실행 · `cmd > log 2>&1; echo rc=$?`)

| 게이트 | rc | 비고 |
|---|---|---|
| `:projects:iam-platform:apps:auth-service:check` | 0 | 990 tests, 실패 0 (1차 rc=1 — 내 시험 단언이 템플릿 주석의 `data-busy-label` 글자를 물었다 → `<form>` 태그만 보도록 고침) |
| `:projects:iam-platform:apps:account-service:check` | 0 | |
| `:projects:iam-platform:apps:admin-service:check` | 0 | 코드 변경 없음 |
| `@Tag("integration")` 새/바뀐 IT | ⚪ 로컬 미실행 | Docker 없음 — 컴파일만(`check` 의 compileTestJava). **CI 가 첫 실측** |
| web-store `pnpm --filter web-store lint` | 0 | No ESLint warnings or errors |
| web-store `pnpm --filter web-store test` (vitest 4) | 1 — ⚪ **판정 아님** | 기동 오류 `ERR_PACKAGE_IMPORT_NOT_DEFINED #module-evaluator`(Node 24 × vitest 4.1, 시험 0개 실행). 이 호스트의 알려진 한계 — CI(Node 20) `frontend-unit-tests` 가 권위 |
| web-store `npx tsc --noEmit` (e2e 포함) | 0 | 로컬에서 낼 수 있는 대체 판정 |
| `git add` 후 가드 | 아래 | |

## bite (커밋 위에서 · 되돌린 뒤 `git diff` 비어 있음 · 재실행 초록)

| 끈 것 | 돌린 시험 | 결과 |
|---|---|---|
| (A) account `ConsentToConsumerSiteUseCase` 쓰기 분기 전체(`if (false && …)`) | `ConsentToConsumerSiteUseCaseTest` + 컨트롤러 슬라이스 | **13 중 3 실패** — «첫 동의» 칸은 단언으로, «이미 ACTIVE»·«LEFT» 칸은 STRICT_STUBS 의 쓰지 않은 스텁(`find`)으로. 🔴 뒤 둘은 의미 단언이 아니라 엄격성이 문 것이다 |
| (A′) 멤버십 `insert` 한 줄만 제거 | `ConsentToConsumerSiteUseCaseTest` | **7 중 1 실패** — 정확히 «첫 동의 → 멤버십 ACTIVE» 칸 |
| (B) auth 게이트 `CONSENT` → `PASS`(동의 단계 건너뜀) | `AuthorizeSessionTenantGatePoolTest` · `AuthorizeSessionTenantGateTest` · `SiteConsentPageSliceTest` | **36 중 2 실패** — 동의 302 칸 · `prompt=none` 칸. 나머지는 이 분기와 무관한 칸 |
| (C) 동의 화면이 account-service 쓰기 없이 ACTIVE 로 간주 | `SiteConsentPageSliceTest` | **10 중 3 실패** — 동의(쓰기 호출 검증) · 장애 503 · ACTIVE 아님 칸 |

## AC

- ⚪ **AC-1** — 단위/슬라이스 ✅: 동의 → 쓰기 → 보관 authorize 재개(`SiteConsentPageSliceTest`), 거절 → `access_denied`+`state` · 쓰기 0. 토큰의 역할
  (`CUSTOMER`/`FAN`)은 615 발급 경로 그대로(멤버십 ACTIVE → 시드). 브라우저 경로 끝까지 = `ConsumerPoolSsoIntegrationTest#storeSignup_fanFirstVisit_consent_fanToken`
  (팬 방향 — AC-1 문장은 스토어 방향이지만 같은 코드 경로, 사이트만 다르다) · `#decline_…` — **로컬 미실행, CI 첫 실측**. 🔴 스토어가 실제로 보이는
  문구는 **재지 않았다**: IAM 은 `error=access_denied` 를 스토어 콜백으로 돌려주지만, NextAuth(v5)가 그것을 `/login?error=` 의 어떤 코드로 바꾸는지
  (`access_denied` 그대로면 «로그인이 거부되었습니다…», `OAuthCallbackError` 등이면 `LoginForm` 의 일반 오류 문구)는 실행해 보지 않았다 — 어느 쪽이든
  `role="alert"` 문구는 나오지만, `AccessDenied` 로 바뀌면 «operator 계정으로는…» 이라는 **틀린 안내**가 된다(`normalizeErrorCode`). 스토어 e2e 로 확인할 것(후속).
- ⚪ **AC-2** — 같은 IT 의 마지막 단계(동의 뒤 팬 authorize = 코드, PUT 1회 그대로) — CI 첫 실측. 단위로는 게이트의 ACTIVE → PASS(615 칸 그대로).
- ⚪ **AC-3** — 단위 ✅: 게이트 콘솔 칸(615) 무변경 — 풀 principal 은 `iam` 으로 사상되지 않아 동의 분기에 오지 않는다; 동의 화면도 풀 principal +
  `poolPrincipalMapsTo(site)` 를 다시 확인. IT `console_neverShowsConsent` — CI 첫 실측. 운영자 계정은 어디서도 만들지 않는다(D1).
- ✅ **AC-4** — 착수 시 결정(소유자) 그대로 적용.
- ✅ **AC-5** — 네이티브 `type=submit` 버튼 둘(Tab · Enter/Space), «동의하고 계속» `autofocus`, 오류 `role="alert"`, 카드 `max-width: calc(100vw - 32px)` —
  `SiteConsentPageSliceTest#page_brandedAndKeyboardOperable` 이 렌더된 바이트로. 🔴 공유 enhance 스크립트의 `data-busy-label` 은 **일부러 안 붙였다**
  (첫 submit 버튼을 disable 하면 «동의» 값이 폼 데이터에서 빠진다). ⚪ 실제 브라우저 400px 렌더·스크린리더는 재지 않았다.
- ⚪ **AC-6** — 플래그 켬(파일 diff). 켜기 전 확인: 614(`98e6c6dbe`) · 615(`3d732a21b`) main 에 있음, 616 = 이 변경. 사슬 시험은 위 두 IT(⚪ CI 첫 실측).
  플래그 OFF 전제 기대값 변경은 위 표.
- ✅ **AC-7** — `V0041`(IAM 로그인 화면) + 스토어 `LoginForm` · `/signup` 문구 + 단위·e2e 단언 갱신. ⚪ web-store vitest 는 로컬 기동 불가(위) —
  CI `frontend-unit-tests` 가 판정. e2e 는 실행하지 않았다(풀스택 — nightly).

## 후속 (이 티켓 밖 — 기록만)

- **콘솔 보안 이벤트 조회**: 콘솔에서 사이트 테넌트(`ecommerce` 등)로 전환해 보안 이벤트를 보면 풀 계정의 이벤트는 보이지 않는다 — 풀 principal 의
  로그인 이벤트는 `consumer-pool` 로 기록된다(615 D-8). 착수 시 결정의 ⚪ 공백 그대로.
- 🔴 **풀 계정의 자기 서비스 표면** — `/api/accounts/me`(GET · PATCH profile) · `/api/accounts/me` DELETE · 상태 조회 · 이메일 인증 · 내부 `gdpr-delete`/`export`
  는 계정을 **`X-Tenant-Id`(= 토큰의 사이트) 테넌트로** 찾는다 → 풀 계정은 404. 플래그를 켠 지금부터 **새로 가입하는 모든 소비자**에 해당한다.
  저장소의 소비자 앱(팬·스토어)은 이 IAM 경로를 부르지 않는다(grep) — 그러나 콘솔의 GDPR 삭제/내보내기가 사이트 테넌트로 부르면 404 다.
  614 가 «쓰기 경로는 넓히지 않았다» 로 미룬 것과 같은 묶음. `AccountSignupIntegrationTest#lockDeletedAccount_returns409` 를 erp 계정으로 옮긴 이유.
- 🔴 **셀러 프로비저닝의 역방향 공존** — product-service 의 셀러 온보딩은 `POST /internal/tenants/ecommerce/accounts` 로 사이트 계정을 만든다. 그 이메일에
  **이미 풀 계정이 있으면** 막는 것이 없다(§ 2 의 거절은 «사이트 계정이 먼저 있고 풀 가입이 뒤» 방향만 덮는다) → 같은 이메일에 풀 + 사이트 계정 공존.
  플래그가 켜져 이제 도달 가능. `TASK-MONO-745`(셀러를 풀로) 의 범위에서 다룰 것.
- **같은 세션의 두 탭**: 보관 슬롯이 하나라, 두 탭이 차례로 동의 화면에 오면 먼저 연 탭의 «동의» 가 **나중 탭의** authorize 를 재개한다(그 탭의 client 는
  `state` 불일치로 거절 — 토큰이 엉뚱한 곳으로 가지는 않는다). 필요해지면 요청별 키로.
- 🔴 **스토어의 거절 문구 미측정** (AC-1 위): NextAuth 가 IdP 의 `error=access_denied` 를 어떤 `?error=` 코드로 넘기는지 실행으로 확인하고,
  `AccessDenied` 라면 `LoginForm.normalizeErrorCode` 가 그것을 «operator 계정» 안내(`role_denied`)로 잘못 읽으니 동의 거절용 문구를 따로 둘 것 — 스토어 쪽 작업.
- `scripts/capture-portfolio.mjs:230` 주석이 스토어 버튼을 «Global Account로 로그인» 으로 적는다 — 셀렉터는 `button:has-text("로그인")` 이라 계속 맞는다.
  공유 경로라 이 프로젝트 티켓에서 고치지 않았다.
- **배포 순서**: account-service(PUT 엔드포인트 + 플래그) 를 auth-service 보다 먼저 또는 함께. 거꾸로면 동의가 404 → 화면 «잠시 후 다시» · 토큰 없음(fail-closed).
  데모는 AMI 재굽기 뒤에 반영된다.
