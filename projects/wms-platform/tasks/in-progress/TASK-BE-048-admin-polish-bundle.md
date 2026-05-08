# Task ID

TASK-BE-048

# Title

admin-service polish bundle — BE-046 minor deviations 7건 묶음 (date-range 필터 + X-Read-Model-Lag-Seconds 헤더 + per-topic Kafka lag + Throughput raw SQL upsert + V2 DB role grants ops 문서화 + inbound-events drift fix + cache.hit.rate metric 정리)

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

[TASK-BE-046](../done/TASK-BE-046-admin-service-readmodel-projection.md) 의 정직 보고 8 deviations 중 #1 (Kafka Testcontainers IT) 은 [TASK-BE-047](TASK-BE-047-admin-projection-kafka-testcontainers-it.md) 가 다룬다. 본 task 는 잔존 7건 (#2-#8) 을 한 PR 로 묶어 admin-service v1 follow-up 카탈로그를 완전히 정리한다.

**구체 목표**: admin-service v1 의 read-side 운영 품질 강화 + spec/contract drift 정리 + ops 문서 보강. 모두 minor wiring tweak 또는 docs only — 새 기능 추가 0, production 회귀 0.

---

# 7 Deviations Bundle

## #2. Dashboard date-range 필터

**현재 상태**: BE-046 의 5 dashboard controller (Order / Shipment / ASN / Adjustment / Alert) 는 scalar 필터만 지원. spec (`admin-service-api.md § 1.3-1.6`) 은 `requiredShipDateFrom/To`, `expectedArriveDateFrom/To`, `shippedAtFrom/To`, `occurredAtFrom/To`, `detectedAtFrom/To` range 필터를 명시.

**작업**:
- `OrderDashboardController` — `requiredShipDateFrom/To` (LocalDate)
- `ShipmentDashboardController` — `shippedAtFrom/To` (Instant)
- `AsnDashboardController` — `expectedArriveDateFrom/To` (LocalDate)
- `AdjustmentAuditController` — `occurredAtFrom/To` (Instant)
- `AlertDashboardController` — `detectedAtFrom/To` (Instant)

각 controller `@RequestParam` 추가 + repository `@Query` 에 `BETWEEN` 또는 `>= AND <=` 절 추가. Optional 필터 (둘 다 없으면 무필터).

**예상 LOC**: ~100 (5 controller × ~20)

## #3. `X-Read-Model-Lag-Seconds` 응답 헤더

**현재 상태**: `admin-service-api.md § Headers` 가 명시한 응답 헤더 — `worstLagSeconds > 5` 일 때만 emit. BE-046 미구현.

**작업**:
- `infra/observability/ReadModelLagHeaderFilter` 또는 `ReadModelLagInterceptor` (Spring `HandlerInterceptor`):
  - `MeterRegistry.find("admin.projection.lag.seconds")` 로 모든 `{topic}` tag 의 max 값 query
  - max > 5s 면 응답 헤더 `X-Read-Model-Lag-Seconds: {value}` set
  - `/api/v1/admin/dashboard/**` path 에만 적용 (OperationsController 도 동일)
- 단위 테스트 (≥ 3): max < 5 → 헤더 없음 / max > 5 → 헤더 있음 / metric 없음 → 헤더 없음

**예상 LOC**: ~60 (filter ~30 + tests ~30)

## #4. `OperationsController` per-topic Kafka lag

**현재 상태**: BE-046 의 `/api/v1/admin/operations/projection-status` 응답이 `lagSeconds`/`lastEventAt`/`lastProjectedAt` 필드를 0/null 로 채움. 실제 Kafka consumer-group offset 정보 미반영.

**작업**:
- `infra/observability/KafkaLagProbe` (`KafkaAdmin` 활용):
  - `AdminClient.listConsumerGroupOffsets("admin-projection")` 으로 partition 별 committed offset
  - `AdminClient.listOffsets(...)` 로 partition 별 latest offset
  - lag = (latest - committed) per partition, sum per topic
  - `lastEventAt` = 마지막 처리된 record 의 timestamp (admin_event_dedupe.processed_at MAX per source)
- `OperationsController` 가 `KafkaLagProbe` 주입 → response shape 채움
- 단위 테스트 (`AdminClient` mock) ≥ 3
- IT (Testcontainers Kafka) ≥ 1 — TASK-BE-047 의 IT class 와 같이 실행 또는 별 IT

**예상 LOC**: ~80 (probe ~40 + controller wiring ~10 + tests ~30)

## #5. Throughput counter raw SQL upsert

**현재 상태**: BE-046 의 `InventoryProjectionService` / `OutboundProjectionService` 의 throughput counter increment 가 JPA `findById` + `increment` (read-modify-write) 패턴. partition assignment 보장으로 functionally equivalent 이지만 cross-pod contention 발생 시 race window 존재.

**작업**:
- `ThroughputDailyJpaRepository` (또는 별 adapter) 에 native query:
  ```sql
  INSERT INTO admin_throughput_inbound_daily
    (date, warehouse_id, putaway_count, qty_received, last_event_at, version)
  VALUES (?, ?, 1, ?, ?, 1)
  ON CONFLICT (date, warehouse_id) DO UPDATE
    SET putaway_count = admin_throughput_inbound_daily.putaway_count + 1,
        qty_received = admin_throughput_inbound_daily.qty_received + EXCLUDED.qty_received,
        last_event_at = EXCLUDED.last_event_at,
        version = admin_throughput_inbound_daily.version + 1
    WHERE admin_throughput_inbound_daily.last_event_at < EXCLUDED.last_event_at;
  ```
- ProjectionService 가 `@Modifying @Query` 로 호출
- 기존 read-modify-write 코드 제거
- 단위 테스트 갱신 + IT (`ReadModelPersistenceIntegrationTest` 의 throughput 시나리오 수정)

**예상 LOC**: ~40 (native query 2 + repository method 2 + tests ~20)

## #6. V2 SQL DB role grants ops 문서화

**현재 상태**: `admin_adjustment_audit` + `admin_alert_log` 가 append-only 이지만 application role 의 UPDATE/DELETE revoke 가 V2 SQL 코멘트만 있고 실제 grant statement 미적용 (environment-agnostic 의도).

**작업**:
- 신규 `projects/wms-platform/specs/services/admin-service/runbooks/db-role-grants.md`:
  - Production / staging 환경에서 admin_app 역할에 대한 GRANT/REVOKE 절차
  - `admin_adjustment_audit` / `admin_alert_log` 의 UPDATE/DELETE revoke (단, `admin_alert_log.acknowledged_at`/`acknowledged_by` 컬럼은 application 가 update 필요 — column-level GRANT)
  - Flyway 가 superuser 로 실행되므로 V2 미포함, 별 ops 절차 권장
  - 검증 SQL 예시
- `architecture.md § Open Items` (또는 § References) 에서 cross-link

**예상 LOC**: ~50 (runbook md ~50)

## #7. `inbound-events.md` split topics drift fix

**현재 상태**: `inbound-events.md` (producer-side, inbound-service 가 발행) 는 `wms.inbound.asn.received.v1` / `.cancelled.v1` / `.closed.v1` 3 topic 분리. `admin-events.md § Consumed Events` (consumer-side) 는 `wms.inbound.asn.v1` 단일 topic 으로 표기. BE-046 은 admin-events.md 의 consumer view 따름.

**작업**: 둘 중 하나로 정합:
- (a) admin-events.md 를 producer-side 와 정합 — 3 topic 으로 분리 표기 + admin-service consumer 도 3 topic listen 으로 변경 (production code 변경)
- (b) inbound-events.md 를 admin-events.md 와 정합 — 3 → 1 topic consolidation 명시 + producer 도 단일 topic 으로 발행 (inbound-service production code 변경 — out of scope)
- (c) 두 spec 모두 유지하되, admin-events.md 의 표가 "logical aggregate" 로 명시 + inbound-events.md 의 split 은 implementation detail 로 명시. spec docs only.

**선호**: (c) — production 변경 0 + 두 spec 의 의도 보존. inbound-events.md (producer) = "어떤 topic 에 이벤트 발행하는가" (구현 세부), admin-events.md (consumer) = "어떤 logical aggregate 를 구독하는가" (의도). 명시적 cross-reference 추가.

**예상 LOC**: ~30 (admin-events.md + inbound-events.md cross-reference 코멘트)

## #8. `admin.query.cache.hit.rate` 메트릭 정리

**현재 상태**: BE-046 의 `ProjectionMetrics` 가 `admin.query.cache.hit.rate` 등록. v1 에 Redis query cache 미적용이라 항상 0. spec (`architecture.md § Observability`) 가 명시.

**작업** (둘 중 하나):
- (a) 메트릭 등록 유지 + 코멘트 명시 ("v1 always 0; v2 Redis cache 도입 시 활성")
- (b) v2 deferred — 메트릭 등록 제거 + `architecture.md § Observability` 에 v2 명시

**선호**: (a) — 메트릭 dashboard 가 v2 도입 시 자연 활성. spec 가 정의한 항목 유지.

**예상 LOC**: ~10 (코멘트 + metric 등록 명시)

---

# Scope

## In Scope

- 위 7 deviations 모두 한 PR 안에서 처리
- `apps/admin-service/**` (project-internal) 변경
- `projects/wms-platform/specs/services/admin-service/runbooks/db-role-grants.md` 신규 (#6)
- `projects/wms-platform/specs/services/admin-service/architecture.md` cross-link (#6)
- `projects/wms-platform/specs/contracts/events/admin-events.md` cross-reference (#7)
- `projects/wms-platform/specs/contracts/events/inbound-events.md` cross-reference (#7)
- 각 항목 단위 + slice 테스트 추가, IT 1-2건 (per-topic Kafka lag IT 가 가장 큼)

## Out of Scope

- TASK-BE-047 의 영역 (Kafka Testcontainers IT 22+ 시나리오) — 별 task
- 새 dashboard 또는 ops endpoint
- v2 기능 (Redis query cache, multi-tenant, approval workflow, TimescaleDB)
- inbound-service 의 split topic 통합 (producer-side production 변경) — 본 task 는 spec docs only 정합 (#7 (c) 옵션)

---

# Acceptance Criteria

- [ ] **AC-01 (#2)** — 5 dashboard controller 에 date-range 필터 추가 + repository `@Query` 확장. Optional 필터 (둘 다 없으면 무필터). 단위 테스트 (각 controller × ≥ 1 happy + ≥ 1 range 검증).
- [ ] **AC-02 (#3)** — `X-Read-Model-Lag-Seconds` 응답 헤더가 `worstLagSeconds > 5` 일 때만 emit. `/api/v1/admin/dashboard/**` path 적용. 단위 테스트 ≥ 3.
- [ ] **AC-03 (#4)** — `OperationsController` 응답의 `lagSeconds`/`lastEventAt`/`lastProjectedAt` 필드가 KafkaAdmin 기반 실제 값. 단위 테스트 (`AdminClient` mock) ≥ 3 + IT (Testcontainers Kafka) ≥ 1.
- [ ] **AC-04 (#5)** — Throughput counter 가 native SQL upsert (`INSERT ... ON CONFLICT DO UPDATE WHERE last_event_at < EXCLUDED.last_event_at`). 기존 read-modify-write 제거. IT 회귀 0.
- [ ] **AC-05 (#6)** — `runbooks/db-role-grants.md` 신규. `architecture.md § Open Items` (또는 § References) cross-link. column-level GRANT (alert_log.acknowledged_*) 명시.
- [ ] **AC-06 (#7)** — `admin-events.md` § Consumed Events 의 inbound.asn 행에 cross-reference 코멘트 ("logical aggregate; inbound-service emits to split topics — see inbound-events.md"). `inbound-events.md` 의 asn topic 표에 동일 cross-reference. spec docs only, production 변경 0.
- [ ] **AC-07 (#8)** — `ProjectionMetrics` 의 `admin.query.cache.hit.rate` 메트릭 등록 유지 + class javadoc 또는 코멘트로 "v1 always 0; v2 Redis cache 활성화 시" 명시.
- [ ] AC-08 — admin-service `:test` 회귀 0 (BE-045 + BE-046 누적 127 + 신규 추가). bootJar SUCCESS.
- [ ] AC-09 — Sibling regression 0 (master / inventory / inbound / outbound / notification / gateway).
- [ ] AC-10 — D4 churn freeze 영향 평가 — `apps/admin-service/**` (project-internal) + `specs/services/admin-service/runbooks/` (project-internal docs) + `specs/contracts/events/admin-events.md` + `specs/contracts/events/inbound-events.md` (project-internal docs). shared 영역 (`libs/`, `platform/`, `rules/`, `.claude/`) 변경 0.

---

# Related Specs

- [`projects/wms-platform/specs/services/admin-service/architecture.md`](../../specs/services/admin-service/architecture.md) § Observability, § Open Items
- [`projects/wms-platform/specs/services/admin-service/idempotency.md`](../../specs/services/admin-service/idempotency.md) § 2 (Throughput counter idempotency)
- [`projects/wms-platform/tasks/done/TASK-BE-046-admin-service-readmodel-projection.md`](../done/TASK-BE-046-admin-service-readmodel-projection.md) — 선행 (정직 보고 8 deviations)
- [`projects/wms-platform/tasks/ready/TASK-BE-047-admin-projection-kafka-testcontainers-it.md`](TASK-BE-047-admin-projection-kafka-testcontainers-it.md) — 동기 (deviation #1, Kafka IT)

# Related Contracts

- [`projects/wms-platform/specs/contracts/http/admin-service-api.md`](../../specs/contracts/http/admin-service-api.md) § 1.3-1.6 (date-range 필터), § Headers (X-Read-Model-Lag-Seconds), § 6.2 (operations/projection-status)
- [`projects/wms-platform/specs/contracts/events/admin-events.md`](../../specs/contracts/events/admin-events.md) § Consumed Events (#7 cross-reference)
- [`projects/wms-platform/specs/contracts/events/inbound-events.md`](../../specs/contracts/events/inbound-events.md) (#7 cross-reference)

---

# Edge Cases

- **#2 date-range 검증**: 둘 다 미제공 → 무필터. 한 쪽만 제공 → 단방향 (>= from / <= to). from > to → 400 `VALIDATION_ERROR`. ISO-8601 parse 실패 → 400.
- **#3 메트릭 없을 때**: `MeterRegistry.find` 가 null 반환 → 헤더 emit 안 함 (silent). startup 직후 / 메트릭 미등록 환경 안전.
- **#4 KafkaAdmin disconnect**: `AdminClient.listConsumerGroupOffsets` 가 timeout / network error → 응답에서 해당 topic 의 lag = -1 (sentinel "unknown") + 별도 error 카운터 increment. 200 OK 유지 (operations endpoint 의 fail-soft 정책).
- **#5 race window**: native SQL upsert 의 `WHERE last_event_at < EXCLUDED.last_event_at` 가드는 stale event 가 더 큰 occurredAt 을 가진 row 를 덮지 않도록 보호. 동시 increment 시 PostgreSQL 의 row-level lock 이 직렬화. ON CONFLICT DO NOTHING (스킵) 케이스 없음 — 항상 UPDATE 분기.
- **#6 column-level GRANT**: `admin_alert_log` 의 `acknowledged_at`/`acknowledged_by` 만 application 이 UPDATE. `GRANT UPDATE (acknowledged_at, acknowledged_by) ON admin_alert_log TO admin_app;` 패턴. runbook 에 명시 SQL.
- **#7 spec drift 의 다른 topic 도 있는가**: outbound 의 order topic 도 producer-side `outbound.order.received.v1` / `.cancelled.v1` 분리. consumer-side 는 `wms.outbound.order.v1` 단일. 동일 패턴 cross-reference 필요. **본 task 는 inbound 만 명시 — outbound 는 별 follow-up** (또는 본 task 에 포함). 추천: 본 task 에서 inbound + outbound 둘 다 정합.
- **#8 v2 Redis cache 도입 시점**: admin v2 에서 dashboard query 가 hot path 가 될 때 (현재 v1 은 6 row routing rule 만, low QPS). 메트릭 dashboard 는 v2 까지 0 표시 — 사용자 confused 가능, javadoc 으로 안내.

# Failure Scenarios

- **#2 repository @Query 회귀**: 기존 sort/pagination 깨지지 않도록 `Pageable` 인자 유지. flyway / JPA 관계 변경 없음.
- **#3 HandlerInterceptor 가 모든 controller 에 fire**: `addPathPatterns("/api/v1/admin/dashboard/**", "/api/v1/admin/operations/**")` 로 명시 제한. 다른 controller (user/role/assignment/settings) 영향 0.
- **#4 KafkaAdmin shutdown timing**: Spring context shutdown 시 AdminClient close — 정상. 정상 시점에는 fail-soft.
- **#5 Hibernate cache invalidation**: native @Modifying query 가 영향. ProjectionService 다음 read 가 cache hit 시 stale 가능 — `@Modifying(clearAutomatically = true)` 또는 `flushAutomatically = true` 적용.
- **#6 V99 seed 와 충돌**: GRANT 가 V99 seed 후 적용 — runbook 에 ordering 명시 (Flyway → seed → grants).
- **#7 spec docs only 변경 = production 회귀 0** — 검증은 spec lint / cross-reference 정합성만.
- **#8 메트릭 제거 시 dashboard 깨짐**: 본 task (a) 옵션이라 등록 유지 — 안전.

---

# Notes

- **모델 권장**: 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 — 7 minor wiring tweaks, 모두 routine. complex synthesis 없음 (each deviation 는 well-scoped). Sonnet 가 적합.
- **D4 churn freeze 면제 근거**: `apps/admin-service/**` (project-internal) + `specs/services/admin-service/runbooks/` + `specs/contracts/events/{admin,inbound}-events.md` (project-internal docs) — 모두 freeze 면제 카테고리. shared 영역 변경 0.
- **PR 분할 옵션**: 단일 PR (default) vs 7 개 micro-PR 분할. 단일 PR 권장 (review 응집성 + admin-service follow-up 카탈로그 한 번에 종결). 너무 커지면 (>30 file) 2-3 sub-PR 으로 분할 가능.
- **선행/후속**:
  - 선행 = TASK-BE-046 (PR #282/#283 머지 완료)
  - 동기 = TASK-BE-047 (deviation #1, Kafka IT — 별 task, 0 conflict)
  - 후속 = admin-service v2 (approval workflow / multi-tenant / SSO/SCIM / TimescaleDB / Redis query cache 등)
- **연관 메모리**: `project_046_series_close` (sibling polish 패턴), `project_scm_be_series_in_progress` (admin-events / inbound-events drift 패턴 학습), `project_046_8_phase_0_partial` (KafkaAdmin / AdminClient 사용 패턴).
