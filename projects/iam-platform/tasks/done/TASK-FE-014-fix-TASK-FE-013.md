---
id: TASK-FE-014
title: "Fix issues found in TASK-FE-013 — 500 에러 토스트 메시지 및 누락 테스트"
status: ready
area: frontend
service: admin-web
---

## Goal

Fix issues found in TASK-FE-013.

TASK-FE-013 구현에서 발견된 두 가지 문제를 수정한다:

1. `500 INTERNAL_SERVER_ERROR` 시 토스트 메시지가 태스크 스펙("서버 오류가 발생했습니다.")과 불일치
2. 500 에러 경로에 대한 단위 테스트 누락

## Background

- `ChangePasswordDialog.tsx` 의 catch 블록에서 `500` 응답은 `err.message` (서버가 보내는 임의의 메시지)를 그대로 표시하거나, `ApiError`가 아닌 경우 "작업에 실패했습니다."를 표시한다.
- 태스크 Failure Scenarios는 "500 INTERNAL_SERVER_ERROR: toast '서버 오류가 발생했습니다.'" 를 명시하고 있다.
- 테스트 파일 `tests/unit/ChangePasswordDialog.test.tsx` 에는 500 에러 경로 테스트가 없다.

## Scope

### In

1. **`features/operators/components/ChangePasswordDialog.tsx`** 수정
   - catch 블록에서 `err instanceof ApiError && err.status >= 500` 조건 처리 추가
   - 해당 경우 toast "서버 오류가 발생했습니다." 표시

2. **`tests/unit/ChangePasswordDialog.test.tsx`** 수정
   - 500 에러 경로 테스트 케이스 추가:
     - `ApiError(500, 'INTERNAL_SERVER_ERROR', ...)` 로 reject 시 "서버 오류가 발생했습니다." 토스트 노출 확인
     - Dialog가 닫히지 않음 확인

### Out

- 다른 컴포넌트 수정
- 새로운 기능 추가

## Acceptance Criteria

- [ ] `ChangePasswordDialog`에서 500 에러 발생 시 toast "서버 오류가 발생했습니다." 표시
- [ ] 500 에러 시 Dialog 닫히지 않음
- [ ] `tests/unit/ChangePasswordDialog.test.tsx`에 500 에러 경로 테스트 케이스 추가됨
- [ ] 기존 테스트 모두 통과 (`npx vitest run`)

## Related Specs

- `specs/features/password-management.md`
- `specs/features/operator-management.md`

## Related Contracts

- `specs/contracts/http/admin-api.md` — `PATCH /api/admin/operators/me/password` Errors 섹션

## Edge Cases

- `ApiError` 이지만 status가 500이 아닌 기타 에러(예: 403, 404): `err.message` 표시 (기존 동작 유지)
- `ApiError`가 아닌 네트워크 에러: "작업에 실패했습니다." 표시 (기존 동작 유지)

## Failure Scenarios

- 수정 후 기존 테스트 깨지는 경우: 기존 동작과의 호환성 유지하며 재작성

## Test Requirements

- 500 에러 케이스 1건 추가 (`shows 서버 오류 toast on 500 error`)
- 기존 4개 케이스 모두 유지 통과
