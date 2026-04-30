---
id: TASK-BE-215
title: "security-service architecture.md RecordLoginHistoryUseCase outbox 이벤트 목록 보완"
type: spec-fix
status: ready
service: security-service
---

## Goal

`specs/services/security-service/architecture.md` 119번 줄 `RecordLoginHistoryUseCase` 설명에 outbox 이벤트가 `suspicious.detected`, `auto.lock.triggered` 2개만 기재되어 있다. TASK-BE-209에서 추가된 `auto.lock.pending` 이벤트가 누락되어 있어 스펙과 구현이 불일치한다. 목록을 3개로 보완한다.

## Scope

### 수정
- `specs/services/security-service/architecture.md` line 119
  - Before: `outbox 이벤트(\`suspicious.detected\`, \`auto.lock.triggered\`) 같이 기록`
  - After: `outbox 이벤트(\`suspicious.detected\`, \`auto.lock.triggered\`, \`auto.lock.pending\`) 같이 기록`

## Acceptance Criteria

- [ ] `architecture.md` 119번 줄에 `auto.lock.pending`이 포함된다
- [ ] `specs/contracts/events/security-events.md`의 `security.auto.lock.pending` 섹션과 일치한다

## Related Specs

- `specs/services/security-service/architecture.md`
- `specs/contracts/events/security-events.md`

## Related Contracts

- 없음

## Edge Cases

- 없음 (스펙 문서 1줄 수정)

## Failure Scenarios

- 없음
