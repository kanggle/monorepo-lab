---
id: TASK-FE-013
title: "운영자 본인 비밀번호 변경 UI"
status: ready
area: frontend
service: admin-web
---

## Goal

운영자가 자신의 비밀번호를 변경할 수 있는 UI를 추가한다.
`OperatorDropdown` (우상단 메뉴)에서 접근 가능한 "비밀번호 변경" Dialog를 구현한다.

## Background

- 비밀번호 변경 엔드포인트: `PATCH /api/admin/operators/me/password` (admin-service, TASK-BE-085에서 구현 완료 가정)
- 현재 비밀번호 확인 후 새 비밀번호로 교체
- 비밀번호 정책: 8자 이상, 대문자·소문자·숫자·특수문자 중 3종 이상

## Scope

### In

1. **`admin-api.ts`** — `ChangePasswordRequestSchema` + `ChangePasswordRequest` 타입 추가
   ```
   ChangePasswordRequestSchema = z.object({
     currentPassword: z.string(),
     newPassword: z.string().min(8).max(128),
   })
   ```
2. **`features/operators/hooks/useChangePassword.ts`** (신규)
   - `useMutation`, `apiClient.patch('/api/admin/operators/me/password', ...)`
   - 성공: toast "비밀번호가 변경되었습니다."
   - 실패 400 `CURRENT_PASSWORD_MISMATCH`: toast "현재 비밀번호가 올바르지 않습니다."
3. **`features/operators/components/ChangePasswordDialog.tsx`** (신규)
   - `currentPassword`, `newPassword`, `confirmPassword` 3개 필드
   - react-hook-form + zodResolver
   - `confirmPassword !== newPassword` → 클라이언트 검증 에러
   - 비밀번호 복잡도 클라이언트 검증 (3종 이상 문자 유형)
   - 제출 중 버튼 disabled
4. **`app/(console)/OperatorDropdown.tsx`** 수정
   - "비밀번호 변경" 메뉴 아이템 추가 → `ChangePasswordDialog` 열기

### Out

- 비밀번호 재설정(forgot password) 플로우
- 이메일 발송
- 다른 세션 revoke

## Acceptance Criteria

- [ ] OperatorDropdown에 "비밀번호 변경" 항목 표시
- [ ] 클릭 시 Dialog 열림: 현재 비밀번호, 새 비밀번호, 비밀번호 확인 필드
- [ ] `confirmPassword !== newPassword` 시 제출 불가 + 에러 메시지 표시
- [ ] `PATCH /api/admin/operators/me/password` 호출 시 `X-Operator-Reason: operator.password.change` 헤더 포함
- [ ] 성공 toast: "비밀번호가 변경되었습니다." + Dialog 닫기
- [ ] 400 `CURRENT_PASSWORD_MISMATCH`: toast "현재 비밀번호가 올바르지 않습니다."
- [ ] 단위 테스트 작성

## Related Specs

- `specs/features/password-management.md`
- `specs/features/operator-management.md`

## Related Contracts

- `specs/contracts/http/admin-api.md` — `PATCH /api/admin/operators/me/password`

## Edge Cases

- 새 비밀번호 = 현재 비밀번호: 서버 측 처리 (클라이언트 검증 없음)
- 비밀번호 8자 미만: 클라이언트 검증 에러 "최소 8자 이상 입력하세요"
- 복잡도 미달: 클라이언트 검증 에러 "대문자·소문자·숫자·특수문자 중 3종 이상 포함하세요"

## Failure Scenarios

- 401 UNAUTHORIZED: 세션 만료 → 기존 apiClient 인터셉터가 처리
- 500 INTERNAL_SERVER_ERROR: toast "서버 오류가 발생했습니다."

## Test Requirements

- `tests/unit/ChangePasswordDialog.test.tsx` 신규 작성
  - 폼 렌더링 확인
  - confirmPassword 불일치 에러 표시
  - 성공 시 toast + Dialog 닫기
  - CURRENT_PASSWORD_MISMATCH 에러 메시지

## Pre-condition

`PATCH /api/admin/operators/me/password` 엔드포인트가 admin-service에 구현되어 있어야 한다.
미구현 시 TASK-BE-085를 먼저 완료할 것.
