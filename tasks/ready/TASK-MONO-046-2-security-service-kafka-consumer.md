# Task ID

TASK-MONO-046-2

# Title

GAP security-service Kafka 소비자 미처리 회귀 — 5 IT class 17건 deferred

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

[TASK-MONO-046](TASK-MONO-046-gap-integration-residual-31.md) PR `#226` 머지/축소 후, security-service IT 의 5 class **17건**이 동일 root cluster — **Kafka @KafkaListener 가 events 를 처리하지 않음** — 으로 fail. 046 PR 가 적용한 schema/validation/fixture fix 만으로는 회귀 해소 불가. 본 task 가 consumer 미작동 root cause 진단 + production fix.

### 17건 분포

| Test class | 실패 수 | 검증 시나리오 |
|---|---|---|
| CrossTenantVelocityIntegrationTest | 1 | 50 events → tenantA SuspiciousEvent row |
| DetectionE2EIntegrationTest | 1 | 10 events → AUTO_LOCK + WireMock + outbox |
| DlqRoutingIntegrationTest | 4 | poison pill + missing tenant_id → `<topic>.dlq` |
| PiiMaskingIntegrationTest | 6 | account.deleted → masking + outbox |
| SecurityServiceIntegrationTest | 5 | auth.login.* → login_history persist |

5 class 17건 모두 PR `#226` 에서 `@Disabled("TASK-MONO-046-2: ...")` 마킹.

---

# Scope

## In Scope

### 진단 (Phase 1)

- CI run `25378966314` (PR #226) 의 GAP Integration Job stack trace + JUnit XML 수집
- security-service Spring context 시작 로그에서 `@KafkaListener` registration + 컨테이너 start 메시지 확인
- 후보 가설:
  - **(a) ConsumerFactory 전혀 시작 안 됨**: `KafkaConsumerConfig.errorHandler` bean 이 의존하는 `MeterRegistry` 가 test profile 에 미등록 — `@ConditionalOnClass(MeterRegistry.class)` 누락 시 silent fail. 컨테이너는 만들어지나 listener 등록 안 됨.
  - **(b) Group rebalance 영구 hang**: cp-kafka:7.6.0 Testcontainer + 정적 group.id="security-service" 가 Spring context 재구성 (DirtiesContext) 시 그룹 상태 inconsistent — `session.timeout.ms` / `heartbeat.interval.ms` 미설정으로 default 값 너무 길어 60s 안에 join 못함.
  - **(c) Offset 침투 회귀**: `enable-auto-commit: false` + AckMode.BATCH 가 처리 실패 시 commit 안 함. 이전 테스트 클래스의 fail-on-deserialize → infinite retry → consumer block. 다음 클래스의 새 listener 가 같은 group 의 lag 위에 시작 → polling 안 함.
  - **(d) ErrorHandlingDeserializer/StrictJsonStringDeserializer chain 회귀**: `application.yml` 의 `spring.deserializer.value.delegate.class` 가 test profile override 와 충돌하여 deserializer 가 silent fail.

### Fix (Phase 2)

- 가설 (a) 면 `MeterRegistry` 의존성 분리 또는 fallback bean 추가 — `errorHandler` 가 등록되지 않으면 listener container 도 시작 안 됨.
- 가설 (b) 면 application-test.yml 에 short session.timeout.ms + heartbeat.interval.ms + max.poll.interval.ms 적용. 테스트 group.id 를 random UUID 로 분리 (이미 DlqRouting 의 dlqConsumer 만 random — 메인 listener 는 fixed).
- 가설 (c) 면 ErrorHandler 의 `ContainerProperties.AckMode` 명시적 설정 + retryable exception classifier 정리.
- 가설 (d) 면 application-test.yml override 일관성 + delegate class explicit nullification.

### 검증

- WSL2 + Docker Desktop 통합 활성화 (Testcontainers 가용한 환경) → 로컬 reproduce
- 로컬 PASS 후 CI 검증
- 17건 `@Disabled` 제거 + 모든 IT PASS

## Out of Scope

- TASK-MONO-046-1 의 auth-service 12 (SAS-side, 별 cluster)
- LoginHistoryImmutability (046 에서 fix 완료)
- security-service production code 의 비즈니스 로직 변경

---

# Acceptance Criteria

## 부팅 + 통과

1. `:projects:global-account-platform:apps:security-service:integrationTest` PASS — 17 건 모두 `@Disabled` 제거 + 통과 (20/20)
2. main CI `Integration (GAP)` Job 다음 run SUCCESS

## 진단 + 분류

3. PR description 에 root cause 분류 + fix 전략 기록
4. 동일 패턴 재발 방지를 위한 production code / test config 변경 explicit

## 회귀 0

5. 046 / 046-1 시리즈 + auth-service IT 회귀 0
6. `knowledge/incidents/2026-05-05-ci-regression.md` 에 본 task 결과 단락 추가

---

# Related Specs

- [TASK-MONO-046](TASK-MONO-046-gap-integration-residual-31.md) — 직접 선행
- [TASK-MONO-046-1](TASK-MONO-046-1-auth-service-sas-deferred-12.md) — 병렬 follow-up
- `projects/global-account-platform/specs/services/security-service/`
- `projects/global-account-platform/specs/contracts/events/security-events.md`

---

# Related Contracts

- 없음 — production 인터페이스 변경 없음 (test config / consumer infra fix)

---

# Target Service / Component

- `projects/global-account-platform/apps/security-service/src/main/java/com/example/security/infrastructure/kafka/KafkaConsumerConfig.java`
- `projects/global-account-platform/apps/security-service/src/main/resources/application.yml` (consumer config)
- `projects/global-account-platform/apps/security-service/src/test/resources/application-test.yml` (test override)
- 5 IT class (Disabled 제거 + assertion 갱신 if needed)

---

# Implementation Notes

- **첫 단계**: WSL2 + Docker Desktop WSL 통합 활성화 — 로컬 reproduce 환경 회복.
- 2단계: `@KafkaListener` 등록 로그 + group join 로그 + 첫 poll 로그를 DEBUG level 로 출력. 어디서 멈추는지 식별.
- 3단계: 가설 (b) 우선 검증 — Spring 의 default `session.timeout.ms` (10s) / `max.poll.interval.ms` (5min) 가 testcontainer 환경에서 짧지 않은지.
- **검증 명령**:
  ```
  ./gradlew :projects:global-account-platform:apps:security-service:integrationTest
  ```

---

# Edge Cases

1. **가설 (a) 단일 cause**: 1 production fix (errorHandler bean optional MeterRegistry) — small PR.
2. **가설 (b) 단일 cause**: 1 application-test.yml + group.id 전략 — small PR.
3. **복수 cause**: 단계별 fix — medium PR.
4. **Production code 회귀**: 회귀 보고서 + 변경 이력 명시.

---

# Failure Scenarios

## A. WSL Docker 통합 복구 불가

CI 의 raw test report XML / Spring DEBUG 로그를 PR comment 에 상세 dump 후 가설 검증.

## B. 단일 root cause 미식별

Bisect: 차례로 가설을 무력화하는 패치 PR 시리즈로 narrow down.

## C. 가설 (c) 가 root cause — AckMode 회귀

이전 PR (예: #100, #107) 에서 AckMode 변경이 있었는지 git log 로 확인. 회복 가능하면 spec 단순 revert.

---

# Test Requirements

- security-service integrationTest 모두 PASS (20/20, `@Disabled` 제거 후)
- main CI `Integration (GAP)` Job 다음 run SUCCESS 검증
- 회귀 보고서 단락 갱신

---

# Definition of Done

- [ ] 17건 stack trace + Spring context 로그 수집
- [ ] root cause 확정 + fix 전략 PR description
- [ ] cause 별 fix commit
- [ ] 17건 `@Disabled` 제거
- [ ] security-service integrationTest 로컬 PASS
- [ ] main CI `Integration (GAP)` Job SUCCESS 검증
- [ ] 회귀 보고서 단락 갱신
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** — Spring Kafka + Testcontainers + DirtiesContext 상호작용 + group rebalance timing 동시 분석.
- **분량 추정**: small (가설 (a) 또는 (b) 단일 cause). Medium 가능성 있음.
- **dependency**:
  - `선행`: TASK-MONO-046 (`@Disabled` 마커 머지 후 본 task 가 제거)
  - `병렬`: TASK-MONO-046-1 (auth-service 12, 다른 cluster)
  - `후속`: 본 task + 046-1 머지 시 main `Integration (GAP)` Job 100% 통과 — 046 시리즈 완전 종결.
- **WSL repro 의존**: 본 task 진행자는 WSL2 + Docker Desktop WSL 통합이 정상 작동하는 환경에서 진행해야 효율적.
