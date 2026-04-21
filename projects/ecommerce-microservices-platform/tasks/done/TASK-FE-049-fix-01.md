# TASK-FE-049-fix-01: TASK-FE-049 리뷰에서 발견된 문제 수정

## Goal

TASK-FE-049 리뷰에서 발견된 다음 문제들을 수정한다:
1. 오래된/잘못된 테스트 수정 (checkout-form, payment-api)
2. app/ 레이어에 위치한 비즈니스 로직을 features/ 레이어로 이동
3. CheckoutForm과 payment/page.tsx 간의 중복 결제 흐름 정리

## Scope

### Frontend (web-store)

1. **테스트 수정 (checkout-form.test.tsx)**
   - 주문 성공 후 `router.push('/orders/order-1')` 기대 제거 — 실제 구현은 Toss SDK를 직접 호출하므로 이 검증이 무효함
   - 중복 클릭 방지 테스트의 버튼 텍스트 `/주문 처리 중/` → `/결제 진행 중/` 으로 수정

2. **테스트 보강 (payment-api.test.ts)**
   - `confirmPayment` 함수에 대한 테스트 케이스 추가 (성공, 실패, 409 중복 결제 케이스)

3. **아키텍처 수정 — payment/page.tsx 비즈니스 로직 이동**
   - `app/(store)/checkout/payment/page.tsx` 에 위치한 Toss SDK 로드 및 결제 요청 로직을 `features/checkout/` 레이어로 이동
   - `features/checkout/ui/PaymentWidget.tsx` 컴포넌트 신규 생성 (Toss SDK 초기화, 결제 요청 UI 포함)
   - `features/checkout/model/use-toss-payment.ts` 훅 신규 생성 (Toss SDK 로딩 상태, 결제 요청 함수 포함)
   - `app/(store)/checkout/payment/page.tsx` 는 파라미터 파싱, 인증 체크, `PaymentWidget` 조합만 수행하도록 수정

4. **결제 흐름 중복 제거**
   - `CheckoutForm.tsx` 가 Toss SDK를 직접 초기화하고 `requestPayment`를 호출하는 방식은 task Scope에서 정의한 `/checkout/payment` 페이지 플로우와 중복됨
   - `CheckoutForm.tsx` 에서 Toss SDK 직접 호출 제거: 주문 생성 후 `/checkout/payment?orderId={orderId}&amount={amount}&orderName={orderName}` 으로 router.push 하도록 변경 (task Scope 4번 요구사항 준수)
   - `CheckoutForm.tsx` 에서 `tossRef`, Toss SDK `useEffect`, `loadTossPayments` import 제거

5. **결제 페이지 테스트 추가 (__tests__/payment-page.test.tsx)**
   - PaymentSuccessPage: confirm API 성공 시 `/checkout/complete` 로 이동 테스트
   - PaymentSuccessPage: confirm API 실패 시 에러 메시지 및 재시도 버튼 표시 테스트
   - PaymentFailPage: 에러 코드/메시지 표시 테스트
   - PaymentFailPage: orderId 있을 때 재시도 링크 표시 테스트

## Acceptance Criteria

- [ ] `checkout-form.test.tsx` 의 주문 성공 후 router.push 검증 테스트가 실제 구현 흐름(결제 페이지 이동)에 맞게 수정됨
- [ ] `checkout-form.test.tsx` 의 중복 클릭 방지 테스트가 실제 버튼 텍스트(`결제 진행 중...`)와 일치함
- [ ] `payment-api.test.ts` 에 `confirmPayment` 테스트 케이스 추가됨
- [ ] Toss SDK 로딩 및 결제 요청 로직이 `app/` 레이어에서 제거되어 `features/checkout/` 레이어에 위치함
- [ ] `CheckoutForm.tsx` 는 주문 생성 후 `/checkout/payment` 로 이동하며, Toss SDK를 직접 호출하지 않음
- [ ] `payment/page.tsx` 는 `PaymentWidget` 컴포넌트를 조합하는 역할만 수행함
- [ ] 결제 성공/실패 페이지에 대한 테스트가 존재함
- [ ] 모든 테스트가 통과함

## Related Specs

- `specs/features/payment-processing.md`
- `specs/use-cases/payment-and-refund.md`
- `specs/services/web-store/architecture.md`

## Related Contracts

- `specs/contracts/http/payment-api.md`

## Edge Cases

- CheckoutForm에서 주문 생성 성공 후 결제 페이지 이동 시 orderId, amount, orderName 파라미터가 모두 URL에 포함되어야 함
- PaymentWidget에서 Toss SDK 로딩 실패 시 에러 메시지를 표시해야 함
- 결제 페이지에서 orderId 또는 amount가 없는 경우 잘못된 접근 메시지를 표시해야 함

## Failure Scenarios

- Toss SDK 로딩 실패 → 에러 메시지 표시 (PaymentWidget에서 처리)
- confirm API 네트워크 에러 → 재시도 안내 (PaymentSuccessPage에서 처리)
- 테스트 모킹 실패 → 개별 테스트를 독립적으로 격리하여 방지
