---
id: TASK-BE-165
title: "security-service SuspiciousEventPersistenceService 단위 테스트 추가"
status: ready
priority: medium
assignee: backend
created: 2026-04-29
---

## Goal

`SuspiciousEventPersistenceService`에 대한 단위 테스트를 작성하여 application 레이어 테스트 커버리지를 완성한다.

## Scope

- `apps/security-service`
- 신규 파일: `SuspiciousEventPersistenceServiceTest.java`

## Acceptance Criteria

- `recordSuspiciousEvent` — 정상 입력 시 `SuspiciousEvent`를 저장하고 반환한다
- `recordSuspiciousEvent` — 반환된 이벤트의 accountId, ruleCode, riskScore, actionTaken 값이 입력과 일치한다
- `updateLockResult` — `save(event)` 한 번 호출됨을 검증한다
- Mockito strict stubs 모드, `@ExtendWith(MockitoExtension.class)`

## Related Specs

- `specs/services/security-service/architecture.md`

## Related Contracts

없음

## Edge Cases

- `recordSuspiciousEvent`의 winner가 `DetectionResult.NONE`이 아닌 실제 fired result인 경우
- `updateLockResult`에 `withLockRequestResult` 적용 후 저장

## Failure Scenarios

- repository.save 미호출 시 테스트 실패 (verify)
