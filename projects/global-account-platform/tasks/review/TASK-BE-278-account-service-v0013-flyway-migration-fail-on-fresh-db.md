# Task ID

TASK-BE-278

# Title

`account-service` V0013 Flyway migration ("rebuild account roles composite pk") nightly docker-compose fresh-DB fail (TASK-MONO-082 cycle 6 spawn)

# Status

review

# Owner

global-account-platform

# Task Tags

- be
- account-service
- flyway
- migration
- gap-e2e
- fix
- impl

---

# Goal

TASK-MONO-082 (PR #442, 2026-05-13 cycle 3 fix = auth-service JWT keys mount) 머지 후 push-to-main trigger 자동 발동된 다섯 번째 nightly run `25776967635` (push `bdc00b40`, 2026-05-13 03:45 UTC) 에서 **gap-e2e-full 16m 54s fail (cycle 6, account-service V0013 Flyway migration validation fail)**. cycle 3 의 auth-service JWT keys 부재는 작동 검증 완료 (auth `Up (healthy)`), 그러나 account-service 가 새로운 boot fail mode 노출:

```
account-service Application run failed:
org.flywaydb.core.api.exception.FlywayValidateException: Validate failed: Migrations have failed validation
Detected failed migration to version 0013 (rebuild account roles composite pk).
Please remove any half-completed changes then run repair to fix the schema history.
```

cycle 5 의 docker compose ps 결과:

- auth-service `Up 10 minutes (healthy)` ✅ (cycle 3 fix 검증 완료)
- security-service `Up 10 minutes (healthy)` ✅
- admin-service `Up 10 minutes (healthy)` ✅
- gateway-service `Up 10 minutes (healthy)` ✅
- **account-service `Exited (1) 4 minutes ago`** ❌ ← cycle 6

각 ComposeFixture HEALTH_TIMEOUT (5min) 만료 시 docker-compose recreate → account-service 재부팅 → 같은 validation fail. 3 full class × 5min = 16m 54s 총 wall-clock.

## 진단 — 두 번째/세 번째 부팅 시점의 message 만 보임

cycle 5 의 Phase 0 진단 step (`Dump gap service logs on failure`) 의 `docker compose logs --tail=200` 는 **마지막 boot 시도의 200 line** 만 capture. account-service 의 3 boot 시도 중 2번째/3번째만 보이고 첫 번째 시도의 SQL error 는 보이지 않음. 보이는 message 는 모두 second-attempt validate fail = "Detected failed migration":

```
03:52:30 (2nd boot) — validate fail, history record 가 V0013 failed
03:57:13 (3rd boot) — 동일
```

→ **첫 번째 boot 시점 (03:46:49 ~ 03:51:55) 에서 V0013 의 실제 SQL execution fail**. 첫 boot 의 actual SQL error message 는 dump 에 없음 (recreate 로 컨테이너 destroyed).

## 가능 root cause

V0013 script (`apps/account-service/src/main/resources/db/migration/V0013__rebuild_account_roles_composite_pk.sql`) 의 5 step:

1. `ALTER TABLE accounts ADD UNIQUE INDEX uk_accounts_tenant_id_id (tenant_id, id)`
2. `CREATE TEMPORARY TABLE account_roles_backup AS SELECT FROM account_roles`
3. `DROP TABLE account_roles`
4. `CREATE TABLE account_roles (... composite PK, FK to accounts(tenant_id, id) ON DELETE CASCADE, FK to tenants ...)`
5. `INSERT INTO account_roles SELECT FROM account_roles_backup` + `DROP TEMPORARY TABLE`

가설 후보:
- **B1 (가장 유력)**: MySQL 8.0 의 implicit-commit semantics — `CREATE TEMPORARY TABLE` + DDL (DROP/CREATE TABLE) 각각이 implicit commit 을 일으켜 Flyway 의 grouped migration transaction 보장 깨짐. 어느 한 step fail 이면 이전 step 은 committed, history 는 "failed" record.
- **B2**: V0013 step 4 의 composite FK `FOREIGN KEY (tenant_id, account_id) REFERENCES accounts(tenant_id, id)` 가 MySQL 8.0 의 InnoDB FK validation rule 위반 (정확한 error 필요 — 첫 boot log dump 필요).
- **B3**: V0010 (tenant_id 추가) + V0012 (account_roles 생성) 의 잔재가 V0013 의 가정과 어긋남. 예: V0012 의 fk_account_roles_account 가 V0013 step 3 DROP TABLE 을 막거나, V0012 의 `assigned_at` (V0013 backup query 의 SELECT column) 이 실제 V0012 와 불일치.
- **B4**: PR-time IT 환경 (Testcontainers MySQL) 와 nightly docker-compose 환경 (mysql:8.0 + custom command + custom init.sh) 의 config 차이. nightly 의 `--log-bin-trust-function-creators=1 --max_connections=500` + `/docker-entrypoint-initdb.d/01-init.sh` 가 V0013 의 가정 깨뜨림.

## fix

본 task in-progress 첫 step = **Phase 0 진단 강화**:

1. 옵션 a: `Dump gap service logs on failure` step 의 dump 시점을 첫 fail 시점 (ComposeFixture HEALTH_TIMEOUT 만료 직후) 로 이동. recreate 후 두 번째 시도 시점에선 늦음.
2. 옵션 b: `docker compose logs --tail=500` 또는 `--since=<timestamp>` 로 range 넓히기 — 첫 boot 의 stack trace 가 200 line 안에 있을 가능성 검증.
3. 옵션 c: nightly job 에 `account-service` 의 flyway_schema_history table SELECT 추가 (mysql exec) — V0013 record 의 actual exception message 직접 확인.
4. 옵션 d: local 에서 `docker compose -f docker-compose.e2e.yml -p gap-e2e up account-service` 직접 실행해 SQL error 재현.

옵션 d (local 재현) 이 가장 빠르고 정확. nightly cycle 의존 안 함.

Phase 1 fix = root cause 별 minimal:

- B1 채택 시: V0013 script 의 transaction-safety 개선 — TEMPORARY TABLE 패턴 제거 (fresh DB 에 account_roles 가 비어 있으므로 data 보존 불필요), DROP + CREATE 만으로 단순화. 또는 V0013 을 V0013a (cleanup) + V0013b (recreate) 로 split.
- B2 채택 시: composite FK definition 조정 (예: 명시적 `ON UPDATE` clause, INDEX 순서 변경 등).
- B3 채택 시: V0012 의 FK 를 V0013 step 1 직후 명시적으로 DROP.
- B4 채택 시: docker-compose.e2e.yml 의 mysql config 또는 init.sh 조정 (production code 0 영역, mono-082 의 옵션 C-1 패턴 답습 가능).

provenance: TASK-MONO-082 (PR #442) partial close 후 cycle 6 잔존 fix. ADR-MONO-011 § D4 "triage within 1 business day" 정책 네 번째 발동.

---

# Scope

## In Scope

### A. Phase 0 진단 — 첫 boot 시점의 actual SQL error 식별

옵션 a/b/c/d 중 한 가지 또는 조합. 가장 빠른 path = local 재현 (옵션 d).

### B. Phase 1 fix — root cause 별 minimal

식별된 root cause 별:
- B1 (transaction-safety): V0013 script 수정 — TEMPORARY TABLE 제거 + 단순 DROP + CREATE.
- B2 (composite FK): FK definition 조정.
- B3 (V0012 잔재): V0013 step 1 직후 명시적 FK DROP.
- B4 (docker-compose mysql config): docker-compose.e2e.yml 조정.

### C. 재검증

본 PR 머지 후 push-to-main trigger → 다음 nightly run 의 gap-e2e-full GREEN within 60min 예상.

### D. PR-time IT 보존

Spring Boot test slice (Testcontainers MySQL) 의 V0013 migration 도 동일하게 동작해야 함. PR-time gap-integration-tests CI job (`Integration (global-account-platform)`) 가 36s 안에 PASS 유지.

## Out of Scope

- gap-e2e-full 의 ComposeFixture / HEALTH_TIMEOUT 자체 수정 (TASK-MONO-082 scope).
- 다른 4 GAP service (auth/security/admin/gateway) production code 변경.
- 다른 backend full job (wms/fan/scm) 영향.
- ADR-MONO-011 본문 직접 수정 (option C-1 audit-only 누적).

---

# Acceptance Criteria

### Impl PR

- [ ] Phase 0 진단 — 첫 boot 의 actual SQL error 식별 + task body 에 명시.
- [ ] Phase 1 fix — 식별된 root cause 별 minimal change.
- [ ] 본 PR 머지 후 다음 nightly run 의 gap-e2e-full GREEN within 60min budget.
- [ ] PR-time `Integration (global-account-platform)` CI job 가 동일 PASS 유지 (Testcontainers MySQL).
- [ ] 다른 backend full job (wms/fan/scm) + frontend-e2e-fullstack + ecommerce-boot-jars-nightly 영향 0.
- [ ] task lifecycle ready → in-progress → review.
- [ ] gap tasks/INDEX.md 동기.

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] gap tasks/INDEX.md ## review 제거, ## done append 1-line outcome.

---

# Related Specs

- `tasks/done/TASK-MONO-082-gap-e2e-nightly-health-timeout.md` (root, 직접 선행, partial close).
- `tasks/done/TASK-MONO-081-gap-e2e-nightly-boot-jars.md` (root, cycle 2 fix).
- `tasks/done/TASK-MONO-080-gap-e2e-nightly-fix.md` (root, cycle 1 fix).
- `tasks/done/TASK-MONO-079-nightly-full-e2e-impl.md` (root, Phase 3 impl 첫 PR).
- `docs/adr/ADR-MONO-011-nightly-full-e2e-cadence.md` (root, § D5 정정 audit 누적).
- `projects/global-account-platform/apps/account-service/src/main/resources/db/migration/V0013__rebuild_account_roles_composite_pk.sql` (수정 후보).
- `projects/global-account-platform/apps/account-service/src/main/resources/db/migration/V0012__create_account_roles.sql` (V0013 의 선행).
- `projects/global-account-platform/apps/account-service/src/main/resources/db/migration/V0010__add_tenant_id_to_domain_tables.sql` (V0013 step 1 의 선행).
- `projects/global-account-platform/docker-compose.e2e.yml` (mysql 컨테이너 config — B4 후보 시 수정).
- `projects/global-account-platform/docker/mysql/init.sh` (mysql init — B4 후보).
- 다섯 번째 nightly run `25776967635` (실측 fail 데이터 source).

# Related Skills

N/A — SQL migration script + 진단.

---

# Related Contracts

None.

---

# Target Service

`account-service` (production code 영역, V0013 migration script).

---

# Architecture

account-service 의 schema migration 영역. Hexagonal architecture 의 infrastructure layer (Flyway).

---

# Implementation Notes

## Local 재현 (옵션 d) 권장

```bash
cd projects/global-account-platform
docker compose -f docker-compose.e2e.yml -p gap-e2e-debug down -v
docker compose -f docker-compose.e2e.yml -p gap-e2e-debug build account-service
docker compose -f docker-compose.e2e.yml -p gap-e2e-debug up -d mysql kafka redis
# wait for mysql healthy
docker compose -f docker-compose.e2e.yml -p gap-e2e-debug up account-service
# observe Flyway migrate output — first attempt 의 actual SQL error
```

이 path 가 가장 빠르고 nightly cycle 의존 안 함. 단점 = Rancher Desktop CLI proxy / Docker Desktop quirk 의존 (project memory 참조).

## V0013 의 TEMPORARY TABLE 패턴 검토

본 script 의 step 2 (CREATE TEMPORARY TABLE) + step 5 (INSERT FROM backup) 는 V0012 era 의 production 데이터 보존 목적. comment 9-13 명시: "Copying existing rows preserves any V0012-era assignments (none in production yet — account_roles was introduced in TASK-BE-231 alongside the provisioning API; only the WMS demo tenant uses it so far)".

**즉 production 에서도 데이터는 거의 없음**. fresh DB (nightly) 에서는 0 row. 따라서 단순화 가능:

```sql
-- V0013 단순화 (transaction-safety 우선)
ALTER TABLE accounts ADD UNIQUE INDEX uk_accounts_tenant_id_id (tenant_id, id);

-- account_roles 가 비어 있다고 가정 (production = 1 tenant 의 일부 row, dev/CI = 0)
DROP TABLE account_roles;
CREATE TABLE account_roles ( ... composite PK, composite FK, ... );
-- INSERT 생략 — production cutover 시 별도 backfill script
```

다만 production 의 WMS demo tenant 의 데이터 보존이 필요한 경우 → 별도 V0013a (data backup to staging table) + V0013b (rebuild) + V0013c (restore) 시리즈로 split.

## ADR-MONO-011 § D5 audit-trail 누적 4차

ADR 본문 직접 수정 안 함. 본 task body + INDEX outcome 에 audit-trail. § D5 정정 = "gap e2e suite 가 CI 첫 정직 호출 시 마다 historical 누락의 새 layer 노출": cycle 1 (settings.gradle) + 2 (boot jars) + 3 (auth JWT keys) + 6 (V0013 migration fresh-DB fail). 4 cycle 누적.

## 메타 발견 누적

TASK-MONO-080/081/082 의 메타 — ComposeFixture self-managed mode 가 CI 에서 한 번도 호출된 적 없는 historical 누락 — 의 새 layer. PR-time IT (Testcontainers) 가 production-grade 검증 아니었음. **장기적 권장**: nightly 의 GREEN 달성 후 PR-time GAP smoke job 도 ComposeFixture path 호출 (ADR-MONO-011 § 6.2 outstanding, ADR-MONO-010 § D5 step 5 ProductReady 후속). 본 task 범위 밖.

---

# Edge Cases

- V0013 fix 후 PR-time IT 가 영향받음: Testcontainers MySQL 도 fresh DB 라 동일 fix 가 작동해야 함.
- V0012 ← V0013 동시 변경 필요 시: V0014/V0015 (tenant seed) 의 V0013 dependency 도 검토.
- WMS demo tenant 의 account_roles 데이터 보존 필요: V0013a/b/c split 으로 backfill 분리.
- 다른 service (auth/security/admin) 의 V0013 동일 패턴 존재: 동일 fix 패턴 적용 후보 (검토 필요).
- cycle 7 발생: 새 root cause → 별도 task author.

---

# Failure Scenarios

- Phase 0 진단 step (옵션 a/b/c) 도 첫 boot SQL error 식별 실패: 옵션 d (local 재현) 강제.
- V0013 fix 가 PR-time IT 깨뜨림: rollback + 다른 root cause 가설 (B2/B3/B4) 재검토.
- nightly GREEN 후 cycle 7 발견: 별도 task author 패턴 답습.
- production migration history 의 V0013 record 와 호환성 fail: V0013a/b 패턴 + flyway repair migration.

---

# Test Requirements

- gap-e2e-full GREEN within 60min budget.
- gap-integration-tests PR-time job 동일 PASS (PR-time IT 보존).
- 3 full class PASS.
- 다른 backend full job + frontend-e2e-fullstack 영향 0.

---

# Definition of Done

### Impl PR

- [ ] AC 완료, 재검증 GREEN.
- [ ] task lifecycle ready → in-progress → review.

### Close chore PR

- [ ] review → done, gap tasks/INDEX.md 동기.

---

# Provenance

- TASK-MONO-082 (PR #442) partial close 후 cycle 6 fix.
- 다섯 번째 nightly run `25776967635` (push `bdc00b40`, 2026-05-13 03:45 UTC) 의 gap-e2e-full 16m 54s fail 진단.
- ADR-MONO-011 § D4 "triage" 정책 네 번째 발동.
- ADR-MONO-011 § D5 audit-trail 누적 4차 (option C-1, TASK-080/081/082 패턴 답습).
- 직접 선행 = TASK-MONO-082 (PR #442) + TASK-MONO-081 (PR #439) + TASK-MONO-080 (PR #437) + TASK-MONO-079 (PR #435).

분석=Opus 4.7 / 구현 권장=Opus 4.7 (Phase 0 진단 + V0013 SQL archaeological inspection, mechanical 아닌 깊은 분석 필요).
