# settlement-service — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `settlement-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `event-consumer + rest-api` (hybrid) |
| Architecture Style | **DDD-style** — see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, PostgreSQL, Kafka (consumer only; no outbox in v1) |
| Deployable unit | `apps/settlement-service/` |
| Bounded Context | `Marketplace Settlement — per-line commission accrual by seller` |
| Persistent stores | PostgreSQL (settlement ledger: `commission_accrual`, `order_snapshot`, `seller_commission_rate`, `processed_event`) |
| Event publication | **None in v1** (terminal consumer). Forward-declared: `settlement.commission.accrued.v1` for the payout increment. |
| Event consumption | `order.order.placed` (OrderPlaced — line snapshot), `payment.payment.completed` (PaymentCompleted — accrual trigger), `payment.payment.refunded` (PaymentRefunded — reversal) |

## Responsibilities

- Consume `OrderPlaced` to cache a per-order line snapshot `(orderId → [{sellerId, gross}], tenant_id)`.
- Consume `PaymentCompleted` to compute and append `ACCRUAL` rows: one per order-line using `CommissionPolicy` (basis-point split).
- Consume `PaymentRefunded` to append `REVERSAL` rows that net the order's accruals to zero.
- Expose operator-plane reads: seller accrual summary, individual seller balance.
- Expose operator-plane rate admin: read/set per-seller commission rates.
- Enforce `commission + seller_net == gross` invariant (no rounding drift).

## Public surface

| Channel | Endpoint / Topic | Auth | Purpose |
|---|---|---|---|
| REST | `GET /api/admin/settlements/accruals` | JWT + ROLE_ADMIN | Seller accrual summary (seller-scoped, tenant-filtered) |
| REST | `GET /api/admin/settlements/sellers/{sellerId}/balance` | JWT + ROLE_ADMIN | One seller's accrued net + platform commission |
| REST | `GET /api/admin/settlements/commission-rates/{sellerId}` | JWT + ROLE_ADMIN | Read per-seller commission rate |
| REST | `PUT /api/admin/settlements/commission-rates/{sellerId}` | JWT + ROLE_ADMIN | Set per-seller commission rate (prospective only) |
| Kafka consume | `order.order.placed` | — | Line snapshot for future accrual |
| Kafka consume | `payment.payment.completed` | — | Accrual trigger (money captured) |
| Kafka consume | `payment.payment.refunded` | — | Full reversal of order's accruals |

자세한 spec 은 [`../../contracts/http/settlement-api.md`](../../contracts/http/settlement-api.md) + [`../../contracts/events/settlement-subscriptions.md`](../../contracts/events/settlement-subscriptions.md) 참조.

## Key invariants

1. **Accrual ledger is append-only** — no `UPDATE` or `DELETE`; corrections are explicit `REVERSAL` rows.
2. **Commission split reconciles exactly**: `commission_minor + seller_net_minor == gross_minor` per line (HALF_UP rounding applied once to commission; seller_net is the remainder).
3. **Cross-tenant isolation (M3)**: all ledger rows carry `tenant_id`; reads filtered by `WHERE tenant_id = currentTenant()`; cross-tenant lookup = 404.
4. **Idempotent consumption**: `processed_event` dedupe on `event_id`; accrual keyed on `(order_id, payment_id)`.
5. **No HTTP read-back from order/payment**: all attribution comes from the cached `OrderPlaced` snapshot; missing snapshot → `SnapshotNotFoundException` (F2).
6. **Rate changes are prospective**: setting a new commission rate never rewrites already-booked accrual rows.

## Owned Data

- `order_snapshot` / `order_snapshot_line` — per-order line cache `(orderId, sellerId, gross, tenant_id)`
- `commission_accrual` — append-only ledger (`ACCRUAL` / `REVERSAL` rows with `tenant_id`, `seller_id`, `commission_minor`, `seller_net_minor`)
- `seller_commission_rate` — per-`(tenant_id, seller_id)` basis-point rate; missing = platform default
- `processed_event` — Kafka dedupe store on `event_id`

## Published Interfaces

- [`../../contracts/http/settlement-api.md`](../../contracts/http/settlement-api.md) (HTTP — operator-plane reads + rate admin)
- [`../../contracts/events/settlement-subscriptions.md`](../../contracts/events/settlement-subscriptions.md) — consumed event shapes

## Dependent Systems

- Kafka — event consumption (`order.order.placed`, `payment.payment.completed`, `payment.payment.refunded`)
- PostgreSQL — settlement ledger persistence (owned DB)
- `order-service` (event producer of `OrderPlaced`)
- `payment-service` (event producer of `PaymentCompleted` / `PaymentRefunded`)

## Out of scope (v1)

- Settlement-period close + payout / disbursement — forward-declared (later increment).
- Seller banking / actual money movement — forward-declared.
- Partial / proportional refund clawback — v1 treats every refund as a full reversal.
- Multi-currency — KRW-only in v1 (matching order/payment).
- Tiered / category-based commission — flat per-seller rate only.
