# Task ID

TASK-BE-486

# Title

admin-service role/permission 조회 API + 계약 (권한·권한세트 화면용)

# Status

ready

# Owner

backend

# Task Tags

- code
- api

---

# Goal

콘솔 「권한」·「권한 세트」 화면(TASK-PC-FE-227, TASK-PC-FE-228)이 소비할 **읽기 전용** role/permission 조회 API를 admin-service에 신설한다. 현재 `admin_roles`/`admin_role_permissions`는 마이그레이션 시점 seed로만 존재하며(`rbac.md` § Seed Matrix), 이를 노출하는 조회 API가 없다.

완료 후 참이 되어야 하는 것: 운영자 토큰으로 role 목록(각 role이 보유한 permission 키 집합 포함)과 permission catalog(전체 permission 키 목록)를 조회할 수 있다. **role/permission의 정의 자체(생성·수정·삭제)는 이 태스크 범위 밖** — 여전히 seed + Flyway 마이그레이션으로만 변경된다.

---

# Scope

## In Scope

- `GET /api/admin/roles` — `admin_roles` 전체 + 각 role의 permission 키 집합(role→permission 매핑) 응답. `GET /api/admin/operators/grantable-roles`(기존, 안정 정렬 = seed/`admin_roles.id` 오름차순)와 정렬 기준 일치.
- `GET /api/admin/permissions` — 전체 permission 키 catalog 응답(`rbac.md` § Permission Keys 기준).
- (선택, 리소스 확보 시) `GET /api/admin/permission-sets` — `admin_roles`를 `permission_set_id` 관점 뷰로 재노출(권한 세트 화면이 role과 동일 테이블을 다른 프레이밍으로 조회하는 경우 대비). 필수 아님 — `GET /api/admin/roles` 응답만으로 TASK-PC-FE-228이 충분히 커버 가능하면 생략.
- 컨트롤러: 신규 `RoleAdminController`(또는 기존 `OperatorAdminController`, `projects/iam-platform/apps/admin-service/src/main/java/com/example/admin/presentation/OperatorAdminController.java` 확장 — 구현 시 택1, 신규 컨트롤러 권장 — 「권한」/「운영자」는 별개 리소스).
- 신규 permission 키(예: `role.read`/`permission.read`) 도입 여부 판단 및 seed 매트릭스 반영 — 또는 기존 `operator.read`류 권한을 재사용할지 결정(이 판단은 이 태스크의 Acceptance Criteria에 포함).
- `specs/contracts/http/admin-api.md`에 신규 엔드포인트 계약 절 **선행 추가**(Change Rule — 계약이 코드보다 먼저).

## Out of Scope

- role/permission 정의(생성·수정·삭제) API — v1은 read-only, 정의 변경은 여전히 seed + Flyway.
- 운영자↔role 배정(`admin_operator_roles`) 변경 API — 기존 `PATCH /api/admin/operators/{operatorId}/roles` 그대로.
- 「운영자 그룹」 관련 일체(ADR-MONO-046 별도 실행 로드맵, 이 태스크와 무관).
- 캐싱 전략 변경(`rbac.md` § Caching & Invalidation 10초 TTL 정책 무변경 — 신규 read API는 role 정의 자체가 자주 바뀌지 않으므로 별도 캐시 불필요 판단이 기본값, 필요 시 구현 시 판단).

---

# Acceptance Criteria

- [ ] `GET /api/admin/roles` 응답에 각 role의 permission 키 매핑이 포함(role→permission 집합).
- [ ] `GET /api/admin/permissions` 응답이 전체 permission 키 catalog를 반환.
- [ ] 비-admin 토큰(운영자 JWT 없음/무효)은 401, 필요 권한 미보유 운영자는 403(deny-default, `rbac.md` § D2 원칙 준수).
- [ ] 신규 엔드포인트에 필요한 permission 키가 seed 매트릭스에 명시되고 최소 하나 이상의 seed role에 부여됨(권한 화면 자체를 볼 권한이 아무 role에도 없으면 화면이 무용지물).
- [ ] Testcontainers 통합 테스트 + Security 테스트(403/401 케이스) 추가.
- [ ] `:test` 전체 GREEN (신규 컨트롤러/권한 키 도입은 시그니처 변경에 준하므로 슬라이스 테스트만으로 검증하지 말 것 — `[[project_gap_account01_mutation_tenant_confinement]]` 교훈: 시그니처 변경 시 전체 `:test` 필요).

---

# Related Specs

- `projects/iam-platform/specs/services/admin-service/rbac.md` (§ Permission Keys, § Seed Roles / Seed Matrix, § Caching & Invalidation — 신규 read API가 노출할 데이터의 SoT)
- `projects/iam-platform/specs/services/admin-service/data-model.md` (§ `admin_roles`, § `admin_role_permissions` 테이블 정의 — 응답 스키마 근거)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` (신규 read 엔드포인트 계약 — **구현 선행**, Change Rule)

---

# Target Service

- `admin-service`

---

# Architecture

Follow:

- `projects/iam-platform/specs/services/admin-service/architecture.md`

---

# Implementation Notes

- role catalog가 tenant별로 다른지 확인 필요 — 현재 `admin_roles`/`admin_role_permissions`는 전역(global) seed이며 tenant 컬럼이 없다(`data-model.md` § `admin_roles`). 응답에 **scope가 전역임을 명시**(예: `scope: "global"` 필드 또는 문서화)하여 프런트가 tenant-scoped로 오인하지 않도록 한다.
- 정렬 기준은 기존 `GET /api/admin/operators/grantable-roles`(seed/`admin_roles.id` 오름차순 안정 순서, `admin-api.md:993`)와 일치시켜 프런트 예측 가능성을 유지한다.
- 캐시(`rbac.md` § Caching & Invalidation, 10초 TTL) 대상은 **operator→permission 평가 경로**이며, 이 신규 read API(role 정의 자체 조회)는 별개 경로다 — 기존 캐시 무효화 로직을 건드리지 않는다.

---

# Edge Cases

- role catalog가 tenant별로 다른가에 대한 명확화 — 현재는 전역이므로 응답에 scope 표기로 해결(신규 tenant-scoped role 모델 도입 아님).
- permission 키가 seed 이후 추가된 이력이 있는 경우(예: ADR-MONO-026/029 관련 조건부 권한) catalog 응답에 신규 키 누락 없는지 확인.
- 신규 컨트롤러 도입 시 `OperatorAdminController`와의 리소스 경계(운영자 vs role/permission) 혼동 방지.

---

# Failure Scenarios

- DB 조회 실패 시 5xx 응답 — read-only 엔드포인트이므로 감사(`admin_actions`) 기록 불필요(rbac.md § D3은 authorization 판정에 대한 감사이며 단순 조회 실패는 별개).
- deny-default 미준수(권한 어노테이션 누락) — `rbac.md` § D2에 따라 반드시 명시적 권한 선언, 누락 시 이 태스크의 Security 테스트로 적발.
- catalog 응답이 tenant-scope 오인을 유발할 경우 프런트(TASK-PC-FE-227/228)가 잘못된 필터링을 구현할 위험 — scope 명시로 예방.

---

# Test Requirements

- unit test: role→permission 매핑 조립 로직.
- integration test: Testcontainers 기반 `GET /api/admin/roles`/`GET /api/admin/permissions` 실 DB 조회 검증.
- security test: 비-admin 401, 권한 미보유 403.
- contract-related test: `admin-api.md` 신규 절과 실제 응답 스키마 정합.

---

# Definition of Done

- [ ] 구현 완료(`GET /api/admin/roles`, `GET /api/admin/permissions`)
- [ ] 테스트 추가 및 통과(unit/integration/security)
- [ ] Contracts 갱신 완료(`admin-api.md`)
- [ ] Specs 갱신 필요 시 선행 완료(`rbac.md` seed 매트릭스에 신규 권한 키 반영)
- [ ] `:test` 전체 GREEN
- [ ] Ready for review
