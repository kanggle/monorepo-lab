# Task ID

TASK-BE-602

# Status

review

# Title

소셜 로그인은 계정의 **실제 테넌트**를 모른다 — 스토어 소셜 계정은 잠겨도 들어오고, 소셜 로그인 이벤트도 못 낸다

# Owner

iam-platform

# Task Tags

- auth-service
- social-login
- multi-tenant
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — «이 계정의 테넌트는 어디서 믿고 가져오나» 가 설계 결정이다.

---

# Goal

두 후속이 **같은 한 질문**에 막혀 있다: 소셜 로그인 경로에서 **계정의 실제 테넌트**를 어디서 얻는가.

1. **잠금 효력** (`TASK-BE-600` 후속) — BE-600 이 상태 조회에 `X-Tenant-Id` 를 붙여 폼 로그인은 모든 테넌트에서 잠금이 효력을 갖게 됐다.
   소셜 경로는 **여전히 테넌트를 안 보낸다** ⇒ account-service 가 fan-platform 으로 읽고, BE-507 이후 `ecommerce` 등에서 만든 소셜 계정은
   **404 → 상태 검사 생략(설계상 유지)** ⇒ **잠긴 스토어 소셜 계정이 소셜로 들어온다**.
2. **소셜 로그인 이벤트** (`TASK-BE-599` 후속) — 소셜 로그인은 `auth.login.*` 을 내지 않는다. BE-599 가 넣지 않은 이유:
   이벤트의 `tenantId` 를 시작 client 에서 가져오면 BE-507 이전 계정의 실제 테넌트와 다를 수 있고(틀린 테넌트 키로 VELOCITY 가 센다),
   소셜 실패의 대부분은 `emailHash` 가 없어 계약(`emailHash` 필수)과 맞지 않으며, `loginMethod` 값 매핑도 정해야 한다.

🔵 **소유자 결정 (2026-09-25 UTC)** — 둘을 **한 티켓**으로 기안(같은 테넌트 결정에 묶여 있다).

## 실측 (2026-09-25 UTC · 저장소만 · BE-599/600 보고 기준)

| 사실 | 근거 |
|---|---|
| 소셜 상태 조회는 테넌트 없이 부른다 | `OAuthLoginUseCase.java` 상태 조회 · BE-600 이 empty 를 «404 만» 으로 좁혔다(실패는 fail-closed) |
| 404 를 거부로 바꾸면 안 된다 | BE-507 이후 `ecommerce` 소셜 계정이 fan-platform 조회에서 404 — 거부로 바꾸면 **스토어 소셜 로그인 전부 차단**(BE-600 보고) |
| 폼 경로는 해결됐다 | BE-600: 폼 로그인은 자격 행의 테넌트로 `X-Tenant-Id` 를 보낸다 |
| ⚪ 미측정 | ① 소셜 신원(`social_identities` 등) 행이 계정의 테넌트를 들고 있는가 ② BE-507 이전 계정의 테넌트 값 실태(마이그레이션 여부) ③ 소셜 실패 유형별로 `accountId`·`emailHash` 를 알 수 있는 지점 |

# Scope

## 포함

- **AC-0 결정**: 소셜 경로의 «계정의 실제 테넌트» 출처(후보: 소셜 신원 행 · 자격 행 · account-service 역조회 · 시작 client). 🔴 소유자 결정 대상.
- 그 출처로 ① 상태 조회에 `X-Tenant-Id` ② 소셜 로그인 `auth.login.*` 발행(`loginMethod` 매핑 · `emailHash` 없는 실패의 계약 처리).

## 제외

- 폼 로그인(해결됨). 디바이스 세션(BE-599 에서 철회 — 기기 식별 쿠키 후속).

# Acceptance Criteria

- [x] **AC-0** — ⚪ 셋 재측 → 테넌트 출처 후보 표(정확성 · BE-507 이전 계정 · 비용) → 🔴 **소유자 결정**.
  🟢 **닫힘 (2026-09-25 UTC · 저장소만)** — 결정 = **account-service 가 답한다**(아래 표 ①).

  | 후보 | BE-507 이후 계정 | BE-507 이전 계정 | 비용 | 판정 |
  |---|---|---|---|---|
  | ① **account-service 가 accountId 로 (상태, 테넌트) 를 답한다** (신규 내부 조회) | ✅ 실제 `accounts` 행 | ✅ 실제 `accounts` 행 | 소셜의 기존 상태 조회 1회를 **대체**(호출 수 불변) · 내부 계약 추가 | 🟢 **선택** |
  | ② 신원 행 `social_identities.tenant_id` | ✅ (시작 client 테넌트 = 계정 테넌트) | 🔴 **틀림** — BE-507 이전에도 신원 행은 시작 client 테넌트로 찍혔고(`OAuthLoginUseCase` 의 `resolveBrowserLogin(…, tenantId)` 가 신원 행에 귀속), 계정 생성 호출만 그 값을 버려 계정=`fan-platform` ⇒ 스토어 소셜 계정은 신원=`ecommerce` · 계정=`fan-platform` 로 **어긋난다**(BE-507 커밋 `b1658cede` 메시지: «이미 들고 있던 tenantId 를 socialSignup 에 넘긴다») | 무료 | 기각 |
  | ③ 신원 행 → 404 면 `fan-platform` 재조회 | ✅ | ✅ (단 «BE-507 이전은 전부 fan-platform» 불변식에 기댐) | 올드 계정 +1 호출 | 기각 — 불변식이 깨지면 조용히 틀림 |
  | ④ 자격 행 `credentials.tenant_id` | ❌ 소셜 전용 계정엔 행이 없다(`SocialSignupUseCase` 가 만들지 않음) | ❌ | — | 기각 |
  | ⑤ 시작 client 테넌트 | ✅ | 🔴 틀림(위 ② 와 같은 어긋남) | 무료 | 기각(Failure Scenario 1) |
  | ⑥ 데이터 소급 수정 | — | — | 운영 데이터 재배정 | 기각 — BE-507 이 의도적으로 피한 범위 |

  **실측 근거 (코드·마이그레이션)**
  - `social_identities.tenant_id` 는 V0007(BE-229)에서 추가, 유니크 키 `(tenant_id, provider, provider_user_id)` — **테넌트별**. 그러나 조회
    `SocialIdentityJpaRepository.findByProviderAndProviderUserId` 는 테넌트 인자 없이 단수 `Optional` ⇒ 같은 신원이 두 테넌트에 있으면
    `IncorrectResultSizeDataAccessException`. ⚪ 실제로 도달 가능한지는 미측정 — AC-1 에서 한 줄로 판정하고, 가능하면 별도 결함으로.
  - account-service `AccountRepository` 규칙 «`findById(id)` without tenant is forbidden» — ① 은 **테넌트를 입력으로 받지 않고 출력으로 돌려주는**
    조회라 이 규칙의 **문서화된 예외**가 된다(내부 전용 · 응답에 테넌트 포함). 참고: `GET /internal/accounts/{id}`(`AccountSearchController`)는
    이미 테넌트 없이 PK 로 읽지만 응답에 `tenantId` 가 없다 — 확장 후보.
  - account-service 에 «accountId → 테넌트» 를 돌려주는 엔드포인트는 **현재 없다**.

  🔴 **범위에 추가 (같은 컨트롤러 · BE-600 후속)** — `SocialLoginBrowserController` 의 catch 목록에 `AccountServiceUnavailableException` 이 없다
  ⇒ BE-600 이 소셜에 넣은 fail-closed 가 **로그인 오류 화면이 아니라 전역 핸들러(500 가능)** 로 떨어질 수 있다(⚪ 응답 미추적). `socialSignup`
  실패도 같은 모양. AC-1 에서 «조회 실패 → 사용자에게는 일반 로그인 오류» 를 단언하라.
  🔵 소셜 실패 분기에서 `accountId`·`emailHash` 를 둘 다 아는 곳은 **마지막 두 단계**(상태 조회 실패 · 비ACTIVE)뿐 — AC-2 이벤트 설계의 입력.
- [x] **AC-1** — 상태 조회에 실제 테넌트 전달 · 단위 테스트: 스토어 소셜 계정 LOCKED → 거부 · 신규 소셜 가입(404) → 통과 · 조회 실패 → fail-closed(BE-600 규칙 유지).
  🟢 **닫힘 (2026-09-25 UTC)** — «테넌트를 전달» 이 아니라 AC-0 결정대로 **테넌트를 돌려받는** 조회로 바꿨다(아래 § 설계 ①).
  - **계약 먼저**: `specs/contracts/http/internal/auth-to-account.md` § `GET /internal/accounts/{accountId}/status-with-tenant`(신설) — 응답
    `{accountId, tenantId, status, statusChangedAt}` · 매핑 규약은 `/status` 의 BE-600 표와 같은 선(404 → empty · 그 밖 → fail-closed · `tenantId` 없는
    200 도 fail-closed). 격리 규칙의 **문서화된 예외**: `specs/features/multi-tenancy.md` § 격리 회귀 방지(규칙이 적힌 곳) + account-service
    `AccountRepository.java:20,51` javadoc.
  - account-service: `AccountRepository.findByIdResolvingTenant` (`AccountRepositoryImpl.java:39`) → `AccountStatusUseCase.getStatusResolvingTenant`
    (`AccountStatusUseCase.java:83`) → `AccountStatusQueryController.java:57` `@GetMapping("/{accountId}/status-with-tenant")`.
  - auth-service: 포트 `AccountServicePort.getAccountStatusAndTenant` · 어댑터 `AccountServiceClient.getAccountStatusAndTenant`(`X-Tenant-Id` 미전송) ·
    소셜 경로 `OAuthLoginUseCase.java:278` 가 옛 `getAccountStatus(accountId)`(fan-platform 고정)를 **대체**(호출 수 불변 — 1회).
  - 단위 테스트(`OAuthLoginUseCaseSocialTenantTest` — 트랜잭션 꼬리를 **실제** `SocialIdentityPersistStep`+`SocialLoginSteps` 로 조립, 저장소만 mock ⇒
    «LOCKED → 거부» 는 스텁이 아니라 실제 `AccountStatusRule`):
    스토어(ecommerce) LOCKED → `AccountLockedException` + 옛 고정 조회 0회 `:111` · DORMANT → 거부 `:125` · **신규 소셜 가입 404 → 통과**(404 를 거부로
    바꾸지 않음) `:135` · **조회 실패 → fail-closed**(예외 그대로 전파 · 신원 행 쓰기 0 · 이벤트 0) `:153`.
    어댑터 `AccountServiceClientUnitTest.java:201,218,227,237,250,263`(200 · 404 · 403 · tenantId 없는 200 · status 없는 200 · 네트워크 오류) ·
    account-service `AccountStatusUseCaseTest.java:106,125` · `InternalControllerSliceTest.java:99,116,131`(200 에 `tenantId` · `X-Tenant-Id` 무시 · 404).
  - 🔴 **범위 추가분(같은 컨트롤러)**: `SocialLoginBrowserController.java:151` 가 `AccountServiceUnavailableException`(조회 실패 · `socialSignup` 실패)을
    잡아 `/login?error=temporarily_unavailable` 로 돌린다(`login.html:60` «Sign-in is temporarily unavailable. Please try again in a moment.» — 계정
    상태에 대해 아무것도 말하지 않음). **이전 응답을 코드로 추적한 결과**: 500 이 아니라 전역 `AuthExceptionHandler.java:241` 의 **503 JSON**
    (`SERVICE_UNAVAILABLE`)이 브라우저에 그대로 떴다. 테스트 `SocialLoginBrowserControllerTest.java:217`(조회 실패) · `:224`(`socialSignup` 실패) —
    둘 다 «세션 없음 + `/login?error=…`» 단언. 🔵 문구를 «일반 로그인 오류»(`*` 케이스 = «Invalid email or password.»)로 떨어뜨리지 않은 이유:
    소셜에는 비밀번호가 없어 그 문구는 **거짓 안내**다 — 새 코드 1개 + 템플릿 1줄.
  - **`findByProviderAndProviderUserId` 의 `IncorrectResultSizeDataAccessException` 판정(한 줄)**: **도달 가능 — 단 경쟁 조건으로만.** 유니크 키가
    V0007 이후 `(tenant_id, provider, provider_user_id)` 라 같은 공급자 사용자가 **서로 다른 client(테넌트)로 동시에 첫 소셜 로그인**을 하면 둘 다
    비트랜잭션 조회에서 «없음» 을 보고 서로 다른 `tenant_id` 로 INSERT 해 키에 걸리지 않는다(`SocialLoginSteps.upsertIdentity` 는 존재하면 update,
    없을 때만 create — 순차 경로로는 두 번째 행이 생기지 않는다). 그 뒤 그 사용자의 **모든** 소셜 로그인이 이 예외 → 컨트롤러 catch 목록 밖 →
    전역 핸들러(영구 장애). → **별도 결함으로 기록**(아래 § 후속 ①) — 이 티켓에서 고치지 않았다(범위 밖 · 사소하지 않음: 어느 행을 이기게 할지가
    결정이다).
- [x] **AC-2** — 소셜 로그인 이벤트 발행 — 🔴 계약에 없는 값이 필요하면 **계약부터**(`loginMethod`, `emailHash` 선택화 여부).
  🟢 **닫힘 (2026-09-25 UTC)** — 계약 먼저: `specs/contracts/events/auth-events.md` § «소셜 로그인 경로 (TASK-BE-602)»(신설) + `loginMethod` enum 에
  `OAUTH_NAVER` 추가(BE-397 이 Naver 를 넣을 때 enum 이 따라오지 않았다 — 유일한 새 값).
  - **`emailHash` 는 선택화하지 않았다** — 이벤트를 **status-with-tenant 가 200 으로 답한 뒤에만** 내기로 했고(§ 설계 ②), 그 시점엔 공급자 이메일이
    **항상** 있다(이메일 없는 콜백은 그 전에 `OAuthEmailRequiredException`). 필수 필드 규칙 그대로 ⇒ 소비자 영향 0.
  - **소비자 검증 필드 확인(코드 읽기)**: security-service `AbstractAuthEventConsumer.java:60-67` 는 `eventId` · `tenantId` 만 검사(누락 → DLQ),
    `AuthEventMapper` 는 `emailHash` · `loginMethod` 를 읽지 않는다. account-service `LoginSucceededConsumer` 는 `accountId` · `tenantId` 로
    `findById(tenantId, accountId)` — 계정의 **실제** 테넌트를 싣기 때문에 스토어 소셜 계정의 `last_login_succeeded_at` 도 갱신된다. 다른 프로젝트의
    `auth.login.*` 소비자 0(`projects/*/apps/**/src/main` grep). ⇒ DLQ 위험 없음.
  - 구현: `OAuthLoginUseCase.java:105-134` — `attempted` → (ACTIVE) `succeeded(loginMethod=OAuthProvider.loginMethod())` / (LOCKED · DORMANT · DELETED)
    `failed(failureReason)` / 계약 밖 상태 → `attempted` 만. `failureReason` 매핑은 `AccountStatusRule.eventFailureReason`(폼과 공유 — 폼 provider 의
    사본 집합도 이것을 가리키게 바꿨다). 레코더는 BE-599 의 `LoginEventRecorder`(+ `loginMethod` 오버로드). `tenantId` = status-with-tenant 의 값.
    텔레메트리 격리 = `OAuthLoginUseCase.recordTelemetry`(레코더 프록시 **밖**에서 catch — BE-599 규칙).
  - 테스트 `OAuthLoginUseCaseSocialTenantTest`: 성공 → attempted+succeeded(`OAUTH_GOOGLE`) `:169` · **BE-507 이전 계정(신원 · client = ecommerce,
    계정 = fan-platform) → 이벤트 tenantId = fan-platform** `:182` · LOCKED → failed(`ACCOUNT_LOCKED`) `:194` · 계약 밖 상태 → failed 없음 `:208` ·
    **텔레메트리 실패 → 성공은 성공** `:222` · **거부는 같은 거부** `:237`. `LoginEventRecorderTest.java:74`(`OAUTH_NAVER`) · `AccountStatusRuleTest.java:62`.
- [ ] **AC-3** — 🔴 결과로 판정: 스토어 테넌트 일회용 소셜 계정을 잠그고 소셜 로그인 → 거부 · 대조군(잠그기 전 성공). 소셜 공급자 없이 재현 가능한지부터(e2e 스텁).
  → **미완 — 런북은 아래 § AC-3 런북.** 단위 테스트는 «잠금 → 소셜 로그인 거부» 의 결과 상태를 모른다.

## 설계 (구현 중 결정 — 근거)

① **새 엔드포인트, `/status` 확장이 아님.** `/status` 의 «헤더 없음 = `fan-platform` 고정» 은 membership-service · admin-service 가 기대는 의미라
바꿀 수 없다(BE-600 이 net-zero 로 지킨 것). «테넌트를 모름» 모드를 같은 엔드포인트에 넣으려면 플래그(쿼리 · 헤더 값)가 필요하고, 그러면 «헤더 없음»
이 호출자에 따라 두 가지를 뜻하게 된다. 응답에 `tenantId` 만 더하는 확장은 **입력이 여전히 고정 테넌트**라 문제를 못 푼다. 새 경로 하나 +
`X-Tenant-Id` 를 받지 않음 = 가장 작은 **올바른** 변경. 기존 `GET /internal/accounts/{id}`(`AccountSearchController`)의 확장도 봤으나 이메일 ·
프로필(PII)을 싣는 운영자 조회라 로그인 경로의 소비자로 쓰지 않았다.

② **이벤트는 status-with-tenant 가 200 으로 답한 뒤에만.** 필수 `tenantId` 를 계정의 실제 테넌트로 채울 수 있는 유일한 지점이다. 그 앞의 실패
(state · 공급자 · 이메일 없음 · `socialSignup` 실패)와 **조회 실패**(티켓 본문 «마지막 두 단계» 중 하나)·**404** 는 이벤트를 내지 않는다 — 그 자리에서
낼 수 있는 테넌트는 시작 client 의 것뿐이고 그것이 Failure Scenario 1 이다. 폼 경로도 «조회 실패 = `attempted` 만» 이었으므로 모양이 크게 다르지
않다(소셜은 `attempted` 조차 테넌트를 모르므로 없음). 결과: **새 enum 값 1(`OAUTH_NAVER`) 외 계약 변경 0 · 필드 선택화 0**.

③ 발급 토큰의 `tenant_id` 는 **바꾸지 않았다**(여전히 시작 client — ADR-006 옵션 1). 이벤트의 `tenantId` 와 다를 수 있다(BE-507 이전 계정). 토큰은
«어느 플랫폼에 들어가나», 이벤트는 «어느 계정의 로그인인가» — 의도된 차이로 `architecture.md` 에 적었다.

## AC-3 런북 (창 또는 iam e2e compose — 미실행)

**소셜 공급자 없이 재현 가능한가 — 예, 단 준비물이 저장소에 «있지는 않다».** 측정(2026-09-25, 저장소 grep): `tests/e2e`(iam) 와
`docker-compose.e2e.yml` 에 소셜 공급자 스텁 **없음**, 기존 소셜 IT(`SocialLoginSasBrowserIntegrationTest`)는 `OAuthClientProvider` 를
`@MockitoBean` 으로 바꾸는 **JVM 안의** 스텁이라 라이브 창에서 못 쓴다. 그러나 **Kakao** 는 id_token 검증이 없고(`application.yml:105`) 토큰 ·
사용자정보 URI 가 설정값(`oauth.kakao.token-uri` · `oauth.kakao.user-info-uri`)이라, auth-service 를 환경변수
`OAUTH_KAKAO_TOKENURI` · `OAUTH_KAKAO_USERINFOURI`(Spring relaxed binding — 🔴 ⚪ 이 변수명으로 실제 바인딩되는지는 라이브 미측정, 첫 단계에서
`/actuator/env` 또는 로그로 확인)로 **WireMock 컨테이너**에 향하게 재기동하면 실제 공급자 없이 콜백 전체가 돈다.
Google · Microsoft 는 id_token JWKS 서명 검증이 있어 스텁이 더 무겁다 — Kakao 를 쓴다.

🔴 **데모 공유 계정은 잠그지 마라.** 이 티켓의 auth-service **와** account-service 가 모두 떠 있어야 한다(이미지 시각 ≥ 머지 시각 — 호스트 `:latest`
가 낡을 수 있다). account-service 가 옛 이미지면 새 엔드포인트가 404 → «규칙 미적용 → 통과» 로 떨어져 5 가 **거짓 FAIL** 이 된다.

1. **스텁** — WireMock 컨테이너(auth-service 와 같은 네트워크)에 두 매핑:
   `POST /oauth/token` → `200 {"access_token":"be602-at","token_type":"bearer"}` ·
   `GET /v2/user/me` → `200 {"id":602000001,"kakao_account":{"email":"be602-<UTC날짜>@example.com","profile":{"nickname":"be602"}}}`.
   auth-service 를 `OAUTH_KAKAO_TOKENURI=http://<wiremock>/oauth/token` · `OAUTH_KAKAO_USERINFOURI=http://<wiremock>/v2/user/me` 로 재기동.
2. **스토어 client 로 진입** — 브라우저(또는 쿠키 유지 curl)로 web-store 의 IAM 로그인 → `/oauth2/authorize?client_id=ecommerce-web-store-client…` →
   `/login` → `GET /login/oauth/kakao` 의 302 `Location` 에서 `state` 를 뽑는다(실제 kauth 로 가지 않는다). 같은 쿠키로
   `GET /login/oauth/kakao/callback?code=any&state=<state>`.
3. **대조군 (잠그기 전)** — 2 의 콜백이 **302 저장된 authorize 로 성공**. `account_db.accounts` 에서 그 이메일의 `id`(= `<AID>`) · `tenant_id=ecommerce`
   (BE-507 이후 신규 → 스토어 테넌트) · `status=ACTIVE`. 보조: auth outbox 에 `auth.login.attempted` + `auth.login.succeeded`(`accountId=<AID>` ·
   `tenantId=ecommerce` · `loginMethod=OAUTH_KAKAO`) — **AC-2 의 결과 상태**도 여기서 같이 잰다. 🔴 첫 로그인은 `socialSignup` 이 계정을 만든 뒤라
   status-with-tenant 가 200 이어야 한다 — 404 가 보이면(이벤트 0) account-service 이미지부터 의심.
4. **잠그기** — `POST /internal/accounts/<AID>/lock` · `Authorization: Bearer <internal.invoke 워크로드 토큰>` · `X-Tenant-Id: ecommerce` ·
   본문 `{"reason":"ADMIN_LOCK","operatorId":"be602-verify"}`. 🔴 판정은 `account_db.accounts.status = LOCKED` 행으로(REST 200 이 아니라).
5. **판정** — 2 를 새 `state` 로 반복 → 콜백이 **302 `/login?error=account_unavailable`**, 세션 인증 없음. 보조: outbox 에 `auth.login.failed`
   (`accountId=<AID>` · `tenantId=ecommerce` · `failureReason=ACCOUNT_LOCKED`) 1건.
6. **(선택) fail-closed 화면** — account-service 를 잠시 내리고 2 → `/login?error=temporarily_unavailable`(503 JSON 이 아님) · 이벤트 0.
7. **정리** — `POST /internal/accounts/<AID>/unlock`(`{"reason":"ADMIN_UNLOCK","operatorId":"be602-verify"}`, 같은 헤더) → 다시 로그인 성공.
   auth-service 를 스텁 URI 없이 재기동(원상복구).

판정 기준: 3 성공 AND 4 LOCKED AND 5 `/login?error=account_unavailable`. 3 이 실패하면 판정 불가(대조군 없음) — 5 를 PASS 로 읽지 마라.
이 티켓 이전 이미지라면 5 는 **성공**(fan-platform 고정 조회 404 → 검사 생략)했을 것 — 그것이 이 티켓이 닫는 결함의 모양이다.

## 검증 (2026-09-25 UTC · 이 호스트)

| 무엇 | 결과 | 비고 |
|---|---|---|
| `./gradlew :projects:iam-platform:apps:auth-service:test` | **rc=0 · 817 tests / 실패 0 / 오류 0 / skipped 28** (JUnit XML 108 스위트 합산) | 이 티켓 신규 +20: `OAuthLoginUseCaseSocialTenantTest` 10 · 어댑터 6 · 컨트롤러 2 · 레코더 1 · 규칙 1. 기존 `OAuthLoginUseCaseTest` 17 은 새 포트로 이관(수 불변) |
| `./gradlew :projects:iam-platform:apps:account-service:test` | **rc=0 · 511 tests / 실패 0 / 오류 0 / skipped 47** (79 스위트) | BE-600 기록 506 + 이 티켓 +5(use case 2 · slice 3) = 511 — 수가 맞는다. skipped = Docker 없는 IT |
| bite ① 이벤트 tenant 를 시작 client 의 것으로 | **1 실패** — `preBe507Account_eventsCarryAccountsTenant_notClientTenant` | 스토어 계정 테스트는 두 값이 같아(ecommerce) 못 잡는다 — 이 bite 를 무는 것은 BE-507 이전 픽스처뿐 |
| bite ② 상태를 persist step 에 안 넘김(검사 생략) | **6 실패** — LOCKED · DORMANT · 계약 밖 · LOCKED 이벤트 · 텔레메트리-거부 + 기존 `OAuthLoginUseCaseTest` 캡처 1 | |
| bite ③ 컨트롤러 catch 제거 | **2 실패** — 컨트롤러 조회 실패 · `socialSignup` 실패 | ①②③ 동시 주입, 39 중 9 실패로 각자 귀속 확인 |
| bite ④ 텔레메트리 swallow 제거 | **2 실패** — 텔레메트리 성공/거부 | |
| bite ⑤ 어댑터의 `tenantId` 필수 검사 제거 | **1 실패** — `getAccountStatusAndTenant_200WithoutTenant_throws` | ④⑤ 동시, 44 중 3 |
| bite ⑥ account-service use case 를 `findById(FAN_PLATFORM, id)` 로 | **2 실패** — tenant 반환 테스트(행동) + 404 테스트(STRICT_STUBS 미사용 스텁 — 행동 아님, 정직하게 기록) | 복원 뒤 두 스위트 전체 재실행 = 위 두 행 |
| `SocialLoginSasBrowserIntegrationTest`(스텁 경로 교체 + WireMock 호출 검증 추가) | ⚪ **CI 에서 판정** | `integrationTest --tests …` 로 돌렸으나 **skipped 1**(이 호스트 Docker 부재, `DockerAvailableCondition`). 🔴 이 IT 는 `test` 태스크에서 제외(`@Tag("integration")`)라 위 817 에 없다 |
| security-service | 변경 없음 · 미실행 | 소비자 필드 검증은 코드 읽기로만 확인(위 AC-2) |

## 후속 (파일 미생성 — 기록만)

1. 🔴 **소셜 신원 경쟁 → 영구 `IncorrectResultSizeDataAccessException`** (AC-1 판정) — 같은 공급자 사용자의 서로 다른 테넌트 client 동시 첫 로그인이
   `(tenant, provider, puid)` 키를 피해 두 행을 만들면 그 사용자의 소셜 로그인이 영구히 전역 핸들러로 떨어진다. 결정 필요: 조회를 테넌트 한정으로
   바꿀지(그러면 한 공급자 신원이 테넌트마다 다른 계정 — `oauth-social-login.md` Business Rules «하나의 provider_user_id 는 하나의 계정에만 연결» 과 충돌) ·
   `(provider, provider_user_id)` 유니크를 되살릴지. 문서도 낡았다: `OAuthLoginUseCase` javadoc 의 «DB unique key on `(provider, provider_user_id)`» 는
   V0007 이후 거짓.
2. **컨트롤러의 나머지 미포착 예외** — DB 오류(신원 upsert) 등 `RuntimeException` 은 여전히 전역 핸들러(500 JSON)로 간다. `/login?error` 로 모을지는
   별도 판단(이 티켓은 명시된 `AccountServiceUnavailableException` 만).
3. **`attempted` · `failed` 의 `loginMethod`** — 지금은 `succeeded` 에만 있어 security-service 가 소셜 실패와 폼 실패를 이벤트로 구별하지 못한다.
   필요해지면 additive 로(소비자는 모르는 필드를 무시).
4. **배포 순서** — 새 auth + 옛 account 조합이면 새 엔드포인트가 404(`NoResourceFoundException` → `CommonGlobalExceptionHandler`) → 소셜 상태 검사
   생략 · 이벤트 0 ⇒ fan-platform 계정에 대해선 BE-600 대비 **퇴행**. 같은 커밋이라 재굽기에서 함께 들어가지만, 굽기 전에 account-service 이미지를
   확인하라(`auth-to-account.md` § 배포 순서).

# Related Specs

- `specs/features/oauth-social-login.md` · `specs/contracts/events/auth-events.md` · `specs/contracts/http/internal/auth-to-account.md`
- `TASK-BE-599` (소셜 이벤트 후속) · `TASK-BE-600` (소셜 테넌트 후속) · `TASK-BE-507` (테넌트별 소셜 계정)

# Related Contracts

- `GET /internal/accounts/{id}/status` — BE-600 이 추가한 선택 `X-Tenant-Id` 를 소셜 경로도 쓴다(계약 변경 없음).
  → 🔴 **이탈 (AC-0 결정의 직접 결과)**: 소셜은 이 엔드포인트를 **더 이상 쓰지 않는다.** 신규 `GET /internal/accounts/{id}/status-with-tenant`
  (auth-to-account.md) 로 옮겼다 — 이 행이 전제한 «헤더로 테넌트를 보낸다» 는 AC-0 표 ①(테넌트는 출력)과 모순이고, 보낼 테넌트가 없다(§ 설계 ①).
  `/status` 자체는 바이트 불변(폼 · membership · admin).
- `auth.login.*` — 필요 시 계약 변경은 AC-2 에서 먼저.
  → auth-events.md § «소셜 로그인 경로 (TASK-BE-602)» 신설 · `loginMethod` enum 에 `OAUTH_NAVER` 추가(유일한 새 값). `emailHash` 선택화 없음.

# Edge Cases

| 상황 | 기대 |
|---|---|
| BE-507 이전 계정(테넌트 값이 fan-platform 으로 굳은) | AC-0 의 출처가 그 계정도 맞게 답하는가 — 표에 칸으로 |
| 한 소셜 신원이 여러 테넌트 계정에 연결 | 존재하는가부터(AC-0) — 있으면 어느 계정으로 로그인하는지가 먼저 정해져 있어야 한다 |

# Failure Scenarios

1. **시작 client 의 테넌트를 믿는다** → BE-507 이전 계정에 틀린 테넌트 → 잠금 누락 · VELOCITY 오집계.
2. **404 를 거부로 바꾼다** → 스토어 소셜 로그인 전면 차단.
3. **이벤트를 계약 밖 값으로 낸다** → 소비자가 DLQ 로 보낸다(테넌트·필드 규칙).
