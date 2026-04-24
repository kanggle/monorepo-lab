# TASK-BE-120-FIX-01: 네이버 OAuth 리뷰 이슈 수정

## Goal
TASK-BE-120 리뷰에서 발견된 이슈를 수정한다.

## Scope
- `apps/auth-service`

## Acceptance Criteria

### 1. OAuthCallbackProperties 구현체 분리
- [ ] `GoogleOAuthProperties`에서 `OAuthCallbackProperties` implements 및 `NaverOAuthProperties` 주입 제거
- [ ] 독립된 `OAuthCallbackPropertiesImpl` 클래스 생성 (Google/Naver redirect-uri + callback-allowlist 통합 관리)

### 2. NaverOAuthClient 보안 개선
- [ ] 토큰 API 호출 시 `client_secret`을 query param이 아닌 request body로 전송
- [ ] `state` 빈 문자열 파라미터 제거

### 3. 테스트 추가
- [ ] `NaverOAuthServiceTest` 단위 테스트 추가 (최소 8개 시나리오):
  - buildAuthorizationUrl 성공/실패
  - handleCallback: state 만료, 기존 사용자, 신규 사용자, API 오류, 이메일 없음, 비활성 사용자
- [ ] `OAuthControllerTest`에 Naver 엔드포인트 슬라이스 테스트 추가:
  - GET /api/auth/oauth/naver 성공
  - GET /api/auth/oauth/naver/callback 성공/실패 시나리오

## Related Specs
- `specs/services/auth-service/architecture.md`
- `specs/platform/testing-strategy.md`
- `specs/platform/security-rules.md`

## Related Contracts
- `specs/contracts/http/auth-api.md`

## Edge Cases
- NaverOAuthClient body 전송 방식 변경 시 네이버 API 호환성 확인 필요

## Failure Scenarios
- 기존 테스트가 깨지지 않아야 함
