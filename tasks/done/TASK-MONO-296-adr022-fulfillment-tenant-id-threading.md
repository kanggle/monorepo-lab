---
id: TASK-MONO-296
title: "ADR-022 fulfillment return-leg tenant_id threading (ADR-MONO-030 Step 4, facet d)"
status: done
scope: cross-project
projects: [ecommerce-microservices-platform, wms-platform]
tags: [code, test, contract, multi-tenant, event-driven, cross-project]
analysis_model: "Opus 4.8"
impl_model: "Opus 4.8"
created: 2026-06-18
---

# TASK-MONO-296 — ADR-022 fulfillment return-leg tenant_id threading

## Goal

Complete the M5 invariant (async `tenant_id` propagation across the ecommerce event mesh,
incl. ADR-MONO-022 fulfillment events) for the **ecommerce ↔ wms fulfillment loop** — the
deferred ADR-MONO-030 Step 4 **facet d**.

**Forward leg is already done** (verify, do not redo): ecommerce `shipping-service`
`FulfillmentAcl` already threads `tenantId` from the inbound `OrderConfirmedEvent.tenant_id`
into the `ecommerce.fulfillment.requested.v1` envelope (`FulfillmentRequestedMessage.tenantId`).

**The gap is the return leg + the wms passthrough:**
- wms `outbound-service` `EventEnvelope` does **not** capture the inbound `tenant_id`; the
  `FulfillmentRequestedConsumer` drops it on the floor.
- the wms return-leg events `wms.outbound.shipping.confirmed.v1` and
  `wms.outbound.order.cancelled.v1` carry **no `tenant_id`**.
- the ecommerce return consumers (`WmsShippingConfirmedConsumer`,
  `WmsOutboundCancelledConsumer` in shipping-service; `WmsOutboundCancelledConsumer` in
  order-service) do **not** bind `TenantContext`, so their tenant-scoped repository
  lookups/writes silently use the default tenant instead of the order's real tenant.

## Design decision (recorded — NOT a new architecture decision)

**wms treats `tenant_id` as an opaque correlation passthrough**, exactly like the ADR-022 **D5
`orderNo` round-trip** pattern: wms receives `tenant_id` on the inbound fulfillment event,
carries it on the outbound order, and echoes it back on the return-leg events — **without
interpreting it, filtering rows by it, or changing any gate**. wms does **NOT** become
multi-tenant (ADR-MONO-030 §1.1 keeps wms domain data single-tenant); `tenant_id` is a
correlation field alongside `orderNo`, nothing more. This is a *realization* of the
already-named ADR-022 D5/D6 + ADR-030 §3.1 M5 deferred work via the established D5
correlation-round-trip pattern — recorded as an ADR-022 §6 status row (same class as the
v2(a)/v2(b) realization rows), not a self-ACCEPT'd new decision.

ecommerce return consumers additionally keep a **local fallback**: when the return envelope's
`tenant_id` is absent (older wms / standalone), resolve the tenant from the local Shipping/Order
row by `orderNo` (ecommerce already persists `tenantId` on `Shipping`). This makes the change
fully backward-compatible (D8).

## Scope (atomic cross-project PR — contracts + wms + ecommerce together)

### A. Contracts (Source-of-Truth first)
1. `projects/wms-platform/specs/contracts/events/outbound-events.md` — add an **additive**
   top-level `tenant_id` (nullable/optional) to the global envelope for
   `wms.outbound.shipping.confirmed.v1` and `wms.outbound.order.cancelled.v1`. Document it as a
   correlation passthrough (echoed from the inbound fulfillment request; wms does not interpret
   it). Additive ⇒ the scm consumer and any other reader ignore the unknown field.
2. `projects/wms-platform/specs/contracts/events/ecommerce-fulfillment-subscriptions.md` —
   document that wms **captures** the inbound `tenant_id` from `ecommerce.fulfillment.requested.v1`
   and **echoes** it on the return-leg events.
3. `projects/ecommerce-microservices-platform/specs/contracts/events/wms-shipment-subscriptions.md`
   — document that the return-leg events now carry `tenant_id` and the ecommerce consumers bind it
   into `TenantContext` (with the local-Shipping fallback when absent).

### B. wms-platform (outbound-service) — opaque passthrough
4. `EventEnvelope` (the record at
   `apps/outbound-service/.../adapter/in/messaging/consumer/EventEnvelope.java`) + its parser
   `EventEnvelopeParser` — capture the top-level `tenant_id` from the inbound envelope.
5. `FulfillmentRequestedConsumer` — thread the captured `tenant_id` into the receive path
   (`ReceiveOrderCommand` / `ReceiveOrderUseCase`) as a correlation field.
6. wms outbound `Order` aggregate — persist `tenantId` as an **additive, nullable correlation
   field** (NOT an isolation key; no `NOT NULL`, no row filtering, no gate change). Determine
   wms's migration tooling (Flyway? confirm in the wms outbound-service resources) and add the
   additive nullable column accordingly. If wms outbound orders are not relationally persisted in
   a way that supports this, thread `tenant_id` through the saga state instead — choose the
   minimal mechanism that lets the return events echo the right `tenant_id`, and document it.
7. The return-leg publishers (`ConfirmShippingUseCase` → `wms.outbound.shipping.confirmed.v1`;
   the cancel/backorder path → `wms.outbound.order.cancelled.v1`) — stamp the stored order's
   `tenantId` onto the outgoing envelope (null/omitted when unknown — additive).

### C. ecommerce (shipping-service + order-service) — bind tenant on return leg
8. `shipping-service` `WmsShippingConfirmedConsumer` — read `tenant_id` from the return envelope;
   set `TenantContext` for the duration of the `markShippedByOrderId(...)` operation; fall back to
   the local Shipping row's `tenantId` (lookup by `orderNo`) when the envelope field is absent;
   clear `TenantContext` in `finally`.
9. `shipping-service` `WmsOutboundCancelledConsumer` (alert path) — same tenant-bind + fallback +
   clear discipline before any tenant-scoped read/log.
10. `order-service` `WmsOutboundCancelledConsumer` (the v2(a) auto-cancel/refund path,
    TASK-MONO-197) — read `tenant_id` from the return envelope; set `TenantContext` before the
    Order lookup/cancel/`order.cancelled` emit; fall back to the local Order row's `tenantId` by
    `orderNo` when absent; clear in `finally`. The emitted `order.cancelled` envelope must carry
    the correct `tenant_id` (it already threads tenant from `TenantContext` per the order
    multi-tenant work — confirm).

### D. Docs / ADR
11. `docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md` — append a **§6 status row**
    recording the facet-d `tenant_id` threading realization (D5 correlation-round-trip pattern;
    no decision reversed; wms stays single-tenant — opaque passthrough). Match the v2(a)/v2(b)
    realization-row tone.
12. `docs/adr/ADR-MONO-030-...md` §3.4 Step 4 — annotate facet d as REALIZED (TASK-MONO-296),
    mirroring the facet-f REALIZED annotation.
13. `projects/ecommerce-microservices-platform/specs/features/multi-tenancy-and-marketplace.md`
    §7 — move "ADR-022 이행 이벤트 `tenant_id` 스레딩" out of the deferred list and mark it
    realized (TASK-MONO-296).

## Acceptance Criteria

- AC-1 (forward, no-regress): the forward leg still threads `tenantId`
  (`FulfillmentRequestedMessage.tenantId` from `OrderConfirmedEvent.tenant_id`) — unchanged.
- AC-2 (wms capture): wms `EventEnvelope`/parser captures the inbound `tenant_id`; the
  `FulfillmentRequestedConsumer` threads it; the wms outbound order carries it (column or saga
  state).
- AC-3 (return-leg envelopes): `wms.outbound.shipping.confirmed.v1` and
  `wms.outbound.order.cancelled.v1` serialized envelopes include a top-level `tenant_id` (echoed
  from the order), omitted/null when unknown (additive).
- AC-4 (ecommerce bind): all three return consumers set `TenantContext` from the envelope
  `tenant_id` (local-row fallback when absent) and clear it in `finally`; tenant-scoped
  lookups/writes (markShipped, auto-cancel, emitted `order.cancelled`) resolve the **correct**
  tenant, not the default.
- AC-5 (backward-compat / D8): with a return envelope lacking `tenant_id` (older wms/standalone),
  the ecommerce consumers resolve tenant from the local row and behave identically to today; wms
  with no inbound `tenant_id` echoes none.
- AC-6 (wms stays single-tenant): no tenant gate change, no row filtering by `tenant_id`, no
  `NOT NULL` tenant constraint on wms rows — `tenant_id` is opaque correlation only (verify wms
  `TenantClaimValidator` and repositories are untouched in their isolation behavior).
- AC-7 (builds + unit tests): both projects build; new/changed unit tests GREEN:
  `./gradlew :projects:wms-platform:apps:outbound-service:test` and
  `./gradlew :projects:ecommerce-microservices-platform:apps:shipping-service:test`
  `:projects:ecommerce-microservices-platform:apps:order-service:test`.
  (Testcontainers ITs — incl. the wms `FulfillmentRequestedConsumerIT` and the ecommerce
  fulfillment e2e — run in CI only on this host; ensure they compile.)
- AC-8 (docs): ADR-022 §6 realization row added; ADR-030 §3.4 facet d marked REALIZED;
  multi-tenancy-and-marketplace.md §7 updated.

## Related Specs / Contracts

- `docs/adr/ADR-MONO-022-...md` (D5 orderNo round-trip — the pattern this mirrors; §6 history)
- `docs/adr/ADR-MONO-030-...md` §3.1 M5, §3.4 Step 4 facet d
- `projects/wms-platform/specs/contracts/events/outbound-events.md`
- `projects/wms-platform/specs/contracts/events/ecommerce-fulfillment-subscriptions.md`
- `projects/ecommerce-microservices-platform/specs/contracts/events/fulfillment-events.md`
- `projects/ecommerce-microservices-platform/specs/contracts/events/wms-shipment-subscriptions.md`
- `rules/traits/multi-tenant.md` M5
- `projects/wms-platform/PROJECT.md` — confirm wms is NOT declared `multi-tenant` (passthrough must
  not change that)

## Edge Cases

- **Return envelope without `tenant_id`** (older wms / standalone): ecommerce consumers fall back to
  the local Shipping/Order row's `tenantId` by `orderNo` (AC-5).
- **Forward event without `tenant_id`** (standalone ecommerce): wms echoes none; ecommerce falls
  back locally — single-store behavior preserved (D8).
- **orderNo correlation unchanged**: tenant_id rides alongside `orderNo`; correlation key semantics
  (D5) are untouched.
- **scm / other consumers of wms outbound events**: additive `tenant_id` is ignored by them
  (unknown-field tolerance) — verify no scm subscription asserts exact envelope equality.

## Failure Scenarios

- **wms gains true multi-tenant semantics by mistake**: guard against adding row filtering / a
  `NOT NULL` tenant column / gate changes (AC-6) — keep it opaque passthrough.
- **TenantContext leak across return-event threads**: the consumers run on Kafka listener threads;
  failing to clear `TenantContext` in `finally` leaks tenant into the next message — explicit
  `finally` clear required (AC-4).
- **Non-additive contract edit**: any change that makes existing fields required, or that the scm
  consumer can't ignore, breaks the cross-project contract — keep `tenant_id` additive/optional.
- **Migration version clash (wms)**: confirm the exact next migration version in the wms
  outbound-service resources before authoring (if Flyway is used).
