# Task ID

TASK-BE-162

# Title

inbound-service `database-design.md` retrospective authoring + `architecture.md § Persistence` cross-link 정정 (portfolio-wide database-design.md gap 3 service 중 #1 closure)

# Status

ready

# Owner

wms-platform

# Task Tags

- wms
- inbound-service
- spec
- backfill
- database
- be

---

# Goal

[TASK-BE-157/BE-160/BE-161](../done/) 답습 fourth-of-portfolio — `inbound-service/database-design.md` 신규 retrospective spec authoring. **portfolio-wide database-design.md gap 잔존 3 service 중 #1 closure**.

배경:

- inbound-service Flyway = 562 line / 8 migration file:
  - **V1__init_master_readmodel.sql** (124): 6 master snapshot table (warehouse / zone / location / sku / lot / partner)
  - **V2__init_asn_tables.sql** (52): asn aggregate + asn_line (FK CASCADE, source enum MANUAL/WEBHOOK_ERP, 7-status state machine)
  - **V3__init_inspection_tables.sql** (62): inspection (1:1 ASN) + inspection_line + inspection_discrepancy (3-enum + partial index `WHERE acknowledged=FALSE`)
  - **V4__init_putaway_tables.sql** (64): putaway_instruction + putaway_line + putaway_confirmation (append-only)
  - **V5__init_outbox_dedupe.sql** (51): inbound_outbox (UUID+JSONB+partition_key wms-specific) + inbound_event_dedupe (T8, 3-outcome enum)
  - **V6__init_webhook_inbox.sql** (44): erp_webhook_inbox (status FSM PENDING/APPLIED/FAILED + dual partial index) + erp_webhook_dedupe (append-only)
  - **V7__role_grants.sql** (152): 4 trigger function (W2 append-only enforcement, BE-157 inventory V5 2-layer pattern 답습 + putaway_confirmation + erp_webhook_dedupe 추가) + REVOKE block (4 table)
  - **V8__init_asn_no_sequence.sql** (13): per-day ASN number sequence (date_key YYYYMMDD PK + last_seq atomic INSERT...ON CONFLICT)
- architecture.md L389 = "High-level table layout in `specs/services/inbound-service/domain-model.md`." → redirect database-design.md.
- BE-157/BE-160/BE-161 답습 fourth-of-portfolio (sibling 답습 가능 — 3 sibling database-design.md 모두 reference). inbound 는 **18 table + 4 trigger** = 가장 복잡한 schema (master 8 table 보다 많음, inventory 11 보다 많음).
- **HARDSTOP-10 hook clean path** — inbound architecture.md 이미 BE-150 으로 `### Service Type Composition` standard sub-section 통과. 본 task 의 architecture.md edit (1-line cross-link) 가 hook block 안 함 예상.

본 task = **신규 retrospective spec authoring + architecture.md L389 cross-link 1줄 정정** (production code 0, Flyway SQL 0, application.yml 0, markdown only). BE-141~161 single-PR closure 답습 fourth-of-portfolio.

---

# Scope

## In Scope

### A. 신규 `inbound-service/database-design.md`

대상 경로: `projects/wms-platform/specs/services/inbound-service/database-design.md` (신규).

구조 (BE-157/BE-161 답습, ~12 section):

1. **헤더 + intent** — retrospective backfill scope + Flyway V1-V8 가 SoT + 본 file 은 reflection (V9+ 추가 시 동기 갱신 의무, BE-157 contract 답습).
2. **Schema Overview** — 18 table dependency graph (ASCII), 6 read-model + 8 domain table (asn / inspection / putaway 3 aggregate) + 2 outbox/dedupe + 2 webhook + 1 sequence.
3. **Master Read Model (V1)** — 6 snapshot table (warehouse/zone/location/sku/lot/partner) + master_version out-of-order delivery + 각 status enum + index pattern. partner snapshot 의 4-enum (SUPPLIER/CARRIER/CUSTOMER/BOTH, **master-service 의 3-enum 과 차이** = CARRIER 추가).
4. **ASN Aggregate (V2)** — asn root + asn_line child (FK CASCADE) + 7-status state machine (CREATED → INSPECTING → INSPECTED → IN_PUTAWAY → PUTAWAY_DONE → CLOSED + CANCELLED parallel) + source enum + asn_no UNIQUE + 3 index.
5. **Inspection Aggregate (V3)** — 1:1 ASN UNIQUE (FK CASCADE) + inspection_line (compound unique inspection+asn_line, 3 qty columns nonneg) + inspection_discrepancy (3-enum type + partial index `WHERE acknowledged=FALSE`).
6. **Putaway Aggregate (V4)** — putaway_instruction (UNIQUE asn_id, 4-status enum) + putaway_line (3-status enum) + **putaway_confirmation (append-only)** + dual index pattern.
7. **Outbox + EventDedupe (V5, T3 + T8)** — inbound_outbox **wms-specific shape** (UUID PK + JSONB payload + partition_key, master 의 libs/java-messaging BIGSERIAL+TEXT shape 와 차이 명시) + inbound_event_dedupe (3-outcome enum APPLIED/IGNORED_DUPLICATE/FAILED).
8. **ERP Webhook (V6)** — erp_webhook_inbox (3-status FSM + dual partial index pending/failed) + erp_webhook_dedupe (append-only, PK event_id VARCHAR(80)).
9. **Append-Only Enforcement Strategy (V7)** — BE-157 inventory V5 2-layer pattern 답습 **확장 (4 trigger)**: inbound_event_dedupe (UPDATE+DELETE block) / erp_webhook_dedupe (UPDATE+DELETE block) / putaway_confirmation (UPDATE+DELETE block, domain §4 invariant) / **inbound_outbox (DELETE only — publisher needs UPDATE for published_at)**. REVOKE block (4 table).
10. **ASN Number Sequence (V8)** — asn_no_sequence (date_key VARCHAR(8) PK YYYYMMDD + last_seq BIGINT atomic UPSERT pattern). single-row-per-day sequence pattern 의도 명시.
11. **Indexing Strategy Summary** — 모든 인덱스 catalog (~30+ entries — 가장 큰 service).
12. **Migration History** — V1-V8 line count + scope.
13. **References** — Flyway 8 file paths + domain-model.md + architecture.md + idempotency.md + sibling 3 database-design.md (inventory/notification/master) + rules/domains/wms.md + rules/traits/transactional.md + integration-heavy.md.

예상 크기: ~700-900 line (Flyway 562 × 1.6 ratio = 900).

### B. architecture.md § Persistence cross-link 정정

대상: `projects/wms-platform/specs/services/inbound-service/architecture.md` L389.

작업:
- 기존: `High-level table layout in \`specs/services/inbound-service/domain-model.md\`.`
- 정정: `Full schema reflection lives in [\`database-design.md\`](database-design.md); domain meaning per entity in [\`domain-model.md\`](domain-model.md).`

기존 L382-388 의 6-row table list 는 high-level summary 로 유지.

### C. (Out of Scope) architecture.md § Open Items 자체 정리

architecture.md L491 § Open Items list 가 stale 가능성 (BE-152 audit pattern inbound-service 적용 안 됨). 본 task scope 밖.

## Out of Scope

- architecture.md § Open Items list 자체의 stale audit (BE-152 답습 inbound 적용, 별 task).
- 다른 2 service (outbound/admin) database-design.md authoring (BE-163-164 후속 single-task 각각).
- Flyway 변경 0 — schema 1-byte 도 변경 안 함. spec/markdown only.
- 신규 error code / Kafka topic / API endpoint = 0.

---

# Acceptance Criteria

- [ ] `projects/wms-platform/specs/services/inbound-service/database-design.md` 신규 file 존재, 약 700-900 line, 12 section 구조.
- [ ] 18 SQL table entity 모두 file 안에 reflection (6 snapshot + 2 asn + 3 inspection + 3 putaway + 1 outbox + 1 dedupe + 1 webhook_inbox + 1 webhook_dedupe + 1 asn_no_sequence).
- [ ] V7 4 trigger function 정확히 명시 (UPDATE+DELETE 3 + DELETE-only 1 outbox).
- [ ] V8 atomic UPSERT pattern 의도 명시 (INSERT...ON CONFLICT...DO UPDATE...RETURNING).
- [ ] partial index 명시 (5+ partial index: outbox pending / dedupe processed_at / webhook inbox pending / webhook inbox failed / inspection_discrepancy unacked).
- [ ] partner_snapshot 4-enum (SUPPLIER/CARRIER/CUSTOMER/BOTH) vs master-service partners 3-enum (SUPPLIER/CUSTOMER/BOTH) 차이 명시 — CARRIER 추가 의도.
- [ ] inbound_outbox wms-specific shape (UUID+JSONB+partition_key) vs master outbox libs-shape (BIGSERIAL+TEXT) 차이 명시.
- [ ] architecture.md L389 cross-link 정정 (`domain-model.md` → `database-design.md`).
- [ ] References section 의 모든 cross-link 실재 (~13개: Flyway 8 file + 4 sibling spec + 3 sibling database-design + 3 rules/platform).
- [ ] HARDSTOP-03 PASS.
- [ ] grep `(Open Item` projects/wms-platform/specs/services/inbound-service/ → 0 (regression 0).
- [ ] production code 변경 = 0, Flyway SQL 변경 = 0 (markdown only).

---

# Related Specs

- `projects/wms-platform/specs/services/inbound-service/architecture.md` — § Persistence (L380-389), § Open Items
- `projects/wms-platform/specs/services/inbound-service/domain-model.md` — entity 정의 (ASN / Inspection / Putaway / ERP webhook inbox)
- `projects/wms-platform/specs/services/inbound-service/idempotency.md` — REST + outbox + webhook dedupe
- `projects/wms-platform/specs/services/inbound-service/external-integrations.md` — ERP webhook + Kafka catalog
- `projects/wms-platform/apps/inbound-service/src/main/resources/db/migration/V1__init_master_readmodel.sql` — 124 line
- `projects/wms-platform/apps/inbound-service/src/main/resources/db/migration/V8__init_asn_no_sequence.sql` — 13 line
- `projects/wms-platform/specs/services/inventory-service/database-design.md` — sibling primary template (BE-157, V5 trigger pattern source)
- `projects/wms-platform/specs/services/notification-service/database-design.md` — sibling (BE-160)
- `projects/wms-platform/specs/services/master-service/database-design.md` — sibling (BE-161, V2 libs outbox shape divergence reference)
- `rules/domains/wms.md` — W2 append-only invariant
- `rules/traits/transactional.md` — T3 (outbox), T8 (event dedupe)

# Related Skills

- `.claude/skills/INDEX.md`

---

# Related Contracts

- N/A — schema spec authoring 만.

---

# Target Service

- `inbound-service`

---

# Edge Cases

1. **18 table = 가장 큰 schema** — BE-161 master 8 table + BE-157 inventory 11 table 대비 inbound 18 table (6 read-model 포함). spec 본문이 800+ line 가능 — outbound (~940 예상) 와 비슷한 scale.
2. **V7 4 trigger function** — BE-157 inventory V5 의 2 trigger (inventory_movement no-update + no-delete) 대비 inbound V7 = 4 trigger function (dedupe + webhook_dedupe + putaway_confirmation + outbox-delete-only). outbox 의 DELETE-only trigger 가 unique pattern (publisher 가 UPDATE 권한 필요).
3. **partner_snapshot 4-enum vs master 3-enum** — inbound 의 partner_snapshot 이 CARRIER 추가. read-model 측에서 가능한 이유 (transport vendor 를 partner 로 분류 가능, master 는 v1 SUPPLIER/CUSTOMER/BOTH 만). v1 source-of-truth 차이 의도 명시.
4. **asn_no_sequence atomic UPSERT** — `INSERT INTO ... ON CONFLICT (date_key) DO UPDATE SET last_seq = last_seq + 1 RETURNING last_seq`. 단일 SQL 으로 race-free sequence increment. BE-157 inventory 에 없는 pattern.
5. **CRLF vs LF on Windows** — BE-156~161 답습 (LF 작성). hook 검증.

---

# Failure Scenarios

1. **byte-identical SQL DDL** — table 컬럼 이름 / 타입 / 제약 조건 / index name 모두 Flyway 파일에서 그대로 transcribe (18 table + 4 trigger 함수).
2. **architecture.md L389 cross-link 정정 시 sentence flow broken** — L382-389 paragraph 보존. Edit 후 즉시 Read 검증.
3. **HARDSTOP-10 hook clean path 가정 무효 가능성** — BE-150 으로 통과한 inbound architecture.md 가 다른 hook (예: 다른 standard pattern) 으로 block 가능. Edit 후 검증.
4. **section count 증가로 spec line 초과** — 18 table + 4 trigger + 5 partial index + 4 enum 차이 명시 으로 800-900 line 예상. AC target 700-900 — 본 PR 에서 ~900 까지 가능.
5. **path-filter (TASK-MONO-074/075) markdown-only PASS** — 정상 (BE-156~161 답습).

---

# Validation Plan

- `wc -l` → 700-900 line 확인.
- 모든 V1-V8 의 CREATE TABLE statement 가 본 file 에 reflection (18 table count match).
- 4 V7 trigger function + REVOKE block 명시.
- partial index 5+개 catalog.
- `grep -rn "(Open Item" projects/wms-platform/specs/services/inbound-service/` → 0.
- architecture.md L389 cross-link 정정 byte-identical 확인.
- 모든 cross-reference (~13개) `ls` 으로 실재 확인.
- CI = path-filter → ~1 PASS + 15 SKIP 예상.

---

# Implementation Notes

- BE-141~161 single-PR closure 답습 — ready → done.
- D4 OVERRIDE: ADR-MONO-003a § D1.1 (project-internal spec backfill).
- **portfolio-wide database-design.md gap 잔존 3 service 중 #1 closure** — 잔존 2 service (outbound/admin) 별 task 후보 (BE-163/164).
- 분석=Opus 4.7 / 구현 권장=Opus 4.7 (BE-157/160/161 답습 fourth-of-portfolio + byte-identical SQL transcribe + 18 table largest scope so far + V7 4-trigger pattern + atomic UPSERT V8 reference).
