# ADR-MONO-024 — Tenant-Admin Delegation (a tenant-scoped operator-management authority; a customer's own admin manages its operators/assignments within its tenant; strict no-escalation confinement; AWS Organizations "delegated administrator" / GCP project-IAM-admin parity)

**Status:** PROPOSED

**Date:** 2026-06-10

**History:** PROPOSED 2026-06-10 (TASK-MONO-208 — authors the decision record for the **"①" tenant-admin delegation axis** that [ADR-MONO-023](ADR-MONO-023-entitlement-iam-plane-separation.md) repeatedly foreshadowed ("the recurring design question *can an a-company tenant-admin grant its own employees/partners access* is an IAM-plane delegation decision … the future delegation ADR can hand a tenant-admin entitlement-management without operator-management, or vice-versa"). Today every operator-management authority (`operator.manage`) sits only on the platform-central `SUPER_ADMIN`, so a customer cannot manage its own operators — onboarding an employee/partner to a customer tenant requires a platform operator. Seven decisions D1-D7, **CHOSEN-PROPOSED** direction per the reasoning below; the ACCEPTED transition is a separate user-explicit-intent-gated task, mirroring the ADR-MONO-019/020/021/023 staged-child pattern. **No implementation in this task — decision record + impact scope + migration roadmap only.**)

**Decision driver:** The portfolio's IAM plane (admin-service RBAC + [ADR-MONO-020](ADR-MONO-020-operator-multitenant-assignment.md) `operator_tenant_assignment`) has a complete *operator-management* surface — create operator, grant roles, change status, list/set per-assignment `org_scope` — but **all of it is gated by a single platform-central permission `operator.manage`, seeded only on `SUPER_ADMIN`** (`rbac.md` Seed Matrix). There is consequently **no way for a customer's own administrator to manage the customer's operators**: assigning an employee/partner to the customer's tenant, scoping their data access, or revoking them all require a platform `SUPER_ADMIN`. Two further gaps make this acute: (1) there is **no assign/unassign admin surface** for `operator_tenant_assignment` at all — assignment rows are created only by SQL seed (the org-scope endpoint manages an *existing* row but cannot create/delete one); (2) the permission evaluation (`rbac.md` Permission Evaluation Algorithm) computes a role-permission **union with no tenant-scope confinement of the *target*** — `operator.manage` is all-or-nothing platform-wide. Building a "tenant admin can manage its own people" feature during implementation would silently bake the delegation model (HARDSTOP-09): whether confinement is enforced in the evaluator or per-endpoint, whether a tenant-admin can sub-delegate or escalate, whether it spans the entitlement plane (ADR-023 `subscription.manage`) — each is a load-bearing security decision that must not be chosen implicitly. This ADR is that decision record.

**Supersedes:** none. **Amends:** [ADR-MONO-020](ADR-MONO-020-operator-multitenant-assignment.md) § D1 (additive § History "Additive note" blockquote recording that the **delegated-administration model for `operator_tenant_assignment` — who, other than a platform SUPER_ADMIN, may create/mutate assignment rows, and under what tenant-scope confinement — is decided here**; ADR-020 D1-D6 + § 2-7 bodies byte-unchanged — HARDSTOP-04 discipline preserved). **Reconciles:** none yet (PROPOSED scopes the architecture; `Permission.java`, `rbac.md`, the operator controllers, and `OperatorTenantAssignment*` are byte-unchanged at PROPOSED — D7 explicitly preserves current shapes during the staged window; reconciliation lands at the post-ACCEPTED execution tasks, never inside this ADR).

**Related:** [ADR-MONO-020](ADR-MONO-020-operator-multitenant-assignment.md) (the operator↔tenant N:M IAM plane this ADR delegates administration of — `operator_tenant_assignment` + per-assignment `permission_set`/`org_scope`; **amended additively**), [ADR-MONO-023](ADR-MONO-023-entitlement-iam-plane-separation.md) (the sibling entitlement-plane ADR that **foreshadowed this axis** and deliberately made `subscription.manage` a *separately-grantable* permission so this ADR can include or exclude it from the tenant-admin authority — D5), [ADR-MONO-019](ADR-MONO-019-platform-console-customer-tenant-model.md) (the customer-tenant model; a tenant-admin is a customer-scoped operator within it), [ADR-MONO-021](ADR-MONO-021-account-type-claim-source.md) (`account_type` — a tenant-admin is an `OPERATOR`, orthogonal person axis), [ADR-002 (GAP)](../../projects/iam-platform/docs/adr/ADR-002-admin-tenant-scope-sentinel.md) (the RBAC permission model + the `'*'` platform-scope sentinel this ADR's confinement reads), `projects/iam-platform/specs/services/admin-service/rbac.md` (the Seed Roles + Permission Evaluation Algorithm this ADR extends with a confinement step), `projects/iam-platform/apps/admin-service/.../OperatorOrgScopeController.java` + `.../operators/**` (the operator-management surface whose authority this ADR tenant-scopes), `rules/traits/multi-tenant.md` (M1-M7 — the row-level isolation this delegation must never weaken).

---

## 1. Context

### 1.1 What exists, and the delegation gap

The IAM plane today (ADR-020 + admin-service RBAC):

- **Operators** (`admin_operators`) carry a home `tenant_id` and one-or-more roles (`admin_operator_roles`, each row itself carrying a `tenant_id` since V0025/V0026).
- **Assignments** (`operator_tenant_assignment`, ADR-020 D1) are the N:M operator↔customer-tenant grants that let an operator assume-tenant into a customer and operate its domains; each assignment carries an optional `permission_set_id` + `org_scope` (department subtree confinement).
- **Operator-management surface** (all gated `operator.manage`): `GET/POST /api/admin/operators`, `PATCH .../{id}/roles`, `PATCH .../{id}/status`, `GET .../{id}/assignments`, `PUT .../{id}/assignments/{tenantId}/org-scope`.
- **Permission evaluation** (`rbac.md`): operator → roles → permissions **union**; the annotation's required key must be in the union. **No confinement of *which tenant's* operators/assignments the actor may touch** beyond the org-scope endpoint's primitive `path tenantId == X-Tenant-Id` check (and even that trusts the actor's *self-selected* active tenant, gated only by their having `operator.manage` at all).

The gap: **`operator.manage` is seeded ONLY on `SUPER_ADMIN`** (`rbac.md` Seed Matrix), and `SUPER_ADMIN` is platform-central (home `tenant_id='*'`). So:

- A customer (`acme-corp`) cannot have an administrator who manages acme-corp's own operators. Onboarding an acme employee or an outsourced-operations partner to acme-corp — the literal "a회사 관리자가 a회사 직원/외부업체에 권한 부여" scenario from the design discussion — requires a platform `SUPER_ADMIN` to do it for them.
- There is **no assign/unassign endpoint** for `operator_tenant_assignment` at all: rows are created by SQL seed, and the only assignment mutation is org-scope on an *existing* row. The core delegation operation ("grant my employee access to my tenant") has no surface.

### 1.2 The production shape (AWS Organizations delegated-admin / GCP project-IAM-admin parity)

| Concept | AWS | GCP | platform portfolio (target) |
|---|---|---|---|
| Central super-admin | Management-account root / `AdministratorAccess` | Org-level `roles/resourcemanager.organizationAdmin` | `SUPER_ADMIN` (`tenant_id='*'`) — unchanged |
| A boundary's own admin | **Delegated administrator** for a member account / OU | **Project IAM Admin** (`roles/resourcemanager.projectIamAdmin`) — manages IAM **within that project only** | **`TENANT_ADMIN @ <tenant>`** — `operator.manage`-class authority **confined to that tenant** |
| What the boundary-admin may do | Manage IAM principals/policies **within its delegated scope**; cannot touch other accounts or escalate beyond its grant | Grant/revoke roles on **its project**; cannot grant a role it doesn't hold; cannot edit the org policy | Assign/unassign/scope operators **for its tenant(s)**; grant only non-platform, ≤-own roles; cannot reach other tenants or become `SUPER_ADMIN` |
| The hard rule | No privilege escalation: a delegated admin cannot grant more than it has, nor escape its scope | Same — IAM admin on a project is bounded by the project + cannot self-elevate | Same — confinement + no-escalation are invariants (D2/D3) |

The decisive property the portfolio is missing: a **scoped administration boundary** — an authority that is real (manage operators/assignments) but **confined** (one tenant) and **non-escalating** (cannot exceed or escape its grant). This is exactly AWS delegated-administrator / GCP project-IAM-admin.

### 1.3 Why an ADR (HARDSTOP-09) + staged PROPOSED → ACCEPTED

Per `platform/hardstop-rules.md` HARDSTOP-09: implementing "a tenant-admin manages its own operators" without first deciding **(a)** the role/scope model, **(b)** the confinement enforcement point + invariant, and **(c)** the escalation boundary would bake a security-critical authorization model silently — and authorization escalation bugs are the most expensive class to retrofit. And because this **extends the ADR-020 operator-assignment IAM plane** (introducing a non-SUPER_ADMIN writer of assignment rows + a confinement dimension ADR-020 did not specify), HARDSTOP-04 requires the extension be recorded in an ADR, not applied implicitly. This is the same prevention role ADR-019/020/021/023 played for their axes.

**Staged pattern (sibling: ADR-019/020/021/023):** PROPOSED records the **decision direction** (D1-D7) + the **hard invariants the chosen direction must inherit** (multi-tenant M1-M7 row-isolation untouched; `SUPER_ADMIN` net-zero; no privilege escalation; request-time scope resolution for revoke-immediacy; ADR-023 plane separation preserved) + the **zero-regression migration roadmap**. The ACCEPTED transition is a separate user-explicit-intent-gated task; the execution steps remain **PAUSED** until ACCEPTED.

---

## 2. Decision

> Direction is **CHOSEN-PROPOSED**; to be finalised (byte-unchanged) at ACCEPTED. Each decision lists the chosen option + the rejected alternatives.

### D1 — Tenant-admin role model

| Option | Mechanics | Verdict |
|---|---|---|
| **A. A new seed role `TENANT_ADMIN` holding `operator.manage`, made tenant-scoped by the *assignment row's* `tenant_id` (the existing `admin_operator_roles.tenant_id`, V0025/26) — the role is generic, the SCOPE is the grant** | `TENANT_ADMIN` is a seed role whose permission set = `{operator.manage}` (+ `account.read`/`audit.read` confined to its tenant, optional). A platform `SUPER_ADMIN` grants it as a **tenant-scoped row** `admin_operator_roles(operator_id, role=TENANT_ADMIN, tenant_id='acme-corp')`. The operator's **effective admin-scope** for a permission = the set of `tenant_id`s of the role-rows that grant it (`'*'` ⇒ platform-all). So "TENANT_ADMIN @ acme-corp" carries `operator.manage` scoped to `{acme-corp}`. `SUPER_ADMIN` keeps its `tenant_id='*'` row ⇒ scope `{*}` ⇒ unchanged. | **CHOSEN** — reuses the `admin_operator_roles.tenant_id` column that already exists for exactly this purpose; the role stays a simple permission-set while scope is data-driven per grant (so one role definition serves every customer); `'*'` keeps `SUPER_ADMIN` byte-identical (net-zero); mirrors GCP "same role, scoped to a project". |
| B. A distinct permission `operator.manage.tenant` separate from `operator.manage` | Two permissions, one platform-wide one tenant-scoped | Rejected — duplicates the permission catalog and the endpoint annotations; the scope is a property of the *grant*, not the *permission* — encoding it in a second key forks every operator endpoint's annotation and the evaluator. |
| C. Reuse `SUPER_ADMIN` with `tenant_id != '*'` | A "scoped super-admin" | Rejected — `SUPER_ADMIN` implies the *full* platform permission set (account.lock platform-wide, tenant.manage, subscription.manage); silently scoping it down conflates "platform super-admin" with "tenant admin" and risks a mis-seed handing a customer platform powers. The tenant-admin authority must be a **deliberately minimal** role. |

### D2 — Scope-confinement invariant (the crux; no cross-tenant reach)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. The permission evaluation gains a TARGET-tenant confinement step: an `operator.manage` action is allowed iff the actor holds the permission AND the action's target tenant ∈ the actor's effective admin-scope; `'*'` scope ⇒ all tenants (net-zero for SUPER_ADMIN)** | Extend `rbac.md`'s evaluation: after the union check passes, compute `effectiveScope(actor, permission)` = `{tenant_id of each admin_operator_roles row granting that permission}`; if it contains `'*'` ⇒ platform-all. Every operator/assignment **mutation** resolves a **target tenant** (the assignment's `tenant_id`, the managed operator's home `tenant_id`, or the request's tenant) and is **denied (403 `TENANT_SCOPE_DENIED`, audited)** unless `target ∈ effectiveScope`. Reads are likewise scoped (an actor lists only operators/assignments in scope — the MONO-175 tenant-scoped list already does this for the active tenant; this generalizes it to the actor's whole scope). The resolution is **request-time from `admin_db`** (no token claim — `rbac.md` D5 revoke-immediacy). | **CHOSEN** — one confinement rule, enforced centrally (the existing `RequiresPermissionAspect` path), not scattered per-endpoint; `'*'` makes `SUPER_ADMIN` provably net-zero; resolving from DB keeps revoke immediate; the target-tenant resolution reuses data already on every row. This **is** the AWS/GCP "scoped admin cannot reach outside its boundary" guarantee made mechanical. |
| B. Per-endpoint ad-hoc tenant checks | Each controller re-implements "is the target in my tenant" | Rejected — the exact scattering that breeds escalation holes; the org-scope endpoint's lone `path==X-Tenant-Id` check is already a weaker, inconsistent instance. One central rule is auditable; N per-endpoint rules are not. |
| C. Carry the actor's scope in the operator JWT claim | Token embeds `admin_scope: [tenants]` | Rejected — `rbac.md` D5 resolves roles/permissions request-time precisely so a revoke is immediate; embedding scope in the token re-introduces the staleness D5 rejects (a revoked tenant-admin would keep acting until token expiry). |

### D3 — Delegable surface + grant-menu confinement (privilege-escalation prevention)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. A new assign/unassign surface for `operator_tenant_assignment`, plus a strict no-escalation rule on role grants — a tenant-admin may grant only roles it could itself be granted (non-platform, ≤ its own permission set), and never the delegation role itself (no sub-delegation in v1, D4)** | (i) Add the missing **assign/unassign** endpoints (`POST/DELETE /api/admin/operators/{id}/assignments/{tenantId}` — create/remove an `operator_tenant_assignment` row), gated `operator.manage` + the D2 confinement (a `TENANT_ADMIN @ acme` may assign/unassign operators **to acme only**) + reason-gated + audited. (ii) The **role-grant menu is confined**: when a tenant-admin sets an operator's roles (`PATCH .../roles`), the *grantable* set excludes (a) `SUPER_ADMIN` and any platform/privileged role, (b) `TENANT_ADMIN` itself (no sub-delegation, D4), and (c) any permission the actor does not itself hold (**no granting more than you have**). A violation → `403 ROLE_GRANT_FORBIDDEN`, audited. `SUPER_ADMIN` is unconstrained (its menu = all roles) ⇒ net-zero. | **CHOSEN** — supplies the missing core operation (assign/unassign = the literal "grant my employee access to my tenant") and bounds the delegated authority with the two canonical no-escalation rules (can't exceed your grant, can't escape your scope); confining the *menu* (not just the action) gives a clean UX + a fail-closed server check; `SUPER_ADMIN` unconstrained keeps platform onboarding unchanged. |
| B. Let a tenant-admin grant any role (incl. SUPER_ADMIN/TENANT_ADMIN) within its tenant | Unconfined grant menu | Rejected — direct privilege escalation: a tenant-admin could mint a SUPER_ADMIN or a peer/parent tenant-admin; the single most dangerous IAM bug. |
| C. No assign/unassign surface; tenant-admin only edits org-scope of pre-seeded rows | Keep assignment creation SQL-only | Rejected — leaves the core delegation operation (granting access) impossible without a platform operator + raw SQL; defeats the purpose. |

### D4 — Delegation grant authority + sub-delegation (v1 conservative)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Only `SUPER_ADMIN` may grant/revoke `TENANT_ADMIN` (platform-gated onboarding); a `TENANT_ADMIN` may NOT create another `TENANT_ADMIN` (no sub-delegation in v1)** | Granting the `TENANT_ADMIN` role to an operator (the act that *creates* a tenant-admin) requires `operator.manage` **at platform scope** (`'*'`) — i.e. `SUPER_ADMIN` — because `TENANT_ADMIN` is excluded from every tenant-admin's grant menu (D3-ii-b). A customer's tenant-admin manages *operators* in its tenant but cannot manufacture peers. Sub-delegation (a tenant-admin appointing further tenant-admins for its tenant) is a **deliberately deferred v2 decision** — when wanted, a future amendment adds a `tenant.admin.delegate` permission + a self-tenant-only grant path. | **CHOSEN** — v1 keeps the escalation surface minimal and platform-auditable (every tenant-admin is created by a platform operator), matching AWS's "the management account designates delegated administrators"; sub-delegation is a real feature but a separable, riskier decision that should not be defaulted-on. |
| B. Allow sub-delegation now (tenant-admin appoints tenant-admins within its tenant) | Self-tenant `TENANT_ADMIN` grant allowed | Deferred (not rejected) — a legitimate v2; excluded from v1 to bound the initial escalation surface. The D1 role + D2 confinement are designed so it plugs in as a permission + grant-path addition without re-opening D1-D3. |

### D5 — Entitlement-plane boundary (relationship to ADR-023; what a tenant-admin does NOT get)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. `TENANT_ADMIN` does NOT include `subscription.manage` — entitlement/billing stays platform-controlled; the tenant-admin manages PEOPLE (operators/assignments), not its tenant's paid entitlements** | Per ADR-023 D2/D3 the entitlement plane and IAM plane are separate and `subscription.manage` is *separately grantable*. v1 grants the tenant-admin only the **IAM-plane** operator-management authority; it does **not** grant `subscription.manage`, so a customer cannot self-subscribe/suspend its own domain entitlements (that is a platform/billing decision — exactly GCP, where a project IAM admin cannot change the project's *billing*). Self-service entitlement is a deliberate future choice: because ADR-023 made the permission separable, a later amendment (or a `TENANT_BILLING_ADMIN` role) can grant tenant-scoped `subscription.manage` **without touching D1-D4**. | **CHOSEN** — keeps the two planes' delegation independent (the precise reason ADR-023 D3 made the permission separable); avoids handing customers self-service billing/entitlement before a billing model exists (ADR-023 D5); the IAM-plane delegation is useful and shippable on its own. |
| B. Bundle `subscription.manage` into `TENANT_ADMIN` | Tenant-admin self-manages entitlements too | Rejected for v1 — couples IAM delegation to entitlement self-service; lets a customer activate paid domains with no billing gate; conflates the two planes ADR-023 separated. |

### D6 — Token/claim + propagation (net-zero to the issuance pipeline)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. No new token claim; the actor's admin-scope is resolved request-time from `admin_db`; the assume-tenant assignment-check (ADR-020 D2) is unchanged** | Tenant-admin authority is an **admin-service-local** authorization concern (who may call `/api/admin/**` for which tenant) — it is evaluated entirely inside admin-service from `admin_operator_roles` (D2), exactly like every other operator permission (`rbac.md` D5). The **domain-facing** assume-tenant token pipeline (ADR-020 D2 `/internal/operator-assignments/check`, ADR-019/023 `entitled_domains`) is **untouched**: a tenant-admin who is *also* assigned to operate a tenant still gets its domain token the same way; being a tenant-admin grants *administration* rights, not additional *domain* entitlement. No new claim, no customizer change. | **CHOSEN** — keeps the delegation decision off the token (revoke-immediacy, D2/rbac.md D5); zero change to the auth-service issuance pipeline + the ADR-020/023 token contracts (net-zero); the administration plane (`/api/admin/**`) and the domain plane (assume-tenant) stay cleanly separate. |
| B. Add an `admin_scope` claim to the operator token | Token carries tenant-admin scope | Rejected — staleness (D2-C) + couples a pure admin-service concern to the token contract. |

### D7 — Migration phasing (zero-regression; SUPER_ADMIN net-zero; BE-303/BE-317 discipline)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Backward-compatible staged migration, each step independently main-GREEN; ACCEPTED is step 0** | **Step 0 (doc-only):** this ADR PROPOSED → ACCEPTED (user-gated) + ADR-020 additive amendment + `ADR-MONO-003a § 3` audit row. **Step 1 (admin-service, net-zero):** the D2 **scope-confinement** evaluation — `effectiveScope` + the target-tenant gate on the existing operator/assignment endpoints; **net-zero because the only seeded `operator.manage` holder is `SUPER_ADMIN` (`'*'`), which passes the gate for every tenant** — IT proves every existing endpoint is byte-identical for `SUPER_ADMIN`. **Step 2 (admin-service):** the `TENANT_ADMIN` seed role (D1) + the assign/unassign surface + the grant-menu confinement (D3) + audit; `subscription.manage` deliberately excluded (D5). **Step 3 (federation-e2e + IT, the delegation proof):** a `TENANT_ADMIN @ acme-corp` can assign/scope/unassign an operator **to acme-corp**, is **denied** (403 `TENANT_SCOPE_DENIED`) for `globex-corp`, and is **denied** (403 `ROLE_GRANT_FORBIDDEN`) when attempting to grant `SUPER_ADMIN`/`TENANT_ADMIN`; a platform `SUPER_ADMIN` is unaffected throughout. | **CHOSEN** — step 1 is provably net-zero (only `'*'` holds the permission today); the new authority + surface is isolated to step 2 behind the new role; step 3 is the executable assertion of the D2/D3 confinement + no-escalation invariants — the BE-303 "0 failing required checks at merge" + BE-317 staged discipline; mirrors ADR-023's net-zero-then-surface-then-proof shape. |
| B. Big-bang (role + confinement + assign surface + proof in one PR) | Single atomic flip | Rejected — couples the net-zero confinement to the new surface + role; harder to bisect; violates the staged discipline; a confinement bug would ship with the surface that exercises it. |

---

## 3. Consequences

### 3.1 Hard invariants this ADR carries

- **No privilege escalation** — a tenant-admin can never grant a role/permission it does not itself hold, never grant a platform/privileged role, never (v1) create another tenant-admin (D3/D4). The two canonical rules (≤-own, in-scope) are enforced server-side, fail-closed, audited.
- **Scope confinement is central + fail-closed** — `operator.manage` actions are gated by `target tenant ∈ effective admin-scope` in one place (the evaluator), not per-endpoint (D2). Out-of-scope → 403 `TENANT_SCOPE_DENIED` + an `admin_actions` DENIED row (`rbac.md` D3).
- **`SUPER_ADMIN` is net-zero** — the `'*'` platform-scope sentinel passes confinement for all tenants and has the full grant menu; every existing endpoint is byte-identical for `SUPER_ADMIN` (D1/D2/D7 step 1).
- **Administration plane ≠ domain plane** — tenant-admin authority lives in admin-service `/api/admin/**`, resolved request-time from `admin_db`; the assume-tenant domain-token pipeline (ADR-020 D2 / ADR-019/023 `entitled_domains`) is untouched (D6). No new token claim.
- **Plane separation preserved (ADR-023)** — the tenant-admin gets IAM-plane operator-management only, NOT `subscription.manage`; entitlement/billing stays platform-controlled, separately grantable later (D5).
- **multi-tenant M1-M7 untouched** — `tenant_id` stays the single row-isolation key; this ADR governs *who may administer operators for a tenant*, not row-level data isolation. Confinement is an authorization layer above M1-M7, never a relaxation of it.
- **Revoke-immediacy (rbac.md D5)** — scope + roles resolved request-time; revoking a tenant-admin's `TENANT_ADMIN` row takes effect on the next request (bounded by the 10s perm-cache TTL).

### 3.2 What this ADR does NOT do (deferred to ACCEPTED + post-ACCEPTED execution)

- No implementation: no `TENANT_ADMIN` seed migration, no `effectiveScope`/confinement code, no assign/unassign endpoints, no grant-menu rule, no IT — all post-ACCEPTED execution tasks (§ 3.3).
- No sub-delegation — a tenant-admin cannot appoint tenant-admins (D4-A); D4-B is a deferred v2.
- No tenant-scoped `subscription.manage` — entitlement self-service is out (D5-A); a future `TENANT_BILLING_ADMIN`/amendment may add it.
- No change to the assume-tenant token pipeline or any token claim (D6).
- No change to ADR-020 D1-D6 bodies — the only ADR-020 change is an additive § History "Additive note" blockquote (HARDSTOP-04).
- No change to ADR-019/021/023 (orthogonal / sibling); ADR-023's `subscription.manage` separability is *consumed*, not modified.

### 3.3 Future-self (post-ACCEPTED execution roadmap — sketch, finalised at ACCEPTED)

0. **`TASK-MONO-2xx`** (sibling of MONO-206) — ADR-MONO-024 PROPOSED → ACCEPTED transition (doc-only, user-explicit-intent gated); ADR-020 additive amendment; `ADR-MONO-003a § 3` audit-row append.
1. **`TASK-IAM-BE-xxx`** (admin-service, post-ACCEPTED) — D2 scope-confinement: `effectiveScope(actor, permission)` + target-tenant gate in the `RequiresPermissionAspect`/evaluator + `rbac.md` algorithm update; **net-zero proof** (every operator/assignment endpoint byte-identical for `SUPER_ADMIN` `'*'`). Model = **Opus** (authorization-evaluator change; security-critical).
2. **`TASK-IAM-BE-xxx`** (admin-service, post-ACCEPTED) — `TENANT_ADMIN` seed role (D1) + assign/unassign `operator_tenant_assignment` surface (D3-i) + grant-menu confinement (D3-ii) + audit; `subscription.manage` excluded (D5). `rbac.md` Seed Roles/Matrix + `admin-api.md` updated. Model = **Opus** (new authority + surface + escalation rules).
3. **`TASK-MONO-2xx`** (federation-e2e + admin IT, post-step-2) — the delegation proof (D7 step 3): `TENANT_ADMIN @ acme` assigns/scopes/unassigns within acme (200), is denied cross-tenant (`globex`, 403 `TENANT_SCOPE_DENIED`) and on escalating grants (`SUPER_ADMIN`/`TENANT_ADMIN`, 403 `ROLE_GRANT_FORBIDDEN`); `SUPER_ADMIN` unaffected. Model = **Opus** (the confinement + no-escalation executable assertion). Reuses the MONO-207 federation-e2e dedicated-tenant + admin-surface harness.

No step beyond 3 is scoped here; sub-delegation (D4-B) and tenant-scoped entitlement self-service (D5 future) are each a separate amendment/ADR.

---

## 4. Alternatives Considered

The D1-D7 tables enumerate per-axis alternatives. The cross-cutting alternatives:

- **Do nothing — keep `operator.manage` SUPER_ADMIN-only (platform manages every customer's operators).** Rejected as the *default but not the decision* — workable for a tiny portfolio but not a real SaaS shape (customers cannot self-administer; every onboarding is a platform ticket). Recording the delegation model costs nothing until a step executes; execution stays opt-in.
- **One unconfined `operator.manage` granted per-tenant (no central confinement).** Rejected — relies on every endpoint to self-check tenant scope (D2-B); the escalation-hole generator.
- **Scoped `SUPER_ADMIN`.** Rejected (D1-C) — conflates platform super-admin with tenant admin; a mis-seed hands platform powers.
- **Bundle entitlement self-service into the tenant-admin.** Rejected (D5-B) — couples IAM delegation to billing/entitlement before a billing model exists; violates the ADR-023 plane separation that deliberately kept `subscription.manage` separable.
- **Allow sub-delegation in v1.** Deferred (D4-B) — legitimate but a larger escalation surface; the model is designed so it plugs in later.

---

## 5. Relationship to ADR-MONO-019 / 020 / 021 / 023

| | ADR-019 (customer-tenant) | ADR-020 (operator IAM plane) | ADR-021 (account_type) | ADR-023 (entitlement plane) | **ADR-024 (this)** |
|---|---|---|---|---|---|
| Axis | Customer-tenant model + entitlement table | Operator↔tenant N:M assignment + permission-set/org-scope | CONSUMER/OPERATOR person classification | Entitlement lifecycle + plane separation | **Delegated administration of the IAM plane (tenant-scoped operator-management)** |
| Relationship | A tenant-admin is a customer-scoped operator in this model | **Amends** § D1 additively — decides who (≠ SUPER_ADMIN) may write assignment rows + the confinement dimension | **Orthogonal** — a tenant-admin is an `OPERATOR` | **Composes** — consumes `subscription.manage` separability (D5 excludes it from the tenant-admin); inherits the plane-separation invariant | — |

This ADR amends ADR-MONO-020 § History additively (records the delegated-administration model + tenant-scope confinement for `operator_tenant_assignment`; D1-D6 byte-unchanged) and is a prerequisite for the post-ACCEPTED 3-step execution roadmap. ADR-019/021/023 invariants are inherited unchanged; ADR-023's `subscription.manage` separability is the seam D5 deliberately leaves un-consumed for v1.

---

## 6. Status Transition History

Append-only.

| Date | Transition | Decision direction | User intent quote | PR(s) |
|---|---|---|---|---|
| 2026-06-10 | created PROPOSED | D1 = new `TENANT_ADMIN` seed role holding `operator.manage`, tenant-scoped by the grant row's `admin_operator_roles.tenant_id` (`'*'`=platform, SUPER_ADMIN net-zero); D2 = central target-tenant confinement in the evaluator (`target tenant ∈ effective admin-scope`, `'*'`=all; request-time DB, no claim); D3 = new assign/unassign `operator_tenant_assignment` surface + grant-menu confinement (no role above-own, no platform role, no `TENANT_ADMIN` self-grant); D4 = only SUPER_ADMIN grants TENANT_ADMIN, no sub-delegation in v1 (B deferred); D5 = NO `subscription.manage` for tenant-admin — entitlement/billing platform-controlled (ADR-023 separability consumed as exclusion); D6 = no token claim, admin-service-local request-time resolution, assume-tenant pipeline untouched; D7 = staged net-zero migration (confinement net-zero → role+surface+menu → delegation proof e2e) | "① 테넌트관리자 위임 ADR" (TASK-MONO-208 — after ADR-023 closure the user, asked to pick the next initiative from the AWS/GCP-comparison improvement list, selected axis ① tenant-admin delegation; authored as a committed ADR per the staged ADR-019/020/021/023 pattern) | #<this> (TASK-MONO-208) |

---

## 7. Provenance

- HARDSTOP-09 (`platform/hardstop-rules.md`) — mandate for an ADR + PAUSE-until-ACCEPTED on an undocumented architecture decision (the tenant-admin delegation model + scope-confinement + escalation boundary).
- HARDSTOP-04 (`platform/hardstop-rules.md`) — the ADR-MONO-020 amendment is an additive § History "Additive note" blockquote only; D1-D6 byte-unchanged.
- ADR-MONO-020 D1 (`operator_tenant_assignment` N:M + per-assignment permission-set/org-scope — the IAM plane this ADR delegates administration of) + D2 (`/internal/operator-assignments/check` effective-scope read, unchanged).
- ADR-MONO-023 D2/D3 (entitlement↔IAM plane separation; `subscription.manage` made separately-grantable — D5 consumes the separability as a v1 exclusion) + its repeated foreshadowing of "the ① tenant-admin delegation ADR".
- ADR-MONO-019 (customer-tenant model), ADR-MONO-021 (account_type — orthogonal).
- ADR-002 (GAP) — RBAC permission model + `'*'` platform-scope sentinel the confinement reads. ADR-001 (GAP) — central OIDC IdP.
- `rules/traits/multi-tenant.md` M1-M7 — row-level isolation; this ADR is an authorization layer above it, never a relaxation.
- Code evidence: `admin-service/.../domain/rbac/Permission.java` (`operator.manage` catalog), `specs/services/admin-service/rbac.md` (Seed Matrix — `operator.manage` on `SUPER_ADMIN` only; Permission Evaluation Algorithm — union with no target confinement), `admin-service/.../OperatorOrgScopeController.java` (assignment surface today: list + org-scope only, no assign/unassign; lone `path==X-Tenant-Id` primitive confinement), `admin_operator_roles.tenant_id` (V0025/V0026 — the scope column D1 reuses), `operator_tenant_assignment` (V0030/V0031 — the rows D3 adds assign/unassign for).

분석=Opus 4.8 / 구현=Opus 4.8 (tenant-admin delegation authorization architecture; D1-D7 PROPOSED-direction reasoning under HARDSTOP-04/09 discipline; AWS Organizations delegated-administrator / GCP project-IAM-admin parity; central scope-confinement + no-escalation invariants; staged net-zero migration mirroring ADR-023/BE-303/BE-317 discipline; staged-child ADR pattern per ADR-019/020/021/023).
