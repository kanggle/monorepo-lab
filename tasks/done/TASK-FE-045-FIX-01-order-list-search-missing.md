# TASK-FE-045-FIX-01: OrderList 주문번호 검색 기능 누락 수정

## Goal

TASK-FE-045 리뷰에서 발견된 이슈를 수정한다.
OrderList 컴포넌트에 주문번호 검색 필드가 구현되지 않았다. Acceptance Criteria "OrderList에 주문번호 검색 필드가 존재하고 동작한다"를 충족하도록 구현한다.
또한 `useChangeProductStatus` 훅에 대한 전용 테스트가 누락되어 있다 (Toast 성공/실패 동작 검증).

## Scope

- `apps/admin-dashboard/src/features/order-management/components/OrderList.tsx`
- `apps/admin-dashboard/src/features/order-management/hooks/use-orders.ts`
- `apps/admin-dashboard/src/features/order-management/api/order-api.ts` (필요 시 orderId 검색 파라미터 추가)
- `apps/admin-dashboard/src/__tests__/features/order-management/components/OrderList.test.tsx`
- `apps/admin-dashboard/src/__tests__/features/product-management/hooks/use-change-product-status.test.ts` (신규)

### In Scope

1. OrderList FilterBar에 주문번호 검색 입력 필드 추가
2. useOrders 훅에 orderId 필터 파라미터 지원 추가
3. 빈 값 검색 시 전체 목록 조회 동작 보장
4. use-change-product-status 훅 단위 테스트 추가 (성공 Toast, 실패 Toast, 중복 클릭 방지)
5. OrderList 테스트에 주문번호 검색 시나리오 추가

### Out of Scope

- API 컨트랙트 변경 (orderId 검색은 기존 getOrders query param 확장)
- 다른 컴포넌트 수정

## Acceptance Criteria

- [ ] OrderList에 주문번호 검색 입력 필드가 표시된다
- [ ] 주문번호를 입력하고 검색하면 해당 주문번호로 API가 호출된다
- [ ] 빈 값으로 검색하면 orderId 파라미터 없이 전체 목록이 조회된다
- [ ] use-change-product-status 성공 시 '상품 상태가 변경되었습니다.' Toast가 표시된다
- [ ] use-change-product-status 실패 시 에러 Toast가 표시된다
- [ ] 기존 테스트가 통과한다

## Related Specs

- `specs/services/admin-dashboard/architecture.md`
- `specs/contracts/http/order-api.md`

## Related Contracts

- `specs/contracts/http/order-api.md` — GET /api/admin/orders (orderId 필터 파라미터 확인)

## Edge Cases

- 주문번호 검색 시 빈 값을 전송하면 전체 목록이 조회되어야 한다
- 존재하지 않는 주문번호 검색 시 빈 결과가 표시된다

## Failure Scenarios

- API 호출 실패 시 기존 에러 처리 유지
