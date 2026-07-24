# ADR-MONO-053 — logistics-service goes multimodal: carrier dispatch now, 3PL fulfillment designed-in

**Status:** ACCEPTED
**Date:** 2026-07-24
**History:** PROPOSED 2026-07-24 (this record) · ACCEPTED 2026-07-24 (owner exact-form instruction **"ADR-MONO-053 ACCEPTED"**). ACCEPT is a human gate — an agent may not accept its own ADR; author (agent) and acceptor (owner) were separate parties.
**Decision driver:** User (2026-07-24), after a design conversation that walked the whole transport surface — "집계사(굿스플로·EasyPost·스윗트래커)를 붙이고 3pl도 붙이는 방식" → "둘 다 하는 방식으로 구현". The owner elects to **fire [ADR-MONO-052](ADR-MONO-052-transport-context-map.md) §D8-2** by adopting **EasyPost** (real vendor, free sandbox) as a dispatch vendor, replacing the `tms.example.com` placeholder, and to build the hybrid **carrier + 3PL multi-node fulfillment** that 052 mapped but deliberately left unbuilt.
**Supersedes:** none. This ADR **does not supersede** [ADR-MONO-052](ADR-MONO-052-transport-context-map.md); it is 052 doing exactly what its §D8 said — *"reopened as work — not as a decision"*. 052's §D2 ownership map is this ADR's **input**, not something it re-decides.
**Related:** [ADR-MONO-052](ADR-MONO-052-transport-context-map.md) (the map: §D2 ownership, §D5 the `outbound.shipping.confirmed` fact-event seam, §D6 no `tms-platform`, §D7 the wms TMS-adapter interim, §D8 the tripwire this fires), [ADR-MONO-050](ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §D4 (a 3PL warehouse is operated by the 3PL's own WMS — wms rejects its receiving; DLT gate), [ADR-MONO-027](ADR-MONO-027-wms-scm-replenishment-loop.md) (demand-planning owns the reorder/redistribution decision; the reject-synchronous-REST principle), [ADR-MONO-022](ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) (precedent: a non-wms domain drives wms physical work without acquiring wms's logic).

> **Why an ADR, not just tasks.** 052 mapped *who owns* transport but explicitly recorded no implementation (052 §3). The moment the owner elects to build it, several architecture decisions that 052 did **not** make come due at once: multi-vendor dispatch behind one port, a carrier-selection router, the self-vs-3PL fulfillment-routing seam, the 3PL inventory-node factory, and the sequencing of all of it. Those are `platform/architecture-decision-rule.md` decisions — they cannot be made implicitly during implementation. This ADR makes them, records the phasing, and keeps the honest tension with 052's own "premature" judgment on the record rather than laundering it into code.

---

## 1. Context

### 1.1 What 052 decided, and deferred

[ADR-MONO-052](ADR-MONO-052-transport-context-map.md) drew the transport boundary at **custody** (§D1): wms is authoritative while goods are on its floor; the moment they are on a vehicle they are in no warehouse. It assigned all five transport capabilities to scm (§D2), reused the existing `outbound.shipping.confirmed` outbox event as the wms↔transport seam so **no new contract is needed** (§D5), declined an eighth `tms-platform` in favour of a service inside scm (§D6), left the TMS vendor adapter in wms as an interim **until a receiver exists** (§D7), and gated the `logistics-service` bootstrap behind four tripwire conditions (§D8), of which the relevant two are:

- **D8-2** — a real TMS vendor replaces the `tms.example.com` placeholder.
- **D8-3** — a 3PL destination that must be **honoured** rather than DLT'd.

052 §3 scheduled **no** implementation, and §A5 recorded the bootstrap as *"the correct destination, at the wrong time"* — premature while all four triggers were unfired.

### 1.2 The trigger is now fired — deliberately, by the owner

D8-2 fires on adoption of a real dispatch vendor. The owner adopts **EasyPost** — a real carrier-aggregator with a **free sandbox**, native `Idempotency-Key` support (a byte-fit for the existing adapter's `Idempotency-Key={shipment.id}` design), and REST semantics matching the current `ShipmentNotificationPort`. Adopting it replaces the placeholder, which is precisely the D8-2 condition. **052 anticipated this exact move** and pre-specified its shape (052 §D7): wms stops calling the vendor and keeps publishing the event; the receiver owns the vendor client.

### 1.3 The two fulfillment models, and why "둘 다"

A physical order is fulfilled one of two ways, and they are not competitors — they are selected by **where the inventory sits**:

| Model | Inventory at | Who picks/packs/ships | Last-mile |
|---|---|---|---|
| **Self-fulfillment** | a wms `WMS_WAREHOUSE` | wms `outbound-service` | a **carrier aggregator** (EasyPost / 굿스플로) → CJ·한진·UPS·… |
| **3PL fulfillment** | a `THIRD_PARTY_LOGISTICS` node | the **3PL's own WMS** (outside our system) | the 3PL, using its own carrier contracts |

"둘 다" is a **hybrid multi-node fulfillment** system that routes each order to the appropriate node. This is the normal shape of real commerce ops, and — critically — it is the configuration in which scm's cross-node design *earns its keep*: the unifying act ("see stock across own + 3PL nodes → route the order → coordinate the chosen executor") is exactly the Logistics Coordination that `rules/domains/scm.md:24` assigns to scm and that wms structurally cannot perform (wms knows only its own four walls).

### 1.4 The honest tension with 052 (kept on the record)

052 §D6 and §A5 judged this bootstrap *"demonstrates nothing additional"* and *premature*. That judgment was correct **for its trigger state** — all four conditions were unfired. This ADR does not pretend otherwise. What changes the calculus is not new evidence that 052 was wrong; it is the **owner electing to fire D8-2 for portfolio/learning value** — the legitimate override 052 §D8 exists to enable ("reopened as work"). The `[REMEDIATION]` this ADR takes is option 1 of the HARDSTOP-09 stanza: fire a real trigger, then decide the architecture in an ADR before code.

Recorded plainly so a future reader is not misled: the carrier path alone already exercises the full integration-heavy surface (external HTTP, circuit-breaker, retry, idempotency, bulkhead, multi-vendor ports&adapters, routing). The **3PL path is where the cross-node thesis is actually proven**, at a materially higher cost. §D7 sequences accordingly.

---

## 2. Decision

### D1 — Fire D8-2; bootstrap `logistics-service` as a service inside scm

The D8-2 tripwire is fired. `logistics-service` is bootstrapped now under `projects/scm-platform/apps/logistics-service/`, at **service** level — **not** a new `tms-platform` (052 §D6 is unchanged and not reopened). Its classification inherits scm's domain and the `integration-heavy` + `transactional` traits; its Service Types are `event-consumer` (the seam) + `rest-api` (operator/retry surface). This is 052 §D8 producing work, not a reversal of 052 §D6.

### D2 — The dispatch path: one port, many vendors, over the existing seam

`logistics-service` consumes wms `outbound.shipping.confirmed` (052 §D5 — **no new contract**; the subscription is additive to the existing outbound event catalogue) and pushes each shipment to a carrier via an application port `ShipmentDispatchPort`. Vendors are adapters behind that port:

```
logistics-service
├── application/port/out/ShipmentDispatchPort         // domain calls dispatch(shipment)
├── adapter/out/dispatch/
│   ├── EasyPostDispatchAdapter        @Profile("!standalone")   // Phase 1
│   ├── GoodsflowDispatchAdapter       @Profile("!standalone")   // Phase 1 (국내)
│   └── StandaloneDispatchAdapter      @Profile("standalone")    // in-memory fallback
```

Each adapter carries its **own** integration-heavy treatment (I1–I4, I7–I9): dedicated timeout, circuit breaker, retry+jitter, idempotency key, bulkhead — no pool shared across vendors. The `SHIPPED_NOT_NOTIFIED` recovery state and the operator `:retry` endpoint relocate from wms with the adapter (052 §D7), behind the same port so the move is wiring, not rewrite.

### D3 — `CarrierRouter`: vendor/carrier selection

With two dispatch vendors, "which carrier?" becomes a decision. `CarrierRouter` selects the vendor/carrier by region and shipment attributes (domestic → 굿스플로; international → EasyPost; overridable). This is the origin/destination **routing** concept `rules/domains/scm.md:24` assigns to scm — it appears here as the natural consequence of multi-vendor dispatch, and it is one reason transport cannot live in wms.

### D4 — The `FulfillmentRouter` seam is built in Phase 1, with one branch

Before dispatch, an order is routed to a **node**: a wms warehouse (self-fulfillment) or a 3PL. `FulfillmentRouter` is introduced in Phase 1 **with only the self-fulfillment branch wired**, so the 3PL branch (D5) slots in during Phase 2 **without reworking** the dispatch path. Building the seam early is the single most important structural choice here: it makes "둘 다" an additive Phase 2, not a Phase-1 rewrite.

### D5 — The 3PL path: designed now, built Phase 2 (fires D8-3)

3PL fulfillment is **specified** by this ADR and **implemented** in Phase 2, whose start **is** the D8-3 firing (a 3PL destination honoured rather than DLT'd). Its shape:

- **Execution** — `ThirdPartyFulfillmentPort` + a 3PL adapter (e.g. 품고 / ShipBob), owned by `logistics-service` (052 §D2③). The order is sent to the 3PL; the 3PL's own WMS picks/packs/ships (outside our system — ADR-050 §D4).
- **Inventory observation** — `inventory-visibility-service` gains a `THIRD_PARTY_LOGISTICS` node **factory** (the `NodeType` value and table CHECK already exist; only the factory + wiring are missing). Stock at the 3PL is **observed read-only**, never operated.
- **Routing decision** — "own warehouse vs 3PL, and which 3PL" is a redistribution/allocation decision owned by `demand-planning-service` (052 §D2① by analogy to [ADR-MONO-027](ADR-MONO-027-wms-scm-replenishment-loop.md)).

**Honest flag, carried from 052 §5:** the demand-planning ownership of the routing decision (052 §D2①) is *"the weakest allocation"* in 052 — it rests on analogy, not on an existing spec. Phase 2 must re-measure it against the then-current demand-planning design before building, not inherit it.

### D6 — Tracking is a separate axis, not a third dispatch vendor

스윗트래커 is a **tracking aggregator** (read-only carrier scan status), not a dispatch vendor. If adopted it is a distinct `ShipmentTrackingPort`, and per 052 §11 tracking events forward to `notification-service` for user notifications. Tracking is **Phase 3 / optional** — it proves nothing the dispatch path does not, and it is deliberately off the critical path.

### D7 — Phasing: carrier-first, 3PL-second, tracking-optional

```
Phase 1 (D8-2, now):   bootstrap + ShipmentDispatchPort + EasyPost/굿스플로 adapters
                       + CarrierRouter + [FulfillmentRouter seam, self-branch only]
                       + wms TMS-adapter retirement (D8 below)
Phase 2 (D8-3, later): ThirdPartyFulfillmentPort + 3PL adapter
                       + THIRD_PARTY_LOGISTICS node factory (inventory-visibility)
                       + demand-planning fulfillment-routing (re-measure D2① first)
Phase 3 (optional):    ShipmentTrackingPort (스윗트래커) → notification-service
```

The task series (§3) encodes this order with dependency markers. Phase 2/3 tasks are **named but not authored to `ready/`** until Phase 1 lands and D8-3 is elected.

### D8 — Retire the wms TMS adapter when the dispatch path is live

052 §D7 kept the TMS adapter in wms *"until there is a receiver"*. Phase 1 creates the receiver. When `logistics-service`'s dispatch path is live and consuming `outbound.shipping.confirmed`, wms **stops calling the vendor** and keeps **only** publishing the event (052 §D5 — the one piece that does not move). The `adapter/out/tms/*` bundle, `tms_status`, the `:retry-tms-notify` endpoint, and `carrierCode` resolution move to `logistics-service`. This is the D7 exit 052 anticipated; it is a **Phase 1** deliverable so the interim does not outlive its justification.

### D9 — Still not a `tms-platform`

052 §D6 stands. Nothing here reopens the "eighth platform" question; the capability is held by a service inside scm. `tms` remains absent from `rules/taxonomy.md`; `logistics` remains the taxonomy slot and it is scm's.

---

## 3. Implementation plan

**This ADR is PROPOSED and authorises no code.** On ACCEPT, the following task series is authored. Only the first is drafted now, and it sits in `backlog/` (not `ready/`) because its own precondition — this ADR ACCEPTED + the logistics-service specs it writes — is not yet met.

| Task | Phase | Scope | Gate |
|---|---|---|---|
| **TASK-SCM-BE-041** (drafted, `backlog/`) | 1 | logistics-service **spec suite**: `architecture.md` (Service Types, hexagonal layout, D2 seam), `external-integrations.md` (EasyPost + 굿스플로), the additive `outbound.shipping.confirmed` **subscription** contract | ADR-053 ACCEPTED → move `backlog/ → ready/` |
| TASK-SCM-BE-042 | 1 | logistics-service **bootstrap** (skeleton + DB + gateway route + CI filter + `ShipmentDispatchPort` + EasyPost adapter, WireMock IT) | BE-041 done |
| TASK-SCM-BE-043 | 1 | 굿스플로 adapter + `CarrierRouter` | BE-042 done |
| TASK-SCM-BE-044 | 1 | `FulfillmentRouter` seam (self-branch) + consume `outbound.shipping.confirmed` | BE-042 done |
| TASK-MONO-XXX | 1 | **wms TMS-adapter retirement** (D8) — cross-project (wms stops vendor call, keeps publish); atomic | BE-044 done |
| TASK-SCM-BE-045+ | 2 | 3PL adapter, `THIRD_PARTY_LOGISTICS` node factory, demand-planning routing | **D8-3 elected** |
| TASK-SCM-BE-04x | 3 | `ShipmentTrackingPort` (스윗트래커) → notification | optional |

Deliberately **not** scheduled by this ADR: any Phase 2/3 code, and the wms retirement before its receiver is live.

---

## 4. Alternatives considered

### A1 — Build carrier + 3PL together in Phase 1 (Rejected)
The literal reading of "둘 다 하는 방식으로 구현". Rejected because the 3PL path is the heaviest (a whole external WMS, cross-node inventory, a routing decision on the weakest 052 allocation) and contract-gated, while the carrier path runs on a free sandbox today. Building both at once triples the integration surface before any of it is proven and couples the bootstrap's success to the riskiest leg. D4's seam makes "둘 다" the **destination** while D7 sequences it — the hybrid is fully achieved without the all-at-once risk.

### A2 — Keep dispatch in wms, just add vendors there (Rejected)
Rejected under 052 §A2/§A4: wms's model cannot express transport (no origin/destination on `Shipment`, no inventory status enum, singular `warehouse_id`), and `rules/domains/wms.md` scopes wms to *"물건이 창고 안에서"*. Adding a `CarrierRouter` (D3) to wms would seed routing — a transport concept — in the wrong context.

### A3 — A `tms-platform` (Rejected)
052 §D6/§A1, unchanged. The capability is real; an eighth platform is not the way to hold it, and the portfolio declared itself closed at seven domains.

### A4 — Author the tasks straight into `ready/` now (Rejected)
The HARDSTOP-05 remediation names `ready/`, but the scm `backlog → ready` gate requires the related specs to exist and this ADR to be ACCEPTED. Putting the task in `ready/` now would file it against an unaccepted decision and absent specs. It goes to `backlog/` with an explicit promotion condition instead.

---

## 5. Consequences

**Positive**
- "둘 다" is reachable as an **additive** Phase 2 (D4's seam), not a rewrite.
- The wms TMS-adapter interim (052 §D7) is **closed** in Phase 1 rather than accruing indefinitely.
- No new event contract for the seam (052 §D5 reused); the subscription is additive.
- The cross-node scm design (`inventory-visibility` multi-node model) gains its first real consumer, proving *why* transport is scm's and not wms's.

**Negative**
- Reverses 052's *"premature"* judgment (§1.4) — on owner election, not new evidence. Kept explicit.
- The 3PL path (D5) inherits 052's weakest allocation (D2① → demand-planning); Phase 2 must re-measure, not inherit.
- Vendor surface grows ~3× the integration-heavy test matrix (per-vendor WireMock/circuit/bulkhead). Phasing spreads but does not remove the cost.
- `logistics-service` moves from "named in 9 docs, built in none" to partially built — a long-lived multi-phase service with Phase 2/3 deliberately open.

**Neutral**
- No `domain`/`traits` change to any PROJECT.md; no new platform; `tms` stays out of the taxonomy.

---

## 6. Verification

Each row re-measured at authoring (2026-07-24); recount rather than inherit before citing.

| Claim | Check |
|---|---|
| `logistics-service` does not exist | `projects/scm-platform/apps/` → procurement / inventory-visibility / demand-planning only; no `logistics-service`; `specs/services/logistics-service/` absent |
| The seam event already exists | wms `outbound.shipping.confirmed` emitted via outbox in `ConfirmShippingService` (052 §D5) |
| wms TMS adapter is behind a port | `outbound-service` `adapter/out/tms/*` implements `ShipmentNotificationPort`; vendor `base-url` default `https://tms.example.com/api/v1` (placeholder) |
| `THIRD_PARTY_LOGISTICS` node exists but has no factory | `inventory_nodes` CHECK includes `THIRD_PARTY_LOGISTICS`; `InventoryNode` exposes only `autoRegisterWmsWarehouse` |
| wms rejects 3PL destinations today | `CreateScmInboundExpectationService` whitelist gate; IT `thirdPartyNodeTypeCreatesNoAsn` |
| scm owns carrier + routing | `rules/domains/scm.md:24` — carrier 연동, 출발지·도착지 라우팅 |
| EasyPost is a real vendor with a sandbox | free test-mode API keys + native `Idempotency-Key` (vendor docs; verify current terms at adoption) |
| 052 §D8-2 is the fired trigger | 052 §D8 item 2 — "a real TMS vendor replacing the `tms.example.com` placeholder" |

---

## 7. Outstanding follow-ups

- **ACCEPT gate (human).** This ADR is PROPOSED. `PROPOSED → ACCEPTED` requires explicit, exact-form human intent per `platform/architecture-decision-rule.md`; a bare affirmative does not clear it, and no self-ACCEPT. On ACCEPT: author TASK-SCM-BE-041 into `ready/` (from `backlog/`) and begin Phase 1.
- **Vendor terms.** EasyPost / 굿스플로 sandbox + contract conditions to be re-verified against live docs at Phase-1 adoption (092 §6 caveat).
- **D2① re-measurement.** Before Phase 2, re-measure the demand-planning fulfillment-routing ownership against the then-current design (052 §5 names it the weakest allocation).
- **052 amendment, not rewrite.** Should this ADR change 052's substance rather than fulfil it, that goes through a 052 amendment section (the ADR-050 §7 pattern), not an in-place edit.

---

## 8. Status history

| Date | Status | Note |
|---|---|---|
| 2026-07-24 | PROPOSED | Authored in answer to the owner's 2026-07-24 "둘 다 하는 방식으로 구현" instruction, firing 052 §D8-2. Authorises no code. |
| 2026-07-24 | ACCEPTED | Flipped on the owner's exact-form instruction **"ADR-MONO-053 ACCEPTED"**. Author and acceptor were separate parties — no self-ACCEPT. |

**ACCEPT gate — cleared, not bypassed.** The owner's earlier "진행" authorised only **authoring this PROPOSED record and its first backlog task** — it did not accept the decision, because `platform/architecture-decision-rule.md` excludes bare affirmatives from the ACCEPT gate even when the referent is unambiguous. Acceptance came as a **separate, exact-form instruction naming this ADR** ("ADR-MONO-053 ACCEPTED"), issued by the owner (a different party from the authoring agent). The gate's purpose is attributability — it held.

**What acceptance binds.** Acceptance authorises **Phase 1** (D1 bootstrap · D2 dispatch port + EasyPost/굿스플로 · D3 CarrierRouter · D4 the self-branch FulfillmentRouter seam · D8 the wms TMS-adapter retirement once the receiver is live) and the promotion of TASK-SCM-BE-041 `backlog/ → ready/`. Three items are **not** unconditionally bound: **D5** (the 3PL path) is Phase 2, gated on a separate D8-3 election; **D6** (스윗트래커 tracking) is Phase 3 / optional; and **D5's demand-planning routing ownership** rests on 052's *weakest allocation* (§D2① analogy) and must be re-measured before Phase 2, not inherited. **D9** (no `tms-platform`) declines to reopen 052 §D6 rather than re-deciding it.

**Amending this ADR.** What was authorised is the text as read. Later changes — including any strengthening of D5's routing basis — go through an amendment section (the ADR-050 §7 pattern), not an in-place rewrite of §1–§6.

---

## 9. Amendment — [ADR-MONO-054](ADR-MONO-054-third-party-logistics-node-activation.md) (Phase 2, D8-3 firing), 2026-07-24

Per §7 ("052 amendment, not rewrite") and the closing note above, corrections to this ADR are recorded as an amendment section rather than an in-place edit of §1–§6. [ADR-MONO-054](ADR-MONO-054-third-party-logistics-node-activation.md) — accepted 2026-07-24 on the owner's D8-3 election — fired §D5's Phase-2 gate and performed the D2① re-measurement §D5 mandated ("re-measure … not inherit"). The re-measurement **failed**, and 054 §D6 corrects two claims here:

- **§D4** — *"the 3PL branch slots in during Phase 2 without reworking the dispatch path … makes 둘 다 an additive Phase 2, not a Phase-1 rewrite."* — **Holds only for the inbound/observation half.** It does **not** hold for the outbound fulfillment path: the logistics `FulfillmentRouter`'s only input is `outbound.shipping.confirmed`, a **self-fulfillment-only** signal (a 3PL order never emits it), so the router can never receive a 3PL order and the `THIRD_PARTY_LOGISTICS` arm is not an additive slot but unreachable-by-this-seam. See ADR-054 §D5/§D6.
- **§D5** — the demand-planning ownership of the outbound self-vs-3PL routing decision (via 052 §D2① analogy) is **withdrawn as inherited**: `demand-planning-service` is replenishment-only (no order/allocation surface). The outbound routing decision has **no owner yet** and is deferred by ADR-054 §D5 pending a separate order-to-node allocation ADR (ADR-022 territory).

Unchanged and still binding: §D1–§D3 (bootstrap, `ShipmentDispatchPort`, `CarrierRouter`), §D8 (wms TMS retirement, landed), §D9 (no `tms-platform`). Read those together with ADR-054 §D6.
