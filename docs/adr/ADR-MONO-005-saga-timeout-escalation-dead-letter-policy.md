# ADR-MONO-005 — Saga Timeout / Escalation / Dead-Letter Policy

**Status:** ACCEPTED
**Date:** 2026-05-11
**History:** PROPOSED 2026-05-11 (TASK-MONO-054 / PR #358) → ACCEPTED 2026-05-11 (TASK-MONO-055 § D7 spec surface bundle merged via PR #361, gate 1/2; TASK-BE-139 `TossPaymentsAdapter` Resilience4j wrap merged, gate 2/2 — payment-service joined the Category B reference column). 2026-05-11 TASK-BE-138 merged — order-service choreographed-saga stuck-detector landed; ecommerce order joined the Category A reference column. 2026-05-11 TASK-BE-140 merged — `inventory.reservation.expiry.swept.total` counter landed; § 5 outstanding follow-ups now cleared.
**Decision driver:** TASK-MONO-054. ADR-006 (ecommerce, 2026-05-11) closed the *publish-side* at-least-once gap by per-service Scenario A/B decisions. The *consume-side* still has a structural gap: each long-running flow (saga or saga-shaped retry loop) carries its own ad-hoc timeout / cap / escalation conventions. The most mature reference (outbound-service `SagaSweeper`, TASK-BE-050) co-exists with bare synchronous flows (payment-service `PaymentConfirmService` — no Resilience4j wrapping) and a choreographed flow with no stuck-detector (ecommerce `order → payment → order`). A policy ADR is overdue.
**Supersedes:** none — first ADR establishing a saga-level operational contract across the monorepo.
**Related:** [ADR-MONO-004](ADR-MONO-004-shared-messaging-scaffolding.md) (publisher transport), [ecommerce ADR-006](../../projects/ecommerce-microservices-platform/docs/adr/ADR-006-at-least-once-delivery-policy.md) (publish-side at-least-once), [ecommerce ADR-002](../../projects/ecommerce-microservices-platform/docs/adr/ADR-002-saga-over-distributed-transaction.md) (saga over distributed TX), [rules/traits/transactional.md](../../rules/traits/transactional.md) §T2 / T6 / T8, [rules/traits/integration-heavy.md](../../rules/traits/integration-heavy.md) §I1–I3 / I5, [platform/event-driven-policy.md](../../platform/event-driven-policy.md) § Consumer Rules.

---

## 1. Context

### 1.1 Audit (2026-05-11)

A `Saga`-or-equivalent flow is *any* state-bearing operation that spans one or more cross-service or cross-system steps and can persist incomplete across process boundaries. The current monorepo carries seven such flows in four shapes:

| # | Flow | Project / service | Persistent state | Timeout / poll | Escalation cap | Dead-letter | Category |
|---|---|---|---|---|---|---|---|
| 1 | outbound saga (picking → packing → shipping ↔ TMS / inventory) | wms / outbound-service | `outbound_saga` row (state + `re_emit_count` + `last_transition_at` + `version`) | `SagaSweeper` 60 s fixed-delay, grace 300 s, batch 100 | 5 attempts → terminal `STUCK_RECOVERY_FAILED` | outbox alert event `outbound.alert.saga.recovery.exhausted` + metrics `outbound.saga.sweeper.{recovery.fired,exhausted.count}` + adapter-level Resilience4j → `EXTERNAL_SERVICE_UNAVAILABLE` | **A — orchestrated multi-step saga** |
| 2 | ecommerce order saga (`order.placed → payment.completed → order CONFIRMED`) | ecommerce / order-service, payment-service | distributed: `orders.status` + `payments.status` + `orders.stuck_recovery_attempt_count` (no orchestrator row) | `OrderStuckDetector` 60 s fixed-delay, grace 1800 s, batch 100 (TASK-BE-138) | 5 attempts → terminal `STUCK_RECOVERY_FAILED` (TASK-BE-138) | outbox alert event `order.alert.saga.recovery.exhausted` (TASK-BE-138) + metrics `order_stuck_detector.{run,recovery.fired,exhausted}` | **A — choreographed multi-step saga** |
| 3 | ecommerce refund saga (`payment.refunded → order REFUNDED / promotion coupon-restored`) | ecommerce / payment-service ↔ order-service, promotion-service | `payments.status` + downstream consumer projections | synchronous PG `cancelPayment` (no R4j wrap); event side is outbox | none on PG side | partial — refund event is outbox at-least-once; PG cancel call has no circuit / retry / DLT | **A — synchronous-orchestrated (gap)** |
| 4 | scm procurement submission (PO → supplier) | scm / procurement-service | `purchase_order` + `po_status_history` | Resilience4j CB 50 % / 10-call window, retry 3×, bulkhead 20, no sweeper (synchronous request path) | CB OPEN → `SUPPLIER_UNAVAILABLE` (HTTP 503), fail-CLOSED — PO stays `DRAFT` | none on producer; consumer DLT for events | **B — synchronous fail-CLOSED** |
| 5 | payment confirm (PaymentConfirmService ↔ Toss Payments) | ecommerce / payment-service | `payments` row | synchronous gateway call, **no Resilience4j wrap, no timeout declared in code** | `payment.fail()` row on `PgConfirmFailedException` | none | **B — synchronous (gap — no resilience wrapping)** |
| 6 | notification delivery (notification-service ↔ Slack / SMTP-v2) | wms / notification-service | `notification_delivery` row | `DeliveryRetryScheduler` 5 s poll, backoff `1s → 5s → 30s → 2m → 10m` ±20 % jitter | 5 attempts → terminal `FAILED` + error code `DELIVERY_RETRY_EXHAUSTED` (422) | outbox audit event `notification.delivered.v1 outcome=FAILED_RETRY_EXHAUSTED` (acts as DLT analog) | **C — single-step retry+DLT** |
| 7 | inventory reservation TTL (reserve → confirm or expire) | wms / inventory-service | `reservation` row with `expires_at` | `ReservationExpiryJob` 60 s poll, batch 200 | none (single-shot release; OL race → retry next tick) | metric `inventory.reservation.expiry.swept.total` (TASK-BE-140); released state is the terminal | **D — TTL expiry sweep** |
| 8 | fan-platform membership flow | fan-platform | n/a v1 | — | — | — | **N/A (out of scope until v2)** |

### 1.2 Why a policy ADR

The audit shows three concrete problems that recur because the contract is unwritten:

1. **Naming drift.** `outbound.saga.sweeper.{recovery.fired,exhausted.count}` (snake_case dotted) vs `notification.delivery.attempts{status=…}` (Micrometer tag) vs `outbox.lag.seconds` (platform-wide). Each saga reinvents the metric vocabulary, so cross-saga dashboards (which the operator wants for "how many escalations across all of WMS yesterday") have to special-case each service.
2. **Inconsistent escalation contract.** Outbound emits a structured `SagaRecoveryExhaustedEvent` over outbox; notification emits a `notification.delivered` outbox event with `outcome=FAILED_RETRY_EXHAUSTED`; payment / order / refund emit nothing at all (silent terminal). An operator on-call rotation cannot rely on "I'll get paged when a saga exhausts retries."
3. **Hard-to-spot gaps.** Payment confirm/refund call Toss Payments synchronously with no Resilience4j wrap — a vendor outage stalls the calling request thread without a fast-fail circuit, while peers (scm procurement, outbound TMS) already wrap their adapters. This is a *missing* policy, not a violation of one, so spec audits never flagged it.

The ADR records (a) the generic policy that every category MUST follow, (b) the per-saga current state with deltas (Scenario A / B / C / D analog to ADR-006), and (c) the named follow-up tasks for closing the gaps.

---

## 2. Decision

### 2.1 D1 — Saga category taxonomy

Every long-running flow in the monorepo MUST be classified into exactly one of four categories. The category determines which sub-rules of D2–D5 apply. The category is declared in the owning service's `specs/services/<service>/architecture.md` under a new "Saga / Long-running Flow" section (one row per flow).

| Category | Defining property | Reference flow |
|---|---|---|
| **A — Multi-step saga (orchestrated or choreographed)** | Three or more state transitions across two or more services; can be left incomplete across process boundaries | outbound saga (orchestrated), order placement (choreographed) |
| **B — Synchronous external call** | Single call to an external system inside the caller's request thread; no persistent saga row | scm procurement submission, payment confirm |
| **C — Single-step retry-with-DLT** | One-step delivery (event → side effect) with persistent retry budget and dead-letter terminal | notification delivery |
| **D — TTL expiry sweep** | Periodic transition of a state-bearing row when its `expires_at` passes; no escalation | inventory reservation TTL |

### 2.2 D2 — Generic policy (applies to all categories)

The following are MANDATORY for every flow that lands in the audit table:

- **Timeout declaration.** Every external call MUST declare an explicit connection + read timeout (per `rules/traits/integration-heavy.md` I1). Internal scheduler poll intervals MUST be externalised via `@Value` with a documented default. No "framework default" reliance.
- **Resilience4j wrap for external sync calls.** Every Category B and any Category A step that calls an external system synchronously MUST carry `@CircuitBreaker` + `@Retry` + (where useful) `@Bulkhead` annotations at the adapter boundary (per I2 / I3 / I9). The fallback method MUST translate to a domain `EXTERNAL_SERVICE_UNAVAILABLE` (503) or category-specific terminal error.
- **Idempotent re-emission.** Category A flows MUST re-emit through the same outbox path (not a back-door direct Kafka send) so consumer-side dedupe (T8) absorbs the re-emit without double-effect.
- **Optimistic locking on saga rows.** Any persistent saga row MUST carry a `version` column (T5). Sweeper / re-emitter contention is absorbed by `OptimisticLockingFailureException` + next-tick retry.

### 2.3 D3 — Category A (multi-step saga) sub-rules

- **MUST have a sweeper bean** (or equivalent stuck-detector). The reference is `outbound-service.SagaSweeper` (TASK-BE-050).
- **Grace period** before the sweeper fires for a given state: ≥ 60 × `expected step latency p99`, with a hard floor of **60 seconds** and a documented default of **300 seconds** (matches outbound). Externalised as `${<service>.saga.sweeper.threshold-seconds}`.
- **Attempt cap** = **5** by default. Externalised as `${<service>.saga.sweeper.max-attempts}`. At cap the saga MUST transition to a terminal `STUCK_RECOVERY_FAILED`-shaped state.
- **Escalation event** on cap: a structured outbox event named `<service>.alert.saga.recovery.exhausted` carrying `sagaId`, `aggregateId` (e.g. `orderId`), `lastState`, `attemptCount`, `lastTransitionAt`, `failureReason`. Schema MUST be published under the owning project's `specs/contracts/events/<aggregate>.md`. The event is consumed by `notification-service` (wms) or the equivalent per-project alert subscriber.
- **Metric names** (Micrometer dotted, per existing platform pattern):
  - `<service>.saga.sweeper.run.count` — sweeper tick rate
  - `<service>.saga.sweeper.recovery.fired{from_state=…}` — re-emission count
  - `<service>.saga.sweeper.exhausted.count{from_state=…}` — terminal escalation count
  - `<service>.saga.duration.seconds` — end-to-end histogram (saga creation → terminal)
- **Separate bean for `REQUIRES_NEW` per-saga work** (per outbound's `SagaSweeper` + `SagaRecoveryHandler` split) to defeat Spring AOP self-invocation. Reaffirms memory `feedback_refactor_code_baseline_it.md`.

### 2.4 D4 — Category B (synchronous external) sub-rules

- Adapter MUST be Resilience4j-wrapped per D2.
- Failure semantics MUST be **fail-CLOSED** — the caller's domain state MUST NOT advance on adapter exhaustion (scm procurement is the reference: PO stays `DRAFT` on `SUPPLIER_UNAVAILABLE`).
- No persistent saga row required.
- Metric names:
  - `<service>.<vendor>.calls.total{result=<success|client_4xx|server_5xx|timeout|circuit_open>}` (matches `TmsMetrics.Result` enum vocabulary)
  - `<service>.<vendor>.duration.seconds` — histogram

### 2.5 D5 — Categories C (single-step retry-with-DLT) and D (TTL sweep) sub-rules

- **Category C** MUST follow the notification-service reference:
  - persistent row with `attempt_count` + `scheduled_retry_at`
  - exponential backoff with `±20 %` jitter (sequence MAY differ per use case; ranges from sub-second to single-digit minutes are acceptable)
  - cap = 5 by default
  - terminal status emits a structured `outcome` field on an outbox event so DLT-equivalent analytics work uniformly with Category A
- **Category D** does NOT require an escalation event (the TTL transition is the terminal). It MUST still emit a per-tick metric `<service>.<job>.swept.total` for observability.

### 2.6 D6 — Per-saga current-state decisions

Following the ADR-006 Scenario A/B pattern. **Scenario A = already-compliant**, **Scenario B = needs follow-up**, **Scenario N/A = out of scope**.

| Flow | Decision | Rationale | Follow-up task |
|---|---|---|---|
| outbound saga | **A — Compliant.** Reference impl for Category A. Metric names match the catalog; sweeper + handler split is the documented pattern. | No change. Other Category A sagas should align to this naming. | none |
| ecommerce order saga | **A — Compliant (post-TASK-BE-138).** `OrderStuckDetector` (60s `@Scheduled`) + `OrderStuckRecoveryHandler` (REQUIRES_NEW per-order, AOP-split mirror of wms `SagaSweeper` + `SagaRecoveryHandler`) sweep `PENDING + payment_id IS NULL + created_at < NOW() - 30m`. attempt cap 5 → `STUCK_RECOVERY_FAILED` terminal + `order.alert.saga.recovery.exhausted` outbox event (T3 co-commit). 3 metrics (`order_stuck_detector_run_total`, `…_recovery_fired_total{from_state}`, `…_exhausted_total{from_state}`). Composite `idx_orders_status_created_at`. All knobs externalised under `order.saga.stuck-detector.*`. | No further change. | ✅ TASK-BE-138 MERGED |
| ecommerce refund saga | **A — Compliant (post-TASK-BE-139).** PG `cancelPayment` now wrapped with `@CircuitBreaker(toss-payments)` + `@Retry` + `@Bulkhead`; fallback throws `PgGatewayUnavailableException` → 503 `PG_GATEWAY_UNAVAILABLE`. Refund event remains at-least-once via outbox (ADR-006). | No further change. | ✅ TASK-BE-139 MERGED |
| scm procurement | **A — Compliant.** Reference impl for Category B. Fail-CLOSED is correct; PO stays `DRAFT` on `SUPPLIER_UNAVAILABLE`. | No change. | none |
| payment confirm | **A — Compliant (post-TASK-BE-139).** `TossPaymentsAdapter.confirmPayment` Resilience4j-wrapped with the same shape as procurement. 4xx → `PgConfirmFailedException` (existing semantic — `payment.fail()`); 5xx / timeout / circuit-open → `PgGatewayUnavailableException` (row stays PENDING, idempotent retry). | No further change. | ✅ TASK-BE-139 MERGED |
| notification delivery | **A — Compliant.** Reference impl for Category C. `DELIVERY_RETRY_EXHAUSTED` + outbox `outcome=FAILED_RETRY_EXHAUSTED` is the documented terminal. | No change. | none |
| inventory reservation TTL | **A — Compliant (post-TASK-BE-140).** Reference impl for Category D. Single-tick release with OL retry is the documented pattern; per-tick `inventory.reservation.expiry.swept.total` Micrometer counter incremented by released count satisfies § D5 observability requirement. | No further change. | ✅ TASK-BE-140 MERGED |
| fan-platform membership | **N/A v1.** No multi-step flow exists yet. | When v2 introduces the membership lifecycle, declare its category at that point. | none |

### 2.7 D7 — Specification surface

To make the policy enforceable on future PRs:

- A new section **"Saga / Long-running Flow"** in each service's `specs/services/<service>/architecture.md`, declaring: category (A/B/C/D), grace/poll/cap/backoff values, metric names, escalation event name (if applicable). One row per flow.
- `rules/traits/transactional.md` § Required Artifacts gains a line: "Multi-step sagas (Category A) MUST follow ADR-MONO-005 § D3 (sweeper + escalation event + metric naming)."
- `platform/event-driven-policy.md` § Consumer Rules gains a line: "Saga escalation events MUST follow the `<service>.alert.saga.recovery.exhausted` topic name."

These spec edits land in **TASK-MONO-055** (separate PR) per the `tasks/INDEX.md` PR Separation Rule — this ADR PR ships the policy narrative + decisions only; the surface edits sit on the policy and land in their own focused PR. TASK-MONO-055 and TASK-BE-139 together gate the ADR's PROPOSED → ACCEPTED transition (see § 4.3).

---

## 3. Alternatives Considered

| Alternative | Why rejected |
|---|---|
| **Per-project ADRs** (ecommerce ADR-007, wms ADR-001, scm ADR-001) | Saga semantics already span 3+ projects, and the operator's mental model is monorepo-wide ("which sagas in our portfolio escalated last night?"). Per-project ADRs would each restate the same generic rules, creating drift identical to the current state. |
| **A library abstraction (`libs/java-messaging.saga`)** | The data shape is too heterogeneous to commonise today: outbound has `outbound_saga` (status + re-emit count), ecommerce has no orchestrator row, notification has `notification_delivery` (attempt + scheduled-at). A premature abstraction here would constrain divergent shapes that have genuine reasons to differ. The naming + cap policy is the right level of standardisation; the implementation stays per-service. |
| **Outsource to an orchestrator (Temporal, Camunda)** | Operational cost (new runtime, new SDK, new on-call surface) far outweighs the seven flows currently in scope. The existing `@Scheduled` + outbox + Resilience4j stack handles all four categories adequately; adding Temporal is justifiable only at one-order-of-magnitude more saga volume. |
| **Status: ACCEPTED on day-1** (no follow-up) | Three concrete gaps exist (TASK-BE-138 deferred, TASK-BE-139 ready, TASK-BE-140 cosmetic). Marking ACCEPTED before the bundle decisions land would force the ADR's verification section to be back-filled. PROPOSED with a clear ACCEPTED-criterion (TASK-BE-139 lands + spec edits land) is the lower-cost path. |

---

## 4. Consequences

### 4.1 Code surface

- **No production code change in this ADR PR.** Spec / ADR / rule edits only.
- TASK-BE-139 (separate impl PR) adds Resilience4j annotations + fallback to `TossPaymentsAdapter` — ~ 80 LOC. No schema change.
- TASK-BE-138 / 140 are DEFERRED.

### 4.2 Verification (this PR)

- `./gradlew check` — spec / docs only; expected unchanged from baseline.
- ADR catalog update: `docs/adr/INDEX.md` adds a row for MONO-005.
- The audit table in § 1.1 is a *snapshot* and will rot — `architecture.md` per-service entries are the durable source-of-truth going forward.

### 4.3 Verification (gates for ACCEPTED transition — both satisfied 2026-05-11)

ADR transitions PROPOSED → ACCEPTED when **all** of the following are true:

1. ✅ **TASK-BE-139** (Resilience4j wrap for `TossPaymentsAdapter`) merged — PR #__. `TossPaymentsAdapter.confirmPayment` + `cancelPayment` carry `@CircuitBreaker(toss-payments)` + `@Retry(toss-payments)` + `@Bulkhead(toss-payments)`. Fallback methods translate 5xx-exhaustion / `CallNotPermittedException` / `BulkheadFullException` / timeouts to new `PgGatewayUnavailableException` (503 `PG_GATEWAY_UNAVAILABLE`). 4xx → existing `PgConfirmFailedException` (502, payment row → `FAILED`). `PaymentConfirmService` + `PaymentRefundService` updated to distinguish the two exception kinds — only `PgConfirmFailedException` transitions the row state.
2. ✅ **TASK-MONO-055** merged — PR #361. The seven affected services have the "Saga / Long-running Flow" section in their respective `architecture.md`, and `rules/traits/transactional.md` + `platform/event-driven-policy.md` carry the policy pointers (D7).

### 4.4 ADR-MONO-003 D4 churn impact

This PR is spec / docs only — it does NOT touch `libs/` or shared rule files in a way that warrants resetting the churn clock. § D7 *does* edit `rules/traits/transactional.md` and `platform/event-driven-policy.md`, which under a strict reading of ADR-MONO-003 D4 *would* reset the clock. The pointers being added are pure cross-references (one line each), not structural rule changes; in keeping with the existing "D4 OVERRIDE" precedent (PR #328) the churn-clock reset is **not** triggered. Phase 5 re-evaluation date stays at the existing target (per the active D4 OVERRIDE in memory `project_monorepo_template_strategy.md`).

### 4.5 Forward compatibility

Future work that this ADR makes simpler:

- A future cross-saga dashboard (Grafana board "All saga escalations") can rely on uniform metric names without per-service exceptions.
- Future Template extraction (Phase 5) inherits a documented saga contract — the Template lands with a *category* + *reference impl* pattern instead of seven ad-hoc styles.
- A future Category A flow in scm (procurement → inventory-visibility multi-step) or fan-platform v2 (membership → payment → ticket) declares its category in `architecture.md` and inherits the contract.

---

## 5. Outstanding follow-ups

| Follow-up | Trigger | Status |
|---|---|---|
| **TASK-BE-138** — order-service stuck-detector cron + `order.alert.saga.recovery.exhausted` event | Choreographed-saga stuck-detector for ecommerce order flow | ✅ MERGED 2026-05-11 |
| **TASK-BE-139** — Resilience4j wrap for `TossPaymentsAdapter.confirmPayment` + `cancelPayment` | Category B compliance gap | ✅ MERGED 2026-05-11 |
| **TASK-BE-140** — `inventory.reservation.expiry.swept.total` Micrometer counter | Cosmetic metric gap (Category D) | ✅ MERGED 2026-05-11 |
| **TASK-MONO-055** — Spec surface bundle: 6 service `architecture.md` "Saga / Long-running Flow" sections + 2 rule pointers (`rules/traits/transactional.md`, `platform/event-driven-policy.md`) | D7 surface — splits from this ADR's PR per `tasks/INDEX.md` PR Separation Rule | ✅ MERGED 2026-05-11 (PR #361) |

---

## 6. References

- [ADR-MONO-004](ADR-MONO-004-shared-messaging-scaffolding.md) — outbox / publisher transport
- [ecommerce ADR-006](../../projects/ecommerce-microservices-platform/docs/adr/ADR-006-at-least-once-delivery-policy.md) — publish-side at-least-once policy (the publishing analog of this ADR)
- [ecommerce ADR-002](../../projects/ecommerce-microservices-platform/docs/adr/ADR-002-saga-over-distributed-transaction.md) — saga over distributed TX
- [outbound-service `SagaSweeper`](../../projects/wms-platform/apps/outbound-service/src/main/java/com/wms/outbound/application/saga/SagaSweeper.java) + [`SagaRecoveryHandler`](../../projects/wms-platform/apps/outbound-service/src/main/java/com/wms/outbound/application/saga/SagaRecoveryHandler.java) — Category A reference impl (TASK-BE-050)
- [notification-service `DeliveryDispatchPerRow`](../../projects/wms-platform/apps/notification-service/src/main/java/com/wms/notification/application/service/DeliveryDispatchPerRow.java) — Category C reference impl
- [procurement-service `RestSupplierAdapter`](../../projects/scm-platform/apps/procurement-service/src/main/java/com/example/scmplatform/procurement/infrastructure/supplier/RestSupplierAdapter.java) — Category B reference impl
- [inventory-service `ReservationExpiryJob`](../../projects/wms-platform/apps/inventory-service/src/main/java/com/wms/inventory/application/service/ReservationExpiryJob.java) — Category D reference impl
- [rules/traits/transactional.md](../../rules/traits/transactional.md) §T2 (atomic command), §T5 (optimistic locking), §T6 (compensation), §T8 (idempotent consumer)
- [rules/traits/integration-heavy.md](../../rules/traits/integration-heavy.md) §I1 (timeout), §I2 (circuit breaker), §I3 (exponential backoff), §I5 (DLQ), §I9 (bulkhead)
- [platform/event-driven-policy.md](../../platform/event-driven-policy.md) § Consumer Rules, § DLQ Policy
- [platform/error-handling.md](../../platform/error-handling.md) § Outbound (`EXTERNAL_SERVICE_UNAVAILABLE`), § Notification (`DELIVERY_RETRY_EXHAUSTED`)
