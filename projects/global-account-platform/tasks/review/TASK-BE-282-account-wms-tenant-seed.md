# Task ID

TASK-BE-282

# Title

account-service `V0016__seed_wms_tenant.sql` — TASK-MONO-088 PR-time first-call validation cycle 8 root cause closure

# Status

review

# Owner

backend

# Task Tags

- code
- migration
- multi-tenant
- adr-followup
- bugfix
- e2e

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

TASK-MONO-088 (gap PR-time smoke job 신설, commit `57254783`) + TASK-MONO-089 (TenantProvisioningE2ETest smoke→full demotion, commit `9a814d3d`) 의 직접 후속 — PR-time first-call validation 의 **cycle 8 root cause closure**.

진단 결과 (commit `0c836f29` 시점):

1. MONO-088 의 PR-time first-call validation 첫 trigger (run `25829462751`) 가 `TenantProvisioningE2ETest` 의 step1 `POST /internal/tenants/wms/accounts → 201` fail 노출.
2. assertion message ("TASK-BE-228 not implemented") 는 outdated — BE-228/229/230/231 모두 done (`TenantProvisioningController.java` 실재 + endpoint mapping 정확 + `INTERNAL_API_TOKEN: e2e-internal-token` env 일치).
3. **실제 root cause** = account-service Flyway migration 의 **`wms` tenant seed 부재**.

```
V0009 create_tenants            ← schema + 'fan-platform' (B2C) seed
V0010 add_tenant_id_to_domain_tables
V0011 remove_tenant_id_defaults  ← NOT NULL strict mode
V0014 seed_ecommerce_tenant      ← 'ecommerce' (B2C) seed
V0015 seed_scm_tenant            ← 'scm' (B2B_ENTERPRISE) seed
V0016 seed_wms_tenant            ← **신규 추가 (본 task)**
```

V0014 의 comment 가 v1 design intent 명시:
> "wms tenant is not seeded in this table because wms uses GAP only for OAuth client_credentials service-to-service flows in v1; consumer-style provisioning that hits accounts.tenant_id is ecommerce / fan-platform only."

그러나 `TenantProvisioningE2ETest` 는 `wms` tenant 로 consumer-style account provisioning 검증 → `tenants.tenant_id='wms'` row 부재 → `accounts.tenant_id` FK 위배 또는 `ProvisionAccountUseCase` tenant existence 검증 실패 → 4xx (404 / 422).

**Design intent 결정**: 사용자 명시 승인 (2026-05-14 session) 으로 **wms tenant 의 consumer-style provisioning 도 허용** 으로 정책 확장. v1 "wms = service-to-service only" 가정 완화. 의도: GAP IdP 의 multi-tenant lifecycle (B2B_ENTERPRISE) 시연 깊이 강화 + `TenantProvisioningE2ETest` 5-step flow 의 demonstrate 가치 복원 (BE-228~231 e2e 검증 본격 작동).

본 task = **V0016 wms tenant seed migration 추가 (1-line INSERT)**. V0015 scm pattern 답습. wms 는 B2B_ENTERPRISE (V0015 scm 와 동일 분류, V0014 ecommerce 의 B2C_CONSUMER 와 차별).

provenance:
- TASK-MONO-088 close commit `d3bb7d49` + Verification (실측) → CI run `25829462751` FAIL.
- TASK-MONO-089 close commit `700fc6ba` (분류 fix 완료, smoke→full).
- 2026-05-14 session 의 design intent 확정 (Option A — wms tenant seed).

---

# Scope

## In Scope

### A. `V0016__seed_wms_tenant.sql` 추가

위치: `projects/global-account-platform/apps/account-service/src/main/resources/db/migration/V0016__seed_wms_tenant.sql`

내용 (V0015 scm pattern 답습):

```sql
-- TASK-BE-282: register the 'wms' tenant for wms-platform.
-- v1 (TASK-MONO-027/042 era) intentionally seeded only ecommerce + scm
-- and excluded wms — V0014 comment "wms uses GAP only for OAuth
-- client_credentials service-to-service flows in v1; consumer-style
-- provisioning that hits accounts.tenant_id is ecommerce / fan-platform
-- only." That assumption is now reversed (2026-05-14 design decision,
-- TASK-MONO-088 PR-time first-call validation cycle 8 finding): wms
-- supports consumer-style account provisioning to enable GAP IdP
-- multi-tenant lifecycle demonstration (TenantProvisioningE2ETest
-- BE-228~231 e2e flow).
--
-- TenantType: B2B_ENTERPRISE — warehouse management platform is
-- enterprise-facing (operators, supervisors, admins), not consumer-facing.
-- Mirrors scm (V0015) B2B_ENTERPRISE; differs from ecommerce (V0014) /
-- fan-platform (V0009) B2C_CONSUMER seeding.
--
-- INSERT IGNORE keeps the migration idempotent so re-running on
-- environments that may have hand-seeded the row stays safe.
INSERT IGNORE INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
VALUES ('wms', 'Warehouse Management Platform', 'B2B_ENTERPRISE', 'ACTIVE', NOW(6), NOW(6));
```

### B. 검증

- 다음 nightly `gap-e2e-full` run (또는 push to main 의 자체 trigger 의 PR-time `gap-platform-e2e-smoke`) 에서 `TenantProvisioningE2ETest` 5/6 step GREEN 회복 예상 (TenantProvisioning step1-5 + GoldenPath = nightly full target 의 일부).
- 단 본 task 는 V0016 migration 추가만 — TenantProvisioningE2ETest 가 full 분류라 PR-time smoke 에서 실행 안 됨. 검증 자연 시점 = next nightly cron tick + push to main 의 gap-e2e-full job.

## Out of Scope

- V0014 comment 갱신 (historical statement 보존, V0016 가 evolution 표기). Flyway migration comment 는 acceptance 시점 snapshot.
- TenantProvisioningE2ETest 의 다른 step (login / JWT claims / gateway propagation / cross-tenant) 검증 — BE-229/230/231 done 상태에서 자연 작동 예상. fail 시 별 cycle 9 finding.
- wms tenant 의 oauth_clients 등록 (auth-service V0014 도 별 migration, scm 의 client_credentials only flow 와 align). 본 task scope 밖.
- TenantProvisioningE2ETest 의 smoke 재진입 (MONO-089 가 full 로 demote, BE-N closure 시 smoke 가능 명시했지만 본 task 는 그 단계 아님).

---

# Acceptance Criteria

### Impl PR

- [x] `V0016__seed_wms_tenant.sql` 파일 생성.
- [x] INSERT IGNORE INTO tenants (tenant_id='wms', display_name='Warehouse Management Platform', tenant_type='B2B_ENTERPRISE', status='ACTIVE').
- [x] V0014 + V0015 comment pattern 답습 (TASK reference, design intent reverse 명시, INSERT IGNORE idempotent 설명).
- [x] task lifecycle ready → review (in-progress 우회, mechanical 1-line single-PR closure 패턴 — TASK-MONO-084/085/086/087/088/089 precedent).
- [x] [`gap tasks/INDEX.md`](../INDEX.md) 동기.

### CI verification

- [ ] push to main 직후 자연 trigger 의 `gap-platform-e2e-smoke` PR-time = GoldenPath 1 class 변경 0 (TenantProvisioning 은 full 분류라 영향 0).
- [ ] 다음 nightly run 의 `gap-e2e-full` 에서 `TenantProvisioningE2ETest` 검증 (5/6 step GREEN 예상, BE-228~231 e2e flow 본격 작동). wall-clock 실측 자료화.
- [ ] account-service 의 다른 integration test (특히 `TenantProvisioningIntegrationTest`) 회귀 0.

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] [`gap tasks/INDEX.md`](../INDEX.md) ## review 제거, ## done append 1-line outcome.

---

# Related Specs

- [`projects/global-account-platform/specs/features/multi-tenancy.md`](../../specs/features/multi-tenancy.md) (multi-tenant row-level isolation 의 source-of-truth)
- [`projects/global-account-platform/apps/account-service/src/main/resources/db/migration/V0009__create_tenants.sql`](../../apps/account-service/src/main/resources/db/migration/V0009__create_tenants.sql) (tenants schema)
- [`projects/global-account-platform/apps/account-service/src/main/resources/db/migration/V0014__seed_ecommerce_tenant.sql`](../../apps/account-service/src/main/resources/db/migration/V0014__seed_ecommerce_tenant.sql) (B2C seed pattern 답습)
- [`projects/global-account-platform/apps/account-service/src/main/resources/db/migration/V0015__seed_scm_tenant.sql`](../../apps/account-service/src/main/resources/db/migration/V0015__seed_scm_tenant.sql) (B2B_ENTERPRISE seed pattern 답습 — 본 task 의 most direct precedent)
- [`tasks/done/TASK-MONO-088-gap-pr-time-smoke-job.md`](../../../../tasks/done/TASK-MONO-088-gap-pr-time-smoke-job.md) (PR-time first-call validation 의무화 source — cycle 8 finding의 enabler)
- [`tasks/done/TASK-MONO-089-gap-tenantprovisioning-smoke-tag-fix.md`](../../../../tasks/done/TASK-MONO-089-gap-tenantprovisioning-smoke-tag-fix.md) (분류 fix follow-up — full 분류로 다음 nightly 에서 검증 예상)
- [`projects/global-account-platform/tests/e2e/src/test/java/com/example/e2e/TenantProvisioningE2ETest.java`](../../tests/e2e/src/test/java/com/example/e2e/TenantProvisioningE2ETest.java) (검증 target test class)

---

# Related Contracts

본 task = Flyway migration 추가 (seed data). HTTP API contract 변경 0. `POST /internal/tenants/{tenantId}/accounts` 의 OpenAPI spec 변경 0 — endpoint 자체 동작 변화 없음, seed 추가만으로 wms tenant 가 valid `{tenantId}` value 가 됨.

`auth-to-account.md` contract 와의 정합성 영향 0 (tenant resolution 는 별 query, seed 만 추가).

---

# Target Service

`account-service` — Flyway migration 1 file 추가.

---

# Architecture

본 task = v1 multi-tenant design intent 의 evolution. V0014 (2026-04-26 추정, TASK-MONO-027) 의 "wms = service-to-service only" 가정이 multi-tenant 시연 깊이 측면에서 한계 — TenantProvisioningE2ETest 의 BE-228~231 e2e demonstrate 목적과 align 안 됨. 2026-05-14 design decision (TASK-MONO-088 first-call validation cycle 8 finding) 으로 wms 도 consumer-style provisioning 허용.

evolution chain:
- V0009 (fan-platform B2C_CONSUMER seed) — TASK-BE-228 era
- V0014 (ecommerce B2C_CONSUMER seed) — TASK-MONO-027 era
- V0015 (scm B2B_ENTERPRISE seed) — TASK-MONO-042 era
- **V0016 (wms B2B_ENTERPRISE seed) — TASK-BE-282 본 task**

tenant_type 결정 (B2B_ENTERPRISE):
- wms 는 warehouse operations (조작자/관리자/운영자) 대상 — enterprise-facing.
- scm 와 동일 B2B nature (V0015 의 직접 precedent).
- ecommerce / fan-platform 의 B2C_CONSUMER 와 차별.

---

# Implementation Notes

## 1-line INSERT (보일러플레이트 후)

```sql
INSERT IGNORE INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
VALUES ('wms', 'Warehouse Management Platform', 'B2B_ENTERPRISE', 'ACTIVE', NOW(6), NOW(6));
```

V0015 의 INSERT line 과 byte-level 평행 (tenant_id + display_name + tenant_type 값만 차이).

## Comment block (V0015 답습 + design intent reverse 명시)

V0014/V0015 의 comment 패턴 (TASK reference + design rationale + tenant_type 결정 + INSERT IGNORE idempotent 설명) 답습 + 본 task 특수 사항 (design intent reverse, 2026-05-14 decision) 추가.

## D4 churn impact

- 1 file gap project touch (account-service Flyway migration).
- 1 INSERT statement, schema 변경 0.
- ADR-MONO-003a § D1.1 인접 (project-internal Flyway migration evolution). D4 OVERRIDE 적용 검토 — 그러나 본 task 는 project-internal (`projects/global-account-platform/`) 이라 D4 churn 의 scope 협소, OVERRIDE 자연 적용.

---

# Edge Cases

- 기존 dev 환경 / hand-seeded wms tenant: INSERT IGNORE 가 idempotent 보장.
- account_db 의 다른 schema (예: account_roles, accounts 등) 의 `tenant_id` FK 무관 (wms row 추가만, 다른 row 의 reference 미존재).
- auth-service oauth_clients 의 wms client 등록은 별 migration (auth-service V0014/V0015 - 별 task scope).
- gap-e2e docker-compose 환경의 account_db = fresh container 라 Flyway 가 처음부터 V0001~V0016 모두 적용 → wms tenant seed 자연 작동.

---

# Failure Scenarios

- V0016 추가 후 다음 nightly gap-e2e-full 에서 `TenantProvisioningE2ETest` 가 step2 (login) 또는 step3 (JWT claims) 에서 fail → 다른 cycle (cycle 9) finding. BE-229/230 의 추가 layer 누락 가능. follow-up task 분리 (TASK-BE-N+).
- step5 (cross-tenant rejection) 가 `TENANT_SCOPE_DENIED` 응답 안 함 → BE-231 의 gateway 측 enforcement 누락. follow-up.
- account-service Flyway 가 V0016 migration 적용 실패 (예: tenants table lock 충돌) → CI fail. retry-on-flake 또는 schema 검토.

---

# Test Requirements

- account-service integration test (`TenantProvisioningIntegrationTest`) 회귀 0. 본 task = seed 추가만, integration test 가 use-case 자체는 검증.
- 다음 nightly gap-e2e-full 에서 `TenantProvisioningE2ETest` 의 5/6 step GREEN 회복 (또는 cycle 9 finding 분리).
- production code = 0 (Flyway migration only).

---

# Definition of Done

### Impl PR

- [ ] AC 완료.
- [ ] task lifecycle ready → review.

### CI verification

- [ ] 다음 nightly gap-e2e-full 에서 TenantProvisioningE2ETest 검증 결과 자료화.
- [ ] account-service 다른 IT 회귀 0.

### Close chore PR

- [ ] review → done, [`gap tasks/INDEX.md`](../INDEX.md) 동기.

---

# Provenance

- TASK-MONO-088 (close commit `d3bb7d49`, 2026-05-14) PR-time first-call validation 첫 trigger FAIL finding (run `25829462751`).
- TASK-MONO-089 (close commit `700fc6ba`, 2026-05-14) TenantProvisioningE2ETest smoke→full 분류 fix.
- 2026-05-14 session 의 design decision (Option A — wms tenant seed, v1 "service-to-service only" 가정 reverse).
- V0014 (ecommerce, TASK-MONO-027) + V0015 (scm, TASK-MONO-042) 의 sibling seed migration pattern 답습.
- 메모리 `project_e2e_3phase_strategy_complete.md` § "Phase 3 의 7 cycle archaeological inspection" PR-time 가속 패턴 (cycle 8 가 PR-time first-call validation 으로 surface, ~30분 cycle).
- D4 OVERRIDE: ADR-MONO-003a § D1.1 인접 (project-internal Flyway migration evolution).
- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (1-line mechanical migration, V0015 byte-level 답습).
