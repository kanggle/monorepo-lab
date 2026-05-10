# ADR-006: At-Least-Once Delivery Policy (per-service decisions)

- **Status**: PROPOSED (2026-05-11) — accepted on PR merge
- **Date**: 2026-05-11
- **Authors**: backend (TASK-BE-135 audit)
- **Supersedes**: —
- **Superseded by**: —

## Context

ecommerce-microservices-platform has **inconsistent at-least-once delivery
semantics** across services. The TASK-BE-133 audit (PR #337,
dependencies.md backfill) surfaced the gap; per-service evidence:

| Service | Publisher class | Mechanism | Delivery semantic |
|---|---|---|---|
| order-service | `OrderEventOutboxRelay` (libs/java-messaging) | transactional outbox + polling relay | **at-least-once** (ack-on-publish, dedupe key in row) |
| promotion-service | `PromotionEventOutboxRelay` | transactional outbox | at-least-once |
| review-service | `ReviewEventOutboxRelay` | transactional outbox | at-least-once |
| shipping-service | `ShippingEventOutboxRelay` | transactional outbox | at-least-once |
| **user-service** | `KafkaUserProfileEventPublisher` (BE-134 fixed) | `KafkaTemplate.send` inside `@TransactionalEventListener(AFTER_COMMIT)` | best-effort (commit succeeds → broker send may fail → no row to retry) |
| **payment-service** | `KafkaPaymentEventPublisher` | `KafkaTemplate.send` inside use-case service; failure → metric increment + swallow | best-effort (DB commit independent of broker ack — silent loss on `KafkaException`) |
| **notification-service** | `EmailNotificationSender` (JavaMailSender) | direct SMTP send via Spring Mail | best-effort (broker SMTP send not durable; no replay) |

This ADR records a **per-service decision** for each direct-publish
service: migrate to the transactional outbox pattern (Scenario A) or
accept best-effort with documented rationale (Scenario B).

The TASK-BE-135 task spec also calls out the saga consistency check —
when an at-least-once producer publishes to a best-effort consumer's
ingress (or the inverse), the overall saga drops to the weakest link.

## Decisions

### payment-service → **Scenario A (Migrate to transactional outbox)**

**Events:** `payment.payment.completed`, `payment.payment.refunded`.

**Why migrate:**

- Both events are **load-bearing for downstream sagas**:
  - `payment.payment.completed` → order-service transitions order
    `PAYMENT_PENDING → PAID`. Silent loss leaves the order frozen in
    `PAYMENT_PENDING` despite PG capture success — manual ops recovery
    required, customer cannot proceed to shipping.
  - `payment.payment.refunded` → order-service transitions to
    `REFUNDED`; promotion-service restores coupon. Silent loss leaves
    refund accounting orphaned.
- Producer-side failure mode is **silent** today: `KafkaException` is
  caught, metric incremented, but caller (use-case service) does not
  know, transaction commits, downstream saga never runs.
- Cost (single new outbox table + relay scheduler bean + JpaRepository) is
  ~2 days of work; saga reliability is **portfolio-grade** (the demo
  narrative explicitly claims at-least-once for payment).

**Migration path** (filed as TASK-BE-136 follow-up):

1. New Flyway migration: `outbox` + `processed_events` (mirror libs/java-messaging schema).
2. Replace `KafkaPaymentEventPublisher.publishXxx` with
   `OutboxWriter.save(...)` (same Tx as the payment state mutation).
3. New `PaymentEventOutboxRelay extends OutboxPollingScheduler` —
   `resolveTopic` switch for the 2 event types.
4. Existing `PaymentMetricRecorder.incrementEventPublishFailure(eventType)`
   wired to relay failure callback (consistent metric label).
5. Integration test (Testcontainers Kafka + Postgres) — payment commits
   + relay polls within N seconds + consumer receives.

**Acceptance after migration:** payment-service joins the at-least-once
column of the table above.

---

### user-service → **Scenario B (Accept best-effort + observability boost)**

**Events:** `user.user.withdrawn`, `user.user.profile-updated`
(canonical names per BE-134 / PR #338).

**Why accept:**

- Publisher already uses `@TransactionalEventListener(AFTER_COMMIT)` —
  the publish is sequenced **after** the DB transaction commits, so
  there is no two-phase-commit mismatch (commit-and-rollback-roundtrip
  is not possible). The narrow remaining failure window is
  `commit succeeds → process crash before broker ack` (≈ ms).
- `user.user.profile-updated` has **no production consumer** in v1
  (admin-dashboard / notification-service v2 candidates only — see
  user-events.md). Silent loss has zero downstream effect today.
- `user.user.withdrawn` does have downstream consumers (order +
  auth-service), but the **business cost of silent loss is recoverable
  manually**: ops can re-publish via an operator endpoint OR replay from
  the `user_profiles.status='WITHDRAWN'` row scan.
- Migration cost is similar to payment-service (~2 days), but the
  marginal risk reduction is small (AFTER_COMMIT + ms-window vs
  ms-window-of-relay-poll). Engineering capacity is better spent on
  payment-service's larger gap.

**Required mitigations** (must land before this ADR is ACCEPTED):

- Existing `userMetrics.incrementEventPublishFailure(eventType)` is the
  observability tap. Confirm a Grafana alert on
  `user_event_publish_failure_total{event_type="UserWithdrawn"} > 0`
  in any 5-minute window.
- Add a **withdrawn-but-not-cancelled** ops query in
  `knowledge/runbooks/` (or equivalent) so on-call has a recovery path.
- v2 trigger: if a future user event has no recoverable side-channel
  (e.g., a financial credit issuance), promote to Scenario A at that
  point.

---

### notification-service → **Scenario B (Accept best-effort + Spring Mail retry strategy)**

**Mechanism:** `EmailNotificationSender` uses `JavaMailSender.send`
(synchronous SMTP).

**Why accept:**

- **No downstream consumer exists**: notification is the *terminal* of
  the saga. Silent loss = "user did not receive an email", not "system
  state diverges". This is qualitatively different from the
  payment-service case.
- A transactional outbox here would gate SMTP delivery on
  notification-service's local DB — over-engineering for a
  best-effort SMTP delivery semantic that the SMTP protocol itself does
  not guarantee. (SMTP `250 OK` from the relay does not guarantee
  inbox delivery — spam folders, bounces, downstream filters.)
- Alternative reliability mechanisms are appropriate **at the
  notification trigger side** (the consumer that reacts to
  `order.placed` etc. and decides to enqueue an email), not at the
  SMTP send.

**Required mitigations** (must land before this ADR is ACCEPTED):

- `EmailNotificationSender.send` currently does **not** wrap the
  `mailSender.send(message)` call in try/catch — a `MailException`
  propagates and causes the consumer to NACK + retry per the consumer's
  retry policy. This is acceptable as long as the consumer's retry +
  DLQ wiring is verified (currently inherited from
  spring-kafka defaults). Confirm DLQ exists for the notification
  consumers.
- Add an `email_send_failure_total{recipient_domain}` Micrometer counter
  (currently the failure path is logged-only).
- Document the DLQ replay procedure (`notification.send.failed.DLT` →
  manual replay) in `knowledge/runbooks/`.

---

## Saga Consistency Check

Two saga edges to verify after this ADR lands:

1. **order → payment → order** (place → capture → confirm):
   - `order.order.placed` (outbox, at-least-once) → payment-service
     consumes
   - payment processes capture → `payment.payment.completed`
     (**currently best-effort**; Scenario A migration closes the gap)
   - order-service consumes → confirm

   **Today**: weakest link is the payment.completed publish — single
   silent loss freezes the order. Post-migration: full at-least-once.

2. **order → notification → email**:
   - `order.order.placed` (outbox) → notification-service consumes
   - notification builds email → `EmailNotificationSender.send` (SMTP
     best-effort)

   **Today and post-ADR**: weakest link is SMTP delivery itself; this
   is acceptable per the notification-service rationale above.

## Consequences

- **Positive**: payment-service joins the at-least-once peer group;
  cross-service saga reliability claim becomes consistent for the
  load-bearing path. user/notification documented divergence with
  rationale + observability fallback (no silent technical debt).
- **Negative**: payment-service migration is engineering work
  (~2 days). user-service / notification-service still emit
  best-effort events — explicit in their `dependencies.md § Notes` so
  consumers cannot mistake them for at-least-once.
- **Neutral**: ADR-002 (saga over distributed transaction) constrains
  the design space; this ADR is consistent — payment outbox is the
  saga-step compensation hook.

## Follow-up tasks

- **TASK-BE-136** (filed in this PR, ready/) — payment-service outbox
  migration (Scenario A impl).
- **TASK-BE-137** (DEFERRED) — user-service AFTER_COMMIT → outbox
  promotion. Filed only if v2 introduces a non-recoverable user event.
- **TASK-BE-138** (DEFERRED) — notification DLQ + email retry runbook.
  Documented in this ADR but the runbook artifact itself is a separate
  small task.

## References

- TASK-BE-133 (PR #337) — surfaced the inconsistency
- TASK-BE-134 (PR #338) — upstream UserWithdrawn topic-name fix
- ADR-002 — saga over distributed transaction
- ADR-MONO-004 (scm-platform) — libs/java-messaging outbox scaffolding
- `rules/traits/transactional.md` § T2 / T3 — outbox invariants
- `libs/java-messaging` — outbox infra (`OutboxWriter`,
  `OutboxPollingScheduler`, `BaseEventPublisher`)
- `platform/event-driven-policy.md` — at-least-once expectations
