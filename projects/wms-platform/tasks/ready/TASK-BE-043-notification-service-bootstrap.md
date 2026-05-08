# Task ID

TASK-BE-043

# Title

notification-service Spring Boot bootstrap — alert routing + Slack channel adapter (v1 minimal slice)

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

`apps/notification-service/` 디렉토리는 v1 published portfolio 부터 placeholder 였음 (`project_wms_v1_published.md` § "다음 권장: notification + admin 부트스트랩"). 본 task 는 그 placeholder 를 production-shaped Spring Boot 모듈로 부트스트랩한다.

**구체 목표**: `wms.inventory.alert.v1` (low-stock 감지) 이벤트가 Kafka 로 발행되면 `notification-service` 가 구독해서 Slack incoming webhook 으로 라우팅 → delivery audit row + outbox `notification.delivered.v1` 이벤트까지 한 transaction 에서 처리. 즉 **end-to-end 한 알람 경로가 외부 채널까지 통과**하는 첫 production-shaped slice.

선행 spec 은 본 PR 에서 머지 완료된 상태 (`spec/wms-notification-service-bootstrap` branch):
- `specs/services/notification-service/architecture.md`
- `specs/services/notification-service/domain-model.md`

본 impl PR 은 위 두 spec + 본 task 를 입력으로 받아 **production code + Flyway + 테스트 피라미드 + boot CI 통합** 까지 한 PR 에서 완료한다.

---

# Scope

## In Scope

### 1. Spring Boot 모듈 부트스트랩

- `apps/notification-service/build.gradle` — sibling 패턴 답습 (master / inventory / inbound / outbound 의 build.gradle 비교)
- `apps/notification-service/src/main/java/com/wms/notification/NotificationServiceApplication.java`
- `apps/notification-service/src/main/resources/application.yml` — Kafka consumer config + Postgres + Slack webhook env vars
- `apps/notification-service/Dockerfile`
- `docker-compose.yml` 에 service entry 추가 (DB / Kafka / Redis 의존)
- 루트 `settings.gradle` 에 모듈 등록 + `package.json` 에 `wms:notification:*` 단축 스크립트 (sibling 패턴)
- `.github/workflows/ci.yml` `Build & Test (JDK 21)` job 의 `gradle :test` 리스트에 모듈 추가
- `.github/workflows/ci.yml` `Integration (...)` job 에 service jar packaging 추가 (다른 wms service 패턴 답습)

### 2. Hexagonal 패키지 구조 (architecture.md § Package Structure 그대로)

```
com.wms.notification/
├── domain/{alert, delivery, routing, error}
├── application/{port/{inbound,outbound}, service}
├── adapter/{inbound/messaging, outbound/{slack, persistence/jpa, messaging}}
└── config/
```

### 3. Domain layer

`domain-model.md` 의 aggregate / VO 그대로:

- `RoutingRule` aggregate + `RoutingMatcher` sealed type (3 impl: `AlwaysMatch`, `PayloadPredicateMatch`, `SeverityThresholdMatch`)
- `NotificationDelivery` aggregate + state machine + invariants
- `NotificationEventDedupe` aggregate
- `ChannelTarget` / `AlertSeverity` / `DeliveryStatus` / `DedupeOutcome` value types
- `NotificationDomainException` + 5 subtypes (`DELIVERY_RETRY_EXHAUSTED`, `DELIVERY_STATE_TRANSITION_INVALID`, `IDEMPOTENCY_KEY_DUPLICATE`, `ROUTING_AMBIGUOUS`, `ROUTING_RULE_NOT_FOUND`)

### 4. Application layer

- `ProcessInboundEventUseCase` (inbound port)
- `RetryFailedDeliveryUseCase` (inbound port)
- `ChannelPort` (sealed) + `SlackChannelPort` impl-marker
- `DeliveryRepository`, `RoutingRuleRepository`, `AlertDedupePort`, `OutboxPort` (outbound ports)
- `AlertRoutingService` — routing decision + dedupe + delivery row + outbox row in one TX
- `DeliveryExecutor` — post-commit dispatch to channel ports + retry-budget arithmetic

### 5. Adapter layer

- 6 inbound `@KafkaListener` consumer (architecture.md § Event Consumption 의 6 source topic)
- `SlackChannelAdapter` — Resilience4j `@CircuitBreaker` + `@Retry` + bounded HTTP client (3s connect / 5s read)
- JPA entities + repositories (mirroring `domain-model.md` § Persistence Layout)
  - **JSONB 컬럼은 모두 `@JdbcTypeCode(SqlTypes.JSON)`** — TASK-SCM-INT-001b root cause #2 + TASK-SCM-BE-005 회귀 가드 학습 답습
- `OutboxAdapter` — libs/java-messaging 패턴 (sibling outbox 와 동일)
- `notification-outbox-polling-scheduler` (libs/java-messaging concrete subclass)

### 6. Flyway

- `V1__init.sql` — 4 table (`notification_routing_rule`, `notification_delivery`, `notification_event_dedupe`, `notification_outbox`) — `domain-model.md` § Persistence Layout 그대로
- `V2__seed_routing_rules.sql` — `domain-model.md` § Seeded Routing Rules (v1) 의 6 row

### 7. Slack webhook 환경 변수

- `SLACK_WEBHOOK_URL_WMS_ALERTS`, `SLACK_WEBHOOK_URL_WMS_SHIPPING` 두 채널 env var
- `application.yml` 의 `wms.notification.channels.slack.<alias>.webhook-url` 으로 매핑
- 기본값: 빈 문자열 (env 미주입 시 `SlackChannelAdapter` 가 `ChannelNotConfiguredException` throw → delivery row 가 `FAILED` (permanent) → outbox 발행 → 로컬 dev 에서도 안전하게 fail-closed)

### 8. 테스트 피라미드 (architecture.md § Testing Requirements 그대로)

- **Unit** (≥ 30 tests):
  - `NotificationDelivery.transition()` 모든 합법/불법 transition
  - `RoutingMatcher` 3 impl × 각각 매치/비매치 케이스
  - `AlertRoutingService` (port fakes) — 6 inbound topic × 2-3 시나리오 each
  - `DeliveryExecutor` retry 산술 + 종단 status 결정 로직
- **Slice** (`@WebMvcTest` 없음 — REST 표면 0). `@DataJpaTest` 로 4 repo 검증
- **Channel adapter** (WireMock) — Slack 200 / 500 / 4xx / timeout / circuit-breaker open
- **Integration** (Testcontainers Postgres + Kafka):
  - `AbstractNotificationIntegrationTest` base (sibling SCM-BE-005 / SCM-BE-002d 패턴 답습)
  - 6 source topic × 1 happy-path IT each (≥ 6 IT)
  - DLT 라우팅 IT (poison envelope → `<topic>.DLT`)
  - 동일 eventId 재발행 → silent skip (replay-safety)
  - **JSONB 회귀 가드 IT** — `notification_delivery.payload_snapshot` JSONB null write 가능성 검증 (BE-005 패턴 답습)
- **Contract test** — `notification-subscriptions.md` payload shape (Open Items 라 본 PR 에 추가 권장)

### 9. Observability

- 5 metrics 등록 (architecture.md § Observability 그대로)
- structured logs with `eventId, deliveryId, channelId, attempt, traceId`

## Out of Scope

- REST surface (v2 — admin retry endpoint, preferences UI)
- Email / Push / SMS 채널 (v2)
- `notification_template` table + 운영자 편집 UI (v2)
- Multi-tenant 라우팅 (PROJECT.md `data_sensitivity: internal` 단일 테넌트 기준)
- 본 task 와 무관한 기존 service 변경 — 본 PR 은 신규 service add-only

---

# Acceptance Criteria

## 통과

1. `apps/notification-service/` 모듈 등록 + `./gradlew :projects:wms-platform:apps:notification-service:bootJar` 통과
2. Hexagonal 패키지 boundary 검증 (`platform/architecture-decision-rule.md` 의 self-test grep)
3. Flyway V1 + V2 마이그레이션 PostgreSQL 16 통과 (Testcontainers IT 에서 검증)
4. 6 source topic × happy-path IT 모두 PASS
5. SlackChannelAdapter WireMock IT — 200 / 500 / 4xx / timeout / circuit-open 5 케이스 모두 PASS
6. JSONB 회귀 가드 IT — `@JdbcTypeCode(SqlTypes.JSON)` 일시 제거 시 IT fail (검증 후 원복) — TASK-SCM-BE-005 패턴
7. Unit + slice + IT 누적 ≥ 50 tests
8. CI Build & Test (JDK 21) job — notification-service 모듈 PASS
9. CI Integration job (Testcontainers) — notification-service IT PASS
10. docker-compose 통합 — `pnpm wms:notification:up` 으로 service 부팅 + Slack webhook env var 미주입 시 모든 delivery `FAILED` 상태로 audit + listener 컨테이너 healthy

## 회귀 없음

11. 다른 wms service IT 회귀 0
12. main `Build & Test` / `Integration (...)` 모든 job pre-existing PASS 상태 유지

---

# Related Specs

- `specs/services/notification-service/architecture.md` — **본 task 의 source of truth**
- `specs/services/notification-service/domain-model.md` — aggregate + persistence
- `rules/domains/wms.md` — Admin / Operations bounded context
- `rules/traits/integration-heavy.md` — I1-I5 (본 service 가 가장 직접적 적용 사례)
- `rules/traits/transactional.md` — T1, T3, T4, T8
- `platform/service-types/event-consumer.md`
- `specs/services/admin-service/architecture.md` — sibling consumer-only 패턴 (스코프 다름)
- `specs/services/inventory-service/architecture.md` — sibling Hexagonal + outbox 패턴 reference

---

# Related Contracts

- `specs/contracts/events/inventory-events.md` — 구독 (alert + adjusted)
- `specs/contracts/events/inbound-events.md` — 구독 (asn.cancelled + inspection.completed)
- `specs/contracts/events/outbound-events.md` — 구독 (order.cancelled + shipping.confirmed)
- `specs/contracts/events/notification-subscriptions.md` — **신규** (본 PR 에서 작성 또는 follow-up)
- `specs/contracts/events/notification-events.md` — **신규** (본 PR 에서 작성 또는 follow-up)

---

# Target Service / Component

- `projects/wms-platform/apps/notification-service/` (전체 신규)
- `projects/wms-platform/specs/contracts/events/notification-subscriptions.md` (신규 또는 follow-up)
- `projects/wms-platform/specs/contracts/events/notification-events.md` (신규 또는 follow-up)
- `settings.gradle` (모듈 등록)
- `package.json` (단축 스크립트)
- `docker-compose.yml` (service entry)
- `.github/workflows/ci.yml` (Build & Test + Integration jar packaging)
- `platform/error-handling.md` (5 error code 등록)

---

# Edge Cases

1. **Slack webhook 미주입**: env var 빈 값 → `SlackChannelAdapter` 가 `ChannelNotConfiguredException` throw → delivery row `FAILED` (permanent) + outbox `notification.delivered.v1` with `outcome=FAILED_CHANNEL_NOT_CONFIGURED`. 로컬 dev / 자동 테스트에서 자연스럽게 fail-closed.
2. **Slack 5xx 일시 outage**: Resilience4j retry → exhaustion 후 PENDING → scheduled retry. 5번 시도 후에도 fail 시 FAILED.
3. **Slack 4xx (404 channel-not-found / 410 token-revoked)**: 영구 실패 — retry 안 함. delivery `FAILED` + outbox audit.
4. **동일 eventId Kafka replay**: dedupe 가 막아서 두 번째 호출은 silent exit. delivery row 0건 추가.
5. **Routing rule 변경 직후**: in-memory cache 60s TTL → 변경이 최대 1분 지연. 운영자에게 명시.
6. **JSONB null payload_snapshot**: `payload_snapshot NOT NULL` constraint 라 발생 안 함 — 발생 시 application 에러로 fail-closed.
7. **두 worker 인스턴스 동시 retry**: `SELECT … FOR UPDATE SKIP LOCKED` + version 컬럼으로 차단. 단일 인스턴스가 attempt 증가.
8. **Out-of-order events**: notification 라우팅은 last-write-wins 가 의미 없음 (이미 발생한 event 는 무조건 알림). 첫 도착 시 처리, 중복은 dedupe 차단.

---

# Failure Scenarios

## A. 첫 IT 부터 Testcontainers Docker 환경 fail

CI Linux runner 는 무관. 로컬 Windows/Rancher 는 메모리 [`project_testcontainers_docker_desktop_blocker.md`] 의 2026-05-08 transient regression (cold-start 1 cycle 만 정상) 에 영향. **운영 절차**: 매 cycle 사이 `rdctl shutdown && rdctl start`. CI 결과를 1차 신호로 사용. 자세한 reproduce path 는 `knowledge/incidents/2026-05-07-docker-cli-proxy-regression.md`.

## B. Slack webhook 으로 실제 메시지 발송 검증 못함

WireMock 이 충분 — Slack 자체와의 contract 는 incoming-webhooks API spec 으로 정의. 운영 검증은 별 admin task (deploy to dev environment + manual trigger) — 본 v1 task scope 외.

## C. spec § Open Items 의 contracts/events/notification-subscriptions.md 미작성

본 PR 안에서 같이 작성 권장. 분리 시 즉시 follow-up task 발행 (TASK-BE-043b candidate).

## D. integration-heavy trait 의 circuit breaker state 가 IT 에 누수

`@DirtiesContext(AFTER_CLASS)` + Resilience4j registry per Spring context 로 isolation. 단 sibling `OutboxRelayIntegrationTest` 패턴 답습 + per-class consumer group `wms-notification-${random.uuid}` (TASK-MONO-046-3 학습).

## E. 6-cycle CI burn 임계값 초과

TASK-MONO-046-7 의 11-cycle burn 학습: 6-cycle 후에도 통과 안 되면 production code architectural 결함 가능성. spec deviation 검토. v1 minimal slice 라 6 cycle 안에 안정화 가능 예상.

---

# Test Requirements

- ≥ 50 tests 누적 (unit + slice + IT)
- 6 source topic × happy-path IT 통과
- JSONB 회귀 가드 IT 검증 (제거 시 fail / 복원 시 PASS)
- Slack channel adapter 5 케이스 (WireMock) 통과
- main `Build & Test` + `Integration (...)` Job 모두 SUCCESS

---

# Definition of Done

- [ ] 1-9 In Scope 모두 구현
- [ ] Acceptance Criteria 1-12 모두 통과
- [ ] notification-service 모듈 boot jar build / docker-compose up 통과
- [ ] CI 모든 job pre-existing PASS 유지 + 신규 notification-service job 추가
- [ ] Open Items contracts (`notification-subscriptions.md`, `notification-events.md`) 본 PR 에 포함 또는 follow-up task 발행
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** — Hexagonal 신규 service + integration-heavy trait + outbox + Resilience4j + Slack adapter + 50+ tests. SCM-BE-002 + SCM-BE-003 부트스트랩 패턴 답습 가능 (참고: 두 task 모두 Opus).
- **분량 추정**: large (신규 service 부트스트랩, ~80 files / ~4000 LOC 예상 — sibling SCM-BE-002 / SCM-BE-003 비슷한 규모).
- **dependency**:
  - `선행`: 본 PR 의 spec 머지 (`spec/wms-notification-service-bootstrap`)
  - `병렬`: 없음
  - `후속`:
    - `TASK-BE-044` admin-service event-projection 구독 추가 (`notification.delivered.v1` 구독해서 admin dashboard 에 delivery rate 노출) — admin-service spec 에 이미 의도 명시
    - `TASK-BE-XXX` notification email/push 채널 v2
- **D4 churn freeze 영향**: 부분적. `settings.gradle` / `package.json` / `.github/workflows/ci.yml` / `docker-compose.yml` 변경 포함 — 공유 영역 변경. **메모리 [Monorepo Template Strategy] D4** = "TASK-MONO-046-7/8 면제" 명시. 본 task 는 면제 외. 그러나 새 service 부트스트랩은 churn freeze 의 의도 (라이브러리 churn 차단) 와 다른 영역 (project-internal 신규 추가). 사용자 승인 시 진행 권장.
- **CI 비용**: path-filter `wms-platform` + `workflows`/`libs` cross-project 활성화. service jar packaging 추가로 boot jars step 시간 +20s. 신규 IT 가 Integration job 시간 +1-2min.
