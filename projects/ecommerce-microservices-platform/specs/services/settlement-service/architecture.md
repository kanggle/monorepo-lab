# settlement-service — Architecture

This document declares the internal architecture of `settlement-service`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

> **Status: forward-declared (ADR-MONO-030 Step 4 facet b).** This service does
> not exist yet — this spec is the source-of-truth for the first settlement
> increment (TASK-BE-365). It models **marketplace seller settlement /
> commission**: the platform takes a commission per order-line by seller, and the
> seller's net proceeds are accrued. Settlement-period close, payout/disbursement,
> and seller banking are **forward-declared** to a later increment (see
> § Forward-Declared below).

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
| Event publication | **None in v1** (terminal consumer). `settlement.commission.accrued.v1` forward-declared for the payout increment. |
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
- aggregates (`SettlementAccount` per `(tenant_id, seller_id)`; `CommissionAccrual` ledger rows)
- value objects (`Money` minor-units, `CommissionRate` in basis points)
- domain services (`CommissionPolicy` — the split computation)
- repositories
- domain events (forward-declared — none emitted in v1)

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
- application layer orchestrates use-cases (consume → accrue, consume → reverse) and transaction boundaries
- domain layer protects the commission-split invariant + the accrual/reversal net-zero invariant
- infrastructure layer implements repositories, the Kafka consumers, and the dedupe store
- the settlement ledger is **append-only** — an accrual is never updated or deleted; a correction is a **reversal row** (F3, ledger-style immutability)

## Outbox

- **None in v1.** settlement-service is a **terminal consumer** — it accrues into
  its own ledger and exposes reads; it publishes no events. The libs
  `OutboxAutoConfiguration` / `OutboxMetricsAutoConfiguration` are **excluded**
  (mirror the finance ledger-service / erp read-model terminal-consumer precedent).
- A `settlement.commission.accrued.v1` (or `settlement.period.closed.v1`) is
  **forward-declared** for the payout increment, at which point the outbox is
  introduced (the events contract sequences it there).

## Events (consumed)

Consumed via Kafka; each consumer dedupes on the envelope `event_id` through a
`processed_event` table in the same `@Transactional` boundary as the ledger write
(at-most-once accrual under at-least-once delivery).

| Event | Topic | Role |
|---|---|---|
| `OrderPlaced` | `order.order.placed` | **Line snapshot.** Upsert a per-order line cache `(order_id → [{seller_id, gross_minor}], tenant_id)`. Idempotent on `order_id`. The envelope's `tenant_id` is the **only** source of the order's tenant for settlement (see Multi-Tenancy below). |
| `PaymentCompleted` | `payment.payment.completed` | **Accrual trigger.** The money is captured (real). Look up the snapshot by `orderId`; for each line compute the commission split and append an `ACCRUAL` row. Idempotent on `(order_id, payment_id)`. |
| `PaymentRefunded` | `payment.payment.refunded` | **Reversal.** Append `REVERSAL` rows (negative) that net the order's accruals to zero. v1 treats a refund as a **full** reversal of the order's accruals; partial / proportional clawback is forward-declared. Idempotent on `(order_id, payment_id)`. |

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

## HTTP surface (operator-plane reads + rate admin)

Per `specs/contracts/http/settlement-api.md`. **No write path for accruals.**
- `GET /api/admin/settlements/accruals` — seller accrual summary + lines
  (operator-plane, **seller-scoped** — see Multi-Tenancy below).
- `GET /api/admin/settlements/sellers/{sellerId}/balance` — one seller's accrued
  net + platform commission.
- `GET/PUT /api/admin/settlements/commission-rates/{sellerId}` — read / set the
  per-seller rate (operator-plane). Setting a rate is **prospective** — it never
  rewrites already-booked accruals (immutability, F3).

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

- **Settlement-period close + payout** — `settlement_period` aggregate (OPEN →
  CLOSED), period-close snapshot of each seller's accrued net, a `seller_payout`
  generated per period (mirror finance ledger period-close). Introduces the outbox
  + `settlement.period.closed.v1` / `settlement.commission.accrued.v1`.
- **Seller banking / disbursement** — bank account on the seller, actual money
  movement (PG payout API), disbursement status.
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
