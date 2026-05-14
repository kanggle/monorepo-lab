# Task ID

TASK-BE-164

# Title

admin-service `database-design.md` retrospective authoring — **portfolio-wide database-design.md gap 완전 종결** (6/6 service)

# Status

ready

# Owner

wms-platform

# Task Tags

- wms
- admin-service
- spec
- backfill
- database
- be

---

# Goal

BE-157/160/161/162/163 답습 sixth-of-portfolio (마지막) — `admin-service/database-design.md` 신규 retrospective spec authoring. **portfolio-wide database-design.md gap 잔존 1 service 종결 → 6/6 완전 종결**.

배경:

- admin-service Flyway = **731 line / 3 file** (가장 fewest file 이지만 file 당 가장 크다):
  - **V1__init.sql** (194): **6 write-side tables** (admin_user + admin_role + admin_user_role_assignment + admin_setting + admin_outbox + admin_event_dedupe).
  - **V2__init_readmodel.sql** (398): **15 read-model projection tables** (6 master ref + ASN/inspection/order/shipment summary + inventory_snapshot + adjustment_audit + alert_log + 2 daily throughput counter).
  - **V99__seed_dev_data.sql** (139): dev/standalone seed (4 built-in role + bootstrap admin user + 7 seed settings, data-only, profile-gated).
- **21 table = portfolio 최대 table count** (inbound 18, outbound 15). 단 V99 = data-only.
- **CQRS read-side architecture exception** — Layered (not Hexagonal); read-model projection 패턴 = portfolio-unique. last_event_at + version LWW + Kafka 30d eventId dedupe (idempotency.md § 2).
- architecture.md L327 § Persistence stale cross-link → redirect database-design.md.
- **HARDSTOP-10 hook clean path** — admin-service architecture.md L23 `### Service Type Composition` 이미 존재 (outbound 와 동일 dual `rest-api + event-consumer`).

본 task = **신규 retrospective spec authoring + architecture.md cross-link 정정 + V99 narrative section** (production code 0, Flyway SQL 0, application.yml 0, markdown only). BE-141~163 single-PR closure 답습 sixth + final-of-portfolio.

---

# Scope

## In Scope

### A. 신규 `admin-service/database-design.md`

대상: `projects/wms-platform/specs/services/admin-service/database-design.md` (신규).

구조 (BE-163 outbound 답습 sixth-of-portfolio + admin-specific CQRS-rich sections):

1. **헤더 + intent** — retrospective backfill + Flyway V1/V2/V99 가 SoT + V3+ 추가 시 동기 갱신 의무 (BE-157 contract) + **CQRS read-side architecture context 명시** (Layered exception per architecture.md § Architecture Style Rationale).
2. **Schema Overview** — 21 table dependency graph (ASCII), write-side (6) vs read-model (15) 분리 표시 + LWW projection 데이터 흐름.
3. **§ 1 admin_user (V1)** — case-insensitive email partial unique (LOWER(email)), status enum, default_warehouse_id soft-FK.
4. **§ 2 admin_role (V1)** — permissions_json JSONB + is_builtin flag (ROLE_BUILTIN_IMMUTABLE invariant) + JsonbColumnRegressionGuardTest 참조.
5. **§ 3 admin_user_role_assignment (V1)** — partial unique `WHERE status='ACTIVE'` + **sentinel UUID for null warehouse_id** (COALESCE pattern) + status enum ACTIVE/REVOKED + FK to admin_user/admin_role.
6. **§ 4 admin_setting (V1)** — composite PK (key, warehouse_id) + sentinel UUID `00000000-...` for GLOBAL scope + CHECK constraint pinning scope-vs-sentinel semantics + value_json/schema_json JSONB.
7. **§ 5 admin_outbox (V1)** — wms-specific shape (UUID PK + JSONB + partition_key + attempt_count) — inventory/notification/inbound/outbound 동일 패턴.
8. **§ 6 admin_event_dedupe (V1)** — **4-outcome enum** APPLIED/IGNORED_DUPLICATE/**IGNORED_DUPLICATE_LATE**/FAILED. LATE = LWW projection 에서 stale event 가 도착했을 때 (last_event_at 비교 후 skip). 다른 service 의 3-outcome 과 차이 명시.
9. **§ 7 Read-Model Projection Pattern (V2 전체)** — LWW upsert (ON CONFLICT DO UPDATE WHERE last_event_at < incoming) + version optimistic-lock + 18 source topic (admin-events.md) + No FK constraints between read-model tables 의 정당화 (independent + replayable).
10. **§ 8 Master Reference Projections (V2.1-6)** — 6 *_ref tables (warehouse/zone/location/sku/lot/partner) + master.*.v1 topic projection.
11. **§ 9 ASN + Inspection Summary (V2.7-8)** — admin_asn_summary (inbound.asn.* projection, 1:N line_count denormalised) + admin_inspection_summary (1:1 per ASN, 3-bucket qty roll-up).
12. **§ 10 Order + Shipment Summary (V2.9-10)** — admin_order_summary (outbound.order.* projection, saga_state denormalised) + admin_shipment_summary (outbound.shipping.confirmed projection).
13. **§ 11 admin_inventory_snapshot (V2.11)** — **composite PK with sentinel lot_id** (`00000000-...` for non-LOT-tracked, mirrors V1 admin_setting pattern) + **partial low_stock_flag index** + 4 quantity columns (available + reserved + damaged + on_hand_qty).
14. **§ 12 Append-Only Audit Tables (V2.12-13)** — admin_adjustment_audit (PK = source eventId, INSERT-only via natural dedupe, no trigger needed) + admin_alert_log (PK = eventId + **conditional mutability** ack path only mutates acknowledged_at/acknowledged_by per architecture.md § 1.6 Justification, partial unacked index).
15. **§ 13 Daily Throughput Counters (V2.14-15)** — admin_throughput_inbound_daily + admin_throughput_outbound_daily (date+warehouse_id composite PK, daily roll-up counter).
16. **§ 14 V99 Dev Seed (narrative)** — 4 built-in role UUIDs (`11111111-...` viewer / `22222222-...` operator / etc.) + bootstrap admin user + 7 seed settings. profile-gated (dev/standalone only); prod 는 별도 manual procedure. Stable UUIDs for e2e/dev fixtures.
17. **§ 15 Indexing Strategy Summary** — 모든 인덱스 한 표 catalog (~50+ entries).
18. **§ 16 Migration History** — V1/V2/V99 + V3+ 동기 contract.
19. **References** — Flyway 3 file + domain-model.md + architecture.md + idempotency.md + admin-events.md + 5 sibling database-design.md (inventory BE-157/notification BE-160/master BE-161/inbound BE-162/outbound BE-163) + rules.

예상 크기: ~1000-1200 line (Flyway 592 schema + V99 narrative + 21 table 만의 byte-identical + CQRS pattern explanation).

### B. architecture.md § Persistence cross-link 정정

대상: `projects/wms-platform/specs/services/admin-service/architecture.md` L320-330 부근.

작업: L327 `(full layout in domain-model.md — Open Items)` redirect database-design.md. L418 부근 추가 cross-link 도 정찰 후 정정 가능.

### C. (Out of Scope) architecture.md § Open Items 정리

architecture.md L448 § Open Items list stale 가능성 — BE-152 audit pattern admin 적용은 별 task.

## Out of Scope

- architecture.md § Open Items list 자체의 stale audit (별 task).
- Flyway 변경 0 — schema 1-byte 도 변경 안 함.
- 신규 error code / Kafka topic / API endpoint = 0.

---

# Acceptance Criteria

- [ ] `projects/wms-platform/specs/services/admin-service/database-design.md` 신규 file 존재, 약 1000-1200 line, 16 section 구조.
- [ ] 21 table 모두 reflection (6 write-side V1 + 15 read-model V2).
- [ ] **CQRS read-side LWW upsert pattern 명시** (last_event_at-guarded ON CONFLICT DO UPDATE WHERE).
- [ ] **4-outcome admin_event_dedupe enum** (IGNORED_DUPLICATE_LATE 의 LWW context 명시) 다른 service 의 3-outcome 과 차이.
- [ ] **Sentinel UUID composite PK pattern** (admin_setting + admin_inventory_snapshot + admin_user_role_assignment partial unique COALESCE) 의도 명시.
- [ ] **Append-only via PK=eventId** (admin_adjustment_audit + admin_alert_log) — trigger 없는 natural dedupe pattern + alert_log conditional mutability 정확 명시.
- [ ] **admin_alert_log partial unacked index** + low_stock_flag partial index 명시.
- [ ] V99 narrative section: 4 built-in role UUID + profile gate.
- [ ] architecture.md L327 cross-link 정정.
- [ ] References 모든 cross-link 실재 (~15개: Flyway 3 + sibling 5 spec + 5 sibling database-design + 4 rules/platform).
- [ ] HARDSTOP-03 PASS.
- [ ] grep `(Open Item` projects/wms-platform/specs/services/admin-service/ → 0 (regression 0).
- [ ] production code 변경 = 0, Flyway SQL 변경 = 0.

---

# Related Specs

- `projects/wms-platform/specs/services/admin-service/architecture.md` — § Persistence (L320), § Read-Model Projection Pattern, § Open Items
- `projects/wms-platform/specs/services/admin-service/domain-model.md` — § 1-15 entity 정의 (write + read-model)
- `projects/wms-platform/specs/services/admin-service/idempotency.md` — LWW projection + Kafka 30d eventId dedupe
- `projects/wms-platform/specs/services/admin-service/external-integrations.md` — BE-159 zero-state
- `projects/wms-platform/specs/contracts/events/admin-events.md` — 18 consumed source topic
- `projects/wms-platform/apps/admin-service/src/main/resources/db/migration/V*.sql` — 3 Flyway file
- sibling 5 database-design.md (inventory BE-157 / notification BE-160 / master BE-161 / inbound BE-162 / outbound BE-163)
- `rules/domains/wms.md` — Admin/Operations bounded context
- `rules/traits/transactional.md` — T1, T3, T4, T5, T8

---

# Related Contracts

- N/A — schema spec authoring 만.

---

# Target Service

- `admin-service`

---

# Edge Cases

1. **21 table = portfolio 최대** — V1 6 + V2 15 + 0 trigger function. inbound (18+4) vs admin (21+0). admin 의 append-only 가 trigger 가 아닌 PK=eventId natural dedupe + role grants (ops procedure, Flyway 가 아님) 으로 enforce.
2. **CQRS Layered exception** — Hexagonal 답습 default 가 아니라 Layered (architecture.md § Architecture Style Rationale 인용) — read-heavy CQRS, minimal domain logic. spec authoring 시 sibling Hexagonal pattern 답습 안 함.
3. **Sentinel UUID 3 instance** — admin_setting (warehouse_id), admin_inventory_snapshot (lot_id), admin_user_role_assignment partial unique COALESCE. 동일 sentinel `00000000-0000-0000-0000-000000000000` 사용. JPA composite-id handling simplification 의도 일관 명시.
4. **admin_event_dedupe IGNORED_DUPLICATE_LATE** — 다른 service (inbound/inventory/notification) 의 3-outcome 과 차이. admin 만의 LWW context — projection 에서 last_event_at < incoming 비교 후 stale 인 경우 skip 처리. spec 본문에 LWW 와 cross-reference.
5. **admin_alert_log conditional mutability** — append-only 가 아니라 *conditional* (insert + acknowledge mutation only). architecture.md § 1.6 Justification 인용. trigger 없이 application layer enforce.

---

# Failure Scenarios

1. **21 table byte-identical SQL** — write-side 6 + read-model 15 모두 정확 transcribe.
2. **LWW pattern explanation 정확성** — ON CONFLICT DO UPDATE 의 WHERE 절 + last_event_at 비교 의도 명시. spec 본문 SQL snippet 으로 LWW pattern 예시 가능.
3. **Sentinel UUID pattern 3 instance 누락** — admin_setting + admin_inventory_snapshot + admin_user_role_assignment 모두 명시 + COALESCE 의도 일관 인용.
4. **CRLF vs LF on Windows** — BE-156~163 답습 (LF 작성). hook 검증.
5. **path-filter (TASK-MONO-074/075) markdown-only PASS** — 정상 (BE-156~163 답습).

---

# Validation Plan

- `wc -l` → 1000-1200 line 확인.
- 21 table reflection (CREATE TABLE count match V1+V2 = 6+15).
- LWW + sentinel UUID + 4-outcome dedupe 모두 명시 확인.
- partial index 5+ catalog (uq_admin_user_email_ci + uq_admin_assignment_active + low_stock + alert_log unacked + outbox pending).
- `grep -rn "(Open Item" projects/wms-platform/specs/services/admin-service/` → 0.
- architecture.md L327 cross-link 정정.
- 모든 cross-reference (~15개) `ls` 실재 확인.
- CI = path-filter → ~1 PASS + 15 SKIP 예상.

---

# Implementation Notes

- BE-141~163 single-PR closure 답습 — ready → done.
- D4 OVERRIDE: ADR-MONO-003a § D1.1 (project-internal spec backfill).
- **portfolio-wide database-design.md gap 완전 종결** — 6/6 service (잔존 gateway = N/A schemaless) 모두 database-design.md 보유.
- 분석=Opus 4.7 / 구현 권장=Opus 4.7 (BE-157~163 답습 sixth + final-of-portfolio + 21 table 최대 + CQRS Layered exception + 3 sentinel UUID pattern + 4-outcome IGNORED_DUPLICATE_LATE + append-only via PK=eventId natural dedupe + conditional mutability alert_log).
