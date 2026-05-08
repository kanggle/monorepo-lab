# Task ID

TASK-BE-046

# Title

admin-service read-model projection — 18 source-topic Kafka consumer + 15 read-side table + dashboard / ops REST + read-model rebuild verification (BE-045 후속, CQRS read-side 완성)

# Status

ready

# Owner

backend

# Task Tags

- code
- spec

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

[TASK-BE-045](TASK-BE-045-admin-service-bootstrap.md) 가 admin-service v1 의 **write-side** (Layered 모듈 골격 + 4 aggregate + REST § 2-5 + outbox) 를 완성. 본 task 는 그 위에 **read-side** (CQRS read-model projection) 를 완성하여 admin-service v1 종결을 만든다.

**구체 목표**: admin-service 의 핵심 가치 = "다른 6 wms service 의 모든 도메인 이벤트를 구독해 dashboard / KPI 쿼리에 최적화된 read-model 을 유지" — 이 끝-to-끝 경로를 완성. 즉

```
6 sibling service (master / inbound / outbound / inventory) →
  18 source topic →
  4 *ProjectionConsumer (per-source-service grouping) →
  EventDedupePort + last_event_at LWW (T8 + idempotency.md § 2) →
  15 read-side table (12 unique entity + 2 throughput counter + 1 alert acknowledgement) →
  /api/v1/admin/dashboard/** 7 surface (admin-service-api.md § 1) +
  /api/v1/admin/operations/projection-status (§ 6.2) +
  alert acknowledge mutation (§ 1.6)
```

선행 = BE-045 머지 (필수). 본 task 는 BE-045 의 Out of Scope 에 명시된 모든 read-side 영역을 인수.

---

# Scope

## In Scope

### 1. Read-side Flyway

`V2__init_readmodel.sql` — `domain-model.md § 5-13` 의 read-side 테이블 신설:

- **§ 5 MasterRef (6 table)**: `admin_warehouse_ref` / `admin_zone_ref` / `admin_location_ref` / `admin_sku_ref` / `admin_lot_ref` / `admin_partner_ref` — 각각 source-service 의 master event 가 upsert
- **§ 6 AsnSummary** (`admin_asn_summary`) — 1 row per ASN
- **§ 7 InspectionSummary** (`admin_inspection_summary`) — 1:1 per ASN
- **§ 8 OrderSummary** (`admin_order_summary`)
- **§ 9 ShipmentSummary** (`admin_shipment_summary`)
- **§ 10 InventorySnapshot** (`admin_inventory_snapshot`) — primary dashboard data source
- **§ 11 AdjustmentAudit** (`admin_adjustment_audit`) — append-only, PK = source eventId
- **§ 12 AlertLog** (`admin_alert_log`) — append-only + `acknowledged_at`/`acknowledged_by` mutation 1 (admin-service application layer 만, 다른 read-model 테이블엔 없는 예외)
- **§ 13 ThroughputDaily (2 table)**: `admin_throughput_inbound_daily` / `admin_throughput_outbound_daily` — `INSERT ... ON CONFLICT DO UPDATE` 원자 increment

**모든 JSONB 컬럼 + indexes + unique constraints 명시**. 모든 read-model 행은 `last_event_at` (Instant) + `version` (Long) 컬럼 보유 (LWW + 동시 projection conflict 안전망).

### 2. Read-model entities + repositories (`com.wms.admin.readmodel.*`)

`architecture.md § Package Structure` 의 `readmodel/{master,inbound,outbound,inventory,alert}` 분류 그대로:

```
com.wms.admin.readmodel/
├── master/{Warehouse,Zone,Location,Sku,Lot,Partner}Ref{Entity,Repository}
├── inbound/{AsnSummary,InspectionSummary}{Entity,Repository}
├── outbound/{OrderSummary,ShipmentSummary}{Entity,Repository}
├── inventory/{InventorySnapshot,AdjustmentAudit}{Entity,Repository}
├── alert/AlertLog{Entity,Repository}
└── throughput/Throughput{Inbound,Outbound}Daily{Entity,Repository}
```

전체 ~15 entity + ~15 repository. **모든 JSONB 컬럼 `@JdbcTypeCode(SqlTypes.JSON)`** — TASK-SCM-INT-001b + TASK-SCM-BE-005 + TASK-BE-043 + TASK-BE-045 회귀가드 학습 답습.

read-model entity 는 query 응답에 직접 사용 (DTO 매핑 후) — `architecture.md § Layer Rules § 4` "Read-model entities are JPA entities used directly in query responses (after DTO mapping). No separate domain model layer for projections — intentional simplification." 그대로.

### 3. Projection consumers (`com.wms.admin.application.projection.*`)

`architecture.md § Read-Model Projection Pattern` 의 service-별 consumer class 분류 (architecture 가 명시한 단일 클래스 패턴):

```
com.wms.admin.application.projection/
├── MasterProjectionService    — wms.master.{warehouse,zone,location,sku,partner,lot}.v1 (6)
├── InboundProjectionService   — wms.inbound.{asn,inspection.completed,putaway.completed}.v1 (3)
├── OutboundProjectionService  — wms.outbound.{order,shipping.confirmed}.v1 (2)
└── InventoryProjectionService — wms.inventory.{received,adjusted,transferred,reserved,released,confirmed,alert}.v1 (7)
```

**총 18 source topic** (admin-events.md § Consumed Events 카탈로그 확정). 4 consumer class 안에서 `eventType` 별 method 분기.

각 projection 메서드는 **한 `@Transactional`** boundary 안에서:

```
1. INSERT INTO admin_event_dedupe (event_id, event_type, processed_at, outcome=APPLIED)
   ON CONFLICT (event_id) DO NOTHING RETURNING ...
2a. row inserted:
    - 기존 read-model row 의 last_event_at < event.occurredAt 인 경우만 mutate
      (upsert / append / increment)
    - last_event_at >= event.occurredAt: UPDATE dedupe row outcome=IGNORED_DUPLICATE_LATE
2b. no row returned (eventId 충돌):
    - skip mutation
    - log "Duplicate event skipped"
```

특수 케이스:

- **Append-only table** (`admin_adjustment_audit` + `admin_alert_log`): PK = source eventId. `INSERT ... ON CONFLICT (id) DO NOTHING` 가 dedupe 와 동등. last_event_at 미적용 (monotonic).
- **Throughput counter**: `INSERT ... ON CONFLICT (date, warehouse_id) DO UPDATE` 의 WHERE 절에 `last_event_at < EXCLUDED.last_event_at` 가드 — out-of-order 안전.
- **`master.location.v1` 의 cross-aggregate denorm**: source `master.location.created` 가 도착했는데 `admin_warehouse_ref` 가 아직 없으면 → location row 는 만들되 `warehouse_code` denorm 컬럼 null 허용 (architecture.md § Out-of-Order Event Handling). 후속 reconciliation 은 v2.

### 4. Read-side REST endpoints (`com.wms.admin.api.dashboard.*`)

`admin-service-api.md § 1` 그대로:

| 컨트롤러 | 엔드포인트 |
|---|---|
| `InventoryDashboardController` | `GET /api/v1/admin/dashboard/inventory` (§ 1.1) + `GET /by-key` |
| `ThroughputDashboardController` | `GET /api/v1/admin/dashboard/throughput` (§ 1.2) — 90-day max 가드 |
| `OrderDashboardController` | `GET /api/v1/admin/dashboard/orders` (§ 1.3) |
| `ShipmentDashboardController` | `GET /api/v1/admin/dashboard/shipments` (§ 1.3) |
| `AsnDashboardController` | `GET /api/v1/admin/dashboard/asns` (§ 1.4) + `GET /asns/{asnId}/inspection` |
| `AdjustmentAuditController` | `GET /api/v1/admin/dashboard/adjustments` (§ 1.5) |
| `AlertDashboardController` | `GET /api/v1/admin/dashboard/alerts` (§ 1.6) + `POST /alerts/{alertId}/acknowledge` |
| `MasterRefController` | `GET /api/v1/admin/dashboard/refs/{type}` (§ 1.7) — `{type}` ∈ warehouses\|zones\|locations\|skus\|lots\|partners |

모두 read paths — `Idempotency-Key` 미요구. 페이지네이션 `architecture.md` 그대로.

**예외 한 곳 — `POST /alerts/{alertId}/acknowledge`** (§ 1.6 second endpoint): read-model write 의 유일한 application-layer 경로. `architecture.md § Forbidden Patterns` 의 "writing read-model tables from REST controllers or application write-path code — projection consumers only" 규칙의 **명시적 예외** (architecture.md § 1.6 Justification 그대로). `acknowledged_at`/`acknowledged_by` 두 컬럼만 mutate. `Idempotency-Key` 강제.

### 5. Operations endpoint

`admin-service-api.md § 6.2`:

- `GET /api/v1/admin/operations/projection-status` — 모든 consumer group 의 lag + lifetime apply/IGNORED/FAILED 카운트 + worstLagSeconds
  - 데이터 소스: `KafkaListenerEndpointRegistry` + `admin_event_dedupe` 집계 + Micrometer 메트릭
  - 응답 shape: api.md § 6.2 그대로
  - `WMS_ADMIN+` 접근

`runbooks/read-model-rebuild.md` 의 "Step 6 Monitor Catch-Up" 검증 입력으로 사용. `--rerun-tasks` 시 expected.

### 6. Authorization 강화

- 모든 dashboard endpoint: `WMS_VIEWER` 이상 (Spring Security method-level `@PreAuthorize`)
- `POST /alerts/{alertId}/acknowledge`: `WMS_OPERATOR` 이상 + 본인 warehouse scope 검증 (application-layer enforce)
- `GET /operations/projection-status`: `WMS_ADMIN` 이상

`SecurityConfig` 는 BE-045 의 wiring 재사용. 추가 path 만 등록.

### 7. Observability 메트릭

`architecture.md § Observability`:

- `admin.projection.lag.seconds{source_service,topic}` — event time → applied time
- `admin.projection.dropped.count{reason=stale|duplicate}` — late-event drops + dedupe hits
- `admin.projection.error.count{topic}` — exception rate per consumer
- `admin.query.latency.p95{endpoint}` — slow dashboard 가시화
- `admin.query.cache.hit.rate` — Redis 쿼리 캐시 효과 (v1 미적용 시 항상 0, 향후 v2)

각 projection method 의 try/finally 에 lag/dropped 메트릭 emit. 메트릭 등록은 `MeterRegistry` 직접.

### 8. Idempotency / DLT 동작 검증

`idempotency.md § 2` (Kafka 30d eventId dedupe + last_event_at LWW + append-only PK 안전망 + throughput counter 안전망) 그대로.

- `DefaultErrorHandler` exponential backoff (1s/2s/4s) + DLT routing — `<source-topic>.DLT`
- non-retryable: `JsonProcessingException`, `IllegalArgumentException` (enum), 커스텀 `UnknownEventTypeException`
- DLT replay 30일 임계값 — append-only PK + last_event_at 가드가 mostly absorb (`idempotency.md § 3.1` 그대로)

### 9. Tests (≥ 60 — BE-045 보다 큼; 18 consumer × IT + 7 dashboard REST + LWW + dedupe + replay)

- **Unit (≥ 25)**:
  - `MasterProjectionService` × 6 topic happy + 1 dedupe + 1 LWW (≥ 8)
  - `InboundProjectionService` × 3 topic happy + 1 LWW + 1 inspection 1:1 (≥ 5)
  - `OutboundProjectionService` × 2 topic happy + 1 throughput increment (≥ 3)
  - `InventoryProjectionService` × 7 topic happy + 1 transfer dual-write + 1 alert append + 1 throughput (≥ 9)
- **Application slice (port fakes)**:
  - 4 ProjectionService × dedupe-hit + LWW-stale 시나리오
  - acknowledge alert mutation
- **Persistence adapter (Testcontainers Postgres)**:
  - 15 entity round-trip
  - JSONB column `@JdbcTypeCode(SqlTypes.JSON)` 회귀가드 (BE-043 / BE-045 패턴 답습)
  - V2 마이그레이션 적용 + read-model row 시드 검증
- **REST controller (`@WebMvcTest` + `@MockitoBean`)**:
  - 7 dashboard endpoint × happy + (≥ 1 error per controller)
  - Authorization 매트릭스 (VIEWER / OPERATOR / ADMIN / SUPERADMIN)
  - acknowledge alert 멱등성
  - `/operations/projection-status` 응답 shape
- **Integration (Testcontainers Postgres + Kafka)**:
  - 4 ProjectionService × happy path × 18 topic 분배 (per-topic round-trip ≥ 18)
  - dedupe-hit 시나리오 (eventId 중복) ≥ 1
  - LWW-stale 시나리오 (out-of-order event drop) ≥ 1
  - throughput counter increment idempotency ≥ 1
  - alert acknowledge 후 dashboard 응답 반영 ≥ 1
  - **Replay test**: consume same offset range twice → identical read-model state ≥ 1 (`runbooks/read-model-rebuild.md § Step 6` 입력)
- **Contract test**: 4 published `admin.*` event (BE-045 에서 이미 다루지만 본 task 는 consumed event schema cross-link 만 추가)

총 ≥ 60.

### 10. Spec / contracts cross-link

- 본 PR 은 spec 변경 0 (admin-service spec 4 file 모두 PR #273 머지 완료). 단 cross-reference 업데이트:
  - `architecture.md § Open Items` — projection runbook execution 검증 추가 ack (이미 ✅)
  - `domain-model.md § Open Items` — 동일

선택적 보강 (impl 중 발견 시): admin-events.md 의 `aggregateId` 매핑 정정, `admin-service-api.md § 1.7 MasterRefController` path-variable 매핑 등.

## Out of Scope (향후 v2 또는 별 task)

- read-model row 의 nullable denorm 컬럼 reconciliation 배치 (architecture.md § Extensibility — v2)
- routing-rule TTL cache (admin v2 edit volume)
- Redis query cache (`admin.query.cache.hit.rate` 가 v1 항상 0)
- approval workflow (architecture.md § Extensibility — v2 add 시 Layered → Hexagonal 재평가)
- multi-tenant + SSO/SCIM sync + time-series TimescaleDB hypertable
- read-model rebuild 자동화 (현재 manual ops procedure)

---

# Acceptance Criteria

- [ ] AC-01 — Flyway V2 마이그레이션 적용 시 15 read-side 테이블 생성 (6 MasterRef + AsnSummary + InspectionSummary + OrderSummary + ShipmentSummary + InventorySnapshot + AdjustmentAudit + AlertLog + ThroughputInboundDaily + ThroughputOutboundDaily). 모든 JSONB 컬럼 + last_event_at + version + indexes + unique constraints 적용.
- [ ] AC-02 — `readmodel/{master,inbound,outbound,inventory,alert,throughput}/` 패키지 구조. 15 entity + 15 repository. 모든 JSONB 컬럼 `@JdbcTypeCode(SqlTypes.JSON)` 회귀가드 자동 검증.
- [ ] AC-03 — 4 *ProjectionService class (`Master` / `Inbound` / `Outbound` / `Inventory`). 18 source topic 분배 + eventType 별 method 분기. `admin-projection` 단일 consumer group.
- [ ] AC-04 — 각 projection method 가 `@Transactional` 안에서: dedupe insert + last_event_at 가드 + read-model mutation 일관 실행. Append-only / Upsert / Throughput counter 3 패턴 모두 idempotent.
- [ ] AC-05 — `admin-service-api.md § 1` 의 7 dashboard 엔드포인트 (총 ~12 method) 구현. 페이지네이션 / 필터 / authorization 매트릭스 매칭.
- [ ] AC-06 — `POST /api/v1/admin/dashboard/alerts/{alertId}/acknowledge` 의 application-layer write 1 건. `Idempotency-Key` 멱등성. `STATE_TRANSITION_INVALID` (이미 acknowledged) + `NOT_FOUND` 처리.
- [ ] AC-07 — `GET /api/v1/admin/operations/projection-status` 구현. KafkaListenerEndpointRegistry + admin_event_dedupe 집계 + Micrometer 메트릭 통합.
- [ ] AC-08 — Spring Security 매트릭스: dashboard read = `WMS_VIEWER+` / alert acknowledge = `WMS_OPERATOR+` (warehouse scope) / projection-status = `WMS_ADMIN+`. application-layer enforce.
- [ ] AC-09 — Observability 메트릭 5 (projection.lag / projection.dropped / projection.error / query.latency / query.cache.hit) 등록 + emit.
- [ ] AC-10 — DLT 라우팅 (`<source-topic>.DLT`) + non-retryable exception list 적용. exponential backoff (1s/2s/4s) + 3 retries.
- [ ] AC-11 — 테스트 ≥ 60 (Unit ≥25 + slice + persistence + REST + integration ≥18 topic + replay test). 로컬 PASS (Rancher env blocker 케이스 `--rerun-tasks` 검증).
- [ ] AC-12 — CI Build & Test (JDK 21, Linux) + Package boot jars (wms) job 회귀 0.
- [ ] AC-13 — 다른 wms service 회귀 0 (`master` / `inventory` / `inbound` / `outbound` / `gateway` / `notification`). admin-service write 경로 (BE-045) 회귀 0.
- [ ] AC-14 — D4 churn freeze 면제 카테고리만 변경 — `apps/admin-service/**` (project-internal). shared 영역 (`libs/`/`platform/`/`rules/`/`.claude/`) 변경 0. spec/contract 보강은 선택적 (impl 중 발견 시 minor fix only).

---

# Related Specs

- [`projects/wms-platform/specs/services/admin-service/architecture.md`](../../specs/services/admin-service/architecture.md) — § Read-Model Projection Pattern, § Read-Model Rebuild Procedure, § Observability
- [`projects/wms-platform/specs/services/admin-service/domain-model.md`](../../specs/services/admin-service/domain-model.md) — § 5-13 (15 read-side table)
- [`projects/wms-platform/specs/services/admin-service/idempotency.md`](../../specs/services/admin-service/idempotency.md) — § 2 (Kafka 30d eventId dedupe + last_event_at LWW + append-only PK 안전망)
- [`projects/wms-platform/specs/services/admin-service/runbooks/read-model-rebuild.md`](../../specs/services/admin-service/runbooks/read-model-rebuild.md) — 9-step ops procedure (replay test 의 verification 입력)
- [`projects/wms-platform/tasks/in-progress/TASK-BE-045-admin-service-bootstrap.md`](TASK-BE-045-admin-service-bootstrap.md) — 선행 (write-side)

# Related Contracts

- [`projects/wms-platform/specs/contracts/http/admin-service-api.md`](../../specs/contracts/http/admin-service-api.md) — § 1 Dashboard / § 6.2 Operations / § 1.6 alert acknowledge mutation
- [`projects/wms-platform/specs/contracts/events/admin-events.md`](../../specs/contracts/events/admin-events.md) — § Consumed Events 18 topic
- [`projects/wms-platform/specs/contracts/events/master-events.md`](../../specs/contracts/events/master-events.md), [`inbound-events.md`](../../specs/contracts/events/inbound-events.md), [`outbound-events.md`](../../specs/contracts/events/outbound-events.md), [`inventory-events.md`](../../specs/contracts/events/inventory-events.md) — source schema (cross-link, ownership 은 source service)

---

# Edge Cases

- **Cross-aggregate denorm out-of-order**: 예) `inventory.received` 가 `master.location.created` 보다 먼저 도착 → `inventory_snapshot.location_code` denorm 컬럼 null 가능. nullable + 후속 reconciliation 배치 (v2). ID FK 는 항상 채움.
- **Lot 미적용 SKU**: `lot_id` null 의 dedupe key — `(location_id, sku_id, lot_id IS NOT DISTINCT FROM ?)` 패턴 (PostgreSQL idiom).
- **Throughput 30d 후 replay**: 30일 dedupe TTL 만료 + last_event_at 가드 우회 시나리오 — counter 의 double-increment 위험. mitigation: `idempotency.md § 3.1` 의 ops 가이드 (rebuild procedure 권장).
- **Alert acknowledge 후 read-model rebuild**: rebuild 시 `acknowledged_at`/`acknowledged_by` 손실 (admin-owned, source 이벤트 derivable 아님). `read-model-rebuild.md § Acknowledgement Preservation` 의 optional 단계로 백업/복원. 본 task 는 backup script 작성까지만, automation 은 v2.
- **Dashboard 90-day range 가드**: throughput query 가 `to - from > 90` 인 경우 400 `VALIDATION_ERROR`. spec 그대로.
- **`X-Read-Model-Lag-Seconds` 헤더**: dashboard 응답에 `worstLagSeconds > 5` 일 때만 emit (api.md § Headers). 미superfluous.

# Failure Scenarios

- **Projection 메서드 internal exception**: `@Transactional` rollback → dedupe row 미작성 → 다음 delivery 가 retry. 3-retry 후 DLT.
- **DLT replay 후 dedupe 충돌**: dedupe row 가 이미 outcome=APPLIED → mutation skip + log. 안전.
- **Consumer group rebalance 도중 partition 누락**: Kafka 가 보장 (one consumer per partition). 본 task 는 production 그대로 수용.
- **DB connection pool 고갈**: `@Transactional` 시점 → DataAccessResourceFailureException → DefaultErrorHandler 가 retry. backoff 후 회복.
- **PostgreSQL upsert deadlock** (`InventorySnapshot` 의 transfer dual-write): id-ascending lock order 강제 (sibling `inventory-service.TransferStockService` 패턴 답습). 본 task 도 source/target row id 비교 후 작은 id 먼저.
- **read-model rebuild 도중 새 이벤트 유입**: rebuild 절차 § Step 2 가 consumer 0 으로 scale → 신규 이벤트 broker 에 누적. Step 5 scale-up 후 catch-up. 정상.

---

# Notes

- **모델 권장**: 분석=Opus 4.7 / 구현=Opus 4.7 — 18 source-topic CQRS read-side projection + 15 read table + 7 dashboard REST + ops endpoint + LWW + dedupe + DLT 회귀가드 = complex domain work. Sonnet 으로 진행 시 cross-aggregate denorm out-of-order / throughput counter idempotency / append-only vs upsert 패턴 분리 같은 invariant subtle 누락 위험.
- **PR 분할 대안**: 본 task 가 너무 커지면 (예 ≥ 80 file) 4 sub-task 로 분할 가능 — (a) MasterProjection + 6 MasterRef table + dashboard refs, (b) Inbound + Outbound projection + 4 summary table + 2 dashboard, (c) Inventory projection + InventorySnapshot + AdjustmentAudit + 2 dashboard, (d) AlertLog + Throughput + ops endpoint + replay test. 단일 PR 권장 (review 응집성 + 회귀가드 통합).
- **D4 churn freeze 면제 근거**: `apps/admin-service/**` project-internal + shared 영역 변경 0 (BE-046 spec 본문 보강 시점은 minor fix 한도). spec 입력 (admin spec 4 file + admin-events 18 topic + admin-service-api § 1+6.2) 모두 main 에 있음.
- **연관 메모리**: `project_admin_service_first_be_filed` (이건 신규 메모리 작성 권장 — BE-045/046 시리즈의 portfolio 내 위상 추적), `project_046_series_close` (sibling bootstrap 패턴), `project_scm_be_series_in_progress` (cross-service consumer + JSONB 회귀가드 학습), `project_046_8_phase_0_partial` (env blocker case).
- **선행/후속**:
  - 선행 = TASK-BE-045 (ready/, PR #277 머지 완료)
  - 후속 = (a) read-model 자동 reconciliation 배치 (v2), (b) Redis query cache (v2), (c) approval workflow (architecture.md § expiry trigger 시 Layered → Hexagonal 재평가)
