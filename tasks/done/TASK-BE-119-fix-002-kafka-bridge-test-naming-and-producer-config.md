# Task ID

TASK-BE-119-fix-002

# Title

TASK-BE-119 리뷰 fix: 통합 테스트 파일명 컨벤션 + Kafka producer acks/idempotence 설정 추가

# Status

ready

# Owner

backend

# Task Tags

- code
- test
- fix

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

TASK-BE-119 코드 리뷰에서 발견된 두 가지 경고를 수정한다:

1. **통합 테스트 파일명 컨벤션 불일치**: `AuthSignupKafkaPublishIntegrationTest.java` → `specs/platform/testing-strategy.md`에 따라 `AuthSignupKafkaPublishEventIntegrationTest.java`로 변경
2. **Kafka producer 신뢰성 설정 누락**: `application.yml`의 `spring.kafka.producer`에 `acks: all` 및 `enable-idempotence: true` 추가 (`specs/platform/event-driven-policy.md` 준수)

---

# Scope

## In Scope

- `apps/auth-service/src/test/java/com/example/auth/AuthSignupKafkaPublishIntegrationTest.java` → `AuthSignupKafkaPublishEventIntegrationTest.java`로 파일명 변경 (클래스명 및 내부 참조 일치)
- `apps/auth-service/src/main/resources/application.yml` Kafka producer 블록에 `acks: all`, `enable-idempotence: true` 추가
- 변경으로 인한 기존 테스트 참조 업데이트 (있는 경우)

## Out of Scope

- 테스트 로직 변경 (파일명만 변경)
- DLQ/재시도 정책 변경
- 다른 서비스의 Kafka producer 설정

---

# Acceptance Criteria

- [ ] 통합 테스트 파일명이 `AuthSignupKafkaPublishEventIntegrationTest.java`로 변경됨
- [ ] 클래스명도 `AuthSignupKafkaPublishEventIntegrationTest`로 일치
- [ ] `application.yml` Kafka producer 블록에 `acks: all`, `enable-idempotence: true` 존재
- [ ] 기존 auth-service 테스트 전체 통과 (빌드 성공)

---

# Related Specs

- `specs/platform/testing-strategy.md`
- `specs/platform/event-driven-policy.md`

# Related Skills

- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

없음 (변경 없음)

---

# Target Service

- `auth-service`

---

# Edge Cases

- 파일명 변경 시 IDE/빌드 캐시에 구 파일명이 남아있지 않도록 확인

---

# Failure Scenarios

- `enable-idempotence: true` 설정 시 `acks: all`이 아니면 Kafka 클라이언트 예외 발생 → 두 설정을 함께 추가

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing
- [ ] Ready for review
