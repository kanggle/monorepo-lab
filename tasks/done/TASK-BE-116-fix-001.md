# Task ID

TASK-BE-116-fix-001

# Title

payment-service GlobalExceptionHandler AmountMismatchException HTTP 상태 코드 422 → 400 수정

# Status

review

# Owner

backend

# Task Tags

- code
- api

---

# Goal

TASK-BE-116 구현 중 payment-service `GlobalExceptionHandler`에 `AmountMismatchException` 핸들러가 추가되었으나, HTTP 상태 코드가 `422 UNPROCESSABLE_ENTITY`로 잘못 설정되었다. `specs/platform/error-handling.md`는 `AMOUNT_MISMATCH` 오류를 **400 Bad Request**로 명시한다.

이 태스크 완료 시:
- `AmountMismatchException` 핸들러가 HTTP **400 Bad Request**를 반환한다.
- 기존 테스트에서 상태 코드 assertion이 있다면 400으로 업데이트된다.

---

# Scope

## In Scope

- `apps/payment-service/src/main/java/com/example/payment/adapter/in/rest/GlobalExceptionHandler.java`의 `handleAmountMismatch` 메서드 HTTP 상태 코드를 `UNPROCESSABLE_ENTITY` → `BAD_REQUEST` 수정
- 해당 변경 관련 테스트 케이스 상태 코드 assertion 수정 (필요한 경우)

## Out of Scope

- 다른 핸들러 수정
- 에러 코드(`AMOUNT_MISMATCH`) 변경
- payment-service 기타 로직 변경

---

# Acceptance Criteria

- [ ] `GlobalExceptionHandler.handleAmountMismatch`가 `HttpStatus.BAD_REQUEST`(400)를 반환한다.
- [ ] `specs/platform/error-handling.md`의 `AMOUNT_MISMATCH → 400` 명세와 일치한다.
- [ ] 관련 테스트가 모두 통과한다.

---

# Related Specs

- `specs/platform/error-handling.md` (§ Payment — AMOUNT_MISMATCH: 400)

# Related Contracts

- `specs/contracts/http/payment-api.md`

---

# Edge Cases

- `PaymentControllerTest`에 AmountMismatch 관련 테스트 케이스가 있다면 상태 코드 기대값을 400으로 수정한다.

---

# Failure Scenarios

- 수정 후 기존 테스트 중 422를 기대하는 케이스가 있으면 테스트 실패 → 해당 테스트도 400으로 함께 수정한다.

---

# Definition of Done

- [x] `handleAmountMismatch`가 `HttpStatus.BAD_REQUEST` 반환
- [x] 관련 테스트 통과
- [x] Ready for review

---

# Implementation Summary

## 변경 파일

- [apps/payment-service/src/main/java/com/example/payment/adapter/in/rest/GlobalExceptionHandler.java:48](apps/payment-service/src/main/java/com/example/payment/adapter/in/rest/GlobalExceptionHandler.java#L48) — `HttpStatus.UNPROCESSABLE_ENTITY` → `HttpStatus.BAD_REQUEST` (1-line 수정)

## 테스트 영향

`AMOUNT_MISMATCH` HTTP 상태 코드를 assert하는 기존 테스트는 없음. `PaymentConfirmServiceTest`가 `AmountMismatchException` 발생 자체만 검증하고 있어 영향 없음.

## 검증

- `./gradlew :apps:payment-service:test --tests PaymentControllerTest --tests PaymentConfirmServiceTest` 전체 통과
- `specs/platform/error-handling.md` L121: `AMOUNT_MISMATCH | 400 | Confirm amount does not match PENDING payment amount` 일치
- `specs/contracts/http/payment-api.md` L49: `400 | AMOUNT_MISMATCH | Confirm amount does not match PENDING payment amount` 일치
- `specs/use-cases/payment-and-refund.md` L35: AF-3 금액 불일치 시나리오 유지
