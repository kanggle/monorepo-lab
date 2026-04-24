# Use Case: 장바구니 및 주문

---

## UC-0: 장바구니 사용 (인증 필수)

### 액터

- 인증된 사용자 (Authenticated User)

### 사전조건

- 사용자가 로그인되어 있음

### 정상 흐름

1. 인증된 사용자가 상품 상세에서 "장바구니 담기"를 선택한다.
2. web-store가 클라이언트 상태(localStorage)에 상품을 추가한다.
3. 사용자는 `/cart` 경로에서 담은 상품 목록을 확인·수량 변경·제거할 수 있다.

### 대안 흐름

- 없음

### 예외 흐름

- **EF-1: 비로그인 상태에서 "장바구니 담기"** — 로그인 페이지로 리디렉트하며, 로그인 성공 후 원래 위치로 복귀한다. 클라이언트 상태에는 **아무것도 추가하지 않는다**.
- **EF-2: 비로그인 상태에서 `/cart` 접근** — 로그인 페이지로 리디렉트한다.
- **EF-3: 로그아웃 시** — 클라이언트의 장바구니 상태를 즉시 비운다 (localStorage 삭제 포함). 동일 브라우저에서 다른 계정으로 로그인하더라도 이전 카트는 상속되지 않는다.

### 관련 규칙

- 카트는 **서버에 저장하지 않는다** (현 단계). 기기 간 동기화가 필요해지면 별도 use case로 확장한다.
- 카트 표시 여부는 UI 전 영역에서 인증 상태에 **의존**해야 한다 (비로그인 상태의 뱃지 카운트·드로어·헤더 노출 금지).

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
