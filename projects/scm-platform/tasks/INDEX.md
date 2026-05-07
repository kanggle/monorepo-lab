# Tasks Index — scm-platform

This document defines task lifecycle, naming, and move rules for the **scm-platform** project. Repo-root [tasks/INDEX.md](../../../tasks/INDEX.md) covers monorepo-level (cross-project) tasks; this file covers scm-platform-internal tasks only.

---

# Lifecycle

backlog → ready → in-progress → review → done → archive

Only tasks in `ready/` may be implemented.

---

# Task Types

- `TASK-SCM-BE-XXX`: backend (Spring Boot service implementations)
- `TASK-SCM-INT-XXX`: cross-service integration / E2E (Testcontainers · Docker compose)
- `TASK-SCM-FE-XXX`: frontend — declared for future use, scm v1 is backend-only

---

# Move Rules

## backlog → ready
Allowed only when:
- related specs exist (`specs/services/<service>/architecture.md`, `specs/contracts/...`)
- related contracts are identified
- acceptance criteria are clear
- task template is complete

## ready → in-progress
Allowed only when implementation starts.

## in-progress → review
Allowed only when:
- implementation is complete
- tests are added
- contract / spec updates are completed if required

## review → done
Allowed only after review approval.

### Review Rules
- Tasks in `review/` must not be re-implemented directly.
- If a review reveals a bug or missing requirement, create a new fix task in `ready/` referencing the original task.
- Fix tasks must include the original task ID in their Goal section (e.g. "Fix issue found in TASK-SCM-BE-001").
- Do not modify a task file after it moves to `review/` or `done/`.

### PR Separation Rule (lifecycle ↔ PR boundary)

Each lifecycle transition lands in its own PR. **Never bundle task spec authoring with implementation in the same PR.**

| Stage | Recommended PR shape |
|---|---|
| `(writing) → ready` | **spec PR** — adds the task file to `ready/` + updates this `INDEX.md` ready list. No implementation code. |
| `ready → in-progress → review` | **impl PR** — moves the task file through `in-progress/` to `review/` and lands the implementation. Lifecycle moves and impl commits should be separate commits but live in one PR. |
| `review → done` | **chore PR** — moves merged task file(s) from `review/` to `done/` + updates the `INDEX.md` done list. May batch multiple merged tasks. |

The repo-root [tasks/INDEX.md](../../../tasks/INDEX.md) is the authoritative definition. This summary applies the same rule at the project level.

## done → archive
Allowed when no further active change is expected.

---

# Rule

Tasks must not be implemented from `backlog/`, `in-progress/`, `review/`, `done/`, or `archive/`.

---

# Task List

## backlog

(empty)

## ready

(empty)

## in-progress

- `TASK-SCM-INT-001b-deeper-investigation-2-scenarios.md` — TASK-SCM-INT-001a 6 fix 후 잔존 2 fail (SupplierCircuitBreaker 첫 POST 응답 409 instead of 201 + WmsInventoryAdjusted snapshot=0 instead of 42) 의 deeper investigation. Root cause 분리 가설: (a) idempotency / supplier validation / aggregate state, (b) Kafka consumer subscription / partition assignment / WmsInventoryAdjustedConsumer business logic. Cycle 1 = `@Disabled` 제거 + 응답 body / consumer log diagnostic. 선행=TASK-SCM-INT-001a (PR #260 머지). 분석=Opus 4.7 / 구현 권장=Opus.

## review

- `TASK-SCM-INT-001-procurement-inventory-visibility-e2e.md` — scm-platform 첫 cross-service E2E. procurement → outbox → Kafka → inventory-visibility 흐름 + GAP IdP `tenant_id=scm` fail-closed + supplier circuit breaker E2E + cross-tenant isolation + cross-project event consumption. PR #260 — 4/6 시나리오 PASS, 2 시나리오 `@Disabled("TASK-SCM-INT-001b: ...")` 분리. 6 fix (TASK-SCM-INT-001a) cascade. Phase 5 trigger 의 마지막 outstanding 의 1차 종결.

- `TASK-SCM-INT-001a-e2e-environment-fixup.md` — PR #260 의 6 cascade fix. (1) Slf4jLogConsumer attach 진단 인프라, (2) inventory-visibility V1 outbox + processed_events tables, (3) inventory-visibility JpaConfig (libs:java-messaging suppress 우회), (4) gateway @Primary on accountKeyResolver (TASK-MONO-044d 패턴), (5) e2e classpath PostgreSQL JDBC driver, (6) ProcurementDbFixtures URL host:port 명시 + Kafka topic 사전 생성. 5 fix 중 3 production / 2 test. 4/6 PASS, 2 잔존 → TASK-SCM-INT-001b 분리.

## done

- `TASK-SCM-BE-002d-procurement-testcontainers-it.md` — PR #257. TASK-SCM-BE-002b Phase 5 종결 — procurement-service Testcontainers IT 7 클래스 (multi-tenant isolation / outbox relay / supplier circuit breaker / supplier idempotency / state machine atomicity / asn overreceipt / audit log) + AbstractProcurementIntegrationTest base. Production small fix 3 (V1__init.sql `currency CHAR(3)` → `VARCHAR(3)` for PostgreSQL bpchar↔Hibernate VARCHAR mismatch / `@JdbcTypeCode(SqlTypes.JSON)` on `Supplier.contactInfoJson` + `AuditLog.beforeState`/`afterState` for Hibernate 6 JSONB binding) 별 commit 분리. Test infra fix: PO number suffix UUID v7 timestamp → pure random (collision in 65s window), AuditLog `@AfterEach` → `@AfterAll` for MockWebServer cross-test, IT-4 supplier-first call order alignment. local + CI 검증 — `Integration (scm-platform, Testcontainers)` Job pass 1m57s (PR #258 의 CI job 위에서). 9 tests PASS. **Phase 5 trigger 의 첫 outstanding 해소** — 남은 1건 = TASK-SCM-INT-001. 2026-05-07.

- `TASK-SCM-BE-002c-procurement-slice-tests.md` — PR #247. Phase 4 slice tests 추가. `@WebMvcTest` 3 controllers: PurchaseOrderControllerSliceTest 11 + AsnWebhookControllerSliceTest 4 + SupplierAckWebhookControllerSliceTest 4 = **20 slice tests**. + GlobalExceptionHandler small fix (`ResponseStatusException` 핸들러 부재로 webhook 401 이 catch-all 의 500 INTERNAL_ERROR 로 잘못 매핑되던 버그 — handler + test 1건 추가, 별 commit). H2 Flyway 호환 결과 **FAIL** — V1__init.sql 가 PostgreSQL syntax (JSONB/TIMESTAMPTZ/BYTEA/BIGSERIAL) → JPA slice 는 002d (Testcontainers IT) 로 통합. Security context 패턴: `TestingAuthenticationToken` + SecurityContextHolder 직접 + `@AutoConfigureMockMvc(addFilters=false)` (MockedStatic 불필요). 누적 procurement-service tests = 121 (002b 101 + 002c 20). 2026-05-06.

- `TASK-SCM-BE-002b-procurement-test-pyramid.md` — PR #243 + #244 + #245. procurement-service test pyramid 1차 완료 (TASK-SCM-BE-002 production code 후속). **101 tests 누적**: Phase 1 domain unit 64 (PoStatusMachine 49 — full transition matrix per actor + terminal/self-transition guards + linear lifecycle, Money VO 15 — factory normalization + null/negative/length validation + add() currency match) + Phase 2 application unit 16 (PurchaseOrderApplicationServiceTest — 6 commands × happy + edge, Mockito strict-safe with `lenient()`) + Phase 3 GlobalExceptionHandler 21 (모든 exception → ApiErrorBody status code 매핑 검증, direct unit test no Spring context). Phase 4 (slice WebMvcTest) → TASK-SCM-BE-002c 분리. Phase 5 (Testcontainers IT) → TASK-SCM-BE-002d 분리 (Docker fix 후). production code 무변경. CI Build & Test all PASS. 2026-05-06.

- `TASK-SCM-BE-003-inventory-visibility-service-bootstrap.md` — PR #241. scm-platform 두 번째 도메인 service `inventory-visibility-service` 부트스트랩. **Hexagonal architecture** + Service Type=`rest-api`+`event-consumer` + cross-node 재고 read-model (자사 wms / supplier / 3PL / in-transit). **cross-project event consumption 첫 사례** — wms-platform 의 `wms.inventory.{received,adjusted,transferred}.v1` 3 topic 구독 + EventDedupe (eventId 기반 멱등). **batch-heavy trait 첫 코드** — `@Scheduled` 5분 주기 staleness 감지 + ShedLock 분산 lock + alert event publish (`scm.inventory.alert.v1`). 5 read-only API endpoint + 4 entity (InventoryNode / InventorySnapshot / NodeStaleness / EventDedupe) + Flyway V1 schema + OAuth2 RS (RS256 / GAP JWKS / `tenant_id=scm` fail-closed). 81 files / 4409 insertions. Spec 3개 (architecture / data-model / staleness-monitoring) + contracts 2개 (inventory-visibility-api / inventory-visibility-subscriptions). gateway-service `/api/v1/inventory-visibility/**` 라우트 활성화. CI 12/12 PASS — Build & Test 1m42s, GAP/master/gateway/fan-platform IT all pass, frontend e2e pass, 모든 boot jars pass. 2026-05-06.

- `TASK-SCM-BE-002-procurement-service-bootstrap.md` — PR #239. scm-platform 핵심 도메인 service `procurement-service` 부트스트랩. **Hexagonal architecture** (domain/application/infrastructure/presentation) + PO 상태기계 8 단계 (DRAFT→SUBMITTED→ACKNOWLEDGED→CONFIRMED→PARTIALLY_RECEIVED→RECEIVED→SETTLED→CLOSED) + supplier adapter port + Resilience4j circuit breaker + idempotency + outbox polling scheduler + OAuth2 RS (RS256 / GAP JWKS / `tenant_id=scm` fail-closed) + AES credential encryptor + tenant claim enforcer filter + 9 domain exceptions + Money VO + audit history. JPA adapters 6개 + Flyway V1__init.sql 209 lines. REST controllers 3 (PurchaseOrder + AsnWebhook + SupplierAckWebhook). 89 files / 4231 insertions. CI 12/12 PASS — Build & Test 1m38s, GAP/master/gateway IT pass, Frontend e2e pass, 모든 boot jars pass. **Tests deferred** (agent FailedToOpenSocket mid-session) → TASK-SCM-BE-002b 분리. 2026-05-06.

- `TASK-SCM-BE-001-gateway-service-bootstrap.md` — scm-platform 의 첫 service `gateway-service` Spring Boot 부트스트랩. Spring Cloud Gateway 3.4 + OAuth2 Resource Server (GAP RS256 JWT 검증) + TenantClaimValidator (`tenant_id=scm` only) + Redis rate limit + Traefik label 활성화 (`scm.local`). procurement / inventory-visibility 라우트는 placeholder (BE-002/003 활성화). spec 3개 (gateway architecture / public-routes / gap-integration). 단위·슬라이스·통합 테스트 + 루트 CI Build & Test 에 모듈 추가. PR #194 머지. 2026-05-05.

## archive

(empty)
