# ADR-MONO-032 — Unified identity model (single account → roles set; remove the `account_type` CONSUMER/OPERATOR partition)

**Status:** ACCEPTED

**Date:** 2026-06-14

**History:** PROPOSED 2026-06-14 (TASK-MONO-253 — authors the decision record for **collapsing the `account_type` (CONSUMER|OPERATOR) single-immutable partition into the already-existing `roles` set**, so that one identity may simultaneously hold consumer-facing and operator-facing roles — the Google-IAM "one identity + a set of role bindings" model. Replaces the [`jwt-standard-claims.md`](../../platform/contracts/jwt-standard-claims.md) L21 hard constraint *"A single account cannot hold both account types. One person requiring both roles must provision separate accounts."* and the L85-92 no-cross-type-SSO rule. **CHOSEN-PROPOSED** direction per the reasoning below (D1 = roles-set, A selected; account_type multi-value B + single-active-role-switch C rejected). The ACCEPTED transition is a separate user-explicit-intent-gated task, mirroring the ADR-MONO-019/020/021/023/024 staged-child pattern. **No implementation in this task — decision record + impact scope + migration roadmap only. Self-ACCEPT prohibited.**) · ACCEPTED 2026-06-14 (TASK-MONO-254 — user-explicit intent *"추천대로 진행해줘"* after the offered "ADR-032 ACCEPTED 승급" next-step on the PROPOSED #1513 squash `82cb08c0` main merge; D1-D6 CHOSEN-PROPOSED direction **finalised byte-unchanged** from PROPOSED — ACCEPTED *finalises*, does not re-decide; § 1 Context + § 2 Decision tables + § 3 Consequences + § 4 Alternatives + § 5 Relationship + § 7 Provenance byte-identical; flip = Status + this History clause + § 6 ACCEPTED row + § 3.3 PAUSED→UNPAUSED + the **ADR-MONO-021 supersession takes effect** (ADR-021 Status → SUPERSEDED-by-032, its D1-D5 historical). This authorizes the § 3.3 execution roadmap as a dependency-correct base. Sibling staged-child ACCEPTED pattern: ADR-019→MONO-153 / ADR-020→MONO-157 / ADR-021→MONO-165 / ADR-023→MONO-206 / ADR-024→MONO-209.)

**Supersedes:** [ADR-MONO-021](ADR-MONO-021-account-type-claim-source.md) (ACCEPTED 2026-06-02) — ADR-021 decided *where the `account_type` claim is stored and how it is derived* (per-account, denormalized on `auth_db.credentials.account_type`, set at provisioning, immutable for the life of the account). ADR-032 removes the claim's reason-to-exist: there is no longer a single immutable account classification to source. ADR-021's D1-D5 become historical the moment ADR-032 reaches ACCEPTED; its credential-column + token-injection machinery is repurposed by D5/D6 below (the column is dropped or demoted to a derived convenience, the injection switches to roles-only). **Amends:** [`platform/contracts/jwt-standard-claims.md`](../../platform/contracts/jwt-standard-claims.md) (the requiring contract — this is a **breaking change** to the contract: `account_type` goes from Required to removed/optional, the gateway account-type partition is replaced by role-based enforcement, and the SSO scoping rule is rewritten; per the contract's own § Change Rule the contract body must be updated *before* any service emits or any gateway enforces the change — that update lands at the post-ACCEPTED execution step, not in this PROPOSED ADR). **Reconciles:** [ADR-MONO-002 RBAC](ADR-MONO-002-phase-4-template-extraction-trigger.md)-lineage role model + [ADR-MONO-020](ADR-MONO-020-operator-multitenant-assignment.md) (operator → multi-tenant assignment) + [ADR-MONO-024](ADR-MONO-024-tenant-admin-delegation.md) (`TENANT_ADMIN`/`TENANT_BILLING_ADMIN` roles) + [ADR-MONO-025](ADR-MONO-025-abac-data-scope-generalization.md) (ABAC data scope) + [ADR-MONO-026](ADR-MONO-026-role-grant-access-conditions.md) (role-grant access conditions) — all of which already model authorization as a **set of role grants**; ADR-032 makes that set the *only* axis by folding the consumer side (`CUSTOMER`/`FAN`) into it.

**Decision driver:** The platform classifies every person with a single immutable `account_type` that is **CONSUMER xor OPERATOR**, never both ([`jwt-standard-claims.md`](../../platform/contracts/jwt-standard-claims.md) L21, L46, L91). The real world does not partition that way: the same human is routinely both a *customer* and a *staff member* of the same business (a marketplace MD who also shops on the storefront; a seller who both sells and buys; a warehouse lead who is also a fan-platform member). Under the current model that person must hold **two separate accounts with two separate credentials and zero shared session** (L21 + L91). Google Cloud demonstrates the alternative: **one Google identity** holds Gmail/YouTube (consumer surfaces) *and* GCP/Workspace-admin (operator surfaces) simultaneously, because Google has **no `account_type` partition at all** — it has one identity bound to a *set* of IAM roles, and each resource access is authorized by policy evaluation over that set. This monorepo already has the substrate for that model: the `roles` claim (L48, Required) is already a set, already aud-scoped, and the operator side already supports multi-role / multi-platform (L74-81). The `account_type` claim is the *only* thing forcing the xor partition. **The user has explicitly requested moving to the Google-style unified-identity model** (2026-06-14, after walking through the CONSUMER/OPERATOR examples and the AWS-vs-GCP comparison). Choosing the unified model's mechanics during implementation would silently bake the identity model (HARDSTOP-09) and would breach the `jwt-standard-claims.md` § Change Rule (contract-first). This ADR is that decision record.

**Related:** [`platform/contracts/jwt-standard-claims.md`](../../platform/contracts/jwt-standard-claims.md) (the requiring contract — § Account Types L12-22, § Standard Claims L46/L48, § Role Strategy L60-81, § SSO Scope L85-92, § Gateway Enforcement L119-124/L132), [`platform/service-types/identity-platform.md`](../../platform/service-types/identity-platform.md) (the per-account-immutable framing ADR-021 adopted and this ADR overturns), [ADR-001 (IAM)](../../projects/iam-platform/docs/adr/ADR-001-oidc-adoption.md) (the central OIDC IdP — the single issuance authority that must now emit roles-only tokens), [ADR-MONO-014](ADR-MONO-014-platform-console-operator-auth-token-exchange.md) + [ADR-MONO-020](ADR-MONO-020-operator-multitenant-assignment.md) (the assume-tenant token-exchange the rejected option C would have leaned on), `projects/iam-platform/.../oauth2/TenantClaimTokenCustomizer.java` (the customizer that injects claims — D3 rewrites its `account_type` leg), `projects/iam-platform/.../security/CredentialAuthenticationProvider.java` (the details-map builder — D6 changes the credential/account shape it reads), `projects/ecommerce-microservices-platform/apps/gateway-service/.../filter/AccountTypeEnforcementFilter.java` + `JwtHeaderEnrichmentFilter.java` (the enforcement D3 rewrites — from `account_type` partition to role-based), and the wms/erp/scm/fan gateways' equivalent account-type checks.

---

## 1. Context

### 1.1 What the current model is

`jwt-standard-claims.md` defines **two orthogonal authorization axes**:

1. **`account_type`** (L46, Required, `CONSUMER` | `OPERATOR`) — a single immutable per-account classification. L21: *"A single account cannot hold both account types. One person requiring both roles must provision separate accounts."* Gateways gate on it first (L119-124): fan/wms/erp/scm reject `!= OPERATOR` (resp. `!= CONSUMER` for fan); ecommerce is path-based (`/api/admin/**` → OPERATOR, else CONSUMER).
2. **`roles`** (L48, Required, `string[]`, aud-scoped) — a *set* of platform-scoped roles for the token's `aud`. Consumer side is single-role (`["CUSTOMER"]`, `["FAN", "PREMIUM_MEMBER"]`); operator side is genuinely multi-role and multi-platform (L74-81).

The `roles` axis is already the Google-IAM shape (a set of bindings). The `account_type` axis is a coarse xor partition layered *on top* — and it is the only thing that makes "consumer" and "operator" mutually exclusive for one account. SSO is scoped by that partition (L85-92): consumer accounts SSO across ecommerce+fan; operator accounts SSO across wms/erp/scm; **no cross-type SSO even for the same person** (L91).

### 1.2 Why the partition is a modeling limitation, not a safety feature

The xor partition was justified by *enforcement simplicity* (one `equals()` per gateway) and *blast-radius isolation* (a consumer session can never reach an operator API because the token literally cannot carry an operator identity). Both benefits are real — but both are **also achievable with role-based enforcement** (D3): a gateway that admits a request iff the token's `roles` contains a role valid for the requested surface is exactly as safe, because authorization is still a positive check against a closed role set. The partition buys simplicity at the cost of **not being able to represent a true fact about the world** — that one person is both a customer and an operator. Cloud IAM (Google especially) proves a single identity can safely hold both consumer and operator capability when every access is authorized by role/policy rather than by a person-level type flag.

### 1.3 What ADR-021 decided, and why ADR-032 supersedes it

ADR-MONO-021 (ACCEPTED 2026-06-02) answered *"where does the required `account_type` claim come from?"* — per-account, denormalized on `auth_db.credentials.account_type`, set at provisioning, immutable. That was the correct decision **given that the partition exists**. ADR-032 removes the premise: if one identity may hold both consumer and operator roles, there is no single immutable account classification to source. ADR-021's machinery (the credential column, the customizer injection) is not discarded blindly — D5/D6 repurpose it (the column is dropped or demoted to a derived convenience; the injection switches to roles-only). ADR-032 therefore **supersedes** ADR-021 rather than amending it: the claim ADR-021 sourced ceases to be a required claim.

### 1.4 The unspecified point this ADR must resolve (HARDSTOP-09 + contract-first)

"Move to a Google-style unified identity" is underspecified in five ways, each of which would silently bake the identity model if chosen during implementation:

- **How is the consumer side represented once `account_type` is gone?** (Is "consumer" a role like `CUSTOMER`/`FAN`, or a residual flag?) — D1/D2.
- **What does the token carry, and is it still aud-scoped or cross-platform?** — D2.
- **How does each gateway authorize without the partition, while preserving the consumer↔operator isolation that genuinely matters (a storefront session must not, by accident, drive an admin API)?** — D3/D4.
- **What is the credential/account shape — one credential per person, or the current two?** — D6.
- **How does a backward-compatible migration land without a window where tokens are mis-authorized?** — D5.

The contract's own § Change Rule additionally mandates that the contract body be updated **before** any emit/enforce. This ADR records the decision (HARDSTOP-09 remediation: decide first, PAUSE until ACCEPTED); the contract rewrite + implementation are post-ACCEPTED execution tasks.

---

## 2. Decision

> Direction is **CHOSEN-PROPOSED**; finalised (byte-unchanged) at ACCEPTED. Each decision lists the chosen option + the rejected alternatives. **No code/contract change in this ADR.**

### D1 — identity model (the crux): single account, roles-set is the only authorization axis

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Remove `account_type`; one account holds a SET of roles spanning consumer and operator capability. "Consumer" is expressed as ordinary roles (`CUSTOMER`, `FAN`), exactly as operator capability already is (`WMS_OPERATOR`, …). Authorization everywhere is a positive check against the role set — the Google-IAM "one identity + role bindings" model.** | The `roles` claim (already Required, already a set) becomes the *sole* authz axis. A single person who both shops and operates holds e.g. `CUSTOMER` (ecommerce) + `WMS_OPERATOR` (wms) on one account, one credential, one SSO session. No person-level type flag. Gateways authorize by role membership for the requested surface (D3). | **CHOSEN** — directly realizes the user's requested Google model; reuses the existing roles substrate (minimal net-new concept); makes the role set the single source of truth (no orthogonal flag to keep consistent); the consumer↔operator isolation that matters is preserved as a *role-presence* check, not a *type* check (D3/D4). |
| B. Keep `account_type` but make it a multi-value set (`account_types: ["CONSUMER","OPERATOR"]`) | Smallest gateway diff (`equals` → `contains`); the partition becomes a set | **Rejected** — keeps a second, redundant authorization axis alongside `roles` that must be kept consistent with it (an account with `WMS_OPERATOR` role but no `OPERATOR` type, or vice-versa, is a contradiction the system must now police); does not actually unify (it widens the partition instead of removing it); cannot express *which domain* a capability applies to (that is what `roles` already does). A redundant coarse axis is worse than no axis. |
| C. Single identity + single-active-role per token (role/context switch via token-exchange) | One credential, but each token activates one role/context; switching = RFC 8693 re-exchange (reuse the ADR-014/020 assume-tenant pipeline) | **Rejected as the model (retained as an optional UX affordance — see § 3.3 note)** — this is the AWS-`AssumeRole` shape, not the Google shape the user chose; it does not let one *token* carry both consumer and operator capability, so "one login, both surfaces simultaneously" still requires a context switch. It also threads every consumer/operator transition through token-exchange, a heavier runtime path than role-membership checks. (If a future UX wants an explicit "act-as" scoping-down, it can layer on top of D1-A as a *narrowing* of the role set, not as the identity model.) |

### D2 — token shape: keep `roles` aud-scoped; drop `account_type`; SSO becomes role/assignment-scoped

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Retain the existing aud-scoped `roles` claim unchanged in shape; simply (a) stop emitting `account_type`, and (b) allow one identity to obtain a token for ANY `aud` for which it holds ≥1 role. The token for `aud=wms` carries that account's wms roles; the token for `aud=ecommerce` carries its ecommerce roles (consumer and/or admin). SSO is scoped by "has a role on the target platform", not by account type.** | Token body is byte-smaller (one fewer claim). `aud` + `roles` together fully express "who may do what here". The same account requesting `aud=ecommerce` gets `roles:["CUSTOMER"]` (shops) and, if also an admin, `["CUSTOMER","ADMIN"]`; the gateway path-routes on role (D3). Cross-type SSO (L91) is **lifted**: one session, tokens for any entitled platform. | **CHOSEN** — least structural churn (the `roles` claim and its aud-scoping are battle-tested); preserves per-platform least privilege (a wms token still only carries wms roles); makes the migration a *removal* (drop a claim + relax SSO) rather than a *reshape*. The cross-platform-flattened `domain:role` form (option B) is not needed because `aud` already scopes. |
| B. Flatten to a single cross-platform `roles` array (`["ecommerce:customer","wms:operator"]`) in one token | One token carries every platform's roles | **Rejected** — breaks the aud-scoping least-privilege property (a storefront-only request would carry the holder's wms-operator capability in the same token — exactly the blast-radius the partition guarded against, now reintroduced); larger tokens; bigger gateway rewrite. aud-scoped roles already give the Google model without flattening. |

### D3 — gateway enforcement: replace the `account_type` partition with role-based admission

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Each gateway admits iff the token (matching `aud`) carries ≥1 role valid for the requested surface. fan: any `FAN`-family role. wms/erp/scm: any operator role for that platform. ecommerce path-based: `/api/admin/**` requires an admin-family role (e.g. `ADMIN`), all other paths require a consumer role (e.g. `CUSTOMER`). `X-Account-Type` injection (L132) is removed; downstream services that need the distinction derive it from `X-User-Role`.** | `AccountTypeEnforcementFilter` changes from `"CONSUMER".equals(accountType)` to `roles.contains(<surface-required-role>)`. Same positive-closed-set check, same 403-on-miss semantics; the isolation property (consumer session can't drive admin API) is preserved because a `CUSTOMER`-only token still fails the `/api/admin/**` role check. | **CHOSEN** — exactly as safe (positive check against a closed role set); removes the redundant axis; the ecommerce path-based rule maps cleanly onto role-presence; downstream `X-User-Role` already exists (L130), so dropping `X-Account-Type` loses no information. |
| B. Keep `account_type` enforcement, add role checks alongside | Two-axis enforcement retained | **Rejected** — keeps the partition (defeats the purpose); double-gates with a redundant axis. |

### D4 — consumer↔operator isolation under one identity (the safety question)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Isolation is preserved by `aud`-scoping + role-presence, NOT by an account-level type. A token is always for exactly one `aud` and carries only that platform's roles (D2-A). A storefront (`aud=ecommerce`, non-admin path) admits only on a consumer role; it never sees the holder's wms roles (different `aud`, different token). The "one person, both capabilities" fact lives in the account's role *grants*; any single token is still narrowly scoped.** | The dangerous case ("a consumer session accidentally drives an operator API") cannot occur: that API lives behind a different `aud` whose token the storefront flow never requested. Admin-vs-consumer *within* ecommerce is separated by the path→role rule (D3). Sensitive operator surfaces additionally retain RBAC (ADR-002 lineage) + ABAC data scope (ADR-025) + access conditions (ADR-026) — unchanged, and now the *only* gates rather than a second line behind the type partition. | **CHOSEN** — keeps the genuinely-valuable isolation (per-token least privilege) while discarding the modeling limitation (person-level xor). Defense-in-depth (RBAC+ABAC+conditions) is unaffected. |
| B. Require an explicit "operator mode" elevation (step-up auth) before any operator token issues to a dual-capability identity | Operator tokens for a mixed identity require re-auth/MFA | **Deferred, not rejected** — a reasonable *hardening* (mirrors GCP's sensitive-action re-auth), but it is a step-up-auth policy orthogonal to the identity model; it can layer on D1-A later without changing the model. Recorded as a § 3.3 future option. |

### D5 — migration phasing (backward-compatible; zero mis-authorization window)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Backward-compatible staged migration; ACCEPTED is step 0; each step independently main-GREEN.** | **Step 0 (doc-only):** this ADR PROPOSED → ACCEPTED (user-gated) + the `jwt-standard-claims.md` contract rewrite (§ Account Types / § Standard Claims / § Role Strategy / § SSO / § Gateway Enforcement) landed contract-first per its § Change Rule. **Step 1 (dual-read gateways):** every gateway accepts EITHER the legacy `account_type` partition OR the new role-based admission (D3) — both paths admit the same legacy tokens, so no token is mis-authorized while issuance still emits `account_type`. **Step 2 (issuance):** IdP stops requiring `account_type`, emits roles-only tokens, and allows one identity to request any `aud` it holds roles for (D2-A); consumer capability is seeded as `CUSTOMER`/`FAN` roles for existing CONSUMER accounts. **Step 3 (account/credential unify):** merge the two-credential model toward one-account-many-roles (D6) — opt-in account-link first, no forced merge of existing separate accounts. **Step 4 (drop legacy):** remove the `account_type` claim, column, and the dual-read gateway leg; SSO cross-type lifted. **Step 5 (verify):** extend the GAP-backed e2e (TASK-INT-023 lineage) to assert a single account holding both a consumer and an operator role obtains both tokens in one session. | **CHOSEN** — dual-read (step 1) guarantees no window where a valid legacy token is rejected; consumer-role seeding (step 2) is additive; account unification (step 3) is opt-in (existing separate accounts keep working). Each step main-GREEN; reversible until step 4. |
| B. Big-bang (contract + issuance + all gateways + account merge in one PR) | Single atomic flip across IdP + 5 gateways + frontends | **Rejected** — couples a breaking contract change across 5 gateways + issuance + the account model in one un-bisectable PR; violates the staged discipline every prior cross-cutting ADR (019/020/021/023/024) followed. |

### D6 — credential/account shape + provisioning

| Option | Mechanics | Verdict |
|---|---|---|
| **A. One account = one credential = a set of role grants. Self-service signup grants consumer roles (`CUSTOMER`/`FAN`); operator/admin provisioning grants operator roles onto the SAME account model. A person who is both has one account with both grants. Existing separate accounts are not force-merged (opt-in link, D5 step 3).** | `CredentialAuthenticationProvider` reads one credential and assembles the role set from the account's grants (RBAC store, ADR-002 lineage + ADR-020 assignments). Provisioning paths each *add grants* rather than *set a type*. The `auth_db.credentials.account_type` column (ADR-021 D1) is dropped (or demoted to a derived convenience for legacy reads during the migration window). | **CHOSEN** — the role grants already exist as the authz substrate; folding consumer roles in unifies the account; opt-in link avoids a risky bulk identity merge; matches D2/D3. |
| B. Keep two credentials, add a "linked accounts" pointer | Two credentials, linked for SSO | **Rejected** — preserves the two-credential split (only papers over it with a link); SSO-cross-type still needs special handling; not the single-identity model the user chose. |

---

## 3. Consequences

### 3.1 Hard invariants this ADR carries

- **One identity may hold consumer AND operator roles simultaneously** — the L21 xor constraint and the L91 no-cross-type-SSO rule are removed; this is the entire point.
- **Per-token least privilege is preserved** — a token is for one `aud` and carries only that platform's roles (D2-A/D4-A); the unification is at the *account/grant* level, not the *token* level.
- **Authorization is a positive check against a closed role set everywhere** — gateways (D3), plus the unchanged RBAC (ADR-002 lineage) / ABAC (ADR-025) / access-conditions (ADR-026) defense-in-depth.
- **IAM is the single issuance authority (ADR-001)** — only the IdP emits the roles; gateways consume, never derive.
- **Contract-first** — `jwt-standard-claims.md` is rewritten before any emit/enforce (its § Change Rule), at D5 step 0.

### 3.2 What this ADR does NOT do (deferred to ACCEPTED + post-ACCEPTED execution)

- No implementation: no contract rewrite, no customizer/provider/gateway change, no account merge, no e2e — all post-ACCEPTED execution tasks (§ 3.3).
- No forced merge of existing CONSUMER/OPERATOR account pairs (D6-A opt-in link only).
- No step-up-auth / operator-mode elevation (D4-B deferred).
- Does not change RBAC/ABAC/access-condition semantics (ADR-002/025/026) — it only makes the role set the sole top-level axis by folding the consumer side in.

### 3.3 Future-self (post-ACCEPTED execution roadmap — sketch, finalised at ACCEPTED)

> **✅ ALL SIX STEPS COMPLETE — landed on `main` 2026-06-15 (TASK-MONO-265). See § 6's "D5 ROADMAP COMPLETE" note for the authoritative per-step ledger.** The `TASK-…` placeholders below are the original ACCEPTED-time sketch (kept for provenance); each was realized: step 0 = MONO-255, step 1 = MONO-256, step 2 = ADR-MONO-033 + BE-368/369/370, step 3 = ADR-MONO-034 + BE-371/372/373/374, step 4 = ADR-MONO-035 (4a BE-376 / 4b MONO-261-263 / 4c BE-377 / 4d BE-378), step 5 = MONO-265. **Nothing in this roadmap remains open** — `account_type` is fully removed and `roles` is the sole authorization axis.

1. **`TASK-…`** (contract-first, D5 step 0) — rewrite `jwt-standard-claims.md` (§ Account Types → unified-identity; `account_type` Required→removed; § Role Strategy consumer-role folding; § SSO role/assignment-scoped; § Gateway Enforcement role-based). Model = **Opus** (breaking contract).
2. **`TASK-…`** (dual-read gateways, D5 step 1, D3) — every gateway accepts legacy-`account_type` OR role-based admission. Model = **Opus** (5-gateway cross-cutting).
3. **`TASK-…`** (IdP roles-only issuance + any-aud SSO, D5 step 2, D2) — stop requiring `account_type`; seed `CUSTOMER`/`FAN` roles; allow any entitled `aud`. Model = **Opus**.
4. **`TASK-…`** (account/credential unify + provisioning, D5 step 3, D6) — one-account-many-roles + opt-in link. Model = **Opus**.
5. **`TASK-…`** (drop legacy `account_type`, D5 step 4) — remove claim/column/dual-read leg. Model = **Sonnet**.
6. **`TASK-…`** (verify, D5 step 5) — e2e: one account, both tokens, one session. Model = **Sonnet**.
- **Optional follow-ups:** D4-B operator-mode step-up auth; C-style explicit "act-as" narrowing as a UX affordance.

---

## 4. Alternatives Considered

- **Do nothing (keep the xor partition).** Rejected — it cannot represent the true fact that one person is both a customer and an operator; forces two accounts / two credentials / no shared session; the user explicitly asked to move off it.
- **`account_type` multi-value set (D1-B).** Rejected — a redundant coarse axis alongside `roles` that must be kept consistent; widens rather than removes the partition.
- **Single-active-role token-exchange (D1-C / AWS AssumeRole shape).** Rejected as the *model* (retained as an optional later affordance) — does not let one token carry both capabilities; threads every transition through token-exchange; it is the AWS shape, not the Google shape chosen.
- **Cross-platform flattened roles (D2-B).** Rejected — breaks aud-scoped least privilege; reintroduces the blast radius the partition guarded.
- **Keep two credentials + link (D6-B).** Rejected — papers over the split instead of unifying.

## 5. Relationship to the contract + ADR-021 + the RBAC/ABAC ADR family

| | `jwt-standard-claims.md` | ADR-021 (account_type source) | ADR-002 RBAC / ADR-020 / 024 / 025 / 026 |
|---|---|---|---|
| Relationship | **Breaking amend** — removes `account_type` Required, replaces the gateway partition + SSO rule with role-based (contract-first at D5 step 0) | **Supersedes** — removes the claim ADR-021 sourced; repurposes its column/injection (D5/D6) | **Reconciles / builds on** — these already model authz as a role-grant set; ADR-032 makes that set the sole top-level axis by folding the consumer side (`CUSTOMER`/`FAN`) in. ADR-020 assignments, ADR-024 `TENANT_ADMIN`/`TENANT_BILLING_ADMIN`, ADR-025 ABAC scope, ADR-026 access conditions all unchanged and now the primary gates. |

## 6. Status Transition History

| Date | Transition | Decision summary | Trigger | PR |
|---|---|---|---|---|
| 2026-06-14 | created PROPOSED | D1 = remove `account_type`; roles-set is the sole authz axis (Google "one identity + role bindings"); reject multi-value type (redundant axis) + single-active-role-switch (AWS AssumeRole shape, not Google). D2 = keep aud-scoped `roles`, drop `account_type`, SSO role/assignment-scoped (reject cross-platform flattening). D3 = gateway role-based admission replaces the type partition (ecommerce path→role); drop `X-Account-Type`. D4 = isolation preserved by aud-scoping + role-presence, not a person-level type; RBAC/ABAC/conditions unchanged (step-up "operator mode" deferred). D5 = backward-compatible staged migration (contract-first → dual-read gateways → roles-only issuance → account unify (opt-in) → drop legacy → e2e); zero mis-authorization window via dual-read. D6 = one account = one credential = role-grant set; provisioning adds grants; existing pairs opt-in-link not force-merged. **Supersedes ADR-021.** | User-explicit request to adopt the Google-style unified-identity model (2026-06-14, after the CONSUMER/OPERATOR walk-through + AWS-vs-GCP comparison + AskUserQuestion D1 = "roles 집합 클레임" / option A) | #1513 |
| 2026-06-14 | PROPOSED → ACCEPTED | D1-D6 CHOSEN-PROPOSED direction **finalised byte-unchanged** (ACCEPTED *finalises*, does not re-decide); § 1-5/7 byte-identical to PROPOSED #1513 squash `82cb08c0`; flip = Status + History ACCEPTED clause + this row + § 3.3 PAUSED→UNPAUSED + **ADR-MONO-021 supersession발효** (ADR-021 Status → `SUPERSEDED by ADR-MONO-032`, D1-D5 historical). Authorizes the § 3.3 6-step execution roadmap (dependency-correct base = this ACCEPTED main): contract-first `jwt-standard-claims.md` rewrite → dual-read gateways → roles-only issuance → account/credential unify → drop legacy `account_type` → e2e. | "추천대로 진행해줘" (TASK-MONO-254 — user-explicit intent after the offered "ADR-032 ACCEPTED 승급" next-step; sibling ADR-021→MONO-165 same-session PROPOSED→ACCEPTED) | #<this> |

> **ACCEPTED 2026-06-14 (TASK-MONO-254).** The § 3.3 execution roadmap is now **UNPAUSED**; the execution steps proceed dependency-correct from this ACCEPTED main, beginning with the contract-first `jwt-standard-claims.md` rewrite (D5 step 0). Each remains a separate task; D1-D6 are finalised and not re-litigated at execution. **ADR-MONO-021 is now SUPERSEDED** (its `account_type` claim-source decision is historical; the claim ceases to be required once the execution roadmap lands).

> **D5 ROADMAP COMPLETE — all of steps 0–5 have landed on `main` (2026-06-15, TASK-MONO-265).** Step 0 contract rewrite (MONO-255) → step 1 dual-read gateways (MONO-256) → step 2 roles-only issuance (ADR-MONO-033 + BE-368/369/370) → step 3 account/credential unify (ADR-MONO-034 + BE-371/372/373/374) → **step 4 drop legacy `account_type`** (the operator-auth mechanics decided in [ADR-MONO-035](ADR-MONO-035-operator-auth-unification-model.md): 4a BE-376, 4b MONO-261/262/263, 4c BE-377, 4d BE-378) → **step 5 e2e** (MONO-265). `account_type` is fully removed (claim, `credentials`/`admin_operators` columns, the dual-read gateway legs, `X-Account-Type`); `roles` is the sole authorization axis for consumers (seeded `CUSTOMER`/`FAN`) and operators (derived at assume-tenant from the selected tenant's entitled domains); one identity holds both role-bindings and obtains both tokens in one session (step-5 IT). The unified-identity model (D1-D6) is realized end-to-end.

## 7. Provenance

- `platform/contracts/jwt-standard-claims.md` L21 (the xor constraint this ADR removes) + L46 (`account_type` Required → removed) + L48 (`roles` set — the substrate D1 reuses) + L60-81 (§ Role Strategy — consumer single-role / operator multi-role, the asymmetry D1 unifies) + L85-92 (§ SSO no-cross-type — lifted by D2) + L119-124 (gateway account-type partition → D3 role-based) + L132 (`X-Account-Type` injection → dropped).
- `platform/service-types/identity-platform.md` (the per-account-immutable framing ADR-021 adopted; overturned here).
- ADR-MONO-021 (the superseded decision — `account_type` per-account/immutable/credential-denormalized).
- ADR-MONO-002 RBAC lineage + ADR-MONO-020 (operator multi-tenant assignment) + ADR-MONO-024 (`TENANT_ADMIN`/`TENANT_BILLING_ADMIN`) + ADR-MONO-025 (ABAC data scope) + ADR-MONO-026 (role-grant access conditions) — the role-grant substrate ADR-032 makes the sole top-level axis.
- `projects/ecommerce-microservices-platform/apps/gateway-service/.../filter/AccountTypeEnforcementFilter.java` + `JwtHeaderEnrichmentFilter.java` (D3 rewrite targets) + `projects/iam-platform/.../oauth2/TenantClaimTokenCustomizer.java` + `.../security/CredentialAuthenticationProvider.java` (D3/D6 rewrite targets).
- Google Cloud IAM (one identity + role-binding set; no person-level account-type partition; consumer Gmail/YouTube + operator GCP/Workspace on one identity) — the reference model the user chose, contrasted in-session with AWS (where the consumer surface = Amazon retail is a separate system, so AWS itself has no cross-type identity).

분석=Opus 4.8 / 구현=Opus 4.8 (cross-service identity-model supersession under HARDSTOP-09 + contract-first discipline; D1-D6 PROPOSED-direction reasoning; breaking JWT-contract amend touching IdP issuance + 5 gateways + the account/credential model; staged-child ADR pattern per ADR-019/020/021/023/024).
