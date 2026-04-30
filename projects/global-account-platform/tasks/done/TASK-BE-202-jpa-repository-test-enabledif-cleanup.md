# TASK-BE-202: JPA Repository 테스트 @EnabledIf 제거 (auth / community / admin)

## Goal

auth-service, community-service, admin-service의 `@DataJpaTest` 슬라이스 테스트 및 admin-service
통합 테스트에서 `@EnabledIf("isDockerAvailable")` + `isDockerAvailable()` 중복 코드를 제거한다.

- `@DataJpaTest` 파일: `@ExtendWith(DockerAvailableCondition.class)` 추가
- `AbstractIntegrationTest` 상속 파일: `@EnabledIf` 제거만 (이미 커버됨)

## Scope

### auth-service (4 files — @DataJpaTest)

- `apps/auth-service/src/test/java/com/example/auth/infrastructure/persistence/CredentialJpaRepositoryTest.java`
- `apps/auth-service/src/test/java/com/example/auth/infrastructure/persistence/DeviceSessionJpaRepositoryTest.java`
- `apps/auth-service/src/test/java/com/example/auth/infrastructure/persistence/RefreshTokenJpaRepositoryTest.java`
- `apps/auth-service/src/test/java/com/example/auth/infrastructure/persistence/SocialIdentityJpaRepositoryTest.java`

### community-service (5 files — @DataJpaTest)

- `apps/community-service/src/test/java/com/example/community/infrastructure/persistence/CommentJpaRepositoryTest.java`
- `apps/community-service/src/test/java/com/example/community/infrastructure/persistence/FeedSubscriptionJpaRepositoryTest.java`
- `apps/community-service/src/test/java/com/example/community/infrastructure/persistence/PostJpaRepositoryTest.java`
- `apps/community-service/src/test/java/com/example/community/infrastructure/persistence/PostStatusHistoryJpaRepositoryTest.java`
- `apps/community-service/src/test/java/com/example/community/infrastructure/persistence/ReactionJpaRepositoryTest.java`

### admin-service (12 files)

**@DataJpaTest (10 files):**
- `apps/admin-service/src/test/java/com/example/admin/infrastructure/persistence/AdminActionJpaRepositoryTest.java`
- `apps/admin-service/src/test/java/com/example/admin/infrastructure/persistence/AdminOperatorRefreshTokenJpaRepositoryTest.java`
- `apps/admin-service/src/test/java/com/example/admin/infrastructure/persistence/AdminOperatorTotpJpaRepositoryTest.java`
- `apps/admin-service/src/test/java/com/example/admin/infrastructure/persistence/AdminRefreshTokenJpaAdapterTest.java`
- `apps/admin-service/src/test/java/com/example/admin/infrastructure/persistence/BulkLockIdempotencyJpaRepositoryTest.java`
- `apps/admin-service/src/test/java/com/example/admin/infrastructure/persistence/rbac/AdminOperatorJpaRepositoryTest.java`
- `apps/admin-service/src/test/java/com/example/admin/infrastructure/persistence/rbac/AdminOperatorRoleJpaRepositoryTest.java`
- `apps/admin-service/src/test/java/com/example/admin/infrastructure/persistence/rbac/AdminRoleJpaRepositoryTest.java`
- `apps/admin-service/src/test/java/com/example/admin/infrastructure/persistence/rbac/AdminRolePermissionJpaRepositoryTest.java`
- `apps/admin-service/src/test/java/com/example/admin/infrastructure/persistence/PermissionEvaluatorCacheTest.java` (Redis 컨테이너 사용)

**@SpringBootTest — extends AbstractIntegrationTest (2 files, @EnabledIf 제거만):**
- `apps/admin-service/src/test/java/com/example/admin/integration/OperatorAdminIntegrationTest.java`
- `apps/admin-service/src/test/java/com/example/admin/integration/RecoveryCodeRegenerateIntegrationTest.java`

## Acceptance Criteria

- [ ] 21개 파일 모두 `@EnabledIf("isDockerAvailable")` 없음
- [ ] 21개 파일 모두 `isDockerAvailable()` 정적 메서드 없음
- [ ] `@DataJpaTest` 및 독립 컨테이너 파일(11개)에 `@ExtendWith(DockerAvailableCondition.class)` 추가
- [ ] `AbstractIntegrationTest` 상속 파일(2개)은 `@ExtendWith` 추가 없이 `@EnabledIf`만 제거
- [ ] `./gradlew :apps:auth-service:test` BUILD SUCCESSFUL
- [ ] `./gradlew :apps:community-service:test` BUILD SUCCESSFUL
- [ ] `./gradlew :apps:admin-service:test` BUILD SUCCESSFUL

## Related Specs

- `platform/testing-strategy.md`

## Related Contracts

없음

## Edge Cases

- `PermissionEvaluatorCacheTest`는 `@DataJpaTest`가 아닌 Redis 컨테이너 단독 테스트이나, `@EnabledIf` 제거 + `@ExtendWith(DockerAvailableCondition.class)` 추가 처리는 동일

## Failure Scenarios

- `@DataJpaTest` 파일에 `java-test-support` testFixtures 의존성 미설정 시 컴파일 오류 → 3개 서비스 모두 이미 설정되어 있음
