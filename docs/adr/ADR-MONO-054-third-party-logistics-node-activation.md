# ADR-MONO-054 — activating `THIRD_PARTY_LOGISTICS`: the 3PL node is observed and its inbound half is honoured; the outbound routing decision still has no owner

**Status:** PROPOSED
**Date:** 2026-07-24
**History:** PROPOSED 2026-07-24 (this record). ACCEPT is a human gate — an agent may not accept its own ADR; `PROPOSED → ACCEPTED` requires explicit, exact-form human intent per [`platform/architecture-decision-rule.md`](../../platform/architecture-decision-rule.md). Authorises **no code**.
**Decision driver:** Owner (2026-07-24) — *"Phase 2 3PL 게이트 열자 … D8-3 충족 근거 + THIRD_PARTY_LOGISTICS 활성화 설계"*. The owner elects to fire [ADR-MONO-052](ADR-MONO-052-transport-context-map.md) §D8-3 (a 3PL destination honoured rather than DLT'd) — the same legitimate override [ADR-MONO-053](ADR-MONO-053-logistics-service-multimodal-fulfillment.md) used to fire §D8-2 — and asks for the activation design of the `THIRD_PARTY_LOGISTICS` inventory node.
**Supersedes:** none. Like 053 to 052, this ADR **does not supersede** [ADR-MONO-053](ADR-MONO-053-logistics-service-multimodal-fulfillment.md); it is 053 §D5/§D7 producing *Phase-2 work* on the D8-3 firing. It **corrects** two 053 claims that re-measurement contradicts (§D6 below) via the amendment-section pattern 053 §7 itself mandates — not an in-place rewrite of 053 §1–§6.
**Related:** [ADR-MONO-053](ADR-MONO-053-logistics-service-multimodal-fulfillment.md) (§D4 the FulfillmentRouter seam, §D5 the 3PL path sketch this ADR builds/corrects, §D7 phasing, "re-measure D2① before Phase 2, not inherit"), [ADR-MONO-052](ADR-MONO-052-transport-context-map.md) (§D1 the custody line, §D2① the *weakest allocation*, §D8-3 the fired trigger), [ADR-MONO-050](ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §D4 (a 3PL warehouse is operated by the 3PL's own WMS — wms rejects its receiving; the DLT gate this ADR reads as *correct boundary enforcement*, not a limitation to remove), [ADR-MONO-027](ADR-MONO-027-wms-scm-replenishment-loop.md) (demand-planning owns the *reorder* decision — the analogy 052 §D2① rests on, and that §5 below finds does **not** stretch to fulfillment routing), [ADR-MONO-022](ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) (where an *order* actually originates — the missing upstream this ADR names).

> **Why an ADR, not just Phase-2 tasks.** 053 §D5 *sketched* the 3PL path (a `ThirdPartyFulfillmentPort`, a node factory, a demand-planning routing decision) and explicitly deferred the hard part: *"Phase 2 must re-measure [the demand-planning ownership] against the then-current demand-planning design before building, not inherit it."* Re-measuring it (§5) does not confirm the sketch — it **breaks** it in two places: the `outbound.shipping.confirmed` seam that feeds the logistics `FulfillmentRouter` is a **self-fulfillment-only** signal (a 3PL order never emits it), and `demand-planning-service` is **replenishment-only** (it has no order, no allocation, no self-vs-3PL decision to own). That means "둘 다" as 053 §D4 promised it — *the 3PL branch slots in additively at the FulfillmentRouter* — is **not** reachable for the outbound path. Which half of D8-3 is buildable, and on what basis the other half is deferred, are `platform/architecture-decision-rule.md` decisions. This ADR makes them and keeps the correction on the record rather than discovering it silently during implementation.

---

## 1. Context

### 1.1 What D8-3 literally names, and where it lives in code

[ADR-MONO-052](ADR-MONO-052-transport-context-map.md) §D8 item 3 fires the transport work on:

> **a 3PL destination that must be honoured** rather than DLT'd — today's rejection is correct precisely because no adapter exists (ADR-050 §5 says so).

The word is **destination**, and its referent is concrete: [`CreateScmInboundExpectationService`](../../projects/wms-platform/apps/inbound-service/src/main/java/com/wms/inbound/application/service/CreateScmInboundExpectationService.java#L81-L86) — the wms consumer that turns an scm confirmed-PO into a wms inbound expectation (ASN) — carries a whitelist gate:

```java
// D4 — defensive node-type gate (scm filters 3PL producer-side; reject if one arrives).
if (!NODE_TYPE_WMS_WAREHOUSE.equals(command.destinationNodeType())) {
    throw new InboundExpectationRejectedException(
        "unsupported destinationNodeType=" + command.destinationNodeType()
        + " (v1 accepts only WMS_WAREHOUSE); po=" + command.poNumber());
}
```

Anything not `WMS_WAREHOUSE` is rejected → DLT. A replenishment PO addressed to a 3PL warehouse — "restock our stock held at 품고 / ShipBob" — is refused today. That refusal is **inbound**: goods flowing *to* a 3PL node.

### 1.2 The `THIRD_PARTY_LOGISTICS` node exists at every layer except the one that constructs it

The node type is declared and constrained end-to-end, and constructed nowhere:

| Layer | State |
|---|---|
| Domain enum | [`NodeType.THIRD_PARTY_LOGISTICS`](../../projects/scm-platform/apps/inventory-visibility-service/src/main/java/com/example/scmplatform/inventoryvisibility/domain/node/NodeType.java#L13) — present; javadoc: *"registered but ha[s] no active event adapters in v1"* |
| JPA enum | [`InventoryNodeJpaEntity.NodeTypeJpa`](../../projects/scm-platform/apps/inventory-visibility-service/src/main/java/com/example/scmplatform/inventoryvisibility/adapter/outbound/persistence/jpa/InventoryNodeJpaEntity.java#L60) — includes it |
| DB CHECK | `ck_inventory_nodes_type CHECK (node_type IN ('WMS_WAREHOUSE','SUPPLIER','THIRD_PARTY_LOGISTICS','IN_TRANSIT'))` (V1__init.sql:20) |
| **Factory** | [`InventoryNode`](../../projects/scm-platform/apps/inventory-visibility-service/src/main/java/com/example/scmplatform/inventoryvisibility/domain/node/InventoryNode.java#L60) exposes **only** `autoRegisterWmsWarehouse(...)` — **no** `THIRD_PARTY_LOGISTICS` constructor path |

Activating the node is therefore a small, contained addition — a factory method plus its registration trigger — **not** a schema change. The enum value and its CHECK are already paid for.

### 1.3 There are two 3PL surfaces, and 052/053 blur them

A 3PL touches the system in two structurally different places:

| | **Surface A — inbound to 3PL** | **Surface B — outbound from 3PL** |
|---|---|---|
| What happens | replenishment stock is sent *to* a 3PL warehouse for storage | a customer order is *fulfilled by* the 3PL (its WMS picks/packs/ships) |
| Today | `CreateScmInboundExpectationService` **DLTs** it (§1.1) | never reached — no order-routing feed exists |
| Trigger text | **this is what D8-3 literally names** ("a 3PL *destination*") | 053 §D5's `ThirdPartyFulfillmentPort` / FulfillmentRouter branch |
| Node need | a `THIRD_PARTY_LOGISTICS` node, **observed read-only** | the same node, **plus** an execution adapter **plus** an allocation decision |
| Buildable now? | **Yes** — §D2/§D3 | **No** — §D5 (blocked on a missing owner + a self-only seam) |

053 §D5 folded both into one bullet list. Separating them is the central act of this ADR: Surface A is the genuinely-reachable D8-3, Surface B is where the "둘 다" thesis is not yet reachable and must not be faked.

### 1.4 The honest tension, kept on the record (053 §1.4 pattern)

053 recorded that the carrier path alone already exercises the full integration-heavy surface, and that *"the 3PL path is where the cross-node thesis is actually proven, at a materially higher cost."* Re-measurement sharpens that: the cross-node thesis is proven by **Surface A** (observe stock across own + 3PL nodes — the unifying act `rules/domains/scm.md:24` assigns to scm), and Surface A is cheap. The expensive, still-unowned part is **Surface B's routing decision** — and 053 already flagged its basis (052 §D2①) as *"the weakest allocation … must be re-measured, not inherited."* This ADR does the re-measurement (§5) and reports that it **fails**. That is not laundered into a task; it is a decision (§D5) to defer Surface B on a named, missing prerequisite.

---

## 2. Decision

### D1 — Fire D8-3; scope Phase 2 to the genuinely-reachable half

The D8-3 tripwire is fired on owner election (the same override 052 §D8 exists to enable, exercised exactly as 053 exercised it for D8-2). **Phase 2a** — this ADR's buildable scope — is: activate the `THIRD_PARTY_LOGISTICS` node (D2), observe 3PL stock read-only (D4), and honour (stop DLT-ing) an inbound expectation addressed to a 3PL node (D3). **Surface B** (outbound 3PL fulfillment routing) is **Phase 2b**, deferred on a named blocker (D5). No new platform; still a service inside scm (D8).

### D2 — Activate the node: a `THIRD_PARTY_LOGISTICS` factory in `inventory-visibility-service`

Add the missing constructor path to the [`InventoryNode`](../../projects/scm-platform/apps/inventory-visibility-service/src/main/java/com/example/scmplatform/inventoryvisibility/domain/node/InventoryNode.java) aggregate — a `registerThirdPartyLogistics(...)` factory, sibling to `autoRegisterWmsWarehouse`, producing a `NodeType.THIRD_PARTY_LOGISTICS` node in `ACTIVE` status. Unlike a wms warehouse (auto-registered on first mutation event), a 3PL node is **explicitly registered** — a 3PL relationship is an onboarding fact, not an event side-effect. Registration is operator/config-driven (a small internal endpoint or seed), because there is no upstream event stream that births a 3PL node the way `wms.inventory.*` births a warehouse node. The enum, JPA mapping, and CHECK constraint already accept it (§1.2), so this is **no Flyway change** — a factory, a registration use case, and its wiring only.

### D3 — Honour, don't DLT: route a 3PL-destined inbound expectation away from wms, not *into* it

The wms whitelist gate (§1.1) is **correct and stays** — wms does **not** operate the 3PL's WMS, so it must not create a wms ASN for a 3PL destination ([ADR-MONO-050](ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §D4; the custody line of [ADR-MONO-052](ADR-MONO-052-transport-context-map.md) §D1). "Honouring" D8-3 does **not** mean widening that gate to accept `THIRD_PARTY_LOGISTICS`; it means a 3PL-destined expectation is **routed to a different handler before it reaches the wms consumer** — recorded as an expectation against the 3PL node in the transport/visibility context (scm), where 3PL execution belongs (052 §D2③). wms keeps refusing; its refusal is now **enforcement of a boundary** rather than a rejected feature. The producer-side filter the gate's own comment references (*"scm filters 3PL producer-side"*) is where the routing decision lands: scm addresses a 3PL PO's inbound-expected to the 3PL path, and only `WMS_WAREHOUSE` destinations are published toward wms.

### D4 — 3PL stock is **observed read-only**, never operated

Stock held at a 3PL node is **observed** through `inventory-visibility-service` — a `THIRD_PARTY_LOGISTICS` node with the same staleness model (`FRESH`/`STALE`/`UNREACHABLE`) the service already applies to wms nodes — and **never mutated by us**. We do not pick, pack, adjust, or transfer at a 3PL; its own WMS is authoritative for its four walls exactly as wms is for ours ([ADR-MONO-050](ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §D4). Observation is fed by the 3PL adapter's read APIs (or periodic snapshots) in Phase 2a's minimal form; the write/execute side is Surface B (D5/D7). This read-only stance is what makes the cross-node thesis real and cheap: scm *sees* stock across own + 3PL nodes and can *route* against that view — the capability `rules/domains/scm.md:24` assigns to scm and wms structurally cannot hold.

### D5 — The outbound routing decision (Surface B) is deferred on a **named, missing owner** — 052 §D2① fails re-measurement

053 §D5 assigned "own warehouse vs 3PL, and which 3PL" to `demand-planning-service` by analogy to 052 §D2① — and required that analogy be re-measured before building. **Re-measured (§5), it fails.** `demand-planning-service` is **replenishment-only**: it consumes `wms.inventory.alert.v1` (low-stock), evaluates a reorder policy, and raises a `ReorderSuggestion` that becomes a **supplier draft PO** ([`EvaluateReorderUseCase`](../../projects/scm-platform/apps/demand-planning-service/src/main/java/com/example/scmplatform/demandplanning/application/usecase/EvaluateReorderUseCase.java)). It has **no order-intake surface, no allocation logic, no inter-facility redistribution, and no self-vs-3PL concept** — its only decision is *"we are low, buy more from a supplier."* Giving it the outbound fulfillment-routing decision would not be *inheriting* an allocation; it would be *building an order-allocation service inside a replenishment service*, on an analogy that has now been checked and does not hold.

Compounding it, the seam is the wrong shape for the decision: `outbound.shipping.confirmed` means *goods already left a wms warehouse* — it is emitted **only for self-fulfillment**, and a 3PL order never produces it (the 3PL's WMS ships it; wms never sees it). So the logistics `FulfillmentRouter` (fed by that seam) receives, by construction, only already-self-fulfilled shipments — it can decide nothing about self-vs-3PL, because that decision was already made upstream, before wms was asked to pick.

Therefore Surface B is **deferred** with its true prerequisite named: an **order-intake / allocation surface** that decides *node* at order time — which is **upstream of wms**, in the territory of [ADR-MONO-022](ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) (where an order actually originates), **not** in demand-planning and **not** in the logistics consumer. Building Surface B is a separate epic that must first answer *"who owns the order-to-node allocation decision?"* — this ADR does not answer it, and explicitly declines to fake an owner.

### D6 — Correct two ADR-053 claims (amendment-section pattern, per 053 §7)

Re-measurement contradicts two statements in the (ACCEPTED) 053. Per 053 §7 (*"Should this ADR change 053's substance … that goes through an amendment section, not an in-place edit"*), these corrections are recorded **here** and a forward-pointer is added to 053's amendment section on this ADR's acceptance:

- **053 §D4** — *"the 3PL branch (D5) slots in during Phase 2 without reworking the dispatch path … the single most important structural choice … makes 둘 다 an additive Phase 2, not a Phase-1 rewrite."* — **Holds for Surface A** (node + observation attach additively). **Does not hold for Surface B**: the logistics `FulfillmentRouter` cannot be the place the self-vs-3PL decision is made, because its only input (`outbound.shipping.confirmed`) already encodes self-fulfillment. The inert `FulfillmentMode.THIRD_PARTY_LOGISTICS` arm ([`FulfillmentRouter`](../../projects/scm-platform/apps/logistics-service/src/main/java/com/example/scmplatform/logistics/application/routing/FulfillmentRouter.java#L34-L37)) stays inert not "until Phase 2 wires it" but **until an upstream feed that carries un-fulfilled orders exists** — which the current seam is not.
- **053 §D5** — the demand-planning routing ownership: **withdrawn as inherited**, per its own re-measure mandate (§5).

Nothing else in 053 changes; §D1–§D3 (bootstrap, dispatch port, CarrierRouter), §D8 (wms TMS retirement, already landed), and §D9 (no `tms-platform`) stand.

### D7 — The 3PL execution adapter (`ThirdPartyFulfillmentPort`) is designed, not built

When Surface B's upstream owner exists (D5), 3PL *execution* is a `ThirdPartyFulfillmentPort` + a 3PL adapter (품고 / ShipBob …) owned by `logistics-service` (052 §D2③) — **not** a `ShipmentDispatchPort` adapter (a 3PL fulfills a whole order; it is not a last-mile carrier — [`external-integrations.md`](../../projects/scm-platform/specs/services/logistics-service/external-integrations.md#L333-L341) § Not In Phase 1 already draws this line). It carries its own integration-heavy treatment (I1–I9), like the carrier adapters. This ADR **specifies its shape and defers its build** — the same design-now/build-later stance 053 §D5 took, now with the blocker made explicit.

### D8 — Still a service inside scm; still no `tms-platform`

052 §D6 / 053 §D9 stand, untouched. The 3PL capability is held by scm's existing services (`inventory-visibility-service` for observation, `logistics-service` for execution); `tms` stays out of `rules/taxonomy.md`.

---

## 3. Implementation plan

**This ADR is PROPOSED and authorises no code.** On ACCEPT, the following Phase-2a task series is authored into scm `backlog/` (promoted to `ready/` only when its specs exist and this ADR is ACCEPTED — the 053 §A4 gate). Surface B tasks are **named but not authored** — they are blocked on D5's missing owner, not merely unscheduled.

| Task | Surface | Scope | Gate |
|---|---|---|---|
| **TASK-SCM-BE-046** | A | `inventory-visibility` spec update + `THIRD_PARTY_LOGISTICS` node **factory** (`registerThirdPartyLogistics`) + explicit registration use case/endpoint (no Flyway) | ADR-054 ACCEPTED |
| TASK-SCM-BE-047 | A | 3PL **read-only observation** — node staleness + snapshot ingestion for a `THIRD_PARTY_LOGISTICS` node | BE-046 done |
| TASK-SCM-BE-048 | A | **Honour** a 3PL-destined inbound-expected — scm producer-side routing so a 3PL PO's expectation goes to the 3PL path, not the wms consumer (wms gate unchanged); contract-first | BE-046 done |
| TASK-SCM-BE-04x | B | `ThirdPartyFulfillmentPort` + 3PL execution adapter | **blocked — D5 owner exists** |
| TASK-MONO-XXX | B | order-to-node **allocation owner** (the ADR-022-territory epic D5 names) | separate ADR |

Deliberately **not** scheduled: widening the wms whitelist gate (D3 keeps it), any demand-planning routing code (D5), any outbound 3PL execution (D7), and Surface B before its owner is decided.

---

## 4. Alternatives considered

### A1 — Widen the wms gate to accept `THIRD_PARTY_LOGISTICS` (Rejected)
The literal-looking fix for "stop DLT-ing 3PL destinations." Rejected: wms does not operate the 3PL's WMS, so a wms ASN for a 3PL destination would be a **lie the receiving flow can never satisfy** ([ADR-MONO-050](ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §D4; custody line, 052 §D1). Honouring D8-3 is **routing away from wms** (D3), not teaching wms a context it was deliberately built without. The gate's rejection is the boundary working.

### A2 — Inherit 052 §D2① — demand-planning owns the self-vs-3PL routing (Rejected)
The path 053 §D5 sketched, on the condition it be re-measured. Re-measured (§5), demand-planning is replenishment-only — no order, no allocation. Adopting this would build an order-allocation surface inside a reorder service on an analogy that failed its own check. 053 itself named this *"the weakest allocation … re-measure, not inherit"*; this ADR honours that instruction by declining it.

### A3 — Build the outbound FulfillmentRouter 3PL branch now, inside `logistics-service` (Rejected)
The reading 053 §D4 invites. Rejected structurally: the `FulfillmentRouter`'s only input is `outbound.shipping.confirmed`, which is emitted **only** when a wms warehouse already shipped — a self-fulfillment signal. A 3PL order never arrives there, so the branch would be unreachable code decorated as a decision point. The self-vs-3PL decision belongs **upstream of wms**, at order/allocation time (D5).

### A4 — Defer all of D8-3 until Surface B has an owner (Rejected)
Treats the two surfaces as one. Rejected: Surface A (node activation + read-only cross-node observation + inbound honouring) is genuinely reachable, cheap, and is *precisely* where the cross-node thesis 052/053 invoke is proven — scm seeing stock across own + 3PL nodes. Deferring it too would forfeit the demonstrable half to protect against the blocked half. The owner elected the gate; A2/A3's over-reach is the thing to refuse, not the whole trigger.

### A5 — Amend ADR-053 in place instead of a new ADR (Rejected)
053 is ACCEPTED, and its §7 forbids in-place rewrites of §1–§6 — corrections go through an amendment section (the [ADR-MONO-050](ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §7 pattern). More substantively, Phase 2 makes **new** decisions 053 did not (the two-surface split, the deferral-on-named-owner, the read-only observation stance) — the same reason 053 was a new ADR fulfilling 052 §D8 rather than a 052 edit. A new ADR + a forward-pointer added to 053's amendment section on accept is the chain's established shape.

---

## 5. Verification (re-measured at authoring, 2026-07-24 — recount, don't inherit)

| Claim | Check | Result |
|---|---|---|
| wms DLTs 3PL inbound destinations today | [`CreateScmInboundExpectationService`](../../projects/wms-platform/apps/inbound-service/src/main/java/com/wms/inbound/application/service/CreateScmInboundExpectationService.java#L82-L86) `if (!"WMS_WAREHOUSE".equals(destinationNodeType)) throw InboundExpectationRejectedException` | **confirmed** |
| `THIRD_PARTY_LOGISTICS` node exists but has no factory | enum + JPA enum + `ck_inventory_nodes_type` CHECK all include it; `InventoryNode` exposes only `autoRegisterWmsWarehouse` | **confirmed** |
| Activation needs no Flyway | enum value + CHECK already present (V1__init.sql:20) | **confirmed** |
| demand-planning is replenishment-only | `EvaluateReorderUseCase` consumes `wms.inventory.alert.v1` → `ReorderSuggestion` → supplier draft PO; no order/allocation/redistribution/self-vs-3PL surface anywhere in `demand-planning-service/**` | **confirmed — 052 §D2① does not stretch to fulfillment routing** |
| The seam is self-fulfillment-only | `outbound.shipping.confirmed` = "goods left a wms warehouse"; the logistics `FulfillmentRouter.route()` returns `SELF` unconditionally; the `THIRD_PARTY_LOGISTICS` arm is never returned | **confirmed** |
| A 3PL order never emits the seam | the 3PL's own WMS ships it; wms has no record of it → no `outbound.shipping.confirmed` | **confirmed (by construction; ADR-050 §D4)** |
| 3PL execution is designed as a `ThirdPartyFulfillmentPort`, not a dispatch adapter | `external-integrations.md` § Not In Phase 1 | **confirmed** |
| Phase 1 (053) fully landed | scm INDEX: BE-041…BE-045 DONE, wms TMS retirement (BE-560 / PC-FE-258) DONE | **confirmed** |

Per repo practice, a prior count/claim is a hypothesis, not a source — every row above was re-derived from current code, not carried from 053.

---

## 6. Consequences

**Positive**
- The cross-node thesis (scm sees + routes across own + 3PL nodes) is proven by the **cheap, reachable** half (Surface A), not gated behind the blocked half.
- `THIRD_PARTY_LOGISTICS` moves from "declared at four layers, constructed at none" to activated, with **no schema change**.
- The wms 3PL rejection is re-read as **correct boundary enforcement** (D3), closing the "why does wms refuse 3PL?" question the way 052 §D3 closed it for cross-warehouse transfer.
- 053's weakest allocation (052 §D2①) is **checked and retired** rather than silently inherited — the exact outcome 053 §D5's re-measure clause was written to force.

**Negative**
- "둘 다" is **not** fully delivered: the outbound 3PL fulfillment path (Surface B) is deferred, and its real prerequisite — an order-to-node allocation owner upstream of wms — is a separate epic (D5). This ADR narrows the 053 promise and says so.
- Two ACCEPTED-053 claims are corrected (D6); a future reader must read 053 **with** this ADR's §D6, until 053's amendment-section pointer lands.
- 3PL read-only observation (D4) adds a data-freshness surface (a `THIRD_PARTY_LOGISTICS` node can go `STALE`/`UNREACHABLE`) with no write-back to reconcile it — deliberate, but an operational watch item.

**Neutral**
- No `domain`/`traits` change to any `PROJECT.md`; no new platform; `tms` stays out of the taxonomy. No contract version moves in this ADR (the Phase-2a tasks move them, contract-first).

---

## 7. Outstanding follow-ups

- **ACCEPT gate (human).** PROPOSED. `PROPOSED → ACCEPTED` requires explicit, exact-form human intent ("ADR-MONO-054 ACCEPTED") per [`platform/architecture-decision-rule.md`](../../platform/architecture-decision-rule.md); a bare affirmative does not clear it, and no self-ACCEPT. On ACCEPT: (a) author TASK-SCM-BE-046 into scm `ready/` (via `backlog/`, once its spec exists); (b) add a forward-pointer to **ADR-053's amendment section** recording the §D6 corrections.
- **The Surface B owner (D5).** Before any outbound 3PL fulfillment is built, a separate ADR must decide *"who owns the order-to-node (self-vs-3PL) allocation decision?"* — its natural home is the order-origination territory ([ADR-MONO-022](ADR-MONO-022-ecommerce-wms-fulfillment-integration.md)), not demand-planning and not the logistics consumer.
- **3PL vendor terms.** 품고 / ShipBob sandbox + read-API availability (for D4 observation) to be verified against live docs at Phase-2a implementation.
- **`FulfillmentRouter` disposition.** If Surface B is not built for a long interval, decide whether the inert 3PL arm stays as a documented extension point or is removed (a dead-code question for a later refactor pass), given D6 reframes what "wires" it.

---

## 8. Status history

| Date | Status | Note |
|---|---|---|
| 2026-07-24 | PROPOSED | Authored in answer to the owner's 2026-07-24 "Phase 2 3PL 게이트 열자" instruction, firing 052 §D8-3. Authorises no code. Re-measures 053 §D5's deferred D2① analogy and reports it fails (§5). |
