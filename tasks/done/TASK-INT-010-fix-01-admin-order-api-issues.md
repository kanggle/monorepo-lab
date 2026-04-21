# TASK-INT-010-fix-01: 어드민 주문 API 이슈 수정

## Goal

TASK-INT-010 코드 리뷰에서 발견된 세 가지 이슈를 수정한다:
1. shippingAddress 필드명 컨트랙트 불일치 (`recipientName` vs `recipient`)
2. Out of Scope 기능인 `cancelOrder`가 어드민 API에 포함된 문제
3. AdminOrderController 컨트랙트 테스트 부재

## Scope

- `specs/contracts/http/order-api.md` — shippingAddress 필드명 확인 및 정합성 결정
- `apps/order-service/src/main/java/com/example/order/presentation/dto/AdminOrderDetailResponse.java` — ShippingAddressDetail 필드명 정렬
- `packages/types/src/order.ts` — ShippingAddress 타입 필드명 정렬
- `packages/api-client/src/services/admin-order-api.ts` — cancelOrder 제거
- `apps/admin-dashboard/src/features/order-management/api/order-api.ts` — cancelOrder 제거
- `apps/admin-dashboard/src/features/order-management/hooks/use-cancel-order.ts` — 삭제 또는 비활성화
- `apps/admin-dashboard/src/features/order-management/components/OrderDetail.tsx` — 취소 기능 UI 제거
- `apps/order-service/src/test/java/com/example/order/contract/AdminOrderApiContractTest.java` — 신규 작성

### In Scope

1. shippingAddress 필드명 통일: 컨트랙트(`specs/contracts/http/order-api.md`)가 최우선이므로, 컨트랙트의 `recipientName`을 `recipient`로 수정하거나 구현을 `recipientName`으로 변경하여 정합성을 맞춤
   - 단, 기존 일반 주문 API(`GET /api/orders/{orderId}`)가 이미 `recipient`를 사용하고 있으므로, 어드민 엔드포인트 컨트랙트도 `recipient`로 통일 (컨트랙트 수정)
2. 어드민 api-client에서 `cancelOrder` 제거 (Out of Scope)
3. admin-dashboard의 주문 취소 UI 및 훅 제거
4. AdminOrderController 대상 컨트랙트 테스트 추가:
   - `GET /api/admin/orders` 200 응답 필드 검증
   - `GET /api/admin/orders/{orderId}` 200 응답 필드 검증
   - 비어드민 역할 → 403 검증
   - 존재하지 않는 orderId → 404 검증

### Out of Scope

- AdminOrderController 외 기존 OrderController 변경
- 어드민 주문 상태 변경 API 추가
- 환불 처리 API

## Acceptance Criteria

- [ ] `specs/contracts/http/order-api.md` 어드민 엔드포인트 shippingAddress 필드명이 `recipient`로 통일되어 있다
- [ ] `AdminOrderDetailResponse.ShippingAddressDetail`의 필드명이 컨트랙트와 일치한다
- [ ] `packages/types/src/order.ts` `ShippingAddress.recipient` 필드명이 컨트랙트와 일치한다
- [ ] `createAdminOrderApi`에 `cancelOrder` 메서드가 없다
- [ ] admin-dashboard 주문 관리에서 취소 기능 UI가 제거되었다
- [ ] `AdminOrderApiContractTest`가 존재하고, 어드민 목록/상세 200 응답 필드와 403/404 에러 응답을 검증한다
- [ ] 기존 테스트가 통과한다

## Related Specs

- `specs/contracts/http/order-api.md`
- `specs/services/admin-dashboard/architecture.md`
- `specs/platform/testing-strategy.md`

## Related Contracts

- `specs/contracts/http/order-api.md`

## Edge Cases

- 컨트랙트 수정 시 일반 주문 API(`GET /api/orders/{orderId}`)의 shippingAddress 필드와 일치해야 한다
- cancelOrder 제거 후 admin-dashboard 빌드가 정상적으로 통과해야 한다

## Failure Scenarios

- cancelOrder 메서드가 여전히 존재하면 Out of Scope 위반
- 컨트랙트와 구현의 필드명이 여전히 불일치하면 컨트랙트 위반
