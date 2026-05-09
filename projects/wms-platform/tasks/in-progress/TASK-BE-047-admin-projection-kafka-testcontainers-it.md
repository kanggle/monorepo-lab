# Task ID

TASK-BE-047

# Title

admin-service projection Kafka Testcontainers IT — 4 ProjectionConsumer × 18 source-topic round-trip + dedupe-hit + LWW-stale + DLT routing + replay test (BE-046 deviation #1 closure)

# Status

ready

# Owner

backend / qa

# Task Tags

- code
- test

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

[TASK-BE-046](../done/TASK-BE-046-admin-service-readmodel-projection.md) PR #282 가 admin-service v1 의 read-side 를 main 진입시켰지만 정직 보고 8건 중 가장 큰 gap = **Kafka Testcontainers IT 미포함** (deviation #1). 본 task 는 그 gap 을 메워 admin-service v1 의 read-side 신뢰도를 production-ready 수준으로 끌어올린다.

**구체 목표**: BE-046 의 4 ProjectionConsumer (MasterProjectionConsumer / InboundProjectionConsumer / OutboundProjectionConsumer / InventoryProjectionConsumer) 가 18 source topic 를 끝-to-끝 처리하는지 — Kafka producer 가 publish → consumer 가 receive → ProjectionService 가 dedupe + LWW guard + read-model mutation → DB 에 정상 반영 — 의 끝-to-끝 경로를 Testcontainers (Postgres + Kafka) 로 검증. BE-046 의 Mockito unit + Postgres persistence IT 가 커버 안 한 영역:

- Kafka 직렬화 / 역직렬화 (`String → ProjectionEnvelope` via Jackson)
- `@KafkaListener` annotation wiring 정상 동작
- consumer group `admin-projection` partition assignment + offset 관리
- `DefaultErrorHandler` exponential backoff (1s/2s/4s × 3) 동작
- DLT routing (`<topic>.DLT` 발행 + 헤더 보존)
- non-retryable exception list (`JsonProcessingException` / `IllegalArgumentException` / `UnknownEventTypeException`) 즉시 DLT
- replay scenario (`runbooks/read-model-rebuild.md § Step 6` verification — consume same offset range twice → identical state)

**전제**: CI Linux runner 또는 안정적 Docker 환경. 메모리 `project_testcontainers_docker_desktop_blocker` 의 Rancher v29.1.3 cold-start regression 이 회복돼야 로컬 burn 가능. 단 BE-045 + BE-046 의 IT 가 CI Linux 에서 deterministic PASS 한 사례로 동일 환경에서 본 task 도 가능.

---

# Scope

## In Scope

### 1. 4 IT class (Testcontainers Postgres + Kafka)

`apps/admin-service/src/test/java/com/wms/admin/integration/kafka/`:

```
com.wms.admin.integration.kafka/
├── ProjectionKafkaIntegrationBase.java   ← Testcontainers Postgres + Kafka shared
├── MasterProjectionKafkaIT.java          ← 6 master topic
├── InboundProjectionKafkaIT.java         ← 3 inbound topic
├── OutboundProjectionKafkaIT.java        ← 2 outbound topic
└── InventoryProjectionKafkaIT.java       ← 7 inventory topic
```

**Total 4 IT class, 18 source topic 분배.** 각 class 가 자기 service 의 topic 만 다룸.

`ProjectionKafkaIntegrationBase`:
- `@Tag("integration")`
- Testcontainers Postgres `postgres:16-alpine` (BE-045 base 패턴 답습)
- Testcontainers Kafka (Confluent or `apache/kafka` image — sibling 답습)
- `@SpringBootTest(classes = AdminServiceApplication.class)` + `@ActiveProfiles({"test"})` (standalone 미사용 — Kafka 가 실제로 작동해야 함)
- `@DynamicPropertySource` 로 Kafka bootstrap servers + Postgres JDBC 주입
- `${random.uuid}` consumer group-id pattern (`application-test.yml` 의 SCM/security-service 학습 답습)
- shared utility: `KafkaTestProducer` (Spring KafkaTemplate wrapping) + Awaitility helpers + DLT consumer

### 2. 18 source-topic happy-path round-trip (각 ≥ 1 testcase)

각 IT class 가 자기 service 의 topic 별 `wms.<service>.*.v1` 에 envelope 발행 → ProjectionService 가 consume → read-model 행 정상 upsert/append/increment 검증:

| IT Class | Topic | 검증 항목 |
|---|---|---|
| `MasterProjectionKafkaIT` | `wms.master.warehouse.v1` | `admin_warehouse_ref` upsert + last_event_at 갱신 |
| ↑ | `wms.master.zone.v1` | `admin_zone_ref` upsert |
| ↑ | `wms.master.location.v1` | `admin_location_ref` upsert |
| ↑ | `wms.master.sku.v1` | `admin_sku_ref` upsert |
| ↑ | `wms.master.partner.v1` | `admin_partner_ref` upsert |
| ↑ | `wms.master.lot.v1` | `admin_lot_ref` upsert |
| `InboundProjectionKafkaIT` | `wms.inbound.asn.v1` | `admin_asn_summary` upsert + status 매핑 (received/cancelled/closed) |
| ↑ | `wms.inbound.inspection.completed.v1` | `admin_inspection_summary` 1:1 upsert |
| ↑ | `wms.inbound.putaway.completed.v1` | `admin_throughput_inbound_daily` 카운터 increment |
| `OutboundProjectionKafkaIT` | `wms.outbound.order.v1` | `admin_order_summary` upsert + saga_state 반영 |
| ↑ | `wms.outbound.shipping.confirmed.v1` | `admin_shipment_summary` append + `admin_throughput_outbound_daily` 카운터 |
| `InventoryProjectionKafkaIT` | `wms.inventory.received.v1` | `admin_inventory_snapshot` upsert |
| ↑ | `wms.inventory.adjusted.v1` | `admin_inventory_snapshot` 업데이트 + `admin_adjustment_audit` append |
| ↑ | `wms.inventory.transferred.v1` | dual-write (source + target snapshot 둘 다) |
| ↑ | `wms.inventory.reserved.v1` | `reserved_qty` 증가 |
| ↑ | `wms.inventory.released.v1` | `reserved_qty` 감소 |
| ↑ | `wms.inventory.confirmed.v1` | shipping flow 적용 |
| ↑ | `wms.inventory.alert.v1` | `admin_alert_log` append |

**18 topic round-trip ≥ 18 testcase**.

### 3. dedupe-hit IT (≥ 1 per class)

eventId 중복 publish (같은 envelope 두 번) → 첫 publish = APPLIED + read-model 행 1번 mutate. 두 번째 publish = `IGNORED_DUPLICATE` outcome 기록 + read-model 행 변경 없음.

검증:
- `admin_event_dedupe.outcome = 'APPLIED'` (첫 번째)
- 두 번째도 동일 row (`event_id` PK 충돌 → DO NOTHING)
- read-model 의 `version` 컬럼 1 만 증가 (두 번째 처리 안 됨)
- `admin.projection.dropped.count{reason=duplicate}` 메트릭 1 increment

≥ 4 testcase (4 IT class × 1 each).

### 4. LWW-stale IT (≥ 1 per class)

같은 aggregate 에 대해 `occurredAt` 시간이 더 오래된 이벤트가 늦게 도착 → ProjectionService 가 `last_event_at` 가드로 skip. 검증:

- 첫 publish: `occurredAt = T2` (더 최근) → APPLIED + read-model.last_event_at = T2
- 두 번째 publish: `occurredAt = T1` (T1 < T2, 다른 eventId) → 새 dedupe row 인서트되지만 outcome = `IGNORED_DUPLICATE_LATE` + read-model mutation 안 함
- `admin.projection.dropped.count{reason=stale}` 메트릭 1 increment

≥ 4 testcase (4 IT class × 1 each).

### 5. DLT routing IT (≥ 1 per class)

poison message 발행 → DefaultErrorHandler exponential backoff 후 DLT (`<source-topic>.DLT`) 로 라우팅. 본 task 는 환경 안정성을 위해 **non-retryable exception 시나리오 우선** (즉시 DLT, retry 없이 빠름):

- malformed JSON payload → `JsonProcessingException` → 즉시 DLT
- unknown `eventType` → `UnknownEventTypeException` → 즉시 DLT
- enum 파싱 실패 (e.g., `severity=UNKNOWN`) → `IllegalArgumentException` → 즉시 DLT

검증:
- `<source-topic>.DLT` 에 record 도착 (Awaitility ≤ 10s)
- DLT 헤더에 `kafka_dlt-exception-stacktrace` 또는 동등한 진단 정보 포함
- read-model 행 변경 없음 + `admin.projection.error.count{topic}` 메트릭 1 increment

≥ 4 testcase (4 IT class × 1 each).

retryable scenario (transient DB error → 3 retry → DLT) 는 본 task **out of scope** (test flake risk + Resilience4j retry timing). non-retryable IT 가 DLT routing 자체를 충분히 검증.

### 6. Replay test (≥ 1, 별 class 또는 InventoryProjectionKafkaIT 안)

`runbooks/read-model-rebuild.md § Step 6 Monitor Catch-Up` 검증 입력:

- N 이벤트 publish (≥ 5, 다양한 aggregate) → projection consume → read-model state S1 capture
- read-model 테이블 truncate (직접 SQL) + `admin_event_dedupe` truncate
- 같은 N 이벤트 같은 순서로 다시 publish → read-model state S2 capture
- S1 == S2 검증 (동일 read-model 상태)

이는 production rebuild procedure 의 단위 검증 — runbook 의 ops procedure 가 idempotent 함을 코드 단에서 보증.

≥ 1 testcase.

### 7. 메트릭 검증

각 IT class 의 testcase 가 끝나면:
- `admin.projection.lag.seconds{...}` 가 등록되어 있고 (값 정확성 X — registration only)
- `admin.projection.dropped.count{reason=duplicate}` / `{reason=stale}` 가 dedupe-hit / LWW-stale 시나리오에서 increment
- `admin.projection.error.count{topic}` 가 DLT 시나리오에서 increment

`MeterRegistry` 직접 query.

### 8. CI 통합

`.github/workflows/ci.yml` 의 `Integration (master-service + notification-service + admin-service, Testcontainers)` job 은 이미 `admin-service:integrationTest` 를 실행. 본 task 의 신규 4 IT class 는 같은 task 안에서 자동 실행 — **CI yml 변경 0**.

job name 갱신은 cosmetic — admin-service IT 가 14 (BE-045 5 + BE-046 9) → 14+22+ 으로 증가하지만 job name 은 그대로 유지.

### 9. Tests (≥ 22)

- 18 happy round-trip
- 4 dedupe-hit (1 per IT class)
- 4 LWW-stale (1 per IT class)
- 4 DLT routing (1 per IT class)
- 1 replay test

총 ≥ 22 IT, 모두 `@Tag("integration")`. 단위 테스트 추가 0 (BE-046 의 unit 30 + slice 3 + REST 11 이 충분).

기존 admin-service `:test` (BE-045 + BE-046 합 127) 와 합산하면 `:test` 127 + `:integrationTest` 14+22 = ~36 IT. 합계 ~163.

## Out of Scope (별 task 또는 v2)

- **TASK-BE-048 후보 (polish bundle)**: BE-046 의 minor deviations 7건
  - dashboard date-range 필터 (5 controller × ~20 LOC)
  - X-Read-Model-Lag-Seconds 응답 헤더 filter / HandlerInterceptor
  - OperationsController per-topic Kafka lag (`KafkaAdmin` / `ConsumerGroupListing` 통합)
  - Throughput counter raw SQL upsert (cross-pod contention 발생 시)
  - V2 SQL DB role grants ops 문서화 (incident report 또는 runbook)
  - inbound-events split topics drift fix (producer side)
- retryable scenario IT (transient DB error → 3 retry → DLT) — flake risk + Resilience4j timing
- replay test 자동화 (현재 manual ops procedure)
- BE-046 의 read-model rebuild script 도구화 (별 task)

---

# Acceptance Criteria

- [ ] AC-01 — 4 IT class (`Master/Inbound/Outbound/InventoryProjectionKafkaIT`) 신설 + `ProjectionKafkaIntegrationBase` shared base. 모두 `@Tag("integration")`.
- [ ] AC-02 — Testcontainers Postgres + Kafka 가 `@DynamicPropertySource` 로 정상 wiring. `${random.uuid}` consumer group-id 패턴 적용.
- [ ] AC-03 — 18 source topic 모두 happy round-trip ≥ 1 testcase 커버. read-model 행 (`admin_*_ref`, `admin_*_summary`, `admin_inventory_snapshot`, `admin_adjustment_audit`, `admin_alert_log`, `admin_throughput_*`) 정상 upsert/append/increment 검증.
- [ ] AC-04 — dedupe-hit 시나리오 4 testcase (1 per IT class) — 같은 eventId 두 번 publish → outcome=APPLIED 한 번 + IGNORED_DUPLICATE 한 번. read-model `version` 1 만 증가.
- [ ] AC-05 — LWW-stale 시나리오 4 testcase (1 per IT class) — `occurredAt` 더 오래된 다른 eventId 가 늦게 도착 → outcome=IGNORED_DUPLICATE_LATE + read-model mutation 안 함.
- [ ] AC-06 — DLT routing 시나리오 4 testcase (1 per IT class) — non-retryable exception (Json/UnknownEventType/IllegalArg) → 즉시 DLT (`<source>.DLT`) 라우팅 + 헤더 보존 + read-model 변경 0 + projection.error.count 메트릭 increment.
- [ ] AC-07 — Replay test ≥ 1 — N 이벤트 publish → state S1 → truncate + 같은 이벤트 같은 순서 replay → state S2 → S1 == S2 검증.
- [ ] AC-08 — 메트릭 검증: `projection.dropped.count{reason=duplicate|stale}`, `projection.error.count{topic}` 가 각 시나리오에서 increment. `MeterRegistry` 직접 query.
- [ ] AC-09 — CI Integration job (master + notification + admin Testcontainers) 통과. admin-service `:integrationTest` 가 14 (BE-045 5 + BE-046 9) + 22+ (본 task) = 36+ IT 모두 PASS.
- [ ] AC-10 — Sibling regression 0 (master / inventory / inbound / outbound / notification / gateway / admin :test).
- [ ] AC-11 — D4 churn freeze 면제 카테고리만 변경 — `apps/admin-service/src/test/**` (project-internal test only). shared 영역 (`libs/`, `platform/`, `rules/`, `.claude/`) 변경 0.
- [ ] AC-12 — Production code 변경 0 (test-only). BE-046 의 production 결함이 발견되면 별 fix task 분기.

---

# Related Specs

- [`projects/wms-platform/specs/services/admin-service/architecture.md`](../../specs/services/admin-service/architecture.md) § Read-Model Projection Pattern, § Observability
- [`projects/wms-platform/specs/services/admin-service/idempotency.md`](../../specs/services/admin-service/idempotency.md) § 2 (Kafka 30d eventId dedupe + last_event_at LWW)
- [`projects/wms-platform/specs/services/admin-service/runbooks/read-model-rebuild.md`](../../specs/services/admin-service/runbooks/read-model-rebuild.md) § Step 6 (replay test verification 입력)
- [`projects/wms-platform/tasks/done/TASK-BE-046-admin-service-readmodel-projection.md`](../done/TASK-BE-046-admin-service-readmodel-projection.md) — 선행

# Related Contracts

- [`projects/wms-platform/specs/contracts/events/admin-events.md`](../../specs/contracts/events/admin-events.md) § Consumed Events (18 source topic)
- [`projects/wms-platform/specs/contracts/events/master-events.md`](../../specs/contracts/events/master-events.md) — source schema
- [`projects/wms-platform/specs/contracts/events/inbound-events.md`](../../specs/contracts/events/inbound-events.md) — source schema
- [`projects/wms-platform/specs/contracts/events/outbound-events.md`](../../specs/contracts/events/outbound-events.md) — source schema
- [`projects/wms-platform/specs/contracts/events/inventory-events.md`](../../specs/contracts/events/inventory-events.md) — source schema

# Sibling pattern 참조

- `apps/notification-service/src/test/java/com/wms/notification/integration/AlertConsumerIntegrationTest.java` — 6 source topic × @KafkaListener IT (BE-043 패턴, 가장 가까움)
- `apps/admin-service/src/test/java/com/wms/admin/integration/ReadModelPersistenceIntegrationTest.java` — Testcontainers Postgres base
- `apps/scm-platform/...` — `${random.uuid}` consumer group-id 패턴 + DLT IT (TASK-MONO-046-3 학습)

---

# Edge Cases

- **DLT consumer 가 DLT topic 아직 안 생성한 채 record 발행**: Spring Kafka `DeadLetterPublishingRecoverer` 가 첫 publish 시점에 topic 자동 생성. Awaitility ≤ 10s 안에 receive 못 하면 timeout — Kafka broker 의 `auto.create.topics.enable=true` 가 IT 에서 활성 (Testcontainers Kafka 기본값).
- **dedupe table state — 다른 IT class 와 격리**: 각 IT class 가 `@DirtiesContext(AFTER_CLASS)` 로 컨텍스트 격리 (BE-045 의 standalone profile 미사용 시 표준 패턴). 또는 `@BeforeEach` 에서 `admin_event_dedupe` truncate.
- **Kafka topic auto-creation timing**: 첫 publish 와 첫 consume 사이에 partition assignment delay 있을 수 있음. `ContainerTestUtils.waitForAssignment(c, 1)` 패턴 적용 (security-service / inventory-service 답습).
- **last_event_at precision** — Postgres `TIMESTAMPTZ` 마이크로초 정밀도. Java `Instant` 도 동등. LWW guard 비교 시 truncate 영향 없도록 ms 단위 명시 비교.
- **Replay test 의 throughput counter**: counter 행은 `(date, warehouse_id)` PK + LWW guard. truncate 후 같은 이벤트 replay 시 counter 가 정확히 N 번 increment 검증 (LWW guard 가 정상 작동하면 일치).
- **LWW-stale 의 append-only table** (`admin_adjustment_audit` / `admin_alert_log`): PK = source eventId. 다른 eventId 의 stale 이벤트 도착 시 새 row 가 INSERT 됨 (append-only 의 의미). LWW guard 는 upsert table 에만 적용. → AC-05 의 LWW-stale IT 는 upsert table 대상으로만 작성 (Master/Inbound asn-summary/Outbound order-summary/Inventory snapshot 등).
- **Kafka redelivery 와 dedupe**: broker rebalance 시 같은 record 가 다른 consumer 에 재배달 → eventId 동일 → dedupe 작동. 본 IT 는 명시적 redelivery 시뮬레이션 미포함 (Kafka 의 책임). dedupe-hit IT 가 producer 의 명시적 두 번 publish 로 동등 검증.

# Failure Scenarios

- **Testcontainers Kafka 부팅 timeout**: 환경 회귀 가능 (메모리 `project_testcontainers_docker_desktop_blocker`). 발견 시 task spec 업데이트 + cycle 1 회 재시도. CI Linux runner 우선.
- **`ContainerTestUtils.waitForAssignment` flake**: timeout 충분 확보 (≥ 30s) + retry 1회.
- **DLT receive timeout**: Awaitility 10s 부족 시 → 30s 로 늘림. broker 의 topic auto-create 지연 가능.
- **메트릭 race condition**: producer publish 와 metric query 사이에 timing window. Awaitility 로 metric counter ≥ N 까지 polling.
- **read-model state 비교 실패** (replay test): native SQL 로 모든 read-model 테이블 dump (row count + 핵심 컬럼 hash) 비교. JPA fetch 비교는 lazy loading + collection ordering 변동성으로 부적절.
- **production code 결함 발견**: BE-046 의 ProjectionService / Consumer / Adapter 에 IT 가 잡는 결함 발생 시 → 별 fix task 분기 (예: TASK-BE-047a). 본 task 는 test-only.

---

# Notes

- **모델 권장**: 분석=Opus 4.7 / 구현=Opus 4.7 — 18 source-topic × 4 IT class × dedupe / LWW / DLT / replay 매트릭스 = 22+ IT 디자인. Testcontainers Kafka + Postgres 동시 wiring + `${random.uuid}` consumer group + DLT consumer + Awaitility + metric 검증 = complex test design. Sonnet 으로 진행 시 시나리오 cross-product 누락 위험.
- **D4 churn freeze 면제 근거**: `apps/admin-service/src/test/**` (project-internal test only) + production code 변경 0 + shared 영역 변경 0. ADR-MONO-003 Phase 5 churn 시계 영향 0.
- **Sibling pattern 답습**: notification-service `AlertConsumerIntegrationTest` 가 가장 가까움 (Spring KafkaTemplate publish + @KafkaListener consume + Awaitility verify). admin-service 는 18 topic 으로 분량 크지만 패턴 동일.
- **선행/후속**:
  - 선행 = TASK-BE-046 (PR #282 머지 + #283 chore close 완료)
  - 후속 = (a) **TASK-BE-048 후보** (polish bundle — date-range 필터 / X-Read-Model-Lag-Seconds / per-topic Kafka lag / Throughput raw SQL / V2 DB role grants ops / inbound-events drift), (b) v2 read-model rebuild 자동화
- **연관 메모리**: `project_046_series_close` (sibling Kafka IT 패턴), `project_scm_be_series_in_progress` (`${random.uuid}` consumer group + DLT 학습), `project_046_8_phase_0_partial` (Kafka producer/consumer wiring 학습 + KafkaConsumerConfig.containsCause walker), `project_testcontainers_docker_desktop_blocker` (env blocker — CI Linux 우선).
