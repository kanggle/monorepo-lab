---
id: TASK-FE-005
title: "fix(TASK-FE-004): X-Operator-Reason 헤더 URL 인코딩 제거 + LoginForm 테스트 수정"
status: ready
area: frontend
service: admin-web
---

## Goal

TASK-FE-004 구현 중 도입된 두 가지 회귀를 수정한다.

1. `client.ts`의 `X-Operator-Reason` 헤더에 `encodeURIComponent`를 적용한 변경은 HTTP 헤더 규약에 위배되며, 기존 `LockDialog.test.tsx`를 깨뜨린다. 이 인코딩을 제거하여 원래 동작을 복원해야 한다.
2. `LoginForm.tsx`는 TASK-FE-003(commit 7b0be87)에서 필드명이 `이메일` → `운영자 ID`로 변경되었으나 `LoginForm.test.tsx`가 업데이트되지 않아 테스트 2건이 실패한다. 테스트를 현행 컴포넌트에 맞게 수정해야 한다.

## Scope

1. `apps/admin-web/src/shared/api/client.ts`
   - `X-Operator-Reason` 헤더 설정 시 `encodeURIComponent` 호출 제거 (원래대로 평문 문자열 그대로 설정)
2. `apps/admin-web/tests/unit/LoginForm.test.tsx`
   - `getByLabelText('이메일')` → `getByLabelText('운영자 ID')`로 교체 (해당 테스트 2건)
   - 기타 테스트 동작은 변경 없음

## Acceptance Criteria

- [ ] `npx vitest run --reporter=verbose` 실행 시 `LockDialog.test.tsx` 전체 통과
- [ ] `npx vitest run --reporter=verbose` 실행 시 `LoginForm.test.tsx` 전체 통과
- [ ] `X-Operator-Reason` 헤더 값이 URL 인코딩 없이 원문 문자열로 전송됨
- [ ] TASK-FE-004에서 추가된 GDPR 삭제 기능 동작에 영향 없음

## Related Specs

- `specs/contracts/http/admin-api.md` — `X-Operator-Reason` 헤더 규약

## Related Contracts

- `specs/contracts/http/admin-api.md`

## Edge Cases

- `X-Operator-Reason` 값에 공백이 포함된 경우: 인코딩 없이 그대로 헤더에 설정되어야 함
- `LoginForm` 테스트에서 `operatorId` 필드가 올바르게 식별되어야 함

## Failure Scenarios

- 다른 테스트에 의존성 있는 경우: 변경 범위를 최소화하여 기존 통과 테스트에 영향 없도록 수정
