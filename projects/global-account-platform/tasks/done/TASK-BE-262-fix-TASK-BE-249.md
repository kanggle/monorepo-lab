# Task ID

TASK-BE-262

# Title

Follow-up to TASK-BE-249: per-tenant email uniqueness pre-check + audit row for cross-tenant deny

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

TASK-BE-249에서 누락된 두 가지 일관성 결함을 보정한다:

1. **`existsByEmail` 단일 컬럼 검사 → `(tenant_id, email)` 복합 검사**
   `CreateOperatorUseCase`는 V0023이 도입한 복합 unique index `(tenant_id, email)` 모델과 어긋나는 단일-컬럼 사전 검사를 사용한다. 결과적으로 동일 이메일을 다른 테넌트에 등록하려는 정당한 요청이 잘못된 `OPERATOR_EMAIL_CONFLICT(409)`로 거부된다 (DB 제약은 통과). admin-api.md 계약(`동일 (tenant_id, email) 운영자 이미 존재`) 및 task spec Edge Cases(`동일 이메일을 가진 operator가 여러 테넌트에 — (tenant_id, email) 복합 unique index 사용, 다른 테넌트의 동일 이메일은 별개 운영자`)와 어긋난다.

2. **`TenantScopeDeniedException` 발생 시 audit row 누락**
   현재 `AuditQueryUseCase`/`CreateOperatorUseCase`는 cross-tenant 거부 시 예외를 던지고 즉시 403으로 응답하지만, `admin_actions`에는 어떤 행도 기록되지 않는다. 그러나 `specs/services/admin-service/architecture.md` §Tenant Scope Enforcement는 명시적으로 다음을 요구한다:
   > `OPERATOR_DENY` 행: `tenant_id = operator.tenantId`, `target_tenant_id = operator.tenantId` (크로스 테넌트 거부도 자기 테넌트 기록)
   이 요구는 audit-heavy A1(모든 정책 거부 행위는 감사 가능해야 함)과 일치한다. 현재 구현은 cross-tenant 공격 시도를 사후 분석할 수 없게 만든다.

원본 태스크: `TASK-BE-249-admin-service-tenant-audit-schema.md` (review → done).

---

# Scope

## In Scope

- `AdminOperatorJpaRepository`에 `boolean existsByTenantIdAndEmail(String tenantId, String email)` 추가
- `CreateOperatorUseCase`가 새 메서드를 사용하도록 변경 (`existsByEmail(email)` 호출 제거)
- `AdminActionAuditor`의 cross-tenant deny 기록 메서드 추가 (또는 기존 `recordDenied` 재사용 + tenant 정보 전달)
- `AuditQueryUseCase`의 `TenantScopeDeniedException` 던지기 직전에 OPERATOR_DENY (또는 동등) audit row INSERT
- `CreateOperatorUseCase`의 `TenantScopeDeniedException` 던지기 직전에 동일 처리
- `AuditQueryUseCase`/`CreateOperatorUseCase` 단위 테스트에 cross-tenant deny audit row 기록 검증 추가
- 기존 `existsByEmail` 메서드는 deprecate 표시(또는 제거 — 사용처가 더 없으면)

## Out of Scope

- `AccountAdminController`/`SessionAdminController`/GDPR 엔드포인트의 cross-tenant 차단 — 별도 후속 태스크
- `searchCrossTenant`의 시맨틱 변경(현재는 SUPER_ADMIN의 cross-tenant 행만 조회) — 의도된 디자인이며 변경 시 별도 ADR 필요
- 기존 backward-compat 생성자(`OperatorSummary(2-arg)`, `AdminActionJpaEntity.create(13/15-arg)`) 정리

---

# Acceptance Criteria

- [ ] `AdminOperatorJpaRepository.existsByTenantIdAndEmail(tenantId, email)` 메서드 추가
- [ ] `CreateOperatorUseCase`가 새 메서드를 사용하며, 동일 이메일이 다른 테넌트에 존재해도 INSERT까지 진행 (DB 제약이 최종 가드)
- [ ] cross-tenant deny 케이스에서 `admin_actions` 테이블에 `outcome=DENIED`, `tenant_id=operator.tenantId`, `target_tenant_id=operator.tenantId`(또는 거부 대상 테넌트 — 결정 ADR로 기록), `permission_used` 적절한 값으로 행 INSERT 확인
- [ ] `AuditQueryUseCase` 단위 테스트: 일반 운영자가 다른 테넌트 조회 시 `auditor.recordXxx(...)` 호출 검증
- [ ] `CreateOperatorUseCase` 단위 테스트: 비-platform-scope 액터의 `tenantId='*'` 운영자 생성 시도 시 audit row 기록 검증
- [ ] `CreateOperatorUseCase` 단위 테스트 추가: 동일 이메일 + 다른 tenantId 조합 → 정상 생성 (현재 시점 새 동작)
- [ ] `./gradlew :projects:global-account-platform:apps:admin-service:check` PASS

---

# Related Specs

- `specs/services/admin-service/architecture.md` §Tenant Scope Enforcement (감사 행 구성 규칙 — `OPERATOR_DENY` 행 명시)
- `specs/features/admin-operations.md` §Cross-Tenant Semantics
- `rules/traits/audit-heavy.md` A1 (모든 정책 거부는 감사)
- `docs/adr/ADR-002-admin-tenant-scope-sentinel.md`
- 원본 태스크: `tasks/done/TASK-BE-249-admin-service-tenant-audit-schema.md` §Edge Cases (동일 이메일 다른 테넌트 허용)

---

# Related Contracts

- `specs/contracts/http/admin-api.md` §POST /api/admin/operators
  - `409 OPERATOR_EMAIL_CONFLICT` 조건이 `동일 (tenant_id, email)`이라 명시되어 있으므로 contract와 implementation alignment
- `specs/contracts/http/admin-api.md` §GET /api/admin/audit
  - `403 TENANT_SCOPE_DENIED` 응답 시 audit row가 기록된다는 점을 spec에 추가하거나 `Side Effects` 섹션 추가 검토

---

# Edge Cases

- 동일 이메일 + 동일 테넌트 → 기존 동작 유지 (`409 OPERATOR_EMAIL_CONFLICT`)
- 동일 이메일 + 다른 테넌트 → 새 동작 (정상 생성)
- 동일 이메일 + 동일 테넌트 + 동시 요청 두 건 → DB 제약이 최종 가드, 한 건 성공/한 건 충돌
- platform-scope 운영자(`tenantId='*'`)가 임의 테넌트의 이메일과 충돌 → 별도 row(중복 아님 — 다른 (tenant_id, email))
- cross-tenant deny audit row 기록 자체가 실패할 경우 → audit-heavy A10에 따라 fail-closed (요청도 실패시킬지 vs. deny audit는 best-effort로 swallow할지 판단 필요. ADR-002 update 또는 본 태스크의 결정 노트로 기록)

---

# Failure Scenarios

- **deny audit 기록 중 DB 오류**: fail-closed 적용 시 사용자에게 500 반환 → cross-tenant 공격자가 deny 자체를 트리거해 서비스 불안정화 시도 가능. best-effort(swallow + log warn)로 처리하되 메트릭(`audit:cross_tenant_deny_audit_failure.count`)으로 모니터링 권장. 결정은 본 태스크 구현 PR description에 명시.
- **마이그레이션된 environment에서 테스트 실패**: 기존 통합 테스트가 동일 이메일을 다른 테넌트에 등록하던 경로가 있다면 (현재로선 없을 것) 회귀 발생 가능 — 영향 분석 필수.

---

# Test Requirements

- 단위 테스트:
  - `CreateOperatorUseCaseTest`: 동일 이메일 + 다른 tenantId → 성공
  - `CreateOperatorUseCaseTest`: 동일 이메일 + 동일 tenantId → 충돌
  - `CreateOperatorUseCaseTest`: TenantScopeDeniedException 발생 시 auditor.recordDenied(...) 호출 검증
  - `AuditQueryUseCaseTest`: cross-tenant request → auditor 호출 검증
- 통합 테스트:
  - `OperatorAdminIntegrationTest`: tenantA의 operator가 platform-scope creation 시도 → 403 + DB에 DENIED row 1건 INSERT
  - `AdminAuditTenantScopeIntegrationTest`: tenantA가 tenantB 조회 → 403 + DB에 DENIED row 1건 INSERT

---

# Definition of Done

- [ ] Implementation completed
- [ ] Unit + integration tests added
- [ ] Tests passing (CI green)
- [ ] HTTP contract updated (admin-api.md `Side Effects` 섹션에 audit row 명시)
- [ ] (선택) `existsByEmail` 메서드 deprecate 또는 제거
- [ ] Ready for review
