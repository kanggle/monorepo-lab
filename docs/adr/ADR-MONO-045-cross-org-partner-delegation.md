# ADR-MONO-045 — Cross-Org Partner Delegation (a first-class tenant↔tenant partnership that lets a partner organization operate a **bounded, attenuated, revocable-as-a-unit** slice of another tenant, with **relationship-scoped offboarding** — the first privilege origination that crosses the org boundary ADR-024 and ADR-044 both keep inside a single tenant)

**Status:** PROPOSED

**Date:** 2026-07-04

**History:** PROPOSED 2026-07-04 (TASK-MONO-327 — records the **cross-org partner-delegation model**: how a **partner/supplier organization** (tenant B) comes to operate a bounded slice of **another organization's** tenant (tenant A) as a *managed relationship*, and how that access is **attenuated** (never A's full admin), **partner-governed** (B manages which of B's own operators participate), and **revocable as a unit** (terminating the partnership — or B offboarding its employee — removes the A-access without A tracking B's staffing). Decisions D1–D8, **CHOSEN-PROPOSED** direction per the reasoning below; the ACCEPTED transition is a separate user-explicit-intent-gated task, mirroring the ADR-019/020/023/024/030/042/044 staged-child pattern. **No implementation in this task — decision record + impact scope + migration roadmap only. Self-ACCEPT prohibited.**)

**Decision driver:** The portfolio's identity + multi-tenant model, **as verified 2026-07-04**, already realizes three of the four "one person, many hats" facets a real enterprise user has: (1) a **unified identity** that is both a storefront consumer and an operator ([ADR-MONO-032](ADR-MONO-032-unified-identity-roles-model.md) / [ADR-MONO-036](ADR-MONO-036-born-unified-identity-provisioning.md) — one Global Account, roles derived); (2) **owner-admin of one's own company's tenant** ([ADR-MONO-044](ADR-MONO-044-self-service-tenant-onboarding.md) self-service onboarding → first `TENANT_ADMIN`); (3) **operator-employee of an employer's tenant** ([ADR-MONO-020](ADR-MONO-020-operator-multitenant-assignment.md) `operator_tenant_assignment` + [ADR-MONO-024](ADR-MONO-024-tenant-admin-delegation.md) `admin_operator_roles.tenant_id` — one operator holds N tenant-scoped grant rows, the tenant switcher enumerates them, assume-tenant re-scopes `tenant_id` + `entitled_domains` per selection). These are **fully supported and confined** (BE-467/468 tenant-confinement; ADR-024 D2/D3 no-escalation; multi-tenant M1-M7 row isolation; audited via `admin_actions.permission_used` + active tenant). **But the fourth facet — a partner/supplier organization operating another company's tenant — has NO first-class model.** The only way to express it today is to grant *individual* operators of company B a grant row on company A's tenant (`operator_tenant_assignment` / `admin_operator_roles` with `tenant_id = A`). That "individual-grant" workaround has two load-bearing defects the verification confirmed: (a) **A must track B's staffing** — A grants specific B-employees and must remember to revoke when they leave B (there is no automatic linkage to B's employment lifecycle; deprovisioning is N manual per-`(operator, tenant)` unassigns with no cascade — ADR-020 § 3.3 / BE-468 revocation is per-operator-per-tenant, manual); and (b) there is **no relationship object** to reason about, bound, audit, or terminate as a unit. Building a real supplier/partner collaboration during implementation would silently bake a **cross-org privilege-origination model** (HARDSTOP-09): whether B's access into A is attenuated (never A's `TENANT_ADMIN`/`SUPER_ADMIN`), whether the origination is two-sided-consented, whether B can re-delegate A's scope onward (confused-deputy / transitive delegation), and how the `SUPER_ADMIN` net-zero + no-escalation + M1-M7 invariants survive when **B's admin now originates operator access into A's tenant** — each is load-bearing and must not be chosen implicitly. This ADR is that decision record.

**Supersedes:** none. **Amends:** [ADR-MONO-024](ADR-MONO-024-tenant-admin-delegation.md) § D4-B (additive — records that delegated administration, which ADR-024 confines *"within the actor's own tenant only"* (cross-tenant sub-delegation "structurally impossible"), gains **one** carefully-bounded cross-tenant form: a **partnership-mediated** delegation from a host tenant to a partner tenant. ADR-024 D1–D7 bodies byte-unchanged; the within-tenant confinement remains the default and the ONLY path for non-partnership delegation — HARDSTOP-04 additive-supersession discipline). Also **amends** [ADR-MONO-020](ADR-MONO-020-operator-multitenant-assignment.md) § D6 (additive — the assignment/revocation lifecycle gains a *relationship-scoped* driver: a partnership's termination cascade-invalidates the derived cross-org grants; ADR-020 bodies unchanged). **Reconciles:** none (PROPOSED scopes the architecture; no code/spec/contract/seed changes — D8 preserves current shapes during the staged window; reconciliation lands at post-ACCEPTED execution tasks, never inside this ADR).

**Related:** [ADR-MONO-024](ADR-MONO-024-tenant-admin-delegation.md) (**the within-tenant delegated-admin boundary this ADR extends across the org boundary** — its `TENANT_ADMIN` grant-row scoping + D2/D3 confinement/no-escalation are **reused, capped, and attenuated**, not re-decided; only the *cross-org origination* is new), [ADR-MONO-020](ADR-MONO-020-operator-multitenant-assignment.md) (`operator_tenant_assignment` — the multi-tenant grant substrate the derived partner grants ride on; assume-tenant re-scoping unchanged), [ADR-MONO-044](ADR-MONO-044-self-service-tenant-onboarding.md) (**the closest precedent** — self-service *within-org* privilege origination, new-tenant-confined; this ADR is the *cross-org* analogue, and its D2 blast-radius-confinement stance is revisited here at org scale), [ADR-MONO-042](ADR-MONO-042-ecommerce-seller-onboarding-iam-provisioning.md) (**a deliberately-rejected shape to contrast** — the ecommerce seller/supplier is a *participant inside one platform tenant* keyed `(tenant_id, seller_id)`, NOT a tenant↔tenant relationship; this ADR is what ADR-042 is *not*), [ADR-MONO-032](ADR-MONO-032-unified-identity-roles-model.md) / [ADR-MONO-036](ADR-MONO-036-born-unified-identity-provisioning.md) (unified identity — B's operators are the SAME identities, gaining an A-scoped facet, never new principals), [ADR-MONO-023](ADR-MONO-023-entitlement-iam-plane-separation.md) (IAM ↔ entitlement plane separation — what B may *do* in A is still gated by A's `entitled_domains`; the partnership delegates IAM authority, not A's subscriptions), `projects/iam-platform/specs/services/admin-service/rbac.md` (the confinement evaluator the cross-org cap extends), `rules/traits/multi-tenant.md` M1-M7 (the row-isolation this must never weaken).

---

> **PROPOSED (staged, sibling: ADR-019/020/023/024/030/042/044).** This ADR records the **decision direction** (D1–D8) + the **hard invariants the chosen direction inherits** (multi-tenant M1-M7 untouched; `SUPER_ADMIN` net-zero; cross-org delegation attenuated ≤ what the host delegated ≤ what the host itself holds; two-sided consent; no transitive re-delegation; ADR-023 plane separation preserved) + a **zero-regression roadmap**. The ACCEPTED transition is a **separate user-explicit-intent-gated task** (sibling precedent: ADR-024→MONO-209, ADR-044→MONO-325). **Self-ACCEPT prohibited.** No implementation in this task.

---

## 1. Context

### 1.1 What exists (verified 2026-07-04) — three hats realized, the fourth missing

A single person's identity can today hold, simultaneously and safely:

- **Consumer + operator facets on one Global Account** — ADR-032/036. Born-unified `resolveOrCreate` keys the identity on `(tenant, normalized-email)` so consumer-side and operator-side provisioning converge on the same principal (no merge step). One SSO session, roles derived.
- **N tenant-scoped operator grants** — ADR-020 `operator_tenant_assignment` (PK `(operator_id, tenant_id)`, multi-row) is the *operate*-axis; ADR-024 `admin_operator_roles.tenant_id` (multi-row) is the *administer*-axis. The tenant switcher enumerates the operator's effective tenant set (home ∪ assignments); assume-tenant re-mints a **single-`tenant_id`** token per selection carrying that tenant's `entitled_domains` only (least-privilege, ADR-020 D3-A).
- **Confinement across those grants** — `AdminGrantScopeEvaluator.isTenantInAdminScope` returns true only for `'*'` or the exact target tenant, consulting **only the grant scope, never the assignment scope**; fail-closed; audited. BE-467/468 confine account mutation + session revoke to the owning tenant. Accumulating grant rows accumulates *access to each named tenant*, **never reach from A into B**.

So "consumer + owns-my-company + employed-by-a-company" (hats 1–3) is **already the model**, mirroring AWS (one identity, cross-account roles) / GCP (one identity, roles per project).

### 1.2 The gap — the fourth hat has no primitive (the origination row ADR-024 and ADR-044 both stop short of)

| Concept | AWS | GCP / Slack | platform portfolio (this ADR) |
|---|---|---|---|
| **A partner ORG operates a bounded slice of ANOTHER org's boundary** | **Delegated administrator** across accounts (Organizations) | **Slack Connect** shared channels / GCP cross-org IAM grants | **Cross-org partnership**: host tenant A delegates a bounded `{domains}×{roles}` slice to partner tenant B; **B governs which of B's operators act in A** |
| Origination | Management account enables a delegated-admin account | Two-sided invite/accept | **Two-sided consent**: A's `TENANT_ADMIN` invites with a scope; B's `TENANT_ADMIN` accepts |
| Blast radius of the delegation | The delegated services only | The shared channel / granted roles only | **`delegated_scope` only** — never A's `TENANT_ADMIN`/`SUPER_ADMIN`; zero reach into A beyond the slice |
| Offboarding | Remove the delegated-admin account / the person leaves the partner | Leave the connection / the person leaves | **Relationship-scoped**: terminate the partnership → cascade-revoke B's operators from A; B offboarding its employee → that person loses A-access via B's normal operator lifecycle |

ADR-024 answered *"how a tenant-admin manages operators **within its own tenant**"* and fixes cross-tenant sub-delegation as **structurally impossible** (D4-B). ADR-044 answered *"how the first admin of a **new** tenant comes to exist, self-service"* and confines the self-grant to **that new tenant only, zero reach into existing tenants** (D2). **Neither crosses the boundary between two *existing, independently-owned* tenants.** The fourth hat requires exactly that crossing — the first cross-org privilege origination — and today it is only faked by per-individual grant rows (with the offboarding defects in the Decision driver).

### 1.3 Why an ADR (HARDSTOP-09 + HARDSTOP-04)

Letting a **partner org's admin originate operator access into another org's tenant** is the single most security-sensitive change to the IAM plane since ADR-024 — it introduces the first *cross-org* delegation, inverting the "delegation is confined within one tenant" invariant for exactly one, carefully-bounded, two-sided-consented, attenuated case. Implementing it without first deciding **(a)** the relationship primitive (D1), **(b)** two-sided consent (D2), **(c)** the attenuation cap that prevents a partner from ever holding more than the host delegated — and never more than the host itself holds (D3), **(d)** partner-side self-governance so offboarding is automatic (D4/D6), and **(e)** the no-transitive-re-delegation invariant (confused-deputy) would bake a cross-org privilege-origination model silently (HARDSTOP-09). Because it **extends the ADR-024 within-tenant delegation decision** across the org boundary, HARDSTOP-04 requires the extension be recorded, not applied implicitly. Same prevention role ADR-019/020/023/024/044 played.

---

## 2. Decision

Eight axes. Each table's first row is **CHOSEN (PROPOSED direction)**.

### D1 — Relationship primitive: what models the partnership?

| Option | Mechanics | Verdict |
|---|---|---|
| **A. A first-class `tenant_partnership(host_tenant_id, partner_tenant_id, status, delegated_scope, …)` aggregate** — an explicit tenant↔tenant relationship object that is BOTH the unit of grant AND the unit of revocation | admin-service owns a new aggregate: host A ↔ partner B, a `status` (`PENDING` → `ACTIVE` → `SUSPENDED`/`TERMINATED`), and a `delegated_scope` (the `{domains}×{roles}` A delegates, D3). B's operators acting in A derive their grants **from** an ACTIVE partnership (D4); nothing else can originate cross-org access. | **CHOSEN** — the individual-grant workaround has no object to bound/audit/terminate as a unit (the offboarding defect); a first-class relationship is the only thing that makes cascade-revocation (D6) and two-sided consent (D2) expressible. Mirrors AWS delegated-administrator / Slack Connect (both model the *relationship*, not just the grants). |
| B. No primitive — keep individual grant rows (status quo, "model 1") | A grants specific B-operators rows on A directly | Rejected as the direction — the exact gap (A tracks B's staffing; no unit to revoke; N manual unassigns, no cascade). Recorded as the trivial retreat if cross-org is judged out of scope. |
| C. An N-way `consortium` / org-group entity | many tenants share a group | Deferred-not-rejected — pairwise supplier/partner relationships are the need; N-way consortia add membership-governance mass. Revisit only if a real N-way case appears (additive over D1-A). |

### D2 — Origination + consent: who creates the partnership?

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Two-sided consent: host A's `TENANT_ADMIN` INVITES (with a bounded `delegated_scope`); partner B's `TENANT_ADMIN` ACCEPTS → `ACTIVE`** | A decides *what* to delegate and *to whom* (which partner tenant); B decides *whether to participate* and *who from B* acts (D4). The partnership is `PENDING` until B accepts; either side may decline/terminate. | **CHOSEN** — a partner relationship is inherently bilateral: A must not drag B's operators into A without B's governance (that would break B-side offboarding), and B must not enter A without A's grant. Two-sided consent is what makes the offboarding split (A owns the envelope, B owns its people) coherent. Slack Connect / AWS delegated-admin handshake parity. |
| B. Host unilaterally grants (no partner consent) | A directly binds B-operators | Rejected — this *is* the individual-grant workaround relabeled; B has no governance handle, so B offboarding its employee cannot automatically remove A-access. |
| C. Platform `SUPER_ADMIN` brokers each partnership | operator approves | Rejected as default — re-introduces the operator-in-the-loop ADR-044 removed. Recorded as an optional escalation gate (like ADR-044 D4-C) if partner-abuse is observed, slotting *before* the D2-A handshake; the handshake itself is unchanged. |

### D3 — Scope attenuation: what can B do in A? (the security keystone)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. The partnership carries an explicit `delegated_scope` = a bounded set of `{domain}×{role}` A grants; B's operators in A are capped at `delegated_scope`, and the host can never delegate more than the host itself holds; NEVER A's `TENANT_ADMIN`/`SUPER_ADMIN`/platform scope** | The `AdminGrantScopeEvaluator` (extended) consults the ACTIVE partnership envelope for a cross-org actor and intersects: effective authority of a B-operator in A = `delegated_scope ∩ (what B's admin assigned that operator, D4) ∩ (what A itself holds)`. Structurally incapable of exceeding `delegated_scope`; the ADR-024 **≤-own no-escalation** invariant is extended ACROSS the org boundary (A cannot delegate authority A lacks; B cannot confer on its people authority beyond the delegation). | **CHOSEN** — this is the whole safety story: cross-org access is acceptable **because** it is capped at an explicit, host-authored slice that can never reach A's administration or other tenants. `SUPER_ADMIN` stays net-zero; M1-M7 untouched; the partner is a *scoped guest*, never a co-admin. |
| B. A fixed platform-defined "partner role" | one canned role | Rejected — inflexible (too much for a read-only supplier, too little for a fulfillment partner); A cannot tune per relationship; invites scope-creep toward a privileged catch-all. |

### D4 — Partner-side administration: who manages WHICH of B's operators act in A?

| Option | Mechanics | Verdict |
|---|---|---|
| **A. B's `TENANT_ADMIN` assigns/unassigns B's OWN operators into the partnership, bounded by `delegated_scope`** | Within an ACTIVE partnership, B's admin picks which B-operators participate and (optionally) narrows their role within `delegated_scope` — using B's normal operator-management surface, extended to target the partnership. A never names individual B-people. | **CHOSEN** — this is the entire point and the offboarding fix: because B governs its own people, **B suspending/offboarding an employee (its normal lifecycle) automatically removes that person's A-access** — A never tracks B's staffing (closes Decision-driver defect (a)). The `≤-own` cap (D3) means B can only ever confer within what A delegated. |
| B. Host A manages individual B-operators | A names B's people | Rejected — that IS the individual-grant workaround (the offboarding gap: A must know when a B-employee leaves B). |

### D5 — Blast-radius confinement of the derived grants (reuse, not re-decide)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. The derived B-operator grants into A remain per-`(operator, A-tenant)`, request-time-evaluated from the DB, confined to A, audited — the partnership is a MANAGED ENVELOPE over the existing ADR-020/024 machinery, never a bypass** | No token-embedded cross-org scope; the assumed token stays a single-`tenant_id` token (M1). Removing the partnership / the operator's participation takes effect on the next request (bounded by the ~10s perm-cache TTL, ADR-024 § 3.1). Every cross-org action audits `(identity + A-tenant + partnership_id + delegated permission)` so A sees "who from partner B did what". | **CHOSEN** — reuses BE-467/468 confinement + M1-M7 + fail-closed evaluation verbatim; the cross-org story adds an *envelope and a cap*, never a new isolation-bypass. Revoke-immediacy (request-time eval) is inherited. |
| B. Mint a cross-org multi-tenant token | one token spans A+B | Rejected — violates M1 (single-`tenant_id` token) + defeats revoke-immediacy; a stolen token would carry cross-org reach. Non-starter. |

### D6 — Lifecycle / revocation: relationship-scoped offboarding (the companion gap)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Terminating (or SUSPENDING) the partnership — by EITHER side — cascade-invalidates ALL of B's operators' access into A as a unit; independently, B's normal operator lifecycle (suspend/offboard) removes that individual's A-access** | On `SUSPENDED`/`TERMINATED`, the evaluator denies every cross-org actor derived from that partnership at the next request (fail-closed, no per-operator sweep needed — the derivation is gone). A one-shot audit records the cascade. B-side individual offboarding flows through D4 (B's admin/suspension) automatically. | **CHOSEN** — closes the exact defect the verification named (no cascade, no employment linkage today): the relationship IS the revocation unit, so A terminating the partnership or B offboarding an employee both resolve without A tracking B's staffing or issuing N manual unassigns. Opposite of the status-quo manual-only revocation. |
| B. Manual per-operator revoke on termination | N unassigns | Rejected — the status-quo gap; defeats the reason for a relationship primitive. |

### D7 — Identity plane: B's operators are the SAME unified identities (reuse ADR-032/036)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. B's participating operators are their existing born-unified identities; the partnership adds an A-scoped operator FACET (derived, envelope-bounded), never a new principal** | No identity fork; a B-employee who also shops on the storefront and admins their own side-company is still ONE Global Account, now with a partnership-derived facet into A. | **CHOSEN** — reuses ADR-032/036 exactly; avoids duplicate identities (the pollution those ADRs exist to prevent); the "one person, four hats" is literally one account with facets. |
| B. A shadow/guest principal per partner-operator in A | separate A-side account | Rejected — forks identity for the same human across the org boundary; breaks unified sign-in + audit legibility. |

### D8 — Scope + ownership: vertical slice, owned by admin-service

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Vertical slice owned by admin-service: the `tenant_partnership` aggregate + invite/accept (D2) + `delegated_scope` enforcement in the (extended) `AdminGrantScopeEvaluator` (D3) + B-side participant management (D4) + cascade-revoke (D6); prove end-to-end = A invites → B accepts → a B-operator assumes into A within the slice → B offboards them / A terminates → access gone** | admin-service already owns tenant-management, operator provisioning, the ADR-024 role model, `operator_tenant_assignment`, and the confinement evaluator — it is the natural owner. Add the partnership aggregate + the cross-org cap on the evaluator; reuse everything else. **Deferred:** N-way consortia (D1-C), partner discovery/marketplace, per-usage billing of partner activity, fine-grained per-resource (ADR-025 ABAC `org_scope`) cross-org data scoping, a `SUPER_ADMIN` broker gate (D2-C), and the UI (a minimal invite/accept/list surface suffices for the slice). | **CHOSEN** — smallest change that demonstrates the whole cross-org model (bounded, consented, attenuated, partner-governed, cascade-revocable) reusing all existing primitives; main stays GREEN; deferred items are additive. |
| B. A dedicated partnership/federation service | a new service | Rejected (for the slice) — every primitive (tenant, operator, assignment, role, confinement) lives in admin-service; a new service adds deployment/wiring surface for a thin relationship orchestration. Revisit only if partnerships grow marketplace/billing mass. |
| C. Big-bang (aggregate + billing + marketplace + ABAC data-scope + UI) | everything at once | Rejected — transiently large; violates the zero-regression staged discipline (ADR-019 D6 / ADR-030 D5 / ADR-044 D7). |

---

## 3. Consequences

### 3.1 Hard invariants this ADR carries
- **Cross-org access is attenuated (D3)** — capped at the host-authored `delegated_scope`, never A's `TENANT_ADMIN`/`SUPER_ADMIN`/platform scope, and never more than A itself holds. The ADR-024 ≤-own no-escalation invariant is extended across the org boundary, not relaxed.
- **Two-sided consent (D2)** — A authors the envelope; B governs its participants. Neither side can bind the other unilaterally.
- **No transitive re-delegation (confused-deputy)** — B may NOT re-delegate A's `delegated_scope` onward to a third org (B→C into A). Default deny, mirroring ADR-024's within-tenant sub-delegation confinement; any future transitive form is a separate decision.
- **Relationship-scoped offboarding (D6)** — the partnership is the unit of revocation; B's operator lifecycle drives individual A-access; termination cascades. No A-side tracking of B's staffing.
- **Confinement + isolation reused (D5)** — per-`(operator, tenant)` request-time evaluation, single-`tenant_id` tokens (M1), BE-467/468, fail-closed; the partnership is an envelope, never an isolation bypass. `SUPER_ADMIN` net-zero.
- **Plane separation preserved (ADR-023)** — the partnership delegates IAM authority; what B may *do* in A is still gated by A's `entitled_domains`. A partnership is not a subscription.
- **One identity (D7)** — B's operators are the same born-unified principals with an A-scoped facet.

### 3.2 Costs / risk surface
- **First cross-org privilege origination** — the highest-scrutiny change; mitigated wholly by D2 (two-sided consent) + D3 (attenuation cap + ≤-own) + D6 (cascade revoke) + the no-transitive-re-delegation invariant.
- **Confused-deputy / scope-creep** — a partner could try to act beyond the slice; prevented by the evaluator intersecting `delegated_scope` at request time (D3/D5). Named so it is not an afterthought.
- **Audit legibility across orgs** — A must be able to answer "what did partner B's people do in my tenant"; D5 records `partnership_id` + identity + permission on every cross-org action.
- **Abuse (partnership spam / social-engineering an accept)** — D2's two-sided handshake + the optional D2-C broker gate are the controls; rate-limiting invites is an additive follow-up.
- **Reversibility** — high. The partnership aggregate + the evaluator's cross-org branch are additive; disabling the feature reverts to within-tenant-only delegation with zero data-model change to ADR-020/024 objects (the derived grants simply stop being originated).

### 3.3 What this PROPOSED ADR does NOT do (deferred to ACCEPTED + execution)
- Does **not** add the `tenant_partnership` aggregate, invite/accept endpoints, evaluator cross-org branch, cascade-revoke, or any UI.
- Does **not** change `SUPER_ADMIN`, the ADR-024 within-tenant roles/confinement, `rbac.md`, ADR-020 assignment mechanics, or any operator controller.
- Does **not** decide N-way consortia (D1-C), a broker gate (D2-C), partner billing, or ABAC per-resource cross-org data scoping (all additive follow-ups).
- Does **not** execute the ACCEPTED transition — separate user-explicit-intent-gated task (sibling ADR-024→MONO-209, ADR-044→MONO-325).

### 3.4 Post-ACCEPTED execution roadmap (sketch; finalised at ACCEPTED)
0. **`TASK-MONO-3xx`** — ADR-045 PROPOSED → ACCEPTED (doc-only, user-gated). Model = **Opus**.
1. **`TASK-BE-xxx`** (iam-platform) — specs/contracts: the `tenant_partnership` aggregate + invite/accept contract (two-sided consent), the `delegated_scope` shape, the cross-org confinement rule, the cascade-revoke semantics. Source-of-Truth-first. Model = **Opus** (cross-org privilege origination + attenuation).
2. **`TASK-BE-xxx`** (admin-service) — implement the aggregate + invite/accept + `AdminGrantScopeEvaluator` cross-org branch (intersect `delegated_scope` ∩ B-assignment ∩ A-holds, fail-closed) + D4 participant management + D6 cascade-revoke; cross-org-leak IT (assert a B-operator can NEVER exceed `delegated_scope`, never reach A's admin, never a third tenant; assert termination denies at the next request; assert no transitive re-delegation). Model = **Opus** (isolation/escalation).
3. **`TASK-FE/BE-xxx`** — minimal partner-console surface (A: invite + list + terminate; B: accept + assign own operators) proving end-to-end (A invites → B accepts → B-operator assumes into A within the slice → offboard/terminate → access gone). Model = **Sonnet** (thin UI over the proven endpoints).
4. **Deferred follow-ups** (each its own task): N-way consortia, `SUPER_ADMIN` broker gate, partner-activity billing, ABAC per-resource cross-org data scoping, partnership invite rate-limiting, partner discovery.

---

## 4. Alternatives Considered
- **Keep the individual-grant workaround; no partnership primitive (status quo).** Rejected as the direction — it is the exact gap (A tracks B's staffing, no cascade revocation); recorded as the trivial retreat if cross-org collaboration is judged out of portfolio scope.
- **Model the partner as an ecommerce-style seller/participant inside one tenant (ADR-042 shape).** Rejected — that shape (keyed `(tenant_id, seller_id)`, participant-not-tenant) is correct for a marketplace seller *inside* one platform tenant, but cannot express *two independently-owned tenants* collaborating; conflating them would force B's whole org into A's tenant.
- **Mint a cross-org multi-tenant token / embed cross-org scope in the token.** Rejected (D5-B) — violates M1 single-`tenant_id` tokens + defeats revoke-immediacy.
- **Grant the partner a fixed privileged "partner role" or A's `TENANT_ADMIN`.** Rejected (D3-B) — catastrophic escalation; a partner is a scoped guest, never a co-admin.
- **Platform-`SUPER_ADMIN`-brokered only (no self-service handshake).** Rejected as default (D2-C) — re-introduces the operator-in-the-loop; kept as an optional abuse-escalation gate.
- **ACCEPTED now, skip PROPOSED.** Rejected — the attenuation cap (D3), two-sided consent (D2), no-transitive-re-delegation, and cascade-revocation (D6) are load-bearing cross-org security decisions warranting review before code (sibling discipline). Self-ACCEPT prohibited.

---

## 5. Relationship to prior ADRs

| | ADR-MONO-020 | ADR-MONO-024 | ADR-MONO-042 | ADR-MONO-044 | **ADR-MONO-045 (this)** |
|---|---|---|---|---|---|
| Axis | operator multi-tenant assignment | tenant-admin delegation **within one tenant** | seller onboarding (participant **inside** a tenant) | tenant onboarding — first-admin origination (**new tenant only**) | **cross-ORG partner delegation — tenant↔tenant** |
| Relationship | **reused** — the grant substrate the derived partner grants ride on | **extended additively** — within-tenant delegation gains one bounded cross-org form (D4-B amended) | **contrast** — the shape this ADR is *not* (participant ≠ tenant) | **precedent** — within-org self-service origination; D2 confinement stance revisited at org scale | — |

ADR-024 answered *"how a tenant-admin manages its OWN tenant"*; ADR-044 answered *"how the FIRST admin of a NEW tenant self-originates"*; this ADR answers the remaining question *"how TWO existing, independently-owned tenants collaborate — one operating a bounded, revocable slice of the other."* Together they are the full enterprise-collaboration lifecycle: **own (044) → administer within (024) → collaborate across (045)**.

---

## 6. Status Transition History

Append-only.

| Date | Transition | Decision direction | User intent quote | PR(s) |
|---|---|---|---|---|
| 2026-07-04 | created PROPOSED | D1 = first-class `tenant_partnership` aggregate (the unit of grant + revocation); D2 = two-sided consent (host invites with a bounded scope, partner accepts; `SUPER_ADMIN` broker as optional escalation C); D3 = explicit `delegated_scope` attenuation cap, never host-admin/`SUPER_ADMIN`, ≤-own extended across the org boundary; D4 = partner-side self-governance (B's admin assigns B's own operators into the partnership); D5 = per-`(operator,tenant)` request-time confinement reused (envelope, not bypass; single-`tenant_id` token, M1); D6 = relationship-scoped cascade offboarding (terminate partnership OR B offboards employee → A-access gone, no A-side staffing tracking); D7 = born-unified identity, A-scoped facet (no new principal); D8 = vertical slice owned by admin-service, console-proof; + no-transitive-re-delegation (confused-deputy default deny) invariant | (design discussion 2026-07-04: "웹스토어/팬 사용자이면서 자기 회사 콘솔 + 소속사 콘솔 + 협력사로서 다른 회사 테넌트도 운영" → verification of hats 1–3 realized, hat 4 missing → "추천은?" → recommend a PROPOSED ADR fusing cross-org delegation + relationship-scoped offboarding) → "시작" | TASK-MONO-327 |

(PROPOSED row appended 2026-07-04 per the ADR-019/023/024/030/042/044 staged-child format. The ACCEPTED transition is a **separate** user-explicit-intent-gated task; **NOT** a self-ACCEPT.)

---

## 7. Provenance
- User design discussion 2026-07-04 (a chat exploring whether one person can simultaneously be a web-store/fan consumer, owner-admin of their own company's tenant, an operator-employee of an employer's tenant, and a partner/supplier operating another company's tenant) + a read-only verification (2026-07-04) establishing that hats 1–3 are architecturally realized and confined, while hat 4 (cross-org partner) has **no** tenant↔tenant primitive and offboarding is **partial** (manual per-`(operator,tenant)`, no lifecycle linkage, no cascade) + AskUserQuestion-equivalent recommendation (fuse cross-org delegation + relationship-scoped offboarding into one PROPOSED ADR) → "시작".
- HARDSTOP-09 + HARDSTOP-04 (`platform/hardstop-rules.md`) — the mandate for an ADR + staged PROPOSED on a cross-org privilege-origination decision that extends the ADR-024 within-tenant delegation model.
- [ADR-MONO-024](ADR-MONO-024-tenant-admin-delegation.md) (§ D4-B amended additively), [ADR-MONO-020](ADR-MONO-020-operator-multitenant-assignment.md) (§ D6 amended additively), [ADR-MONO-032](ADR-MONO-032-unified-identity-roles-model.md)/[036](ADR-MONO-036-born-unified-identity-provisioning.md)/[042](ADR-MONO-042-ecommerce-seller-onboarding-iam-provisioning.md)/[044](ADR-MONO-044-self-service-tenant-onboarding.md)/[023](ADR-MONO-023-entitlement-iam-plane-separation.md), `projects/iam-platform/specs/services/admin-service/rbac.md`, `rules/traits/multi-tenant.md` M1-M7.

분석=Opus 4.8 / 구현 권장=Opus (cross-org privilege origination: two-sided-consented, `delegated_scope`-attenuated, ≤-own-across-org, cascade-revocable partnership extending ADR-024 delegation across the org boundary under HARDSTOP-09/04; execution mirrors ADR-024/044 confinement reuse + zero-regression staged discipline).
