# Task ID

TASK-BE-116

# Title

order-service Repository save() 업데이트 시 불필요한 SELECT 제거

# Status

in-progress

# Owner

backend

# Task Tags

- code

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

현재 `OrderRepositoryImpl.save()`에서 항상 `OrderJpaEntity.fromDomain()`으로 새 JPA 엔티티를 생성하여 `jpaRepository.save()`에 전달한다. JPA의 `save()`는 ID가 존재하면 `merge()`를 호출하며, 이때 영속성 컨텍스트에 없는 새 객체이므로 불필요한 SELECT가 발생한다. 업데이트 시 영속 상태의 엔티티를 활용하도록 개선한다.

---

# Scope

## In Scope

- `OrderRepositoryImpl.save()`에서 신규/업데이트 분기 처리
- 업데이트 시 기존 영속 엔티티를 조회하여 필드 업데이트
- `OrderJpaEntity`에 업데이트 메서드 추가

## Out of Scope

- JPA 2차 캐시 설정
- Hibernate batch 설정 변경

---

# Acceptance Criteria

- [ ] 신규 주문 저장 시 기존과 동일하게 INSERT가 실행된다
- [ ] 기존 주문 업데이트 시 불필요한 SELECT 없이 UPDATE가 실행된다
- [ ] 낙관적 락(`@Version`) 동작이 정상 유지된다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/services/order-service/architecture.md`

# Related Skills

- `.claude/skills/backend/`

---

# Related Contracts

- N/A

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`

---

# Implementation Notes

- `version`이 null이면 신규, non-null이면 업데이트로 판단
- 업데이트 시: `jpaRepository.findById()` → 필드 업데이트 → dirty check으로 자동 UPDATE
- `OrderJpaEntity`에 `updateFrom(Order domain)` 메서드 추가하여 매핑 로직 캡슐화
- 인프라 레이어 내부 변경이므로 도메인 인터페이스 변경 불필요

---

# Edge Cases

- 동시 업데이트 시 낙관적 락 충돌 (기존과 동일하게 `OptimisticLockingFailureException`)
- findById() 결과가 없는 경우 (이미 삭제된 주문 — 현재 삭제 기능 없으므로 발생하지 않음)

---

# Failure Scenarios

- version 불일치로 merge 실패 → OptimisticLockingFailureException (기존 동작 유지)

---

# Test Requirements

- integration test: 주문 생성 후 상태 변경 저장 시 쿼리 로그에서 불필요한 SELECT 없음 확인
- unit test: version null/non-null에 따른 분기 로직 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
