# TASK-BE-114: auth-service Google OAuth 2.0 로그인 구현

## Goal
auth-service에 Google OAuth 2.0 Authorization Code Flow를 구현한다.
웹스토어와 어드민 대시보드가 공통으로 사용하는 백엔드 OAuth 엔드포인트를 제공한다.

## Scope
- `apps/auth-service`
- DB 마이그레이션: `password_hash` nullable 허용 + `oauth_provider` 컬럼 추가

## Acceptance Criteria
- [ ] `GET /api/auth/oauth/google?callbackUrl={url}` 요청 시 Google 인증 URL로 302 리다이렉트
- [ ] `callbackUrl`이 서버 허용 목록에 없으면 400 반환
- [ ] `GET /api/auth/oauth/google/callback?code=...&state=...` 처리:
  - state를 Redis에서 조회해 callbackUrl 복원 (TTL 10분)
  - Google Token API로 code 교환 → ID 토큰에서 email, name 추출
  - 이메일로 기존 사용자 조회 → 존재하면 해당 사용자로 토큰 발급
  - 존재하지 않으면 CUSTOMER 역할로 신규 사용자 생성 후 토큰 발급
  - 성공: `{callbackUrl}?accessToken={jwt}&refreshToken={token}` 으로 302 리다이렉트
  - 실패: `{callbackUrl}?error=oauth_failed` 으로 302 리다이렉트
- [ ] DB 마이그레이션: `password_hash` NOT NULL 제약 제거, `oauth_provider VARCHAR(50)` 컬럼 추가 (nullable)
- [ ] 환경변수: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `OAUTH_CALLBACK_ALLOWLIST` (콤마 구분)
- [ ] 컨트롤러 테스트, 서비스 테스트 추가

## Related Specs
- `specs/services/auth-service/overview.md`
- `specs/services/auth-service/architecture.md`

## Related Contracts
- `specs/contracts/http/auth-api.md` — GET /api/auth/oauth/google, GET /api/auth/oauth/google/callback

## Implementation Notes
- Spring Security OAuth2 Client(`spring-boot-starter-oauth2-client`) 사용 금지. 기존 패턴 유지를 위해 수동 HTTP 클라이언트(`RestClient` 또는 `WebClient`) 사용.
- Google Token API 엔드포인트: `https://oauth2.googleapis.com/token`
- Google ID 토큰 검증: `jjwt` 또는 Google의 공개키로 서명 검증 (또는 `https://www.googleapis.com/oauth2/v3/userinfo` API 사용)
- state 값은 `SecureRandom`으로 생성한 UUID, Redis key: `oauth:state:{state}`, TTL 10분
- `callbackUrl` 허용 목록은 `OAUTH_CALLBACK_ALLOWLIST` 환경변수로 관리 (예: `http://localhost:3000/oauth/callback,http://localhost:3001/oauth/callback`)
- 레이어 구조 준수: presentation → application → domain interface → infrastructure

## Edge Cases
- state 만료 또는 미존재 → callbackUrl 없으면 500이 아닌 400 반환
- Google API 오류 → callbackUrl로 error=oauth_failed 리다이렉트
- 이메일 없는 Google 계정 → error=oauth_failed 처리
- 기존 이메일/패스워드 사용자가 같은 이메일로 Google 로그인 → 기존 계정 그대로 사용
- `UserJpaEntity.passwordHash` nullable 처리: 도메인 `User`의 `passwordHash`도 null 허용

## Failure Scenarios
- `callbackUrl`이 허용 목록에 없음 → 400 VALIDATION_ERROR
- state 미존재/만료 → 400 INVALID_STATE
- Google Token API 호출 실패 → callbackUrl?error=oauth_failed
- DB 저장 실패 → callbackUrl?error=oauth_failed
