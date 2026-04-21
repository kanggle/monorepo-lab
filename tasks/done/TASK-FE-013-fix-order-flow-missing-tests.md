# TASK-FE-013: TASK-FE-011 주문 플로우 누락 테스트 추가

## Goal
TASK-FE-011 리뷰에서 발견된 누락 테스트를 추가한다.
주문 목록/상세/체크아웃 페이지, 주문 취소, API 래퍼에 대한 테스트가 없다.

## Scope
- 주문 목록 페이지 (`orders/page.tsx`) 테스트 — 로딩/에러/빈 상태, 주문 목록 렌더링, 페이지네이션
- 주문 상세 페이지 (`orders/[id]/page.tsx`) 테스트 — 주문 상세 표시, 취소 기능, 에러 처리
- 체크아웃 페이지 (`checkout/page.tsx`) 테스트 — 인증 리다이렉트, 빈 장바구니 리다이렉트
- 주문 취소 기능 테스트 — handleCancel 로직, 취소 버튼 활성화/비활성화
- entities/order/api (`order-api.ts`) 단위 테스트 — API 래퍼 함수
- 중복 클릭 방지 (`isSubmitting`) 동작 검증

## Acceptance Criteria
- 주문 목록 페이지의 로딩/에러/빈 상태/목록 렌더링 테스트가 존재한다
- 주문 상세 페이지의 상세 표시/취소/에러 처리 테스트가 존재한다
- 체크아웃 페이지의 인증 리다이렉트, 빈 장바구니 리다이렉트 테스트가 존재한다
- 주문 취소 버튼의 상태별 활성화/비활성화 테스트가 존재한다
- order-api.ts의 4개 함수(getOrders, getOrderDetail, placeOrder, cancelOrder) 단위 테스트가 존재한다
- 중복 클릭 방지 동작 테스트가 존재한다
- 모든 테스트가 통과한다

## Related Specs
- `specs/services/web-store/architecture.md`
- `specs/platform/testing-strategy.md`
- `specs/contracts/http/order-api.md`

## Related Contracts
- `specs/contracts/http/order-api.md`

## Edge Cases
- 네트워크 오류 시 에러 메시지 표시 테스트
- 인증 만료 시나리오 테스트
- 존재하지 않는 주문 ID 접근 테스트

## Failure Scenarios
- 테스트 환경에서 모킹이 올바르게 동작하지 않는 경우
- 비동기 상태 변화 테스트 시 타이밍 이슈
