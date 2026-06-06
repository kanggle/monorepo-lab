# Task ID

TASK-BE-249

# Title

Admin-service: add `tenant_id` / `target_tenant_id` to admin schema + tenant-scoped audit query

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- adr

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

`admin-service`에 multi-tenant 격리 컬럼과 query 분리 로직을 추가한다. SUPER_ADMIN의 cross-tenant 운영을 허용하되 그 행위는 audit log에 명시적으로 기록되어야 하고, 테넌트별 운영자는 본인 테넌트 외부 데이터에 접근할 수 없어야 한다.

완료 시점:

1. `admin_operators`에 `tenant_id NOT NULL` 컬럼 — 운영자가 어느 테넌트에 속하는지 명시. SUPER_ADMIN은 platform-scope row(`tenant_id = '*'`)로 표현.
2. `admin_actions`에 `tenant_id` (운영자 자신의 테넌트) 와 `target_tenant_id` (대상 테넌트, cross-tenant 동작에서 다를 수 있음) 컬럼.
3. `admin_operator_roles`에 `tenant_id NOT NULL` — 역할 부여 자체가 테넌트 scope.
4. 모든 audit query (`AdminActionJpaRepository`, audit search use-case)는 운영자의 tenant scope를 강제로 반영. SUPER_ADMIN만 `findCrossTenant` 등의 조회 권한.
5. `PermissionEvaluator`가 tenant scope를 인식하여 cross-tenant 액션을 차단(SUPER_ADMIN 외).

---

# Scope

## In Scope

- Flyway migration 추가:
  - `admin_operators.tenant_id VARCHAR(32) NOT NULL`
  - `admin_operator_roles.tenant_id VARCHAR(32) NOT NULL`
  - `admin_actions.tenant_id VARCHAR(32) NOT NULL` + `target_tenant_id VARCHAR(32) NULL`
  - 인덱스 `(tenant_id, created_at)` on `admin_actions`
- Backfill: 기존 row는 `'fan-platform'` 또는 SUPER_ADMIN seed에 한해 `'*'`로 채움 (ADR로 기록)
- 도메인:
  - `AdminOperator`, `AdminOperatorRole`, `AdminAction` 엔티티에 `tenantId` 필드 추가
  - `PermissionEvaluator`에 `evaluate(operator, action, targetTenantId)` 시그니처 (target tenant 검증 포함)
  - `SUPER_ADMIN`은 `tenantId == '*'`로 식별; 일반 operator는 본인 `tenantId == targetTenantId`만 통과
- Audit query:
  - `AdminActionJpaRepository.findByTenantId(tenantId, pageable)`
  - `AdminActionJpaRepository.findCrossTenant(targetTenantId, pageable)` — SUPER_ADMIN 전용
  - 기존 `findAll`/`findByOperatorId` 사용처 모두 tenant scope를 받도록 시그니처 변경
- API 변경:
  - `GET /api/admin/audit?tenantId=...` — 운영자 본인 tenant만 허용 (SUPER_ADMIN은 `tenantId=*`로 cross-tenant)
  - `POST /api/admin/operators` — 새 운영자 생성 시 `tenant_id` 필수
- 단위 + 통합 테스트:
  - cross-tenant 차단 회귀 테스트
  - SUPER_ADMIN cross-tenant 통과 테스트

## Out of Scope

- 테넌트 자체의 CRUD (POST `/api/admin/tenants`) — TASK-BE-250에서 처리
- 운영자 UI(admin-web) 변경 — 별도 frontend task
- `target_tenant_id`가 와일드카드인 경우(platform-wide audit row) — 본 태스크는 단일 target만, 와일드카드는 후속

---

# Acceptance Criteria

- [ ] `apps/admin-service/src/main/resources/db/migration/`에 신규 V0019+ migration: 3개 테이블에 `tenant_id` (+`target_tenant_id`) 추가
- [ ] `AdminOperator` 도메인이 `tenantId` 필드를 가짐, `'*'`은 SUPER_ADMIN
- [ ] `PermissionEvaluator.evaluate(operator, action, targetTenantId)`: `operator.tenantId != '*' && operator.tenantId != targetTenantId` 시 거부
- [ ] cross-tenant audit query: 일반 operator는 `findByTenantId(self)`만 호출 가능, SUPER_ADMIN만 `findCrossTenant` 사용
- [ ] `GET /api/admin/audit?tenantId=foo`: 본인 테넌트면 200, 다른 테넌트인데 SUPER_ADMIN 아니면 403
- [ ] cross-tenant 행위는 `admin_actions`에 `tenant_id=operator's, target_tenant_id=target` 으로 기록
- [ ] `./gradlew :projects:global-account-platform:apps:admin-service:check` PASS
- [ ] `./gradlew :projects:global-account-platform:apps:admin-service:integrationTest` PASS

---

# Related Specs

> Step 0: read `PROJECT.md`, `rules/common.md`, `rules/domains/saas.md`, `rules/traits/{regulated,audit-heavy,multi-tenant}.md`.

- `specs/features/multi-tenancy.md` § "Isolation Strategy / 적용 범위 (서비스별)" — admin 항목
- `specs/features/multi-tenancy.md` § "Per-Tenant Roles" + "Cross-Tenant Operator"
- `specs/features/admin-operations.md`
- `specs/features/audit-trail.md`
- `specs/services/admin-service/architecture.md`

# Related Skills

- `.claude/skills/backend/audit-trail-policy.md`
- `.claude/skills/backend/rbac-policy.md` (있으면)

---

# Related Contracts

- `specs/contracts/http/admin-audit.yaml` — `tenantId` query param 추가 + 403 응답 명시
- `specs/contracts/http/admin-operators.yaml` — `tenant_id` 필드 필수화
- `admin_actions` 이벤트 contract (있을 시) — `target_tenant_id` 추가

---

# Target Service

- `admin-service`

---

# Architecture

`specs/services/admin-service/architecture.md` 준수. 변경 포인트:

- `domain/operator/`: AdminOperator에 tenantId 추가; SUPER_ADMIN sentinel `'*'` 정의
- `domain/rbac/`: PermissionEvaluator가 tenant scope를 평가
- `application/audit/`: query use-case가 operator의 tenantId를 받아 자동 필터링
- `infrastructure/persistence/`: Repository finder가 tenantId predicate 강제

---

# Implementation Notes

- **`tenant_id = '*'` sentinel**: DB level에서는 단순 string. 도메인 layer에서 `TenantId.PLATFORM = TenantId.of("*")` constant로 표현하고 `isPlatformScope()` predicate 제공.
- **Backfill 정책**: 기존 SUPER_ADMIN seed는 `'*'`, 일반 operator는 `'fan-platform'`로 채운다. ADR로 기록.
- **`PermissionEvaluator` 변경의 호출부 영향**: 기존 2-arg 시그니처를 deprecate하고 3-arg로 강제 (호출부 컴파일 실패로 누락 방지).
- **Cross-tenant 행위 audit 기록**: `tenant_id`(operator의)와 `target_tenant_id`(대상)를 모두 기록 — 이 행이 cross-tenant임을 후속 query에서 식별 가능.

---

# Edge Cases

- SUPER_ADMIN이 본인 platform-scope에서 동작할 때(`target_tenant_id == '*'`) — 일반적으로 platform-level config 변경 같은 동작. 본 태스크에서 정의된 audit row가 그대로 처리.
- 일반 operator가 `target_tenant_id == operator.tenantId`로 동작 — 정상 케이스, allow.
- `target_tenant_id` 누락한 legacy 호출 — 호환성을 위해 `target_tenant_id = operator.tenantId`로 default (single-tenant 가정 유지).
- 동일 이메일을 가진 operator가 여러 테넌트에 — `(tenant_id, email)` 복합 unique index 사용, 다른 테넌트의 동일 이메일은 별개 운영자.

---

# Failure Scenarios

- **PermissionEvaluator 호출부 누락**: 시그니처 변경으로 컴파일 실패 → 안전.
- **migration 도중 audit gap**: backfill 중 새로 들어오는 admin_actions가 NOT NULL 위반 — 2-step migration 사용 (일단 NULL 허용 → backfill → ALTER COLUMN NOT NULL).
- **SUPER_ADMIN 권한 남용**: cross-tenant 동작 자체는 항상 audit row를 남기므로 사후 감사 가능. alert(`audit:cross_tenant_action.count`) 메트릭 추가 권장 (별도 task).

---

# Test Requirements

- 단위 테스트:
  - `PermissionEvaluator.evaluate`: operator tenantId vs targetTenantId 매트릭스 (allow/deny)
  - `AdminOperator.isPlatformScope()`: `'*'` 인 경우만 true
- 통합 테스트 (`@Tag("integration")`):
  - cross-tenant audit query 차단: tenantA operator가 `?tenantId=tenantB` 요청 → 403
  - SUPER_ADMIN cross-tenant 허용: `?tenantId=*` 요청 → 200, target_tenant_id 와일드카드 row 반환
  - cross-tenant 행위 audit 기록: SUPER_ADMIN이 tenantA의 account를 lock → `tenant_id='*', target_tenant_id='tenantA'` row 생성
  - migration apply 후 schema 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Unit + integration tests added
- [ ] Tests passing (CI green)
- [ ] HTTP contract updated
- [ ] specs/services/admin-service/architecture.md 갱신
- [ ] specs/features/admin-operations.md 갱신 (tenant scope 규칙 명시)
- [ ] ADR for `tenant_id = '*'` sentinel + backfill 정책
- [ ] Ready for review
