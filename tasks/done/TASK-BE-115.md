# TASK-BE-115: payment-service 토스페이먼츠 PG 연동 -- 결제 승인 API, PG 어댑터, DB 스키마 확장

## Goal

payment-service에 토스페이먼츠 PG를 연동한다. 기존 시뮬레이션 결제를 제거하고, 실제 PG를 통한 결제 승인(confirm) 및 환불(cancel) 흐름을 구현한다.

## Scope

### Backend (payment-service)

1. **DB 스키마 확장** -- Flyway 마이그레이션으로 PG 관련 컬럼 추가
   - payment_key VARCHAR(200) -- 토스 결제 키
   - payment_method VARCHAR(50) -- 결제 수단 (CARD, TRANSFER 등)
   - receipt_url VARCHAR(500) -- 영수증 URL

2. **도메인 모델 확장** -- Payment 애그리거트에 PG 필드 추가
   - paymentKey, paymentMethod, receiptUrl 필드 추가
   - confirm(paymentKey, paymentMethod, receiptUrl) 메서드 추가 (PENDING -> COMPLETED)
   - 기존 complete() 메서드는 confirm()으로 대체

3. **PaymentGatewayPort 추가** (아웃바운드 포트)
   - confirmPayment(paymentKey, orderId, amount) -> PG 승인 결과
   - cancelPayment(paymentKey, cancelReason) -> PG 환불 결과

4. **TossPaymentsAdapter 구현** (아웃바운드 어댑터)
   - 토스페이먼츠 Confirm API 호출 (POST /v1/payments/confirm)
   - 토스페이먼츠 Cancel API 호출 (POST /v1/payments/{paymentKey}/cancel)
   - RestClient 사용, Base64 인코딩 시크릿키 인증
   - 타임아웃, 에러 핸들링

5. **PaymentConfirmService 추가** (애플리케이션 서비스)
   - confirm 요청 수신 (paymentKey, orderId, amount)
   - PENDING 결제 조회 및 금액 검증
   - 소유권 검증 (userId)
   - PaymentGatewayPort.confirmPayment() 호출
   - 결제 상태 COMPLETED 전이 및 PG 필드 저장
   - PaymentCompleted 이벤트 발행

6. **PaymentProcessingService 수정**
   - OrderPlaced 이벤트 수신 시 PENDING 생성만 하고 자동 완료(complete) 제거

7. **PaymentRefundService 수정**
   - PaymentGatewayPort.cancelPayment() 호출 추가
   - paymentKey가 있는 COMPLETED 결제만 PG 환불 호출

8. **결제 승인 API 엔드포인트 추가**
   - POST /api/payments/confirm
   - Request: { paymentKey, orderId, amount }
   - X-User-Id 헤더 필수

9. **설정 추가**
   - application.yml: toss.payments.secret-key, toss.payments.base-url
   - application-standalone.yml: StandaloneConfig에 mock PaymentGatewayPort 제공

10. **PaymentDetailResponse 확장**
    - paymentMethod, paymentKey, receiptUrl 필드 추가

## Acceptance Criteria

- [ ] POST /api/payments/confirm 호출 시 토스 Confirm API를 통해 결제가 승인된다
- [ ] 금액 불일치 시 AMOUNT_MISMATCH 에러(400)가 반환된다
- [ ] 이미 완료된 결제에 confirm 시 PAYMENT_ALREADY_COMPLETED 에러(409)가 반환된다
- [ ] PG 승인 실패 시 PG_CONFIRM_FAILED 에러(502)가 반환되고 결제 상태는 FAILED가 된다
- [ ] OrderCancelled 이벤트 수신 시 COMPLETED 결제에 대해 토스 Cancel API를 호출한다
- [ ] PENDING 상태 결제의 환불은 PG 호출 없이 종료된다
- [ ] PaymentDetailResponse에 paymentMethod, paymentKey, receiptUrl이 포함된다
- [ ] standalone 프로필에서 mock PaymentGatewayPort로 동작한다
- [ ] 애플리케이션 서비스 단위 테스트가 작성된다
- [ ] TossPaymentsAdapter 통합 테스트가 작성된다 (WireMock)

## Related Specs

- `specs/features/payment-processing.md`
- `specs/services/payment-service/overview.md`
- `specs/services/payment-service/architecture.md`
- `specs/use-cases/payment-and-refund.md`

## Related Contracts

- `specs/contracts/http/payment-api.md`
- `specs/contracts/events/payment-events.md`

## Edge Cases

- 동일 orderId로 중복 confirm 요청 (멱등성 -- 이미 COMPLETED면 409)
- 토스 API 타임아웃 시 결제 상태 불확실 (PENDING 유지, 클라이언트 재시도 가능)
- paymentKey가 없는 COMPLETED 결제에 대한 환불 (레거시 시뮬레이션 데이터 -- PG 호출 건너뜀)

## Failure Scenarios

- 토스 Confirm API 5xx 에러 -- PG_CONFIRM_FAILED 반환, PENDING 유지
- 토스 Cancel API 실패 -- DLQ로 재시도
- DB 저장 실패 후 PG 승인 완료 -- 토스 측에서 자동 취소 (미수신 건 처리)
