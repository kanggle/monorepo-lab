# TASK-BE-121: OAuth 프로바이더 통합 리팩토링

## Goal
Google/Naver OAuth 구현의 코드 중복을 제거하고, 새로운 프로바이더 추가 시 최소 변경으로 확장 가능한 구조로 리팩토링한다.

## Scope
- `apps/auth-service`

## Acceptance Criteria

### 1. 도메인 레이어 통합
- [ ] `GoogleOAuthPort`, `NaverOAuthPort` → 공통 `OAuthPort` 인터페이스로 통합
- [ ] `OAuthPort`에 `provider()` 메서드 추가하여 프로바이더 식별
- [ ] `GoogleUserInfo`, `NaverUserInfo` → 공통 `OAuthUserInfo` 레코드로 통합
- [ ] `OAuthCallbackProperties`에서 프로바이더별 메서드(`googleRedirectUri`, `naverRedirectUri`) 제거 → `redirectUriFor(String provider)` 단일 메서드로 변경

### 2. 애플리케이션 레이어 통합
- [ ] `GoogleOAuthService`, `NaverOAuthService` → 단일 `OAuthService`로 통합
- [ ] 프로바이더별 `OAuthPort` 구현체를 `Map<String, OAuthPort>`로 주입받아 라우팅
- [ ] `CallbackResult` 레코드를 `application.dto` 패키지로 이동
- [ ] `catch (Exception e)` → `catch (RestClientException e)` 등 구체적 예외 타입으로 변경

### 3. 인프라 레이어 정리
- [ ] `OAuthCallbackPropertiesImpl`에서 `callbackAllowlist`를 `oauth.callback-allowlist`로 분리
- [ ] `GoogleOAuthClient`, `NaverOAuthClient`는 공통 `OAuthPort` 인터페이스를 각각 구현 (유지)

### 4. 프레젠테이션 레이어 통합
- [ ] `OAuthController`에서 프로바이더별 중복 메서드 제거
- [ ] `/{provider}`, `/{provider}/callback` 경로변수 방식으로 통합
- [ ] `OAuthService`에 프로바이더명을 전달하여 적합한 Port로 라우팅

### 5. 기존 테스트 업데이트
- [ ] `GoogleOAuthServiceTest`, `NaverOAuthServiceTest` → `OAuthServiceTest`로 통합
- [ ] `OAuthControllerTest`의 Google/Naver 테스트를 통합 구조에 맞게 수정
- [ ] 모든 기존 테스트 시나리오 유지

## Related Specs
- `specs/services/auth-service/architecture.md`
- `specs/platform/coding-rules.md`
- `specs/platform/testing-strategy.md`

## Related Contracts
- `specs/contracts/http/auth-api.md`

## Edge Cases
- 지원하지 않는 provider 요청 시 400 반환
- OAuthPort 구현체가 등록되지 않은 provider 요청 시 명확한 에러 메시지

## Failure Scenarios
- 기존 Google/Naver 로그인 동작이 깨지지 않아야 함
- 기존 테스트 시나리오가 모두 유지되어야 함
