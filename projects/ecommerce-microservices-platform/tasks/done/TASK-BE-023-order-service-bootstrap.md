# Task ID

TASK-BE-023

# Title

order-service 부트스트랩 — 프로젝트 구조, DB 스키마, 도메인 모델

# Status

ready

# Owner

backend

# Task Tags

- code

---

# Goal

order-service의 기반 구조를 구축한다.
Spring Boot 프로젝트 생성, DDD 패키지 구조 설정, JPA 기반 도메인 모델 및 DB 스키마 정의, 기본 설정 파일 작성을 완료한다.
이 태스크 완료 후 주문 생성/조회/취소 기능 구현이 가능한 상태여야 한다.

---

# Scope

## In Scope

- Gradle 멀티모듈 등록 (`apps/order-service`)
- Spring Boot 프로젝트 구조 (DDD: presentation / application / domain / infrastructure)
- Order 애그리거트 도메인 모델: `Order`, `OrderItem`, `OrderStatus`, `ShippingAddress`
- JPA 엔티티 및 리포지토리 인터페이스
- DB 스키마 (Flyway 마이그레이션 또는 `schema.sql`)
- `application.yml` 기본 설정 (포트 8086, DB, JPA)
- `OrderRepository` 도메인 포트 인터페이스
- `GlobalExceptionHandler` 기본 구조
- 단위 테스트: Order 애그리거트 불변식 검증

## Out of Scope

- HTTP API 엔드포인트 구현
- 이벤트 발행
- product-service 연동

---

# Acceptance Criteria

- [ ] `apps/order-service`가 Gradle 빌드에서 독립적으로 컴파일된다
- [ ] Order 애그리거트가 PENDING 상태로 생성된다
- [ ] OrderItem은 quantity > 0 조건을 도메인에서 강제한다
- [ ] OrderStatus 전이 규칙이 도메인 모델에 정의된다 (cancel: PENDING/CONFIRMED만 허용)
- [ ] JPA 엔티티가 `orders`, `order_items` 테이블에 매핑된다
- [ ] `OrderRepository` 인터페이스가 domain 패키지에 정의된다
- [ ] 단위 테스트가 주요 도메인 불변식을 검증한다

---

# Related Specs

- `specs/platform/architecture.md`
- `specs/platform/coding-rules.md`
- `specs/platform/testing-strategy.md`
- `specs/services/order-service/architecture.md`
- `specs/services/order-service/overview.md`
- `specs/platform/error-handling.md`

# Related Skills

- `.claude/skills/backend/implementation-workflow.md`
- `.claude/skills/database/schema-change-workflow.md`
- `.claude/skills/backend/exception-handling.md`

---

# Related Contracts

- `specs/contracts/http/order-api.md`
- `specs/contracts/events/order-events.md`

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`

DDD 스타일: presentation / application / domain / infrastructure 패키지 분리.
도메인 레이어는 프레임워크 의존 없이 순수 Java로 작성한다.

---

# Implementation Notes

- Order 애그리거트 루트: `orderId(UUID)`, `userId`, `items`, `status`, `totalPrice`, `shippingAddress`, `createdAt`, `updatedAt`
- OrderItem 값 객체: `productId`, `variantId`, `productName`, `optionName`, `quantity`, `unitPrice`
- ShippingAddress 값 객체: `recipient`, `phone`, `zipCode`, `address1`, `address2`
- OrderStatus: `PENDING`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED`
- `totalPrice`는 items의 `unitPrice * quantity` 합산으로 계산
- 서버 포트: 8086

---

# Edge Cases

- items가 비어있으면 Order 생성 불가
- quantity가 0 이하이면 OrderItem 생성 불가
- SHIPPED/DELIVERED 상태에서는 취소 불가 (도메인 예외)

---

# Failure Scenarios

- DB 연결 실패 시 서비스 기동 실패 (의도된 동작)
- Flyway 마이그레이션 실패 시 기동 중단

---

# Test Requirements

- Order 애그리거트 단위 테스트 (MockitoExtension 없이 순수 Java)
  - 생성 성공 케이스
  - items 비어있을 때 예외
  - 취소 가능 상태 / 불가 상태 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
