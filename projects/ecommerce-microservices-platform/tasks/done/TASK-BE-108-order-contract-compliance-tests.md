# Task ID

TASK-BE-108

# Title

전 서비스 컨트랙트 준수 자동 검증 테스트 추가 — API 응답 스키마 및 이벤트 페이로드 스펙 일치 검증

# Status

review

# Owner

backend

# Task Tags

- code
- api
- event
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

전 서비스의 API 응답과 이벤트 페이로드가 스펙/컨트랙트 문서와 일치하는지 자동으로 검증하는 테스트를 추가한다.

현재 프로젝트에서 스펙 위반이 반복되는 근본 원인은 **스펙 변경 시 기존 코드의 적합성을 자동으로 검증하는 수단이 없기 때문**이다. 이벤트 직렬화 테스트와 컨트롤러 slice 테스트가 존재하지만, 스펙에 정의된 필드 목록/타입과 실제 응답을 대조하는 검증은 없다.

이 태스크는 다음을 보장한다:
- API 에러 응답이 각 서비스 컨트랙트에 정의된 형식만 포함하는지 검증
- API 성공 응답의 필드 구조가 스펙과 일치하는지 검증
- 이벤트 envelope과 payload의 필드 구조가 스펙과 일치하는지 검증
- 스펙에 없는 필드가 응답/이벤트에 포함되면 테스트가 실패

---

# Scope

## In Scope

### order-service
- API 응답 스키마 검증 (`specs/contracts/http/order-api.md` 기준)
  - `POST /api/orders` 응답: `{"orderId"}` 만 포함
  - `GET /api/orders` 응답: `{"content", "page", "size", "totalElements"}` 만 포함
  - `GET /api/orders/{orderId}` 응답: 스펙 정의 필드만 포함
  - `POST /api/orders/{orderId}/cancel` 응답: `{"orderId", "status"}` 만 포함
  - 에러 응답: `{"code", "message"}` 만 포함
- 이벤트 스키마 검증 (`specs/contracts/events/order-events.md` 기준)
  - envelope: `{"event_id", "event_type", "occurred_at", "source", "payload"}` 만 포함
  - OrderPlaced, OrderCancelled payload: 스펙 정의 필드만 포함

### auth-service
- API 응답 스키마 검증 (`specs/contracts/http/auth-api.md` 기준)
  - `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout` 응답
  - 에러 응답 형식 검증
- 이벤트 스키마 검증 (`specs/contracts/events/auth-events.md` 기준)
  - UserSignedUp, UserLoggedIn, UserLoggedOut, TokenRefreshed, LoginFailed, SessionLimitExceeded payload

### user-service
- API 응답 스키마 검증 (`specs/contracts/http/user-api.md` 기준)
  - 프로필 조회/수정, 주소 CRUD, 관리자 API 응답
  - 에러 응답 형식 검증
- 이벤트 스키마 검증 (`specs/contracts/events/user-events.md` 기준)
  - UserProfileUpdated, UserWithdrawn payload

### product-service
- API 응답 스키마 검증 (`specs/contracts/http/product-api.md` 기준)
  - 상품 조회/등록/수정/삭제, 재고 조정 API 응답
  - 에러 응답 형식 검증
- 이벤트 스키마 검증 (`specs/contracts/events/product-events.md` 기준)
  - ProductCreated, ProductUpdated, ProductDeleted, StockChanged payload

### payment-service
- API 응답 스키마 검증 (`specs/contracts/http/payment-api.md` 기준)
  - 결제 조회 API 응답
  - 에러 응답 형식 검증
- 이벤트 스키마 검증 (`specs/contracts/events/payment-events.md` 기준)
  - PaymentCompleted, PaymentRefunded payload

### search-service
- API 응답 스키마 검증 (`specs/contracts/http/search-api.md` 기준)
  - 검색 API 응답
  - 에러 응답 형식 검증

### 필드 초과 감지
- 모든 서비스에서 스펙에 없는 필드가 응답/이벤트에 포함되면 테스트 실패

## Out of Scope

- gateway-service (프록시 역할이므로 자체 응답 스키마 없음)
- batch-worker (외부 API/이벤트 발행 없음)
- 프론트엔드 서비스 (admin-dashboard, web-store)
- OpenAPI/Swagger 도입
- JSON Schema 파일 도입 (마크다운 스펙 기반 테스트로 충분)

---

# Acceptance Criteria

- [ ] 각 서비스의 API 에러 응답에 컨트랙트에 정의되지 않은 필드가 포함되면 테스트 실패
- [ ] 각 서비스의 API 성공 응답이 스펙에 정의된 필드만 포함하는지 검증
- [ ] 각 서비스의 발행 이벤트 JSON이 스펙의 envelope + payload 필드만 포함하는지 검증
- [ ] 스펙에 정의된 필수 필드가 누락되면 테스트 실패
- [ ] 테스트 이름/주석에 검증 근거가 되는 스펙 문서 경로가 명시됨
- [ ] 대상 서비스 6개 모두에 컨트랙트 테스트가 추가됨 (order, auth, user, product, payment, search)

---

# Related Specs

- `specs/platform/testing-strategy.md`
- `specs/services/order-service/architecture.md`
- `specs/services/auth-service/architecture.md`
- `specs/services/user-service/architecture.md`
- `specs/services/product-service/architecture.md`
- `specs/services/payment-service/architecture.md`
- `specs/services/search-service/architecture.md`

# Related Skills

- `.claude/skills/backend/contract-test.md`

---

# Related Contracts

- `specs/contracts/http/order-api.md`
- `specs/contracts/http/auth-api.md`
- `specs/contracts/http/user-api.md`
- `specs/contracts/http/product-api.md`
- `specs/contracts/http/payment-api.md`
- `specs/contracts/http/search-api.md`
- `specs/contracts/events/order-events.md`
- `specs/contracts/events/auth-events.md`
- `specs/contracts/events/user-events.md`
- `specs/contracts/events/product-events.md`
- `specs/contracts/events/payment-events.md`

---

# Target Service

- `order-service`
- `auth-service`
- `user-service`
- `product-service`
- `payment-service`
- `search-service`

---

# Architecture

Follow:

- 각 서비스의 `specs/services/<service>/architecture.md`

---

# Implementation Notes

- **API 응답 스키마 검증**: 컨트롤러 테스트에서 `MockMvc` 응답 JSON의 필드 목록을 추출하고, 스펙에 정의된 필드 집합과 `assertEquals`로 비교한다. Jackson의 `ObjectMapper.readTree()`로 JSON 필드명을 추출할 수 있다.
- **이벤트 스키마 검증**: 기존 직렬화 테스트를 확장하여, 직렬화된 JSON의 필드 집합이 스펙과 정확히 일치하는지 검증한다.
- **엄격한 필드 검증 패턴 예시**:
  ```java
  JsonNode node = objectMapper.readTree(responseBody);
  Set<String> actualFields = new HashSet<>();
  node.fieldNames().forEachRemaining(actualFields::add);
  assertEquals(Set.of("code", "message"), actualFields,
      "ErrorResponse must match specs/contracts/http/order-api.md error format");
  ```
- 테스트 클래스명: `{Service}ApiContractTest.java`, `{Service}EventContractTest.java`
- 중첩 객체(items, shippingAddress 등)의 필드도 재귀적으로 검증한다.
- 서비스 간 공통 검증 유틸리티(`ContractTestHelper`)를 각 서비스의 테스트 소스에 둔다. 공유 라이브러리로 추출하지 않는다 (shared-library-policy 준수).

---

# Edge Cases

- 응답에 `null` 값 필드가 포함/제외되는 경우 — Jackson의 `Include.NON_NULL` 설정에 따라 필드 자체가 빠질 수 있으므로, 필수 필드는 null이더라도 포함되는지 검증
- 페이지네이션 응답의 Spring Data 기본 필드(`totalPages`, `numberOfElements` 등) — 스펙에 정의되지 않은 필드는 제외되어야 함
- 서비스마다 에러 응답 형식이 다를 수 있음 — 각 서비스의 HTTP 컨트랙트에 정의된 형식을 기준으로 검증

---

# Failure Scenarios

- 테스트가 너무 엄격하여 정당한 변경(스펙 업데이트 후 필드 추가)마다 깨지는 경우 — 테스트에 스펙 문서 경로를 명시하여 스펙 변경 시 테스트도 함께 업데이트해야 함을 알린다
- Spring Data의 `Page` 직렬화가 스펙 외 필드를 자동 포함하는 경우 — 커스텀 직렬화 또는 DTO 변환으로 제어
- 특정 서비스에 컨트랙트에 정의되지 않은 내부 전용 엔드포인트가 있는 경우 — 컨트랙트에 정의된 엔드포인트만 검증 대상

---

# Test Requirements

- 컨트랙트 테스트: 각 서비스의 API 엔드포인트별 응답 필드 집합 검증 (성공/에러 모두)
- 컨트랙트 테스트: 각 서비스의 발행 이벤트별 JSON 필드 집합 검증 (envelope + payload)
- 테스트는 `*ContractTest.java` 네이밍 규칙을 따른다

---

# Definition of Done

- [ ] Implementation completed (6개 서비스 모두)
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
