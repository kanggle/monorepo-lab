# Task ID

TASK-SCM-INT-001

# Title

scm-platform cross-service E2E — procurement → inventory-visibility flow + GAP IdP 통합 (Docker compose)

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
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

scm-platform 의 첫 cross-service E2E 검증. [TASK-SCM-BE-002](../done/TASK-SCM-BE-002-procurement-service-bootstrap.md) (procurement-service) + [TASK-SCM-BE-003](../done/TASK-SCM-BE-003-inventory-visibility-service-bootstrap.md) (inventory-visibility-service) 의 production code 가 머지된 상태에서, 실 docker compose 환경 (gateway + procurement + inventory-visibility + GAP + MySQL + Kafka + Redis + Postgres) 에서 cross-service 흐름이 동작하는지 검증.

본 task 는 **monorepo Phase 4 catalyst 의 cross-project event consumption 검증 단계** — wms-platform 의 inventory event 가 scm-platform 의 inventory-visibility-service 로 정상 전달되는지 (다른 project 간 contract 의존) + procurement-service 가 발행하는 event 가 후속 service 로 흐르는지 (같은 project 내 event chain).

또한 GAP OAuth2 통합 검증 — `tenant_id=scm` claim 강제 fail-closed 가 cross-tenant 격리에 정상 작동하는지.

본 task 완료 후:

- `projects/scm-platform/tests/e2e/` 디렉터리에 e2e test class들 + docker-compose.scm-e2e.yml 추가
- gateway → procurement → outbox → Kafka → inventory-visibility 의 happy path 검증
- cross-tenant isolation 검증 (tenant A JWT 로 tenant B PO 조회 시 404)
- circuit breaker E2E 검증 (supplier WireMock 5xx → 503 응답)
- root CI `.github/workflows/ci.yml` 또는 `nightly-e2e.yml` 에 scm e2e job 추가 (path-filter 적용)

---

# Scope

## In Scope

### docker-compose.scm-e2e.yml

기존 `projects/scm-platform/docker-compose.yml` 확장 또는 별 overlay:
- Traefik (가능하면 공유)
- MySQL (procurement DB)
- Postgres (inventory-visibility DB)
- Kafka + Zookeeper
- Redis
- GAP auth-service (test JWT 발행) + GAP DB
- procurement-service
- inventory-visibility-service
- gateway-service
- WireMock (supplier mock)

### E2E test classes (≥ 5)

`@Tag("e2e")` + `@Testcontainers(disabledWithoutDocker = true)` + `Slf4jLogConsumer` Traefik / 각 service 의 healthcheck 대기:

1. **`ProcurementHappyPathE2E`** — buyer JWT 로 `POST /api/procurement/po` (DRAFT) → `POST /{poId}/submit` (supplier WireMock 200) → outbox row → Kafka topic `scm.procurement.po.submitted.v1` 발행 검증 (Kafka consumer poll)
2. **`SupplierAckWebhookE2E`** — 위 흐름 후 supplier ack webhook 호출 → `ACKNOWLEDGED` + `scm.procurement.po.acknowledged.v1` 발행 + supplier ack ref audit log
3. **`AsnReceiveE2E`** — confirm → ASN webhook (full quantity) → `RECEIVED` + `scm.procurement.po.received.v1` + `asn.received.v1` 발행 + audit log row 5+
4. **`CrossTenantIsolationE2E`** — tenant A actor JWT 로 tenant B PO 조회 시 404 (Edge Case #5)
5. **`SupplierCircuitBreakerE2E`** — WireMock 5xx × 3 → Resilience4j circuit OPEN → 503 SUPPLIER_UNAVAILABLE → cooldown 후 close

### Cross-project (wms → scm) event 검증 (≥ 1 test)

6. **`WmsInventoryAdjustedConsumedE2E`** — wms-platform 의 `wms.inventory.adjusted.v1` topic publish (직접 KafkaTemplate 으로) → inventory-visibility-service 의 consumer 가 받아서 `inventory_snapshots` 갱신 → `GET /api/inventory-visibility/snapshots/{sku}` 가 갱신된 값 반환

### CI integration

- `.github/workflows/nightly-e2e.yml` 에 scm e2e job 추가 (path-filter `projects/scm-platform/**` 또는 무조건 nightly)
- 또는 PR-time e2e job 분리 (path-filter scm 변경 시)

## Out of Scope

- 성능 / 부하 테스트
- procurement → inventory-visibility 직접 데이터 흐름 (S5 — 데이터 공유 0 정책)
- 실 EDI / SFTP supplier integration (v2)
- TASK-SCM-BE-002d 의 procurement-service Testcontainers IT (그건 service-internal 레벨, 본 task 는 cross-service E2E)

---

# Acceptance Criteria

## 통과

1. ≥ 6 E2E tests `@Tag("e2e")` 마킹 + PASS
2. local `docker compose -f docker-compose.scm-e2e.yml` 으로 PASS (Docker 환경 필요)
3. CI nightly e2e job (또는 PR e2e job) PASS
4. SCM service 회귀 0 (`:check` 변경 없음)

## Cross-project event consumption 검증

5. wms-platform → scm-platform 의 inventory event topic 흐름 검증 (Phase 4 catalyst 의 핵심 평가 입력)
6. eventId 기반 멱등 처리 (T8) — duplicate publish 시 1회 적용 확인

## 회귀 0

7. fan-platform / wms-platform / GAP / ecommerce E2E 회귀 0
8. main `Integration` Job + nightly e2e 안정 통과

---

# Related Specs

- [TASK-SCM-BE-002](../done/TASK-SCM-BE-002-procurement-service-bootstrap.md) — procurement service production
- [TASK-SCM-BE-003](../done/TASK-SCM-BE-003-inventory-visibility-service-bootstrap.md) — inventory-visibility service production
- [TASK-SCM-BE-002d](TASK-SCM-BE-002d-procurement-testcontainers-it.md) — procurement service-internal IT (병렬 보완)
- `projects/scm-platform/specs/services/procurement-service/architecture.md`
- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md`
- `projects/wms-platform/specs/contracts/events/inventory-events.md` — cross-project event contract
- `rules/domains/scm.md` § S5 (데이터 공유 0)
- `rules/traits/transactional.md` § T2 (outbox)
- `rules/traits/integration-heavy.md` § (supplier circuit breaker)

---

# Related Contracts

- `procurement-api.md` § 8 endpoint + 2 webhook
- `procurement-events.md` § 7 event types
- `inventory-visibility-api.md` § 5 read-only endpoints
- `inventory-visibility-subscriptions.md` § wms event subscription
- wms `inventory-events.md` § v1 토픽 3개

---

# Target Service / Component

- `projects/scm-platform/tests/e2e/` (신규 module 또는 기존 `apps/*/src/test/integration/` 답습)
- `projects/scm-platform/docker-compose.scm-e2e.yml` (신규 또는 기존 docker-compose.yml 확장)
- `.github/workflows/nightly-e2e.yml` (확장)

---

# Implementation Notes

- 답습 reference: `projects/global-account-platform/tests/e2e/` (GAP E2E 패턴) + `projects/fan-platform/tests/e2e/` (live-trio 패턴) + `projects/wms-platform/tests/e2e/` (live-pair 패턴)
- 선결: Docker Desktop 4.36+ socket 회귀 해결 (memory `project_testcontainers_docker_desktop_blocker.md`) — local 검증 위해
- GAP test JWT 발행: `JwtTestHelper.signJwt(actorContext, "scm", ...)` (TASK-SCM-BE-001 에서 도입된 helper)
- supplier WireMock URL — lazy resolution 패턴 (TASK-MONO-046-1 Cluster C 에서 검증된 패턴)
- nightly cron 트리거: `0 18 * * *` (KST 03:00) — TASK-MONO-045 의 nightly 패턴 답습

---

# Edge Cases

1. **각 service 의 healthcheck 미완료 상태에서 e2e 시작** — Spring Boot Actuator `/actuator/health` polling + Traefik 의 healthcheck label 사용
2. **Kafka topic auto-create timing** — eager startup 또는 e2e fixture 가 미리 toptic 생성
3. **outbox polling interval** — e2e 환경에서 짧게 (예: 1s) 설정해 timeout 단축
4. **cross-project event publish race** — wms-platform 에서 직접 KafkaTemplate 으로 발행 + Kafka 가 시작될 때까지 await 패턴

---

# Failure Scenarios

## A. Docker fix 안 됨

WSL Integration toggle / Docker downgrade / Rancher Desktop 중 어느 것도 효과 없으면 local 검증 포기, CI Linux runner 만 사용. PR description 에 명시.

## B. Cross-project consumption 가 본질적으로 wms 의존

wms-platform 의 inventory event 가 발행되려면 wms `apps/inventory-service` 도 띄워야 함 — overhead 증가. 단독으로 KafkaTemplate 으로 직접 발행 (mock 답습) 으로 단순화.

## C. nightly e2e job 자원 비용

각 service container × 4-5 + DB × 2 = ~8 container 가 nightly runner 에서 OOM 가능. `JAVA_OPTS=-Xmx256m` 등 으로 jvm heap 제약 + sequential execution.

---

# Test Requirements

- ≥ 6 E2E tests + 모두 `@Tag("e2e")` 마킹
- local docker compose PASS + nightly CI PASS
- SCM service-internal `:check` + `:integrationTest` 회귀 0

---

# Definition of Done

- [ ] docker-compose.scm-e2e.yml 추가
- [ ] ≥ 6 E2E test classes 작성 + PASS
- [ ] CI nightly e2e job 추가 또는 PR e2e job 추가
- [ ] knowledge/ 또는 scm specs/ 에 e2e 흐름 README 추가 (선택)
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** — cross-service compose orchestration + cross-project event 검증 + Phase 4 catalyst 평가 마무리.
- **분량 추정**: large — 6+ E2E class + docker-compose 확장 + CI workflow.
- **dependency**:
  - `선행`: TASK-SCM-BE-002 + TASK-SCM-BE-003 (모두 done). Docker Desktop 4.36+ socket 회귀 해결 (local 검증용 — CI 는 무관).
  - `병렬 가능`: TASK-SCM-BE-002d (service-internal IT) — 두 task 가 다른 레벨 (E2E vs IT) 이라 충돌 0.
- **Phase 4 catalyst 평가 1차 마무리**: 본 task 머지 시 SCM 의 3 traits + cross-project event consumption + GAP IdP 통합 모두 e2e 검증 완료 — Template 추출 (`monorepo-lab` → standalone) 의 "library 안정화" 단계 완료 신호.
