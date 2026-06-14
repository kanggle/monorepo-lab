# ADR-MONO-033 — Roles-issuance resolution model (where the `roles` claim's values come from at token-issue time; ADR-032 D5 step 2 mechanics)

**Status:** PROPOSED

**History:** PROPOSED 2026-06-14 (TASK-MONO-257 — records the **source + aud-scoping + failure-policy** of the `roles` claim at issuance, the mechanics that ADR-MONO-032 D5 **step 2** ("roles-only issuance") requires but that ADR-032 D6-A left underspecified. ADR-032 D6-A asserted the role set is "assembled from the account's grants (RBAC store, ADR-002 lineage + ADR-020 assignments)"; **investigation at the dependency-correct base (`origin/main` `632f88206`) found that assumption is factually incomplete** — there is no store that maps an identity to its *domain-platform* roles (`WMS_OPERATOR`, `OUTBOUND_MANAGER`, …), the `roles` claim is **never emitted today** (`TenantClaimTokenCustomizer` injects `tenant_id`/`tenant_type`/`entitled_domains`/`account_type`/`org_scope` only), and three disjoint identity/role stores exist (§ 1.1). Choosing the roles source during implementation would silently bake the issuance model (HARDSTOP-09). **CHOSEN-PROPOSED** direction per the reasoning below (S1 = source-faithful + aud-default seed, option A; convention-only B + full per-platform RBAC store C rejected). Doc-only; ACCEPTED + implementation are separate user-explicit-intent-gated tasks (sibling staged-child pattern, ADR-019/020/021/023/024/032). **Self-ACCEPT prohibited.**)

**Parent:** [ADR-MONO-032](ADR-MONO-032-unified-identity-roles-model.md) (ACCEPTED 2026-06-14) — the unified-identity model (single account → `roles` set as the sole authorization axis). ADR-032 decided **D1-D6** (the model); ADR-033 resolves **one underspecified execution point inside D5 step 2 + D6-A**: the concrete *source* and *aud-scoping* of the `roles` claim values. ADR-033 does **not** re-decide D1-D6 — it fills the gap D6-A glossed. The user selected the S1 direction explicitly (2026-06-14, AskUserQuestion "Roles 소스" = "소스-충실 + aud 기본값 시드", option A) after the three-store finding was surfaced.

**Decision driver:** ADR-032 D5 step 2 says the IdP must "emit roles-only tokens … consumer capability seeded as `CUSTOMER`/`FAN` roles", and the step-1 dual-read gateways (TASK-MONO-256, `632f88206`) already check role-presence (`AccountTypeEnforcementFilter`: `hasRole(roles,"ADMIN") || account_type==OPERATOR`; wms: non-empty roles OR OPERATOR). But **no code path populates `roles`**, and **no store maps an identity → its per-platform domain roles**. The natural consumer source (`account-service.account_roles`) exists but exposes **no read surface**; the operator-side store (`admin-service.admin_operator_roles`) holds **admin-console permission roles** (`SUPER_ADMIN`/`SUPPORT_READONLY`/`TENANT_ADMIN`/…), a different namespace from the contract's `WMS_OPERATOR`-style domain roles, and carries **no aud dimension**. The login path itself (`CredentialAuthenticationProvider`) reads a **third** store (`auth_db.credentials`, no roles). Resolving "which store, scoped how, failing how" is therefore a HARDSTOP-09 architecture decision that must precede the issuance code.

**Related:** [`platform/contracts/jwt-standard-claims.md`](../../platform/contracts/jwt-standard-claims.md) (§ Standard Claims `roles` Required + aud-scoped; § Role Strategy; the **target** the issuance must satisfy — unchanged by this ADR, which decides *how the IdP sources* the claim, not its shape), [ADR-MONO-032](ADR-MONO-032-unified-identity-roles-model.md) D2/D5/D6, [ADR-MONO-019](ADR-MONO-019-platform-console-customer-tenant-model.md) `entitled_domains` keystone (§ 3.3) (the issuance-time cross-service lookup + fail-soft pattern S4/S5 mirror), [ADR-MONO-020](ADR-MONO-020-operator-multitenant-assignment.md) assume-tenant exchange (the operator-token augmentation S4 extends), `projects/iam-platform/.../oauth2/TenantClaimTokenCustomizer.java` (the issuance locus S4 amends), `projects/iam-platform/.../account-service/.../AccountRoleJpaEntity.java` + `AccountRoleController.java` (the consumer role store S2 reads from — has mutation EPs, no read EP), `projects/iam-platform/.../admin-service/.../admin_operator_roles` (the admin-console RBAC store S2/S3 deliberately keeps out of the domain `roles` claim).

---

## 1. Context

### 1.1 The three disjoint identity/role stores (the finding)

| Store | Owner | What it holds | aud dimension | Read surface for issuance |
|---|---|---|---|---|
| `auth_db.credentials` | auth-service | login credential + `account_type` (CONSUMER\|OPERATOR) + `account_id`. **No roles.** | — | login path reads it; carries `account_id` into the principal details map |
| `account-service.account_roles` | account-service | per-account role grants, free-form `^[A-Z][A-Z0-9_]*$` names (`CUSTOMER`, `WAREHOUSE_ADMIN`, `INBOUND_OPERATOR`, …), tenant-scoped | **none** (flat per account+tenant) | **mutation only** (`roles:add`/`roles:remove`); **no read EP** |
| `admin-service.admin_operator_roles` | admin-service | operator → admin-console RBAC role (`SUPER_ADMIN`/`SUPPORT_READONLY`/`SUPPORT_LOCK`/`SECURITY_ANALYST`/`TENANT_ADMIN`/`TENANT_BILLING_ADMIN`), tenant-scoped | **none** | internal `GET /internal/operator-assignments/check` (returns *tenant assignment* + org_scope, **not roles**) |

Two consequences:

1. **The contract's domain-platform roles have no source.** `jwt-standard-claims.md` exemplifies `WMS_OPERATOR`, `OUTBOUND_MANAGER`, `SCM_OPERATOR`. No store maps an identity → those. `account_roles` *can* hold such names (its regex permits `WAREHOUSE_ADMIN`) but is not systematically populated per platform; `admin_operator_roles` is a **different role namespace** (admin-console permissions that gate `account.lock`/`audit.read` inside admin-service, **not** wms/scm domain APIs).
2. **`roles` is never emitted.** Every current gateway admission passes via the legacy `account_type` leg of the step-1 dual-read; the role leg is dead until step 2 fills it. So step 2 is **net-new issuance**, not a toggle.

### 1.2 What step 2 must produce (the gateways' contract)

For roles-only admission to work, issuance must emit (per `aud`):

| aud / surface | required role (step-1 gateway check) |
|---|---|
| ecommerce consumer (`/api/**`) | `CUSTOMER` |
| ecommerce admin (`/api/admin/**`) | `ADMIN` |
| fan | a `FAN`-family role (`FAN`) |
| wms | any operator role (non-empty; convention `WMS_OPERATOR`) |
| scm / erp | backend-only (no gateway role check today); convention `SCM_OPERATOR` / `ERP_OPERATOR` |

### 1.3 The underspecified point (HARDSTOP-09)

"Source the role set from the account's grants" (D6-A) is underspecified in four ways, each baking the issuance model if chosen in code: **(a)** which store is authoritative for the JWT `roles` claim; **(b)** how a *flat, aud-less* store is scoped to a single-`aud` token without breaking aud-scoped least privilege (D2-A/D4); **(c)** what happens when an identity has no stored role for the requested surface; **(d)** how the issuance-time lookup fails (fail-soft like `entitled_domains`, or fail-closed like the assume-tenant assignment gate). This ADR resolves all four.

---

## 2. Decision

> Direction is **CHOSEN-PROPOSED**; finalised at ACCEPTED. **No code/contract change in this ADR.** The `jwt-standard-claims.md` claim *shape* is unchanged — this ADR decides only how the IdP *sources* the claim, which is not a contract concern (the contract is consumer-facing).

### S1 — roles-resolution strategy (the crux): source-faithful + aud-default seed

| Option | Mechanics | Verdict |
|---|---|---|
| **A. The existing per-account role store (`account_roles`) is the authoritative source for the JWT `roles` claim; at issuance, auth-service reads it (new internal read EP), scopes it to the token's `aud` (S3), and **seeds the aud-default role when the scoped set is empty** (`CUSTOMER`/`FAN`/`{AUD}_OPERATOR`/`ADMIN`).** | Reuses the store that already exists and already has free-form role names + mutation EPs; consumers get `CUSTOMER`/`FAN` for free via the seed; richer per-account grants (an account explicitly granted `OUTBOUND_MANAGER`) flow through verbatim; degrades to the seed (never to "no access by accident"). | **CHOSEN** — faithful to D6-A's "from the account's grants" *as far as a real store exists*, while the aud-default seed covers the gap (no domain-platform-role store) without blocking step 2; minimal net-new (one read EP + one customizer leg); extensible to a richer store (S3 deferred aud column / option C) later without re-issuance redesign. |
| B. Convention-only derivation (no store read): consumer `aud`→`CUSTOMER`/`FAN`; operator provisioning-signal (`account_type==OPERATOR`)→`{AUD}_OPERATOR`/`ADMIN`. | Zero remote call; simplest; effectively a 1:1 re-expression of `account_type` as roles. | **Rejected** — leaves the real `account_roles` store (and any explicitly-granted role like `OUTBOUND_MANAGER`) unused; coarse (one role per surface, no multi-role); makes `roles` a derived shadow of the very `account_type` axis ADR-032 removes (the claim would carry no information `account_type` didn't). The seed in A already gives B's simplicity as the *fallback*, so A strictly dominates. |
| C. Build a new per-platform operator RBAC store (operator × aud × roles[]) + provisioning surface + role catalog. | Fully faithful to the contract's multi-role/multi-platform examples. | **Rejected for step 2 (recorded as the S3 future extension)** — a net-new subsystem (table + provisioning API + catalog) heavier than ADR-032 **step 3** (account/credential unify) which it would logically precede; not required to make roles-only admission GREEN (the gateways need role-*presence*, which A delivers). Defer until a real multi-role-per-platform need exists; A's store is forward-compatible with adding the aud dimension then. |

### S2 — authoritative store + read surface

- **`account-service.account_roles` is the authoritative source for the JWT `roles` claim.** auth-service resolves roles by `account_id` (already carried in the principal details map by `CredentialAuthenticationProvider`) at issuance.
- **New internal read EP on account-service**: `GET /internal/tenants/{tenantId}/accounts/{accountId}/roles` → `{ "roles": ["CUSTOMER", …] }`, mounted on the `/internal/**` chain (IAM `client_credentials` Bearer, fail-closed auth — same chain as the existing mutation EPs and the auth→admin edge). Read-only, no audit row.
- **`admin-service.admin_operator_roles` stays out of the domain `roles` claim.** Those admin-console permission roles continue to gate admin-service/platform-console endpoints *inside admin-service* (RBAC, ADR-002 lineage); they are a different namespace and MUST NOT be flattened into wms/scm/ecommerce `roles`. (The ecommerce **`ADMIN`** surface role is produced by the S3 seed for operator-provisioned identities, not by copying an admin-console grant — keeping the two role spaces disjoint.)

### S3 — aud-scoping of a flat store + the aud-default seed table

- **aud-scoping (v1, no schema change):** a role is emitted into an `aud` token iff it belongs to that `aud` by a **fixed role→aud convention map** (e.g. `CUSTOMER`→ecommerce-consumer, `ADMIN`→ecommerce-admin, `FAN`/`PREMIUM_MEMBER`→fan, `WMS_*`→wms, `SCM_*`→scm, `ERP_*`→erp). This preserves aud-scoped least privilege (D2-A/D4) from a flat store without flattening every platform's roles into one token.
- **aud-default seed** (applied when the aud-scoped set from `account_roles` is empty), keyed on the identity's provisioning class (consumer vs operator, derivable from `account_type` during the migration window, later from the presence of any operator-facing grant):

  | aud / surface | consumer-provisioned | operator-provisioned |
  |---|---|---|
  | ecommerce `/api/**` | `["CUSTOMER"]` | — (operator on consumer path → empty → gateway 403, correct) |
  | ecommerce `/api/admin/**` | — | `["ADMIN"]` |
  | fan | `["FAN"]` | `["FAN"]` |
  | wms / scm / erp | — | `["WMS_OPERATOR"]` / `["SCM_OPERATOR"]` / `["ERP_OPERATOR"]` |

- **Deferred (S3 future / option C):** an explicit `aud`/`platform` column on `account_roles` (replacing the convention map with a stored dimension) + a per-platform role catalog. Lands with or after ADR-032 step 3 (account unify), not in step 2.

### S4 — resolution locus

- **Base token (`authorization_code` / `refresh_token`):** `TenantClaimTokenCustomizer` gains a `roles` leg, mirroring `populateEntitledDomains` — resolve by `account_id` + token `aud`, scope (S3), seed (S3), inject `roles`. **Recursion-safe:** never resolved on `client_credentials` (a workload is not an identity; that grant is what mints the Bearer used to call account-service — identical guard to `entitled_domains`).
- **Assume-tenant token (`token_exchange`, ADR-020):** carries the operator's roles for the **selected** tenant's `aud` (augments the existing `customizeForAssumeTenant`), least-privilege (selected tenant only, no union), consistent with how `entitled_domains`/`org_scope` are already handled there.

### S5 — failure policy: fail-soft (NOT fail-closed)

- The `account_roles` read is **fail-soft**, mirroring the `entitled_domains` keystone (ADR-019): account-service down / circuit-open / timeout / empty → fall through to the **aud-default seed** (S3); if even the seed is inapplicable, omit `roles` (emit `[]`). **Issuance never fails on the roles lookup.**
- This is safe precisely because `roles` is now the *sole* authorization axis: an empty/seed-only roles set is **rejected at the gateway** (positive check against a closed role set), so a degraded token is *less*-privileged, never *over*-privileged — net-zero, fail-toward-denied **at the gateway**, never fail-closed **at issuance**. This is the **opposite** of the assume-tenant assignment gate (ADR-020, fail-CLOSED), and deliberately so: that gate is an *authorization* decision made at issuance; this is *claim population* whose authorization is deferred to the gateway.

### S6 — migration phasing (additive within ADR-032 D5 step 2; net-zero)

1. **account-service internal roles read EP** (additive; no caller yet → net-zero).
2. **auth-service base-token `roles` injection** (S4) behind the existing dual-read: a token that previously passed via the `account_type` leg now *also* carries `roles`; the gateway's role leg starts admitting; the `account_type` leg stays tolerant (ADR-032 D5, removed only at step 4). **No token loses access.**
3. **assume-tenant `roles` augmentation** (S4).
4. **(deferred)** S3 aud column + provisioning seeding (option C lineage).
5. **e2e** folds into ADR-032 D5 **step 5** (one account, both tokens, one session — now also asserting the `roles` payload).

Each sub-step is independently main-GREEN and reversible until ADR-032 step 4 drops the `account_type` leg.

---

## 3. Consequences

### 3.1 Invariants

- **`roles` becomes a populated, aud-scoped claim sourced from a real store** (`account_roles`), with a deterministic aud-default seed — the contract's `roles` Required claim is finally satisfiable.
- **The two role namespaces stay disjoint**: domain-platform roles (JWT `roles`, gateway admission) vs admin-console permission roles (`admin_operator_roles`, admin-service-internal RBAC). ADR-033 does not merge them.
- **Per-token least privilege preserved** (S3 aud-scoping; assume-tenant selected-tenant-only).
- **Issuance availability is independent of account-service** (S5 fail-soft) — net-zero vs today's `account_type`-only admission.
- **IAM remains the sole issuance authority** (ADR-001); gateways consume `roles`, never derive.

### 3.2 What this ADR does NOT do (deferred)

- No code/contract change (PROPOSED; HARDSTOP-09 remediation = decide first, PAUSE until ACCEPTED).
- No `account_roles` aud column / per-platform catalog / new operator RBAC store (S3 deferred / option C — with or after ADR-032 step 3).
- No folding of `admin_operator_roles` into the JWT `roles` claim.
- No `PREMIUM_MEMBER` membership-derived role (needs a fan-platform membership lookup — separate, out of step 2 scope).
- Does not change ADR-032 D1-D6, the contract shape, or RBAC/ABAC/access-condition semantics (ADR-002/025/026).

### 3.3 Execution roadmap (post-ACCEPTED; sketch, finalised at ACCEPTED)

1. **`TASK-…`** (S2) — account-service internal roles read EP (`GET /internal/tenants/{tid}/accounts/{aid}/roles`) + auth-service `AccountServicePort` extension. Model = **Sonnet** (additive read EP + port).
2. **`TASK-…`** (S4 base) — `TenantClaimTokenCustomizer` `roles` injection (resolve + S3 scope/seed + S5 fail-soft), recursion-safe. Model = **Opus** (issuance security path).
3. **`TASK-…`** (S4 assume-tenant) — operator selected-tenant `roles` augmentation. Model = **Opus**.
4. **(deferred)** S3 aud column + provisioning seeding (option C lineage).
5. ADR-032 **step 5** e2e extended to assert the `roles` payload (one account, both tokens).

---

## 4. Alternatives Considered

- **Convention-only roles (S1-B).** Rejected — `roles` would carry no information `account_type` didn't; ignores the real `account_roles` store; the A seed already provides B's behavior as the fallback.
- **New per-platform operator RBAC store now (S1-C).** Rejected for step 2 — a subsystem heavier than ADR-032 step 3; not needed for role-presence admission; A is forward-compatible with adding it (S3 deferred).
- **Fold `admin_operator_roles` into the JWT `roles` claim.** Rejected — wrong namespace (admin-console permissions, not domain roles); would conflate the admin-service-internal RBAC with gateway admission and leak admin-console role names into wms/scm tokens.
- **Fail-closed issuance on the roles lookup.** Rejected — `roles`-as-sole-axis already fails-toward-denied at the gateway; failing closed at issuance would make token minting depend on account-service availability for no safety gain (violates the ADR-019 keystone's availability separation).
- **Denormalize roles onto `auth_db.credentials`** (like `account_type`). Rejected — re-introduces the sync/consistency problem ADR-032 D1-B warned against (a second copy of the authz truth to keep consistent).

## 5. Relationship to ADR-032 + the contract + the RBAC family

| | ADR-MONO-032 | `jwt-standard-claims.md` | ADR-019 keystone | `admin_operator_roles` (ADR-002/024) |
|---|---|---|---|---|
| Relationship | **Child** — resolves the D5-step-2 / D6-A roles-*source* gap; does not re-decide D1-D6 | **Satisfies** — makes the Required aud-scoped `roles` claim sourceable; claim *shape* unchanged (no contract amend) | **Mirrors** — issuance-time cross-service lookup + fail-soft + recursion guard (S4/S5) | **Keeps disjoint** — admin-console RBAC stays admin-service-internal; not in the JWT `roles` claim |

## 6. Status Transition History

| Date | Transition | Decision summary | Trigger | PR |
|---|---|---|---|---|
| 2026-06-14 | created PROPOSED | S1 = source-faithful + aud-default seed (`account_roles` authoritative; reject convention-only B + new per-platform RBAC store C). S2 = `account_roles` source + new internal read EP; `admin_operator_roles` kept disjoint. S3 = role→aud convention scoping + aud-default seed table (explicit aud column deferred). S4 = resolve in `TenantClaimTokenCustomizer` base token (recursion-safe) + assume-tenant augmentation. S5 = fail-soft (net-zero; gateway is the deny point, not issuance). S6 = additive within ADR-032 D5 step 2, each sub-step main-GREEN. | User-explicit selection of the source-faithful direction (2026-06-14, AskUserQuestion "Roles 소스" = option A) after the three-store / no-domain-role-source finding was surfaced at base `632f88206` | #<this> |

> **PROPOSED 2026-06-14 (TASK-MONO-257).** ACCEPTED transition + execution (§ 3.3) are separate user-explicit-intent-gated tasks (sibling staged-child pattern, ADR-019/020/021/023/024/032). **Self-ACCEPT prohibited.** D1-D6 of ADR-032 are not re-litigated here — ADR-033 only fills the roles-source gap.

## 7. Provenance

- `origin/main` `632f88206` investigation: `TenantClaimTokenCustomizer.java` (no `roles` leg — claims injected: tenant_id/tenant_type/entitled_domains/account_type/org_scope), `CredentialAuthenticationProvider.java` (reads `auth_db.credentials`, carries `account_id` in details; no roles), `account-service` `AccountRoleJpaEntity`/`AccountRoleName` (regex `^[A-Z][A-Z0-9_]*$`)/`AccountRoleController` (mutation EPs `roles:add`/`roles:remove`, no read EP), `admin-service` `admin_operator_roles` (SUPER_ADMIN/SUPPORT_*/SECURITY_ANALYST/TENANT_ADMIN/TENANT_BILLING_ADMIN; `data-model.md`), ecommerce `AccountTypeEnforcementFilter.java` L62-65 (dual-read role leg `hasRole(roles,"ADMIN")||OPERATOR`).
- ADR-MONO-032 D2-A (aud-scoped roles) / D5 step 2 (roles-only issuance, "seed CUSTOMER/FAN") / D6-A (the "from the account's grants" assertion this ADR finds incomplete and resolves).
- ADR-MONO-019 `entitled_domains` keystone (`populateEntitledDomains` fail-soft + recursion guard — the S4/S5 template).
- `platform/contracts/jwt-standard-claims.md` § Standard Claims (`roles` Required, aud-scoped) + § Role Strategy (platform-defined catalog, multi-role permitted).

분석=Opus 4.8 / 구현=Opus 4.8 (issuance-security mechanics under HARDSTOP-09; child of the ADR-032 unified-identity model; resolves the roles-source gap D6-A left at the dependency-correct base; staged-child ADR pattern).
