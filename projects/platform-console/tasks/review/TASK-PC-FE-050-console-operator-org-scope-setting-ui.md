# Task ID

TASK-PC-FE-050

# Title

**콘솔 운영자 `org_scope` 설정 UI — 활성 테넌트 내 운영자의 데이터-스코프(부서 subtree-root)를 콘솔에서 조회·설정.** TASK-BE-339 의 admin API(`GET .../assignments` + `PUT .../assignments/{tenantId}/org-scope`)를 소비. 운영자 관리 화면(`features/operators`)에 reason-gated "조직 스코프(org_scope)" 다이얼로그 추가 — 부서 picker(erp departments 재사용)로 subtree-root 선택, "전체(net-zero)" / "선택 부서" / "차단(zero-scope)" tri-state. **org_scope 사슬 완결**: 콘솔 설정 → GAP 저장(BE-338/339) → assume-tenant 전파 → erp 소비(ERP-BE-008) → 콘솔 read 카드(PC-FE-049).

# Status

review

# Owner

frontend-engineer (platform-console console-web; TASK-BE-339 가 API 제공 — 이 task 는 콘솔 UI + proxy)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

---

# Dependency Markers

- **선행 (cross-project, GAP)**: TASK-BE-339 (admin `GET .../operators/{id}/assignments` + `PUT .../assignments/{tenantId}/org-scope`). **반드시 main 머지 후** — 콘솔이 런타임 호출(net-zero 순서무관 아님).
- **builds on**: TASK-PC-FE-049 (erp-ops feature — erp departments API client 재사용 가능) + 기존 `features/operators`(operator 관리 화면 + reason-gated mutation + GAP admin proxy 패턴) + TASK-BE-336/337/338(org_scope 전파·소비 사슬 완성).
- **completes**: org_scope end-to-end 루프 — 설정(이 task) → 저장/전파(BE-338/339) → 소비(ERP-BE-008) → 가시화(PC-FE-049 read 카드).
- **decision (user, 2026-06-05)**: 다음 작업 = 콘솔 org_scope 설정 UI.

# Goal

운영자-admin 이 콘솔에서 활성 테넌트 내 운영자의 `org_scope`(부서 subtree-root id)를 조회하고 설정할 수 있다 — SQL 시드 없이 product surface 로. 휴면(net-zero) org_scope 기능이 콘솔만으로 활성화 가능해진다.

# Scope

## In Scope

- **proxy routes**(server-only, GAP admin 프록시 — 기존 `app/api/operators/_proxy.ts` 에러매핑/`getOperatorToken`/`X-Tenant-Id`/`X-Operator-Reason` 패턴 재사용):
  - `app/api/operators/[operatorId]/assignments/route.ts` — **GET** → GAP `GET /api/admin/operators/{operatorId}/assignments`.
  - `app/api/operators/[operatorId]/assignments/[tenantId]/org-scope/route.ts` — **PUT** → GAP `PUT .../assignments/{tenantId}/org-scope`(reason 헤더 필수). write 핸들러는 PUT 만(GET/POST/PATCH/DELETE 미노출).
- **operators feature 확장**(`src/features/operators/`):
  - types(zod): `OperatorAssignment{ tenantId, orgScope: string[] | null, permissionSetId }`, set-input.
  - api client: `listOperatorAssignments(operatorId)` + `setOperatorOrgScope(operatorId, tenantId, { orgScope }, reason)`(server-only, 기존 hardened call 패턴).
  - hooks + react-query keys: assignments query + set mutation; 성공 시 invalidate `['operators']` + **`['erp', 'read-model', ...]`**(org_scope 변경이 read 카드 필터 영향).
  - `OrgScopeDialog.tsx` — operator 행 액션(roles/status/profile 옆)에서 기동. 현재 org_scope 칩 표시 + tri-state 설정:
    - **전체(net-zero)** → `orgScope: null`(기본; "전 부서 — 데이터-스코프 미적용").
    - **선택 부서 subtree** → 부서 multi-select(subtree-root) → `orgScope: [<dept-id>...]`.
    - **차단(zero-scope, advanced)** → `orgScope: []`(명시적 — "어떤 부서도 아님", 경고 표기). null 과 명확 구분.
  - 부서 picker: erp departments(활성 테넌트) 재사용(erp-ops departments API 또는 thin read) — `code · name` 라벨, active(비폐기)만. erp departments 미가용(테넌트 erp 미-entitled / 503) → graceful: 수동 id 입력 fallback + 경고 배너(green-wash 금지).
  - reason 입력 + 기존 operators mutation 의 권한 가드 일치. 활성 테넌트 assignment 행 없는 operator → "이 테넌트에 명시 배정 없음 → org_scope 부적용(전체)" 안내(PUT 비활성).
- **tests**: `OrgScopeDialog.test.tsx`(tri-state: 전체/선택/차단, reason-gating, picker 선택→payload, 미가용 degrade, 미배정 안내) + api client 단위 + proxy route 단위(PUT-only·에러매핑·server-only token). console-web **vitest + `tsc --noEmit` + lint + build** GREEN(MONO-166 PR CI gate).

## Out of Scope

- GAP admin API — TASK-BE-339(소비만).
- assignment 행 생성/삭제(operator 테넌트 배정) — BE-339 범위 밖이므로 여기서도 제외(기존 행 org_scope 설정만).
- erp 소비(subtree 확장/read 필터) — TASK-ERP-BE-008(done).
- cross-tenant 일괄 org_scope 관리 / org_scope 변경 이력 타임라인.

# Acceptance Criteria

- [ ] **AC-1** operators 화면에서 운영자 행 → "조직 스코프" 다이얼로그 → 현재 org_scope(부서 칩 또는 "전체") 표시. (활성 테넌트 assignment GET.)
- [ ] **AC-2** 부서 multi-select 로 subtree-root 선택 → 저장(reason 입력) → PUT `{orgScope:[...]}`; 성공 시 다이얼로그/목록 갱신 + erp read-model query invalidate.
- [ ] **AC-3** tri-state: "전체" 저장 → `orgScope:null`(clear); "차단" 저장 → `orgScope:[]`(경고 확인). null↔[] 명확 구분.
- [ ] **AC-4** erp departments 미가용(503/미-entitled) → 수동 id 입력 fallback + 경고 배너; 활성 테넌트 미배정 operator → 안내 + PUT 비활성.
- [ ] **AC-5** proxy: assignments=GET-only, org-scope=PUT-only(다른 메서드 미노출); 토큰 server-only(`getOperatorToken`)·`X-Tenant-Id`·`X-Operator-Reason` 전달; 401→재로그인/403·404→inline 에러매핑(기존 operators proxy 일치).
- [ ] **AC-6** console-web `vitest` + `tsc --noEmit` + `lint` + `build` GREEN(MONO-166 gate). 기존 operators/erp-ops 테스트 회귀 없음.

# Related Specs

- ADR-MONO-015(dashboards 합성)/ADR-MONO-017(console-bff) additive amendment(운영자 org_scope 설정 surface). `specs/services/console-web/...`(operators feature 확장 note, 이 spec PR). console-integration-contract(operators org_scope edge note).

# Related Contracts

- consume: GAP admin-api.md `GET .../operators/{id}/assignments` + `PUT .../assignments/{tenantId}/org-scope`(BE-339). console-web 자체 proxy 계약(same-origin GET/PUT, write=PUT only).

# Edge Cases

- 활성 테넌트에 assignment 행 없는 operator(home-tenant-only): GET 빈 배열 → "명시 배정 없음, org_scope 부적용(전체)" 안내; PUT 비활성(BE-339 가 404 반환하므로 클라이언트가 사전 차단).
- "차단([])" 저장: 운영자가 그 테넌트에서 아무 부서도 못 보/쓰게 됨 — 명시 경고 + 확인.
- org_scope 변경 후 대상 운영자 기존 세션: 변경前 토큰 보유 → 테넌트 재선택 재발급 필요(BE-337 교훈) — 다이얼로그에 안내 문구.
- 부서 picker 에 retired 부서: 옵션 제외(active 만); 이미 org_scope 에 retired dept-id 있으면 칩으로 표시(보존, 트리상 유효).
- self-operator org_scope 편집: 기존 operators self-mutation 가드 정책 일치(필요 시 비활성).

# Failure Scenarios

- PUT reason 누락 → 400(기존 reason-gated). 다이얼로그가 reason 필수 입력.
- erp departments fetch 실패 → 다이얼로그 전체 실패 아님(수동 fallback). degrade 단언.
- 잘못된 메서드로 proxy 호출 → 405/미노출(GET-only·PUT-only 게이트). 단위 단언.
- write proxy 가 token 클라이언트 노출 → server-only(`getServerEnv` 가드). 단언.

# Test Requirements

- `OrgScopeDialog.test.tsx`(tri-state·reason·picker·degrade·미배정), api client 단위(list/set payload), proxy route 단위(GET-only/PUT-only·에러매핑·server-only).
- console-web `vitest` + `tsc --noEmit` + `lint` + `build` GREEN(MONO-166). erp-ops/operators 회귀 0.
- Local(선택): BE-339 + 이 task 배포 후 라이브 — 콘솔에서 org_scope 설정 → assume-tenant 재발급 토큰이 claim 운반 → erp read 카드 필터 확인(BE-339·ERP-BE-008 양쪽 배포 시).

# Definition of Done

- [ ] proxy(GET assignments + PUT org-scope) + operators feature(types/api/hooks/OrgScopeDialog) + 부서 picker + tri-state + degrade.
- [ ] console-web vitest + tsc + lint + build GREEN(MONO-166); 회귀 0.
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Opus (null/[]/전체 tri-state 직렬화 정확성 + cross-feature 부서 picker + reason-gated mutation + degrade UX + erp read-model invalidation). 사용자 "콘솔 org_scope 설정 UI" 선택. 메타: org_scope end-to-end 루프의 마지막 조각(설정 UI) — source(BE-339)에 human control 달아 read 카드(PC-FE-049)와 대칭 완성. **선행 BE-339 머지 필수**(실 endpoint 호출). [[project_platform_console_adr_013]] [[project_gap_idp_promotion]]
