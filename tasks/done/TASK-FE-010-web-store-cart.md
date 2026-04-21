# TASK-FE-010: web-store 장바구니 기능 구현 — 클라이언트 상태 관리 및 UI

## Goal
web-store에 장바구니 기능을 구현한다.
상품 추가/삭제/수량 변경, 장바구니 페이지, 장바구니 상태 관리를 추가한다.

## Scope
- `features/cart/` — 장바구니 피처 모듈 구현
  - `model/` — CartContext, useCart 훅, 타입 정의 (localStorage 영속화)
  - `ui/` — AddToCartButton, CartItemRow, CartSummary 컴포넌트
  - `lib/` — 합계 계산 로직
  - `index.ts` — 공개 API
- `widgets/ProductCardWithCart.tsx` — ProductCard + AddToCartButton 조합 위젯
- `app/(store)/cart/page.tsx` — 장바구니 페이지
- `app/providers.tsx` — CartProvider 추가
- 테스트 추가

## Acceptance Criteria
- 상품 상세 페이지에서 장바구니에 상품을 추가할 수 있다
- 장바구니 페이지에서 상품 수량을 변경할 수 있다
- 장바구니 페이지에서 상품을 삭제할 수 있다
- 장바구니 합계 금액이 정확히 계산된다
- 장바구니 상태가 localStorage에 영속화되어 새로고침 후에도 유지된다
- CartProvider가 앱 전체를 감싸고 useCart 훅으로 접근 가능하다
- 빈 장바구니 시 적절한 안내 메시지를 표시한다
- 기존 빌드가 깨지지 않는다

## Related Specs
- `specs/services/web-store/architecture.md`
- `specs/contracts/http/order-api.md` (PlaceOrderRequest.items 구조 참조)
- `specs/contracts/http/product-api.md` (ProductDetail, ProductVariant 참조)

## Related Contracts
- `specs/contracts/http/order-api.md` (OrderItem 타입)

## Edge Cases
- 동일 상품+옵션 추가 시 수량 증가 (중복 추가 방지)
- 수량 0 이하 입력 시 자동 삭제
- localStorage 파싱 실패 시 빈 장바구니로 초기화
- 로그아웃 시 장바구니 유지 여부 (유지 — 비로그인 사용자도 장바구니 사용 가능)

## Failure Scenarios
- localStorage 접근 불가 시 메모리 기반 폴백
- 잘못된 JSON이 저장된 경우 빈 장바구니로 복구
