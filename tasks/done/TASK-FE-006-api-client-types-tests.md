# TASK-FE-006: @repo/api-client 및 @repo/types 테스트 추가

## Goal
TASK-FE-002 리뷰에서 발견된 테스트 부재 이슈를 수정한다.
`@repo/api-client`와 `@repo/types` 패키지에 적절한 테스트를 추가한다.

## Scope
- `packages/api-client/`: API 클라이언트 테스트
  - JWT 토큰 자동 주입 로직 테스트
  - 공개 경로(public path) 판별 테스트
  - API 에러 응답 파싱 테스트
  - 401 응답 시 토큰 갱신 및 재시도 테스트
  - 각 서비스별 API 함수의 HTTP 메서드 및 경로 검증
- `packages/types/`: 타입 컴파일 검증
  - 타입 정의가 HTTP 계약과 일치하는지 컴파일 타임 검증
- `ApiErrorResponse.timestamp` 필드를 스펙에 맞게 필수로 변경

## Acceptance Criteria
- `@repo/api-client`에 단위 테스트가 존재하고 통과한다
- JWT 토큰 주입, 에러 핸들링, 토큰 갱신 로직이 테스트된다
- 각 서비스 API 함수가 올바른 HTTP 메서드와 경로를 사용하는지 테스트된다
- `ApiErrorResponse.timestamp`가 필수 필드로 변경된다
- 기존 빌드가 깨지지 않는다

## Related Specs
- `specs/platform/testing-strategy.md`
- `specs/platform/error-handling.md`
- `specs/contracts/http/auth-api.md`
- `specs/contracts/http/product-api.md`
- `specs/contracts/http/order-api.md`
- `specs/contracts/http/search-api.md`
- `specs/contracts/http/payment-api.md`

## Related Contracts
- `specs/contracts/http/auth-api.md`
- `specs/contracts/http/product-api.md`
- `specs/contracts/http/order-api.md`
- `specs/contracts/http/search-api.md`
- `specs/contracts/http/payment-api.md`

## Edge Cases
- 네트워크 오류(응답 없음) 시 에러 타입 변환
- 토큰 갱신 중 동시 요청 처리
- 빈 응답 본문 처리

## Failure Scenarios
- 토큰 갱신 실패 시 원래 에러 반환 확인
- 잘못된 JSON 응답 시 파싱 에러 처리
