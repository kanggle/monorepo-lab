# Task ID

TASK-BE-071

# Title

payment-service 이벤트 envelope snake_case 수정 및 토픽명 설정 외부화

# Status

done

# Owner

backend

# Task Tags

- code
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

TASK-INT-012 크로스 리뷰에서 발견된 이슈 수정. payment-service의 이벤트 envelope 필드명을 event-driven-policy.md의 snake_case 규칙에 맞게 수정하고, 하드코딩된 Kafka 토픽명을 설정으로 외부화한다.

---

# Scope

## In Scope

- `PaymentCompletedEvent`: envelope 필드명 camelCase → snake_case (eventId → event_id, eventType → event_type, occurredAt → occurred_at)
- `PaymentRefundedEvent`: 동일하게 snake_case 적용
- `PaymentProcessingService`: 하드코딩된 토픽명을 application.yml 설정으로 이동
- `PaymentRefundService`: 하드코딩된 토픽명을 application.yml 설정으로 이동
- 이벤트 소비자(order-service 등)가 snake_case 필드를 올바르게 파싱하는지 확인

## Out of Scope

- 다른 서비스의 이벤트 envelope 수정 (별도 태스크로 점검)
- 토픽명 자체 변경

---

# Acceptance Criteria

- [ ] PaymentCompletedEvent의 JSON envelope 필드가 snake_case로 직렬화된다
- [ ] PaymentRefundedEvent의 JSON envelope 필드가 snake_case로 직렬화된다
- [ ] 토픽명이 application.yml의 설정값에서 주입된다
- [ ] 기존 이벤트 소비자 테스트가 snake_case envelope에 맞게 업데이트된다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/platform/event-driven-policy.md` (Event Envelope Format)
- `specs/platform/coding-rules.md` (하드코딩 금지)
- `specs/contracts/events/payment-events.md`

# Related Skills

_(없음)_

---

# Related Contracts

- `specs/contracts/events/payment-events.md`

---

# Target Service

- payment-service (주), order-service (소비자 측 확인)

---

# Architecture

- `specs/services/payment-service/architecture.md`

---

# Edge Cases

- 이벤트 소비자가 기존 camelCase와 신규 snake_case 모두 처리 가능해야 하는지 확인 (배포 순서 의존)
- Jackson의 @JsonProperty 또는 PropertyNamingStrategies 사용 시 기존 직렬화와의 호환성

---

# Failure Scenarios

- 소비자가 업데이트되기 전에 프로듀서가 먼저 배포되면 이벤트 파싱 실패 가능
- 토픽 설정 누락 시 서비스 기동 실패

---

# Test Requirements

- PaymentCompletedEvent, PaymentRefundedEvent 직렬화 단위 테스트
- 토픽명 설정 주입 확인 테스트
- 기존 통합 테스트 snake_case 호환 업데이트

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
