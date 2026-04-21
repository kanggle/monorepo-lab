# TASK-FE-004: api-client Payment API X-User-Id 헤더 누락 수정

## Goal
TASK-FE-002 리뷰에서 발견된 이슈를 수정한다.
`@repo/api-client`의 Payment API 호출 시 계약에서 요구하는 `X-User-Id` 헤더가 주입되지 않는 문제를 해결한다.

## Scope

### In Scope
- `packages/api-client/src/services/payment-api.ts`에 `X-User-Id` 헤더 주입 로직 추가
- Gateway가 자동 주입하지 않는 경우를 대비하여 클라이언트에서 명시적으로 전달

### Out of Scope
- Payment API 외 다른 서비스 API 수정
- 새로운 API 엔드포인트 추가

## Acceptance Criteria
- [ ] Payment API 호출 시 `X-User-Id` 헤더가 요청에 포함된다
- [ ] `X-User-Id` 값은 인증된 사용자의 ID를 사용한다
- [ ] 기존 API 클라이언트의 인터페이스 호환성이 유지된다

## Related Specs
- `specs/contracts/http/payment-api.md`
- `specs/platform/api-gateway-policy.md`

## Related Contracts
- `specs/contracts/http/payment-api.md`

## Edge Cases
- userId를 얻을 수 없는 경우 (토큰 미존재 등) 적절한 에러 처리
- Gateway가 이미 X-User-Id를 주입하는 환경에서 중복 헤더 처리

## Failure Scenarios
- userId 추출 실패 시 API 호출 전 에러 반환
