# Task ID

TASK-SCM-BE-005

# Title

inventory-visibility-service Testcontainers integration tests — wms event consumer chain regression guard (post TASK-SCM-INT-001b)

# Status

ready

# Owner

backend / qa

# Task Tags

- test
- code

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Edge Cases
- Failure Scenarios

---

# Goal

[TASK-SCM-INT-001b](../done/TASK-SCM-INT-001b-deeper-investigation-2-scenarios.md) (PR #262) 가 cross-service E2E 에서 두 production root cause 를 catch:

- **UUID v7 prefix collision** — `PurchaseOrderApplicationService.draft()` 의 `poId.substring(0, 8)` (timestamp prefix 32 bits) → tight loop 충돌 → `idx_purchase_orders_tenant_po_number` UNIQUE 위반 23505. Fix: `substring(poId.length() - 8)` (rand_b tail).
- **Hibernate 6 + JSONB 미스매치** — `InventoryNodeJpaEntity.contactInfo` 가 `String` ↔ `columnDefinition = "jsonb"` mapping 에 `@JdbcTypeCode(SqlTypes.JSON)` 누락 → bytea→jsonb cast 거부 42804. Fix: `@JdbcTypeCode` 추가.

두 root cause 모두 e2e 5m13s 에서만 catch 됨. **service-internal Testcontainers IT 가 동일 회귀를 1.5min 잡에서 catch 하도록 하는 게 본 task 의 목적**. [TASK-SCM-BE-002d](../done/TASK-SCM-BE-002d-procurement-testcontainers-it.md) 가 procurement-service 에 동일 패턴으로 7 IT 를 만든 사례를 답습.

**전제**: Docker 환경 (Rancher Desktop + DOCKER_API_VERSION=1.45) — TASK-SCM-INT-001b cycle 4+ 에서 검증된 환경. CI Linux runner 는 항상 정상.

---

# Scope

## In Scope (≥ 6 IT tests)

`@SpringBootTest` + `@Tag("integration")` + 새 `AbstractInventoryVisibilityIntegrationTest` 상속 (Postgres + Kafka KRaft 공유, BE-002d 의 `AbstractProcurementIntegrationTest` 답습).

1. **WMS inventory.adjusted consumer apply IT** — Kafka producer 가 `wms.inventory.adjusted.v1` 이벤트 publish → `WmsInventoryAdjustedConsumer` 가 받아 `inventory_snapshots.totalQuantity` 갱신. 신규 SKU + 신규 NodeId → 신규 row INSERT 검증. **INT-001b root cause #2 (Hibernate JSONB 42804) 회귀 가드** — `InventoryNode` auto-create 시 `contactInfo=null` write 가 실패하지 않아야 함.

2. **WMS inventory.received consumer apply IT** — `wms.inventory.received.v1` 이벤트 → snapshot 추가 quantity 검증.

3. **Event dedupe IT (T8)** — 동일 `eventId` 두 번 publish → 두 번째는 `processed_events` UNIQUE 위반 silent skip → snapshot quantity 변화 없음. **TASK-SCM-INT-001b 의 dedupe 검증을 service-internal IT 로 reproduce.**

4. **Auto-create WMS node + JSONB write IT** — 새 `nodeExternalId` 로 inventory.adjusted 이벤트 publish → `inventory_nodes` row 자동 생성 + `contact_info=null::jsonb` 정상 INSERT. **INT-001b root cause #2 직접 회귀 가드** — `@JdbcTypeCode(SqlTypes.JSON)` 제거 시 본 IT 가 깨져야 함.

5. **Multi-tenant isolation IT** — tenant=scm 의 inventory.adjusted 이벤트 + tenant=other 의 GET 요청 → other tenant 가 본 snapshot 0 반환 (cross-tenant fail-closed).

6. **Staleness scheduler IT** — `@Scheduled` 의 staleness 감지 + ShedLock 분산 lock + alert event publish. 마지막 `last_event_at` 가 N 분 이상 → `node_staleness.staleness_status` = STALE → `scm.inventory.alert.v1` Kafka 발행 검증.

(선택 7) **Read endpoint IT** — `/api/v1/inventory-visibility/snapshot` 가 read-only API 로 정상 응답 + `meta.staleness` / `meta.warning` 노출 검증.

## Out of Scope

- production code 변경 (BE-002d 정책 동일 — small fix 만 별 commit + spec 본문 update)
- E2E 시나리오 (별 task TASK-SCM-INT-001b 가 이미 cross-service 검증)
- wms-platform 측 outbox relay (cross-project, scm 영역 외)

---

# Acceptance Criteria

1. ≥ 6 IT tests `@Tag("integration")` 마킹 + PASS
2. **회귀 가드 #1 검증**: `@JdbcTypeCode(SqlTypes.JSON)` 어노테이션을 일시적으로 제거하면 IT #1 또는 #4 가 fail (spec 본문에 시연하지 말고, 검증 후 원복) — 즉 회귀 catch 능력 입증.
3. **회귀 가드 #2 검증**: 가능 시 — INT-001b root cause #1 (UUID v7 prefix collision) 은 procurement-service 영역이라 inventory-visibility IT 와 직접 무관. 본 task 의 scope 는 #2 (Hibernate JSONB) 회귀 가드에 집중.
4. local `:integrationTest` PASS (Docker 환경)
5. CI `Integration (scm-platform, Testcontainers)` Job PASS (다음 run)
6. `:inventory-visibility-service:check` 에 `@Tag("integration")` exclude 유지 (Docker-less default — TASK-MONO-048 의 SCM Integration job 답습)
7. 누적 inventory-visibility-service tests ≥ (이전 + 6 IT) — 정확한 baseline 은 spec impl 단계에서 카운트.

---

# Related Specs

- [TASK-SCM-INT-001b](../done/TASK-SCM-INT-001b-deeper-investigation-2-scenarios.md) — 직접 선행 (root cause source)
- [TASK-SCM-BE-002d](../done/TASK-SCM-BE-002d-procurement-testcontainers-it.md) — IT 패턴 답습 reference
- [TASK-SCM-BE-003](../done/TASK-SCM-BE-003-inventory-visibility-service-bootstrap.md) — service 부트스트랩
- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md`
- `projects/scm-platform/specs/services/inventory-visibility-service/data-model.md`
- `projects/scm-platform/specs/services/inventory-visibility-service/staleness-monitoring.md`
- `projects/scm-platform/specs/contracts/events/inventory-visibility-subscriptions.md` — wms.inventory.* topic 구독
- `projects/wms-platform/specs/contracts/events/inventory-events.md` § Global Envelope (cross-project)
- `rules/traits/transactional.md` § T8 (idempotency dedupe)
- `rules/traits/batch-heavy.md` (staleness scheduler + ShedLock)

---

# Related Contracts

- 없음 (test-only — 외부 contract 변경 없음)

---

# Target Service / Component

- `projects/scm-platform/apps/inventory-visibility-service/src/test/java/.../integration/AbstractInventoryVisibilityIntegrationTest.java` (신규)
- `projects/scm-platform/apps/inventory-visibility-service/src/test/java/.../integration/WmsInventoryAdjustedConsumerIntegrationTest.java` (신규)
- `projects/scm-platform/apps/inventory-visibility-service/src/test/java/.../integration/WmsInventoryReceivedConsumerIntegrationTest.java` (신규)
- `projects/scm-platform/apps/inventory-visibility-service/src/test/java/.../integration/EventDedupeIntegrationTest.java` (신규)
- `projects/scm-platform/apps/inventory-visibility-service/src/test/java/.../integration/InventoryNodeAutoCreateIntegrationTest.java` (신규)
- `projects/scm-platform/apps/inventory-visibility-service/src/test/java/.../integration/CrossTenantIsolationIntegrationTest.java` (신규)
- `projects/scm-platform/apps/inventory-visibility-service/src/test/java/.../integration/StalenessSchedulerIntegrationTest.java` (신규, ShedLock + @Scheduled)

---

# Implementation Notes

- 답습 reference (BE-002d):
  - `AbstractIntegrationTest` 패턴 — Postgres + Kafka KRaft `@Container` 공유 + `@DynamicPropertySource`
  - `@DirtiesContext(AFTER_CLASS)` 로 cross-class consumer group offset leak 회피 (TASK-MONO-046-3 선례)
  - per-class Kafka consumer group `${unique-uuid}` 명시 (TASK-MONO-046-2/3 선례)
  - Awaitility `await().atMost(15s).untilAsserted(...)` 로 consumer publish→snapshot apply 의 eventual consistency 검증
- **Kafka producer in test** — host JVM 에서 `KafkaTemplate` 또는 raw `KafkaProducer<String, String>` 으로 wms.inventory.* envelope publish (`KafkaTestProducer` from INT-001b's testsupport 답습 가능)
- **Topic 사전 생성** — INT-001b cycle 1 의 `AdminClient.createTopics(...)` 패턴 답습 (consumer subscription race 방지)
- **JSONB 회귀 시연** — IT #4 의 production code 가 `@JdbcTypeCode(SqlTypes.JSON)` 누락 상태일 때 IT 가 fail 하는지 사전 검증 (정확한 catch 능력 입증, 검증 후 원복)
- **ShedLock 환경** — `shedlock_locks` 테이블 + Postgres lock provider. test profile 에서 lock TTL 짧게 설정.
- **`@Scheduled` 트리거** — test 에서 `@SpyBean` 또는 `ApplicationContext.publishEvent(...)` 로 scheduler 강제 trigger (production scheduler 5분 주기 wait 안 함)

---

# Edge Cases

1. **Cross-test offset accumulation** — TASK-MONO-046-3 선례. `@DirtiesContext(AFTER_CLASS)` + per-class consumer group `test-iv-${random.uuid}` 명시.
2. **WMS topic auto-create race** — Kafka consumer subscribe 시점에 topic 미생성 → `UNKNOWN_TOPIC_OR_PARTITION`. INT-001b cycle 1 의 `AdminClient.createTopics(...)` 사전 호출 패턴 답습.
3. **EventDedupe race** — 동일 `eventId` 두 번 publish 후 두 번째가 정확히 silent skip 되는지 (DB 레벨 UNIQUE 위반인지 application 레벨 short-circuit 인지) 검증 — production 의도 확인.
4. **JSONB null write** — `contact_info=null` 일 때 PostgreSQL 가 nullable JSONB 로 받아들이는지. `@JdbcTypeCode(SqlTypes.JSON)` 가 null 도 정상 처리하는지 인지.
5. **Staleness scheduler timing** — test profile 에서 scheduler 비활성화하고 명시적 trigger (production 5분 주기 wait 안 함). ShedLock TTL 도 작게.
6. **Multi-tenant test data setup** — 두 tenant 의 `inventory_nodes` + `inventory_snapshots` 분리 setup, GET 시 tenant 헤더 fail-closed 검증.

---

# Failure Scenarios

## A. JSONB IT 가 production fix 없어도 PASS

`@JdbcTypeCode(SqlTypes.JSON)` 을 제거했을 때 IT 가 여전히 PASS 한다면 회귀 가드 능력 없음. 이 경우:
- IT setup 점검 — 정말로 InventoryNode auto-create path 를 hit 하는지 (`@Modifying` 없이 EntityManager.persist 가 일어나는지)
- Hibernate version (Spring Boot 3.x default = Hibernate 6) 인지 확인
- production fix 가 다른 곳 (e.g. application.yml `spring.jpa.properties.hibernate.type.preferred_uuid_jdbc_type`) 에 있는지 추적

## B. ShedLock IT 의 cross-class 간섭

`shedlock_locks` 테이블이 IT class 간 공유되면 한 class 의 lock 이 다음 class 로 누수 → race. `@DirtiesContext(AFTER_CLASS)` + Postgres truncation `@BeforeEach` 또는 unique lock name 으로 격리.

## C. cross-project Kafka topic 미생성

WMS 측 outbox relay 가 안 도는 IT 환경에서 `wms.inventory.*` topic 자동 생성 안 됨 → INT-001b 와 동일 race. `AdminClient.createTopics(...)` 명시적 호출 (선례 INT-001b cycle 1).

## D. Kafka consumer group 의 offset earliest/latest 결정

`auto-offset-reset=latest` 시 consumer subscribe 전 publish 된 message lost (INT-001b cycle 1 의 가설 #1). `earliest` 로 명시 또는 `ContainerTestUtils.waitForAssignment(c, 1)` 패턴 (TASK-MONO-046-3 답습).

---

# Test Requirements

- ≥ 6 IT 메서드 PASS
- `@JdbcTypeCode(SqlTypes.JSON)` 회귀 가드 능력 시연 (검증 후 원복)
- main CI `Integration (scm-platform, Testcontainers)` Job SUCCESS

---

# Definition of Done

- [ ] 6+ IT 작성 + PASS
- [ ] InventoryNodeAutoCreate IT 가 production `@JdbcTypeCode(SqlTypes.JSON)` 제거 시 fail (회귀 가드 능력 검증)
- [ ] local `:inventory-visibility-service:integrationTest` PASS
- [ ] CI `Integration (scm-platform, Testcontainers)` Job PASS
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Sonnet** — BE-002d 패턴 답습 + 명확한 IT 시나리오. 새 architectural 분석 부담 없음.
- **분량 추정**: medium (6 신규 IT class + AbstractInventoryVisibilityIntegrationTest base + Kafka topic 사전 생성 패턴)
- **dependency**:
  - `선행`: TASK-SCM-INT-001b (root cause source — done)
  - `참조`: TASK-SCM-BE-002d (IT 패턴 — done)
  - `병렬`: 없음
  - `후속`: 없음
- **D4 churn freeze 영향 0** — project-internal 변경 (`projects/scm-platform/apps/inventory-visibility-service/`)
- **CI 비용**: path-filter scm 활성화 → SCM Integration job ~1.5min + scm-platform boot jars ~45s + Build & Test ~1.5min. 이미 TASK-MONO-048 로 deterministic.
