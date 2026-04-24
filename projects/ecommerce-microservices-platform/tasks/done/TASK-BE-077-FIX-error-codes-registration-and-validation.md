# Task ID

TASK-BE-077-FIX-error-codes-registration-and-validation

# Title

promotion-service 에러 코드 등록 및 validation 에러 코드 수정

# Status

done

# Owner

backend

# Task Tags

- code
- api

---

# Goal

promotion-service 구현에서 사용하는 에러 코드를 `specs/platform/error-handling.md`에 등록하고, validation 에러 코드를 스펙에 맞게 수정한다.

`error-handling.md` 규칙: "Error codes must be registered in this document before use."

현재 promotion-service는 다음 에러 코드를 미등록 상태로 사용한다:
- `INVALID_PROMOTION_REQUEST`
- `PROMOTION_NOT_FOUND`
- `COUPON_NOT_FOUND`
- `PROMOTION_ALREADY_ENDED`
- `PROMOTION_HAS_ISSUED_COUPONS`
- `PROMOTION_NOT_ACTIVE`
- `COUPON_LIMIT_EXCEEDED`
- `COUPON_ALREADY_USED`
- `COUPON_EXPIRED`
- `COUPON_NOT_OWNED`

또한 `error-handling.md`는 validation 에러에 `VALIDATION_ERROR`를 사용하도록 명시하지만, `GlobalExceptionHandler`는 `MethodArgumentNotValidException`, `ConstraintViolationException` 모두에 `INVALID_PROMOTION_REQUEST`를 반환한다.

---

# Scope

## In Scope

- `specs/platform/error-handling.md`에 promotion-service 에러 코드 섹션 추가 (10개 코드)
- `GlobalExceptionHandler`의 `MethodArgumentNotValidException`, `ConstraintViolationException` 핸들러를 `VALIDATION_ERROR` 코드로 수정
- 변경된 에러 코드에 맞춰 `PromotionControllerTest`, `CouponControllerTest` 업데이트

## Out of Scope

- 도메인 로직 변경
- 새 API 추가

---

# Acceptance Criteria

- [ ] `specs/platform/error-handling.md`에 Promotion 섹션이 추가되고 10개 에러 코드가 등록됨
- [ ] `GlobalExceptionHandler`의 validation 예외 핸들러가 `VALIDATION_ERROR` 코드를 반환함
- [ ] 비즈니스 규칙 위반 예외(PromotionNotFoundException 등)는 도메인 전용 코드를 그대로 유지함
- [ ] 컨트롤러 테스트가 변경된 에러 코드를 검증함
- [ ] 기존 테스트가 모두 통과함

---

# Related Specs

- `specs/platform/error-handling.md`
- `specs/platform/coding-rules.md`
- `specs/services/promotion-service/architecture.md`

# Related Contracts

- `specs/contracts/http/promotion-api.md`

---

# Edge Cases

- validation 에러와 비즈니스 규칙 위반 에러의 코드가 혼동되지 않아야 함
- `promotion-api.md` 계약의 에러 코드 표기도 `VALIDATION_ERROR`로 일치시켜야 할 수 있음 (계약 변경이 필요하면 계약 먼저 수정)

---

# Failure Scenarios

- 에러 코드 등록 없이 구현만 변경하면 향후 동일 이슈 재발

