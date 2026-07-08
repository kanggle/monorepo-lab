# Task ID

TASK-PC-FE-227

# Title

권한/역할 화면 (독립)

# Status

backlog

# Owner

frontend

# Task Tags

- code
- api

---

# ⚠️ 선행 태스크 — TASK-BE-486

이 태스크는 **`projects/iam-platform/tasks/ready/TASK-BE-486-admin-role-permission-read-api.md`(admin-service role/permission 조회 API) 선행 필수**다. BE-486이 아직 구현·머지되지 않은 상태에서는 `backlog → ready` 이동 금지(move rule: related contracts identified — BE-486이 계약을 확정해야 이 태스크의 Related Contracts가 실제로 식별된 것으로 간주). BE-486 머지 후 계약(`admin-api.md` 신규 절)을 재확인하고 이 태스크를 `ready/`로 이동한다.

---

# Goal

IAM nav의 「권한」 메뉴(TASK-PC-FE-225에서 신설된 스텁 `/permissions`)에 **독립 역할·권한 조회 화면**을 구현한다. 현재 role/permission 정보는 `operators` 화면에 인라인 종속(예: `KNOWN_OPERATOR_ROLES` 정적 목록, `iam-guide` 정적 콘텐츠)되어 있는데, 이를 **독립 화면**으로 분리하고 TASK-BE-486의 실 API로 대체한다.

완료 후 참이 되어야 하는 것: 「권한」 메뉴에서 role 목록과 각 role이 보유한 permission 키를 조회할 수 있다. **v1은 read-only** — 권한 편집 기능 없음.

---

# Scope

## In Scope

- `src/features/permissions/`(신규 feature) — TASK-BE-486의 `GET /api/admin/roles` + `GET /api/admin/permissions`를 소비하는 api/components/hooks.
- 라우트: `src/app/(console)/permissions/page.tsx`(TASK-PC-FE-225 스텁 대체).
- 화면 구성: role 목록 표 + 각 role의 permission 키 drill-down(펼침/상세).
- 기존 `KNOWN_OPERATOR_ROLES`/`iam-guide` 정적 콘텐츠 중 role/permission 카탈로그에 해당하는 부분을 이 화면으로 흡수(정적 목록 → 실 API 소비로 대체).

## Out of Scope

- 권한 편집(role 생성/수정/삭제, permission 부여/회수) — v1 범위 밖(seed-only, TASK-BE-486과 동일 제약).
- 운영자↔role 배정 UI — 기존 `operators` 화면의 `AssignOperatorForm`/`OperatorConfirmRoleEditor` 그대로 유지, 이 태스크는 조회 전용 별도 화면.
- 「권한 세트」 화면(TASK-PC-FE-228) — 별개 태스크.

---

# Acceptance Criteria

- [ ] role 목록 + permission 표가 TASK-BE-486 API로 렌더.
- [ ] role→permission drill-down이 동작(role 클릭 시 해당 role의 permission 키 집합 표시).
- [ ] `pnpm lint` + `tsc --noEmit` + `vitest` GREEN.
- [ ] TASK-BE-486 의존 — 해당 태스크의 API/계약이 확정된 이후에만 착수.

---

# Related Specs

- `projects/iam-platform/specs/services/admin-service/rbac.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` (TASK-BE-486이 추가할 신규 read 엔드포인트 절)

---

# Target App

- `apps/console-web`

---

# Implementation Notes

- TASK-BE-486 응답의 scope 표기(전역 vs tenant-scoped)를 그대로 반영 — role catalog가 전역임을 화면 문구/필터에 오인 없이 표시.
- 기존 `operators` feature의 상태 매퍼 패턴(noTenant/forbidden/degraded/seeded)을 재사용.
- `KNOWN_OPERATOR_ROLES` 정적 목록을 제거하며 실 API로 대체할 때, `iam-guide` 화면이 참조하던 콘텐츠가 깨지지 않는지 확인(가이드 화면과의 의존 관계 사전 확인).

---

# Edge Cases

- 권한 편집은 v1 범위 밖임을 화면에 명시(편집 버튼 부재로 자연히 표현, 별도 안내 문구 불필요할 수 있음 — 구현 시 판단).
- role에 permission이 0개인 경우(빈 role) 표시.
- role catalog 전역 scope를 tenant-scoped로 오인하지 않도록 표기.

---

# Failure Scenarios

- TASK-BE-486 API degraded(5xx/timeout) 시 fallback 표시(가짜 성공 처리 금지).
- BE-486이 아직 머지되지 않은 상태에서 이 태스크가 실수로 착수되는 경우 — move rule로 차단(선행 태스크 미완료 시 backlog에 유지).

---

# Test Requirements

- component test: role 목록/permission drill-down.
- api test: TASK-BE-486 응답 소비 로직.
- 회귀: `iam-guide` 화면이 참조하던 정적 콘텐츠 대체 후 정상 동작.

---

# Definition of Done

- [ ] TASK-BE-486 머지 확인 후 `backlog → ready` 이동
- [ ] UI 구현(role/permission 조회)
- [ ] API 연동 완료
- [ ] 테스트 추가 및 통과
- [ ] Ready for review
