---
id: TASK-BE-102
title: "fix: TASK-BE-097 — account.deleted(anonymized=true) 재발행 시 actorType/actorId가 원래 삭제 사유와 함께 history에서 복원되지 않는 문제"
status: ready
area: backend
service: account-service
parent: TASK-BE-097
---

## Goal

TASK-BE-097에서 구현된 `AccountAnonymizationScheduler.AnonymizationTransaction`이
`account.deleted(anonymized=true)` 이벤트를 재발행할 때 `actorType`과 `actorId`를
`"system"`/`null`로 하드코딩하여 `specs/services/account-service/retention.md` §2.7의
"원래 DELETED 전이 시 발행한 값 그대로" 규칙을 위반한다.

`reasonCode`는 `account_status_history`에서 올바르게 복원되지만,
`actorType`과 `actorId`는 동일한 history row에서 함께 복원해야 한다.

## Scope

### In

1. **`AnonymizationTransaction.resolveOriginalDeletionReason` 반환값 확장**
   - 메서드 이름을 `resolveOriginalDeletionContext`로 변경 (또는 record/별도 클래스 도입)
   - `reasonCode`, `actorType`, `actorId`를 함께 반환하는 구조체(record 또는 inner class)를 사용한다
   - `AccountStatusHistoryEntry`에서 `actorType`, `actorId`도 함께 추출한다
   - fallback(history row 없음) 시: `reasonCode=USER_REQUEST`, `actorType="system"`, `actorId=null` + WARN 로그

2. **`AnonymizationTransaction.anonymizeOne` 수정**
   - 위 구조체에서 `actorType`, `actorId`를 추출하여 `publishAccountDeletedAnonymized` 호출 시 전달한다
   - 현재 하드코딩된 `"system"`, `null` 제거

3. **단위 테스트 `AccountAnonymizationSchedulerTest` 수정**
   - 정상 케이스(USER_REQUEST): history row의 `actorType="user"`, `actorId=accountId`가 이벤트에 그대로 전달되는지 검증
   - 정상 케이스(ADMIN_DELETE): history row의 `actorType="operator"`, `actorId="op-1"`이 이벤트에 그대로 전달되는지 검증
   - fallback 케이스: history 없을 때 `actorType="system"`, `actorId=null`이 전달되는지 검증

### Out

- `AccountStatusHistoryRepository` 인터페이스 변경 없음 (이미 `findByAccountIdOrderByOccurredAtDesc` 존재)
- `AccountStatusHistoryEntry` 도메인 클래스 변경 없음 (`actorType`, `actorId` 이미 존재)
- `GdprDeleteUseCase` 변경 없음 (즉시 삭제 경로이므로 caller가 직접 `actorType`, `actorId` 전달)
- 다른 서비스 변경 없음

## Acceptance Criteria

- [ ] `account.deleted(anonymized=true)` 재발행 시 `actorType`과 `actorId`가 `account_status_history`의 마지막 DELETED 전이 row에서 복원되어 전달됨
- [ ] fallback(history row 없음) 시 `actorType="system"`, `actorId=null`이 적용되고 WARN 로그 발생
- [ ] 단위 테스트에서 각 케이스(USER_REQUEST/ADMIN_DELETE/fallback)에 대해 `actorType`, `actorId`가 정확히 검증됨
- [ ] `./gradlew :apps:account-service:test` 전체 통과 (Testcontainer 통합 테스트는 Docker 미지원 환경에서 SKIP 허용)

## Related Specs

- specs/services/account-service/retention.md — §2.7 발행 이벤트 ("reasonCode, actorType, actorId 원래 DELETED 전이 시 발행한 값 그대로")
- specs/contracts/events/account-events.md — account.deleted payload 스키마

## Related Contracts

- specs/contracts/events/account-events.md

## Edge Cases

- `account_status_history`에 해당 계정의 DELETED 전이 row가 없는 경우: `actorType="system"`, `actorId=null` fallback + WARN 로그
- history row에 `actorId=null`인 경우(시스템 자동 전이): null 그대로 전달
- 여러 DELETED 전이 row가 있는 경우: `findByAccountIdOrderByOccurredAtDesc`에서 첫 번째(가장 최근) row의 값을 사용

## Failure Scenarios

- resolveOriginalDeletionContext 반환 구조체 미정의 시 컴파일 오류: inner record 또는 private static class 사용 권장
- 단위 테스트에서 기존 `eq("system")`/`eq(null)` stub이 실패로 전환됨: 테스트를 실제 history 값으로 교체해야 함
