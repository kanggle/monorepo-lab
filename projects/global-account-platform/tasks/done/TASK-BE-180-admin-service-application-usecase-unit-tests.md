---
id: TASK-BE-180
title: "admin-service 애플리케이션 서비스 단위 테스트 — AuditQueryUseCase, OperatorRoleResolver"
status: ready
type: TASK-BE
service: admin-service
---

## Goal

admin-service 애플리케이션 계층의 단위 테스트 파일(AuditQueryUseCaseTest, OperatorRoleResolverTest)을
공식 태스크로 관리하고 커밋한다. 두 파일은 이미 작성·검증되어 있으나 git에 추적되지 않는 상태다.

## Scope

- `apps/admin-service/src/test/java/com/example/admin/application/AuditQueryUseCaseTest.java`
- `apps/admin-service/src/test/java/com/example/admin/application/OperatorRoleResolverTest.java`

구현 코드 변경 없음. 테스트 파일 커밋만 수행한다.

## Acceptance Criteria

### AuditQueryUseCaseTest (6개)

1. `query_sourceNullWithAccountId_queriesAllThreeSources` — source=null + accountId 있음 → 세 소스(admin, login_history, suspicious) 모두 조회, totalElements=3
2. `query_sourceAdmin_securityClientNotCalled` — source=admin → SecurityServiceClient 호출 없음
3. `query_nullAccountId_skipsLoginAndSuspicious` — accountId=null → login·suspicious 조회 스킵
4. `query_sizeAboveMax_clampedToHundred` — size=200 → Pageable.pageSize=100으로 클램프
5. `query_sizeZero_clampedToOne` — size=0 → Pageable.pageSize=1로 클램프
6. `query_recordsMetaAuditWithAuditQueryCode` — query 호출 시 ActionCode.AUDIT_QUERY로 메타-감사 기록

### OperatorRoleResolverTest (11개)

7. `resolveRoles_null_returnsEmptyMap` — null 입력 → empty map
8. `resolveRoles_empty_returnsEmptyMap` — [] 입력 → empty map
9. `resolveRoles_knownRole_returnsPopulatedMap` — 알려진 role → populated map
10. `resolveRoles_unknownRole_throwsRoleNotFoundException` — 알 수 없는 role → RoleNotFoundException
11. `resolveRoles_blankAndNullEntries_skipped` — null·공백 항목은 스킵하고 유효한 항목만 반환
12. `resolveRoles_duplicateNames_deduplicates` — 중복 이름 → 크기=1
13. `resolveRoles_multipleKnownRoles_returnsAllInOrder` — 두 role 모두 존재 → 모두 반환
14. `resolveActorInternalId_nullActor_returnsNull` — null actor → null
15. `resolveActorInternalId_nullOperatorId_returnsNull` — operatorId=null → null
16. `resolveActorInternalId_notFoundInRepo_returnsNull` — 레포지토리에 없음 → null
17. `resolveActorInternalId_found_returnsId` — 레포지토리에 존재 → internal id 반환
18. `normalizeReason_null_returnsPlaceholder` — null → REASON_NOT_PROVIDED 상수 반환
19. `normalizeReason_blank_returnsPlaceholder` — 공백 → REASON_NOT_PROVIDED 상수 반환
20. `normalizeReason_givenValue_returnsAsIs` — 값 있음 → 그대로 반환

## Related Specs

- `specs/services/admin-service/architecture.md`

## Related Contracts

없음 (단위 테스트, HTTP/이벤트 계약 변경 없음)

## Edge Cases

- `OperatorRoleResolverTest`는 package-private `OperatorRoleResolver`를 반사(reflection)로 인스턴스화하는 `OperatorUseCaseTestSupport`에 의존함
- `AuditQueryUseCaseTest`는 `@MockitoSettings(strictness = Strictness.LENIENT)` 사용 (source 분기 테스트에서 일부 스텁이 호출되지 않을 수 있음)

## Failure Scenarios

- 테스트 파일 중 하나라도 컴파일 실패 시 커밋하지 않음
- `OperatorUseCaseTestSupport`가 없으면 `OperatorRoleResolverTest` 컴파일 불가 — 이미 추적 중인 파일이므로 문제 없음
