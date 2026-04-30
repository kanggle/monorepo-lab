---
id: TASK-BE-101
title: "fix: TASK-BE-098 — Docker 미가용 환경에서 AbstractIntegrationTest 정적 초기화 실패로 인한 스케줄러 통합 테스트 FAILED 문제 해결"
status: ready
area: backend
service: account-service
parent: TASK-BE-098
---

## Goal

TASK-BE-098 리뷰에서 발견된 문제를 수정한다:

`AccountDormantSchedulerIntegrationTest`와 `AccountAnonymizationSchedulerIntegrationTest`가 Docker 미가용 환경에서 SKIPPED 대신 FAILED로 처리된다.

**근본 원인**: 두 통합 테스트가 `AbstractIntegrationTest`를 확장하는 상황에서 `@EnabledIf("isDockerAvailable")` 조건 평가 시 JVM이 해당 클래스를 초기화하는 과정에서 `AbstractIntegrationTest`의 static block(`MYSQL.start()`, `KAFKA.start()`)이 먼저 실행된다. Docker가 없으면 `IllegalStateException` → `ExceptionInInitializerError`가 발생하여, JUnit이 조건을 평가하기도 전에 테스트가 FAILED 처리된다.

`platform/testing-strategy.md` "Docker Availability Guard" 절 및 Acceptance Criteria(`./gradlew :apps:account-service:test` 전체 통과, Docker 없는 환경에서는 통합 테스트 SKIPPED 허용) 요건 위반.

## Scope

### In

1. **`AccountDormantSchedulerIntegrationTest` 수정**
   - 현재: `@org.junit.jupiter.api.condition.EnabledIf("isDockerAvailable")` + subclass에 정의된 `isDockerAvailable()` 정적 메서드
   - 문제: `isDockerAvailable()`을 호출하기 전에 JVM이 클래스를 초기화 → `AbstractIntegrationTest` static block 실행 → Docker 미가용 시 crash
   - 해결: `@EnabledIf("isDockerAvailable")` 조건 평가가 `AbstractIntegrationTest` 정적 초기화 이전에 실행되도록 수정
   - 권장 방법: `DockerClientFactory.instance().isDockerAvailable()`를 사용하는 별도 JUnit `ExecutionCondition` 구현(예: `DockerAvailableCondition`) 또는 `@DisabledIf` 접근 방식으로 전환하되, `AbstractIntegrationTest` 확장 클래스에서 정상 동작하는 방법 적용
   - 대안: `AbstractIntegrationTest` 자체가 Docker 미가용 시 gracefully 처리하도록 수정(예: static block에 Docker availability guard 추가)하고, 테스트 클래스에서는 `@Assumptions.assumeTrue(isDockerAvailable())` 패턴 사용

2. **`AccountAnonymizationSchedulerIntegrationTest` 수정**
   - 동일한 문제, 동일한 수정 방식 적용

3. **`AbstractIntegrationTest` 수정 (권장)** — `libs/java-test-support`
   - static block에 Docker availability guard 추가:
     ```java
     static {
         if (!DockerClientFactory.instance().isDockerAvailable()) {
             // Docker가 없으면 containers를 시작하지 않음
             // 서브클래스 테스트는 assumeTrue로 건너뜀
         } else {
             MYSQL = new MySQLContainer<>(...);
             KAFKA = new KafkaContainer(...);
             MYSQL.start();
             KAFKA.start();
         }
     }
     ```
   - 또는 `Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable())`를 static block 시작부에 추가하여 Docker 미가용 시 `TestAbortedException`을 발생시켜 테스트 SKIP 처리

### 패턴 결정 기준

- `AbstractIntegrationTest`에 수정을 가하는 방법이 선호된다 — 이 수정은 현재 및 미래 모든 서브클래스에 자동으로 적용된다.
- `Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable())` 를 static block 최상단에 추가하면 Docker 미가용 시 `TestAbortedException`이 발생하고 JUnit은 이를 SKIP(ABORTED)으로 처리한다.
- `DockerClientFactory.instance().isDockerAvailable()` 호출은 Docker 데몬 연결을 시도하기 전에 빠르게 실패하므로 overhead가 적다.
- `@EnabledIf("isDockerAvailable")` 어노테이션은 `AbstractIntegrationTest`를 상속하지 않는 테스트 클래스(예: `AccountSignupIntegrationTest`)에서만 올바르게 동작하므로, 상속 구조에서는 Assumptions 기반 접근이 더 안전하다.

### Out

- `Profile.maskPii()` 수정 없음 (TASK-BE-098에서 이미 완료)
- `application-test.yml` 수정 없음 (TASK-BE-098에서 이미 완료)
- `build.gradle` testFixtures 의존성 수정 없음 (TASK-BE-098에서 이미 완료)
- 다른 서비스의 통합 테스트 수정 없음 (account-service 범위)

## Acceptance Criteria

- [ ] `./gradlew :apps:account-service:test` 가 Docker 미가용 환경에서 `BUILD SUCCESSFUL`로 완료된다
- [ ] Docker 미가용 환경에서 `AccountDormantSchedulerIntegrationTest`의 모든 테스트 메서드가 FAILED 대신 SKIPPED(ABORTED)로 처리된다
- [ ] Docker 미가용 환경에서 `AccountAnonymizationSchedulerIntegrationTest`의 모든 테스트 메서드가 FAILED 대신 SKIPPED(ABORTED)로 처리된다
- [ ] Docker 가용 환경(CI)에서 두 통합 테스트 모두 정상 실행된다
- [ ] 다른 기존 통합 테스트(`AccountSignupIntegrationTest`, `SignupRollbackIntegrationTest`)의 SKIP/PASS 동작이 변경되지 않는다

## Related Specs

- `platform/testing-strategy.md` — "Docker Availability Guard" 절: `@EnabledIf("isDockerAvailable")` + `DockerClientFactory.instance().isDockerAvailable()` 패턴 명시

## Related Contracts

- `specs/contracts/events/account-events.md` — 직접 영향 없음

## Edge Cases

- `AbstractIntegrationTest`를 수정할 경우 `libs/java-test-support`에 위치하므로 모든 서비스의 통합 테스트에 영향을 준다. 수정 전 다른 서비스(admin-service 등)에서 `AbstractIntegrationTest`를 사용하는 테스트가 있는지 확인 후, 해당 테스트들도 Docker 미가용 시 SKIP 처리되는지 검증한다.
- `DockerClientFactory.instance().isDockerAvailable()` 는 내부적으로 Docker 데몬 연결을 시도한다. 첫 호출은 수 초가 걸릴 수 있으나, static block은 JVM당 한 번만 실행되므로 허용 범위 내이다.
- JUnit의 `TestAbortedException`(Assumptions 실패 시)은 테스트를 ABORTED 상태로 표시하며, Gradle은 이를 SKIPPED로 집계한다. `initializationError`가 발생하는 `ExceptionInInitializerError`(현재 동작)와 달리 BUILD SUCCESSFUL을 보장한다.

## Failure Scenarios

- `AbstractIntegrationTest`에 null 필드 문제: `MYSQL = null`, `KAFKA = null`로 분기했는데 다른 서브클래스에서 null check 없이 `MYSQL.getJdbcUrl()` 등을 호출하면 NPE 발생. `@DynamicPropertySource` 내에서도 null guard가 필요하다.
- Docker가 불안정하게 가용/불가용 상태를 반복하는 환경: `isDockerAvailable()` 결과가 실행마다 다를 수 있다. static block은 한 번만 실행되므로 첫 평가 결과에 따라 일관성을 보장한다.
