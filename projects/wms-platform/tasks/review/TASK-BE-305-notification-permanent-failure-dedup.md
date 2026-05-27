# TASK-BE-305 — notification-service permanent-failure persist dedup (F-L6-1)

Status: review

## Goal

`notification-service` 의 `/refactor-code` dry-run 결과 식별된 F-L6-1 single finding 의 closure. `DeliveryDispatchPerRow.dispatch` 안의 2 permanent-failure catch block (`ChannelNotConfiguredException` + `ChannelPermanentFailureException`) 의 동일 4-line `markFailedPermanent + deliveries.update + outbox.writeDeliveryCompleted + attemptsFailed.increment` 패턴을 private helper method 로 추출.

dry-run 결과 base rate (notification-service 55+ main file scope):
- L0 (layer-violation) = 0
- L1 (auth) = 0 (event-consumer, no HTTP auth)
- L2 (dead-code) = 0
- L3 (long-method ≥30 LOC) = 0 (max ~33 LOC `AlertRoutingService.process`, 이미 phase 분리 + 의도된 orchestrator role)
- L4 (pattern-mismatch) = 0 (Hexagonal port/adapter 깨끗, ADR-MONO-004 BaseEventPublisher 비채택 = JSONB-payload 정당화 inline-doc)
- **L6 (duplication) = 1** (본 task scope)
- (intentional preservation 6개 확인 후 verify exemption — AlertConsumer.handle co-location + DeliveryExecutor 4 test seam + 2-constructor pattern 등 모두 documented intent, scope 외)

## Scope

In:

### Finding 1: F-L6-1 — DeliveryDispatchPerRow permanent-failure persist dedup

`DeliveryDispatchPerRow.dispatch` (L89-124) 안의 2 catch block 이 동일 4-line 패턴:

```java
catch (ChannelNotConfiguredException permanent) {
    delivery.markFailedPermanent("CHANNEL_NOT_CONFIGURED: " + permanent.getMessage(), clock.instant());
    deliveries.update(delivery);
    outbox.writeDeliveryCompleted(delivery, "FAILED_CHANNEL_NOT_CONFIGURED");
    attemptsFailed.increment();
}
catch (ChannelPermanentFailureException permanent) {
    delivery.markFailedPermanent("VENDOR_4XX: " + permanent.getMessage(), clock.instant());
    deliveries.update(delivery);
    outbox.writeDeliveryCompleted(delivery, "FAILED_PERMANENT");
    attemptsFailed.increment();
}
```

신규 private helper:

```java
private void markPermanentAndPersist(NotificationDelivery delivery,
                                     String reasonPrefix,
                                     Exception cause,
                                     String outboxStatus) {
    delivery.markFailedPermanent(reasonPrefix + ": " + cause.getMessage(), clock.instant());
    deliveries.update(delivery);
    outbox.writeDeliveryCompleted(delivery, outboxStatus);
    attemptsFailed.increment();
}
```

2 catch 가 단일 호출로 치환:

```java
catch (ChannelNotConfiguredException permanent) {
    markPermanentAndPersist(delivery, "CHANNEL_NOT_CONFIGURED", permanent, "FAILED_CHANNEL_NOT_CONFIGURED");
}
catch (ChannelPermanentFailureException permanent) {
    markPermanentAndPersist(delivery, "VENDOR_4XX", permanent, "FAILED_PERMANENT");
}
```

Out:

- **`RuntimeException retryable` catch + `handleRetryable` 안의 `DeliveryRetryExhaustedException` catch 의 3-line `update + outbox + counter` 패턴은 scope 외** — `markFailedPermanent` 호출이 없어 부분 overlap. 별도 helper 도입 시 4 catch + success path 가 모두 동일 helper 사용하는 더 큰 reshape 가 필요 (의도된 scope 보존). over-extension 회피.
- **Success path L101-105** (`markSucceeded + update + outbox("SUCCEEDED") + attemptsSucceeded`) 은 부분 overlap 이지만 counter 변수가 다르고 markSucceeded 가 다른 메서드 호출. 동일 helper 적용 시 counter 인자화 필요 = 추가 reshape. 본 task scope 외.
- 다른 service / cluster 변경 0.
- API / event contract / DB schema / domain method signature 변경 0.
- Domain layer / port interface 변경 0.
- Test 변경 = mechanical fixture update 만 (test what 의 verify 는 unchanged).

## Acceptance Criteria

AC-1. `DeliveryDispatchPerRow` 에 신규 private method:

```java
private void markPermanentAndPersist(NotificationDelivery delivery,
                                     String reasonPrefix,
                                     Exception cause,
                                     String outboxStatus)
```

4-line body = `delivery.markFailedPermanent(reasonPrefix + ": " + cause.getMessage(), clock.instant()); deliveries.update(delivery); outbox.writeDeliveryCompleted(delivery, outboxStatus); attemptsFailed.increment();` byte-identical 순서로.

AC-2. `dispatch` 의 2 catch block (ChannelNotConfiguredException L106-110 + ChannelPermanentFailureException L111-115) 가 각각 단일 `markPermanentAndPersist(delivery, prefix, cause, outboxStatus)` 호출로 치환. message prefix 매핑: `CHANNEL_NOT_CONFIGURED` / `VENDOR_4XX` byte-identical. outboxStatus 매핑: `FAILED_CHANNEL_NOT_CONFIGURED` / `FAILED_PERMANENT` byte-identical.

AC-3. `dispatch` 의 RuntimeException catch (retryable path L116-117) + `handleRetryable` private method body unchanged. `markSucceeded` success path L101-105 unchanged. MDC.put/remove + Timer.record finally block unchanged. `@Transactional(propagation = Propagation.REQUIRES_NEW)` annotation byte-unchanged.

AC-4. Domain method `NotificationDelivery.markFailedPermanent(reason, now)` signature unchanged. `DeliveryRepository.update` + `OutboxPort.writeDeliveryCompleted` interface unchanged.

AC-5. CI 19/20 GREEN authoritative (`Integration (master-service + notification-service, Testcontainers)` notification IT 포함; FAILURE = 0).

AC-6. cross-service drift 없음 — `projects/wms-platform/apps/` 의 notification-service 외 다른 6 service + `projects/{scm,global-account,erp,fan,ecommerce-microservices,finance,platform-console}-platform/` + `libs/` 변경 0. (**40회째 zero-retrofit invariant** 검증.)

AC-7. `attemptsSucceeded` + `attemptsRetried` counter 호출 순서 + 횟수 unchanged. `deliveryDuration.record(...)` finally block 위치 unchanged.

## Related Specs

- `projects/wms-platform/specs/services/notification-service/architecture.md` (특히 § Routing & Delivery Pipeline, § Why post-commit delivery)
- `projects/wms-platform/specs/contracts/events/notification-events.md`
- `platform/refactoring-policy.md` § Allowed Refactoring Categories: Reduce Duplication (Medium risk; 본 task = Low risk 단일 in-class 4-line extract)
- `platform/error-handling.md` (notification 에러 코드 매핑)
- `rules/traits/transactional.md` § T3 outbox + T1 idempotency
- `rules/traits/integration-heavy.md` § I3 retry + I5 DLQ
- `.claude/skills/backend/architecture/hexagonal/SKILL.md`
- `.claude/skills/backend/refactoring/SKILL.md`

## Related Contracts

- 본 task 는 contract 변경 0. notification-events.md 의 publish payload 변경 없음.
- Outbox event payload 의 status 문자열 (`SUCCEEDED` / `FAILED_CHANNEL_NOT_CONFIGURED` / `FAILED_PERMANENT` / `FAILED_RETRY_EXHAUSTED`) byte-identical 보존.

## Edge Cases

- **`markFailedPermanent` invariant**: 도메인 method `NotificationDelivery.markFailedPermanent(reason, now)` 가 state-transition 강제 (PENDING/RETRYING → FAILED). helper 안에서 호출 시 정상 적용. caller 측에서 호출 order 가 동일 — `markFailedPermanent` 가 `deliveries.update` 이전이어야 함 (snapshot 갱신 후 persist).
- **`outbox.writeDeliveryCompleted` 의 status 문자열**: spec L46-49 의 `wms.notification.delivered.v1` event payload 의 `status` field 가 이 string 사용. byte-identical 보존 의무. 새 status 도입 없음.
- **MDC + Timer**: helper 안에 들어가지 않음 (caller 의 finally block 유지). helper 는 exception handling 책임만, MDC / Timer 의 lifecycle 은 caller.
- **`@Transactional(REQUIRES_NEW)`**: helper 는 같은 bean 안 private method — Spring AOP 의 self-invocation 함정 없음 (이미 caller 의 outer @Transactional scope 안에서 실행). proxy 우회 X. BE-300 의 ProjectionConsumerSupport 와 다른 점 — 본 task 는 같은 bean 안 private method, BE-300 은 utility class.
- **DeliveryRetryExhaustedException catch in handleRetryable**: 별도 catch (markRetryable 가 throw). 본 task scope 외 명시.

## Failure Scenarios

- **helper 안에서 markFailedPermanent 호출 후 update 누락**: order 가 byte-identical 보존 의무. AC-1 의 4-line body 정확한 순서 + AC-7 의 counter call 순서.
- **prefix 또는 status string 오타**: byte-identical 매핑 표 (CHANNEL_NOT_CONFIGURED → FAILED_CHANNEL_NOT_CONFIGURED / VENDOR_4XX → FAILED_PERMANENT) AC-2 명시. 테스트 (NotificationOutboxIT 또는 SlackChannelAdapterIT) 가 outbox event payload 검증.
- **새 catch block 추가 시**: 본 task 가 extract 한 helper signature 가 다른 catch (`DeliveryRetryExhaustedException` + RuntimeException retryable) 에 적용 가능하나 부분 overlap 으로 scope 외 — 미래 task 에서 over-extension 검토 가능.

## Approach Notes

- Refactoring policy § Allowed Refactoring Categories: Reduce Duplication (Medium risk per policy, 본 task = Low risk 단일 in-class 4-line extract, no behavior change risk).
- BE-301 / BE-304 의 utility class extraction (cross-class) 와 다른 점: 본 task = same-class private method extraction (in-class). public surface 변경 0.
- Catch handler dedup 의 sweet-spot: 2-3 catch block 의 동일 4-5 line block 이 typical. 본 case 는 2 catch + clean parameter mapping (prefix + outboxStatus 만 다름).
- 잔존 success path + retryable path + retry-exhausted path 의 3-line `update + outbox + counter` 패턴은 별도 helper 분리 가능 (counter 인자화 필요) 하지만 본 task 범위 외 명시.

## 분석/구현 권장 모델

- 분석=Opus 4.7 (verification + 6 intentional preservation 검증)
- 구현 권장=Sonnet 4.6 (private method extract + 2 catch block 치환, mechanical)
