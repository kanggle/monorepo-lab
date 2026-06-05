---
id: TASK-FE-008
title: "AccountDetail에 데이터 내보내기(Export) 버튼 추가"
status: ready
area: frontend
service: admin-web
---

## Goal

AccountDetail 페이지에 `GET /api/admin/accounts/{accountId}/export` 를 호출하는 "데이터 내보내기" 버튼을 추가한다. 응답 JSON을 브라우저 파일 다운로드로 저장한다.

## Background

- 백엔드 `GET /api/admin/accounts/{accountId}/export` 는 이미 구현 완료.
- 권한: `audit.read` — `SUPER_ADMIN`, `SUPPORT_READONLY`, `SECURITY_ANALYST` 에게 부여.
- 응답은 JSON 객체 (파일 스트림 아님). 프론트엔드에서 Blob 다운로드로 처리.
- `X-Operator-Reason` 헤더 필수 (GET이지만 감사 사유 요구).

## Scope

1. **`admin-api.ts`** — `DataExportResponseSchema` + `DataExportResponse` 타입 추가
2. **`useExportAccount.ts`** 훅 생성 (`features/accounts/hooks/`)
   - `useMutation` 사용 (버튼 클릭 → 요청 → 다운로드)
   - `apiClient.get`에 `operatorReason: 'account.export'` 전달
   - 응답 JSON을 `Blob`으로 변환 → `URL.createObjectURL` → `<a>` 클릭 트리거 → 다운로드
   - 파일명: `export-{accountId}-{YYYYMMDD}.json`
3. **`AccountDetail.tsx`** — Export 버튼 추가
   - `RoleGuard allow={['SUPER_ADMIN', 'SUPPORT_READONLY', 'SECURITY_ANALYST']}`
   - 버튼 `variant="outline"`, `disabled={isPending}`
   - 성공 시 toast "데이터를 내보냈습니다."
   - 실패 시 toast 에러

## Acceptance Criteria

- [ ] `SUPER_ADMIN`, `SUPPORT_READONLY`, `SECURITY_ANALYST` 역할에게 Export 버튼 표시
- [ ] `SUPPORT_LOCK` 역할에게 Export 버튼 미표시
- [ ] 버튼 클릭 시 `GET /api/admin/accounts/{accountId}/export` 호출 + `X-Operator-Reason: account.export` 헤더
- [ ] 응답 JSON이 `export-{accountId}-{YYYYMMDD}.json` 파일명으로 브라우저 다운로드
- [ ] 성공 toast: "데이터를 내보냈습니다."
- [ ] 실패 toast: ApiError 메시지
- [ ] 기존 Lock/Unlock/Revoke/GDPR 동작 회귀 없음
- [ ] 단위 테스트 1개 이상

## Related Specs

- `specs/features/admin-operations.md` — 데이터 내보내기 동작 정의

## Related Contracts

- `specs/contracts/http/admin-api.md` — `GET /api/admin/accounts/{accountId}/export`

## Edge Cases

- DELETED 계정도 Export 가능 (버튼 비활성화 없음)
- 응답 JSON 파싱 실패 시 toast 에러
- 다운로드 중 버튼 disabled

## Failure Scenarios

- 403 PERMISSION_DENIED: toast 에러
- 404 ACCOUNT_NOT_FOUND: toast 에러
- 503 DOWNSTREAM_ERROR: toast "서비스 일시적 오류"
