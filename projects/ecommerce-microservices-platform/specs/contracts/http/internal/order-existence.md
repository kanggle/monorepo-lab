# Internal Contract — batch-worker → order-service (which of these orders exist)

> **TASK-INT-028.** order-service **OWNS** this endpoint; batch-worker is the **CALLER**. It exists so
> batch-worker can tell, for a coupon that promotion-service holds as `USED` by some `orderId`, whether
> that order was ever saved. A coupon whose order does not exist is stranded — the placement that used
> it rolled back and its release never reached promotion-service — and batch-worker releases it.
> Auth and gateway-exclusion follow [order-confirm-paid-stale.md](order-confirm-paid-stale.md) exactly.

## Endpoint

`POST /api/internal/orders/existence`

- Hosted by **order-service** under the gateway-excluded `/api/internal/orders/**` route — no external
  path, internal service network only.
- **Read-only.** It never mutates an order and emits no event.

## Authentication — `client_credentials` Bearer JWT, fail-closed

Identical to [order-confirm-paid-stale.md § Authentication](order-confirm-paid-stale.md): the same
`/api/internal/**` resource-server chain, JWKS + timestamps + issuer + audience + **system-client subject
pin** (`order.internal.oauth2.allowed-client-ids`). Missing / invalid token, or a valid user token →
**401 `UNAUTHORIZED`**, never reaches the controller.

## Request

```json
{
  "orderIds": ["0b7c…", "9f21…"]
}
```

| Field | Type | Constraint |
|---|---|---|
| `orderIds` | string[] | 1..500 entries, each non-blank. Duplicates are allowed and answered once. |

## Semantics

An id is **existing** when an order row with that id is present in **any tenant**, in **any status**
(`PENDING`, `CONFIRMED`, `CANCELLED`, `DELIVERED`, … — and orders whose PII was anonymized). The lookup is
**tenant-agnostic by design**, mirroring `OrderRepository#findByIdAcrossTenants`: order ids are globally
unique, and the caller is a system sweep with no request tenant. A tenant-scoped lookup here would answer
another tenant's orders as absent, and batch-worker would release coupons held by real orders.

## Response `200`

```json
{
  "existingOrderIds": ["0b7c…"]
}
```

| Field | Type | Meaning |
|---|---|---|
| `existingOrderIds` | string[] | The subset of the requested ids that exist. Order is unspecified. Ids not requested are never included. |

🔴 **The response is the whole answer or no answer.** A caller must treat a transport error, a non-2xx
status, a missing body, or a missing `existingOrderIds` field as **"unknown"**, never as "none exist".
Reading an empty or absent answer as "absent" turns an outage into releasing every coupon in the batch.

## Error responses

| Status | Code | Reason |
|---|---|---|
| 400 | INVALID_REQUEST | `orderIds` missing, empty, over 500 entries, or containing a blank id |
| 401 | UNAUTHORIZED | Missing or invalid internal credentials (filter chain) |

## Invariants

1. **"Absent" means "never committed" only while orders are never deleted.** At `2563bba1f` order-service
   has no order deletion or retention-purge path (orders are retained in every status). 🔴 Introducing
   any order deletion **must** first revisit TASK-INT-028's orphan-coupon job — after a purge, a delivered
   order would read as absent and its coupon would be released.
2. Read-only; safe to call any number of times.
