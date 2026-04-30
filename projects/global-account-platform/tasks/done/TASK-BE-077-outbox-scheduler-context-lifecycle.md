# Task ID

TASK-BE-077

# Title

infra(test): OutboxPollingScheduler scheduler thread 수명을 Spring context 와 정렬 — orphaned HikariPool 참조 제거

# Status

ready

# Owner

backend

# Task Tags

- test
- infra
- code

# depends_on

- TASK-BE-076 (partial — AbstractIntegrationTest 베이스 완료)

---

# Goal

PR #44 (TASK-BE-076) CI 실측 + XML artifact 분석으로 9건 실패의 **진짜** 근본원인 확정:

- 새 Spring context 의 HikariPool-4 는 shared MySQL container 에 정상 접속
- **`scheduling-1` thread** 가 HikariPool-2 / HikariPool-3 (이전 context 의 orphaned pool) 에 계속 `@Transactional` 요청을 보내 `total=0 / CommunicationsException`
- TASK-BE-073 의 `@PreDestroy` + `AtomicBoolean running` guard 는 작동 중이지만 **불완전**:
  - Spring `@Scheduled` 의 default thread pool 이 context 생명주기를 outlive
  - 이미 실행 중인 tick 은 `@PreDestroy` 이후에도 완주
  - orphaned scheduler thread 가 다음 trigger 에서 orphaned pool 참조

본 task 는 scheduler thread pool 자체가 context 와 정확히 함께 종료되도록 재설계한다.

---

# Scope

## In Scope

### A. OutboxPollingScheduler 재설계

**Option 1 (권장)**: 명시적 `TaskScheduler` bean 등록 + context-scoped

```java
@Configuration
@ConditionalOnProperty(name = "outbox.polling.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxSchedulerConfig {
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler outboxTaskScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(1);
        s.setThreadNamePrefix("outbox-");
        s.setWaitForTasksToCompleteOnShutdown(true);
        s.setAwaitTerminationSeconds(5);
        s.initialize();
        return s;
    }
}
```

그리고 `OutboxPollingScheduler` 가 이 전용 scheduler 를 주입받아 `@Scheduled` 대신 프로그램적으로 schedule 하도록:

```java
@PostConstruct
public void start() {
    scheduledFuture = taskScheduler.scheduleAtFixedRate(this::pollAndPublish, intervalMs);
}

@PreDestroy
public void stop() {
    if (scheduledFuture != null) scheduledFuture.cancel(false);
}
```

**Option 2 (fallback)**: test profile 에서 scheduler 완전 disable
- `application-test.yml` (공유) 에 `outbox.polling.enabled=false`
- OutboxRelayIntegrationTest 등 scheduler 동작을 검증하는 테스트는 `@TestPropertySource(properties = "outbox.polling.enabled=true")` 로 override

### B. (선택적) 5 서비스 subclass 의 스케줄러 base 클래스 통합

OutboxPollingScheduler 는 5 서비스 (auth, account, security, membership, admin) 가 각자 구현 없이 공통 base 를 상속. 본 task 변경은 base 하나만 수정하면 5 subclass 자동 반영.

### C. 3 테스트 `@Disabled` 제거 + CI 3회 연속 green

TASK-BE-076 의 partial 에서 복원된 `@Disabled` 를 제거하고 CI 실측.

### D. testing-strategy.md 업데이트

- Scheduler lifecycle convention 명문화
- `outbox.polling.enabled=false` in test profile (선택 Option 2)

## Out of Scope

- 전체 13 integration test 를 AbstractIntegrationTest 로 이전 (별도 task)
- OutboxPollingScheduler 의 폴링 간격/backoff 변경
- 애플리케이션 코드 중 OutboxPollingScheduler 외 다른 스케줄러 변경

---

# Acceptance Criteria

- [ ] `OutboxPollingScheduler` 가 context-scoped executor 로 `shutdown()` 시점에 즉시 중단 (Option 1) 또는 test profile 에서 disable (Option 2)
- [ ] CI 실측에서 `scheduling-1` thread 의 `HikariPool-N Connection is not available` 로그가 관측되지 않음
- [ ] 9 `@Disabled` 제거 + CI 3회 연속 green
- [ ] `./gradlew build` CI green
- [ ] 기존 `OutboxRelayIntegrationTest` 회귀 없음 (Option 2 선택 시 해당 테스트는 override 필요)

---

# Related Specs

- `platform/event-driven-policy.md` (outbox 정책)
- `platform/testing-strategy.md`

---

# Related Contracts

없음

---

# Target Service

- `libs/java-messaging` (OutboxPollingScheduler base)
- `apps/auth-service`, `apps/security-service`, `apps/account-service`, `apps/membership-service`, `apps/admin-service` (subclass 는 미변경 예정이나 test yml 확인 필요)

---

# Architecture

layered 4-layer + shared lib. `libs/java-messaging` 의 scheduler 는 infrastructure 레이어.

---

# Edge Cases

- `@Scheduled(fixedDelayString="...")` 를 완전히 대체하는 것이라, fixed rate vs fixed delay 의미 차이 확인
- `ThreadPoolTaskScheduler` `setWaitForTasksToCompleteOnShutdown(true)` 가 현재 실행 중인 tick 을 기다리므로, 그동안 context destroy 가 블록됨 — `setAwaitTerminationSeconds(5)` 로 상한
- Test context 에서 outbox 관련 테스트 (예: OutboxRelayIntegrationTest) 는 scheduler 가 active 해야 함 — Option 2 선택 시 property override 필요

---

# Failure Scenarios

- Option 1 적용 후에도 다른 scheduler (예: membership expiry) 가 같은 문제 유발 → 한 번에 모든 스케줄러 base 를 이 패턴으로 통합 필요
- Option 2 가 OutboxRelayIntegrationTest 의 `@TestPropertySource` override 가 context cache key 를 바꿔 HikariPool 재생성 반복 유발 가능 — 회피하려면 context-scoped executor 가 더 안전

---

# Test Requirements

- 9 통합 테스트 CI 3회 연속 green
- OutboxRelayIntegrationTest 회귀 없음
- 기존 unit/slice 회귀 없음

---

# Implementation Notes

**진단 근거** (PR #44 실측 log):
```
scheduling-1 ERROR HikariPool-2 - Connection is not available, request timed out after 3000ms (total=0)
scheduling-1 ERROR Communications link failure
scheduling-1 ERROR Unexpected error occurred in scheduled task
  org.springframework.transaction.CannotCreateTransactionException: Could not open JPA EntityManager for transaction
  at OutboxPublisher$$SpringCGLIB$$0.publishPendingEvents(<generated>)
```

HikariPool-4 은 정상 작동 (current context), HikariPool-2/3 은 orphaned — scheduler thread 가 어느 context 의 자원을 참조하는지 명확히 분리돼야 함.

---

# Definition of Done

- [ ] A (+B optional) + C + D 적용
- [ ] CI 3회 연속 green
- [ ] Ready for review
