# Task ID

TASK-MONO-046-3

# Title

GAP security-service IT 11건 — cross-class consumer-group offset leak fix (TASK-MONO-046-2 Phase 5)

# Status

ready

# Owner

backend / qa

# Task Tags

- test
- code

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

[TASK-MONO-046-2](../done/TASK-MONO-046-2-security-service-kafka-consumer.md) PR #228 Phase 1-4 진단 + 부분 회복 후 잔존 11건 (CrossTenantVelocity 1 + DetectionE2E 1 + DlqRouting 4 + PiiMasking 5) 의 Kafka 컨슈머 cross-class offset leak 해소 — 4 IT class `@Disabled("TASK-MONO-046-2: cross-class consumer-group offset leak")` 마커 제거 + 모두 PASS.

### 046-2 Phase 1-4 진단 결론

CI run `25387218796` (Phase 3) 의 test report XML system-out 분석:

```
[KafkaListenerEndpointContainer#4-0-C-1] o.a.k.c.c.i.LegacyKafkaConsumer
  - [Consumer clientId=consumer-security-service-29, groupId=security-service]
    Seeking to offset 0 for partition auth.login.succeeded-0
[KafkaListenerEndpointContainer#4-0-C-1] c.e.s.i.kafka.KafkaConsumerConfig
  - Sending to DLQ: topic=auth.login.succeeded, eventKey=acc-dlq-001
```

`acc-dlq-001` 은 DlqRoutingIntegrationTest (이전 클래스) 의 test data. SecurityServiceIntegrationTest 의 새 listener 가 group "security-service" 에 join 후 `auto-offset-reset=earliest` + 이전 클래스의 uncommitted offsets 때문에 offset 0 부터 read → 이전 클래스의 events replay → poison pill / detection events 처리 시도 → Redis (이미 stop 된 이전 클래스의 testcontainer) 호출 실패 → DLQ → 시간 소진 → 본 클래스의 메시지 도달 못함 → test await timeout.

Phase 1-3 시도 (모두 무효):
- Phase 1: logback `test` 프로파일 매칭 추가 → 진단 가시성 회복 (이번 root cause 식별의 근거). SecurityServiceIntegrationTest solo PASS (1m27s).
- Phase 2: `@KafkaListener.groupId` 외부화 (`${security.consumer.group-id:security-service}`) + `application-test.yml` 의 `${random.uuid}` → listener 별로 다른 UUID resolve 되어 production semantics 어긋남.
- Phase 3: 4 IT class 에 `@DirtiesContext(AFTER_CLASS)` → 컨텍스트 직렬 teardown 으로도 group offset state 정리 안 됨.

Phase 4 (PR #228 ship): 4 IT class 재 `@Disabled` + Phase 1 logback fix + Phase 2 groupId externalization production code 보전.

본 task 가 Phase 5 — 11건 fix.

---

# Scope

## In Scope

### 진단 (recap)

- CI 환경에서 5 IT class 가 같은 group "security-service" 사용 + `auto-offset-reset=earliest` + `enable-auto-commit=false` + `AckMode.BATCH` 의 조합이 cross-class offset leak 유발.
- 토픽 (auth.login.*, account.deleted, account.locked, auth.token.*) 도 클래스 간 공유.

### Phase 5 fix 후보 (택 1 또는 조합)

- **(A) Per-class consumer group via @DynamicPropertySource UUID** — Phase 2 의 `${security.consumer.group-id:security-service}` 외부화 활용. 각 IT class 의 @DynamicPropertySource 에서 `static final String CONSUMER_GROUP = "test-" + UUID.randomUUID()` 한 번 generate → `registry.add("security.consumer.group-id", () -> CONSUMER_GROUP)`. 한 클래스 내 7 listeners 가 동일 group, 클래스 간 다른 group → 격리.
  - 단점: 매 클래스 fresh group → topic 의 모든 이전 messages replay. account-service `LoginSucceededConsumerIntegrationTest` 패턴 참고 (이미 작동).
  - 단점: random UUID 가 production code path 에 직접 영향 — `@KafkaListener.groupId` SpEL 이 RandomValuePropertySource 의 `${random.uuid}` 를 매 resolve 마다 새로 생성하므로 lambda capture 필수.

- **(B) AckMode.RECORD** — record 단위 commit 으로 mid-batch teardown 시에도 commit 보존. application-test.yml 에 `spring.kafka.listener.ack-mode: record` 적용. 부수효과: production behavior 변동 없음 (test profile only).

- **(C) Per-class topic isolation** — 각 IT class 의 @DynamicPropertySource 에서 `kafkaTemplate.send(...)` 의 topic 도 random suffix 적용 + production listener 의 topic 도 property 로 외부화. 가장 침습적, 그러나 가장 격리됨.

- **(D) `auto-offset-reset=latest` + 명시적 partition assignment wait** — test 가 Spring Kafka `KafkaListenerEndpointRegistry.getListenerContainers()` 통해 partition assignment 기다린 후 메시지 send. tests 별 `@BeforeEach` 추가 필요.

권장: **(A) + (B) 조합** — 격리 + 안전한 commit. 토픽 replay 부담 (B) 로 완화.

### Fix 적용

- 4 IT class (`CrossTenantVelocityIntegrationTest`, `DetectionE2EIntegrationTest`, `DlqRoutingIntegrationTest`, `PiiMaskingIntegrationTest`) 의 `@Disabled` 제거
- @DynamicPropertySource 에 `security.consumer.group-id` 등록 (옵션 A)
- application-test.yml 에 `spring.kafka.listener.ack-mode: record` 추가 (옵션 B)
- 기존 `@DirtiesContext(AFTER_CLASS)` 유지 (Phase 3 잔재, 도움됨)

### 검증

- `:projects:global-account-platform:apps:security-service:integrationTest` 모두 PASS (20/20)
- main CI `Integration (GAP)` Job 안정적 SUCCESS

## Out of Scope

- TASK-MONO-046-1 의 auth-service 12 (SAS-side, 별 cluster)
- account-service / community-service / 다른 서비스의 Kafka 컨슈머 (현재 정상 작동 — account-service `LoginSucceededConsumerIntegrationTest` 8.6s PASS)

---

# Acceptance Criteria

## 부팅 + 통과

1. `:projects:global-account-platform:apps:security-service:integrationTest` PASS — 4 disabled 클래스 모두 `@Disabled` 제거 + 통과 (20/20)
2. main CI `Integration (GAP)` Job 다음 run SUCCESS

## 진단 + 검증

3. PR description 에 fix 옵션 (A/B/C/D 중 어느 것 또는 조합) 적용 + root cause re-statement
4. account-service consumer 동작 (CI 에서 PASS) 와의 차이 명시 (혹시 production code 변경 필요 시)

## 회귀 0

5. 046 / 046-2 / 046-1 시리즈 + auth-service IT 회귀 0
6. `knowledge/incidents/2026-05-05-ci-regression.md` 에 본 task 결과 단락 추가

---

# Related Specs

- [TASK-MONO-046](../done/TASK-MONO-046-gap-integration-residual-31.md) — 직접 선행
- [TASK-MONO-046-2](../done/TASK-MONO-046-2-security-service-kafka-consumer.md) — Phase 1-4 (직접 선행 + diagnosis)
- [TASK-MONO-046-1](TASK-MONO-046-1-auth-service-sas-deferred-12.md) — 병렬 follow-up
- `projects/global-account-platform/specs/services/security-service/`

---

# Related Contracts

- 없음 — production 인터페이스 변경 없음

---

# Target Service / Component

- `projects/global-account-platform/apps/security-service/src/test/resources/application-test.yml`
- 4 IT class 의 `@DynamicPropertySource`
- 필요 시 `projects/global-account-platform/apps/security-service/src/main/java/com/example/security/consumer/*.java` (옵션 C 시)

---

# Implementation Notes

- **첫 단계**: WSL2 + Docker Desktop WSL 통합 활성화로 로컬 reproduce 환경 회복.
- 옵션 (A) 구현 주의:
  ```java
  // 각 IT class:
  private static final String TEST_GROUP_ID = "test-" + java.util.UUID.randomUUID();

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
      ...
      registry.add("security.consumer.group-id", () -> TEST_GROUP_ID);
  }
  ```
  Lambda 가 매 호출마다 같은 capture 된 UUID 반환 → 클래스 내 일관, 클래스 간 다름.
- 옵션 (B) 구현: application-test.yml `spring.kafka.listener.ack-mode: record`.
- **검증 명령**:
  ```
  ./gradlew :projects:global-account-platform:apps:security-service:integrationTest
  ```

---

# Edge Cases

1. **옵션 (A) 가 충분**: 11건 모두 PASS. small PR.
2. **옵션 (A) + (B) 필요**: medium PR.
3. **production code 회귀 발견** (옵션 C): 토픽 외부화로 인한 contract 변경 — spec 갱신 필요.
4. **CrossTenantVelocity 의 50-event burst 가 여전히 timeout**: Spring Kafka container concurrency 또는 partition count 조정 필요.

---

# Failure Scenarios

## A. 옵션 (A) random UUID 가 listener 별로 다른 값 resolve

Phase 2 회귀 패턴. `@DynamicPropertySource` 의 lambda capture 패턴으로 회피.

## B. account.deleted topic replay 영향

PiiMaskingIntegrationTest 가 자기 클래스의 first event 만 처리하면 됨. group 격리되면 OK.

## C. session.timeout.ms / max.poll.interval.ms 회귀

Spring Boot 3.4.x default 가 변경되었을 수 있음. application-test.yml 에 명시적 short timeout 추가 검토.

---

# Test Requirements

- security-service integrationTest 모두 PASS (20/20, `@Disabled` 4 제거 후)
- main CI `Integration (GAP)` Job 다음 run SUCCESS 검증
- 회귀 보고서 단락 갱신

---

# Definition of Done

- [ ] Phase 5 fix 옵션 결정 (A/B/C/D 또는 조합)
- [ ] 4 IT class `@Disabled` 제거
- [ ] @DynamicPropertySource (옵션 A) + application-test.yml (옵션 B) 적용
- [ ] security-service integrationTest 로컬 PASS 또는 CI 검증
- [ ] main CI `Integration (GAP)` Job SUCCESS 검증
- [ ] 회귀 보고서 단락 갱신
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** — Spring Kafka group rebalance + Testcontainers + multi-context interaction 분석.
- **분량 추정**: small (옵션 A 단일이면 4 IT class @DynamicPropertySource 추가). Medium (옵션 A+B 조합).
- **dependency**:
  - `선행`: TASK-MONO-046-2 (Phase 1-4 머지). 본 task 는 Phase 5.
  - `후속`: 본 task + 046-1 머지 시 main `Integration (GAP)` Job 100% — 046 시리즈 완전 종결.
- **Docker 의존**: WSL2 + Docker Desktop WSL 통합 정상 작동 환경 강력 권장.
