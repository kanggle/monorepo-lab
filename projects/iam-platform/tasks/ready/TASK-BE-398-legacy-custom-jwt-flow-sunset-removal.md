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
