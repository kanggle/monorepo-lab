# TASK-BE-535 — Close the two money-path endpoints where a duplicate request moves real money

**Status:** review

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (refund idempotency has a partial/full interaction — this is not a "add a guard" edit)

> From the ADR-002 D3 idempotency census (2026-07-20), which enumerated **20** money/inventory/coupon-mutating
> endpoints across all 12 ecommerce services. Distribution: 0 ENFORCED, 1 OPTIONAL, 13 NATURAL-KEY, **6 NONE**.
> This task takes the 2 NONE endpoints that move money. The 4 stock/coupon ones are
> [`TASK-BE-536`](TASK-BE-536-inventory-coupon-path-duplicate-request-guards.md).

---

## Goal

### ① `POST /api/payments/{paymentId}/refund` — a duplicated *partial* refund pays out twice

`Payment.refund(long amount)` (`payment-service/.../domain/model/Payment.java:144-158`) validates only
`0 < amount ≤ remainingRefundable` and then **accumulates**:

```java
this.refundedAmount += amount;
this.status = (this.refundedAmount == this.amount) ? REFUNDED : PARTIALLY_REFUNDED;
```

There is no client key and no dedupe on the write path (`PaymentRefundService.java:166-190`). Two identical
`POST …/refund {amount: 1000}` against a 10 000 payment leave `refundedAmount = 2000` — a genuine double
payout, and one that is **indistinguishable from a legitimate second partial refund**, which is exactly why a
server-side natural key cannot solve it alone.

The zero-arg `Payment.refund()` (`Payment.java:130-135`) *is* idempotent — it returns early when already
`REFUNDED`. So the full-refund path is safe and only the partial path is exposed. That asymmetry is the real
hazard: a reviewer reading the class concludes "refund is idempotent" and is right about the method they
happened to read.

### ② `POST /api/admin/settlements/periods` — duplicate OPEN period is a double-payout vector

`OpenSettlementPeriodUseCase.java:28-33` mints `UUID.randomUUID()` per call with no key and no overlap check —
the class doc says so outright ("no overlap check"). Every replay opens another period over the same window.
No money moves at open time, but `close` folds accruals into payout rows, so two overlapping OPEN periods each
closed produce two payouts for one accrual window.

## Scope

**In scope:**

1. **Partial refund idempotency.** Give the refund path a way to distinguish "retry of the refund I already
   performed" from "a second, intentional partial refund". A client-supplied `Idempotency-Key` scoped to the
   payment is the mechanism ADR-002 D3 names; the concrete storage shape should follow what this project
   already does rather than importing a new pattern (see § Related Contracts on the deliberate non-adoption of
   `libs/java-web-servlet`).
2. **Settlement period creation.** Prevent a duplicate OPEN period over the same `(tenant, seller-scope, window)`
   — a unique constraint on the period's natural key is likely sufficient here and is cheaper than a key store.
   If overlap (not exact-duplicate) is the real risk, say so and guard the overlap.
3. Tests that drive the duplicate case for both, at the level where the guard actually lives.

**Out of scope:**

- The 13 NATURAL-KEY endpoints. They are already safe by unique constraint or state machine. Do **not** bolt
  client keys onto them — it is pure cost and it widens this diff past reviewability.
- The 4 stock/coupon NONE endpoints → `TASK-BE-536`.
- **Amending ADR-002 Decision-3 itself.** The census found 0/20 endpoints satisfy its literal wording (accept a
  client key AND deterministically reject), yet 13 are genuinely safe by other means. That suggests the ADR's
  wording, not the code, is what needs revisiting — but amending an ACCEPTED ADR's decision clause is the ADR
  owner's call, not this task's.
- Adopting `libs/java-web-servlet`'s `IdempotencyKeyFilter`. Non-adoption here is documented and deliberate
  (`tasks/done/TASK-BE-430-order-placement-idempotency.md:41,58`): order-service has no Redis and the library's
  own Javadoc cites domain-layer unique constraints as its backstop. Re-litigating that is a separate decision.

## Acceptance Criteria

- **AC-0 (gate — re-measure; the code wins)** — Do not inherit this ticket's reading. At start of work, read
  `Payment.refund(long)` and `Payment.refund()` directly and confirm the accumulate-vs-early-return asymmetry
  still holds; confirm `OpenSettlementPeriodUseCase` still lacks an overlap/dup guard. If either has changed,
  **STOP and report**.
- **AC-1** — A replayed partial refund with the same client key does not increase `refundedAmount` a second
  time. Proven by a test that issues the same partial refund twice and asserts the cumulative amount, **not**
  by asserting that a store row exists.
- **AC-2** — A *genuine* second partial refund (different key, same payment, still within remaining) still
  succeeds. This is the regression that a naive "reject any second refund" guard would introduce, and it must
  be covered by its own test.
- **AC-3** — The full-refund path's existing idempotency is preserved; a duplicate full refund remains a no-op.
- **AC-4** — A duplicate `POST /api/admin/settlements/periods` for the same window does not produce a second
  OPEN period. State in the PR body whether you guarded exact-duplicate or true overlap, and why.
- **AC-5** — Concurrency, not just sequential replay: state how the guard behaves under two simultaneous
  in-flight duplicates. A read-then-write check with no unique constraint or lock behind it is not a guard —
  both requests pass the check before either commits. If the chosen mechanism relies on a DB constraint, say
  which one; if it relies on a lock, say what happens when the lock store is unavailable (fail-open vs
  fail-closed is a money decision here, so make it explicitly).
- **AC-6** — `payment-service` and `settlement-service` build + tests GREEN.

## Related Specs

- `projects/ecommerce-microservices-platform/docs/adr/ADR-002-saga-over-distributed-transaction.md` § Decision-3
- `projects/ecommerce-microservices-platform/specs/services/payment-service/architecture.md`
- `projects/ecommerce-microservices-platform/specs/services/settlement-service/architecture.md`

## Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/` — if a request header or error code is added
  (e.g. a 409 on key reuse with a different body), the contract must be updated **before** implementation per
  `CLAUDE.md` § Core Principles.
- Precedent to follow inside this project: `order-service` `V10__add_order_idempotency_key.sql` (unique-indexed
  domain column + `DataIntegrityViolationException` race catch, `OrderPlacementService.java:34-68`) and
  `shipping-service` `ProcessCarrierWebhookService.java:48-51` (`registerIfFirst` on a PK'd table in the same
  transaction). The second is the closest existing match to what ADR-002 D3 describes — it is simply applied
  to a webhook rather than to a money endpoint. Reuse the shape rather than inventing a third.

## Edge Cases

- **Same key, different body** — a client reusing a key for a *different* refund amount must not silently
  replay the first response. Decide and document: 409, or treat as distinct. The library precedent in this repo
  returns 409 on a body-hash mismatch.
- **Key absent** — decide whether a missing key is refused (ADR-002 D3's literal reading) or falls back. Note
  that `order-service` chose fallback (`required = false`), which the census classified OPTIONAL; repeating
  that choice here reproduces the same hole on a funds-out endpoint. Recommend refusing on this path and state
  the reasoning either way.
- **Refund racing the payment's own status transition** — a refund arriving while the payment moves
  `COMPLETED → PARTIALLY_REFUNDED` must not double-count. Covered by AC-5.
- **Retention** — an idempotency record that expires before a client's retry window closes reopens the hole.
  State the retention chosen relative to the caller's retry policy.

## Failure Scenarios

- **F1 — the guard rejects legitimate second partial refunds.** The most likely regression, and it breaks a
  real business flow. Guarded by AC-2.
- **F2 — guard passes sequentially, fails concurrently.** A `findBy… → if absent → save` with no constraint
  behind it is defeated by two simultaneous requests. Guarded by AC-5.
- **F3 — fail-open under store outage on a funds-out path.** The shared wms filter is deliberately fail-open
  for availability; a refund endpoint is a different risk calculus. AC-5 forces the choice to be explicit
  rather than inherited.
- **F4 — scope creep into the 13 safe endpoints.** Doing "consistency work" here inflates the diff and risks
  regressions in code that is currently correct.
