# TASK-BE-204: @EnabledIf 제거 — account / membership / security-service

## Goal

account-service, membership-service, security-service 의 테스트 파일에서
`@EnabledIf("isDockerAvailable")` + `isDockerAvailable()` 중복 코드를 제거한다.

- `@DataJpaTest` 독립 파일(10개): `@ExtendWith(DockerAvailableCondition.class)` 추가
- `AbstractIntegrationTest` 상속 파일(6개): `@EnabledIf` 제거만 (이미 커버됨)

## Scope

### account-service

**@DataJpaTest (3 files):**
- `apps/account-service/src/test/java/com/example/account/infrastructure/persistence/AccountJpaRepositoryTest.java`
- `apps/account-service/src/test/java/com/example/account/infrastructure/persistence/AccountStatusHistoryJpaRepositoryTest.java`
- `apps/account-service/src/test/java/com/example/account/infrastructure/persistence/ProfileJpaRepositoryTest.java`

**extends AbstractIntegrationTest (2 files — @EnabledIf 제거만):**
- `apps/account-service/src/test/java/com/example/account/infrastructure/scheduler/AccountAnonymizationSchedulerIntegrationTest.java`
- `apps/account-service/src/test/java/com/example/account/infrastructure/scheduler/AccountDormantSchedulerIntegrationTest.java`

### membership-service

**@DataJpaTest (4 files):**
- `apps/membership-service/src/test/java/com/example/membership/infrastructure/persistence/ContentAccessPolicyJpaRepositoryTest.java`
- `apps/membership-service/src/test/java/com/example/membership/infrastructure/persistence/MembershipPlanJpaRepositoryTest.java`
- `apps/membership-service/src/test/java/com/example/membership/infrastructure/persistence/SubscriptionJpaRepositoryTest.java`
- `apps/membership-service/src/test/java/com/example/membership/infrastructure/persistence/SubscriptionStatusHistoryJpaRepositoryTest.java`

### security-service

**@DataJpaTest (3 files):**
- `apps/security-service/src/test/java/com/example/security/infrastructure/persistence/AccountLockHistoryJpaRepositoryTest.java`
- `apps/security-service/src/test/java/com/example/security/infrastructure/persistence/LoginHistoryJpaRepositoryTest.java`
- `apps/security-service/src/test/java/com/example/security/infrastructure/persistence/SuspiciousEventJpaRepositoryTest.java`

**extends AbstractIntegrationTest (4 files — @EnabledIf 제거만):**
- `apps/security-service/src/test/java/com/example/security/integration/DetectionE2EIntegrationTest.java`
- `apps/security-service/src/test/java/com/example/security/integration/DlqRoutingIntegrationTest.java`
- `apps/security-service/src/test/java/com/example/security/integration/LoginHistoryImmutabilityIntegrationTest.java`
- `apps/security-service/src/test/java/com/example/security/integration/SecurityServiceIntegrationTest.java`

## Acceptance Criteria

- [ ] 16개 파일 모두 `@EnabledIf("isDockerAvailable")` 없음
- [ ] 16개 파일 모두 `isDockerAvailable()` 정적 메서드 없음
- [ ] `@DataJpaTest` 파일(10개)에 `@ExtendWith(DockerAvailableCondition.class)` 추가
- [ ] `AbstractIntegrationTest` 상속 파일(6개)은 `@ExtendWith` 추가 없이 `@EnabledIf`만 제거
- [ ] `./gradlew :apps:account-service:test` BUILD SUCCESSFUL
- [ ] `./gradlew :apps:membership-service:test` BUILD SUCCESSFUL
- [ ] `./gradlew :apps:security-service:test` BUILD SUCCESSFUL

## Related Specs

- `platform/testing-strategy.md`

## Related Contracts

없음

## Edge Cases

- 3개 서비스 모두 `testImplementation testFixtures(project(':libs:java-test-support'))` 의존성이 이미 설정되어 있으므로 build.gradle 수정 불필요
- `@DataJpaTest` 파일에 `import org.junit.jupiter.api.extension.ExtendWith` 및 `import com.example.testsupport.integration.DockerAvailableCondition` import 추가 필요
- `@EnabledIf` import 행(`import org.junit.jupiter.api.condition.EnabledIf`) 도 함께 제거

## Failure Scenarios

- `isDockerAvailable()` 제거 후 `@EnabledIf` import 만 남으면 unused import 경고 → import 행도 함께 제거
