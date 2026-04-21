# Task ID

TASK-R-07

# Title

notification-service KafkaConsumerConfig 패키지 위치 수정 (Hexagonal 아키텍처 준수)

# Status

review

# Owner

backend

# Task Tags

- refactor

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

notification-service의 KafkaConsumerConfig가 Hexagonal 아키텍처 구조 밖의 config 패키지에 존재한다. Hexagonal 아키텍처에서 Kafka 관련 설정은 인프라/어댑터 계층에 속해야 한다.

KafkaConsumerConfig를 adapter 내부 패키지로 이동하여 Hexagonal 아키텍처 구조를 준수하도록 수정한다.

---

# Scope

## In Scope

- KafkaConsumerConfig 클래스를 adapter/in/kafka 또는 적절한 adapter 패키지로 이동
- import 경로 업데이트
- 패키지 구조가 Hexagonal 아키텍처 규칙을 준수하는지 확인

## Out of Scope

- KafkaConsumerConfig 로직 변경
- 다른 서비스의 config 패키지 위치 변경
- Kafka 설정값 변경
- 다른 config 클래스 이동

---

# Acceptance Criteria

- [ ] KafkaConsumerConfig가 adapter 패키지 내부에 위치한다
- [ ] Hexagonal 아키텍처 구조(adapter/in, adapter/out, application, domain)를 준수한다
- [ ] 기존 기능이 정상 동작한다 (Kafka consumer 설정이 올바르게 적용됨)
- [ ] config 패키지에 Hexagonal 구조 밖의 Kafka 관련 클래스가 없다

---

# Related Specs

- `specs/services/notification-service/architecture.md`

# Related Skills

- `.claude/skills/backend/architecture/hexagonal.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- 없음 (내부 구조 변경만 해당)

---

# Target Service

- `notification-service`

---

# Architecture

Follow:

- `specs/services/notification-service/architecture.md`

Hexagonal 구조:
- adapter/in (inbound adapters: HTTP controllers, Kafka consumers)
- adapter/out (outbound adapters: email sender, SMS sender, push sender, persistence)
- application (use-cases, ports)
- domain

---

# Implementation Notes

- KafkaConsumerConfig는 Kafka consumer 인프라 설정이므로 adapter/in/kafka 패키지가 적절하다
- @Configuration 클래스이므로 Spring component scan 범위 내에 있어야 한다
- 패키지 이동 시 다른 클래스에서의 import 변경이 필요할 수 있다

---

# Edge Cases

- component scan 범위 변경으로 인한 빈 등록 실패 가능성
- 같은 패키지 내 다른 config 클래스와의 관계 확인

---

# Failure Scenarios

- 패키지 이동 후 Spring context 로드 실패: component scan 경로 확인
- KafkaConsumerConfig가 다른 빈에 의존하는 경우 순환 참조 발생 가능

---

# Test Requirements

- 통합 테스트: Spring context가 정상 로드되는지 확인 (@SpringBootTest)
- 기존 Kafka consumer 관련 테스트가 통과하는지 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
