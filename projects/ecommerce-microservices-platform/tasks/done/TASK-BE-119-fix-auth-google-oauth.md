# TASK-BE-119: TASK-BE-114 리뷰 이슈 수정 - auth-service Google OAuth

## Goal
TASK-BE-114 리뷰에서 발견된 Critical 및 Warning 이슈를 수정한다.

## Scope
- `apps/auth-service`
- `specs/platform/error-handling.md` — 에러코드 등록
- `specs/services/auth-service/redis-keys.md` — OAuth state 키 패턴 등록

## Acceptance Criteria
- [ ] Flyway 마이그레이션 버전 번호 충돌 해결: `V7__oauth_support.sql`을 `V8__oauth_support.sql`로 변경
- [ ] `specs/platform/error-handling.md`에 `INVALID_STATE` (400), `OAUTH_UPSTREAM_ERROR` (502) 에러코드 등록
- [ ] `GlobalExceptionHandler`에 `OAuthException` 핸들러 추가: `OAuthException` → 400 `INVALID_STATE` 반환
- [ ] `specs/services/auth-service/redis-keys.md`에 OAuth state 키 패턴 (`oauth:state:{state}`) 등록
- [ ] `OAuthController`에서 `GoogleOAuthProperties` 직접 의존 제거: callbackUrl 허용 목록 검증을 application 레이어(`GoogleOAuthService`)로 이동
- [ ] Google이 `error` 파라미터를 보낼 때 callbackUrl을 복원할 수 있는 경우 `{callbackUrl}?error=oauth_failed`로 302 리다이렉트 처리 (state가 있고 Redis에서 callbackUrl을 복원할 수 있는 경우)

## Related Specs
- `specs/services/auth-service/architecture.md`
- `specs/services/auth-service/overview.md`
- `specs/platform/error-handling.md`
- `specs/platform/naming-conventions.md`
- `specs/services/auth-service/redis-keys.md`

## Related Contracts
- `specs/contracts/http/auth-api.md` — GET /api/auth/oauth/google, GET /api/auth/oauth/google/callback

## Edge Cases
- `OAuthException` 발생 시 기존 일반 Exception 핸들러(500)가 아닌 400 `INVALID_STATE`로 처리되어야 함
- callbackUrl 검증 실패 시 application 레이어에서 예외를 던지고 컨트롤러에서 400 반환

## Failure Scenarios
- Flyway V7 충돌로 서비스 기동 불가 → V8으로 버전 변경
- state 만료 시 500 반환 → 400 `INVALID_STATE` 반환으로 수정
