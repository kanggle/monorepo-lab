# Task ID

TASK-BE-339

# Title

**admin operator `org_scope` 관리 API — assignment 조회 + org_scope set/clear (콘솔 설정 UI 의 GAP 측).** TASK-BE-338 이 `operator_tenant_assignment.org_scope`(V0031) + 전파를 확립했으나 값 설정 경로가 **seed/SQL 뿐**(admin-api.md "set/write API 는 follow-up" 명시). 이 task 가 admin-facing 관리 endpoint 를 추가: `GET /api/admin/operators/{operatorId}/assignments`(활성 테넌트 scope, org_scope 포함 반환) + `PUT /api/admin/operators/{operatorId}/assignments/{tenantId}/org-scope`(set/clear, reason-gated). 콘솔(TASK-PC-FE-050)이 이 API 를 소비해 운영자별 데이터-스코프를 SQL 없이 설정. ADR-MONO-020 D3 amendment(2026-06-05) follow-up.

# Status

review

# Owner

backend-engineer (GAP admin-service; TASK-BE-338 데이터모델/포트 이미 land — 이 task 는 관리 surface impl)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- api
- test

---

# Dependency Markers

- **builds on**: TASK-BE-338 (`operator_tenant_assignment.org_scope` V0031 컬럼 + `OperatorTenantAssignmentPort.findOrgScope` + entity `@JdbcTypeCode(JSON)`). 이 task 는 **write/list surface** 만 추가(스키마 불변).
- **consumed by (후속)**: TASK-PC-FE-050 (콘솔 org_scope 설정 UI). **선행**: 이 API 가 main 에 있어야 콘솔이 런타임 호출 가능(net-zero 순서무관 아님 — UI 가 실 endpoint 호출).
- **realises**: admin-api.md "org_scope set/write API 는 follow-up" note 해소. ADR-MONO-020 D3 amendment(2026-06-05) per-assignment 데이터-스코프의 관리 경로.
- **decision (user, 2026-06-05)**: 다음 작업 = 콘솔 org_scope 설정 UI(이 task = 그 GAP 측 foundation).

# Goal

운영자-admin 이 활성 테넌트 내 운영자의 `org_scope`(부서 subtree-root id 배열)를 **조회·설정·해제**할 수 있는 admin API 를 제공한다. `null`(미설정) = `["*"]`(테넌트 전체, net-zero); 명시적 `[]` = zero-scope(BE-338 의미). 데이터-스코프 설정이 SQL 시드가 아닌 product surface 로 가능해진다.

# Scope

## In Scope

- **GET `/api/admin/operators/{operatorId}/assignments`** — 해당 operator 의 operator_tenant_assignment 행을 **활성 테넌트(`X-Tenant-Id`)로 scope** 하여 반환(0 또는 1 행; cross-tenant 누설 방지). 응답: `{ assignments: [{ tenantId, orgScope, permissionSetId }] }`. `orgScope` 는 `null`(미설정, NON_NULL 직렬화로 **생략**) 또는 `string[]`(subtree-root department id). 활성 테넌트에 assignment 행이 없으면 빈 배열(operator 가 그 테넌트에 배정 안 됨 — home-tenant-only 면 org_scope 부적용, 빈 배열로 신호).
- **PUT `/api/admin/operators/{operatorId}/assignments/{tenantId}/org-scope`** — body `{ orgScope: string[] | null }`. 동작:
  - `path tenantId` 는 **`X-Tenant-Id`(활성 테넌트)와 일치해야** 함(불일치 → 403 `TENANT_SCOPE_MISMATCH`; 운영자-admin 은 자기 활성 테넌트 내 assignment 만 관리).
  - 해당 `(operatorId, tenantId)` assignment 행 부재 → 404 `ASSIGNMENT_NOT_FOUND`(org_scope 는 assignment 행에만 존재; 행 생성은 본 task 범위 밖).
  - `orgScope: null` → 컬럼 `NULL` 로 clear(= net-zero 전체).
  - `orgScope: []` → 명시적 zero-scope 영속(`NULL` 과 구분, BE-338 fail-closed 의미).
  - `orgScope: [<dept-id>, ...]` → 정규화(공백 제거·blank 거부·중복 제거; 형식은 GAP 이 erp 트리 모르므로 검증 안 함 — 비-blank 문자열 배열만, 상한 cap; erp 가 소비 시점 검증) 후 영속.
  - reason-gated(`X-Operator-Reason`, 기존 status/roles mutation 패턴) + 운영자 관리 권한(기존 OperatorAdminController authz 재사용). 성공 시 갱신된 assignment 반환.
- **port write**: `OperatorTenantAssignmentPort` 에 `updateOrgScope(operatorId, tenantId, orgScope)`(또는 동등) 추가 + `OperatorTenantAssignmentPortImpl`(JPA `saveAndFlush` — BE-335 명시 flush 교훈 적용, dirty UPDATE 영속 보장) + `findAssignment(operatorId, tenantId)` 조회.
- **use-case**: `ManageOperatorOrgScopeUseCase`(또는 OperatorAdmin 계열 확장) — `listAssignments(operatorId, activeTenant)` + `setOrgScope(operatorId, tenantId, activeTenant, orgScope, reason, actor)`. 감사 기록(기존 operator mutation audit 패턴 일치).
- **contract**: `specs/contracts/http/admin-api.md` 두 endpoint 추가 + "set/write API 는 follow-up" note 교체. `architecture.md`/`data-model.md` org_scope 관리 surface note(이 spec PR).
- **tests**: use-case unit(set non-empty / clear null / explicit [] / tenant-mismatch 403 / not-assigned 404 / 정규화 dedupe·blank 거부) + controller test(reason 필수, authz) + **IT(Testcontainers MySQL)**: PUT 영속 후 GET 반영, JSON round-trip, null→clear, []→persist, 활성-테넌트 scope, NON_NULL 직렬화로 null orgScope **absent**(`.doesNotExist()` 단언 — §14 / BE-338 교훈). net-zero 회귀: 기존 assume-tenant/check IT GREEN.

## Out of Scope

- assignment 행 **생성/삭제**(operator 를 테넌트에 배정/해제) — 본 task 는 기존 행의 org_scope set/clear 만. (home-tenant-only 운영자에 org_scope 부여하려면 행 생성 필요 → 별도 follow-up.)
- 콘솔 UI — TASK-PC-FE-050.
- erp 소비(subtree 확장/read 필터) — TASK-ERP-BE-008(done).
- cross-tenant org_scope 일괄 관리 / `'*'` sentinel 재설계.

# Acceptance Criteria

- [ ] **AC-1** `GET /api/admin/operators/{operatorId}/assignments` 활성 테넌트(`X-Tenant-Id`) scope — 그 테넌트의 assignment 행(org_scope 포함; null 은 NON_NULL 로 생략) 반환; 미배정 → 빈 배열. 타 테넌트 assignment 미노출.
- [ ] **AC-2** `PUT .../assignments/{tenantId}/org-scope` `{orgScope:[...]}` → 행 org_scope 영속(`saveAndFlush`); GET 재조회 반영; JSON 배열 round-trip.
- [ ] **AC-3** `orgScope:null` → 컬럼 NULL(clear, net-zero); `orgScope:[]` → 빈 배열 영속(zero-scope, NULL 과 구분).
- [ ] **AC-4** `path tenantId ≠ X-Tenant-Id` → 403 `TENANT_SCOPE_MISMATCH`; assignment 행 부재 → 404 `ASSIGNMENT_NOT_FOUND`; reason 누락 → 기존 reason-gated 동작(400).
- [ ] **AC-5** net-zero 회귀: 기존 운영자(org_scope NULL)의 assume-tenant 토큰 + `/internal/operator-assignments/check` 동작 불변. 기존 `AssumeTenantExchangeIntegrationTest`/`OperatorAssignmentCheckIntegrationTest` GREEN.
- [ ] **AC-6** `./gradlew :apps:admin-service:check` GREEN. IT(@Tag integration) CI Linux(Testcontainers MySQL). 정규화(blank 거부·dedupe) 단위 검증.

# Related Specs

- `specs/contracts/http/admin-api.md`(두 endpoint additive + follow-up note 해소). admin-service `architecture.md`/`data-model.md`(org_scope 관리 surface note, 이 spec PR). ADR-MONO-020 D3 amendment(2026-06-05).

# Related Contracts

- `admin-api.md` — `GET /api/admin/operators/{operatorId}/assignments` + `PUT /api/admin/operators/{operatorId}/assignments/{tenantId}/org-scope` (additive; 기존 endpoint/상태코드 불변).
- assume-tenant 토큰 `org_scope` claim(BE-338) — 본 task 가 그 source 데이터를 product surface 로 편집 가능케.

# Edge Cases

- home-tenant-only 운영자(활성 테넌트 assignment 행 없음): GET 빈 배열; PUT 404(행 없음 — org_scope 는 assignment 행 전용). 콘솔이 "이 테넌트에 명시 배정 없음 → org_scope 부적용(전체)" 로 안내.
- `orgScope:[]` vs `null`: []=zero-scope(모든 targeted write/read 차단), null=전체(net-zero) — 명확 구분 영속.
- 오염 값(non-array / 비문자열 원소): 400 거부(fail-closed; BE-338 의 '*'/유효배열만 주입 원칙과 정합).
- 동일 운영자 다수 테넌트 assignment: 활성 테넌트 행만 대상(per-(operator,tenant)).
- org_scope 에 erp 에 없는 dept-id: GAP 은 검증 안 함(트리 모름) → 영속; erp 소비 시 미해소 가지로 보수적 제외(ERP-BE-008 동작).

# Failure Scenarios

- PUT 후 flush 누락 → 미영속(BE-335 재발). `saveAndFlush` + IT re-query 단언으로 게이트.
- tenant-scope 검사 누락 → cross-tenant org_scope 편집(권한 상승). path↔header 일치 단언 + IT.
- 정규화 미흡(중복/blank) → 오염 claim → erp 오판. dedupe/blank 거부 단위 게이트.

# Test Requirements

- admin: `ManageOperatorOrgScopeUseCaseTest`(set/clear/[]/mismatch/not-found/정규화) + controller test(reason/authz) + `OperatorTenantAssignmentPortImpl`/JpaRepository org_scope round-trip + **IT**(PUT→GET 영속, null absent `.doesNotExist()`, [] persist, tenant scope, reason). H2 forbidden(Testcontainers MySQL).
- net-zero 회귀: 기존 assume-tenant/check IT GREEN.
- `./gradlew :apps:admin-service:check` GREEN. IT CI Linux.

# Definition of Done

- [ ] GET assignments + PUT org-scope endpoint + use-case + port write(`saveAndFlush`) + 정규화/authz/reason.
- [ ] admin-api.md 두 endpoint + follow-up note 해소; architecture/data-model note.
- [ ] admin-service `:check` GREEN; IT CI Linux GREEN; net-zero 회귀 확인.
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Opus (authz-adjacent 데이터 write + tenant-scope 불변식 + NON_NULL/flush/[]-vs-null 직렬화 규율 + net-zero 회귀). 사용자 "콘솔 org_scope 설정 UI" 선택 → 이 task = 그 GAP foundation. 메타: BE-338 이 source 컬럼+전파, 이 task 가 그 값의 관리 surface(콘솔이 소비). 선행(콘솔이 실 endpoint 호출). [[project_gap_idp_promotion]] [[project_platform_console_adr_013]] [[feedback_spring_boot_diagnostic_patterns]]
