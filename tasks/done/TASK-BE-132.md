# Task ID

TASK-BE-132

# Title

admin-service — AccountAdminUseCase 감사 실패 기록 인라인 중복 제거 — recordAuditFailure 헬퍼 추출

# Status

ready

# Owner

backend

# Task Tags

- refactor

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

`AccountAdminUseCase`의 `lock()`과 `unlock()` 메서드 각각에서 아래 패턴이 두 차례씩 (CallNotPermittedException, DownstreamFailureException), 총 4회 반복된다:

```java
auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
        auditId, actionCode, cmd.operator(),
        "ACCOUNT", cmd.accountId(),
        cmd.reason(), cmd.ticketId(), cmd.idempotencyKey(),
        Outcome.FAILURE, "<failureMessage>",
        startedAt, Instant.now()));
throw ex;
```

`private void recordAuditFailure(String auditId, ActionCode actionCode, OperatorContext operator, String targetId, String reason, String ticketId, String idempotencyKey, Instant startedAt, String failureMessage)` 헬퍼를 추출하여, 4개의 catch 블록을 2줄(`recordAuditFailure(...); throw ex;`)로 단순화한다.

---

# Scope

## In Scope

- `AccountAdminUseCase.java` 단일 파일 수정
- `private void recordAuditFailure(...)` 헬퍼 추출
- `lock()` CallNotPermittedException catch — `"CIRCUIT_OPEN: " + ex.getMessage()` 메시지로 헬퍼 호출
- `lock()` DownstreamFailureException catch — `ex.getMessage()` 메시지로 헬퍼 호출
- `unlock()` 동일한 두 catch 블록도 헬퍼 호출로 대체

## Out of Scope

- `AdminActionAuditor` 변경 없음
- `lock()` / `unlock()` 의 성공 경로(`recordCompletion(SUCCESS)`) 변경 없음
- API 계약 변경 없음
- 행위(behavior) 변경 없음

---

# Acceptance Criteria

- [ ] `recordAuditFailure` private 헬퍼가 추가된다
- [ ] `lock()` / `unlock()` 의 두 catch 블록(총 4개)이 각각 `recordAuditFailure(...)` + `throw ex` 2줄로 대체된다
- [ ] `Outcome.FAILURE`와 `Instant.now()` 호출이 헬퍼 내부에서만 발생한다
- [ ] 기존 `AccountAdminUseCaseTest`가 모두 통과한다
- [ ] 빌드 통과

---

# Related Specs

- `specs/services/admin-service/architecture.md`
- `specs/services/admin-service/overview.md`

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

없음 — 행위 변경 없음

---

# Target Service

- `admin-service`

---

# Architecture

Follow:

- `specs/services/admin-service/architecture.md`
- Thin Layered (Command Gateway): application 레이어 내부 리팩토링

---

# Implementation Notes

헬퍼 시그니처:
```java
private void recordAuditFailure(String auditId, ActionCode actionCode,
                                 OperatorContext operator, String targetId,
                                 String reason, String ticketId, String idempotencyKey,
                                 Instant startedAt, String failureMessage) {
    auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
            auditId, actionCode, operator,
            "ACCOUNT", targetId,
            reason, ticketId, idempotencyKey,
            Outcome.FAILURE, failureMessage,
            startedAt, Instant.now()));
}
```

- `Instant.now()`는 헬퍼 내부에서 호출 — `completedAt`은 catch 시점에 캡처된다.
- `throw ex`는 caller에서 유지 (헬퍼가 re-throw하면 제네릭 처리가 필요해 복잡해짐).
- `CallNotPermittedException` catch: `failureMessage = "CIRCUIT_OPEN: " + ex.getMessage()`
- `DownstreamFailureException` catch: `failureMessage = ex.getMessage()`

---

# Edge Cases

- `ex.getMessage()`가 null인 경우: `DownstreamFailureException` 생성자가 메시지를 항상 제공한다고 가정하나, null safe하게 동작해야 한다. `AdminActionAuditor.CompletionRecord`의 `downstreamDetail` 필드는 nullable을 허용한다.
- `CallNotPermittedException`은 Resilience4j가 던지므로 메시지가 항상 non-null이다.

---

# Failure Scenarios

- 헬퍼 내부에서 `auditor.recordCompletion`이 실패하면 `AuditFailureException`이 전파된다 — 이는 기존 동작과 동일하게 유지.
- `throw ex` 순서: 헬퍼 호출 후 반드시 throw해야 하며, 헬퍼가 성공적으로 완료된 후에도 caller가 예외를 전파하지 않으면 lock/unlock 결과가 오류 없이 반환되는 버그 발생 — 기존 테스트로 감지 가능.

---

# Test Requirements

- 기존 `AccountAdminUseCaseTest` 전체 케이스 재실행하여 모두 통과 확인
- 추가 테스트 불필요 (행위 변경 없음, 기존 커버리지로 충분)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing
- [ ] Contracts updated if needed (해당 없음)
- [ ] Specs updated first if required (해당 없음)
- [ ] Ready for review
