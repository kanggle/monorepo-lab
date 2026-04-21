# Task ID

TASK-R-09

# Title

notification-service controller 도메인 모델 직접 노출 제거

# Status

review

# Owner

backend

# Task Tags

- refactor
- api

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

notification-service의 NotificationController가 Notification 도메인 엔티티를 API 응답으로 직접 반환하고 있다. coding-rules.md에서 "Use DTOs for request/response. Do not expose domain entities directly." 규칙을 위반한다.

application result DTO를 도입하여 도메인 모델이 API 경계 밖으로 노출되지 않도록 수정한다.

---

# Scope

## In Scope

- Notification 도메인 엔티티 직접 반환하는 controller 메서드 식별
- application 레이어에 result DTO 도입 (예: NotificationResult, NotificationListResult)
- application service에서 도메인 엔티티를 result DTO로 변환
- controller에서 result DTO를 response DTO로 변환 (또는 직접 반환)
- 기타 도메인 엔티티를 직접 노출하는 controller가 있으면 함께 수정

## Out of Scope

- API 응답 필드 변경 (기존 응답과 동일해야 함)
- 다른 서비스의 도메인 노출 수정
- 새로운 API 추가
- 도메인 모델 자체 변경

---

# Acceptance Criteria

- [ ] controller가 도메인 엔티티를 직접 반환하지 않는다
- [ ] application 레이어에 result DTO가 존재한다
- [ ] application service가 도메인 엔티티를 result DTO로 변환하여 반환한다
- [ ] API 응답 JSON 형식이 기존과 동일하다 (하위 호환)
- [ ] controller에서 domain 패키지를 직접 import하지 않는다

---

# Related Specs

- `specs/platform/coding-rules.md`
- `specs/services/notification-service/architecture.md`

# Related Skills

- `.claude/skills/backend/architecture/hexagonal.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/notification-api.md`

---

# Target Service

- `notification-service`

---

# Architecture

Follow:

- `specs/services/notification-service/architecture.md`

Boundary Rules:
- "inbound adapters handle HTTP mapping, Kafka message deserialization, and delegation to use-cases"
- coding-rules.md: "Use DTOs for request/response. Do not expose domain entities directly."

---

# Implementation Notes

- Command/Result 패턴 사용: application service가 Result DTO를 반환
- naming-conventions.md 규칙에 따라 네이밍 (예: GetNotificationResult, ListNotificationsResult)
- controller response DTO는 interfaces/adapter 레이어에 위치
- 도메인 엔티티의 모든 직렬화 관련 어노테이션(@JsonProperty 등)이 있으면 함께 제거

---

# Edge Cases

- null 필드가 있는 도메인 엔티티의 DTO 변환 시 처리
- 컬렉션 반환 시 빈 리스트 처리
- 페이징 결과와 결합된 경우의 변환 (TASK-R-08과 연관 가능)

---

# Failure Scenarios

- DTO 변환 시 필드 매핑 누락으로 인한 응답 누락
- 기존 클라이언트가 의존하던 필드가 사라지는 경우 (하위 호환 깨짐)

---

# Test Requirements

- 슬라이스 테스트: controller 응답이 기존 JSON 형식과 동일한지 검증 (@WebMvcTest)
- 단위 테스트: 도메인 엔티티 -> result DTO 변환 로직 검증
- 단위 테스트: application service가 result DTO를 반환하는지 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
