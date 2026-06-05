# Task ID

TASK-BE-134

# Title

admin-service — GdprAdminUseCase / SessionAdminUseCase 감사 실패 인라인 중복 제거 — recordAuditFailure 헬퍼 추출

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

TASK-BE-132 (`AccountAdminUseCase.recordAuditFailure` 헬퍼 추출)의 패턴을 admin-service의 나머지 두 use-case에 동일하게 적용한다.

`GdprAdminUseCase.gdprDelete()` / `GdprAdminUseCase.dataExport()` / `SessionAdminUseCase.revoke()` 의 catch 블록에서 반복되는 audit FAILURE 기록 호출(`auditor.recordCompletion(new CompletionRecord(...))` 또는 `auditor.record(new AuditRecord(...))`)을 private 헬퍼로 추출하여 catch 블록을 2줄(`recordAuditFailure(...); throw ex;`)로 단순화한다.

대상 catch 블록 (총 6개):

- `GdprAdminUseCase.gdprDelete()` — `CallNotPermittedException` (CompletionRecord 사용)
- `GdprAdminUseCase.gdprDelete()` — `DownstreamFailureException` (CompletionRecord 사용)
- `GdprAdminUseCase.dataExport()` — `CallNotPermittedException` (AuditRecord 사용 — 단발성 기록)
- `GdprAdminUseCase.dataExport()` — `DownstreamFailureException` (AuditRecord 사용 — 단발성 기록)
- `SessionAdminUseCase.revoke()` — `CallNotPermittedException` (CompletionRecord 사용, entityType="SESSION")
- `SessionAdminUseCase.revoke()` — `DownstreamFailureException` (CompletionRecord 사용, entityType="SESSION")

---

# Scope

## In Scope

- `apps/admin-service/src/main/java/com/example/admin/application/GdprAdminUseCase.java`
  - `private void recordAuditFailure(...)` (CompletionRecord 호출, entityType="ACCOUNT" 하드코딩)
  - `private void recordSingleShotAuditFailure(...)` (AuditRecord 호출, entityType="ACCOUNT" 하드코딩)
  - `gdprDelete()` 두 catch → `recordAuditFailure(...) + throw ex`
  - `dataExport()` 두 catch → `recordSingleShotAuditFailure(...) + throw ex`
- `apps/admin-service/src/main/java/com/example/admin/application/SessionAdminUseCase.java`
  - `private void recordAuditFailure(...)` (CompletionRecord 호출, entityType="SESSION" 하드코딩)
  - `revoke()` 두 catch → `recordAuditFailure(...) + throw ex`

## Out of Scope

- `AdminActionAuditor` 변경 없음
- 성공 경로(`recordCompletion(SUCCESS)`, `record(SUCCESS)`) 변경 없음
- `AccountAdminUseCase` 변경 없음 (TASK-BE-132에서 이미 처리)
- API 계약, 이벤트 envelope 변경 없음
- 행위(behavior) 변경 없음

---

# Acceptance Criteria

- [ ] `GdprAdminUseCase`에 `recordAuditFailure`(CompletionRecord 용) 헬퍼가 추가된다
- [ ] `GdprAdminUseCase`에 `recordSingleShotAuditFailure`(AuditRecord 용) 헬퍼가 추가된다
- [ ] `SessionAdminUseCase`에 `recordAuditFailure`(CompletionRecord 용, entityType="SESSION") 헬퍼가 추가된다
- [ ] 6개의 catch 블록 모두 헬퍼 호출 + `throw ex` 2줄로 대체된다
- [ ] `Outcome.FAILURE`와 `Instant.now()`(completedAt) 호출이 헬퍼 내부에서만 발생한다
- [ ] 기존 admin-service 테스트가 모두 통과한다
- [ ] `./gradlew :apps:admin-service:test` 통과

---

# Related Specs

- `specs/services/admin-service/architecture.md`

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

### GdprAdminUseCase 헬퍼 시그니처

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

private void recordSingleShotAuditFailure(String auditId, ActionCode actionCode,
                                          OperatorContext operator, String targetId,
                                          String reason, Instant startedAt,
                                          String failureMessage) {
    auditor.record(new AdminActionAuditor.AuditRecord(
            auditId, actionCode, operator,
            "ACCOUNT", targetId,
            reason, null, null,
            Outcome.FAILURE, failureMessage,
            startedAt, Instant.now()));
}
```

### SessionAdminUseCase 헬퍼 시그니처

```java
private void recordAuditFailure(String auditId, ActionCode actionCode,
                                OperatorContext operator, String targetId,
                                String reason, String idempotencyKey,
                                Instant startedAt, String failureMessage) {
    auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
            auditId, actionCode, operator,
            "SESSION", targetId,
            reason, null, idempotencyKey,
            Outcome.FAILURE, failureMessage,
            startedAt, Instant.now()));
}
```

### Pattern Notes

- `Instant.now()`는 헬퍼 내부에서 호출 — `completedAt`은 catch 시점에 캡처된다.
- `throw ex`는 caller에서 유지 (BE-132 패턴과 동일 — 헬퍼가 re-throw하면 제네릭 처리가 필요해 복잡해짐).
- `CallNotPermittedException` catch: `failureMessage = "CIRCUIT_OPEN: " + ex.getMessage()`
- `DownstreamFailureException` catch: `failureMessage = ex.getMessage()`
- `dataExport()`의 `ticketId` / `idempotencyKey`는 모두 null — `AuditRecord` 헬퍼는 이 두 값을 null로 하드코딩.

---

# Edge Cases

- `ex.getMessage()`가 null인 경우: `CompletionRecord`의 `downstreamDetail`은 nullable이며 `AuditRecord`도 동일.
- `CallNotPermittedException`은 Resilience4j가 던지므로 메시지가 항상 non-null.
- `dataExport()`의 entityType은 "ACCOUNT" — `AdminActionAuditor.normalizeTargetType()`이 ActionCode.DATA_EXPORT의 경우 "ACCOUNT"를 그대로 유지하므로 envelope 영향 없음.
- `SessionAdminUseCase.revoke()`의 entityType은 "SESSION" — 마찬가지로 ActionCode.SESSION_REVOKE의 경우 normalizeTargetType이 "SESSION"으로 정규화하므로 envelope 영향 없음.

---

# Failure Scenarios

- 헬퍼 내부 `auditor.recordCompletion` / `auditor.record` 실패 시 `AuditFailureException`이 전파된다 — 기존 동작과 동일.
- `throw ex` 순서: 헬퍼 호출 후 반드시 throw — 누락 시 catch 블록이 정상 흐름으로 빠지는 버그 발생 (기존 테스트로 감지 가능).

---
