---
id: TASK-BE-167
title: "security-service auth 이벤트 Kafka 컨슈머 단위 테스트 추가"
status: ready
priority: high
assignee: backend
created: 2026-04-29
---

## Goal

`AbstractAuthEventConsumer`를 상속하는 5개 컨슈머 클래스에 대한 단위 테스트를 작성한다.
기존 `AccountLockedConsumerTest` 패턴을 따르며, dedup·DB중복·malformed JSON·detection 오류
흡수 등 공통 베이스 로직과 각 컨슈머의 defaultOutcome/resolveOutcome 재정의를 모두 커버한다.

## Scope

- `apps/security-service`
- 신규 파일 5개:
  - `LoginAttemptedConsumerTest.java`
  - `LoginFailedConsumerTest.java`
  - `LoginSucceededConsumerTest.java`
  - `TokenRefreshedConsumerTest.java`
  - `TokenReuseDetectedConsumerTest.java`

## Acceptance Criteria

### LoginAttemptedConsumer (3개)
- 정상 처리 → outcome=ATTEMPTED, recordLoginHistoryUseCase.execute() 호출
- Redis dedup hit → execute() 호출 없음
- eventId 공백 → 조용히 skip (예외 없음, execute 미호출)

### LoginFailedConsumer (3개)
- failureReason 없음 → outcome=FAILURE
- failureReason=RATE_LIMITED → outcome=RATE_LIMITED
- Redis dedup hit → execute() 미호출

### LoginSucceededConsumer (3개)
- 정상 처리 → outcome=SUCCESS, dedup mark 호출
- DB 중복(execute=false) → markProcessedInRedis 미호출
- Malformed JSON → RuntimeException 전파

### TokenRefreshedConsumer (3개)
- 정상 처리 → outcome=REFRESH
- detection 오류 → 예외 흡수 (RuntimeException 미전파)
- eventId 공백 → 조용히 skip

### TokenReuseDetectedConsumer (3개)
- 정상 처리 → outcome=TOKEN_REUSE
- Redis dedup hit → execute() 미호출
- Malformed JSON → RuntimeException 전파

## Related Specs

- `specs/services/security-service/architecture.md`

## Related Contracts

없음

## Edge Cases

- eventId 공백: AbstractAuthEventConsumer는 예외를 던지지 않고 return
- detection 오류: RuntimeException을 catch하여 흡수 (history는 보존)

## Failure Scenarios

- RuntimeException 미전파 시 DLQ 라우팅 불가 → malformed JSON 테스트 실패
