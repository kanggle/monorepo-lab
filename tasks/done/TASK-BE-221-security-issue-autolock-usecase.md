---
id: TASK-BE-221
title: security-service IssueAutoLockCommandUseCase 추출
type: refactoring
service: security-service
status: ready
---

## Goal

`specs/services/security-service/architecture.md:45`에 `IssueAutoLockCommandUseCase`가 명시되어 있으나 미구현 상태.
현재 `DetectSuspiciousActivityUseCase.triggerAutoLock()` (line 90–109)에 embed된 auto-lock 로직을
아키텍처 spec에 따라 별도 use case로 추출한다.

## Scope

1. `IssueAutoLockCommandUseCase.java` 신규 — `triggerAutoLock()` 로직 이관
2. `DetectSuspiciousActivityUseCase.java` 수정 — `accountLockClient` 직접 의존 제거, `IssueAutoLockCommandUseCase` 주입
3. `DetectSuspiciousActivityUseCaseTest.java` 수정 — `lockClient` mock 제거, `issueAutoLockCommandUseCase` mock으로 교체
4. `IssueAutoLockCommandUseCaseTest.java` 신규 — 신규 use case 단위 테스트

## Acceptance Criteria

### IssueAutoLockCommandUseCase
- `execute(SuspiciousEvent event)` 메서드 하나
- 내부 로직: `accountLockClient.lock(event)` → 결과 코드 정규화(SUCCESS/ALREADY_LOCKED/FAILURE) → `persistenceService.updateLockResult()` → `publisher.publishAutoLockTriggered()` → FAILURE이면 `publisher.publishAutoLockPending()` + log.warn
- 의존성: `AccountLockClient`, `SuspiciousEventPersistenceService`, `SecurityEventPublisher`

### DetectSuspiciousActivityUseCase
- `accountLockClient` 필드 및 주입 제거
- `issueAutoLockCommandUseCase` 주입 추가
- `triggerAutoLock()` private 메서드 제거
- `if (level == RiskLevel.AUTO_LOCK)` 블록: `issueAutoLockCommandUseCase.execute(persisted)` 호출

### DetectSuspiciousActivityUseCaseTest
- `@Mock AccountLockClient lockClient` → `@Mock IssueAutoLockCommandUseCase issueAutoLockCommandUseCase` 로 교체
- 생성자 주입 파라미터 변경
- AUTO_LOCK 관련 테스트: lockClient mock 제거, `verify(issueAutoLockCommandUseCase).execute(any())` 으로 교체
- ALERT/noFire/throwingRule 테스트: lockClient 관련 stub/verify 제거

### IssueAutoLockCommandUseCaseTest
네 가지 케이스:
- `execute_success_updatesResultAndPublishesTriggered`: SUCCESS → updateLockResult("SUCCESS"), publishAutoLockTriggered(SUCCESS), no publishAutoLockPending
- `execute_failure_updatesResultAndPublishesPending`: FAILURE → updateLockResult("FAILURE"), publishAutoLockTriggered(FAILURE), publishAutoLockPending
- `execute_alreadyLocked_updatesResult`: ALREADY_LOCKED → updateLockResult("ALREADY_LOCKED"), publishAutoLockTriggered(ALREADY_LOCKED), no publishAutoLockPending
- `execute_invalidTransition_normalizedToFailure`: INVALID_TRANSITION → updateLockResult("FAILURE") (정규화)

## Related Specs

- `specs/services/security-service/architecture.md` (IssueAutoLockCommandUseCase 명세)

## Related Contracts

없음 (내부 리팩토링)

## Edge Cases

- `DetectSuspiciousActivityUseCase`는 이제 `AccountLockClient`에 직접 의존하지 않음 → Spring bean 그래프 확인 필요 (Spring이 자동으로 IssueAutoLockCommandUseCase를 감지해야 함)

## Failure Scenarios

- `accountLockClient.lock()` 예외 발생 시: `IssueAutoLockCommandUseCase`에서 catch하지 않음 — 기존 동작 유지 (예외 전파)
