# ADR-MONO-022 — ecommerce ↔ wms Cross-Project Order-Fulfillment Integration

**Status:** ACCEPTED
**Date:** 2026-06-08 (PROPOSED 2026-06-08 · ACCEPTED 2026-06-08, same-session user-explicit intent "진행" on the §D7 plan — see § 6)
**Decision driver:** User request (2026-06-08) — *"이 프로젝트들을 실전에서 사용할 수 있도록, 웹스토어에서 상품을 구매하면 연결된 창고에서 물건을 배송할 수 있도록"* (Coupang-style: order in the storefront → ship from the connected warehouse). User chose, via AskUserQuestion, **full bidirectional loop** (order → warehouse fulfillment → shipment-confirmed回신 → order auto-SHIPPED) over a Kafka **event-subscription** transport.
**Supersedes:** none.
**Related:** [ADR-MONO-004](ADR-MONO-004-shared-messaging-scaffolding.md) (shared `libs/java-messaging` outbox/consumer scaffolding — the transport this ADR rides on), [ADR-MONO-005](ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md) (saga category taxonomy — the fulfillment loop is a cross-project Category-B saga), [`platform/service-boundaries.md`](../../platform/service-boundaries.md) §「Asynchronous (Events) — cross-project allowed」 (the rule that *permits* this), the live precedent **scm `inventory-visibility-service` ← wms inventory events** (`projects/scm-platform/specs/contracts/events/inventory-visibility-subscriptions.md`), memory `project_portfolio_7axis_architecture`, memory `project_wms_be_153_driven_audit_series`.

> **PROPOSED→ACCEPTED (2026-06-08, same session).** This ADR was authored PROPOSED to let the user review the sub-decisions (B2C ship-to model, inventory SoT, correlation key, ACL mapping) before code. The user reviewed the §2 decisions and gave the §D7 affirmative intent ("진행") accepting the recommended options (D1 Kafka, D2-a optional `shipTo`, D4 independent-v1 inventory, D5 `orderNo` round-trip) → ACCEPTED. **NOT self-ACCEPT**: the dispatcher did not unilaterally decide; the user explicitly directed the transition + implementation. Cross-project runtime coupling between two *independently-published portfolio axes* is a genuine architecture decision, recorded here before the §D7 implementation tasks proceed.

---

## 1. Context

### 1.1 What each side already has (no new domain invention needed)

**ecommerce-microservices-platform** (14 services) already runs a complete event-driven order→shipping pipeline:

```
OrderPlaced → (payment) → PaymentCompleted → OrderConfirmed
   → shipping-service creates a Shipping record (status PREPARING)
   → [TODAY: admin manually flips PREPARING → SHIPPED]
   → ShippingStatusChanged → order-service flips Order → SHIPPED/DELIVERED
```

The `PREPARING` state is exactly the "please fulfill this" moment. Today it dead-ends at manual admin entry; there is **no warehouse automation and no wms coupling** (only pattern-level references — saga sweeper, tenant config).

**wms-platform** (7 services) `outbound-service` is *built to receive orders from an external order source*: it already accepts orders via **`POST /webhooks/erp/order`** (HMAC, code→uuid resolution via MasterReadModel) in addition to the manual REST path, and runs the full saga: `RECEIVED → PICKING → PACKED → SHIPPED` with `inventory-service` reserve/confirm. On ship it already publishes **`wms.outbound.shipping.confirmed.v1`** (shipmentId, shipmentNo, shippedAt, carrierCode).

So both endpoints of the loop already exist. The work is an **Anti-Corruption Layer (ACL)** + two cross-project event legs — *not* new domain logic.

### 1.2 The permitting rule + the live precedent

`platform/service-boundaries.md` forbids cross-DB access but **explicitly designs cross-project async event consumption via published contracts**. This is already in production between two business domains: scm `inventory-visibility-service` consumes `wms.inventory.{received,adjusted,transferred}.v1` (consumer group `scm-inventory-visibility-v1`, versioned, idempotent). This ADR follows that exact pattern, in the order direction.

### 1.3 The reconciliation that needs an explicit decision (why this is an ADR, not a task)

Four things are NOT trivially wireable and are the substance of § 2:

1. **wms outbound is B2B, not B2C drop-ship.** A wms outbound order ships to a `customerPartner` (a code resolved to a master uuid). It has **no end-consumer ship-to address field**. ecommerce orders carry a B2C consumer shipping address. The order intake contract must gain a ship-to, OR the ecommerce store is modeled as the single "customer partner" (3PL/fulfillment-for-store model). → **D2**.
2. **Vocabulary mismatch (ACL).** ecommerce product SKU ↔ wms SKU master code; ecommerce store ↔ wms customer partner; warehouse routing (which warehouse). → **D3**.
3. **Two inventory owners.** ecommerce `product-service` reserves its own stock at order time; wms `inventory-service` reserves physical stock at fulfillment. Double bookkeeping + a real failure path (wms `BACKORDERED` when physical stock is insufficient). → **D4**.
4. **Correlation key round-trip.** `wms.outbound.shipping.confirmed.v1` identifies the order by wms's internal `orderId`/`shipmentNo`, not by ecommerce's `orderId`. The loop-back consumer needs a stable correlation key. → **D5**.

### 1.4 Scope boundary — wms PROJECT.md says "ecommerce / marketplace: out of scope"

This is **not** a contradiction. wms's out-of-scope statement is about *domain responsibility* ("WMS는 창고 내부 물류에 집중; 상거래는 관심사가 아니다"), not about *who may send it orders*. wms already accepts orders from an external commerce/ERP source by design (the ERP webhook). ecommerce simply becomes a second external order source. **wms acquires no commerce logic.** The ACL lives on the ecommerce/integration side (and a thin inbound adapter in wms), keeping wms's domain pure. The ADR records this so a future reader does not mistake the integration for a scope breach (it is not — but the integration MUST NOT push pricing/promotion/payment concepts into wms).

---

## 2. Decision

### D1 — Transport: Kafka event subscription (chosen)

ecommerce publishes a **dedicated fulfillment-intent event**; wms `outbound-service` adds a **consumer** for it. Mirrors scm↔wms exactly. (Webhook alternative in § 4.) A dedicated event — **not** overloading the existing `order.order.confirmed` (which ecommerce's own shipping-service already consumes) — keeps the fulfillment contract enriched and decoupled.

**Forward leg (ecommerce → wms):** `ecommerce.fulfillment.requested.v1`, published by **shipping-service** (transactional outbox) when a Shipping record enters `PREPARING`. wms `outbound-service` consumes it → ACL maps → `ReceiveOrderUseCase` (same entry the webhook uses) → saga starts.

**Return leg (wms → ecommerce):** the **existing** `wms.outbound.shipping.confirmed.v1` is consumed by ecommerce **shipping-service** → flips Shipping `PREPARING → SHIPPED` (tracking = `shipmentNo`/`carrierCode`) → existing `ShippingStatusChanged` → order-service flips Order → SHIPPED. (Optional: also consume `wms.outbound.order.cancelled.v1` for the BACKORDERED/cancel path — see D4.)

```
ecommerce shipping-service  --ecommerce.fulfillment.requested.v1-->  wms outbound-service
        ^                                                                      |
        |  (Shipping PREPARING→SHIPPED, ShippingStatusChanged → Order SHIPPED) |
        +------------------ wms.outbound.shipping.confirmed.v1 ----------------+
```

**Topic ownership:** `ecommerce.fulfillment.requested.v1` owned by ecommerce (`specs/contracts/events/`); `wms.outbound.shipping.confirmed.v1` owned by wms (already authoritative). Each consumer side gets a cross-project subscription doc (scm precedent: `*-subscriptions.md`). Both: idempotent on `eventId` (T8), at-least-once, retry+DLT.

### D2 — B2C ship-to model: extend the wms outbound order with an optional ship-to (recommended)

| Option | Description | Verdict |
|---|---|---|
| **D2-a (recommended)** | wms outbound order gains an **optional `shipTo` block** (recipient name, address, phone) — additive, nullable, used only by external-commerce-origin orders. The single ecommerce store maps to one wms `customerPartner` (`ECOMMERCE-STORE`); the consumer's address rides in `shipTo`. | wms stays B2B-shaped for existing flows; drop-ship is an additive capability. Honest model of "warehouse ships to the end consumer on behalf of the store." |
| **D2-b** | Store = customer partner, **no** consumer address; warehouse ships in bulk to the store, store re-ships. | Doesn't match "쿠팡식 직배송". Rejected for v1. |
| **D2-c** | Each consumer becomes a wms customer partner. | Explodes master data with B2C consumers; pollutes partner master. Rejected. |

D2-a requires an **additive** field on `POST /api/v1/outbound/orders` + the fulfillment event + `outbound.order.received` payload. Additive ⇒ backward compatible with the ERP webhook path.

### D3 — ACL / master-data mapping (where the integration's real cost lives)

The mapping layer (a new ecommerce-side **integration adapter**, or a thin wms inbound adapter — § D6) resolves:

- **SKU**: ecommerce product SKU → wms `skuCode`. v1 = a **mapping table** (or a shared SKU coding convention if the demo seeds them aligned). Unmapped SKU → fulfillment request rejected to DLT + ops alert (never silently dropped).
- **Store → customer partner**: constant `ECOMMERCE-STORE` (seeded ACTIVE in wms partner master).
- **Warehouse routing**: v1 = **single default warehouse** (config). Multi-warehouse routing = v2.
- **Quantity/unit**: ecommerce line qty → wms `qtyOrdered` (EA). No short-pick in wms v1 (carried over).

### D4 — Inventory source of truth: keep independent for v1, reconcile in v2

- v1: ecommerce `product-service` stock stays the **sellability** gate at order time (unchanged); wms `inventory-service` is the **physical** SoT at fulfillment. Dual bookkeeping is accepted and **documented as a known v1 limitation**.
- **Failure path (handled in v1, MONO-196):** if wms cannot reserve physical stock it moves the order to `BACKORDERED` and emits `wms.outbound.order.cancelled.v1` (orderNo + reason=INSUFFICIENT_STOCK). ecommerce shipping-service consumes it → surfaces an ops alert (Shipping stays PREPARING-flagged).
- **v2(a) — auto-refund/auto-cancel saga (built, TASK-MONO-197):** ecommerce `order-service` also consumes `wms.outbound.order.cancelled.v1` → Order CONFIRMED→CANCELLED (system-initiated) → emits the existing `order.cancelled`, which the already-wired `payment-service` (refund) + `promotion-service` (coupon restore) fan-out consumes. No new refund machinery; v2(a) is the missing **trigger** into the existing cancel→refund saga. shipping-service stays alert-only (no Shipping row exists at backorder time; ShippingStatus has no terminal CANCELLED).
- **v2(b) — inventory reconciliation (still named, not built):** wms already publishes `wms.inventory.*.v1` (scm consumes them) — ecommerce could consume the same to keep `product-service` availability in sync, collapsing the dual bookkeeping.

### D5 — Correlation key: ecommerce `orderId` round-trips as wms `orderNo`

ecommerce sends its order business id as the wms **`orderNo`** (the human/business identifier wms already carries through every event). For the return leg, `wms.outbound.shipping.confirmed.v1` is given an **additive `orderNo`** field (today it carries wms `orderId`/`shipmentNo` but not `orderNo`) so ecommerce can correlate without storing a wms↔ecommerce id map. Additive ⇒ backward compatible (scm consumer ignores unknown fields). Alternatively ecommerce persists the wms `orderId` from an intake ack; the additive-`orderNo` route is preferred (no synchronous ack needed for a pure-event transport).

### D6 — Where the ACL lives: ecommerce integration adapter (recommended)

A new **ecommerce-side** boundary component (a small adapter in shipping-service or a dedicated `fulfillment-integration` concern) owns the ACL and emits the wms-shaped fulfillment event. Rationale: keeps wms's domain pure (§ 1.4) and concentrates the commerce↔warehouse vocabulary translation on the commerce side, which understands its own SKUs/consumers. wms gets only a thin `FulfillmentRequestedConsumer` that calls the **existing** `ReceiveOrderUseCase` with already-wms-shaped data. (Alternative: ACL inside wms — rejected, would import commerce concepts into wms.)

### D7 — ACCEPTED transition + implementation breakdown (deferred)

On explicit user intent ("ADR-022 ACCEPTED" / "진행"), the implementation is created as tasks. Proposed breakdown (cross-project ⇒ contracts first, then atomic-ish per-project, then e2e):

1. **Contracts (atomic):** ecommerce `ecommerce.fulfillment.requested.v1` event contract + wms `outbound.order.received`/`shipping.confirmed` additive fields (`shipTo`, `orderNo`) + two cross-project `*-subscriptions.md`. (specs only — Source-of-Truth-first per CLAUDE.md.)
2. **wms task:** `FulfillmentRequestedConsumer` + additive `shipTo`/`orderNo` plumbing through outbound order + `shipping.confirmed` (+ partner/SKU seed). Project task in `wms-platform/tasks/ready/`.
3. **ecommerce task:** shipping-service outbox publish of `ecommerce.fulfillment.requested.v1` on PREPARING + ACL (SKU/partner/warehouse mapping) + consumer of `wms.outbound.shipping.confirmed.v1` → SHIPPED + BACKORDER/cancel handling. Project task in `ecommerce-.../tasks/ready/`.
4. **e2e:** federation/integration harness scenario "order → fulfillment → warehouse ship → order SHIPPED" (ADR-MONO-010 tag taxonomy; nightly per ADR-MONO-011).
5. **Recording:** ADR § 6 ACCEPTED row; memory update.

Each side stays independently buildable/publishable (D8).

> **§D7 ④ realisation (TASK-MONO-195).** The e2e is **ecommerce-owned** (`projects/ecommerce-microservices-platform/tests/e2e/`, ACL owner = ecommerce per D6) and boots the two real ecommerce services (order-service + shipping-service) on a shared Kafka, driving the loop end to end (POST order → synthetic `product.product.stock-changed` → CONFIRMED → real `ecommerce.fulfillment.requested.v1` **asserted on the broker** → synthetic `wms.outbound.shipping.confirmed.v1` → order SHIPPED, correlated by `orderNo`). The wms **boundary event is host-synthesised, not booted** — the wms internal RECEIVED→SHIPPED saga (pick/pack/ship + inventory reservation + TMS + OUTBOUND_WRITE JWT) is wms-owned coverage already gated by `FulfillmentRequestedConsumerIT` (TASK-BE-340/342); booting it here would duplicate that and add boot-ordering flake for a non-cross-project path. This mirrors the monorepo cross-project idiom (scm `WmsInventoryAdjustedConsumedE2ETest` — "Failure Scenario B → synthesise the counterpart"). `@Tag("full")` → nightly job `ecommerce-fulfillment-e2e-full`. AC-2's wms-DB assertion (`source=FULFILLMENT_ECOMMERCE` + `shipTo`) stays gated by the wms IT; the e2e asserts the forward-event **input contract** that produces it.

### D8 — Standalone-publish degradation (inherited from scm↔wms)

Both projects are published as independent repos. The consumer side must **degrade gracefully** when the producer's topics are absent in a standalone deployment (no fulfillment event ⇒ ecommerce shipping stays manual-admin, as today; no wms intake ⇒ wms runs on ERP webhook/manual only). Versioned topics + grace-period migration policy (scm precedent). The integration is **additive to each project's standalone story**, never a hard runtime dependency.

---

## 3. Consequences

- **Positive:** the portfolio gains a *real* cross-domain business flow (storefront purchase → physical warehouse shipment) — the strongest "these are production-shaped systems, not isolated demos" signal. Reuses existing endpoints on both sides; net-new code is an ACL + two consumers + additive fields.
- **Cost:** dual inventory bookkeeping in v1 (D4); a SKU/partner mapping table to seed; the demo harness must run both stacks + a shared broker (already the local topology).
- **Risk surface:** the BACKORDER failure path (D4) and correlation (D5) are the two places a naive wiring would silently lose orders — both are explicitly contracted.
- **Reversibility:** high. Pure additive — remove the consumer + the publish call and both projects revert to today's behavior.

---

## 4. Alternatives Considered

- **HTTP webhook (ecommerce → wms `/webhooks/erp/order`)** — wms change ≈ 0 (input already exists). Rejected as the *primary* transport: synchronous coupling, ecommerce must know wms's URL/HMAC secret, and it reuses the *ERP* webhook semantics (`source: WEBHOOK_ERP`) which misrepresents the origin. Kept as the standalone/fallback path (D8) and as a possible v1 shortcut if the user prefers minimal wms change.
- **Overload `order.order.confirmed` as the fulfillment trigger** — rejected (D1): it's already consumed by ecommerce's own shipping-service and lacks ship-to/warehouse/SKU-mapping richness.
- **Make wms the ACL owner** — rejected (D6): imports commerce vocabulary into wms, violating the spirit of § 1.4.
- **Unify inventory in v1 (single SoT)** — rejected: large blast radius across both inventory models; v2 path named in D4.
- **ACCEPTED now, skip PROPOSED** — rejected: the B2C ship-to (D2), inventory SoT (D4), and correlation (D5) decisions warrant user review before code. self-ACCEPT prohibited.

---

## 5. Relationship to prior ADRs

| Aspect | ADR-MONO-004 | ADR-MONO-005 | ADR-MONO-022 (this) |
|---|---|---|---|
| Scope | shared messaging scaffolding | saga timeout/DLT policy | cross-project order-fulfillment loop |
| Status | ACCEPTED | ACCEPTED | **PROPOSED** |
| Relation | transport this rides on | the loop is a cross-project saga under this taxonomy | first business-domain↔business-domain *order* coupling |

First runtime coupling in the **order** direction between two business domains (scm↔wms inventory coupling is the read/visibility precedent).

---

## 6. Status Transition History

Append-only.

| Date | Transition | Transport | Ship-to (D2) | Inventory SoT (D4) | User intent quote | PR(s) |
|---|---|---|---|---|---|---|
| 2026-06-08 | created PROPOSED | Kafka event subscription (D1) | D2-a optional `shipTo` (proposed) | independent v1, reconcile v2 (proposed) | "웹스토어에서 상품을 구매하면 연결된 창고에서 물건을 배송… 쿠팡에서 시키면 연결된 창고에서 배송" + AskUserQuestion: 전체 루프 / Kafka 이벤트 구독 | spec PR (TASK-MONO-193) |
| 2026-06-08 | ACCEPTED | Kafka event subscription (D1, chosen) | D2-a optional `shipTo` (accepted) | independent v1, reconcile v2 (accepted) | "진행" (on the §D7 plan: ADR ACCEPTED + create ①~④ implementation tasks + 실제 연결까지; recommended D2/D4/D5 options accepted, no overrides) | spec PR (TASK-MONO-193) + §D7 ①~④ follow-up PRs |
| 2026-06-08 | D4 v2(a) realized | (unchanged) | (unchanged) | **auto-refund/cancel saga built** (v2(b) inventory reconciliation still named) | "다음 작업 추천" → "진행" (on the recommendation to realize §D4 v2 auto-refund/cancel saga) | spec + impl PR (TASK-MONO-197) |

(PROPOSED row appended 2026-06-08 per the ADR-MONO-008/016 § D6.3 format. ACCEPTED row appended same-session on the user's explicit "진행" intent on the §D7 plan — NOT self-ACCEPT. D1–D8 decision bodies unchanged; only Status + this row + the §1 note reconciled to ACCEPTED tense. §D7 ①~④ implementation tasks created at ACCEPTED: contracts → wms → ecommerce → e2e. **D4 v2(a) row (TASK-MONO-197): a realization of the already-named "auto-refund/cancel saga = v2", not a new architecture decision — no self-ACCEPT gate; D4's chosen approach is unchanged, only its v2(a) status flips named→built.**)

---

## 7. Provenance

- User request 2026-06-08 + AskUserQuestion (loop depth = full bidirectional; transport = Kafka subscription).
- `platform/service-boundaries.md` — the rule permitting cross-project async event consumption.
- `projects/scm-platform/specs/contracts/events/inventory-visibility-subscriptions.md` — the live cross-project subscription precedent this mirrors.
- `projects/wms-platform/specs/contracts/events/outbound-events.md` (`wms.outbound.shipping.confirmed.v1`) + `specs/contracts/webhooks/erp-order-webhook.md` (external-order-source precedent) + `specs/contracts/http/outbound-service-api.md`.
- `projects/ecommerce-microservices-platform/specs/contracts/events/{order,shipping}-events.md` + shipping-service spec (PREPARING state).
- `projects/wms-platform/PROJECT.md` § Out of Scope (the scope-boundary reconciliation, § 1.4).

분석=Opus 4.8 / 구현 권장=Opus (cross-project contract design + saga/ACL + inventory SoT reconciliation = complex domain work per CLAUDE.md model-routing).
