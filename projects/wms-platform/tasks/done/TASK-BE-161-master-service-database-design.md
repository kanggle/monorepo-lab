# Task ID

TASK-BE-161

# Title

master-service `database-design.md` retrospective authoring + `architecture.md § Persistence` cross-link 정정 (portfolio-wide database-design.md gap 4 service 중 #1 closure)

# Status

ready

# Owner

wms-platform

# Task Tags

- wms
- master-service
- spec
- backfill
- database
- be

---

# Goal

[TASK-BE-157](../done/TASK-BE-157-inventory-service-database-design.md) + [TASK-BE-160](../done/TASK-BE-160-notification-service-database-design.md) 답습 third-of-portfolio — `master-service/database-design.md` 신규 retrospective spec authoring. **portfolio-wide database-design.md gap 잔존 4 service 중 #1 closure** (가장 작은 Flyway scope = master 302 line).

배경:

- master-service Flyway = 302 line / 7 file:
  - **V1__init_warehouse.sql** (32): warehouse aggregate root.
  - **V2__init_outbox.sql** (28): outbox + processed_events (libs/java-messaging schema, EntityScan validate 요구로 producer-only master 가 processed_events 도 create).
  - **V3__init_zone.sql** (43): zone (FK warehouse, compound unique).
  - **V4__init_location.sql** (53): location (FK warehouse+zone, globally unique location_code = W3).
  - **V5__init_sku.sql** (52): SKU (UPPERCASE check, partial unique barcode `WHERE barcode IS NOT NULL`).
  - **V6__init_lot.sql** (49): lot (FK sku, compound unique (sku_id, lot_no), partial expiry index `WHERE status='ACTIVE'`, supplier_partner_id intentional no-FK).
  - **V7__init_partner.sql** (45): partner (enum partner_type SUPPLIER/CUSTOMER/BOTH, internal B2B contact data).
- architecture.md L230-231 = "Full schema lives in migration scripts. High-level tables per entity are defined in domain-model.md." stale reference — domain-model.md 가 full table layout 안 가지고 있음. database-design.md 신규 작성 후 redirect 정정.
- BE-157/BE-160 답습 third-of-portfolio (sibling 답습 가능 — inventory 650 line / notification 323 line). master 는 ~480 line 예상 (Flyway 302 × 1.6 ratio).

본 task = **신규 retrospective spec authoring + architecture.md L230-231 cross-link 1줄 정정** (production code 0, Flyway SQL 0, application.yml 0, markdown only). BE-141~160 single-PR closure 답습.

---

# Scope

## In Scope

### A. 신규 `master-service/database-design.md`

대상 경로: `projects/wms-platform/specs/services/master-service/database-design.md` (신규).

구조 (BE-157 답습, ~12 section):

1. **헤더 + intent** — retrospective backfill scope + Flyway V1-V7 가 SoT + 본 file 은 reflection (V8+ 추가 시 동기 갱신 의무, BE-157 contract 답습).
2. **Schema Overview** — 8 table dependency graph (warehouses → zones → locations + warehouses → lots ↑ skus + outbox + processed_events, partners independent).
3. **Warehouse aggregate root (V1)** — warehouses table + warehouse_code globally unique + status enum + version + indexes.
4. **Outbox + ProcessedEvent (V2, libs/java-messaging)** — outbox shared schema (BIGSERIAL PK, status enum PENDING/PUBLISHED/FAILED) + processed_events stub (master = producer-only, but JPA EntityScan validate 요구). 다른 wms service 와 다른 outbox shape (UUID PK vs BIGSERIAL) 명시.
5. **Zone (V3)** — zones table + FK warehouse_id + compound unique (warehouse_id, zone_code) + zone_type enum (6 values) + indexes.
6. **Location (V4)** — locations table + FK warehouse + zone + **globally unique location_code (W3)** + location_type enum (5 values) + capacity_units optional positive + dual index pattern (warehouse_id+status / zone_id+status).
7. **SKU (V5)** — skus table + globally unique sku_code + **UPPERCASE check** + partial unique barcode (`WHERE barcode IS NOT NULL`) + base_uom enum (5) + tracking_type enum (NONE/LOT) + optional nonneg numeric columns (weight/volume/shelf_life) + index.
8. **Lot (V6)** — lots table + FK sku + compound unique (sku_id, lot_no) + date_pair check + **supplier_partner_id intentional no-FK** rationale (Partner aggregate lifecycle 분리, v1 soft validation, v2 promotion candidate) + partial expiry index (`WHERE status='ACTIVE'`) + dual index.
9. **Partner (V7)** — partners table + globally unique partner_code + partner_type enum (SUPPLIER/CUSTOMER/BOTH) + internal B2B contact data note (PROJECT.md `data_sensitivity: internal`) + 2 indexes.
10. **Indexing Strategy Summary** — 모든 인덱스 한 표 catalog (~15 entries).
11. **Migration History** — V1-V7 line count + scope.
12. **References** — Flyway 7 file paths + domain-model.md + architecture.md + idempotency.md + 2 sibling database-design.md (inventory BE-157 + notification BE-160) + rules/domains/wms.md + rules/traits/transactional.md.

예상 크기: ~440-500 line.

### B. architecture.md § Persistence cross-link 정정

대상: `projects/wms-platform/specs/services/master-service/architecture.md` L230-231.

작업:
- 기존: `Full schema lives in migration scripts. High-level tables per entity are defined in / specs/services/master-service/domain-model.md.`
- 정정: `Full schema reflection lives in [database-design.md](database-design.md); domain meaning per entity in [domain-model.md](domain-model.md).`

기존의 L228 `master_outbox` cell (현재 6 column 명시) 도 V2 schema 와 align 검증 — `master_outbox` 라는 명칭이 V2 의 `outbox` 와 다름! 정정 가능성 있음. 단, architecture.md 의 문맥 (libs/java-messaging shared schema) 를 보면 documentation 수준 정확화. 정찰 후 결정.

### C. (Out of Scope) architecture.md § Open Items 자체 정리

architecture.md L295-307 의 `## Open Items (Before First Implementation Task)` list 가 stale (items 1, 2, 3, 4 = 이미 작성됨, item 5 = MONO-087 으로 closure, item 6 = BE-153 으로 closure). BE-152 audit pattern (master-service 적용) 의 별 task. **본 task scope 밖**.

## Out of Scope

- architecture.md § Open Items list 자체의 stale audit (BE-152 답습 master 적용, 별 task).
- 다른 3 service (inbound/outbound/admin) database-design.md authoring (BE-162-164 후속 single-task 각각).
- Flyway 변경 0 — schema 1-byte 도 변경 안 함. spec/markdown only.
- 신규 error code / Kafka topic / API endpoint = 0.

---

# Acceptance Criteria

- [ ] `projects/wms-platform/specs/services/master-service/database-design.md` 신규 file 존재, 약 440-500 line, 12 section 구조.
- [ ] 8 SQL table entity 모두 file 안에 reflection (warehouses + outbox + processed_events + zones + locations + skus + lots + partners).
- [ ] W1-W6 invariant cross-reference 명시 (W3 location_code 글로벌 unique 특히 강조).
- [ ] partial unique index 2개 (sku barcode + lot expiry partial index) + 의도 명시.
- [ ] supplier_partner_id intentional no-FK 이유 명시 (Partner aggregate lifecycle 분리, v2 promotion candidate).
- [ ] outbox + processed_events (libs/java-messaging shared schema) 의 BIGSERIAL PK vs 다른 wms service 의 UUID PK 차이 명시.
- [ ] architecture.md L230-231 cross-link 정정 (`migration scripts ... domain-model.md` → `database-design.md ... domain-model.md`).
- [ ] References section 의 모든 cross-link 실재 (~12개: Flyway 7 file + domain-model + architecture + idempotency + sibling 2 database-design + rules/domains/wms + rules/traits/transactional).
- [ ] HARDSTOP-03 PASS — 신규 file 이 wms-specific (project-internal).
- [ ] grep `(Open Item` projects/wms-platform/specs/services/master-service/ → 0 (regression 0).
- [ ] production code 변경 = 0, Flyway SQL 변경 = 0 (markdown only, architecture.md 1-line edit).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md` (`domain: wms`, `traits: [transactional, integration-heavy]`), then load `rules/common.md` + `rules/domains/wms.md` + `rules/traits/transactional.md` + `rules/traits/integration-heavy.md`. Service Type 은 `master-service/architecture.md § Identity` 의 `rest-api`.

- `projects/wms-platform/specs/services/master-service/architecture.md` — § Persistence (L224-231), § Open Items (참고만)
- `projects/wms-platform/specs/services/master-service/domain-model.md` — 6 entity 정의 (Warehouse / Zone / Location / SKU / Partner / Lot)
- `projects/wms-platform/specs/services/master-service/idempotency.md` — REST + outbox dedupe
- `projects/wms-platform/specs/services/master-service/external-integrations.md` — BE-159, zero-state
- `projects/wms-platform/apps/master-service/src/main/resources/db/migration/V1__init_warehouse.sql` — 32 line
- `projects/wms-platform/apps/master-service/src/main/resources/db/migration/V2__init_outbox.sql` — 28 line (libs/java-messaging shared)
- `projects/wms-platform/apps/master-service/src/main/resources/db/migration/V3__init_zone.sql` — 43 line
- `projects/wms-platform/apps/master-service/src/main/resources/db/migration/V4__init_location.sql` — 53 line
- `projects/wms-platform/apps/master-service/src/main/resources/db/migration/V5__init_sku.sql` — 52 line
- `projects/wms-platform/apps/master-service/src/main/resources/db/migration/V6__init_lot.sql` — 49 line
- `projects/wms-platform/apps/master-service/src/main/resources/db/migration/V7__init_partner.sql` — 45 line
- `projects/wms-platform/specs/services/inventory-service/database-design.md` — sibling reference (BE-157, primary template)
- `projects/wms-platform/specs/services/notification-service/database-design.md` — sibling reference (BE-160)
- `rules/domains/wms.md` — W1-W6 invariants
- `rules/traits/transactional.md` — T1, T3, T8 (outbox + dedupe)

# Related Skills

- `.claude/skills/INDEX.md`

---

# Related Contracts

- N/A — schema spec authoring 만.

---

# Target Service

- `master-service`

---

# Edge Cases

1. **outbox + processed_events 의 libs/java-messaging shared schema** — 다른 wms service 의 outbox 가 UUID PK + JSONB payload + partition_key 인 것과 달리 master 는 libs base (BIGSERIAL PK + TEXT payload + status enum). BE-157/BE-160 답습 시 inventory_outbox / notification_outbox 의 wms-specific shape 와 정확히 다르다는 점 명시 + master 가 libs base 답습 이유 (V2 inline 주석 "schemas match libs/java-messaging entities").
2. **supplier_partner_id intentional no-FK** — V6 L15-18 inline 주석 ("v1 performs only soft validation ... BE-005 follow-up cleanup expected before promoting to FK"). spec 본문에 v2 promotion candidate 명시.
3. **SKU UPPERCASE constraint** — `ck_skus_sku_code_uppercase CHECK (sku_code = UPPER(sku_code))` 는 흔치 않은 SQL pattern. case-insensitive lookup 의 DB-level guarantee 의도 명시.
4. **partial unique barcode** — `WHERE barcode IS NOT NULL`. JPA `@UniqueConstraint` 가 WHERE 표현 못해 DB-only constraint, entity-level annotation 부재. V5 L42-46 inline 주석 답습.
5. **partial expiry index** — `idx_lots_expiry_active WHERE status='ACTIVE'` 는 daily batch (expiry sweeper) 의 hot-path optimization. BE-157 inventory `idx_reservation_status_expires_at WHERE status='RESERVED'` 답습 패턴.

---

# Failure Scenarios

1. **byte-identical SQL DDL** — table 컬럼 이름 / 타입 / 제약 조건 / index name 모두 Flyway 파일에서 그대로 transcribe.
2. **architecture.md L230-231 cross-link 정정 시 sentence flow broken** — L224-231 paragraph 의 보존. Edit 후 즉시 Read 검증.
3. **architecture.md L228 의 `master_outbox` 명칭** — V2 의 실제 table 이름이 `outbox` (NOT `master_outbox`). 본 task scope 에서 architecture.md L228 도 정정 가능 — 검증 후 결정.
4. **CRLF vs LF on Windows** — BE-156~160 답습 (LF 작성). hook 검증.
5. **path-filter (TASK-MONO-074/075) markdown-only PASS** — 정상 (BE-156~160 답습).

---

# Validation Plan

- `ls projects/wms-platform/specs/services/master-service/database-design.md` → file 존재 확인.
- `wc -l` → 440-500 line 범위 확인.
- 모든 V1-V7 의 CREATE TABLE statement 가 본 file 에 reflection (8 table count = warehouses + outbox + processed_events + zones + locations + skus + lots + partners).
- partial unique index 2개 + 모든 named CHECK constraint 가 본 file 의 SQL snippet 안에 포함.
- `grep -rn "(Open Item" projects/wms-platform/specs/services/master-service/` → 0 (regression 0 확인).
- architecture.md L230-231 cross-link 정정 byte-identical 확인.
- (Optional) architecture.md L228 의 `master_outbox` 명칭 정정 여부 결정 후 검증.
- 모든 cross-reference (~12개) `ls` 으로 실재 확인.
- CI = path-filter (TASK-MONO-074/075) → markdown-only path 로 ~1 PASS + 15 SKIP 예상.

---

# Implementation Notes

- BE-141~160 single-PR closure 답습 — ready → done.
- D4 OVERRIDE: ADR-MONO-003a § D1.1 (project-internal spec backfill).
- **portfolio-wide database-design.md gap 잔존 4 service 중 #1 closure** — 잔존 3 service (inbound/outbound/admin) 별 task 후보 (BE-162/163/164).
- 분석=Opus 4.7 / 구현 권장=Opus 4.7 (BE-157/BE-160 답습 third-of-portfolio + byte-identical SQL transcribe + W1-W6 cross-reference + libs/java-messaging shared schema 차이 명시).
