# Task ID

TASK-BE-076

# Title

infra(test): MySQL Testcontainers + Spring context cache 수명 정렬 — HikariPool-2 `total=0` 재발 방지

# Status

partial

> **종결 (2026-04-21)**: Option 1 (shared `AbstractIntegrationTest`) 을 적용했으나 CI
> 실측 결과 9건 실패 재현. XML artifact 분석으로 구체적 원인 재확정:
>
> - 새 context 의 HikariPool-4 는 shared MySQL container 에 정상 접속 (`localhost:32785/test`)
> - 문제는 `scheduling-1` thread 가 **HikariPool-2 / HikariPool-3 (이전 context 의 orphaned pool)** 에
>   계속 요청을 보내 `total=0` timeout + `CommunicationsException`
> - TASK-BE-073 의 `@PreDestroy` + `AtomicBoolean running` guard 가 작동하지만 **불완전**:
>   `@Scheduled` thread 가 이미 실행 중인 tick 을 중단하지 못하거나, Spring 의 scheduler
>   thread pool 이 context 생명주기를 outlive 하는 것으로 추정
>
> Block A (`AbstractIntegrationTest`) 와 Block D (testing-strategy.md convention) 는
> 유지 — infra baseline 으로 유효. Block B (3 테스트 migration) 도 base 전환 자체는
> 유효하지만 `@Disabled` 복원.
>
> 근본원인 fix 는 **TASK-BE-077** 로 승계 — OutboxPollingScheduler 의 scheduler thread
> 수명 재설계 (context-scoped executor 또는 test profile scheduler disable).

# Owner

backend

# Task Tags

- test
- infra

# depends_on

- TASK-BE-075 (partial, infra improvements merged)

---

# Goal

PR #42 (TASK-BE-075) CI 실측 + XML artifact 분석 결과, 9건 통합 테스트 실패의 실제 근본원인이 다음으로 좁혀짐:

**OAuthLogin 5건 공통 증상**:
- HTTP 503 응답 (expected 200)
- application log 내: `HikariPool-2 - Connection is not available, request timed out after 3000ms (total=0, active=0, idle=0, waiting=0)`
- 원인: `CJCommunicationsException: Communications link failure — The driver has not received any packets from the server`

**핵심 단서**:
- `HikariPool-**2**` 의 존재 = Spring test context cache 가 context 재생성하면서 **새 Hikari 풀을 만들고 있음**
- `total=0` = 풀이 완전히 비어있고 MySQL 서버에 새 connection 자체를 못 맺음
- 이는 stale connection 문제(TASK-BE-075 Block A 대상) 아님 — **MySQL container 가 이미 unavailable** 한 상태

**가설**: 현재 각 integration test 클래스가 독립적으로 `@Container static MySQLContainer` 를 선언하고 `@DynamicPropertySource` 로 URL 을 주입함. Spring test context cache 가 여러 context 를 캐싱해두면서, 특정 context 가 활성화될 때 해당 context 의 `@DynamicPropertySource` 가 referencing 하는 container 와 실제 HikariPool-2 의 URL 이 불일치 하거나, container 가 의도치 않게 stop 됨.

---

# Scope

## In Scope

### A. 진단 강화 (선결)

1. 모든 integration test 클래스의 `@Container` 필드 선언부 + `@DynamicPropertySource` 메서드 grep:
   ```bash
   grep -r "@Container\|@DynamicPropertySource\|static.*MySQLContainer" apps/auth-service/src/test/ apps/security-service/src/test/
   ```
2. 각 클래스별 container 선언이 static 인지 확인 (JUnit 5 에서 non-static 은 test method 마다 재시작 — 현재 증상 설명 가능)
3. Spring context cache key 영향 요소 확인:
   - `@ActiveProfiles` 일관성
   - `@MockitoBean` / `@MockBean` 사용 차이
   - `@SpringBootTest.classes` attribute 차이

### B. 수정 (가설 기반 — 진단 후 재조정 가능)

**Option 1 (권장)**: 공용 `AbstractIntegrationTest` 도입
- `libs/java-test-support` 에 추가 (기존 test support lib 있음, TASK-BE-070 에서 확인)
- 정적 `@Container MySQLContainer MYSQL` + `@Container KafkaContainer KAFKA`
- `@DynamicPropertySource` 로 JDBC URL + Kafka bootstrap server 주입
- 9개 + 관련 integration test 가 상속하도록 수정
- TASK-BE-070 에서 "공용 base class 생성 금지" 라 했으나 **본 task 는 그 금지를 explicit override**
  (069~075 의 integration test 실패 해결을 위한 최후 수단)

**Option 2 (Option 1 실패 시)**: Singleton Container 패턴
- `enum` singleton 에 container 선언 (static field 와 비슷하지만 JVM 전체 1회 initialization 보장)
- `@DynamicPropertySource` 는 singleton 참조
- 공용 base class 없이 각 integration test 가 singleton 참조

**Option 3 (infra 대체)**: `testcontainers-cloud` 또는 `reuse=true` 강제
- 비용/설정 복잡도 증가, 최후 수단

### C. 9 @Disabled 재제거 + CI 3회 연속 green

Block B 수정 후:
1. `OAuthLoginIntegrationTest` (class-level)
2. `DetectionE2EIntegrationTest`
3. `DlqRoutingIntegrationTest`

@Disabled 제거 + CI 3회 연속 green → TASK-BE-062 residual 최종 종결.

### D. testing-strategy.md 추가 업데이트

- 채택한 option (1/2/3) 의 convention 공식화
- 각 integration test 가 공용 container 를 공유한다는 rule 을 "MUST" 로 표기

## Out of Scope

- Testcontainers 실행기 교체 (dind, k3s 등)
- 새 통합 테스트 추가
- TASK-BE-069/072/073/075 (Block A/B/D) 머지된 변경 롤백
- docker-compose 기반 테스트로 전환

---

# Acceptance Criteria

- [ ] 모든 integration test 가 동일한 MySQL/Kafka container 인스턴스를 참조 (A: 진단 결과 확인)
- [ ] `HikariPool-2 total=0` 로그가 CI 에서 관측되지 않음 (pool 이 재생성되지 않거나 재생성돼도 URL 이 valid)
- [ ] 9 테스트 `@Disabled` 제거 + CI 3회 연속 green (062 residual 최종 AC)
- [ ] `platform/testing-strategy.md` 에 container sharing convention 명시
- [ ] 기존 green 테스트 회귀 없음

---

# Related Specs

- `platform/testing-strategy.md`

---

# Related Contracts

없음 (test infra)

---

# Target Service

- `apps/auth-service`
- `apps/security-service`
- `libs/java-test-support` (Option 1 선택 시)

---

# Architecture

test infrastructure. application code 무영향.

---

# Edge Cases

- 공용 base class 도입 시 기존 개별 container 선언 제거 필요 — 누락 시 두 개의 병렬 container 가 떠서 port conflict
- Singleton pattern 은 JUnit 5 + Spring 에서 `@ServiceConnection` 같은 헬퍼와 상호작용 주의
- `@DynamicPropertySource` 가 여러 클래스에서 같은 container 참조 시 Spring context cache key 에 URL 이 포함되므로 cache hit 증가 가능성

---

# Failure Scenarios

- Option 1 적용 후에도 일부 테스트가 context key 달라서 여전히 HikariPool-2 재생성 → `@MockitoBean` override 를 테스트별로 정리하거나 `@DirtiesContext` 로 명시적 recreate
- Kafka container 의 경우 이미 TASK-BE-075 Block B 로 log-wait 적용 — MySQL 쪽이 핵심

---

# Test Requirements

- 9 integration test CI 3회 연속 green
- 기존 13 integration test (Auth/Device/Outbox 등) 회귀 없음
- `./gradlew build` CI green

---

# Implementation Notes

본 task 시작 전 반드시 PR #42 (TASK-BE-075) 의 CI run XML artifact 재확인:
- `gh run download <run-id> --name backend-test-reports --dir build/artifacts/`
- `build/artifacts/backend-test-reports/apps/auth-service/build/test-results/test/TEST-com.example.auth.integration.OAuthLoginIntegrationTest.xml` 의 `<system-out>` 분석

현재 얻은 단서:
- `HikariPool-2` 존재 = 최소 2개의 context 생성됨
- `total=0` = pool 이 커넥션 0개로 시작, MySQL 에 접근 불가
- `Communications link failure` — 서버 사이드에 도달 자체 안 됨

---

# Definition of Done

- [ ] A + B + C + D 전부 적용
- [ ] CI 3회 연속 green
- [ ] Ready for review
