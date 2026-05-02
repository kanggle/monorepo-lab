# Task ID

TASK-MONO-023c

# Title

AccountAnonymization / AccountEventPublisher / AdminAuditTenantScope 회귀 fix

# Status

ready

# Owner

backend / qa

# Task Tags

- code
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

[TASK-MONO-023](../in-progress/TASK-MONO-023-main-baseline-integration-cleanup.md) 의 분류 매트릭스에서 식별된 **audit / anonymization 관련 3 통합 테스트** 의 회귀를 fix:

| 테스트 클래스 | 증상 | 추정 원인 |
|---|---|---|
| `account.AccountAnonymizationSchedulerIntegrationTest` | `actorType=user` 인데 spec 은 `system` 기대 | 배치 스케줄러가 actor 컨텍스트 없이 호출 시 `actorType` 기본값 처리 회귀 |
| `account.AccountEventPublisherIntegrationTest` | `No EntityManager with actual transaction available` | publisher 호출 시점에 트랜잭션 컨텍스트 없음 — `@Transactional` 누락 |
| `admin.AdminAuditTenantScopeIntegrationTest` | `expected:1 was:0` (cross-tenant deny audit row 미존재) | TASK-BE-262 spec 에 명시된 cross-tenant deny audit row INSERT 가 production 코드에 미구현 |

이 태스크 완료 후 위 3 테스트가 모두 PASS.

---

# Scope

## In Scope

### 1. AccountAnonymizationScheduler — actorType 회귀

- 스케줄러 호출 시 actor 컨텍스트 명시 (`actorType=system`, `actorId=anonymization-batch` 등)
- spec 의 의도된 actorType 명세 확인 + 코드 정렬
- `account.deleted` (anonymized=true) 이벤트의 `actorType` 필드가 `system` 으로 발행되는지 단위 테스트 추가

### 2. AccountEventPublisher — 트랜잭션 컨텍스트 회귀

- publisher 호출 시점의 호출 경로 분석 (`@Transactional` 경계가 끊어지는 지점)
- 직접 EntityManager 사용을 outbox pattern 으로 우회 (이미 outbox 가 있다면 publisher 가 outbox 를 통해 발행)
- `@TransactionalEventListener` 사용 시 propagation 검토

### 3. AdminAuditTenantScope — cross-tenant deny audit row INSERT

- TASK-BE-262 의 spec 검토 (이미 done 상태인지, 미구현인지 확인)
- `AuditQueryUseCase` 와 `CreateOperatorUseCase` 의 `TenantScopeDeniedException` 발생 직전에 `admin_actions` INSERT
- audit row 컬럼: `tenant_id=operator.tenantId`, `target_tenant_id=operator.tenantId` (또는 cross-tenant target), `outcome=DENIED`, `permission_used` 적절
- spec 결정 사항: cross-tenant deny audit 기록 실패 시 fail-closed vs swallow + warn — best-effort + 메트릭 권장

## Out of Scope

- Provisioning 회귀 — TASK-MONO-023a
- OAuth2 / OIDC 회귀 — TASK-MONO-023b
- Outbox 회귀 — TASK-MONO-023d
- Community JPA 격리 — TASK-MONO-023e

---

# Acceptance Criteria

- [ ] `AccountAnonymizationSchedulerIntegrationTest` PASS — actorType=system 단언 통과
- [ ] `AccountEventPublisherIntegrationTest` PASS — EntityManager 트랜잭션 정상
- [ ] `AdminAuditTenantScopeIntegrationTest` PASS — cross-tenant deny 시 audit row INSERT 확인 (count=1)
- [ ] 각 fix 에 단위 테스트 추가
- [ ] spec 보강 필요 시 별도 PR 또는 본 PR 에 spec 변경 포함

---

# Related Specs

- `projects/global-account-platform/specs/services/account-service/architecture.md` § Anonymization Scheduler
- `projects/global-account-platform/specs/services/admin-service/architecture.md` § Tenant Scope Enforcement
- `projects/global-account-platform/specs/features/admin-operations.md` § Cross-Tenant Semantics
- `projects/global-account-platform/tasks/done/TASK-BE-262-fix-TASK-BE-249.md` — cross-tenant deny audit 기획

---

# Related Contracts

- `specs/contracts/events/account-events.md` § account.deleted (anonymized=true)
- `specs/contracts/http/admin-api.md` § GET /api/admin/audit (Side Effects)

---

# Target Service / Component

- `projects/global-account-platform/apps/account-service/src/main/java/com/example/account/infrastructure/scheduler/AccountAnonymizationScheduler.java`
- `projects/global-account-platform/apps/account-service/src/main/java/com/example/account/application/event/AccountEventPublisher.java`
- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/application/AdminActionAuditor.java`
- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/application/AuditQueryUseCase.java`
- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/application/CreateOperatorUseCase.java`
- 대응 통합 테스트 파일 3건

---

# Implementation Notes

- 각 fix 가 독립적이라 sub-sub-task 분할도 가능하나, 모두 audit-heavy 도메인이라 한 PR 에 묶음
- AccountAnonymization actorType: `actorType` 필드를 `"system"` 으로 발행 — `actorId` 도 적절한 식별자 (예: `"anonymization-batch"` 또는 `null`)
- AccountEventPublisher: `@Transactional(propagation = REQUIRES_NEW)` 도입 또는 outbox-based 발행으로 경계 명확화
- AdminAuditTenantScope: TASK-BE-262 의 done 여부 먼저 확인 — done 이면 implementation 누락된 부분만, 아직 ready 면 그 태스크와 통합

---

# Edge Cases

- AccountAnonymization 의 actorType 가 spec 에 명시되지 않으면 spec 보강 PR 먼저
- AccountEventPublisher 의 트랜잭션 위치가 어플리케이션 전역에 영향 → 회귀 영향 분석
- AdminAudit 의 fail-closed vs best-effort: ADR 또는 spec 노트 결정 후 구현

---

# Failure Scenarios

- audit row INSERT 실패 시 cross-tenant 공격자가 deny 자체를 트리거해 서비스 불안정 → best-effort + 메트릭 (`audit:cross_tenant_deny_audit_failure.count`)
- AccountEventPublisher 의 트랜잭션 변경이 다른 발행 경로에 영향 → 단위·통합 테스트 보강

---

# Test Requirements

- 3 통합 테스트 PASS
- 단위 테스트로 actorType / 트랜잭션 / audit row INSERT 각각 cover

---

# Definition of Done

- [ ] 3 통합 테스트 PASS (단일 + 5회 연속)
- [ ] 단위 테스트 추가
- [ ] spec 보강 (필요 시) 분리 PR 머지
- [ ] Ready for review
