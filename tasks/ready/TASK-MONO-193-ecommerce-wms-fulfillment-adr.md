# Task ID

TASK-MONO-193

# Title

Author **ADR-MONO-022 (PROPOSED)** — ecommerce ↔ wms cross-project order-fulfillment integration. Capture the design + sub-decisions (transport, B2C ship-to model, inventory source-of-truth, correlation key, ACL mapping) so the user can review them before any code. This is the design-record task; the implementation tasks are created at ADR-022 ACCEPTED (this task's § ACCEPTED breakdown).

# Status

ready

# Owner

claude (Opus 4.8) — cross-project architecture decision record. Monorepo-level (touches `docs/adr/` + the design that ripples into ecommerce + wms specs). One atomic doc PR (ADR + this task). Zero code; zero contract value change (the ADR only *proposes* additive contract fields — they land at ACCEPTED).

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- adr

---

# Dependency Markers

- **선행**: none (ADR-MONO-004 shared messaging + ADR-MONO-005 saga policy already ACCEPTED — referenced, not blocking). Live precedent = scm `inventory-visibility-service` ← wms inventory events.
- **후속 (created at ADR-022 ACCEPTED, NOT now)**:
  1. **Contracts task (atomic)** — ecommerce `ecommerce.fulfillment.requested.v1` event contract + wms `outbound.order.received`/`shipping.confirmed` additive `shipTo`/`orderNo` + two cross-project `*-subscriptions.md`.
  2. **wms project task** (`wms-platform/tasks/ready/`) — `FulfillmentRequestedConsumer` → existing `ReceiveOrderUseCase`; additive `shipTo`/`orderNo` plumbing; partner/SKU seed.
  3. **ecommerce project task** (`ecommerce-.../tasks/ready/`) — shipping-service outbox publish on PREPARING + ACL (SKU/partner/warehouse map) + consumer of `wms.outbound.shipping.confirmed.v1` → SHIPPED + BACKORDER/cancel handling.
  4. **e2e task** — "order → fulfillment → warehouse ship → order SHIPPED" harness scenario (ADR-MONO-010/011).

# Goal

`docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md` exists with **Status: PROPOSED**, recording D1–D8 + alternatives + the ACCEPTED-time implementation breakdown. No code, no spec mutation in other projects yet (those are the § 후속 tasks gated on ACCEPTED). User reviews D2/D4/D5 before implementation.

# Scope

## In Scope

- `docs/adr/ADR-MONO-022-...md` (new, PROPOSED).
- This task file.

## Out of Scope (gated on ADR-022 ACCEPTED)

- Any ecommerce / wms spec or contract edit (additive `shipTo`/`orderNo`, the fulfillment event, subscription docs).
- Any code in either project.
- The ACCEPTED status flip (self-ACCEPT prohibited — requires explicit user intent per ADR-022 § D7).

# Acceptance Criteria

- AC-1: ADR-MONO-022 file present, `Status: PROPOSED`, with §1 Context, §2 Decision (D1–D8), §3 Consequences, §4 Alternatives, §5 prior-ADR relationship, §6 append-only history (PROPOSED row), §7 Provenance.
- AC-2: The four reconciliation decisions are each explicit and optioned: D1 transport (Kafka, chosen), D2 B2C ship-to (D2-a recommended), D4 inventory SoT (independent v1), D5 correlation (`orderNo` round-trip).
- AC-3: The wms PROJECT.md "ecommerce out of scope" boundary is reconciled (§1.4) — integration adds no commerce logic to wms.
- AC-4: `git diff` touches only `docs/adr/ADR-MONO-022-...md` + `tasks/ready/TASK-MONO-193-...md`. No other project file altered.
- AC-5: ADR carries the model-routing annotation (구현 권장=Opus) and the PROPOSED→ACCEPTED self-ACCEPT-prohibited note.

# Related Specs

- `projects/ecommerce-microservices-platform/specs/contracts/events/{order,shipping}-events.md` + shipping-service spec (PREPARING fulfillment trigger).
- `projects/wms-platform/specs/contracts/events/outbound-events.md` (`wms.outbound.shipping.confirmed.v1`), `specs/contracts/http/outbound-service-api.md`, `specs/contracts/webhooks/erp-order-webhook.md` (external-order-source precedent).
- `projects/scm-platform/specs/contracts/events/inventory-visibility-subscriptions.md` (cross-project subscription pattern to mirror).
- `platform/service-boundaries.md` (the permitting rule).

# Related Contracts

- None changed by this task. ADR-022 *proposes* additive contract fields (`shipTo`, `orderNo`) + a new event (`ecommerce.fulfillment.requested.v1`); these are authored in the § 후속 contracts task at ACCEPTED, not here.

# Edge Cases

- **Scope-boundary false alarm** — wms PROJECT.md out-of-scopes "ecommerce/marketplace". The ADR (§1.4) records that this is about wms's *domain responsibility*, not its *order sources* (it already accepts external orders via the ERP webhook). Reviewer must not read the integration as a HARDSTOP-03/scope breach.
- **self-ACCEPT prohibition** — the dispatcher must not flip ADR-022 to ACCEPTED without an explicit user statement; PROPOSED is terminal for this task.
- **Additive-only contract proposals** — every proposed wms/ecommerce contract change is additive (nullable field / new topic), preserving the scm↔wms consumer and the ERP webhook path.

# Failure Scenarios

- **Authoring spec edits in other projects now** → premature; those are ACCEPTED-gated § 후속 tasks. Prevented by AC-4 (diff scope).
- **Silently dropping unmapped SKU / unreservable stock** → the ADR mandates DLT + ops alert (D3) and an explicit BACKORDER signal (D4); a future implementation that no-ops these violates the ADR.
- **Treating the integration as a hard standalone dependency** → D8 requires graceful degradation; a standalone ecommerce/wms must still build and run without the other.
