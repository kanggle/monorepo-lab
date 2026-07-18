# Task ID

TASK-BE-398

# Title

레거시 커스텀-JWT 인증 플로우 일몰 제거 (`/api/auth/login` + `/api/auth/oauth/**`) — ⏳ 2026-08-01

# Status

ready

# Owner

backend

# Task Tags

- code
- api

---

# ⏳ SCHEDULE GUARD — DO NOT IMPLEMENT BEFORE 2026-08-01

이 task 는 **deprecation sunset(2026-08-01) 이전에는 구현 금지**다. 그 전에 제거하면 90일 마이그레이션 윈도우를 위반한다(소비자가 아직 `/oauth2/*` 표준 OIDC 로 이전 중일 수 있음). `ready/` 에 잔류시키되, **AC-0 를 먼저 verify** 한 뒤에만 착수한다(verify-then-act). 선례: 미래 날짜 cleanup 은 cron(이 호스트는 소멸성)이 아니라 git-tracked 백로그 task.

---

# 🔴 선행 의존 (2026-07-12 추가 — `TASK-MONO-365`)

**이 task 는 `iss=iam` 을 발행하는 유일한 경로를 제거한다.** 그런데 **iam 게이트웨이가 그 발급자를 받고 있었다** — 그것도 **단일 값으로**(`expected-issuer: ${JWT_EXPECTED_ISSUER:iam}`, 전 compose 통틀어 오버라이드 0건). 이 task 를 그 상태로 착수했다면 **게이트웨이가 받을 수 있는 발급자가 0 이 되어 엣지가 전면 사망**했을 것이다.

**`TASK-MONO-365` 가 그것을 이미 고쳤다** — iam 게이트웨이는 이제 나머지 6개와 같은 **CSV allowlist**(`allowed-issuers: ${JWT_ALLOWED_ISSUERS:${OIDC_ISSUER_URL:...},iam}`)를 쓴다.

**착수 시 반드시 할 것**:

1. `projects/iam-platform/apps/gateway-service/src/main/resources/application.yml` 의 `allowed-issuers` 기본값에서 **후행 `,iam` 을 제거**한다. **이 task 의 체크리스트에 넣어라** — 안 지우면 폐기된 발급자를 계속 받는다.
2. `TokenValidatorUnitTest#theEdgeSurvivesTheLegacyIssuerSunset` 가 **이미 그 세계를 단언하고 있다**(레거시 뺀 allowlist 로 SAS 토큰 통과 + 레거시 토큰 거부). 초록이면 엣지는 살아있다.
3. **`,iam` 은 iam 게이트웨이 하나가 아니라 게이트웨이 6개 전부의 기본값에 있다**(wms · scm · fan · ecommerce · finance · erp). `TASK-BE-390` 은 **ecommerce 하나만** 본다 — `TASK-MONO-365` § F2 의 범위 판단을 확인할 것.

**왜 이 노트가 필요한가**: 날짜 게이트 task 는 **두 달 뒤 컨텍스트 없이 집어들린다.** 이 결함이 아무 데서도 안 터진 이유는 console-bff 가 게이트웨이를 우회하기 때문이고(`TASK-MONO-347`), 그래서 **착수자가 스스로 알아챌 신호가 없다.**

---

# Goal

ADR-001 D2-b(2026-05-01 deprecated, removal ≥2026-08-01) + ADR-006(BE-396 이 `/api/auth/oauth/**` 커스텀-JWT 소셜 플로우를 SAS 브라우저 세션 플로우로 대체)에 따라, **레거시 커스텀-JWT 인증 경로를 제거**한다.

완료 후 참이 되어야 하는 것: 인증은 전적으로 표준 OIDC SAS(`/oauth2/authorize`·`/oauth2/token`·`/oauth2/revoke`·`/oauth2/userinfo` + 브라우저 `/login`·`/login/oauth/**`)로만 이뤄지고, 자체-발급 커스텀 JWT 경로는 코드에서 사라진다. **SAS 브라우저 소셜 플로우(BE-396/397)는 그대로 동작**한다.

---

# Acceptance Criteria

- [ ] **AC-0 (verify-then-act, 착수 전 필수)**: 아래 레거시 엔드포인트에 대한 **live 소비자가 0** 임을 확인한다 — (a) `git grep` repo-wide 로 `/api/auth/login`·`/api/auth/oauth/` 호출처 0(프런트·서비스·e2e·fed-e2e 시드 포함), (b) access log / gateway route 에서 트래픽 0(운영 데모 스택 기준), (c) 각 consumer 프로젝트가 OIDC `/oauth2/*` 로 이전 완료. **하나라도 잔존 시 STOP** 후 이전 선행.
- [ ] `LoginController`(`POST /api/auth/login`) 제거.
- [ ] `OAuthController`(`GET /api/auth/oauth/authorize`, `POST /api/auth/oauth/callback`) 제거.
- [ ] 커스텀-JWT 발급 꼬리(`OAuthLoginUseCase.callback()` + `OAuthLoginTransactionalStep.persistLogin` + 관련 DTO `OAuthCallbackRequest/Response`·`LoginRequest/Response`) 제거.
- [ ] `RefreshController`(`/api/auth/refresh`) + `LogoutController`(`/api/auth/logout`) — AC-0 에서 잔존 소비자 0 확인 시 제거(SAS 는 `/oauth2/token` refresh + `/oauth2/revoke` + RP-initiated logout 사용). 잔존 시 별도 후속으로 분리.
- [ ] `SecurityConfig` 의 레거시 경로 permit + `DeprecatedApiHeaderFilter` 정리.
- [ ] `:check` GREEN + fed-e2e(IAM) Testcontainers IT GREEN(SAS 브라우저 플로우 회귀 0).

---

# AC-0 사전점검 결과 (2026-07-04, static grep — verify-then-act 준비)

착수 전 필수인 AC-0 의 **(a) 코드 레벨 grep + (c) 소비자 이전** 을 미리 조사한 결과. **(b) 라이브 게이트웨이 트래픽=0 은 구동 스택 access-log 가 필요하므로 08-01 구현 시점에 재확인** 한다. 아래는 정적 스냅샷이며, 착수 시 동일 grep 을 **재확인**할 것(그사이 신규 소비자 유입 가능).

**결론: iam-platform 레거시 `POST /api/auth/login` + `/api/auth/oauth/**` 의 live 외부 소비자 = 0 (정적).** repo-wide `api/auth/(login|oauth|refresh|logout)` 히트를 전수 분류:

- **iam auth-service 자체 컨트롤러/테스트** (`LoginController(Test)`·`OAuthController(Test)`·`RefreshController(Test)`·`LogoutControllerTest`) → 제거 대상 자신, 소비자 아님.
- **iam gateway-service 라우트 설정 + `RouteConfigTest`** → 레거시 public route 정의. 제거 시 SecurityConfig/route 와 함께 정리(In Scope). 외부 소비자 아님.
- **`TenantProvisioningE2ETest`** → 이미 `@Disabled`("2026-08-01 LoginController 와 함께 삭제 예정" 명시, `@Tag("full")`). live 아님 → 제거 스코프에 포함(테스트도 함께 삭제).
- **`ecommerce-microservices-platform/*`** (자체 auth-service·api-client·load-test·gateway) → **별개 프로젝트의 독립 auth 스택**. 경로 문자열만 동일할 뿐 iam-platform 엔드포인트 아님 → **BE-398 무관**.
- **console-web `/api/auth/{login,refresh,logout}`** → console-web **자체 Next.js 라우트**(→ OIDC `/oauth2/*` 리다이렉트/rotation·RP-initiated logout, PC-FE-033/120). iam 레거시 호출 아님 → **이미 OIDC 이전 완료**.
- **fan-platform** → `api/auth/(login|oauth|refresh|logout)` 참조 **0건** (완전 OIDC 이전, ADR-001 D2-b 의 마이그레이션 대상이었음).
- **admin-web** → `projects/iam-platform/apps/admin-web/**` 파일 **0개**(은퇴 완료, platform-console 흡수). 소비자 아님.

**후속 정리 필요(제거의 블로커는 아님):**
- **community-service** 는 레거시 `POST /api/auth/login` 발급 토큰(`iss=iam`)을 SAS 토큰과 **병행 수용**(`OAuth2ResourceServerConfig`·`AllowedIssuersValidator`·`application.yml` "drop after deprecation" 주석). 엔드포인트를 **호출하지는 않으므로** 제거 자체를 막지 않음(제거 후 신규 iam 토큰 발급 중단 → 기존 토큰 만료와 함께 자연 소멸). 단 `iss=iam` 수용 분기는 제거 후 **dead code** → 동반/후속 정리 권장.
- **refresh/logout**: `architecture.md` 는 `/api/auth/refresh`·`/api/auth/logout` 을 "유지(status 미정)"로 표기. iam 외부 소비자는 정적 grep 상 **0**(console-web·ecommerce 는 각자 자체 라우트/서비스). AC 의 "잔존 시 별도 후속 분리" 조건대로, **login+oauth 제거를 코어로 하고 refresh/logout 제거는 08-01 재확인 후 별도 판단** 권장.

**08-01 착수 시 남은 verify:** (b) 데모 스택 gateway access-log 로 레거시 4경로 트래픽 0 확인 + 위 정적 grep 재실행(신규 소비자 유입 없음 재확인). 통과 시에만 제거 착수.

---

# AC-0 재확인 (2026-07-18, static re-measure)

착수자(≥08-01)가 물려받을 스냅샷을 **재측정**했다(2026-07-04 숫자를 승계하지 않고 repo-wide 전수 재분류). **결론: 레거시 4경로(`/api/auth/{login,oauth,refresh,logout}`)의 live 외부 call-site 소비자 = 0.** 2026-07-04 스냅샷과 동일하게 (a) 코드 grep + (c) 소비자 OIDC 이전 **PASS**. (b) 라이브 게이트웨이 트래픽=0 은 여전히 구동 스택이 필요 → **08-01 재확인 유지**.

전수 재분류:

- **fan · scm · erp · finance-platform**: 참조 **0건** (1차 소비자 fan 포함 완전 OIDC 이전).
- **iam auth-service 자체 컨트롤러/테스트/route-config**: 제거 대상 자신 — 소비자 아님.
- **ecommerce-microservices-platform**: 독립 auth 스택 — 경로 문자열만 동일, iam 엔드포인트 아님.
- **platform-console (console-web)**: `/api/auth/{login,refresh,logout}` 은 **console 자체 Next.js 라우트**(→ OIDC `/oauth2/*` · RP-initiated logout). iam 레거시 호출 아님.
- **admin-web**: live 파일 0 (은퇴). done-task history 만 잔존.

**2026-07-04 스냅샷 대비 정정 2건 (둘 다 제거 블로커 아님 — dead-code/자산 정리):**

1. **레거시 `iss=iam` 토큰-수용 dead-code 표면이 community-service 보다 넓다.** `wms-platform` 이 gateway·master·inbound **3서비스**의 `OAuth2ResourceServerConfig`(Javadoc *"accepts both legacy `POST /api/auth/login` tokens and SAS"*) + `master-service/application.yml` 주석(*"Drop after deprecation"*) + `JwtTestHelper` + `infra/grafana/auth-overview.json` 에서 레거시 발급자를 **검증 수용**한다(호출 아님). 제거 후 신규 `iss=iam` 발급 중단 → 이 분기들은 dead code → **동반/후속 정리 대상**(community-service 와 동일 범주). `TASK-MONO-365` 의 게이트웨이 `,iam` allowlist 테마와 같으나, wms 백엔드(master/inbound) resource-server 수용은 **게이트웨이 밖 표면**이라 별도로 헤아릴 것.
2. **iam 자체 load-test 가 레거시 경로를 구동한다.** `load-tests/scenarios/auth-load-test.js`(login·logout) + `load-tests/lib/helpers.js` — 제거 시 함께 갱신할 **In-Scope 테스트 자산**(현 Scope 절 미기재).

---

# Scope

## In Scope (제거)

- `presentation/LoginController.java` (`/api/auth/login`, @Deprecated forRemoval 2026-08-01)
- `presentation/OAuthController.java` (`/api/auth/oauth/**` 커스텀-JWT JSON 플로우)
- `application/OAuthLoginUseCase.callback()` + `OAuthLoginTransactionalStep.persistLogin()` (커스텀 JWT/device-session/refresh 발급 꼬리)
- 레거시 DTO: `OAuthCallbackRequest`/`OAuthCallbackResponse`/`LoginRequest`/`LoginResponse` (다른 곳 미사용 시)
- `infrastructure/config/SecurityConfig` 의 레거시 경로 permit + `DeprecatedApiHeaderFilter` 등록
- `specs/features/oauth-social-login.md` 의 "DEPRECATED 레거시 JSON 콜백" 절 → 제거 기록으로 갱신
- contract `specs/contracts/http/auth-api.md` 의 레거시 엔드포인트 절

## Out of Scope (보존 — SAS 브라우저 플로우가 재사용; 절대 제거 금지)

- `OAuthLoginUseCase.authorize()` / `resolveBrowserLogin()` / `resolveSocialLogin()` (BE-396 브라우저 플로우)
- `application/SocialIdentityPersistStep` (BE-396)
- `presentation/SocialLoginBrowserController` + `LoginPageController` + `templates/login.html` (BE-396/397)
- `OAuthClient` 구현 전부(Google/Kakao/Microsoft/Naver) + `OAuthClientFactory` + `OAuthPropertiesConfigAdapter` + `OAuthProperties` + `OAuthStateStore` + `SavedRequestTenantResolver`
- SAS 일체(`AuthorizationServerConfig`·`TenantClaimTokenCustomizer`·`WebLoginSecurityConfig`·`/oauth2/**`)
- `social_identities` 테이블 + repository (브라우저 플로우가 upsert)
- `PasswordController`/`PasswordResetController`/`AccountSessionController`/`JwksController`/`InternalCredentialController` (deprecation 무관)

---

# Related Specs

> **Before reading**: `platform/entrypoint.md` Step 0 — `PROJECT.md` → `rules/common.md` + `rules/domains/saas.md` + 선언된 trait 파일들. Unknown 태그 = Hard Stop.

- `docs/adr/ADR-001-oidc-adoption.md` (D2-b deprecation 근거)
- `docs/adr/ADR-006-external-idp-login-sas-integration.md` (BE-396 대체)
- `specs/features/oauth-social-login.md` + `specs/features/authentication.md`
- `specs/services/auth-service/architecture.md`

# Related Contracts

- `specs/contracts/http/auth-api.md` (레거시 엔드포인트 절 제거)

---

# Target Service

- `auth-service`

---

# Edge Cases

- `OAuthLoginUseCase.callback()` 와 `resolveSocialLogin()` 가 공유 helper 를 호출 — **`resolveSocialLogin()` 는 보존**하고 `callback()` 만 제거. helper 가 callback 전용이 아님(브라우저 플로우도 사용)에 주의.
- `OAuthLoginTransactionalStep` 제거 시 그 의존(`TokenGeneratorPort`·device-session·refresh repo)이 다른 곳에서 쓰이는지 확인 — 커스텀 JWT 전용이면 함께 정리, 공유면 보존.
- `social_identities` upsert 는 레거시 `persistLogin` 과 브라우저 `SocialIdentityPersistStep` 양쪽에 있음 — 후자만 남기고 전자 제거(중복 해소).
- 레거시 DTO/예외가 테스트에서만 참조될 수 있음 — 테스트도 함께 제거.

---

# Failure Scenarios

- AC-0 미verify 후 제거 → 미이전 소비자 401/404 (프로덕션 회귀). **반드시 verify 선행.**
- `resolveSocialLogin()`/`authorize()` 를 실수로 제거 → SAS 브라우저 소셜 로그인 파손(fed-e2e IT RED 로 적발).
- `OAuthLoginTransactionalStep` 의 공유 의존을 과제거 → 컴파일/wiring 실패.

---

# Test Requirements

- 레거시 컨트롤러/유스케이스 테스트 제거(삭제 대상과 함께).
- 회귀: `SocialLoginSasBrowserIntegrationTest`(BE-396) + `NaverOAuthClientTest`/`OAuthClientFactoryTest`(BE-397) GREEN 유지.
- `:check` + fed-e2e(IAM) Testcontainers IT GREEN.

---

# Definition of Done

- [ ] AC-0 verify 통과(잔존 소비자 0)
- [ ] 레거시 경로 제거 완료
- [ ] SAS 브라우저 플로우 회귀 0 (IT GREEN)
- [ ] Specs/contracts 갱신
- [ ] Ready for review
