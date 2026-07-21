# ADR-MONO-051 — Master data stays federated (no central MDM hub)

**Status:** ACCEPTED
**Date:** 2026-07-20
**History:** PROPOSED 2026-07-20 (TASK-MONO-433) · ACCEPTED 2026-07-20 (TASK-MONO-434 — user-explicit ADR-naming intent "ADR-MONO-051 ACCEPT")
**Decision driver:** User question (2026-07-20) — "프로젝트에 mdm 있어?" → "도입하는 것과 유지하는 것 어떤 게 더 나아?". The repo has **no** service, spec, or task named `mdm` / "master data management" (repo-wide string search: 0 hits), but it does have several domain-owned master services. The question is whether to consolidate them behind a central MDM hub or keep the current federated shape. This ADR records the answer so the question does not have to be re-researched.
**Supersedes:** none.
**Related:** [ADR-MONO-050](ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §7 D9 (cross-service identifiers are **codes**, not UUIDs — this ADR generalises that from the scm↔wms leg to a repo-wide rule, and inherits its §4 rejection of "erp as the hub"), [ADR-MONO-027](ADR-MONO-027-wms-scm-replenishment-loop.md) (wms→scm replenishment; the `skuCode` join key), [ADR-MONO-022](ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) (ecommerce→wms fulfillment; `lines[].skuCode` resolved locally), [`TEMPLATE.md`](../../TEMPLATE.md) § Cross-Project Runtime Coupling (Extraction Constraint) (the standalone-extraction constraint that makes a hub structurally expensive — D6 was promoted into that section by TASK-MONO-437. The name this ADR originally assumed — § Discovery → Distribution — never existed as a heading; §1.4 and §6 below now cite the real section, and §7 records why that anchor was chosen — see §7), [`platform/service-boundaries.md`](../../platform/service-boundaries.md) § Asynchronous (Events) — cross-project allowed, **[ADR-MONO-016](ADR-MONO-016-erp-platform-bootstrap.md) § D2 (the original declaration that erp owns 부서/직원/직급/비용센터/거래처 master data — §D5 here refines the business-partner half of it into an identity / sourcing / receiving-projection split; that ADR now carries a forward link back to this one)**.

> **Why an ADR, not just an answer in chat.** The conclusion is "change nothing", which is exactly the kind of decision that leaves no artefact and therefore gets re-litigated. Three things need to be *written down*, not merely concluded: (a) that the federated shape is **deliberate**, so a future reader does not read it as an omission; (b) the **tripwire** — the specific, checkable condition under which this decision is reopened; (c) the reason a hub is not merely unnecessary but structurally hostile to this repo's distribution strategy. A "no" recorded without its reopening condition is indistinguishable from an oversight.

---

## 1. Context

### 1.1 What exists today

There is no MDM hub. There are **domain-owned master services**:

| Project | Service | Master scope |
|---|---|---|
| wms-platform | `master-service` | warehouse / zone / location / SKU / lot / partner |
| erp-platform | `masterdata-service` | department / employee / job grade / cost centre / business partner |
| fan-platform | `artist-service` | artist profile + fandom metadata (community master data) |

Each is the sole writer of its own master within its project. Neither reads the other's.

### 1.2 How master identity already crosses project boundaries

It crosses as a **business code string**, and only as an event:

- wms `master-service` publishes `wms.master.sku.v1` carrying both its internal `id` (uuid) and `skuCode` ([master-events.md:230-247](../../projects/wms-platform/specs/contracts/events/master-events.md#L230-L247)).
- ecommerce `product-service` consumes it into a **locally derived** `WmsSkuSnapshot(skuId, skuCode)` and resolves `skuCode → variantId` itself ([wms-inventory-subscriptions.md:47-56](../../projects/ecommerce-microservices-platform/specs/contracts/events/wms-inventory-subscriptions.md#L47-L56)).
- The reverse leg is symmetric in style: `ecommerce.fulfillment.requested.v1` carries `lines[].skuCode`, and wms `outbound-service` resolves it via `findSkuByCode`. The contract states explicitly that **no wms↔ecommerce id map is stored** ([wms-shipment-subscriptions.md:42-45](../../projects/ecommerce-microservices-platform/specs/contracts/events/wms-shipment-subscriptions.md#L42-L45)).
- scm joins on the same string: the wms low-stock alert's `payload.skuCode` is the join key into `sku_supplier_map` / `reorder_policy` ([replenishment-subscriptions.md:61](../../projects/scm-platform/specs/contracts/events/replenishment-subscriptions.md#L61)).
- [ADR-MONO-050](ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §7 D9 already elevated this to a decision for its own leg: "**Cross-service identifiers are CODES**, not UUIDs."

So the repo is already running a **federated MDM** pattern — single authoritative owner per master, business-code identity at the seam, consumer-owned eventual-consistency projections. It was arrived at leg by leg rather than declared once, which is why it reads as absence rather than as design.

### 1.3 The one genuine divergence

"Supplier / business partner" exists in **three independent schemas**:

| | erp `BusinessPartner` | scm `suppliers` | wms `Partner` |
|---|---|---|---|
| natural key | `code` (unique) | none — uuid `id` only | `partnerCode` |
| type discriminator | `partnerType`: CUSTOMER \| SUPPLIER \| BOTH | none | `partnerType: SUPPLIER` |
| payment terms | `paymentTerms{termDays, method}` | — | — |
| lifecycle | ACTIVE / RETIRED, effective-dated | ACTIVE / INACTIVE / CONTRACT_EXPIRED, contract dates | — |
| contact | REST schema | `contact_info` JSONB | `businessNumber`, contact name/email/phone |

Sources: [masterdata-api.md:362-365](../../projects/erp-platform/specs/contracts/http/masterdata-api.md#L362-L365), [masterdata-service/architecture.md:380-393](../../projects/erp-platform/specs/services/masterdata-service/architecture.md#L380-L393), [procurement-service/data-model.md:18-35](../../projects/scm-platform/specs/services/procurement-service/data-model.md#L18-L35), [master-events.md:262-280](../../projects/wms-platform/specs/contracts/events/master-events.md#L262-L280).

The fields have genuinely diverged. **But the three are not joined at runtime today:**

- scm `purchase_orders.supplier_id` declares **no cross-service FK** — "references `suppliers.id` (no FK declared — supplier may be in v2 supplier-service)" ([data-model.md:82](../../projects/scm-platform/specs/services/procurement-service/data-model.md#L82)).
- erp states its v1 has **no real integration** with procurement/finance ([architecture.md:382-385](../../projects/erp-platform/specs/services/masterdata-service/architecture.md#L382-L385)).
- `erp.masterdata.businesspartner.changed.v1` is published with **zero subscribers** in v1 ([erp-masterdata-events.md:48-51](../../projects/erp-platform/specs/contracts/events/erp-masterdata-events.md#L48-L51)).
- The `supplierId` that scm→wms events carry is not erp's `BusinessPartner.code` and not scm's `suppliers.id` either — it is scm's own `sku_supplier_map.supplier_id` used as a **stand-in** pending a v2 supplier-service ([scm-procurement-events.md:400](../../projects/scm-platform/specs/contracts/events/scm-procurement-events.md#L400)).

Divergence without a join is not yet a defect. Nothing currently reconciles these three, so nothing is currently wrong. This is the distinction between *duplication* and *divergence that hurts* — the triplication becomes a defect at the moment a single supplier identity has to mean the same thing in two projects at once, and not before (§ D5).

### 1.4 The constraint a hub would violate

Every project in this repo is extractable to its own standalone GitHub repo via `scripts/sync-portfolio.sh` ([README.md:33-44](../../README.md#L33-L44), [TEMPLATE.md](../../TEMPLATE.md) § Cross-Project Runtime Coupling (Extraction Constraint)). ecommerce's standalone snapshot is deliberately frozen specifically to avoid dragging IAM in as a transitive dependency. Correspondingly, **every** cross-project subscription contract carries a "Standalone-publish degradation / no hard dependency" section (e.g. [wms-inventory-subscriptions.md:90-93](../../projects/ecommerce-microservices-platform/specs/contracts/events/wms-inventory-subscriptions.md#L90-L93)).

An MDM hub is by construction a component that every project must reach to resolve identity. That is a hard dependency for every project — the exact property the distribution strategy is built to avoid.

---

## 2. Decision

### D1 — Master data stays federated; no central MDM hub is introduced (chosen)

Each master stays owned by the domain that operates it. No new hub service, no repo-wide golden-record store, no match/merge/survivorship engine. What the repo already does — one authoritative owner per master, propagated by event — **is** the MDM strategy, and it is hereby named as such rather than left implicit.

### D2 — Shared identity across a project boundary is a business CODE (repo-wide)

Generalises [ADR-MONO-050](ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §7 D9 from that one leg to every cross-project seam: `skuCode`, `warehouseCode`, `partnerCode`, `poNumber`. Internal UUID PKs never cross a project boundary as the join key. A consumer resolves code → its own PK locally, at consume time.

Rationale: a code is a contract that survives the other system being absent, re-seeded, or extracted standalone. A UUID PK is an implementation detail whose stability is not the producer's promise.

### D3 — Replication is one-way and consumer-owned

A consumer that needs an upstream master keeps its **own** derived projection (ecommerce `WmsSkuSnapshot`; scm `inventory_nodes.node_external_id` / `inventory_snapshots.sku`). The producer keeps no mapping table and no knowledge of its consumers. Projections are explicitly eventually-consistent read models, never a second source of truth.

The current asymmetry is correct and intentional: wms is the source, ecommerce and scm are the cachers, and wms holds no copy of anyone else's master.

### D4 — The supplier/business-partner triplication is accepted as-is (not a defect today)

erp `BusinessPartner`, scm `suppliers`, and wms `Partner` remain three independent schemas. Each holds the attributes its own domain needs (erp: payment terms + effective dating; scm: contract expiry; wms: business number + receiving contact). Absent a cross-project join, that is legitimate local concern, not drift.

No unification work is scheduled by this ADR. **This decision is conditional and expires at the D5 tripwire.**

### D5 — Tripwire: what reopens this decision, and what the answer will be

This ADR is reopened when a **single supplier identity must mean the same thing in two projects at the same time**. Concretely, the first of:

1. an scm PO → wms inbound leg that must reconcile its supplier against an erp business partner; or
2. finance settlement matching a counterparty to an erp business partner; or
3. any consumer subscribing to `erp.masterdata.businesspartner.changed.v1` (today: zero).

When it fires, the answer is **still not a hub**. It is the path SKU already walked, and half of its wiring exists:

- promote scm's `sku_supplier_map.supplier_id` stand-in to the planned v2 `supplier-service` with a real `supplierCode` natural key;
- have it subscribe to the already-published, currently-unconsumed `erp.masterdata.businesspartner.changed.v1`;
- keep wms's `Partner` as a local receiving-side projection keyed by `partnerCode`.

That is D1–D3 applied to one more entity, not an exception to them.

**Level: service, not platform.** No `mdm-platform` is created — D6 forbids it. What the tripwire produces is one new *service* inside an existing project, and even that one is already foreshadowed in the specs ("supplier may be in v2 supplier-service", [procurement-service/data-model.md:82](../../projects/scm-platform/specs/services/procurement-service/data-model.md#L82)).

**Ownership split (explicit, so the level question does not have to be re-derived).** The tripwire does *not* create "the supplier master" somewhere new. Business-partner identity is **already owned by erp** and stays there; what gets added is a sourcing-side consumer in scm:

| Holder | Owns | Why there |
|---|---|---|
| erp `masterdata-service` (existing, unchanged) | partner **identity** — `code`, legal/contact detail, `paymentTerms`, effective dating | already the richest schema, and `partnerType: CUSTOMER \| SUPPLIER \| BOTH` already spans both directions of trade |
| scm `supplier-service` (new at v2) | **sourcing** attributes — contract expiry, lead time, `sku_supplier_map` | procurement-domain concerns; putting them in erp would require erp to model procurement |
| wms `Partner` (existing, unchanged) | receiving-side **projection** — business number, receiving contact | plain D3: consumer-owned, keyed by `partnerCode` |

So "does MDM appear inside erp?" is answered *no new erp service* — erp is already the identity owner and needs no addition. And "is it a platform?" is answered *no* — the only new deployable is scm-side.

Note the distinction from §4 A2: rejecting **erp as the repo-wide hub** (routing every master through it) is not the same as erp owning *this* master. erp owns business partner because it genuinely holds that entity's lifecycle, not because it is a hub.

### D6 — The standalone-extraction constraint is binding on this decision

Any future proposal that introduces a component every project must call to resolve identity must first demonstrate how `scripts/sync-portfolio.sh` extraction and the per-contract "no hard dependency" degradation clauses survive it. Failing that demonstration is sufficient grounds for rejection without further architectural argument.

---

## 3. Implementation plan

**None. This ADR changes no code, no contract, and no schema.** Its entire output is the record itself plus the D5 tripwire.

Deliberately *not* scheduled: supplier schema unification, an `erp.masterdata.businesspartner.changed.v1` subscriber, a shared partner library, and any `libs/` master-schema module.

---

## 4. Alternatives considered

### A1 — A central MDM hub as a 6th platform (Rejected)

Golden records, match/merge, survivorship rules, a repo-wide identity resolution API.

Rejected on three counts: (a) it makes every project hard-depend on it, breaking the distribution strategy (§1.4, D6); (b) the problem it solves — the same entity duplicated *and joined* across systems — measurably does not exist yet for anything except supplier, and supplier is not joined (§1.3); (c) as portfolio work it demonstrates a platform the other projects can no longer be shown without.

### A2 — Promote erp `masterdata-service` to the repo-wide hub (Rejected)

Already rejected once, for the same reason, in [ADR-MONO-050](ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §4: erp is an E5 domain that owns neither the PO ledger nor the stock ledger, so routing master identity through it produces a domain-blind nano-hop. Additionally erp today has **zero** cross-project event seams — making it the hub would invent one to every other project.

**What is rejected here is erp as the *hub*, not erp as an owner.** erp remains the authoritative owner of business partner under D5, because it holds that entity's lifecycle — which is the opposite of a hub, where a service brokers masters whose lifecycles it does not own. Conflating the two is the likeliest way this alternative gets re-proposed.

### A3 — A shared master schema in `libs/` (Rejected)

A common `Partner` / `Sku` type consumed by every project. Rejected: `libs/` must stay project-agnostic, and a shared entity carrying `partnerType: SUPPLIER` and `businessNumber` is project-specific content in a shared library — HARDSTOP-03 by construction ([`platform/shared-library-policy.md`](../../platform/shared-library-policy.md)).

### A4 — Answer in conversation, record nothing (Rejected)

The cheapest option and the one this ADR exists to refuse. A federated shape that is nowhere declared reads to the next reader as an oversight, and the research behind §1.3 (four services, three schemas, a stand-in identifier, a zero-subscriber topic) would be repeated from scratch. The tripwire in D5 is the part that cannot survive in chat.

---

## 5. Consequences

**Positive**

- The existing shape is named and defensible; "we have no MDM" becomes "master data is federated, deliberately, and here is the rule".
- D2 gives a single sentence to apply to every future cross-project field, instead of re-deciding per leg.
- Standalone extraction stays intact; no project gains a dependency.
- D5 converts an open architectural worry into a checkable condition, so nobody has to keep it in their head.

**Negative**

- Supplier stays triplicated. If the tripwire fires unnoticed, three schemas drift further before anyone reconciles them — the mitigation is that all three current entry points are named in D5.
- No repo-wide "who is this partner, really" query exists, and this ADR declines to build one.
- Consumer-owned projections mean each consumer independently carries staleness and replay concerns (accepted; already true today).

**Neutral**

- No project's declared `domain` / `traits` change. No service is added or removed. No contract version moves.

---

## 6. Verification

This ADR asserts facts about the current repo; each is checkable:

| Claim | Check |
|---|---|
| No MDM component exists | no service, spec, or contract is **named** `mdm` / `master data management`. The repo-wide string search is non-zero and grows as tickets cite this ADR, but no hit is a component: matches are documents naming this ADR/decision plus the `mdm` substring inside Testcontainers' `createNetworkCmdModifier` in E2E test bases. Verify the absence of a *component* (a named service/spec/contract), not the string count. |
| SKU crosses boundaries as a code, not a UUID | `skuCode` present in the payloads of `master-events.md`, `ecommerce-fulfillment-subscriptions.md`, `replenishment-subscriptions.md`, `scm-procurement-events.md` |
| No producer-side id map exists | "No wms↔ecommerce id map is stored" — `wms-shipment-subscriptions.md:42-45` |
| Supplier is triplicated | three schemas at the §1.3 citations |
| Supplier is not joined cross-project | `procurement-service/data-model.md:82` (no FK declared); `masterdata-service/architecture.md:382-385` (no real integration) |
| The tripwire has not fired | `erp.masterdata.businesspartner.changed.v1` subscriber count = 0 (`erp-masterdata-events.md:48-51`) |
| A hub would break extraction | per-contract "Standalone-publish degradation" clauses; `TEMPLATE.md` § Cross-Project Runtime Coupling (Extraction Constraint) |

Re-run these before citing this ADR as current. Per repo practice, a prior count is a hypothesis, not a source — recount rather than inherit.

---

## 7. Outstanding follow-ups

- **None scheduled.** D5 is a trigger, not a backlog item; it produces a task when it fires, and no earlier.
- **✅ Done (TASK-MONO-435, 2026-07-20):** D2 is promoted into [`platform/service-boundaries.md`](../../platform/service-boundaries.md) § Data Boundaries, where it now binds at the layer agents actually read. Read the rule there; this ADR remains the record of *why* it was decided.

  Measuring before promoting narrowed the work: **D1 and D3 were already canonical** in that same section ("Master/reference data … is owned by exactly one service and distributed via events for local caching") — propagated, not divergent, so restating them would have split the canon. Only D2 was genuinely absent; nothing in `platform/` declared what *form* an identifier takes when it crosses a boundary.

  **D6 was deliberately not promoted here.** It constrains deployment and extraction strategy rather than service boundaries, so TASK-MONO-435 left its home unadjudicated rather than forcing it into the wrong file. Resolved below.

- **✅ Done (TASK-MONO-437, 2026-07-20):** D6 is promoted into [`TEMPLATE.md`](../../TEMPLATE.md) § Cross-Project Runtime Coupling (Extraction Constraint), placed as the sibling of § Library vs Project Boundary (Strict). The two now stand together as the pair of rules protecting extractability — one governing *where content lives*, the other *what one project may require another to be running*.

  The section this ADR originally named — § Discovery → Distribution — **does not exist as a heading** in `TEMPLATE.md`. The string occurs only in the document preamble and as a sub-step of the standalone-repo import procedure, and that sub-step would have been the wrong home (it reconciles shared-layer duplicates during an import, not architectural proposals). TASK-MONO-437 selected a concrete anchor and recorded the reasoning rather than following the name literally into the wrong subsection.

  With this, §7 carries no remaining promotion follow-ups: D1 and D3 were already canonical, D2 landed under TASK-MONO-435, D6 under TASK-MONO-437, and D4/D5 are not promotion candidates (D4 is a conditional acceptance that expires at its own tripwire; D5 *is* the tripwire).

---

## 8. Status history

| Date | Status | Note |
|---|---|---|
| 2026-07-20 | PROPOSED | Authored under TASK-MONO-433 in answer to the user's 2026-07-20 question. |
| 2026-07-20 | ACCEPTED | Flipped under TASK-MONO-434 on the user's exact-form instruction **"ADR-MONO-051 ACCEPT"**. |

**ACCEPT gate — cleared, not bypassed.** Authoring and authorisation were separate acts by separate parties: TASK-MONO-433 wrote this ADR and left it PROPOSED, the user named it explicitly, and TASK-MONO-434 performed the flip. No self-ACCEPT occurred. The gate is defined in [`platform/architecture-decision-rule.md`](../../platform/architecture-decision-rule.md) § The ACCEPTED Gate; a bare "진행" would not have cleared it.

**What acceptance binds.** The decision's conclusion is "change nothing", so acceptance authorises no implementation. It makes three things binding: **D2** (cross-project identifiers are business codes), **D6** (a hub-shaped proposal must first demonstrate that standalone extraction survives it), and **D5** (the tripwire and the answer it should get). **D4 remains conditional** — accepting the supplier triplication as "not a defect today" is not permanent approval, and it expires the moment D5 fires.

**Amending this ADR.** What was authorised is the text as it was read. Later improvements — including any strengthening of D4's reasoning — go through an amendment section (the [ADR-MONO-050](ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §7 pattern), not an in-place rewrite of §1–§6.
