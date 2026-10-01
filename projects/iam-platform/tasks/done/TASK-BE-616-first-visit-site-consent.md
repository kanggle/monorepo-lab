# Task ID

TASK-BE-616

# Status

done (2026-10-01 UTC — PR #4093 squash `2e7e2951f` · 통합 시험은 CI 실측, § CORRECTION)

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
- ~~풀 계정의 자기 서비스 표면 404~~ → **이 브랜치에서 고쳤다** (아래 «추가 — § 5 단건 표면»). `AccountSignupIntegrationTest#lockDeletedAccount_returns409` 는
  erp 계정 그대로 두었다(그 시험의 주제는 테넌트별 수명주기이고, 풀 멤버의 같은 경로는 새 IT 가 잰다).
- ~~셀러 프로비저닝의 역방향 공존~~ → **이 브랜치에서 막았다** (아래 «추가 — § 2 역방향»).
- **같은 세션의 두 탭**: 보관 슬롯이 하나라, 두 탭이 차례로 동의 화면에 오면 먼저 연 탭의 «동의» 가 **나중 탭의** authorize 를 재개한다(그 탭의 client 는
  `state` 불일치로 거절 — 토큰이 엉뚱한 곳으로 가지는 않는다). 필요해지면 요청별 키로.
- 🔴 **스토어의 거절 문구 미측정** (AC-1 위) — 구체적으로:
  - **코드 경로**: `projects/ecommerce-microservices-platform/apps/web-store/src/features/auth/ui/LoginForm.tsx` 의 `normalizeErrorCode` →
    `STANDARD_ERROR_MESSAGES`. `'AccessDenied'` 는 `role_denied` 로 접혀 «operator 계정으로는 web-store 에 접근할 수 없습니다…» 를 띄운다
    (그 매핑은 `signInCallback` 의 operator 거절용이다). `access_denied` 그대로면 «로그인이 거부되었습니다. 권한을 확인해 주세요.», 그 밖의 코드
    (`OAuthCallbackError` 등)면 일반 문구. IAM 이 동의 거절에 돌려주는 것은 `error=access_denied&error_description=the user declined to use this site&state=…`
    (스토어 콜백 `/api/auth/callback/iam`) — NextAuth v5 가 그것을 `/login?error=` 의 어느 값으로 바꾸는지는 **실행해 보지 않았다**.
  - **잴 시험**: web-store 풀스택 e2e 에 새 스펙 하나(예: `apps/web-store/e2e/consent-decline.spec.ts`, 짝 = 기존 `e2e/account-type-guard.spec.ts`
    — `role_denied` 문구를 이미 같은 방식으로 단언한다): 팬에만 멤버인 풀 계정으로 스토어 «IAM 로그인» → IAM `/consent` → «동의하지 않음» →
    스토어 `/login` 의 `role="alert"` 문구를 단언하고 URL 의 `error=` 값을 기록. 시드 계정이 필요하다(팬 멤버십만 있는 풀 계정 — dev 시드에 아직 없다).
    nightly 전용(`nightly-e2e.yml`). 결과가 `AccessDenied` 면 `normalizeErrorCode` 에 동의 거절 갈래를 따로 둔다 — 스토어 쪽 작업.
- `scripts/capture-portfolio.mjs:230` 주석이 스토어 버튼을 «Global Account로 로그인» 으로 적는다 — 셀렉터는 `button:has-text("로그인")` 이라 계속 맞는다.
  공유 경로라 이 프로젝트 티켓에서 고치지 않았다.
- **배포 순서**: account-service(PUT 엔드포인트 + 플래그) 를 auth-service 보다 먼저 또는 함께. 거꾸로면 동의가 404 → 화면 «잠시 후 다시» · 토큰 없음(fail-closed).
  데모는 AMI 재굽기 뒤에 반영된다.

---

## 추가 (코디네이터 지시, 2026-10-01 UTC) — 플래그를 켜면 바로 결함이 되는 둘

### § 5 단건 표면 — 풀 멤버를 사이트 테넌트로 찾는다

계약 근거: multi-tenancy.md § 소비자 계정 풀 § 5 — «사이트 테넌트로 계정을 찾는 표면은 그 사이트 멤버십이 있는 풀 계정을 포함» · 판정 «계정 테넌트 = 입력
∨ (풀 ∧ 그 사이트 ACTIVE 멤버)». 614 는 목록 · 검색 · `/internal/tenants/{t}/accounts/{id}` 에만 적용했다. 새 규칙이 아니라 같은 규칙의 나머지 적용이다.

구현: `application/service/SiteAccountLookup.find(repo, flag, tenant, id)` — 플래그 ON ∧ 입력 ≠ `consumer-pool` → 614 의 `findByIdInSiteIncludingPoolMembers`
(같은 JPQL), 아니면 옛 `findById(tenant, id)`. 테넌트 없는 조회 신설 없음.

**census** — account-service 에서 계정 하나를 `(tenantId, accountId)` / `(tenantId, email)` 로 찾는 호출 전부(`accountRepository.find*/exists*` grep, 2026-10-01):

| 호출 지점 | 표면 (테넌트의 출처) | 판정 | 이유 |
|---|---|---|---|
| `ProfileUseCase.getMe` / `updateProfile` | `GET /api/accounts/me` · `PATCH /me/profile` (게이트웨이 `X-Tenant-Id`) | **넓힘** | 본인 조회 — 풀 멤버가 404 였다 |
| `AccountStatusUseCase.getStatus(id, tenant)` | `GET /api/accounts/me/status` · `GET /internal/accounts/{id}/status`(헤더) | **넓힘** | auth-service 의 풀 principal 상태 조회는 `consumer-pool` 을 보내 정확 조회 그대로 |
| `AccountStatusUseCase.changeStatus(cmd, tenant)` | `POST /internal/accounts/{id}/lock` · `/unlock`(헤더 = 사이트) | **넓힘** | 계정 전체에 적용(아래 결과) |
| `AccountStatusUseCase.deleteAccount(…, tenant)` | `DELETE /api/accounts/me` · `POST /internal/accounts/{id}/delete`(헤더) | **넓힘** | 〃 |
| `SendVerificationEmailUseCase` | `POST /api/accounts/signup/resend-verification-email`(헤더) | **넓힘** + 토큰엔 **계정 자신의 테넌트** | 인증 단계를 넓히지 않아도 되게(사이트 계정은 같은 값 — 바이트 동일) |
| `VerifyEmailUseCase` | `POST …/verify-email` (토큰이 테넌트를 운반) | 넓히지 않음 | 토큰이 이제 계정의 실제 테넌트를 싣는다 → 정확 조회가 맞다 |
| `GdprDeleteUseCase` | `POST /internal/accounts/{id}/gdpr-delete`(헤더) | **넓힘** | 코디네이터 결정 — 아래 |
| `DataExportUseCase` | `GET /internal/accounts/{id}/export`(헤더) | **넓힘** | 〃 |
| `ProvisionStatusChangeUseCase` | `PATCH /internal/tenants/{t}/accounts/{id}/status` (경로) | **넓힘** · 이력 행 테넌트 = 계정의 테넌트 | product-service 셀러 정지도 이 경로 — 사이트 계정은 무변경 |
| `ProvisionPasswordResetUseCase` | `POST /internal/tenants/{t}/accounts/{id}/password-reset` (경로) | **넓힘** · 이력 행 테넌트 = 계정의 테넌트 | 감사 행만 쓴다 |
| `TenantAccountQueryUseCase` · `AccountSearchQueryService` | 목록 · 단건 · 이메일 검색 | 이미 넓음(614) | — |
| `AssignRolesUseCase` · `AddAccountRoleUseCase` · `RemoveAccountRoleUseCase` | `PATCH …/roles` · `:add` · `:remove` | **넓히지 않음** | `account_roles` 복합 FK `(tenant_id, account_id) → accounts(tenant_id, id)` 가 풀 계정을 사이트 테넌트로 담을 수 없다. 풀 계정의 사이트 역할은 `consumer_site_roles`(작성자 = BE-618 / MONO-745). 404 유지 |
| `GetAccountRolesUseCase` | `GET …/roles` | 넓히지 않음 | `account_roles` 만 읽는다 — 풀 멤버는 `[]`. auth-service 는 풀 principal 에 이 경로 대신 consumer-members 읽기를 쓴다 |
| `GetAccountIdentityUseCase` (`findIdentityId`) · `ResolveOrCreateIdentity` | 운영자 연결 · 운영자 생성 | 넓히지 않음 | 운영자 측면 — 615 소유자 결정(«운영자 생성은 옛 규칙»), `ADR-MONO-080` 후보 |
| `UpdateLastLoginUseCase` | auth 로그인 이벤트 소비 | 넓히지 않음 | 이벤트 테넌트 = 자격 행 테넌트 = 풀 principal 이면 `consumer-pool`(615 D-8) — 이미 정확 일치 |
| `SocialSignupUseCase.findByEmail` | 소셜 가입 | 넓히지 않음 | 가입 경로 — `TASK-BE-617` |
| `SignupUseCase.existsByEmail` · `ConsumerAccountPool.refuseIfEmailHasSiteAccount` | 가입 | 해당 없음 | 614 가 풀 규칙을 이미 적용 |
| `ProvisionAccountUseCase.existsByEmail` | 내부 계정 생성(단건 · 벌크 · 셀러) | **§ 2 역방향 거절 추가** | 아래 |
| `AccountStatusUseCase.*ResolvingTenant` · `GetConsumerSiteMembershipUseCase` · `ConsentToConsumerSiteUseCase` | id 만 / 풀 전용 | 해당 없음 | 사이트 테넌트 입력이 아니다 |

**쓰기의 결과 (기록)** — 사이트 테넌트로 풀 멤버에게 하는 변이는 **풀 계정 하나**에 일어난다:
- 🔴 **GDPR 삭제**: 그 사람이 멤버인 어느 사이트의 운영자든 할 수 있다(데이터 주체는 사람). 결과는 **모든 소비자 사이트에서** DELETED + PII 마스킹 —
  팬에서 지워 달라고 한 사람의 스토어 계정도 같이 사라진다(하나이므로). 이벤트(`account.status.changed` · `account.deleted`)는 `consumer-pool` 로 한 번.
- 잠금 · 해제 · 삭제 · 상태 전이도 같다 — 스토어 운영자의 잠금은 팬에서도 잠근다. 계약(account-events.md «풀 계정의 상태 전이는 계정 하나의 일»)과 같은 결.
- 🔴 사이트 단위로만 «떠나기»(`LEFT`)는 이 변경의 범위가 아니다 — 그 작성자는 아직 없다.
- **비멤버 대조군**: 스토어에만 멤버인 풀 계정을 `fan-platform` 으로 찾으면 읽기 · 상태 변경 · GDPR 삭제 모두 404, 계정 무변경(아래 IT).

### § 2 역방향 — 내부 계정 생성이 풀 계정 이메일과 겹치면 거절

- `ConsumerAccountPool.refuseIfEmailHasPoolAccount(tenant, email)` — `ProvisionAccountUseCase` 의 테넌트 내 중복 확인 바로 뒤.
  단건 `POST /internal/tenants/{t}/accounts` 와 벌크(`RowProvisioningHelper` → 같은 use case) 둘 다 탄다. 응답은 기존 중복 그대로 `409 ACCOUNT_ALREADY_EXISTS`.
- **소비자 사이트에만**(`Tenant.isConsumerSite()`). wms · erp · demo-corp 같은 B2B/고객 테넌트는 풀 조회조차 하지 않는다(D1).
- 플래그와 무관 — 풀 계정이 하나라도 있으면 공존은 틀리다(플래그 OFF 면 풀 계정이 새로 생기지 않으므로 사실상 무변화, 인덱스 읽기 1회).
- **잠정 결과(기록)**: 풀 쇼퍼를 **같은 이메일로** 셀러 온보딩할 수 없다 — `TASK-MONO-745` 전까지 product-service 의 기존 fail-soft 대로
  `PENDING_PROVISIONING`(`AccountServiceSellerProvisioner.provision` 의 catch → `ProvisioningResult.failed()`, 확인함).
- 🔵 **앞 기록의 정정**: product-service 셀러 온보딩이 보내는 이메일은 사람의 이메일이 아니라 합성값 `seller+{tenant}+{sellerId}@marketplace.local`
  (`AccountServiceSellerProvisioner.sellerEmail`) 이다 — 그 경로로 실제 쇼퍼 이메일과 겹칠 일은 사실상 없다. 실제로 열려 있던 것은 **운영자 프로비저닝 · 벌크**
  (콘솔이 `ecommerce` 에 계정을 사람 이메일로 만드는 경우)이고, 거절은 경로와 무관하게 셋 모두에 걸린다. 앞의 «셀러 온보딩이 공존을 만든다» 는 과장이었다.

### 추가한 시험

- 단위: `PoolMemberSiteLookupTest` 10(규칙 4칸 — ON/사이트 · ON/비멤버 · `consumer-pool` 입력 · OFF; 표면 6칸 — getMe · 상태(멤버 200 / 비멤버 404) ·
  GDPR 삭제(풀 계정 DELETED · 이벤트 `consumer-pool`) · export · 인증 재발송(토큰 테넌트 `consumer-pool`) · 프로비저닝 상태 변경(이력 행 `consumer-pool`)) ·
  `ProvisionAccountPoolEmailRefusalTest` 3(ecommerce 거절 · 풀 없음 통과 · B2B/고객/풀 테넌트 대조군 — 풀 조회 0).
- 기존 단위 시험 배선만: `AccountStatusUseCaseTest` · `GdprDeleteUseCaseTest`(생성자에 플래그 OFF), `ProfileUseCaseTest` · `DataExportUseCaseTest` ·
  `SendVerificationEmailUseCaseTest`(`@Mock ConsumerPoolFlag` — 미스텁 = OFF), `ProvisionAccountUseCaseTest`(`@Mock ConsumerAccountPool`). 기대값 변경 0,
  단 `SendVerificationEmailUseCaseTest#execute_tenantAware…` 의 **픽스처**: ecommerce 로 찾은 계정이 fan-platform 테넌트를 들고 있던 모순을 ecommerce 계정으로
  고쳤다(토큰이 이제 계정의 테넌트를 싣기 때문 — 단언 «토큰 테넌트 = ecommerce» 는 그대로).
- 통합(⚪ 로컬 미실행 — CI 첫 실측): `PoolMemberSiteSurfacesIntegrationTest` — **`AbstractConsumerPoolIntegrationTest` 하위 클래스**(새 컨텍스트 없음).
  스토어 풀 쇼퍼로 `/me` · `/me/status` · `export`(ecommerce 200 / fan-platform 404) · 상태 변경(fan 404 무변경 → ecommerce LOCKED · 이력 행 `consumer-pool`) ·
  GDPR 삭제(fan 404 무변경 → ecommerce 200 · DELETED · 이메일 마스킹 · `account.deleted` 테넌트 `consumer-pool`) · 프로비저닝(ecommerce 409 · 사이트 행 0 /
  같은 이메일 wms 201).

### 게이트 (각각 단독 · `cmd > log 2>&1; echo rc=$?`)

| 게이트 | rc | 비고 |
|---|---|---|
| `:projects:iam-platform:apps:account-service:check` | 0 | |
| `:projects:iam-platform:apps:auth-service:check` | 0 | 이 추가분은 auth 코드 변경 없음(up-to-date) |
| `:projects:iam-platform:apps:admin-service:check` | 0 | |
| web-store `pnpm --filter web-store lint` | 0 | 이 추가분은 web-store 변경 없음 |
| web-store `npx tsc --noEmit` | 0 | 〃 |
| `git add` 후 가드 10개 — index-queue-drift · task-id-collision · walkthrough-ledger-drift · jwt-claims-registry · flyway-version-collision · error-code-registry · project-adr-index-drift · internal-caller-addresses · flyway-unresolvable-placeholder · dev-seed-migration-band | 각각 0 | |
| 새 IT | ⚪ 로컬 미실행 | Docker 없음 — CI 첫 실측 |

### bite (§ 5)

| 끈 것 | 돌린 시험 | 결과 |
|---|---|---|
| (D) `ProfileUseCase.getMe` 만 넓힌 조회 → 옛 `accountRepository.findById` | `PoolMemberSiteLookupTest` · `ProfileUseCaseTest` | **16 중 1 실패** — 정확히 «`/api/accounts/me` … 풀 멤버의 내 정보» 칸. 되돌린 뒤 `git diff` 비어 있음 |

---

## 검토 (2026-10-01 UTC · 분석=Opus 5.5 — 구현 에이전트와 별개)

- **머지 전에 막은 두 결함**: 1차 구현은 플래그를 켜면서 ① 풀 계정이 사이트 테넌트 단건 조회(`/me` · 상태 · GDPR 삭제·내보내기)에서 404, ② 내부 프로비저닝이 풀 계정과 같은 이메일로 사이트 계정을 만들 수 있었다. ①은 새 규칙이 아니라 **계약 § 5 를 `TASK-BE-614` 가 목록·검색에만 적용한 결과** — 단건 조회 전수로 넓혔다(전수 표는 위 «추가» 절). ②는 § 2 공존 금지 — 소비자 사이트에 한해 409.
- **bite**: 넓힌 조회 하나(`ProfileUseCase.getMe`)를 옛 조회로 되돌리면 정확히 그 칸(1/16)만 실패.
- 🔵 **후속 — «사이트 탈퇴» 와 «계정 삭제» 의 구분**: 지금 사이트 운영자의 GDPR 삭제는 **풀 계정 하나**를 지워 모든 소비자 사이트에서 사라진다. 데이터 주체의 삭제 요청은 사람 단위라 맞는 동작이지만, «이 사이트만 그만 쓰기»(멤버십 `LEFT`)를 사용자·운영자가 할 길이 아직 없다. 별도 티켓으로 다룬다.
- 🔵 **후속 — 동의 거절 문구**: `LoginForm.normalizeErrorCode` 가 거절을 운영자 거부 문구로 보일 수 있음(미측정). 측정은 새 nightly e2e `consent-decline.spec.ts` + 팬 전용 풀 시드 계정이 필요.
- 🔵 **후속 — 콘솔 보안 이벤트 조회**: 사이트 테넌트로 걸러 보면 풀 계정 이벤트(`consumer-pool`)가 안 보인다.
- **배포 순서**: account-service 를 auth-service 보다 먼저 또는 함께. 그리고 🔴 **이 PR 이 플래그를 켜므로** 머지 = 데모 재굽기 뒤 실제 동작 변경이다(팬·스토어 프런트는 Vercel — 바로 반영되는 것은 스토어 «IAM 로그인» 버튼 문구뿐이고, 백엔드는 재굽기 전까지 옛 동작).

---

## CORRECTION (2026-10-01 UTC) — ⚪ 로 적은 통합 시험 칸은 **CI 가 쟀다**

«구현 기록» 은 AC-1·2·3·6 의 통합 절반과 web-store vitest 를 ⚪ «로컬 미실행» 으로 적었다(작성 시점에 참, 고치지 않는다). PR #4093 CI:

| 항목 | 결과 |
|---|---|
| 통합 iam A·B · ecommerce A·B·C | **통과** — 로그에서 실행 확인: `ConsumerPoolSsoIntegrationTest` AC-6 체인(스토어 풀 가입 → 스토어 토큰 → 팬 첫 방문 `/consent` → 동의 → 팬 토큰) · AC-1 거절(`access_denied` + `state`, 코드·토큰·멤버십 쓰기 없음) · AC-3 콘솔에는 동의 없음 · 계정 쪽 동의 쓰기 IT(체인 · B2B `erp` 는 쓰기 없음) |
| Frontend unit tests (vitest, CI Node) | **통과** |
| Frontend E2E smoke | 1차 **실패** — 시험 전 web-store 빌드가 `next/font` Google Fonts 내려받기에서 `TypeError`(이 PR 은 `layout.tsx` 무변경, 같은 런의 «Frontend lint & build» 는 같은 앱 빌드 통과) → 그 잡만 재실행 → **통과**. 외부 네트워크 일시 실패로 판정 |

⇒ 위 AC 는 CI 실측으로 닫혔다. 머지 `2e7e2951f`(#4093), 실패 0. 후속 셋(사이트 탈퇴 vs 계정 삭제 · 동의 거절 문구 · 콘솔 보안 이벤트 조회)은 «검토» 절에 그대로 남는다 — 이 티켓을 닫아도 그 의무는 사라지지 않게 별도 티켓으로 기안해야 한다(닫는 PR 에서 처리).
