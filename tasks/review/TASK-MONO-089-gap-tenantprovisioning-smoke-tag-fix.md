# Task ID

TASK-MONO-089

# Title

`TenantProvisioningE2ETest` smoke→full 재분류 — TASK-MONO-088 PR-time first-call validation finding closure

# Status

review

# Owner

monorepo

# Task Tags

- ci
- e2e
- gap
- adr-followup
- bugfix

---

# Goal

TASK-MONO-088 (gap-platform-e2e-smoke PR-time job 신설, 2026-05-14 commit `57254783`) 의 **first-call validation 첫 trigger FAIL finding closure**.

C3 push 의 run `25829462751` 의 `E2E smoke (gap docker-compose)` 첫 trigger 3m 16s 후 FAIL — root cause = [`TenantProvisioningE2ETest`](../../projects/global-account-platform/tests/e2e/src/test/java/com/example/e2e/TenantProvisioningE2ETest.java) (class-level `@Tag("smoke")`) 의 step1 (`WMS tenant provisioning: POST /internal/tenants/wms/accounts → 201 + accountId`) 이 production endpoint `POST /internal/tenants/wms/accounts` (TASK-BE-228 미구현) 의존. 5/6 test FAIL (GoldenPathE2ETest 1개만 GREEN). assertion message 자체 가이드: `"If 404: TASK-BE-228 (account-service tenant provisioning API) is not implemented."`

이는 8번째 archaeological cycle 가 아닌 **smoke @Tag 분류 mistake** — `TenantProvisioningE2ETest` 가 v2-planned production endpoint 의존 (BE-228 ready 미진입) 상태에서 ADR-MONO-010 의 smoke rubric (fast-feedback / 의존 0 / stable) 미달 ↔ MONO-078 Phase 2 분류 시 smoke 로 marked. PR-time first-call validation 가속이 이를 즉시 surface — MONO-088 의 의도 그대로 작동.

본 task = 분류 fix. 옵션 검토 후 가장 적합한 fix 적용:

- **Option A (권장)**: `TenantProvisioningE2ETest` class-level annotation `@Tag("smoke")` → `@Tag("full")` 재분류. ADR-MONO-010 의 smoke rubric (fast-feedback / 의존 0 / stable) 미달 → full 분류가 정확. nightly 만 fail (yellow flag), PR-time blocker 해소.

- **Option B**: `@Disabled("until TASK-BE-228")` 추가 (smoke 분류 유지). BE-228 closure 시 enable. trade-off: yellow flag 영구화 가능성, ADR-MONO-010 D5 의 `@Disabled` 정책 검토 필요.

- **Option C**: TASK-BE-228 우선 구현 (substantial production work, account-service tenant provisioning API). 별 task scope.

**Option A 선택**: ADR-MONO-010 D1 의 smoke rubric S1 ("test 가 production endpoint 의 stable working implementation 의존") 위배. full 분류가 정확한 home. BE-228 closure 시 다시 smoke 로 promote 가능.

provenance: TASK-MONO-088 close 시점 (commit `d3bb7d49`) 의 INDEX entry § Verification "Follow-up = TASK-MONO-089".

---

# Scope

## In Scope

### A. `TenantProvisioningE2ETest` class-level @Tag 재분류

[`projects/global-account-platform/tests/e2e/src/test/java/com/example/e2e/TenantProvisioningE2ETest.java`](../../projects/global-account-platform/tests/e2e/src/test/java/com/example/e2e/TenantProvisioningE2ETest.java) L51:

```java
@Tag("smoke")   ← @Tag("full") 로 변경
class TenantProvisioningE2ETest extends E2EBase {
```

### B. ADR-MONO-010 § 1.2 distribution 정정 (option C-1 audit-only)

ADR 본문 § 1.2 의 표 ("gap smoke 2 / full 3") 가 본 fix 후 "gap smoke 1 / full 4" 로 변경. **ADR 본문 직접 수정 안 함** (acceptance 후 변경 = 신뢰성 손상, memory `project_e2e_3phase_strategy_complete.md` § "ADR 정정 패턴 — option C-1"). 대신:

- 본 task body 의 § Implementation Notes 에 distribution 갱신 명시.
- INDEX outcome line 에 "ADR-MONO-010 § 1.2 audit-trail 누적 7차" 명시.

### C. CI 자연 검증

push to main 후 자체 trigger 의 `gap-platform-e2e-smoke` job 이 GREEN (or wall-clock 측정). full 분류 후 nightly `gap-e2e-full` 에서 `TenantProvisioningE2ETest` 가 nightly target 이 되어 FAIL — yellow flag (BE-228 미구현이라 expected).

## Out of Scope

- TASK-BE-228 (account-service tenant provisioning API) 실제 구현 — substantial production work, 별 task.
- `TenantProvisioningE2ETest` 의 다른 step 들 (`step2 login` / `step3 JWT claims` / `step4 gateway X-Tenant-Id injection` / `step5 cross-tenant 403`) review — 동일 cascade dependency, full 분류로 일괄 이동.
- gap e2e 의 다른 smoke class (`GoldenPathE2ETest`) 검토 — first-call validation GREEN, 정확한 분류.
- nightly gap-e2e-full 의 `Dump gap service logs on failure` step cleanup (TASK-MONO-082 잔재) — 별 task 후보 (MONO-088 § Out of Scope 답습).

---

# Acceptance Criteria

### Impl PR

- [x] [`TenantProvisioningE2ETest.java`](../../projects/global-account-platform/tests/e2e/src/test/java/com/example/e2e/TenantProvisioningE2ETest.java) L51 의 `@Tag("smoke")` → `@Tag("full")`.
- [x] 1-line edit, 다른 file 변경 0.
- [x] task lifecycle ready → review (in-progress 우회, mechanical 1-line single-PR closure 패턴 — MONO-084/085/086/087/088 precedent).
- [x] [`tasks/INDEX.md`](../INDEX.md) 동기.

### CI verification

- [ ] push to main 후 자체 trigger 의 `gap-platform-e2e-smoke` job GREEN (`GoldenPathE2ETest` 1 class smoke 만).
- [ ] wall-clock 실측 자료화.
- [ ] 기존 다른 jobs 회귀 0.

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] [`tasks/INDEX.md`](../INDEX.md) ## review 제거, ## done append 1-line outcome.

---

# Related Specs

- [`docs/adr/ADR-MONO-010-e2e-tag-taxonomy.md`](../../docs/adr/ADR-MONO-010-e2e-tag-taxonomy.md) § 1.2 (gap smoke 2/full 3 → 1/4 의 audit-trail target) + § D1 (smoke rubric S1 — production endpoint stable working implementation 의존성 위배 source)
- [`docs/adr/ADR-MONO-011-nightly-full-e2e-cadence.md`](../../docs/adr/ADR-MONO-011-nightly-full-e2e-cadence.md) § 6.2 (TASK-MONO-088 의 inherited closure)
- [`tasks/done/TASK-MONO-088-gap-pr-time-smoke-job.md`](../done/TASK-MONO-088-gap-pr-time-smoke-job.md) (provenance — first-call validation FAIL finding)
- [`projects/global-account-platform/tests/e2e/src/test/java/com/example/e2e/TenantProvisioningE2ETest.java`](../../projects/global-account-platform/tests/e2e/src/test/java/com/example/e2e/TenantProvisioningE2ETest.java) (target file)

---

# Related Contracts

본 task = e2e test classification fix. HTTP API / event payload contract 변경 0. `POST /internal/tenants/wms/accounts` (TASK-BE-228) production endpoint 자체는 별 task scope.

---

# Target Service

`projects/global-account-platform/tests/e2e/` (gap e2e module). 1-line edit.

---

# Architecture

본 task = ADR-MONO-010 smoke/full rubric 의 retrospective enforcement. Phase 2 (MONO-078) 분류 시점 의 `TenantProvisioningE2ETest` smoke 분류가 rubric S1 위배 — TASK-MONO-088 PR-time first-call validation 가 이를 surface. fix = full 로 demotion, BE-228 closure 시 smoke promotion 가능.

분류 변경 후 gap e2e distribution:

| 분류 | 전 (MONO-078) | 후 (본 task) |
|---|---|---|
| smoke | GoldenPathE2ETest, TenantProvisioningE2ETest | GoldenPathE2ETest |
| full | CrossServiceBulkLockE2ETest, DlqHandlingE2ETest, RefreshReuseDetectionE2ETest | + TenantProvisioningE2ETest |
| **총** | smoke 2 / full 3 | smoke 1 / full 4 |

ADR-MONO-010 § 1.2 의 분포 표가 source-of-truth 였으나 본 fix 후 변경 → option C-1 audit-trail (INDEX outcome) 로 갱신.

---

# Implementation Notes

## 1-line edit

`TenantProvisioningE2ETest.java` L51:

```java
@Tag("smoke")
```

→

```java
@Tag("full")
```

이 외 file 변경 0.

## ADR-MONO-010 § 1.2 distribution audit-trail (option C-1)

ADR 본문 § 1.2 의 표 ("gap smoke 2 / full 3") 는 acceptance 시점 (2026-05-13) snapshot. 본 fix 후 분포 변경 = post-acceptance correction. ADR 본문 직접 수정 안 함 (memory `project_e2e_3phase_strategy_complete.md` § "ADR 정정 패턴 — option C-1" 답습).

INDEX outcome line 에 "ADR-MONO-010 § 1.2 audit-trail 누적 7차" 명시 (TASK-MONO-080/081/082 + BE-278/279 + MONO-088 + 본 task = 7 누적).

## D4 churn impact

- 1 file gap project touch (e2e test code).
- 1-line edit.
- ADR-MONO-003a § D1.3 IN-scope (cross-cutting test policy 인접 — MONO-088 와 동일 분류). D4 OVERRIDE 적용.

---

# Edge Cases

- `TenantProvisioningE2ETest` 의 5 step 이 cascade dependency (step1 fail → step2-5 fail). class-level @Tag 변경이라 step-level @Tag 검토 불필요.
- nightly gap-e2e-full 의 다음 run 에서 `TenantProvisioningE2ETest` 가 full 분류로 실행 → BE-228 미구현이라 FAIL 예상. nightly badge yellow → BE-228 closure 까지 yellow flag. ADR-MONO-011 § D4 의 nightly failure handling policy 자연 적용 ("triaged within 1 business day").
- ADR-MONO-010 § 1.2 의 fan/scm/wms 분포는 무변경.

---

# Failure Scenarios

- 본 fix 후 `gap-platform-e2e-smoke` PR-time job 이 또 fail → 또 다른 smoke class (`GoldenPathE2ETest`) 의 분류 mistake 가능성. 그러나 first-call validation FAIL 시 동일 패턴 (별 follow-up task) 답습.
- nightly gap-e2e-full 의 `TenantProvisioningE2ETest` fail 이 yellow flag 영구화 → BE-228 우선순위 검토 (별 task scope).

---

# Test Requirements

- push to main 후 `gap-platform-e2e-smoke` self-CI GREEN (`GoldenPathE2ETest` 1 class 만 실행).
- production code = 0 (e2e test classification only).
- D4 OVERRIDE: ADR-MONO-003a § D1.3 적용.

---

# Definition of Done

### Impl PR

- [ ] AC 완료.
- [ ] task lifecycle ready → review.

### CI verification

- [ ] `gap-platform-e2e-smoke` self-CI GREEN.
- [ ] 다른 jobs 회귀 0.

### Close chore PR

- [ ] review → done, [`tasks/INDEX.md`](../INDEX.md) 동기.

---

# Provenance

- TASK-MONO-088 (commit `57254783` impl + `d3bb7d49` close, 2026-05-14) PR-time first-call validation 첫 trigger FAIL finding.
- ADR-MONO-010 § 1.2 distribution (gap smoke 2/full 3 — acceptance snapshot) audit-trail.
- ADR-MONO-010 § D1 smoke rubric S1 retrospective enforcement.
- 메모리 `project_e2e_3phase_strategy_complete.md` § "ADR 정정 패턴 — option C-1" 답습.
- D4 OVERRIDE: ADR-MONO-003a § D1.3 (cross-cutting test policy 인접) 적용.
- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (1-line mechanical edit).
