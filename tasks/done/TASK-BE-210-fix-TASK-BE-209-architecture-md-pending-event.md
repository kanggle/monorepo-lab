---
id: TASK-BE-210
title: "security-service architecture.md Integration Rules에 auto.lock.pending 이벤트 추가"
type: spec
status: ready
service: security-service
related: TASK-BE-209
---

## Goal

TASK-BE-209에서 `security.auto.lock.pending` 이벤트가 `specs/contracts/events/security-events.md`에 추가되었으나,
`specs/services/security-service/architecture.md`의 Integration Rules 섹션에는 여전히 두 개의 발행 이벤트만 기재되어 있다.
코드·계약서·아키텍처 문서 간 일관성을 확보하기 위해 architecture.md를 갱신한다.

## Scope

- `specs/services/security-service/architecture.md` Integration Rules 섹션 수정
  - `이벤트 발행` 항목에 `auto.lock.pending` 추가
- 코드 변경 없음

## Acceptance Criteria

- [ ] `specs/services/security-service/architecture.md`의 Integration Rules — 이벤트 발행 항목이 세 개의 토픽 (`suspicious.detected`, `auto.lock.triggered`, `auto.lock.pending`) 을 모두 기재한다
- [ ] 기존 두 항목의 서술 방식과 동일한 형식을 유지한다

## Related Specs

- `specs/services/security-service/architecture.md`
- `specs/contracts/events/security-events.md`

## Related Contracts

- 없음 (계약서 변경 없음)

## Edge Cases

- 없음

## Failure Scenarios

- 없음 (스펙 문서 수정 작업)
