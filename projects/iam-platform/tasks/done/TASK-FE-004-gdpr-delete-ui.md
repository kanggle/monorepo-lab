---
id: TASK-FE-004
title: "GDPR Delete UI — AccountDetail 에 GDPR 삭제 버튼 + OperatorRole 스펙 정렬"
status: ready
area: frontend
service: admin-web
---

## Goal

AccountDetail 페이지에 GDPR 삭제 버튼과 다이얼로그를 추가한다.
동시에 admin-web의 `OperatorRoleSchema`가 스펙 역할명과 불일치하는 버그를 수정한다.

## Background

- 백엔드 `POST /api/admin/accounts/{accountId}/gdpr-delete` 는 이미 구현 완료 (AdminGdprController).
- 프론트엔드 `OperatorRoleSchema`가 `ACCOUNT_ADMIN`, `AUDITOR`를 사용 중이나, 실제 DB/JWT에는 `SUPPORT_LOCK`, `SUPPORT_READONLY`, `SECURITY_ANALYST` 가 사용된다.
- AccountDetail의 RoleGuard도 `ACCOUNT_ADMIN` 을 참조하여 잘못된 guard 를 적용하고 있다.

## Scope

1. `OperatorRoleSchema` 수정: `SUPER_ADMIN`, `SUPPORT_READONLY`, `SUPPORT_LOCK`, `SECURITY_ANALYST`
2. AccountDetail `RoleGuard` allow 목록: `['SUPER_ADMIN', 'SUPPORT_LOCK']`
3. `admin-api.ts`: `GdprDeleteRequestSchema`, `GdprDeleteResponseSchema` 추가
4. `GdprDeleteDialog.tsx` 컴포넌트 생성 (LockDialog 패턴)
5. `useGdprDelete` 훅 생성
6. AccountDetail에 GDPR 삭제 버튼 추가 (SUPER_ADMIN + SUPPORT_LOCK guard)

## Acceptance Criteria

- [ ] `OperatorRoleSchema`가 스펙의 4개 역할(`SUPER_ADMIN`, `SUPPORT_READONLY`, `SUPPORT_LOCK`, `SECURITY_ANALYST`)을 정확히 열거
- [ ] AccountDetail의 기존 잠금/해제/세션 버튼 guard가 `['SUPER_ADMIN', 'SUPPORT_LOCK']`으로 변경
- [ ] AccountDetail에 GDPR 삭제 버튼이 추가되며, SUPER_ADMIN + SUPPORT_LOCK 만 표시
- [ ] GDPR 삭제 다이얼로그: 사유(필수) + 티켓 번호(선택) 입력, 확인 클릭 시 API 호출
- [ ] 성공 시 toast "계정이 삭제(마스킹)되었습니다." + 목록으로 이동
- [ ] 이미 DELETED 상태 계정에서는 버튼 비활성화
- [ ] 기존 Lock/Unlock/Revoke 동작 회귀 없음

## Related Specs

- `specs/services/admin-service/rbac.md` — Seed Matrix (역할명 canonical source)
- `specs/features/admin-operations.md` — GDPR 삭제 동작 정의

## Related Contracts

- `specs/contracts/http/admin-api.md` — `POST /api/admin/accounts/{accountId}/gdpr-delete`

## Edge Cases

- 이미 DELETED 상태: button disabled
- reason/ticketId 유효성 검사 실패: 폼 인라인 에러
- API 400 `STATE_TRANSITION_INVALID`: toast 에러
- API 503: toast "서비스 일시적 오류"

## Failure Scenarios

- Idempotency-Key 중복: 동일 요청 재시도 시 서버가 이미 처리된 결과를 반환 (정상)
- 네트워크 오류: toast 에러, 다이얼로그 유지
