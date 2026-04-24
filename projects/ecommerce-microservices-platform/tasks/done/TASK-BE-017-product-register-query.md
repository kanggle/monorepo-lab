# Task ID

TASK-BE-017

# Title

product-service 상품 등록 + 조회 API — POST /api/admin/products, GET /api/products, GET /api/products/{id}, ProductCreated 이벤트

# Status

review

# Owner

backend

# Task Tags

- code
- api
- event

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

상품 등록 및 조회 API를 구현한다. 등록 시 `ProductCreated` 이벤트를 발행하여 search-service가 색인할 수 있게 한다.

이 태스크 완료 후: 관리자가 상품을 등록할 수 있고, 사용자가 상품 목록과 상세를 조회할 수 있다.

---

# Scope

## In Scope

- `POST /api/admin/products` — 상품 등록 (admin 전용)
- `GET /api/products` — 상품 목록 조회 (categoryId, status 필터, 페이지네이션)
- `GET /api/products/{productId}` — 상품 상세 조회 (variants 포함)
- Application service: `RegisterProductService`, `QueryProductService`
- 이벤트 발행 인프라: `ProductEventPublisher` 인터페이스 + Spring ApplicationEventPublisher 구현체
- `ProductCreated` 이벤트 발행 (등록 성공 시)
- 요청 DTO 검증

## Out of Scope

- 상품 수정/삭제 (TASK-BE-018)
- 재고 조정 (TASK-BE-019)
- 카테고리 관리 API
- 외부 메시지 브로커 연동

---

# Acceptance Criteria

- [ ] `POST /api/admin/products` 성공 시 201과 `{ "id": "..." }` 반환
- [ ] 등록된 상품이 DB에 저장된다
- [ ] 등록 성공 시 `ProductCreated` 이벤트가 발행된다 (payload가 계약과 일치)
- [ ] `GET /api/products` 가 페이지네이션(`page`, `size`)과 필터(`categoryId`, `status`)를 지원한다
- [ ] `GET /api/products/{productId}` 가 variants 포함 상세를 반환한다
- [ ] 존재하지 않는 productId 조회 시 404 + `{ "code": "PRODUCT_NOT_FOUND" }` 반환
- [ ] request body 필수 필드 누락 시 400 반환
- [ ] `ProductEventPublisher` 인터페이스가 도메인 레이어에 위치한다
- [ ] Spring 구현체가 인프라 레이어에 위치한다
- [ ] 단위 테스트 및 통합 테스트가 추가된다
- [ ] 기존 모든 테스트가 통과한다

---

# Related Specs

- `specs/services/product-service/architecture.md`
- `specs/services/product-service/overview.md`
- `specs/platform/testing-strategy.md`
- `specs/platform/error-handling.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/testing-backend.md`
- `.claude/skills/backend/implementation-workflow.md`

---

# Related Contracts

- `specs/contracts/http/product-api.md`
- `specs/contracts/events/product-events.md`

---

# Target Service

- `product-service`

---

# Architecture

Follow:

- `specs/services/product-service/architecture.md`

계층 배치:
- Domain: `ProductEventPublisher` 인터페이스, `ProductCreated` 이벤트 레코드
- Application: `RegisterProductService`, `QueryProductService`, `RegisterProductCommand`
- Infrastructure: `SpringProductEventPublisher`, JPA 조회 구현
- Interface: `ProductController`, `AdminProductController`, 요청/응답 DTO

---

# Implementation Notes

### 이벤트 구조

auth-service의 `AuthEvent` 패턴을 따른다:

```java
public record ProductEvent(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    String source,
    Object payload
) {}
```

### 페이지네이션

Spring Data `Pageable`을 사용. 응답은 계약의 `content`, `page`, `size`, `totalElements` 구조를 따른다.

### 이벤트 발행 실패 격리

이벤트 발행 실패가 상품 등록 플로우를 차단하지 않도록 try-catch로 격리한다.

---

# Edge Cases

- variants 없이 등록 시도 → 도메인 레이어에서 예외 → 400 반환
- 존재하지 않는 categoryId로 등록 → 404 또는 400 (정책 결정 필요, 기본: 400)
- price가 0 또는 음수 → VO에서 예외 → 400 반환
- `GET /api/products`에서 size가 매우 큰 경우 → 최대 100으로 제한

---

# Failure Scenarios

- DB 저장 후 이벤트 발행 실패 → 상품은 등록됨, 이벤트 유실 로깅 (현재 단계 허용)
- DB 저장 실패 → 500 반환, 이벤트 미발행
- 동시 등록으로 동일 상품명 중복 → 허용 (상품명은 unique 제약 없음)

---

# Test Requirements

- 단위 테스트: `RegisterProductServiceTest` — 등록 성공, variant 없는 경우, 이벤트 발행 검증
- 단위 테스트: `QueryProductServiceTest` — 조회 성공, 없는 상품 예외
- 컨트롤러 슬라이스: `ProductControllerTest` — 요청 검증, 404 응답 형식
- 통합 테스트: `ProductRegisterQueryIntegrationTest` — 등록 후 목록/상세 조회 전체 흐름

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
