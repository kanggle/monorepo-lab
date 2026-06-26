# payment-service — Architecture

This document declares the internal architecture of `payment-service`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `payment-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `rest-api + event-consumer` (hybrid — see Service Type Composition below) |
| Architecture Style | **Hexagonal Architecture** (Ports & Adapters) |
| Domain | ecommerce |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | Payment authorize / confirm / refund (PG vendor integration) |
| Deployable unit | `apps/payment-service/` |
| Data store | PostgreSQL (owned) |
| Event publication | Kafka via outbox (payment.* lifecycle events) |
| Event consumption | `OrderPlaced` from `order.order.placed`, `OrderCancelled` from `order.order.cancelled` (saga participation, ADR-MONO-005 Category B) |

### Service Type Composition

`payment-service` is a hybrid service per
`platform/service-types/INDEX.md` § Hybrid Cases (REST service that also
consumes events for saga orchestration). Primary type is `rest-api`; the
secondary `event-consumer` capability subscribes to order lifecycle events to
drive payment saga state transitions. PG vendor integration via
TossPaymentsAdapter (Resilience4j CB + Retry + Bulkhead, ADR-MONO-005
Category B, TASK-BE-139). Hexagonal 으로 vendor adapter 격리. The primary
type determines the spec read order — applied rules:
[platform/service-types/rest-api.md](../../../../../platform/service-types/rest-api.md).
The secondary capability is documented under "Events" below with topic /
consumer-group details.

---

## Why This Architecture
This service depends heavily on external systems and integration boundaries.

Payment processing requires clear separation between business logic and external adapters.

Testability, adapter isolation, and boundary control are critical.

## Internal Structure Rule
This service uses a ports-and-adapters structure.

Recommended internal areas:
- inbound adapters
- application
- domain
- outbound ports
- outbound adapters

Business logic must remain independent from specific framework or vendor integrations.

## Allowed Dependencies
- inbound adapters -> application
- application -> domain
- application -> ports
- outbound adapters -> ports
- outbound adapters -> external SDKs or infrastructure

## Forbidden Dependencies
- domain must not depend on framework or vendor SDK code
- application must not depend directly on external payment vendor implementations
- adapters must not own business policy
- controllers or message consumers must not bypass application services

## Boundary Rules
- inbound adapters translate external input into application commands
- application layer coordinates use-cases through ports
- domain contains payment rules and decision logic
- outbound adapters implement external gateway, database, messaging, or notification integrations

## Integration Rules
- external payment provider integration must be isolated behind outbound ports
- HTTP and event contracts must follow published contracts
- retry, timeout, and failure behavior must be implemented through adapter/application coordination
- shared libraries may support technical concerns only

## Testing Expectations
Required emphasis:
- application service tests
- port contract tests
- outbound adapter tests
- integration tests for provider interaction boundaries
- failure and retry scenario tests

## Outbox
Domain events (`PaymentCompleted`, `PaymentRefunded`, `PaymentRefundStranded`,
`PaymentRefundUnresolved`) are published via the
**transactional outbox** pattern (`libs/java-messaging` —
[ADR-006](../../../docs/adr/ADR-006-at-least-once-delivery-policy.md), Scenario
A; impl TASK-BE-136). The outbound flow:

1. The use-case service (`PaymentConfirmService` / `PaymentRefundService`) runs
   inside a `@Transactional` boundary. The state mutation on `payments` and
   the envelope row on `outbox` commit atomically.
2. `PaymentEventOutboxWriter` (`adapter.out.event`) serializes the event
   record and calls `OutboxWriter.save(...)`. It does NOT touch Kafka.
3. `PaymentEventOutboxRelay extends OutboxPollingScheduler` polls the
   `outbox` table at `outbox.polling.interval-ms` (default 1s), resolves the
   target Kafka topic (`PaymentCompleted` → `payment.payment.completed`,
   `PaymentRefunded` → `payment.payment.refunded`,
   `PaymentRefundStranded` → `payment.alert.refund.stranded` — TASK-BE-437,
   `PaymentRefundUnresolved` → `payment.alert.refund.unresolved` — TASK-BE-438),
   and marks the row `PUBLISHED` only after broker ack.
4. On Kafka send failure, the relay invokes
   `PaymentMetricRecorder.incrementEventPublishFailure(eventType)` —
   preserving the original `event_publish_failure_total{service=payment-service,event_type=...}`
   metric label semantics so existing dashboards / alerts continue to work.

**Delivery semantic:** at-least-once. The relay retries until broker ack;
consumers must already be idempotent on `event_id` (existing dedupe layer).

**Latency envelope:** up to one polling interval (default 1s) between
transaction commit and broker dispatch. Consumers should not assume
sub-second consistency.

## Saga / Long-running Flow (ADR-MONO-005)

Per [ADR-MONO-005](../../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md).

| Flow | Category | Resilience4j config | Exception classification | Status |
|---|---|---|---|---|
| payment confirm (Toss Payments `confirmPayment` ↔ `payments` row) | **B** (synchronous external) | `@CircuitBreaker(toss-payments)` 50 % / 10-call TIME_BASED window · `@Retry(toss-payments)` 3 attempts exp+jitter (4xx + `PgConfirmFailedException` in `ignore-exceptions`) · `@Bulkhead(toss-payments)` semaphore, 10 concurrent · connect 5 s + read 10 s timeouts | **4xx → `PgConfirmFailedException`** (PG-side definitive rejection, payment row → `FAILED`); **5xx / timeout / circuit-open / bulkhead-full → `PgGatewayUnavailableException`** (transport failure, payment row unchanged, caller may idempotently retry) | **Compliant** (TASK-BE-139, PR #__) |
| payment refund (Toss Payments `cancelPayment` ↔ `payments` row + downstream `payment.payment.refunded` outbox) | **B** (synchronous external) | Same R4j config as confirm | Same exception classification — on `PgGatewayUnavailableException` the refund row stays `COMPLETED` and the caller's retry / DLT mechanism re-drives | **Compliant** (TASK-BE-139, PR #__) |
| stranded-refund reconciliation (`stranded_refund` row → PG `fetchStatus` + `cancelPayment` → `RESOLVED` \| `UNRESOLVED` + `payment.alert.refund.unresolved` outbox) | **A** (multi-step saga) | `StrandedRefundSweeper` (`@Scheduled` `fixed-delay-ms` default 60s) + `StrandedRefundReconciler` (REQUIRES_NEW per-record, AOP-split) · bounded retry `max-attempts` default 8 · exponential backoff 1s→2s→4s…30s cap (Clock-injected) | PG-state-first: `CANCELED` → `RESOLVED` (no re-cancel); `CAPTURED` → re-cancel; `PgGatewayUnavailableException`/`UNKNOWN`/fetch-fail → transient backoff; `PgConfirmFailedException` (4xx) or attempt-cap → terminal `UNRESOLVED` + escalation | **Compliant** (TASK-BE-438) |

Source: `TossPaymentsAdapter` (`@CircuitBreaker(name="toss-payments", fallbackMethod="confirmFallback"/"cancelFallback")`). Fallback translates `CallNotPermittedException` / `BulkheadFullException` / retry-exhausted `HttpServerErrorException` / `ResourceAccessException` (timeout) uniformly to `PgGatewayUnavailableException`. `PaymentConfirmService` / `PaymentRefundService` distinguish the two exception kinds — only `PgConfirmFailedException` transitions the row state. Caller error codes: 502 `PG_CONFIRM_FAILED` vs 503 `PG_GATEWAY_UNAVAILABLE`.

## Payment state machine (`PaymentStatus`)

```
PENDING ──confirm()──────────────▶ COMPLETED ──refund(part)──▶ PARTIALLY_REFUNDED ──refund(rest)──▶ REFUNDED
   │                                   └──────────refund(full)───────────────────────────────────▶ REFUNDED
   ├──fail() (PG 4xx rejection)──────▶ FAILED                  (terminal)
   └──voidForOrderCancelled()────────▶ VOIDED                  (terminal — TASK-BE-435)
```

| Status | Meaning | Terminal |
|---|---|---|
| `PENDING` | Created from `OrderPlaced`, not yet captured | no |
| `COMPLETED` | PG capture succeeded | no (refundable) |
| `PARTIALLY_REFUNDED` | Some captured amount refunded; remainder refundable | no |
| `REFUNDED` | Fully refunded | yes |
| `FAILED` | PG-side definitive rejection (4xx) at confirm | yes |
| `VOIDED` | **TASK-BE-435** — order was cancelled *before* capture; the never-captured PENDING payment is voided so any later `confirm()` is rejected. Distinct from `FAILED` (PG-rejected) for analytics. No PG money movement occurred, so no refund is owed and no observable event is emitted. | yes |

`VOIDED` is an **additive enum value** stored in the existing `payments.status VARCHAR(20)` column (`@Enumerated(EnumType.STRING)`) — no Flyway migration is required (no destructive change; the column already stores enum names as strings).

## OrderCancelled consumer — money-safe late-payment compensation (TASK-BE-435)

`OrderCancelledEventConsumer` (`order.order.cancelled`, group `payment-service`) delegates to
`PaymentRefundService.handleOrderCancelled(orderId)`, which **branches on the current `Payment`
row state** so both event orderings are money-safe regardless of `cancelReason`
(`OPERATOR` and `PAYMENT_TIMEOUT` both require money safety — the reason is informational here):

| Payment state at `OrderCancelled` | Action |
|---|---|
| `COMPLETED` / `PARTIALLY_REFUNDED` (captured) | Full auto-refund via `PaymentRefundService.refundPayment(orderId)` (PG `cancelPayment` + `PaymentRefunded` for the remaining refundable). |
| `PENDING` (not yet captured) | `voidPayment(orderId)` → `PENDING → VOIDED`. No PG call, no event — nothing was captured. A later `confirm()` is then rejected. |
| `VOIDED` / `REFUNDED` / `FAILED` (terminal) | No-op (idempotent). |

**Both orderings are safe:**
- **COMPLETED → CANCELLED**: existing refund path reverses the capture.
- **CANCELLED → COMPLETED**: the void rejects the late `confirm()` pre-capture.

**Concurrency belt-and-suspenders (`PaymentConfirmService.confirm()`).** For the genuinely
concurrent interleave where `confirm()` is mid-flight when `OrderCancelled` commits a `VOIDED`:
1. **Pre-capture guard** — `confirm()` rejects when the row is already `VOIDED` (no PG call).
2. **Post-capture guard** — after the (slow) PG capture, `confirm()` **re-reads** the row; if it
   is now `VOIDED` (the cancel committed *during* the PG call), it **immediately PG-cancels the
   just-captured amount** (`cancelPayment(paymentKey, "Order cancelled during confirm")`), does
   **not** advance to `COMPLETED`, and publishes no `PaymentCompleted`. Funds are never retained.

   Mechanism: a **fresh, persistence-context-bypassing** re-read after capture —
   `PaymentRepository.findByOrderIdFresh` (TASK-BE-443), which forces an `entityManager.refresh`.
   A plain `findByOrderId` would **not** observe the concurrent `VOIDED`: `confirm()`'s pre-capture
   read already loaded the row as a **managed** entity in this transaction's persistence context
   (L1), and a derived query re-hydrates the matched row through the same session, where Hibernate's
   managed-entity identity (session-level repeatable-read) returns the **stale `PENDING`** instance
   and discards the freshly-read `VOIDED` columns — masking the race **regardless of DB isolation
   level** (READ_COMMITTED governs what the SQL fetches, not whether Hibernate uses it for an
   already-managed entity; the row carries no optimistic-`@Version`). `findByOrderIdFresh`'s
   `refresh` re-SELECTs and overwrites the managed entity's stale fields with the committed `VOIDED`,
   so the post-capture guard actually fires. The refresh needs no schema change and is a **no-op on
   the success path** (in the no-race case the row is still `PENDING`, so `confirm()` proceeds to
   capture-confirm unchanged). If the void instead commits *after* this transaction, the void path
   is a no-op on the now-`COMPLETED` row and the consumer's COMPLETED→refund branch reverses it —
   also safe. (Before BE-443 this guard was unreachable for the real concurrent race — the L1 cache
   returned the stale `PENDING`, so a captured-but-cancelled order would have advanced to `COMPLETED`
   with funds retained: a money-safety defect that only surfaced against a real DB, not the
   mock-repo unit test. See the `PaymentRefundStrandedDurabilityIntegrationTest` interleave proof.)

**Post-capture auto-refund failure — stranded-refund escalation (TASK-BE-437).** The
post-capture `cancelPayment` call above can itself **fail at the PG**
(`PgGatewayUnavailableException` 5xx/circuit-open/timeout, or `PgConfirmFailedException`
4xx). Unlike the **consumer** refund path (protected by the Kafka `DefaultErrorHandler`
retry-3×-then-DLT), this **synchronous HTTP** path has no net — an uncaught failure would
let the exception propagate, roll back the `confirm()` `@Transactional`, and leave the
captured customer funds **silently stranded** (the row is already `VOIDED`, the
`OrderCancelled` consumer has already run its void branch and will not re-fire). `confirm()`
therefore **catches** both PG exceptions and:

1. **Durably records a `PaymentRefundStranded` escalation** to the outbox via
   `PaymentRefundStrandedRecorder.record(...)` — a **separate `@Component`** whose method runs
   `@Transactional(propagation = REQUIRES_NEW)`. Because it is a distinct bean (AOP proxy
   boundary, exactly like order-service's `OrderStuckRecoveryHandler`), the escalation row
   **commits in its own transaction** and **survives** the `confirm()` rollback — the alert is
   not lost. (A private method on `PaymentConfirmService` would self-invoke past the proxy and
   inherit the rolling-back outer TX, losing the alert.)
2. Increments the money-safety counter `payment_refund_stranded_total`.
3. `log.error(...)` with orderId / paymentId / paymentKey / amount / cause.
4. Still **rejects** the confirm (`PaymentAlreadyCompletedException`) — a cancelled order must
   never advance `VOIDED → COMPLETED`.

The escalation payload (`paymentId`, `orderId`, `paymentKey`, `amount`, `reason`, `occurredAt`;
see `contracts/events/payment-events.md` § `PaymentRefundStranded`) carries enough context for a
reconciliation/operator to **check PG state first** — a transient 5xx cancel *may* have actually
succeeded, so acting blindly risks a double-refund (F3). **F1 safety:** if the REQUIRES_NEW write
itself throws, the call site logs + increments the metric anyway — the captured-funds loss is
never invisible. **F2:** the escalation fires **only** on the catch path; a successful
`cancelPayment` emits no `PaymentRefundStranded` (no false money-safety page). A full
auto-reconciliation sweeper that retries the PG cancel is a deliberate out-of-scope follow-up
(Category-A saga, ADR-MONO-005); this delivers the non-silent, operator-recoverable net only.

**Idempotency (AC-7).** Duplicate `OrderCancelled` is a no-op (refund gates on already-`REFUNDED`
/ remaining-refundable; void gates on terminal state). A retried `confirm()` is a no-op
(`PENDING`-only capture + the VOIDED guards).

**Auto-refund PG failure (TASK-BE-139).** The COMPLETED→refund leg goes through
`TossPaymentsAdapter`; on `PgGatewayUnavailableException` the refund row stays unchanged and the
`@Transactional` boundary rolls back, so the consumer's at-least-once redelivery / DLT re-drives
the refund — the customer is never left silently un-refunded.

## Stranded-refund reconciliation sweeper (TASK-BE-438, ADR-MONO-005 § 2.3 D3 Category A)

TASK-BE-437 made a stranded refund **non-silent** (an escalation event + a metric) but left the
actual recovery to a human. TASK-BE-438 makes it **auto-reconciling**: a scheduled sweeper retries
the PG cancel for stranded refunds so transient strandings self-heal, while preserving a
bounded-retry **terminal** + re-escalation for the genuinely-stuck ones (the Category-A
bounded-retry-then-terminal contract, mirroring order-service's `OrderStuckDetector` →
`OrderStuckRecoveryHandler`).

**Durable record.** `PaymentRefundStrandedRecorder` (the BE-437 `REQUIRES_NEW` recorder) now also
persists a `stranded_refund` row (Flyway V7) in the **same `REQUIRES_NEW` transaction** as the
`PaymentRefundStranded` escalation event, so the queryable obligation and the alert commit
atomically across the `confirm()` rollback. Dedupe: a partial unique index
(`uq_stranded_refund_open_payment ON stranded_refund(payment_id) WHERE status='STRANDED'`) plus a
read-then-insert guard ensure at most **one open obligation per payment** — a retried `confirm()`
does not create a second open row (the escalation *event* may still re-emit; the alert consumer
dedupes on `paymentId`/`event_id`).

**Bean split (AOP boundary — F5).** `StrandedRefundSweeper` (`@Scheduled`, `@Profile("!standalone")`,
toggled by `payment.stranded-refund.enabled`) polls a bounded batch and dispatches each record to the
**separate** `StrandedRefundReconciler` bean whose `reconcile(id)` runs `@Transactional(REQUIRES_NEW)`.
The proxy boundary is what makes one poisoned record roll back **only that record**, not the batch.
Single-thread `@Scheduled(fixedDelay)` never overlaps its own previous run, so two ticks cannot
reconcile the same record concurrently (no pessimistic lock needed); the reconciler additionally
re-checks `isOpen()` under a fresh load before acting.

**PG-state-first reconciliation (double-refund guard — F1, the central hazard).** A transient
stranding (`PgGatewayUnavailableException` 5xx/circuit-open/timeout) means the original cancel's PG
outcome is **unknown** — it may have actually succeeded. The reconciler therefore reads the PG state
via the new read-only `PaymentGatewayPort.fetchStatus(paymentKey)` (Toss `GET /v1/payments/{paymentKey}`,
mapped: `CANCELED → CANCELED`, `DONE`/`PARTIAL_CANCELED → CAPTURED`, else `UNKNOWN`) **before** any
compensating action:

| PG state | Action |
|---|---|
| `CANCELED` (already reversed) | Mark `RESOLVED`, `payment_refund_stranded_resolved_total`++ — **never** call `cancelPayment` again (no double refund). |
| `CAPTURED` (still held) | `cancelPayment(...)`. Success → `RESOLVED` + resolved metric. `PgGatewayUnavailableException` → transient backoff. `PgConfirmFailedException` (4xx) → terminal `UNRESOLVED` + escalation. |
| `UNKNOWN` / `fetchStatus` itself fails | Transient (backoff, stay `STRANDED`). **Never** infer `RESOLVED` from an error. |

**State machine (`stranded_refund.status`).**
```
STRANDED ──PG already CANCELED, or retry cancel succeeds──────────────────▶ RESOLVED   (terminal)
STRANDED ──attempts >= max-attempts (F2), or definitive 4xx PG reject──────▶ UNRESOLVED (terminal)
```
- **Bounded retry + backoff (AC-4, F2).** A transient failure increments `attempts` and pushes
  `next_attempt_at` out by exponential backoff (`1s → 2s → 4s … 30s` cap — the
  `AbstractOutboxPublisher` shape, `Clock`-injected for deterministic tests). The poll predicate is
  `status='STRANDED' AND next_attempt_at <= now`, so a not-yet-due record is skipped (no retry storm).
- **Terminal escalation (AC-5, F3).** At `attempts >= max-attempts` (default `payment.stranded-refund.max-attempts: 8`)
  **or** a definitive `PgConfirmFailedException`, the record transitions to terminal `UNRESOLVED`,
  `payment_refund_stranded_unresolved_total`++, and a **`PaymentRefundUnresolved`** escalation is
  re-emitted — the status transition and the escalation event **co-commit** in the reconciler's
  `REQUIRES_NEW` TX. An `UNRESOLVED` record is excluded from the poll predicate and never auto-retried again.

**Metrics.** `payment_refund_stranded_total` (BE-437, at stranding time — unchanged);
`payment_refund_stranded_resolved_total` (auto-healed); `payment_refund_stranded_unresolved_total`
(terminal); gauge `payment_refund_stranded_open` (current `STRANDED` count, for an alerting SLO).

**Tenant scope.** The `stranded_refund` table is **operational / tenant-agnostic** (no `tenant_id`
column — like order-service's stuck-detector), swept globally on a background thread. The terminal
`PaymentRefundUnresolved` escalation carries the ambient tenant (`TenantContext.currentTenant()` →
default `'ecommerce'` on the sweeper thread).

**Standalone.** The sweeper / reconciler are `@Profile("!standalone")` (no scheduler / no PG); the
recorder's `stranded_refund` row write stays active in all profiles (it is part of the synchronous
`confirm()` path).

## Multi-Tenancy (ADR-MONO-030 Step 4 facet c — TASK-BE-400)

payment-service adopts the `multi-tenant` trait M1-M7 pattern matching the sibling
services (user-service V4, notification-service V5, promotion-service V6,
shipping-service V7).

| Mechanism | Applicability | Notes |
|---|---|---|
| **M1 row-level `tenant_id`** | **Applied** — Flyway V5 | `payments` table gains `tenant_id VARCHAR(64) NOT NULL`; backfilled to `'ecommerce'` |
| **M2 3-layer isolation** | **Applied** | (a) Gateway `TenantClaimValidator` (TASK-BE-357); (b) `TenantContextFilter` reads `X-Tenant-Id` header; (c) repository reads scoped by `TenantContext.currentTenant()` |
| **M3 404-over-403** | **Applied** | `findByOrderId` / `findById` scoped to tenant; cross-tenant row → 404 via `PaymentNotFoundException` |
| **M4 enumeration prevention** | **Applied** | Listings are tenant-scoped; id traversal cannot reveal other-tenant data |
| **M5 async event propagation** | **Applied** | `PaymentCompletedEvent` / `PaymentRefundedEvent` envelopes include `tenant_id` top-level field |
| **M6 cross-tenant leak regression IT** | **Applied** | `MultiTenantIsolationIntegrationTest` — `@Tag("integration")` `@SpringBootTest` (Testcontainers): (a) tenant-A context querying tenant-B's payment by orderId → 404/empty; (b) cross-tenant payment confirmation attempt → 404 (`PaymentNotFoundException`). Host-blocked locally; authoritative proof runs in CI. |
| **M7 per-tenant quota** | **Not applicable** | Out of scope for payment-service v1 |

### Context propagation

- **HTTP requests**: `TenantContextFilter` (highest-precedence `OncePerRequestFilter`) reads
  `X-Tenant-Id` from the gateway-injected header into `TenantContext` (ThreadLocal).
  Cleared in `finally` to prevent thread-pool leakage.
- **Event consumer threads** (`OrderPlacedEventConsumer`, `OrderCancelledEventConsumer`):
  no HTTP context. Both consumers read `tenant_id` directly from the inbound event
  envelope (`OrderPlacedEvent.tenantId()` / `OrderCancelledEvent.tenantId()`), call
  `TenantContext.set(event.tenantId())` before delegating to the application service
  (`OrderCancelledEventConsumer` → `PaymentRefundService.handleOrderCancelled`, the
  state-branching money-safe handler — see "OrderCancelled consumer" above),
  and unconditionally clear it in a `finally` block to prevent thread-pool leakage.
  A null/blank `tenant_id` in the event resolves to the default tenant (`'ecommerce'`)
  via `TenantContext.set(null)` semantics (net-zero, D8).
- **Standalone / no-IAM (D8)**: absent header → default tenant `'ecommerce'` → net-zero.

### Event envelope

`tenant_id` is a top-level field alongside `event_id` / `event_type` / `occurred_at` /
`source`. Consumers that do not yet read `tenant_id` are unaffected (additive). Pre-TASK-BE-400
outbox rows may lack the field; consumers should treat missing `tenant_id` as `'ecommerce'`.

## Change Rule
Any architectural change to this service must be documented here first before implementation.