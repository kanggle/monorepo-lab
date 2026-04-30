---
id: TASK-BE-197
title: membership-service SubscriptionExpirySchedulerIntegrationTest — AbstractIntegrationTest 상속 적용
status: ready
type: TASK-BE
target_service: membership-service
---

## Goal

`SubscriptionExpirySchedulerIntegrationTest`가 자체 MySQL·Kafka `@Container` 필드와
`@DynamicPropertySource`를 선언하고 있다. `platform/testing-strategy.md` 규칙에 따라
`libs/java-test-support`의 `AbstractIntegrationTest`를 상속하도록 교체한다.

현재 구조의 문제:
- 서비스별로 MySQL·Kafka 컨테이너를 중복 선언하면 Spring `ContextCache` 교체 시
  Hikari pool 재생성 → 기존 컨테이너 종료로 `CommunicationsException` 발생 가능 (TASK-BE-076 배경)
- `libs/java-test-support`의 static 초기화 방식으로 컨테이너를 JVM 당 한 번만 시작해야 함

## Scope

### 1. build.gradle — testFixtures 의존성 추가

`apps/membership-service/build.gradle` 의 test 의존성 블록에 추가:

```groovy
testImplementation(testFixtures(project(':libs:java-test-support')))
```

### 2. SubscriptionExpirySchedulerIntegrationTest 리팩터링

`apps/membership-service/src/test/java/com/example/membership/integration/SubscriptionExpirySchedulerIntegrationTest.java`

변경 사항:
- `extends AbstractIntegrationTest` 추가
- `@Testcontainers` 어노테이션 제거
- `@EnabledIf("isDockerAvailable")` 어노테이션 제거 (AbstractIntegrationTest의 `@ExtendWith(DockerAvailableCondition.class)` 로 대체)
- `isDockerAvailable()` 정적 메서드 제거
- MySQL `@Container` 필드 및 관련 `@DynamicPropertySource` 항목 제거 (AbstractIntegrationTest에서 상속)
- Kafka `@Container` 필드 및 관련 `@DynamicPropertySource` 항목 제거 (AbstractIntegrationTest에서 상속)
- Redis `@Container` 필드는 서비스 고유 컨테이너이므로 유지
- Redis 전용 `@DynamicPropertySource` 메서드만 남김 (MySQL·Kafka 항목 제거)
- `AbstractIntegrationTest`의 `MYSQL` / `KAFKA` 상수 참조로 datasource·kafka 프로퍼티 등록 제거

## Acceptance Criteria

- `build.gradle`에 `testFixtures(project(':libs:java-test-support'))` 의존성이 추가됨
- `SubscriptionExpirySchedulerIntegrationTest`가 `AbstractIntegrationTest`를 상속함
- 자체 MySQL·Kafka `@Container` 필드 및 해당 `@DynamicPropertySource` 항목이 제거됨
- Redis `@Container`와 Redis 관련 `@DynamicPropertySource`는 유지됨
- `./gradlew :apps:membership-service:test` BUILD SUCCESSFUL, failures=0

## Related Specs

- `platform/testing-strategy.md` — AbstractIntegrationTest 사용 규칙
- `specs/services/membership-service/architecture.md`

## Related Contracts

없음

## Edge Cases

- `AbstractIntegrationTest`는 MySQL과 Kafka만 관리; Redis는 서비스 고유이므로 subclass에 유지
- Docker 미사용 환경에서는 `DockerAvailableCondition`이 테스트를 SKIP 처리
- `@DynamicPropertySource` 메서드는 Spring이 클래스 계층 전체에서 수집하므로 subclass의 Redis 등록과 base의 MySQL·Kafka 등록이 함께 적용됨

## Failure Scenarios

- `testFixtures` 의존성 누락 시 컴파일 오류
- `AbstractIntegrationTest.MYSQL` null 체크 미흡 시 Docker 미사용 환경에서 NPE
