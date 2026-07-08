# Task ID

TASK-PC-FE-228

# Title

권한 세트 화면

# Status

backlog

# Owner

frontend

# Task Tags

- code
- api

---

# ⚠️ 선행 태스크 — TASK-BE-486

이 태스크는 **`projects/iam-platform/tasks/ready/TASK-BE-486-admin-role-permission-read-api.md`(admin-service role/permission 조회 API) 선행 필수**다. BE-486이 아직 구현·머지되지 않은 상태에서는 `backlog → ready` 이동 금지(move rule: related contracts identified — BE-486이 계약을 확정해야 이 태스크의 Related Contracts가 실제로 식별된 것으로 간주). BE-486 머지 후 계약(`admin-api.md` 신규 절, 특히 `permission-sets` 뷰 또는 `roles` 재사용 여부)을 재확인하고 이 태스크를 `ready/`로 이동한다.

---

# Goal

IAM nav의 「권한 세트」 메뉴(TASK-PC-FE-225에서 신설된 스텁 `/permission-sets`)에 **배정에 쓰이는 권한 세트(`permission_set_id`) 조회 화면**을 구현한다. `permission_set_id`는 `operator_tenant_assignment`의 per-assignment role-set narrowing 필드(ADR-MONO-020 D5)이며, 물리적으로 `admin_roles`를 재사용한다 — 신규 테이블이 아니다.

완료 후 참이 되어야 하는 것: 「권한 세트」 메뉴에서 권한 세트(=role) 목록과 각 세트가 보유한 permission, 그리고 현재 몇 개의 배정(assignment)에서 쓰이고 있는지를 조회할 수 있다. **v1은 read-only**.

---

# Scope

## In Scope

- `src/features/permission-sets/`(신규 feature) — TASK-BE-486 API(`GET /api/admin/roles`, 필요 시 `GET /api/admin/permission-sets` 뷰)를 소비.
- 라우트: `src/app/(console)/permission-sets/page.tsx`(TASK-PC-FE-225 스텁 대체).
- 화면 구성: 권한 세트(=role) 목록 + 각 세트의 permission 키 + 현재 사용 배정 수(가능한 범위에서 표시 — TASK-BE-486 응답에 사용 카운트가 없으면 이 태스크에서 별도 집계 방식을 판단하거나 카운트 표시를 후속으로 분리).
- `permission_set_id`가 `NULL`인 경우(= operator-level role 상속, 배정에 세트가 지정되지 않은 상태)를 명확히 표기.

## Out of Scope

- 권한 세트 생성/수정/삭제 — v1 범위 밖(seed-only, `admin_roles` 자체 CRUD는 TASK-BE-486 범위 밖).
- 배정(`operator_tenant_assignment`)에서 `permission_set_id`를 지정/변경하는 UI — 기존 `operators` 화면의 배정 관리 기능 그대로 유지, 이 태스크는 조회 전용 별도 화면.
- 「권한」 화면(TASK-PC-FE-227) — role/permission 자체의 독립 조회는 별개 태스크(단, 물리적으로 같은 `admin_roles` 데이터를 다른 관점으로 보여줄 수 있음 — 두 화면 간 API 재사용은 구현 시 판단).

---

# Acceptance Criteria

- [ ] 권한 세트(=role) 목록이 TASK-BE-486 API로 렌더.
- [ ] 각 세트의 permission 키가 표시.
- [ ] 가능한 범위에서 각 세트의 사용 배정 수 표시(집계 방식은 구현 시 판단, 불가능하면 이 AC를 후속 과제로 명시하고 생략 가능).
- [ ] `permission_set_id` NULL(operator-level role 상속) 케이스가 화면에 명확히 표기.
- [ ] `pnpm lint` + `tsc --noEmit` + `vitest` GREEN.
- [ ] TASK-BE-486 의존 — 해당 태스크의 API/계약이 확정된 이후에만 착수.

---

# Related Specs

- `projects/iam-platform/specs/services/admin-service/rbac.md`
- ADR-MONO-020 § D5 (`permission_set_id` per-assignment role-set narrowing 결정 근거)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` (TASK-BE-486이 추가할 신규 read 엔드포인트 절 + 기존 `GET /api/admin/operators/{operatorId}/assignments`의 `permissionSetId` 필드 정의)

---

# Target App

- `apps/console-web`

---

# Implementation Notes

- `permission_set_id`는 물리적으로 `admin_roles.id`를 가리킨다(신규 엔티티 아님) — 화면은 "권한 세트"라는 별도 개념 프레이밍만 제공하고, 데이터 소스는 TASK-BE-486의 role API와 동일할 수 있다. 신규 백엔드 테이블/엔티티를 가정하지 않는다.
- 기존 `GET /api/admin/operators/{operatorId}/assignments` 응답의 `permissionSetId` 필드(`admin-api.md:1050`, `NULL`이면 omit = operator-level role 상속)와의 의미 정합을 유지 — 이 화면의 "세트 미지정" 표기가 그 의미와 일치해야 한다.
- 사용 배정 수 집계가 TASK-BE-486 API 범위 밖이면(예: 배정 테이블 조인 집계가 필요) 별도 후속 API 필요 여부를 이 태스크 착수 시 판단 — 무리하게 프런트에서 N+1 조회로 집계하지 않는다.

---

# Edge Cases

- `permission_set_id` NULL(operator-level role 상속) 케이스 표기 — 기존 배정 화면의 동일 의미와 일치.
- 사용 배정 수 집계가 불가능하거나 비용이 큰 경우의 대체 표시(예: "—" 또는 생략).
- 권한 세트 편집은 v1 범위 밖임을 화면에 명시.

---

# Failure Scenarios

- TASK-BE-486 API degraded 시 fallback 표시.
- BE-486이 아직 머지되지 않은 상태에서 이 태스크가 실수로 착수되는 경우 — move rule로 차단(선행 태스크 미완료 시 backlog에 유지).
- 사용 배정 수 집계를 위해 무리한 N+1 API 호출을 프런트에서 수행 — 성능 저하로 이어지므로 지양(집계가 필요하면 BE-486 또는 별도 후속 API에서 해결).

---

# Test Requirements

- component test: 권한 세트 목록/permission 표시/NULL 케이스 표기.
- api test: TASK-BE-486 응답 소비 로직.

---

# Definition of Done

- [ ] TASK-BE-486 머지 확인 후 `backlog → ready` 이동
- [ ] UI 구현(권한 세트 조회)
- [ ] API 연동 완료
- [ ] 테스트 추가 및 통과
- [ ] Ready for review
