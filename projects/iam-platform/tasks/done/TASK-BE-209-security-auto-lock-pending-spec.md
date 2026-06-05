---
id: TASK-BE-209
title: "security.auto.lock.pending 이벤트 계약서 추가"
type: spec
status: ready
service: security-service
---

## Goal

`security.auto.lock.pending` 토픽이 SecurityEventPublisher에 구현되어 있으나 specs/contracts/events/security-events.md에 미등재.
코드-스펙 불일치를 해소하기 위해 계약서에 해당 이벤트 섹션을 추가한다.

## Scope

- `specs/contracts/events/security-events.md` 에 `security.auto.lock.pending` 섹션 추가
- 구현 코드 변경 없음

## Acceptance Criteria

- [ ] `security.auto.lock.pending` 섹션이 security-events.md에 존재한다
- [ ] 발행 조건 (account-service HTTP 재시도 전부 실패) 이 명시된다
- [ ] 페이로드 스키마 6개 필드 (`suspiciousEventId`, `accountId`, `ruleCode`, `riskScore`, `reason`, `raisedAt`) 가 문서화된다
- [ ] 소비자 목록 (admin-service 수동 개입 경로) 이 명시된다
- [ ] 기존 두 이벤트 섹션과 동일한 형식으로 작성된다

## Related Specs

- `specs/contracts/events/security-events.md`
- `specs/services/security-service/architecture.md`

## Related Contracts

- `specs/contracts/events/security-events.md` (수정 대상)

## Edge Cases

- `reason` 필드는 현재 `ACCOUNT_SERVICE_UNREACHABLE` 단일 값이나, 향후 확장 가능성을 고려해 string 타입으로 기술

## Failure Scenarios

- 없음 (스펙 문서 추가 작업)
