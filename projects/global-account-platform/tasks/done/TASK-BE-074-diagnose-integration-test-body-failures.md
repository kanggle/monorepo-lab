# Task ID

TASK-BE-074

# Title

infra(test): OAuthLogin/DetectionE2E/DlqRouting 테스트 body 레벨 실패 원인 진단 — XML artifact 기반

# Status

ready

# Owner

backend

# Task Tags

- test
- infra

# depends_on

- TASK-BE-069 (merged)
- TASK-BE-070 (merged)
- TASK-BE-072 (merged)
- TASK-BE-073 (superseded — P0 scheduler fix만 유효)

---

# Goal

PR #37 CI 실측(run 24696679115) 결과, TASK-BE-069/070/072/073 모두 머지한 상태에서도 9건이 동일 assertion 실패 재현:

- `OAuthLoginIntegrationTest.java:166` (Google happy path)
- `OAuthLoginIntegrationTest.java:204` (Kakao happy path)
- `OAuthLoginIntegrationTest.java:221` (Microsoft happy path)
- `OAuthLoginIntegrationTest.java:238` (Microsoft email absent → preferred_username fallback)
- `OAuthLoginIntegrationTest.java:261` (Microsoft existing email)
- `DetectionE2EIntegrationTest.java:154` (velocity rule auto-lock)
- `DlqRoutingIntegrationTest` 3건 (malformed JSON / invalid UTF-8 / missing eventId)

가설이 틀렸음: OutboxPollingScheduler shutdown noise 는 부수적 현상이었고, 실제 실패는 **test body 내부 assertion** 에서 발생. stdout 로그에는 stack trace 요약만 있고 expected vs actual 은 **XML artifact 에만 기록**됨.

---

# Scope

## In Scope

### P0 — CI XML artifact 확보 (선결 조건)

1. 최신 실패 CI run 의 test-results artifact 다운로드:
   ```bash
   gh run download <run-id> --name backend-test-results --dir build/artifacts/
   ```
   실패한 최신 run 예: 24696679115 (master 기준 PR #37 최종 실측)
2. `build/artifacts/**/TEST-*.xml` 에서 각 `<failure>` 의 `message` + `<system-out>` 확인
3. 각 실패의 실제 **expected vs actual** 메시지를 본 task file 에 기록

### P1 — 실패 카테고리 분류

각 실패가 어느 카테고리에 해당하는지 판별:

- **WireMock stub mismatch**: 테스트가 기대한 HTTP path/method 와 실제 코드가 보낸 요청이 다름
  (TASK-BE-069/072 orchestration 변경 가능성 — 예: 이제 UseCase 에서 직접 account-service 호출)
- **Assertion data drift**: DB 상태가 기대와 다름 (FK 변경, column default 변경 등)
- **Timing / race**: outbox write 와 assertion 사이 타이밍 race
- **Testcontainers infra**: 여전히 container 불안정

P2 진단 경로는 카테고리에 따라 분기.

### P2 — 카테고리별 fix

WireMock mismatch 인 경우:
- `wireMock.getAllServeEvents()` 로 실제 요청 dump → 기대 stub 과 diff
- UseCase 변경(069/072) 으로 HTTP path/body 가 바뀌었다면 테스트 stub 수정

Assertion data drift 인 경우:
- 기대값과 실제값 차이 분석 → 테스트 수정 또는 구현 수정

Timing/race:
- Awaitility 적용 또는 outbox polling 을 테스트에서 명시 trigger

Testcontainers infra:
- TASK-BE-070 추가 튜닝 (context cache, DirtiesContext 등)

### P3 — 3 테스트 @Disabled 제거 + CI 3회 연속 green

## Out of Scope

- 새 통합 테스트 추가
- TASK-BE-069/072 롤백 (그 설계는 연구원 분석 대상 아님, 가설의 fix 로만 사용)
- Testcontainers 실행기 교체

---

# Acceptance Criteria

- [ ] 최신 CI 실패 run 의 XML artifact 다운로드 + 실제 assertion 메시지 9건 전부 task file 에 기록
- [ ] 각 실패를 4 카테고리 중 하나로 분류
- [ ] 카테고리별 fix 적용 (mismatch 면 stub 수정 / drift 면 assertion/구현 수정 등)
- [ ] 3 테스트 `@Disabled` 제거 + CI 3회 연속 green
- [ ] 회귀 없음

---

# Related Specs

- `platform/testing-strategy.md`
- `specs/services/auth-service/architecture.md` (TASK-BE-069/072 로직)
- `platform/event-driven-policy.md`

---

# Related Contracts

없음 (test/infra 레이어)

---

# Target Service

- `apps/auth-service` (OAuthLoginIntegrationTest)
- `apps/security-service` (DetectionE2E, DlqRouting)

---

# Architecture

test + possibly application-adjacent. 레이어 변경 없음.

---

# Edge Cases

- XML artifact 가 `backend-test-results` 가 아닌 다른 이름일 수 있음 — `gh run view <run-id>` 로 아티팩트 목록 확인 선행
- 실패 카테고리가 섞여 있을 수 있음 (9건 중 5건 WireMock mismatch, 3건 timing, 1건 drift 식) — 카테고리별로 병렬 fix 해야 함
- 069 이전 commit 에서 돌리면 통과하는 테스트가 069 이후 실패라면 그 자체가 강력한 단서

---

# Failure Scenarios

- artifact 가 CI 에 업로드되지 않은 경우 → `gradle-actions/setup-gradle` test report 업로드 설정 확인 후 재실행
- 3회 연속 green 미달성 시 test-by-test 단위 @Disabled 재부착하고 fix-task 세분화

---

# Test Requirements

- CI 3회 연속 green (071/073 AC 승계)
- 로컬 에서는 Docker 가용 시 integration 실행 + pass

---

# Definition of Done

- [ ] 9건 실패의 실제 원인 메시지가 task file 에 기록
- [ ] 카테고리별 fix 적용 + CI 3회 green
- [ ] Ready for review

---

# P0 Findings

CI run 24696679115 의 `backend-test-reports` artifact (XML 아님, **Gradle HTML report**) 를 다운로드 해서 9건의 실제 assertion + stdout 확인. 놀랍게도 stdout 에 **infrastructure 실패** 신호가 뚜렷이 찍혀 있다. **9건 모두 WireMock/assertion drift 아닌 Testcontainers infra 카테고리**.

> 보조 기록: CI workflow 는 `apps/*/build/reports/tests/` 를 업로드하고 있어 raw XML (`build/test-results/**/*.xml`) 은 artifact 에 없었음. HTML 의 `<pre>` 에 expected/actual + stack trace + stdout 가 충분히 포함되어 있어 진단에는 문제 없었다. 후속 개선 여지: TASK-BE-074 follow-up 으로 XML 도 업로드 추가.

## OAuthLoginIntegrationTest — 5건 (googleHappyPath:166, kakaoHappyPath:204, microsoftHappyPath:221, microsoftPreferredUsernameFallback:238, microsoftExistingEmailAutoLink:261)

- Category: **Testcontainers infra (MySQL connection loss)**
- Actual assertion message (5건 전부 동일):
  ```
  java.lang.AssertionError: Status expected:<200> but was:<503>
  	at ... StatusResultMatchers.lambda$matcher$9
  	at ... OAuthLoginIntegrationTest.<method>(OAuthLoginIntegrationTest.java:<line>)
  ```
- stdout evidence (stdout Tab of the HTML report):
  ```
  WARN  com.zaxxer.hikari.pool.PoolBase - HikariPool-3 - Failed to validate connection com.mysql.cj.jdbc.ConnectionImpl@... (No operations allowed after connection closed.).
  ...
  ERROR o.h.e.jdbc.spi.SqlExceptionHelper - HikariPool-2 - Connection is not available, request timed out after 3000ms (total=0, active=0, idle=0, waiting=0)
  ERROR o.h.e.jdbc.spi.SqlExceptionHelper - Communications link failure
  CannotCreateTransactionException: Could not open JPA EntityManager for transaction
    at ... OutboxPublisher$$SpringCGLIB$$0.publishPendingEvents
    at ... OutboxPollingScheduler.pollAndPublish(OutboxPollingScheduler.java:45)
  Caused by: com.mysql.cj.exceptions.CJCommunicationsException: Communications link failure
  ```
  Kafka 도 `Node 1 disconnected. ... Connection to node 1 (localhost/127.0.0.1:32783) could not be established.` 반복 로깅.
- Quick hypothesis: 테스트 클래스 간 **context reuse** 인데 이전 테스트에서 사용한 **Testcontainers MySQL/Kafka 가 이미 종료**된 상태로 Hikari/Kafka client 가 stale port 로 연결 시도. 503 은 `/callback` 이 DB 트랜잭션을 열려다 실패한 결과. TASK-BE-069/072 의 orchestrator refactor 와는 무관 (orchestrator 가 이미 호출 받기 전에 Spring filter/controller 가 DB 트랜잭션을 연다). **categoreically a testcontainers/context-caching bug**, 내가 건드릴 작은 코드 범위 밖.

## DetectionE2EIntegrationTest.velocityTriggersAutoLockE2E (line 154)

- Category: **Timing/race + infra (Kafka listener not established)**
- Actual assertion message:
  ```
  java.lang.AssertionError: [suspicious_events row with AUTO_LOCK]
  Expecting actual not to be empty
    at DetectionE2EIntegrationTest.lambda$velocityTriggersAutoLockE2E$11(DetectionE2EIntegrationTest.java:154)
    at org.awaitility.core.AssertionCondition.lambda$new$0
  ```
  (Awaitility polling loop, 기본 timeout 내에 empty 상태 유지)
- stdout evidence: 컨테이너 생성은 정상 — MySQL PT9s, Kafka PT6.8s 내에 시작, Flyway 7개 migration 도 성공. 그러나 테스트 실행 구간 동안 Kafka consumer/producer 가 `Node 1 disconnected` 반복 (DlqRoutingIntegrationTest stdout 와 동일 패턴).
- Quick hypothesis: Awaitility 기본 timeout 안에 Kafka consumer 가 접속 → 10x `auth.login.failed` 소비 → VelocityRule 트리거 → `suspicious_events` insert 까지 **타이밍 완주 못함**. Kafka broker listener 가 advertised.listeners 가 외부 포트에 맞게 설정되지 않아 client 재접속 루프. TASK-BE-070 의 `Wait.forListeningPort()` 만으로는 **Kafka 내부 broker ready** 까지 보장 X. **Testcontainers infra 카테고리**.

## DlqRoutingIntegrationTest — 3건 (malformedJsonRoutedToDlq:112, invalidBytesRoutedToDlq:143, accountLockedMissingEventIdRoutedToDlq:129)

- Category: **Testcontainers infra (Kafka broker unreachable) — 1m timeout 내 ConditionTimeoutException**
- Actual assertion message (3건 동일 패턴):
  ```
  org.awaitility.core.ConditionTimeoutException: Assertion condition defined as a Lambda expression ...
  Expecting actual not to be empty within 1 minutes.
    at ... DlqRoutingIntegrationTest.assertDlqContainsValue(DlqRoutingIntegrationTest.java:159)
    at ... DlqRoutingIntegrationTest.<method>(...)
  Caused by: java.util.concurrent.TimeoutException
  ```
- stdout evidence: 테스트 시작 시점부터 약 60초 내내:
  ```
  [Consumer clientId=consumer-security-service-*, groupId=security-service] Node 1 disconnected.
  [Consumer] Connection to node 1 (localhost/127.0.0.1:32797) could not be established. Node may not be available.
  ```
  수백 회 반복. DLQ 토픽에 메시지 자체가 consume/produce 되지 않음.
- Quick hypothesis: **Kafka testcontainer 는 container 자체는 up 이지만 broker listener 가 client 가 쓰는 `localhost:32797` 에서 실제로 accept 못함**. testcontainers `KafkaContainer` 의 `getBootstrapServers()` 와 advertised.listeners 가 어긋났거나 container 가 test 중간에 재생성되면서 포트가 달라진 상태. **Testcontainers infra 카테고리**.

## Category Summary

| Category | Count | Tests |
|---|---|---|
| Testcontainers infra (MySQL pool stale) | 5 | OAuthLoginIntegrationTest.* |
| Testcontainers infra (Kafka broker unreachable) | 4 | DetectionE2E + DlqRouting 3건 |
| WireMock stub mismatch | 0 | — |
| Assertion data drift | 0 | — |
| Pure timing/race | 0 | — |

**9건 전부 Testcontainers infra 이며 TASK-BE-069/072 의 transactional refactor 와 인과 관계 없음.** 그 refactor 들은 유효하고, 유지해야 한다.

---

# P1 Analysis

## 핵심 발견

1. **OAuth 5건 503**: stdout 에 선명히 남은 `HikariPool-2/3 Failed to validate connection ... No operations allowed after connection closed` + `Communications link failure` 는 **테스트 context 가 시작된 시점엔 MySQL 컨테이너가 살아 있었으나, 테스트 runtime 중 (또는 이전 테스트 teardown 과 이 테스트 setup 사이) 커넥션이 끊긴 후 Hikari 가 재시도 실패** 패턴. TASK-BE-069/072 와 무관.
2. **Kafka 4건**: `localhost/127.0.0.1:<randomPort>` 반복 `could not be established`. Kafka testcontainer 의 advertised.listeners/bootstrap 바인딩 이슈 또는 container 가 준비 완료 선언을 너무 일찍 했음. TASK-BE-070 이 `Wait.forListeningPort()` 을 추가했지만, `cp-kafka` 이미지의 경우 포트 open ≠ broker accept ready.
3. **둘 다 본 task (069/072/073) 이후 CI 가 새로 드러낸 infra race 문제** 로, "body-level assertion" 해석은 틀렸다. body-level 처럼 보인 이유: MockMvc 어서션이 마지막에 터져서 첫 실패 라인 이 body 로 보였을 뿐.

## Fix Target 판정

- WireMock/stub mismatch 가설 (과제 제시): **false**. `AccountServiceClient` HTTP path/body 를 지금 수정해도 503 은 MySQL pool 이라 해결 안 됨.
- Production code bug: **없음**. Transactional 분리는 정상.
- **Actionable fix**: Testcontainers 설정 추가 강화. 그러나 task 지침 P2 는 "P0/P1 이 명확한 단일 fix 를 가리킬 때만" 이라고 함. 본 조사에서 **단일 행 fix 로 9건을 해결할 확신은 없다** — Kafka advertised.listeners 문제는 Testcontainers `KafkaContainer` 내부 설정이고, MySQL 커넥션 pool stale 은 context-per-class reuse 전략 재검토가 필요. 두 영역 모두 이 task 의 "diagnose" 범위 밖이며 별도 fix task 로 분기해야 맞음.

## 결론

- 9 failures 전부 카테고리 **Testcontainers infra** 로 분류 완료.
- 단일 minimal fix 로 해결 불가 — **P2 은 skip** 하고 main session 이 이어서 infra 재조사 task (TASK-BE-075 suggested) 로 분기할 것을 권장.
- 단, 진단 과정에서 발견한 **CI workflow 업로드 누락** (XML test-results 미업로드) 은 이번에 고쳐 두면 다음 진단이 훨씬 쉬워짐 → P2 가치 있는 minimal change 로 실행.

---

# P2 Actions

**적용한 fix**: `.github/workflows/ci.yml` 에 `apps/*/build/test-results/` + `libs/*/build/test-results/` 를 artifact 업로드 path 에 추가. 다음 실패 run 에서부터 raw JUnit XML 이 artifact 에 포함되어 일관된 programmatic 분석이 가능.

**적용하지 않은 fix** (의도적으로):
- `@Disabled` 제거 — 해당 테스트의 실제 infra 문제는 아직 미해결. 제거하면 바로 빨강.
- Testcontainers 설정 변경 — 진단 범위 초과, 별도 fix task 필요.
- OAuthLoginUseCase/AccountServiceClient 변경 — 해당 없음 (503 은 DB pool 이지 HTTP path mismatch 가 아님).

---

# Recommended Follow-up Task (for main session)

**TASK-BE-075 (suggested)**: Testcontainers infra 재정비
- MySQL: `@DirtiesContext(BEFORE_CLASS)` 또는 container 를 `@ClassRule` 로 고정 + Hikari `validationTimeout` 축소.
- Kafka: `cp-kafka` 이미지에서 broker ready 체크를 `Wait.forLogMessage("started.*", 1)` 로 보강 또는 `confluentinc/cp-kafka` 대신 `testcontainers/KafkaContainer#withEmbeddedZookeeper()` 사용.
- CI 3회 green 달성까지 run.
