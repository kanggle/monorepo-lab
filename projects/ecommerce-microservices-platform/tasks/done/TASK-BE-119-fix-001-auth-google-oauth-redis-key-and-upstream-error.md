# TASK-BE-119-FIX-001: auth-service Google OAuth Redis 키 패턴 불일치 및 OAUTH_UPSTREAM_ERROR 미사용 수정

## Goal
TASK-BE-119 리뷰에서 발견된 Warning 이슈 두 건을 수정한다.

1. `specs/services/auth-service/redis-keys.md`의 OAuth state 키 패턴 예시가 실제 구현(`auth:oauth:state:{state}`)과 불일치하는 스펙 문서를 정정한다.
2. `OAUTH_UPSTREAM_ERROR`(502) 에러코드가 스펙에 등록되어 있으나 실제 사용되지 않는 문제를 해결한다: Google upstream 오류를 별도 예외로 구분하거나 핸들러에서 502로 응답한다.

## Scope
- `specs/services/auth-service/redis-keys.md` — OAuth state 키 패턴 예시를 `auth:oauth:state:{state}`로 수정
- `apps/auth-service/src/main/java/com/example/auth/application/exception/` — `OAuthUpstreamException` 신규 추가 (또는 `OAuthException`에 종류 구분 추가)
- `apps/auth-service/src/main/java/com/example/auth/application/service/GoogleOAuthService.java` — Google API 호출 실패 시 `OAuthUpstreamException` 던지도록 수정
- `apps/auth-service/src/main/java/com/example/auth/presentation/advice/GlobalExceptionHandler.java` — `OAuthUpstreamException` 핸들러 추가: 502 `OAUTH_UPSTREAM_ERROR` 반환
- `apps/auth-service/src/test/java/com/example/auth/` — 관련 테스트 추가/수정

## Acceptance Criteria
- [ ] `specs/services/auth-service/redis-keys.md`의 OAuth state 키 패턴 예시가 `auth:oauth:state:{state}` (namespace 포함)로 수정된다
- [ ] Google upstream 오류(API 호출 실패) 발생 시 `OAUTH_UPSTREAM_ERROR`(502)로 응답한다 (CallbackResult.failure 리다이렉트 방식은 유지하되 예외 구분이 명확해야 함)
- [ ] `GlobalExceptionHandler`에 upstream 오류 핸들러가 추가된다
- [ ] `GoogleOAuthServiceTest`에서 upstream 오류 시나리오의 예외 타입이 검증된다
- [ ] `OAuthControllerTest`에서 upstream 오류 시 응답이 검증된다

## Related Specs
- `specs/services/auth-service/architecture.md`
- `specs/platform/error-handling.md`
- `specs/services/auth-service/redis-keys.md`
- `specs/platform/naming-conventions.md`

## Related Contracts
- `specs/contracts/http/auth-api.md`

## Edge Cases
- `OAuthException`(상태 무효)과 `OAuthUpstreamException`(외부 API 실패)은 서로 다른 HTTP 상태로 응답해야 한다
- upstream 오류가 callback 흐름에서 발생할 때 callbackUrl이 알려진 경우 리다이렉트 방식(302 + error=oauth_failed)을 유지할 수 있다

## Failure Scenarios
- `OAuthUpstreamException`을 `OAuthException` 핸들러가 캐치해 400을 반환하는 경우 → 별도 핸들러로 분리하여 502 반환
- 스펙 문서의 키 패턴 예시가 실제 구현과 달라 혼란을 야기하는 경우 → 스펙을 실제 구현에 맞게 수정
