# Task ID

TASK-BE-163

# Title

outbound-service `database-design.md` retrospective authoring + `architecture.md § Persistence` cross-link 정정 (portfolio-wide database-design.md gap 2 service 중 #1 closure)

# Status

ready

# Owner

wms-platform

# Task Tags

- wms
- outbound-service
- spec
- backfill
- database
- be

---

# Goal

BE-157/160/161/162 답습 fifth-of-portfolio — `outbound-service/database-design.md` 신규 retrospective spec authoring. **portfolio-wide database-design.md gap 잔존 2 service 중 #1 closure**.

배경:

- outbound-service Flyway = **589 line / 14 migration file** (BE-162 inbound 8 file 대비 1.75배 evolution count) — **evolution-rich history**:
  - **V1-V8** (init batch, 397 line): 6 master snapshot + order + picking + packing/shipping + saga + outbox/dedupe + erp_order_webhook + role_grants (4 trigger).
  - **V9-V14** (schema_align + feature batch, 192 line): outbox/dedupe schema align (V9) + order schema align (V10) + picking/packing/shipment schema align (V11) + shipment.created_by (V12) + tms_request_dedupe BE-049 (V13) + saga.re_emit_count BE-050 (V14).
- **15 table + 4 trigger** = inbound (18+4) 보다 약간 작음, master (8) 의 ~2배.
- architecture.md L483 § Persistence — redirect database-design.md.
- **HARDSTOP-10 hook clean path** — outbound architecture.md 가 이미 `### Service Type Composition` standard sub-section 보유 (L23 정찰 확인). BE-150/154/161 답습 fix 불필요.
- BE-049 (TMS dedupe BE-049, vendor idempotency I4 fallback, JSONB response_snapshot) + BE-050 (saga sweeper re_emit_count) feature evolution 이 portfolio depth signal — spec 본문 highlight.

본 task = **신규 retrospective spec authoring + architecture.md L483 cross-link 정정** (production code 0, Flyway SQL 0, application.yml 0, markdown only). BE-141~162 single-PR closure 답습 fifth-of-portfolio.

---

# Scope

## In Scope

### A. 신규 `outbound-service/database-design.md`

대상 경로: `projects/wms-platform/specs/services/outbound-service/database-design.md` (신규).

구조 (BE-162 inbound 답습 + outbound-specific evolution sections):

1. **헤더 + intent** — retrospective backfill scope + Flyway V1-V14 가 SoT + 본 file 은 reflection (V15+ 추가 시 동기 갱신 의무) + **evolution-rich history 명시** (9 init + 6 schema_align/feature 패턴, 다른 wms service 와 차이).
2. **Schema Overview** — 15 table dependency graph (ASCII), 6 read-model + 9 domain table (order/picking/packing/shipping/saga) + outbox/dedupe + erp webhook + tms dedupe.
3. **Master Read Model (V1)** — 6 snapshot table (partner_snapshot 4-enum 동일 inbound).
4. **Order Aggregate (V2 + V10)** — outbound_order + outbound_order_line + **V10 schema align evolution** (order_no globally unique partial-index + source enum + customer_partner_id/notes/created_by/updated_by 추가 + erp_order_number/partner_id legacy 유지 zero-downtime pattern).
5. **Picking Aggregate (V3 + V11)** — picking_request (UNIQUE order_id) + picking_request_line + picking_confirmation (denormalized order_id + notes 추가 V11) + picking_confirmation_line (V11 order_line_id + actual_location_id + qty_confirmed 추가) + saga_id cross-reference.
6. **Packing + Shipping (V4 + V11 + V12)** — packing_unit (V11 carton_no/packing_type/weight/dimensions 추가, partial unique carton_no WHERE NOT NULL) + packing_unit_line + **shipment** (V11 shipment_no/tms_status/tms_notified_at/tms_request_id 추가, partial unique shipment_no + 1:1 order index, V12 created_by 추가 BE-040).
7. **OutboundSaga (V5 + V14)** — outbound_saga (UNIQUE order_id + status FSM) + **V14 re_emit_count BE-050 saga sweeper recovery** + partial sweeper-candidates index (`WHERE status IN ('REQUESTED', 'CANCELLATION_REQUESTED', 'SHIPPED')`).
8. **Outbox + EventDedupe (V6 + V9)** — outbound_outbox (V6 BIGSERIAL early shape → V9 schema align: aggregate_type + event_version + partition_key 추가, wms-specific shape 완성). V6 status/retry_count publisher-implementation 컬럼 유지. outbound_event_dedupe (V9 outcome enum APPLIED/IGNORED_DUPLICATE/FAILED 추가).
9. **ERP Order Webhook (V7)** — erp_order_webhook_inbox (status FSM PENDING + partial index) + erp_order_webhook_dedupe (append-only).
10. **TMS Request Dedupe (V4 + V13)** — V4 early bootstrap (shipment_id PK + idempotency_key + tms_status + requested_at) + **V13 BE-049 final shape** (request_id PK = Shipment.id UUIDv7 + sent_at + response_snapshot JSONB). vendor idempotency I4 fallback pattern — 외부 TMS 가 Idempotency-Key 미준수 시 local fallback ground-truth.
11. **Append-Only Enforcement Strategy (V8)** — 4 trigger function (outbound_event_dedupe + erp_order_webhook_dedupe + tms_request_dedupe full-block + outbound_outbox DELETE-only publisher pattern) + REVOKE block (4 table). inbound V7 답습 동일 패턴.
12. **Indexing Strategy Summary** — 모든 인덱스 한 표 catalog (~40+ entries).
13. **Migration History** — V1-V14 line count + scope + evolution rationale (init vs schema_align vs feature).
14. **References** — Flyway 14 file paths + domain-model.md + architecture.md + idempotency.md + sagas/outbound-saga.md + external-integrations.md (TMS I4 cross-ref) + sibling 4 database-design.md (inventory/notification/master/inbound) + rules.

예상 크기: ~850-1000 line (BE-162 inbound 746 line + outbound 의 14-migration evolution + tms_request_dedupe + saga re_emit_count + V11 wide schema align = 약간 더 큼).

### B. architecture.md § Persistence cross-link 정정

대상: `projects/wms-platform/specs/services/outbound-service/architecture.md` L483 부근.

작업: high-level table list 후 redirect — `Full schema reflection lives in [\`database-design.md\`](database-design.md); domain meaning per entity in [\`domain-model.md\`](domain-model.md).`

### C. (Out of Scope) architecture.md § Open Items 자체 정리

architecture.md L631 § Open Items list 가 stale 가능성. 본 task scope 밖.

## Out of Scope

- architecture.md § Open Items list 자체의 stale audit (BE-152 답습 outbound 적용, 별 task).
- admin database-design.md authoring (BE-164 후속 single task).
- Flyway 변경 0 — schema 1-byte 도 변경 안 함. spec/markdown only.
- 신규 error code / Kafka topic / API endpoint = 0.

---

# Acceptance Criteria

- [ ] `projects/wms-platform/specs/services/outbound-service/database-design.md` 신규 file 존재, 약 850-1000 line, 13 section 구조.
- [ ] 15 table entity + 4 V8 trigger function 모두 reflection.
- [ ] **Evolution history 명시** — V1-V8 init vs V9-V14 schema_align/feature 분리 + 각 schema align 의 zero-downtime pattern (ADD COLUMN IF NOT EXISTS + legacy column 유지 + partial unique nullable column) 명시.
- [ ] TMS Request Dedupe V4 early shape vs V13 BE-049 final shape evolution 명시 + I4 vendor idempotency fallback 의도 인용.
- [ ] OutboundSaga V14 re_emit_count BE-050 saga sweeper cap pattern 명시 + partial sweeper-candidates index (`WHERE status IN (...)`) 의도.
- [ ] outbound_outbox V6 → V9 schema align evolution (wms-specific shape 완성 stages) 명시.
- [ ] partial index 명시 (4+ partial: outbox pending / saga sweeper / shipment_no partial unique / carton_no partial unique).
- [ ] partner_snapshot 4-enum (CARRIER 포함) 동일 inbound 답습 cross-ref.
- [ ] architecture.md L483 cross-link 정정.
- [ ] References section 의 모든 cross-link 실재 (~16개: Flyway 14 file + 4 sibling spec + 4 sibling database-design + 3 rules/platform).
- [ ] HARDSTOP-03 PASS.
- [ ] grep `(Open Item` projects/wms-platform/specs/services/outbound-service/ → 0 (regression 0).
- [ ] production code 변경 = 0, Flyway SQL 변경 = 0.

---

# Related Specs

- `projects/wms-platform/specs/services/outbound-service/architecture.md` — § Persistence (L483), § Saga Persistence (L330), § Open Items
- `projects/wms-platform/specs/services/outbound-service/domain-model.md` — entity 정의 (Order / Picking / Packing / Shipping / Saga / Outbox / Webhook)
- `projects/wms-platform/specs/services/outbound-service/idempotency.md` — REST + outbox + webhook + tms dedupe
- `projects/wms-platform/specs/services/outbound-service/external-integrations.md` — TMS I4 + erp webhook
- `projects/wms-platform/specs/services/outbound-service/sagas/outbound-saga.md` — saga state machine + sweeper
- `projects/wms-platform/apps/outbound-service/src/main/resources/db/migration/V*.sql` — 14 Flyway file
- sibling 4 database-design.md (inventory BE-157 / notification BE-160 / master BE-161 / inbound BE-162)
- `rules/domains/wms.md` — W2 append-only invariant
- `rules/traits/transactional.md` — T3 (outbox), T8 (event dedupe), T4 (state machine)
- `rules/traits/integration-heavy.md` — I4 (vendor idempotency)

---

# Related Contracts

- N/A — schema spec authoring 만.

---

# Target Service

- `outbound-service`

---

# Edge Cases

1. **14 Flyway file = evolution-rich** — schema_align (V9/V10/V11) + feature migration (V12/V13/V14) 의 incremental 한 history. inbound (8 init only) 와 패턴 차이. spec 본문에서 init vs schema_align 분리해서 evolution 보여줌 = portfolio depth signal.
2. **tms_request_dedupe V4 vs V13 dual shape** — V4 early bootstrap 5-column (shipment_id PK + idempotency_key + tms_status + requested_at) → V13 BE-049 final 3-column (request_id PK + sent_at + response_snapshot JSONB). V13 의 `CREATE TABLE IF NOT EXISTS` 가 V4 가 이미 존재하면 no-op. spec 본문에서 final = V13 shape 명시 (V4 = bootstrap, V13 = production).
3. **outbound_outbox V6+V9 wms-specific shape completion** — V6 early (id+aggregate_id+event_type+payload+status+created_at+published_at+retry_count) → V9 schema align (aggregate_type + event_version + partition_key 추가 = wms-specific shape 완성). V6 의 status/retry_count 가 publisher-implementation column (other wms outbox 에 없음).
4. **partial unique index nullable column** — V10 outbound_order.order_no + V11 packing_unit.carton_no + V11 shipment.shipment_no 가 backfill 후 unique 적용 위해 partial-unique. PostgreSQL `WHERE col IS NOT NULL` (또는 default NULLs excluded).
5. **HARDSTOP-10 hook clean path 가정 검증** — outbound architecture.md L23 `### Service Type Composition` 이미 존재 정찰. Edit 후 hook trigger 안 함 예상. 만약 trigger 시 BE-161 답습 ~5min inline fix.

---

# Failure Scenarios

1. **byte-identical SQL DDL** — 15 table + 4 trigger + all schema_align ALTER statement 모두 Flyway 에서 그대로 transcribe.
2. **evolution narrative drift** — V1-V14 의 incremental story 가 sloppy 하면 reader 가 final shape 확신 못 함. 각 schema_align section 에 "V<N> introduced ... → V<M> aligned to ..." 명시.
3. **TMS dedupe section 의 V4/V13 confusing** — 두 shape 모두 명시하되 V13 = final ground-truth 명확히.
4. **CRLF vs LF on Windows** — BE-156~162 답습 (LF 작성). hook 검증.
5. **path-filter (TASK-MONO-074/075) markdown-only PASS** — 정상 (BE-156~162 답습).

---

# Validation Plan

- `wc -l` → 850-1000 line 확인.
- 15 table + 4 trigger reflection (CREATE TABLE / CREATE TRIGGER count match).
- V1-V14 14-file migration history table 명시.
- partial index 4+ catalog.
- `grep -rn "(Open Item" projects/wms-platform/specs/services/outbound-service/` → 0.
- architecture.md L483 cross-link 정정 byte-identical 확인.
- 모든 cross-reference (~16개) `ls` 실재 확인.
- CI = path-filter → ~1 PASS + 15 SKIP 예상.

---

# Implementation Notes

- BE-141~162 single-PR closure 답습 — ready → done.
- D4 OVERRIDE: ADR-MONO-003a § D1.1 (project-internal spec backfill).
- **portfolio-wide database-design.md gap 잔존 2 service 중 #1 closure** — 잔존 1 service (admin) 별 single task 후보 (BE-164, 예상 ~1170 line).
- 분석=Opus 4.7 / 구현 권장=Opus 4.7 (BE-162 답습 fifth-of-portfolio + byte-identical SQL transcribe + 14-file evolution history narrative + TMS I4 fallback / saga sweeper / wms-specific outbox shape evolution).
