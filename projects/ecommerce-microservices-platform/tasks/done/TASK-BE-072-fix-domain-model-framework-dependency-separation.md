# Task ID

TASK-BE-072

# Title

payment-service, batch-worker 도메인 모델 프레임워크 의존성 분리

# Status

review

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

TASK-INT-012 크로스 리뷰에서 발견된 이슈 수정. Hexagonal/Layered 아키텍처 스펙에 따라 도메인 모델에서 JPA 등 프레임워크 어노테이션을 분리한다.

---

# Scope

## In Scope

- payment-service: `Payment` 도메인 엔티티에서 JPA 어노테이션(@Entity, @Table, @Column 등) 제거, infrastructure 레이어에 JPA 전용 persistence 모델 생성, 도메인↔persistence 매퍼 구현
- batch-worker: `BatchJobExecution` 도메인 엔티티에서 JPA 어노테이션 제거, infrastructure 레이어에 JPA 전용 persistence 모델 생성
- batch-worker: 도메인 모델 필드를 final로 변경하여 불변성 확보

## Out of Scope

- 다른 서비스의 도메인-JPA 분리 (Layered 아키텍처 서비스는 JPA 직접 사용 허용)
- 비즈니스 로직 변경

---

# Acceptance Criteria

- [x] payment-service 도메인 `Payment` 클래스에 JPA import가 없다
- [x] payment-service infrastructure에 `PaymentJpaEntity`와 매퍼가 존재한다
- [x] batch-worker 도메인 `BatchJobExecution`에 JPA import가 없다
- [x] batch-worker 도메인 모델 필드가 final이다
- [x] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/services/payment-service/architecture.md` (domain은 프레임워크 의존 금지)
- `specs/services/batch-worker/architecture.md` (domain은 infrastructure 직접 의존 금지)
- `specs/platform/coding-rules.md` (immutability)

# Related Skills

- `.claude/skills/backend/architecture/hexagonal.md`

---

# Related Contracts

_(없음)_

---

# Target Service

- payment-service, batch-worker

---

# Architecture

- `specs/services/payment-service/architecture.md`
- `specs/services/batch-worker/architecture.md`

---

# Edge Cases

- 기존 JPA 쿼리가 새 persistence 모델을 올바르게 참조하는지 확인
- Flyway 마이그레이션은 변경 불필요 (테이블 구조 동일)

---

# Failure Scenarios

- 매퍼 누락으로 도메인↔persistence 변환 시 필드 손실
- JPA lazy loading 동작 변경

---

# Test Requirements

- 도메인 모델 단위 테스트 (프레임워크 의존 없이 동작 확인)
- persistence 매퍼 단위 테스트
- 기존 통합 테스트 통과 확인

---

# Definition of Done

- [x] Implementation completed
- [x] Tests added
- [x] Tests passing
- [x] Contracts updated if needed
- [x] Specs updated first if required
- [x] Ready for review
