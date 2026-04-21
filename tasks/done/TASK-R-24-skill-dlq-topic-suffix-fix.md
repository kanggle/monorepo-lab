# Task ID

TASK-R-24

# Title

consumer-retry-dlq 스킬 문서 DLQ 토픽 suffix 표기 수정 (.DLT → .dlq)

# Status

review

# Owner

backend

# Task Tags

- docs
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

`.claude/skills/messaging/consumer-retry-dlq.md`의 DLQ 토픽 네이밍 표기가 Spring Kafka 기본값인 `.DLT`로 적혀 있어 실제 플랫폼 정책(`specs/platform/event-driven-policy.md`의 `{original-topic}.dlq`) 및 모든 서비스의 `KafkaConsumerConfig` 실구현과 불일치한다. 스펙이 진실 공급원이므로 스킬 문서를 `.dlq` 표기로 수정하여 정합성을 확보한다.

---

# Scope

## In Scope

- `.claude/skills/messaging/consumer-retry-dlq.md`의 "DLQ Topic Naming" 섹션 표기 수정
  - `{original-topic}.DLT` → `{original-topic}.dlq`
  - 예시 2건(`order.order.placed.DLT`, `product.product.stock-changed.DLT`) 수정
  - Spring Kafka 기본 suffix(`.DLT`)가 아닌 커스텀 suffix(`.dlq`)를 사용함을 명시 (오해 방지)

## Out of Scope

- 실제 서비스의 `KafkaConsumerConfig` 코드 변경 (이미 `.dlq` 사용 중)
- `event-driven-policy.md` 정책 변경
- DLQ 재처리(replay) 로직 추가
- 다른 스킬 문서 수정

---

# Acceptance Criteria

- [ ] 스킬 문서의 DLQ 토픽 네이밍이 `.dlq`로 표기되어 있다
- [ ] 예시 토픽명이 모두 `.dlq` suffix로 수정되어 있다
- [ ] Spring Kafka 기본값(`.DLT`)이 아닌 커스텀 suffix(`.dlq`)를 사용한다는 점이 독자에게 명확히 전달된다
- [ ] `specs/platform/event-driven-policy.md` 및 실 구현(`KafkaConsumerConfig.java`)과 표기가 완전히 일치한다

---

# Related Specs

- `specs/platform/event-driven-policy.md` (DLQ Policy 섹션)

# Related Skills

- `.claude/skills/messaging/consumer-retry-dlq.md` (수정 대상)

---

# Related Contracts

- 해당 없음 (문서 정합성 수정, 계약 영향 없음)

---

# Target Service

- 해당 없음 (공용 스킬 문서)

---

# Architecture

Follow:

- `specs/platform/event-driven-policy.md`

---

# Implementation Notes

- 실제 구현 레퍼런스: `apps/order-service/src/main/java/com/example/order/infrastructure/config/KafkaConsumerConfig.java`에서 `DeadLetterPublishingRecoverer`에 destination resolver를 넘겨 `record.topic() + ".dlq"`로 라우팅하는 패턴 사용 중
- 스킬 문서에도 destination resolver 사용 패턴을 간단히 언급하여 독자가 기본 `.DLT` suffix가 아님을 즉시 이해하도록 한다

---

# Edge Cases

- 문서 내 다른 위치에 `.DLT` 언급이 남아있는 경우 → 전체 검색으로 확인 후 모두 수정
- 코드 예시에서 기본 `DeadLetterPublishingRecoverer` 생성자만 사용하면 `.DLT`가 붙으므로, 예시 코드도 destination resolver 방식으로 맞춰야 함

---

# Failure Scenarios

- `.dlq` 수정 누락으로 `.DLT` 표기가 일부 남아있는 경우 → 문서 내 `DLT` 전체 grep으로 확인
- 예시 코드가 실제 동작과 달라 독자를 오도하는 경우 → 실 서비스 `KafkaConsumerConfig.java`와 대조

---

# Test Requirements

- 문서 수정이므로 자동 테스트 없음
- 수동 검증: 문서 내 `DLT` 문자열이 남아있지 않은지 확인
- 수동 검증: `event-driven-policy.md`의 `.dlq`와 표기 일치 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
