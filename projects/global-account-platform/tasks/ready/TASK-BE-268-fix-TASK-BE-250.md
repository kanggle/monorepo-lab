# Task ID

TASK-BE-268

# Title

Fix TASK-BE-250: enforce X-Operator-Reason on tenant lifecycle endpoints + correct integration test assertion

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- test

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

TASK-BE-250 review에서 발견된 두 건의 결함을 수정한다.

1. **`X-Operator-Reason` 헤더 강제 누락** (Spec compliance — Warning)
   `TenantAdminController` 의 4개 엔드포인트 중 mutating 2개 (POST `/api/admin/tenants`, PATCH `/api/admin/tenants/{tenantId}`) 가 `@RequestHeader(value = "X-Operator-Reason", required = false)` 로 헤더를 옵셔널 처리하고 있다. [admin-api.md §Authentication](../../specs/contracts/http/admin-api.md#authentication) 은 "모든 `/api/admin/*` 경로는 기본적으로 ... `X-Operator-Reason` 헤더 필수" 를 명시하며, Tenant Lifecycle subtree 는 [Exceptions sub-tree](../../specs/contracts/http/admin-api.md#exceptions-no-operator-jwt-required) 에 포함되지 않는다. 누락 시 `400 REASON_REQUIRED` 가 반환되어야 한다 (이미 `AdminExceptionHandler.handleMissingHeader` 에 분기 존재).

   영향: 운영자가 사유 없이 mutation 을 호출할 수 있어 audit trail 의 reason 컬럼에 항상 `<not_provided>` 만 기록되며, audit-heavy A2 표준 필드 (reason) 위반.

2. **Integration test 의 잘못된 응답 코드 단언** (Testing — fix-needed)
   `TenantAdminIntegrationTest.regularOperator_post_returns_403` 가 `TENANT_SCOPE_DENIED` 를 단언하나, 실제 흐름은 `RequiresPermissionAspect` 가 먼저 실행되어 `tenant.manage` 권한 부재 → `PermissionDeniedException` → `PERMISSION_DENIED` 가 반환된다 (slice test 의 동일 시나리오는 정확히 `PERMISSION_DENIED` 로 단언). Docker 가 환경에서 이 integration test 가 실행되면 즉시 실패한다 — 현재 CI 가 Docker 미가용 환경에서 자동 skip 되므로 결함이 가려진 상태. CI 에 Docker 가 도입되면 즉시 회귀.

   조정: assertion 을 `PERMISSION_DENIED` 로 변경 (slice test 와 일치). 또는 별도 시나리오로 (a) `tenant.manage` 권한은 있으나 `tenant_id != '*'` 인 운영자 → `TENANT_SCOPE_DENIED` 케이스를 분리 추가.

---

# Scope

## In Scope

- `TenantAdminController.createTenant` / `updateTenant` 의 `X-Operator-Reason` 파라미터를 `required = true` 로 변경 (또는 명시적 검증). 누락 시 기존 `AdminExceptionHandler.handleMissingHeader` 가 `400 REASON_REQUIRED` 응답하도록 동작 검증.
- `TenantAdminIntegrationTest.regularOperator_post_returns_403` 의 단언을 `PERMISSION_DENIED` 로 수정.
- (옵션) integration test 에 `tenant.manage` 권한을 갖되 비-platform-scope 인 운영자에 대한 별도 `TENANT_SCOPE_DENIED` 시나리오 추가 — seed 시 SUPPORT 등 가상 role 에 `tenant.manage` 를 잠시 부여하거나, 직접 `admin_role_permissions` 에 임시 row 삽입.
- 테스트 추가: 슬라이스 테스트에서 X-Operator-Reason 누락 시 400 REASON_REQUIRED 검증.

## Out of Scope

- TASK-BE-250 의 다른 모든 부분 (controller / use case / publisher / persistence) — 검토 시 spec 준수 + 동작 정합성 확인 완료.
- `Idempotency-Key` 헤더 wiring (스펙상 "권장" 이며 본 task 의 implementation note 에 의식적으로 미구현으로 명시됨 — 별도 follow-up 시 재평가).

---

# Acceptance Criteria

- [ ] `POST /api/admin/tenants` `X-Operator-Reason` 누락 시 `400 REASON_REQUIRED` 응답 (slice test 로 검증).
- [ ] `PATCH /api/admin/tenants/{tenantId}` `X-Operator-Reason` 누락 시 `400 REASON_REQUIRED` 응답 (slice test 로 검증).
- [ ] `TenantAdminIntegrationTest.regularOperator_post_returns_403` 단언이 `PERMISSION_DENIED` (slice test 와 일치).
- [ ] (옵션) Integration test 에 `TENANT_SCOPE_DENIED` 별도 시나리오 (tenant.manage 보유 + 비-SUPER_ADMIN) 추가.
- [ ] `./gradlew :projects:global-account-platform:apps:admin-service:test` PASS.

---

# Related Specs

- [admin-api.md §Authentication](../../specs/contracts/http/admin-api.md#authentication) — `X-Operator-Reason` 필수
- [admin-api.md §Tenant Lifecycle (TASK-BE-256)](../../specs/contracts/http/admin-api.md#tenant-lifecycle-task-be-256) — 403 응답 코드 분기 (`PERMISSION_DENIED` vs `TENANT_SCOPE_DENIED`)
- [rules/traits/audit-heavy.md](../../../../rules/traits/audit-heavy.md) — A2 reason 컬럼 무결성

---

# Related Contracts

- `specs/contracts/http/admin-api.md` (기존, 변경 없음 — 구현이 spec 와 일치하지 않는 상태를 수정)

---

# Target Service

- `admin-service`

---

# Edge Cases

- `X-Operator-Reason: ` (빈 문자열) → 400 REASON_REQUIRED (이미 다른 admin 엔드포인트에서 동일 처리)
- URL 인코딩된 reason (e.g. `%EC%8A%A4%ED%8C%B8`) → 디코드 후 audit row 에 정상 저장 (현 `decodeReason` 로직 유지)

---

# Failure Scenarios

- 응답 코드 변경으로 인한 외부 모니터링 alert 미스: 본 endpoint 는 신규 도입이므로 외부 의존성 없음. internal CI 만 영향.

---

# Test Requirements

- 슬라이스 테스트:
  - POST `/api/admin/tenants` X-Operator-Reason 미설정 → 400 REASON_REQUIRED
  - PATCH `/api/admin/tenants/{id}` X-Operator-Reason 미설정 → 400 REASON_REQUIRED
- Integration 테스트:
  - 기존 `regularOperator_post_returns_403` 단언 수정 → `PERMISSION_DENIED`
  - (선택) 신규: tenant.manage 보유 + 비-platform-scope → `TENANT_SCOPE_DENIED`

---

# Definition of Done

- [ ] Implementation completed
- [ ] Unit + integration test 단언 수정
- [ ] `./gradlew :projects:global-account-platform:apps:admin-service:test` PASS
- [ ] Ready for review
