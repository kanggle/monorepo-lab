# Task ID

TASK-BE-075

# Title

infra(test): Testcontainers 인프라 hardening — MySQL Hikari 연결 검증 + Kafka advertised-listeners

# Status

partial

> **종결 (2026-04-21)**: Block A (Hikari validation), Block B (Kafka wait + reconnect),
> Block D (testing-strategy.md docs) 3건은 legitimate infra improvement 로 머지 유지.
> Block C (9 `@Disabled` 제거) 는 CI 실측에서 9건 전부 동일 실패 재현 — revert.
>
> **XML artifact 실측 근본원인** (TASK-BE-074 categorization 보다 구체):
> - OAuthLogin 5건: HikariPool-**2** 가 `total=0` 상태, MySQL 서버 자체에
>   `Communications link failure` ("The driver has not received any packets from the server")
> - Hikari validation 옵션 문제 아님 — **MySQL container 가 테스트 진행 중 unavailable** 상태
> - HikariPool-2 존재 = Spring test context cache 가 context 재생성하면서 새 pool 을
>   만드는데 해당 context 의 `@DynamicPropertySource` 가 가리키는 container 수명/URL
>   가 일치하지 않는 것이 가장 유력한 가설
>
> 근본원인 fix 는 **TASK-BE-076** 에서 처리 (Spring context cache + @Container static
> 수명 정렬 또는 shared base class 도입).

# Owner

backend

# Task Tags

- test
- infra

# depends_on

- TASK-BE-074 (진단 완료)

---

# Goal

TASK-BE-074 의 CI artifact 기반 진단 결과, 9건 통합 테스트 실패의 실제 원인이 **Testcontainers 인프라 불안정** 으로 확정됨:

- **OAuthLoginIntegrationTest 5건**: MySQL Hikari pool 이 stale connection 을 검증 없이 재사용 → `Communications link failure` → `OutboxPollingScheduler.pollAndPublish` 내 `Could not open JPA EntityManager for transaction` → HTTP 503
- **DetectionE2EIntegrationTest 1건 + DlqRoutingIntegrationTest 3건**: Kafka broker 의 advertised-listeners 가 random host port 로 노출되어 Producer/Consumer 가 `localhost/127.0.0.1:<randomPort>` 로 연결 시도하지만 broker listener 와 불일치 → `Node 1 disconnected ... Connection could not be established`

본 task 는 두 인프라 레이어를 hardening 하여 9건을 CI 3회 연속 green 상태로 만든다.

---

# Scope

## In Scope

### A. MySQL Hikari 연결 검증

1. 모든 integration 테스트의 MySQL testcontainer 사용처 (TASK-BE-070 에서 `withStartupTimeout(3min)` 적용한 위치들) 에 Hikari 옵션 추가:
   - `spring.datasource.hikari.validation-timeout=3000` (기본 5s → 3s)
   - `spring.datasource.hikari.connection-test-query=SELECT 1` (드라이버 기본 isValid() 외 백업)
   - `spring.datasource.hikari.max-lifetime=60000` (60s — 테스트 context 생애 짧으므로 connection 재생성 촉진)
   - `spring.datasource.hikari.keepalive-time=30000` (30s — idle connection 주기적 검증)
   - `spring.datasource.hikari.leak-detection-threshold=10000` (10s — connection leak 조기 감지)
2. 적용 위치는 공용 test profile (`application-test.yml` 또는 테스트 리소스의 공통 yml). 서비스별 override 는 필요 시에만.

### B. Kafka advertised-listeners 정합화

1. `KafkaContainer` 선언부 (TASK-BE-070 에서 `Wait.forListeningPort` 적용한 위치들) 에 명시적 listener 설정:
   - `confluentinc/cp-kafka` 이미지의 경우 `withKraft()` + `withListener()` 또는 환경변수 직접 설정:
     - `KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://${HOST}:${PORT}` (Testcontainers 가 제공하는 `getBootstrapServers()` 와 정합)
     - `KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092` (컨테이너 내부)
2. `Wait.forLogMessage("\\[KafkaServer id=.\\] started.*", 1)` 로 더 엄격한 ready signal (현재의 `forListeningPort()` 는 포트만 확인하여 metadata 미전파 상태에서도 통과)
3. Consumer/Producer 재연결 설정 보강:
   - `spring.kafka.consumer.properties.reconnect.backoff.ms=500`
   - `spring.kafka.consumer.properties.reconnect.backoff.max.ms=10000`
   - `spring.kafka.consumer.properties.request.timeout.ms=60000`

### C. 3 테스트 `@Disabled` 제거 + CI 3회 연속 green

- `OAuthLoginIntegrationTest.java` class-level `@Disabled` 제거
- `DetectionE2EIntegrationTest.java` `@Disabled` 제거
- `DlqRoutingIntegrationTest.java` `@Disabled` 제거
- 모두 `@EnabledIf("isDockerAvailable")` 유지

### D. findings 문서화

`platform/testing-strategy.md` Testcontainers conventions 섹션에 본 task 에서 밝혀진 패턴을 추가:
- MySQL container 사용 시 Hikari validation 옵션 세트
- Kafka container 사용 시 advertised-listeners + wait strategy

## Out of Scope

- Testcontainers 실행기 변경 (dind, k3s 등)
- 새 integration test 추가
- TASK-BE-069/072/073 의 application layer 변경 롤백 (그것들은 올바른 구현)
- JUnit platform 업그레이드

---

# Acceptance Criteria

- [ ] MySQL container 사용 통합 테스트에 Hikari validation 옵션 세트 적용
- [ ] Kafka container 에 명시적 `KAFKA_ADVERTISED_LISTENERS` + `Wait.forLogMessage` 적용
- [ ] Consumer/Producer 재연결 설정 보강
- [ ] 9건 `@Disabled` 제거 (`OAuthLogin*` 5, `DetectionE2E` 1, `DlqRouting` 3)
- [ ] `./gradlew :apps:auth-service:test :apps:security-service:test` 로컬 (Docker 가용) green — integration 실제 실행
- [ ] **CI 3회 연속 green** (TASK-BE-062/071/073 의 승계 AC 최종 달성)
- [ ] `platform/testing-strategy.md` 섹션 업데이트
- [ ] 기존 green 테스트 회귀 없음

---

# Related Specs

- `platform/testing-strategy.md`
- `specs/services/auth-service/architecture.md` (TASK-BE-069/072)
- `platform/event-driven-policy.md`

---

# Related Contracts

없음 (test 레이어)

---

# Target Service

- `apps/auth-service` (OAuthLoginIntegrationTest + MySQL/Kafka container)
- `apps/security-service` (DetectionE2E, DlqRouting + Kafka container)

---

# Architecture

test infrastructure. 애플리케이션 코드 불변.

---

# Edge Cases

- `keepalive-time` 이 `max-lifetime` 보다 크면 Hikari 설정 오류 — 30s < 60s 이면 OK
- `KAFKA_ADVERTISED_LISTENERS` 를 override 하면 일부 Testcontainers 버전의 `getBootstrapServers()` 가 실제 advertised address 와 불일치 가능 — Testcontainers 버전 확인 후 내장 helper 사용 권장
- `Wait.forLogMessage` 패턴이 Kafka 버전별로 다름 (cp-kafka 7.5 vs 7.6) — 대안 패턴 `|` 조합으로 여러 버전 커버

---

# Failure Scenarios

- Hikari 옵션 적용 후 테스트 간 state leak → `@DirtiesContext(classMode = AFTER_CLASS)` 고려 (이전 TASK-BE-073 P1 조사 시 불필요하다 판단했으나 재검토)
- Kafka listener override 가 특정 Testcontainers 버전에서 무시됨 → lib 버전 bump 후 재시도
- 3회 연속 green 미달성 시 각 테스트 별로 독립 @Disabled 유지 + 원인별 fix-task 세분화 (partial complete 허용)

---

# Test Requirements

- 9건의 통합 테스트가 CI 3회 연속 green
- CI artifact (JUnit XML, TASK-BE-074 에서 추가됨) 로 재발 시 programmatic 분석 가능
- 기존 green 테스트 회귀 0

---

# Implementation Notes

TASK-BE-074 findings 원문 참조: `tasks/done/TASK-BE-074-diagnose-integration-test-body-failures.md` P0 Findings 섹션. 구체적 에러 로그 (Hikari pool timeout, Kafka Node 1 disconnected 등) 이 설계 근거.

---

# Definition of Done

- [ ] A + B + C + D 전부 적용
- [ ] CI 3회 연속 green
- [ ] Ready for review
