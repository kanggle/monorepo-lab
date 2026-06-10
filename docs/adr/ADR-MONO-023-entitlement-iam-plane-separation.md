# ADR-MONO-023 — Entitlement/Subscription Plane ↔ IAM Plane Separation (subscription lifecycle state machine; GCP billing↔IAM parity; entitlement suspension never mutates IAM bindings)

**Status:** ACCEPTED

**Date:** 2026-06-10

**History:** PROPOSED 2026-06-10 (TASK-MONO-205 — authors the decision record for **separating the entitlement/subscription plane from the IAM plane** and for the **subscription lifecycle state machine** that [ADR-MONO-019](ADR-MONO-019-platform-console-customer-tenant-model.md) § D2 left under-specified (it created `tenant_domain_subscription (tenant_id, domain_key, status, …)` and made it the entitlement authority the catalog (D4) and the keystone `entitled_domains` claim (D5) read, but defined neither the `status` state set/transitions, nor a mutation authority, nor what a non-ACTIVE subscription does to operator assignments / RBAC), and that ADR-019 § 3.3 step 2 deferred as an *"admin surface for subscription management (optional)"*. Six decisions D1-D6, **CHOSEN-PROPOSED** direction per the reasoning below; the ACCEPTED transition is a separate user-explicit-intent-gated task, mirroring the ADR-MONO-014/015/017/018/019/020/021 staged-child pattern. **No implementation in this task — decision record + impact scope + migration roadmap only.**) · ACCEPTED 2026-06-10 (TASK-MONO-206 — user-explicit intent *"권장 순서대로 진행"* selecting the recommended A→B order after the PROPOSED #1237 squash `c4a30422` main merge; D1-D6 CHOSEN-PROPOSED direction **finalised byte-unchanged** from PROPOSED — ACCEPTED *finalises*, does not re-decide; this authorizes the § 3.3 3-step execution roadmap as a dependency-correct base. Sibling staged-child ACCEPTED pattern: ADR-019→MONO-153 / ADR-020→MONO-157 / ADR-021→MONO-165.)

**Decision driver:** ADR-MONO-019 D2 introduced `tenant_domain_subscription` as the single entitlement authority and shipped it for the backward-compatible MVP (each domain-slug tenant subscribes to its own domain, all rows `ACTIVE`). The catalog (`ConsoleRegistryUseCase` — intersects subscribers with operator scope ∩ ACTIVE) and the keystone `entitled_domains` claim (`TenantClaimTokenCustomizer` — the selected tenant's ACTIVE subscriptions) both already *read* the table and filter ACTIVE. But three things are unspecified, and each would be silently baked the moment a subscription is ever suspended, cancelled, or administratively changed: **(1)** the `status` column has no defined state set or legal-transition model — only an implicit `ACTIVE` exists; **(2)** there is no decided authority or authorization for *mutating* a subscription — ADR-019 § 3.3 step 2 deferred the admin surface as "optional", so the only writes today are Flyway seeds; **(3)** the relationship between the **entitlement plane** (a tenant's right to use a domain) and the **IAM plane** (which operator may act for a tenant, with what role — [ADR-MONO-020](ADR-MONO-020-operator-multitenant-assignment.md) `operator_tenant_assignment` + RBAC) is implicit — nothing records what happens to operator assignments / RBAC bindings when a subscription is suspended or cancelled. AWS/GCP keep these planes distinct: in GCP, **billing account ↔ project ↔ IAM** are three separable layers — disabling billing blocks API use but leaves the project's IAM policy intact, and re-enabling billing restores access with no re-grant. Resolving the subscription lifecycle + the management authority + the plane-separation invariant during implementation would silently bake the entitlement model (HARDSTOP-09) and silently extend an ADR-019 D2 under-specified decision (HARDSTOP-04). This ADR is that decision record.

**Supersedes:** none. **Amends:** [ADR-MONO-019](ADR-MONO-019-platform-console-customer-tenant-model.md) § D2 (additive § History "Additive note" blockquote recording that the **production form of D2's `status` column + the deferred § 3.3-step-2 "optional admin surface" is the subscription lifecycle + plane-separation model decided here**; ADR-019 D1-D6 + § 2-7 bodies byte-unchanged — HARDSTOP-04 discipline preserved). **Reconciles:** none yet (PROPOSED scopes the architecture; the `tenant_domain_subscription` schema, `ConsoleRegistryUseCase`, and `TenantClaimTokenCustomizer` are byte-unchanged at PROPOSED — D6 explicitly preserves current shapes during the staged window; reconciliation lands at the post-ACCEPTED execution tasks, never inside this ADR).

**Related:** [ADR-MONO-019](ADR-MONO-019-platform-console-customer-tenant-model.md) (parent — created `tenant_domain_subscription` (D2), the subscription-driven catalog (D4), and the entitlement-trust gate (D5); this ADR formalizes D2's lifecycle and realizes its deferred § 3.3-step-2 admin surface), [ADR-MONO-020](ADR-MONO-020-operator-multitenant-assignment.md) (the **IAM plane** this ADR separates entitlement from — `operator_tenant_assignment` N:M + per-assignment permission-set; D2's plane-separation invariant protects these bindings from entitlement changes), [ADR-MONO-021](ADR-MONO-021-account-type-claim-source.md) (`account_type` — the *person* axis; orthogonal to both the entitlement plane and the operator-assignment IAM plane), [ADR-001 (GAP)](../../projects/iam-platform/docs/adr/ADR-001-oidc-adoption.md) (GAP central OIDC IdP — the single entitlement/issuance authority this model keeps centralized), [ADR-002 (GAP)](../../projects/iam-platform/docs/adr/ADR-002-admin-tenant-scope-sentinel.md) (RBAC permission model — the `subscription.manage` permission D3 adds is a sibling of `operator.manage`), `rules/traits/multi-tenant.md` (M1-M7 — untouched; this ADR governs the entitlement lifecycle, not row-level isolation), `projects/iam-platform/apps/account-service/.../TenantDomainSubscription*.java` (the entity + query use-case whose lifecycle this ADR formalizes), `projects/iam-platform/apps/admin-service/.../console/ConsoleRegistryUseCase.java` (the catalog consumer that already filters ACTIVE — unchanged shape), `projects/iam-platform/apps/auth-service/.../oauth2/TenantClaimTokenCustomizer.java` (the `entitled_domains` derivation that already filters ACTIVE — unchanged shape).

> **Separate axis from the tenant-admin delegation ADR (the "①" axis, not yet authored):** the recurring design question *"can an a-company tenant-admin grant its own employees/partners access"* is an **IAM-plane delegation** decision (giving a tenant-scoped operator a `operator.manage`-class permission). This ADR is the **entitlement-plane** decision (subscription lifecycle + who may mutate a subscription). They compose — D3's `subscription.manage` is deliberately a *separately-grantable* permission so the future delegation ADR can hand a tenant-admin entitlement-management without operator-management, or vice-versa — but they are distinct axes and may land in either order.

---

## 1. Context

### 1.1 What ADR-019 D2 shipped, and the three unspecified points

ADR-MONO-019 D2 (CHOSEN): a new account-service-owned `tenant_domain_subscription (tenant_id, domain_key, status, created_at, updated_at)` table, N:M, the single entitlement authority. Step 1 seeded it backward-compatibly (each existing slug-tenant subscribes to its own domain, all `ACTIVE`); step 2 seeded a real customer (`acme-corp`). Two consumers read it today and **both already filter ACTIVE**:

1. **Catalog** — `ConsoleRegistryUseCase.selectableTenants()` resolves a domain product's `tenants[]` = subscribers(domain) ∩ operator-scope ∩ **ACTIVE**.
2. **Token** — `TenantClaimTokenCustomizer` derives `entitled_domains` = the selected tenant's **ACTIVE** subscriptions (ADR-020 D3).

So the *read* path is entitlement-aware. The *write* path and the *lifecycle* are not:

- **(1) No state model.** `status` is a bare column. Only `ACTIVE` is ever written (by seed). There is no defined set of legal states, no transition guard, no terminal state. The first "suspend customer X's finance subscription" has no defined meaning.
- **(2) No mutation authority.** ADR-019 § 3.3 step 2 explicitly deferred *"admin surface for subscription management (optional)"*. There is no endpoint, no permission, no audit path for changing a subscription. Entitlement is currently immutable-after-seed.
- **(3) No plane relationship.** When a subscription is suspended/cancelled, nothing records what happens to the **IAM** facts — the operator's `operator_tenant_assignment` rows (ADR-020) and RBAC bindings (ADR-002). Are they revoked? Preserved? Undefined.

### 1.2 The production shape (GCP billing↔IAM / AWS account-enablement parity)

| Concept | GCP | AWS | platform portfolio (target) |
|---|---|---|---|
| "May this boundary use this service" | **Billing enabled** + **API enabled** on the project | Service enabled / Marketplace subscription on the account | **Entitlement plane** — `tenant_domain_subscription.status = ACTIVE` |
| "May this principal act here, with what role" | **IAM policy** binding on the project | **Permission set** assignment to the account | **IAM plane** — `operator_tenant_assignment` + RBAC (ADR-020/002) |
| Effect of disabling the first | APIs blocked; **IAM policy intact**; re-enable → access returns, no re-grant | Service calls blocked; assignments intact | Suspend subscription → domain drops from catalog + next token's `entitled_domains`; **assignments + RBAC preserved**; re-activate → access returns, no re-grant |

The decisive property the portfolio is missing: the two planes are **separable** and have a **one-way dependency**. Entitlement gates *whether* a domain is reachable; IAM gates *who* may operate it and *how*. Suspending entitlement must not destroy IAM grants — exactly as disabling GCP billing does not wipe a project's IAM policy.

### 1.3 Why an ADR (HARDSTOP-09) + staged PROPOSED → ACCEPTED

Per `platform/hardstop-rules.md` HARDSTOP-09: implementing a subscription suspend/cancel, or an admin mutation surface, without deciding the state machine + the plane-separation invariant would bake the entitlement model silently — e.g. a "suspend" that cascades into revoking operator assignments (losing the grant on re-activation) is a different product than a "suspend" that only blocks the domain; choosing implicitly forecloses the cloud-parity model. And because this **extends an under-specified ADR-019 D2 decision** (the `status` column + the deferred admin surface), HARDSTOP-04 requires the extension be recorded in an ADR, not applied implicitly. This is the exact prevention role ADR-MONO-019/020/021 played for their axes.

**Staged pattern (sibling: ADR-019/020/021):** PROPOSED records the **decision direction** (D1-D6) + the **hard invariants the chosen direction must inherit** (multi-tenant M1-M7 untouched; GAP as the single entitlement authority; short-lived-token self-containment; least-privilege via a distinct permission) + the **zero-regression migration roadmap**. The ACCEPTED transition is a separate user-explicit-intent-gated task; the execution steps remain **PAUSED** until ACCEPTED. **(ACCEPTED transition WAS executed as TASK-MONO-206, 2026-06-10 — D1-D6 finalised byte-unchanged from PROPOSED; the three execution steps are now UNPAUSED and proceed dependency-correct from this ACCEPTED main, beginning with § 3.3 step 1.)**

---

## 2. Decision

> Direction is **CHOSEN-PROPOSED**; finalised (byte-unchanged) at ACCEPTED. Each decision lists the chosen option + the rejected alternatives.

### D1 — Subscription lifecycle state machine

| Option | Mechanics | Verdict |
|---|---|---|
| **A. A small explicit state set owned by account-service: `ACTIVE` / `SUSPENDED` / `CANCELLED` (+ optional `PENDING`), with guarded transitions** | `tenant_domain_subscription.status` takes a closed enum. Transitions: `(none)→ACTIVE` (subscribe); `ACTIVE↔SUSPENDED` (suspend / resume — reversible, "entitled but blocked"); `ACTIVE\|SUSPENDED→CANCELLED` (terminate — terminal). `SUSPENDED` is the **generic blocked state** any driver (admin, compliance, future billing) sets; `PAST_DUE` and other billing sub-states are NOT first-class here (deferred to the billing axis, D5). account-service owns the state + the transition guard. The DB constraint (CHECK/enum) makes illegal values un-storable; the existing all-`ACTIVE` seed is conformant → net-zero. | **CHOSEN** — minimal, queryable, audit-friendly; mirrors GCP project lifecycle (`ACTIVE` / `DISABLED` / `DELETE_REQUESTED`) and AWS account enablement; `SUSPENDED` reversibility is the keystone of the D2 plane-separation invariant; keeping billing sub-states out keeps the set portfolio-sized. |
| B. Free-form `status` string | Any string; meaning by convention | Rejected — un-queryable, no transition guard, drift between writers; the exact bare-column gap this ADR closes. |
| C. Per-domain status semantics | Each domain interprets `status` its own way | Rejected — status is a **platform-level** entitlement fact (GAP authority, ADR-019 ②); per-domain interpretation forks the meaning and breaks the central catalog/token derivation. |

### D2 — Plane-separation invariant (the crux; GCP billing↔IAM parity)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Two planes, one-way dependency; entitlement state changes affect ONLY the entitlement plane** | **Entitlement plane** = account-service `tenant_domain_subscription` — the authority for *"may this tenant use this domain"*. **IAM plane** = admin-service operators + RBAC + `operator_tenant_assignment` (ADR-020/002) — the authority for *"may this operator act for this tenant, with what role"*. Dependency is **one-way**: the IAM/catalog/token path MAY READ entitlement (it already does, filtering ACTIVE), but **an entitlement change MUST NOT mutate any IAM row, and the entitlement plane MUST NOT read IAM**. Therefore **suspending/cancelling a subscription**: (i) drops the domain from the catalog (ADR-019 D4, already ACTIVE-filtered) and from the **next-issued** `entitled_domains` claim (ADR-019 D5 / ADR-020 D3, already ACTIVE-filtered) — so the tenant can no longer reach the domain; (ii) **leaves operator assignments + RBAC bindings byte-unchanged**. Re-activation (`SUSPENDED→ACTIVE`) restores access with **no IAM re-grant**. | **CHOSEN** — exact GCP "disable billing → APIs blocked, IAM policy intact → re-enable → access returns" parity; reversibility without re-grant is the product property a SaaS needs (a non-paying customer is suspended, not dismantled); the one-way dependency keeps the central-IdP entitlement authority (②) clean and the IAM plane independently evolvable (the ① delegation axis). |
| B. Cascade — suspending a subscription also revokes the operator assignments / RBAC | Entitlement suspension tears down IAM grants | Rejected — couples the planes; the grant is lost on re-activation (must be rebuilt); not the cloud model; makes a temporary billing blip destructive to access control. |
| C. IAM gates on subscription per-request (synchronous) | Each IAM/domain decision re-reads subscription live | Rejected — synchronous cross-plane read on the hot path; the entitlement decision already rides in the short-lived token via `entitled_domains` (ADR-019 ③/D5); a per-request check makes GAP a runtime SPOF and defeats token self-containment. |

### D3 — Subscription management authority + authorization (realizes ADR-019 § 3.3 step 2)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. account-service-owned subscription admin API, gated by a NEW `subscription.manage` permission distinct from `operator.manage`** | The entitlement authority owns its own writes: a subscription admin surface (subscribe / suspend / resume / cancel — the D1 transitions) lives in **account-service**, NOT admin-service. It is gated by a **new RBAC permission `subscription.manage`**, deliberately **distinct from the IAM `operator.manage`**. The distinct permission **IS the plane separation at the authorization layer** — managing entitlement ≠ managing operators. Today both permissions sit on `SUPER_ADMIN` (platform-central), exactly as `operator.manage` does; the future tenant-admin delegation ADR (① axis) can grant `subscription.manage` **independently** of `operator.manage`. Every mutation writes an audit record + emits a domain event (D4). | **CHOSEN** — keeps entitlement writes in the entitlement plane (D2); the separate permission is the least-privilege seam that makes independent delegation possible later; reuses the existing GAP RBAC/audit scaffolding; realizes the ADR-019 § 3.3-step-2 "optional admin surface" as a decided, not implicit, surface. |
| B. Put subscription mutation in admin-service (the IAM service) | The IAM service writes entitlement | Rejected — bleeds entitlement writes into the IAM plane; violates the one-way dependency (D2); admin-service would own both planes. |
| C. Reuse `operator.manage` for subscription mutation | One permission gates both planes | Rejected — conflates the two authorizations; can't delegate entitlement-management to a tenant-admin without also handing operator-management (and vice-versa); the exact conflation D2/D3 exist to prevent. |

### D4 — Propagation of subscription changes (decoupled)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Live-read reflection + an async `tenant.subscription.changed` outbox event; existing tokens expire by short TTL (no eager revocation)** | Three mechanisms, all decoupled: **(i)** the catalog reads the table live and `entitled_domains` is derived at token issuance — both already filter ACTIVE, so a state change is reflected at the **next catalog read / next token issuance with zero extra wiring**; **(ii)** each mutation emits a `tenant.subscription.changed` domain event via the **outbox** (`libs/java-messaging`) for asynchronous consumers (console cache invalidation, future billing / notification) — fire-and-forget, never a synchronous coupling; **(iii)** **already-issued** short-lived tokens are **NOT eagerly revoked** on suspend — they lapse by their short TTL (ADR-019 ③: access 1800s), the same natural-expiry model the portfolio already trusts everywhere. | **CHOSEN** — the read paths need no change (the ACTIVE filter is already there); the outbox event is the established decoupling primitive (ADR-MONO-004); natural-expiry avoids introducing revocation infrastructure the portfolio doesn't have, while the short access-TTL bounds the worst-case post-suspend window to minutes. |
| B. Synchronous push to each domain on change | GAP pushes suspension to all 5 domains synchronously | Rejected — per-domain coupling; GAP→domain fan-out SPOF; contradicts central-authority + self-contained-token model. |
| C. Eager token revocation on every suspend | Maintain a revocation list / introspection checked per request | Rejected — revocation infra the portfolio does not have and does not need at this scale; the short access-TTL already bounds exposure; per-request introspection defeats self-containment (③). (Available as a future option if a domain ever needs sub-TTL enforcement — out of scope here.) |

### D5 — Billing relationship (explicitly OUT of scope; the seam is defined)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. NO billing/payment service in this ADR; billing is designed in as a future *driver* of the D1 state machine** | This ADR separates the planes and defines the entitlement lifecycle; subscription transitions are driven **manually/administratively** (the D3 admin API) for the portfolio. A **future billing axis (its own ADR)** becomes simply **another driver** of the D1 states — e.g. a payment failure transitions `ACTIVE→SUSPENDED` via the same `subscription.manage`-authorized path or a billing-service event consumed into a transition; `PAST_DUE` and dunning sub-states are introduced there as refinements of `SUSPENDED`. The D1 state machine + the D2 plane separation are designed so billing **plugs in without re-opening D1-D4**. | **CHOSEN** — keeps the portfolio honest (no fake payment integration) while making the entitlement plane *billing-ready*; the "subscription" naming + the `SUSPENDED` generic blocked-state are deliberately driver-agnostic so billing is additive, not a redesign. |
| B. Model billing/payment now | Introduce a payment/billing service + `PAST_DUE` dunning in this ADR | Rejected — speculative; no payment integration exists; over-scopes a portfolio demo; couples the lifecycle decision to a payment-provider choice that isn't being made. |

### D6 — Migration phasing (zero-regression; BE-303 / BE-317 discipline)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Backward-compatible staged migration, each step independently main-GREEN; ACCEPTED is step 0** | **Step 0 (doc-only):** this ADR PROPOSED → ACCEPTED (user-gated). **Step 1 (account-service):** formalize the `status` state set (D1) — CHECK/enum constraint + state-transition guard; the existing all-`ACTIVE` seed is conformant → **net-zero behavior**; unit/IT prove the catalog + `entitled_domains` reads are byte-identical. **Step 2 (account-service):** subscription admin API + the new `subscription.manage` permission (D3, `SUPER_ADMIN`-only initially) + audit record + `tenant.subscription.changed` outbox event (D4). **Step 3 (verify, the plane-separation proof):** an IT/e2e that **suspends** a subscription and asserts BOTH: (a) the domain disappears from the catalog **and** from the next-issued token's `entitled_domains`; (b) the operator's `operator_tenant_assignment` rows + RBAC bindings are **byte-unchanged**; then **re-activates** and asserts access returns with **no IAM re-grant**. | **CHOSEN** — each step independently main-GREEN; step 1 is provably net-zero (seed already ACTIVE); the risk (mutation surface) is isolated to step 2 behind the new permission; step 3 is the executable assertion of the D2 invariant — the BE-303 "0 failing required checks at merge" + BE-317 staged discipline. |
| B. Big-bang (state set + admin API + event + verify in one PR) | Single atomic flip | Rejected — couples the net-zero constraint to the mutation surface + event; harder to bisect; violates the staged discipline. |

---

## 3. Consequences

### 3.1 Hard invariants this ADR carries

- **One-way plane dependency** — the IAM/catalog/token path reads entitlement; an entitlement change never mutates IAM, and the entitlement plane never reads IAM (D2). The two authorities (account-service entitlement, admin-service IAM) compose without overlap.
- **Suspension is reversible without IAM re-grant** — `SUSPENDED→ACTIVE` restores access; operator assignments + RBAC survive a suspend/cancel (D2). GCP billing↔IAM parity.
- **GAP is the single entitlement authority (ADR-019 ②)** — the subscription table + its lifecycle + its mutation surface all live in account-service; no domain replicates or re-derives entitlement.
- **Least-privilege via a distinct permission (⑤)** — `subscription.manage` ≠ `operator.manage`; entitlement-management and operator-management are independently grantable (the seam the future ① delegation ADR consumes).
- **Short-lived-token self-containment preserved (ADR-019 ③)** — no revocation infrastructure introduced; suspension reflected at next issuance, bounded by the access-token TTL (D4).
- **multi-tenant M1-M7 untouched** — this ADR governs the entitlement *lifecycle*, not row-level isolation; `tenant_id` stays the single isolation key, the 3-layer gate is unchanged.
- **Read-path shapes byte-stable** — `ConsoleRegistryUseCase` + `TenantClaimTokenCustomizer` already filter ACTIVE; step 1 is net-zero, no consumer change.

### 3.2 What this ADR does NOT do (deferred to ACCEPTED + post-ACCEPTED execution)

- No implementation: no CHECK/enum migration, no admin API, no `subscription.manage` permission seed, no outbox event, no IT — all post-ACCEPTED execution tasks (§ 3.3).
- No billing/payment service, no `PAST_DUE`/dunning model (D5 — a future billing ADR).
- No tenant-admin delegation — granting `subscription.manage` to a tenant-scoped operator is the separate ① IAM-delegation ADR (this ADR only makes the permission *separately grantable*).
- No eager token revocation (D4-C — future option if a domain needs sub-TTL enforcement).
- No change to ADR-019 D1-D6 bodies — the only ADR-019 change is an additive § History "Additive note" blockquote (HARDSTOP-04).
- No change to ADR-020 (operator-assignment IAM plane) or ADR-021 (account_type) — both orthogonal.

### 3.3 Future-self (post-ACCEPTED execution roadmap — sketch, finalised at ACCEPTED)

0. **`TASK-MONO-2xx`** (sibling of MONO-153/157/165) — ADR-MONO-023 PROPOSED → ACCEPTED transition (doc-only, user-explicit-intent gated); `ADR-MONO-003a § 3` audit-row append.
1. **`TASK-IAM-BE-xxx`** (iam-platform account-service, post-ACCEPTED) — `status` state set (D1): CHECK/enum constraint + transition guard; net-zero proof (catalog + `entitled_domains` byte-identical). Model = **Sonnet/Opus** (constraint + guard; low blast radius).
2. **`TASK-IAM-BE-xxx`** (iam-platform account-service, post-ACCEPTED) — subscription admin API + `subscription.manage` permission (D3, SUPER_ADMIN-only) + audit + `tenant.subscription.changed` outbox event (D4). Model = **Opus** (new authorization seam + event contract).
3. **`TASK-MONO-2xx`** (federation-e2e + iam IT, post-step-2) — the plane-separation proof (D6 step 3): suspend → domain drops from catalog + next token's `entitled_domains`; operator assignment + RBAC byte-unchanged; re-activate → access returns, no re-grant. Model = **Opus** (the D2-invariant executable assertion).

No step beyond 3 is scoped here; the billing axis (D5) and the tenant-admin delegation axis (①) are each a new ADR.

---

## 4. Alternatives Considered

The D1-D6 tables enumerate per-axis alternatives. The cross-cutting alternatives:

- **Leave the subscription a bare ACTIVE-only table (do nothing).** Rejected as the *default but not the decision* — fine until the first suspend/cancel, at which point the meaning is baked implicitly (HARDSTOP-09). Recording the lifecycle + plane separation costs nothing until a step is executed; execution stays opt-in.
- **Fold entitlement into IAM (one plane).** Rejected — the AWS/GCP lesson is precisely that billing/entitlement and IAM are *separable* layers; merging them makes a billing suspension destructive to access control and forecloses independent delegation.
- **Model billing now to drive the states.** Rejected (D5-B) — speculative; no payment integration; the state machine is designed so billing plugs in later as a driver.
- **Cascade suspension into assignment revocation.** Rejected (D2-B) — loses the grant on re-activation; not the cloud model.

---

## 5. Relationship to ADR-MONO-019 / 020 / 021 + the future ① delegation axis

| | ADR-019 (parent) | ADR-020 (operator IAM plane) | ADR-021 (account_type) | future ① (tenant-admin delegation) | **ADR-023 (this)** |
|---|---|---|---|---|---|
| Axis | Customer-tenant model; created `tenant_domain_subscription` (D2) | Operator↔tenant N:M assignment + permission-set | CONSUMER/OPERATOR person classification | Tenant-scoped `operator.manage`-class delegation | **Entitlement-plane lifecycle + plane separation** |
| Relationship | **Amends** § D2 additively (formalizes its `status` + realizes its § 3.3-step-2 admin surface) | **Protects** — D2 invariant guarantees assignments survive an entitlement change | **Orthogonal** — person axis, neither plane | **Composes** — consumes `subscription.manage` as a separately-grantable permission | — |

This ADR amends ADR-MONO-019 § History additively (records that D2's `status` + deferred admin surface production form is decided here; D1-D6 byte-unchanged) and is a prerequisite for the post-ACCEPTED 3-step execution roadmap. ADR-020 / 021 invariants are inherited unchanged.

---

## 6. Status Transition History

Append-only.

| Date | Transition | Decision direction | User intent quote | PR(s) |
|---|---|---|---|---|
| 2026-06-10 | created PROPOSED | D1 = explicit lifecycle state set (`ACTIVE`/`SUSPENDED`/`CANCELLED`(+`PENDING`)) owned by account-service, `SUSPENDED` reversible generic blocked-state, billing sub-states excluded; D2 = two planes one-way dependency (IAM reads entitlement; entitlement change never mutates IAM; suspend/cancel affects entitlement plane only; re-activate without re-grant — GCP billing↔IAM parity); D3 = account-service-owned subscription admin API gated by a NEW `subscription.manage` permission distinct from `operator.manage` (the plane separation at the authorization layer; separately delegable); D4 = live-read reflection + `tenant.subscription.changed` outbox event + short-TTL natural expiry (no eager revocation); D5 = billing OUT of scope, designed in as a future *driver* of the D1 states; D6 = staged net-zero migration (step 0 ACCEPTED → state set → admin API + permission + event → plane-separation proof IT) | "추천대로 진행" (TASK-MONO-205 — after the AWS/GCP IAM-comparison discussion, the user chose to proceed with the recommended first item ③ "구독↔IAM 평면 분리" as a committed ADR per the staged ADR-019/020/021 pattern) | #1237 (TASK-MONO-205) |
| 2026-06-10 | PROPOSED → ACCEPTED | D1-D6 CHOSEN-PROPOSED direction **finalised byte-unchanged** from PROPOSED #1237 squash `c4a30422` (ACCEPTED *finalises*, does not re-decide; § 1 Context + § 2 Decision tables + § 3 Consequences + § 4 Alternatives + § 5 Relationship + § 7 Provenance byte-identical; flip = Status + History ACCEPTED clause + this row + § 1.3 minimal past-tense). Authorizes the § 3.3 3-step execution roadmap (dependency-correct base = this ACCEPTED main): subscription `status` state set (D1) → admin API + `subscription.manage` permission + `tenant.subscription.changed` event (D3/D4) → plane-separation proof IT (D6 step 3). | "권장 순서대로 진행" (TASK-MONO-206 — user-explicit intent after the PROPOSED #1237 merge + the offered A→B recommended order; sibling ADR-021→MONO-165 same-session PROPOSED→ACCEPTED) | #<this> (TASK-MONO-206) |

---

## 7. Provenance

- HARDSTOP-09 (`platform/hardstop-rules.md`) — mandate for an ADR + PAUSE-until-ACCEPTED on an undocumented architecture decision (subscription lifecycle + plane separation + mutation authority).
- HARDSTOP-04 (`platform/hardstop-rules.md`) — the ADR-MONO-019 amendment is an additive § History "Additive note" blockquote only; D1-D6 byte-unchanged.
- ADR-MONO-019 § D2 (created `tenant_domain_subscription`; the `status` column + § 3.3-step-2 "optional admin surface" this ADR formalizes) + § D4 (catalog ACTIVE filter) + § D5 (entitlement-trust gate).
- ADR-MONO-020 D3 (`entitled_domains` = selected tenant's ACTIVE subscriptions) + the `operator_tenant_assignment` IAM plane D2 protects.
- ADR-MONO-021 (account_type — orthogonal person axis).
- ADR-MONO-004 (`libs/java-messaging` outbox — the decoupling primitive D4 reuses).
- ADR-001 (GAP) — central OIDC IdP / single entitlement authority. ADR-002 (GAP) — RBAC permission model the `subscription.manage` permission extends.
- `rules/traits/multi-tenant.md` M1-M7 — untouched (entitlement lifecycle, not row isolation).
- Code evidence: `account-service/.../TenantDomainSubscription*.java` (entity + query, ACTIVE-only seed), `admin-service/.../console/ConsoleRegistryUseCase.java` (catalog ∩ ACTIVE), `auth-service/.../oauth2/TenantClaimTokenCustomizer.java` (`entitled_domains` ACTIVE filter) — the read paths step 1 keeps byte-identical.

분석=Opus 4.8 / 구현=Opus 4.8 (entitlement-plane lifecycle + plane-separation architecture; D1-D6 PROPOSED-direction reasoning under HARDSTOP-04/09 discipline; GCP billing↔IAM / AWS account-enablement parity; staged net-zero migration mirroring BE-303/BE-317 discipline; staged-child ADR pattern per ADR-019/020/021).
