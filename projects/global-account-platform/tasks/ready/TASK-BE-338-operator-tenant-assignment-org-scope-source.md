# Task ID

TASK-BE-338

# Title

**per-operator `org_scope` 멤버십 출처 + assume-tenant 전파 (BE-337 `["*"]` 브리지의 v2 대체, GAP 측).** `operator_tenant_assignment` 에 nullable `org_scope` 컬럼(V0031) 추가 → `/internal/operator-assignments/check` 가 반환 → auth-service `AssumeTenantAuthenticationProvider` 가 grant 에 실어 → `TenantClaimTokenCustomizer.customizeForAssumeTenant` 가 하드코딩 `["*"]` 대신 **실제** org_scope 주입(`NULL`/empty → `["*"]` = net-zero). erp 가 subtree-root 를 소비(TASK-ERP-BE-008). ADR-MONO-020 D3 amendment(2026-06-05) 실행 — foundation.

# Status

ready

# Owner

backend-engineer (GAP admin-service + auth-service; ADR-MONO-020 D3 amendment 이미 land — 이 task 는 impl)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- api
- test

---

# Dependency Markers

- **realises**: ADR-MONO-020 D3 amendment(2026-06-05) "Membership-derived org_scope" + D5(per-assignment scoping) + erp masterdata architecture.md E6 point 3 v2.
- **supersedes (브리지)**: TASK-BE-337 (`TenantClaimTokenCustomizer.customizeForAssumeTenant` 하드코딩 `org_scope=["*"]`).
- **builds on**: TASK-BE-326 (`operator_tenant_assignment` V0030, N:M), TASK-BE-327 (assume-tenant exchange + `/internal/operator-assignments/check` edge + `OperatorAssignmentPort`).
- **consumed by (후속)**: TASK-ERP-BE-008 (erp masterdata subtree containment + read-model read 필터). **net-zero 라 순서 무관**(이 task 가 실제 org_scope 주입해도 erp 가 BE-337 처럼 `["*"]`만 받으면 동작 불변; erp 가 BE-008 으로 subtree 처리 시작하면 narrowing 발현).
- **decision (user, 2026-06-05)**: 다음 작업 = per-operator org_scope, 멤버십 출처 = operator_tenant_assignment.org_scope.

# Goal

운영자의 데이터-스코프가 `operator_tenant_assignment.org_scope`(per-assignment, 부서 subtree-root)에서 도출되어 assume-tenant 토큰의 `org_scope` claim 으로 전파된다. 미설정(`NULL`)이면 `["*"]`(테넌트 전체) — 기존 모든 운영자 동작 불변(net-zero). 운영자별 조직 격리의 **출처**를 GAP 측에 확립.

# Scope

## In Scope

- **admin V0031** `operator_tenant_assignment` 에 `org_scope JSON NULL` 컬럼 추가(부서 subtree-root id 문자열 배열; `NULL` = 미설정 = `["*"]` 의미). `@JdbcTypeCode(SqlTypes.JSON)` 매핑 + `OperatorTenantAssignmentJpaEntity` 필드 + `OperatorTenantAssignmentPort`/Impl 노출.
- **admin assignment-check edge** `OperatorAssignmentCheckUseCase` + `OperatorAssignmentCheckController`(`GET /internal/operator-assignments/check`) — 현행 boolean(assigned 여부)에 **`orgScope` 필드 additive**(assigned=true 일 때 해당 assignment 의 org_scope, `NULL`→`["*"]` 또는 null-로-두고-수신측-기본). `admin-api.md` / `internal/auth-to-admin.md` 계약 갱신(additive, 기존 필드 불변).
- **admin set-API**(운영자 assignment 의 org_scope 설정) — 최소 surface: 기존 assignment 관리 경로가 있으면 org_scope 갱신 추가; 없으면 이 task 범위는 **read+propagation 우선**, set 은 seed/SQL 또는 follow-up(데모는 V 시드로 globex 등에 subtree 부여 가능). **결정: read+propagation 필수, write-API 는 기존 assignment endpoint 존재 시에만 확장**(없으면 seed-only + follow-up 명시, green-wash 금지).
- **auth `OperatorAssignmentPort`** `isAssigned`(void/boolean)을 org_scope 반환 형태로 확장(result record: assigned + orgScope) 또는 별도 메서드. `AdminAssignmentClient` 가 check 응답의 orgScope 파싱.
- **auth `AssumeTenantAuthenticationProvider`** resolved grant(`AssumeTenantAuthenticationToken`)에 orgScope 필드 추가(operatorAccountType 와 동형). `OperatorAssignmentPort` 결과의 org_scope 를 실어 전달.
- **auth `TenantClaimTokenCustomizer.customizeForAssumeTenant`** 하드코딩 `List.of("*")` → grant 의 org_scope(`null`/empty → `List.of("*")` net-zero) 주입.
- **tests**: admin(V0031 마이그레이션 + check edge org_scope 반환 + JSON round-trip) + auth(`AssumeTenantAuthenticationProviderTest` org_scope grant 전달 + `TenantClaimTokenCustomizer` assume-tenant org_scope 주입[설정값/NULL→'*'] + `AdminAssignmentClient` 파싱 + `AssumeTenantExchangeIntegrationTest` org_scope claim 단언). 기존 테스트(org_scope='*' 가정)는 NULL→'*' net-zero 로 GREEN 유지.

## Out of Scope

- erp masterdata subtree containment + read-model read 필터 — TASK-ERP-BE-008.
- 콘솔 운영자 org_scope 설정 UI — follow-up(PC-FE; 이 task 는 GAP API/seed 까지).
- `'*'` sentinel 재설계 / operator home-tenant assignment 백필(ADR-020 D6 step4 별건).
- 다른 도메인의 org_scope 소비(erp 전용, repo-verified).

# Acceptance Criteria

- [ ] **AC-1** V0031 `operator_tenant_assignment.org_scope JSON NULL` 추가; 기존 행/신규 행 `NULL` 허용; JSON 배열 round-trip(저장/조회) 검증. **net-zero**: 기존 dual-read/gate 동작 byte-불변(NULL 행).
- [ ] **AC-2** `/internal/operator-assignments/check` 응답에 `orgScope` additive(assigned=true 시 assignment org_scope; 기존 필드/상태코드 불변). 계약(admin-api/auth-to-admin) 갱신.
- [ ] **AC-3** auth `AssumeTenantAuthenticationProvider` 가 check 결과의 org_scope 를 resolved grant 에 실음; `TenantClaimTokenCustomizer` 가 assume-tenant 토큰에 **실제 org_scope** 주입(설정 시 그 값, `NULL`/empty → `["*"]`).
- [ ] **AC-4** net-zero 회귀: org_scope 미설정(NULL) 운영자의 assume-tenant 토큰 `org_scope=["*"]`(= BE-337 동작). 기존 `AssumeTenantExchangeIntegrationTest` / customizer 테스트 GREEN.
- [ ] **AC-5** 설정된 org_scope(예: `["dept-sales"]`) 운영자 토큰이 그 값을 claim 으로 운반(IT 단언). erp 미배포여도 GAP 측 토큰 형상만으로 검증.
- [ ] **AC-6** `./gradlew :apps:admin-service:check :apps:auth-service:check` GREEN. IT 는 CI Linux(Testcontainers).

# Related Specs

- ADR-MONO-020 D3 amendment(2026-06-05) + D5. admin-service `data-model.md`(org_scope 컬럼 note, 이 spec PR) + `rbac.md`(claim 형상) + auth-service `architecture.md`(propagation). 계약 `admin-api.md` + `internal/auth-to-admin.md`(check edge org_scope additive).

# Related Contracts

- `internal/auth-to-admin.md` `GET /internal/operator-assignments/check` — `orgScope` 응답 필드 additive.
- assume-tenant 토큰 `org_scope` claim(jwt-standard-claims 계열) — erp 소비.

# Edge Cases

- assigned=false → org_scope 무의미(gate 가 invalid_grant 로 먼저 차단). 응답 orgScope null/omitted.
- org_scope `[]`(빈 배열, 명시적 zero-scope) vs `NULL`(미설정=전체): 빈 배열은 "어떤 부서도 아님"(fail-closed, 모든 targeted write 거부) — **NULL≠[]** 의미 구분 명확화. (데모는 NULL=전체 기본.)
- 동일 운영자 다수 assignment(테넌트별): check 는 selected tenant 의 assignment org_scope 만(per-(operator,tenant)).
- JSON 비배열/오염 값: fail-soft 또는 fail-closed? **fail-closed**(토큰 발급은 fail-closed 원칙; 파싱 불가 org_scope → invalid_grant 또는 '*' 거부) — 단 net-zero 위해 NULL 만 '*' 로, 오염은 거부.

# Failure Scenarios

- check edge org_scope 누락(구버전 admin) → auth 수신측 기본 `["*"]`(net-zero, graceful).
- V0031 적용 전 토큰 발급: 컬럼 부재 → NULL 취급 → '*'. 무중단.
- 기존 테스트가 org_scope='*' 하드코딩 가정 → NULL→'*' 로 GREEN(회귀 없음 확인).

# Test Requirements

- admin: V0031 마이그레이션(Flyway) + `OperatorTenantAssignmentJpaRepositoryTest` org_scope round-trip + `OperatorAssignmentCheckUseCaseTest`/`...ControllerTest`/IT org_scope 반환.
- auth: `AssumeTenantAuthenticationProviderTest`(grant org_scope 전달) + `TenantClaimTokenCustomizer` assume-tenant 분기(설정값/NULL→'*') + `AdminAssignmentClientUnitTest`(orgScope 파싱) + `AssumeTenantExchangeIntegrationTest`(claim 단언, net-zero + 설정값).
- `./gradlew :apps:admin-service:check :apps:auth-service:check` GREEN. IT CI Linux.

# Definition of Done

- [ ] admin V0031 + entity/port + check edge org_scope additive + 계약 갱신.
- [ ] auth port/provider/customizer org_scope 전파(NULL→'*' net-zero).
- [ ] admin+auth `:check` GREEN; IT CI Linux GREEN.
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Opus (멀티-서비스 authz 전파 — admin 데이터모델 + auth SAS customizer/provider hot-path; net-zero 회귀 규율). 사용자 "per-operator org_scope, 멤버십 출처=operator_tenant_assignment.org_scope" 선택. 메타: BE-337 `["*"]` 브리지의 v2 대체 foundation — 출처(admin)+전파(auth) 가 erp 소비(BE-008) 전제. net-zero(NULL→'*') 라 BE-008 과 순서 무관. [[project_gap_idp_promotion]] [[project_platform_console_adr_013]]
