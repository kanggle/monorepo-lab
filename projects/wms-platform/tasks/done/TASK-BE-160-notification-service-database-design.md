# Task ID

TASK-BE-160

# Title

notification-service `database-design.md` retrospective authoring + `architecture.md § Persistence` cross-link 정정 (portfolio-wide database-design.md gap 5 service 중 #1 closure)

# Status

ready

# Owner

wms-platform

# Task Tags

- wms
- notification-service
- spec
- backfill
- database
- be

---

# Goal

[TASK-BE-157](../done/TASK-BE-157-inventory-service-database-design.md) inventory database-design retrospective (Flyway V1-V5, 398 → 650 line spec) 답습 — `notification-service/database-design.md` 신규 retrospective spec authoring. **portfolio-wide database-design.md gap 5 service 중 #1 closure**.

배경:

- notification-service Flyway = 194 line / 2 file:
  - **V1__init.sql** (117 line): 4 table — `notification_routing_rule` + `notification_delivery` + `notification_event_dedupe` + `notification_outbox`.
  - **V2__seed_routing_rules.sql** (77 line): seed 6 routing rules data (INSERTs, schema 0).
- architecture.md L314 = "Tables (full layout in `domain-model.md` — Open Items)" stale reference — domain-model.md 에 full table layout 없음. database-design.md 신규 작성 후 redirect 정정.
- BE-157 답습 first-of-portfolio pattern (sibling 답습 가능 — inventory database-design.md, 650 line, 10 section). notification 은 Flyway 작아 ~300-400 line 예상.

본 task = **신규 retrospective spec authoring + architecture.md L314 cross-link 1줄 정정** (production code 0, Flyway SQL 0, application.yml 0, markdown only). BE-141~159 single-PR closure 답습.

---

# Scope

## In Scope

### A. 신규 `notification-service/database-design.md`

대상 경로: `projects/wms-platform/specs/services/notification-service/database-design.md` (신규).

구조 (BE-157 inventory database-design.md 답습, ~10 section):

1. **헤더 + intent** — retrospective backfill scope 명시 + Flyway V1+V2 가 source-of-truth + 본 file 은 reflection (V3+ 추가 시 동기 갱신 의무).
2. **Schema Overview** — 4 table dependency graph (ASCII).
3. **NotificationRoutingRule (V1, domain-model § Persistence Layout)** — table + matcher_json/channel_targets_json JSONB + severity enum + partial unique index (`WHERE enabled=true`).
4. **NotificationDelivery (V1)** — table + state machine SQL constraint (`PENDING → SUCCEEDED|FAILED`) + delivery_idempotency_key UNIQUE + hot-path partial index for retry scheduler (`WHERE status='PENDING'`) + event_id index + version optimistic locking.
5. **NotificationEventDedupe (V1, T8)** — table + outcome enum (QUEUED/FILTERED/NO_RULE/ERROR) + processed_at retention index (30-day sweeper v2).
6. **NotificationOutbox (V1, T3)** — table + pending-publisher partial index (`WHERE published_at IS NULL`) + aggregate index. service-local outbox (NOT libs/java-messaging base, JSONB payload + partition_key 요구).
7. **V2 Seeded Routing Rules** — 6 row seed (event_type / matcher / channel_target / severity) byte-identical references + architecture.md § Routing Rules table cross-reference.
8. **Indexing Strategy Summary** — 모든 인덱스 (PK + UNIQUE + partial + btree) 한 표 catalog.
9. **Migration History** — V1, V2 + 향후 V3+ 동기 갱신 의무 명시.
10. **References** — Flyway file paths + domain-model.md + architecture.md + idempotency.md + sibling 4 service (incl. BE-157 inventory) database-design.md + rules/traits/transactional.md + integration-heavy.md.

예상 크기: ~280-380 line (inventory 650 의 ~50%, Flyway 0.5x ratio).

### B. architecture.md L314 cross-link 정정

대상: `projects/wms-platform/specs/services/notification-service/architecture.md` L313-314.

작업:
- 기존: `Tables (full layout in \`domain-model.md\` — Open Items):` 
- 정정: `Tables (full layout in [\`database-design.md\`](database-design.md)):`
- 4-table list (L315-321) 은 high-level reference 로 유지 (database-design.md 가 wire-level detail).

### C. (Out of Scope) architecture.md § Open Items 자체 정리

architecture.md L451 의 `## Open Items (Before First Implementation Task)` list 가 stale (items 1, 2, 3, 6 = 이미 작성된 file 들). 그러나 BE-152 audit pattern (notification-service 에 적용 안 됨) 이라 별 task 후보. **본 task scope 밖**.

## Out of Scope

- architecture.md § Open Items list 자체의 stale audit + 정리 (BE-152 답습 notification 적용, 별 task).
- 다른 4 service (master/inbound/outbound/admin) database-design.md authoring (BE-160-N 후속 single-task 각각).
- Flyway 변경 0 — schema 1-byte 도 변경 안 함. spec/markdown only.
- 신규 error code / Kafka topic / API endpoint = 0.

---

# Acceptance Criteria

- [ ] `projects/wms-platform/specs/services/notification-service/database-design.md` 신규 file 존재, 약 280-380 line, 10 section 구조.
- [ ] 4 SQL table entity 모두 file 안에 reflection (notification_routing_rule + notification_delivery + notification_event_dedupe + notification_outbox).
- [ ] V2 6 seed row catalog (event_type / matcher type / channel_id / severity) 명시.
- [ ] state-machine SQL constraint (PENDING → SUCCEEDED|FAILED) 및 partial unique index (`WHERE enabled=true` + `WHERE status='PENDING'` + `WHERE published_at IS NULL`) 의도 명시.
- [ ] architecture.md L313-314 cross-link 정정 (`domain-model.md — Open Items` → `database-design.md`).
- [ ] References section 의 모든 cross-link 실재 (~10개: Flyway 2 file + 4 sibling spec + 2 rules/traits + platform + sibling 1 BE-157 inventory).
- [ ] HARDSTOP-03 PASS — 신규 file 이 wms-specific (project-internal).
- [ ] grep `(Open Item` projects/wms-platform/specs/services/notification-service/ → 0 (regression 0).
- [ ] production code 변경 = 0, Flyway SQL 변경 = 0 (git diff `--stat` 으로 spec + INDEX + task file 만 변경 확인).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md` (`domain: wms`, `traits: [transactional, integration-heavy]`), then load `rules/common.md` + `rules/domains/wms.md` + `rules/traits/transactional.md` + `rules/traits/integration-heavy.md`. Service Type 은 `notification-service/architecture.md § Identity` 의 `event-consumer` (pure v1).

- `projects/wms-platform/specs/services/notification-service/architecture.md` — § Persistence (L310-321) + § Invariants + § Open Items (참고만, 본 task 정정 대상 아님)
- `projects/wms-platform/specs/services/notification-service/domain-model.md` — Persistence Layout, RoutingRule / NotificationDelivery / Alert entity 정의
- `projects/wms-platform/specs/services/notification-service/idempotency.md` — T8 dedupe + delivery_idempotency_key
- `projects/wms-platform/specs/services/notification-service/external-integrations.md` — BE-158, Slack adapter
- `projects/wms-platform/apps/notification-service/src/main/resources/db/migration/V1__init.sql` — 117 line, 4 table
- `projects/wms-platform/apps/notification-service/src/main/resources/db/migration/V2__seed_routing_rules.sql` — 77 line, 6 seed row
- `projects/wms-platform/specs/services/inventory-service/database-design.md` — sibling reference (BE-157, primary template)
- `rules/traits/transactional.md` — T3 (outbox), T4 (state machine), T8 (event-dedupe)
- `rules/traits/integration-heavy.md` — I1-I5 (DLQ retry + delivery)
- `platform/architecture.md` — system-level architecture

# Related Skills

- `.claude/skills/INDEX.md`

---

# Related Contracts

- N/A — schema spec authoring 만. 신규 API/event/webhook contract = 0.

---

# Target Service

- `notification-service`

---

# Edge Cases

1. **Flyway 가 향후 V3 추가 시 본 file 의 drift** — BE-157 inventory 와 동일 retrospective contract 명시 (§ Migration History 에 "V3+ lands → must update this file in the same commit").
2. **V2 seed row 의 byte-identical preservation** — UUIDs / matcher_json / channel_targets_json 6 row 모두 architecture.md § Routing Rules (L169-174) 와 정합 검증.
3. **JSONB column @JdbcTypeCode(SqlTypes.JSON) JPA 매핑** — V1__init.sql 의 line 12-14 inline 주석 (TASK-SCM-INT-001b root cause #2 + TASK-SCM-BE-005 regression-guard learning) 가 important context. spec 본문에 cross-reference 명시 (JsonbColumnRegressionGuardTest 가 build-time 강제).
4. **partial unique index `WHERE enabled = true`** — operator 가 routing rule 을 swap 할 때 임시 `enabled=false` row 가 생길 수 있는 패턴. SQL 의도 명시 (single enabled rule per event_type).
5. **state machine SQL constraint** (`PENDING → SUCCEEDED|FAILED`) 는 SQL-level enum check 만 — 실제 transition rule 은 domain code 가 enforce. 본 spec 은 SQL 의도만 명시.

---

# Failure Scenarios

1. **Flyway 와 본 file 의 byte-level inconsistency** — table 컬럼 이름 / 타입 / 제약 조건 / index name 등 모두 byte-identical 검증 필수. 각 section 의 SQL DDL snippet 은 Flyway 파일에서 그대로 복사 (변경 0).
2. **architecture.md L314 cross-link 정정 시 sentence 구조 broken** — L313-321 block 의 들여쓰기 / bullet 보존. Edit 후 즉시 Read 검증.
3. **V2 seed UUIDs 7-prefix (UUIDv7 시간 기반) byte-identical** — `00000000-0000-7000-8000-00000000000{1-6}` 가 placeholder UUID. 본 spec 본문에 명시.
4. **CRLF vs LF on Windows** — BE-156/157/158/159 답습 (LF 작성). hook 검증.
5. **path-filter (TASK-MONO-074/075) markdown-only PASS** — 정상 (BE-156~159 답습).

---

# Validation Plan

- `ls projects/wms-platform/specs/services/notification-service/database-design.md` → file 존재 확인.
- `wc -l` → 280-380 line 범위 확인.
- 모든 V1+V2 의 CREATE TABLE statement 가 본 file 에 reflection (4 table count match).
- partial unique index 3개 + 모든 named CHECK constraint 가 본 file 의 SQL snippet 안에 포함.
- `grep -rn "(Open Item" projects/wms-platform/specs/services/notification-service/` → 0 (regression 0).
- architecture.md L314 cross-link 정정 byte-identical 확인.
- 모든 cross-reference (~10개) `ls` 으로 실재 확인.
- CI = path-filter (TASK-MONO-074/075) → markdown-only path 로 ~1 PASS + 15 SKIP 예상.

---

# Implementation Notes

- BE-141~159 single-PR closure 답습 — ready → done.
- D4 OVERRIDE: ADR-MONO-003a § D1.1 (project-internal spec backfill).
- **portfolio-wide database-design.md gap 5 service 중 #1 closure** — 잔존 4 service (master/inbound/outbound/admin) 별 task 후보 (BE-161/162/163/164 또는 batch).
- 분석=Opus 4.7 / 구현 권장=Opus 4.7 (BE-157 답습 retrospective spec authoring + byte-identical SQL transcribe + sibling pattern 답습 second-of-portfolio).
