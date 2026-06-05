---
id: TASK-BE-198
title: account·admin·membership-service integration 테스트 AbstractIntegrationTest 상속 적용 (잔여 5개)
status: ready
type: TASK-BE
target_service: account-service, admin-service, membership-service
---

## Goal

TASK-BE-197에서 `SubscriptionExpirySchedulerIntegrationTest`를 수정했으나, 동일한 패턴의
자체 MySQL·Kafka `@Container` 중복 선언이 5개 파일에 남아있다.
`platform/testing-strategy.md` 규칙에 따라 모두 `AbstractIntegrationTest` 상속으로 교체한다.

## Scope

### membership-service — integration (2개)

`apps/membership-service/src/test/java/com/example/membership/integration/`

| 파일 | 제거 | 유지 |
|---|---|---|
| `ActivateSubscriptionIntegrationTest.java` | MySQL·Kafka `@Container`, 해당 DPS 항목, `@EnabledIf`, `isDockerAvailable()` | Redis `@Container` + Redis DPS, WireMockServer |
| `SubscriptionReactivationIntegrationTest.java` | MySQL·Kafka `@Container`, 해당 DPS 항목, `@EnabledIf`, `isDockerAvailable()` | Redis `@Container` + Redis DPS, WireMockServer |

build.gradle 변경 없음 (이미 TASK-BE-197에서 추가됨).

### account-service — integration (2개)

`apps/account-service/src/test/java/com/example/account/integration/`

| 파일 | 제거 | 유지 |
|---|---|---|
| `AccountSignupIntegrationTest.java` | MySQL `@Container`, MySQL DPS 항목 (`spring.datasource.*`), `@EnabledIf`, `isDockerAvailable()` | `spring.flyway.enabled`·`internal.api.token` DPS 항목, `@MockitoBean` 빈들 |
| `SignupRollbackIntegrationTest.java` | MySQL `@Container`, MySQL DPS 항목 (`spring.datasource.*`), `@EnabledIf`, `isDockerAvailable()` | WireMockServer, `spring.flyway.enabled`·`internal.api.token`·`account.auth-service.*` DPS 항목, `@MockitoBean` 빈들 |

build.gradle: `testImplementation(testFixtures(project(':libs:java-test-support')))` 이미 존재.

### admin-service — integration (1개)

`apps/admin-service/src/test/java/com/example/admin/integration/AdminIntegrationTest.java`

| 제거 | 유지 |
|---|---|
| MySQL·Kafka `@Container`, 해당 DPS 항목 (`spring.datasource.*`, `spring.kafka.bootstrap-servers`), `@EnabledIf`, `isDockerAvailable()` | Redis `@Container` + Redis DPS, WireMockServer, `admin.jwt.*`·`admin.*.base-url` DPS 항목 |

build.gradle: `testImplementation(testFixtures(project(':libs:java-test-support')))` 이미 존재.

## Acceptance Criteria

- 5개 파일 모두 `extends AbstractIntegrationTest` 선언
- 각 파일에서 MySQL·Kafka `@Container` 필드 제거됨
- `@EnabledIf("isDockerAvailable")` 및 `isDockerAvailable()` 메서드 제거됨 (DockerAvailableCondition 상속)
- Redis·WireMock 등 서비스 고유 컨테이너/픽스처는 유지됨
- `./gradlew :apps:membership-service:test` BUILD SUCCESSFUL, failures=0
- `./gradlew :apps:account-service:test` BUILD SUCCESSFUL, failures=0
- `./gradlew :apps:admin-service:test` BUILD SUCCESSFUL, failures=0

## Related Specs

- `platform/testing-strategy.md` — AbstractIntegrationTest 사용 규칙
- `specs/services/membership-service/architecture.md`
- `specs/services/account-service/architecture.md`
- `specs/services/admin-service/architecture.md`

## Related Contracts

없음

## Edge Cases

- `@Testcontainers` 어노테이션은 서비스 고유 `@Container` 필드(Redis 등)가 있는 경우 유지
- account-service 테스트는 `@MockitoBean KafkaTemplate`·`OutboxPollingScheduler`를 유지
  (AbstractIntegrationTest가 Kafka 컨테이너를 시작하더라도 mocked bean이 우선 적용됨)
- `@DynamicPropertySource` 메서드는 Spring이 클래스 계층 전체에서 수집하므로
  base의 MySQL·Kafka 등록 + subclass의 Redis·WireMock 등록이 함께 적용됨

## Failure Scenarios

- `AbstractIntegrationTest` import 누락 시 컴파일 오류
- MySQL·Kafka DPS를 중복 등록하면 Spring ContextCache 키 불일치 가능
