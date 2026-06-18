# ADR-MONO-030 — ecommerce Multi-Vendor Marketplace SaaS (join the platform federation as the 6th customer-tenant domain + an in-tenant seller axis)

**Status:** ACCEPTED
**Date:** 2026-06-12 (PROPOSED 2026-06-12 · ACCEPTED 2026-06-12, same-session user-explicit "진행" intent on the §2 decisions as reviewed; the three forks were pre-fixed via AskUserQuestion — see § 6. **NOT self-ACCEPT** — the user directed the transition; cross-cutting tenancy promotion of an independently-published portfolio axis is a genuine architecture decision, recorded before execution.)
**Decision driver:** User request (2026-06-12) — surface the ecommerce admin in `platform-console`. Investigation surfaced that ecommerce is the **only one of the 5 live backend domains absent from the console**, and the reason is a **tenancy-model mismatch**: ecommerce is classified **single-tenant, single-seller** ([`projects/ecommerce-microservices-platform/PROJECT.md`](../../projects/ecommerce-microservices-platform/PROJECT.md) § Out of Scope — `multi-tenant` + `marketplace`), while the console and the other four domains (wms/scm/erp/finance) are **multi-tenant SaaS** under the ADR-MONO-019 customer-tenant model. The user, via AskUserQuestion (2026-06-12), chose to **promote ecommerce to a multi-vendor marketplace SaaS** — both the **outer tenant axis** (Shopify-style: each customer-tenant runs its own store) **and** the **inner seller axis** (Coupang-style: many sellers within one tenant's marketplace) — and chose the three execution forks: **(1) vertical-slice-first** (prove the architecture in product + order, not all 13 services), **(2) row-level `tenant_id` isolation**, **(3) reuse the existing platform IAM tenancy** (assume-tenant / federation / `tenant_domain_subscription`) rather than invent an ecommerce-local tenancy.
**Supersedes:** none. **Amends:** [`projects/ecommerce-microservices-platform/PROJECT.md`](../../projects/ecommerce-microservices-platform/PROJECT.md) § Out of Scope — this ADR **lifts** the `multi-tenant` and `marketplace` exclusions (HARDSTOP-04 supersession record — D7). The classification change (add the `multi-tenant` trait; reclassify the marketplace exclusion to "in-tenant seller axis in scope, settlement deferred") lands at the ACCEPTED transition, not in this PROPOSED ADR.
**Related:** [ADR-MONO-019](ADR-MONO-019-platform-console-customer-tenant-model.md) (the customer-tenant model + entitlement-trust gate this ADR makes ecommerce a 6th consumer of — the outer axis machinery is **reused, not reinvented**), [ADR-MONO-020](ADR-MONO-020-operator-multitenant-assignment.md) (operator↔tenant assignment + active-tenant token scoping — the seller/store-admin operator surface rides this), [ADR-MONO-023](ADR-MONO-023-entitlement-iam-plane-separation.md) (entitlement-plane ↔ IAM-plane separation — the precedent for D4's consumer-plane ↔ tenant/seller-admin-plane split), [ADR-MONO-025](ADR-MONO-025-abac-data-scope-generalization.md) (ABAC `org_scope` data-scope — the natural shape for **seller-scoped** data filtering, D3), [ADR-MONO-022](ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) (the existing ecommerce↔wms order-fulfillment loop — its events must eventually carry `tenant_id`; threaded in a deferred step, D5/D6), [`rules/traits/multi-tenant.md`](../../rules/traits/multi-tenant.md) M1-M7 (the isolation invariants the outer axis inherits wholesale), [`projects/ecommerce-microservices-platform/PROJECT.md`](../../projects/ecommerce-microservices-platform/PROJECT.md) (the classification this ADR changes).

> **PROPOSED (staged, sibling: ADR-019/020/023/024/025/026).** This ADR records the **decision direction** (D1-D8) + the **invariants the chosen direction inherits** (multi-tenant M1-M7; GAP/IAM as the single entitlement authority; consumer-plane ↔ tenant-plane separation; standalone-publish degradation) + a **zero-regression vertical-slice roadmap**. The ACCEPTED transition is a **separate, user-explicit-intent-gated** task; all execution steps remain **PAUSED** until ACCEPTED. The three forks the user already fixed (slice-first / row-level / reuse-IAM) are recorded as **CHOSEN-PROPOSED**; the remaining sub-decisions (D3 seller-aggregate placement, D4 plane wiring, D5 exact slice boundary) are PROPOSED for review before code, per the ADR-022 discipline (B2C ship-to / inventory SoT were reviewed at PROPOSED before the fulfillment code).

---

## 1. Context

### 1.1 The mismatch, concretely

- **ecommerce today is single-tenant, single-seller.** `projects/ecommerce-microservices-platform/PROJECT.md` declares `multi-tenant` and `marketplace` both **out of scope** (단일 판매자 / 단일 테넌트). Evidence: product-service and order-service carry **no `tenant_id`** on any entity or migration (verified 2026-06-12) — every product/order belongs to one implicit store. There is one storefront (`web-store`) and one merchant admin (`admin-dashboard`).
- **The platform around it is multi-tenant SaaS.** ADR-MONO-019 established the customer-tenant model: the account-service `tenants` table **is** the customer boundary; `tenant_domain_subscription (tenant_id, domain_key, status)` is the N:M entitlement; GAP/IAM issues a `tenant_id`-scoped, JWKS-verified token only for an entitled (subscribed + operator-assigned) tenant; each domain's `TenantClaimValidator` evolved from "tenant == domain slug" (single-tenant) to **entitlement-trust** (accept any GAP-entitled `tenant_id`, isolate by row — M1-M7). The **token-level entitlement gate** (`TenantClaimValidator`) is present in every domain — **including ecommerce's own `gateway-service`** — but the two parts of the evolution are at different stages across domains: (1) the **gate logic** — ecommerce's gate still enforces the **fixed slug** `tenant_id == 'ecommerce'` (the ADR-019 *"before"* state, per `specs/integration/iam-integration.md`), not entitlement-trust; (2) **row-level `tenant_id` on domain data** — **scm and erp carry it** (the reference implementations this slice mirrors), **wms does not** (gateway entitlement-trust only; its domain data is single-tenant per its `architecture.md`). ecommerce has **neither** the entitlement-trust gate evolution **nor** row-level `tenant_id` yet — so the promotion is, concretely, the **ADR-019 D5 evolution applied to ecommerce** (fixed-slug `'ecommerce'` → entitlement-trust + row-level `tenant_id`), with scm/erp as the precedent.

> **Correction (2026-06-12, factual — post-ACCEPTED amendment TASK-MONO-232):** the PROPOSED §1.1 said "wms/scm/erp/finance have **all completed this evolution**." That overstated wms (gateway entitlement-trust only; no row-level `tenant_id` in domain data) and omitted that **ecommerce already has a (fixed-slug) `TenantClaimValidator`**. The corrected sentence above is the accurate state. No decision changes — the reuse framing (D1) is unchanged and in fact sharpened (the promotion = ADR-019 D5 for ecommerce, scm/erp precedent).
- **Result:** ecommerce cannot appear as a console domain product, because the console catalog resolves a product's selectable tenants from `tenant_domain_subscription` (ADR-019 D4) and ecommerce has no subscription axis and no `tenant_id` to isolate by. The console gap is a **symptom**; the tenancy model is the cause.

### 1.2 Two orthogonal axes (the crux — why this is one ADR, not two)

The user's "쿠팡식 + Shopify식 둘 다" is **two independent tenancy axes** (the chat established their orthogonality):

| Axis | Model | Mechanics | Where it lives |
|---|---|---|---|
| **Outer — tenant** | Shopify (multi-tenant SaaS) | each customer-tenant runs an **isolated** store; `tenant_id` row isolation | **platform-level** — reuse ADR-019 machinery (GAP/IAM, `tenant_domain_subscription`, entitlement-trust gate) |
| **Inner — seller** | Coupang (marketplace / multi-vendor) | many sellers list products **within one tenant's** marketplace, sharing that tenant's catalog/customer pool; `seller_id` participant key | **ecommerce-domain-local** — net-new (the `marketplace` scope PROJECT.md dropped) |

Nesting: `platform → tenant A (isolated) → marketplace A → {seller a1, a2, …}`. The outer axis is **isolation** (reuse); the inner axis is **sharing + participant attribution** (new). Conflating them is the trap the chat untangled: making ecommerce "multi-tenant" in the Shopify sense is what slots it into the console; the Coupang/seller axis sits *inside* one tenant and does **not** map to the console's tenant switcher.

### 1.3 What "reuse platform IAM" (fork 3) actually buys

Because the outer axis **reuses** ADR-019's machinery, ecommerce does **not** reinvent multi-tenancy — it becomes the **6th federated domain**, exactly like wms/scm/erp/finance did:

1. `tenant_domain_subscription` gains `domain_key = 'ecommerce'` rows (which customers subscribe to a store).
2. ecommerce store-admin/seller-facing services trust a GAP/IAM-issued `tenant_id` claim (entitlement-trust gate, ADR-019 D5) and isolate by row (M1-M7).
3. The console catalog renders ecommerce as a customer-subscribable domain product **with no console code change** (ADR-019 D4 byte-stable envelope) once the subscription + gate exist.

> **Correction (2026-06-13, factual — post-ACCEPTED amendment TASK-MONO-240): "no console code change" applies to existing members, not to adding a new one.** #3 above is precise only for **flipping `available`/`displayName`/`tenants` of an EXISTING catalog member** (the dynamic-list render is data-driven, 0-change). **Adding a NEW `productKey` (`ecommerce`) requires a one-line `console-web` `ProductKeySchema` Zod enum extension** (+ its `registry-contract.test.ts` membership assertion) — the console asserts a **fixed catalog membership**, so an unknown `productKey` makes `RegistryResponseSchema.parse` throw and the whole catalog renders `degraded`. So the producer item and the consumer enum land in the **same atomic PR** (§ 6, TASK-MONO-240). **Render is 0-change; membership enum is an explicit extension** — the D1 reuse framing stands, sharpened.

This is the single biggest simplification: the heavy, risky machinery (central entitlement, dual-accept gate migration, row isolation, federation) is **already built and battle-tested across 4 domains**. ecommerce's outer-axis work is *adoption*, not *invention*.

### 1.4 Why an ADR (HARDSTOP-09 + HARDSTOP-04)

Promoting ecommerce's tenancy is a cross-cutting architecture decision spanning the ecommerce data model (every persistent entity gains `tenant_id`), its auth/identity boundary (consumer vs tenant/seller plane), the GAP entitlement registry (a new `domain_key`), and a **classification change to `PROJECT.md`** (lifting two declared out-of-scope tags). Starting any execution step without resolving D1-D8 bakes the model silently (HARDSTOP-09 #2); changing the declared classification without recording it violates HARDSTOP-04. This ADR is that record, staged PROPOSED→ACCEPTED per the ADR-019/023/025/026 sibling pattern.

---

## 2. Decision

Eight axes. Each table's first row is **CHOSEN (PROPOSED direction)**. D1/D2/D5-scope reflect the three forks the user already fixed via AskUserQuestion.

### D1 — Outer (tenant) axis: join the existing platform federation vs ecommerce-local tenancy

| Option | Mechanics | Verdict |
|---|---|---|
| **A. ecommerce becomes the 6th entitlement-trust domain (reuse ADR-019/020/023)** | `tenant_domain_subscription` gains `domain_key='ecommerce'`; ecommerce store-admin/seller services trust GAP/IAM `tenant_id` claims (entitlement-trust gate) + row-level isolation (M1-M7); console renders it via the byte-stable catalog envelope. No new entitlement authority, no new isolation key. | **CHOSEN** (user fork 3) — the customer boundary key (`tenant_id`), the entitlement authority (GAP/IAM), and the isolation invariants (M1-M7) are **already the platform standard**; 4 domains already adopted them. Reusing them keeps one isolation key, one IdP, zero schema fork — and makes the console fit fall out for free (§ 1.3). |
| B. ecommerce-local tenancy (ecommerce defines its own tenant entity/registry) | ecommerce owns a `stores`/`tenants` table; its own auth-service issues tenant-scoped tokens | Rejected — forks the entitlement authority (drift vs GAP), forks the isolation key, and requires console-side integration glue to reconcile ecommerce-tenants with platform-tenants. Re-solves a solved problem; loses the "free console fit." |

### D2 — Isolation strategy: row-level `tenant_id` vs schema/DB-per-tenant

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Row-level `tenant_id` (NOT NULL) + query filter, M1-M7** | Every persistent ecommerce entity gains `tenant_id`; reads filter `WHERE tenant_id = <claim>`; 404-over-403 (M3); async propagation (M5); cross-tenant-leak regression IT (M6). Identical shape to wms/scm/erp/finance. | **CHOSEN** (user fork 2) — matches the platform's M1-M7 standard exactly, minimal migration cost, and the dual-accept migration pattern (ADR-019 D6) is already proven for it. |
| B. Schema-per-tenant | a DB schema per tenant | Rejected — stronger isolation than the portfolio needs; multiplies Flyway/migration + operational surface; no other domain uses it (consistency cost). |
| C. DB-per-tenant | a DB instance per tenant | Rejected — SaaS-scale operational overkill; portfolio-inappropriate; diverges from every existing domain. |

### D3 — Inner (seller/vendor) axis: new ecommerce-domain-local `seller` aggregate

The marketplace axis is **net-new domain work** (the `marketplace` scope PROJECT.md dropped). It is **ecommerce-local** (not a platform concern): sellers exist *within* a tenant's marketplace.

| Option | Mechanics | Verdict |
|---|---|---|
| **A. `seller` aggregate in ecommerce, nested under `tenant_id`; `seller_id` ownership/attribution; seller-scoped reads via ABAC (ADR-025 `org_scope` shape)** | A new ecommerce `seller` entity keyed `(tenant_id, seller_id)`; product ownership = `(tenant_id, seller_id)`; order **line** attribution to the owning seller; a seller's own-data view filtered ABAC-style (ADR-025 `org_scope` is the natural reuse). Composite scoping: `tenant_id` isolates (D2), `seller_id` attributes/filters within the tenant. | **CHOSEN** — keeps the two axes cleanly layered (isolate-then-attribute); reuses the ABAC data-scope shape for seller-own-data; settlement/commission deferred (D5) so the aggregate stays small for the slice. |
| B. Seller as a platform-IAM tenant (sellers = sub-tenants) | model each seller as a nested platform tenant | Rejected — sellers share the tenant's catalog/customer pool (the *defining* marketplace property); making them isolated tenants contradicts the inner axis's "sharing" semantics and re-introduces the axis-mismatch the chat resolved. Seller is a participant, not an isolation boundary. |
| C. Seller as a flat `seller_id` column only (no aggregate) | just a column, no lifecycle | Deferred-partial — the slice MAY start column-only, but the ADR records the aggregate (onboarding/lifecycle) as the production form so a later step is additive, not a redesign. |

### D4 — Identity plane separation: consumer-shopper plane vs tenant/seller-admin plane

ecommerce has **two distinct actor classes** — this is the subtlety reuse-IAM forces.

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Two planes: consumers stay on ecommerce `auth-service`; tenant + seller-admin (operator) actions use platform IAM entitlement-trust** | **Shoppers** (end consumers) authenticate via ecommerce's existing `auth-service` (a consumer concern — consumers are NOT platform tenants/operators). **Store operators / sellers** managing the marketplace authenticate via **platform IAM** (GAP), carrying the `tenant_id` (+ seller scope) claim the entitlement-trust gate trusts. Mirrors ADR-023 entitlement-plane ↔ IAM-plane separation. | **CHOSEN** — consumers and operators are different identity populations on different planes; conflating them would either drag consumers into the platform IdP (wrong) or fork tenant identity into ecommerce (D1-B, rejected). The two-plane split is exactly the ADR-023 precedent. |
| B. Single plane (everything through platform IAM) | consumers also become IAM principals | Rejected — pollutes the platform IdP with B2C consumer accounts (the same pollution D2-c rejected in ADR-022 for partner master); consumers have no tenant/operator semantics. |
| C. Single plane (everything through ecommerce auth-service) | ecommerce auth issues tenant claims too | Rejected — collapses into D1-B (ecommerce-local tenancy); forks the entitlement authority. |

> **Correction (2026-06-12, factual — post-ACCEPTED amendment TASK-MONO-232): the plane mechanism, against the live `specs/integration/iam-integration.md`.** The PROPOSED D4 was built on a wrong premise about where consumers authenticate, so two rows are corrected — though **the decision *intent* (consumer plane ≠ tenant/seller-admin plane) stands**:
> - **D4-A mechanism corrected.** ecommerce does **not** keep consumers on a separate `auth-service`. Both populations already authenticate via **platform IAM**, split by the existing **`account_type` claim (`CONSUMER` | `OPERATOR`)** enforced at the gateway (`AccountTypeEnforcementFilter` + `JwtHeaderEnrichmentFilter`, TASK-BE-131); consumers are created via IAM **self-service signup**, operators via **provisioning**; the standalone ecommerce `auth-service` is **slated for removal** (TASK-BE-132).
> - **D4-B rejection premise corrected.** "Single plane through IAM pollutes the IdP with B2C consumers" was **wrong** — IAM is **designed** to hold them (`tenant_type=B2C`, `account_type=CONSUMER`; iam-integration.md §Tenant Identity). So putting consumers in IAM is not pollution; it is the platform's intended model.
> - **Corrected D4 decision:** **one IdP (platform IAM), two planes by `account_type`** — `CONSUMER` (shoppers, `/api/**`) vs `OPERATOR` (store/seller admins, `/api/admin/**`). The **seller axis (`seller_id`) lives inside the `OPERATOR` plane**. Composite identity = `tenant_id` (which store) × `account_type` (consumer/operator) × `seller_id` (which seller; operator-plane only). This is **cleaner** than the PROPOSED text (no parallel auth-service to maintain) and preserves the D4 intent (consumers carry no tenant/operator/seller authority). It supersedes the PROPOSED D4-A "separate auth-service" mechanic and the D4-B "pollution" rationale; D4-C (ecommerce-local tenancy) remains rejected (D1).
>
> Net for the slice: the **promotion evolves ecommerce's existing fixed-slug `TenantClaimValidator`** (`tenant_id == 'ecommerce'`) **to entitlement-trust** (ADR-019 D5) and adds row-level `tenant_id` (scm/erp precedent); `account_type` is already the plane gate; `seller_id` is the net-new axis within the operator plane.

### D5 — Scope: vertical slice (product + order) vs full 13-service transition

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Vertical slice = product-service + order-service, both axes proven end-to-end** | The slice proves the **whole nested model** in two services: a seller (in tenant A) lists a product → a customer orders it → the order is attributed `(tenant A, seller a1)` → tenant B sees none of it (isolation IT). Outer axis (`tenant_id` + entitlement-trust gate + row isolation) **and** inner axis (`seller_id` ownership/attribution) both land. **Deferred:** settlement/commission (D3 lifecycle), console integration (the original ask — lands after the slice is GREEN), the remaining 11 services, and threading `tenant_id` through the ADR-022 fulfillment events. | **CHOSEN** (user fork 1) — completes the architecture demonstration inside two services; the other 11 services + settlement + console are then "repeat the proven pattern," recorded honestly rather than silently truncated. Best portfolio ROI; main stays GREEN throughout. |
| B. Staged full transition (slice → all 13) | roadmap names all 13 | Deferred-not-rejected — the slice roadmap (D6) names the remaining services + settlement + console as explicit follow-ups; the user may extend after the slice proves out. |
| C. Big-bang full transition | flip all 13 services + seller + settlement at once | Rejected — transiently breaks main across the whole project; violates the BE-303 "0 failing required at merge" + ADR-019 D6 dual-accept zero-regression discipline. |

### D6 — Migration phasing: zero-regression, dual-accept, backward-compatible default seed

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Backward-compatible, each step independently main-GREEN; default-tenant + default-seller seed reproduces today's single-store behavior** | **Step 0 (doc):** ADR PROPOSED → ACCEPTED (user-gated) + `PROJECT.md` classification change (D7). **Step 1 (specs/contracts):** ecommerce product/order specs gain `tenant_id` + `seller_id`; `tenant_domain_subscription` `domain_key='ecommerce'` contract; consumer↔operator plane (D4) documented. **Step 2 (outer axis, product+order):** add `tenant_id` (NOT NULL) + Flyway; **seed a single default tenant** so all existing data maps to it (today's behavior byte-identical); entitlement-trust gate **dual-accepts** absent-claim→default-tenant (standalone, D8) ∪ GAP-entitled `tenant_id`; cross-tenant-leak regression IT (M6). **Step 3 (inner axis):** `seller` aggregate/`seller_id` + a **single default seller** seed (existing products → default seller, behavior unchanged); product ownership + order-line attribution + seller-scoped read (ADR-025 shape). **Step 4 (deferred follow-ups):** settlement/commission, console integration (the original ask), remaining 11 services, ADR-022 fulfillment `tenant_id` threading. | **CHOSEN** — each step is independently mergeable and main-GREEN; the default-tenant/default-seller seeds keep the standalone single-store story intact (D8); mirrors the ADR-019 D6 dual-accept window + the ADR-022 additive-and-degradable discipline. |
| B. Big-bang | one PR | Rejected (= D5-C rationale). |

### D7 — `PROJECT.md` classification change (HARDSTOP-04 supersession record)

ecommerce `PROJECT.md` declares `multi-tenant` and `marketplace` **out of scope**. This ADR changes that classification:

- **Add the `multi-tenant` trait** → loads `rules/traits/multi-tenant.md` (M1-M7) into ecommerce's rule layers. (This is the rule basis the outer axis must satisfy; adding the trait is what makes M1-M7 binding rather than aspirational.)
- **Reclassify the `marketplace` exclusion** → from "out of scope (단일 판매자)" to "**in-tenant seller axis in scope; seller settlement/commission deferred**" (D3/D5). Full marketplace economics (settlement/payout/commission) remain a named follow-up, not v1.

Per HARDSTOP-04, this supersession is **recorded here** and **applied** when the change is code-consistent, not silently. The PROPOSED ADR does not edit `PROJECT.md`.

> **Application-timing refinement (ACCEPTED, 2026-06-12 — user decision, decision-body unchanged):** the PROPOSED text placed the `PROJECT.md` classification change at **Step 0** (the ACCEPTED transition). At ACCEPTED this was **re-sequenced to Step 2** (the outer-axis execution step, when product+order gain `tenant_id`). Rationale: adding the `multi-tenant` trait makes `rules/traits/multi-tenant.md` **M1** (every persistent row carries `tenant_id` NOT NULL) mandatory **project-wide**, but the slice migrates only 2 of 13 services — applying the trait at Step 0 would declare the **11 not-yet-migrated services** M1-violating for the entire migration window (the same "broken intermediate" hazard ADR-019 D6 staged the riskiest change last to avoid). The **decision** (lift the `multi-tenant`+`marketplace` exclusions; become a multi-vendor SaaS) is **unchanged**; only the **application step** moves Step 0 → Step 2. Consequently the ACCEPTED transition (Step 0) is a **pure status flip — it does NOT edit `PROJECT.md`**; the trait change lands with the outer-axis code (Step 2). When the trait is added, the 11 not-yet-migrated services are the named migration backlog (Step 4 / `D5-B` extension), not silent violations.

### D8 — Standalone-publish degradation

ecommerce is published as an independent portfolio repo. Without the platform IAM/federation present (standalone deploy), it must **degrade to today's single-tenant, single-seller behavior**: the entitlement-trust gate's absent-`tenant_id`-claim path resolves to the **default tenant** (Step 2 seed), and the absent-`seller` path resolves to the **default seller** (Step 3 seed). The multi-vendor SaaS capability is **additive** to ecommerce's standalone story, never a hard runtime dependency — the same degradation discipline as ADR-022 D8 (no wms ⇒ manual shipping) and ADR-025 (empty `org_scope` ⇒ net-zero unfiltered).

---

## 3. Consequences

### 3.1 Hard invariants this ADR carries

- **multi-tenant M1-M7 inherited wholesale (D1/D2)** — `tenant_id` the single isolation key (M1); 3-layer gate (M2); 404-over-403 (M3); enumeration defense (M4); async propagation across the ecommerce event mesh incl. ADR-022 fulfillment events (M5, threaded in Step 4); cross-tenant-leak regression IT (M6). The outer axis adopts the *existing* standard; it does not invent a variant.
- **GAP/IAM stays the single entitlement authority (D1/D4)** — ecommerce reads entitlement from `tenant_domain_subscription`; it does not fork a second tenant registry or IdP. Consumers remain on ecommerce auth-service (D4) — a separate plane, not a second entitlement authority.
- **Two clean axes (D3)** — `tenant_id` isolates (outer), `seller_id` attributes within a tenant (inner). Composite scoping is *isolate-then-attribute*; the seller axis never crosses a tenant boundary.
- **Console fit is a consequence, not a goal (§ 1.3)** — once the subscription (`domain_key='ecommerce'`) + entitlement-trust gate exist, the console renders ecommerce with the byte-stable ADR-019 D4 envelope. The original ask is satisfied by the outer axis as a side effect; console integration is the named Step-4 follow-up. **Correction (2026-06-13, TASK-MONO-240):** the *render* is byte-stable/0-change, but registering a **new** catalog `productKey` is **not** zero console-web change — it requires the one-line `console-web` `ProductKeySchema` Zod enum membership extension (fixed-catalog guard; § 6 + § 1.3 correction note). The producer catalog Entry + consumer enum land in one atomic PR.
- **Standalone degradation (D8)** — default-tenant/default-seller seeds keep ecommerce's independent-repo single-store demo intact.

### 3.2 Costs / risk surface

- **Broadest data-model change in the project's history** — `tenant_id` on every persistent entity is intrusive even scoped to the slice; the default-tenant seed + dual-accept gate (D6) are what keep it zero-regression.
- **Two identity planes (D4)** to keep straight — a wiring mistake could leak consumer auth into the operator plane or vice-versa; the ADR-023 precedent + an explicit plane test mitigate.
- **Marketplace economics deferred (D5/D7)** — settlement/commission is the genuinely new domain mass; deferring it keeps the slice tractable but the ADR must name it so a reader doesn't read "marketplace" as "settlement done."
- **Reversibility** — moderate. The outer axis (column + gate) is additive and degradable (D8); the seller aggregate is additive. Removing them reverts to single-store. Lower reversibility than ADR-022 (which was pure additive consumers) because `tenant_id NOT NULL` touches schemas.

### 3.3 What this PROPOSED ADR does NOT do (deferred to ACCEPTED + execution)

- Does **not** add `tenant_id`/`seller_id` to any entity, migration, or seed. (Steps 2/3.)
- Does **not** edit `PROJECT.md` classification. (Step 0, at ACCEPTED.)
- Does **not** add the `domain_key='ecommerce'` subscription or any console integration. (Steps 1/4.)
- Does **not** change ecommerce auth-service or any plane wiring. (Steps 1/2.)
- Does **not** build seller settlement/commission, touch the other 11 services, or thread `tenant_id` through ADR-022 fulfillment events. (Step 4.)
- Does **not** execute the ACCEPTED transition — separate user-explicit-intent-gated task (sibling ADR-019→MONO-153, ADR-025→MONO-213, ADR-026→MONO-217).

### 3.4 Post-ACCEPTED execution roadmap (sketch; finalised at ACCEPTED)

0. **`TASK-MONO-2xx`** — ADR-030 PROPOSED → ACCEPTED transition (doc-only, user-gated) **+ `PROJECT.md` classification change (D7)**. Model = **Opus** (classification + ADR finalisation).
1. **`TASK-BE-xxx`** (ecommerce project-internal) — product/order specs + `tenant_id`/`seller_id` model + `tenant_domain_subscription` `domain_key='ecommerce'` contract + D4 plane doc. Source-of-Truth-first. Model = **Opus** (contract + tenancy design).
2. **`TASK-BE-xxx`** (ecommerce, outer axis) — `tenant_id` + Flyway + default-tenant seed + entitlement-trust dual-accept gate + M6 cross-tenant-leak IT, in product-service + order-service. Model = **Opus** (isolation — per ADR-019 D6 "isolation → Opus").
3. **`TASK-BE-xxx`** (ecommerce, inner axis) — `seller` aggregate/`seller_id` + default-seller seed + product ownership + order-line attribution + seller-scoped read (ADR-025 shape). Model = **Opus**.
4. **Deferred follow-ups** (each its own ADR-extension or task as it lands): seller settlement/commission; console integration (`domain_key` subscription seed + catalog render — the original ask); the remaining 11 services; ADR-022 fulfillment `tenant_id` threading; **seller onboarding flow + real IAM provisioning (facet f)**. Model = **Opus** (settlement/isolation) / **Sonnet** (console render, pattern-repeat services).
   - **facet f — REALIZED** by [ADR-MONO-042](ADR-MONO-042-ecommerce-seller-onboarding-iam-provisioning.md) (ACCEPTED 2026-06-18) / TASK-BE-402: seller onboarding mints a real IAM seller-operator account + born-unified identity (ADR-036 reuse), fail-soft, with seller-lifecycle deactivation locking the backing account; authz net-zero (D6). The seller is no longer a trusted-claim shim.

No step beyond 4 is scoped here; multi-region / cross-tenant marketplace settlement would each be a new ADR.

---

## 4. Alternatives Considered

The D1-D8 tables enumerate per-axis alternatives. Cross-cutting alternatives:

- **(b) Keep ecommerce single-tenant; add a thin read-only order/fulfillment slice to the console** (the chat's lighter option). Rejected **as the chosen direction** but recorded as the cheaper fallback — it completes the console's order→fulfillment narrative without any tenancy change (ecommerce framed as a fixed single-store leaf outside the tenant switcher). The user explicitly chose the larger multi-vendor SaaS direction over this; this ADR records that choice. If the slice (D5) proves too costly, (b) remains the documented retreat.
- **Marketplace (seller axis) only, no multi-tenant.** Rejected (chat-established) — the seller axis is orthogonal to the console's tenant axis; adding it alone does **not** make ecommerce fit the console and introduces a nested-tenancy mismatch of its own.
- **Multi-tenant only, no seller axis (plain Shopify).** Viable but narrower — it would slot ecommerce into the console cleanly (outer axis only) without the marketplace. The user chose **both**; the seller axis (D3) is additive on top of the outer axis and is deferred-decomposable (Step 3 after Step 2), so a "Shopify-only" outcome is reachable by stopping after Step 2 if desired.
- **ACCEPTED now, skip PROPOSED.** Rejected — the seller-aggregate placement (D3), plane wiring (D4), and exact slice boundary (D5) warrant review before code (ADR-022 reviewed B2C ship-to / inventory SoT at PROPOSED). self-ACCEPT prohibited.

---

## 5. Relationship to prior ADRs

| | ADR-MONO-019 | ADR-MONO-023 | ADR-MONO-025 | ADR-MONO-022 | **ADR-MONO-030 (this)** |
|---|---|---|---|---|---|
| Axis | customer-tenant model + entitlement-trust gate | entitlement-plane ↔ IAM-plane separation | ABAC `org_scope` data-scope | ecommerce↔wms order-fulfillment loop | **ecommerce multi-vendor marketplace SaaS** |
| Relationship | **reused** — ecommerce becomes the 6th consumer of the customer-tenant machinery (D1) | **precedent** — the consumer-plane ↔ tenant/seller-admin-plane split (D4) | **reused** — the seller-scoped read shape (D3) | **threaded** — its fulfillment events gain `tenant_id` in a deferred step (D5/D6 Step 4) | — |

ecommerce is the **last of the 5 backend domains** to adopt the ADR-019 customer-tenant model; this ADR brings it into the federation (outer axis) and adds the in-tenant marketplace (inner axis) that the other domains do not have.

---

## 6. Status Transition History

Append-only.

| Date | Transition | Decision direction | User intent quote | PR(s) |
|---|---|---|---|---|
| 2026-06-12 | created PROPOSED | D1 = join platform federation as 6th entitlement-trust domain (reuse ADR-019, user fork 3); D2 = row-level `tenant_id` M1-M7 (user fork 2); D3 = new ecommerce-local `seller` aggregate nested under `tenant_id`, ABAC seller-scoped read, settlement deferred; D4 = two planes — consumers on ecommerce auth-service, tenant/seller-admin on platform IAM (ADR-023 precedent); D5 = vertical slice product+order, both axes end-to-end, console/settlement/11-services/fulfillment-threading deferred (user fork 1); D6 = zero-regression dual-accept + default-tenant/default-seller seed; D7 = lift `PROJECT.md` `multi-tenant`+`marketplace` out-of-scope (HARDSTOP-04, at ACCEPTED); D8 = standalone degrades to single-store | "(a+쿠팡) 멀티벤더 SaaS 둘 다" + AskUserQuestion forks: 수직 슬라이스 먼저 / Row-level tenant_id / 기존 플랫폼 IAM 재사용 → "진행" | #1365 (TASK-MONO-230) |
| 2026-06-12 | PROPOSED → ACCEPTED | D1-D8 **finalised** as the reviewed PROPOSED direction (ACCEPTED *finalises*, does not re-decide): the sub-decisions left open at PROPOSED — D3 seller-aggregate placement (A), D4 plane wiring (A two-plane), D5 slice boundary (A product+order) — are accepted **as-recommended**, byte-unchanged. **One refinement**: D7's `PROJECT.md` classification change re-sequenced Step 0 → Step 2 (apply the `multi-tenant` trait with the outer-axis code, not at this flip — see the D7 application-timing note; **decision unchanged, application step moved**, user decision). ACCEPTED is a pure status flip — **no `PROJECT.md` edit, no code**. | "진행" (on merge #1365 → 3-dim → ACCEPTED) + AskUserQuestion: D7 timing = "Step 2로 미룸" | #1366 (TASK-MONO-231) |
| 2026-06-12 | ACCEPTED — factual correction (D4 mechanism + §1.1) | **No decision reversed.** Pre-Step-1 verification against the live `specs/integration/iam-integration.md` found two factual errors in the ACCEPTED text: (§1.1) "wms/scm/erp/finance all completed the row-level evolution" overstated wms (gateway entitlement-trust only) and omitted ecommerce's existing fixed-slug `TenantClaimValidator`; (D4) consumers do **not** stay on a separate ecommerce `auth-service` — both planes already authenticate via **platform IAM**, split by the existing **`account_type` (CONSUMER\|OPERATOR)** claim (auth-service slated for removal, TASK-BE-132), and D4-B's "IdP pollution" rejection premise was wrong (IAM is designed for B2C consumers, `tenant_type=B2C`). Corrected D4 = **one IdP, two planes by `account_type`**, seller axis inside OPERATOR plane; the D4 *intent* (consumer plane ≠ operator/seller plane) stands. Recorded as additive correction notes under §1.1 + D4. | (pre-Step-1 verification reading the live ecommerce iam-integration spec) | #<this PR> (TASK-MONO-232) |
| 2026-06-13 | ACCEPTED — factual correction (Step 4 console-fit premise) | **No decision reversed.** Implementing Step 4 facet (a) (console catalog integration, TASK-MONO-240) found §1.3 #3 / §3.1 "console renders ecommerce **with no console code change** / console fit falls out for free" is true only for **flipping `available`/`displayName`/`tenants` of an EXISTING catalog member** — the dynamic-list render is data-driven (0-change). **Adding a NEW `productKey`, however, requires a one-line `console-web` `ProductKeySchema` Zod enum extension** (+ its `registry-contract.test.ts` membership assertion): the console asserts a **fixed catalog membership** (`registry-contract.test.ts` "rejects unknown productKey"), so an unknown `productKey` makes `RegistryResponseSchema.parse` throw → the whole catalog renders `degraded`. So the producer item (admin-service `ProductCatalog` Entry + `tenant_domain_subscription` `domain_key='ecommerce'` V0022 seed) and the consumer enum extension land in the **same atomic PR**. D1 reuse framing / D4 / D5 unchanged (sharpened: **render is 0-change; membership enum is an explicit extension**). Recorded as additive correction notes under §1.3 + §3.1. | (Step-4 facet-a implementation: producer catalog Entry + consumer Zod enum land atomically) | #<this PR> (TASK-MONO-240) |

(PROPOSED row appended 2026-06-12 per the ADR-019/023/025/026 staged-child format. ACCEPTED row appended same-session on the user's explicit "진행" intent — NOT self-ACCEPT. D1-D8 decision bodies byte-unchanged; the only as-accepted change is the D7 **application-timing** refinement (Step 0 → Step 2), recorded as an additive note under D7. The 2026-06-12 correction row is a **factual amendment** (TASK-MONO-232) — it corrects the §1.1 state-of-the-platform description and the D4 plane *mechanism* against the live integration spec; **no decision is reversed** (D4 intent + D1 reuse framing preserved, in fact sharpened). The seller-placement / plane-wiring(corrected) / slice-boundary sub-decisions are finalised at the recommended directions.)

---

## 7. Provenance

- User request 2026-06-12 (surface ecommerce admin in console) + the chat that untangled the two tenancy axes + AskUserQuestion (slice-first / row-level / reuse-IAM forks).
- HARDSTOP-09 #2 + HARDSTOP-04 (`platform/hardstop-rules.md`) — the mandate for an ADR + PAUSE-until-ACCEPTED on a cross-cutting tenancy decision that also changes a declared `PROJECT.md` classification.
- ADR-MONO-019 (`docs/adr/`) — the customer-tenant model + entitlement-trust gate the outer axis reuses (D1). ADR-MONO-020 — operator↔tenant assignment (seller-admin operator surface). ADR-MONO-023 — entitlement-plane ↔ IAM-plane separation (D4 precedent). ADR-MONO-025 — ABAC `org_scope` (D3 seller-scoped read shape). ADR-MONO-022 — the ecommerce↔wms fulfillment loop whose events gain `tenant_id` (D5/D6 Step 4).
- `rules/traits/multi-tenant.md` M1-M7 — the isolation invariants the outer axis inherits (D2/D7).
- `projects/ecommerce-microservices-platform/PROJECT.md` § Out of Scope — the `multi-tenant` + `marketplace` declarations this ADR lifts (D7). Code evidence (§ 1.1): product-service + order-service carry no `tenant_id` (verified 2026-06-12).

분석=Opus 4.8 / 구현 권장=Opus (cross-cutting tenancy promotion: outer-axis federation adoption + inner-axis marketplace aggregate + plane separation + `PROJECT.md` classification change under HARDSTOP-04/09; vertical-slice zero-regression migration mirroring ADR-019 D6 dual-accept + ADR-022 additive-degradable discipline).
