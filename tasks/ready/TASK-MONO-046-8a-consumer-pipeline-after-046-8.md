# Task ID

TASK-MONO-046-8a

# Title

GAP security-service consumer-pipeline 3 deferred — fix on top of 046-8 Phase 0 instrumentation

# Status

ready

# Owner

backend / qa

# Task Tags

- code
- test

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

[TASK-MONO-046-8](../in-progress/TASK-MONO-046-8-consumer-pipeline-deeper-investigation.md) Phase 0 (branch `fix/mono-046-8-consumer-pipeline-deeper`) 가 단일 cold-start cycle 에서 byte[] DLPR test 를 concrete signal 로 재현 + production-side instrumentation 을 main 에 진입시킬 준비를 완료. Phase 0 의 4 변경 (3 production diagnostic + 1 build-side) 은 환경 회복 시점의 burn 비용을 크게 줄임:

- `KafkaConsumerConfig.containsCause(Throwable, Class)` 가 cause-chain 을 walking — 직전 shallow `getCause() instanceof DeserializationException` 이 nested wrapper 놓치던 것 fix.
- `dlqTemplate.setProducerListener(new ProducerListener<>() { onError → log.error })` 가 silent DLQ publish 실패를 노출.
- destinationResolver 가 `valueClass=...` 추가 logging.
- `security-service/build.gradle` testcontainers BOM 1.21.3 override.

본 task 는 Phase 0 누적된 진단 단서 위에서 **3 deferred test 의 root cause 를 확정 + 최소 fix + 회귀 0** 를 달성한다.

**전제**: 환경 회복 (CI Linux runner 또는 안정적 Docker 환경). 자세한 cycle 비용은 `project_testcontainers_docker_desktop_blocker.md` 메모리 참조.

---

# Phase 0 누적 진단 단서

## DlqRoutingIntegrationTest.invalidBytesRoutedToDlq (byte[] DLPR)

cycle3 1 cold-start window 결과:
- `Sending to DLQ: topic=auth.login.failed, eventKey=acc-dlq-002, error=Listener method ... threw exception` 로그 출력 (DLPR destinationResolver 까지 도달)
- 그러나 `auth.login.failed.dlq` topic 에 60s 동안 record 0건 (Awaitility ConditionTimeoutException)
- destinationResolver `else` branch 분류 (`DeserializationException` 도 `MissingTenantIdException` 도 아님)
- 같은 cycle 의 control 3 tests (Order 1, 3, 4) 모두 PASS — DLPR happy path 자체는 동작

**가장 가능성 높은 가설 (fix candidate 1차)**: `StrictJsonStringDeserializer.deserialize(byte[])` 가 raw `org.apache.kafka.common.errors.SerializationException` throw → ErrorHandlingDeserializer 가 catch 후 `recoverFromSupplier(topic, null, data, e)` 로 `DeserializationException(message, data, isKey, ex)` wrap 하여 헤더에 stash. **그러나 wrap 시 cause-chain 깊이 차이로 destinationResolver 의 first if 가 false 반환** → `else` (other counter) 로 분류 → **`outcome=other` 로 increment** 후 publish.

publish 자체는 시도했지만 `.dlq` 에 도달 못한 것은 별개 문제. 가설 후보:
- (i) DLPR 가 byte[] 보존 publish 시 `DelegatingByTypeSerializer` 의 byte[] dispatch silent fail → `setProducerListener.onError` 가 노출 (Phase 0 instrumentation 효과)
- (ii) record.value() = null 인데 vDeserEx.getData() 가 SerializationException constructor 에 byte[] data 안 넣어서 null → DLPR 가 null payload 로 publish 시도 → broker 가 받음 → assertion mismatch

**다음 cycle 진단**: ProducerListener.onError 출력 + valueClass log 1줄로 (i)/(ii) 결정적 분리 가능. 환경 회복 즉시.

## CrossTenantVelocityIntegrationTest / DetectionE2EIntegrationTest (burst tests)

미진단 — Phase 0 의 1 cold-start window 가 byte[] DLPR test 단독 reproduce 에 소진. burst tests 는 추가 cycle 필요.

기존 가설 (TASK-MONO-046-8 본문 그대로):
1. **Offset race** — consumer commit-before-VelocityRule
2. **Redis counter cross-context reset** — `@DirtiesContext` 가 Redis 상태 누수
3. **Consumer group collision** — production `@KafkaListener` 가 hardcoded `groupId="security-service"` (또는 `application.yml` group-id) → SCM 의 `${random.uuid}` 패턴 미적용 → cross-class offset 누수
4. **VelocityRule threshold race** — counter 증가 vs 결정 persisted 의 race window

**가장 가능성 높은 가설 (fix candidate 1차)**: #3. SCM IT 가 같은 패턴에서 안정 작동하는 reference (procurement-service `OutboxRelayIntegrationTest` 의 per-class `CONSUMER_GROUP = "it-outbox-relay-" + UUID.randomUUID()`). security-service 도 같은 override 적용 — application-test.yml 의 `spring.kafka.consumer.group-id: test-security-service-${random.uuid}` 가 이미 있다면 production `@KafkaListener` 가 그걸 픽업하는지 확인 필수 (annotation 기반 listener 가 properties 보다 hardcoded 우선일 수 있음).

---

# Scope

## In Scope

### Phase 1 — byte[] DLPR root cause 확정 + fix

1. 환경 회복 즉시 `DlqRoutingIntegrationTest.invalidBytesRoutedToDlq` 단독 cycle 1 회 — Phase 0 instrumentation 의 ProducerListener.onError + valueClass 출력 capture
2. 가설 (i)/(ii) 결정적 분리:
   - `DLQ publish FAILED for topic=... valueClass=...` 로그 있음 → (i) DelegatingByTypeSerializer dispatch fail
   - 로그 없음 + record valueClass=null + dlq consumer 받은 record value 가 null/empty bytes → (ii) byte[] 보존 실패
3. minimal fix 적용:
   - (i) 시: `byte[].class → ByteArraySerializer` 명시 등록 검증 (이미 있으면 wrap chain 다른 곳)
   - (ii) 시: `AbstractAuthEventConsumer.processEvent(...)` 에 `record.value() == null` null-guard + DeserializationException 던져 retry-skip path 활성

### Phase 2 — burst tests root cause 확정 + fix

4. `CrossTenantVelocityIntegrationTest` + `DetectionE2EIntegrationTest` 의 production `@KafkaListener` group-id 동작 점검:
   - 만약 hardcoded `groupId="security-service"` 면 SCM 패턴 답습으로 SpEL 표현식 `groupId="${spring.kafka.consumer.group-id:security-service}"` 로 변경 (production behaviour 유지 + test override 가능)
   - 또는 `application-test.yml` 의 `${random.uuid}` 가 정상 동작하는지 검증
5. cold-start cycle 1 회 — 양 burst test 결과 capture
6. 결과에 따라 추가 1-2 cycle:
   - PASS → ✅ Phase 2 종결
   - 같은 timeout → 가설 #1 / #2 / #4 진단으로 전환 (Redis state dump / consumer offset commit timing 로그 추가)

### Phase 3 — `@Disabled` 제거 + main 진입

7. 3 deferred test 의 `@Disabled` annotation 제거
8. local cycle 1 회 PASS 확인
9. CI `Integration (GAP)` Job 1 회 PASS 확인 (CI Linux runner 가 unblocked)

## Out of Scope

- 046-7 / 046-7a 영역 (auth-service SAS) — 별 task
- 046-8 Phase 0 의 instrumentation 추가 변경 (이미 main 진입 후 고정) — 단순 활용
- 새 production behaviour (예: 새 metric / 새 channel) — 본 task 는 fix-only

---

# Acceptance Criteria

## 통과

1. `DlqRoutingIntegrationTest.invalidBytesRoutedToDlq` PASS — byte[] poison → `auth.login.failed.dlq` 도달 + `value().equals(poison)` 검증
2. `CrossTenantVelocityIntegrationTest.tenantABurst_doesNotTriggerTenantBDetection` PASS
3. `DetectionE2EIntegrationTest.velocityTriggersAutoLockE2E` PASS
4. PR description 에 byte[] DLPR root cause 진단 결과 + burst tests root cause 진단 결과 + 적용 fix 명시

## 회귀 없음

5. 다른 17 security-service IT 회귀 0 (Phase 0 머지 후 baseline 17/20 PASS)
6. 다른 GAP service IT 회귀 0
7. main `Build & Test (JDK 21)` + `Integration (GAP)` Job pre-existing PASS 유지

## CI

8. `Integration (GAP, Testcontainers)` Job security-service: 20 tests — **20 PASS / 0 FAIL / 0 DISABLED**

---

# Related Specs

- [TASK-MONO-046-8](../in-progress/TASK-MONO-046-8-consumer-pipeline-deeper-investigation.md) — **직접 선행** (Phase 0 partial diagnostics + 환경 blocker 기록 + Next-cycle entry plan)
- [TASK-MONO-046-6](../done/TASK-MONO-046-6-consumer-pipeline-burst-timing.md) — Phase 1 timeout 30→60s 가설 deterministically 반증
- [TASK-MONO-046-3](../done/TASK-MONO-046-3-...) — per-class consumer group 패턴 확립 (SCM 답습)
- `projects/global-account-platform/apps/security-service/specs/` — service architecture

---

# Related Contracts

- 없음 (test-only fix + production code 작은 fix — 외부 contract 변경 없음)

---

# Target Service / Component

- `projects/global-account-platform/apps/security-service/src/test/java/com/example/security/integration/CrossTenantVelocityIntegrationTest.java` — `@Disabled` 제거
- `projects/global-account-platform/apps/security-service/src/test/java/com/example/security/integration/DetectionE2EIntegrationTest.java` — `@Disabled` 제거
- `projects/global-account-platform/apps/security-service/src/test/java/com/example/security/integration/DlqRoutingIntegrationTest.java` — Order=2 `@Disabled` 제거
- `projects/global-account-platform/apps/security-service/src/main/java/com/example/security/consumer/AbstractAuthEventConsumer.java` — 가설 (ii) 시 null-guard 추가
- `projects/global-account-platform/apps/security-service/src/main/java/com/example/security/consumer/*Consumer.java` — 가설 #3 (burst tests) 시 SpEL group-id 변경
- `projects/global-account-platform/apps/security-service/src/main/java/com/example/security/infrastructure/kafka/KafkaConsumerConfig.java` — 가설 (i) 시 serializer 매핑 보강 (이미 byte[]+String 있음 — wrap chain 다른 곳일 가능성)

---

# Edge Cases

1. **Phase 1 의 가설 (i)/(ii) 가 둘 다 동시에 fail**: drag chain 이 deeper. ProducerListener.onError 가 ClassCastException 외 다른 exception 노출 가능. `AbstractAuthEventConsumer.processEvent()` 의 catch (JsonProcessingException) 가 IllegalArgumentException (null input) 을 못 잡고 propagate 하는 추가 경로 가능 → null-guard 가 필수.

2. **Phase 2 의 가설 #3 으로 burst tests fix 안 됨**: per-class group-id 적용했는데도 같은 timeout. → 가설 #1 (commit-before-rule race) 진단으로 전환. consumer 의 offset commit 시점을 `MANUAL` ack-mode 로 변경 + `applicationService.applyXxx(...)` 후 ack 검증.

3. **Phase 1 fix 가 burst tests 에 회귀**: `AbstractAuthEventConsumer` null-guard 가 정상 path 의 record.value() != null 시에도 영향 주면 안 됨. test 로 happy path 재검증.

4. **Phase 0 Resilience4j circuit breaker IT 누수**: SCM-BE-002d 패턴 참조 — `@DirtiesContext(AFTER_CLASS)` + Resilience4j registry per Spring context. 본 task 의 fix 가 새 누수 만들지 않는지 확인.

5. **6-cycle 임계값 (TASK-MONO-046-7 11-cycle burn 학습)**: cycle 6 후에도 pass 안 되면 production code architectural 결함 가능성. spec deviation 검토 (예: `@KafkaListener` 자체 추상화 변경, Spring Kafka 버전 bump 등).

---

# Failure Scenarios

## A. byte[] DLPR 가 ErrorHandlingDeserializer wrap 자체 안 함

가설 (i)/(ii) 모두 wrap 가정. 만약 ErrorHandlingDeserializer 가 SerializationException 을 wrap 안 하고 그대로 throw 한다면 (Spring Kafka 버전 bug 또는 misconfiguration) listener 진입 자체 안 함 + DLPR 호출 안 함 — 그러나 cycle3 log 에 `Sending to DLQ` 명확히 보였으니 wrap 은 됨. 본 시나리오 가능성 낮음.

## B. Burst tests 의 production code 변경이 production OAuth-flow 에 영향

`@KafkaListener(groupId="${spring.kafka.consumer.group-id:security-service}")` SpEL 표현식 변경은 production application.yml 에서 정상 group-id 로 fallback. 영향 0. 그러나 SpEL parsing 실패 시 startup fail — startup test 1 회 검증 필수.

## C. CI `Integration (GAP)` Job 가 path-filter 로 SKIP

본 task 의 변경이 모두 GAP security-service 영역 → path-filter 자동 활성화. SKIP 가능성 0.

## D. Phase 0 instrumentation 머지 후 production observability 영향

`ProducerListener.onError` 가 ERROR 레벨 로그 출력 — 이미 정상 path 에서 호출 안 됨 (실패 시만). production noise 0. 단 vendor outage 시 log 폭증 — Resilience4j circuit breaker 가 차단해서 N 회 후 자동 정지.

---

# Test Requirements

- 3 IT method `@Disabled` 제거 + 모두 PASS
- 다른 security-service IT 회귀 0 (17/20 → 20/20)
- 다른 GAP service IT 회귀 0
- main `Integration (GAP)` Job security-service: 20 PASS / 0 FAIL / 0 DISABLED

---

# Definition of Done

- [ ] Phase 1 byte[] DLPR root cause 확정 + fix 적용
- [ ] Phase 2 burst tests root cause 확정 + fix 적용
- [ ] 3 `@Disabled` annotation 제거
- [ ] local 또는 CI integration test PASS 확인
- [ ] PR description 에 cluster 별 root cause 진단 + fix approach 명시
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** — Spring Kafka ErrorHandlingDeserializer + DLPR header chain + cause chain wrapper 분석 필요. 046-8 Phase 0 의 instrumentation 활용으로 burn 비용 절감 가능하지만 진단 자체는 여전히 architectural depth.
- **분량 추정**: small-medium (production code 변경 ≤ 2 파일, test 변경 ≤ 3 파일, ≥ 1-2 fix cycle).
- **dependency**:
  - `선행`: TASK-MONO-046-8 Phase 0 (branch `fix/mono-046-8-consumer-pipeline-deeper`) — main 머지 필수. 본 task 는 그 위 fix.
  - `병렬`: TASK-MONO-046-7a (auth-service SAS architectural refactor) — 영역 무관.
  - `후속`: 본 task + 046-7a 모두 머지 시 main `Integration (GAP)` Job 전체 GREEN milestone.
- **D4 churn freeze 면제**: 메모리 [`project_monorepo_template_strategy.md`] 의 D4 = "TASK-MONO-046-7/8 면제" 정책 자연 확장 — 046-8a 도 같은 카테고리 (regression fix path) 라 면제.
- **target cycle 수**: ≤ 3 CI cycle (Phase 0 instrumentation 덕에 byte[] DLPR 1 cycle, burst tests 1-2 cycle 예상). 6-cycle 임계값 초과 시 spec deviation 검토.
