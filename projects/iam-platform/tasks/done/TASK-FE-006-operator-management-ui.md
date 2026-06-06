---
id: TASK-FE-006
title: "운영자 관리 UI — /operators 페이지 (목록 + 생성 + 역할 변경 + 상태 변경)"
status: ready
area: frontend
service: admin-web
---

## Goal

SUPER_ADMIN이 admin-web `/operators` 페이지에서 운영자 목록을 조회하고, 신규 운영자를 생성하며, 기존 운영자의 역할과 상태를 변경할 수 있도록 UI를 구현한다.

## Background

- TASK-BE-083이 완료되면 아래 API가 사용 가능해진다:
  - `GET /api/admin/me` (현재 로그인 운영자 정보)
  - `GET /api/admin/operators` (목록)
  - `POST /api/admin/operators` (생성)
  - `PATCH /api/admin/operators/{operatorId}/roles` (역할 교체)
  - `PATCH /api/admin/operators/{operatorId}/status` (상태 변경)
- 현재 `/operators` 페이지는 `<OperatorInfo />` (자기 자신 정보 표시) 만 있는 stub 상태.
- `useOperatorSession` 훅이 `GET /api/admin/me`를 호출하나 백엔드 구현이 없어 동작 안 함 → TASK-BE-083 완료 후 활성화됨.

## Scope

1. **`admin-api.ts`** — 운영자 관리 Zod 스키마 + 타입 추가:
   - `OperatorSchema`, `OperatorListResponseSchema`
   - `CreateOperatorRequestSchema`, `CreateOperatorResponseSchema`
   - `PatchRolesRequestSchema`, `PatchRolesResponseSchema`
   - `PatchStatusRequestSchema`, `PatchStatusResponseSchema`

2. **훅 3개** (`features/operators/hooks/`):
   - `useOperatorList` — `GET /api/admin/operators` (React Query)
   - `useCreateOperator` — `POST /api/admin/operators` (useMutation)
   - `usePatchOperatorRoles` — `PATCH .../roles` (useMutation)
   - `usePatchOperatorStatus` — `PATCH .../status` (useMutation)

3. **컴포넌트** (`features/operators/components/`):
   - `OperatorList` — 목록 테이블 (email, displayName, roles 배지, status, lastLoginAt, 액션 버튼)
   - `CreateOperatorDialog` — 생성 다이얼로그 (email, displayName, password, roles 다중선택)
   - `EditRolesDialog` — 역할 편집 다이얼로그 (checkbox 다중선택, 전체 교체)
   - `ChangeStatusDialog` — 상태 변경 확인 다이얼로그 (사유 입력)

4. **`/operators` 페이지** (`app/(console)/operators/page.tsx`) — 전체 재작성:
   - `RoleGuard allow={['SUPER_ADMIN']}` 로 전체 감싸기
   - 미허가 역할 접근 시 "권한이 없습니다" fallback 표시
   - 상단: 현재 운영자 정보 (`OperatorInfo` 유지)
   - 하단: `OperatorList` + "운영자 추가" 버튼

## Acceptance Criteria

- [ ] SUPER_ADMIN으로 로그인 시 `/operators` 에서 운영자 목록이 테이블로 표시됨
- [ ] "운영자 추가" 버튼 클릭 시 생성 다이얼로그 열림. 성공 시 목록 갱신 + toast
- [ ] 목록의 "역할 변경" 버튼 클릭 시 `EditRolesDialog` 열림. 저장 시 해당 행 갱신
- [ ] 목록의 "정지" / "활성화" 버튼 클릭 시 `ChangeStatusDialog` 열림. 확인 시 상태 변경
- [ ] 본인 계정에 대한 "정지" 버튼은 disabled (SELF_SUSPEND_FORBIDDEN 방지)
- [ ] SUPER_ADMIN이 아닌 역할로 접근 시 "권한이 없습니다" 메시지만 표시 (목록/버튼 없음)
- [ ] API 400/409/404 에러는 toast + 다이얼로그 유지
- [ ] 단위 테스트: `OperatorList`, `CreateOperatorDialog` 각 1개 이상

## Related Specs

- `specs/features/operator-management.md`
- `specs/services/admin-service/rbac.md` — 역할 목록 (`SUPER_ADMIN`, `SUPPORT_READONLY`, `SUPPORT_LOCK`, `SECURITY_ANALYST`)

## Related Contracts

- `specs/contracts/http/admin-api.md` — GET /api/admin/operators, POST /api/admin/operators, PATCH roles/status

## Edge Cases

- 운영자 없음: "등록된 운영자가 없습니다" 빈 상태 표시
- 목록 로딩 실패: 에러 메시지 표시
- `roles` 빈 배열로 저장: 허용, "역할 없음" 표시
- password 정책 위반(≥10자, 영문+숫자+특수문자): 폼 인라인 에러

## Failure Scenarios

- `POST /api/admin/operators` 409 (`OPERATOR_EMAIL_CONFLICT`): "이미 사용 중인 이메일입니다." toast, 다이얼로그 유지
- `PATCH .../status` 400 (`SELF_SUSPEND_FORBIDDEN`): 버튼 disabled로 예방 (FE 방어), 만약 서버에서 400 반환 시 toast 에러
- 네트워크 오류: toast "작업에 실패했습니다.", 다이얼로그 유지
