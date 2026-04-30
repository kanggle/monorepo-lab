---
id: TASK-BE-166
title: "admin-service AuditQueryUseCase·OperatorRoleResolver 단위 테스트 추가"
status: ready
priority: medium
assignee: backend
created: 2026-04-29
---

## Goal

admin-service application 레이어의 마지막 미테스트 클래스 2개에 대한 단위 테스트를 작성하여
서비스 전체 애플리케이션 레이어 커버리지를 완성한다.

## Scope

- `apps/admin-service`
- 신규 파일 2개:
  - `AuditQueryUseCaseTest.java`
  - `OperatorRoleResolverTest.java`

## Acceptance Criteria

### AuditQueryUseCase (6개)
- source=null + accountId 있음 → admin·login_history·suspicious 세 소스 모두 조회
- source="admin" → SecurityServiceClient 호출 없음
- accountId=null → login_history·suspicious 조회 스킵
- size>100 → 100으로 클램프
- size<1 (0) → 1로 클램프
- 메타-감사(AUDIT_QUERY) 기록 검증

### OperatorRoleResolver (13개)
- resolveRoles(null) → empty map
- resolveRoles([]) → empty map
- resolveRoles(["SUPER_ADMIN"]) → populated map
- resolveRoles(["UNKNOWN"]) → RoleNotFoundException
- resolveRoles(blank·null 포함) → 스킵
- resolveRoles(중복 이름) → deduplication
- resolveActorInternalId(null) → null
- resolveActorInternalId(operatorId=null) → null
- resolveActorInternalId(미존재) → null
- resolveActorInternalId(존재) → id 반환
- normalizeReason(null) → "<not_provided>"
- normalizeReason(blank) → "<not_provided>"
- normalizeReason(값 있음) → 그대로 반환

## Related Specs

- `specs/services/admin-service/architecture.md`

## Related Contracts

없음

## Edge Cases

- AuditQueryUseCase: source="suspicious" + accountId=null → suspicious 쿼리 스킵
- OperatorRoleResolver: 리스트에 null과 공백이 섞인 경우 모두 스킵

## Failure Scenarios

- RoleNotFoundException 미발생 시 테스트 실패
- SecurityServiceClient 호출 발생 시 verify(never()) 실패
