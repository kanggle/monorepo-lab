---
id: TASK-FE-023
title: "fix: TASK-FE-020 — GdprDeleteDialog 테스트 mock 반환값 수정 + 공백 reason Edge Case 추가 + useExportAccount mock 추가"
status: ready
area: frontend
service: admin-web
---

## Goal

TASK-FE-020 리뷰에서 발견된 3가지 문제를 수정한다:

1. `GdprDeleteDialog.test.tsx`의 `mutateAsync` mock 반환값이 실제 `GdprDeleteResponse` 타입과 불일치
2. Edge Case "reason 공백 문자열만 입력" 테스트 누락
3. `AccountDetail.test.tsx`에 `useExportAccount` mock 부재 (`AccountDetail.tsx`가 이를 사용함)

## Scope

### In

- `apps/admin-web/tests/unit/GdprDeleteDialog.test.tsx` 수정
  - `mutateAsync` mock 반환값을 실제 `GdprDeleteResponse` 타입과 일치하도록 수정
    - 올바른 필드: `{ accountId, status: 'DELETED', maskedAt, auditId }`
    - 제거 대상: `previousStatus`, `currentStatus`, `operatorId`, `deletedAt`
  - 공백 문자열 reason 제출 시 유효성 에러 표시 케이스 추가
    - reason에 `'   '` (공백만) 입력 후 제출 → `role="alert"` 표시, `mutateAsync` 미호출
- `apps/admin-web/tests/unit/AccountDetail.test.tsx` 수정
  - `vi.mock('@/features/accounts/hooks/useExportAccount', ...)` mock 추가
  - `mutateAsync: vi.fn()`, `isPending: false` 를 기본 반환값으로 설정

### Out

- `GdprDeleteDialog.tsx` 컴포넌트 구현 변경
- `useGdprDelete.ts` 훅 로직 변경
- `AccountDetail.tsx` 컴포넌트 변경
- E2E 테스트

## Acceptance Criteria

- [ ] `GdprDeleteDialog.test.tsx`의 모든 `mutateAsync.mockResolvedValue(...)` 호출이 `GdprDeleteResponse` 타입과 일치
  - `{ accountId: 'acc-1', status: 'DELETED', maskedAt: '2026-04-18T10:00:00Z', auditId: 'a-1' }` 형식 사용
  - `previousStatus`, `currentStatus`, `operatorId`, `deletedAt` 필드 제거
- [ ] reason에 공백 문자열만 입력한 경우 `role="alert"` 유효성 에러 표시, `mutateAsync` 미호출 테스트 추가
- [ ] `AccountDetail.test.tsx`에 `useExportAccount` vi.mock 추가
  - `mutateAsync: vi.fn()`, `isPending: false` 반환하는 mock
- [ ] `npx vitest run --reporter=verbose` 전체 통과 (기존 테스트 회귀 없음)

## Related Specs

- specs/features/data-rights.md — GDPR 삭제 동작 정의
- specs/services/admin-service/rbac.md — 역할별 권한

## Related Contracts

- specs/contracts/http/admin-api.md — `POST /api/admin/accounts/{accountId}/gdpr-delete` 응답 스키마
  - Response fields: `accountId`, `status`, `maskedAt`, `auditId`

## Edge Cases

- reason 공백 문자열 유효성 검증은 `ReasonSchema`가 처리하며, 테스트는 실제 폼 제출 흐름을 따름
- `useExportAccount` mock은 테스트에서 export 버튼을 클릭하지 않으므로 최소 stub으로 충분

## Failure Scenarios

- mock 반환값 수정 후 기존 성공/실패 케이스 테스트가 회귀할 경우: mock 객체 구조 재확인 및 `GdprDeleteResponseSchema` 와 비교
