# TASK-BE-536 — Close the four stock/coupon endpoints that have no duplicate-request protection

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (four narrow guards following an existing in-repo pattern)

> From the ADR-002 D3 idempotency census (2026-07-20): **20** in-scope endpoints, of which **6** have no
> protection at all. This task takes the 4 stock/coupon ones. The 2 money-path ones (payment partial refund,
> settlement period creation) are [`TASK-BE-535`](TASK-BE-535-money-path-duplicate-request-guards.md) and are
> higher severity — land that first if the two are worked serially.

---

## Goal

Four endpoints change a durable balance with no client key, no dedupe, and no natural-key or state guard:

| Endpoint | Evidence | What a replay does |
|---|---|---|
| `PATCH /api/admin/products/{productId}/stock` | `AdminProductController.java:209`; `AdjustStockService.java:44-55,78` — only guard is `quantity != 0` | Adjusts stock twice and emits a second `StockChangedPayload` |
| `POST /api/admin/products/{productId}/variants` | `AdminProductController.java:167`; `Product.java:115-122` — `addVariant` null-checks only, **no uniqueness on `optionName`** | Creates a second variant carrying the stock again |
| `POST /api/admin/products` | `AdminProductController.java:130` | Creates a second product with a second stock ledger |
| `POST /api/promotions/{promotionId}/coupons/issue` | `CouponCommandService.java:37-61`; `Coupon.java:27` mints a fresh UUID per call; `validateCanIssue` caps only the *total* | Issues an entire second batch until `maxIssuanceCount` is hit |

`PATCH …/stock` is the sharpest: product-service owns stock and this is the platform's canonical inventory-write
API. A retried request silently double-adjusts.

The census also recorded that a case-insensitive `idempotenc` grep over promotion-service returns **zero** hits,
sanity-checked against order-service (which returns many) — so that absence is real, not a broken pattern.

## Scope

**In scope:**

1. Duplicate-request protection for the four endpoints above, each guarded at the level where the balance
   actually changes.
2. Tests driving the duplicate case for each.
3. Where a unique constraint is the chosen mechanism, the Flyway migration that adds it.

**Out of scope:**

- The 13 NATURAL-KEY endpoints — already safe via unique constraint or state machine. Do not add keys to them.
- The 2 money-path endpoints → `TASK-BE-535`.
- **Amending ADR-002 Decision-3.** The census found its literal wording ("accept a client key AND
  deterministically reject") is satisfied by 0 of 20 endpoints while 13 are genuinely safe by other means —
  which points at the ADR's wording rather than the code. That is the ADR owner's decision, not this task's.
- Adopting `libs/java-web-servlet`'s `IdempotencyKeyFilter`. Non-adoption in this project is documented and
  deliberate (`tasks/done/TASK-BE-430-order-placement-idempotency.md:41,58`).
- `PATCH /{id}/variants/{vid}` (mutates `additionalPrice`, a catalog list price, not a durable balance) — the
  census flagged it as a judgement call. If the reviewer decides catalog price counts as money, file it
  separately rather than widening this diff.

## Acceptance Criteria

- **AC-0 (gate — re-measure; the code wins)** — Do not inherit this ticket's table. Re-read each of the four
  handlers and confirm the absence still holds; sanity-check any "no idempotency here" grep against
  `order-service` (a known-positive) before trusting a zero. If a guard has since appeared, drop that endpoint
  from scope and say so. If the mechanism for a given endpoint turns out to already exist one layer down,
  **STOP and report** rather than adding a second one.
- **AC-1** — For each of the four, a replayed request does not change the balance a second time, proven by a
  test that asserts the **balance** (stock quantity / issued-coupon count), not that a guard row exists.
- **AC-2** — Legitimate repeated operations still work: a genuine second stock adjustment, a genuine second
  variant with a different `optionName`, a genuine second issuance batch. Each covered by its own test. This is
  the regression a naive guard introduces.
- **AC-3** — `PATCH …/stock` emits exactly one `StockChangedPayload` per effective adjustment. A guard that
  prevents the DB write but still publishes the event moves the defect downstream instead of fixing it.
- **AC-4** — Concurrency, not just sequential replay: state how each guard behaves under two simultaneous
  duplicates. A read-then-write check with nothing behind it is not a guard. Name the constraint or lock.
- **AC-5** — State in the PR body, per endpoint, which mechanism was chosen (client key / unique constraint /
  state guard) and why. Mixed choices are fine and probably correct — `optionName` uniqueness is a natural key,
  while a stock delta genuinely needs a client key because two identical adjustments can both be intentional.
- **AC-6** — `product-service` and `promotion-service` build + tests GREEN.

## Related Specs

- `projects/ecommerce-microservices-platform/docs/adr/ADR-002-saga-over-distributed-transaction.md` § Decision-3
- `projects/ecommerce-microservices-platform/specs/services/product-service/architecture.md`
- `projects/ecommerce-microservices-platform/specs/services/promotion-service/architecture.md`

## Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/` — a new request header or error code must land in
  the contract **before** implementation (`CLAUDE.md` § Core Principles).
- `StockChangedPayload` consumers (search-service reindex, and any other subscriber) — AC-3 exists because the
  event is a contract surface, not an internal detail.
- In-repo precedent to reuse rather than reinvent: `shipping-service`
  `ProcessCarrierWebhookService.java:48-51` — `registerIfFirst(deliveryId)` backed by
  `JpaWebhookDeliveryStore.java:28-43` (`existsById` + `DataIntegrityViolationException` catch, same
  transaction). This is the shape ADR-002 D3 describes and it already exists in this codebase; it is simply
  applied to a webhook while the stock endpoint has nothing. Note its caveat: a null/blank id is treated as
  first, i.e. dedup is skipped — do not copy that hole onto a balance endpoint.

## Edge Cases

- **Two identical stock adjustments can both be intentional** — "+10 received twice" is a real warehouse
  event. This is why stock needs a caller-supplied key rather than a natural key; a naive "reject identical
  delta" guard breaks correct usage. AC-2 covers it.
- **`optionName` uniqueness scope** — unique per product, not globally. Getting the scope wrong blocks a
  legitimate variant on a different product.
- **Coupon issuance cap interaction** — `validateCanIssue` already caps the total. A dedup guard must compose
  with the cap, not replace it; a replay must issue nothing, not "issue up to the cap".
- **Admin-surface risk profile** — these are operator endpoints, so a retry is likelier to come from a
  double-clicked UI or an at-least-once job than from a hostile client. That shapes the mechanism (a UI-supplied
  request id is enough) but does not remove the need.

## Failure Scenarios

- **F1 — the guard blocks legitimate repeated operations** (a real second +10 stock receipt). Most likely
  regression. Guarded by AC-2.
- **F2 — DB write guarded, event still published** — downstream consumers double-process. Guarded by AC-3.
- **F3 — sequential guard defeated by concurrent duplicates.** Guarded by AC-4.
- **F4 — copying the webhook store's null-id hole** — `registerIfFirst` treats a blank id as first, silently
  skipping dedup. On a balance endpoint that is a bypass. Called out in § Related Contracts.
