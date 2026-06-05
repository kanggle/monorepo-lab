# Task ID

TASK-BE-082

# Title

fix(test): TASK-BE-080 review — AbstractIntegrationTest testFixtures 구조 불일치 수정

# Status

ready

# Owner

backend

# Task Tags

- test
- infra

# depends_on

- TASK-BE-080 (done)

---

# Goal

TASK-BE-080 리뷰에서 발견된 두 가지 결함을 수정한다.

1. **testFixtures 구조 불일치**: `libs/java-test-support` 의 HEAD 상태에서
   `AbstractIntegrationTest` 가 `src/main/java` 에 위치하고 `build.gradle` 에
   `java-test-fixtures` 플러그인이 없다. TASK-BE-078 이 `7cabe8b` 에서 이를
   `src/testFixtures/java` 로 이동했으나 해당 변경이 master 브랜치에 반영되지 않았다.
   이 상태에서는 Testcontainers 클래스가 `java-test-support` 의 production
   classpath 로 노출되어 `platform/testing-strategy.md` Container Lifecycle
   MUST 규칙을 위반한다.

2. **`security-service/build.gradle` 의존성 선언 불일치**: HEAD 에서
   `testImplementation project(':libs:java-test-support')` 를 사용하고 있으나,
   testFixtures 구조가 복원되면 `testImplementation testFixtures(project(':libs:java-test-support'))`
   로 변경해야 한다.

---

# Scope

## In Scope

- `libs/java-test-support/build.gradle` 에 `java-test-fixtures` 플러그인 추가, 의존성을 `testFixturesApi` 로 전환
- `libs/java-test-support/src/main/java/.../AbstractIntegrationTest.java` →
  `libs/java-test-support/src/testFixtures/java/.../AbstractIntegrationTest.java` 이동
- `apps/security-service/build.gradle`: `testImplementation project(...)` →
  `testImplementation testFixtures(project(':libs:java-test-support'))`
- `apps/auth-service/build.gradle` 도 동일하게 전환 (TASK-BE-078 에서 처리된 경우 확인 후 미처리 시 적용)

## Out of Scope

- `SecurityServiceIntegrationTest` 의 자체 MySQL/Kafka container 제거
  (별도 task 범위)
- `LoginHistoryImmutabilityTest` 의 `spring.flyway.locations` 등록 제거/검증
  (기능상 무해하므로 별도 task 외)
- 다른 서비스 테스트 마이그레이션

---

# Acceptance Criteria

- [ ] `libs/java-test-support/build.gradle` 에 `id 'java-test-fixtures'` 플러그인 선언
- [ ] `AbstractIntegrationTest.java` 가 `src/testFixtures/java/...` 에 위치
- [ ] `src/main/java/.../AbstractIntegrationTest.java` 파일 없음
- [ ] `libs/java-test-support/build.gradle` 의존성이 `testFixturesApi` 사용
- [ ] `apps/security-service/build.gradle` 이 `testImplementation testFixtures(project(':libs:java-test-support'))` 사용
- [ ] `apps/auth-service/build.gradle` 이 `testImplementation testFixtures(project(':libs:java-test-support'))` 사용 (혹은 이미 그렇다면 확인 주석)
- [ ] `platform/testing-strategy.md` Container Lifecycle MUST 규칙 위반 없음
- [ ] 기존 green 테스트 회귀 없음

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
- `apps/security-service`
- `apps/auth-service`

---

# Architecture

test infrastructure only. application code 무영향.

---

# Edge Cases

- `src/main/java` 아래 `AbstractIntegrationTest.java` 가 삭제되지 않으면
  동일 클래스가 두 source set 에 존재해 컴파일 오류 발생 → 반드시 삭제
- `testFixtures` source set 내 클래스는 `testFixturesApi` 로 노출해야
  소비자가 `testImplementation testFixtures(...)` 로 접근 가능
- `java-test-fixtures` 플러그인은 `java-library` 플러그인과 함께 사용 가능

---

# Failure Scenarios

- `testImplementation project(...)` 유지 시 `testFixtures` source set의 클래스를
  소비자가 찾지 못해 `ClassNotFoundException: AbstractIntegrationTest` 컴파일 오류
- `src/main/java` 파일 미삭제 시 동일 FQCN 클래스 중복으로 컴파일 오류
