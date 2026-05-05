# Task ID

TASK-MONO-046-4

# Title

GAP security-service DLQ producer ClassCastException — String 값 직렬화 (TASK-MONO-046-3 Phase 8 분리)

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

[TASK-MONO-046-3](../done/TASK-MONO-046-3-...md) PR #230 Phase 7 가 cross-class consumer-group offset leak 해소 + SecurityServiceIntegrationTest 6/6 PASS 회복. 그러나 잔존 6건 (CrossTenantVelocity 1 + DetectionE2E 1 + DlqRouting 4) 은 동일 root cause — **DLQ producer 의 `ByteArraySerializer` 가 consumer value (String) 를 cast 못함**:

```
[KafkaListenerEndpointContainer#3-0-C-1] ERROR ...
  Caused by: java.lang.ClassCastException:
    class java.lang.String cannot be cast to class [B
```

`KafkaConsumerConfig.errorHandler` 의 DLQ producer:

```java
producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
KafkaTemplate<String, byte[]> dlqTemplate = new KafkaTemplate<>(...);
```

원래 의도: `ErrorHandlingDeserializer` 가 deserialization fail 시 raw bytes 를 record header 에 stash, value 는 null. DLPR 이 header 의 byte[] 를 사용하여 DLQ 발행. ByteArraySerializer 적합.

문제: deserialization 은 성공했으나 처리 중 실패한 record (예: MissingTenantIdException, IllegalArgumentException, ClassCastException) 의 경우 value 는 deserialize 된 String 객체. ByteArraySerializer 로 String → byte[] cast 불가 → ClassCastException → DLQ 발행 실패 → 무한 retry → test timeout.

본 task 가 DLQ producer 를 String + byte[] 둘 다 처리하도록 수정.

---

# Scope

## In Scope

### Production fix (KafkaConsumerConfig)

옵션 (택 1):

- **(A)** `DelegatingByTypeSerializer` 사용 — `Map.of(byte[].class, new ByteArraySerializer(), String.class, new StringSerializer())`. 두 type 자동 분기.
- **(B)** Custom `Serializer<Object>` impl — instanceof 검사 후 분기.
- **(C)** `ToStringSerializer` 단일 사용 — String/Object → bytes 자동 변환. 단점: byte[] header path 도 String 로 변환됨 (raw bytes 보존 무효).
- **(D)** DLPR 의 `processFailedRecord` 또는 record pre-processor 에서 value 를 byte[] 로 변환.

권장: **(A)** — 가장 명시적, raw bytes path 보존.

### 테스트 활성화

- 4 IT class (`CrossTenantVelocityIntegrationTest`, `DetectionE2EIntegrationTest`, `DlqRoutingIntegrationTest`) `@Disabled("TASK-MONO-046-4: ...")` 제거 + 통과 검증
- PiiMasking 은 별 이유 (TASK-MONO-046-5) 이므로 본 task 외

### 검증

- `:projects:global-account-platform:apps:security-service:integrationTest` 6건 추가 PASS (현재 SecurityServiceIntegrationTest 6 + LoginHistoryImmutability 2 + 추가 6 = 14/20, PiiMasking 5 만 잔존)
- main CI `Integration (GAP)` Job 안정적 SUCCESS

## Out of Scope

- TASK-MONO-046-5 의 PiiMasking trigger conflict (별 cluster)
- TASK-MONO-046-1 의 auth-service SAS 12

---

# Acceptance Criteria

## 부팅 + 통과

1. 6 IT 메서드 (CrossTenantVelocity 1 + DetectionE2E 1 + DlqRouting 4) `@Disabled` 제거 + PASS
2. main CI `Integration (GAP)` Job 다음 run SUCCESS

## 진단 + 검증

3. PR description 에 fix 옵션 (A/B/C/D 중 어느 것) 적용 + ClassCastException stack trace 인용
4. production code 영향 명시 — DLQ 발행 path 가 정확히 어떻게 변하는지

## 회귀 0

5. 046 / 046-2 / 046-3 시리즈 + auth-service IT 회귀 0
6. `knowledge/incidents/2026-05-05-ci-regression.md` 에 본 task 결과 단락 추가

---

# Related Specs

- [TASK-MONO-046-3](../done/TASK-MONO-046-3-...md) — 직접 선행
- `projects/global-account-platform/specs/services/security-service/`

---

# Target Service / Component

- `projects/global-account-platform/apps/security-service/src/main/java/com/example/security/infrastructure/kafka/KafkaConsumerConfig.java`
- 3 IT class (`@Disabled` 제거)

---

# Implementation Notes

- 옵션 (A) 구현 예:
  ```java
  Map<Class<?>, Serializer<?>> typeMap = Map.of(
      byte[].class, new ByteArraySerializer(),
      String.class, new StringSerializer()
  );
  Serializer<Object> dlqValueSerializer = new DelegatingByTypeSerializer(typeMap);
  ```
- KafkaConsumerConfig 의 `errorHandler` bean 생성에서 `producerProps.put(VALUE_SERIALIZER_CLASS_CONFIG, ...)` 대신 `KafkaTemplate` 의 `setProducerListener` 또는 `ProducerFactory` setter 활용.
- 실제로는 `Serializer<Object>` 를 받는 `DefaultKafkaProducerFactory<String, Object>` constructor + 명시적 serializer 인스턴스 전달 패턴이 깔끔.

---

# Edge Cases

1. **byte[] path 도 보존**: EHD-failed deserialize 시 header bytes path 가 그대로 작동해야 함.
2. **Producer factory 호환**: `KafkaTemplate<String, byte[]>` → `KafkaTemplate<String, Object>` 로 변경 필요 (bean type 변경).
3. **DLPR ConfigurableException**: 옵션 (A) 의 DelegatingByTypeSerializer 가 unknown type 만나면 fail-fast — 기본값 fallback 추가 검토.

---

# Failure Scenarios

## A. 옵션 (A) 단일이면 small PR

KafkaConsumerConfig 단 하나 수정. 4-line diff 정도.

## B. ProducerFactory bean type 충돌

`DefaultKafkaProducerFactory<String, byte[]>` → `<String, Object>` 변경 시 다른 호출처 영향. KafkaConsumerConfig 내부에서만 사용하면 격리됨.

---

# Test Requirements

- 6 IT 메서드 PASS (`@Disabled` 제거 후)
- main CI `Integration (GAP)` Job 다음 run SUCCESS 검증
- 회귀 보고서 단락 갱신

---

# Definition of Done

- [ ] DLQ producer fix 옵션 결정 + 적용
- [ ] 3 IT class `@Disabled` 제거
- [ ] security-service integrationTest 추가 6 통과
- [ ] main CI `Integration (GAP)` Job SUCCESS 검증
- [ ] 회귀 보고서 단락 갱신
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Sonnet** 가능 — 단일 production fix + 3 IT class @Disabled 제거. (Opus 도 가능, 단순 case.)
- **분량 추정**: small (단 1 production file + 3 test class).
- **dependency**:
  - `선행`: TASK-MONO-046-3 (이미 review/done).
  - `병렬`: TASK-MONO-046-5 (PiiMasking trigger), TASK-MONO-046-1 (auth SAS).
  - `후속`: 본 task + 046-5 + 046-1 머지 시 main `Integration (GAP)` Job 100% milestone.
