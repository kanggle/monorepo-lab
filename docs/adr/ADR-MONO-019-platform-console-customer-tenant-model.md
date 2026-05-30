# ADR-MONO-019 — platform-console Real Customer-Tenant Model (AWS IAM Identity Center-style tenant ↔ domain subscription, decoupling tenant from product/domain)

**Status:** ACCEPTED
**Date:** 2026-05-30
**History:** PROPOSED 2026-05-30 (TASK-MONO-152 — authors the decision record for decoupling the *customer/tenant* axis from the *product/domain* axis in `platform-console`'s data-driven catalog, resolving the six architecture decisions (D1-D6) that ADR-MONO-013 § D5 (data-driven catalog) left as a demo simplification. Decision direction **CHOSEN-PROPOSED** per the reasoning recorded below; ACCEPTED transition is a separate user-explicit-intent-gated task per D6/D8, mirroring the ADR-MONO-014/015/017/018 staged-child pattern. **No implementation in this task — decision record + impact scope + migration roadmap only.**) · ACCEPTED 2026-05-31 (TASK-MONO-153 — user-explicit intent "ADR-MONO-019 PROPOSED → ACCEPTED 승급 ... 작성·머지" + "진행해" after PROPOSED #953 squash `b4ec7edc` main merge; D1-D6 CHOSEN-PROPOSED direction **finalised byte-unchanged** from PROPOSED — ACCEPTED *finalises*, does not re-decide; this authorizes the § 3.3 4-step execution roadmap as a dependency-correct base, beginning with step 1 (GAP backward-compatible model+catalog). Sibling staged-child ACCEPTED pattern: ADR-014→MONO-110 / ADR-015→MONO-112 / ADR-017→MONO-126 / ADR-018→MONO-138.)
**Decision driver:** `platform-console`'s tenant switcher currently lists **domain names** (`gap` / `wms` / `scm` / `erp` / `finance`), not customer names. This is because the catalog conflates two axes that AWS/GCP keep distinct: the **product/domain** axis (which service) and the **customer/tenant** axis (which paying organization). Concretely — `ProductCatalog` binds each domain product to a `tenantSlug` that is *literally the domain name* (`new Entry("wms", …, "wms", "/wms")`), and the demo DB seeds tenant rows whose ids are `wms`/`scm`/`erp`/`finance`; so a domain product's `tenants[]` resolves to `[<domain-name>]` and the switcher shows domain names. The per-domain isolation gate (`TenantClaimValidator`) reinforces this by enforcing `tenant_id ∈ {<domain-slug>, *}` — a single fixed expected value per domain. This is an intentional, recorded demo simplification (ADR-MONO-013 § 1.2 / D5 — "data-driven catalog, registry-config-only product additions"), but it means the portfolio does NOT yet model the real SaaS shape: a **customer** subscribes to **N products** (N:M), an **operator** is assigned to **M customers** with a permission set, and each **service** trusts an IdP-issued, tenant-scoped credential and isolates by `tenant_id` row. Introducing the customer-tenant entity + tenant↔domain subscription mapping + operator↔tenant assignment, and evolving each domain's isolation gate from "tenant == my domain slug" to "entitlement-trust", is a **cross-cutting architecture decision** spanning the GAP registry (account-service + admin-service), the console catalog (`ConsoleRegistryUseCase` + `console-integration-contract.md` § 2.2), and the per-domain isolation gate in **all five** federated domains. Authoring any of those execution steps without resolving the six axes below silently bakes the tenant model (HARDSTOP-09 #2) and silently supersedes the ADR-MONO-013 demo-simplification assumption (HARDSTOP-04 — must be recorded, not implied). This ADR is that decision record.
**Supersedes:** none. **Amends:** [ADR-MONO-013](ADR-MONO-013-platform-console-foundation.md) § D5 / § 1.2 (additive § History "Additive note" blockquote recording that the "tenant == domain slug" data-driven-catalog shape is a **demo simplification** whose production form is decided here; D1-D8 bodies byte-unchanged — HARDSTOP-04 discipline preserved; blockquote count 7 → 8). **Reconciles:** none yet (PROPOSED scopes the architecture; `console-integration-contract.md` § 2.2 / § 2.4.x + per-domain `TenantClaimValidator` specs are byte-unchanged at PROPOSED — D4 + D5 explicitly preserve their *shape*; reconciliation lands at the post-ACCEPTED execution tasks, never inside this ADR).
**Related:** [ADR-MONO-013](ADR-MONO-013-platform-console-foundation.md) (console foundation, Model B, data-driven catalog — the demo simplification this ADR's production model supersedes; parent), [ADR-MONO-017](ADR-MONO-017-platform-console-bff-architecture.md) (D6 `tenant_id` pass-through — the BFF stays a pass-through under the new model, never a tenant rewriter), [ADR-MONO-018](ADR-MONO-018-platform-console-phase-8-federation-hardening.md) (§ 3.3 "if post-Phase-8 hardening surfaces a new architectural axis … it will be a new ADR" — this ADR realizes that anticipation; its D5 multi-tenant isolation regression cohort is the verification base the new gate (D5 here) extends), [ADR-001 (GAP)](../../projects/global-account-platform/docs/adr/ADR-001-oidc-adoption.md) (GAP as the central OIDC IdP — the entitlement authority this model centralizes on), [ADR-002 (GAP)](../../projects/global-account-platform/docs/adr/ADR-002-admin-rbac.md) (`admin_operators.tenant_id` + `'*'` platform sentinel — the operator-tenant scope this ADR's D3 extends), `rules/traits/multi-tenant.md` (M1-M7 — the isolation invariants this model preserves while widening the allowed-set), `projects/global-account-platform/apps/admin-service/.../console/ProductCatalog.java` + `ConsoleRegistryUseCase.java` (the catalog code whose tenant-binding this model rewrites), `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.2 (the registry envelope whose *shape* is preserved and whose *values* change from domain-slugs to customer-ids).

> **Separate axis from ADR-005 (GAP) / TASK-BE-317:** [ADR-005 (GAP, PROPOSED)](../../projects/global-account-platform/docs/adr/ADR-005-service-to-service-workload-identity.md) addresses **service-to-service** workload identity (identity pattern ① keyless + ④ workload identity — `client_credentials` short JWT replacing the static `X-Internal-Token`). **This ADR addresses a different identity axis: the *tenant model* (who the customer is, what they subscribe to, who may operate for them).** The two are orthogonal — BE-317 hardens machine-to-machine auth; ADR-MONO-019 makes the customer/operator/subscription model real. They share no files and may land in either order.

---

## 1. Context

### 1.1 The conflation today (concrete evidence)

The console's data-driven catalog conflates the *product* axis with the *tenant* axis at three layers:

1. **Catalog binding** — `ProductCatalog` (`admin-service/.../console/ProductCatalog.java`) binds each domain product to a `tenantSlug` that **equals the domain name**:
   ```java
   new Entry("gap",     …, true,  true,  null,      "/gap")      // bindsAllTenants
   new Entry("wms",     …, true,  false, "wms",     "/wms")      // tenantSlug == "wms"
   new Entry("scm",     …, true,  false, "scm",     "/scm")
   new Entry("erp",     …, true,  false, "erp",     "/erp")
   new Entry("finance", …, true,  false, "finance", "/finance")
   ```
2. **Catalog resolution** — `ConsoleRegistryUseCase.selectableTenants()` resolves a domain product's `tenants[]` as `activeTenants.contains(entry.tenantSlug()) ? [entry.tenantSlug()] : []`. Because the demo DB **seeds tenant rows named `wms`/`scm`/`erp`/`finance`**, the switcher shows **domain names**, not customer names.
3. **Per-domain isolation gate** — each domain's `TenantClaimValidator` enforces `tenant_id ∈ {<domain-slug>, *}` (a single fixed expected value, e.g. finance accepts `tenant_id ∈ {finance, *}`). The gate is *correct* (cross-tenant denial works, regression-tested per multi-tenant M6) but its allowed-set is **one fixed slug**, so a domain is effectively single-tenant.

This is recorded as a demo simplification in ADR-MONO-013 (§ 1.2 / D5: registry-driven catalog, product additions are config-only). It is NOT wrong for a portfolio demo — but it is NOT the production SaaS shape, and the gap is now an explicit, surfaced finding (the 5-identity-pattern assessment + the user's "왜 테넌트 선택창에 도메인 이름이 나오나" investigation).

### 1.2 The production shape (AWS IAM Identity Center / GCP parity)

| Concept | AWS IAM Identity Center | GCP | platform-console (target) |
|---|---|---|---|
| Customer boundary | **Account** | **Project** | **Customer-tenant** (`tenant_id`) |
| Product entitlement | Account → enabled services | Project → enabled APIs | **tenant ↔ domain subscription** (N:M) |
| Operator → boundary grant | **Permission set** assignment (user → account) | IAM binding (member → project role) | **operator ↔ tenant assignment** (+ RBAC permission set) |
| Service trust | STS issues account-scoped short-lived creds; service trusts the cred's account | SA / Workload Identity; service trusts the token's project | Domain trusts the GAP-issued, `tenant_id`-scoped token; isolates by `tenant_id` row |

The four model elements the portfolio is missing: **(1)** a customer-tenant entity distinct from the product/domain; **(2)** an N:M tenant↔domain subscription mapping; **(3)** an explicit operator↔tenant assignment (least-privilege — not implied by subscription); **(4)** a per-domain isolation gate that trusts the IdP's entitlement decision rather than hardcoding one slug. Elements already in place: central IdP + federation (ADR-001 — ②), short-lived creds (access 1800s + refresh 604800s + RT rotation — ③), deny-by-default RBAC (`@RequiresPermission` aspect — ⑤), single-value operator tenant scope + `'*'` platform sentinel (ADR-002), and a working producer-side isolation gate (`TenantClaimValidator` — M2/M3/M6). The redesign **widens** the existing pieces; it does not replace the IdP, the token model, or the RBAC.

### 1.3 Why an ADR (HARDSTOP-09) + staged PROPOSED → ACCEPTED

Per [`platform/hardstop-rules.md`](../../platform/hardstop-rules.md) HARDSTOP-09 #2: starting any execution step without resolving D1-D6 bakes architecture silently — e.g. seeding real customer tenants while the domain gate still hardcodes `tenant_id == <slug>` would make customers catalog-visible but silently un-callable (a broken intermediate state); rewriting the catalog to a new envelope shape would break the ADR-013 D5 zero-retrofit invariant; caching a per-domain subscriber list in each domain would fork GAP's entitlement authority (drift). And because the model **supersedes an ADR-MONO-013 recorded assumption** (tenant == domain slug), HARDSTOP-04 requires the supersession be recorded in an ADR, not applied implicitly. This is the exact prevention role ADR-MONO-014/015/017/018 played for their phases.

**Staged pattern (sibling: ADR-014/015/017/018)**: the PROPOSED stage records the **decision direction** (D1-D6) + the **hard invariants the chosen direction must inherit** (multi-tenant M1-M7; the registry envelope *shape*; GAP as the single entitlement authority; the BFF as a `tenant_id` pass-through, never a rewriter; the producer-side isolation authority) + the **4-step zero-regression migration roadmap**. The ACCEPTED transition is a separate post-PROPOSED, user-explicit-intent-gated task (D6 step 0 / D8); the four execution steps remain **PAUSED** until ACCEPTED. **(ACCEPTED transition WAS executed as TASK-MONO-153, 2026-05-31 — D1-D6 finalised byte-unchanged from PROPOSED; the four execution steps are now UNPAUSED and proceed dependency-correct from this ACCEPTED main, beginning with § 3.3 step 1.)**

---

## 2. Decision

Six decision axes. Each table's first row is **CHOSEN (PROPOSED direction)**.

### D1 — Customer-tenant entity (new aggregate vs reuse the existing tenant registry)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Reuse the existing account-service `tenants` registry as the customer-tenant entity** | The account-service `tenants` table (already the tenant authority via `ListTenantsUseCase`, already the source of `admin_operators.tenant_id` scope) **is** the customer-tenant entity. The change is **semantic + seed**, not a new table: stop seeding tenant rows whose ids are domain names (`wms`/`scm`/…); seed real customer tenants (`acme-corp`, `globex-trading`, `initech`, …). `tenant_id` stays the single isolation key end-to-end (M1). | **CHOSEN** — the portfolio already treats `tenant_id` as *the* customer boundary key across M1-M7 (DB rows, cache keys, Kafka envelopes, metrics dims); the existing registry already is the customer authority. The conflation is in the *seed values* + the *catalog binding*, not in the entity model. Reusing it keeps one isolation key and zero schema fork. |
| B. New `customer` aggregate separate from `tenant` | Introduce a `customers` table; `tenant` becomes a sub-concept (e.g. a customer's environment) | Rejected — forks the isolation key (`customer_id` vs `tenant_id`); every M1-M7 enforcement point (5 domains × persistence/cache/event/metric) would need to learn a second key; massive retrofit for a distinction the portfolio does not need (one customer == one tenant boundary is sufficient). |
| C. Per-domain customer tables | Each domain owns its customer list | Rejected — customer identity is a **platform-level IdP concern** (GAP is the authority); per-domain customer tables fragment the entitlement source and make a central catalog impossible to assemble. |

### D2 — tenant ↔ domain subscription mapping (the missing N:M axis)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. New `tenant_domain_subscription` table in GAP account-service** | A new account-service-owned table `(tenant_id, domain_key, status, subscribed_at, …)`, N:M. admin-service `ConsoleRegistryUseCase` reads it to resolve a domain product's `tenants[]` = **the customers subscribed to that domain** (intersected with operator scope + ACTIVE). GAP is the single entitlement authority — the AWS "enabled services per account" / GCP "enabled APIs per project" analog. The catalog's `ProductCatalog.tenantSlug == domain` binding (§ 1.1) is **replaced** by a subscription lookup. | **CHOSEN** — entitlement must be **queryable** (admin surface: who subscribes to what; audit: subscription changes) and **server-side catalog-drivable**; a table in the existing tenant authority is the minimal, centralized form. Reuses the GAP admin/audit/RBAC scaffolding. |
| B. Subscription encoded only in JWT scope (no table) | The operator's token carries the subscribed-domain set as a scope claim; no persistent mapping | Rejected — no admin/audit surface; can't drive the catalog for a tenant the current operator isn't scoped to; entitlement is a durable business fact, not only a token-embedded ephemeral. (The token MAY *carry* the entitlement for D5 — but the source of truth is the table.) |
| C. Per-domain subscriber config | Each domain lists its own subscribers | Rejected — fragments the entitlement source; a cross-tenant catalog can't be centrally assembled; guaranteed drift between the catalog's view and each domain's view. |

### D3 — operator ↔ tenant assignment (AWS permission-set analog; least-privilege)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Keep `admin_operators.tenant_id` single-value (+ `'*'` sentinel) for the MVP; the value becomes a real customer id; multi-assignment is a documented later step** | The operator's tenant scope stays the existing single `tenant_id` (or `'*'` platform-scope) — the only change is that the value is now a real customer id, not a domain slug. The full AWS-parity shape (one operator assigned to **several** customers via an `operator_tenant_assignment` join table) is recorded as **migration step 4 / future**, NOT in the MVP, to keep step 1 backward-compatible (today every operator already has exactly one `tenant_id` or `*`). | **CHOSEN** — least-disruptive first step; preserves the ADR-002 scope model; the multi-assignment extension is real AWS parity but over-scopes the initial cut and is cleanly additive later. |
| B. Full N:M `operator_tenant_assignment` join table now | Operator → many customers, each with a permission set, immediately | **Deferred, not rejected** — this is the eventual AWS Identity Center "user → multiple account assignments" shape; folded into the roadmap (§ 3.3 step 4 extension) so the MVP stays small and zero-regression. |
| C. Derive operator access from subscription (operator sees every subscribed customer) | No explicit operator↔tenant grant; if a customer subscribes to a domain, any operator of that domain sees it | Rejected — **violates least-privilege (⑤)**: "customer subscribes to domain" (D2) and "this operator may act for that customer" are different facts; conflating them means a domain operator automatically gains cross-customer reach. The operator's tenant access MUST be an explicit grant. |

### D4 — Catalog resolution rewrite (console-facing; envelope-shape-preserving)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. `selectableTenants(product)` = subscribers(product.domain) ∩ operator scope ∩ ACTIVE; envelope shape byte-stable** | `ConsoleRegistryUseCase.selectableTenants()` is rewritten: a domain product's `tenants[]` = the customers (in the operator's assigned scope, ACTIVE-registered) **subscribed to that domain** (D2 table). The `gap` product still binds to ALL in-scope tenants (unchanged shape — `gap` federates everything). The switcher now shows **customer names**. **`console-integration-contract.md` § 2.2 `tenants: string[]` is byte-stable on shape** — only the *values* change from domain-slugs to customer-ids; **zero `console-web` code change** (ADR-013 D5 data-driven invariant — sixth confirmation). | **CHOSEN** — the registry envelope already carries `tenants: string[]` of tenant ids; the values changing is not a contract break; preserves the zero-retrofit invariant; the customer-name switcher falls out of the seed + the subscription lookup with no new envelope. |
| B. New catalog envelope with explicit customer objects (`{ id, displayName, subscribedDomains[] }`) | Replace `tenants: string[]` with a richer customer object array | Rejected — breaks the ADR-013 D5 zero-retrofit data-driven invariant; forces a `console-web` parser change for a display-name nicety that can be delivered later additively (`@JsonInclude.NON_NULL` extension, the same way `operatorContext` was added in TASK-BE-304) without a breaking envelope swap. |

### D5 — Per-domain isolation gate evolution (the deepest, highest-risk change)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Entitlement-trust: domain accepts any GAP-issued (JWKS-verified) `tenant_id` claim and isolates by row; GAP only issues a domain-scoped token for an entitled (subscribed + operator-assigned) tenant** | Each domain's `TenantClaimValidator` evolves from `tenant_id ∈ {<domain-slug>, *}` (one fixed value) to **entitlement-trust**: accept any well-formed `tenant_id` in a GAP RS256/JWKS-verified token, and enforce row-level `WHERE tenant_id = <claim>` (M1/M2 layer 3) + 404-over-403 (M3) + regression (M6). **The entitlement decision moves to GAP at token issuance** (GAP issues a domain-scoped token only when the subscription (D2) + the operator assignment (D3) both exist). This is exactly the AWS model: STS issues an account-scoped credential only if the assignment exists; the service trusts the credential's account and isolates by it. The allowed-set widens from **{one slug}** to **{GAP-entitled tenants}**; **no isolation layer is dropped** — M2 3-layer, M3, M4, M5, M6 all preserved. | **CHOSEN** — centralizes entitlement on the IdP (②), keeps tokens self-contained (③ — no per-request callback), and keeps each domain's enforcement (row-level isolation + regression) intact. The domain stops being single-tenant-per-domain and becomes multi-customer with row isolation — the actual SaaS shape. **Highest-risk step → lands LAST behind a dual-accept window (step 3).** |
| B. Each domain syncs/caches a subscriber list and validates `tenant_id ∈ subscribers` | Each domain replicates the D2 subscription set and checks membership locally | Rejected — duplicates GAP's entitlement authority in every domain (drift, sync lag); a stale cache = a customer wrongly locked out or wrongly admitted; contradicts central-IdP (②). |
| C. Domain calls GAP per request to verify entitlement | Each domain read does a synchronous GAP entitlement check | Rejected — per-request cross-service call on the read hot path; defeats short-lived-token self-containment (③); makes GAP a runtime SPOF for every domain read. |

### D6 — Migration phasing + roadmap (zero-regression; BE-303 / BE-317 discipline)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. 4-step backward-compatible migration, each step independently mergeable + main-GREEN; ACCEPTED transition is step 0** | **Step 0 (doc-only):** ADR PROPOSED → ACCEPTED (user-explicit-intent gated, D8). **Step 1 (GAP, backward-compatible):** add `tenant_domain_subscription` (D2); **seed it so each existing slug-tenant subscribes to its own domain** (reproduces today's catalog exactly); rewrite `ConsoleRegistryUseCase` to resolve via subscription instead of `tenantSlug==domain` (D4). Net behavior **unchanged** (seed makes subscription ≡ old binding) → zero-regression, mergeable alone. **Step 2 (GAP):** seed real customer tenants (`acme-corp`/…) + their N:M subscriptions + operator assignments (D1/D3); catalog now shows customer names. **Gated behind step 3** (or shipped as a documented catalog-visible-but-not-yet-callable intermediate). **Step 3 (per-domain, Opus — isolation):** each domain `TenantClaimValidator` dual-accepts BOTH the legacy fixed slug AND any GAP-entitled customer id (D5); per-domain cross-tenant-leak regression IT (M6) proves no leak; 1 console-bff `tenant_id` pass-through IT (ADR-017 D6). **Step 4 (cleanup):** remove the legacy slug-tenants + the legacy fixed-slug branch in each validator; update `console-integration-contract.md` § 2.2/§ 2.4.x tenant-model notes + per-domain gateway specs; (optional) `operator_tenant_assignment` N:M extension (D3-B). | **CHOSEN** — each step is independently main-GREEN; the riskiest change (5 domain gates) lands last behind a dual-accept window — the exact zero-regression discipline of TASK-BE-317 (service-auth dual-accept) and the BE-303 CI-RED-at-merge lesson (never flip the whole portfolio in one PR). |
| B. Big-bang (one PR flips catalog + all 5 domain gates + seed) | Single atomic cross-project PR | Rejected — transiently breaks main across 5 domains during review; impossible to bisect a regression; violates the BE-303 "0 failing required checks at merge" discipline and the zero-regression dual-accept precedent. |

---

## 3. Consequences

### 3.1 Hard invariants this ADR carries

- **multi-tenant M1-M7 preserved** — `tenant_id` stays the single isolation key (M1); the 3-layer gate (M2) keeps all three layers (the change widens the *allowed-set* at layer 1/2, never drops a layer; row-level isolation at layer 3 is unchanged); 404-over-403 (M3), enumeration defense (M4), async propagation (M5), and the cross-tenant-leak regression cohort (M6) are all preserved and **re-verified** by step 3's per-domain IT. The new gate is strictly a *widening* under GAP's entitlement authority, not a relaxation of isolation.
- **Registry envelope shape byte-stable (D4)** — `console-integration-contract.md` § 2.2 `tenants: string[]` is unchanged in shape; only the values move from domain-slugs to customer-ids. Zero `console-web` code change — the ADR-MONO-013 § D5 data-driven catalog "registry-config-only" invariant's **sixth confirmation**.
- **GAP is the single entitlement authority (②)** — the subscription table (D2) + the operator assignment (D3) + token issuance (D5) all live in GAP; no domain replicates or re-derives entitlement (D5-B/C rejected). The central-IdP pattern is reinforced, not weakened.
- **BFF stays a `tenant_id` pass-through (ADR-017 D6)** — `console-bff` never becomes a tenant rewriter or a central tenant gate under the new model; producer-side authority is preserved (ADR-018 D5 invariant inherited).
- **Least-privilege preserved (⑤)** — operator↔tenant access is an explicit grant (D3-A/B), never implied by a customer's subscription (D3-C rejected).
- **Producer-side isolation authority (ADR-018 D5)** — the domain remains the enforcement point; GAP decides entitlement *at issuance*, the domain enforces isolation *at every read* (row-level). Two authorities, no overlap.

### 3.2 What this ADR does NOT do (deferred to ACCEPTED + post-ACCEPTED execution)

- It does **NOT** add the `tenant_domain_subscription` table, any migration, or any seed change. (Step 1.)
- It does **NOT** seed real customer tenants or operator assignments. (Step 2.)
- It does **NOT** change any domain `TenantClaimValidator` or add any isolation IT. (Step 3.)
- It does **NOT** rewrite `ConsoleRegistryUseCase` / `ProductCatalog` or change `console-integration-contract.md`. (Steps 1/4.)
- It does **NOT** add the `operator_tenant_assignment` N:M table. (Step 4 / D3-B extension.)
- It does **NOT** execute the ACCEPTED transition — that is a separate user-explicit-intent-gated doc-only task (D8; sibling ADR-014 → MONO-110, ADR-015 → MONO-112, ADR-017 → MONO-126, ADR-018 → MONO-138).
- It does **NOT** modify ADR-MONO-013 D1-D8 bodies — the only ADR-013 change is an additive § History "Additive note" blockquote (HARDSTOP-04; count 7 → 8).
- It does **NOT** touch ADR-005 (GAP) / TASK-BE-317 — service-to-service workload identity is an orthogonal axis.

### 3.3 Future-self (post-ACCEPTED execution roadmap — sketch, finalised at ACCEPTED)

0. **`TASK-MONO-1xx`** (sibling of MONO-110/112/126/138) — ADR-MONO-019 PROPOSED → ACCEPTED transition (doc-only, user-explicit-intent gated per D8); `ADR-MONO-003a § 3` audit-row append.
1. **`TASK-BE-3xx`** (GAP project-internal `tasks/`, post-ACCEPTED) — `tenant_domain_subscription` table + Flyway migration + `ConsoleRegistryUseCase` rewrite (subscription-driven `selectableTenants`) + **backward-compatible seed** (each existing slug-tenant subscribes to its own domain → catalog behavior byte-identical) + unit/IT proving net-zero catalog change. Model = **Opus** (registry + catalog resolution + isolation-adjacent).
2. **`TASK-BE-3xx`** (GAP, post-ACCEPTED, gated behind step 3) — seed real customer tenants + N:M subscriptions + operator assignments; catalog shows customer names; admin surface for subscription management (optional). Model = **Sonnet** (seed + read surface) / **Opus** if it includes the admin mutation surface.
3. **`TASK-XXX-BE-NNN` × 5 + `TASK-PC-BE-00x` × 1** (per-domain project-internal `tasks/` × 5 + platform-console-internal × 1, post-ACCEPTED) — domain `TenantClaimValidator` dual-accept (legacy slug ∪ GAP-entitled customer id) + per-domain cross-tenant-leak regression IT (M6) + 1 console-bff `tenant_id` pass-through IT. Model = **Opus** (per ADR-MONO-013 § D6 "isolation → Opus" + ADR-018 D5 precedent). **Highest-risk step; dual-accept window.**
4. **`TASK-XXX-BE-NNN` × 5 + `TASK-MONO-1xx`** (cleanup, post-step-3) — remove legacy slug-tenants + the legacy fixed-slug validator branch in each domain; update `console-integration-contract.md` § 2.2/§ 2.4.x tenant-model notes + per-domain gateway specs; (optional) `operator_tenant_assignment` N:M extension (D3-B → full AWS Identity Center parity). Model = **Opus** (isolation cleanup) / **Sonnet** (doc).

No step beyond 4 is scoped here; if multi-region / multi-environment-per-customer surfaces, it is a new ADR (not an extension of this one).

---

## 4. Alternatives Considered

The D1-D6 tables each enumerate per-axis alternatives. The cross-cutting alternatives:

- **Leave the demo simplification as-is (do nothing).** Rejected as the *default but not the decision* — the simplification is legitimate for a demo, but the gap is now a surfaced, documented finding (5-identity-pattern assessment); recording the production model + a zero-regression migration path is the portfolio-grade response, and it costs nothing until a step is executed. This ADR is the record; execution stays opt-in.
- **Make multi-tenancy a separate project.** Rejected — the customer-tenant model is a *property of the existing GAP IdP + console catalog + domain gates*, not a new domain/product; it has no service of its own. It belongs in the GAP registry + the console foundation lineage (this ADR).
- **Model the customer in the console (BFF/web) instead of GAP.** Rejected — entitlement + identity are IdP concerns (②); the console is a public OIDC client + a read consumer, not an identity authority. The customer-tenant entity + subscription + assignment live in GAP; the console only *renders* the resolved catalog.
- **Skip the dual-accept window (flip each domain gate directly).** Rejected — a direct flip of `tenant_id == slug` → entitlement-trust without a dual-accept window risks locking out the legacy slug-tenants mid-migration (the seed + the catalog still reference them until step 4); the dual-accept window (BE-317 precedent) keeps main GREEN across the cutover.

---

## 5. Relationship to ADR-MONO-013 / 017 / 018 + ADR-005 (GAP)

| | ADR-MONO-013 | ADR-MONO-017 | ADR-MONO-018 | ADR-005 (GAP) | **ADR-MONO-019 (this)** |
|---|---|---|---|---|---|
| Axis | Console foundation (Model B, data-driven catalog) | Phase 7 BFF architecture | Phase 8 federation hardening | Service-to-service workload identity (① + ④) | **Customer-tenant model (tenant ↔ domain subscription; gate entitlement-trust)** |
| Identity pattern | — | tenant pass-through | isolation regression | ① keyless + ④ workload identity | **② central-IdP entitlement + ⑤ least-privilege assignment (tenant-model realization)** |
| Relationship | **Parent** — supersedes its "tenant == slug" demo simplification (additive § History note) | D6 pass-through preserved | § 3.3 "new axis → new ADR" realized; D5 isolation cohort extended | **Orthogonal** (m2m auth; shares no files) | — |

This ADR amends ADR-MONO-013 § History additively (records the demo-simplification supersession; D1-D8 byte-unchanged) and is a prerequisite for the post-ACCEPTED 4-step execution roadmap. ADR-MONO-017 D6 / ADR-MONO-018 D5 invariants are inherited unchanged. ADR-005 (GAP) is orthogonal.

---

## 6. Status Transition History

Append-only.

| Date | Transition | Decision direction | User intent quote | PR(s) |
|---|---|---|---|---|
| 2026-05-30 | created PROPOSED | D1 = reuse account-service `tenants` as the customer-tenant entity (semantic + seed, no new entity table); D2 = new `tenant_domain_subscription` N:M table in GAP account-service (entitlement authority); D3 = keep single-value `admin_operators.tenant_id` for MVP, multi-assignment join table deferred to step 4 (least-privilege, D3-C rejected); D4 = subscription-driven `selectableTenants`, registry envelope shape byte-stable, zero console-web change; D5 = domain gate evolves `tenant_id == slug` → entitlement-trust (GAP issues domain-scoped token only when subscription + assignment exist; domain isolates by row), highest-risk, dual-accept window; D6 = 4-step zero-regression migration (step 0 ACCEPTED → step 1 backward-compatible model+catalog → step 2 real customers → step 3 per-domain dual-accept gate (Opus) → step 4 cleanup) | "task/ADR 로 만들기" (TASK-MONO-152 — after the AWS-SSO-style multi-tenancy design summary; user chose to make the offered redesign a committed ADR/task rather than a chat-only explanation or a hand-off prompt) | #953 (TASK-MONO-152) |
| 2026-05-31 | PROPOSED → ACCEPTED | D1-D6 **finalised byte-unchanged** from PROPOSED (ACCEPTED *finalises*, does not re-decide; § 1 Context + § 2 Decision tables + § 3 Consequences + § 4 Alternatives + § 5 Relationship + § 7 Provenance byte-identical; flip = Status + History ACCEPTED clause append + § 1.3 minimal past-tense + this row) | "ADR-MONO-019 PROPOSED → ACCEPTED 승급 ... 작성·머지" + "진행해" (TASK-MONO-153 — user-explicit intent after PROPOSED #953 squash `b4ec7edc` main merge; sibling ADR-014/015/017/018 staged-child ACCEPTED 동형) | #<this> (TASK-MONO-153) |

ACCEPTED execution (post-ACCEPTED): `TASK-MONO-153` (ACCEPTED transition, **done** 2026-05-31) + `TASK-BE-3xx` (step 1 model+catalog) + `TASK-BE-3xx` (step 2 real customers) + `TASK-XXX-BE-NNN × 5 + TASK-PC-BE-00x` (step 3 dual-accept gate + IT) + cleanup — execution steps now UNPAUSED, proceeding dependency-correct from this ACCEPTED main beginning with step 1.

---

## 7. Provenance

- HARDSTOP-09 #2 (`platform/hardstop-rules.md`) — mandate for an ADR + PAUSE-until-ACCEPTED on an undocumented cross-cutting architecture decision (6 axes across GAP registry + console catalog + 5 domain gates).
- HARDSTOP-04 (`platform/hardstop-rules.md`) — the ADR-MONO-013 amendment is an additive § History "Additive note" blockquote only (count 7 → 8); D1-D8 byte-unchanged.
- ADR-MONO-013 § D5 / § 1.2 — the data-driven catalog demo simplification (tenant == domain slug) this ADR's production model supersedes.
- ADR-MONO-014 / 015 / 017 / 018 — the staged-child PROPOSED-then-ACCEPTED frame this ADR mirrors.
- ADR-MONO-018 § 3.3 — explicitly anticipated "a new architectural axis → a new ADR" post-Phase-8; this ADR realizes it.
- ADR-001 (GAP) — central OIDC IdP, the entitlement authority D2/D5 centralize on. ADR-002 (GAP) — `admin_operators.tenant_id` + `'*'` sentinel, the operator scope D3 extends.
- `rules/traits/multi-tenant.md` M1-M7 — the isolation invariants preserved while widening the allowed-set (D5).
- Code evidence (§ 1.1): `admin-service/.../console/ProductCatalog.java` (tenantSlug == domain), `ConsoleRegistryUseCase.java` (`selectableTenants` binding), per-domain `TenantClaimValidator` (`tenant_id ∈ {<slug>, *}`).
- ADR-005 (GAP) / TASK-BE-317 — the orthogonal service-to-service workload-identity axis (① + ④); not amended.

분석=Opus 4.7 / 구현=Opus 4.7 (cross-cutting tenant-model architecture; D1-D6 PROPOSED-direction reasoning under HARDSTOP-04/09 discipline; AWS IAM Identity Center / GCP parity mapping; 4-step zero-regression migration mirroring BE-317 dual-accept + BE-303 CI-RED-at-merge discipline; staged-child ADR pattern per ADR-014/015/017/018).
