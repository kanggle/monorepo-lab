# TASK-FE-011: web-store 주문 플로우 구현 — 주문 생성, 주문 내역, 주문 상세

## Goal
web-store에 주문 플로우를 구현한다.
장바구니에서 주문 생성(체크아웃), 주문 내역 조회, 주문 상세 조회, 주문 취소 기능을 추가한다.

## Scope
- `features/checkout/` — 체크아웃 피처 모듈 구현
  - `api/` — 주문 생성 API 래퍼
  - `model/` — 체크아웃 폼 타입
  - `ui/` — CheckoutForm (배송지 입력 + 주문 생성)
  - `index.ts` — 공개 API
- `entities/order/` — 주문 엔티티
  - `api/` — 주문 조회/취소 API 래퍼
  - `ui/` — OrderCard, OrderStatusBadge
  - `index.ts` — 공개 API
- `app/(store)/checkout/page.tsx` — 체크아웃 페이지
- `app/(store)/orders/page.tsx` — 주문 내역 페이지
- `app/(store)/orders/[id]/page.tsx` — 주문 상세 페이지
- 테스트 추가

## Acceptance Criteria
- 장바구니에서 체크아웃 페이지로 이동할 수 있다
- 배송지 정보(수령인, 전화번호, 우편번호, 주소1, 주소2)를 입력할 수 있다
- 주문 생성 성공 시 장바구니를 비우고 주문 상세 페이지로 이동한다
- 주문 내역 페이지에서 내 주문 목록을 조회할 수 있다
- 주문 상세 페이지에서 주문 정보(상품, 배송지, 상태)를 확인할 수 있다
- PENDING/CONFIRMED 상태의 주문을 취소할 수 있다
- 인증되지 않은 사용자는 로그인 페이지로 리다이렉트된다
- 기존 빌드가 깨지지 않는다

## Related Specs
- `specs/services/web-store/architecture.md`
- `specs/contracts/http/order-api.md`

## Related Contracts
- `specs/contracts/http/order-api.md`

## Edge Cases
- 빈 장바구니로 체크아웃 페이지 접근 시 장바구니로 리다이렉트
- 주문 생성 실패 시 에러 메시지 표시 및 장바구니 유지
- 취소 불가 상태(SHIPPED/DELIVERED/CANCELLED)에서 취소 버튼 비활성화
- 존재하지 않는 주문 ID 접근 시 에러 처리

## Failure Scenarios
- 네트워크 오류 시 적절한 에러 메시지
- 주문 생성 중 중복 클릭 방지
- 인증 만료 시 로그인 페이지로 이동
