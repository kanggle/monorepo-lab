# Use Case: 장바구니 및 주문

---

## UC-0: 장바구니 사용 (로그인 없이도 가능)

> TASK-FE-102 (2026-09-29, 소유자 결정 «로그인 시 이어 가기»): 비로그인 장바구니를 허용한다. 이전 v1 규칙(«인증 필수», EF-1·EF-2)은 이 결정으로 대체됐다. **주문(`/checkout`)은 여전히 로그인이 필요하다**(UC-1).

### 액터

- 방문자(비로그인) 또는 인증된 사용자

### 사전조건

- 없음

### 정상 흐름

1. 방문자가 상품 상세에서 "장바구니 담기"를 선택한다.
2. web-store가 클라이언트 상태(localStorage)에 상품을 추가한다 — 비로그인이면 **비로그인 장바구니**, 로그인 상태면 **계정 장바구니**.
3. 방문자는 `/cart` 경로에서 담은 상품 목록을 확인·수량 변경·제거할 수 있다.

### 대안 흐름

- **AF-1: 비로그인 장바구니를 가진 채 로그인** — 로그인 직후 비로그인 장바구니를 계정 장바구니에 **합친다**(같은 상품·옵션이면 수량을 더한다). 합친 뒤 비로그인 장바구니는 비운다.
- **AF-2: 비로그인 상태에서 "주문하기"** — `/checkout` 은 로그인이 필요하므로 로그인 페이지로 가고, 로그인 후 주문 화면으로 돌아온다. 담은 상품은 AF-1 로 이어진다.

### 예외 흐름

- ~~EF-1·EF-2~~ — TASK-FE-102 로 폐지(위 인용).
- **EF-3: 로그아웃 시** — **계정 장바구니**를 즉시 비운다 (localStorage 삭제 포함). 동일 브라우저에서 다른 계정으로 로그인하더라도 이전 계정의 카트는 상속되지 않는다. 🔴 비로그인으로 페이지가 열릴 때도 계정 장바구니는 지운다 — 세션이 만료된 채 남은 이전 사용자의 카트가 다음 방문자의 «비로그인 장바구니»로 보이면 안 된다. 그래서 두 장바구니는 **저장 위치(localStorage 키)가 다르다**.

### 관련 규칙

- 카트는 **서버에 저장하지 않는다** (현 단계). 기기 간 동기화가 필요해지면 별도 use case로 확장한다.
- 카트(아이콘·뱃지·`/cart`)는 비로그인 방문자에게도 보인다. 무엇을 보여 주느냐(비로그인 장바구니 / 계정 장바구니)는 인증 상태가 정한다.

---

## UC-1: 주문 생성

### 액터

- 인증된 사용자 (Authenticated User)

### 사전조건

- 사용자가 로그인되어 있음
- 주문할 상품과 variant가 존재하며 재고가 충분함

### 정상 흐름

1. 사용자가 주문할 상품 정보(productId, variantId, productName, quantity, unitPrice, 선택적으로 optionName)와 배송지 주소를 입력한다.
2. 클라이언트가 POST /api/orders 요청을 보낸다.
3. order-service가 주문을 PENDING 상태로 생성한다.
4. order-service가 `OrderPlaced` 이벤트를 발행한다 (orderId, userId, totalPrice, items, shippingAddress).
5. product-service가 `StockChanged` (reason: ORDER_RESERVED) 이벤트로 재고를 차감한다.
6. payment-service가 `OrderPlaced` 이벤트를 수신하여 결제를 처리한다.
7. 결제 완료 시 payment-service가 `PaymentCompleted` 이벤트를 발행한다.
8. order-service가 `PaymentCompleted` 이벤트를 수신하여 주문 상태를 CONFIRMED로 변경한다.
9. 시스템이 orderId를 포함한 201 응답을 반환한다.

### 대안 흐름

- **AF-1: 결제 실패** — payment-service에서 결제가 실패하면 `PaymentFailed` 이벤트를 발행하고, order-service가 이를 수신하여 주문 상태를 CANCELLED로 변경한다.

### 예외 흐름

- **EF-1: 입력값 오류** — 필수 필드 누락 시 `INVALID_ORDER_REQUEST` 오류를 반환한다 (400).
- **EF-2: 미인증** — 인증 토큰이 없거나 유효하지 않으면 `UNAUTHORIZED` 오류를 반환한다 (401).

---

## UC-2: 주문 목록 조회

### 액터

- 인증된 사용자 (Authenticated User)

### 사전조건

- 사용자가 로그인되어 있음

### 정상 흐름

1. 사용자가 주문 내역 페이지에 접근한다.
2. 클라이언트가 GET /api/orders 요청을 보낸다.
3. order-service가 해당 사용자의 주문 목록을 페이지네이션하여 반환한다.

### 대안 흐름

- **AF-1: 상태 필터** — status 파라미터로 특정 상태(PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED)의 주문만 조회할 수 있다.
- **AF-2: 주문 없음** — 주문 내역이 없으면 빈 목록을 반환한다.

### 예외 흐름

- **EF-1: 미인증** — 인증 토큰이 없거나 유효하지 않으면 `UNAUTHORIZED` 오류를 반환한다 (401).

---

## UC-3: 주문 상세 조회

### 액터

- 인증된 사용자 (Authenticated User)

### 사전조건

- 사용자가 로그인되어 있음
- 해당 주문이 존재함

### 정상 흐름

1. 사용자가 주문 목록에서 특정 주문을 선택한다.
2. 클라이언트가 GET /api/orders/{orderId} 요청을 보낸다.
3. order-service가 소유권을 검증한다 (주문 소유자만 조회 가능).
4. 주문 상세 정보를 반환한다.

### 대안 흐름

- 없음

### 예외 흐름

- **EF-1: 주문 미존재** — orderId에 해당하는 주문이 없으면 `ORDER_NOT_FOUND` 오류를 반환한다 (404).
- **EF-2: 소유권 불일치** — 다른 사용자의 주문을 조회하려 하면 `ACCESS_DENIED` 오류를 반환한다 (403).

---

## UC-4: 주문 취소

### 액터

- 인증된 사용자 (Authenticated User)

### 사전조건

- 사용자가 로그인되어 있음
- 해당 주문이 PENDING 또는 CONFIRMED 상태임

### 정상 흐름

1. 사용자가 주문 상세에서 취소를 요청한다.
2. 클라이언트가 POST /api/orders/{orderId}/cancel 요청을 보낸다.
3. order-service가 소유권을 검증한다.
4. order-service가 주문 상태가 취소 가능한지 확인한다 (PENDING 또는 CONFIRMED).
5. 주문 상태를 CANCELLED로 변경한다.
6. order-service가 `OrderCancelled` 이벤트를 발행한다.
7. payment-service가 이벤트를 수신하여 환불을 처리한다.
8. product-service가 `StockChanged` (reason: ORDER_CANCELLED) 이벤트로 재고를 복원한다.
9. 시스템이 orderId, status(CANCELLED)를 포함한 200 응답을 반환한다.

### 대안 흐름

- 없음

### 예외 흐름

- **EF-1: 취소 불가 상태** — SHIPPED 또는 DELIVERED 상태의 주문은 취소할 수 없으며 `ORDER_CANNOT_BE_CANCELLED` 오류를 반환한다 (422).
- **EF-2: 주문 미존재** — orderId에 해당하는 주문이 없으면 `ORDER_NOT_FOUND` 오류를 반환한다 (404).
- **EF-3: 소유권 불일치** — 다른 사용자의 주문을 취소하려 하면 `ACCESS_DENIED` 오류를 반환한다 (403).

---

## UC-5: 회원 탈퇴 시 주문 자동 취소

### 액터

- 시스템 (user-service → order-service)

### 사전조건

- 사용자가 회원 탈퇴를 완료함

### 정상 흐름

1. user-service가 `UserWithdrawn` 이벤트를 발행한다.
2. order-service가 이벤트를 수신한다.
3. 해당 사용자의 취소 가능한 주문(PENDING, CONFIRMED)을 모두 CANCELLED로 변경한다.
4. 각 취소된 주문에 대해 `OrderCancelled` 이벤트를 발행한다.

### 대안 흐름

- **AF-1: 취소 가능 주문 없음** — 해당 사용자의 미완료 주문이 없으면 별도 처리 없이 종료한다.

### 예외 흐름

- **EF-1: 이벤트 처리 실패** — 이벤트 수신 실패 시 재시도 메커니즘을 통해 최종 일관성을 보장한다.

---

## Related Contracts
- HTTP: `specs/contracts/http/order-api.md`
- Events: `specs/contracts/events/order-events.md`, `specs/contracts/events/user-events.md`
