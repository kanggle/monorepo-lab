# Task ID

TASK-BE-120

# Title

OutboxPollingScheduler 리팩터링 후속 수정 — resolveTopic private화, 실패 메트릭 복구, SKILL.md 업데이트

# Status

ready

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

---

# Goal

TASK-BE-002 리뷰에서 발견된 3가지 문제를 수정한다:

1. **`resolveTopic()` 접근 제어 강화**: `OutboxPollingScheduler.resolveTopic()`이 `protected`로 선언되어 있어 서브클래스 오버라이드가 여전히 가능하다. 서브클래스 패턴을 제거한 취지에 맞게 `private`으로 변경한다.

2. **admin-service / membership-service OutboxFailureHandler 누락**: 기존 서브클래스가 담당하던 Micrometer 카운터(`outbox_publish_failures`) 빈이 두 서비스에서 제거되었다. account-service / auth-service처럼 `OutboxFailureHandlerConfig` 빈을 각 서비스에 추가한다.

3. **SKILL.md 업데이트**: `.claude/skills/messaging/outbox-pattern/SKILL.md`가 여전히 구 서브클래스 패턴("Each service extends it and implements `resolveTopic()`")과 `@Scheduled(fixedDelayString...)` 방식을 설명한다. 새로운 설정 기반 방식으로 업데이트한다.

---

# Scope

## In Scope

- `libs/java-messaging/src/main/java/com/example/messaging/outbox/OutboxPollingScheduler.java`: `resolveTopic()` 접근 제어를 `protected` → `private` 변경
- `apps/admin-service/src/main/java/com/example/admin/infrastructure/messaging/AdminOutboxFailureHandlerConfig.java` 신규 생성
- `apps/membership-service/src/main/java/com/example/membership/infrastructure/messaging/MembershipOutboxFailureHandlerConfig.java` 신규 생성
- `.claude/skills/messaging/outbox-pattern/SKILL.md` 업데이트 (설정 기반 방식 반영)
- `OutboxPollingSchedulerTest` 에 `resolveTopic`이 여전히 접근 가능한지 확인 (`ReflectionTestUtils` 또는 `pollAndPublish()` 간접 경로로 커버)

## Out of Scope

- `LoginSucceededConsumerIntegrationTest.java` Javadoc 코멘트 수정 (사소한 주석, 별도 태스크 불필요)
- 서비스별 OutboxRelay 통합 테스트 신규 작성 (scope 외)
- Kafka topic 이름 변경

---

# Acceptance Criteria

- [ ] `OutboxPollingScheduler.resolveTopic()`이 `private`으로 선언된다
- [ ] `OutboxPollingSchedulerTest`의 `resolveTopic` 테스트가 계속 통과한다 (리플렉션 또는 `pollAndPublish` 경유로 커버)
- [ ] `admin-service`에 `admin_outbox_publish_failures` Micrometer 카운터를 등록하는 `AdminOutboxFailureHandlerConfig` 빈이 추가된다
- [ ] `membership-service`에 `membership_outbox_publish_failures` Micrometer 카운터를 등록하는 `MembershipOutboxFailureHandlerConfig` 빈이 추가된다
- [ ] `.claude/skills/messaging/outbox-pattern/SKILL.md`가 설정 기반 방식을 반영한다 (서브클래스 예제 제거, `@ConfigurationProperties` 방식 설명 추가)
- [ ] 빌드 및 테스트 통과

---

# Related Specs

- `specs/services/account-service/architecture.md`
- `specs/services/admin-service/architecture.md`
- `specs/services/auth-service/architecture.md`
- `specs/services/membership-service/architecture.md`
- `platform/shared-library-policy.md`

# Related Skills

- `.claude/skills/messaging/outbox-pattern/SKILL.md`
- `.claude/skills/backend/scheduled-tasks/SKILL.md`

---

# Related Contracts

없음 — Kafka topic 이름 변경 없음

---

# Target Service

- `libs/java-messaging`
- `admin-service`
- `membership-service`

---

# Architecture

Follow:

- 각 서비스의 `specs/services/<service>/architecture.md`

---

# Implementation Notes

## 1. resolveTopic private화

```java
// Before
protected String resolveTopic(String eventType) { ... }

// After
private String resolveTopic(String eventType) { ... }
```

`OutboxPollingSchedulerTest`의 `resolveTopic_knownEventType` / `resolveTopic_unknownEventType` 테스트는 현재 `scheduler.resolveTopic(...)` 직접 호출로 커버됨.
`private` 변경 후 두 테스트를 `pollAndPublish()` + mock `outboxPublisher` 경유로 간접 검증하거나, 또는 `@TestAccessor`/패키지 분리 없이 삭제 후 `sendToKafka` 경로로 통합 커버한다.

## 2. OutboxFailureHandler 빈

account-service 패턴 참고:
```java
@Configuration
class AdminOutboxFailureHandlerConfig {

    @Bean
    OutboxFailureHandler outboxFailureHandler(MeterRegistry meterRegistry) {
        return (eventType, aggregateId, e) ->
            meterRegistry.counter("admin_outbox_publish_failures", "event_type", eventType).increment();
    }
}
```

## 3. SKILL.md

- `## Rules` 섹션: "Each service extends it and implements `resolveTopic()`" → "The base `OutboxPollingScheduler` reads `outbox.topic-mapping` from `application.yml` via `OutboxProperties`. Services do not subclass it."
- `@Scheduled(fixedDelayString = "...")` 언급 제거 → `@PostConstruct` / `ThreadPoolTaskScheduler.scheduleWithFixedDelay` 방식으로 업데이트
- 예제 코드(서브클래스)를 `application.yml` 설정 예시로 교체

---

# Edge Cases

- `resolveTopic()`을 `private`으로 변경 후 기존 테스트에서 직접 호출하는 경우 컴파일 에러 발생 → 테스트 코드 수정 필요
- `OutboxFailureHandler` 빈이 없는 서비스에서 Micrometer가 미등록 상태로 남아있던 기간의 메트릭 갭 → 허용 (history 손실, 신규 배포 후 복구)

---

# Failure Scenarios

- `MeterRegistry` 빈이 없는 테스트 슬라이스에서 `OutboxFailureHandlerConfig` 로딩 실패 → `@ConditionalOnBean(MeterRegistry.class)` 또는 테스트 프로파일에서 `outbox.polling.enabled=false` 설정으로 방어

---

# Test Requirements

- `OutboxPollingSchedulerTest`: `resolveTopic` private화 후 간접 커버 (pollAndPublish 경로)
- `AdminOutboxFailureHandlerConfig` 단위 테스트 또는 컨텍스트 슬라이스 테스트

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
