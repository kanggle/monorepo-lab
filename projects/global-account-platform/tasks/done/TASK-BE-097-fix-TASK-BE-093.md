---
id: TASK-BE-097
title: "fix: TASK-BE-093 — account.deleted(anonymized=true) 이벤트 payload 수정 (gracePeriodEndsAt 누락, reasonCode 하드코딩)"
status: ready
area: backend
service: account-service
parent: TASK-BE-093
---

## Goal

TASK-BE-093에서 구현된 `AccountAnonymizationScheduler`가 재발행하는 `account.deleted(anonymized=true)` 이벤트가
`specs/contracts/events/account-events.md`의 payload 스키마를 두 가지 측면에서 위반한다.
이를 수정하여 컨트랙트를 완전히 준수하도록 한다.

## Scope

### In

1. **`AccountEventPublisher.publishAccountDeletedAnonymized` 시그니처 확장**
   - 파라미터에 `Instant gracePeriodEndsAt` 추가
   - payload에 `"gracePeriodEndsAt"` 필드 포함 (account-events.md 스키마 준수)

2. **`AccountAnonymizationScheduler.AnonymizationTransaction.anonymizeOne` 수정**
   - `reasonCode` 하드코딩(`USER_REQUEST`) 제거
   - `account.getDeletedAt()`이 이미 존재하므로 `gracePeriodEndsAt = Instant.now()` (익명화 시각)를 계산하여 전달
   - `reasonCode`는 `account_status_history`에서 원래 삭제 사유를 조회하거나,
     또는 `Account` 도메인 객체에 `deletionReason` 필드를 추가하는 방식으로 복원.
     **우선 접근**: `AccountStatusHistoryRepository`를 통해 해당 계정의 마지막 `DELETED` 전이 행을 조회하여
     `reason_code`를 가져온다. 조회 실패(row 없음) 시 `USER_REQUEST`를 fallback으로 사용하고 WARN 로그.

3. **모든 `publishAccountDeletedAnonymized` 호출부 업데이트**
   - `GdprDeleteUseCase`: `gracePeriodEndsAt` 인수 추가 (`Instant.now()` — 즉시 익명화이므로 deletedAt = gracePeriodEndsAt)
   - `AccountAnonymizationScheduler`: 위 2번의 수정에 맞춰 인수 전달

4. **단위 테스트 업데이트 (`AccountAnonymizationSchedulerTest`)**
   - `publishAccountDeletedAnonymized` verify 구문에 `gracePeriodEndsAt` 인수 추가
   - `reasonCode` 검증 시나리오 추가 (정상 케이스에서 history 조회 mock 설정 및 결과 검증)

5. **`scheduler.anonymize.duration_ms` histogram 추가 (Warning 항목)**
   - `runAnonymizationBatch()` 시작/종료 시각 측정 후 `meterRegistry.timer("scheduler.anonymize.duration_ms")` 기록

### Out

- `scheduler.anonymize.overdue` gauge (별도 관찰성 태스크로 분리)
- `AnonymizationTransaction` static inner class를 top-level로 이동 (아키텍처 변경 없이 동작하므로 별도 리팩토링 태스크)
- `PiiAnonymizer.sha256Hex()` / `GdprDeleteUseCase.sha256()` 중복 통합 (libs 변경 필요, 별도 태스크)

## Acceptance Criteria

- [ ] `publishAccountDeletedAnonymized` payload에 `gracePeriodEndsAt` 포함 (account-events.md 준수)
- [ ] 배치 경로에서 `reasonCode`가 `USER_REQUEST`로 고정되지 않고 원래 삭제 사유를 반영
- [ ] `GdprDeleteUseCase` 호출부도 컴파일 오류 없이 업데이트
- [ ] `scheduler.anonymize.duration_ms` timer 기록
- [ ] 단위 테스트에서 `gracePeriodEndsAt` 전달 검증
- [ ] `./gradlew :apps:account-service:test` 전체 통과

## Related Specs

- specs/contracts/events/account-events.md — account.deleted payload 스키마
- specs/services/account-service/retention.md — §2.7 발행 이벤트, §2.10 지표

## Related Contracts

- specs/contracts/events/account-events.md

## Edge Cases

- `account_status_history`에 해당 계정의 DELETED 전이 row가 없는 경우: `USER_REQUEST` fallback + WARN 로그
- `gracePeriodEndsAt` 값 — 배치 경로에서는 "익명화 완료 시각"(`Instant.now()`)을 사용 (retention.md §2.7)
- GDPR 경로에서는 `deletedAt` == `gracePeriodEndsAt` (즉시 익명화)

## Failure Scenarios

- `publishAccountDeletedAnonymized` 시그니처 변경 시 컴파일 오류: 모든 호출부(2개)를 동시에 수정
- history 조회 추가 시 N+1 위험: 배치 전체 계정 수는 일반적으로 소규모이므로 허용 범위. 단, 별도 쿼리임을 Javadoc에 명시
