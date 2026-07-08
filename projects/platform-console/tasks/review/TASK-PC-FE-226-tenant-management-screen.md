# Task ID

TASK-PC-FE-226

# Title

테넌트 관리 화면 (목록·상세·생성·수정)

# Status

review

# Owner

frontend

# Task Tags

- code
- api

---

# Goal

IAM nav의 「테넌트」 메뉴(TASK-PC-FE-225에서 신설된 스텁 `/tenants`)에 **테넌트 목록·상세·생성·수정** 화면을 구현한다. 기존 admin-service `TenantAdminController`(`GET/POST/PATCH /api/admin/tenants`)를 소비한다.

완료 후 참이 되어야 하는 것: 콘솔에서 SUPER_ADMIN 운영자가 전체 테넌트를 조회하고, 신규 테넌트를 생성하고, 기존 테넌트의 `display_name`/`status` 등을 수정할 수 있다. 이 화면은 **테넌트 관리 화면**이며, 기존 `TenantSwitcher`(세션의 활성 테넌트 전환 UI, `src/features/tenant/components/TenantSwitcher.tsx`)와는 별개다 — 혼동하지 않는다.

---

# Scope

## In Scope

- `src/features/tenants/`(신규 feature) — `api/`(tenants-api.ts 등 gateway 호출), `components/`(목록·상세·생성 폼·수정 폼), `hooks/`(list/mutation 훅), 배럴(`index.ts`). 기존 `features/accounts`·`features/operators` feature 패턴(getXxxListState + noTenant/forbidden/degraded 4-상태 게이트) 답습.
- 라우트: `src/app/(console)/tenants/page.tsx`(목록, TASK-PC-FE-225 스텁 대체) + `src/app/(console)/tenants/[tenantId]/page.tsx`(상세/수정).
- BFF 프록시: `src/app/api/tenants/**` route handler (목록/상세 GET, 생성 POST, 수정 PATCH).
- 목록: `display_name`/`type`(B2C/B2B)/`status` 컬럼 + 페이징.
- 상세: 단일 테넌트 정보 표시(`proj_console_ecommerce_detail_conventions` 컨벤션의 DetailHeader/dl 순서 패턴 재사용 — 명칭→상태→식별자→날짜).
- 생성 폼: `POST /api/admin/tenants` 호출, `tenant_id` 규칙 검증(contract § Tenant ID 규칙 참고).
- 수정 폼: `PATCH /api/admin/tenants/{tenantId}` 호출.
- 쓰기 권한 게이팅: **SUPER_ADMIN(`tenant_id='*'`)만 생성/수정 가능** — 일반 운영자는 조회만(계약 § Tenant Lifecycle에 따라 일반 운영자 mutating 호출 시 403).

## Out of Scope

- 테넌트 삭제/비활성화 이외의 상태 전이 세부 로직 변경(백엔드 status 전이 매트릭스는 기존 그대로 소비만).
- `TenantSwitcher`(활성 테넌트 전환) 변경 — 별개 컴포넌트, 무변경.
- 구독(subscription)/파트너십(partnership) 관리 화면 — 별도 스코프.
- 백엔드 `TenantAdminController`/계약 변경 — 기존 계약을 그대로 소비(부족 시 계약 갱신을 선행 태스크로 분리, 이 태스크 착수 전 계약 재확인 필요).

---

# Acceptance Criteria

- [ ] 테넌트 목록이 `display_name`/`type`/`status` 컬럼과 페이징으로 렌더.
- [ ] 테넌트 상세 화면이 단일 테넌트 정보를 DetailHeader/dl 컨벤션대로 표시.
- [ ] 생성 폼이 `POST /api/admin/tenants`를 호출하고 성공 시 목록/상세로 반영.
- [ ] 수정 폼이 `PATCH /api/admin/tenants/{tenantId}`를 호출하고 반영.
- [ ] SUPER_ADMIN이 아닌 운영자는 생성/수정 UI가 비활성화되거나 시도 시 403이 forbidden 상태로 표면화(가짜 degraded 아님).
- [ ] B2C/B2B `tenant_type` 분기가 목록/상세에 올바르게 표시.
- [ ] `pnpm lint` + `tsc --noEmit` + `vitest`(신규 tenants-api/화면/프록시 테스트 포함) 전부 GREEN.

---

# Related Specs

- `projects/iam-platform/specs/features/multi-tenancy.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` (§ Tenant Lifecycle — `POST/GET/GET-by-id/PATCH /api/admin/tenants`, § Tenant ID 규칙, § Status 전이 매트릭스. **착수 전 기존 계약이 화면 요구사항을 충분히 커버하는지 재확인 — 부족 시 계약 갱신을 선행**.)

---

# Target App

- `apps/console-web`

---

# Implementation Notes

- `TenantAdminController`의 4개 엔드포인트는 SUPER_ADMIN(`tenant_id='*'`)만 호출 가능(계약 § Tenant Lifecycle 명시) — 프런트 게이팅은 백엔드 403을 신뢰하되, UX상 일반 운영자에게 생성/수정 컨트롤 자체를 숨기거나 비활성화하는 것을 권장(기존 `AssignOperatorForm`류의 role 기반 게이팅 패턴 참고).
- `features/accounts`·`features/operators`의 getXxxListState 패턴(noTenant/forbidden/degraded/seeded 4-상태) 그대로 재사용 — 신규 상태 매퍼 발명 금지.
- 테넌트 관리 화면과 `TenantSwitcher`(세션 활성 테넌트 전환)는 **기능적으로 완전히 별개** — 같은 "테넌트"라는 단어를 쓰지만 하나는 관리 CRUD, 다른 하나는 로그인 세션의 스코프 전환. UI 배치(nav 위치)로도 혼동 방지.

---

# Edge Cases

- `TenantSwitcher`(세션 전환)와 테넌트 관리 화면 간 혼동 방지 — 별개 화면임을 nav/문구로 명확히.
- B2C/B2B `tenant_type` 분기에 따라 표시 필드가 달라지는 경우.
- 페이지네이션 경계(빈 목록, 마지막 페이지).
- 생성 폼에서 `tenant_id` 형식 위반 입력.

---

# Failure Scenarios

- account-service로의 위임 쓰기(생성/수정)가 실패할 경우 degraded 표시(가짜 성공 처리 금지).
- 중복 `tenant_id`로 생성 시도 시 백엔드 409를 사용자 메시지로 정확히 표면화(BFF가 non-2xx 본문을 삼켜 500으로 변질시키지 않도록 — `[[env_bff_proxy_null_body_status_500]]` 계열 함정 주의).
- SUPER_ADMIN 아닌 운영자의 mutating 호출 시도 → 403 forbidden 상태(가짜 degraded 아님).
- cold-start 타임아웃(재배포 직후) — 필요 시 데모 오버레이 타임아웃 예산 확인.

---

# Test Requirements

- component test: 목록/상세/생성 폼/수정 폼.
- api/proxy test: `tenants-api.ts` + BFF route handler.
- page/flow test: 목록→상세→수정 플로우, 403/409 에러 표면화.

---

# Definition of Done

- [ ] UI 구현(목록/상세/생성/수정)
- [ ] API 연동 완료(`tenants-api.ts` + BFF 프록시)
- [ ] Loading/error/empty/forbidden 상태 처리
- [ ] 테스트 추가 및 통과
- [ ] 계약 재확인 완료(부족 시 계약 갱신 선행)
- [ ] Ready for review
