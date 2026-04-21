# Task ID

TASK-BE-118

# Title

order-service OrderRepositoryImpl save() 최적화 통합 테스트 추가

# Status

done

# Owner

backend

# Task Tags

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

TASK-BE-116(order-service Repository save() 업데이트 시 불필요한 SELECT 제거) 리뷰에서 발견된 누락 사항을 보완한다.

TASK-BE-116의 Test Requirements에 "integration test: 주문 생성 후 상태 변경 저장 시 쿼리 로그에서 불필요한 SELECT 없음 확인"이 명시되어 있으나 단위 테스트만 구현되어 있다. 실제 JPA dirty check 동작 및 SELECT 쿼리 발생 여부를 검증하는 통합 테스트를 추가한다.

---

# Scope

## In Scope

- `OrderRepositoryImpl` 통합 테스트 작성 (Testcontainers 또는 H2 기반)
- 신규 주문 저장 시 INSERT 실행 확인
- 기존 주문 업데이트 시 dirty check에 의한 UPDATE 실행 확인
- 낙관적 락 충돌 시나리오 검증

## Out of Scope

- `OrderRepositoryImpl` 로직 자체의 수정
- JPA 2차 캐시 또는 Hibernate batch 설정 변경

---

# Acceptance Criteria

- [ ] 신규 주문 저장 시 INSERT 쿼리가 실행되고 SELECT 없이 처리됨을 통합 테스트로 검증한다
- [ ] 기존 주문 업데이트 시 findById SELECT 1회 후 dirty check UPDATE가 실행됨을 검증한다
- [ ] `@Version` 기반 낙관적 락 충돌 시 `OptimisticLockingFailureException`이 발생함을 검증한다
- [ ] 기존 단위 테스트가 모두 통과한다

---

# Related Specs

- `specs/services/order-service/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/testing-backend.md`

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

- 프로젝트의 기존 통합 테스트 패턴을 우선 참고하여 동일한 방식으로 작성한다
- H2 또는 Testcontainers 중 이미 프로젝트에서 사용 중인 방식을 따른다
- 쿼리 카운트 검증은 Hibernate Statistics 또는 DataSource proxy 라이브러리를 활용할 수 있다
- 통합 테스트 클래스명: `OrderRepositoryImplIntegrationTest`

---

# Edge Cases

- 동시 업데이트로 인한 낙관적 락 충돌

---

# Failure Scenarios

- version 불일치로 업데이트 실패 → `OptimisticLockingFailureException` 발생

---

# Test Requirements

- integration test: `@SpringBootTest` 또는 `@DataJpaTest` 기반으로 실제 JPA 동작 검증
- 신규/업데이트/낙관적락 시나리오 각 1개 이상의 테스트 케이스

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
