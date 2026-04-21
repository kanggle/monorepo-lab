# TASK-BE-120: auth-service 네이버 OAuth 2.0 로그인 구현

## Goal
auth-service에 네이버 OAuth 2.0 Authorization Code Flow를 구현한다.
기존 Google OAuth와 동일한 패턴으로, 웹스토어가 사용하는 백엔드 OAuth 엔드포인트를 제공한다.

## Scope
- `apps/auth-service`
- 신규 파일: NaverOAuthPort, NaverOAuthClient, NaverOAuthProperties, NaverOAuthService
- 변경 파일: OAuthController, OAuthCallbackProperties, application.yml

## Acceptance Criteria
- [ ] `GET /api/auth/oauth/naver?callbackUrl={url}` 요청 시 네이버 인증 URL로 302 리다이렉트
- [ ] `callbackUrl`이 서버 허용 목록에 없으면 400 반환
- [ ] `GET /api/auth/oauth/naver/callback?code=...&state=...` 처리:
  - state를 Redis에서 조회해 callbackUrl 복원 (TTL 10분)
  - 네이버 Token API로 code 교환 → 프로필 API에서 email, name 추출
  - 이메일로 기존 사용자 조회 → 존재하면 해당 사용자로 토큰 발급
  - 존재하지 않으면 CUSTOMER 역할로 신규 사용자 생성 후 토큰 발급
  - 성공: `{callbackUrl}?accessToken={jwt}&refreshToken={token}` 으로 302 리다이렉트
  - 실패: `{callbackUrl}?error=oauth_failed` 으로 302 리다이렉트
- [ ] 환경변수: `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`
- [ ] `OAuthCallbackProperties` 인터페이스에 `naverRedirectUri()` 추가

## Related Specs
- `specs/services/auth-service/overview.md`
- `specs/services/auth-service/architecture.md`

## Related Contracts
- `specs/contracts/http/auth-api.md`

## Implementation Notes
- Google OAuth 구현 패턴(TASK-BE-114)과 동일하게 수동 RestClient 사용
- 네이버 OAuth 엔드포인트:
  - Authorization: `https://nid.naver.com/oauth2.0/authorize`
  - Token: `https://nid.naver.com/oauth2.0/token`
  - Profile: `https://openapi.naver.com/v1/nid/me`
- OAuthStateStore는 Google과 공유 (동일 Redis 키 패턴)
- 레이어 구조 준수: presentation → application → domain interface → infrastructure

## Edge Cases
- state 만료 또는 미존재 → callbackUrl 없으면 400 반환
- 네이버 API 오류 → callbackUrl로 error=oauth_failed 리다이렉트
- 이메일 없는 네이버 계정 → error=oauth_failed 처리
- 기존 이메일/패스워드 사용자가 같은 이메일로 네이버 로그인 → 기존 계정 그대로 사용

## Failure Scenarios
- `callbackUrl`이 허용 목록에 없음 → 400 VALIDATION_ERROR
- state 미존재/만료 → 400 INVALID_STATE
- 네이버 Token API 호출 실패 → callbackUrl?error=oauth_failed
- DB 저장 실패 → callbackUrl?error=oauth_failed
