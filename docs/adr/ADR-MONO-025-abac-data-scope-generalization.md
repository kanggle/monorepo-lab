# ADR-MONO-025 — ABAC Data-Scope Generalization (`data_scope` as a cross-domain attribute-based data-scope claim)

**Status:** ACCEPTED

**Date:** 2026-06-11

**Deciders:** platform (IAM axis)

**Supersedes / amends:** amends ADR-MONO-020 § D3 (the erp `org_scope` data-scope amendment) additively — generalises the one-off into a named cross-domain pattern.

---

## 1. Context

This ADR fills the **attribute-based (ABAC) data-scope** axis of the AWS/GCP-comparison improvement list (axis ②). Axes ③ (subscription↔IAM plane separation, ADR-MONO-023) and ① (tenant-admin delegation, ADR-MONO-024) are CLOSED; ② was triaged as "고비용 — full policy engine 피하고 org_scope ABAC 명문화 선에서 (마지막 또는 보류)". This ADR scopes ONLY the low-cost **1단계**: formalise the data-scope-as-attribute pattern that already exists for erp and extend it to a second domain. The higher-cost **2단계** (role + condition: time/IP/resource-tag) is explicitly deferred (§ D6).

### 1.1 What already exists (the raw material)

- **erp `org_scope`** (ADR-MONO-020 § D3 amendment + TASK-BE-336…339 / ERP-BE-008 / PC-FE-050): an operator's `operator_tenant_assignment.org_scope` is a per-assignment list of **department subtree-root ids**. `null` ⟺ `["*"]` ⟺ unrestricted (net-zero). It is resolved at the assume-tenant exchange and carried as a **signed JWT claim** on the domain-facing (assumed) token, then enforced in the erp read paths (read-model + masterdata 3-stage authz).
- **The claim** is written by the producer as `org_scope` (`TenantClaimTokenCustomizer.CLAIM_ORG_SCOPE = "org_scope"`).
- **A latent generalisation hook already exists**: erp's `ActorContextJwtAuthenticationConverter` already reads `extractClaim(jwt, "org_scope", "data_scope")` — i.e. it dual-reads `org_scope` AND a generic `data_scope`. The generic name was anticipated but never formalised or adopted elsewhere.

### 1.2 The gap

The "data scope = a signed attribute on the token, interpreted by the domain" pattern is **real but un-named, un-documented, and erp-only**. There is no contract a second domain can follow; nothing states the null/`["*"]` net-zero convention, the opaque-token semantics, or the deny-by-default rule as a reusable invariant. Adding data-scoping to a second domain today would mean re-deriving the pattern by reading erp's code.

### 1.3 RBAC vs ABAC framing (why this is the right shape)

RBAC (ADR-019/020/021/024) answers **"what may this operator DO"** (permissions, roles, tenant-grant scope). This axis answers **"over WHICH SLICE of a tenant's data"** — an attribute (the data-scope token set) carried on the identity, interpreted by the resource owner (the domain). That is textbook ABAC. The low-cost win is to *name and formalise the attribute*, NOT to build a policy-evaluation engine.

### 1.4 Why an ADR (HARDSTOP-09) + staged PROPOSED → ACCEPTED

A cross-domain authorization attribute + its enforcement contract is an architecture decision spanning shared specs and ≥2 projects. It follows the same staged-ADR discipline as ADR-019/020/021/023/024: a committed PROPOSED ADR, then an ACCEPTED transition (with any user gate adjustments), then per-task execution (§ 3.3).

---

## 2. Decision (PROPOSED directions — finalised at ACCEPTED)

### D1 — Canonical claim: `data_scope` (generic), `org_scope` retained as alias

The cross-domain ABAC data-scope attribute is the signed claim **`data_scope`**: a JSON array of **opaque scope tokens** (strings the producer copies verbatim from `operator_tenant_assignment.org_scope`; it does NOT interpret them). `org_scope` is kept as a **backward-compatible alias** — consumers MUST dual-read (`data_scope` then `org_scope`), exactly as erp already does. **Net-zero invariant (verified against erp's `RoleScopeAuthorizationAdapter`):** `["*"]` ⟺ unrestricted; the producer maps an UNSCOPED assignment (`org_scope` NULL) to `["*"]`, so an un-scoped operator carries `["*"]` and sees everything (net-zero). An **empty / absent** claim is NOT unrestricted — it is **fail-closed (deny)** (a correctly minted token always carries at least `["*"]`; empty means deny, never allow-all).

- **D1-A (chosen-PROPOSED)** — keep the storage column + producer claim name `org_scope`; introduce `data_scope` only as the canonical *consumer-facing* name + alias. Zero producer change (see D5).
- **D1-B (rejected)** — hard-rename `org_scope` → `data_scope` everywhere (column + producer + erp). Breaking, churny, touches the token producer; no benefit over the alias.

### D2 — Per-domain interpretation contract

The scope tokens are **opaque to the producer and to IAM**; each consuming domain owns their meaning, published in a new shared contract doc (`platform/abac-data-scope.md`):

- erp → department subtree-root ids (existing).
- wms → warehouse (and/or zone) ids (§ D3).
- finance → accounting-unit ids (future).

Enforcement invariants (uniform across domains):
1. **Unrestricted**: token set contains `"*"` → no data-scope filter (net-zero; the producer emits `["*"]` for unscoped assignments).
2. **Scoped**: a non-empty token set WITHOUT `"*"` → the domain filters its query/results to rows reachable from those tokens; **deny-by-default** for anything outside.
2b. **Empty / absent**: no tokens → **fail-closed deny** (NOT unrestricted) — defensive; a real token always carries at least `["*"]`.
3. The data-scope filter is **orthogonal to and composed with** RBAC (permission) and tenant-scope (which tenant) — all three must pass.

### D3 — First extension domain: wms warehouse-scope

The first generalisation case is **wms**: a `data_scope` of warehouse ids restricts an operator's wms reads to those warehouses (and their child zones/locations). Proves the pattern is reusable beyond erp with no IAM change (wms reads the same claim, applies its own interpretation).

- **D3-A (CHOSEN, ACCEPTED 2026-06-11)** — wms warehouse-scope first (most data-rich domain; user-selected at the ACCEPTED gate).
- **D3-B (rejected)** — finance accounting-unit first.

### D4 — Net-zero migration

- erp is byte-unchanged (it already dual-reads; the producer keeps emitting `org_scope`).
- The producer is **unchanged** (D5) → no token-pipeline churn, no touch to the auth-service token customizer.
- The new domain's enforcement is **opt-in** and **net-zero by default**: an unscoped operator carries `["*"]` (producer maps NULL → `["*"]`), so the new domain behaves exactly as today (unrestricted). The filter only bites once an operator is assigned a non-`*` scope. (An empty/absent claim fail-closes to deny — defensive, not the net-zero path.)

### D5 — Producer unchanged; consumers dual-read

The assume-tenant token producer continues to emit the existing `org_scope` claim (no change to `TenantClaimTokenCustomizer`). The canonicalisation to `data_scope` is realised purely on the **consumer** side: a shared `com.example.security.jwt.AbacDataScope` reader (`libs/java-security`) codifies the dual-read + the § D1/D2 semantics so new domains (wms/finance) reuse one implementation. erp's existing inline reader is the behavioural reference the shared helper matches; **re-pointing erp onto the shared helper is OPTIONAL/deferred** (erp already works and is verified — a re-point adds regression risk for no behaviour change), so the shared helper is introduced for new consumers first. This keeps the change additive and avoids the token-pipeline blast radius. (A future producer migration to emit `data_scope` is possible but unnecessary for 1단계.)

### D6 — 2단계 (role + condition) explicitly DEFERRED

Conditional policy — attaching a condition (time-of-day / source-IP / resource-tag) to a role grant, an AWS-IAM-Condition miniature — is **out of scope** for this ADR and deferred to a future ADR if demand appears. This ADR does **not** introduce a policy language, a condition-evaluation engine, or any new grant-time condition field. The data-scope attribute (D1-D5) is the entire 1단계.

### D7 — Staged execution (zero-regression)

1. This ADR PROPOSED → ACCEPTED (doc-only).
2. Shared contract doc `platform/abac-data-scope.md` + a shared `AbacDataScope` reader (`libs/java-security`) for new consumers (erp re-point optional/deferred).
3. wms data-scope enforcement (the chosen D3 domain) + IT proving scoped/unrestricted/deny-by-default.
4. (optional, future) producer emits `data_scope`; (optional, future) 2단계 conditions — each its own ADR/task.

---

## 3. Scope

### 3.1 Hard invariants this ADR carries

- The data-scope attribute is **advisory data-filtering, never privilege escalation**: it can only NARROW what an already-RBAC-authorised, tenant-authorised operator sees. It can never grant access.
- **Deny-by-default** when scoped: an unknown/empty interpretation yields no rows, never all rows.
- **Net-zero**: `["*"]`/absent ⟺ unrestricted; every existing read path is byte-identical until a non-`*` scope is assigned.
- Scope tokens are **opaque** to IAM/producer; only the owning domain interprets them.

### 3.2 What this ADR does NOT do

- No full policy engine / policy language (the AWS IAM policy-document equivalent) — rejected as 고비용 over-engineering.
- No 2단계 conditions (time/IP/resource-tag) — deferred (§ D6).
- No producer/token-customizer change (§ D5).
- No new grant surface — data-scope continues to be set via the existing admin `org_scope` management endpoint (BE-339); only its *reach* generalises.

### 3.3 Future-self (post-ACCEPTED execution roadmap — sketch, finalised at ACCEPTED)

0. **`TASK-MONO-212` (this PROPOSED) + `TASK-MONO-2xx` (ACCEPTED transition)** — doc-only. Model = Opus (analysis) / the ACCEPTED flip is doc-only.
1. **`TASK-MONO-214` (DONE)** — `platform/abac-data-scope.md` contract + shared `com.example.security.jwt.AbacDataScope` reader (`libs/java-security`) codifying the dual-read + § D1/D2 semantics, with unit tests. erp re-point optional/deferred (erp's verified inline reader is the reference). Also corrected the D1/D2/D4 net-zero wording to the precise semantics (`["*"]`=unrestricted via producer NULL→`["*"]`; empty/absent=fail-closed deny), verified against erp's `RoleScopeAuthorizationAdapter`. Model = Sonnet→Opus (doc + lib + accuracy audit).
2. **`TASK-<wms>-BE-xxx`** — wms warehouse data-scope enforcement on the wms read paths + opt-in net-zero + Testcontainers IT (scoped / unrestricted / deny-by-default). Model = Opus (authorization enforcement; security-critical).
3. **(optional)** federation-e2e proof that a warehouse-scoped operator sees only its warehouses while an unscoped operator sees all — reuses the MONO-207/210 dedicated-tenant harness.

No producer change and no 2단계 are scoped here.

---

## 4. Alternatives Considered

- **Full ABAC policy engine (policy documents + condition evaluation).** Rejected — 고비용; the portfolio needs the *pattern demonstrated*, not a rules engine. The opaque-token + per-domain-interpretation shape gives the ABAC story at a fraction of the cost.
- **Hard-rename `org_scope` → `data_scope` (column + producer + consumers).** Rejected (D1-B) — breaking + churny + touches the token producer; the dual-read alias achieves the canonical name with zero breakage (erp already dual-reads).
- **Producer emits `data_scope` now.** Rejected for 1단계 (D5) — unnecessary token-pipeline change; consumers dual-reading the existing `org_scope` is net-zero and sufficient. Deferred as an optional later step.
- **Per-domain bespoke scoping (no shared contract).** Rejected — that is the status quo (erp-only, re-derive per domain); the whole value of this ADR is the named, documented, reusable invariant.
- **Do ② now as the full 1+2단계.** Rejected — 2단계 (conditions) is independent and higher-cost; bundling would inflate blast radius. Deferred (§ D6).

---

## 5. Relationship to ADR-MONO-019 / 020 / 021 / 023 / 024

| | ADR-019/020/021 | ADR-023 | ADR-024 | **ADR-025 (this)** |
|---|---|---|---|---|
| Axis | Customer-tenant + operator N:M assignment + account_type | Entitlement↔IAM plane separation | Tenant-admin delegation (RBAC who-may-administer) | **ABAC data-scope (which data slice)** |
| Question | who / which tenant / what type | which plane | what may you administer | **over which data** |
| Relationship | **builds on** ADR-020 § D3 (`org_scope` lived on `operator_tenant_assignment`) — generalises that one-off | orthogonal | orthogonal (RBAC vs ABAC are complementary axes) | — |

ADR-024's `TenantScopeGuard` (admin-grant tenant confinement) and this ADR's data-scope are **distinct, composed dimensions**: the former bounds *which tenant* an admin may manage; the latter bounds *which data rows* an operator may read within a tenant. Both compose with RBAC permissions.

---

## 6. Status Transition History

Append-only.

| Date | Transition | Decision direction | User intent quote | PR(s) |
|---|---|---|---|---|
| 2026-06-11 | created PROPOSED | D1 = canonical `data_scope` claim + `org_scope` alias (consumers dual-read; absent/null/`["*"]`=unrestricted net-zero); D2 = per-domain opaque-token interpretation contract (`platform/abac-data-scope.md`) with deny-by-default; D3 = first extension = wms warehouse-scope; D4 = net-zero (erp byte-unchanged, new domain opt-in); D5 = producer unchanged, consumer-side canonicalisation (no token-customizer touch); D6 = 2단계 role+condition DEFERRED (no policy engine); D7 = staged (contract+shared dual-read → wms enforcement → optional producer/2단계) | "③ 먼저 / ① 두번째 / ② 마지막·보류 … ② 1단계(저비용): 이미 있는 org_scope 를 ABAC 일반화의 첫 사례로 정식화, '데이터 스코프 = JWT claim attribute' 패턴을 다른 도메인으로 확장; 2단계는 필요시 역할+조건식; 풀 정책 언어 회피" → "진행" (TASK-MONO-212 — after ③/① closure the user authorised axis ② 1단계, ADR-first per the ADR-019…024 pattern) | #1268 (TASK-MONO-212) |
| 2026-06-11 | PROPOSED → ACCEPTED | D1-D2, D4-D7 directions **finalised unchanged** from PROPOSED #1268 squash `1484c611`; **D3 finalised = wms warehouse-scope** (the first generalisation domain). Authorises the § 3.3 execution roadmap (dependency-correct base = this ACCEPTED main): `platform/abac-data-scope.md` contract + shared dual-read utility (erp net-zero re-point) → wms warehouse data-scope enforcement + IT → optional federation-e2e proof. Same one-off Meta-policy category as the sibling ACCEPTED transitions (ADR-023/024). | "wms 창고 스코프 (권장)" + "ACCEPTED 후 구현까지 진행 (권장)" (TASK-MONO-213 — user selected wms as the first data-scope domain and authorised ACCEPTED + execution at the gate) | #1270 (TASK-MONO-213) |
| 2026-06-11 | accuracy correction (post-ACCEPTED, § 3.3 step 1) | D1/D2/D4 net-zero wording corrected to the verified semantics: `["*"]`=unrestricted (producer maps unscoped assignment NULL→`["*"]`); **empty/absent=fail-closed deny, NOT unrestricted** (confirmed against erp's `RoleScopeAuthorizationAdapter` lines 92-102). D5/D7 erp re-point downgraded to OPTIONAL/deferred (regression-risk avoidance); the shared `AbacDataScope` reader is introduced for new consumers. No decision reversal — wording accuracy + the step-1 deliverable. | (execution of the ACCEPTED roadmap) | #<this> (TASK-MONO-214) |
