# Task ID

TASK-SCM-BE-003

# Title

scm-platform inventory-visibility-service Spring Boot 부트스트랩 (cross-node read-model + wms 이벤트 구독 + staleness 모니터링)

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- event
- deploy

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

scm-platform 의 두 번째 도메인 service 인 `inventory-visibility-service` 를 부트스트랩한다. 이 service 는 **cross-node 재고 가시성** 을 제공한다 — 자사 wms 창고 / supplier 보유 / 3PL / in-transit 의 재고를 노드별로 추적하고, 합산 read-model 을 운영자/buyer 에게 노출한다.

이 task 는 scm-platform Phase 4 catalyst 의 **cross-project event consumption** 첫 사례 — wms-platform 의 [inventory-events.md](../../../wms-platform/specs/contracts/events/inventory-events.md) 토픽을 scm 의 service 가 구독한다. monorepo 의 cross-project 이벤트 의존이 라이브러리·계약·배포 순서에 어떤 영향을 주는지 검증.

또한 `batch-heavy` trait 의 **첫 적용 사례** — 노드별 staleness threshold 초과 감지 batch + 주기 aggregation 이 본 service 의 핵심 워크로드. fan-platform / wms / ecommerce 어디에도 declared 안 된 trait 의 첫 stress.

이 태스크 완료 후:

- `projects/scm-platform/apps/inventory-visibility-service/` 에 Spring Boot 3.4 + JPA + Redis + Kafka 기반 service 동작
- TASK-SCM-BE-001 gateway-service 의 placeholder 라우트 `/api/v1/inventory-visibility/**` 가 활성화 (`gateway → inventory-visibility-service:8080`)
- OAuth2 Resource Server (RS256, GAP JWKS, `tenant_id=scm` fail-closed)
- Service Type: `rest-api` + `event-consumer` (PROJECT.md Service Map 명시) — Kafka consumer + REST 동시
- 핵심 엔티티: `InventoryNode` (창고/3PL/supplier/in-transit), `InventorySnapshot` (read-model, SKU × Node 단위), `NodeStaleness` (마지막 이벤트 수신 시각), `EventDedupe` (eventId 멱등 키)
- wms-platform 의 [inventory-events.md](../../../wms-platform/specs/contracts/events/inventory-events.md) 의 v1 토픽 3개 구독 (`wms.inventory.received.v1`, `wms.inventory.adjusted.v1`, `wms.inventory.transferred.v1`) — eventId 기반 멱등 처리
- 노드별 staleness threshold 초과 감지 batch (`@Scheduled`, 5분 주기) — `SNAPSHOT_STALE` 경보 (S5 — `rules/domains/scm.md`)
- read-only API: 전체 / 노드별 / SKU별 합산 + staleness 조회
- 단위 + 슬라이스 + Testcontainers integration test (Postgres + Kafka + WireMock supplier)
- `docker-compose.yml` 에 inventory-visibility-service + Postgres `scm_inventory_visibility` DB 추가

본 task 는 **TASK-SCM-BE-002 와 데이터 공유 없이 병렬 가능** — procurement 는 PO 결정에 inventory-visibility 를 사용 금지 (S5). 두 service 가 동시에 impl 진행되어도 충돌 없음. **선행** 은 TASK-SCM-BE-001 (gateway placeholder 라우트 활성화 대상).

---

# Scope

## In Scope

### 1. Project skeleton

- `projects/scm-platform/apps/inventory-visibility-service/` 디렉토리 + `build.gradle` (Spring Boot starter, Spring Web, Spring Data JPA, Flyway, Spring Kafka, Spring Data Redis, Spring Security OAuth2 Resource Server, Lombok, libs:java-common/web/security/observability/messaging)
- `Dockerfile` — multi-stage (Eclipse Temurin 21 JRE), bootJar
- 루트 `settings.gradle` 에 `'projects:scm-platform:apps:inventory-visibility-service'` include 추가

### 2. Application configuration

- `InventoryVisibilityServiceApplication.java` (main)
- `application.yml` (default + `local` profile + `test` profile):
  - `spring.application.name: scm-platform-inventory-visibility-service`
  - `spring.datasource` (Postgres) — `jdbc:postgresql://${POSTGRES_HOST:postgres}:5432/${POSTGRES_DB:scm_inventory_visibility}`
  - `spring.flyway.enabled: true`, locations `classpath:db/migration/inventory-visibility`
  - `spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP:kafka:9092}` — consumer group `scm-inventory-visibility-v1`
  - `spring.data.redis` — read-cache (per-SKU aggregation)
  - OAuth2 RS server: issuer + JWKS URI `${OIDC_ISSUER_URL}` (TASK-SCM-BE-001 과 동일 default)
  - Staleness 모니터링: `inventory-visibility.staleness.threshold-seconds` (기본 600 — 10분)
- `application-test.yml` — Testcontainers 가정

### 3. Architecture (Hexagonal — event consumer + read-model)

`apps/inventory-visibility-service/src/main/java/com/example/scmplatform/inventoryvisibility/`:

```
├── InventoryVisibilityServiceApplication.java
├── domain/                                     ← 순수 도메인
│   ├── node/
│   │   ├── InventoryNode.java                  ← @Aggregate root (한 노드 = 한 외부 system)
│   │   ├── NodeId.java                         ← UUID v7 value object
│   │   ├── NodeType.java                       ← enum: WMS_WAREHOUSE / SUPPLIER / THIRD_PARTY_LOGISTICS / IN_TRANSIT
│   │   ├── NodeStatus.java                     ← enum: ACTIVE / SUSPENDED / DECOMMISSIONED
│   │   └── repository/
│   │       └── InventoryNodeRepository.java    ← port
│   ├── snapshot/
│   │   ├── InventorySnapshot.java              ← @Entity (sku × node 단위, 수량 + version)
│   │   ├── SnapshotId.java
│   │   ├── Sku.java                            ← value object
│   │   ├── Quantity.java                       ← BigDecimal wrapper
│   │   └── repository/
│   │       └── InventorySnapshotRepository.java ← port (findBySku, findByNode, aggregate)
│   ├── staleness/
│   │   ├── NodeStaleness.java                  ← node_id, last_event_at, last_event_id
│   │   ├── StalenessThreshold.java             ← Duration (기본 10분)
│   │   ├── StalenessStatus.java                ← enum: FRESH / STALE / UNREACHABLE
│   │   └── repository/
│   │       └── NodeStalenessRepository.java    ← port
│   ├── dedupe/
│   │   ├── EventDedupeRecord.java              ← (event_id, processed_at) — 멱등 보장 (T8)
│   │   └── repository/
│   │       └── EventDedupeRepository.java      ← port
│   ├── tenant/
│   │   └── TenantContext.java                  ← Spring SecurityContext 어댑터
│   └── error/
│       ├── NodeNotFoundException.java
│       ├── NodeUnreachableException.java       ← S5
│       └── SnapshotStaleException.java         ← S5
├── application/                                 ← use case + port 사용
│   ├── ApplyInventoryReceivedUseCase.java       ← wms.inventory.received 처리
│   ├── ApplyInventoryAdjustedUseCase.java       ← wms.inventory.adjusted 처리
│   ├── ApplyInventoryTransferredUseCase.java    ← wms.inventory.transferred 처리 (source/dest 양쪽 갱신)
│   ├── GetSnapshotUseCase.java                  ← 단일 노드 조회
│   ├── GetCrossNodeSnapshotUseCase.java         ← 전 노드 합산
│   ├── GetSkuSnapshotUseCase.java               ← SKU 단위 cross-node 합산
│   ├── GetNodeStalenessUseCase.java             ← 노드별 staleness 상태
│   ├── DetectStaleNodesUseCase.java             ← @Scheduled 5분 주기
│   ├── port/
│   │   ├── inbound/                             ← (use case 자체가 inbound port)
│   │   └── outbound/
│   │       ├── EventDedupePort.java
│   │       ├── AlertPublisherPort.java          ← SNAPSHOT_STALE / NODE_UNREACHABLE 알림 발행
│   │       └── ClockPort.java
│   └── service/
│       └── InventoryVisibilityApplicationService.java ← @Transactional
├── adapter/
│   ├── inbound/
│   │   ├── web/
│   │   │   ├── InventoryVisibilityController.java   ← /api/inventory-visibility/snapshot, /sku/{sku}
│   │   │   ├── NodeStalenessController.java         ← /api/inventory-visibility/staleness
│   │   │   ├── dto/                                  ← request/response DTO
│   │   │   ├── advice/
│   │   │   │   └── GlobalExceptionHandler.java     ← 401/403/404 envelope
│   │   │   └── filter/
│   │   │       └── TenantClaimEnforcer.java         ← service 레벨 tenant_id=scm fail-closed
│   │   └── messaging/
│   │       ├── WmsInventoryReceivedConsumer.java    ← @KafkaListener wms.inventory.received.v1
│   │       ├── WmsInventoryAdjustedConsumer.java    ← @KafkaListener wms.inventory.adjusted.v1
│   │       ├── WmsInventoryTransferredConsumer.java ← @KafkaListener wms.inventory.transferred.v1
│   │       └── EventEnvelope.java                   ← wms-platform 의 envelope 호환 DTO
│   └── outbound/
│       ├── persistence/
│       │   ├── jpa/
│       │   │   ├── InventoryNodeJpaEntity.java
│       │   │   ├── InventoryNodeJpaRepository.java
│       │   │   ├── InventorySnapshotJpaEntity.java
│       │   │   ├── InventorySnapshotJpaRepository.java
│       │   │   ├── NodeStalenessJpaEntity.java
│       │   │   ├── NodeStalenessJpaRepository.java
│       │   │   └── EventDedupeJpaEntity.java
│       │   └── adapter/
│       │       ├── InventoryNodeRepositoryAdapter.java
│       │       ├── InventorySnapshotRepositoryAdapter.java
│       │       ├── NodeStalenessRepositoryAdapter.java
│       │       └── EventDedupeRepositoryAdapter.java
│       ├── messaging/
│       │   └── KafkaAlertPublisherAdapter.java       ← scm.inventory.alert.v1 발행 (SNAPSHOT_STALE 등)
│       ├── cache/
│       │   └── SnapshotAggregationCache.java         ← Redis (cross-node 합산 결과 5분 TTL)
│       └── batch/
│           └── StalenessDetectionScheduler.java      ← @Scheduled fixedDelay = 5분
└── config/
    ├── ServiceLevelOAuth2Config.java
    ├── KafkaConsumerConfig.java                      ← consumer group, eventId dedup, retry+DLT
    ├── RedisConfig.java
    └── SchedulerConfig.java                          ← batch-heavy 첫 적용
```

### 4. Database schema (Flyway)

`db/migration/inventory-visibility/V1__init.sql`:

- `inventory_nodes` (id UUID PK, tenant_id, node_type, node_external_id, name, status, contact_info JSONB, created_at, updated_at, INDEX (tenant_id, node_type, status))
- `inventory_snapshots` (id UUID PK, node_id FK, sku VARCHAR, quantity NUMERIC(18,3), tenant_id, last_event_id UUID NOT NULL, last_event_at TIMESTAMP, version INT, updated_at, UNIQUE (node_id, sku, tenant_id), INDEX (tenant_id, sku), INDEX (node_id, updated_at DESC))
- `node_staleness` (node_id PK, tenant_id, last_event_at TIMESTAMP, last_event_id UUID, staleness_status, last_checked_at, INDEX (tenant_id, staleness_status))
- `event_dedupe` (event_id UUID PK, tenant_id, processed_at TIMESTAMP, source_topic VARCHAR, INDEX (tenant_id, processed_at)) — eventId 기반 멱등 (T8)

모든 테이블 `tenant_id` 컬럼 + 인덱스 prefix.

**의도된 단순화**: 본 service 는 **outbox 미사용** — 로컬 mutating event 가 없는 read-model. consumer 가 event 를 받아 snapshot 갱신만 하고 자체 발행은 alert (`scm.inventory.alert.v1`) 만. alert 발행은 outbox 없이 직접 KafkaTemplate (best-effort, at-least-once 보장 약함) — alert 손실 시 다음 batch 가 다시 감지하므로 acceptable.

### 5. API contract (신규)

- `projects/scm-platform/specs/contracts/http/inventory-visibility-api.md` (신규):
  - `GET /api/inventory-visibility/snapshot` — 전 노드 cross-node 합산 (paginated by sku)
  - `GET /api/inventory-visibility/snapshot?nodeId={id}` — 단일 노드 snapshot
  - `GET /api/inventory-visibility/sku/{sku}` — SKU 단위 cross-node breakdown (노드별 수량 array + 합산)
  - `GET /api/inventory-visibility/staleness` — 노드별 staleness 상태
  - `GET /api/inventory-visibility/nodes` — 노드 목록 + status
  - 모든 응답: `{ data: ..., meta: ... }` envelope
  - 에러: `{ code, message, details? }` — code 는 `rules/domains/scm.md` Standard Error Codes (`NODE_UNREACHABLE`, `SNAPSHOT_STALE`)
  - **Read-only** — mutating endpoint 없음 (snapshot 갱신은 Kafka consumer 만)
  - 모든 응답에 `staleness` 메타 필드 (응답 시점의 SKU/노드 staleness 표시) — S5 명시적 노출

### 6. Event consumption contracts (cross-project, 신규 문서)

- `projects/scm-platform/specs/contracts/events/inventory-visibility-subscriptions.md` (신규):
  - **구독**: wms-platform 의 `wms.inventory.received.v1`, `wms.inventory.adjusted.v1`, `wms.inventory.transferred.v1`
  - 의존하는 wms 측 envelope 스키마: [`projects/wms-platform/specs/contracts/events/inventory-events.md`](../../../wms-platform/specs/contracts/events/inventory-events.md)
  - 구독 멱등 키: wms envelope 의 `eventId` (UUID v7)
  - consumer group: `scm-inventory-visibility-v1`
  - 처리 실패 시 retry (3회) + DLT (`wms.inventory.received.v1.DLT`)
  - 호환성: wms 가 envelope schema breaking change 시 v2 토픽 신설 — scm consumer 는 v1 토픽 grace 기간 동안 양쪽 구독
  - **본 service 가 발행하는 이벤트**: `scm.inventory.alert.v1` 만 (SNAPSHOT_STALE / NODE_UNREACHABLE 알림). spec 본 파일에 envelope + payload 동시 정의

### 7. Spec 작성

- `specs/services/inventory-visibility-service/architecture.md` — Service Type=`rest-api` + `event-consumer`, Architecture Style=**Hexagonal** (event consumer 와 REST 가 동일 도메인 코어 공유, adapter 분리 자연스러움)
- `specs/services/inventory-visibility-service/data-model.md` — 4 테이블 + 인덱스 + tenant_id 정책 + S5 (eventual consistency)
- `specs/services/inventory-visibility-service/dependencies.md` — postgres, redis, kafka, GAP IdP, gateway-service, wms-platform (cross-project event source)
- `specs/services/inventory-visibility-service/observability.md` — 메트릭 (snapshot_lag_seconds, node_staleness_status, event_dedupe_hits_total, batch_staleness_detection_duration_seconds), 로그, 트레이스 (Kafka consumer trace propagation)
- `specs/services/inventory-visibility-service/overview.md` — 1-pager
- `specs/services/inventory-visibility-service/staleness-monitoring.md` — staleness threshold 정책 + 감지 batch 설계 (`batch-heavy` 첫 적용 사례 문서)

### 8. Tests

- 단위:
  - `InventorySnapshotTest` — 동일 (node, sku) upsert + version 증가
  - `NodeStalenessTest` — last_event_at 갱신 + threshold 비교
  - 각 use-case 단위 테스트 (port mock)
  - `EventDedupeTest` — 동일 eventId 두 번 처리 시 두 번째 skip
- 슬라이스:
  - `InventoryVisibilityControllerTest` (`@WebMvcTest`)
  - `NodeStalenessControllerTest`
- 통합 (`@Tag("integration")`):
  - `WmsInventoryReceivedConsumerIntegrationTest` — wms envelope 형식 메시지 발행 → snapshot 갱신 + dedupe 적재 검증
  - `EventDedupeIntegrationTest` — 동일 eventId 두 번 발행 → snapshot 한 번만 갱신
  - `MultiTenantIsolationTest` — `tenant_id=scm` 외 토큰 시 service 레벨 403
  - `StalenessDetectionBatchIntegrationTest` — Clock 조작 → threshold 초과 노드 감지 → alert 발행
  - `CrossNodeAggregationIntegrationTest` — 3 노드의 snapshot → SKU 합산 정확성 + Redis 캐시
  - `WmsInventoryTransferredConsumerIntegrationTest` — source / destination 양쪽 노드 atomic 갱신 (단일 트랜잭션)
  - `KafkaConsumerRetryIntegrationTest` — 처리 실패 → retry 3회 → DLT 전송
- 컨트랙트: `WmsInventoryEventEnvelopeContractTest` — wms envelope 스키마 호환성 (TASK-SCM-BE-003 머지 후 wms 측 변경 시 본 테스트가 fail → cross-project 호환성 안전망)

### 9. gateway-service 라우트 활성화 검증

- TASK-SCM-BE-001 의 `application.yml` 의 `inventory-visibility-service` placeholder 라우트 (`/api/v1/inventory-visibility/**` → `http://inventory-visibility-service:8080`) 가 본 task 완료 시 실제로 응답
- gateway-service 변경 없음 — 단 본 task 의 통합 테스트가 gateway → inventory-visibility 경로 한 번 검증

### 10. docker-compose 통합

- `projects/scm-platform/docker-compose.yml` 에 `inventory-visibility-service` 추가:
  - `expose: ["8080"]`, `depends_on: [postgres, kafka, redis]`, networks: `scm-platform-net`
  - 환경 변수: `OIDC_ISSUER_URL`, `JWT_JWKS_URI`, `POSTGRES_HOST`, `POSTGRES_DB=scm_inventory_visibility`, `KAFKA_BOOTSTRAP`, `REDIS_HOST`, `SPRING_PROFILES_ACTIVE`, `WMS_KAFKA_BOOTSTRAP` (cross-project source — 동일 클러스터일 수도 별도일 수도)
  - Traefik 라벨 불필요 (gateway 만 외부 노출)
- `infra/postgres/init/01-create-databases.sh` 에 `scm_inventory_visibility` DB 추가 (TASK-SCM-BE-002 가 init script 도입 후 본 task 가 row 추가)
- `.env.example` 갱신 — `WMS_KAFKA_BOOTSTRAP` (dev 에서는 동일 kafka:9092)

### 11. Cross-project event 호환성 검증

- 본 task 머지 시 wms-platform 의 [inventory-events.md](../../../wms-platform/specs/contracts/events/inventory-events.md) 의 envelope schema 가 동결 — 향후 wms 측 변경 시 본 service 호환성 영향 명시
- wms-platform 측에 cross-project 의존 추가 정보 메모: scm-platform 이 v1 토픽을 구독한다는 사실. wms 측 spec 에 1줄 주석 추가 (선택 사항, 본 task 에서 결정)

### 12. CI 통합

- 루트 `.github/workflows/ci.yml` 의 "Build and check scm-platform backend (Docker-free)" step 에 추가:
  - `:projects:scm-platform:apps:inventory-visibility-service:check`
- boot-jars / integrationTest CI job 은 본 task 범위 밖

## Out of Scope

- **supplier / 3PL / in-transit 노드의 외부 adapter 구현** — v1 은 노드 마스터 + wms 노드만. v2 에서 supplier API / 3PL EDI / logistics shipment 이벤트 구독 추가
- **PO 결정 통합** — S5 명시적 금지. procurement-service 가 inventory-visibility 를 PO 결정에 single-source 로 사용 안 함
- **logistics-service 통합** (in-transit 이벤트) — v2 (logistics-service 부트스트랩 후)
- **demand-planning 통합** (forecast 입력으로 snapshot 사용) — v2 (demand-planning-service 부트스트랩 후)
- **frontend** — scm v1 = backend only
- **secrets manager / vault** — 본 service 는 supplier credentials 미보유 (외부 시스템 없음, wms 이벤트만 구독)
- **outbox 패턴** — 의도적 미적용 (로컬 mutating event 0, alert 만 best-effort 직접 발행)
- **E2E 시나리오** (gateway → inventory-visibility → wms event 흐름) — TASK-SCM-INT-001 (BE-002 + BE-003 완료 후 별도)

---

# Acceptance Criteria

## Build / Test

1. `./gradlew :projects:scm-platform:apps:inventory-visibility-service:build` 통과
2. `./gradlew :projects:scm-platform:apps:inventory-visibility-service:check` 통과 (단위 + 슬라이스)
3. `./gradlew :projects:scm-platform:apps:inventory-visibility-service:integrationTest` 통과 (Docker 필요, `@Tag("integration")`)
4. 루트 CI 의 Build & Test gradle 리스트에 본 모듈 추가 + 통과

## Runtime

5. `pnpm traefik:up` + `pnpm scm:up` 후 valid `tenant_id=scm` JWT 로 `GET http://scm.local/api/v1/inventory-visibility/snapshot` → 200 + `{ data: [...], meta: { staleness: ... } }` 응답
6. `tenant_id=wms` JWT → gateway 가 403 차단
7. `tenant_id=scm` 이지만 service 레벨 fail-closed 재검증 동작 (단위 테스트)

## Event consumption (cross-project)

8. wms 의 `wms.inventory.received.v1` 토픽에 envelope 형식 메시지 발행 → `inventory_snapshots` 테이블에 한 행 upsert + `event_dedupe` 행 적재
9. 동일 `eventId` 두 번 발행 → `inventory_snapshots` 한 번만 갱신, `event_dedupe` 두 번째는 skip (멱등 검증)
10. `wms.inventory.transferred.v1` 처리 시 source 노드 차감 + destination 노드 가산이 단일 트랜잭션 atomic
11. consumer 처리 실패 (예: DB 다운) → retry 3회 → DLT 전송

## Staleness (S5)

12. 노드별 last_event_at 이 staleness threshold (10분) 초과 시 `node_staleness.staleness_status = STALE` + `scm.inventory.alert.v1` alert 발행
13. `GET /api/inventory-visibility/staleness` 응답에 STALE 노드 포함
14. snapshot 응답의 `meta.staleness` 필드가 정확 (FRESH / STALE / UNREACHABLE)

## Read API

15. `GET /api/inventory-visibility/sku/{sku}` 응답이 노드별 quantity + 합산 정확
16. Redis 캐시 hit 시 동일 응답 (5분 TTL), Redis miss / 다운 시 DB 직접 조회 (fail-open) — 응답 헤더 `X-Cache: HIT|MISS|UNAVAILABLE`

## Spec 무결성

17. `architecture.md` Service Type=`rest-api`+`event-consumer`, Architecture Style=`Hexagonal`
18. `data-model.md` 의 인덱스가 `V1__init.sql` 과 일치
19. `inventory-visibility-subscriptions.md` 의 구독 토픽 / consumer group 이 application.yml 과 일치
20. `staleness-monitoring.md` 의 threshold / 주기가 application.yml 의 `inventory-visibility.staleness.*` 와 일치 (`batch-heavy` trait 첫 적용 문서화)

---

# Related Specs

- [TASK-SCM-BE-001](../review/TASK-SCM-BE-001-gateway-service-bootstrap.md) (review/PR #194) — gateway placeholder 라우트 활성화 대상. 본 task 의 선행.
- [TASK-SCM-BE-002](TASK-SCM-BE-002-procurement-service-bootstrap.md) (ready) — 병렬 가능 (데이터 공유 0). procurement 는 inventory-visibility 를 사용하지 않음 (S5).
- [`PROJECT.md`](../../PROJECT.md) § Service Map (v1) — inventory-visibility-service 책임
- [`PROJECT.md`](../../PROJECT.md) § GAP IdP Integration
- [`specs/integration/gap-integration.md`](../../specs/integration/gap-integration.md) (TASK-SCM-BE-001 에서 작성)
- [rules/domains/scm.md](../../../../rules/domains/scm.md) — Inventory Visibility bounded context, S5 (eventual consistency, NODE_UNREACHABLE / SNAPSHOT_STALE)
- [rules/traits/integration-heavy.md](../../../../rules/traits/integration-heavy.md) — consumer retry / DLT / vendor (cross-project source) fallback
- `rules/traits/batch-heavy.md` (없으면 common 만 적용) — staleness detection batch 의 chunking · restartability · checkpoint
- [platform/architecture-decision-rule.md](../../../../platform/architecture-decision-rule.md) — Hexagonal 선택 근거
- [platform/event-driven-policy.md](../../../../platform/event-driven-policy.md) — consumer 멱등 (T8)
- [platform/service-types/rest-api.md](../../../../platform/service-types/rest-api.md)
- [platform/service-types/event-consumer.md](../../../../platform/service-types/event-consumer.md)
- [projects/wms-platform/specs/contracts/events/inventory-events.md](../../../wms-platform/specs/contracts/events/inventory-events.md) — **cross-project source contract** (의존)
- [projects/wms-platform/specs/services/inventory-service/architecture.md](../../../wms-platform/specs/services/inventory-service/architecture.md) — wms 측 source 의 동작 이해

# Related Skills

- `.claude/skills/backend/springboot-api/SKILL.md`
- `.claude/skills/backend/architecture/hexagonal/SKILL.md`
- `.claude/skills/backend/exception-handling/SKILL.md`
- `.claude/skills/backend/dto-mapping/SKILL.md`
- `.claude/skills/backend/transaction-handling/SKILL.md`
- `.claude/skills/backend/pagination/SKILL.md`
- `.claude/skills/backend/observability-metrics/SKILL.md`
- `.claude/skills/messaging/event-consumption/SKILL.md` (있다면)
- `.claude/skills/messaging/idempotent-consumer/SKILL.md` (있다면)
- `.claude/skills/messaging/dlt-handling/SKILL.md` (있다면)
- `.claude/skills/database/schema-change-workflow/SKILL.md`
- `.claude/skills/database/indexing/SKILL.md`
- `.claude/skills/cross-cutting/caching/SKILL.md`
- `.claude/skills/cross-cutting/scheduled-jobs/SKILL.md` (있다면, batch-heavy 첫 적용)
- `.claude/skills/testing/testcontainers/SKILL.md`
- `.claude/skills/testing/contract-test/SKILL.md`
- `.claude/skills/service-types/rest-api-setup/SKILL.md`
- `.claude/skills/service-types/event-consumer-setup/SKILL.md` (있다면)

---

# Related Contracts

본 task 에서 신설:

- `projects/scm-platform/specs/contracts/http/inventory-visibility-api.md`
- `projects/scm-platform/specs/contracts/events/inventory-visibility-subscriptions.md` (구독 contract + 자체 alert 이벤트)

기존 / 참조 (cross-project):

- [`projects/wms-platform/specs/contracts/events/inventory-events.md`](../../../wms-platform/specs/contracts/events/inventory-events.md) — wms 가 발행, scm 이 구독. **schema 변경 시 본 service 호환성 영향**.

---

# Edge Cases

1. **wms 토픽 schema 변경 (cross-project breaking)**: wms 가 `wms.inventory.received.v2` 도입 시 scm consumer 는 v1 + v2 동시 구독 (grace period). v1 deprecated 시 scm 측에서 별도 follow-up task 로 v2 마이그레이션. **본 task 는 v1 만 구독.**
2. **eventId 누락 / 형식 오류**: 잘못된 envelope 메시지 도착 시 retry 무의미 → 즉시 DLT 전송 + 메트릭 `event_envelope_invalid_total`.
3. **wms.inventory.transferred 의 source/destination 노드 중 하나가 본 service 에 미등록**: 노드 자동 등록 vs reject. v1 = 자동 등록 (NodeType=WMS_WAREHOUSE, status=ACTIVE) — wms 가 source-of-truth 라 scm 이 미리 노드 마스터를 알 필요 없음. 단 노드 메타 (name, contact_info) 는 비어있는 상태로 시작.
4. **노드가 모두 STALE 인 경우**: 전체 시스템 알람 우선순위 상향 (`node_staleness_status=STALE` 비율이 50% 초과 시 `system.inventory-visibility.degraded` 별도 alert).
5. **Redis 캐시 다운**: cross-node aggregation 응답이 느려짐 (DB 직접 query) — fail-open, 응답 헤더 `X-Cache: UNAVAILABLE`. metric `inventory_visibility_cache_unavailable_total`. (read-only 라 fail-CLOSED 의 가치 없음 — procurement 의 idempotency cache 와 반대)
6. **PO 결정에 inventory-visibility API 사용 시도**: API 응답에 명시적 `meta.warning: "Not for procurement decisions (S5)"` 필드 노출. 외부 운영자가 dashboard 용으로만 사용하도록 가이드.
7. **batch staleness 감지 중 신규 이벤트 도착**: 감지 batch 가 `last_event_at` 을 읽는 시점과 신규 이벤트가 갱신하는 시점의 race. solution: batch 가 `SELECT ... FOR UPDATE SKIP LOCKED` (또는 advisory lock) — staleness 판정 후 alert 발행 직전에 last_event_at 재확인.
8. **Cross-tenant event 도착**: wms envelope 의 tenant 가 scm 이 아닌 경우 (wms 가 multi-tenant 가 아니라면 발생 안 함, 안전장치) → consumer 가 무시 + 메트릭 `cross_tenant_event_filtered_total`.

---

# Failure Scenarios

## A. wms-platform Kafka 클러스터 다운

scm consumer 가 메시지를 받지 못함 → snapshot 갱신 정지 → 모든 노드 staleness 진입 → alert 폭증. mitigation: alert dedup (동일 노드 5분 내 1회만 발행) + circuit breaker (downstream alert 시스템 보호). dashboard 의 "Kafka source 다운" 별도 panel.

## B. Postgres 다운

snapshot 갱신 / 조회 모두 5xx. consumer 는 retry → DLT 전송. read API 503. recovery 시 DLT 메시지 수동/자동 replay (v2).

## C. Redis 캐시 다운

read fail-open (DB 직접). 응답 시간 증가. `inventory_visibility_cache_unavailable_total` metric 으로 모니터링.

## D. JWKS 캐시 stale

TASK-SCM-BE-001 / BE-002 와 동일 — 5분 TTL + JwksHealthProbe 부팅 시 검증.

## E. Consumer offset 손실 (consumer group reset)

전체 토픽 re-process → eventId dedupe 가 보호. 단 ~30일치 이벤트 처리 시간 + DB 부하. mitigation: consumer offset reset 은 운영자 명시적 명령으로만, dashboard 에 마지막 commit offset 노출.

## F. 스케줄러 (StalenessDetectionScheduler) 노드 단일 인스턴스 문제

동일 service 가 다수 인스턴스 배포 시 batch 가 N 번 실행 — Spring `@Scheduled` 단일 인스턴스 구조 안 맞음. mitigation: ShedLock (libs:java-common 의 분산 락) — Redis 또는 DB row 기반 단일 인스턴스 보장. v1 = ShedLock 적용. **`batch-heavy` trait 첫 적용 사례라 본 task 가 ShedLock 도입 결정.**

## G. wms 측 envelope 의 actorId / traceId 누락

wms 가 시스템 발행 (consumer-driven) 인 경우 actorId 가 `system:putaway-consumer` 같은 형식. scm 측 dedupe / snapshot 갱신은 actorId 무관. 단 audit / debugging 시 actorId 가 필수가 아니라는 점 명시.

## H. inventory.alert 발행 실패

KafkaTemplate 직접 발행 (outbox 미사용) → producer 다운 시 alert 손실. mitigation: 다음 batch (5분 후) 가 동일 STALE 상태 재감지 후 재발행. 손실 영향 = 최대 5분 알림 지연. acceptable.

---

# Notes

- **Recommended impl model**: **Sonnet 4.6** — Kafka consumer + read-model + scheduled batch 의 패턴은 wms / fan-platform 의 기존 패턴 답습으로 충분. 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 — read-only + 단순 consumer + batch 1개 (BE-002 의 Hexagonal + state machine + outbox + circuit breaker + idempotency 의 6중 stress 와 다름).
- **분량 추정**: BE-002 (485 라인 spec) 의 약 2/3. 4 테이블 + 3 consumer + 5 REST endpoint + 1 batch.
- **dependency 표현**:
  - `선행`: TASK-SCM-BE-001 (PR #194 review/머지 후 시작)
  - 병렬 가능: TASK-SCM-BE-002 (procurement) — 데이터 공유 0. BE-001 머지 후 BE-002 + BE-003 동시 impl dispatch 가능
  - `후속`: TASK-SCM-INT-001 (BE-002 + BE-003 완료 후 e2e), v2 supplier / 3PL / in-transit 노드 adapter 추가 task
- **Cross-project event 의존**: monorepo Phase 4 catalyst 의 핵심 평가 입력 — wms-platform 의 inventory-events.md 가 변경되면 scm 의 본 service 가 영향받음. 본 task 는 wms 측 v1 schema 동결을 가정. 향후 wms 측 변경 시 cross-project 호환성 task 별도 신설.
- **`batch-heavy` trait 첫 적용**: PROJECT.md 의 `batch-heavy` declared 가 본 service 에서 처음으로 코드 표면화 (StalenessDetectionScheduler + ShedLock). 본 task 머지 후 메모리에 "batch-heavy 첫 코드 적용 사례 = inventory-visibility-service" 기록 권장.
- **Service Type 두 개 동시**: `rest-api` + `event-consumer` 같이 선언 — `platform/service-types/` 두 파일 모두 적용. 충돌 시 rest-api 가 우선 (REST 응답이 외부 SLA 의 1차 면).
- **Phase 4 evaluation 영향**: cross-project event 구독이 라이브러리 (`libs:java-messaging` consumer side) 에 새 stress. wms 의 OutboxRelay 가 발행한 envelope 을 scm 의 consumer 가 deserialize — envelope schema 호환성이 양쪽 코드 베이스에서 공유. 라이브러리 변경 시 양쪽 영향. ADR-MONO-002 D3 churn 평가의 직접 입력.
- **CI 변경**: 루트 `.github/workflows/ci.yml` 의 scm-platform Build & Test step 에 1줄 추가. minimal.
