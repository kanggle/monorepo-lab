# Task ID

TASK-BE-078

# Title

fix(test): TASK-BE-076 review — AbstractIntegrationTest 위치 오류 + 미이행 테스트 마이그레이션

# Status

ready

# Owner

backend

# Task Tags

- test
- infra

# depends_on

- TASK-BE-076 (partial, done)

---

# Goal

TASK-BE-076 리뷰에서 발견된 두 가지 코드 수준 문제를 수정한다.

1. **`AbstractIntegrationTest` 소스셋 오류**: `libs/java-test-support/src/main/java/` 에 위치하고
   있어 Testcontainers 가 프로덕션 런타임 의존성으로 누출된다. `src/test/java/` 로 이동해야 한다.
2. **미이행 테스트 마이그레이션**: `platform/testing-strategy.md` 의 Container Lifecycle MUST 규칙에
   따라 모든 integration test 가 `AbstractIntegrationTest` 를 상속해야 한다. 그러나
   `AuthIntegrationTest`, `OutboxRelayIntegrationTest`, `DeviceSessionIntegrationTest`,
   `SecurityServiceIntegrationTest` 는 여전히 각자 `@Container static MySQLContainer` /
   `KafkaContainer` 를 선언하고 있다.

---

# Scope

## In Scope

### A. `AbstractIntegrationTest` 소스셋 수정

- `libs/java-test-support/src/main/java/.../AbstractIntegrationTest.java` →
  `libs/java-test-support/src/test/java/.../AbstractIntegrationTest.java` 로 이동
- `libs/java-test-support/build.gradle` 에서 `api` 의존성을
  `testApi` (또는 `testFixturesApi` with `java-test-fixtures` plugin) 로 전환해
  소비 서비스가 `testImplementation project(':libs:java-test-support')` 로 올바르게
  연결되도록 유지
- 소비 서비스(`auth-service`, `security-service`) 의 `build.gradle` 에서
  dependency scope 확인 및 필요 시 조정 (`testImplementation` 유지)

### B. 미이행 테스트 AbstractIntegrationTest 상속

다음 4개 클래스가 `AbstractIntegrationTest` 를 extend 하도록 수정:

1. `apps/auth-service/.../AuthIntegrationTest` — 자체 `@Container mysql`, `@Container kafka` 제거,
   `@DynamicPropertySource` 에서 MySQL/Kafka 속성 제거, base class 로부터 상속
2. `apps/auth-service/.../OutboxRelayIntegrationTest` — 동일
3. `apps/auth-service/.../DeviceSessionIntegrationTest` — 동일
4. `apps/security-service/.../SecurityServiceIntegrationTest` — 동일
   (현재 `confluentinc/cp-kafka:7.5.0` 을 로컬로 사용, base class 의 `7.6.0` 로 통일됨)

각 클래스에서 서비스별 컨테이너(Redis, WireMock) 및 관련 `@DynamicPropertySource` 는 유지.

### C. Kafka 이미지 버전 통일

`SecurityServiceIntegrationTest` 가 `confluentinc/cp-kafka:7.5.0` 을 직접 참조하고 있다.
Block B 수정 후 base class 의 `7.6.0` 을 자동으로 사용하게 되므로 별도 조치 불필요.
단, `LoginHistoryImmutabilityTest` 도 `7.5.0` 을 직접 참조하는 경우 동일하게 마이그레이션 적용.

## Out of Scope

- TASK-BE-077 (OutboxPollingScheduler scheduler thread lifecycle) — 별도 진행
- `@Disabled` 된 3개 테스트 복원 — TASK-BE-077 완료 후 별도 처리
- 새 통합 테스트 추가

---

# Acceptance Criteria

- [ ] `AbstractIntegrationTest` 가 `libs/java-test-support/src/test/java/` (또는 `src/testFixtures/java/`) 에 위치
- [ ] Testcontainers 클래스가 `libs/java-test-support` 의 프로덕션 런타임 클래스패스에 포함되지 않음
  (즉, `jar` 태스크로 생성된 아티팩트에 `AbstractIntegrationTest` 미포함)
- [ ] `AuthIntegrationTest`, `OutboxRelayIntegrationTest`, `DeviceSessionIntegrationTest`,
  `SecurityServiceIntegrationTest` 가 `AbstractIntegrationTest` 를 extend
- [ ] 위 4개 클래스에서 자체 `@Container MySQLContainer` / `KafkaContainer` 선언 제거
- [ ] 기존 green 테스트 회귀 없음 (`./gradlew :apps:auth-service:test :apps:security-service:test`)
- [ ] `platform/testing-strategy.md` Container Lifecycle MUST 규칙 위반 없음

---

# Related Specs

- `platform/testing-strategy.md` (Container Lifecycle 섹션)
- `platform/shared-library-policy.md`

---

# Related Contracts

없음 (test infra)

---

# Target Service

- `libs/java-test-support`
- `apps/auth-service`
- `apps/security-service`

---

# Architecture

test infrastructure. application code 무영향.

---

# Edge Cases

- `java-test-fixtures` plugin 사용 시 소비 서비스에서 `testImplementation testFixtures(project(':libs:java-test-support'))` 구문 필요.
  기존 `testImplementation project(':libs:java-test-support')` 와 차이 있으므로
  두 서비스 build.gradle 모두 업데이트 필요.
- `src/test/java` 이동 시 `java-library` plugin 의 기본 test 소스셋은
  `jar` 에 포함되지 않으므로 별도 플러그인 없이도 목표 달성 가능.
  단, 소비 서비스가 `testImplementation project(':libs:java-test-support')` 만으로
  해당 클래스를 참조할 수 있는지 Gradle 동작 확인 필요
  (`java-test-fixtures` 플러그인이 더 명시적이고 안전한 접근).

---

# Failure Scenarios

- Gradle test source set 공유 설정이 맞지 않아 소비 서비스에서 `AbstractIntegrationTest` 를
  찾지 못하는 경우 → `java-test-fixtures` plugin 적용 또는 별도 `testSupportJar` task 구성

---

# Test Requirements

- `./gradlew :libs:java-test-support:jar` 아티팩트에 `AbstractIntegrationTest` 미포함 확인
- `./gradlew :apps:auth-service:test :apps:security-service:test` green (기존 @Disabled 제외)
