# TASK-BE-162 — auth-service 미테스트 Controller @WebMvcTest 작성

## Goal
auth-service에서 `@WebMvcTest` 슬라이스 테스트가 없는 두 컨트롤러에 대해 테스트를 추가한다.

## Scope
- `LogoutController` — POST /api/auth/logout
- `OAuthController` — GET /api/auth/oauth/authorize, POST /api/auth/oauth/callback

## Acceptance Criteria
- [ ] `LogoutControllerTest` — 정상 로그아웃(204), 유효성 오류(400) 시나리오
- [ ] `OAuthControllerTest` — authorize 정상(200), 지원 안 되는 provider(400), callback 정상(200), invalid state(401), email required(422), 유효성 오류(400) 시나리오
- [ ] `@WebMvcTest` + `@Import({SecurityConfig.class, AuthExceptionHandler.class})` 패턴 준수
- [ ] `./gradlew :apps:auth-service:compileTestJava` BUILD SUCCESSFUL

## Related Specs
- `specs/services/auth-service/architecture.md`
- `specs/features/oauth-social-login.md`

## Related Contracts
없음

## Edge Cases
- LogoutController: `X-Device-Id` 헤더는 optional
- OAuthController callback: `isNewAccount` true/false 에 따라 동일 200 반환
- OAuthController authorize: `redirectUri` 파라미터 optional

## Failure Scenarios
- `UnsupportedProviderException` → 400 UNSUPPORTED_PROVIDER
- `InvalidOAuthStateException` → 401 INVALID_STATE
- `OAuthEmailRequiredException` → 422 EMAIL_REQUIRED
