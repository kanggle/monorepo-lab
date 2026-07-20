# ADR-MONO-052 — Transport is scm's context; wms owns the dock, not the road

**Status:** ACCEPTED
**Date:** 2026-07-20
**History:** PROPOSED 2026-07-20 (TASK-MONO-452) · ACCEPTED 2026-07-20 (TASK-MONO-453 — user-explicit ADR-naming intent "ADR-MONO-052 ACCEPTED")
**Decision driver:** User questions (2026-07-20) — "멀티창고고 서로 이동도 가능하고 외부로부터 들어오는것도 가능해?" → "창고 간 이동(출고→입고 사가) 관리는 누가해?" → "erp나 scm이 아니라 wms에서 관리하는거야?" → "tms 옮기는거랑 관련있어?" → "지금 3pl을 연동하는건 wms야?" → "외부 운송회사에 물건 보낼때 무슨 플랫폼/서비스에서 해?" → "내 프로젝트에서 플랫폼 급으로 tms를 만들고 거기서 외부 tms를 연동하는건?". Five distinct capabilities were found to be unowned or ambiguously owned, all of them transport-side. This ADR maps them once.
**Supersedes:** none.
**Related:** [ADR-MONO-050](ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §D4 (the single sentence that currently carries this whole design — "carrier/3PL execution lives in scm's v2-deferred `logistics-service`"), [ADR-MONO-027](ADR-MONO-027-wms-scm-replenishment-loop.md) §D1 (the fact-event seam, and the rejection of synchronous cross-project REST that D5 here inherits), [ADR-MONO-022](ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) (precedent: a non-wms domain triggers wms physical work without wms acquiring that domain's logic), [ADR-MONO-051](ADR-MONO-051-master-data-stays-federated.md) (same shape — naming an undeclared arrangement and attaching a tripwire), [ADR-MONO-002](ADR-MONO-002-phase-4-template-extraction-trigger.md) §D4 + [ADR-MONO-016](ADR-MONO-016-erp-platform-bootstrap.md) (the "erp is the final domain" declaration that D6 declines to reopen).

> **Why an ADR, not just an answer in chat.** The question "who owns cross-warehouse transfer?" was answered three different ways in a single conversation — *wms owns it* → *wms owns only the execution* → *wms does not own the transport leg either* — each time by reading one more document. That is the signature of a genuinely under-determined boundary, not of careless reading. The repo currently expresses this design in **one subordinate clause of one ADR** ([ADR-MONO-050](ADR-MONO-050-scm-procurement-wms-inbound-expected.md):105) while **nine documents** name a service that does not exist. A boundary that can only be reconstructed by cross-reading five specs will be re-litigated every time someone touches it.

---

## 1. Context

### 1.1 What exists today

Goods physically move in three phases. The repo implements the first and third, and nothing in the middle:

| Phase | Owner today | State |
|---|---|---|
| Goods leave facility A | wms `outbound-service` | implemented — pick/pack/ship saga, `SHIPPED` |
| Goods are in transit | **nobody** | no model, no service, no state |
| Goods arrive at facility B | wms `inbound-service` | implemented — ASN → inspection → putaway |

The two implemented ends are wms's and correctly so. The gap is not an omission inside wms; it is a context wms does not model.

### 1.2 The five unowned capabilities

Each was reached by a separate question and each landed in the same empty place:

| # | Capability | Current state |
|---|---|---|
| ① | Deciding *whether* to redistribute stock between facilities | no service, no spec |
| ② | The A→B transport leg + in-transit custody | no service; `IN_TRANSIT` exists as an scm enum value with no factory |
| ③ | 3PL execution | actively **rejected** by wms (see §1.5) |
| ④ | External TMS vendor connection | implemented **inside wms** `outbound-service` |
| ⑤ | `logistics-service` itself | named in 9 documents, `apps/` directory does not exist |

### 1.3 Cross-warehouse transfer is already forbidden in code, deliberately

[`TransferStockService.resolveSameWarehouse()`](../../projects/wms-platform/apps/inventory-service/src/main/java/com/wms/inventory/application/service/TransferStockService.java#L225) throws `"Cross-warehouse transfers are not supported in v1"`, and `stock_transfer` carries a **singular** `warehouse_id` — the table cannot represent two facilities. The spec agrees and names the intended shape:

> **Multi-warehouse transfer**: model as outbound-from-A + inbound-to-B saga in `outbound-service` orchestration. Inventory itself stays single-warehouse-only.
> — [inventory-service/architecture.md:589-590](../../projects/wms-platform/specs/services/inventory-service/architecture.md#L589-L590)

This is the only place in the repo that assigns an owner to cross-warehouse transfer, it lives in an *Extensibility Notes* section explicitly framed as "documented to guide v2 decisions", and it assigns **the whole thing** to wms — including the middle phase §1.1 shows wms does not model. D3 keeps its structure and D1 corrects its scope.

### 1.4 "Orchestration" in this repo means state-keeping, not commanding

The word appears in six places implying wms drives a saga. The canonical saga spec denies it in its own heading — [outbound-saga.md:62](../../projects/wms-platform/specs/services/outbound-service/sagas/outbound-saga.md#L62) reads **"Choreographed (not Orchestrated)"** — and the code agrees: `outbound-service` holds **no HTTP client to any internal service** (its only outbound client is the TMS vendor adapter), and `OutboundSagaCoordinator` runs `MANDATORY`-propagation methods that transition state and write outbox rows. Reading §1.3's "in `outbound-service` orchestration" as "wms commands the transfer" would invent a coupling the repo does not have.

### 1.5 wms already refuses the 3PL half

[ADR-MONO-050](ADR-MONO-050-scm-procurement-wms-inbound-expected.md):105 states it and `CreateScmInboundExpectationService` enforces it with a whitelist gate that DLTs anything other than `WMS_WAREHOUSE`:

> a 3PL warehouse is operated by the 3PL's own WMS — wms `inbound-service` does not manage its receiving. […] carrier/3PL execution lives in scm's v2-deferred `logistics-service`.

That subordinate clause is, today, the **entire** written basis for transport ownership. It was written to justify a rejection, not to allocate a context — yet it is what every other document defers to.

### 1.6 scm has already claimed the context, in the rule layer

[rules/domains/scm.md:24](../../rules/domains/scm.md#L24) — Logistics Coordination:

> shipment(운송) 단위 생성·조회, **carrier 연동**, ETA/추적, **출발지·도착지 라우팅**

Origin/destination routing, carrier integration, ETA — precisely the vocabulary absent from wms. scm.md:120 further assigns `logistics → inventory-visibility: shipment 출발/도착 이벤트로 in-transit 상태 업데이트`, and `NodeType.IN_TRANSIT` already exists in scm code (with no factory method — an enum value nothing can construct). The claim is real but entirely unbuilt.

### 1.7 The constraint on making this a platform

[README.md:46](../../README.md#L46) and [docs/project-overview.md:190](../../docs/project-overview.md#L190) declare erp the portfolio's **final** domain and state that **no further bootstrap ADR is planned** (`mes`/`hr`/판매/구매/생산 explicitly dropped, 2026-05-07). `tms` was never a candidate — [ADR-MONO-002](ADR-MONO-002-phase-4-template-extraction-trigger.md) §D4 considered only scm/erp/mes. Separately, [ADR-MONO-003a](ADR-MONO-003a-d4-override-scope-canonicalization.md) §D2.1 requires a dedicated ADR plus user-explicit approval for any new domain skeleton, and the measured cost of the smallest such bootstrap was **39 files / 1646+ lines** ([TASK-MONO-119:187](../../tasks/done/TASK-MONO-119-erp-platform-bootstrap-artifact.md#L187)) on top of a two-stage ADR.

---

## 2. Decision

### D1 — Transport is a distinct bounded context, and it belongs to scm

wms owns what happens **inside the four walls, plus the dock handover instant**: receiving, putaway, inventory, picking, packing, and the fact that a shipment left. Everything **between two facilities** — carrier selection, custody in transit, ETA, tracking, 3PL execution — is transport coordination and belongs to scm's already-declared Logistics Coordination context (§1.6).

The dividing line is custody: wms is authoritative while goods are on its floor. The moment they are on a vehicle they are in no warehouse, and wms's model — quantity buckets scoped to a `warehouse_id`, no status enum — has no way to hold them. That is not a gap to fill in wms; it is the boundary itself.

### D2 — Ownership of the five capabilities

| # | Capability | Owner | Basis |
|---|---|---|---|
| ① | Redistribution decision (whether to move stock A→B) | scm `demand-planning-service` | it already owns the mirror decision — reorder-point evaluation on a wms low-stock alert ([ADR-MONO-027](ADR-MONO-027-wms-scm-replenishment-loop.md) §D2). "Buy from a supplier" and "pull from another facility" are two answers to one question; splitting them across domains would split the question |
| ② | Transport leg + in-transit custody | scm `logistics-service` | §1.6; `NodeType.IN_TRANSIT` is already scm's |
| ③ | 3PL execution | scm `logistics-service` | [ADR-MONO-050](ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §D4, unchanged — this ADR promotes that clause from rejection-rationale to allocation |
| ④ | External TMS vendor connection | scm `logistics-service` (**target**; interim stays in wms — D7) | carrier 연동 is scm.md:24's first clause |
| ⑤ | `logistics-service` bootstrap | scm, **service level** | D6 |

wms's share of ①–⑤ is zero. Its share of cross-warehouse transfer is the two legs it already implements.

### D3 — A cross-warehouse move is outbound-from-A + inbound-to-B, never one transaction

Confirms [inventory-service/architecture.md:589-590](../../projects/wms-platform/specs/services/inventory-service/architecture.md#L589-L590) structurally and corrects it on ownership: the two legs are wms's existing outbound and inbound flows, the middle is scm's, and no service holds all three. `inventory-service` stays single-warehouse-only and the §1.3 guard stays in place — it is not a v1 limitation awaiting removal but the enforcement of this boundary.

Consequence worth stating plainly: there is **no atomic cross-warehouse transfer** and there will not be one. Stock leaves A before it exists at B, and the interval is real, not a modelling artefact.

### D4 — In-transit custody belongs to transport, not to either warehouse

wms `inventory-service` will **not** gain an `IN_TRANSIT` bucket or status. Its state is quantity buckets scoped to one warehouse ([domain-model.md:622](../../projects/wms-platform/specs/services/inventory-service/domain-model.md#L622) — "no enum status"), and stock in transit is scoped to neither endpoint. It is held by the transport context — where scm's `NodeType.IN_TRANSIT` already anticipates it.

Rationale: putting in-transit in wms forces a choice between crediting B before arrival (a lie the receiving flow must then reconcile) and leaving A's outbound un-reconciled. Both corrupt the ledger wms is authoritative for.

### D5 — The wms↔transport seam is a fact event, never a synchronous call

wms publishes what it knows — goods left — and does not wait. It already does: `outbound.shipping.confirmed` is emitted via outbox in the shipping-confirm transaction. `logistics-service` subscribes. **No new event contract is required for the seam to exist.**

This is not a new principle; it is the third application of one the repo has already decided twice, in both directions:

- [ADR-MONO-027](ADR-MONO-027-wms-scm-replenishment-loop.md):67 — *"Rejected alternative: wms directly calls a scm REST endpoint. That inverts the dependency […] and couples wms's transaction to scm availability."*
- [ADR-MONO-050](ADR-MONO-050-scm-procurement-wms-inbound-expected.md):79 — *"Rejected alternative: scm calls a wms REST endpoint synchronously. That couples scm's PO-confirm transaction to wms availability."*

A synchronous wms→`logistics-service` call would re-create exactly the coupling 027 rejected, and would additionally make wms's shipping confirmation depend on scm being up — today it depends on nothing, by construction (the TMS push runs `AFTER_COMMIT`, and its failure is a recorded state, not a rollback).

### D6 — No `tms-platform`. Transport is a service inside scm

The capability is real; an eighth platform is not the way to hold it. Three reasons, in decreasing order of weight:

1. **The portfolio declared itself complete at seven domains** (§1.7), and that declaration is itself an artefact — a record of scoping discipline. Reversing it costs more than it buys.
2. **Nothing additional is demonstrated.** What transport work shows — external vendor integration, circuit breaking, idempotency, in-transit state, saga participation — is fully shown by one service. A gateway, an IAM tenant seed, a compose file, and a CI filter would be the eighth instance of patterns already demonstrated seven times.
3. **Nine documents already point at `logistics-service`** (PROJECT.md:60, gateway-public-routes.md:307, project-overview.md:107, ADR-050 §D4, SCM-BE-002/003, and others). A platform-level answer invalidates all of them; a service-level answer fulfils them.

Note the taxonomy detail, so it is not mistaken for a blocker: `tms` is absent from the 41-domain catalogue, but [`logistics`](../../rules/taxonomy.md#L223) is present and unclaimed — *"운송 계획, 창고, 배송 추적. 전형 서브시스템: Shipment, Route Planning, Tracking, Fleet, POD"*. The lighter path exists. This ADR declines it on the grounds above, not on procedural grounds.

**If reopened**, a proposal must supersede [ADR-MONO-002](ADR-MONO-002-phase-4-template-extraction-trigger.md) §D4 and [ADR-MONO-016](ADR-MONO-016-erp-platform-bootstrap.md), and must argue the reversal of "final domain" explicitly rather than treating it as unstated.

### D7 — The TMS adapter stays in wms until there is a receiver

D2 ④ names the destination; this decision declines to schedule the move. `adapter/out/tms/*` remains in `outbound-service` and continues to work as built. Relocating it before `logistics-service` exists would strand the `SHIPPED_NOT_NOTIFIED` recovery path and the operator retry endpoint with nothing to talk to.

When the move happens, the shape is already determined by D5: wms stops calling the vendor and keeps publishing `outbound.shipping.confirmed`; `logistics-service` subscribes and owns the vendor client, `tms_status`, the retry endpoint, and `carrierCode` resolution. wms's outbox publication is unchanged — it is the one piece that does not move.

Two facts make this interim cheap rather than a debt accruing interest: the vendor **does not exist yet** (`base-url` defaults to `https://tms.example.com/api/v1`, and [tms-shipment-api.md](../../projects/wms-platform/specs/contracts/http/tms-shipment-api.md) calls itself *indicative* pending a real vendor schema), and the adapter is already behind a port (`ShipmentNotificationPort`), so relocation is a wiring change rather than a rewrite.

### D8 — Tripwire: what triggers the `logistics-service` bootstrap

This ADR schedules no implementation. It is reopened as *work* — not as a decision — on the first of:

1. **a second warehouse actually operated** — the dev seed contains only `WH01` ([V99__seed_dev_warehouse.sql](../../projects/wms-platform/apps/master-service/src/main/resources/db/seed/V99__seed_dev_warehouse.sql)); multi-warehouse is structurally supported and operationally unused, so cross-warehouse transfer currently has no subject; or
2. **a real TMS vendor** replacing the `tms.example.com` placeholder — at which point D7's interim stops being free, because vendor-specific logic would accrue in the wrong project; or
3. **a 3PL destination that must be honoured** rather than DLT'd — today's rejection is correct precisely because no adapter exists (ADR-050 §5 says so); or
4. **a redistribution decision anyone intends to act on** — ① is the only one of the five that could be built without ②–⑤, and building it first would produce recommendations nothing can execute.

Until one fires, the correct amount of transport code is the amount that exists: the two wms legs and the vendor push.

---

## 3. Implementation plan

**None. This ADR changes no code, no contract, and no schema.** Its output is the map in D2 plus the tripwire in D8.

Deliberately *not* scheduled: the `logistics-service` bootstrap, the TMS adapter relocation, an `IN_TRANSIT` state anywhere in wms, removal of the cross-warehouse guard, a 3PL adapter, and any redistribution-decision service.

---

## 4. Alternatives considered

### A1 — A `tms-platform` as an eighth platform (Rejected)

Rejected per D6. Recorded here because it is the option most likely to be re-proposed: the domain instinct behind it is sound — a TMS genuinely is a peer of a WMS in industry — and the taxonomy has an unclaimed `logistics` slot that would make it procedurally easy. The objection is not that transport is unworthy of a platform; it is that this portfolio declared its scope closed and demonstrates nothing new by reopening it.

### A2 — wms owns transport end-to-end (Rejected)

The reading §1.3 invites. Rejected because wms's model actively cannot express it: `Shipment` has no origin and no destination field (only `orderId`); the TMS contract **forbids** `pickupAddress`/`deliveryAddress`/`warehouseCode` as v1 fields; `Inventory` has no status enum; and `stock_transfer` has a singular `warehouse_id`. Making wms own transport means giving it routing, custody, and carrier concepts it was deliberately built without — and doing so in the one domain whose rule file scopes it to *"물건이 창고 안에서"* ([rules/domains/wms.md](../../rules/domains/wms.md)).

### A3 — wms synchronously calls `logistics-service` (Rejected)

Rejected under D5 — it is the mirror image of what [ADR-MONO-027](ADR-MONO-027-wms-scm-replenishment-loop.md) §D1 already rejected, and it would newly couple wms's shipping confirmation to scm availability. Recorded explicitly because "wms keeps pushing, just to a different endpoint" is a natural-sounding migration of D7 that quietly violates two accepted ADRs.

### A4 — Leave the TMS adapter in wms permanently (Rejected as end state; **adopted as interim** — D7)

Defensible while there is no vendor and no receiver, which is why D7 adopts it now. Rejected as the end state because `carrierCode` resolution, ETA, and tracking have nowhere to go in wms without importing the routing model A2 rejects — the adapter would become the seed of transport logic in the wrong context.

### A5 — Bootstrap `logistics-service` now (Rejected — premature, not wrong)

The correct destination, at the wrong time. All four D8 conditions are currently unmet: one seeded warehouse, a placeholder vendor, no 3PL adapter to route to, and no redistribution decision anyone intends to act on. Building it now yields a service with no traffic and four speculative interfaces. D8 exists so this is deferred on a checkable condition rather than on sentiment.

### A6 — Answer in conversation, record nothing (Rejected)

The option this ADR exists to refuse, and the one the §1 preamble measures the cost of: the same question produced three different answers in one sitting because the design lives in a subordinate clause. Nine documents name a service nobody has decided the contents of. Re-deriving that from specs each time is the actual recurring cost.

---

## 5. Consequences

**Positive**

- The five capabilities have named owners; "who does transport?" stops being a five-document read.
- The wms cross-warehouse guard and the 3PL rejection become *enforcement of a boundary* rather than *v1 limitations*, which changes how a future reader treats them.
- D5 means the seam requires no new contract — the subscription can be built the day `logistics-service` exists.
- D8 converts "we should probably build logistics-service someday" into four checkable conditions.
- No project gains a dependency and no declared `domain`/`traits` change.

**Negative**

- The TMS adapter sits in the project D2 says does not own it, for an unbounded interval (D7). Mitigated by the port boundary and the absent vendor, but it is a stated inconsistency rather than a hidden one.
- Cross-warehouse transfer remains impossible. If a second warehouse is ever operated, this is felt immediately (D8-1).
- `logistics-service` accumulates further references while remaining unbuilt — this ADR adds to that count rather than reducing it.
- ① (redistribution decision) is assigned to `demand-planning-service` by analogy to ADR-027 rather than by any existing spec. It is the weakest allocation here and the one most likely to want revisiting when it is actually built.

**Neutral**

- No service is added or removed; no contract version moves; no task is created.

---

## 6. Verification

Each claim is checkable; re-run before citing this ADR as current. Every row below was **re-measured at authoring time** (2026-07-20, TASK-MONO-452 AC-0) rather than inherited from the investigation that prompted this ADR — the reference count in particular was carried in as "nine" and is recorded here only because counting reproduced it. None of the measurements overturned D2 or fired D8.

| Claim | Check |
|---|---|
| Cross-warehouse transfer is rejected in code | `TransferStockService.java` — `"Cross-warehouse transfers are not supported in v1"`; `stock_transfer` has singular `warehouse_id` |
| wms has no in-transit concept | repo-wide `IN_TRANSIT` in `projects/wms-platform/**` → 0 hits |
| scm has the enum but no factory | `NodeType.IN_TRANSIT` exists; `InventoryNode` exposes only `autoRegisterWmsWarehouse` |
| `logistics-service` does not exist | `projects/scm-platform/apps/` → 4 services, none named `logistics-service`; `specs/services/logistics-service/` absent |
| …but is referenced widely | `logistics-service` string → **9 files**, measured 2026-07-20 at `1e96d6180~1` (i.e. excluding this ADR's own task, which would otherwise inflate the count): ADR-050, `project-overview.md`, scm `PROJECT.md`, scm `README.md`, `gateway-public-routes.md`, SCM-BE-002, SCM-BE-003, MONO-040, MONO-430 |
| wms holds the only live carrier-side integration | `adapter/out/tms/` in `outbound-service`; it is that service's only non-vendor-free outbound HTTP client |
| The TMS vendor is a placeholder | `base-url` default `https://tms.example.com/api/v1` |
| wms rejects 3PL destinations | `CreateScmInboundExpectationService` whitelist gate; IT `thirdPartyNodeTypeCreatesNoAsn` |
| Only one warehouse is seeded | `V99__seed_dev_warehouse.sql` → `WH01` only |
| scm claims carrier + origin/destination routing | `rules/domains/scm.md:24` |
| The portfolio declared itself closed at seven domains | `README.md:46`, `docs/project-overview.md:190` |
| `tms` is not a taxonomy domain; `logistics` is | `rules/taxonomy.md` — 41 domains, `logistics` at §Logistics & Mobility, no `tms` |

Per repo practice, a prior count is a hypothesis, not a source — recount rather than inherit.

---

## 7. Outstanding follow-ups

- **None scheduled.** D8 is a trigger, not a backlog item; it produces tasks when it fires, and no earlier.
- **Promotion candidate (only if ACCEPTED):** D1's custody line — *"wms is authoritative while goods are on its floor; goods on a vehicle are in no warehouse"* — is the reusable half of this ADR and would belong in [`rules/domains/wms.md`](../../rules/domains/wms.md) alongside its existing Transfer definition, which currently reads *"같은 창고 내 **또는 창고 간** 재고를 이동하는 행위"* and is **wider than what wms implements or (per D1) should**. Measure before promoting: if the narrowing is already stated elsewhere, restate nothing.

---

## 8. Status history

| Date | Status | Note |
|---|---|---|
| 2026-07-20 | PROPOSED | Authored under TASK-MONO-452 in answer to the user's 2026-07-20 question series. |
| 2026-07-20 | ACCEPTED | Flipped under TASK-MONO-453 on the user's exact-form instruction **"ADR-MONO-052 ACCEPTED"**. |

**ACCEPT gate — cleared, not bypassed.** Authoring and authorisation were separate acts by separate parties: TASK-MONO-452 wrote this ADR and left it PROPOSED, the user named it explicitly, and TASK-MONO-453 performed the flip. No self-ACCEPT occurred.

The gate is worth recording because it **visibly bit**. The turn before the accepting one was a bare **"ACCEPT"**, and that did not clear it — [`platform/architecture-decision-rule.md`](../../platform/architecture-decision-rule.md):43-46 excludes bare affirmative tokens *"even when it replies directly to the message that proposed it, and even when the intent seems obvious from context"*. That clause forecloses exactly the inference available at the time (only one ADR was under discussion, so the referent was unambiguous). The rule's own reason is attributability (:53-56): a gate that any affirmative noise can open launders the author's preference into an accepted decision. It held here.

**What acceptance binds.** Acceptance authorises **no implementation** — §3 is "None", and all four §D8 triggers were unfired at ratification. What becomes binding is the allocation: **D1** (transport is scm's context; the line is custody), **D3** (a cross-warehouse move is two legs, and no atomic cross-warehouse transfer will exist), **D4** (in-transit lives in neither warehouse; wms gains no `IN_TRANSIT`), **D5** (the wms↔transport seam is a fact event, never a synchronous call), and **D6** (no `tms-platform`).

Three items are **not** unconditionally accepted, and reading them as such would misstate the decision:

- **D7 is conditional** — the TMS adapter's stay in wms expires when a receiver exists. It is an interim, not an endorsement of its current home.
- **D8 is a trigger, not a backlog item** — it produces tasks when it fires, and no earlier.
- **D2's ① row** (redistribution decision → `demand-planning-service`) rests on analogy to [ADR-MONO-027](ADR-MONO-027-wms-scm-replenishment-loop.md), not on any existing spec. §5 names it the weakest allocation here; acceptance does not upgrade its evidence.

D6 in particular **declines to reopen** a prior user decision (§1.7) rather than re-deciding it. Being written down and accepted does not make it a fresh reversal-eligible choice; reopening still requires superseding [ADR-MONO-002](ADR-MONO-002-phase-4-template-extraction-trigger.md) §D4 and [ADR-MONO-016](ADR-MONO-016-erp-platform-bootstrap.md).

**Amending this ADR.** What was authorised is the text as it was read. Later improvements — including any strengthening of D2 ①'s basis, or a correction to [inventory-service/architecture.md:589-590](../../projects/wms-platform/specs/services/inventory-service/architecture.md#L589-L590) once §7's promotion candidate is measured — go through an amendment section (the [ADR-MONO-050](ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §7 pattern), not an in-place rewrite of §1–§6.
