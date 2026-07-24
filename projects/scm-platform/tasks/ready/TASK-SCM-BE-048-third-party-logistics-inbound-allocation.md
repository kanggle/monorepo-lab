# TASK-SCM-BE-048 — 3PL inbound replenishment-target allocation (demand-planning owns the node-type choice)

**Status:** ready
**Type:** TASK-SCM-BE
**Depends on / 전제:** [ADR-MONO-055](../../../../docs/adr/ADR-MONO-055-order-to-node-allocation-ownership.md) **ACCEPTED** §D2/§D3 (inbound replenishment-target allocation is demand-planning's, widened to node type) · [TASK-SCM-BE-047](../done/TASK-SCM-BE-047-third-party-logistics-observed-stock.md) **done** (3PL stock is now observed read-only — the input this allocation reads) · [TASK-SCM-BE-046](../done/TASK-SCM-BE-046-third-party-logistics-node-factory.md) **done** (a `THIRD_PARTY_LOGISTICS` node is constructible). Context: [ADR-MONO-027](../../../../docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md) (demand-planning's reorder decision — the *un-retired* basis) · [ADR-MONO-050](../../../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §D4 (a 3PL is operated by its own WMS).
**후속 / blocks:** [TASK-SCM-BE-049](../backlog/TASK-SCM-BE-049-third-party-logistics-inbound-honour-sink.md) (honour/sink — records the 3PL-destined expectation; gated on this task done). **Does NOT** touch Surface B / outbound routing (ADR-055 §D5 — ADR-022 territory).

> **The allocation half, not the honour half.** ADR-055 §D2 finds that "honouring a 3PL inbound-expected" (054 §3's original BE-048) presupposes a prior decision: *a replenishment PO must first be **addressed** to a 3PL node*. Today it never is — [`ProcurementDraftPoClient`](../../apps/demand-planning-service/src/main/java/com/example/scmplatform/demandplanning/adapter/outbound/procurement/ProcurementDraftPoClient.java) hardcodes `destinationNodeType = "WMS_WAREHOUSE"`. This task builds the **allocation** (demand-planning decides a 3PL node can be a replenishment target); BE-049 builds the **honour/sink** (what happens to the resulting 3PL-destined expectation). Splitting them keeps each reviewable.

---

## Why (the gap this closes)

Stock reaches a `THIRD_PARTY_LOGISTICS` node the same way it reaches a wms warehouse: a replenishment PO is drafted to it. But demand-planning's reorder sweep can only ever target a wms warehouse today, for two reasons found by ADR-055 §5:

1. The below-reorder read-model the **batch** sweep consumes ([`InventoryVisibilityRestAdapter.findAllBelowReorderPoint`](../../apps/demand-planning-service/src/main/java/com/example/scmplatform/demandplanning/adapter/outbound/visibility/InventoryVisibilityRestAdapter.java)) exposes `sku/nodeId/availableQty/warehouseCode` — **no `nodeType`** — and (verify) does not include `THIRD_PARTY_LOGISTICS` nodes in the sweep at all.
2. `ReorderSuggestion` carries `warehouseId`/`warehouseCode` but **no node-type dimension**, and `ProcurementDraftPoClient` fills `destinationNodeType` with the hardcoded `WMS_WAREHOUSE` constant.

BE-047 now makes 3PL stock *observable*; this task lets that observation *drive a replenishment decision*. The reorder **policy** (point, quantity, supplier) is unchanged — only the **target vocabulary** widens from "wms warehouse" to "any observed node." Everything downstream of `ProcurementDraftPoClient` (the procurement PO `destination_node_type` column, the wire field, the producer emit-gate, the wms consumer gate) is **already** node-type-aware (ADR-055 §5) and needs no change here.

**Trigger = the batch sweep, NOT the alert path.** `wms.inventory.alert.v1` fires only for wms nodes (wms has no 3PL concept), so a 3PL node's low-stock is visible only through the IVS snapshot the batch path reads. `raiseFromAlert` stays wms-only and untouched; the wms↔scm alert loop (ADR-027) is not modified.

## Scope

**In scope (contract-first at each hop):**

1. **inventory-visibility read-model** — the below-reorder projection consumed by the sweep surfaces `nodeType`, and includes `THIRD_PARTY_LOGISTICS` nodes (currently WMS-only). Contract row in the canonical IVS API precedes code. Confirm the projection can include 3PL nodes **without destabilising the existing wms-node reorder cadence** (the sweep's current tenants) — e.g. 3PL nodes appear only when they have a reorder point / observed snapshot.
2. **`ReorderSuggestion`** — gains a node-type dimension alongside `warehouseId`/`warehouseCode`, populated by `raiseFromBatch` from the read-model's `nodeType`. `raiseFromAlert` (wms-only) keeps its existing `WMS_WAREHOUSE`-implied behaviour.
3. **`DraftPoCommand` / `ProcurementDraftPoPort`** — carry the node type from the suggestion.
4. **`ProcurementDraftPoClient`** — sources `destinationNodeType` from the command/suggestion instead of the hardcoded `NODE_TYPE_WMS_WAREHOUSE` constant. A 3PL-typed suggestion now drafts a PO with `destinationNodeType = THIRD_PARTY_LOGISTICS`.
5. **Tests** — unit: batch sweep raises a 3PL-typed suggestion for a below-reorder 3PL node; `ProcurementDraftPoClient` emits the suggestion's node type (not the constant); `raiseFromAlert` still yields WMS. Slice/IT as the touched layers require (Testcontainers IT is CI-authority on Windows).

**Out of scope:**

- **BE-049 / the honour/sink** — what the procurement producer *does* with a 3PL-destined expectation (route it away from wms to the scm sink) is BE-049. This task stops at *drafting a PO addressed to a 3PL node*; the PO producer-gate already silent-skips non-wms destinations today (ADR-054 §1.1 / ADR-055 §5), so **nothing regresses** if BE-049 has not landed — a 3PL-typed PO simply does not emit an `inbound-expected` toward wms (correct interim).
- **Surface B / outbound routing** (ADR-055 §D5) — customer-order fulfillment-node allocation is ADR-022 territory. Do not touch ecommerce/shipping/the logistics `FulfillmentRouter`.
- **The wms DLT gate** — stays (ADR-054 §D3). No widening.
- **The alert path** (`raiseFromAlert`, `wms.inventory.alert.v1`) — untouched.
- **External 3PL vendor read-API** — BE-047 already chose the observation push; no vendor adapter here.
- **A reorder *policy* differentiated by node type** (different lead time/supplier for a 3PL) — the mechanism widens; policy tuning is a later refinement, not this task.

## Acceptance Criteria

- [ ] The IVS below-reorder read-model surfaces `nodeType` and can include `THIRD_PARTY_LOGISTICS` nodes; contract row lands **before/with** the code; the existing wms-node sweep behaviour is unchanged (verified).
- [ ] A below-reorder-point `THIRD_PARTY_LOGISTICS` node (observed via BE-047) produces a `ReorderSuggestion` carrying node type `THIRD_PARTY_LOGISTICS` via the **batch** path.
- [ ] `ProcurementDraftPoClient` sends `destinationNodeType` from the suggestion; a 3PL-typed suggestion drafts a PO with `destinationNodeType = THIRD_PARTY_LOGISTICS` (the hardcoded constant is retired as the source of truth).
- [ ] `raiseFromAlert` (wms alert path) still yields a `WMS_WAREHOUSE`-targeted suggestion — no regression to the ADR-027 loop.
- [ ] No change to procurement PO persistence, the emit-gate, or the wms consumer gate (they are already node-type-aware); **no Flyway** unless the `ReorderSuggestion` node-type column genuinely requires one (additive nullable if so).
- [ ] **No** Surface-B / outbound / `FulfillmentRouter` / ecommerce change; **no** wms gate widening; **no** alert-path change.
- [ ] Build & Test + scm Integration CI lanes **GREEN** (CI authority for IT).

## Related Specs

- `projects/scm-platform/specs/services/demand-planning-service/architecture.md` — the batch sweep now targets any observed node type (was wms-warehouse-implied); the reorder decision (ADR-027) is widened, not changed.
- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md` / `data-model.md` — the below-reorder read-model exposes `nodeType`.
- `docs/adr/ADR-MONO-055-order-to-node-allocation-ownership.md` §D2/§D3; `docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md`.

## Related Contracts

- `projects/scm-platform/specs/contracts/http/inventory-visibility-api.md` (**canonical**) — the below-reorder / snapshot read surface gains `nodeType`. Contract first.
- `projects/scm-platform/specs/contracts/http/procurement-*` (the `from-suggestion` draft-PO surface already carries an optional `destinationNodeType` — confirm and document it is now populated from the suggestion, not always `WMS_WAREHOUSE`).
- No **event** contract change (the inbound-expected event's `destinationNodeType` field already exists — ADR-050; this task only changes which value the producer *can* set).

## Edge Cases

- **3PL node with a reorder point but no recent observation (STALE/UNREACHABLE)** — decide whether a stale 3PL node should raise a replenishment suggestion (replenishing against stale stock may over/under-order). Default: treat staleness as it is treated for wms nodes today; document the choice.
- **3PL node observed but with no reorder point configured** — must not raise a suggestion (no policy) — same as an unconfigured wms node.
- **Mixed sweep** — a single batch run yielding both wms- and 3PL-typed suggestions must address each PO to the correct node type (no cross-contamination of `destinationNodeType`).
- **Alert vs batch collision** — the same SKU low at both a wms node (alert) and a 3PL node (batch) must yield two independent, correctly-typed suggestions (dedup keys must include node identity, as they do today).
- **Backward compatibility** — an existing wms-only suggestion (no node type set) must default to `WMS_WAREHOUSE` behaviour (the pre-055 contract), so in-flight suggestions are unaffected.

## Failure Scenarios

- **A — The retired outbound claim resurrected.** If this task adds any *order-fulfillment* / self-vs-3PL customer-order routing to demand-planning, it has rebuilt the 052 §D2① claim ADR-054 §5 retired. This task is **replenishment-target** selection only (ADR-055 §1.5). Stop if it drifts toward order intake.
- **B — Alert-path contamination.** Widening `raiseFromAlert` (the wms alert loop) to 3PL is wrong — a 3PL emits no `wms.inventory.alert.v1`. Only the batch/snapshot path gains 3PL. If the alert path is touched, revert.
- **C — Downstream gate change.** If the procurement emit-gate or wms consumer gate is edited, the task overreached — they are already node-type-aware; the only source-of-truth change is `ProcurementDraftPoClient` sourcing the value. (Widening the wms gate would violate ADR-054 §D3.)
- **D — Honour/sink absorbed.** If this task also routes the 3PL-destined expectation to a sink or records an inbound-expectation, it swallowed BE-049. Draft-the-PO-to-a-3PL-node is the boundary; stop there.
- **E — Contract after code.** The IVS read-model `nodeType` row (and the draft-PO `destinationNodeType` documentation) must precede/accompany the code.

---

**Recommended models** (분석=Opus 4.8 / 구현 권장): cross-hop node-type threading (IVS read-model → `ReorderSuggestion` → `DraftPoCommand` → client) reusing existing sweep/suggestion machinery, no new service, downstream already ready → **Opus** for the read-model-inclusion design call (not destabilising the wms cadence) and the retired-claim boundary discipline; the mechanical threading is Sonnet-able once the read-model shape is decided. 구현은 ADR-055 ACCEPTED 이후 backlog → ready 승격 시.
