# Task ID

TASK-R-10

# Title

review-service 도메인 이벤트에서 Jackson 의존 제거

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

review-service의 ReviewEvent 도메인 이벤트 클래스에 @JsonProperty 등 Jackson 어노테이션이 사용되고 있다. DDD-style 아키텍처에서 "domain must not depend on framework or persistence details" 규칙을 위반한다.

도메인 이벤트에서 Jackson 의존을 제거하고, 직렬화 처리를 infrastructure 레이어로 이동한다.

---

# Scope

## In Scope

- ReviewEvent 및 관련 도메인 이벤트 클래스에서 Jackson 어노테이션 제거
- infrastructure 레이어에 이벤트 직렬화/역직렬화 처리 추가 (커스텀 Serializer 또는 DTO 매핑)
- 도메인 이벤트의 프레임워크 독립성 확보

## Out of Scope

- 이벤트 페이로드 구조 변경
- Kafka 토픽 변경
- 다른 서비스의 도메인 이벤트 수정
- consumer 측 역직렬화 변경

---

# Acceptance Criteria

- [ ] domain 패키지에 Jackson import(@JsonProperty, @JsonCreator 등)가 없다
- [ ] 도메인 이벤트가 프레임워크 독립적인 POJO/record이다
- [ ] infrastructure 레이어에서 도메인 이벤트를 Kafka 메시지로 직렬화한다
- [ ] Kafka에 발행되는 이벤트 JSON 형식이 기존과 동일하다 (하위 호환)
- [ ] 기존 consumer가 정상적으로 이벤트를 수신한다

---

# Related Specs

- `specs/services/review-service/architecture.md`

# Related Skills

- `.claude/skills/backend/architecture/ddd.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/events/review-events.md`

---

# Target Service

- `review-service`

---

# Architecture

Follow:

- `specs/services/review-service/architecture.md`

Forbidden Dependencies:
- "domain must not depend on framework or persistence details"

Boundary Rules:
- "infrastructure layer handles persistence, event publishing, and external adapters"

---

# Implementation Notes

- 방법 1: infrastructure 레이어에 이벤트 DTO(KafkaReviewEvent)를 두고 도메인 이벤트 -> DTO 매핑 후 직렬화
- 방법 2: 커스텀 JsonSerializer를 infrastructure에 등록하여 도메인 이벤트를 직접 직렬화
- 방법 1이 더 명확한 계층 분리를 제공하므로 권장
- 기존 JSON 필드명이 Java 필드명과 다른 경우(snake_case vs camelCase) DTO에서 처리

---

# Edge Cases

- 도메인 이벤트 필드명과 JSON 필드명이 다른 경우 매핑 주의
- null 필드가 있는 이벤트의 직렬화 처리
- 이벤트 상속 구조가 있는 경우 다형성 직렬화

---

# Failure Scenarios

- DTO 매핑 누락으로 인한 필드 손실 -> consumer 측 역직렬화 실패
- JSON 형식 변경으로 인한 하위 호환성 깨짐

---

# Test Requirements

- 단위 테스트: 도메인 이벤트 -> DTO 매핑 검증
- 단위 테스트: DTO 직렬화 결과가 기존 JSON 형식과 동일한지 검증
- 통합 테스트: Kafka에 발행된 이벤트가 기존 형식과 동일한지 검증 (Testcontainers)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
