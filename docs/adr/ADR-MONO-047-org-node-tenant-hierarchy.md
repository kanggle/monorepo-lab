# ADR-MONO-047 — Org-Node Tenant Hierarchy (a grouping tree **above** `tenant` that lets one company own many isolated service-tenants, with a **deny-ceiling** entitlement guardrail inherited down the tree — the "회사 → 서비스 → 도메인" three-axis structure AWS Organizations OU→Account and GCP Folder→Project both provide but the portfolio's flat tenant registry never had)

**Status:** PROPOSED

**Date:** 2026-07-10

**History:** PROPOSED 2026-07-10 (records the **org-node hierarchy model**: how a paying company becomes a data-less grouping node that owns many **isolated service-tenants**, how an **entitlement ceiling** attached to a node is inherited **downward as a deny-only guardrail** (never a grant), and how tenant remains the **single flat isolation key** — `org_node` *groups* tenants, it does not *nest* them. Decisions D1–D7, **CHOSEN-PROPOSED** direction per the reasoning below; the ACCEPTED transition is a separate user-explicit-intent-gated task, mirroring the ADR-019/020/023/024/044/045/046 staged-child pattern. **No implementation in this task — decision record + impact scope + execution roadmap only. Self-ACCEPT prohibited.**)

**Decision driver:** The portfolio's isolation model has exactly **one axis = `tenant_id` = a paying customer company** (ADR-019 D1: the `tenants` registry *is* the customer-tenant entity; AWS Account / GCP Project parity). Domain permissions within a tenant are already fully hierarchical — `tenant_domain_subscription` (`entitled_domains`) → derived domain role (`WMS_OPERATOR`…) → granular service role (`OUTBOUND_WRITE`…) (ADR-019/020/033/035). **But there is no structural layer between "company" and "domain".** A company that wants **several independently-isolated services** (e.g. a 물류 service and a 영업 service, each with its own data boundary, each subscribing to a different mix of wms/erp/scm/finance) cannot express that: the only isolation unit is the tenant, so either the whole company is one tenant (no service isolation) or each service is a separate tenant with **no object that groups them back into "the company"** (company-wide billing/admin/audit becomes a manual roll-up, and there is no place to set a company-wide entitlement boundary). This is the middle tier of the user's stated need — **회사(여러 개) → 서비스(각 격리) → 도메인(wms/erp/scm 권한)** — and it is exactly the grouping-node-above-the-isolation-boundary facet that **AWS Organizations (OU → Account)** and **GCP Resource Hierarchy (Folder → Project)** provide. Building it during implementation would silently bake load-bearing choices (HARDSTOP-09): whether the new layer *nests the isolation key* (sub-tenant — an M1 violation) or *groups it* (a data-less node above tenant); whether the inherited entitlement policy *accumulates* (GCP-style additive) or *caps* (AWS-SCP-style deny-ceiling); the ceiling's granularity; and how company-wide delegated admin works. Each must be decided, not defaulted.

**Supersedes:** none. **Amends:** [ADR-MONO-019](ADR-MONO-019-platform-console-customer-tenant-model.md) § D1 (additive — the customer-tenant registry gains an optional **parent grouping node**; `tenant` stays the isolation key, ADR-019 bodies byte-unchanged; a tenant with no `org_node` is a legal singleton, so existing rows are unaffected). [ADR-MONO-024](ADR-MONO-024-tenant-admin-delegation.md) § D2 (additive — `TenantScopeGuard`'s effective-scope resolution gains a **subtree** driver: an `ORG_ADMIN @ node` administers every tenant under that node; within-tenant `TENANT_ADMIN` byte-unchanged). [ADR-MONO-035](ADR-MONO-035-operator-auth-unification-model.md) § O1 (additive — derived domain roles are **intersected with the org-node ceiling**; derivation from `entitled_domains` byte-unchanged, the ceiling only *narrows*).

**Related:** [ADR-MONO-019](ADR-MONO-019-platform-console-customer-tenant-model.md) (customer-tenant = isolation key — the leaf this tree sits above), [ADR-MONO-020](ADR-MONO-020-operator-multitenant-assignment.md) (`operator_tenant_assignment` — one identity spans many service-tenants of a company via assignment, the "switcher" substrate), [ADR-MONO-024](ADR-MONO-024-tenant-admin-delegation.md) (within-tenant delegated admin + no-escalation — the cap `ORG_ADMIN` inherits and extends to a subtree), [ADR-MONO-025](ADR-MONO-025-abac-data-scope-generalization.md) (`org_scope` = **intra-tenant department** data-scope — orthogonal and easily confused: that is a data filter *inside one tenant/one domain*; this ADR's `org_node` is a grouping *above* tenants), [ADR-MONO-035](ADR-MONO-035-operator-auth-unification-model.md) (subscription→role derivation — the ceiling intersects its output), [ADR-MONO-045](ADR-MONO-045-cross-org-partner-delegation.md) § D1-C (N-way org-group / consortium **deferred-not-rejected** — this ADR is the intra-owner hierarchy that D1-C's cross-owner consortium is a sibling of), `rules/traits/multi-tenant.md` M1–M7 (row isolation — this ADR **preserves M1 by not nesting the isolation key**).

---

> **PROPOSED (staged, sibling: ADR-019/020/023/024/044/045/046).** This ADR records the **decision direction** (D1–D7) + the **invariants the chosen direction preserves** (M1 single-isolation-key untouched — `org_node` groups, never nests; ceiling narrows-only, never grants; `SUPER_ADMIN` net-zero; deny-default; fail-closed; ADR-023 plane separation) + a **zero-regression execution roadmap**. The roadmap steps are **PAUSED** until a separate user-explicit ACCEPT gate. **Self-ACCEPT prohibited.** No implementation in this task.

---

## 1. Context

### 1.1 What exists (verified 2026-07-10)

- **One isolation axis** — `tenant_id` = a paying customer company (ADR-019 D1; `rules/traits/multi-tenant.md` M1). AWS Account / GCP Project parity is explicit in ADR-019's mapping table.
- **Domain permissions already hierarchical inside a tenant** — `tenant_domain_subscription.entitled_domains` (ADR-019/023) → derived domain role `*_OPERATOR` (ADR-035 O1) → derived granular service role `OUTBOUND_WRITE`… (ADR-035 BE-433 amendment). Least-privilege: assume-tenant carries **one selected tenant's** domains, never a union (ADR-020 D2).
- **One identity spans many tenants** — `operator_tenant_assignment` (ADR-020) + `admin_operator_roles.tenant_id` multi-row (ADR-024) + assume-tenant re-scope. The console tenant-switcher already lets one operator act for many tenants.
- **Delegated admin is tenant-flat** — `TENANT_ADMIN @ <tenant>` administers exactly one tenant (`TenantScopeGuard`, ADR-024 D2). `SUPER_ADMIN` (`'*'`) is platform-wide and net-zero elsewhere.

### 1.2 The gap — no layer between company and domain

`org_node | org_unit | folder | tenant_group | parent_tenant` matches **zero** tables/entities/specs/ADRs. The tenant registry is **flat**. A company wanting N independently-isolated services has no first-class expression, and no object to carry a company-wide entitlement boundary or company-wide admin. This is the grouping-node-above-the-isolation-boundary facet every hyperscaler provides:

| Concept | AWS | GCP | portfolio (this ADR) |
|---|---|---|---|
| Data-less grouping node (nestable) | **Organizational Unit (OU)** | **Folder** | **`org_node`** (this ADR) |
| Isolation / billing leaf | **Account** | **Project** | **`tenant`** (unchanged, ADR-019) |
| Permissions within the leaf | IAM roles/policies | IAM roles | domain subscription → role (unchanged, ADR-035) |
| Policy inherited down the tree | **SCP (deny-ceiling)** | Org Policy / IAM Deny (guardrail) + IAM (additive) | **entitlement ceiling (deny-only, D2)** |
| Delegated admin at a node | delegated administrator @ OU | Folder IAM admin | **`ORG_ADMIN @ node`** (D5) |

The three-axis result — **`org_node` (회사, grouping) → `tenant` (서비스, isolation leaf) → domain subscription (wms/erp/scm, permission)** — is the app-level transplant of the AWS/GCP four-layer tree (root → OU/Folder → Account/Project → resource).

### 1.3 Why an ADR (HARDSTOP-09 + HARDSTOP-04)

Introducing a layer that changes where the isolation boundary sits relative to "the company", and a policy that is **inherited across that layer**, is an IAM/tenancy change on the order of ADR-019/024. Implementing without first deciding **(D1)** group-vs-nest (the difference between preserving M1 or violating it), **(D2)** deny-ceiling-vs-allow-accumulate (opposite failure directions), **(D3)** ceiling granularity, **(D4)** tree shape, **(D5)** node-level delegated admin, and **(D6)** how derivation composes with the ceiling would bake a tenancy model silently (HARDSTOP-09). Because it **amends ADR-019 D1 / ADR-024 D2 / ADR-035 O1** additively, HARDSTOP-04 requires the amendments be recorded, not applied implicitly.

---

## 2. Decision

Seven axes. Each table's first row is **CHOSEN (PROPOSED direction)**.

### D1 — The primitive: group the isolation key, never nest it

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Data-less `org_node` tree ABOVE tenant; `tenant` stays the flat isolation leaf** — `org_node(id, parent_id, name, entitlement_ceiling)` + `tenant.org_node_id`. A **service = a tenant**; a **company = an `org_node`** owning ≥1 service-tenant. Nodes carry policy + grouping only, **never rows/data/isolation**. | **CHOSEN** — preserves M1 (one isolation key = `tenant_id`) exactly: the tree adds grouping *above* the leaf, it does not subdivide the leaf. Direct AWS OU→Account / GCP Folder→Project parity. A company's several services become several isolated tenants, re-grouped by one node. |
| B. Sub-tenant / nested `tenant` (parent-child `tenant.parent_id`) | isolation key becomes hierarchical | **Rejected** — **M1 violation.** A nested isolation key forces every row-isolation guard, every `@TenantScoped` query, and every M1–M7 invariant to become subtree-aware. Enormous blast radius on the security-critical path; the hyperscalers deliberately did **not** do this (Account/Project stay flat). |
| C. Extra column on `tenant` (`tenant.company_group` string tag) | flat tag, no tree | **Rejected** — a flat tag cannot express nesting (회사 → 부문 → 서비스) and has no place to attach an inherited ceiling or node-scoped admin. A degenerate tree; the tree costs the same and generalizes. |

### D2 — Ceiling inheritance semantics: deny-ceiling (SCP-style), not allow-accumulate (GCP-style)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Deny-only ceiling (AWS SCP parity)** — each node's `entitlement_ceiling` is a **maximum** set of domains; the effective ceiling at a leaf = **intersection of the node chain** (child ⊆ parent enforced). The ceiling **can only narrow** what a tenant may subscribe to / derive; it **never grants**. | **CHOSEN** — matches the portfolio's standing fail-closed / least-privilege posture (assume-tenant is single-tenant, never union; ABAC data-scope "narrows, never grants", ADR-025). A misconfigured node **reduces** reach (safe failure), never expands it. Composes cleanly as one more narrowing gate. |
| B. Allow-accumulate inheritance (GCP additive IAM) — a node *grants* domains that sum downward | node membership *adds* entitlement | **Rejected** — opposite failure direction: a misconfigured parent **over-grants** (a security incident), and it makes "prove this tenant *cannot* reach domain X" require evaluating the whole ancestor chain's grants. Contradicts the narrowing-only philosophy the rest of the authz stack is built on. (Note: GCP itself later added Org Policy / IAM Deny guardrails precisely because pure-additive did not scale to provable bounds.) |
| C. Both (grant + ceiling on nodes) | full policy engine on the tree | **Deferred** — over-engineering for the portfolio (mirrors ADR-025's rejection of a full ABAC policy engine). Additive later if a real "grant at node" need appears. |

### D3 — Ceiling granularity: domain-level, not role-level

| Option | Verdict |
|---|---|
| **A. Domain-set ceiling (`{wms, erp, scm, …}`)** | **CHOSEN** — same granularity as `tenant_domain_subscription` (domain-keyed), so the intersection (D6) is a trivial set operation and node administration stays legible. Granular roles (`OUTBOUND_WRITE`) remain **derived inside the tenant** (ADR-035), unchanged. AWS SCPs are likewise typically drawn at the service (domain) level, not per-action. |
| B. Role-level ceiling (cap individual `*_WRITE` roles at the node) | **Rejected v1** — explodes node-management surface and duplicates ADR-035's derivation logic one layer up. Additive later if a company genuinely needs "this whole company is read-only in wms". |

### D4 — Tree shape: nestable, depth-capped

| Option | Verdict |
|---|---|
| **A. Arbitrary nesting (회사 → 부문 → 팀 → 서비스), single `max_depth` cap (e.g. 5)** | **CHOSEN** — recursion cost is identical to a 2-level tree, so hard-coding 2 levels only loses expressiveness; GCP Folders and AWS OUs both nest. The cap is a runaway/cycle guardrail (AWS OUs cap at 5). `parent_id` self-reference must be cycle-checked on write. |
| B. Fixed 2 levels (company → service only) | **Rejected** — cannot express a mid-level 부문/division grouping the user's phrasing implies ("각 회사 안에 여러 서비스", plausibly grouped). No cost saving. |

### D5 — Node-scoped delegated admin (`ORG_ADMIN @ node`)

**CHOSEN:** a new seed role `ORG_ADMIN`, granted **at an `org_node`**, administers **every tenant in that node's subtree** — the company-wide admin the flat `TENANT_ADMIN` cannot express. `TenantScopeGuard` (ADR-024 D2) gains a subtree driver: `target tenant ∈ subtree(admin's org_node)`. **No-escalation is reused unchanged** (ADR-024 D2/D3): an `ORG_ADMIN` cannot grant a role/domain the granter does not hold, cannot exceed the node's own ceiling, and cannot grant `SUPER_ADMIN`. `TENANT_ADMIN @ <tenant>` (single-tenant) is untouched and remains valid for a one-service company. AWS delegated-administrator @ OU / GCP Folder-admin parity.

### D6 — Composition with role derivation

**CHOSEN:** at assume-tenant, derived domain roles =

```
effective_roles = derive(selected_tenant.entitled_domains)  ∩  ceiling(chain from root → selected_tenant.org_node)
```

The ceiling is applied as an **intersection after** ADR-035 derivation — it can only remove domains, never add. Enforcement-stack order becomes:

```
RBAC permission → tenant-scope (incl. ORG_ADMIN subtree, D5) → [org-node ceiling, D2] → ABAC data-scope (org_scope) → access-condition
```

The token still carries exactly **one `tenant_id`** (M1 preserved); the org tree participates only in **pre-issuance ceiling computation**, never in the token's isolation identity.

### D7 — Migration: company=tenant → org_node + service-tenant, back-compatibly

**CHOSEN:** existing rows migrate as **1 `org_node` + 1 service-tenant** per current tenant (a singleton company owning one service) — behaviourally identical to today (`org_node_id` nullable ⟹ "ungrouped singleton" is also legal, so migration can even be lazy). Splitting a company into multiple services is then an **additive** operation (create sibling service-tenants under the same node). No existing tenant's `tenant_id`, isolation, or subscriptions change. Seed data (`acme-corp`, `globex-trading`, …) gains a same-named parent `org_node`; the original tenant becomes that node's first service-tenant (or stays a singleton until a second service is added).

---

## 3. Invariants preserved

1. **M1 single isolation key** — `tenant_id` remains the one flat isolation axis. `org_node` **groups** tenants; it **never nests** them (D1-A). Every M1–M7 row-isolation guard is byte-unchanged; the token carries one `tenant_id` (D6).
2. **Narrow-only ceiling** — the org-node ceiling can only **reduce** a tenant's reach, never grant (D2-A); a misconfiguration fails **closed**. Mirrors ADR-025 "data-scope narrows, never grants".
3. **`SUPER_ADMIN` net-zero + no-escalation** — `ORG_ADMIN` grants ≤ granter's holdings ∧ ≤ node ceiling; cannot mint `SUPER_ADMIN` (D5, reusing ADR-024 D2/D3).
4. **ADR-023 plane separation** — the ceiling bounds **entitlement** (which domains), never mints IAM roles directly; a tenant still needs its own `entitled_domains` ∩ ceiling, and an operator still needs the derived role. The two planes stay separate.
5. **Deny-default, fail-closed, audited** — node CRUD + ceiling edits + `ORG_ADMIN` grants gated by a new `org.manage` permission + `admin_actions` row.

---

## 4. Execution roadmap (PAUSED until ACCEPT)

Each step is a separate dependency-ordered task spawned off the ACCEPTED main (sibling: ADR-045 § 3.4). **None may start before the user-explicit ACCEPT gate.**

1. **Spec** (`iam-platform`, backend) — `rbac.md` (`org.manage` + `ORG_ADMIN` seed; `TenantScopeGuard` subtree driver; note ceiling = narrow-only, evaluation composition D6), `data-model.md` (`org_node` + `tenant.org_node_id` DDL + cycle/depth constraints + classification), `contracts/http/admin-api.md` (org-node CRUD + ceiling + `ORG_ADMIN` grant endpoints), `features/…` (org hierarchy + company-wide admin). **Contracts/specs precede code (Change Rule).** Also amend ADR-019/024/035 amendment notes.
2. **Backend** (`iam-platform`) — Flyway `org_node` (+ `tenant.org_node_id` FK, nullable) with `parent_id` cycle-check + depth cap; JPA entities + repo/adapter/port; `OrgNodeAdminController` (`/api/admin/org-nodes` CRUD, `/{id}/ceiling`, `/{id}/admins`); ceiling-intersection service wired into assume-tenant derivation (ADR-035 seam, D6); `TenantScopeGuard` subtree resolution (D5); `org.manage` seed; `@RequiresPermission` + `admin_actions`; no-escalation cap reuse. Tests: Unit (ceiling intersection + cycle/depth reject + no-escalation), Integration (Testcontainers + WireMock: derived roles narrowed by ceiling; `ORG_ADMIN` subtree reach; cross-node 404), Security (non-admin 403, over-ceiling grant denied), Fail-closed, Migration idempotence.
3. **Frontend** (`platform-console`) — org-hierarchy screen (`src/features/org-hierarchy/`): tree CRUD, ceiling editor, `ORG_ADMIN` assignment; tenant-switcher **grouped by `org_node`** (AWS IdC account-picker parity); no-escalation gating (grantable-roles convention). Consumes step-2 API.
4. **Migration task** — backfill 1 `org_node` per existing tenant (D7), verify behavioural no-op (existing subscriptions/isolation unchanged).

Follow-up ADRs (out of this scope): **role-level ceiling** (D3-B) if a company needs sub-domain caps; **grant-at-node** (D2-C) if additive node grants are ever needed; **cross-owner consortium** (ADR-045 D1-C) — the sibling that groups tenants of *different* owners, vs this ADR's *same*-owner hierarchy.

---

## 5. Consequences

- **Positive** — expresses the full **회사 → 서비스 → 도메인** three-axis structure the flat registry could not; company-wide entitlement boundary + company-wide admin as first-class objects; AWS/GCP org-hierarchy parity at the app level; **M1 untouched** (grouping, not nesting) so zero blast radius on the row-isolation core; ceiling is one more narrowing gate, composing with the existing fail-closed stack.
- **Negative / trade-offs** — tenant count grows (one company → many service-tenants; the intended AWS "account-per-workload" shape, but more rows to administer — mitigated by node-level `ORG_ADMIN` + grouped switcher); a new tree to keep acyclic/depth-bounded; a migration step (behaviourally a no-op but must run).
- **Neutral** — intra-tenant department data-scope (`org_scope`, ADR-025) is a **separate, orthogonal** tree inside a tenant/domain and is unchanged by this ADR; cross-owner consortium (ADR-045 D1-C) remains deferred by design.

**No implementation in this task.** PROPOSED → ACCEPTED is a separate user-explicit-gated step; **self-ACCEPT prohibited**.
