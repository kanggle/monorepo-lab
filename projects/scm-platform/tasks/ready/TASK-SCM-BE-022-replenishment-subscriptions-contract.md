# Task ID

TASK-SCM-BE-022

# Title

Author `replenishment-subscriptions.md` — scm `demand-planning-service` cross-project subscription to wms `wms.inventory.alert.v1` (`inventory.low-stock-detected`). Consumer-driven contract for the ADR-MONO-027 replenishment loop. spec-only.

# Status

ready

# Owner

backend

# Task Tags

- event
- contract
- spec

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **선행 (prerequisite)**: ADR-MONO-027 ACCEPTED ([TASK-MONO-220](../../../../tasks/ready/TASK-MONO-220-adr-mono-027-accepted-transition.md)). This task realizes ADR-027 D1.
- **sibling pattern**: [`inventory-visibility-subscriptions.md`](../../specs/contracts/events/inventory-visibility-subscriptions.md) — the existing scm←wms cross-project subscription doc this mirrors.
- **후속 (follow-on)**: [TASK-SCM-BE-023](../backlog/TASK-SCM-BE-023-demand-planning-service-spec.md) (service spec consumes this contract), TASK-SCM-BE-024 (impl).

# Goal

Define, as a consumer-driven contract, that scm's new `demand-planning-service` subscribes to the **existing** wms low-stock alert topic — establishing the cross-project seam of the replenishment loop **before** any code, per the Contract Rule (contracts precede implementation).

The wms producer is **unchanged** (it already publishes `inventory.low-stock-detected` for its internal notification/admin consumers). This task only declares the new scm consumer and records a 1-line cross-project-consumer note on the wms side for discoverability.

# Scope

## In Scope

### 1. New scm subscription contract

`projects/scm-platform/specs/contracts/events/replenishment-subscriptions.md` (NEW):

- **Subscribed topic**: `wms.inventory.alert.v1`, event type `inventory.low-stock-detected`.
- **Authoritative schema reference**: `projects/wms-platform/specs/contracts/events/inventory-events.md` §7 (the producing service owns it; reproduce only the consumed subset).
- **Consumer group**: `scm-demand-planning-v1` (distinct from `scm-inventory-visibility-v1`).
- **Consumer-driven subset read**: `eventId`, `occurredAt`, `payload.skuId`, `payload.skuCode`, `payload.warehouseId`/`locationId`, `payload.availableQty`, `payload.threshold`, `payload.triggeringEventType`. Consumer MUST ignore unknown payload fields (forward compat).
- **Idempotency key**: `eventId` (UUID v7), `processed_events` dedupe (T8). Duplicate → no-op.
- **Retry + DLT**: 3 attempts exponential backoff → `wms.inventory.alert.v1.DLT`. Null `eventId`/`payload`, or unmapped `skuCode` (see D3) → non-retryable → DLT + ops alert (never silently dropped).
- **Schema compatibility**: if wms bumps to `wms.inventory.alert.v2`, this consumer continues on v1 during the grace period; a separate task migrates (scm↔wms precedent).
- **Standalone-publish degradation (ADR-027 D8)**: without wms present the topic never arrives; demand-planning holds an empty suggestion list. No hard dependency.
- **NOT a published-events doc** — demand-planning's own emissions (if any, e.g. an intra-scm `scm.replenishment.suggestion.raised.v1`) are decided in BE-023/D5 and documented there, not here. This file is subscription-only.

### 2. wms-side cross-project consumer note (additive, 1 line)

`projects/wms-platform/specs/contracts/events/inventory-events.md` §7 (`inventory.low-stock-detected` consumer-expectations list) — append one bullet:
> - `scm-platform demand-planning-service` (cross-project, ADR-MONO-027): consumes for replenishment reorder-suggestion decisioning. Subscription contract: scm `replenishment-subscriptions.md`.

No schema change, no payload change. Same atomic PR (cross-project doc parity, per Cross-Project Changes rule).

## Out of Scope

- `demand-planning-service` architecture / data-model / API — TASK-SCM-BE-023.
- Any `apps/` code, Flyway, consumer wiring — TASK-SCM-BE-024.
- procurement DRAFT-PO entry point — TASK-SCM-BE-025.
- Any wms producer logic change — none (alert already emitted).

# Acceptance Criteria

- **AC-1** `replenishment-subscriptions.md` exists declaring the `wms.inventory.alert.v1` subscription, group `scm-demand-planning-v1`, consumed subset, eventId idempotency, retry/DLT, v2 grace, D8 degradation.
- **AC-2** The authoritative-schema pointer references wms `inventory-events.md` §7 (not duplicated as authoritative).
- **AC-3** wms `inventory-events.md` §7 gains exactly one additive consumer-note bullet; no other wms diff; schema byte-unchanged.
- **AC-4** Unmapped-SKU and null-envelope are documented as non-retryable → DLT + alert (fail-closed, not silent-drop).
- **AC-5** spec-only diff (no `apps/`, no migration). CI markdown path-filter applies.

# Related Specs

- [ADR-MONO-027](../../../../docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md) (D1 transport, D6 idempotency, D8 degradation)
- [`inventory-visibility-subscriptions.md`](../../specs/contracts/events/inventory-visibility-subscriptions.md) (sibling subscription doc — structure to mirror)
- [`scm-procurement-events.md`](../../specs/contracts/events/scm-procurement-events.md) (envelope/consumer-rules conventions to reuse)
- [rules/domains/scm.md](../../../../rules/domains/scm.md) S2 (idempotency keys), S5 (eventual consistency)
- [platform/event-driven-policy.md](../../../../platform/event-driven-policy.md) (consumer T8)

# Related Contracts

- [`projects/wms-platform/specs/contracts/events/inventory-events.md`](../../../wms-platform/specs/contracts/events/inventory-events.md) §7 — the consumed signal (authoritative on the wms side; +1 consumer note here).

# Edge Cases

- The `alert` topic carries `aggregateType=alert` and is debounced 1h per inventory row (wms side) — document that business-duplicate suppression still needs the open-suggestion guard (ADR-027 D6), not just the wms debounce.
- `payload` uses wms camelCase envelope (`eventId`/`eventType`/`occurredAt`) — note the shape the consumer DTO maps (mirror the `inventory-visibility-subscriptions.md` envelope note).
- warehouse vs location: the alert payload carries both `warehouseId`-equivalent (`locationId`/`locationCode`) and SKU — document which the reorder key uses (`skuCode` + warehouse) so BE-023 models the join consistently.

# Failure Scenarios

- If this contract is authored to make wms the authoritative duplicate of the schema → drift risk. It MUST reference wms §7 as authoritative and reproduce only the consumed subset.
- If the wms-side note edits the payload/schema instead of adding a consumer bullet → breaks the "zero wms producer change" invariant of ADR-027 D1.

# Notes

- 분석=Opus 4.8 / 구현 권장=Opus (cross-project contract design). spec-only, ~1 new file + 1-line wms edit.
- PR Separation: spec PR (this) precedes BE-023 spec PR and BE-024 impl PR.
