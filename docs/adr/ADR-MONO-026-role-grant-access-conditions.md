# ADR-MONO-026 — Role-Grant Access Conditions (closed-enum condition clauses — the 2단계 of axis ②)

**Status:** ACCEPTED

**Date:** 2026-06-11

**Deciders:** platform (IAM axis)

**Supersedes / amends:** lifts the **ADR-MONO-025 § D6** deferral of 2단계 (role + condition: time/IP/resource-tag). Does NOT change ADR-025's 1단계 data-scope attribute — this ADR adds an orthogonal, independently-deferred axis that ADR-025 § D6 explicitly routed to "a future ADR if demand appears". This is that future ADR.

---

## 1. Context

This ADR fills the **conditional-policy** half of axis ② of the AWS/GCP-comparison improvement list — the **2단계** that ADR-MONO-025 (1단계, data-scope attribute) deliberately deferred. Axes ③ (subscription↔IAM plane separation, ADR-MONO-023), ① (tenant-admin delegation, ADR-MONO-024) and ② 1단계 (data-scope, ADR-MONO-025) are CLOSED.

Where 1단계 answered **"over WHICH SLICE of a tenant's data"** (data-scope), 2단계 answers **"under WHICH CIRCUMSTANCES is a permission live"** — gating an already-granted permission by *time-of-day*, *source-IP*, or *resource-tag*. It is a deliberately-bounded **AWS-IAM-`Condition` miniature**.

### 1.1 The hard boundary (set before this ADR, carried verbatim)

The triage that produced ADR-025 fixed the shape of 2단계 in advance, and a follow-up design discussion sharpened it:

- **NOT a full policy engine / policy language.** No policy documents, no Rego/CEL, no runtime-registrable condition SPI, no boolean policy grammar. That is the 고비용 over-engineering ADR-025 § 4 already rejected.
- **A closed, code-defined set of condition types** — "역할 + 조건식 한 겹", AWS-IAM-`Condition`의 축소판. Adding a condition type is a code change, never a data/config change.
- **Restriction-only (fail-safe).** A condition can only *gate* (deny when unmet) a permission the operator already holds; it can **never grant** one. This was confirmed in design: subtractive conditions are fail-safe (eval failure ⇒ deny ⇒ safe); additive/elevation (break-glass) is a different, riskier shape owned by other mechanisms (§ 3.2).
- **Same blueprint as 1단계**: a shared contract (`platform/access-conditions.md`) + a shared evaluator (`com.example.security.jwt.AccessCondition`, sibling to `AbacDataScope`) + a single pilot domain — net-zero, opt-in.

### 1.2 What already exists (the raw material)

- **The composed-authorization stack** at every domain's enforcement seam: RBAC permission (ADR-019/024) → tenant-scope → data-scope (ADR-025). A condition gate is a natural **4th orthogonal check** at that same seam.
- **The assume-tenant token + producer** (`TenantClaimTokenCustomizer`, currently emits `org_scope`; consumers dual-read `data_scope`). A per-grant condition spec *could* ride as a sibling signed claim (the D3-A carrier), exactly as data-scope rides today.
- **`libs/java-security`** already hosts `AbacDataScope` — the framework-agnostic shared-evaluator pattern this ADR mirrors.
- **Request context** available at enforcement: source IP (gateway `X-Forwarded-For` / `RemoteAddr`) and the server clock (request time) — the inputs `SOURCE_IP` / `TIME_WINDOW` need, with no new infrastructure.

### 1.3 The gap

RBAC today is **all-or-nothing per permission**: a permission held is a permission usable anytime, from anywhere, on any row. There is no way to express "this grant is live only 09–18", "only from the corporate CIDR", or "not on rows tagged `confidential`". The conditional dimension is entirely absent, and there is no contract a domain could follow to add one.

### 1.4 Why an ADR (HARDSTOP-09) + staged PROPOSED → ACCEPTED

A new cross-domain authorization dimension + its enforcement contract is an architecture decision spanning shared specs and ≥1 project, and it lifts a deferral recorded in a prior ACCEPTED ADR (025 § D6). It follows the same staged-ADR discipline as ADR-019/020/021/023/024/025: a committed PROPOSED ADR, then an ACCEPTED transition (with the gate fixing the open options), then per-task execution (§ 3.3).

---

## 2. Decision (PROPOSED directions — finalised at ACCEPTED)

### D1 — Closed condition enum; NOT a policy language

The condition vocabulary is a **fixed, code-defined enum**:

| Type | Input | Evaluates |
|---|---|---|
| `TIME_WINDOW` | request time + a declared zone | request-time falls within an allowed local time-of-day / day-of-week window |
| `SOURCE_IP` | request source IP | source IP is within an allowed CIDR set |
| `RESOURCE_TAG` | the targeted resource's tag set | the resource carries (or, for a deny-variant, lacks) a required tag |

- Adding a new condition type requires a **code change** (enum constant + evaluator branch + tests). It is **NOT runtime-registrable**. This is THE boundary distinguishing 2단계 from the rejected full engine.
- **Combination semantics: AND-only.** Multiple conditions on one grant must all hold. No arbitrary boolean/OR/NOT *nesting*; a single negation is expressed *within* a type (e.g. `RESOURCE_TAG` deny-if-present), not as a combinator. (OR/NOT nesting is the slippery slope to a policy language — § D6.)

- **D1-A (chosen-PROPOSED)** — closed enum `{TIME_WINDOW, SOURCE_IP, RESOURCE_TAG}`, AND-only.
- **D1-B (rejected)** — an open/pluggable condition SPI (register condition types at runtime). That *is* a policy engine by another name — 고비용, rejected by ADR-025 § 4 and the standing boundary.
- **D1-C (rejected)** — a full boolean policy language (Rego / CEL / AWS-`Condition` grammar). Rejected — the entire point is the miniature, not the engine.

### D2 — Restriction-only, fail-safe direction (the 4th composed gate)

A condition can **only gate** an action that has already passed RBAC + tenant-scope + data-scope. An unmet condition → **deny** (`403 ACCESS_CONDITION_UNMET`). A condition **never grants** a permission the operator lacks.

- **Fail-safe evaluation**: if a condition's required input is unavailable or ambiguous (e.g. the source IP cannot be resolved, the resource tag set is unknown), the condition is treated as **UNMET → deny**, never allow. (Same direction as data-scope's deny-by-default, § ADR-025 3.1.)
- **No additive/elevation here.** Conditional *grant* — break-glass, time-boxed elevation ("only while incident flag is on, grant admin") — is the riskier *fail-dangerous* shape and is explicitly **out of scope** (§ 3.2). That direction is owned by **assume-tenant token scoping (ADR-020)** and **admin delegation (ADR-024)**, where a granted privilege is auditable at grant/exchange time rather than synthesised on the hot path.

### D3 — Condition carrier (gate choice)

Where a grant's conditions live and how they reach the enforcement seam. PROPOSED options for the ACCEPTED gate:

- **D3-A — signed claim** `access_conditions` on the assume-tenant token (sibling to `data_scope`): a structured value the producer copies from a new per-grant condition field; the domain evaluates it via the shared evaluator. *Pro:* per-operator, travels with the identity, stateless enforcement, true "condition on a grant". *Con:* requires a **producer touch** (`TenantClaimTokenCustomizer` + a grant-storage field) — the main cost, and the inverse of ADR-025 § D5's "producer unchanged". **(deferred — promotable later if per-operator conditions are wanted.)**
- **D3-B (CHOSEN, ACCEPTED 2026-06-11)** — domain/endpoint-side guard config: the condition is attached to a resource/endpoint policy in the consuming domain (e.g. "the admin write endpoints require `SOURCE_IP ∈ corp-CIDR`"), not to a per-operator grant. *Pro:* **zero producer/claim/IAM change** — purely consumer-side, the cheapest credible 2단계 demonstration; matches ADR-025's consumer-side spirit. *Con:* condition is per-resource/endpoint, not per-operator-grant (coarser; closer to a "guarded endpoint" than an "AWS-`Condition` on a grant"). **User-selected at the ACCEPTED gate** as the net-zero, producer-untouched pilot — proves the closed-enum evaluator and the 4th-gate composition at the lowest blast radius, exactly as ADR-025 stayed consumer-side first.
- **D3-C (rejected)** — domain calls IAM at enforcement to resolve a grant's conditions. Chatty; couples domain↔IAM on the hot path.

### D4 — Pilot domain + condition subset (gate choice)

Which one domain and which of the 3 condition types the first enforcement proves (keep the blast radius minimal, exactly as ADR-025 § D3 picked a single domain). PROPOSED candidates:

- **iam admin endpoints + `SOURCE_IP` (CHOSEN, ACCEPTED 2026-06-11)** — gate the operator/admin write surface to the corporate CIDR. Security-natural, single condition, smallest pilot. **User-selected at the gate.**
- **wms write endpoints + `TIME_WINDOW` (not chosen)** — e.g. outbound-confirm only during business hours.
- **a `RESOURCE_TAG` pilot (not chosen)** — e.g. deny mutating actions on rows tagged `confidential`.

The gate selected exactly one pilot: **iam admin endpoints + `SOURCE_IP`** (one domain, one condition type), keeping the first enforcement's blast radius minimal exactly as ADR-025 § D3 picked a single wms entity.

### D5 — Net-zero / opt-in

Absent conditions (no `access_conditions` claim AND/OR no domain guard config) ⟺ **no gate** ⟺ behaves exactly as today. The condition gate only bites once a condition is configured for a grant/endpoint. Every existing path is **byte-identical** until then. Mirrors ADR-025 § D4.

### D6 — Still NO full policy engine (the standing boundary)

This ADR does **NOT** introduce: a policy document/language; a runtime-registrable condition SPI; arbitrary boolean combinators (OR/NOT nesting); cross-resource policy evaluation; or a policy-admin UI. The **closed enum + AND-only + restriction-only** is the entire 2단계. If a real need for open/composable conditions ever appears, that is yet another ADR — this one explicitly does **not** open that door.

### D7 — Staged execution (zero-regression), mirroring ADR-025 § D7

1. This ADR **PROPOSED → ACCEPTED** (doc-only; the gate fixes the D3 carrier + the D4 pilot).
2. Shared contract `platform/access-conditions.md` + shared `com.example.security.jwt.AccessCondition` evaluator (closed enum, fail-safe) in `libs/java-security`, with unit tests. (The producer is touched **only if** D3-A is chosen.)
3. Pilot-domain enforcement (the chosen D4) + IT proving **met / unmet / absent (net-zero)**.
4. (optional, future) federation-e2e proof; (optional, future) additional condition types / domains — each its own ADR/task.

---

## 3. Scope

### 3.1 Hard invariants this ADR carries

- **Restriction-only** — a condition can only NARROW (gate) an already-RBAC-, tenant-, data-scope-authorised action; it can **never grant**. (Same invariant as data-scope, ADR-025 § 3.1.)
- **Fail-safe** — an unresolvable/ambiguous condition input ⟹ **deny**, never allow.
- **Net-zero** — absent condition ⟺ unchanged behaviour; every existing path is byte-identical until a condition is configured.
- **Closed enum** — condition types are **code, not data**; no runtime registration; AND-only combination.

### 3.2 What this ADR does NOT do

- No full policy engine / policy language / open condition SPI (§ D1-B/C, D6) — the standing 고비용 rejection.
- No boolean combinators (OR/NOT nesting), no cross-resource policy evaluation (§ D6).
- **No additive / elevation conditions** (conditional grant / break-glass) — that fail-dangerous direction is owned by ADR-020 (assume-tenant scoping) and ADR-024 (admin delegation), auditable at grant time (§ D2).
- No producer / token-customizer change **unless** the gate picks D3-A.
- No change to ADR-025's data-scope attribute — this is an orthogonal, independently-staged dimension.

### 3.3 Future-self (post-ACCEPTED execution roadmap — sketch, finalised at ACCEPTED)

0. **`TASK-MONO-216` (PROPOSED, DONE #1284) + `TASK-MONO-217` (ACCEPTED transition, this)** — doc-only. The ACCEPTED gate fixed **D3 = D3-B** (domain/endpoint guard-config — producer untouched) + **D4 = iam admin endpoints + `SOURCE_IP`**. Model = Opus (analysis); the ACCEPTED flip is doc-only.
1. **`TASK-MONO-218`** — `platform/access-conditions.md` contract + shared `com.example.security.jwt.AccessCondition` evaluator (closed enum, fail-safe, AND-only) in `libs/java-security`, with unit tests. Producer untouched (D3-B). Model = Sonnet→Opus (doc + security-lib).
2. **`TASK-<iam>-BE-xxx`** — the iam pilot's `SOURCE_IP` condition enforcement on the admin write endpoints + opt-in net-zero + IT (met / unmet / absent). Model = Opus (authorization enforcement; security-critical).
3. **(optional)** federation-e2e proof that a conditioned operator is gated while an unconditioned operator is unaffected — reuses the MONO-207/210 dedicated-tenant harness.

No full engine and no additive conditions are scoped here.

---

## 4. Alternatives Considered

- **Full ABAC/policy engine (policy documents + condition evaluation).** Rejected — 고비용; the portfolio needs the *conditional pattern demonstrated*, not a rules engine. (Already rejected for 1단계, ADR-025 § 4; the same boundary holds for 2단계.)
- **Open / pluggable condition SPI** (D1-B). Rejected — runtime-registrable condition types ARE a policy engine in disguise; the closed enum is the whole point.
- **Boolean policy language (Rego / CEL / AWS-`Condition` grammar)** (D1-C). Rejected — the miniature is the goal; AND-only over a fixed enum gives the conditional story at a fraction of the cost.
- **Additive / elevation conditions (conditional grant, break-glass).** Rejected for this ADR (§ D2/3.2) — fail-dangerous direction, and already covered (auditable at grant time) by ADR-020 / ADR-024.
- **Bundle 2단계 into ADR-025.** Rejected — 2단계 is independent and higher-cost than the data-scope attribute; ADR-025 § D6 deliberately deferred it to keep that blast radius small. Separating them is the discipline, not an accident.
- **Domain-resolves-conditions-via-IAM-callback** (D3-C). Rejected — chatty, couples domain↔IAM on the hot path; the claim (D3-A) or domain-config (D3-B) carriers are stateless at enforcement.

---

## 5. Relationship to ADR-MONO-019 / 020 / 021 / 023 / 024 / 025

| | ADR-019/020/021 | ADR-023 | ADR-024 | ADR-025 (axis ② 1단계) | **ADR-026 (this — axis ② 2단계)** |
|---|---|---|---|---|---|
| Axis | who / which tenant / what type | entitlement↔IAM plane | tenant-admin delegation (RBAC who-may-administer) | ABAC data-scope (which data slice) | **access conditions (under which circumstances)** |
| Question | who / which tenant / what type | which plane | what may you administer | over which data | **when / from where / on which tagged rows** |
| Gate position | base RBAC + tenant | orthogonal | RBAC (admin-grant confinement) | 3rd gate (data-scope filter) | **4th gate (condition, restriction-only)** |
| Direction | grant + scope | — | grant (≤ own) | narrow-only | **narrow-only (gate, never grant)** |

The four authorization dimensions **compose** at one enforcement seam: RBAC permission → tenant-scope → data-scope (which rows) → **access condition (whether the action is permitted *right now / here / on this row*)**. All must pass; none can substitute for another. ADR-026's condition gate is the last and is restriction-only, exactly like ADR-025's data-scope filter.

---

## 6. Status Transition History

Append-only.

| Date | Transition | Decision direction | User intent quote | PR(s) |
|---|---|---|---|---|
| 2026-06-11 | created PROPOSED | D1 = closed condition enum `{TIME_WINDOW, SOURCE_IP, RESOURCE_TAG}`, AND-only, code-not-data (no policy language / open SPI); D2 = restriction-only + fail-safe, 4th composed gate, no additive/elevation (owned by 020/024); D3 = condition carrier (A signed `access_conditions` claim **vs** B domain/endpoint guard-config) — gate to fix; D4 = single pilot domain + condition subset — gate to fix; D5 = net-zero/opt-in; D6 = still NO full policy engine (standing boundary); D7 = staged (contract+evaluator → pilot enforcement+IT → optional fed-e2e), mirroring ADR-025 § D7. Lifts the ADR-025 § D6 deferral additively (no change to 025's data-scope). | "2단계 진행" — after the axis ② 1단계 (data-scope) closure and a design discussion that fixed 2단계 as a **closed-enum, restriction-only, fail-safe** condition gate ("역할 + 조건식 한 겹, AWS-IAM-Condition 축소판; 풀 정책 엔진은 거부; 더하기(elevation)는 assume-tenant/위임이 소유; enum 을 코드에 박아 닫아둔 재사용 패턴"). ADR-first per the established ADR-019…025 staged pattern. | #1284 (TASK-MONO-216) |
| 2026-06-11 | PROPOSED → ACCEPTED | D1-D2, D5-D7 directions **finalised unchanged** from PROPOSED #1284 squash `58905a654`; **D3 finalised = D3-B** (domain/endpoint guard-config — producer/token/IAM unchanged, net-zero consumer-side like ADR-025 § D5; D3-A signed-claim deferred/promotable); **D4 finalised = iam admin endpoints + `SOURCE_IP`** (the single first pilot — smallest, security-natural). Authorises the § 3.3 execution roadmap (dependency-correct base = this ACCEPTED main): `platform/access-conditions.md` contract + shared `AccessCondition` evaluator (`libs/java-security`) → iam `SOURCE_IP` enforcement + IT → optional federation-e2e proof. Same one-off Meta-policy category as the sibling ACCEPTED transitions (ADR-023/024/025). | "도메인 가드설정 (D3-B)" + "iam admin + SOURCE_IP" + "Draft PR 열기" → "진행해줘" (TASK-MONO-217 — user fixed the carrier=D3-B + pilot=iam/SOURCE_IP at the gate and authorised ACCEPTED + execution) | #<this> (TASK-MONO-217) |
