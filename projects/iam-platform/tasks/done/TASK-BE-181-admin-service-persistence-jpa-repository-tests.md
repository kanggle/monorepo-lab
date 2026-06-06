---
id: TASK-BE-181
title: "admin-service JPA 레포지토리 슬라이스 테스트 — persistence 7개 파일"
status: ready
type: TASK-BE
service: admin-service
---

## Goal

admin-service infrastructure/persistence 계층의 레포지토리 슬라이스 테스트 파일 7개를
공식 태스크로 관리하고 커밋한다. 파일은 이미 작성·검증되어 있으나 git에 추적되지 않는 상태다.

## Scope

- `apps/admin-service/src/test/java/com/example/admin/infrastructure/persistence/AdminOperatorRefreshTokenJpaRepositoryTest.java`
- `apps/admin-service/src/test/java/com/example/admin/infrastructure/persistence/AdminOperatorTotpJpaRepositoryTest.java`
- `apps/admin-service/src/test/java/com/example/admin/infrastructure/persistence/BulkLockIdempotencyJpaRepositoryTest.java`
- `apps/admin-service/src/test/java/com/example/admin/infrastructure/persistence/rbac/AdminOperatorJpaRepositoryTest.java`
- `apps/admin-service/src/test/java/com/example/admin/infrastructure/persistence/rbac/AdminOperatorRoleJpaRepositoryTest.java`
- `apps/admin-service/src/test/java/com/example/admin/infrastructure/persistence/rbac/AdminRoleJpaRepositoryTest.java`
- `apps/admin-service/src/test/java/com/example/admin/infrastructure/persistence/rbac/AdminRolePermissionJpaRepositoryTest.java`

구현 코드 변경 없음. 테스트 파일 커밋만 수행한다.

## Acceptance Criteria

### AdminOperatorRefreshTokenJpaRepositoryTest (4개)

1. `revokeAllForOperator_activeTokens_revokesAllAndReturnsCount` — 활성 토큰 2개 → count=2, 모두 revoked
2. `revokeAllForOperator_skipsAlreadyRevokedTokens` — 이미 revoked 1개 + 활성 1개 → count=1만 업데이트
3. `revokeAllForOperator_differentOperator_notAffected` — opA revoke 시 opB 토큰 영향 없음
4. `revokeAllForOperator_noTokens_returnsZero` — 토큰 없는 operator → 0 반환

### AdminOperatorTotpJpaRepositoryTest (3개)

5. `findByOperatorIdIn_enrolledOperators_returnsRows` — 2개 operator TOTP 모두 반환
6. `findByOperatorIdIn_unenrolledOperator_excluded` — 미등록 operator는 제외
7. `findByOperatorIdIn_excludesUnrequested` — 요청 목록에 없는 operator TOTP 제외

### BulkLockIdempotencyJpaRepositoryTest (4개)

8. `findById_existingKey_returnsEntity` — 복합키(operatorId + idempotencyKey) → 엔티티 반환
9. `findById_notFound_returnsEmpty` — 없는 복합키 → empty
10. `findById_sameOperatorDifferentKey_returnsEmpty` — 같은 operator, 다른 key → empty
11. `findById_differentOperatorSameKey_returnsEmpty` — 다른 operator, 같은 key → empty

### AdminOperatorJpaRepositoryTest (9개)

12. `findByEmail_existing_returnsOperator` — 존재하는 이메일 → 반환
13. `findByEmail_unknown_returnsEmpty` — 없는 이메일 → empty
14. `findByOperatorId_existing_returnsOperator` — 존재하는 operatorId → 반환
15. `findByOperatorId_unknown_returnsEmpty` — 없는 operatorId → empty
16. `existsByEmail_existing_returnsTrue` — 존재 → true
17. `existsByEmail_unknown_returnsFalse` — 없음 → false
18. `findByStatus_filter_returnsMatchingOnly` — ACTIVE 3개 + SUSPENDED 1개 → ACTIVE 3개만
19. `findByStatus_pagination_respectsPageSize` — size=2, page0/page1 분리 확인
20. `findByStatus_noMatch_returnsEmptyPage` — 없는 status → 빈 페이지

### AdminOperatorRoleJpaRepositoryTest (6개)

21. `findByOperatorId_existing_returnsBindings` — 역할 바인딩 2개 반환
22. `findByOperatorId_noBindings_returnsEmpty` — 역할 없음 → 빈 목록
23. `findByOperatorIdIn_multipleOperators_returnsAllBindings` — 복수 operator 일괄 조회
24. `findByOperatorIdIn_excludesUnrequestedOperators` — 요청 목록에 없는 operator 제외
25. `deleteByOperatorId_deletesAllBindings_returnsCount` — 2개 삭제, count=2
26. `deleteByOperatorId_doesNotAffectOtherOperators` — 다른 operator 역할 보존

### AdminRoleJpaRepositoryTest (6개)

27. `findByName_existing_returnsRole` — 존재하는 role 반환
28. `findByName_unknown_returnsEmpty` — 없는 role → empty
29. `findByNameIn_existingNames_returnsMatchingRoles` — 이름 목록 일괄 조회
30. `findByNameIn_partialMatch_excludesUnrequested` — 요청 목록에 없는 이름 제외
31. `findByNameIn_noMatch_returnsEmpty` — 존재하지 않는 이름만 → 빈 목록

### AdminRolePermissionJpaRepositoryTest (4개)

32. `findPermissionKeysByRoleIds_singleRole_returnsKeys` — 단일 role 권한 키 목록
33. `findPermissionKeysByRoleIds_multipleRoles_returnsAllKeys` — 복수 role 합산
34. `findPermissionKeysByRoleIds_roleWithNoPermissions_returnsEmpty` — 권한 없는 role → 빈 목록
35. `findPermissionKeysByRoleIds_excludesOtherRoles` — 다른 role 권한 포함하지 않음

## Related Specs

- `specs/services/admin-service/architecture.md`

## Related Contracts

없음 (레포지토리 슬라이스 테스트, HTTP/이벤트 계약 변경 없음)

## Edge Cases

- 모든 테스트는 `@EnabledIf("isDockerAvailable")`으로 Docker 없는 환경에서 자동 스킵
- `@DataJpaTest` + `@AutoConfigureTestDatabase(replace=NONE)` + `spring.flyway.enabled=true` → 실제 MySQL + Flyway 마이그레이션으로 스키마 검증
- `AdminOperatorRoleJpaRepositoryTest`와 `AdminRoleJpaRepository*`는 JdbcTemplate으로 `TEST_` prefix role 직접 INSERT/DELETE
- `BulkLockIdempotencyJpaRepositoryTest`는 복합 PK(`Key` inner class) 사용

## Failure Scenarios

- Docker를 사용할 수 없으면 모든 테스트가 스킵됨 (BUILD SUCCESSFUL, 테스트 미실행)
- 테스트 파일 중 하나라도 컴파일 실패 시 커밋하지 않음
