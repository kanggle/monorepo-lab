# settlement-service — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `settlement-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `event-consumer + rest-api` (hybrid) |
| Architecture Style | **DDD-style** — see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, PostgreSQL, Kafka (consumer + producer; outbox introduced in the period-close increment) |
| Deployable unit | `apps/settlement-service/` |
| Bounded Context | `Marketplace Settlement — per-line commission accrual by seller` |
| Persistent stores | PostgreSQL (settlement ledger: `commission_accrual`, `order_snapshot`, `seller_commission_rate`, `processed_event`; period-close: `settlement_period`, `seller_payout`, `outbox`) |
| Event publication | `settlement.period.closed.v1` on `settlement.period.closed` (period-close increment, transactional outbox). Forward-declared: `settlement.commission.accrued.v1` (deferred — not emitted). |
| Event consumption | `order.order.placed` (OrderPlaced — line snapshot), `payment.payment.completed` (PaymentCompleted — accrual trigger), `payment.payment.refunded` (PaymentRefunded — reversal) |

## Responsibilities

- Consume `OrderPlaced` to cache a per-order line snapshot `(orderId → [{sellerId, gross}], tenant_id)`.
- Consume `PaymentCompleted` to compute and append `ACCRUAL` rows: one per order-line using `CommissionPolicy` (basis-point split).
- Consume `PaymentRefunded` to append `REVERSAL` rows that net the order's accruals to zero.
- Expose operator-plane reads: seller accrual summary, individual seller balance.
- Expose operator-plane rate admin: read/set per-seller commission rates.
- Enforce `commission + seller_net == gross` invariant (no rounding drift).
- **Close a settlement period** (`SettlementPeriod` OPEN→CLOSED over a half-open
  `[from, to)` window): aggregate the EXISTING in-window `commission_accrual` rows
  into one `seller_payout` per seller (skip net-zero sellers), never mutating
  accruals (F3), and emit `settlement.period.closed.v1` atomically via the outbox.
- **Execute simulated seller payouts** (`SellerPayout` PENDING→PAID|FAILED via the
  `SellerPayoutPort` simulated adapter — synthetic reference, clearly marked, no
  green-wash). REAL banking/PG disbursement + seller bank accounts remain deferred.

## Public surface

| Channel | Endpoint / Topic | Auth | Purpose |
|---|---|---|---|
| REST | `GET /api/admin/settlements/accruals` | JWT + ROLE_ADMIN | Seller accrual summary (seller-scoped, tenant-filtered) |
| REST | `GET /api/admin/settlements/sellers/{sellerId}/balance` | JWT + ROLE_ADMIN | One seller's accrued net + platform commission |
| REST | `GET /api/admin/settlements/commission-rates/{sellerId}` | JWT + ROLE_ADMIN | Read per-seller commission rate |
| REST | `PUT /api/admin/settlements/commission-rates/{sellerId}` | JWT + ROLE_ADMIN | Set per-seller commission rate (prospective only) |
| REST | `POST /api/admin/settlements/periods` | JWT + ROLE_ADMIN | Open a settlement period `[from, to)` |
| REST | `POST /api/admin/settlements/periods/{periodId}/close` | JWT + ROLE_ADMIN | Close a period → `seller_payout` PENDING + emit `settlement.period.closed.v1` |
| REST | `GET /api/admin/settlements/periods` | JWT + ROLE_ADMIN | List settlement periods (tenant-scoped) |
| REST | `GET /api/admin/settlements/periods/{periodId}/payouts` | JWT + ROLE_ADMIN | List a period's seller payouts (tenant + seller-scoped) |
| REST | `POST /api/admin/settlements/periods/{periodId}/payouts/execute` | JWT + ROLE_ADMIN | Execute simulated payouts (PENDING→PAID\|FAILED) |
| Kafka consume | `order.order.placed` | — | Line snapshot for future accrual |
| Kafka consume | `payment.payment.completed` | — | Accrual trigger (money captured) |
| Kafka consume | `payment.payment.refunded` | — | Full reversal of order's accruals |
| Kafka publish | `settlement.period.closed` | — | `settlement.period.closed.v1` on period close (transactional outbox) |

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
- `settlement_period` — period aggregate (OPEN→CLOSED), half-open `[period_from, period_to)`, `tenant_id`, `closed_at`/`closed_by`/`seller_count`, `version` (period-close increment)
- `seller_payout` — one row per `(period_id, seller_id)` (UNIQUE), `payable_net_minor`/`commission_minor`/`accrual_count`, `status [PENDING|PAID|FAILED]`, `payout_reference`, `paid_at`, `version` (period-close increment)
- `outbox` — transactional outbox for `settlement.period.closed.v1` (period-close increment)

## Published Interfaces

- [`../../contracts/http/settlement-api.md`](../../contracts/http/settlement-api.md) (HTTP — operator-plane reads + rate admin + period close/payout)
- [`../../contracts/events/settlement-subscriptions.md`](../../contracts/events/settlement-subscriptions.md) — consumed event shapes
- [`../../contracts/events/settlement-events.md`](../../contracts/events/settlement-events.md) — published `settlement.period.closed.v1`

## Dependent Systems

- Kafka — event consumption (`order.order.placed`, `payment.payment.completed`, `payment.payment.refunded`)
- PostgreSQL — settlement ledger persistence (owned DB)
- `order-service` (event producer of `OrderPlaced`)
- `payment-service` (event producer of `PaymentCompleted` / `PaymentRefunded`)

## Out of scope (still forward-declared)

- **REAL** seller banking / actual money movement (bank/PG payout API, seller bank
  accounts) — this increment ships only a **simulated** payout adapter; the `=bank`
  `SellerPayoutPort` adapter slot is a left-unimplemented seam.
- `settlement.commission.accrued.v1` — deferred (only `settlement.period.closed.v1`
  is emitted this increment).
- Period reopen — the state machine is OPEN→CLOSED with no reopen.
- Partial / proportional refund clawback — v1 treats every refund as a full reversal.
- Multi-currency — KRW-only in v1 (matching order/payment).
- Tiered / category-based commission — flat per-seller rate only.
