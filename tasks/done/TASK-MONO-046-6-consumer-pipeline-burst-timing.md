# Task ID

TASK-MONO-046-6

# Title

GAP security-service consumer-pipeline burst timing + byte[] DLQ edge case — 3 IT 메서드 재활성화 (TASK-MONO-046-4 후속)

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

[TASK-MONO-046-4](../in-progress/TASK-MONO-046-4-dlq-producer-classcast.md) PR #232 에서 `DelegatingByTypeSerializer` 적용으로 ClassCastException 제거 완료. 3 of 6 originally-disabled DLQ tests 통과. 그러나 잔존 3 메서드가 CCE 와 무관한 별도 root cause 로 실패 — 본 task 에서 진단 + 수정 + 재활성화.

## 실패한 3 메서드 (046-4 부분회복 후)

| 클래스 | 메서드 | 증상 |
|--------|--------|------|
| `CrossTenantVelocityIntegrationTest` | `tenantABurst_doesNotTriggerTenantBDetection` | 50 events 전송 → 30s 내 `suspicious_events` row 없음 (AssertionError line 135) |
| `DetectionE2EIntegrationTest` | `velocityTriggersAutoLockE2E` | 10 events 전송 → 30s 내 `suspicious_events` row 없음 (AssertionError line 140) |
| `DlqRoutingIntegrationTest` | `invalidBytesRoutedToDlq` | raw byte[] poison → `auth.login.failed.dlq` timeout (Order=2, 60s) |

## 증상 공통점 vs 차이점

- **CrossTenantVelocity + DetectionE2E**: VelocityRule pipeline 이 burst 이벤트 (10~50건) 를 30s 내 처리하지 못함. 동일 consumer 그룹에서 `DirtiesContext` 순서에 따른 consumer group offset 상태, 또는 `auto-offset-reset=latest` race 가능.
- **DlqRoutingIntegrationTest.invalidBytesRoutedToDlq**: producer factory 가 `<String, Object>` 으로 변경된 후 raw `byte[]` 값이 `auth.login.failed.dlq` 에 도달하지 못함 — `DelegatingByTypeSerializer` 의 `byte[]` 분기가 실제 byte[] object 를 받는지 확인 필요. ErrorHandlingDeserializer 가 header 에 raw bytes 를 stash 하고 value null 로 전달하는 경로 vs DLPR 이 직접 byte[] 발행하는 경로 재검토.

## Root cause 가설

1. **burst saturation**: 50/10 event 를 single-partition 로 일괄 전송 시 consumer 처리 backlog 가 30s awaitility timeout 을 초과. 해결: timeout 증가 또는 burst 를 소량으로 줄이거나 consume-confirm wait 추가.
2. **auto-offset-reset=latest race**: consumer 가 partition assignment 를 완료하기 전 producer 가 메시지를 전송하면 latest offset 기준으로 건너뜀. `ContainerTestUtils.waitForAssignment` 이미 호출하지만 단일 partition 수 맞는지 확인 필요.
3. **byte[] DLPR routing edge case**: `DelegatingByTypeSerializer` 의 `byte[]` path 가 `ErrorHandlingDeserializer` 의 null-value + header-stash 경로와 정합성 문제. DLPR 이 `record.value()` 를 발행하려 할 때 null 인 경우 처리.
4. **DirtiesContext 순서 간섭**: `@Order(2)` `invalidBytesRoutedToDlq` 가 `@Order(1)` 직후 실행될 때 Kafka container 또는 consumer group 이 아직 재초기화 중.

---

# Scope

## In Scope

### 진단

- 3 메서드 각각 local Testcontainers 실행 + verbose consumer 로그로 root cause 확인
- `auto-offset-reset`, `ContainerTestUtils.waitForAssignment` partition count, burst delivery timing 분석
- `DelegatingByTypeSerializer` byte[] path — ErrorHandlingDeserializer null-value header route 추적

### Fix 후보 (진단 결과에 따라 택1+)

- awaitility timeout 증가 (30s → 60s) for burst tests
- burst 이벤트 수 축소 + await-per-batch 패턴
- `waitForAssignment(c, actualPartitionCount)` partition count 정확화
- `auto-offset-reset=earliest` 테스트 프로파일 적용
- byte[] DLPR path — `DeserializationException` header bytes 활용 경로 명시적 검증

### 테스트 재활성화

- 3 클래스/메서드 `@Disabled("TASK-MONO-046-6: ...")` 제거
- local + CI 통과 검증

## Out of Scope

- TASK-MONO-046-5 PiiMasking (별 task)
- TASK-MONO-046-1 auth-service SAS 12
- 046-4 의 `KafkaConsumerConfig` DelegatingByTypeSerializer fix 재변경 (fix 는 correct, 이 task 는 downstream 영향만)

---

# Acceptance Criteria

## 통과

1. `CrossTenantVelocityIntegrationTest.tenantABurst_doesNotTriggerTenantBDetection` PASS
2. `DetectionE2EIntegrationTest.velocityTriggersAutoLockE2E` PASS
3. `DlqRoutingIntegrationTest.invalidBytesRoutedToDlq` PASS
4. 나머지 DlqRouting 3 메서드 (Order 1, 3, 4) + SecurityServiceIntegrationTest 6 + LoginHistoryImmutability 2 회귀 0

## CI

5. main CI `Integration (GAP)` Job: 17 PASS / 0 FAIL / 9 skipped (PiiMasking by 046-5) 달성
6. PR description 에 root cause 진단 결과 + fix approach 명시

---

# Related Specs

- [TASK-MONO-046-4](../in-progress/TASK-MONO-046-4-dlq-producer-classcast.md) — **직접 선행** (이 task 의 `@Disabled` 가 046-4 PR 에서 추가됨)
- [TASK-MONO-046-3](../done/) — offset leak 선행 fix
- `projects/global-account-platform/specs/services/security-service/`

---

# Related Contracts

- 없음 (consumer pipeline 내부 동작 — 외부 contract 변경 없음)

---

# Target Service / Component

- `projects/global-account-platform/apps/security-service/src/test/java/com/example/security/integration/CrossTenantVelocityIntegrationTest.java`
- `projects/global-account-platform/apps/security-service/src/test/java/com/example/security/integration/DetectionE2EIntegrationTest.java`
- `projects/global-account-platform/apps/security-service/src/test/java/com/example/security/integration/DlqRoutingIntegrationTest.java`
- 필요 시 `projects/global-account-platform/apps/security-service/src/main/java/com/example/security/infrastructure/kafka/KafkaConsumerConfig.java` (byte[] DLPR path 만)

---

# Edge Cases

1. **partition count mismatch**: `waitForAssignment(c, 1)` 은 partition 이 1개일 때만 안전. Testcontainers Kafka 토픽의 실제 partition count 가 다르면 race 잔존.
2. **cross-class consumer group reuse**: 3 IT class 가 동일 Kafka container 공유 + `@DirtiesContext(AFTER_CLASS)` 로 Spring context 재생성. consumer group offset 가 class 간 누적되면 latest-only 메시지 유실.
3. **DelegatingByTypeSerializer null-value**: ErrorHandlingDeserializer 가 null value + header stash 경로일 때 DelegatingByTypeSerializer 가 null 수신 — NPE 또는 fallback 처리 확인.
4. **byte[] raw value vs header bytes**: DLPR 이 `DeserializationException` header 에서 bytes 를 꺼내 재발행하는 경로와, 직접 byte[] value 로 보내는 경로 혼동 여부.

---

# Failure Scenarios

## A. timeout 증가로 해결 안 됨

VelocityRule pipeline 자체 결함 (e.g., Redis counter key TTL 설정 오류) 일 경우 timeout 증가로 통과 불가 → Redis key 패턴 로그 추가 + counter 증감 직접 검증.

## B. byte[] path 구조 문제

`DelegatingByTypeSerializer` byte[] 분기가 header-stash null-value DLPR path 와 호환되지 않을 경우 — KafkaConsumerConfig errorHandler 에서 byte[] 전용 `KafkaTemplate<String, byte[]>` 분리 유지 고려.

## C. CI 환경 자원 부족

local 통과, CI 실패 패턴이면 Testcontainers startup memory / CPU constraint 원인 — `@ResourceLock` 또는 sequential execution 강제.

---

# Test Requirements

- 3 IT 메서드 `@Disabled` 제거 + PASS
- 전체 security-service integrationTest: 20 total / 17+ PASS / ≤9 skipped (PiiMasking by 046-5) / 0 FAIL
- main CI `Integration (GAP)` Job SUCCESS

---

# Definition of Done

- [ ] Root cause 진단 완료 (3 메서드 각각 원인 명시)
- [ ] Fix 적용 (test timing / byte[] path / partition wait 등)
- [ ] 3 IT 메서드 `@Disabled` 제거
- [ ] local integrationTest PASS 확인
- [ ] main CI `Integration (GAP)` Job SUCCESS 확인
- [ ] knowledge/incidents 단락 갱신
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** — timing + concurrency 분석, Kafka consumer lifecycle 추적, byte[] deserialization pipeline 디버깅.
- **분량 추정**: medium (진단 + 3 test class + 필요 시 KafkaConsumerConfig 소수 수정).
- **dependency**:
  - `선행`: TASK-MONO-046-4 (본 task 의 `@Disabled` 가 046-4 PR #232 에서 추가됨 — 046-4 머지 후 착수).
  - `병렬`: TASK-MONO-046-5 (PiiMasking), TASK-MONO-046-1 (auth SAS).
  - `후속`: 본 task + 046-5 + 046-1 모두 머지 시 main `Integration (GAP)` Job 20/20 milestone.
