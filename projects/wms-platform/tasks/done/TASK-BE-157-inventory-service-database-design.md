# Task ID

TASK-BE-157

# Title

inventory-service `database-design.md` retrospective authoring + `domain-model.md § Open Items` database-design.md ❌ → ✅ sync (BE-152 surfaced candidate 마지막 closure)

# Status

ready

# Owner

wms-platform

# Task Tags

- wms
- inventory-service
- spec
- backfill
- database
- be

---

# Goal

[TASK-BE-152](../done/TASK-BE-152-inventory-service-open-items-audit-and-list-correction.md) audit § "candidate inventory" #2 closure — `inventory-service/database-design.md` 신규 retrospective spec authoring. [TASK-BE-156](../done/TASK-BE-156-inventory-service-external-integrations-zero-state.md) 가 #1 closure 했고 본 task 가 마지막 #2 closure → BE-152 surfaced 4 candidate (#1 external-integrations.md / #2 database-design.md / #3 LOT_INACTIVE / #10 gateway route) **전체 종결**.

배경:

- inventory-service 의 actual schema = Flyway migrations 5 file (`apps/inventory-service/src/main/resources/db/migration/V1__init_inventory_tables.sql` ~ `V5__role_grants.sql`, total 398 line, 12 table/trigger). 이게 de-facto schema, 그러나 spec layer 에 schema 통합 문서 부재.
- `inventory-service/domain-model.md § Open Items` (L681-686) 가 "❌ outstanding — Flyway migrations are the de-facto schema; this file would consolidate them into a single spec doc" 로 surfaced.
- CLAUDE.md § Core Principles ("Specifications are the source of truth") + § Source of Truth Priority layer 7 (services spec) > layer 14 (existing code) — spec 부재 시 code 가 SoT 가 되는 inversion 발생 → backfill 필요.
- **portfolio 전체 첫 database-design.md 사례** — master/inbound/outbound/admin/notification/gateway 모두 미존재. sibling 답습 패턴 없음 (BE-156 이 external-integrations.md zero-state 첫 사례 패턴과 동일 구조).

본 task = **신규 retrospective spec authoring** (Flyway V1-V5 + domain-model.md 통합 reflection) + **§ Open Items 1 항목 sync** (domain-model.md L681-686 ❌ → ✅, architecture.md 는 database-design.md 미언급 — sync 대상 0). production code = 0, schema 변경 = 0 (retrospective reflection only).

---

# Scope

## In Scope

### A. 신규 `inventory-service/database-design.md`

대상 경로: `projects/wms-platform/specs/services/inventory-service/database-design.md` (신규).

구조 (Flyway V1-V5 + domain-model.md mapping 답습):

1. **헤더 + intent** — retrospective backfill scope 명시 + Flyway V1-V5 가 source-of-truth + 본 file 은 reflection (변경 없으면 Flyway 가 다음 v 추가 시 본 file 도 동기 업데이트).
2. **Schema Overview** — table inventory + 의존성 그래프 (ASCII or mermaid simple).
3. **Inventory aggregate (V1, §1 domain-model)** — table + indexes (partial-unique on `(location_id, sku_id, lot_id)` with NULL-aware key) + bucket non-neg constraint + version optimistic locking.
4. **InventoryMovement append-only ledger (V1 + V5)** — table + qty_after = qty_before + delta constraint + bucket/movement_type enum + hot-path indexes + W2 append-only 2-layer defense (V5 trigger + REVOKE).
5. **InventoryOutbox (V1, T3 outbox pattern)** — table + pending-publisher index (`WHERE published_at IS NULL`).
6. **EventDedupe (V1, T8)** — table + processed_at retention sweeper index.
7. **Reservation aggregate (V2)** — reservation + reservation_line (FK CASCADE) + state-machine SQL constraint (RESERVED/CONFIRMED/RELEASED) + released_reason enum + expiry sweeper index (`WHERE status = 'RESERVED'`).
8. **StockAdjustment + StockTransfer (V3)** — both immutable post-create + delta-nonzero + reason_code enum (TRANSFER_INTERNAL/REPLENISHMENT/CONSOLIDATION) + source ≠ target check.
9. **Master Read Model (V4)** — 3 snapshot tables (location/sku/lot) + master_version for out-of-order delivery + status enum.
10. **Append-only Enforcement Strategy (V5)** — 2-layer (trigger + REVOKE) detail + role separation prod vs local + test contract (non-superuser connection 의무).
11. **Indexing Strategy Summary** — 모든 인덱스 한 표로 (hot-path query mapping).
12. **Migration History** — V1-V5 1-line each.
13. **References**.

예상 크기: ~280-380 line.

### B. `domain-model.md § Open Items` sync

대상: `projects/wms-platform/specs/services/inventory-service/domain-model.md` L681-686.

작업:
- `❌ database-design.md — physical schema ... **Outstanding**` → `✅ [database-design.md](database-design.md) — physical schema ... authored in TASK-BE-157 (2026-05-14), retrospective Flyway V1-V5 reflection`.
- 다른 § Open Items row (3 ✅ + 1 ⚠️→✅ from BE-156) 는 변경 0.

### C. portfolio-wide gap surface (file authoring 0)

retrospective Flyway reflection 패턴이 다른 5 service (master/inbound/outbound/admin/notification/gateway) 에도 적용 가능 — INDEX.md done entry 본문 또는 task body 에 "잠재 portfolio-wide audit candidate" 만 명시 (별 task 작성 X). 본 PR scope 는 inventory only.

## Out of Scope

- 다른 service 의 database-design.md authoring (master/inbound/outbound/admin/notification/gateway 모두 별 task 후보, portfolio-wide audit).
- Flyway migration 변경 0 — schema 1-byte 도 변경 안 함. spec/markdown only.
- 신규 error code / Kafka topic / API endpoint = 0.
- `architecture.md § Open Items` 변경 — database-design.md 가 list 에 없으므로 sync 대상 0 (확인됨).

---

# Acceptance Criteria

- [ ] `projects/wms-platform/specs/services/inventory-service/database-design.md` 신규 file 존재, 약 280-380 line, 13 section 구조 (헤더 / Schema Overview / Inventory / InventoryMovement / Outbox / EventDedupe / Reservation / Adjustment+Transfer / Master Read Model / Append-only Enforcement / Indexing / Migration History / References).
- [ ] 모든 12 SQL table/trigger entity 가 file 안에 reflection (inventory, inventory_movement, inventory_outbox, inventory_event_dedupe, reservation, reservation_line, stock_adjustment, stock_transfer, location_snapshot, sku_snapshot, lot_snapshot, 2 V5 trigger).
- [ ] partial-unique 인덱스 2개 (`uq_inventory_loc_sku_lot` WHERE `lot_id IS NOT NULL` + `uq_inventory_loc_sku_no_lot` WHERE `lot_id IS NULL`) 와 NULL-aware key 의도 명시.
- [ ] V5 append-only 2-layer defense (trigger + REVOKE) 의 prod vs local role separation 명시.
- [ ] `domain-model.md § Open Items` database-design.md row ❌ → ✅ + link 활성화 + BE-157 attribution.
- [ ] References section 의 cross-link 모두 dead-reference 0 (Flyway file path 5개 + domain-model.md + architecture.md + rules/domains/wms.md + rules/traits/transactional.md + platform 관련 file).
- [ ] HARDSTOP-03 PASS — 신규 file 이 wms-specific (project-internal) 이고 shared path 진입 0.
- [ ] grep `(Open Item` projects/wms-platform/specs/services/inventory-service/ → 0 (BE-151+BE-156 outcome 유지).
- [ ] production code 변경 = 0, Flyway SQL 변경 = 0 (git diff `--stat` 으로 spec + INDEX + task file 만 변경 확인).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md` (`domain: wms`, `traits: [transactional, integration-heavy]`), then load `rules/common.md` + `rules/domains/wms.md` + `rules/traits/transactional.md` + `rules/traits/integration-heavy.md`. Service Type 은 `inventory-service/architecture.md § Identity` 의 `rest-api` (primary) + `event-consumer` (consumer path).

- `projects/wms-platform/specs/services/inventory-service/domain-model.md` — § 1-8 entity 정의 (§ Open Items 정정 대상 L681-686)
- `projects/wms-platform/specs/services/inventory-service/architecture.md` — § Architecture Style (Hexagonal), § Dependencies
- `projects/wms-platform/apps/inventory-service/src/main/resources/db/migration/V1__init_inventory_tables.sql` — 156 line, 4 table
- `projects/wms-platform/apps/inventory-service/src/main/resources/db/migration/V2__init_reservation_tables.sql` — 60 line, 2 table
- `projects/wms-platform/apps/inventory-service/src/main/resources/db/migration/V3__init_adjustment_transfer_tables.sql` — 69 line, 2 table
- `projects/wms-platform/apps/inventory-service/src/main/resources/db/migration/V4__init_master_readmodel.sql` — 58 line, 3 table
- `projects/wms-platform/apps/inventory-service/src/main/resources/db/migration/V5__role_grants.sql` — 55 line, trigger + REVOKE
- `rules/domains/wms.md` — W1 (Inventory bounded context), W2 (append-only Movement ledger)
- `rules/traits/transactional.md` — T3 (outbox), T8 (event-dedupe)
- `platform/architecture.md` — system-level architecture

# Related Skills

- `.claude/skills/INDEX.md` — spec authoring (해당 시)

---

# Related Contracts

- N/A — schema spec authoring 만. 신규 API/event/webhook contract = 0.

---

# Target Service

- `inventory-service`

---

# Edge Cases

1. **Flyway 가 향후 V6 추가 시 본 file 의 drift** — retrospective reflection 이라 Flyway 가 다음 migration 추가 시 본 file 도 동기 업데이트 의무. § Migration History section 의 "Update when V6+ lands" inline 명시 + future task pattern (TASK-BE-N+1 형식) reference 포함.
2. **partial-unique index NULL semantics 가 sibling DB engine 에서 다를 수 있음** — 본 file 은 PostgreSQL 14+ 가정 (실제 production target). NULL ≠ NULL 동작 + WHERE 조건 partial-unique 가 PostgreSQL native. MySQL/SQLite portability 는 v1 out-of-scope (배포 target 확정 = PostgreSQL).
3. **V5 trigger 가 ROLE-based REVOKE 와 redundant** — local docker-compose 에서는 application role = table owner 이라 REVOKE no-op (PostgreSQL owner bypass GRANT/REVOKE). prod 에서는 inventory_owner DDL role + runtime application role 분리 → REVOKE 실효성. 본 file 의 § 10 Append-only Enforcement Strategy 에 명시 (V5 SQL 의 inline 주석과 byte-identical 의도 보존).
4. **transactional T1-T8 와 W1/W2 cross-reference dense** — spec 본문에 모든 rule citation 을 inline 으로 박으면 noise. 핵심 invariant 만 inline + § References 에 catalog.
5. **schema diagram 형식** — ASCII art vs mermaid? sibling 답습 패턴 없음. domain-model.md L251-353 의 mermaid 사용 사례가 inventory-service 내 existing precedent — mermaid 답습 가능 (단순 box-arrow only).

---

# Failure Scenarios

1. **Flyway 와 본 file 의 byte-level inconsistency** — table 컬럼 이름 / 타입 / 제약 조건 / index name 등 모두 byte-identical 검증 필수. 각 section 의 SQL DDL snippet 은 Flyway 파일에서 그대로 복사 (변경 0).
2. **§ Indexing Strategy Summary table 의 stale index list** — Flyway 의 모든 `CREATE INDEX` + `CREATE UNIQUE INDEX` (V1-V4 합 12-15개) 가 한 표에 catalog. grep `CREATE.*INDEX` 결과와 표 row count match 검증.
3. **partial-unique 인덱스 의도 잘못 transcribe** — `WHERE lot_id IS NOT NULL` (LOT-tracked) vs `WHERE lot_id IS NULL` (non-LOT-tracked) 의 두 인덱스 의도 = NULL-safe natural key 구현. spec 본문에 "PostgreSQL treats NULL as not-equal-to-NULL" 명시 + NULL sentinel 회피 의도 explain.
4. **CRLF vs LF on Windows** — sibling external-integrations.md (BE-156) 답습 — LF 으로 write. 작성 후 hook 검증.
5. **path-filter (TASK-MONO-074/075) markdown-only** — wms-spec markdown only 라 ~1 PASS + 15 SKIP 예상 (BE-156 결과 답습).

---

# Validation Plan

- `ls projects/wms-platform/specs/services/inventory-service/database-design.md` → file 존재 확인.
- `wc -l projects/wms-platform/specs/services/inventory-service/database-design.md` → 280-380 line 범위 확인.
- 모든 V1-V5 의 CREATE TABLE/TRIGGER statement 가 본 file 에 reflection: `grep -c "CREATE TABLE\|CREATE TRIGGER" projects/wms-platform/apps/inventory-service/src/main/resources/db/migration/V*.sql` 카운트와 본 file 의 section count 비교.
- partial-unique index 2개 + 모든 named CHECK constraint 가 본 file 의 SQL snippet 안에 포함.
- `grep -rn "(Open Item" projects/wms-platform/specs/services/inventory-service/` → 0 (regression 0 확인).
- `grep -n "❌\|⚠️" projects/wms-platform/specs/services/inventory-service/domain-model.md` → database-design.md 의 ❌ 가 ✅ 로 정정됐는지 확인.
- 모든 cross-reference (~10개) `ls` 으로 실재 확인.
- CI = path-filter (TASK-MONO-074/075) → markdown-only path 로 ~1 PASS + 15 SKIP 예상.

---

# Implementation Notes

- BE-141~156 의 single-PR closure 답습 — ready → done (single-PR pattern).
- D4 OVERRIDE: ADR-MONO-003a § D1.1 (project-internal spec backfill).
- BE-152 § "candidate inventory" #2 closure (마지막 잔존 candidate) → BE-152 surfaced 4 candidate 전체 closure (#1 BE-156 + #2 BE-157 + #3 MONO-087 + #10 BE-153 (+ status sync BE-156)).
- 분석=Opus 4.7 / 구현 권장=Opus 4.7 (mid scope retrospective spec authoring, byte-identical SQL transcribe + cross-reference dense + sibling pattern 답습 안 됨 = first-of-portfolio).
