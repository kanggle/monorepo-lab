# Event Contract: settlement-service (producer)

## Overview
Domain events **published** by settlement-service. Consumers must not depend on
fields not defined in this contract.

settlement-service was a terminal consumer in v1 (it published nothing — see
`settlement-subscriptions.md` for the consumed streams). The **period-close +
simulated payout increment** (ADR-MONO-030 Step 4 facet b continuation; TASK-BE-414
spec / TASK-BE-415 impl) introduces the transactional **outbox** and the service's
**first published event**, `settlement.period.closed.v1`. It is appended in the same
`@Transactional` boundary as the period close + `seller_payout` row creation.

> `settlement.commission.accrued.v1` is **forward-declared / deferred** — it is NOT
> defined or emitted in this increment. Only `settlement.period.closed.v1` is published.

---

## Event Envelope (ecommerce convention — snake_case)
Matches the order/payment-service ecommerce envelope (`event_id` / `event_type` /
`occurred_at` / `source` / `tenant_id` / `payload`).

```json
{
  "event_id": "string (UUID)",
  "event_type": "string",
  "occurred_at": "string (ISO 8601)",
  "source": "settlement-service",
  "tenant_id": "string (owning tenant; default 'ecommerce')",
  "payload": {}
}
```

> **`tenant_id` (ADR-MONO-030 Step 2, M5).** The outer-axis tenant that owns the
> closed period. Carried on the **envelope** (not the payload), like order/payment
> events, so consumers can route/scope per tenant across the async boundary without
> parsing the payload. (For convenience the closing period's `tenant_id` is also
> echoed in the payload — see below.)

---

## SettlementPeriodClosed

Published when an operator closes a settlement period (the `settlement_period`
aggregate transitions OPEN→CLOSED and its `seller_payout` rows are created). Emitted
**once per successful close**, co-committed with the close + payout-row inserts via
the transactional outbox.

**Event type:** `settlement.period.closed.v1`

**Topic:** `settlement.period.closed`

**Consumers:** **none yet.** The event is published so future subscribers (e.g. an
operator/notification surface, or a real-disbursement orchestrator) can consume it
without a producer change. No service currently subscribes.

**Payload**
```json
{
  "period_id": "string (UUID)",
  "tenant_id": "string (owning tenant; mirrors the envelope)",
  "period_from": "string (ISO 8601, inclusive)",
  "period_to": "string (ISO 8601, exclusive)",
  "closed_at": "string (ISO 8601)",
  "seller_count": 2,
  "payouts": [
    {
      "seller_id": "string",
      "payable_net_minor": 27000,
      "commission_minor": 3000,
      "accrual_count": 1
    }
  ]
}
```

- `[period_from, period_to)` is the **half-open** window the period covers
  (`period_from` inclusive, `period_to` exclusive).
- All money fields are **minor units** (`long`, KRW — matching the accrual ledger).
- `payouts[]` contains one entry per seller with a **positive** `payable_net_minor`
  (net-zero sellers, `payable_net_minor ≤ 0`, are skipped — they get no
  `seller_payout` row, decision 7). `seller_count == payouts.length`.
- `payable_net_minor = Σ seller_net_minor` (ACCRUAL − REVERSAL) over the seller's
  in-window accruals; `commission_minor = Σ commission_minor`; `accrual_count` is
  the number of accrual rows folded.
- The payload reflects the **PENDING** payout rows at close time — payout
  **execution** (PENDING→PAID|FAILED via the simulated adapter) is a separate step
  and is **not** reflected by this event (no payout-status / `payout_reference`
  field here).

---

## Producer Rules

- Published only via the transactional outbox (co-committed with the period close).
  Never published from a non-transactional path.
- Emitted exactly once per close (the OPEN→CLOSED transition happens once; a re-close
  is rejected, so no duplicate close event for the same period).
- The envelope `event_id` is the outbox row's idempotency key; downstream consumers
  must dedupe on it.
- Additive payload evolution only — never remove or repurpose a published field.
