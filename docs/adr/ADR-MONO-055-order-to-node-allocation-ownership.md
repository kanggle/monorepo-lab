# ADR-MONO-055 — the order→node allocation decision has two owners, not none: inbound replenishment-target is demand-planning's; outbound fulfillment-node is ADR-022 §D3's deferred multi-warehouse routing

**Status:** ACCEPTED
**Date:** 2026-07-24
**History:** PROPOSED 2026-07-24 (this record) · ACCEPTED 2026-07-24 (owner exact-form instruction **"ADR-MONO-055 ACCEPTED"**). ACCEPT is a human gate — an agent may not accept its own ADR ([`platform/architecture-decision-rule.md` § The ACCEPTED Gate](../../platform/architecture-decision-rule.md)); author (agent) and acceptor (owner) were separate parties. The preceding bare affirmatives did not clear it. The PROPOSED record authorised no code; acceptance binds the **inbound half** (see § What acceptance binds).
**Decision driver:** Owner (2026-07-24) — *"allocation-owner ADR 탐색·작성 (BE-048 + Surface B를 함께 여는 결정)"*. [ADR-MONO-054](ADR-MONO-054-third-party-logistics-node-activation.md) §D5 deferred the 3PL **outbound** path on "a named missing owner" and named the open slot `TASK-MONO-XXX` (054 §3); its §7 said *"a separate ADR must decide 'who owns the order-to-node (self-vs-3PL) allocation decision?'"*. This is that ADR.
**Supersedes:** none. This ADR **does not supersede** [ADR-MONO-054](ADR-MONO-054-third-party-logistics-node-activation.md); it answers the question 054 §D5/§7 explicitly deferred. It **refines** 054 §D5's phrasing ("the outbound routing decision has no owner yet") via the amendment-section pattern (§D6 below) — re-measurement finds the owner it names as "missing" is not missing but **pre-existing and mis-swept**.
**Related:** [ADR-MONO-054](ADR-MONO-054-third-party-logistics-node-activation.md) (§D5 the deferral this ADR answers, §D3 the inbound honour, §D7 the deferred `ThirdPartyFulfillmentPort`), [ADR-MONO-022](ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) (§D3 *"Warehouse routing: v1 = single default warehouse (config). Multi-warehouse routing = v2"* — the outbound owner this ADR pins, §D6 the ecommerce-side ACL where it lives), [ADR-MONO-027](ADR-MONO-027-wms-scm-replenishment-loop.md) (demand-planning owns the **reorder** decision — the actual, un-retired basis for inbound allocation), [ADR-MONO-052](ADR-MONO-052-transport-context-map.md) §D2① (the *weakest allocation*, retired by 054 §5 for the **outbound** reading), [ADR-MONO-053](ADR-MONO-053-logistics-service-multimodal-fulfillment.md) §D5 (the analogy 054 re-measured), [ADR-MONO-050](ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §D4 (a 3PL warehouse is operated by the 3PL's own WMS — the boundary that makes the inbound **sink** a scm record, not a wms ASN).

> **Why an ADR, not just authoring BE-048.** 054 §D5 deferred Surface B on the statement *"the outbound routing decision has **no owner yet**"* and pointed at "ADR-022 territory" as where an order originates — but it stopped at pointing. Re-measuring that pointer (§5) finds two things 054's single "no owner yet" phrase obscured: (1) the outbound owner is **not missing** — [ADR-MONO-022](ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) §D3 already **owns** "warehouse routing" and deferred *multi-warehouse* routing to v2; the outbound self-vs-3PL decision is that deferred v2, generalised, not a greenfield owner to invent; and (2) the **inbound** replenishment-target decision — which BE-048 needs — was **swept into the same "unowned" bucket** even though it is `demand-planning-service`'s existing job (it already selects which node a replenishment PO targets). The retired 052 §D2① claim was about **outbound fulfillment routing**; retiring it correctly did not vacate the **inbound** decision, which was never demand-planning's *by analogy* but its *by construction*. Which decision has which owner, and on what basis one is buildable now while the other stays deferred, are `platform/architecture-decision-rule.md` decisions. This ADR makes them.

---

## 1. Context

### 1.1 What 054 §D5 deferred, and the framing trap in "no owner yet"

[ADR-MONO-054](ADR-MONO-054-third-party-logistics-node-activation.md) split the 3PL work into two surfaces (054 §1.3): **Surface A — inbound to 3PL** (restock our stock held at a 3PL — built by BE-046/BE-047, the node factory + read-only observation) and **Surface B — outbound from 3PL** (a customer order fulfilled by the 3PL's own WMS). It shipped Surface A and deferred Surface B (054 §D5) on:

> an **order-intake / allocation surface** that decides *node* at order time — which is **upstream of wms**, in the territory of [ADR-MONO-022](ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) … Building Surface B is a separate epic that must first answer *"who owns the order-to-node allocation decision?"*.

That deferral is correct in outcome but its single phrase — *"the outbound routing decision has **no owner yet**"* (054 §D6, INDEX row 66) — carries a trap for the reader who comes to write the answering ADR: it reads as **"one owner is missing; invent it."** Re-measurement (§5) shows the opposite on both counts — the outbound owner is **pre-existing** (ADR-022 §D3), and there is a **second, distinct** decision (inbound replenishment-target) that BE-048 actually needs and that already has an owner too. 054 §D5 named "the order-to-node allocation decision" in the singular; there are two.

### 1.2 The two "3PL node" decisions are structurally different, at different altitudes

"3PL" and "node allocation" name **two** decisions that share vocabulary and nothing else:

| | **Inbound replenishment-target** (BE-048 needs this) | **Outbound fulfillment-node** (Surface B needs this) |
|---|---|---|
| The question | *we are low at / want stock held at node X — is X a wms warehouse or a 3PL?* | *a customer order arrived — does our own warehouse fulfil it, or a 3PL?* |
| Flow originates in | scm `demand-planning-service` (the reorder sweep) | ecommerce `order-service` → `shipping-service` (a customer order) |
| Decision seam today | [`ProcurementDraftPoClient`](../../projects/scm-platform/apps/demand-planning-service/src/main/java/com/example/scmplatform/demandplanning/adapter/outbound/procurement/ProcurementDraftPoClient.java#L42) sets `destinationNodeType` — **hardcoded `WMS_WAREHOUSE`** | [ADR-MONO-022](ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) §D3 "Warehouse routing" — **hardcoded single default warehouse (config)**; the ACL is ecommerce-side (§D6) |
| Altitude | **inside scm** (demand-planning → procurement → wms/3PL) | **upstream of wms** (ecommerce ACL, cross-project) |
| Downstream readiness | column + wire + gates already node-type-aware (§5) | fixed to one warehouse; multi-warehouse "= v2" (022 §D3) |
| Buildable now? | **Yes** (§D2–§D4) — BE-047 observation is the enabler | **No** (§D5) — the routing *policy* is an unmade cross-project design |

They are not two views of one decision; they are two decisions made by two originators in two projects. A single "allocation owner" cannot hold both, because neither originator sees the other's flow — which is precisely why 052/053's attempt to put the **outbound** decision inside scm (demand-planning, then the logistics consumer) failed re-measurement in 054: scm does not originate outbound customer flows.

### 1.3 The principle this ADR applies: allocation belongs to the flow's originator

The decision "which node does this flow target?" can only be made where the flow is *born*, because that is the first — and often only — point that holds the flow's intent (a reorder policy; a customer's order + ship-to). Downstream, the intent has already been compressed into a fact:

- The **inbound** flow is born in demand-planning's reorder evaluation, which already picks a target node (a `warehouseCode`) when it raises a suggestion. The node-type dimension is a *widening* of a choice it already makes — not a new authority.
- The **outbound** flow is born in the ecommerce order + its fulfillment-intent ACL (022 §D6), which already picks a warehouse (022 §D3). Self-vs-3PL is a *generalisation* of a choice it already makes — not a new authority.

054 §D5 proved the contrapositive by construction: `outbound.shipping.confirmed` — the only feed into the logistics `FulfillmentRouter` — is emitted *after* a wms warehouse already shipped, so the router receives the fulfillment **fact**, never the **decision**; it can only rubber-stamp `SELF` ([`FulfillmentRouter.route()` returns `SELF` unconditionally](../../projects/scm-platform/apps/logistics-service/src/main/java/com/example/scmplatform/logistics/application/routing/FulfillmentRouter.java#L51-L53)). A post-fact consumer cannot host a pre-fact decision. The decision lives at origination.

### 1.4 Both owners already exist — nothing is invented

Applying §1.3 to the two decisions lands on **two pre-existing owners**:

- **Inbound → `demand-planning-service`.** [ADR-MONO-027](ADR-MONO-027-wms-scm-replenishment-loop.md) already gives it the reorder decision; it already emits a target node. This is the *un-retired* basis — 054 retired the 052 §D2① **outbound** analogy, not demand-planning's own **reorder** ownership (§1.5).
- **Outbound → [ADR-MONO-022](ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) §D3.** It already owns "warehouse routing" and explicitly deferred *multi-warehouse* routing to v2: *"v1 = single default warehouse (config). Multi-warehouse routing = v2."* Outbound self-vs-3PL is that deferred v2, generalised to node **type**, decided in the ecommerce-side ACL (022 §D6).

So this ADR does **not** appoint a new owner for either half. It **routes each decision to the originator that already holds it**, un-defers the inbound half (buildable), and pins the outbound half to its real, pre-existing home (deferred there, not here — §D5).

### 1.5 The distinction that 054's "no owner" phrase blurred (kept explicit)

054 §5 retired *"demand-planning owns the **self-vs-3PL fulfillment routing** decision"* — the 052 §D2① / 053 §D5 claim — because demand-planning has **no order-intake surface**. That retirement is correct and stands. It does **not** retire demand-planning's ownership of **which node a replenishment PO targets**, because that is not an *order-fulfillment* decision at all — it is the reorder decision demand-planning was built for (ADR-027). The two share the words "3PL" and "node"; they are otherwise unrelated:

- **Retired (outbound):** *"a customer's order — self warehouse or 3PL?"* — demand-planning never sees a customer order. Not its decision. Correctly withdrawn.
- **Not retired (inbound):** *"we are low at a 3PL node we stock — draft a replenishment PO to it?"* — demand-planning already drafts replenishment POs to nodes. Its decision by construction, merely widened to a node type it can now *see* (BE-047).

054 §D5's single "no owner yet" swept the second into the first's grave. This ADR exhumes it and names its (pre-existing) owner.

### 1.6 The honest tension, kept on the record (054 §1.4 pattern)

Two asymmetries this ADR does not launder:

1. **Inbound is buildable but its physical sink is thin.** demand-planning can *decide* to replenish a 3PL node, and scm can *record* that expectation (§D4) and later *observe* the stock arrive (BE-047) — but the actual "tell the 3PL to expect a delivery" is an external integration (the 3PL's own WMS, via 054 §D7's deferred `ThirdPartyFulfillmentPort`). Phase 2a's honour is therefore a **scm-internal expectation record + observation**, not an external ASN. That is enough to stop DLT-ing and to make the cross-node inbound thesis real; it is not a full 3PL onboarding. Stated, not hidden.
2. **Outbound has an owner but not a policy.** Pinning outbound to ADR-022 §D3 answers *who decides*; it does **not** answer *how* — the routing policy (by geography? stock? cost? SLA?) is a genuine cross-project design with ecommerce + wms + 3PL inputs, and nothing in the current code determines it. This ADR **declines to invent that policy** (§D7); it converts "no owner" into "owner named, policy is the ADR-022 amendment's job."

---

## 2. Decision

### D1 — The order→node allocation decision is two decisions; each is owned by its flow's originator

There is no single "allocation owner" because there is no single allocation decision. **Inbound replenishment-target** and **outbound fulfillment-node** are distinct decisions at distinct altitudes (§1.2), each owned by the service where its flow originates (§1.3). This ADR assigns each to its pre-existing originator (§1.4), un-defers the inbound half, and defers the outbound half to its real home. It appoints no new service and builds no new allocation platform.

### D2 — Inbound replenishment-target allocation → `demand-planning-service` (its reorder decision, widened to node type)

Ownership of "which node does a replenishment PO target, and is it a wms warehouse or a 3PL?" is assigned to `demand-planning-service`, as an extension of its existing reorder-target selection ([ADR-MONO-027](ADR-MONO-027-wms-scm-replenishment-loop.md)) — **not** as the retired 052 §D2① outbound-fulfillment claim (§1.5). Concretely: demand-planning's reorder sweep already chooses the node it suggests replenishing (a `warehouseCode`); this ADR widens that choice to carry the node **type**, so a `THIRD_PARTY_LOGISTICS` node — now visible through BE-047's read-only observation — can be a replenishment target when its observed stock falls below its reorder point. The reorder *policy* (point, quantity, supplier) is unchanged; only the target vocabulary widens from "wms warehouse" to "any observed node."

The trigger is the **batch** reorder path, not the alert path: `wms.inventory.alert.v1` fires only for wms nodes (wms has no 3PL concept), so a 3PL node's low-stock signal can only come from the IVS snapshot the batch sweep reads. This keeps the wms↔scm alert loop (ADR-027) untouched.

### D3 — The inbound seam: surface node type at each hop that already carries the node, source it at the one hop that hardcodes it

The inbound chain is already node-type-*aware* end to end except at its two ends (§5). The seam is three additive changes, no new service:

1. **inventory-visibility read-model** — the below-reorder-point projection the sweep consumes ([`InventoryVisibilityRestAdapter.findAllBelowReorderPoint`](../../projects/scm-platform/apps/demand-planning-service/src/main/java/com/example/scmplatform/demandplanning/adapter/outbound/visibility/InventoryVisibilityRestAdapter.java#L59-L71)) surfaces `nodeType` (today it exposes `sku/nodeId/availableQty/warehouseCode` only), and includes `THIRD_PARTY_LOGISTICS` nodes in the sweep.
2. **`ReorderSuggestion`** — gains a node-type dimension alongside its existing `warehouseId`/`warehouseCode` (today it has no node-type field), carried by both `raiseFromBatch` and (unchanged/wms-only) `raiseFromAlert`.
3. **`ProcurementDraftPoClient`** — sources `destinationNodeType` from the suggestion instead of the [hardcoded `NODE_TYPE_WMS_WAREHOUSE` constant](../../projects/scm-platform/apps/demand-planning-service/src/main/java/com/example/scmplatform/demandplanning/adapter/outbound/procurement/ProcurementDraftPoClient.java#L42).

Everything downstream — the procurement PO's [`destination_node_type` column](../../projects/scm-platform/apps/procurement-service/src/main/java/com/example/scmplatform/procurement/domain/po/PurchaseOrder.java#L100-L101) (built 3PL-ready), the wire field, the producer emit-gate, and the wms consumer gate — already checks the value and needs **no change**. The PO drafting/confirming path stays a pure pass-through of the decided node type (procurement stays FK-free / node-registry-free — the decision is made upstream, in the service that reads the node registry).

### D4 — The inbound honour sink: an scm-internal expectation against the 3PL node; the external 3PL-WMS notification stays deferred (054 §D7)

"Honouring" a 3PL-destined inbound expectation (054 §D3 — route it away from wms, don't widen the correct wms DLT gate) requires a **sink** in scm, because 054 §1.3 named "no 3PL inbound-expectation sink exists in scm" as a Surface-A gap. This ADR sizes it: the sink is a **lightweight expected-inbound record against the `THIRD_PARTY_LOGISTICS` node in `inventory-visibility-service`** — the context that already owns the node and its staleness. The physical receiving is the 3PL's own WMS ([ADR-MONO-050](ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §D4); we **record** the expectation and **observe** (BE-047) the stock arrive to reconcile it. The *external* notification of the 3PL's WMS (an ASN to 품고/ShipBob) is the deferred `ThirdPartyFulfillmentPort` work ([ADR-MONO-054](ADR-MONO-054-third-party-logistics-node-activation.md) §D7 — designed, not built) — it stays deferred; the inbound expectation record does **not** depend on it. This is what makes BE-048 buildable now without the full external 3PL integration.

### D5 — Outbound fulfillment-node allocation → ADR-022 §D3's deferred multi-warehouse routing (owner NAMED, build DEFERRED to an ADR-022 amendment)

Ownership of "does our own warehouse fulfil this customer order, or a 3PL?" is **not** unowned — it is [ADR-MONO-022](ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) §D3's "Warehouse routing," which chose *"single default warehouse (config)"* for v1 and deferred *"Multi-warehouse routing = v2."* The outbound self-vs-3PL decision **is** that deferred v2, generalised from "which of our warehouses" to "which node, of any type" — decided in the ecommerce-side fulfillment-integration ACL (022 §D6), the component that already holds the order + ship-to and already picks the warehouse.

This ADR **names that owner and stops there.** It does **not** build Surface B, because:

- The routing **policy** — how the ACL chooses among {default warehouse, other warehouses, 3PL nodes} for a given order — is a real cross-project design (geography, stock availability across nodes, cost, SLA) that nothing in the current codebase determines. Inventing it here would be fabrication.
- It is ecommerce + wms + 3PL territory, cross-project; [ADR-MONO-022](ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) is its home. The build lands as an **ADR-022 amendment** (022 §D3 "Multi-warehouse routing = v2" → "node-type-aware routing = v2/v3"), authored when the owner elects it, in 022's own status-transition history.

So Surface B stays deferred — but its deferral basis changes from 054 §D5's *"no owner"* to **"owner is ADR-022 §D3; its build is a scoped ADR-022 amendment awaiting a routing policy."** That is the advance this ADR makes on the outbound half.

### D6 — Refine 054 §D5 (amendment-section pattern, per 054's "Amending this ADR")

054's closing note mandates that *"any decision that gives Surface B an owner [go] through an amendment section … or a new ADR."* This is that new ADR; on its acceptance a forward-pointer is added to **054's** record. The refinement:

- **054 §D5 / §D6 — "the outbound routing decision has no owner yet."** Refined: the outbound owner is **[ADR-MONO-022](ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) §D3** (pre-existing, deferred as "multi-warehouse routing v2"), not a missing one to invent (§D5). Surface B remains **deferred**, now on a **named routing-policy prerequisite in ADR-022 territory**, not on a missing owner.
- **054 §D5's singular "the order-to-node allocation decision."** Refined: it is **two** decisions (§1.2); 054's deferral correctly covered the **outbound** one but its phrasing swept in the **inbound** replenishment-target decision, which is `demand-planning-service`'s existing job (§D2, §1.5) and is **un-deferred** by this ADR.

Nothing in 054's Surface-A decisions changes: §D2 (node factory), §D3 (route inbound away from wms), §D4 (read-only observation), §D7 (`ThirdPartyFulfillmentPort` designed-not-built) all stand. This ADR **consumes** 054 §D3 (it supplies the sink §D3/§D4 named) and **honours** 054 §D7 (the external notification stays deferred).

### D7 — What this ADR deliberately does NOT decide

- **The outbound routing policy** (§D5) — deferred to the ADR-022 amendment; not invented here.
- **The external 3PL execution/notification adapter** (`ThirdPartyFulfillmentPort`, 054 §D7) — stays deferred; the inbound sink (§D4) is scm-internal and does not need it.
- **Any change to the wms DLT gate** — 054 §D3 keeps it; the inbound routing stays producer-side (§D3).
- **Any change to the wms↔scm alert loop** (ADR-027) — inbound 3PL replenishment rides the **batch** sweep, not the alert path (§D2).
- **A new central allocation service / platform** — rejected (§A3); both owners pre-exist. Still a service inside scm for inbound; still ADR-022 territory for outbound; still no `tms-platform` (054 §D8).

---

## 3. Implementation plan

**This ADR is PROPOSED and authorises no code.** On ACCEPT, the following is authored into scm `backlog/` (promoted to `ready/` per the 053 §A4 gate — specs exist + this ADR ACCEPTED). Outbound work is **named, not authored** — it is an ADR-022 amendment, not an scm task.

| Task | Half | Scope | Gate |
|---|---|---|---|
| **TASK-SCM-BE-048** | Inbound (allocation) | Surface `nodeType` in the IVS below-reorder read-model + include 3PL nodes; add the node-type dimension to `ReorderSuggestion` (batch path); source `destinationNodeType` in `ProcurementDraftPoClient` from the suggestion (retire the hardcoded constant). Contract-first. | ADR-055 ACCEPTED |
| **TASK-SCM-BE-049** | Inbound (honour/sink) | The scm-internal expected-inbound record against a `THIRD_PARTY_LOGISTICS` node in `inventory-visibility` + producer-side routing of a 3PL-destined expectation to that sink (wms gate unchanged); reconcile against BE-047 observation. Contract-first. | BE-048 done |
| — | Outbound (owner) | **ADR-022 amendment** generalising §D3 "multi-warehouse routing v2" → node-type-aware (self-vs-3PL) routing in the ecommerce ACL, incl. the routing **policy**. | separate ADR-022 amendment, owner-elected |
| — | Outbound (execution) | `ThirdPartyFulfillmentPort` + 3PL execution adapter (054 §D7). | **blocked — outbound owner's build exists** |

Deliberately **not** scheduled: the outbound routing policy (D5/D7), any external 3PL adapter (054 §D7), any wms gate widening (054 §D3 keeps it), any alert-path change (D2).

*(BE-048's number is reused from 054 §3's placeholder, now re-scoped to the **allocation** half; the honour/sink becomes BE-049. 054's §3 listed BE-048 as "honour … producer-side routing" — this ADR finds that honour presupposes the allocation, so the allocation is BE-048 and the sink is BE-049. Final numbering is set at authoring against the then-current scm queue.)*

## 4. Alternatives considered

### A1 — One "allocation owner" for both halves (the 054-phrasing reading) — Rejected
The literal reading of 054 §D5's singular "the order-to-node allocation decision." Rejected: re-measurement (§5) shows two decisions at two altitudes with two originators (§1.2). A single owner cannot hold both because neither originator sees the other's flow — the exact reason 052/053's scm-side outbound owner failed. Forcing one owner would recreate that failure.

### A2 — Re-assign the outbound decision to demand-planning (resurrect 052 §D2①) — Rejected
Already retired by 054 §5 and not reopened: demand-planning has no order-intake surface. This ADR is careful **not** to let its inbound assignment (§D2) look like this — inbound replenishment-target is demand-planning's *reorder* decision (ADR-027), not an *order-fulfillment* decision (§1.5).

### A3 — A new central allocation service owning both halves — Rejected
Clean-looking single owner. Rejected: both decisions already have pre-existing owners (§1.4); a central service would **strip** ADR-022 §D3 of the warehouse-routing it owns and demand-planning of the reorder-target it owns, then re-consume the same order/reorder intent a second time downstream — reintroducing the post-fact-vs-decision problem (§1.3) for the outbound half. Over-engineered for a portfolio with one 3PL relationship; violates "the decision lives where the flow is born."

### A4 — Assign inbound allocation to procurement-service instead of demand-planning — Rejected
Procurement's PO already carries `destination_node_type` and the emit gate, so it *looks* like the seam. Rejected: procurement is deliberately FK-free with **no node registry** — it cannot *decide* self-vs-3PL, only *carry* a decided value ([`PurchaseOrder.isWmsWarehouseDestination`](../../projects/scm-platform/apps/procurement-service/src/main/java/com/example/scmplatform/procurement/domain/po/PurchaseOrder.java#L352-L356) checks a value, it does not choose it). The service that *reads the node registry and picks the target* is demand-planning (via the IVS read-model). Procurement stays the pass-through carrier (§D3).

### A5 — Make THIS ADR decide the outbound routing policy and amend ADR-022 in place — Rejected
Tempting to "finish" outbound here. Rejected: (a) the routing policy is a cross-project design needing ecommerce + wms + 3PL inputs that no current code determines — inventing it would be fabrication (§1.6); (b) ADR-022 is ACCEPTED and its home is its own status-transition history — the outbound build belongs in an ADR-022 amendment, not a rewrite from an scm-centric ADR. This ADR pins the owner and stops (§D5), the same design-now/decide-later discipline 054 §D5 used.

### A6 — Defer both halves until the outbound policy is ready — Rejected
Treats the two halves as one (the A1 error). Rejected: the inbound half is genuinely reachable now — BE-047's observation feed is exactly the input a 3PL replenishment target needs, and the downstream is already node-type-aware (§5). Deferring it too would forfeit the buildable half to protect against the blocked one — 054 §A4 rejected the same move for Surface A.

## 5. Verification (re-measured at authoring, 2026-07-24 — recount, don't inherit)

| Claim | Check | Result |
|---|---|---|
| ADR-022 §D3 owns warehouse routing and deferred multi-warehouse to v2 | 022 §D3 (line 86): *"Warehouse routing: v1 = single default warehouse (config). Multi-warehouse routing = v2."*; ACL is ecommerce-side (022 §D6) | **confirmed — outbound owner pre-exists, is not "missing"** |
| demand-planning already selects the replenishment target node | `ReorderSuggestion` carries `warehouseId`/`warehouseCode`, chosen at `raiseFromBatch`/`raiseFromAlert`; approve → `DraftPoCommand(warehouseCode, …)` | **confirmed — inbound target selection is its existing job** |
| The inbound seam hardcodes node type at exactly one place | [`ProcurementDraftPoClient`](../../projects/scm-platform/apps/demand-planning-service/src/main/java/com/example/scmplatform/demandplanning/adapter/outbound/procurement/ProcurementDraftPoClient.java#L42) `NODE_TYPE_WMS_WAREHOUSE = "WMS_WAREHOUSE"`, set unconditionally when a destination is emitted | **confirmed** |
| The below-reorder read-model omits node type | [`InventoryVisibilityRestAdapter.findAllBelowReorderPoint`](../../projects/scm-platform/apps/demand-planning-service/src/main/java/com/example/scmplatform/demandplanning/adapter/outbound/visibility/InventoryVisibilityRestAdapter.java#L59-L71) reads `sku/nodeId/availableQty/warehouseCode`, no `nodeType` | **confirmed — the one surfacing gap for BE-048** |
| The procurement/wms downstream is already 3PL-ready | PO [`destination_node_type` column](../../projects/scm-platform/apps/procurement-service/src/main/java/com/example/scmplatform/procurement/domain/po/PurchaseOrder.java#L100-L101) + wire field + producer gate + [wms consumer gate](../../projects/wms-platform/apps/inbound-service/src/main/java/com/wms/inbound/application/service/CreateScmInboundExpectationService.java#L82-L86) all check the value | **confirmed — no downstream change needed** |
| No 3PL inbound-expectation sink exists in scm | inventory-visibility domain = `node`/`snapshot`/`staleness`/`dedupe` only; no expectation model | **confirmed — the sink is D4's new lightweight record** |
| The outbound decision cannot live in the logistics consumer | [`FulfillmentRouter.route()`](../../projects/scm-platform/apps/logistics-service/src/main/java/com/example/scmplatform/logistics/application/routing/FulfillmentRouter.java#L51-L53) returns `SELF` unconditionally; its only feed is the self-fulfillment-only `outbound.shipping.confirmed` | **confirmed (054 §5) — post-fact seam, not a decision point** |
| demand-planning has no order-intake (outbound claim stays retired) | no order/allocation/self-vs-3PL-fulfillment surface in `demand-planning-service/**` | **confirmed — §D2 is the reorder decision, not the retired outbound one** |
| The inbound trigger is the batch sweep, not the alert path | `wms.inventory.alert.v1` fires only for wms nodes; a 3PL node's low-stock is visible only via the IVS snapshot the batch path reads | **confirmed** |

Per repo practice, a prior count/claim is a hypothesis, not a source — including 054 §D5's own "no owner yet." Every row was re-derived from current code (three parallel Explore sweeps, 2026-07-24), not carried from 052/053/054.

## 6. Consequences

**Positive**
- The order→node allocation question is **answered**, not re-deferred: inbound gets a buildable owner (unblocking BE-048/049), outbound gets a **named, pre-existing** owner (ADR-022 §D3) in place of 054's "no owner."
- The inbound half reuses BE-047's observation as its decision input — the read-only 3PL stock now *drives* a replenishment decision, closing the loop the node activation opened.
- No new service, no new platform, no gate widening; the fix is three additive changes on a chain already built node-type-aware (§5).
- 054's "둘 다" promise is honoured as far as re-measurement allows: the inbound half is delivered; the outbound half is handed to its true home with the prerequisite (a routing policy) named, not faked.

**Negative**
- "둘 다" is **still not fully delivered**: outbound Surface B remains deferred — its build is an ADR-022 amendment awaiting a routing policy this ADR declines to invent (§1.6, §D5). The advance is ownership, not execution.
- The inbound honour (§D4) is an scm-internal expectation + observation, **not** an external ASN to the 3PL's WMS — the full 3PL onboarding (`ThirdPartyFulfillmentPort`, 054 §D7) stays deferred. A watcher must not read "BE-048/049 done" as "3PL inbound fully integrated."
- One more ADR (022 amendment) now sits on the outbound critical path — the chain lengthens before Surface B is buildable. Deliberate: the alternative (A5) fabricates a policy.

**Neutral**
- No `domain`/`traits` change to any `PROJECT.md`; no contract version moves in this ADR (BE-048/049 move them, contract-first). ADR-022 is referenced, not edited (its amendment is a later, owner-elected act).

## 7. Outstanding follow-ups

- **ACCEPT gate (human).** ✅ **CLEARED** 2026-07-24 (owner exact-form "ADR-MONO-055 ACCEPTED"; author ≠ acceptor, no self-ACCEPT). On-ACCEPT actions taken with this record: (a) TASK-SCM-BE-048 (+049) authored into scm `backlog/`; (b) forward-pointer added to **ADR-054's status record** noting §D5's "no owner" is refined here to "ADR-022 §D3, two decisions."
- **The outbound ADR-022 amendment (D5).** Before any outbound 3PL fulfillment is built, an ADR-022 amendment must generalise §D3 "multi-warehouse routing v2" to node-type-aware routing **and decide the routing policy** (geography/stock/cost/SLA). Owner-elected; cross-project (ecommerce + wms + 3PL).
- **3PL vendor terms.** 품고/ShipBob read-API + (later) ASN-ingest availability, to be verified at BE-049 / the ADR-022 amendment against live docs.
- **`ReorderSuggestion` node-type source.** BE-048 must confirm the IVS below-reorder projection can be extended to include 3PL nodes without destabilising the wms-node reorder cadence (the batch sweep's existing tenants).

## 8. Status history

| Date | Status | Note |
|---|---|---|
| 2026-07-24 | PROPOSED | Authored in answer to the owner's 2026-07-24 "allocation-owner ADR 탐색·작성" instruction and 054 §D5/§7's explicit deferral. Authorises no code. Re-measures 054 §D5's "no owner yet" (§5) and finds **two** decisions with **two pre-existing** owners: inbound → demand-planning (ADR-027), outbound → ADR-022 §D3. |
| 2026-07-24 | ACCEPTED | Flipped on the owner's exact-form instruction **"ADR-MONO-055 ACCEPTED"**. Author and acceptor were separate parties — no self-ACCEPT. Binds the inbound half (D2/D3/D4) + D6; authors TASK-SCM-BE-048 (+049). Surface B (D5) stays deferred to the ADR-022 amendment. |

**ACCEPT gate — cleared, not bypassed.** The owner's preceding bare affirmatives did **not** accept this ADR — [`platform/architecture-decision-rule.md` § The ACCEPTED Gate](../../platform/architecture-decision-rule.md) excludes bare affirmatives from the ACCEPT gate even when only one ADR is under discussion, and the authoring agent held the gate rather than laundering an affirmative into acceptance (the same bite 052/053/054 record). Acceptance came as a **separate, exact-form instruction naming this ADR** ("ADR-MONO-055 ACCEPTED"), issued by the owner — a different party from the authoring agent. The gate's purpose is attributability; it held.

**What acceptance binds.** Acceptance authorises the **inbound** half only: **D2** (demand-planning owns replenishment-target allocation, widened to node type), **D3** (the three-hop seam surfacing/sourcing node type), **D4** (the scm-internal 3PL inbound-expectation sink; external notification stays deferred), and the authoring of **TASK-SCM-BE-048 (+049)** into scm `backlog/`. It binds **D6** — the 054 §D5 refinement + the forward-pointer added to 054's amendment/status record on this acceptance. Explicitly **not** bound: **D5's outbound build** — Surface B stays **deferred**, its owner named as ADR-022 §D3 and its build routed to a separate, owner-elected ADR-022 amendment; acceptance does **not** authorise building Surface B, does **not** decide the outbound routing policy, and does **not** reopen 054 §D7 (the external `ThirdPartyFulfillmentPort` stays deferred). **D7** enumerates what stays out.

**Amending this ADR.** What is authorised is the text as read. Later changes — including the ADR-022 amendment that gives Surface B its routing policy — go through an amendment section or a new ADR, not an in-place rewrite of §1–§6.

---

분석=Opus 4.8 / 구현 권장=Opus (BE-048 inbound allocation seam = cross-hop node-type threading through demand-planning → procurement, read-model + aggregate + client change; BE-049 sink = new expectation model — complex domain work per CLAUDE.md model-routing). 구현은 ADR ACCEPTED 이후.
