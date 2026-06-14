# ADR-MONO-021 — `account_type` OIDC claim source (CONSUMER vs OPERATOR — per-account, denormalized on the credential)

**Status:** SUPERSEDED by [ADR-MONO-032](ADR-MONO-032-unified-identity-roles-model.md) (2026-06-14) — ADR-032 removes the `account_type` CONSUMER/OPERATOR xor partition entirely (one identity holds a *set* of roles spanning consumer + operator capability; the Google-IAM unified model), so the per-account-immutable classification this ADR sourced ceases to exist as a required claim. D1-D5 below are **historical**: they remain the authoritative record of *how `account_type` was sourced while the partition existed*, and ADR-032 D5/D6 repurpose this ADR's credential-column + token-injection machinery (column dropped/demoted, injection switched to roles-only) across the migration window. Was: ACCEPTED 2026-06-02.

**History:** PROPOSED 2026-06-02 (TASK-MONO-164 — authors the decision record for **where the platform-required `account_type` JWT claim is stored and how GAP auth-service derives it at issuance**. [`platform/contracts/jwt-standard-claims.md`](../../platform/contracts/jwt-standard-claims.md) L46 marks `account_type` (`CONSUMER`|`OPERATOR`) **Required**, and [`platform/service-types/identity-platform.md`](../../platform/service-types/identity-platform.md) L131 states it is *"set at issuance and immutable for the life of the account"* — but **no spec or ADR says where it lives or how it is determined** (0 matches for `account_type` across GAP `specs/`, GAP `docs/adr/`, and repo `docs/adr/`). The GAP token pipeline does not emit it today. Five decisions D1-D5, **CHOSEN-PROPOSED** direction per the reasoning below; the ACCEPTED transition is a separate user-explicit-intent-gated task, mirroring the ADR-MONO-014/015/017/018/019/020 staged-child pattern. **No implementation in this task — decision record + impact scope + migration roadmap only.**) · ACCEPTED 2026-06-02 (TASK-MONO-165 — user-explicit intent *"진행"* after the offered "ADR-021 ACCEPTED 승급" next-step on the PROPOSED #1006 squash `20f19c26` main merge; D1-D5 CHOSEN-PROPOSED direction **finalised byte-unchanged** from PROPOSED — ACCEPTED *finalises*, does not re-decide; this authorizes the § 3.3 execution roadmap as a dependency-correct base. Sibling staged-child ACCEPTED pattern: ADR-014→MONO-110 / ADR-015→MONO-112 / ADR-017→MONO-126 / ADR-018→MONO-138 / ADR-019→MONO-153 / ADR-020→MONO-157.) · SUPERSEDED 2026-06-14 (TASK-MONO-254 — [ADR-MONO-032](ADR-MONO-032-unified-identity-roles-model.md) ACCEPTED removes the `account_type` xor partition; D1-D5 here become historical, the credential-column + injection machinery repurposed by ADR-032 D5/D6. § 1-7 body byte-unchanged — supersession is a Status + History note only, not a re-decision of how the now-removed claim was sourced.)

**Decision driver:** The platform JWT contract makes `account_type` a **required** access-token claim and assigns the downstream gateways hard behaviour on it ([`jwt-standard-claims.md`](../../platform/contracts/jwt-standard-claims.md) L119-124: fan gateway rejects `!= CONSUMER`; ecommerce gateway path-based — `/api/admin/**` rejects `!= OPERATOR`, all else rejects `!= CONSUMER`; wms/erp/scm reject `!= OPERATOR`). GAP auth-service, however, **never injects the claim** — `TenantClaimTokenCustomizer` adds only `tenant_id`/`tenant_type`/`entitled_domains`, and `CredentialAuthenticationProvider`'s details map carries `tenant_id`/`tenant_type`/`account_id` but no `account_type`. The effect is a **latent contract violation that is already a hard breakage** on any real GAP-backed path: `AccountTypeEnforcementFilter` (ecommerce gateway, `ORDER=-2`) reads `account_type` from the JWT and does `"CONSUMER".equals(accountType)` — with the claim **absent the comparison is `false`, so every authenticated request is 403'd** (consumer paths require CONSUMER; admin paths require OPERATOR; null satisfies neither). It is masked today only because the ecommerce GAP-login e2e specs are `SKIP_GAP_E2E=1` (no real GAP in the e2e stack — see TASK-INT-023) and no environment exercises an authenticated GAP-token request through the gateway. Emitting the claim is therefore a genuine **correctness fix** (un-break the gateway), not a cosmetic addition. But *where* `account_type` is stored and *how* it is derived at issuance is an architecture decision spanning GAP (auth-service issuance + the credential/account model + the provisioning paths) and every consuming gateway's contract — implementing it without recording the decision would silently bake the account-classification model (HARDSTOP-09). This ADR is that decision record.

**Supersedes:** none. **Amends:** none (the claim is net-new; no existing ADR decided it). **Reconciles:** [`platform/contracts/jwt-standard-claims.md`](../../platform/contracts/jwt-standard-claims.md) (the contract *requires* the claim but is silent on its source — this ADR supplies the missing source decision; the contract body is byte-unchanged at PROPOSED, the reconciling note lands at execution). The GAP `credentials`/`accounts` data-model specs + `TenantClaimTokenCustomizer` + `CredentialAuthenticationProvider` are byte-unchanged at PROPOSED — D4 preserves them through a default-bearing migration window; reconciliation lands at the post-ACCEPTED execution tasks, never inside this ADR.

**Related:** [`platform/contracts/jwt-standard-claims.md`](../../platform/contracts/jwt-standard-claims.md) (the requiring contract; L46 + L119-124 gateway rules + L132 `X-Account-Type` injection), [`platform/service-types/identity-platform.md`](../../platform/service-types/identity-platform.md) (L131 "set at issuance, immutable per account" — the per-account framing D1 adopts), [ADR-001 (GAP)](../../projects/iam-platform/docs/adr/ADR-001-oidc-adoption.md) (GAP central OIDC IdP — the single issuance authority that must now emit the claim), [ADR-MONO-019](ADR-MONO-019-platform-console-customer-tenant-model.md) + [ADR-MONO-020](ADR-MONO-020-operator-multitenant-assignment.md) (the `tenant_id`/`entitled_domains` claims this one sits alongside — `account_type` is **orthogonal** to tenant scoping: it classifies the *person* CONSUMER vs OPERATOR, not which customer they act for), `projects/global-account-platform/apps/auth-service/.../oauth2/TenantClaimTokenCustomizer.java` (the customizer D3 extends — exact mirror of the `tenant_id` injection), `projects/global-account-platform/apps/auth-service/.../security/CredentialAuthenticationProvider.java` (the details-map builder D3 extends), `projects/ecommerce-microservices-platform/apps/gateway-service/.../filter/AccountTypeEnforcementFilter.java` + `JwtHeaderEnrichmentFilter.java` (the consumers — unchanged; they already read the claim).

---

## 1. Context

### 1.1 What the contract requires, and what GAP emits

`jwt-standard-claims.md` L46: `account_type` is **Required**, values exactly `CONSUMER` | `OPERATOR`, "Account classification — determines platform eligibility". `identity-platform.md` L131: "set at issuance and **immutable for the life of the account**". The gateways gate on it (L119-124) and inject `X-Account-Type` from it (L132).

GAP emits `tenant_id`, `tenant_type`, `entitled_domains` — **never `account_type`**. There is no column for it on `auth_db.credentials` or `account_db.accounts`, no derivation anywhere, and no spec/ADR that says where it should come from.

### 1.2 Why "absent" is a hard break, not a soft gap

`AccountTypeEnforcementFilter` (ecommerce gateway): `"OPERATOR".equals(accountType)` for `/api/admin/**`, `"CONSUMER".equals(accountType)` otherwise. With the claim **absent**, both comparisons are `false` → **403 on every authenticated request**. The ecommerce authenticated API path is therefore non-functional against a real GAP token; it is only un-exercised because the GAP-backed e2e is skipped (TASK-INT-023 documented the `SKIP_GAP_E2E=1` reality). fan/wms/erp/scm gateways gate the same way. Emitting the claim **unbreaks** this.

### 1.3 The unspecified point this ADR must resolve (HARDSTOP-09)

`account_type` is a CONSUMER/OPERATOR classification that the contract calls per-account-immutable, but:
- It is **not** `tenant_type` (B2C/B2B): the `ecommerce` tenant hosts BOTH a CONSUMER surface (web-store, `ecommerce-web-store-client`) AND an OPERATOR surface (admin-dashboard, `ecommerce-admin-dashboard-client`) — so a single tenant maps to *both* account types. Deriving `account_type` from `tenant_type` is impossible.
- It is **not** cleanly per-client either: the contract says immutable **per account**, so the same account must yield the same `account_type` regardless of which client it authenticates through.

No ADR records where `account_type` is stored or how auth-service derives it at token issuance. Choosing during implementation would silently bake the account-classification model. D1 below is that decision.

---

## 2. Decision

> Direction is **CHOSEN-PROPOSED**; finalised (byte-unchanged) at ACCEPTED. Each decision lists the chosen option + the rejected alternatives.

### D1 — `account_type` source model (the crux)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Per-account property, stored DENORMALIZED on `auth_db.credentials.account_type` (the row the SAS form-login path already reads), with `account_db.accounts.account_type` as the business authority** | `account_type` is an immutable property of the account, set at provisioning (D2). It is denormalized onto `credentials` — exactly as `tenant_id` already is — so `CredentialAuthenticationProvider` (which reads only `credentials`) can put it in the principal details with **no account-service call on the issuance hot path**. The `accounts` table MAY carry the authoritative copy for the business model; the **claim is sourced from the credential**. | **CHOSEN** — matches the contract's per-account-immutable framing (L131); mirrors the proven `tenant_id` denormalization (already on `credentials` and already the claim source); keeps token issuance independent of account-service (the fail-soft / lean-stack principle — TASK-INT-023 proved login needs only `credentials`); single write at provisioning, single read at issuance. |
| B. Derive from `tenant_type` (B2C→CONSUMER, B2B→OPERATOR) | No new storage; mapping in `TenantClaimTokenCustomizer` | **Rejected** — impossible: the `ecommerce` tenant has BOTH CONSUMER (web-store) and OPERATOR (admin) accounts (§ 1.3). A tenant→type mapping cannot express both. |
| C. Derive from the OIDC client / requested scope (`ecommerce.consumer` vs `ecommerce.operator`) | The active client/scope determines the claim at issuance | **Rejected** — violates per-account immutability (L131): the SAME account authenticating through two clients would receive two different `account_type`s. `account_type` classifies the person, not the app. (The scope naming reflects which app a *typed* account uses, not the type's source of truth.) |

### D2 — assignment at provisioning (who sets CONSUMER vs OPERATOR)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. The provisioning path sets `account_type`: self-service signup → `CONSUMER` (default); admin/operator provisioning → `OPERATOR`. The value is carried on the internal credential-creation call (`/internal/auth/credentials`) and stored on `credentials` (D1).** | CONSUMER accounts are self-registered (account-service `POST /api/accounts/signup` → default CONSUMER). OPERATOR accounts are company-provisioned (admin/operator-creation path → OPERATOR). Each provisioning path is the authority for the type it creates; the credential row records it immutably. | **CHOSEN** — matches the contract's own definitions (L14-19: CONSUMER = self-registered B2C, OPERATOR = company-provisioned B2B); the type is decided exactly where the account is born; immutable thereafter. |
| B. A post-hoc admin toggle / migration assigns types | Types set by a separate administrative process | **Rejected** — contradicts "immutable at issuance"; invites drift between the account's real role and its claim. |

### D3 — claim injection points (mirror the `tenant_id` path)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. `TenantClaimTokenCustomizer` injects `account_type` on the access token AND the id_token for authorization_code + refresh grants, read from the principal details set by `CredentialAuthenticationProvider` (which reads it from `credentials`, D1). `client_credentials` (workload) tokens get NO `account_type` (a workload is not an account). `token_exchange` (assume-tenant, ADR-020 D2) PRESERVES the operator's `account_type` (OPERATOR).** | Exact structural mirror of the existing `tenant_id` claim path. Access token: contract-required (the gateways read it). id_token: required so NextAuth's `signIn` profile sees `account_type` (web-store/admin/console read `profile.account_type`; the userinfo `/profile` endpoint is unimplemented and returns sub-only, so the id_token is the only channel that reaches NextAuth). | **CHOSEN** — minimal, proven pattern; covers every account-bearing grant; correctly omits workload tokens; preserves the operator type through the assume-tenant exchange. |
| B. Access token only | Inject on the access token, not the id_token | **Rejected** — NextAuth consumers (web-store/admin/console `signIn` callbacks) read `account_type` from the OIDC *profile* = id_token claims (userinfo is sub-only). Access-token-only leaves the frontend `account_type` guard blind. |

### D4 — migration phasing (net-positive un-break; zero wrong-type regression)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Backward-compatible staged migration, each step independently main-GREEN; ACCEPTED is step 0.** | **Step 0 (doc-only):** this ADR PROPOSED → ACCEPTED (user-gated). **Step 1:** add `account_type` to `auth_db.credentials` (`NOT NULL DEFAULT 'CONSUMER'`), set OPERATOR on the operator-provisioned/seed credential rows; `CredentialAuthenticationProvider` puts it in the details map; `TenantClaimTokenCustomizer` injects the claim (access + id token) — D1/D2/D3. **Step 2:** account-service signup sets `CONSUMER` explicitly on the internal credential-creation call (no longer relying on the column default); operator provisioning sets `OPERATOR`. **Step 3 (verify):** extend the TASK-INT-023 GAP-backed e2e to assert `account_type=CONSUMER` in the web-store token + (optionally) un-gate one authenticated gateway path now that the 403-on-absent is resolved. | **CHOSEN** — the claim was ABSENT (gateway 403'd everything), so emitting a correct value is strictly an **un-break** (net-positive), not a regression. The only real risk is emitting the WRONG type (a CONSUMER row defaulting where an OPERATOR was meant, or vice-versa) → that group would 403; the migration's explicit operator-row correction + the provisioning-path assignment (D2) close it. Each step main-GREEN; dual-safe (column default + explicit set). |
| B. Big-bang (column + injection + provisioning + e2e in one PR) | Single atomic flip | **Rejected** — couples the schema/issuance change to the provisioning + e2e changes; harder to bisect a wrong-type 403; violates the BE-303 staged discipline. |

### D5 — userinfo endpoint (deferred)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Do NOT add `account_type` to the OIDC userinfo (`/oauth2/userinfo`) at this time — access + id token only (D3).** | The contract requires `account_type` in the **access token**; the id_token covers the NextAuth profile need (D3). The userinfo `/profile` mapper currently returns sub-only (the account-service `/internal/accounts/{id}/profile` endpoint is unimplemented); adding `account_type` there is unnecessary for any current consumer. | **CHOSEN** — least surface; no consumer needs userinfo `account_type`. (If a future consumer reads it from userinfo, a follow-up adds it — a reversible, local addition.) |
| B. Add to userinfo now | Inject in `OidcUserInfoMapper` too | **Rejected (for now)** — no consumer; the `/profile` source endpoint is itself unimplemented; speculative. |

---

## 3. Consequences

### 3.1 Hard invariants this ADR carries

- **`account_type` is per-account and immutable** (contract L131) — decided once at provisioning (D2), stored on the credential (D1), never re-derived per request or per client.
- **Orthogonal to tenant scoping** — `account_type` (who: CONSUMER/OPERATOR) is independent of `tenant_id`/`entitled_domains` (which customer). ADR-019/020 tenant decisions are unaffected; this claim sits alongside them.
- **GAP is the single issuance authority (ADR-001)** — only auth-service emits the claim; gateways consume, never derive.
- **Issuance hot-path stays account-service-independent** — the claim is read from `credentials` (D1), preserving the fail-soft / lean-stack property TASK-INT-023 relied on.
- **Existing `tenant_id` claim path unchanged** — D3 is additive structural mirroring; no change to tenant claim behaviour.

### 3.2 What this ADR does NOT do (deferred to ACCEPTED + post-ACCEPTED execution)

- No implementation: no schema migration, no customizer/provider change, no provisioning change, no e2e — all post-ACCEPTED execution tasks (§ 3.3).
- No change to the requiring contract body (the reconciling note lands at execution; the contract already mandates the claim — this ADR supplies its source).
- No userinfo change (D5).
- Does not redesign the unimplemented account-service `/internal/accounts/{id}/profile` endpoint (orthogonal; a separate concern if a consumer ever needs userinfo profile data).

### 3.3 Future-self (post-ACCEPTED execution roadmap — sketch, finalised at ACCEPTED)

1. **`TASK-…`** (GAP auth-service, D1+D3+D4 step 1) — `account_type` column on `credentials` (`NOT NULL DEFAULT 'CONSUMER'`, operator rows → OPERATOR) + `CredentialAuthenticationProvider` details + `TenantClaimTokenCustomizer` access+id-token injection (workload omitted, assume-tenant preserved). Model = **Opus** (issuance / token contract).
2. **`TASK-…`** (GAP account-service + admin/operator provisioning, D2 + D4 step 2) — signup sets `CONSUMER` explicitly; operator provisioning sets `OPERATOR`, on the internal credential-creation call. Model = **Opus / Sonnet**.
3. **`TASK-…`** (verify, D4 step 3) — extend the TASK-INT-023 GAP-backed e2e to assert `account_type=CONSUMER` in the web-store token; optionally exercise one authenticated gateway path (now un-403'd). Model = **Sonnet**.

---

## 4. Alternatives Considered

- **Do nothing (leave `account_type` absent).** Rejected — it is a required contract claim and the gateways 403 every authenticated request without it; the ecommerce authenticated API path is non-functional against a real GAP token.
- **Derive from `tenant_type`.** Rejected (D1-B) — one tenant (`ecommerce`) has both CONSUMER and OPERATOR accounts.
- **Derive from the OIDC client/scope.** Rejected (D1-C) — violates per-account immutability; the same account would get different types via different clients.
- **Store only on `account_db.accounts` and look it up via account-service at issuance.** Rejected as primary — couples the token issuance hot-path to account-service availability (breaks the fail-soft / lean-stack property); the denormalized credential copy (D1-A, mirroring `tenant_id`) avoids the remote call. (`accounts.account_type` MAY still hold the business-authority copy.)

## 5. Relationship to the contract + ADR-019/020 + ADR-001 (GAP)

| | `jwt-standard-claims.md` (contract) | ADR-019 / 020 (tenant model) | ADR-001 (GAP OIDC) |
|---|---|---|---|
| Relationship | **Supplies** the missing source for the claim the contract already requires (contract body byte-unchanged at PROPOSED) | **Orthogonal** — `account_type` (who) is independent of `tenant_id`/`entitled_domains` (which customer); sits alongside, decided/injected by the same customizer | **Realizes** the central-IdP responsibility to emit the required claim; gateways consume, never derive |

## 6. Status Transition History

| Date | Transition | Decision summary | Trigger | PR |
|---|---|---|---|---|
| 2026-06-02 | created PROPOSED | D1 = `account_type` per-account, denormalized on `auth_db.credentials.account_type` (mirror `tenant_id`; `accounts.account_type` = business authority); reject tenant_type-derivation (ecommerce has both types) + client/scope-derivation (violates per-account immutability). D2 = set at provisioning (signup→CONSUMER default, operator-provision→OPERATOR), carried on `/internal/auth/credentials`. D3 = inject on access + id token via `TenantClaimTokenCustomizer` (workload omitted; assume-tenant preserves OPERATOR); id_token required for NextAuth profile. D4 = backward-compatible staged migration (column `NOT NULL DEFAULT 'CONSUMER'` + operator-row correction → injection → provisioning → e2e verify); net-positive un-break of the gateway 403-on-absent. D5 = userinfo deferred (no consumer). | "진행 권장대로 진행" (TASK-MONO-164 — after the HARDSTOP-09 emission for implementing `account_type`; user chose the recommended ADR-first staged path over implementing the source model implicitly) | #1006 |
| 2026-06-02 | PROPOSED → ACCEPTED | D1-D5 CHOSEN-PROPOSED direction **finalised byte-unchanged** (ACCEPTED *finalises*, does not re-decide); § 1-5/7 byte-identical to PROPOSED #1006 `20f19c26`; flip = Status + History ACCEPTED clause + this row + § 1.3/§ 3.3 minimal past-tense. Authorizes the § 3.3 execution roadmap (dependency-correct base = this ACCEPTED main): credentials `account_type` column + customizer/provider injection (D1/D3) → account-service/operator provisioning (D2) → TASK-INT-023 e2e claim assertion (D4 step 3). | "진행" (TASK-MONO-165 — user-explicit intent after the offered "ADR-021 ACCEPTED 승급" next-step; sibling ADR-020→MONO-157 same-session PROPOSED→ACCEPTED) | #<this> |

> **ACCEPTED 2026-06-02 (TASK-MONO-165).** The § 3.3 execution roadmap is now **UNPAUSED**; the execution steps proceed dependency-correct from this ACCEPTED main, beginning with the `account_type` credential column + token-injection (D1/D3). Each remains a separate task; D1-D5 are finalised and not re-litigated at execution.

## 7. Provenance

- `platform/contracts/jwt-standard-claims.md` L46 (`account_type` Required CONSUMER|OPERATOR) + L119-124 (gateway rejection rules) + L132 (`X-Account-Type` injection) + L14-19 (CONSUMER=self-registered B2C / OPERATOR=company-provisioned B2B definitions).
- `platform/service-types/identity-platform.md` L131 ("set at issuance and immutable for the life of the account") + L77 (required access-token claim).
- `projects/ecommerce-microservices-platform/apps/gateway-service/.../filter/AccountTypeEnforcementFilter.java` L53-62 (reads the claim; 403s when absent — `"CONSUMER".equals(null)=false`) + `JwtHeaderEnrichmentFilter.java` L55-58 (sets `X-Account-Type` from the claim).
- `projects/global-account-platform/apps/auth-service/.../oauth2/TenantClaimTokenCustomizer.java` (injects tenant_id/tenant_type/entitled_domains; NO account_type — the gap D3 fills) + `.../security/CredentialAuthenticationProvider.java` L106-110 (details map: tenant_id/tenant_type/account_id; NO account_type — the gap D1/D3 fill).
- GAP `auth-service`/`account-service` data-model specs — no `account_type` column anywhere (the gap D1 fills).
- TASK-INT-023 — proved the GAP-backed web-store path needs only `credentials` to log in AND documented the `SKIP_GAP_E2E=1` reality that masks the gateway 403-on-absent; D4 step 3 extends its harness to assert the claim.

분석=Opus 4.8 / 구현=Opus 4.8 (cross-service token-contract claim-source architecture; D1-D5 PROPOSED-direction reasoning under HARDSTOP-09 discipline; per-account-immutable account classification; staged-child ADR pattern per ADR-014/015/017/018/019/020).
