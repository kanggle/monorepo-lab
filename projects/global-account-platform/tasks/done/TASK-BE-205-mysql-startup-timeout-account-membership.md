# TASK-BE-205: MySQLContainer startup timeout 추가 — account / membership-service

## Goal

account-service와 membership-service의 `@DataJpaTest` 파일 중 `MySQLContainer` 선언에
`.withStartupTimeout(Duration.ofMinutes(3))` 이 누락된 파일을 수정한다.

다른 서비스(auth, community, admin, security)의 동일 파일들은 이미 timeout 이 설정되어 있다.

## Scope

### account-service (2 files)

- `apps/account-service/src/test/java/com/example/account/infrastructure/persistence/AccountJpaRepositoryTest.java`
- `apps/account-service/src/test/java/com/example/account/infrastructure/persistence/AccountStatusHistoryJpaRepositoryTest.java`

### membership-service (3 files)

- `apps/membership-service/src/test/java/com/example/membership/infrastructure/persistence/ContentAccessPolicyJpaRepositoryTest.java`
- `apps/membership-service/src/test/java/com/example/membership/infrastructure/persistence/MembershipPlanJpaRepositoryTest.java`
- `apps/membership-service/src/test/java/com/example/membership/infrastructure/persistence/SubscriptionJpaRepositoryTest.java`

## Acceptance Criteria

- [ ] 5개 파일 모두 `MySQLContainer` 체인에 `.withStartupTimeout(Duration.ofMinutes(3))` 추가
- [ ] 5개 파일 모두 `import java.time.Duration;` import 추가
- [ ] `./gradlew :apps:account-service:test` BUILD SUCCESSFUL
- [ ] `./gradlew :apps:membership-service:test` BUILD SUCCESSFUL

## Related Specs

- `platform/testing-strategy.md`

## Related Contracts

없음

## Edge Cases

- `ProfileJpaRepositoryTest.java`는 `MySQLContainer` 대신 다른 컨테이너를 사용할 수 있으므로 확인 후 필요 시 추가
- `SubscriptionStatusHistoryJpaRepositoryTest.java` 도 동일하게 확인
- `java.time.Duration` import 가 이미 있는 파일은 중복 추가 불필요

## Failure Scenarios

- `.withStartupTimeout` 메서드가 사용하는 Testcontainers 버전에서 지원되지 않을 경우 — 프로젝트 다른 파일들이 이미 사용 중이므로 해당 없음
