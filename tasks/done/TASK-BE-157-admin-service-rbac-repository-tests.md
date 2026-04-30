---
id: TASK-BE-157
title: admin-service RBAC 리포지토리 슬라이스 테스트
status: ready
priority: medium
assignee: ""
---

## Goal

admin-service 내 RBAC 관련 JPA 리포지토리 5개에 대한 쿼리 슬라이스 테스트를 작성하여
커스텀 쿼리(`@Query`, `@Modifying`, `findByStatus(Pageable)` 등)가 실제 MySQL 스키마에서
올바르게 동작함을 검증한다.

## Scope

대상 리포지토리:
- `AdminOperatorJpaRepository` — findByEmail, findByOperatorId, existsByEmail, findByStatus(Pageable)
- `AdminOperatorRoleJpaRepository` — findByOperatorId, findByOperatorIdIn, deleteByOperatorId(@Modifying)
- `AdminRolePermissionJpaRepository` — findPermissionKeysByRoleIds(@Query IN)
- `AdminRoleJpaRepository` — findByName, findByNameIn
- `AdminOperatorTotpJpaRepository` — findByOperatorIdIn

## Acceptance Criteria

- [ ] 5개 클래스 각각 `@DataJpaTest + @Testcontainers + @EnabledIf("isDockerAvailable")` 패턴 준수
- [ ] `withDatabaseName("admin_db")` + Flyway 마이그레이션으로 실제 스키마 적용
- [ ] `findByStatus` 페이지네이션 테스트: status 필터 + 페이지 크기 동작 검증
- [ ] `deleteByOperatorId` 벌크 DELETE 반환 카운트 검증
- [ ] `findPermissionKeysByRoleIds` IN 쿼리: 단일/복수 role 시나리오
- [ ] `findByOperatorIdIn` bulk fetch: TOTP 없는 오퍼레이터 제외 확인
- [ ] 테스트 메서드 네이밍: `{scenario}_{condition}_{expectedResult}`
- [ ] `@DisplayName` 한국어 비즈니스 설명
- [ ] `compileTestJava` 성공

## Related Specs

- `specs/services/admin-service/architecture.md`
- `specs/services/admin-service/rbac.md`

## Related Contracts

없음 (DB 내부 쿼리 검증)

## Edge Cases

- `findByStatus` 결과가 0건인 status 조회
- `findByNameIn` 빈 컬렉션 입력
- `findPermissionKeysByRoleIds` 복수 role 권한 중복 없이 전체 반환

## Failure Scenarios

- Docker 미사용 환경: `@EnabledIf("isDockerAvailable")` 로 자동 스킵
- FK 제약(admin_operators.id) 위반 시 테스트 실패 → insertOperator() 헬퍼로 선행 데이터 삽입
