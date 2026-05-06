# Task ID

TASK-SCM-BE-002b

# Title

procurement-service test pyramid 추가 (TASK-SCM-BE-002 후속)

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

[TASK-SCM-BE-002](../done/TASK-SCM-BE-002-procurement-service-bootstrap.md) PR #239 가 procurement-service production code 89 files 머지 — Hexagonal architecture + PO 상태기계 + supplier adapter + outbox + OAuth2 RS 등. 그러나 agent FailedToOpenSocket 으로 mid-session 종료되어 **test pyramid 작성 직전 중단** — `compileTestJava NO-SOURCE`, `test NO-SOURCE` 상태. 본 task 가 누락된 test pyramid 를 추가한다.

production code 가 안정적으로 머지·CI green 통과한 상태이므로 본 task 는 deterministic test 작성 + 리팩토링 발견 시 small fix 만 동반.

---

# Scope

## In Scope

### Domain unit tests (`src/test/java/.../domain/`)

- **PoStatusMachine 8 transition** — DRAFT→SUBMITTED→ACKNOWLEDGED→CONFIRMED→PARTIALLY_RECEIVED→RECEIVED→SETTLED→CLOSED 정상 흐름 + 잘못된 전이 모두 `PoStatusTransitionInvalidException`
- **Money VO** — currency mismatch / negative amount / scale 처리
- **9 domain exception 발화 경로** — AsnOverreceiptException, CatalogSkuUnknownException, IdempotencyKeyMismatchException, PoAlreadyConfirmedException, PoNotFoundException, PoQuantityExceededException, PoStatusTransitionInvalidException, SupplierInactiveException, SupplierUnavailableException, SupplierNotFoundException
- **PurchaseOrder aggregate invariants** — line 추가/수정 제약, `submit()` / `confirm()` / `cancel()` 호출 조건

### Application layer unit tests

- **PurchaseOrderApplicationService** — 6 commands (Draft / Submit / Acknowledge / Confirm / Cancel / ReceiveAsn) 핵심 흐름 + idempotency check 분기
- **outbound port mocking** — ClockPort, IdempotencyStore, SupplierAdapterPort
- **ProcurementEventPublisher** — outbox row 생성 검증 (mock OutboxWriter)

### Slice tests (`@WebMvcTest` / `@DataJpaTest`)

- **PurchaseOrderController** — 8 endpoint 의 happy path + 401 (no JWT) / 403 (tenant_id mismatch) / 4xx domain exception → ApiErrorBody 매핑 (`GlobalExceptionHandler` 검증)
- **AsnWebhookController** + **SupplierAckWebhookController** — webhook 수신 + idempotency 검증
- **JPA slice** — PurchaseOrderJpaRepository / SupplierJpaRepository / AsnJpaRepository / AuditLogJpaRepository (H2 in-memory, Flyway V1 적용)

### Integration tests (`@Tag("integration")` + Testcontainers)

- **multi-tenant isolation** — tenant A 의 PO 가 tenant B 에서 조회되지 않음
- **outbox relay** — PO submit → outbox_events row 생성 → polling scheduler 가 Kafka 발행
- **supplier circuit breaker** — RestSupplierAdapter 가 5xx 연속 시 circuit OPEN, fallback path
- **supplier idempotency** — 동일 idempotency key 재전송 시 200 (재실행 NO)
- **state machine atomicity** — submit + outbox + audit history 단일 transaction (rollback 시 모두 일관)
- **asn overreceipt** — quantity > po line 잔여 시 `AsnOverreceiptException`
- **audit log** — 모든 state transition + cancel + confirm 경로마다 audit row 생성

### CI

- 본 task 의 PR 가 `:projects:scm-platform:apps:procurement-service:check` (unit + slice) green
- CI `Integration (...)` job 에 procurement-service IT 포함 (Testcontainers)

## Out of Scope

- Production code 변경 (이미 PR #239 로 안정 머지됨). Test 작성 중 발견된 명백한 버그만 small fix 동반 (별도 PR 또는 본 PR 의 별 commit).
- supplier 실 EDI/SFTP integration (v2 — TASK-SCM-BE-XXX)
- inventory-visibility-service 연계 (TASK-SCM-BE-003 영역)
- 성능 테스트 / load test

---

# Acceptance Criteria

## 통과

1. domain unit + application unit + slice tests + IT 모두 PASS
2. `:procurement-service:check` (`test` task) green
3. `:procurement-service:integrationTest` (Testcontainers) green
4. CI `Build & Test` + `Integration (scm-platform)` 또는 통합 GAP/master/scm IT job 에 procurement IT 추가 PASS

## Coverage

5. PoStatusMachine 8 transition 모두 검증 (정상 + 비정상 전이)
6. 9 domain exception 모두 발화 경로 1개 이상 test
7. 8 REST endpoint 모두 happy + 401/403/4xx 핵심 분기 검증
8. integration tests 7건 (위 In Scope 의 Integration tests 항목 7개) 모두 통과

## CI

9. main CI 다음 run SUCCESS — Build & Test + Frontend + GAP IT + master IT + boot jars 모두 pass

---

# Related Specs

- [TASK-SCM-BE-002](../done/TASK-SCM-BE-002-procurement-service-bootstrap.md) — 직접 선행
- `projects/scm-platform/specs/services/procurement-service/architecture.md`
- `projects/scm-platform/specs/services/procurement-service/state-machines/po-status.md`
- `projects/scm-platform/specs/services/procurement-service/integration/supplier-adapters.md`

---

# Related Contracts

- `projects/scm-platform/specs/contracts/http/procurement-api.md` — 8 endpoint + webhook 2
- `projects/scm-platform/specs/contracts/events/procurement-events.md` — 7 event types

---

# Target Service / Component

- `projects/scm-platform/apps/procurement-service/src/test/java/...` (모든 신규)
- 필요 시 `src/main/...` 의 small fix (test 가 발견하는 명백한 버그만)

---

# Implementation Notes

- 환경 차단 주의: Docker Desktop 4.36+ 의 socket 회귀로 Testcontainers 가 로컬에서 차단됨 (memory `project_testcontainers_docker_desktop_blocker.md` 참조). user-side fix 후 진행 권장. CI 는 정상 동작.
- fan-platform community-service 또는 GAP security-service 의 IT 패턴 답습 (`AbstractIntegrationTest` 상속, `@Tag("integration")`, `@DirtiesContext(AFTER_CLASS)` 등).
- multi-tenant isolation IT 는 `tenant_id` claim 을 다르게 한 두 JWT 로 검증.
- supplier circuit breaker IT 는 WireMock 으로 5xx stub → 연속 호출 → CircuitBreakerOpenException 검증.

---

# Edge Cases

1. **DirtiesContext + Testcontainers 비용**: 7 IT class 가 각각 fresh context 사용 시 startup 누적 — possible mitigation: 일부 IT 를 단일 class 로 통합 (테스트 그룹화).
2. **outbox polling scheduler 와 IT 충돌**: scheduler 가 IT 도중 작동 → race. test profile 에서 비활성화 or @Order 로 명시적 trigger.
3. **AES credential encryptor 의 master key**: test profile 에서 별도 fixed key 주입 (production key leak 방지).

---

# Failure Scenarios

## A. Production code 의 명백한 버그 발견

본 task 진행 중 명백한 버그 발견 시: small fix 를 본 PR 에 commit (별 commit) + PR description 에 명시.

## B. CI 환경 vs 로컬 차이

local `:check` PASS / CI `:check` FAIL → flaky 또는 환경 의존 → @Tag 분리 또는 explicit await 추가.

## C. supplier circuit breaker IT 의 RestClient 구성 차이

local 에서 WireMock + lazy URL resolution 이 정상이나 CI 에서 timing race → `@DirtiesContext` 또는 `@DynamicPropertySource` 명시화.

---

# Test Requirements

- domain unit ≥ 30개 (PoStatusMachine + Money + 9 exception + aggregate invariants)
- application unit ≥ 12개 (6 command × 2 — happy + edge)
- slice ≥ 15개 (8 controller endpoint + 2 webhook + 5 JPA)
- integration ≥ 7개 (위 7건)
- 합계 ≈ 64+ test

---

# Definition of Done

- [ ] domain unit + application unit + slice + IT 모두 작성 + PASS
- [ ] `:procurement-service:check` PASS
- [ ] `:procurement-service:integrationTest` PASS (Testcontainers — Docker fix 후)
- [ ] CI Build & Test + Integration job PASS
- [ ] PR description 에 cluster 별 test 추가 결과 + 발견된 small fix 명시
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Sonnet** — production code 가 안정적, test 작성은 deterministic.
- **분량 추정**: medium — 64+ test, 그러나 production code 는 read-only 라 spec 추적이 깔끔.
- **dependency**:
  - `선행`: TASK-SCM-BE-002 (이미 done). Docker WSL fix (memory `project_testcontainers_docker_desktop_blocker.md`) 가 IT local 검증 위해 권장 — CI 만으로도 진행 가능.
  - `병렬 가능`: TASK-SCM-BE-003 (inventory-visibility-service bootstrap, 데이터 공유 0).
- **CI gating**: 본 PR 자체 영향 = procurement IT 추가 (Testcontainers 환경 필요).
