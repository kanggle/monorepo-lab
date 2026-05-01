# Task ID

TASK-BE-250

# Title

Admin-service: tenant lifecycle management API (`POST/PATCH /api/admin/tenants`)

# Status

ready

# Owner

backend

# Task Tags

- code
- api

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

`admin-service`에 테넌트 lifecycle CRUD API를 구현한다. 현재는 `account-service`에 `Tenant` 도메인 + 내부 provisioning이 있지만, 운영자가 신규 테넌트를 등록·중단할 통로(spec § "테넌트 등록 방식"이 명시한 admin-service 경로)가 없다. 이 task가 그 게이트웨이를 채운다.

**선행 contract** (TASK-BE-256, completed): [admin-api.md § Tenant Lifecycle](../../specs/contracts/http/admin-api.md#tenant-lifecycle-task-be-256) + [tenant-events.md](../../specs/contracts/events/tenant-events.md). 본 태스크는 이 contract 를 정확히 구현 대상으로 사용한다 — endpoint 형태 / 요청·응답 schema / 오류 코드 / status 전이 매트릭스 / 예약어 / outbox 이벤트 형태를 임의 변경 금지. contract 변경이 필요하면 본 태스크 시작 전 BE-256 의 후속 contract patch task 를 발행할 것.

완료 시점 (BE-256 contract 와 일치):

1. SUPER_ADMIN이 `POST /api/admin/tenants` 로 신규 테넌트 등록 (e.g. `wms`, `erp`). 예약어 차단 (`400 TENANT_ID_RESERVED`).
2. `PATCH /api/admin/tenants/{tenantId}` 단일 endpoint 로 `displayName` 또는 `status` (또는 둘 다) 변경. status 전이 매트릭스 (ACTIVE ↔ SUSPENDED) 준수, 동일 status PATCH 는 no-op (200 + audit/event 미발행).
3. `GET /api/admin/tenants` (목록, `status`/`tenantType` 필터) · `GET /api/admin/tenants/{tenantId}` (상세, SUPER_ADMIN 또는 본인 테넌트) 로 조회.
4. 모든 mutation은 `admin_actions`에 `action_code IN ('TENANT_CREATE', 'TENANT_SUSPEND', 'TENANT_REACTIVATE', 'TENANT_UPDATE')` 로 기록 + outbox 이벤트 (`tenant.created`, `tenant.suspended`, `tenant.reactivated`, `tenant.updated`) 발행.
5. admin-service는 account-service의 internal provisioning API를 호출하여 실제 tenant row를 생성/업데이트 (admin-service 자체는 tenant data를 직접 소유하지 않음 — account-service가 source of truth).

---

# Scope

## In Scope

- `admin-service` 신규 모듈: `application/tenant/` (use cases) + `presentation/tenant/` (controllers)
  - `CreateTenantUseCase`: validation → account-service `POST /internal/tenants` 호출 → audit_action 기록
  - `SuspendTenantUseCase` / `ReactivateTenantUseCase`: 상태 전이 + audit
  - `ListTenantsUseCase` · `GetTenantUseCase`: account-service `GET /internal/tenants` 프록시
- `account-service`에 internal API 추가/확장 (이미 일부 존재 시 보강):
  - `POST /internal/tenants` (idempotent, header `X-Internal-Token` 검증)
  - `PATCH /internal/tenants/{tenantId}/status` (SUSPEND/ACTIVE 전이)
  - `GET /internal/tenants`, `GET /internal/tenants/{tenantId}`
- HTTP contract:
  - `specs/contracts/http/admin-tenants.yaml` (신규)
  - `specs/contracts/http/account-internal-tenants.yaml` (account-service internal — 신규/갱신)
- audit 통합:
  - `admin_actions.action_code` enum에 `TENANT_CREATE`, `TENANT_SUSPEND`, `TENANT_REACTIVATE` 추가
  - 각 동작은 TASK-BE-249 의 `target_tenant_id` 컬럼을 사용 (cross-tenant action으로 기록)
- 권한:
  - SUPER_ADMIN만 호출 가능 (PermissionEvaluator로 gate)
- resilience:
  - account-service 호출은 기존 `ResilienceClientFactory` 사용 (CB + retry)

## Out of Scope

- 테넌트 자체의 데이터 마이그레이션 (예: 기존 tenant 데이터 보존 + 신규 schema migrate) — operations runbook에 절차만 정의
- 테넌트 삭제 (HARD DELETE) — GDPR-driven tenant offboarding은 별도 ADR 후 separate task
- 테넌트별 rate limit/feature flag 설정 UI — 별도 frontend task
- WMS-specific user provisioning bulk import — 이미 TASK-BE-231에서 single-user provisioning은 존재, bulk는 별도

---

# Acceptance Criteria

- [ ] `POST /api/admin/tenants` (SUPER_ADMIN only): 201 + `{tenantId, displayName, tenantType, status, createdAt}` 응답
- [ ] `PATCH /api/admin/tenants/{tenantId}/suspend` (SUPER_ADMIN only): 200 + `{status: "SUSPENDED"}`
- [ ] `PATCH /api/admin/tenants/{tenantId}/reactivate` (SUPER_ADMIN only): 200 + `{status: "ACTIVE"}`
- [ ] `GET /api/admin/tenants` (SUPER_ADMIN only): 200 + 페이징 응답
- [ ] `GET /api/admin/tenants/{tenantId}` (SUPER_ADMIN or scoped operator with matching `tenant_id`): 200 + 상세
- [ ] 일반 operator가 위 mutating 엔드포인트 호출 시 403
- [ ] 모든 mutation은 `admin_actions`에 `action_code IN ('TENANT_CREATE', 'TENANT_SUSPEND', 'TENANT_REACTIVATE')` + `target_tenant_id` 기록
- [ ] account-service `/internal/tenants` 호출은 `ResilienceClientFactory` 기반 (CB open 시 503 반환)
- [ ] tenantId slug 정규식 검증: `^[a-z][a-z0-9-]{1,31}$` 위반 시 400 (`VALIDATION_ERROR`)
- [ ] 동일 `tenantId` 재등록 시 409 (`CONFLICT`)
- [ ] `./gradlew :projects:global-account-platform:apps:admin-service:check` PASS
- [ ] `./gradlew :projects:global-account-platform:apps:admin-service:integrationTest` PASS

---

# Related Specs

> Step 0: read `PROJECT.md`, `rules/common.md`, `rules/domains/saas.md`, `rules/traits/{audit-heavy,multi-tenant,integration-heavy}.md`.

- `specs/features/multi-tenancy.md` § "테넌트 등록 방식"
- `specs/features/multi-tenancy.md` § "Tenant 엔터티"
- `specs/features/admin-operations.md`
- `specs/services/admin-service/architecture.md`
- `specs/services/account-service/architecture.md` (internal API 위치)

# Related Skills

- `.claude/skills/backend/integration-resilience.md`
- `.claude/skills/backend/audit-trail-policy.md`
- `.claude/skills/backend/idempotency-pattern.md`

---

# Related Contracts

- `specs/contracts/http/admin-tenants.yaml` — 신규 (이 태스크가 작성)
- `specs/contracts/http/account-internal-tenants.yaml` — 신규 또는 보강
- `admin_actions` 이벤트 (있을 시) — `TENANT_*` action_code 추가

---

# Target Service

- `admin-service` (primary)
- `account-service` (internal API 보강)

---

# Architecture

- `admin-service`는 tenant 데이터를 직접 소유하지 않음 — account-service의 `domain/tenant/`가 source of truth.
- `admin-service`는 use-case → account-service 호출 → audit 기록 의 thin orchestration layer로 동작.
- 실패 시나리오:
  - account-service unreachable → `ResilienceClientFactory` CB가 open되어 503 + `INTEGRATION_UNAVAILABLE` 코드 반환.
  - account-service 200 + admin-service audit 실패 → 일관성 위반. 대응: outbox 패턴 (admin-service의 audit 기록을 outbox로 처리해 retry).

---

# Implementation Notes

- **`Idempotency-Key` 헤더**: `POST /api/admin/tenants` 는 idempotent해야 함. 이미 존재하는 `tenantId`로 재요청 시 200 (또는 409, 정책에 따라). 운영자가 retry할 때 중복 audit row를 만들지 않도록 idempotency store 활용.
- **TenantType enum**: `B2C_CONSUMER`, `B2B_ENTERPRISE` (spec § Tenant 엔터티). request body에서 받음.
- **수동 등록만 허용**: self-service 가입 없음. `gateway-service`의 일반 routing path가 아닌 `/api/admin/tenants` 만 사용.
- **SUPER_ADMIN 검증**: PermissionEvaluator (TASK-BE-249)에서 `operator.tenantId == '*'` 체크. 없으면 403.

---

# Edge Cases

- 운영자가 테넌트를 SUSPEND한 직후 해당 테넌트의 access token이 여전히 유효 — 별도 작업 (gateway에서 SUSPENDED tenant token reject) 권장이지만 본 태스크 out of scope; 대신 `application.yml`의 token TTL을 짧게(15min) 운용 권고를 RUNBOOK에 명시.
- account-service에서 tenant 등록 시 conflict (이미 존재) — 409 그대로 반환, audit row는 만들지 않음.
- account-service 200 OK이지만 audit 기록 실패 — outbox로 retry; 외부 응답은 이미 보낸 상태이므로 사용자에겐 성공 응답이지만 메트릭은 `audit:write.failure.count` 증가.

---

# Failure Scenarios

- **account-service 다운 → CB open**: 운영자에게 503 + retry 안내.
- **token 위조한 일반 operator**: gateway에서 JWT 검증 + admin-service에서 PermissionEvaluator double-check.
- **tenantId slug 충돌**: account-service `POST /internal/tenants`가 409로 응답하면 admin-service도 409 그대로 전파.
- **partial state**: account-service 등록 성공 + audit 기록 실패 → outbox retry 정상화 (eventual consistency 허용).

---

# Test Requirements

- 단위 테스트:
  - `CreateTenantUseCase`: mock account-service port. happy path / slug invalid / 409 conflict / 503 CB open
  - `SuspendTenantUseCase` / `ReactivateTenantUseCase`: idempotent re-call 시 audit row 단일화
- 통합 테스트 (`@Tag("integration")`):
  - end-to-end: SUPER_ADMIN token으로 `POST /api/admin/tenants` → account-service에 row 생성 + admin_actions에 TENANT_CREATE 기록
  - 일반 operator 차단: 403
  - tenantId 형식 위반: 400
  - 중복 등록: 409
  - account-service 다운 시 (테스트 환경에서 wiremock으로 5xx 시뮬레이션): 503 + CB open 메트릭 증가

---

# Definition of Done

- [ ] Implementation completed (admin-service controllers + use cases + ports)
- [ ] account-service internal API 보강 (필요 시)
- [ ] Unit + integration tests added
- [ ] Tests passing (CI green)
- [ ] HTTP contracts: admin-tenants.yaml + account-internal-tenants.yaml 작성
- [ ] specs/services/admin-service/architecture.md 갱신
- [ ] specs/features/multi-tenancy.md § "테넌트 등록 방식" 의 endpoint detail 보강 (현재는 이름만 언급)
- [ ] Ready for review
