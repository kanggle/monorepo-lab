# Task ID

TASK-BE-082

# Title

auth-service, order-service, user-service 도메인 모델 프레임워크 의존성 분리 — JPA 애노테이션 제거 및 JpaEntity 분리

# Status

in-progress

# Owner

backend

# Task Tags

- code
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

TASK-BE-072에서 payment-service와 batch-worker의 도메인 모델 JPA 분리를 완료했으나, auth-service, order-service, user-service의 도메인 모델에 여전히 JPA 애노테이션(@Entity, @Table, @Column)이 남아 있다. 각 서비스의 아키텍처 스펙(Layered/DDD)에 따라 도메인 모델을 순수 POJO로 전환하고, 인프라 계층에 별도 JpaEntity + Mapper를 생성한다.

---

# Scope

## In Scope

- auth-service: `User` 도메인 엔티티에서 JPA 애노테이션 제거, `UserJpaEntity` + `UserJpaMapper` 생성
- order-service: `Order` 도메인 모델에서 JPA 애노테이션 제거, `OrderJpaEntity` + `OrderJpaMapper` 생성
- user-service: `UserProfile`, `Address` 도메인 모델에서 JPA 애노테이션 제거, `UserProfileJpaEntity`, `AddressJpaEntity` + 각 Mapper 생성
- 각 서비스의 Repository 구현체가 JpaEntity를 사용하도록 변경

## Out of Scope

- product-service, search-service (이미 JpaEntity 분리 완료)
- payment-service, batch-worker (TASK-BE-072에서 완료)
- 도메인 로직 변경
- 새로운 기능 추가

---

# Acceptance Criteria

- [ ] auth-service `User` 도메인 클래스에 JPA import가 없다
- [ ] auth-service infrastructure에 `UserJpaEntity`와 `UserJpaMapper`가 존재한다
- [ ] order-service `Order` 도메인 클래스에 JPA import가 없다
- [ ] order-service infrastructure에 `OrderJpaEntity`와 `OrderJpaMapper`가 존재한다
- [ ] user-service `UserProfile`, `Address` 도메인 클래스에 JPA import가 없다
- [ ] user-service infrastructure에 `UserProfileJpaEntity`, `AddressJpaEntity`와 각 Mapper가 존재한다
- [ ] 모든 기존 테스트가 통과한다
- [ ] 각 서비스 빌드가 성공한다

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/services/order-service/architecture.md`
- `specs/services/user-service/architecture.md`
- `specs/platform/architecture.md`
- `specs/platform/coding-rules.md`

# Related Skills

- `.claude/skills/backend/dto-mapping.md`

---

# Related Contracts

- 해당 없음 (내부 리팩토링, API 계약 변경 없음)

---

# Target Service

- `auth-service`
- `order-service`
- `user-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`
- `specs/services/order-service/architecture.md`
- `specs/services/user-service/architecture.md`

---

# Implementation Notes

- TASK-BE-072에서 완료한 payment-service의 `PaymentJpaEntity` + `PaymentJpaMapper` 패턴을 동일하게 적용한다.
- order-service는 DDD 아키텍처이므로 infrastructure/persistence 패키지에 JpaEntity를 배치한다.
- user-service의 Address는 202줄로 크기가 크므로, JpaEntity 분리 시 검증 로직은 도메인 모델에 유지하고 영속성 매핑만 분리한다.

---

# Edge Cases

- 도메인 모델의 equals/hashCode가 JPA 프록시에 의존하는 경우 → 비즈니스 키 기반으로 전환
- 양방향 연관관계가 있는 경우 → JpaEntity 간에만 유지, 도메인 모델은 단방향
- Lazy loading을 사용하는 필드 → JpaEntity에서 처리, Mapper에서 eager 변환

---

# Failure Scenarios

- JpaEntity ↔ 도메인 모델 매핑 누락으로 필드 값이 손실됨
- 기존 JPQL 쿼리가 도메인 모델 대신 JpaEntity를 참조하지 않아 컴파일 오류
- 트랜잭션 내에서 dirty checking이 동작하지 않음 → save 명시 호출 필요

---

# Test Requirements

- 각 서비스별 기존 단위 테스트 통과
- 각 서비스별 기존 통합 테스트 통과
- Mapper 단위 테스트 추가 (도메인 ↔ JpaEntity 변환 검증)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
