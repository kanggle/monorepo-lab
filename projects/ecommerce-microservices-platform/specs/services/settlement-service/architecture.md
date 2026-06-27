# settlement-service — Architecture

This document declares the internal architecture of `settlement-service`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

> **Status: implemented — ADR-MONO-030 Step 4 facet b, TASK-BE-365.** This service
> is fully implemented (66 Java source files). It models **marketplace seller
> settlement / commission**: the platform takes a commission per order-line by
> seller, and the seller's net proceeds are accrued.
>
> **Period-close + simulated payout increment — IN THIS INCREMENT (TASK-BE-414
> spec, TASK-BE-415 / TASK-BE-416 impl).** A `settlement_period` aggregate
> (OPEN→CLOSED) closes a half-open `[from, to)` window by aggregating the EXISTING
> immutable `commission_accrual` rows into one `seller_payout` per seller; payout
> moves PENDING→PAID|FAILED through a `SellerPayoutPort` whose **only** adapter in
> this increment is a clearly-marked **SIMULATED** one. The outbox is introduced
> and `settlement.period.closed.v1` is published on close. **Still forward-declared**
> (see § Forward-Declared below): a REAL banking/PG payout adapter + seller bank
> accounts (the simulated adapter leaves a `=bank` seam unimplemented), and
> `settlement.commission.accrued.v1` (deferred — NOT defined or emitted here).

---

## Identity

| Field | Value |
|---|---|
| Service name | `settlement-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `event-consumer + rest-api` (hybrid — see Service Type Composition below) |
| Architecture Style | **DDD-style Architecture** (4-layer + application/port) |
| Domain | ecommerce |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | Marketplace settlement — per-line commission accrual by seller, refund reversal, seller accrual read |
| Deployable unit | `apps/settlement-service/` |
| Data store | PostgreSQL (owned) |
| Event publication | `settlement.period.closed.v1` on `settlement.period.closed` (published on period close via the transactional outbox — see § Outbox). `settlement.commission.accrued.v1` remains forward-declared (deferred — not emitted). |
| Event consumption | `OrderPlaced` from `order.order.placed` (line snapshot), `PaymentCompleted` from `payment.payment.completed` (accrual trigger), `PaymentRefunded` from `payment.payment.refunded` (reversal) |

### Service Type Composition

`settlement-service` is a hybrid per `platform/service-types/INDEX.md` § Hybrid
Cases. The **primary** type is `event-consumer` — the settlement ledger is built
entirely from the order/payment event streams (there is **no HTTP write path** for
accruals; commission is never booked by an operator call). The **secondary**
`rest-api` capability serves two read/admin surfaces: an operator-plane **seller
accrual read** and a **commission-rate admin** (set per-seller rates). The primary
type determines the spec read order — applied rules:
[platform/service-types/event-consumer.md](../../../../../platform/service-types/event-consumer.md).
The secondary capability follows
[platform/service-types/rest-api.md](../../../../../platform/service-types/rest-api.md)
and is documented under "HTTP surface" + the `settlement-api.md` contract.

---

## Why This Architecture

Settlement owns money-attribution rules that are central to marketplace
economics: how a captured payment is split between the platform's commission and
the seller's net proceeds, and how a refund unwinds that split. These are domain
invariants (a commission must equal `round(gross × rate)`, an accrual plus its
reversal must net to zero, an accrual must never cross a tenant boundary), not
CRUD — so the service uses explicit domain modeling, mirroring the
finance-platform ledger's accrual + idempotent-posting discipline.

## Internal Structure Rule

This service uses a DDD-style internal structure.

Recommended internal areas:
- presentation or interface
- application
- domain
- infrastructure

Recommended domain concepts:
- aggregates (`SettlementAccount` per `(tenant_id, seller_id)`; `CommissionAccrual` ledger rows; **`SettlementPeriod`** OPEN→CLOSED over a half-open `[from, to)` window — the one mutating aggregate besides nothing-else, mirroring finance `AccountingPeriod`; **`SellerPayout`** PENDING→PAID|FAILED, one per `(period, seller)`)
- value objects (`Money` minor-units, `CommissionRate` in basis points)
- domain services (`CommissionPolicy` — the split computation)
- application ports (`SellerPayoutPort` — outbound payout-execution seam; the only adapter this increment is a clearly-marked SIMULATED one)
- repositories
- domain events (`settlement.period.closed.v1` emitted on close via the outbox; `settlement.commission.accrued.v1` still forward-declared)

Package organization should preserve aggregate boundaries and domain ownership.

## Allowed Dependencies
- interface -> application
- application -> domain
- infrastructure -> domain
- infrastructure -> application ports if defined

## Forbidden Dependencies
- domain must not depend on framework code
- domain must not depend on persistence implementation details
- application layer must not contain domain rules that belong inside aggregates or domain services
- controllers must not bypass application services
- repositories must not contain business decisions that belong to the domain
- **settlement-service must not call order-service / payment-service HTTP APIs to
  backfill missing event data** (consumer rule — all attribution comes from the
  cached `OrderPlaced` snapshot, never a synchronous read-back)

## Boundary Rules
- application layer orchestrates use-cases (consume → accrue, consume → reverse; **open period, close period, execute payout**) and transaction boundaries
- domain layer protects the commission-split invariant + the accrual/reversal net-zero invariant + the **period state machine** (OPEN→CLOSED, no reopen) + the **payout state machine** (PENDING→PAID|FAILED)
- infrastructure layer implements repositories, the Kafka consumers, the dedupe store, **the outbox dispatcher, and the `SellerPayoutPort` adapter(s)**
- the settlement ledger is **append-only** — an accrual is never updated or deleted; a correction is a **reversal row** (F3, ledger-style immutability). **Period-close NEVER mutates accrual rows** — it only reads/aggregates them into `seller_payout` rows (the ledger immutability is preserved across the close).
- the close use-case is **one `@Transactional` boundary**: load+validate the period (re-close → already-closed error), aggregate the in-window accruals into `seller_payout` rows (PENDING), flip the period OPEN→CLOSED, and append the `settlement.period.closed.v1` outbox row — all atomic.
- the payout-execute use-case is a **separate step** (two-step payout): it runs the `SellerPayoutPort` adapter per PENDING payout and flips PENDING→PAID|FAILED, idempotent on `(period_id, seller_id)`.

## Outbox

- **Outbox v2 (TASK-BE-447).** Migrated to the shared `AbstractOutboxPublisher`
  (ADR-MONO-004 § 5) from the v1 stack (lib `OutboxPollingScheduler` relay + lib
  `OutboxWriter`/`OutboxJpaEntity` write path). Table is now `settlement_outbox`
  (v2 shape — `event_id UUID` PK, `occurred_at`, `retries`/`last_error`; mirrors
  `master_outbox`/`promotion_outbox`). The v1 `outbox` table (V2 migration) is
  retained but unused (kept so the still-EntityScanned lib `OutboxJpaEntity`
  validates under `ddl-auto=validate`). The locally-owned `processed_event`
  consumer-dedupe table is unrelated and untouched.
- Write path: `SpringSettlementEventPublisher` (implements `SettlementEventPublisher`,
  `@Profile("!standalone")`) persists a `SettlementOutboxEntity` row directly; the row
  `event_id` reuses the event envelope's own `event_id`, payload is the byte-identical
  serialized envelope (wire-preserving). Standalone keeps `NoopSettlementEventPublisher`
  (no outbox row written).
- Relay: `SettlementOutboxPublisher extends AbstractOutboxPublisher<SettlementOutboxEntity>`
  (`@Profile("!standalone")`) — `@Scheduled` poll, backoff, `eventId`/`eventType`
  headers, `MicrometerOutboxMetrics("settlement")` (+ preserved
  `settlement.outbox.pending.count` gauge).
- **`settlement.period.closed.v1`** is appended to the outbox **in the same
  `@Transactional` boundary** as the period close + `seller_payout` row creation
  (transactional outbox — mirrors order/payment co-commit discipline). The dispatcher
  relays it to topic `settlement.period.closed` (preserved verbatim). See
  `specs/contracts/events/settlement-events.md` for the producer schema.
- **`settlement.commission.accrued.v1` is still forward-declared** — it is NOT
  defined or emitted in this increment (deferred to a later increment).
- The accrual/reversal **consume** path is unchanged and emits nothing — only the
  period-close path produces.

## Events (consumed)

Consumed via Kafka; each consumer dedupes on the envelope `event_id` through a
`processed_event` table in the same `@Transactional` boundary as the ledger write
(at-most-once accrual under at-least-once delivery).

| Event | Topic | Role |
|---|---|---|
| `OrderPlaced` | `order.order.placed` | **Line snapshot.** Upsert a per-order line cache `(order_id → [{seller_id, gross_minor}], tenant_id)`. Idempotent on `order_id`. The envelope's `tenant_id` is the **only** source of the order's tenant for settlement (see Multi-Tenancy below). |
| `PaymentCompleted` | `payment.payment.completed` | **Accrual trigger.** The money is captured (real). Look up the snapshot by `orderId`; for each line compute the commission split and append an `ACCRUAL` row. Idempotent on `(order_id, payment_id)`. |
| `PaymentRefunded` | `payment.payment.refunded` | **Proportional reversal.** Append `REVERSAL` rows (negative) clawing back commission in proportion to the refund `amount` (`reverses_accrual_id` links each to its parent ACCRUAL; the final `fullyRefunded` refund reverses the exact remaining so the order nets to zero per seller). Idempotent on the envelope `event_id` (a payment may emit several partial refunds). See `contracts/events/settlement-subscriptions.md` § Proportional clawback rule. |

Consumer group: `settlement-service`. Malformed / unattributable events route to
the retry topic → DLQ (never fail the whole pipeline).

## Settlement domain model

**Money** stays integer **minor units** (`long`, KRW implicit — matching
order-service / payment-service; no `Money` VO exists in ecommerce yet, so this
service introduces a local one). **Commission rate** is an integer in **basis
points** (bps; `1000 bps = 10%`) — never a float/`BigDecimal` (F5-style: the only
arithmetic is `gross × bps / 10000`, an exact integer division rounded HALF_UP).

**Commission split (per order-line, `CommissionPolicy`):**

```
rate_bps        = sellerRate(tenant_id, seller_id) ?? platform default     // see rate resolution
commission_minor = round(gross_minor × rate_bps / 10000)   (HALF_UP, ≥ 0)
seller_net_minor = gross_minor − commission_minor                           (exact, no second rounding)
```

where `gross_minor = unitPrice × quantity` for the line (from the `OrderPlaced`
snapshot). `seller_net` is the remainder so the split always reconciles
(`commission + seller_net == gross`, no rounding drift).

**Rate resolution (per-seller + platform default):**
- `seller_commission_rate (tenant_id, seller_id) → rate_bps` — an operator-set
  per-seller rate. Missing row → the **platform default** `settlement.commission.default-rate-bps`.
- **net-zero / standalone degrade (D8):** the platform default may be `0` — a
  single-seller standalone store with no configured rate accrues `commission = 0`,
  `seller_net = gross` (the seller keeps everything = byte-equivalent to "no
  marketplace economics"). The default-seller (Step 3) accrues at the default rate.

**Accrual ledger (`commission_accrual`, append-only, immutable — F3):** one row
per `(order line × event)`. An `ACCRUAL` row is positive; a `REVERSAL` row is the
negative of the original. A seller's settleable balance is `Σ(seller_net_minor)`
over their rows (commission is `Σ(commission_minor)` to the platform). Reads
aggregate; nothing is mutated in place.

**Idempotency:** the `processed_event` dedupe (on `event_id`) guards re-delivery;
additionally the accrual write is keyed on `(order_id, payment_id)` so a replayed
`PaymentCompleted` cannot double-accrue, and a reversal on the same key cannot
double-reverse.

## Period close + simulated payout (this increment)

Mirrors the finance-platform ledger `AccountingPeriod` precedent (half-open window,
OPEN→CLOSED, one allowed mutating aggregate; the underlying ledger stays immutable).

**`SettlementPeriod` aggregate** — per-tenant, half-open `[period_from, period_to)`
window (operator supplies the window; **grain-agnostic** — daily/weekly/monthly are
all just windows). State machine `OPEN → CLOSED` (no reopen — forward-declared).
`open(...)` validates `from < to`; `close(...)` flips OPEN→CLOSED and stamps
`closed_at`/`closed_by`/`seller_count`. A second close → **already-closed error**.

**Close-time aggregation (NEVER mutates accruals — F3 preserved):** the close
use-case reads the EXISTING `commission_accrual` rows whose `occurred_at` falls in
`[period_from, period_to)` (tenant-scoped) and folds them, per seller, into one
`seller_payout` row:
```
payable_net_minor = Σ seller_net_minor   (ACCRUAL positive − REVERSAL negative)
commission_minor  = Σ commission_minor
accrual_count     = number of accrual rows folded
```
**Net-zero seller skip (decision 7):** a seller whose `payable_net_minor ≤ 0` after
the fold (e.g. fully reversed) produces **no `seller_payout` row** — `seller_count`
counts only sellers with a positive payable. Aggregation is idempotent: re-running
close on an already-CLOSED period is rejected (already-closed error), so payout rows
are created exactly once.

**`SellerPayout` aggregate** — one per `(period_id, seller_id)` (UNIQUE), created
**PENDING** at close. State machine `PENDING → PAID | FAILED` via the
`SellerPayoutPort`. `payout_reference` is NULL while PENDING, set when PAID.

**`SellerPayoutPort` (outbound application port) + adapter seam:**
- The port abstracts "execute a payout for this `seller_payout` row" → returns a
  PAID(`payout_reference`) or FAILED outcome.
- **The only adapter in this increment is a SIMULATED one**
  (`@ConditionalOnProperty(name = "settlement.payout.mode", havingValue = "simulated",
  matchIfMissing = true)`). It records a **synthetic** `payout_reference` and flips
  the row PAID. **No green-washing** (mirror erp `NoopExternalChannelAdapter`
  discipline): the adapter clearly marks the payout as simulated (log + reference
  prefix), never claims a real disbursement occurred.
- A **REAL** `=bank` adapter slot
  (`havingValue = "bank"`) is left **unimplemented** (seam only) — actual
  banking/PG money movement + seller bank accounts are forward-declared.

**Two-step payout (decision 4):** period close creates `seller_payout` rows PENDING;
a **separate** payout-execute step runs the simulated adapter to flip them PAID|FAILED.
Close and execute are distinct operator actions / use-cases.

### Owned Data (this increment additions — Flyway V2)

- **`settlement_period`** `(period_id PK, tenant_id NOT NULL, period_from NOT NULL,
  period_to NOT NULL [exclusive], status [OPEN|CLOSED] CHECK, closed_at, closed_by,
  seller_count, version)`
  + `CHECK(period_from < period_to)` + `idx(tenant_id, period_from, period_to)`.
- **`seller_payout`** `(payout_id PK, period_id FK → settlement_period, tenant_id
  NOT NULL, seller_id NOT NULL, payable_net_minor, commission_minor, accrual_count,
  status [PENDING|PAID|FAILED] CHECK, payout_reference NULL-while-pending, paid_at,
  version)`
  + `UNIQUE(period_id, seller_id)` + `idx(period_id)` + `idx(tenant_id, seller_id)`.
- **`outbox`** — the standard ecommerce outbox table (same DDL as order/payment-service;
  introduced this increment for `settlement.period.closed.v1`).

These join the existing v1 stores (`order_snapshot` / `commission_accrual` /
`seller_commission_rate` / `processed_event`). All carry `tenant_id` (M1).

## HTTP surface (operator-plane reads + rate admin)

Per `specs/contracts/http/settlement-api.md`. **No write path for accruals.**
- `GET /api/admin/settlements/accruals` — seller accrual summary + lines
  (operator-plane, **seller-scoped** — see Multi-Tenancy below).
- `GET /api/admin/settlements/sellers/{sellerId}/balance` — one seller's accrued
  net + platform commission.
- `GET/PUT /api/admin/settlements/commission-rates/{sellerId}` — read / set the
  per-seller rate (operator-plane). Setting a rate is **prospective** — it never
  rewrites already-booked accruals (immutability, F3).
- **Period close + payout (this increment, operator-plane, `roles ∋ ADMIN`):**
  - `POST /api/admin/settlements/periods` — open a period over a `[from, to)` window.
  - `POST /api/admin/settlements/periods/{periodId}/close` — close it (aggregate
    accruals → `seller_payout` PENDING + emit `settlement.period.closed.v1`).
  - `GET /api/admin/settlements/periods` — list periods (tenant-scoped).
  - `GET /api/admin/settlements/periods/{periodId}/payouts` — list the period's
    `seller_payout` rows (tenant-scoped + **seller-scoped** ABAC, same filter as the
    accrual reads).
  - `POST /api/admin/settlements/periods/{periodId}/payouts/execute` — run the
    simulated `SellerPayoutPort` over the period's PENDING payouts (PENDING→PAID|FAILED).

## Integration Rules
- consumed events must follow the published producer contracts (`order-events.md`, `payment-events.md`); settlement reads only contracted fields
- HTTP APIs must follow `settlement-api.md`
- settlement must not call order/payment HTTP APIs to compensate for missing event data (consumer rule)
- shared libraries must not absorb settlement-domain logic

## Testing Expectations
Required emphasis:
- domain: `CommissionPolicy` split (gain/zero-rate/rounding HALF_UP; `commission + seller_net == gross`)
- application: consume `OrderPlaced` → snapshot; `PaymentCompleted` → accrual; `PaymentRefunded` → reversal nets to zero; idempotent replay
- repository integration tests (Testcontainers): the ledger + dedupe + the snapshot cache
- contract tests for consumed events + the read API
- **out-of-order**: `PaymentCompleted` before its `OrderPlaced` snapshot (failure scenario F2)
- **period close (this increment)**: domain — `SettlementPeriod` open/close (window
  validation, re-close already-closed guard, half-open coverage); application — close
  folds in-window accruals into `seller_payout` rows, skips net-zero sellers, emits
  `settlement.period.closed.v1` to the outbox atomically; payout-execute flips
  PENDING→PAID via the simulated adapter, idempotent on `(period_id, seller_id)`;
  repository IT (Testcontainers) over `settlement_period` + `seller_payout` + the
  outbox; contract test for `settlement.period.closed.v1`

## Multi-Tenancy & Marketplace (ADR-MONO-030)

> 모델 SoT = [specs/features/marketplace-settlement.md](../../features/marketplace-settlement.md)
> + [specs/features/multi-tenancy-and-marketplace.md](../../features/multi-tenancy-and-marketplace.md)
> (ADR-MONO-030). 본 섹션은 settlement-service 적용분만 선언한다.

- **바깥 축 (tenant, M1-M7)**: 모든 settlement row(`seller_commission_rate` /
  snapshot / `commission_accrual`)에 `tenant_id` (NOT NULL). read =
  `WHERE tenant_id = <요청 컨텍스트>`(gateway `X-Tenant-Id`); cross-tenant 조회 =
  404 (M3). 참조 = order/product (Step 2).
- **★tenant 파생 — settlement 고유 제약**: `payment.payment.*` 이벤트 봉투에는
  **`tenant_id` 가 없다**(payment-service 는 Step 2 tenant 미적용 —
  `payment-events.md` 봉투 확인). 따라서 settlement 의 `tenant_id` 권위 소스는
  **`OrderPlaced` 스냅샷의 봉투 `tenant_id`** 뿐이다. accrual/reversal 은 `orderId`
  로 스냅샷을 조인해 그 `tenant_id` 를 적재한다. (스냅샷 부재 = 귀속 불가 → F2.)
- **안쪽 축 (seller) — accrual 단위 귀속**: 각 `commission_accrual` row 는 그
  order-line 의 `seller_id`(`OrderPlaced` payload `items[].sellerId`, Step 3)로
  귀속. 한 주문이 여러 셀러에 걸치면 라인별 독립 accrual.
- **셀러-스코프 read (ABAC `org_scope` 형태, ADR-025 재사용, net-zero/fail-OPEN)**:
  OPERATOR 가 자기 셀러 accrual 만 보는 필터. seller-scope claim(gateway
  `X-Seller-Scope`) 부재 / `'*'` = **무필터**(테넌트 운영자 전체 조망, fail-OPEN);
  restricted = `seller_id` 필터. **항상 `tenant_id` 필터 *내부***(isolate-then-attribute,
  Step 3 AC-6 미러). write/consume 경로는 seller-scope 무관(이벤트 기반).
- **degradation (D8)**: default-tenant + default-seller + 플랫폼 기본율(`0` 가능)
  → 단일 스토어 = commission 0, seller_net = gross(오늘 동작과 경제적으로 동치).
- **회귀 (M6)**: cross-tenant leak IT — 테넌트 A accrual 이 B 토큰으로 안 보임.
- **PROJECT.md `marketplace` 스코프**: ADR §D7 타이밍 — 구현(impl PR) 시 PROJECT.md
  Out-of-Scope "marketplace 정산/수수료 없음" 라인을 정정.

## Forward-Declared (later increments — NOT this slice)

- **REAL seller banking / disbursement** — a bank account on the seller, actual
  money movement (PG / bank payout API), real disbursement status. This increment
  ships only the **simulated** payout adapter; the `SellerPayoutPort` `=bank` adapter
  slot is a left-unimplemented seam (see § Period close + simulated payout).
- **`settlement.commission.accrued.v1`** — deferred. Only `settlement.period.closed.v1`
  is emitted in this increment; a per-accrual event is NOT defined or published.
- **Period reopen** — the period state machine is OPEN→CLOSED with no reopen.
- **Partial / proportional refund clawback** — v1 reverses a refund as a full
  order reversal; proportional netting against a partial `PaymentRefunded.amount`
  is deferred.
- **Multi-currency** — KRW-only in v1 (matching order/payment).
- **Tiered / category / promotion-adjusted commission** — flat per-seller rate in
  v1; tiered or category-based or promotion-net rates are deferred.
- **payment-service tenant_id enrichment** — when payment-service joins Step 2,
  settlement can read tenant from the payment envelope directly (and drop the
  snapshot-derivation dependency for the accrual path).

## Change Rule
Any architectural change to this service must be documented here first before implementation.
