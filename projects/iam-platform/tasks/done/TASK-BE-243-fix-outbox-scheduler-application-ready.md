# TASK-BE-243: fix — OutboxPollingScheduler를 ApplicationReadyEvent 기반으로 전환

## Goal

`docker-compose.e2e.yml` 환경에서 account-service 및 security-service 시작 시 다음 race condition 발생:

```
Creating singleton bean 'transactionManager' in thread "outbox-1"
while other thread holds singleton lock for other beans [DataSourceAutoConfiguration]
...
BeanCurrentlyInCreationException: Error creating bean with name 'transactionManager'
```

원인: `libs/messaging-outbox`의 `OutboxPollingScheduler`가 **컨텍스트 초기화 완료 전에 polling 스레드를 시작**한다. 백그라운드 `outbox-1` 스레드가 transactionManager를 사용하려 들면, 메인 스레드는 아직 `DataSourceAutoConfiguration` 락을 잡고 있어 데드락 유사 상황이 발생.

스케줄러 시작 시점을 `ApplicationReadyEvent` 리스너로 미루어, 컨텍스트가 완전히 ready 된 후에만 polling을 시작하도록 변경.

## Scope

**In:**
- `libs/messaging-outbox/src/main/java/com/example/messaging/outbox/OutboxPollingScheduler.java`:
  - `@PostConstruct` 또는 `SmartLifecycle.start()`에서 polling을 시작하던 로직을 `@EventListener(ApplicationReadyEvent.class)`로 이동
  - 또는 `SmartLifecycle.isAutoStartup()`을 false로 두고 `ApplicationReadyEvent`에서 `start()` 호출
- 기존 단위 테스트가 컨텍스트 라이프사이클 시작 시점을 가정하고 있다면 ready 이벤트 발행 시뮬레이션으로 갱신
- 통합 테스트(있다면): account-service 또는 security-service의 outbox 통합 테스트가 정상 동작하는지 확인

**Out:**
- outbox polling 로직 자체 변경 없음 (배치 크기, 인터벌 등)
- `OutboxPublisher` / `OutboxRepository` 등 다른 컴포넌트 변경 없음
- 사용처 서비스(auth, account, security, admin)의 코드 변경 없음

## Acceptance Criteria

- [ ] account-service가 e2e profile로 정상 기동 (transactionManager race 해소)
- [ ] security-service가 e2e profile로 정상 기동
- [ ] auth-service / admin-service에서 회귀 없음 (이미 정상 동작 중일 수 있으나 동일 라이브러리 사용)
- [ ] outbox polling이 컨텍스트 ready 직후 정상 시작 (시작 로그 `OutboxPollingScheduler started`가 컨텍스트 초기화 완료 후 출력)
- [ ] 기존 outbox 단위 테스트 + 통합 테스트 회귀 없음 (`./gradlew :libs:messaging-outbox:test`)
- [ ] 4개 서비스(`auth/account/security/admin`) 빌드 + 단위 테스트 회귀 없음

## Related Specs

- `platform/event-driven-policy.md` (outbox 패턴 규약)
- `specs/services/account-service/architecture.md`
- `specs/services/security-service/architecture.md`
- `libs/messaging-outbox/README.md` (있을 경우)

## Related Contracts

- 없음 (라이브러리 내부 라이프사이클 변경)

## Edge Cases

- `ApplicationReadyEvent`는 web context refresh가 끝난 후 발행됨. non-web Spring Boot 앱에서도 정상 발행 — auth/account/security/admin 모두 web app이므로 문제 없음
- 테스트 환경에서 `@SpringBootTest`로 컨텍스트만 띄우고 즉시 종료하면 `ApplicationReadyEvent`가 발행되지 않을 수 있음 — 이 경우 outbox는 시작되지 않으나 테스트 종료 후 정상 cleanup
- 다중 인스턴스 환경(향후): polling 시작 시점은 그대로 유지하되, leader election은 별개 기제로 처리 (이 태스크 범위 밖)

## Failure Scenarios

- 변경 후 `ApplicationReadyEvent`가 발행되지 않는 환경(예: 초기화 실패)에서는 polling이 시작되지 않음 — 의도된 동작
- 라이브러리 변경이 사용처 서비스의 SmartLifecycle 시작 순서에 영향을 주는 경우: 회귀 테스트로 검증
- `outbox-1` 스레드 풀이 ApplicationReadyEvent 발행 전에 생성·초기화는 되지만 polling만 미뤄지도록 분리 — 스레드 풀 생성 자체가 transactionManager에 의존하지 않는다는 전제 검증 필요

## Implementation Notes

- 기존 코드의 polling 시작 트리거를 grep으로 식별: `OutboxPollingScheduler.java`에서 `@PostConstruct`, `start()`, `scheduleAtFixedRate`, `SmartLifecycle.start` 등
- 시작 로그 메시지("OutboxPollingScheduler started: intervalMs=...")는 ready 이벤트 핸들러로 함께 이동
- 종료 로직(`@PreDestroy` 또는 `SmartLifecycle.stop()`)은 영향 없으므로 그대로 유지

## Risk Assessment

**중간 위험도** — 라이브러리 라이프사이클 변경은 모든 outbox 사용 서비스에 영향. 단위 테스트만으로는 race condition이 재현되지 않을 수 있어 docker-compose.e2e.yml로 실제 4개 서비스 cold start 검증이 권장됨.
