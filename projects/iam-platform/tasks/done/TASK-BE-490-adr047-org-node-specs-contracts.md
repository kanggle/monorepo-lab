# Task ID

TASK-BE-490

# Title

ADR-047 § 4 step 1 — org-node hierarchy specs + contracts (rbac.md `org.manage`/`ORG_ADMIN`, account/admin data-model `org_node` DDL, admin-api org-node CRUD + ceiling + node-admin grant, multi-tenancy feature, internal entitled-domains contract amendment)

# Status

done

# Owner

backend

# Task Tags

- docs
- api
- adr

---

# Dependency Markers

- **선행 (prerequisite)**: `TASK-MONO-340` — ADR-MONO-047 ACCEPTED (§ 4 roadmap UNPAUSED). Do not start before that merges to `main`.
- **후속 (blocks)**: `TASK-BE-491` (account-service impl), `TASK-BE-492` (admin-service impl). **Specs/contracts precede code** (CLAUDE.md Change Rule) — those two may not start until this merges.

---

# Goal

Author the specification + contract surface for ADR-MONO-047's org-node hierarchy, **before any code exists**, so that BE-491/492 implement against a written contract rather than inventing one.

After this task, the following are true and written down:

1. `org_node` is a **data-less grouping node above `tenant`** — `tenant` remains the single flat isolation key (M1). The DDL, the cycle/depth constraints, and the `tenants.org_node_id` nullable FK are specified.
2. The **entitlement ceiling** is specified as a **deny-only, narrow-only** guardrail: `effective_ceiling(tenant) = ⋂ ceiling(n)` over the node chain `root → … → tenant.org_node`; a tenant with `org_node_id = NULL` is an **unbounded singleton** (net-zero for every existing row).
3. The **D6 seam placement** is written down: the intersection is applied **once, in account-service's entitled-domains resolution** (`ACTIVE subscriptions ∩ effective_ceiling`), because account-service owns `tenants` and therefore `org_node`. auth-service's `TenantClaimTokenCustomizer` is **unchanged** — it already consumes whatever domain list account-service returns, so `derive(E ∩ C) = derive(E) ∩ derive(C)` (ADR-035 derivation is per-domain). The ADR-045 `applyCrossOrgCap` narrowing composes on top, order-independently (both narrow).
4. `org.manage` is specified as a new permission key; `ORG_ADMIN` as a new seed role whose grant is scoped **to an org-node** rather than to a tenant, and whose effective admin scope is **the tenant subtree under that node** (`TenantScopeGuard` subtree driver, ADR-024 D2 additive amendment).
5. The subscription-write path is specified to **reject** activating a domain outside the tenant's effective ceiling (entitlement plane, ADR-023 — the ceiling bounds entitlement, it never mints IAM roles).

---

# Scope

## In Scope

- `projects/iam-platform/specs/services/account-service/data-model.md` — `org_node` table (id, parent_id, name, entitlement_ceiling, depth, created_at, updated_at), `tenants.org_node_id` nullable FK, cycle + `max_depth = 5` constraints, classification, `effective_ceiling` resolution algorithm, `NULL org_node_id ⟹ unbounded` net-zero rule.
- `projects/iam-platform/specs/services/admin-service/rbac.md` — `org.manage` permission key row; `ORG_ADMIN` seed role + its permission set; the `TenantScopeGuard` **subtree driver** (effective admin scope = `{tenants under subtree(grant.org_node_id)}` ∪ existing tenant-scoped grants ∪ `'*'` platform short-circuit); no-escalation restated (`grant ≤ granter's holdings ∧ ≤ node ceiling ∧ never SUPER_ADMIN`); the composed enforcement-stack order from ADR-047 D6.
- `projects/iam-platform/specs/services/admin-service/data-model.md` — `admin_operator_roles.org_node_id` nullable column (the node-scoped grant). **`tenant_id` is not repurposed**: it keeps mirroring the bound operator's own tenant (BE-289 WI-2 — audit-routing/isolation column). Specify the scope-resolution order per grant row (`'*'` → subtree → tenant) and the `CHECK (org_node_id IS NULL OR tenant_id <> '*')` prohibition.
- `projects/iam-platform/specs/contracts/http/admin-api.md` — `GET/POST /api/admin/org-nodes`, `GET/PATCH/DELETE /api/admin/org-nodes/{id}`, `PUT /api/admin/org-nodes/{id}/ceiling`, `GET/POST/DELETE /api/admin/org-nodes/{id}/admins`, `GET /api/admin/org-nodes/{id}/tenants`; request/response schemas; error codes (422 cycle / 422 depth-exceeded / 422 ceiling-not-subset-of-parent / 422 over-ceiling grant / 403 / 404 cross-scope).
- `projects/iam-platform/specs/contracts/http/internal/account-tenant-domain-subscriptions.md` — document a **new dedicated endpoint** `GET /internal/tenants/{tenantId}/entitled-domains` returning the **effective** set (`ACTIVE ∩ effective_ceiling`), with the fail-soft contract preserved. The existing `GET /internal/tenant-domain-subscriptions` keeps returning **raw ACTIVE subscription rows** (management + console-catalog truth); its `tenantId` filter is marked **deprecated for token issuance** and its keystone note is repointed. Rationale: a security-critical endpoint whose semantics change with a query parameter is a footgun — the effective set gets its own name.
- `projects/iam-platform/specs/contracts/http/internal/admin-to-account.md` — the internal reads admin-service needs: node subtree tenant ids, effective ceiling for a node.
- `projects/iam-platform/specs/features/multi-tenancy.md` — the 회사 → 서비스 → 도메인 three-axis narrative; `org_node` vs ADR-025 `org_scope` disambiguation (grouping *above* tenants vs department data-filter *inside* one tenant).
- ADR amendment notes: append the "amended by ADR-047" pointer to `docs/adr/ADR-MONO-019` § D1, `ADR-MONO-024` § D2, `ADR-MONO-035` § O1 (additive pointer only, no decision change).

## Out of Scope

- Any `.java`, `.sql`, `.ts`, `.tsx` change. **Doc-only.**
- Re-deciding D1–D7 (finalised by TASK-MONO-340).
- Role-level ceiling (D3-B), grant-at-node (D2-C), cross-owner consortium (ADR-045 D1-C) — deferred follow-up ADRs.
- Changing `entitled_domains` **management** reads (the subscription CRUD view must keep showing rows as stored; only the *entitlement resolution* leg is ceiling-narrowed).

---

# Acceptance Criteria

- [ ] **AC-1**: `org_node` DDL + `tenants.org_node_id` documented in account-service `data-model.md`, incl. cycle-check, `max_depth = 5`, and `child.ceiling ⊆ parent.ceiling` write invariant.
- [ ] **AC-2**: `effective_ceiling(tenant)` algorithm written as an explicit intersection over the root→node chain, with `org_node_id = NULL ⟹ unbounded (no ceiling)` stated as the net-zero back-compat rule (D7).
- [ ] **AC-3**: `rbac.md` carries the `org.manage` key row, the `ORG_ADMIN` seed role + permission set, and the `TenantScopeGuard` subtree driver written as an amended `effectiveAdminScope` pseudocode block (house style: `### Target-Tenant Scope Confinement`), with the `SUPER_ADMIN '*'` short-circuit **evaluated first** and no-escalation preserved verbatim from ADR-024.
- [ ] **AC-4**: `admin-api.md` documents every org-node endpoint with its permission gate (`org.manage`), request/response schema, and the five 422 rejection reasons + 403 + 404.
- [ ] **AC-5**: A dedicated `GET /internal/tenants/{tenantId}/entitled-domains` is specified as returning the **effective** set (`ACTIVE ∩ effective_ceiling`), names the single-point-of-enforcement rationale, and preserves the fail-soft contract (account-service down → auth-service omits the claim; the ceiling can never *widen* on failure). `GET /internal/tenant-domain-subscriptions` is documented as **unchanged raw ACTIVE rows** so the console catalog and subscription-management views keep seeing what is stored.
- [ ] **AC-6**: `features/multi-tenancy.md` contains the `org_node` (grouping, above tenant) vs `org_scope` (department filter, inside tenant) contrast, so the two are not conflated.
- [ ] **AC-7**: ADR-019/024/035 each carry an additive "amended by ADR-047 §…" pointer; **their decision bodies are byte-unchanged**.
- [ ] **AC-8**: Diff is doc-only (`git diff --stat` shows no code/SQL/TS files). Every internal doc link resolves (GitHub-exact anchors).

---

# Related Specs

> Before reading: follow `platform/entrypoint.md` Step 0 — read `projects/iam-platform/PROJECT.md`, then `rules/common.md` + the declared domain/trait rule files. `rules/traits/multi-tenant.md` M1–M7 is load-bearing for this task.

- `docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md` (**the authority** — D1–D7)
- `docs/adr/ADR-MONO-019-platform-console-customer-tenant-model.md` § D1, `ADR-MONO-023-entitlement-iam-plane-separation.md`, `ADR-MONO-024-tenant-admin-delegation.md` § D2/D3, `ADR-MONO-025-abac-data-scope-generalization.md`, `ADR-MONO-035-operator-auth-unification-model.md` § O1, `ADR-MONO-045-cross-org-partner-delegation.md` § D1-C
- `projects/iam-platform/specs/services/{account-service,admin-service,auth-service}/data-model.md`
- `projects/iam-platform/specs/services/admin-service/rbac.md`
- `projects/iam-platform/specs/features/multi-tenancy.md`
- `rules/traits/multi-tenant.md` (M1–M7)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md`
- `projects/iam-platform/specs/contracts/http/internal/account-tenant-domain-subscriptions.md`
- `projects/iam-platform/specs/contracts/http/internal/admin-to-account.md`

---

# Edge Cases

- **`org_node` vs `org_scope` conflation** — ADR-025's `org_scope` is a department subtree *inside* one tenant/domain (a data filter). ADR-047's `org_node` groups tenants *above* the isolation key. Both are trees; they are different axes. The spec must contrast them explicitly.
- **Ceiling on the management view** — narrowing the subscription CRUD read would hide rows an admin must still see (and would make the ceiling look like a delete). Only the *entitlement resolution* leg narrows.
- **`NULL` ceiling ≠ empty ceiling** — `org_node_id = NULL` (ungrouped) and a node with an explicitly empty ceiling `{}` are opposites: unbounded vs "may subscribe to nothing". The spec must say so, or a lazy migration silently locks every tenant out.
- **Intersection is order-independent** but the ADR-045 cross-org cap and the ceiling must both be described as narrowing gates, or an implementer may "optimise" by skipping one.
- **`max_depth = 5`** counts the root as depth 1 (AWS OU parity). State the convention; an off-by-one here is a silently-wrong constraint.

---

# Failure Scenarios

- Spec claims the ceiling *grants* (even in one sentence) → D2-A inverted, and a misconfigured node over-grants. Guard: AC-2/AC-5 state narrow-only, and the ceiling never appears on a grant path.
- `admin_operator_roles` gains `org_node_id` but the spec fails to say **which column drives scope** → an implementer either repurposes `tenant_id` (breaking the BE-289 WI-2 operator-mirror invariant and audit routing) or unions both (silently widening). Guard: AC-3 + an explicit per-row resolution order in `data-model.md`.
- The internal entitled-domains contract keeps saying "ACTIVE subscriptions" → BE-491 implements the intersection but every downstream reader's expectation is now stale, and the next agent "fixes" the code back. Guard: AC-5.
- ADR-019/024/035 bodies edited rather than pointer-appended → HARDSTOP-04. Guard: AC-7.
- Code lands in this task → the Change Rule ("contracts precede code") is satisfied only nominally. Guard: AC-8 doc-only diff.
