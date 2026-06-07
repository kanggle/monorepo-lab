# Task ID

TASK-MONO-194

# Title

ADR-MONO-022 §D7 ① — Author the cross-project fulfillment **contracts** (Source-of-Truth-first): the forward event `ecommerce.fulfillment.requested.v1`, two cross-project subscription docs, and the additive `shipTo`/`orderNo` fields on wms `outbound.order.received` / `outbound.shipping.confirmed`.

# Status

ready

# Owner

claude (Opus 4.8) — cross-project contract authoring (ecommerce + wms `specs/contracts/events/`). Spec-only; precedes implementation per CLAUDE.md Contract Rule. One doc PR (bundled with TASK-MONO-193 ADR or standalone).

# Task Tags

- event
- api

---

# Dependency Markers

- **선행**: TASK-MONO-193 (ADR-MONO-022 ACCEPTED).
- **후속**: TASK-BE-340 (wms impl), TASK-BE-341 (ecommerce impl) — both consume these contracts.

# Goal

These files exist and agree on envelope/correlation: `ecommerce-.../specs/contracts/events/{fulfillment-events.md, wms-shipment-subscriptions.md}`; `wms-platform/specs/contracts/events/ecommerce-fulfillment-subscriptions.md`; `wms-platform/specs/contracts/events/outbound-events.md` carries additive `shipTo` (order.received) + `orderNo` (shipping.confirmed) + `FULFILLMENT_ECOMMERCE` source.

# Scope

## In Scope
- `projects/ecommerce-microservices-platform/specs/contracts/events/fulfillment-events.md` (new — forward event, wms-envelope-shaped by ACL design).
- `projects/ecommerce-microservices-platform/specs/contracts/events/wms-shipment-subscriptions.md` (new — return leg consume).
- `projects/wms-platform/specs/contracts/events/ecommerce-fulfillment-subscriptions.md` (new — forward leg consume).
- `projects/wms-platform/specs/contracts/events/outbound-events.md` (edit — additive `shipTo`/`orderNo`/`FULFILLMENT_ECOMMERCE`).

## Out of Scope
- Any code (TASK-BE-340/341).

# Acceptance Criteria

- AC-1: Forward event documents the **wms-compatible camelCase envelope** (interop note) so wms's existing `EventEnvelopeParser` consumes it unchanged.
- AC-2: Correlation (D5) is explicit on both sides: ecommerce orderId → wms `orderNo` → additive `orderNo` on `shipping.confirmed`/`order.cancelled` → ecommerce locates Shipping by `orderId == orderNo`.
- AC-3: B2C `shipTo` (D2-a) is additive + nullable; ERP/manual paths unaffected (`shipTo=null`).
- AC-4: Every additive field marked "existing consumers ignore it" (backward compat: scm↔wms inventory consumer + admin-service + ERP webhook path unbroken).
- AC-5: D8 graceful-degradation paragraph present on each cross-project doc.

# Related Specs

- ADR-MONO-022; `platform/service-boundaries.md`; scm `inventory-visibility-subscriptions.md` (pattern).

# Related Contracts

- The contracts authored here ARE the deliverable.

# Edge Cases

- **Envelope shape mismatch** — ecommerce-internal events are snake_case; this cross-project event MUST be camelCase (wms convention). The ACL on the producer side emits the consumer's shape.
- **Missing `orderNo` on return leg** — consumer DLTs rather than guessing (a pre-D5 wms).

# Failure Scenarios

- **Non-additive change to a shared topic** → breaks scm↔wms or admin-service. Prevented by AC-4 (additive-only, nullable).
