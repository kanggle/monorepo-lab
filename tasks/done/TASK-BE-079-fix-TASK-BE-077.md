# Task ID

TASK-BE-079

# Title

fix(infra): OutboxPollingScheduler 서브클래스 `outbox.polling.enabled=false` 시 기동 실패 수정

# Status

ready

# Owner

backend

# Task Tags

- code
- infra

# depends_on

- TASK-BE-077

---

# Goal

TASK-BE-077 리뷰에서 발견된 결함 수정: `outbox.polling.enabled=false` 설정 시 `OutboxSchedulerConfig`(클래스 레벨 `@ConditionalOnProperty`)는 빈 등록을 건너뛰지만, 서비스 서브클래스(`AuthOutboxPollingScheduler`, `AccountOutboxPollingScheduler`, `AdminOutboxPollingScheduler`, `MembershipOutboxPollingScheduler`)는 여전히 `@Component`로 등록되어 `outboxTaskScheduler` 빈을 생성자에서 요구한다. 결과적으로 `NoSuchBeanDefinitionException`으로 Spring context 기동 실패.

`platform/testing-strategy.md` Scheduler Thread Lifecycle 절은 "Test profiles may opt out when they do not exercise the relay path" 를 명문화하고 있으므로, 이 동작은 반드시 보장되어야 한다.

---

# Scope

## In Scope

### A. 서비스 서브클래스에 `@ConditionalOnProperty` 추가

대상 파일 4개:
- `apps/auth-service/src/main/java/com/example/auth/infrastructure/messaging/AuthOutboxPollingScheduler.java`
- `apps/account-service/src/main/java/com/example/account/infrastructure/messaging/AccountOutboxPollingScheduler.java`
- `apps/admin-service/src/main/java/com/example/admin/infrastructure/messaging/AdminOutboxPollingScheduler.java`
- `apps/membership-service/src/main/java/com/example/membership/infrastructure/kafka/MembershipOutboxPollingScheduler.java`

각 클래스에 아래 어노테이션 추가:

```java
@ConditionalOnProperty(name = "outbox.polling.enabled", havingValue = "true", matchIfMissing = true)
```

`OutboxSchedulerConfig`와 동일한 조건이므로, 두 빈이 함께 등록되거나 함께 생략된다.

### B. 단위 테스트 추가 — OutboxPollingScheduler 수명주기 검증

`libs/java-messaging/src/test/java/com/example/messaging/outbox/OutboxPollingSchedulerTest.java` 신규 작성:
- `start()` 호출 시 `taskScheduler.scheduleWithFixedDelay(...)` 가 호출되는지 확인
- `stop()` 호출 시 `ScheduledFuture.cancel(false)` 가 호출되는지 확인
- `running=false` 상태에서 `pollAndPublish()` 가 조기 반환하는지 확인

이 테스트는 `OutboxPollingScheduler`가 추상 클래스이므로 익명 서브클래스 또는 `@TestComponent` 구현체를 사용한다.

## Out of Scope

- `security-service` — outbox 발행 서브클래스가 없어 해당 없음
- `platform/testing-strategy.md` 변경 — TASK-BE-077에서 이미 업데이트됨
- `OutboxPollingScheduler` 기본 로직 변경

---

# Acceptance Criteria

- [ ] `outbox.polling.enabled=false` 설정 시 4개 서비스 서브클래스가 빈으로 등록되지 않고 Spring context가 정상 기동됨
- [ ] `outbox.polling.enabled=true` (기본값) 시 기존과 동일하게 동작함
- [ ] `OutboxPollingSchedulerTest` 단위 테스트 작성 및 통과
- [ ] `./gradlew :libs:java-messaging:test` green

---

# Related Specs

- `platform/testing-strategy.md` (Scheduler Thread Lifecycle 절)
- `platform/coding-rules.md`

---

# Related Contracts

없음

---

# Target Service

- `libs/java-messaging` (단위 테스트)
- `apps/auth-service`, `apps/account-service`, `apps/admin-service`, `apps/membership-service`

---

# Architecture

layered 4-layer + shared lib. 서비스 서브클래스는 infrastructure 레이어.

---

# Edge Cases

- `@ConditionalOnMissingBean(name = "outboxTaskScheduler")`은 `OutboxSchedulerConfig`에 이미 있으므로 서브클래스에는 불필요
- 외부에서 `outboxTaskScheduler` 빈을 직접 등록한 서비스가 없는 경우, `@ConditionalOnProperty`만으로 충분
- 테스트에서 `@TestPropertySource(properties = "outbox.polling.enabled=true")`를 명시하면 기존 동작 그대로 scheduler가 활성화됨

---

# Failure Scenarios

- `@ConditionalOnProperty`를 서브클래스에 추가했으나 `OutboxAutoConfiguration`이 서브클래스 빈을 직접 생성하는 경우 — 해당 없음 (서브클래스는 `@Component`로 자동 등록됨)
- 단위 테스트에서 추상 클래스 `OutboxPollingScheduler` 인스턴스화 실패 — 익명 서브클래스로 해결

---

# Test Requirements

- `OutboxPollingSchedulerTest` 단위 테스트 3개 이상 (start / stop / running guard)
- `./gradlew :libs:java-messaging:test` green
- 기존 통합 테스트 회귀 없음

---

# Implementation Notes

TASK-BE-077 리뷰 결과 (CRITICAL):
- `OutboxSchedulerConfig`는 `@ConditionalOnProperty(name = "outbox.polling.enabled", havingValue = "true", matchIfMissing = true)`로 클래스 레벨 조건부이지만, 4개 서비스 서브클래스는 이 조건 없이 `@Component`로 등록되어 `outboxTaskScheduler` 빈을 생성자에서 요구함
- `outbox.polling.enabled=false`를 test profile에서 사용하면 `NoSuchBeanDefinitionException: No qualifying bean of type 'ThreadPoolTaskScheduler' available` 발생
- `platform/testing-strategy.md`의 Scheduler Thread Lifecycle 절이 이 옵션을 권장하므로 반드시 수정 필요

---

# Definition of Done

- [ ] 4개 서비스 서브클래스에 `@ConditionalOnProperty` 추가
- [ ] `OutboxPollingSchedulerTest` 단위 테스트 신규 작성 및 통과
- [ ] CI green
- [ ] Ready for review
