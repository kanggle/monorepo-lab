# TASK-BE-156 — LoginHistoryJpaRepository · AdminActionJpaRepository 슬라이스 테스트

## Goal

두 서비스에 nullable 다중-파라미터 `@Query` + 페이지네이션 쿼리가 전용 슬라이스 테스트 없이 통합 테스트에서만 간접 커버됨.
real MySQL Testcontainer로 쿼리 정확성을 검증한다.

## Scope

**추가 파일 (2개)**

| # | 서비스 | 경로 |
|---|--------|------|
| 1 | security-service | `.../infrastructure/persistence/LoginHistoryJpaRepositoryTest.java` |
| 2 | admin-service | `.../infrastructure/persistence/AdminActionJpaRepositoryTest.java` |

**컨벤션**: 기존 `AccountLockHistoryJpaRepositoryTest` / `AdminRefreshTokenJpaAdapterTest` 패턴 —
`@DataJpaTest` + `@AutoConfigureTestDatabase(replace=NONE)` + `@Testcontainers`.

**변경 없음**: 프로덕션 코드, 계약서, 스펙.

## Acceptance Criteria

### LoginHistoryJpaRepositoryTest
- [ ] `existsByEventId_existing_returnsTrue`
- [ ] `existsByEventId_unknown_returnsFalse`
- [ ] `findFirstByAccountIdAndOutcomeOrderByOccurredAtDesc_returnsLatestEntry`
- [ ] `findByAccountIdAndFilters_allNullFilters_returnsAllForAccount`
- [ ] `findByAccountIdAndFilters_withOutcomeFilter_filtersCorrectly`
- [ ] `findByAccountIdAndFilters_withDateRange_filtersCorrectly`
- [ ] `findByAccountIdAndFilters_pagination_works`

### AdminActionJpaRepositoryTest
- [ ] `findByActorIdAndActionCodeAndIdempotencyKey_existing_returnsAction`
- [ ] `findByLegacyAuditId_existing_returnsAction`
- [ ] `search_allNullFilters_returnsAllRows`
- [ ] `search_withTargetIdFilter_filtersCorrectly`
- [ ] `search_withActionCodeFilter_filtersCorrectly`
- [ ] `search_withDateRange_filtersCorrectly`
- [ ] `search_pagination_works`

- [ ] `./gradlew :apps:security-service:compileTestJava` BUILD SUCCESSFUL
- [ ] `./gradlew :apps:admin-service:compileTestJava` BUILD SUCCESSFUL

## Related Specs

- `specs/services/security-service/architecture.md`
- `specs/services/admin-service/architecture.md`

## Related Contracts

없음 (테스트 전용)

## Edge Cases

- `findByAccountIdAndFilters`: `:from IS NULL OR ...` — null 파라미터 전달 시 조건 무시됨을 검증
- `admin_actions.operator_id` NOT NULL + FK → `admin_operators.id` — JDBC로 operator 행 먼저 삽입 필요
- `admin_actions` finalize-only 트리거: INSERT 후 UPDATE는 `outcome → terminal` 전환만 허용

## Failure Scenarios

- Docker 미설치 → `@EnabledIf("isDockerAvailable")` SKIP
