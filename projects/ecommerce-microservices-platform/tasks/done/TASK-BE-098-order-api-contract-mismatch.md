# Task ID

TASK-BE-098

# Title

order-service API 컨트랙트 불일치 수정 — POST 요청 필드 차이 및 GET 목록 status 필터 미구현

# Status

done

# Owner

backend

# Task Tags

- code
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

order-service HTTP API 구현과 컨트랙트 스펙 간의 불일치를 해소한다.

1. **POST /api/orders 요청 필드 차이**: 스펙에는 items에 `productId`, `variantId`, `quantity` 3개 필드만 정의되어 있으나, 구현체(`PlaceOrderRequest`)에는 `productName`, `optionName`, `unitPrice`가 추가로 존재한다. 스펙이 불완전한 것이므로 컨트랙트를 코드에 맞게 업데이트한다.
2. **GET /api/orders status 필터 미구현**: 스펙에 `status` 쿼리 파라미터가 정의되어 있으나 컨트롤러에 구현되지 않았다. 컨트롤러와 리포지토리에 status 필터를 구현한다.

---

# Scope

## In Scope

- `specs/contracts/http/order-api.md` POST /api/orders 요청 필드 업데이트 (`productName`, `optionName`, `unitPrice` 추가)
- `OrderController.getOrders()`에 `status` 쿼리 파라미터 추가
- `OrderRepository`에 status 필터링 쿼리 추가 (userId + status + pageable)
- `OrderJpaRepository`에 대응하는 JPA 쿼리 메서드 추가
- `OrderRepositoryImpl`에 위임 메서드 추가
- `OrderQueryService`에 status 파라미터 전달
- 기존 테스트 수정 및 신규 테스트 추가

## Out of Scope

- 다른 서비스의 API 컨트랙트 변경
- 이벤트 컨트랙트 변경
- 프론트엔드 연동 수정

---

# Acceptance Criteria

- [ ] `specs/contracts/http/order-api.md` POST /api/orders 요청 필드가 구현체와 일치한다
- [ ] GET /api/orders에 `status` 쿼리 파라미터(optional)를 지원한다
- [ ] status 미지정 시 기존과 동일하게 전체 주문을 반환한다
- [ ] status 지정 시 해당 상태의 주문만 필터링하여 반환한다
- [ ] 유효하지 않은 status 값에 대해 400 응답을 반환한다
- [ ] 단위 테스트 및 통합 테스트가 추가된다

---

# Related Specs

- `specs/services/order-service/overview.md`
- `specs/services/order-service/architecture.md`

# Related Skills

- `.claude/skills/backend/spring-data-jpa.md`
- `.claude/skills/backend/controller-validation.md`

---

# Related Contracts

- `specs/contracts/http/order-api.md`

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`

---

# Implementation Notes

- 컨트랙트 변경을 먼저 수행한 후 코드를 수정한다 (Contract Rule).
- status 필터는 `OrderStatus` enum 값으로 변환하며, 유효하지 않은 값은 `MethodArgumentTypeMismatchException` 또는 커스텀 검증으로 400 처리한다.
- `OrderRepository` 인터페이스에 `findByUserIdAndStatus(String userId, OrderStatus status, Pageable pageable)` 메서드를 추가한다.

---

# Edge Cases

- status 파라미터가 빈 문자열인 경우 — 전체 조회로 처리
- status 파라미터가 유효하지 않은 enum 값인 경우 — 400 에러
- status 파라미터와 페이지네이션 조합 시 결과가 0건인 경우 — 빈 페이지 반환

---

# Failure Scenarios

- 잘못된 status 값 전달 시 적절한 에러 응답 반환
- DB 쿼리 실패 시 500 에러 반환

---

# Test Requirements

- 컨트롤러 단위 테스트: status 파라미터 유무에 따른 분기
- 서비스 단위 테스트: status 필터 쿼리 위임 확인
- 통합 테스트: status 필터링 실제 DB 조회 검증

---

# Definition of Done

- [ ] 컨트랙트 업데이트 완료
- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
