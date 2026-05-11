# Task ID

TASK-BE-138

# Title

order-service choreographed-saga stuck-detector — `PENDING + payment_id IS NULL` 30 분 grace 경과 행을 sweep 하여 attempt cap 5 도달 시 `STUCK_RECOVERY_FAILED` 전이 + `order.alert.saga.recovery.exhausted` 알림 발행 (ADR-MONO-005 § D6 ecommerce order 행 Gap → Compliant)

# Status

ready

# Owner

backend / ecommerce

# Task Tags

- code
- event
- saga
- adr

---

# Goal

[ADR-MONO-005](../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md) § D6 의 ecommerce order saga 행은 현재 **Scenario B (Gap)** — choreographed `order.placed → payment.completed → order CONFIRMED` 흐름에 stuck-detector 가 없다. payment-service 가 어떤 이유로든 `OrderPlaced` 를 소비하지 못해 `payments` row 가 생성되지 않으면, 대응 `orders` row 는 무한히 `PENDING + payment_id=null` 상태로 남고 운영자에게는 알림이 가지 않는다. 현재는 `SELECT … FROM orders WHERE status='PENDING' AND payment_id IS NULL AND created_at < NOW() - INTERVAL '30 min'` 같은 ad-hoc 쿼리로만 식별 가능.

본 task 는 ADR § D3 (Category A multi-step saga sub-rules) 에 정합한 stuck-detector + escalation event 를 도입해 ADR § D6 ecommerce order 행을 **Compliant** 로 전이시킨다. 레퍼런스 구현은 wms outbound-service `SagaSweeper` + `SagaRecoveryHandler` (TASK-BE-050) — sweeper 와 recovery handler 를 분리해 Spring AOP self-invocation 회귀를 회피하는 두-빈 패턴.

---

# Scope

## In Scope

### 1. Domain — `OrderStatus` enum 확장

- `OrderStatus.STUCK_RECOVERY_FAILED` 신규 enum 값. terminal — `isCancellable()=false`.
- `Order.recordStuckRecoveryAttempt(Instant now)` 메서드 — `stuck_recovery_attempt_count` 를 +1 하고 `stuck_recovery_at` 을 갱신 (status 는 변경하지 않음).
- `Order.markStuckRecoveryFailed(Instant now)` 메서드 — `status = STUCK_RECOVERY_FAILED`, `updated_at` + `stuck_recovery_at` 갱신.
- `Order.reconstitute(...)` 시그니처에 새 두 필드 추가. `getStuckRecoveryAttemptCount()` + `getStuckRecoveryAt()` 게터 노출.

### 2. 영속성 — Flyway migration `V7`

- `V7__add_order_stuck_recovery_columns.sql`:
  - `ALTER TABLE orders ADD COLUMN stuck_recovery_attempt_count INTEGER NOT NULL DEFAULT 0;`
  - `ALTER TABLE orders ADD COLUMN stuck_recovery_at TIMESTAMP;`
  - `CREATE INDEX idx_orders_status_created_at ON orders (status, created_at);`
- `OrderJpaEntity` 에 두 컬럼 추가, `OrderJpaMapper.toDomain` / `OrderJpaEntity.fromDomain/updateFrom` 동기화.

### 3. Repository — stuck order 쿼리

- `OrderRepository.findStuckPaymentPending(Instant placedBefore, int batchSize): List<Order>` 신규 메서드.
- `OrderJpaRepository` Spring Data 메서드: `findByStatusAndPaymentIdIsNullAndCreatedAtLessThanOrderByCreatedAtAsc(OrderStatus, Instant, Pageable)`.
- DB-clock 기준 (`now()` 를 caller 가 계산하지 않고 `Clock` 으로 주입한 `Instant.now() - graceSeconds`) — wms reference 는 DB `now()` 를 쓰지만 ecommerce 는 단일 sweeper 인스턴스 가정 + Clock 주입이 더 단순. application-level cutoff 계산.

### 4. Application — sweeper + recovery handler 두 빈 분리

- `OrderStuckDetector` (application/saga 패키지, `@Component`):
  - `@Scheduled(fixedDelayString = "${order.saga.stuck-detector.fixed-delay-ms:60000}", initialDelayString = "${order.saga.stuck-detector.initial-delay-ms:30000}")`
  - `@Profile("!standalone & !test")` — wms 패턴 정합. test 프로파일에서는 IT 가 직접 호출.
  - `@ConditionalOnProperty("order.saga.stuck-detector.enabled", havingValue="true", matchIfMissing=true)`
  - `@Value` 외부화: `order.saga.stuck-detector.threshold-seconds` (default `1800` = 30 min), `order.saga.stuck-detector.batch-size` (default `100`), `order.saga.stuck-detector.max-attempts` (default `5`).
  - `sweep()`: `stuck-detector.run.count` metric +1 → `OrderRepository.findStuckPaymentPending(now - grace, batch)` → 각 row 에 대해 `OrderStuckRecoveryHandler.recover(orderId, maxAttempts)` 호출 (예외 격리). 추가 `sweepOnce()` 테스트용.
- `OrderStuckRecoveryHandler` (application/saga 패키지, `@Component`):
  - `@Transactional(propagation = Propagation.REQUIRES_NEW)` — 별도 빈으로 분리하여 Spring AOP 프록시를 통한 RH 트랜잭션 보장 (wms `SagaRecoveryHandler` 와 동일 — `feedback_refactor_code_baseline_it.md` 회귀 회피).
  - `recover(orderId, maxAttempts)`:
    1. 주문 fresh load. 사라졌거나 (`Optional.empty`) 이미 `payment_id != null` 또는 status 가 `PENDING` 이 아닌 경우 skip + DEBUG log (race condition).
    2. `nextAttempt = order.stuckRecoveryAttemptCount() + 1` 계산.
    3. `nextAttempt >= maxAttempts` 인 경우 → `markExhausted` (terminal 전이 + 알림 outbox 발행 + metric).
    4. else → `order.recordStuckRecoveryAttempt(now)` + save + `stuck-detector.recovery.fired{from_state=PENDING}` metric +1.
  - `markExhausted`: `order.markStuckRecoveryFailed(now)` + save → `OrderEventPublisher.publishOrderSagaRecoveryExhausted(...)` (outbox 동일 트랜잭션) → `stuck-detector.exhausted.count{from_state=PENDING}` metric +1.

### 5. Outbox event + topic 매핑

- 신규 application event record `OrderSagaRecoveryExhaustedEvent` (application/event 패키지). 봉투 + payload:
  - 봉투: 표준 `event_id` / `event_type=OrderSagaRecoveryExhausted` / `occurred_at` / `source=order-service`.
  - payload: `orderId`, `userId`, `lastState=PENDING`, `attemptCount`, `placedAt`, `lastTransitionAt`, `failureReason="order_stuck_payment_pending_attempts_exhausted"`.
- `OrderEventPublisher` 인터페이스에 `publishOrderSagaRecoveryExhausted(OrderSagaRecoveryExhaustedEvent event)` 메서드 추가.
  - `SpringOrderEventPublisher` 구현 (outbox aggregateType="Order", eventType="OrderSagaRecoveryExhausted").
  - `StandaloneOrderEventPublisher` no-op 구현 (standalone 프로파일).
- `OutboxPollingScheduler.resolveTopic` 에 `case "OrderSagaRecoveryExhausted" -> "order.alert.saga.recovery.exhausted"` 추가.

### 6. Metrics

- `OrderMetricsPort` 에 신규 메서드:
  - `recordStuckDetectorRun()` → counter `order_stuck_detector_run_total`
  - `recordStuckDetectorRecoveryFired(String fromState)` → counter `order_stuck_detector_recovery_fired_total{from_state=…}`
  - `recordStuckDetectorExhausted(String fromState)` → counter `order_stuck_detector_exhausted_total{from_state=…}`
- `MicrometerOrderMetrics` 구현. ADR § D3 metric naming 은 dotted (`<service>.saga.sweeper.…`) 이지만 ecommerce 도메인은 기존 underscore convention (`order_placed_total`, `order_status_transition_total`) 을 일관 유지하기 위해 underscore 로 등록 + Prometheus query 호환 (대시보드 측에서 정규화). ADR D3 의 의도 (각 saga 가 metric 이름을 reinvent 하지 않을 것) 는 `from_state` tag 와 `recovery.fired` / `exhausted.count` 시멘틱 보존으로 충족.

### 7. ADR + spec 갱신

- `docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md`:
  - § D6 ecommerce order 행: Decision = "**A — Compliant (post-TASK-BE-138)**", Follow-up = ✅ MERGED.
  - § 5 Outstanding follow-ups: TASK-BE-138 row 를 ✅ MERGED 로 갱신.
  - History 라인 추가: "TASK-BE-138 merged — order-service choreographed-saga stuck-detector landed; ecommerce order joined the Category A reference column."
- `projects/ecommerce-microservices-platform/specs/services/order-service/architecture.md` § "Saga / Long-running Flow" 행 업데이트:
  - Current state 에 stuck-detector + cap 5 + 알림 event 정합 기술.
  - Status: "**Compliant** (post-TASK-BE-138)" — Gap → Compliant.
- `projects/ecommerce-microservices-platform/specs/contracts/events/order-events.md`:
  - "OrderSagaRecoveryExhausted" 섹션 추가. payload + topic + consumer (notification-service / 운영자 alert) 명시.

### 8. application.yml 외부화

- `application.yml` 에 `order.saga.stuck-detector` 블록 추가:
  ```yaml
  order:
    saga:
      stuck-detector:
        enabled: ${ORDER_STUCK_DETECTOR_ENABLED:true}
        fixed-delay-ms: ${ORDER_STUCK_DETECTOR_FIXED_DELAY_MS:60000}
        initial-delay-ms: ${ORDER_STUCK_DETECTOR_INITIAL_DELAY_MS:30000}
        threshold-seconds: ${ORDER_STUCK_DETECTOR_THRESHOLD_SECONDS:1800}
        batch-size: ${ORDER_STUCK_DETECTOR_BATCH_SIZE:100}
        max-attempts: ${ORDER_STUCK_DETECTOR_MAX_ATTEMPTS:5}
  ```

### 9. 테스트

- `OrderStuckDetectorTest` (unit): mock `OrderRepository` + `OrderStuckRecoveryHandler` + fixed `Clock` + `SimpleMeterRegistry`. cases:
  - 빈 결과 → recovery handler 호출 0회, run metric +1.
  - 3개 stuck row → handler 3회 호출, run metric +1.
  - handler 가 throw → 다른 row 처리 계속 + WARN 로그.
- `OrderStuckRecoveryHandlerTest` (unit): mock `OrderRepository` + `OrderEventPublisher` + fixed `Clock` + `SimpleMeterRegistry`. cases:
  - first attempt: count 0→1, status 그대로, recovery.fired metric +1, no event publish.
  - cap-1 attempt (count 4 → 5): markExhausted, status = `STUCK_RECOVERY_FAILED`, exhausted metric +1, `OrderSagaRecoveryExhaustedEvent` published with attemptCount=5.
  - race: `payment_id != null` 일 때 skip (no metric, no event).
  - race: order disappeared (Optional.empty) skip without throw.
- `OrderStuckRecoveryIT` (Testcontainers Postgres + EmbeddedKafka):
  - PENDING + payment_id=null + created_at = NOW - 1h row seed → `sweeper.sweep()` → DB row 의 attempt count = 1, status PENDING 유지, outbox 미발행.
  - 5번 sweep 호출 → 마지막 호출에 status=STUCK_RECOVERY_FAILED + outbox 에 `OrderSagaRecoveryExhausted` 1행.
  - PENDING 이지만 payment_id != null 인 row → sweep 후 무영향.
  - PENDING + payment_id=null 이지만 created_at < grace 미경과 → sweep 후 attempt 0 유지.

## Out of Scope

- 기존 `PENDING` semantic 변경: 본 task 는 `STUCK_RECOVERY_FAILED` 신규 terminal 만 도입. `OrderStatus.PENDING` 의 의미 / 다른 컨슈머의 PENDING 처리 로직은 변경 없음.
- 알림 event 의 downstream consumer 구현 — `notification-service` 가 ecommerce 에 없음 (wms 도메인). 본 task 는 outbox + Kafka topic 까지만 커버. 운영자 dashboard 측 alert 구현은 별도.
- saga `re-emit` 로직 (wms 패턴): choreographed flow 에서는 `order.order.placed` 재발행이 payment-service consumer dedupe 와 결합해 효과적 일 수 있으나, 기본 ADR-006 (at-least-once outbox) 가 이미 publish-side 손실을 막고 있으며, 추가 재발행은 portfolio v1 범위 초과. 본 task 는 **detection + escalation** 만 — re-emission 은 v2 candidate.
- payment-service 측 변경: 본 task 는 order-service single-service 변경.
- ADR-MONO-005 자체 정책 변경.
- 전체 ecommerce CI 부하 증가 — IT 1개 추가만 (`OrderStuckRecoveryIT`), 기존 IT 와 동일 Testcontainers Postgres + EmbeddedKafka 패턴.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus `rules/domains/ecommerce.md` (if present) and `rules/traits/{transactional,content-heavy,read-heavy,integration-heavy}.md` per declared classification.

- `docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md` § D2 (generic policy), § D3 (Category A sub-rules), § D6 (ecommerce order 행 — 본 task 가 Compliant 로 전이)
- `rules/traits/transactional.md` § Required Artifacts (Category A sweeper rule), §T2 (atomic command), §T5 (optimistic locking), §T8 (idempotent consumer)
- `rules/traits/integration-heavy.md` §I1 (timeout — 외부 호출 없음이지만 scheduler interval 명시 외부화)
- `platform/event-driven-policy.md` § Consumer Rules (saga escalation event topic naming)
- `platform/error-handling.md` (terminal status semantic — `STUCK_RECOVERY_FAILED` 운영자 playbook)
- `projects/ecommerce-microservices-platform/specs/services/order-service/architecture.md` § Saga / Long-running Flow (본 task 가 갱신)
- `projects/ecommerce-microservices-platform/docs/adr/ADR-002-saga-over-distributed-transaction.md` (saga over distributed TX — choreographed shape 정당화)
- `projects/ecommerce-microservices-platform/docs/adr/ADR-006-at-least-once-delivery-policy.md` (publish-side at-least-once — 본 task 는 consume-side stuck escalation 보완)

---

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/events/order-events.md` — `OrderSagaRecoveryExhausted` 신규 섹션 추가 대상. envelope/payload + 토픽 + consumer.

---

# Acceptance Criteria

- AC-01: `OrderStatus.STUCK_RECOVERY_FAILED` enum 값이 추가되고 terminal (cancellable=false) 이다.
- AC-02: `orders` 테이블에 `stuck_recovery_attempt_count INTEGER NOT NULL DEFAULT 0` + `stuck_recovery_at TIMESTAMP` 컬럼이 Flyway `V7` 으로 추가된다.
- AC-03: `idx_orders_status_created_at` 인덱스가 `(status, created_at)` 으로 생성된다.
- AC-04: `Order.recordStuckRecoveryAttempt(Instant)` + `Order.markStuckRecoveryFailed(Instant)` 도메인 메서드가 존재한다. `markStuckRecoveryFailed` 는 status 를 STUCK_RECOVERY_FAILED 로 전이.
- AC-05: `OrderStuckDetector` 가 `@Scheduled` + `@Profile("!standalone & !test")` 빈으로 등록되고, `${order.saga.stuck-detector.*}` 외부화 properties 를 사용한다.
- AC-06: `OrderStuckRecoveryHandler` 가 별도 `@Component` + `@Transactional(REQUIRES_NEW)` 빈으로 분리되어 있다 (`SagaSweeper` / `SagaRecoveryHandler` 와 동일 split — wms reference).
- AC-07: cap 도달 시 `OrderSagaRecoveryExhaustedEvent` 가 outbox 동일 트랜잭션으로 발행되고, `OutboxPollingScheduler` 가 `order.alert.saga.recovery.exhausted` 토픽으로 라우팅한다.
- AC-08: `application.yml` 의 `order.saga.stuck-detector.*` 블록이 외부화 default 와 함께 존재한다 (threshold 1800s / batch 100 / max-attempts 5).
- AC-09: 3종 metric (run / recovery.fired / exhausted) 이 `MicrometerOrderMetrics` 에 등록되고 `from_state` tag 를 가진다 (recovery.fired / exhausted).
- AC-10: `OrderStuckDetectorTest` 최소 3개 메서드 (empty, multi-row, handler-throws-isolation) 통과.
- AC-11: `OrderStuckRecoveryHandlerTest` 최소 4개 메서드 (first attempt / cap exhaustion / race-payment-completed / race-order-vanished) 통과.
- AC-12: `OrderStuckRecoveryIT` 최소 4개 메서드 (single sweep no exhaust / 5 sweep exhaustion + outbox / payment_id 존재 시 skip / grace 미경과 skip) 통과 + Testcontainers Postgres + EmbeddedKafka 패턴 정합.
- AC-13: ADR-MONO-005 § D6 / § 5 / History + `architecture.md` § Saga 행 / `order-events.md` 모두 갱신.
- AC-14: `./gradlew :projects:ecommerce-microservices-platform:apps:order-service:test` PASS (회귀 + 신규 unit) — 기존 baseline 회귀 0.
- AC-15: `./gradlew :projects:ecommerce-microservices-platform:apps:order-service:integrationTest` PASS — 기존 IT 회귀 0 + 신규 IT 통과.
- AC-16: Conventional Commit scope: `feat(ecommerce/order)+adr(mono-005)+task(be-138)`.

---

# Edge Cases

- **EC-01**: sweeper 가 stuck row 를 식별한 직후 payment-service 가 실제 PaymentCompleted 를 발행 → race. `OrderStuckRecoveryHandler.recover` 가 fresh load 로 `payment_id != null` 검증 후 skip. metric / event 발행 없음.
- **EC-02**: sweep tick 사이 grace 가 경과한 row 는 다음 tick 에 발견되어 attempt count +1 — 단조 증가 보장 (이전 tick 결과 read-after-write 일관성).
- **EC-03**: 동일 order 가 sweep tick 중 두 번 처리될 가능성 (단일 sweeper 인스턴스 + REQUIRES_NEW). 발생 시 OL `version` 충돌로 두 번째 transaction 이 `OptimisticLockingFailureException` → 다음 tick 재시도. 정상 동작.
- **EC-04**: `STUCK_RECOVERY_FAILED` 상태 row 는 `findStuckPaymentPending` query (`status='PENDING'`) 에서 자동 제외 — 추가 sweep 안 됨.
- **EC-05**: 기존 `OrderConfirmationService.confirmOrder(orderId)` 가 `STUCK_RECOVERY_FAILED` 상태에 대해 `Order.confirm()` 호출 시 `InvalidOrderException` ("can only be confirmed in PENDING") 발생 — 정상. 운영자 manual intervention 후 새 주문 또는 정정 필요.
- **EC-06**: `OrderCancelledEventConsumer` 가 `STUCK_RECOVERY_FAILED` 주문 취소 시도 시 `OrderCannotBeCancelledException` 발생 — `STUCK_RECOVERY_FAILED.isCancellable()=false` 이므로 정상. terminal 상태에서 추가 변환 차단.
- **EC-07**: Postgres 인덱스 `idx_orders_status_created_at` 의 cardinality — `status` 가 5~6개 값이고 PENDING 은 short-lived, 대부분 row 가 CONFIRMED/SHIPPED/DELIVERED 로 빠르게 이동. PENDING 행 sub-second 범위 → composite index 가 PENDING 행만 빠르게 탐색.

---

# Failure Scenarios

- **FS-01**: REQUIRES_NEW 분리 빈 누락 (handler 메서드를 sweeper 내부에 inline). Spring AOP 프록시 미적용으로 트랜잭션 격리 깨짐 — `feedback_refactor_code_baseline_it.md` 회귀 그대로. 해결: AC-06 으로 명시 + IT 검증 (5번째 sweep 만 트랜잭션 commit, 그 전 4번은 attempt count 만 commit).
- **FS-02**: standalone 프로파일에서 sweeper 가 활성화되어 DB / Kafka 미존재 환경에서 ClassCastException / DataAccessException 폭발. 해결: `@Profile("!standalone & !test")` + `@ConditionalOnProperty(matchIfMissing=true)` — standalone 에서는 자동 비활성.
- **FS-03**: `OrderEventPublisher.publishOrderSagaRecoveryExhausted` 의 `OutboxWriter.save` 가 트랜잭션 외부에서 호출되어 outbox row 미저장. 해결: handler 가 `@Transactional(REQUIRES_NEW)` + outbox 호출이 같은 메서드 안 → 동일 트랜잭션 보장.
- **FS-04**: cap=5 가 너무 빨라 일시적 payment-service 정전 (5분) 동안 stuck row 가 모두 exhausted. 해결: grace 30 min × 5 attempts × 1 min poll = ~30 min + 5 min 최소 → 35 분 이상 정전이어야 cap 도달. 운영자 plenty of room. 외부화 가능.
- **FS-05**: payment-service 정전이 grace 미만 길이로 끝났을 때 sweeper 가 시작도 전에 자연 회복 → no metric, no event. 정상 (gray zone 없음).
- **FS-06**: IT 가 EmbeddedKafka 대신 Testcontainers Kafka 를 요구 — 기존 IT 패턴 (EmbeddedKafka) 그대로 사용. outbox row → Kafka 발행은 별도 OutboxPollingScheduler 가 처리하지만 IT 는 outbox row 자체 검증으로 끝낼 수 있음 (Kafka 발행은 다른 IT 가 커버).

---

# Test Requirements

- unit: `OrderStuckDetectorTest`, `OrderStuckRecoveryHandlerTest`
- integration: `OrderStuckRecoveryIT` (Testcontainers Postgres + EmbeddedKafka, 기존 `OrderPaymentCompletedIntegrationTest` 패턴 정합)
- contract: `OrderEventContractTest` 가 새 `OrderSagaRecoveryExhausted` 섹션의 envelope/payload 를 검증하도록 확장 (기존 contract test helper 활용).

---

# Definition of Done

- [ ] AC-01..AC-16 모두 충족
- [ ] order-service:test + order-service:integrationTest 회귀 0
- [ ] ADR / architecture / event contract spec 갱신
- [ ] CI green (path-filter 매칭으로 전체 ecommerce 파이프라인 트리거)

---

# Recommendation

진행 권장 (분석=Opus 4.7 / 구현 권장=Opus — state machine + scheduler + REQUIRES_NEW AOP split + 신규 outbox event + Flyway migration + IT — wms `TASK-BE-050` 와 동등 복잡도. ~300 LOC production + ~250 LOC 테스트.)
