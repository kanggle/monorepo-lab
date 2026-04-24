# TASK-FE-012: TASK-FE-011 주문 플로우 구현 수정 — FSD 위반, 페이지네이션, 계약 불일치, 코드 품질

## Goal
TASK-FE-011 리뷰에서 발견된 구현 결함을 수정한다.
FSD 아키텍처 위반, 누락된 페이지네이션 UI, API 계약 불일치, 코드 품질 이슈를 해결한다.

## Scope
- `features/checkout/ui/CheckoutForm.tsx` — `features/cart` 직접 임포트 제거, app 레이어에서 props/context로 cart 데이터 주입 방식으로 변경
- `features/checkout/model/` — 체크아웃 폼 타입 정의 (태스크 scope 산출물 누락 보완)
- `features/checkout/index.ts` — model 공개 API 추가
- `app/(store)/checkout/page.tsx` — cart 데이터를 CheckoutForm에 주입하도록 수정
- `app/(store)/orders/page.tsx` — 페이지네이션 UI 추가 (page/size 변경)
- `app/(store)/orders/[id]/page.tsx` — updatedAt 표시, items key 개선, ORDER_NOT_FOUND 전용 에러 메시지
- `entities/order/api/order-api.ts` — getOrders 기본 size를 20으로 수정 (계약 일치)
- 에러 처리 시 `err as ApiErrorResponse` 대신 타입 가드 적용

## Acceptance Criteria
- `features/checkout`이 `features/cart`를 직접 임포트하지 않는다
- `features/checkout/model/` 디렉토리가 존재하고 체크아웃 폼 타입이 정의되어 있다
- 주문 내역 페이지에 페이지네이션 UI가 있고 page/size를 변경할 수 있다
- `getOrders()` 기본 size가 20이다 (계약 일치)
- 주문 상세 페이지에 `updatedAt`이 표시된다
- 주문 상세 items의 key가 `productId-variantId` 조합이다
- 존재하지 않는 주문 접근 시 "주문을 찾을 수 없습니다" 메시지가 표시된다
- 에러 캐스팅에 타입 가드가 적용되어 있다
- 기존 빌드가 깨지지 않는다

## Related Specs
- `specs/services/web-store/architecture.md`
- `specs/contracts/http/order-api.md`

## Related Contracts
- `specs/contracts/http/order-api.md`

## Edge Cases
- cart context/props 주입 시 기존 동작과 동일하게 유지
- 페이지네이션에서 마지막 페이지 초과 요청 시 빈 목록 표시
- 타입 가드 적용 후 알 수 없는 에러 형태에 대한 폴백 메시지 유지

## Failure Scenarios
- cart 주입 방식 변경 후 체크아웃 플로우가 정상 동작하지 않는 경우
- 페이지네이션 상태 변경 시 API 호출 실패
