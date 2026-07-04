# ADR-MONO-044 — Self-Service B2B Tenant Onboarding (an authenticated visitor creates a NEW tenant and is appointed its first `TENANT_ADMIN` with zero platform-operator in the loop; AWS "create account → root" / GCP "create project → owner" parity)

**Status:** ACCEPTED

**Date:** 2026-07-04

**History:** PROPOSED 2026-07-04 (TASK-MONO-325 — records the **self-service tenant-onboarding bootstrap**: how a brand-new visitor, with **no platform `SUPER_ADMIN` in the loop**, creates a new customer tenant and becomes its **first `TENANT_ADMIN`**, scoped to that tenant only. Decisions D1–D7, **CHOSEN-PROPOSED** direction per the reasoning below; the ACCEPTED transition is a separate user-explicit-intent-gated task, mirroring the ADR-019/020/023/024/030/042 staged-child pattern. **No implementation in this task — decision record + impact scope + migration roadmap only. Self-ACCEPT prohibited.**) · ACCEPTED 2026-07-04 (TASK-MONO-325 — user-explicit ACCEPT at the required gate after the D1–D7 decisions were presented for review, with **one user-directed gate adjustment: D6 → also grant `TENANT_BILLING_ADMIN` to the first admin at onboarding** (so the tenant owner can immediately self-enable domain subscriptions — the AWS/GCP "owner can turn on services" parity — WITHOUT re-introducing a platform `SUPER_ADMIN` in the loop; plane separation preserved — a role grant, not an auto-subscription). D1–D5, D7 directions **finalised byte-unchanged**; D6 mechanics updated for the adjustment. The gate was honored — the PROPOSED decisions were presented and the decision awaited before this flip; **NOT a self-ACCEPT**. Delivered in the same branch as the PROPOSED record (sibling precedent: ADR-042); both §6 rows preserve the staged-child trail. This authorizes the §3.4 execution roadmap (now UNPAUSED).)

**Decision driver:** [ADR-MONO-024](ADR-MONO-024-tenant-admin-delegation.md) built the **delegated-administration boundary** — a `TENANT_ADMIN` role holding `operator.manage` scoped (via the `admin_operator_roles.tenant_id` grant row) to a single tenant, plus `TENANT_BILLING_ADMIN` (`subscription.manage`) and in-tenant sub-delegation (`tenant.admin.delegate`). So "a customer's own admin manages its operators/subscriptions within its tenant" **exists**. But ADR-024 D1 fixes the origin of that authority as *"A platform `SUPER_ADMIN` grants it as a tenant-scoped row."* Every tenant-creation and first-operator-appointment surface in the platform is gated by an operator token (`token_type=admin`) — verified across `specs/contracts/http/admin-api.md` (all `/api/admin/**` tenant/operator/subscription endpoints require `operator.manage`-class authority). **Consequently there is no path for a self-service visitor to create a new tenant and become its administrator** — the "AWS create-account → root user" / "GCP create-project → owner" moment is missing. Onboarding a brand-new customer requires a platform `SUPER_ADMIN` to (a) create the tenant and (b) appoint its first `TENANT_ADMIN`. Building this self-service bootstrap during implementation would silently bake a **security-critical privilege-origination model** (HARDSTOP-09): whether the self-elevation is confined to the *new* tenant only, how a half-provisioned tenant is prevented, what trust gate stops tenant-spam, and how the `SUPER_ADMIN` net-zero + no-escalation invariants (ADR-024 D2/D3, BE-467/468 tenant confinement) survive when a non-operator can now originate a `TENANT_ADMIN` grant — each is load-bearing and must not be chosen implicitly. This ADR is that decision record.

**Supersedes:** none. **Amends:** [ADR-MONO-024](ADR-MONO-024-tenant-admin-delegation.md) § D1 (additive — records that the **origination of the first `TENANT_ADMIN` for a NET-NEW tenant, without a platform `SUPER_ADMIN`, is decided here**; ADR-024 D1–D7 bodies byte-unchanged. ADR-024's *"`SUPER_ADMIN` grants it"* remains the origination path for `TENANT_ADMIN`s of **existing** tenants and for all sub-delegation; this ADR adds the **self-service origination path for a new tenant's first admin only** — HARDSTOP-04 additive-supersession discipline). **Reconciles:** none (PROPOSED scopes the architecture; no code/spec/contract/seed changes — D7 preserves current shapes during the staged window; reconciliation lands at post-ACCEPTED execution tasks, never inside this ADR).

**Related:** [ADR-MONO-024](ADR-MONO-024-tenant-admin-delegation.md) (**the delegated-admin boundary this ADR originates self-service** — its `TENANT_ADMIN`/`TENANT_BILLING_ADMIN` roles, tenant-scoped grant rows, and D2/D3 confinement + no-escalation invariants are **reused, not re-decided**; only the *first-admin origination* is new), [ADR-MONO-019](ADR-MONO-019-platform-console-customer-tenant-model.md) (the customer-tenant model — the `tenants` table + `tenant_domain_subscription` this onboarding creates a row in), [ADR-MONO-020](ADR-MONO-020-operator-multitenant-assignment.md) (`operator_tenant_assignment` — the first admin's home-tenant + self-assignment), [ADR-MONO-023](ADR-MONO-023-entitlement-iam-plane-separation.md) (IAM-plane ↔ entitlement-plane separation — the new tenant starts with **zero** domain subscriptions; entitlement is a separate `TENANT_BILLING_ADMIN` act, D6), [ADR-MONO-032](ADR-MONO-032-unified-identity-roles-model.md) (unified identity — one Global Account is both consumer and this tenant-admin operator, roles derived), [ADR-MONO-036](ADR-MONO-036-born-unified-identity-provisioning.md) (`resolveOrCreate` born-unified mint — the provisioning primitive the first-admin appointment reuses, D5), [ADR-MONO-042](ADR-MONO-042-ecommerce-seller-onboarding-iam-provisioning.md) (**the closest precedent** — seller onboarding mints a real IAM operator account via `resolveOrCreate`; this ADR is the *tenant-level* analogue of that *seller-level* self-provisioning, and its D3 availability crux is revisited here), `projects/iam-platform/specs/services/admin-service/rbac.md` (the Seed Roles + Permission Evaluation the first-admin grant writes into), `rules/traits/multi-tenant.md` M1-M7 (the row-isolation this onboarding must never weaken).

---

> **ACCEPTED (staged, sibling: ADR-019/020/023/024/030/042).** This ADR records the **decision direction** (D1–D7) + the **hard invariants the chosen direction inherits** (multi-tenant M1-M7 untouched; `SUPER_ADMIN` net-zero; self-elevation confined to the NEW tenant only; no privilege escalation; ADR-023 plane separation — new tenant born with zero entitlements) + a **zero-regression roadmap**. PROPOSED → ACCEPTED 2026-07-04 (user-explicit gate, one adjustment: D6 grants `TENANT_BILLING_ADMIN` at onboarding). The §3.4 execution steps are now **UNPAUSED**; each is a separate dependency-ordered task off this ACCEPTED main. **Was NOT a self-ACCEPT** — the decisions were presented and awaited (§6).

---

## 1. Context

### 1.1 What exists (ADR-024) and the origination gap

The IAM plane after ADR-024 (ACCEPTED 2026-06-10):

- **`TENANT_ADMIN`** — a seed role whose permission set is `{operator.manage}` (+ optional tenant-confined `account.read`/`audit.read`), made **tenant-scoped by the grant row** `admin_operator_roles(operator_id, role=TENANT_ADMIN, tenant_id=<t>)`. Effective admin-scope for `operator.manage` = the set of `tenant_id`s of the granting rows (`'*'` ⇒ platform-all). "TENANT_ADMIN @ acme" carries `operator.manage` confined to `{acme}`.
- **`TENANT_BILLING_ADMIN`** (D5-C) — carries `subscription.manage` (entitlement self-service within the tenant), a *separate* role so the IAM plane and entitlement plane stay separable (ADR-023).
- **In-tenant sub-delegation** (D4-B) — a `TENANT_ADMIN` holding `tenant.admin.delegate` may appoint further `TENANT_ADMIN`s **within its own tenant only**.
- **Confinement + no-escalation invariants** (D2/D3) — the target of any operator-management act is confined to the actor's tenant scope; nobody can grant more than they hold or escape their scope. Reinforced by BE-467/468 (tenant-confinement on account mutation + session revoke).

**The gap:** ADR-024 D1 originates `TENANT_ADMIN` **only** via *"a platform `SUPER_ADMIN` grants it."* And a tenant itself is created only by an operator (`admin-service` tenant-management, BE-250, `operator.manage`-gated). So the **entire chain that turns a stranger into the administrator of a new customer** — create tenant → mint the person's operator account → appoint them `TENANT_ADMIN @ <new tenant>` — has **no self-service entry**; it is 100% platform-operator-driven. The "sign up and you own your workspace" front door does not exist.

### 1.2 The production shape (the origination row of the ADR-024 table)

ADR-024 § 1.2 already mapped the *steady-state* delegated-admin to AWS delegated-administrator / GCP project-IAM-admin. This ADR fills the **origination** row that table omitted:

| Concept | AWS | GCP | platform portfolio (this ADR) |
|---|---|---|---|
| **Create the boundary + become its owner, self-service** | Sign up → a **new AWS account** with **you as root** | Create a **new project** → you get `roles/owner` on it | **Self-service onboarding**: authenticated visitor → **new tenant** + they are appointed **`TENANT_ADMIN @ <new tenant>`** |
| Trust gate | Email + payment verification | Google account + (org policy may gate project creation) | Authenticated IAM identity + trust gate (D4) |
| Blast radius of the self-grant | Root of **that new account only** | Owner of **that new project only** | `TENANT_ADMIN` of **that new tenant only** — zero reach into existing tenants |
| Downstream | Root/IAM-admin then manages users **within** the account | Owner grants roles **within** the project | ADR-024 `TENANT_ADMIN`/`BILLING_ADMIN` manages operators/subscriptions **within** the tenant |

The decisive missing property: a **privilege-origination event that is self-service yet blast-radius-confined to a freshly-created isolation boundary**. AWS/GCP both have it (account/project creation); the portfolio does not. This ADR adds exactly it, and **nothing downstream** — the moment the first `TENANT_ADMIN` exists, ADR-024's machinery takes over unchanged.

### 1.3 Why an ADR (HARDSTOP-09 + HARDSTOP-04)

Letting a **non-operator originate a `TENANT_ADMIN` grant** is the single most security-sensitive change to the IAM plane since ADR-024 — it inverts the "only an existing operator can create operators" invariant for exactly one, carefully-bounded case. Implementing it without first deciding **(a)** the confinement (new-tenant-only, D2), **(b)** the atomicity that prevents an admin-less orphan tenant (D3), **(c)** the trust gate against tenant-spam (D4), and **(d)** how the `SUPER_ADMIN` net-zero + no-escalation invariants survive would bake a privilege-origination model silently (HARDSTOP-09). Because it **extends the ADR-024 origination decision** (a second, non-`SUPER_ADMIN` originator), HARDSTOP-04 requires the extension be recorded, not applied implicitly. Same prevention role ADR-019/020/023/024 played.

---

## 2. Decision

Seven axes. Each table's first row is **CHOSEN (PROPOSED direction)**.

### D1 — Onboarding transaction: what a single self-service call provisions

| Option | Mechanics | Verdict |
|---|---|---|
| **A. One atomic "create-organization" transaction: {new tenant} + {caller's operator account/identity, born-unified} + {`TENANT_ADMIN` + `TENANT_BILLING_ADMIN` grants @ new-tenant} + {self-assignment `operator_tenant_assignment`}** | An authenticated caller (a normal IAM user JWT — NOT an operator token) hits a new self-service endpoint. The orchestration creates a `tenants` row (status ACTIVE), resolves-or-creates the caller's central identity + a backing operator account (ADR-036 `resolveOrCreate`, D5), writes tenant-scoped `admin_operator_roles(role=TENANT_ADMIN, tenant_id=<new>)` **and** `(role=TENANT_BILLING_ADMIN, tenant_id=<new>)` grant rows (D6 — so the owner can both administer AND self-enable domains), and an `operator_tenant_assignment(operator, <new tenant>)` so they can assume-tenant into it. All-or-nothing (D3). | **CHOSEN** — mirrors AWS/GCP "one act creates the boundary AND makes you its admin"; reuses every existing primitive (tenant-management BE-250, born-unified mint ADR-036/BE-402, the ADR-024 `TENANT_ADMIN`/`TENANT_BILLING_ADMIN` roles + grant-row scoping); nothing downstream is new. |
| B. Two steps: create tenant, then separately appoint self | caller creates a tenant, then a second call self-appoints | Rejected — a window where a tenant exists with no admin (orphan, D3); two trust-gate surfaces; worse UX; no upside over the atomic A. |
| C. Request/approval queue only (no direct self-serve) | onboarding files a request a platform operator approves | Deferred-not-rejected — a **trust-gate option** (D4-C), not a different transaction shape. If chosen, the *approval* precedes the D1-A transaction; the transaction itself is unchanged. |

### D2 — Blast-radius confinement of the self-grant

| Option | Mechanics | Verdict |
|---|---|---|
| **A. The self-elevation grants `TENANT_ADMIN` on the NEWLY-CREATED `tenant_id` ONLY — never `'*'`, never an existing tenant** | The onboarding path can write **exactly one** grant row and its `tenant_id` is **the id of the tenant it just minted in the same transaction**. It is structurally incapable of granting scope over any pre-existing tenant or the `'*'` platform scope. Downstream, ADR-024 D2/D3 confinement + no-escalation govern everything the new admin does (they can only manage operators/subscriptions within their tenant, cannot self-elevate to `SUPER_ADMIN`, cannot reach other tenants — BE-467/468). | **CHOSEN** — this is the whole safety story: self-service is acceptable **because** the self-grant's blast radius is a fresh, empty, isolated boundary the caller just created (GCP "you own the project you created, nothing else"). `SUPER_ADMIN` stays net-zero; M1-M7 untouched; no existing tenant is reachable. |
| B. Grant a broader bootstrap role | self-grant carries cross-tenant or platform authority | Rejected — catastrophic escalation; defeats confinement; a stranger could reach other customers. Non-starter. |

### D3 — Atomicity / availability stance (the D-crux, HARDSTOP-09 — revisits ADR-042 D3)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Fail-closed + compensating rollback: no tenant without its first admin** | The D1-A steps run as a saga with compensation: if the identity mint or the `TENANT_ADMIN`/assignment write fails, the just-created `tenants` row is rolled back (or marked `PROVISIONING_FAILED` and swept), so onboarding **never yields an orphan tenant with no administrator**. The caller sees an error and retries; no partial tenant lingers. | **CHOSEN** — **opposite of ADR-042 D3 (seller = fail-soft)**, and deliberately: a seller can sit `PENDING_PROVISIONING` and still be a meaningful attribution row, but a **tenant with no admin is a dead, unreachable boundary** (nobody can ever administer it) — worse than no tenant. The onboarding's *product invariant* is "a created tenant always has an owner", so it fails closed. (Contrast recorded so the reader does not mis-copy ADR-042's fail-soft.) |
| B. Fail-soft (tenant created, admin appointment deferred) | tenant ACTIVE even if admin appointment failed | Rejected — produces orphan tenants (D3 hazard); the appointment is the *point*, not a side effect. |

### D4 — Trust gate: who may self-onboard, and abuse control

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Authenticated + email-verified caller, rate-limited, one-active-onboarding guard** | Caller must be an authenticated IAM identity whose **email is verified** (the `signup.md` email-verification path, promoted from optional → required *for the onboarding entry only*); onboarding is rate-limited (per-identity + per-IP) and guarded against runaway tenant creation (e.g. a small cap on ACTIVE tenants owned per identity in the portfolio scope). | **CHOSEN** — email-verified + rate-limit is the minimum that keeps tenant-spam out while staying self-service (matches AWS's email/payment gate at portfolio scale, no billing). Verification requirement is scoped to the onboarding entry so ordinary consumer `/signup` is unchanged. |
| B. Open self-serve (any authenticated identity, no verification) | no email gate | Deferred-partial — acceptable for a pure local demo, but the ADR records verified-gate as the production shape so a later hardening is additive, not a redesign. |
| C. Approval queue (platform operator approves each onboarding) | request → operator approves → D1-A runs | Deferred-not-rejected — the most conservative gate; slots **before** the D1-A transaction. Recorded as the escalation option if abuse is observed; not the default (it re-introduces an operator in the loop, defeating "self-service"). |

### D5 — Identity plane: the first admin is an OPERATOR via born-unified identity

| Option | Mechanics | Verdict |
|---|---|---|
| **A. `resolveOrCreate` (ADR-036) — one central identity; the onboarding adds an OPERATOR facet (`TENANT_ADMIN`) to it** | The caller's email resolves to (or mints) a single central identity (ADR-032/036). Onboarding provisions the **operator** account/facet and the `TENANT_ADMIN` role. If the email was already a **consumer** (e.g. a prior `/signup`), the SAME identity gains the operator facet — one person, unified identity, two facets (consumer + tenant-admin operator), exactly ADR-032. | **CHOSEN** — reuses the born-unified primitive ADR-042 already uses for sellers; avoids a duplicate identity; realizes ADR-032's "one Global Account, roles derived" for the self-onboard case. |
| B. A fresh operator-only account divorced from any consumer identity | separate principal | Rejected — forks identity for the same human (the pollution ADR-032/036 exist to prevent); breaks unified sign-in. |

### D6 — Entitlement at birth: the new tenant starts with ZERO domain subscriptions

| Option | Mechanics | Verdict |
|---|---|---|
| **A. New tenant is born entitlement-empty; the first admin ALSO receives `TENANT_BILLING_ADMIN` so they can self-enable domain subscriptions — but the tenant subscribes to nothing until they act (ADR-023 plane separation)** | Onboarding creates the tenant + admin, granting the first admin **both `TENANT_ADMIN`** (operator management) **and `TENANT_BILLING_ADMIN`** (`subscription.manage`) scoped to the new tenant. Which platform domains (ecommerce/wms/erp/…) the tenant may use is still **not** decided at onboarding — the tenant is born with **zero** `tenant_domain_subscription` rows; the owner **self-enables** domains afterward via the ADR-024 D5-C `subscription.manage` surface. IAM plane (who administers) and entitlement plane (what the tenant may use) stay separate (ADR-023) — the grant is a *capability to subscribe*, not a subscription. | **CHOSEN (ACCEPTED-adjusted 2026-07-04).** The PROPOSED left the `TENANT_BILLING_ADMIN`-at-onboarding grant an optional D7 sub-choice; the ACCEPTED gate **fixes it to YES**. Rationale: without it, a self-onboarded owner can create a tenant but **cannot enable any domain** — a platform `SUPER_ADMIN` would have to grant billing-admin, re-introducing the operator-in-the-loop this whole ADR removes (self-contradiction). Granting it completes the AWS/GCP "owner can turn on services" parity while **preserving plane separation** (a role, not an auto-subscription; the tenant is still entitlement-empty until the owner subscribes). The no-escalation invariant holds — `TENANT_BILLING_ADMIN` is tenant-scoped to the new tenant (ADR-024 D5-C grant-row scoping, D2). |
| B. Auto-subscribe to a default domain set at onboarding | pick domains during signup | Deferred — a UX convenience layerable later; baking it now couples the two planes and pre-decides capability. Recorded as an additive follow-up. |

### D7 — Scope + ownership: vertical slice, orchestrated where the primitives already live

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Vertical slice: one public self-service endpoint (authenticated user JWT, NOT operator-gated) orchestrating {tenant + born-unified operator + `TENANT_ADMIN` (+`TENANT_BILLING_ADMIN`) + self-assignment}, owned by admin-service; prove end-to-end = onboard → console login scoped to the new tenant** | admin-service already owns tenant-management (BE-250) + operator creation + `AccountServiceClient` + the ADR-024 role model, so it is the natural orchestrator. Add **one** new endpoint on a **self-service (authenticated-but-not-operator-gated) surface** — distinct from the `operator.manage`-gated `/api/admin/**` — that runs D1-A under the D2/D3/D4/D5 rules. **Deferred:** org profile management, billing, approval-queue (D4-C), auto-subscribe (D6-B), a polished onboarding UI (a minimal page suffices for the slice), and multi-tenant-per-identity org switching UX. | **CHOSEN** — smallest change that demonstrates the whole model (a stranger self-creates a workspace and administers it) reusing all existing primitives; main stays GREEN; the deferred items are additive. |
| B. New dedicated onboarding-service | a service just for onboarding | Rejected (for the slice) — the primitives (tenant mgmt, operator provisioning, role grant) all live in admin-service; a new service adds deployment/wiring surface for a thin orchestration. Revisit only if onboarding grows billing/approval mass. |
| C. Big-bang (endpoint + billing + approval + UI + auto-subscribe) | everything at once | Rejected — transiently large; violates the zero-regression staged discipline (ADR-019 D6 / ADR-030 D5). |

---

## 3. Consequences

### 3.1 Hard invariants this ADR carries
- **Self-elevation is blast-radius-confined to the freshly-created tenant (D2)** — the origination path can never grant scope over an existing tenant or `'*'`. `SUPER_ADMIN` net-zero; M1-M7 row-isolation untouched; BE-467/468 tenant-confinement + ADR-024 D2/D3 no-escalation govern everything the new admin then does.
- **A created tenant always has an administrator (D3 fail-closed)** — no orphan boundaries; opposite of ADR-042's seller fail-soft, by design.
- **One unified identity (D5)** — the first admin is an operator facet on the caller's central identity (ADR-032/036); a prior consumer becomes consumer+operator, not a second principal.
- **Plane separation preserved (D6)** — the new tenant is entitlement-empty at birth; capability is a separate, auditable subscription act (ADR-023).
- **Downstream is 100% ADR-024 reuse** — once the first `TENANT_ADMIN` exists, operator management, subscriptions, and sub-delegation are unchanged. This ADR adds *only* the origination event.

### 3.2 Costs / risk surface
- **Inverts "only an operator creates operators" for one bounded case** — the highest-scrutiny change; mitigated wholly by D2 (new-tenant-only) + D4 (trust gate) + the fail-closed atomicity.
- **Abuse (tenant-spam)** — self-service tenant creation is a spam vector; D4 (email-verified + rate-limit + cap, or D4-C approval) is the control; the ADR names it so it is not an afterthought.
- **Reversibility** — high. The onboarding endpoint is additive; disabling it reverts to operator-only provisioning with zero data-model change (the tenants/operators it created remain valid ADR-024 objects).

### 3.3 What this PROPOSED ADR does NOT do (deferred to ACCEPTED + execution)
- Does **not** add the endpoint, orchestration, role-grant code, or any UI.
- Does **not** change `SUPER_ADMIN`, the ADR-024 roles/confinement, `rbac.md`, or any operator controller.
- Does **not** promote `signup.md` email verification to required (D4 decides the *intent*; the code lands at execution).
- Does **not** execute the ACCEPTED transition — separate user-explicit-intent-gated task (sibling ADR-024→MONO-209, ADR-030→MONO-231, ADR-042→BE-402).

### 3.4 Post-ACCEPTED execution roadmap (sketch; finalised at ACCEPTED)
0. **`TASK-MONO-3xx`** — ADR-044 PROPOSED → ACCEPTED (doc-only, user-gated). Model = **Opus**.
1. **`TASK-BE-xxx`** (iam-platform) — specs/contracts: the self-service onboarding endpoint contract (public/authenticated, not operator-gated), the D1-A orchestration + D3 saga/compensation, D4 trust gate. Source-of-Truth-first. Model = **Opus** (privilege-origination + confinement).
2. **`TASK-BE-xxx`** (admin-service) — implement the orchestration reusing tenant-mgmt + born-unified mint + ADR-024 `TENANT_ADMIN` **+ `TENANT_BILLING_ADMIN`** grants (D6) + self-assignment; D2 confinement enforcement + D3 compensation + D4 rate-limit; cross-tenant-origination-leak IT (assert the self-grant can only ever target the new tenant, never `'*'`/existing). Model = **Opus** (isolation/escalation).
3. **`TASK-FE/BE-xxx`** — minimal "create organization" flow (self-service page → endpoint → console login scoped to the new tenant) proving end-to-end. Model = **Sonnet** (thin UI over the proven endpoint).
4. **Deferred follow-ups** (each its own task): `TENANT_BILLING_ADMIN` grant-at-onboarding + domain subscription selection (D6-B), org profile management, approval-queue mode (D4-C), billing, multi-org switch UX.

---

## 4. Alternatives Considered
- **Keep operator-only provisioning; no self-service (status quo).** Rejected as the direction — it is the exact gap the user asked to close (AWS/GCP parity); recorded as the trivial retreat if self-service is judged out of portfolio scope.
- **Make ordinary consumer `/signup` itself create a tenant.** Rejected — conflates the consumer front door (a shopper needs no tenant) with the B2B org-creation front door; they are different intents and different trust gates (D4). Onboarding is a *distinct* authenticated act (D7).
- **Grant the first admin `SUPER_ADMIN` or a cross-tenant role.** Rejected (D2-B) — catastrophic escalation.
- **ACCEPTED now, skip PROPOSED.** Rejected — the confinement (D2), atomicity (D3), and trust-gate (D4) are load-bearing security decisions warranting review before code (sibling discipline). Self-ACCEPT prohibited.

---

## 5. Relationship to prior ADRs

| | ADR-MONO-019 | ADR-MONO-024 | ADR-MONO-032/036 | ADR-MONO-042 | **ADR-MONO-044 (this)** |
|---|---|---|---|---|---|
| Axis | customer-tenant model | tenant-admin **delegation** (steady state) | unified + born-unified identity | seller onboarding IAM provisioning | **tenant onboarding — first-admin ORIGINATION** |
| Relationship | **reused** — creates a `tenants` row | **originated** — this ADR is the self-service source of ADR-024's first `TENANT_ADMIN` (amended additively) | **reused** — `resolveOrCreate` mints the admin's operator facet (D5) | **precedent** — the seller-level analogue; D3 availability crux revisited (seller fail-soft → tenant fail-closed) | — |

ADR-024 answered *"how does a tenant-admin manage its tenant."* This ADR answers the prior question *"how does the FIRST tenant-admin of a NEW tenant come to exist, self-service."* Together they are the full AWS/GCP account/project lifecycle: **create-and-own (044) → administer (024)**.

---

## 6. Status Transition History

Append-only.

| Date | Transition | Decision direction | User intent quote | PR(s) |
|---|---|---|---|---|
| 2026-07-04 | created PROPOSED | D1 = one atomic create-organization transaction (tenant + born-unified operator + `TENANT_ADMIN` + self-assignment); D2 = self-grant confined to the NEW tenant only (never `'*'`/existing); D3 = **fail-closed** + compensating rollback (no orphan tenant — opposite of ADR-042 seller fail-soft); D4 = authenticated + email-verified + rate-limited trust gate (approval-queue as escalation option C); D5 = born-unified `resolveOrCreate`, operator facet on the caller's unified identity; D6 = tenant born entitlement-empty, subscriptions a separate ADR-023-plane act (optional `TENANT_BILLING_ADMIN` at onboarding); D7 = vertical slice, one public authenticated (not operator-gated) endpoint orchestrated by admin-service, console-login-scoped proof | (self-service B2B 테넌트 셀프 가입 + 정식 PROPOSED ADR 먼저 — AskUserQuestion 2026-07-04) → "진행" | TASK-MONO-325 |
| 2026-07-04 | PROPOSED → ACCEPTED | D1–D5, D7 **finalised** as the reviewed PROPOSED direction (ACCEPTED *finalises*, does not re-decide). **One user-directed gate adjustment: D6 — the optional `TENANT_BILLING_ADMIN`-at-onboarding grant is fixed to YES** (the first admin receives both `TENANT_ADMIN` + `TENANT_BILLING_ADMIN` scoped to the new tenant, so the owner can self-enable domain subscriptions without a platform `SUPER_ADMIN` — else the ADR would be self-contradictory). D1 mechanics updated to write both grant rows; plane separation preserved (a role, not an auto-subscription; tenant still born entitlement-empty). The §3.4 execution roadmap is UNPAUSED. **NOT a self-ACCEPT** — decisions presented and awaited (recommendation given, user directed "진행" with the D6 adjustment). | "추천은?" → (recommend ACCEPT + D6 adjust) → "진행" | TASK-MONO-325 |

(PROPOSED + ACCEPTED rows appended 2026-07-04 per the ADR-019/023/024/030/042 staged-child format. The ACCEPTED transition was delivered in the **same branch** as the PROPOSED record — sibling precedent ADR-042 — with both §6 rows preserving the staged-child governance trail; it was **NOT a self-ACCEPT** (the D1–D7 decisions were presented for review and the ACCEPT + D6 adjustment came on the user's explicit "진행"). D1–D5/D7 byte-unchanged from PROPOSED; only D6 mechanics reflect the gate adjustment.)

---

## 7. Provenance
- User request 2026-07-04 (a chat comparing the portfolio's "signup → need a grant" model to AWS/GCP "signup → immediately use"; established that AWS/GCP "immediate use" = *creation grants ownership of a new account/project*, which the portfolio lacks) + AskUserQuestion (onboarding unit = **B2B tenant self-signup**; rigor = **formal PROPOSED ADR first**).
- HARDSTOP-09 + HARDSTOP-04 (`platform/hardstop-rules.md`) — the mandate for an ADR + PAUSE-until-ACCEPTED on a self-service privilege-origination decision that extends the ADR-024 origination model.
- [ADR-MONO-024](ADR-MONO-024-tenant-admin-delegation.md) (the delegated-admin boundary this ADR originates; § D1 amended additively), [ADR-MONO-019](ADR-MONO-019-platform-console-customer-tenant-model.md)/[020](ADR-MONO-020-operator-multitenant-assignment.md)/[023](ADR-MONO-023-entitlement-iam-plane-separation.md)/[032](ADR-MONO-032-unified-identity-roles-model.md)/[036](ADR-MONO-036-born-unified-identity-provisioning.md)/[042](ADR-MONO-042-ecommerce-seller-onboarding-iam-provisioning.md), `projects/iam-platform/specs/services/admin-service/rbac.md`, `rules/traits/multi-tenant.md` M1-M7.

분석=Opus 4.8 / 구현 권장=Opus (self-service privilege-origination: new-tenant-confined self-elevation + fail-closed provisioning saga + trust gate, extending the ADR-024 delegated-admin origination under HARDSTOP-09/04; execution mirrors ADR-024/042 born-unified reuse + zero-regression staged discipline).
