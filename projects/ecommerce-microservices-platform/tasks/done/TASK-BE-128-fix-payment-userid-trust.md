# Task ID

TASK-BE-128

# Title

[Security P0] payment-service POST /api/payments userId 요청 바디 신뢰 취약점 수정

# Status

review

# Owner

backend

# Task Tags

- code
- security
- api
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`POST /api/payments`는 현재 요청 바디의 `userId` 필드를 그대로 신뢰하여 결제를 생성한다.
게이트웨이가 `X-User-Id` 헤더를 주입하고 있음에도 이를 무시하므로,
공격자가 타인의 `orderId`를 알면 자신의 `userId`로 결제 소유권을 가로챌 수 있다.

수정 목표:
1. `PaymentController.createPayment`가 `userId`를 `X-User-Id` 헤더에서만 읽도록 변경한다.
2. `PaymentCreateRequest`에서 `userId` 필드를 제거하거나 무시한다.
3. 결제 생성 전 `orderId`가 해당 `userId`의 주문인지 검증한다 (order-service 연동 또는 `userId` ↔ `orderId` 소유권 체크).

---

# Scope

## In Scope

- `PaymentController.createPayment` 시그니처 변경: `@RequestHeader("X-User-Id") String userId` 추가, 바디의 `userId` 제거
- `PaymentCreateRequest` DTO에서 `userId` 필드 제거
- `PaymentProcessingService.processPayment`에서 orderId의 userId 소유권 검증 추가
  - order-service HTTP 클라이언트 호출 또는 `Payment` 생성 전 DB에서 기존 결제의 `orderId`-`userId` 쌍 확인
  - 불일치 시 `ORDER_ACCESS_DENIED` (403) 반환
- `specs/contracts/http/payment-api.md` 계약 갱신 (`userId` 필드 요청 바디에서 제거)
- 관련 단위/통합 테스트 수정 및 보안 검증 테스트 추가

## Out of Scope

- order-service 내부 ownership API 신규 구현 (기존 `GET /api/orders/{orderId}` 응답의 `userId` 필드를 활용)
- 결제 금액 검증 (별도 태스크로 분리 가능)
- 다른 결제 엔드포인트 변경

---

# Acceptance Criteria

- [ ] `POST /api/payments` 요청 바디에 `userId` 필드가 없어도 요청이 처리된다
- [ ] `POST /api/payments`는 `X-User-Id` 헤더의 값으로만 userId를 설정한다
- [ ] `X-User-Id` 헤더 없이 요청 시 400 또는 게이트웨이에서 401 반환 (게이트웨이가 미인증 요청을 차단하므로 서비스 레이어 도달 불가)
- [ ] 다른 유저의 `orderId`로 결제 생성 시도 시 403 반환
- [ ] 본인의 `orderId`로 결제 생성 시 정상 처리된다
- [ ] `PaymentCreateRequest` 에 `userId` 필드가 존재하지 않는다
- [ ] `specs/contracts/http/payment-api.md`에서 `createPayment` 요청 바디의 `userId` 필드가 제거된다
- [ ] 기존 결제 관련 통합 테스트가 전부 통과한다

---

# Related Specs

- `specs/platform/error-handling.md` (403 에러 코드)
- `specs/platform/coding-rules.md`
- `specs/platform/architecture.md`
- `specs/services/payment-service/architecture.md`

---

# Related Skills

- `.claude/skills/backend/spring-boot-service.md`

---

# Related Contracts

- `specs/contracts/http/payment-api.md` — **본 태스크에서 갱신 필요**: `POST /api/payments` 요청 바디에서 `userId` 제거

---

# Target Service

- `payment-service`

---

# Architecture

**변경 전:**
```java
@PostMapping
public ResponseEntity<?> createPayment(@RequestBody PaymentCreateRequest request) {
    // request.getUserId() 신뢰
    paymentProcessingService.processPayment(request.getOrderId(), request.getUserId(), request.getAmount());
}
```

**변경 후:**
```java
@PostMapping
public ResponseEntity<?> createPayment(
    @RequestHeader("X-User-Id") String userId,
    @RequestBody PaymentCreateRequest request) {
    // userId는 헤더에서만
    paymentProcessingService.processPayment(request.getOrderId(), userId, request.getAmount());
}
```

소유권 검증은 `PaymentProcessingService` 또는 별도 `PaymentAuthorizationService`에서:
```java
// order-service GET /api/orders/{orderId} 호출 후 응답의 userId 비교
// 또는 payment 테이블에서 orderId로 기존 레코드 조회 후 userId 불일치 체크
if (!order.getUserId().equals(userId)) {
    throw new OrderAccessDeniedException(orderId);
}
```

---

# Edge Cases

- `orderId`가 존재하지 않는 경우 → order-service 404 → `ORDER_NOT_FOUND` (404) 반환
- order-service 호출 실패(타임아웃/다운) → 결제 생성 거부 + `EXTERNAL_SERVICE_UNAVAILABLE` (503) 반환
- 이미 결제가 존재하는 `orderId`로 재시도 → 기존 중복 처리 로직 유지
- `X-User-Id` 헤더가 UUID 형식이 아닌 경우 → `@Valid` 또는 변환 실패 → 400

---

# Failure Scenarios

- order-service 소유권 확인 API 호출 실패 → 결제 생성 차단 (fail closed) — 403 또는 503
- DB 트랜잭션 실패 → 결제 미생성, 이벤트 미발행

---

# Test Requirements

- **단위 테스트**: `PaymentProcessingService` — 타인 orderId 접근 시 `OrderAccessDeniedException` 발생
- **통합 테스트**:
  - `X-User-Id` 헤더 없는 요청 → 400/401
  - 본인 orderId → 정상 처리
  - 타인 orderId → 403
- **계약 테스트**: `PaymentCreateRequest` 바디에 `userId` 포함 시 무시됨을 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated (`payment-api.md` — userId 필드 제거)
- [ ] Specs updated first if required
- [ ] Ready for review
