# Task ID

TASK-BE-279

# Title

`CrossServiceBulkLockE2ETest.seedAccount` — direct-SQL seed 에 `tenant_id` 명시 (TASK-BE-278 cycle 7 spawn)

# Status

ready

# Owner

global-account-platform

# Task Tags

- be
- account-service
- e2e
- test
- gap-e2e
- fix
- impl

---

# Goal

TASK-BE-278 (PR #444, cycle 6 = CREATE TEMPORARY TABLES privilege fix) 머지 후 push-to-main trigger 자동 발동된 여섯 번째 nightly run `25778172254` (push `6d2bb874`, 2026-05-13 04:24 UTC) 에서 **gap-e2e-full 3m 8s fail (cycle 7)**.

**중대한 진척**: 
- 이전 cycle 3/5/6 의 16-17min ComposeFixture HEALTH_TIMEOUT 패턴이 사라짐 (V0013 PASS + ComposeFixture + 5 service healthy 정상 검증).
- 3 e2e test 가 실제로 실행 → 2 PASS (DlqHandling + RefreshReuseDetection), 1 FAIL (CrossServiceBulkLock).
- BUILD FAILED in 2m 25s — setup OK, test logic 1개 fail.

cycle 7 root cause:

```
CrossServiceBulkLockE2ETest > bulk_lock_propagates_to_account_and_security_dbs FAILED
    java.sql.SQLException: Field 'tenant_id' doesn't have a default value
        at CrossServiceBulkLockE2ETest.seedAccount(CrossServiceBulkLockE2ETest.java:90)
        at CrossServiceBulkLockE2ETest.bulk_lock_propagates_to_account_and_security_dbs(...:45)
```

`seedAccount` 의 direct-SQL INSERT 가 `tenant_id` column 명시 안 함. V0010 (TASK-BE-228) 이 `accounts.tenant_id` 를 `NOT NULL DEFAULT 'fan-platform'` 추가, V0011 이 default 제거 → strict NOT NULL. test code 가 V0011 이후 schema 와 불일치.

```java
// before
"INSERT INTO accounts(id,email,status,created_at,updated_at,version) VALUES(?,?,?,?,?,0)"

// fix
"INSERT INTO accounts(id,tenant_id,email,status,created_at,updated_at,version) VALUES(?,?,?,?,?,?,0)"
// ps.setString(2, "fan-platform");  // V0009 seeded tenant, V0010-era historical default
```

본 cycle 7 = **test code 영역 (production code 무관)**. `@Tag("full")` 로 nightly-only e2e test 라 PR-time CI 영향 0 (Testcontainers IT 무관).

provenance: TASK-BE-278 의 partial close (cycle 6 verified) 후 cycle 7 잔존 fix. ADR-MONO-011 § D4 "triage" 정책 다섯 번째 발동.

---

# Scope

## In Scope

### A. `CrossServiceBulkLockE2ETest.seedAccount` 의 INSERT 에 `tenant_id` column + 'fan-platform' value 추가

`tests/e2e/src/test/java/com/example/e2e/CrossServiceBulkLockE2ETest.java:82-90`. 1 line change (statement + 1 ps.setString).

### B. inline comment 으로 V0010/V0011 schema evolution + 'fan-platform' tenant 선택 근거 명시

V0009 (tenants seed) + V0010 (NOT NULL DEFAULT 'fan-platform') + V0011 (default 제거) 의 migration history. bulk-lock flow 는 tenant-agnostic 이므로 historical default 안전.

### C. 재검증

본 PR 머지 후 push-to-main trigger → 다음 nightly run 의 gap-e2e-full GREEN within 60min 예상 (3 e2e test 모두 PASS).

## Out of Scope

- production code 변경 (account-service application yml / src 무관).
- V0010/V0011/V0014/V0015 migration script 변경.
- 다른 e2e test 변경 (DlqHandling + RefreshReuseDetection 이미 PASS).
- TASK-BE-278 의 cycle 6 fix 변경.
- ComposeFixture / nightly-e2e.yml / docker-compose.e2e.yml 변경 (cycle 1-6 scope, 모두 closed).
- TASK-MONO-082 / 081 / 080 영향.

---

# Acceptance Criteria

### Impl PR

- [ ] `tests/e2e/src/test/java/com/example/e2e/CrossServiceBulkLockE2ETest.java` 의 `seedAccount` INSERT 에 `tenant_id` column + 'fan-platform' value 추가.
- [ ] inline comment 에 V0010/V0011 migration history + TASK-BE-279 명시.
- [ ] 본 PR 머지 후 다음 nightly run 의 gap-e2e-full GREEN within 60min budget.
- [ ] 3 full class (CrossServiceBulkLock + DlqHandling + RefreshReuseDetection) 모두 PASS.
- [ ] 다른 backend full job (wms/fan/scm) + frontend-e2e-fullstack + ecommerce-boot-jars-nightly 영향 0.
- [ ] PR-time `Integration (global-account-platform)` CI job 동일 PASS 유지.
- [ ] Production code 0 (test code only).
- [ ] task lifecycle ready → in-progress → review.
- [ ] gap tasks/INDEX.md 동기.

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] gap tasks/INDEX.md ## review 제거, ## done append 1-line outcome (impl PR # + commit + 다음 nightly run wall-clock + GREEN 검증 + Phase 3 5/5 완성 마지막 단추).

---

# Related Specs

- `tasks/done/TASK-BE-278-account-service-v0013-flyway-migration-fail-on-fresh-db.md` (직접 선행, partial close).
- Root `tasks/done/TASK-MONO-082-gap-e2e-nightly-health-timeout.md` (cycle 3 root cause).
- Root `tasks/done/TASK-MONO-081-gap-e2e-nightly-boot-jars.md` (cycle 2).
- Root `tasks/done/TASK-MONO-080-gap-e2e-nightly-fix.md` (cycle 1).
- Root `tasks/done/TASK-MONO-079-nightly-full-e2e-impl.md` (Phase 3 impl 첫 PR).
- `docs/adr/ADR-MONO-011-nightly-full-e2e-cadence.md` (§ D5 audit-trail 누적 5차).
- `projects/global-account-platform/tests/e2e/src/test/java/com/example/e2e/CrossServiceBulkLockE2ETest.java` (수정 대상).
- `projects/global-account-platform/apps/account-service/src/main/resources/db/migration/V0009__create_tenants.sql` (fan-platform tenant seed).
- `projects/global-account-platform/apps/account-service/src/main/resources/db/migration/V0010__add_tenant_id_to_domain_tables.sql` (accounts.tenant_id NOT NULL DEFAULT 'fan-platform').
- `projects/global-account-platform/apps/account-service/src/main/resources/db/migration/V0011__remove_tenant_id_defaults.sql` (DEFAULT 제거).
- 여섯 번째 nightly run `25778172254` (실측 fail 데이터 source).

# Related Skills

N/A — test code 1-line edit.

---

# Related Contracts

None.

---

# Target Service

`tests/e2e` (test code only).

---

# Architecture

E2E test seed code 의 schema sync.

---

# Implementation Notes

## fan-platform tenant 선택 근거

V0009 가 `INSERT INTO tenants VALUES ('fan-platform', ...)` 로 seed. V0010 이 `accounts.tenant_id NOT NULL DEFAULT 'fan-platform'` 추가 (V0011 이 default 제거 전 historical default). bulk-lock flow 는 tenant-agnostic — admin command 가 임의 account 잠금이므로 어떤 tenant 든 무관. V0014 (ecommerce) / V0015 (scm) tenant 도 사용 가능하지만 'fan-platform' 이 V0010-era 의 implicit default 라 가장 안전.

## ADR-MONO-011 § D5 audit-trail 누적 5차 (option C-1)

ADR 본문 직접 수정 안 함. 본 task body + INDEX outcome 에 audit-trail. § D5 정정 = "gap e2e suite 의 historical 누락 5 layer" 명시: (1) settings.gradle 미등록 + (2) boot jars 부재 + (3) JWT keys 부재 + (4) MySQL CREATE TEMPORARY TABLES privilege 부재 + (5) e2e test seed code 의 schema 불일치 (tenant_id 누락).

## 메타 발견 누적 5 cycle

TASK-MONO-080/081/082 + TASK-BE-278/279 = 모두 gap e2e suite 가 CI 첫 정직 호출 시마다 historical 누락의 새 layer 노출. **장기 권장 = nightly 외 PR-time gap smoke job 신설** (ADR-MONO-011 § 6.2, ADR-MONO-010 § D5 step 5 ProductReady 후속). 본 task 의 GREEN 달성 후 follow-up 후보.

---

# Edge Cases

- 다른 e2e test 가 같은 패턴 (direct-SQL seed without tenant_id) 일 가능성: `DlqHandling` 과 `RefreshReuseDetection` 은 이번 cycle 7 에서 PASS 했으므로 영향 없음. 다만 future test 추가 시 동일 패턴 주의.
- 'fan-platform' tenant 가 향후 archive/delete 될 가능성: V0009 seed 영구. test 가 'ecommerce' 또는 'scm' 으로 옮길 future fix 후보.
- cycle 8 발생: 새 root cause → 별도 task author.

---

# Failure Scenarios

- 본 fix 후 다른 column NOT NULL 누락 노출 (예: V0011 phase 의 다른 column): hypothesis 동일, 동일 패턴 fix.
- 'fan-platform' tenant 가 다른 migration 으로 modified 됨: test seed 가 강제로 'fan-platform' 사용 → migration history 검토 필요.
- 본 fix 후 다른 e2e test 도 fail: cycle 8 author 패턴 답습.

---

# Test Requirements

- gap-e2e-full GREEN within 60min budget.
- 3 full class (CrossServiceBulkLock + DlqHandling + RefreshReuseDetection) 모두 PASS.
- PR-time `Integration (global-account-platform)` 동일 PASS.
- Production code 0.

---

# Definition of Done

### Impl PR

- [ ] AC 완료, 재검증 GREEN.
- [ ] task lifecycle ready → in-progress → review.

### Close chore PR

- [ ] review → done, gap tasks/INDEX.md 동기.
- [ ] **Phase 3 5/5 완성** = gap-e2e-full 첫 GREEN — 4 backend full job + frontend-e2e-fullstack + ecommerce-boot-jars-nightly 모두 PASS.

---

# Provenance

- TASK-BE-278 (PR #444, cycle 6 CREATE TEMPORARY TABLES privilege) partial close 후 cycle 7 fix.
- 여섯 번째 nightly run `25778172254` (push `6d2bb874`, 2026-05-13 04:24 UTC) 의 gap-e2e-full 3m 8s fail 진단 — ComposeFixture + V0013 + 5 service healthy 정상 + CrossServiceBulkLockE2ETest seed code missing tenant_id.
- ADR-MONO-011 § D4 "triage" 정책 다섯 번째 발동.
- ADR-MONO-011 § D5 audit-trail 누적 5차 (option C-1).
- 직접 선행 = TASK-BE-278 (PR #444) + TASK-MONO-082 (PR #442) + TASK-MONO-081 (PR #439) + TASK-MONO-080 (PR #437) + TASK-MONO-079 (PR #435).

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical 1-line test edit, V0010/V0011 schema evolution 근거 명확).
