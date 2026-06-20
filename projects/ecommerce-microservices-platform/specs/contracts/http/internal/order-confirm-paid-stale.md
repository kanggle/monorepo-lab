# Internal Contract — batch-worker → order-service (stale paid-order forward-confirm)

> **TASK-BE-410 (spec-clarification) → TASK-BE-412 (order-service impl, owner) / TASK-BE-413
> (batch-worker caller, impl).** order-service **OWNS** this endpoint; batch-worker is the
> **CALLER**. The endpoint exists because a paid order whose confirmation event was lost can
> sit forever in `status='PENDING' AND payment_id IS NOT NULL`. batch-worker periodically
> invokes this endpoint so order-service can **forward-confirm** (`PENDING → CONFIRMED`,
> never cancel) that paid-but-unconfirmed bucket. Mirrors the BE-402 /
> [product-to-account.md](product-to-account.md) `client_credentials` precedent for the
> auth + gateway-exclusion shape.

## Why FORWARD-CONFIRM (not cancel)

The customer already paid (`payment_id IS NOT NULL`). The correct recovery is to **fulfill**,
not refund. The normal confirm path (`StockChangedEventConsumer` consuming
`product.product.stock-changed` reason `ORDER_RESERVED` → `OrderConfirmationService.confirmOrder()`)
can be missed if that Kafka event is lost/never delivered. This endpoint replays exactly that
confirm step — same domain transition, same emitted event — so downstream fulfillment fires.

## Endpoint

`POST /api/internal/orders/confirm-paid-stale`

- Hosted by **order-service** on an internal-only route. **Gateway-excluded**: the
  ecommerce gateway routes only `/api/orders/**` and `/api/admin/orders/**` to order-service,
  so `/api/internal/orders/**` has no external route and is reachable only on the internal
  service network (same pattern as product-service `/internal/**`, BE-402).
- **System-command** endpoint (batch op). It is NOT the user-facing
  `POST /api/orders/{id}/cancel` (ownership-checked) nor the admin
  `POST /api/admin/orders/{id}/status` (ADMIN Bearer). Reuse of either was **rejected**:
  the user/admin endpoints carry ownership/role semantics that do not apply to a
  server-evaluated batch sweep, and the predicate must be evaluated **server-side** (order-service
  owns the orders table) rather than passed as a list of ids by the caller.

## Authentication — `client_credentials` Bearer JWT, fail-closed

`Authorization: Bearer <jwt>` minted via the reserved IAM OAuth client
**`ecommerce-internal-services-client`** (`client_credentials` grant — activated from
"v2 DEFERRED" by TASK-BE-410; see
[iam-integration.md § OAuth Clients](../../../integration/iam-integration.md)). The caller
(batch-worker) obtains + caches the token through its own
`IamClientCredentialsTokenProvider` (mirrors product-service, BE-402).

order-service validates the bearer as a **resource server** (JWKS signature + `exp`/`nbf`/`iat`
+ issuer + audience). **Fail-closed**: a missing / expired / malformed / wrong-issuer /
wrong-audience token → **401 `UNAUTHORIZED`**; the endpoint NEVER executes the sweep without a
valid system credential. The internal security filter chain for `/api/internal/**` is separate
from the gateway-fronted user chain (which trusts `X-User-*` headers stripped+injected by the
gateway) — `/api/internal/**` is never gateway-fronted, so it validates the JWT directly.

## Request

```json
{
  "olderThanMinutes": 30,
  "limit": 200
}
```

| Field | Type | Default | Constraint |
|---|---|---|---|
| `olderThanMinutes` | int | 30 | ≥ 1. Orders younger than this are NOT swept (gives the normal saga time to confirm). |
| `limit` | int | 200 | 1..1000. Max orders processed per call (batch back-pressure). |

## Server-side predicate (order-service evaluates)

```sql
SELECT * FROM orders
WHERE status = 'PENDING'
  AND payment_id IS NOT NULL
  AND created_at < (now() - (:olderThanMinutes * interval '1 minute'))
ORDER BY created_at ASC
LIMIT :limit
```

For each selected order, the action is `Order.confirm(clock)` (`PENDING → CONFIRMED`),
executed through the **same** application path the normal saga uses
(`OrderConfirmationService.confirmOrder(orderId)` — re-load + `confirm` + save + metrics +
publish `OrderConfirmed` via the transactional outbox), so the downstream side effects are
identical (see "Confirmation event emitted" below).

### Idempotency

- Each order is re-loaded inside a per-order `REQUIRES_NEW` transaction and its status
  re-checked. `Order.confirm()` already returns `false` (no-op, no event) when the order is
  **already `CONFIRMED`** — that order is **counted as `skipped`, not errored**.
- Re-running the whole sweep is safe: orders confirmed by a prior tick (or by the normal saga
  in between) no longer match the predicate (status is no longer `PENDING`), or are skipped if a
  race re-selected them.
- An order that left `PENDING` to a non-`CONFIRMED` status (e.g. `CANCELLED`) between selection
  and re-load is skipped (re-check), never force-confirmed.

## Confirmation event emitted

The endpoint publishes the **same** `OrderConfirmed` event the normal payment-confirm/saga path
emits — topic **`order.order.confirmed`**, co-committed with the status transition through the
transactional outbox (`SpringOrderEventPublisher` → `outboxWriter.save("Order", orderId,
"OrderConfirmed", payload)`). Payload per
[order-events.md § OrderConfirmed](../../events/order-events.md) (`orderId`, `userId`,
`confirmedAt`, `lines[]`, `shippingAddress`). Consumer **shipping-service** therefore reacts
to a batch-recovered confirmation exactly as to a normal one — no new event type, no new
consumer. The envelope `tenant_id` is the order's tenant (M5); a pre-multi-tenant order
resolves to the default tenant `ecommerce` (net-zero, D8).

## Response `200`

```json
{
  "scanned": 12,
  "confirmed": 9,
  "skipped": 3,
  "confirmedOrderIds": ["...", "..."]
}
```

| Field | Type | Meaning |
|---|---|---|
| `scanned` | int | Orders matched by the predicate this call. |
| `confirmed` | int | Orders actually transitioned `PENDING → CONFIRMED` (event emitted). |
| `skipped` | int | Orders no-op'd (already `CONFIRMED`, or raced out of `PENDING`) — counted, NOT errored. |
| `confirmedOrderIds` | string[] (optional) | Ids confirmed this call (for audit / caller logging). |

`scanned == confirmed + skipped` for a successful call. Per-order failures (e.g. optimistic-lock
conflict) are isolated: the order is left for the next tick, logged, and excluded from
`confirmed`; the call still returns `200` with the partial tally (the caller records its own
batch-history outcome).

## Error responses

| Status | Code | Reason |
|---|---|---|
| 400 | `INVALID_REQUEST` | `olderThanMinutes < 1` or `limit` out of 1..1000 |
| 401 | `UNAUTHORIZED` | Missing / expired / malformed / wrong-issuer / wrong-audience bearer (fail-closed) |

Error envelope per `platform/error-handling.md` (`{ "code", "message", "timestamp" }`).

## Boundary vs BE-138 OrderStuckDetector (DISJOINT by `payment_id`)

BE-138's `OrderStuckDetector` (in order-service) owns the **`PENDING AND payment_id IS NULL`**
bucket — orders whose payment never completed. It escalates to the terminal
`STUCK_RECOVERY_FAILED` (emits `OrderSagaRecoveryExhausted`) and **never confirms**.

This endpoint owns the **`PENDING AND payment_id IS NOT NULL`** bucket — orders that paid but
whose confirmation event was lost. The two predicates are **mutually exclusive on `payment_id`**;
no order is ever a candidate for both. This endpoint never touches `STUCK_RECOVERY_FAILED` rows
(predicate is `status='PENDING'`).

## Net-zero / invariants

- order-service initiates no outbound service call for this path (it owns the orders table; the
  predicate is a local query). It only **receives** one inbound system call — the single
  documented exception to its "all inbound through gateway" stance (see
  [order-service dependencies.md](../../../services/order-service/dependencies.md)).
- The transition, event, and downstream effects are **identical** to a normal confirm; this is a
  recovery replay, not a new business capability.
