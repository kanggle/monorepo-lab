# TASK-FE-049: web-store 토스페이먼츠 결제 UI -- SDK 위젯 연동 및 결제 콜백 처리

## Goal

web-store 결제 플로우에 토스페이먼츠 결제 위젯을 연동한다. 주문 생성 후 사용자가 결제 위젯을 통해 결제를 승인하고, 결제 확정 API를 호출하여 결제를 완료하는 전체 프론트엔드 흐름을 구현한다.

## Scope

### Frontend (web-store)

1. **토스페이먼츠 SDK 설치**
   - @tosspayments/tosspayments-sdk 패키지 추가

2. **결제 페이지 신규** (`/checkout/payment`)
   - 주문 생성 후 orderId와 함께 결제 페이지로 이동
   - 토스페이먼츠 결제 위젯 렌더링
   - 결제 수단 선택 (카드, 계좌이체 등)
   - 결제 요청 (widget.requestPayment)

3. **결제 성공/실패 콜백 페이지**
   - `/checkout/payment/success` -- paymentKey, orderId, amount 수신 -> POST /api/payments/confirm 호출
   - `/checkout/payment/fail` -- 에러 코드/메시지 표시, 재시도 링크 제공

4. **CheckoutForm 수정**
   - 주문 생성 후 /checkout/complete 대신 /checkout/payment?orderId={orderId}&amount={amount} 로 이동

5. **api-client 확장**
   - confirmPayment(paymentKey, orderId, amount) 함수 추가
   - PaymentConfirmResponse 타입 추가

6. **types 패키지 확장**
   - PaymentConfirmRequest, PaymentConfirmResponse 타입 추가

7. **환경변수**
   - NEXT_PUBLIC_TOSS_CLIENT_KEY -- 토스페이먼츠 클라이언트 키

## Acceptance Criteria

- [ ] 주문 생성 후 토스페이먼츠 결제 위젯이 표시된다
- [ ] 사용자가 결제 수단을 선택하고 결제를 승인할 수 있다
- [ ] 결제 성공 시 POST /api/payments/confirm이 호출되고 완료 페이지로 이동한다
- [ ] 결제 실패 시 에러 메시지와 재시도 옵션이 표시된다
- [ ] 결제 완료 후 주문 상세에서 결제 수단과 상태를 확인할 수 있다
- [ ] 토스 클라이언트 키는 환경변수로 관리된다

## Related Specs

- `specs/features/payment-processing.md`
- `specs/use-cases/payment-and-refund.md`

## Related Contracts

- `specs/contracts/http/payment-api.md`

## Edge Cases

- 결제 페이지에서 브라우저 뒤로 가기 -- orderId는 PENDING 상태 유지, 재결제 가능
- 결제 성공 후 confirm API 실패 -- 에러 표시, 새로고침으로 재시도 가능
- 중복 탭에서 동시 결제 시도 -- 첫 confirm만 성공, 두 번째는 409

## Failure Scenarios

- 토스 SDK 로딩 실패 -- 에러 메시지 표시
- confirm API 네트워크 에러 -- 재시도 안내
- orderId가 잘못된 경우 -- 404 에러 표시
