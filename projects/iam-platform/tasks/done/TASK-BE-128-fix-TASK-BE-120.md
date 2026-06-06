# Task ID

TASK-BE-128

# Title

TASK-BE-120 후속 수정 — AdminOutboxFailureHandlerConfig / MembershipOutboxFailureHandlerConfig 단위 테스트 누락

# Status

ready

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

---

# Goal

TASK-BE-120 리뷰에서 발견된 1가지 문제를 수정한다:

**AdminOutboxFailureHandlerConfig / MembershipOutboxFailureHandlerConfig 단위 테스트 누락**: TASK-BE-120의 "Test Requirements" 섹션은 "AdminOutboxFailureHandlerConfig 단위 테스트 또는 컨텍스트 슬라이스 테스트"를 명시했으나, 두 Config 클래스 모두 테스트가 작성되지 않았다. platform/testing-strategy.md의 code 태스크 테스트 요건을 충족하도록 각각 단위 테스트를 추가한다.

---

# Scope

## In Scope

- apps/admin-service/src/test/java/com/example/admin/infrastructure/messaging/AdminOutboxFailureHandlerConfigTest.java 신규 생성
- apps/membership-service/src/test/java/com/example/membership/infrastructure/messaging/MembershipOutboxFailureHandlerConfigTest.java 신규 생성

## Out of Scope

- SecurityServiceClientCircuitBreakerTest.recovers_to_closed_after_half_open_success() 플키 테스트 수정 (TASK-BE-033에서 도입된 별도 문제, 별도 태스크 대상)
- 다른 OutboxFailureHandlerConfig (account-service, auth-service) 테스트 추가 (TASK-BE-120 범위 외)

---

# Acceptance Criteria

- [ ] AdminOutboxFailureHandlerConfigTest가 admin_outbox_publish_failures 카운터를 올바른 event_type 태그와 함께 MeterRegistry에 등록하는지 검증한다
- [ ] MembershipOutboxFailureHandlerConfigTest가 membership_outbox_publish_failures 카운터를 올바른 event_type 태그와 함께 MeterRegistry에 등록하는지 검증한다
- [ ] 두 테스트 모두 단위 테스트 수준 (Spring context 없이, SimpleMeterRegistry 사용)
- [ ] ./gradlew :apps:admin-service:test :apps:membership-service:test 통과

---

# Related Specs

- specs/services/admin-service/architecture.md
- specs/services/membership-service/architecture.md
- platform/testing-strategy.md

# Related Skills

- .claude/skills/backend/testing-backend/SKILL.md
- .claude/skills/messaging/outbox-pattern/SKILL.md

---

# Related Contracts

없음 — Kafka topic 이름 변경 없음

---

# Target Service

- admin-service
- membership-service

---

# Architecture

Follow:

- 각 서비스의 specs/services/<service>/architecture.md

---

# Implementation Notes

## 테스트 패턴

AdminOutboxFailureHandlerConfig와 MembershipOutboxFailureHandlerConfig 모두 @Bean이 람다를 반환하는 단순한 팩토리 메서드이므로, SimpleMeterRegistry를 사용한 단위 테스트로 충분하다.

패키지: 테스트가 package-private 클래스를 직접 생성해야 하므로 동일 패키지에 위치해야 한다.
- admin: com.example.admin.infrastructure.messaging
- membership: com.example.membership.infrastructure.messaging

검증 포인트:
1. outboxFailureHandler() 팩토리 빈이 null이 아닌 OutboxFailureHandler를 반환한다
2. onFailure() 호출 후 SimpleMeterRegistry에서 해당 카운터(event_type 태그 포함)가 1.0으로 증가한다

---

# Edge Cases

- SimpleMeterRegistry는 Micrometer 테스트 유틸리티로, 생성 시 Spring context가 필요 없다
- OutboxFailureHandlerConfig 빈이 package-private 클래스이므로 테스트가 동일 패키지에 위치해야 리플렉션 없이 생성 가능하다

---

# Failure Scenarios

- SimpleMeterRegistry 없이 MeterRegistry 모킹만 사용하는 경우 counter 값 검증이 불가 → SimpleMeterRegistry 사용

---

# Test Requirements

- AdminOutboxFailureHandlerConfigTest: admin_outbox_publish_failures 카운터 증가 검증 (단위)
- MembershipOutboxFailureHandlerConfigTest: membership_outbox_publish_failures 카운터 증가 검증 (단위)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
