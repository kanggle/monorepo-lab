---
id: TASK-FE-020
title: "GdprDeleteDialog 컴포넌트 테스트 + AccountDetail GDPR 버튼 동작 테스트"
status: ready
area: frontend
service: admin-web
---

## Goal

TASK-FE-004에서 구현된 `GdprDeleteDialog.tsx`와 AccountDetail의 GDPR 삭제 버튼에 대한 컴포넌트 테스트를 추가한다. 현재 GDPR delete UI에 대한 테스트가 전혀 없어 다른 다이얼로그(LockDialog.test.tsx, ExportButton.test.tsx)에 비해 커버리지 공백이 존재한다.

## Scope

### In

- `tests/unit/GdprDeleteDialog.test.tsx` 신규 작성
  - 사유(reason) 필드 미입력 시 폼 유효성 에러 표시
  - reason 입력 후 제출 시 `useGdprDelete` mutateAsync 호출 (accountId, reason, ticketId 확인)
  - 성공 시 toast "계정이 삭제(마스킹)되었습니다." + router.push('/accounts')
  - API 오류(ApiError) 시 toast 에러 메시지
  - isPending 중 버튼 "처리 중..." + disabled
  - 취소 버튼 클릭 시 onOpenChange(false) 호출
  - a11y: axe 위반 없음
- `tests/unit/AccountDetail.test.tsx` 기존 파일에 GDPR 관련 케이스 추가
  - GDPR 삭제 버튼이 SUPER_ADMIN / SUPPORT_LOCK 역할에게 표시됨
  - GDPR 삭제 버튼이 SUPPORT_READONLY / SECURITY_ANALYST 역할에게 미표시됨
  - status=DELETED 계정: GDPR 삭제 버튼 disabled

### Out

- `useGdprDelete.ts` 훅 로직 변경
- 백엔드 GDPR API 변경
- 데이터 내보내기 버튼 테스트 (이미 ExportButton.test.tsx에서 커버)
- E2E 테스트

## Acceptance Criteria

- [ ] `GdprDeleteDialog.test.tsx` 파일 생성 (LockDialog.test.tsx 패턴 참조)
  - [ ] reason 미입력 후 제출 → `role="alert"` 에러 메시지 표시
  - [ ] reason 입력 후 제출 → `useGdprDelete` mutateAsync 가 `{ accountId, reason, ticketId: '' }` 로 호출됨
  - [ ] ticketId 입력 포함 제출 → mutateAsync 가 ticketId 값을 전달
  - [ ] 성공: toast "계정이 삭제(마스킹)되었습니다." + router.push('/accounts') 호출
  - [ ] ApiError 응답: toast 에러 메시지 표시, 다이얼로그 유지
  - [ ] isPending=true: 제출 버튼 "처리 중..." + disabled
  - [ ] 취소 버튼 클릭: onOpenChange(false) 호출
  - [ ] axe 위반 없음 (runAxe)
- [ ] `AccountDetail.test.tsx`에 아래 케이스 추가
  - [ ] SUPER_ADMIN 역할: "GDPR 삭제" 버튼 표시
  - [ ] SUPPORT_LOCK 역할: "GDPR 삭제" 버튼 표시
  - [ ] SUPPORT_READONLY 역할: "GDPR 삭제" 버튼 미표시
  - [ ] SECURITY_ANALYST 역할: "GDPR 삭제" 버튼 미표시
  - [ ] status=DELETED 계정 + SUPER_ADMIN 역할: "GDPR 삭제" 버튼 disabled
- [ ] `npx vitest run --reporter=verbose` 전체 통과 (기존 테스트 회귀 없음)

## Related Specs

- specs/features/data-rights.md — GDPR 삭제 동작 정의
- specs/services/admin-service/rbac.md — 역할별 권한

## Related Contracts

- specs/contracts/http/admin-api.md — `POST /api/admin/accounts/{accountId}/gdpr-delete`

## Edge Cases

- ticketId 미입력 시 빈 문자열로 전달 (선택 필드)
- reason 공백 문자열만 입력: ReasonSchema 유효성 에러 표시
- 다이얼로그 open=false 시 폼 상태가 초기화됨 여부 (테스트 대상 아님, 동작 확인 불필요)

## Failure Scenarios

- `useGdprDelete` mutateAsync가 non-ApiError 예외 throw: toast "작업에 실패했습니다." 표시
- 성공 후 router.push 실패: toast는 이미 표시됨, 네비게이션 오류는 별도 처리

## Test Requirements

- 파일 위치: `apps/admin-web/tests/unit/GdprDeleteDialog.test.tsx`
- `useGdprDelete` 훅 전체를 `vi.mock`으로 대체 (LockDialog.test.tsx 참조)
- `useRouter`는 `vi.mock('next/navigation', () => ({ useRouter: () => ({ push: vi.fn() }) }))` 패턴
- `QueryClientProvider` + `ToastProvider`로 wrap
- `runAxe` 헬퍼는 `tests/a11y/axe-helper`에서 import
- `AccountDetail.test.tsx`에 추가 시 기존 mock 설정 재사용 (useExportAccount, useAccountDetail mock 유지)
- DELETED 상태 계정 테스트용 fixture: `{ ...detailFixture, status: 'DELETED' }`
