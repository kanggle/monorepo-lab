# Use Case: 결제 및 환불

---

## UC-1: 주문 결제 (토스페이먼츠 PG 연동)

### 액터

- 인증된 사용자 (Authenticated User)
- 시스템 (order-service -> payment-service)
- 외부 시스템 (Toss Payments)

### 사전조건

- 주문이 생성되어 `OrderPlaced` 이벤트가 발행됨
- payment-service가 PENDING 결제 레코드를 생성함

### 정상 흐름

1. order-service가 `OrderPlaced` 이벤트를 발행한다.
2. payment-service가 이벤트를 수신하고 결제를 PENDING 상태로 생성한다.
3. 사용자가 web-store 결제 페이지에서 토스페이먼츠 결제 위젯을 통해 결제를 승인한다.
4. 토스페이먼츠가 paymentKey, orderId, amount와 함께 성공 URL로 리다이렉트한다.
5. web-store가 POST /api/payments/confirm 을 호출한다 (paymentKey, orderId, amount).
6. payment-service가 금액 일치 여부를 검증한다.
7. payment-service가 토스페이먼츠 Confirm API를 호출하여 결제를 확정한다.
8. 결제 상태를 COMPLETED로 변경하고 paymentKey, paymentMethod, receiptUrl을 저장한다.
9. payment-service가 `PaymentCompleted` 이벤트를 발행한다.
10. order-service가 이벤트를 수신하여 주문 상태를 CONFIRMED로 변경한다.

### 대안 흐름

- **AF-1: 결제 실패 (사용자 취소)** -- 사용자가 토스 결제 위젯에서 취소하면 fail URL로 리다이렉트되고, 결제는 PENDING 상태를 유지한다.
- **AF-2: PG 승인 실패** -- 토스 Confirm API가 에러를 반환하면 결제 상태를 FAILED로 변경하고, PG_CONFIRM_FAILED 에러를 클라이언트에 반환한다.
- **AF-3: 금액 불일치** -- confirm 요청의 금액이 PENDING 결제 금액과 다르면 AMOUNT_MISMATCH 에러를 반환한다.

### 예외 흐름

- **EF-1: 이벤트 수신 실패** -- `OrderPlaced` 이벤트 처리 실패 시 재시도 메커니즘을 통해 최종 일관성을 보장한다.

---

## UC-2: 주문 취소에 의한 환불

### 액터

- 시스템 (order-service -> payment-service)
- 외부 시스템 (Toss Payments)

### 사전조건

- 주문이 취소되어 `OrderCancelled` 이벤트가 발행됨
- 해당 주문의 결제가 COMPLETED 상태임

### 정상 흐름

1. order-service가 `OrderCancelled` 이벤트를 발행한다.
2. payment-service가 이벤트를 수신한다.
3. 해당 orderId의 결제 정보를 조회한다.
4. payment-service가 토스페이먼츠 Cancel API를 호출하여 환불을 처리한다 (paymentKey 사용).
5. 결제 상태를 REFUNDED로 변경한다.
6. payment-service가 `PaymentRefunded` 이벤트를 발행한다.
7. order-service가 이벤트를 수신하여 환불 완료를 기록한다.

### 대안 흐름

- **AF-1: 결제 미완료 상태 취소** -- 결제가 PENDING 또는 FAILED 상태에서 주문이 취소되면 PG 환불 없이 결제를 종료한다.
- **AF-2: PG 환불 실패** -- 토스 Cancel API가 에러를 반환하면 로그를 기록하고 DLQ를 통해 재시도한다.

### 예외 흐름

- **EF-1: 이벤트 수신 실패** -- `OrderCancelled` 이벤트 처리 실패 시 재시도 메커니즘을 통해 최종 일관성을 보장한다.

---

## UC-3: 결제 내역 조회

### 액터

- 인증된 사용자 (Authenticated User)

### 사전조건

- 사용자가 로그인되어 있음
- 해당 주문의 결제 정보가 존재함

### 정상 흐름

1. 사용자가 주문의 결제 정보를 확인한다.
2. 클라이언트가 GET /api/payments/orders/{orderId} 요청을 보낸다 (X-User-Id 헤더 포함).
3. payment-service가 소유권을 검증한다 (X-User-Id와 결제의 userId 일치 확인).
4. 결제 상세 정보를 반환한다 (paymentId, orderId, userId, amount, status, paymentMethod, paymentKey, receiptUrl, timestamps).

### 대안 흐름

- 없음

### 예외 흐름

- **EF-1: 결제 미존재** -- 해당 orderId의 결제 정보가 없으면 `PAYMENT_NOT_FOUND` 오류를 반환한다 (404).
- **EF-2: 소유권 불일치** -- X-User-Id가 결제 소유자와 다르면 `ACCESS_DENIED` 오류를 반환한다 (403).
- **EF-3: 잘못된 요청** -- 유효하지 않은 orderId 형식이면 `INVALID_PAYMENT_REQUEST` 오류를 반환한다 (400).

---

## Related Contracts
- HTTP: `specs/contracts/http/payment-api.md`, `specs/contracts/http/order-api.md`
- Events: `specs/contracts/events/payment-events.md`, `specs/contracts/events/order-events.md`
