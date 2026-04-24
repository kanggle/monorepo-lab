# TASK-BE-115-fix-01: TASK-BE-115에서 발견된 이슈 수정

## Goal

TASK-BE-115 (payment-service 토스페이먼츠 PG 연동) 리뷰에서 발견된 아래 이슈를 수정한다.

1. `GlobalExceptionHandler`에서 `UnauthorizedPaymentAccessException` 처리 시 에러 코드가 `UNAUTHORIZED`(401)로 설정되어 있으나 계약서(`specs/contracts/http/payment-api.md`)에서는 `ACCESS_DENIED`(403)을 요구한다.
2. `AMOUNT_MISMATCH`, `PAYMENT_ALREADY_COMPLETED`, `PG_CONFIRM_FAILED` 에러 코드가 `specs/platform/error-handling.md`에 등록되어 있지 않다.
3. `PaymentControllerTest` 및 `PaymentApiContractTest`가 `PaymentController`에 주입된 `PaymentProcessingService`를 `@MockitoBean`으로 선언하지 않아 ApplicationContext 로딩에 실패한다.

## Scope

### Backend (payment-service)

1. **GlobalExceptionHandler 수정**
   - `UnauthorizedPaymentAccessException` 핸들러에서 에러 코드를 `UNAUTHORIZED` → `ACCESS_DENIED`로 변경
   - HTTP 상태 코드는 기존 `403 FORBIDDEN` 유지

2. **specs/platform/error-handling.md 업데이트**
   - Payment 섹션에 다음 에러 코드 추가 (코드 사용 전 등록 규칙 준수):
     - `AMOUNT_MISMATCH` | 400 | Confirm amount does not match PENDING payment amount
     - `PAYMENT_ALREADY_COMPLETED` | 409 | Payment is not in PENDING status
     - `PG_CONFIRM_FAILED` | 502 | Toss Payments confirmation API returned an error
     - `ACCESS_DENIED` 코드는 Authorization 섹션에 이미 존재하므로 Payment 계약에서 재사용하는 것으로 문서화

3. **PaymentControllerTest 수정**
   - `@MockitoBean private PaymentProcessingService paymentProcessingService;` 추가

4. **PaymentApiContractTest 수정**
   - `@MockitoBean private PaymentProcessingService paymentProcessingService;` 추가

## Acceptance Criteria

- [ ] `POST /api/payments/confirm` 소유권 검증 실패 시 HTTP 403 + `ACCESS_DENIED` 에러 코드가 반환된다
- [ ] `GET /api/payments/orders/{orderId}` 소유권 검증 실패 시 HTTP 403 + `ACCESS_DENIED` 에러 코드가 반환된다
- [ ] `specs/platform/error-handling.md`에 `AMOUNT_MISMATCH`, `PAYMENT_ALREADY_COMPLETED`, `PG_CONFIRM_FAILED` 에러 코드가 등록된다
- [ ] `PaymentControllerTest` 전체 테스트가 통과한다
- [ ] `PaymentApiContractTest` 전체 테스트가 통과한다
- [ ] `./gradlew :apps:payment-service:test` 빌드가 성공한다

## Related Specs

- `specs/platform/error-handling.md`
- `specs/services/payment-service/architecture.md`

## Related Contracts

- `specs/contracts/http/payment-api.md`

## Edge Cases

- `ACCESS_DENIED` 에러 코드는 플랫폼 `error-handling.md`의 Authorization 섹션에 이미 정의되어 있으므로 중복 등록하지 않는다
- 테스트 수정 시 기존 테스트 로직(검증 항목)은 변경하지 않는다

## Failure Scenarios

- `PaymentProcessingService` mock 미추가 시 `@WebMvcTest`가 Bean 로딩에 실패하여 모든 테스트가 `IllegalStateException`으로 실패한다
