# Task ID

TASK-BE-326

# Title

ADR-MONO-020 § 3.3 step 1 (D1+D5+D6) — GAP admin-service `operator_tenant_assignment` N:M 테이블 + per-assignment permission-set ref + **dual-read** effective tenant scope(assignment rows ∪ legacy single-value `admin_operators.tenant_id`). backward-compatible, **net-zero**(assignment 미시드 → legacy 단일값과 byte-identical).

# Status

ready

# Owner

backend

# Task Tags

- code
- security
- multi-tenant

---

# Dependency Markers

- **depends on**: ADR-MONO-020 ACCEPTED(MONO-157 #979 `de68ab03`) — § 2 D1(operator_tenant_assignment N:M) + D5(per-assignment permission-set) + D6 step 1(table + dual-read, backward-compatible). § 3.3 execution roadmap UNPAUSED.
- **enables (후속)**: D2 RFC8693 assume-tenant exchange(auth-service, **최고위험 hot-path** — 별 task, 새 세션 권장) → D4 console switcher → D6 step 4 cleanup.
- **orthogonal to**: ADR-005/TASK-BE-317.
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** (operator 스코프 권한 모델, 6 gating site dual-read, net-zero 리스크).

---

# Goal

ADR-020 D1/D5/D6-step1 의 backward-compatible 첫 실행: admin-service 에 N:M `operator_tenant_assignment` 테이블(operator × tenant × permission-set)을 만들고, operator 의 **effective tenant scope** 를 "assignment rows ∪ legacy 단일 `admin_operators.tenant_id`"(`'*'` 는 그대로 platform sentinel) 로 **dual-read** 한다. assignment 를 **아무것도 시드하지 않으므로** 모든 operator 의 effective scope = legacy 단일값 → 카탈로그/권한 게이트 동작 **byte-identical**(net-zero). 멀티-assignment 는 step 3(seed)에서 비로소 효력.

조사 확정(blueprint):
- **chokepoint 없음** — scope 가 6 site 에서 ad-hoc 체크됨. 단일 resolver `Set<String> resolveEffectiveTenantScope(operatorId)` 도입(assignments ∪ {legacy tenantId}; `'*'`→`{'*'}`)이 정공법.
- **테이블 = `admin_operator_roles` mirror**: 복합 PK `(operator_id BIGINT, tenant_id VARCHAR(32))`, `operator_id` FK→`admin_operators.id`(surrogate, ON DELETE CASCADE), `granted_at`, `granted_by` FK→`admin_operators.id`(NULL, ON DELETE SET NULL), `permission_set_id BIGINT NULL` FK→`admin_roles.id`(ON DELETE RESTRICT — D5, nullable=operator-level role 상속), index on `tenant_id`. 최신 migration=V0029 → **V0030**.
- `AdminOperator.isPlatformScope()` (`'*'` sentinel) **무변경** — field-level, assignment 무관.

# Scope

## In scope (admin-service)

1. **Flyway `V0030__create_operator_tenant_assignment.sql`**: 위 mirror DDL. **시드 없음**(빈 테이블 = net-zero). 헤더 주석: ADR-020 D1/D5; assignment.tenant_id = *배정* 테넌트(operator home tenant 아님 — V0026 admin_operator_roles.tenant_id invariant 와 구분 명시).
2. **domain + persistence + port**: `OperatorTenantAssignment` 도메인 + JPA entity(@IdClass 복합 PK, admin_operator_roles entity 패턴 답습) + repository + port 메서드 `List<...> findByOperatorId(Long operatorId)` (또는 tenantId set 반환).
3. **`resolveEffectiveTenantScope(String operatorId) → Set<String>`**: `OperatorLookupPort`(기존, AuditQuery/CreateOperator/AuditWriter 사용) 에 추가 또는 신규 `TenantScopeResolver` application service. 로직: operator 의 assignment tenantId 집합 ∪ {`admin_operators.tenant_id`}; `'*'` operator → `{'*'}`(platform). **assignment 없으면 → {legacy tenantId}**(net-zero).
4. **6 gating site dual-read 배선**(legacy 단일 equality → effectiveSet 멤버십; `'*'`/platform 분기 무변경):
   - **(HIGHEST)** `ConsoleRegistryUseCase.selectableTenants`: `bound.contains(ownTenant)` → `bound ∩ effectiveTenants`. ⚠️ `resolveOperator()` 가 `OperatorLookupPort` 우회하고 `AdminOperatorJpaRepository` 직접 사용 — effectiveScope resolver 를 명시 배선(divergence 리스크 #1).
   - **(HIGH)** `PermissionEvaluator.isTenantAllowed`: `tenantId.equals(target)` → `effectiveTenants.contains(target)`(domain record 에 `effectiveTenants()` 추가 or 시그니처에 Set 전달 — 최소변경 경로 선택).
   - `TenantAdminController.getTenant`(isTenantAllowed 위임 — 상속) + write paths(`isPlatformScope()` only — 무변경).
   - `AuditQueryUseCase.query`: `operatorTenantId.equals(requested)` → `effectiveTenants.contains(requested)`.
   - `UpdateOperatorProfileUseCase.update`: `callerView.tenantId().equals(target.tenantId())` → `effectiveTenants.contains(target.tenantId())`.
   - `CreateOperatorUseCase`(actor platform-scope only — 무변경). `AdminActionAuditWriter`(tenant 스탬프, gate 아님 — operator primary tenant 그대로 스탬프, 무변경).
5. **테스트**:
   - `OperatorTenantAssignment` repository IT(real DB) — findByOperatorId 반환.
   - `resolveEffectiveTenantScope` 단위 — assignment 0 → {legacy}; assignment 2 → union; `'*'` → {`'*'`}.
   - 각 dual-read site 의 기존 테스트(`AdminOperatorTest`/`PermissionEvaluatorTenantScopeTest`/`ConsoleRegistryUseCaseTest`/`AuditQueryUseCaseTest`/`AdminAuditTenantScopeIntegrationTest`) **무회귀**(net-zero — assignment 미시드) + 각 site 에 "assignment 존재 시 union 동작" 케이스 추가(seed assignment).

## Out of scope

- assignment **시드**(실 멀티-assignment operator) — step 3.
- D2 assume-tenant exchange(auth-service) / D4 console switcher / D6 step4 cleanup — 별 task.
- `AdminOperator.isPlatformScope()` 변경(`'*'` sentinel 무관).
- admin_operator_roles.tenant_id invariant(V0026) 변경.

# Acceptance Criteria

- **AC-1**: `operator_tenant_assignment` 테이블(V0030, admin_operator_roles mirror: 복합 PK, FK→admin_operators.id CASCADE, permission_set_id NULL FK→admin_roles.id, tenant_id index). clean-migrate GREEN, **시드 0**.
- **AC-2 (resolver)**: `resolveEffectiveTenantScope(operatorId)` = assignment tenantId ∪ {legacy tenant_id}; `'*'`→{`'*'`}; assignment 0 → {legacy}.
- **AC-3 (dual-read 배선)**: ConsoleRegistry/PermissionEvaluator/AuditQuery/UpdateOperatorProfile 4 site 가 effectiveTenants 멤버십으로 게이트(legacy 단일 equality 대체). platform/`'*'` 분기 무변경.
- **AC-4 (NET-ZERO)**: assignment 미시드 → 전 게이트/카탈로그 동작 byte-identical. 기존 단위/IT(AdminOperatorTest/PermissionEvaluatorTenantScopeTest/ConsoleRegistryUseCaseTest 10+/AuditQueryUseCaseTest/AdminAuditTenantScopeIntegrationTest) 단언 **무변경 GREEN**.
- **AC-5 (multi-assignment 동작)**: assignment 시드 시 effective scope = union → 해당 site 가 union 멤버 통과(신규 케이스).
- **AC-6**: admin-service 컴파일 + 전 테스트 GREEN — **CI Linux GAP Integration(Testcontainers)** 권위. 회귀 0.
- **AC-7 (scope-lock)**: 변경 = admin-service(V0030 + assignment domain/persistence/port + resolver + 4 site dual-read + 그 테스트) 만. auth-service/console/시드/도메인 게이트 0.

# Related Specs

- `docs/adr/ADR-MONO-020-...md` § 2 D1/D5 + § 3.3 step 1 + § 3.1(invariants). `projects/global-account-platform/docs/adr/ADR-002-...md`(admin-rbac scope — 확장). `rules/traits/multi-tenant.md` M1-M7.

# Related Contracts

- 내부 모델 — contract 변경 없음(console-registry envelope shape 불변, D4 미변경).

# Related Code

- admin-service: `db/migration/V0030`(신규) + `domain/rbac/{AdminOperator,PermissionEvaluator}` + `application/{console/ConsoleRegistryUseCase,AuditQueryUseCase,UpdateOperatorProfileUseCase}` + `OperatorLookupPort`(resolver) + 신규 `OperatorTenantAssignment`{domain,JpaEntity,Repository,port}. 템플릿 = `admin_operator_roles`{JpaEntity,V0004/V0007 DDL}.

# Edge Cases

- **net-zero**: assignment 0 → {legacy} → byte-identical. ConsoleRegistryUseCaseTest backward-compat seed 단언 무변경 필수(리스크 #2).
- **ConsoleRegistryUseCase 직접-JPA 경로**: `resolveOperator()` 가 port 우회 → resolver 명시 배선(리스크 #1).
- **`'*'` platform**: assignment 무관, {`'*'`} 반환, isPlatformScope() 무변경.
- **assignment.tenant_id 의미**: *배정* 테넌트(operator home 아님) — admin_operator_roles.tenant_id(V0026, operator home) 와 구분, migration 주석 명시(리스크 #3).
- **permission_set_id NULL**: step 1 은 NULL(operator-level role 상속); 실 permission-set 적용은 후속.

# Failure Scenarios

- assignment 시드(테스트 외) → net-zero 깨짐 → V0030 시드 0.
- 일부 site 미배선 → split scope(카탈로그는 union, 게이트는 legacy) → 4 site 일관.
- ConsoleRegistry 직접-JPA 미배선 → 카탈로그가 dual-read 미반영 → 명시 배선.
- isPlatformScope 변경 → `'*'` 회귀 → 건드리지 말 것.

---

# Implementation Design Notes

- 테이블 = admin_operator_roles mirror(복합 PK, surrogate FK). resolver 단일 도입 → 6 site 중 4 gate 배선. net-zero = 시드 0 + 빈 assignment→{legacy}.
- CI Linux GAP Integration 권위. 로컬 compileJava+compileTestJava + 단위.
- 구현 = Opus.

---

# Notes

- ADR-020 § 3.3 step 1(D1/D5/D6-step1) — backward-compatible store + dual-read. 후속: D2 assume-tenant exchange(auth hot-path, **최고위험, 새 세션 권장**) → D4 console switcher → step4 cleanup. dependency-correct base = 본 머지 main.
