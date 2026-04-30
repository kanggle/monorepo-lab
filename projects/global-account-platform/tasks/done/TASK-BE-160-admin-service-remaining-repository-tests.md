# TASK-BE-160 — admin-service 나머지 JPA Repository 슬라이스 테스트

## Goal
admin-service의 커버리지 공백인 두 JPA repository에 대해 `@DataJpaTest` 슬라이스 테스트를 작성한다.

## Scope
- `BulkLockIdempotencyJpaRepository` — composite key `(operator_id, idempotency_key)` 기반 존재 확인 동작
- `AdminOperatorRefreshTokenJpaRepository` — `revokeAllForOperator` JPQL 벌크 업데이트 동작

## Acceptance Criteria
- [ ] `BulkLockIdempotencyJpaRepositoryTest` 작성 — composite key findById 시나리오 3개 이상
- [ ] `AdminOperatorRefreshTokenJpaRepositoryTest` 작성 — `revokeAllForOperator` 시나리오 4개
- [ ] `MySQLContainer("mysql:8.0")` + `withDatabaseName("admin_db")` 사용
- [ ] `@EnabledIf("isDockerAvailable")` 적용
- [ ] 두 테이블 모두 `admin_operators` FK → `operatorRepo.deleteAll()` BeforeEach cleanup
- [ ] `./gradlew :apps:admin-service:compileTestJava` BUILD SUCCESSFUL

## Related Specs
- `specs/services/admin-service/architecture.md`

## Related Contracts
없음

## Edge Cases
- `revokeAllForOperator`: 이미 revoked된 토큰은 업데이트 제외 (WHERE revokedAt IS NULL)
- `revokeAllForOperator`: 다른 operator의 토큰에 영향 없음
- Composite key: 동일 operator + 다른 key, 다른 operator + 동일 key 각각 별개 행

## Failure Scenarios
- Docker 미실행 → `@EnabledIf` 로 자동 skip
