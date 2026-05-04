# Task ID

TASK-SCM-BE-002

# Title

scm-platform procurement-service Spring Boot 부트스트랩 (PO 상태기계 + supplier 통합 + outbox)

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

scm-platform 의 핵심 도메인 service 인 `procurement-service` 를 부트스트랩한다. 이 service 는 **공급망 흐름의 진입점** — buyer 의 구매 발주(PO) 작성·확정·취소, supplier ack 교환, ASN 수신 후 입고 정합성 확인을 책임진다.

이 task 는 **scm-platform Phase 4 catalyst 의 trait 검증** 역할 — `transactional` (PO 상태기계 멱등 + outbox) + `integration-heavy` (supplier adapter circuit breaker + retry + idempotency key) 두 traits 가 동시에 stress 받는 첫 service 다. fan-platform 의 [community-service](../../../fan-platform/apps/community-service/) 부트스트랩 패턴을 reference 로 답습하되, **Layered** 가 아닌 **Hexagonal** — 외부 supplier 통합이 도메인 핵심이라 adapter 분리가 자연스럽다 ([platform/architecture-decision-rule.md](../../../../platform/architecture-decision-rule.md)).

이 태스크 완료 후:

- `projects/scm-platform/apps/procurement-service/` 에 Spring Boot 3.4 + JPA + Redis + Kafka 기반 REST API 동작
- TASK-SCM-BE-001 gateway-service 의 placeholder 라우트 `/api/v1/procurement/**` 가 활성화 (`gateway → procurement-service:8080`)
- OAuth2 Resource Server (RS256, GAP JWKS, `tenant_id=scm` 강제 fail-closed) — gateway 에서 이미 검증되지만 service 레벨 재검증
- 핵심 엔티티: `PurchaseOrder` (DRAFT/SUBMITTED/ACKNOWLEDGED/CONFIRMED/PARTIALLY_RECEIVED/RECEIVED/SETTLED/CLOSED 상태기계, [rules/domains/scm.md](../../../../rules/domains/scm.md) Ubiquitous Language), `PurchaseOrderLine`, `Supplier` (v1 임시 마스터), `AdvanceShipmentNotice`, `AsnLine`, `po_status_history` (append-only audit S7)
- supplier 외부 통합: v1 은 mock REST adapter 1개 (실 EDI/SFTP 는 v2). adapter port 인터페이스 + circuit breaker + retry+jitter + idempotency key (S2) — `integration-heavy` trait 의 stress test
- outbox 패턴 — `scm.procurement.po.{submitted,acknowledged,confirmed,canceled,received,closed}` + `scm.procurement.asn.received` 이벤트 Kafka 발행 (settlement / inventory-visibility 후속 연결 기반)
- 단위 + 슬라이스 + Testcontainers integration test (Postgres + Kafka + Redis + WireMock supplier)
- `docker-compose.yml` 에 procurement-service + Postgres `scm_procurement` DB 추가

본 task 는 **TASK-SCM-BE-001 의 후속** (gateway placeholder 라우트 활성화) 이며, **TASK-SCM-BE-003 의 선행이 아닌 병렬** — inventory-visibility-service 는 wms snapshot 구독자라 procurement 와 독립.

---

# Scope

## In Scope

### 1. Project skeleton

- `projects/scm-platform/apps/procurement-service/` 디렉토리 + `build.gradle` (Spring Boot starter, Spring Web, Spring Data JPA, Flyway, Spring Kafka, Spring Data Redis, Spring Security OAuth2 Resource Server, Resilience4j, Lombok, libs:java-common/web/security/observability/messaging)
- `Dockerfile` — multi-stage (Eclipse Temurin 21 JRE), bootJar
- 루트 `settings.gradle` 에 `'projects:scm-platform:apps:procurement-service'` include 추가

### 2. Application configuration

- `ProcurementServiceApplication.java` (main)
- `application.yml` (default + `local` profile + `test` profile):
  - `spring.application.name: scm-platform-procurement-service`
  - `spring.datasource` (Postgres) — `jdbc:postgresql://${POSTGRES_HOST:postgres}:5432/${POSTGRES_DB:scm_procurement}`
  - `spring.flyway.enabled: true`, locations `classpath:db/migration/procurement`
  - `spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP:kafka:9092}`
  - `spring.data.redis` — supplier circuit breaker state / catalog cache
  - OAuth2 RS server: issuer + JWKS URI `${OIDC_ISSUER_URL}` (TASK-SCM-BE-001 과 동일 default)
  - Resilience4j: supplier adapter circuit breaker / retry / bulkhead 설정
- `application-test.yml` — Testcontainers 가정

### 3. Domain layer (Hexagonal — adapter 가 도메인 핵심이라 Layered 부적합)

`apps/procurement-service/src/main/java/com/example/scmplatform/procurement/`:

```
├── ProcurementServiceApplication.java
├── domain/                                     ← 순수 도메인 (외부 의존 0)
│   ├── po/
│   │   ├── PurchaseOrder.java                  ← @Aggregate root
│   │   ├── PurchaseOrderId.java                ← UUID v7 value object
│   │   ├── PurchaseOrderLine.java
│   │   ├── PoNumber.java                       ← value object (외부 표시용)
│   │   ├── status/
│   │   │   ├── PoStatus.java                   ← enum
│   │   │   ├── PoStatusMachine.java            ← 전이 매트릭스 (S1)
│   │   │   └── PoStatusHistory.java            ← append-only (S7)
│   │   ├── Money.java                          ← amount + currency
│   │   └── repository/
│   │       └── PurchaseOrderRepository.java    ← port
│   ├── asn/
│   │   ├── AdvanceShipmentNotice.java
│   │   ├── AsnId.java
│   │   ├── AsnLine.java
│   │   └── repository/
│   │       └── AsnRepository.java              ← port
│   ├── supplier/
│   │   ├── Supplier.java                       ← v1 임시 마스터 (v2 supplier-service 분리 시 마이그레이션)
│   │   ├── SupplierId.java
│   │   ├── SupplierStatus.java                 ← enum: ACTIVE/INACTIVE/CONTRACT_EXPIRED
│   │   ├── SupplierCredentials.java            ← 암호화 보관 (S6)
│   │   └── repository/
│   │       └── SupplierRepository.java         ← port
│   ├── audit/
│   │   ├── AuditLog.java                       ← S7 actor + before/after
│   │   └── repository/
│   │       └── AuditLogRepository.java         ← port
│   ├── tenant/
│   │   └── TenantContext.java                  ← Spring SecurityContext 어댑터
│   └── error/
│       ├── PoNotFoundException.java
│       ├── PoAlreadyConfirmedException.java
│       ├── PoQuantityExceededException.java
│       ├── PoStatusTransitionInvalidException.java
│       ├── AsnOverreceiptException.java
│       ├── SupplierNotFoundException.java
│       ├── SupplierInactiveException.java
│       └── CatalogSkuUnknownException.java     ← rules/domains/scm.md Standard Error Codes
├── application/                                 ← use case + port 사용
│   ├── DraftPurchaseOrderUseCase.java           ← DRAFT 작성
│   ├── SubmitPurchaseOrderUseCase.java          ← DRAFT → SUBMITTED + supplier 발신
│   ├── AcknowledgePurchaseOrderUseCase.java     ← SUBMITTED → ACKNOWLEDGED (supplier ack 수신)
│   ├── ConfirmPurchaseOrderUseCase.java         ← ACKNOWLEDGED → CONFIRMED
│   ├── CancelPurchaseOrderUseCase.java          ← 취소 (CONFIRMED 이전만)
│   ├── ReceiveAsnUseCase.java                   ← ASN 수신 → PARTIALLY_RECEIVED / RECEIVED
│   ├── GetPurchaseOrderUseCase.java
│   ├── ListPurchaseOrdersUseCase.java           ← 페이지네이션 + 필터
│   ├── port/
│   │   ├── inbound/                             ← (use case 자체가 inbound port)
│   │   └── outbound/
│   │       ├── SupplierAdapterPort.java         ← PO 발신 / ack 수신 / catalog
│   │       ├── EventPublisherPort.java          ← outbox 적재
│   │       └── ClockPort.java                   ← 테스트 가능 시계
│   └── service/
│       └── PurchaseOrderApplicationService.java ← @Transactional, outbox 적재
├── adapter/
│   ├── inbound/
│   │   ├── web/
│   │   │   ├── PurchaseOrderController.java     ← /api/procurement/po
│   │   │   ├── AsnWebhookController.java         ← /api/procurement/webhooks/asn (supplier 입구)
│   │   │   ├── SupplierAckWebhookController.java ← /api/procurement/webhooks/supplier-ack
│   │   │   ├── dto/                              ← request/response DTO
│   │   │   ├── advice/
│   │   │   │   └── GlobalExceptionHandler.java  ← 401/403/404/409/422 envelope
│   │   │   └── filter/
│   │   │       └── TenantClaimEnforcer.java     ← service 레벨 tenant_id=scm fail-closed
│   │   └── messaging/
│   │       └── (v2 — settlement.invoice.received 컨슈머 등)
│   └── outbound/
│       ├── persistence/
│       │   ├── jpa/
│       │   │   ├── PurchaseOrderJpaEntity.java
│       │   │   ├── PurchaseOrderJpaRepository.java
│       │   │   ├── AsnJpaEntity.java
│       │   │   ├── SupplierJpaEntity.java
│       │   │   ├── AuditLogJpaEntity.java
│       │   │   └── PoStatusHistoryJpaEntity.java
│       │   └── adapter/
│       │       ├── PurchaseOrderRepositoryAdapter.java   ← port impl
│       │       ├── AsnRepositoryAdapter.java
│       │       ├── SupplierRepositoryAdapter.java
│       │       └── AuditLogRepositoryAdapter.java
│       ├── supplier/
│       │   ├── rest/
│       │   │   ├── RestSupplierAdapter.java     ← v1 mock REST adapter (port impl)
│       │   │   ├── SupplierApiClient.java        ← WebClient + Resilience4j
│       │   │   └── IdempotencyKeyGenerator.java  ← S2: supplier 호출 idempotency key
│       │   └── (v2 — EdiSupplierAdapter, SftpSupplierAdapter)
│       ├── messaging/
│       │   ├── outbox/
│       │   │   ├── OutboxEntity.java
│       │   │   └── OutboxRelay.java              ← @Scheduled, libs:java-messaging
│       │   └── KafkaEventPublisherAdapter.java   ← EventPublisherPort impl (outbox 적재)
│       └── crypto/
│           └── SupplierCredentialsEncryptor.java ← S6: column-level AES-GCM
└── config/
    ├── ServiceLevelOAuth2Config.java             ← gateway 우회 방어 fail-closed
    ├── ResilienceConfig.java                     ← circuit breaker / retry / bulkhead bean
    ├── RedisConfig.java
    └── KafkaProducerConfig.java
```

Hexagonal 의 핵심 원칙:
- `domain/` 는 Spring / JPA / Kafka 의존 0 — 순수 Java + Lombok 만
- `application/` 는 port 인터페이스만 호출, 구체 adapter 모름
- `adapter/inbound/` 는 application 호출, `adapter/outbound/` 는 application 의 port 를 구현

### 4. Database schema (Flyway)

`db/migration/procurement/V1__init.sql`:

- `purchase_orders` (id UUID, po_number VARCHAR UNIQUE, tenant_id, supplier_id, buyer_account_id, status, total_amount NUMERIC(18,2), currency CHAR(3), version INT, created_at, updated_at, submitted_at, confirmed_at, INDEX (tenant_id, status, created_at DESC), INDEX (tenant_id, supplier_id, status))
- `purchase_order_lines` (id UUID, po_id, line_no INT, sku VARCHAR, supplier_sku VARCHAR, quantity NUMERIC, unit_price NUMERIC(18,2), tenant_id, INDEX (po_id, line_no UNIQUE))
- `po_status_history` (id, po_id, from_status, to_status, actor_account_id, actor_type, reason, occurred_at, tenant_id) — append-only (S7)
- `suppliers` (id UUID, tenant_id, name, status, contract_started_at, contract_expires_at, contact_info JSONB, created_at, INDEX (tenant_id, status))
- `supplier_credentials` (supplier_id PK, credential_type, encrypted_payload BYTEA, encryption_key_id, tenant_id, rotated_at) — S6 column-level 암호화
- `advance_shipment_notices` (id UUID, po_id, supplier_asn_ref VARCHAR, expected_arrival_at TIMESTAMP, received_at TIMESTAMP NULL, tenant_id, INDEX (po_id, expected_arrival_at), UNIQUE (supplier_asn_ref, tenant_id) — S2 멱등 키)
- `asn_lines` (id, asn_id, po_line_id, quantity_shipped NUMERIC, quantity_received NUMERIC NULL, tenant_id, INDEX (asn_id))
- `audit_log` (id, tenant_id, aggregate_type, aggregate_id, action, actor_account_id, actor_type, before JSONB, after JSONB, occurred_at, INDEX (aggregate_type, aggregate_id, occurred_at DESC), INDEX (tenant_id, occurred_at DESC))
- `outbox_events` (id, aggregate_type, aggregate_id, event_type, payload JSONB, tenant_id, created_at, processed_at NULL, INDEX (processed_at NULLS FIRST, created_at))

모든 테이블에 `tenant_id` 컬럼 + 인덱스 prefix (gateway 가 `tenant_id=scm` 만 통과시키지만 service 레벨 fail-closed 일관성).

### 5. API contract (신규)

- `projects/scm-platform/specs/contracts/http/procurement-api.md` (신규):
  - `POST /api/procurement/po` — 발주 작성 (DRAFT)
  - `GET /api/procurement/po/{id}` — 단건 조회
  - `GET /api/procurement/po` — 검색 / 페이지네이션 (status, supplier, date range)
  - `POST /api/procurement/po/{id}/submit` — DRAFT → SUBMITTED (supplier 외부 발신)
  - `POST /api/procurement/po/{id}/confirm` — ACKNOWLEDGED → CONFIRMED
  - `POST /api/procurement/po/{id}/cancel` — 취소 (CONFIRMED 이전)
  - `POST /api/procurement/webhooks/supplier-ack` — supplier 가 보내는 ack (외부 inbound)
  - `POST /api/procurement/webhooks/asn` — supplier 가 보내는 ASN (외부 inbound)
  - 모든 응답: `{ data: ..., meta: ... }` envelope (gateway 의 ApiErrorEnvelope 일관)
  - 에러: `{ code, message, details? }` — code 는 [rules/domains/scm.md](../../../../rules/domains/scm.md) Standard Error Codes
  - **Idempotency-Key 헤더** 필수 (모든 mutating endpoint): client 가 동일 key 로 재시도 시 동일 응답 보장 (S1)

### 6. Event contracts (신규)

- `projects/scm-platform/specs/contracts/events/procurement-events.md` (신규):
  - `scm.procurement.po.submitted` — { po_id, po_number, supplier_id, buyer_account_id, total_amount, currency, tenant_id, submitted_at }
  - `scm.procurement.po.acknowledged` — { po_id, supplier_ack_ref, acknowledged_at, tenant_id }
  - `scm.procurement.po.confirmed` — { po_id, confirmed_at, actor_account_id, tenant_id }
  - `scm.procurement.po.canceled` — { po_id, reason, canceled_at, actor_account_id, tenant_id }
  - `scm.procurement.po.received` — { po_id, received_at, tenant_id } (RECEIVED 진입 시)
  - `scm.procurement.po.closed` — { po_id, closed_at, tenant_id } (settlement 완료 후 v2)
  - `scm.procurement.asn.received` — { asn_id, po_id, supplier_asn_ref, expected_arrival_at, received_at, tenant_id }
  - 이벤트 envelope: `{ event_id (UUID v7), event_type, occurred_at, payload, tenant_id }`
  - 멱등 키: `event_id` — consumer 가 중복 처리 방지
  - outbox 패턴 — `libs:java-messaging` 의 `OutboxEntity` / `OutboxRelay` 재사용 (S1: 비즈니스 트랜잭션과 동일 트랜잭션 적재)

### 7. Spec 작성

- `specs/services/procurement-service/architecture.md` — Service Type=`rest-api`, Architecture Style=**Hexagonal** ([architecture-decision-rule.md](../../../../platform/architecture-decision-rule.md): 외부 supplier adapter 분리가 도메인 핵심)
- `specs/services/procurement-service/state-machines/po-status.md` — DRAFT → SUBMITTED → ACKNOWLEDGED → CONFIRMED → (PARTIALLY_RECEIVED →) RECEIVED → SETTLED → CLOSED 다이어그램 ([rules/domains/scm.md](../../../../rules/domains/scm.md) Required Artifacts #1)
- `specs/services/procurement-service/data-model.md` — 엔터티 / 인덱스 / tenant_id 컬럼 정책
- `specs/services/procurement-service/dependencies.md` — postgres, redis, kafka, GAP IdP, supplier 외부 시스템, gateway-service
- `specs/services/procurement-service/observability.md` — 메트릭 (po_state_transitions_total, supplier_call_duration_seconds, supplier_circuit_state, outbox_lag_seconds), 로그, 트레이스
- `specs/services/procurement-service/overview.md` — 1-pager 책임 요약
- `specs/services/procurement-service/integration/supplier-adapters.md` — supplier adapter port + v1 mock REST 구조 + 향후 EDI/SFTP 확장 (Required Artifacts #3 의 부분 충족)

### 8. Tests

- 단위:
  - `PoStatusMachineTest` — 전체 전이 매트릭스 (DRAFT→SUBMITTED, ACKNOWLEDGED→CONFIRMED, ... + 잘못된 전이 reject)
  - `PurchaseOrderTest` — aggregate invariant (line 합계 ≤ total, 동일 line_no 중복 reject)
  - 각 use-case 단위 테스트 (port mock)
  - `IdempotencyKeyGeneratorTest` (S2)
  - `SupplierCredentialsEncryptorTest` (S6 round-trip)
- 슬라이스:
  - `PurchaseOrderControllerTest` (`@WebMvcTest`)
  - `AsnWebhookControllerTest`, `SupplierAckWebhookControllerTest`
- 통합 (`@Tag("integration")`):
  - `ProcurementServiceIntegrationTest` — happy path (작성 → submit → ack → confirm → ASN 수신 → outbox → Kafka 발행 검증)
  - `MultiTenantIsolationTest` — `tenant_id=scm` 외 토큰 시 service 레벨 403 (gateway 우회 시뮬레이션)
  - `OutboxRelayIntegrationTest` — outbox → Kafka → processed_at
  - `SupplierCircuitBreakerIntegrationTest` — WireMock supplier 5xx → circuit OPEN → fast-fail (Resilience4j 검증)
  - `SupplierIdempotencyIntegrationTest` — 동일 idempotency key 로 supplier 재호출 시 중복 PO 생성 안 됨 (S2)
  - `PoStateMachineIntegrationTest` — DB 트랜잭션 안에서 상태 전이 + outbox 적재 atomic (S1)
  - `AuditLogIntegrationTest` — PO confirm 시 audit_log 적재 (S7)
  - `AsnOverreceiptIntegrationTest` — ASN 수량 > PO 잔여 시 422 reject + outbox 미발행
- 컨트랙트: `ProcurementApiContractTest` (요청/응답 스키마)

### 9. gateway-service 라우트 활성화 검증

- TASK-SCM-BE-001 의 `application.yml` 의 `procurement-service` placeholder 라우트 (`/api/v1/procurement/**` → `http://procurement-service:8080`) 가 본 task 완료 시 실제로 응답
- 별도 gateway 변경은 없음 — 단 본 task 의 integration / e2e 테스트가 gateway → procurement 경로를 한 번 검증

### 10. docker-compose 통합

- `projects/scm-platform/docker-compose.yml` 에 `procurement-service` 추가:
  - `expose: ["8080"]`, `depends_on: [postgres, kafka, redis]`, networks: `scm-platform-net`
  - 환경 변수: `OIDC_ISSUER_URL`, `JWT_JWKS_URI`, `POSTGRES_HOST`, `POSTGRES_DB=scm_procurement`, `KAFKA_BOOTSTRAP`, `REDIS_HOST`, `SPRING_PROFILES_ACTIVE`, `SUPPLIER_CREDENTIALS_KEY` (S6 암호화 키)
  - Traefik 라벨 불필요 (gateway 만 외부 노출)
- `infra/postgres/init/` 디렉토리 + `01-create-databases.sh` 스크립트 (TASK-SCM-BE-001 docker-compose.yml 의 주석 처리된 `# - ./infra/postgres/init:...` 라인 활성화) — `scm_procurement` DB 생성 (fan-platform 의 `infra/postgres/init/` 패턴 답습)
- `.env.example` 갱신 — `SUPPLIER_CREDENTIALS_KEY` (dev 더미 32바이트), `SUPPLIER_MOCK_BASE_URL` (v1 mock supplier endpoint)

### 11. CI 통합

- 루트 `.github/workflows/ci.yml` 의 "Build and check scm-platform backend (Docker-free)" step 에 추가:
  - `:projects:scm-platform:apps:procurement-service:check`
- boot-jars / integrationTest CI job 은 본 task 범위 밖 (TASK-MONO 후속 — 둘째 service 부트스트랩 시점에 함께)

## Out of Scope

- **inventory-visibility-service 부트스트랩** — TASK-SCM-BE-003 (병렬, 본 task 와 의존 없음)
- **supplier-service 분리** — v1 은 procurement 내부에 supplier 마스터 임시 보유, v2 별도 service 분리 시 마이그레이션 task 별도
- **demand-planning 통합** (자동 발주 추천) — v2 (현재는 buyer 수동 PO 작성만)
- **logistics-service 통합** (PO confirm → shipment 생성 트리거) — v2
- **settlement-service 통합** (PO ↔ ASN ↔ invoice reconciliation) — v2 (S8 자동 close 금지 룰만 본 task 범위에서 미적용)
- **실 EDI / SFTP supplier adapter** — v1 mock REST 만 (port 인터페이스는 확장 가능하게 설계)
- **secrets manager / vault 통합** — v1 은 column-level AES-GCM + 환경변수 키, v2 vault 마이그레이션
- **모더레이션 / supplier 제재 워크플로** — v2 admin-service
- **E2E 시나리오** (gateway → procurement → outbox → Kafka 컨슈머) — TASK-SCM-INT-001 (본 task + BE-003 완료 후 별도)
- **Frontend** — scm v1 = backend only

---

# Acceptance Criteria

## Build / Test

1. `./gradlew :projects:scm-platform:apps:procurement-service:build` 통과
2. `./gradlew :projects:scm-platform:apps:procurement-service:check` 통과 (단위 + 슬라이스)
3. `./gradlew :projects:scm-platform:apps:procurement-service:integrationTest` 통과 (Docker 필요, `@Tag("integration")`)
4. 루트 CI 의 Build & Test gradle 리스트에 본 모듈 추가 + 통과

## Runtime

5. `pnpm traefik:up` + `pnpm scm:up` 후 valid `tenant_id=scm` JWT 로 `POST http://scm.local/api/v1/procurement/po` → 201 + outbox 행 생성
6. outbox relay 가 Kafka 토픽 `scm.procurement.po.submitted.v1` 에 메시지 발행 (envelope + tenant_id 포함)
7. `tenant_id=wms` JWT → gateway 가 403 차단 (service 까지 안 옴)
8. `tenant_id=scm` 이지만 service 레벨 fail-closed 재검증 동작 (gateway 우회 시뮬레이션 단위 테스트 통과)

## State machine (S1)

9. PO 상태 전이는 매트릭스대로만 허용:
   - 허용: DRAFT→SUBMITTED, SUBMITTED→ACKNOWLEDGED, ACKNOWLEDGED→CONFIRMED, CONFIRMED→PARTIALLY_RECEIVED, PARTIALLY_RECEIVED→RECEIVED, RECEIVED→SETTLED, SETTLED→CLOSED, {DRAFT,SUBMITTED,ACKNOWLEDGED}→CANCELED
   - 거부 (422 `PO_STATUS_TRANSITION_INVALID`): CONFIRMED→DRAFT, RECEIVED→SUBMITTED, CONFIRMED→CANCELED 등
10. 동일 PO 의 동일 전이 두 번 호출 (멱등) — 같은 Idempotency-Key 면 동일 응답, 키 다르면 409 `PO_ALREADY_CONFIRMED` (또는 해당 상태 에러)

## Idempotency / Integration (S2)

11. supplier adapter 호출 시 모든 요청에 `Idempotency-Key` 헤더 부착 (UUID v7)
12. supplier 가 5xx 반복 시 Resilience4j circuit breaker OPEN → 후속 호출 fast-fail + metric `supplier_circuit_state{state="open"}` 노출
13. supplier 가 idempotency key 로 동일 요청 재처리 안 함을 본 service 가 신뢰 — 재시도 시 동일 PO 두 개 생성 안 됨 (통합 테스트 검증)

## ASN

14. ASN 수신 후 PO 잔여 수량 부분 수신 시 PARTIALLY_RECEIVED, 전량 수신 시 RECEIVED 자동 전이
15. ASN 수량 > PO 잔여 시 422 `ASN_OVERRECEIPT` + outbox 미발행 + audit_log 적재 (시도 기록)
16. 동일 `supplier_asn_ref` 두 번 수신 시 멱등 처리 (S2 — UNIQUE 인덱스 + idempotent upsert)

## Audit / Encryption

17. PO confirm / cancel / supplier 비활성화 시 audit_log 행 생성 (actor_account_id, before/after JSONB, occurred_at) (S7)
18. supplier_credentials 컬럼은 AES-GCM 암호화 — DB 직접 SELECT 시 평문 노출 0 (S6)

## Spec 무결성

19. `architecture.md` Service Type=`rest-api`, Architecture Style=`Hexagonal` 명시
20. `state-machines/po-status.md` 의 전이 매트릭스가 `PoStatusMachine.java` 와 일치
21. `procurement-api.md` 의 endpoint / status code 가 컨트롤러와 일치
22. `procurement-events.md` 의 envelope / payload 가 outbox 발행과 일치

---

# Related Specs

- [TASK-SCM-BE-001](../review/TASK-SCM-BE-001-gateway-service-bootstrap.md) (review) — gateway placeholder 라우트 활성화 대상. 본 task 의 선행.
- [`PROJECT.md`](../../PROJECT.md) § Service Map (v1) — procurement-service 책임
- [`PROJECT.md`](../../PROJECT.md) § GAP IdP Integration
- [`specs/integration/gap-integration.md`](../../specs/integration/gap-integration.md) (TASK-SCM-BE-001 에서 작성)
- [rules/domains/scm.md](../../../../rules/domains/scm.md) — Procurement bounded context, S1/S2/S6/S7/S8 mandatory rules, Standard Error Codes (Procurement / Supplier)
- [rules/traits/transactional.md](../../../../rules/traits/transactional.md) — 상태기계 / 멱등성 / outbox / 트랜잭션 경계
- [rules/traits/integration-heavy.md](../../../../rules/traits/integration-heavy.md) — circuit breaker / retry+jitter / DLQ / vendor fallback
- [platform/architecture-decision-rule.md](../../../../platform/architecture-decision-rule.md) — Hexagonal 선택 근거
- [platform/event-driven-policy.md](../../../../platform/event-driven-policy.md) — outbox 패턴
- [platform/service-types/rest-api.md](../../../../platform/service-types/rest-api.md)
- [TASK-FAN-BE-002](../../../fan-platform/tasks/done/TASK-FAN-BE-002-community-service-bootstrap.md) (done) — reference 부트스트랩 패턴 (Layered + 상태기계)
- [projects/wms-platform/apps/master-service/](../../../wms-platform/apps/master-service/) — reference (Hexagonal 적용 사례)

# Related Skills

- `.claude/skills/backend/springboot-api/SKILL.md`
- `.claude/skills/backend/architecture/hexagonal/SKILL.md` (Layered 가 아닌 Hexagonal)
- `.claude/skills/backend/exception-handling/SKILL.md`
- `.claude/skills/backend/validation/SKILL.md`
- `.claude/skills/backend/dto-mapping/SKILL.md`
- `.claude/skills/backend/transaction-handling/SKILL.md`
- `.claude/skills/backend/audit-logging/SKILL.md`
- `.claude/skills/backend/pagination/SKILL.md`
- `.claude/skills/backend/observability-metrics/SKILL.md`
- `.claude/skills/messaging/event-implementation/SKILL.md`
- `.claude/skills/messaging/outbox-pattern/SKILL.md`
- `.claude/skills/database/schema-change-workflow/SKILL.md`
- `.claude/skills/database/indexing/SKILL.md`
- `.claude/skills/database/transaction-boundary/SKILL.md`
- `.claude/skills/cross-cutting/circuit-breaker/SKILL.md` (있다면)
- `.claude/skills/cross-cutting/idempotency/SKILL.md` (있다면)
- `.claude/skills/cross-cutting/encryption-at-rest/SKILL.md` (있다면)
- `.claude/skills/testing/testcontainers/SKILL.md`
- `.claude/skills/testing/contract-test/SKILL.md`
- `.claude/skills/service-types/rest-api-setup/SKILL.md`

---

# Related Contracts

본 task 에서 신설:

- `projects/scm-platform/specs/contracts/http/procurement-api.md`
- `projects/scm-platform/specs/contracts/events/procurement-events.md`

기존 / 참조:

- `projects/scm-platform/specs/contracts/http/gateway-public-routes.md` (TASK-SCM-BE-001 에서 작성, procurement placeholder → 본 task 에서 status=active 갱신)

---

# Edge Cases

1. **Idempotency-Key 헤더 누락**: 모든 mutating endpoint 는 `Idempotency-Key` 필수 — 누락 시 400 `IDEMPOTENCY_KEY_REQUIRED`. (gateway 가 강제하지 않음 — service 레벨 검증)
2. **CONFIRMED 후 line 수정 시도**: PO 가 CONFIRMED 이후로는 line 추가/수정/삭제 reject (422 `PO_ALREADY_CONFIRMED`). 변경이 필요하면 cancel + 신규 PO.
3. **부분 ACK 수량**: supplier 가 PO line 의 일부 수량만 ack (예: PO 100개 중 80개 ack) — line 별 acknowledged_quantity 기록, 80 ack + 20 partial reject 면 PO 상태는 ACKNOWLEDGED 진입하되 나머지 20 은 별도 처리 (v1 = 단순화: 전량 ack 만 ACKNOWLEDGED, 부분은 reject + alarm).
4. **ASN 수신 시 PO 가 SUBMITTED 단계**: ack 없이 ASN 도착 — 422 `PO_STATUS_TRANSITION_INVALID` (ACKNOWLEDGED 이상에서만 ASN 수신 허용). 단, supplier 가 ack 누락 후 바로 ASN 보내는 케이스 → fallback: ack 자동 합치기 (config flag `procurement.asn.auto-ack-on-arrival`).
5. **Cross-tenant PO ID 추측**: 임의 UUID 로 다른 tenant PO 조회 시도 → tenant_id 미스매치로 404 (403 아님 — 존재 자체 누설 방지).
6. **outbox 폭증**: relay 처리량보다 적재 속도가 빠르면 lag 증가. metric `procurement_outbox_lag_seconds` 노출 + alarm.
7. **circuit breaker fallback**: supplier circuit OPEN 시 PO submit 호출 → 503 `SUPPLIER_UNAVAILABLE` + PO 는 SUBMITTED 진입 안 함 (DRAFT 유지). 운영자가 retry 가능. fan-platform 의 fail-open 패턴과 다름 — 본 도메인은 supplier 미발신 시 PO 가 submit 된 척하면 안 됨.
8. **Idempotency 키 충돌**: 같은 `Idempotency-Key` 다른 payload (예: PO 1000 vs 2000) → 422 `IDEMPOTENCY_KEY_MISMATCH`. Redis 에 (key, payload_hash, response) 24h TTL 캐시.
9. **Resilience4j retry + outbox**: supplier 호출 실패 시 retry 가 비즈니스 트랜잭션 안에서 일어나면 트랜잭션 길어짐 — outbox 적재만 트랜잭션, supplier 호출은 트랜잭션 밖 별도 saga step (또는 outbox 컨슈머가 supplier 호출).
10. **PO 동시 confirm**: 두 운영자가 동시에 같은 PO confirm 시도 → `@Version` optimistic lock 으로 두 번째 쪽 409 `CONFLICT`.

---

# Failure Scenarios

## A. Postgres 다운

모든 쓰기 / 조회 5xx — gateway 의 503 envelope. circuit breaker (libs:java-web) 적용. PO 작성 / 상태 전이 모두 reject.

## B. Kafka 다운

outbox 적재는 성공 (DB 트랜잭션). relay 만 실패 — `processed_at` null 유지, exponential backoff retry. metric `procurement_outbox_publish_failures_total` + alarm.

## C. Supplier 외부 시스템 다운

Resilience4j circuit breaker OPEN → fast-fail + 503 `SUPPLIER_UNAVAILABLE`. PO 는 SUBMITTED 진입 안 함, DRAFT 유지. 운영자 dashboard 에 supplier circuit 상태 노출. half-open 후 자동 복구.

## D. Redis 다운 (idempotency cache)

idempotency key 검증 fail-open 위험 (같은 키로 두 번 처리 가능) — fail-CLOSED 가 안전. Redis 다운 시 모든 mutating 요청 503 `IDEMPOTENCY_STORE_UNAVAILABLE`. metric `procurement_idempotency_cache_failures_total`. (fan-platform 의 read 캐시 fail-open 과 반대 — 멱등성 보장이 우선)

## E. JWKS 캐시 stale

TASK-SCM-BE-001 과 동일 — 5분 TTL + JwksHealthProbe 부팅 시 검증.

## F. 상태 기계 충돌

두 운영자가 동시에 PO 전이 시도 — `@Version` optimistic lock + 409 `CONFLICT` + 실패한 쪽이 retry.

## G. outbox dead letter

`outbox_events.processed_at` 가 1시간 이상 null + retry > 10 → DLQ 토픽 적재 + 운영자 알림 (v2 — v1 은 alarm 만).

## H. Supplier credentials 키 손상

`SUPPLIER_CREDENTIALS_KEY` 환경변수 누락/변경 시 모든 supplier 호출 실패 — 부팅 시 self-test (sample encrypt/decrypt) 로 fail-fast. v2 vault 마이그레이션 시 dual-key 지원.

## I. ASN webhook replay 공격

같은 supplier 의 같은 `supplier_asn_ref` 가 두 번 도착 시 — UNIQUE 인덱스 + idempotent upsert 로 두 번째 요청은 200 + 동일 응답 (S2). webhook signature 검증은 v2 (v1 은 mock supplier 라 불필요).

---

# Notes

- **Recommended impl model**: **Opus** — Hexagonal + 상태기계 + outbox + circuit breaker + idempotency + 암호화 + audit_log 의 동시 작성. 분석=Opus 4.7 / 구현 권장=Opus.
- **분량 추정**: fan-platform community-service 와 비슷하거나 약간 많음 (Hexagonal + 외부 adapter + 암호화 추가). 단일 PR 또는 2 PR 분할 (skeleton + adapter 각각) 가능. 분할 권장: 1) skeleton + state machine + REST API + outbox, 2) supplier adapter + webhook + circuit breaker — 이 분할은 backend-engineer 가 판단.
- **dependency 표현**:
  - `선행`: TASK-SCM-BE-001 (review/PR #194 대기 중. 본 task 는 BE-001 머지 후 시작)
  - `후속`: TASK-SCM-BE-003 (inventory-visibility, 병렬), TASK-SCM-BE-004 (settlement-service v2 부트스트랩 시 procurement → settlement 연결)
  - 의존 없음 (병렬 가능): TASK-SCM-BE-003 (inventory-visibility 는 wms snapshot 구독, procurement 와 데이터 공유 없음)
- **Phase 4 evaluation 영향**: `transactional` + `integration-heavy` 두 traits 가 처음으로 도메인 service 에 동시 stress — 라이브러리 (`libs:java-messaging` outbox, `libs:java-web` circuit breaker) 가 새 도메인에서 잘 작동하는지 검증. ADR-MONO-002 D3 churn 평가의 직접 입력. 라이브러리 변경이 발생하면 cross-project 영향 (wms / fan-platform) 확인 필수.
- **Hexagonal 선택 근거**: 외부 supplier 통합이 도메인 핵심이라 port/adapter 분리가 자연스러움. fan-platform community 는 외부 통합이 약해 Layered 가 단순했지만, procurement 는 supplier adapter 가 v1 mock → v2 EDI/SFTP 로 확장될 예정이라 Hexagonal 의 비용이 정당화됨. wms master-service 의 Hexagonal 답습.
- **CI 변경**: 루트 `.github/workflows/ci.yml` 의 scm-platform Build & Test step 에 1줄 추가. 라이브러리 영역 (`.github/workflows/`) 변경이라 ADR-MONO-002 D3 churn 평가 입력이지만 1줄이라 minimal.
- **dev 환경 토큰 발급**:
  ```
  curl -u scm-platform-internal-services-client:scm-dev \
       -d "grant_type=client_credentials&scope=scm.write" \
       http://gap.local/oauth2/token
  ```
  → 발급된 JWT 로 `POST http://scm.local/api/v1/procurement/po` 호출 (Acceptance Criteria #5).
