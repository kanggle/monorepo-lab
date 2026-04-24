# TASK-FE-008: api-client 테스트 보완 — 토큰 갱신 재시도 및 엣지 케이스

## Goal
TASK-FE-006 리뷰에서 발견된 테스트 누락 이슈를 수정한다.
토큰 갱신 후 재시도, 동시 401 큐잉, 실패 경로, 비정상 응답 처리 테스트를 추가한다.

## Scope
- `packages/api-client/src/__tests__/client.test.ts` 수정/추가
  - 토큰 갱신 후 원래 요청 재시도(retry) 동작 검증
  - 동시 401 요청 시 `refreshSubscribers` 큐잉 처리 검증
  - 토큰 갱신 실패 시 원래 에러 반환 검증
  - 잘못된 JSON 응답 시 에러 처리 검증
  - 빈 응답 본문 처리 검증

## Acceptance Criteria
- 토큰 갱신 성공 후 원래 요청이 새 토큰으로 재시도되는지 테스트한다
- 동시 401 발생 시 하나의 갱신만 실행되고 대기 요청이 모두 새 토큰을 받는지 테스트한다
- 갱신 실패 시 `onAuthError` 호출과 함께 원래 에러가 반환되는지 테스트한다
- 잘못된 JSON 응답 시 적절한 에러 처리가 되는지 테스트한다
- 빈 응답 본문 시 `NETWORK_ERROR` 폴백이 동작하는지 테스트한다
- 기존 테스트가 깨지지 않는다

## Related Specs
- `specs/platform/testing-strategy.md`
- `specs/platform/error-handling.md`

## Related Contracts
- `specs/contracts/http/auth-api.md` (refresh 엔드포인트)

## Edge Cases
- 토큰 갱신 중 추가 401 요청 3개 이상 동시 발생
- 갱신 토큰 자체가 만료된 경우
- 네트워크 오류로 갱신 요청 자체가 실패하는 경우
- 응답 본문이 null/undefined인 경우

## Failure Scenarios
- mock 구조 변경으로 기존 테스트 깨짐 → 기존 테스트 호환성 유지
- retry 테스트에서 무한 루프 발생 → isRefreshing 플래그 검증
