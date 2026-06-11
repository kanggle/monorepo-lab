# Task ID

TASK-MONO-216

# Title

ADR-MONO-026 (PROPOSED) — Role-Grant Access Conditions (the 2단계 of axis ②). Author the committed PROPOSED ADR that lifts the ADR-MONO-025 § D6 deferral and formalises a deliberately-bounded **AWS-IAM-`Condition` miniature**: a **closed, code-defined condition enum** (`TIME_WINDOW` / `SOURCE_IP` / `RESOURCE_TAG`, AND-only), **restriction-only + fail-safe** (a condition can only gate an already-authorised action, never grant; unresolvable input ⇒ deny), composed as a **4th orthogonal gate** after RBAC + tenant-scope + data-scope. Explicitly **NOT** a full policy engine / policy language / open SPI / boolean combinators / additive-elevation (§ D1/D6 + Alternatives). The condition **carrier** (signed `access_conditions` claim vs domain/endpoint guard-config) and the **pilot domain + condition subset** are PROPOSED options left for the ACCEPTED gate. Doc-only; the ACCEPTED transition + execution (`platform/access-conditions.md` contract + shared `AccessCondition` evaluator + pilot enforcement) are separate follow-up tasks (ADR § 3.3).

# Status

ready

# Owner

backend

# Task Tags

- adr
- abac
- conditional-policy
- multi-tenant
- iam
- doc

---

# Dependency Markers

- **amends**: ADR-MONO-025 § D6 — lifts the explicit deferral of 2단계 (role + condition: time/IP/resource-tag) additively; does NOT change ADR-025's 1단계 data-scope attribute.
- **follows**: ADR-MONO-023 (axis ③, CLOSED) + ADR-MONO-024 (axis ①, CLOSED) + ADR-MONO-025 (axis ② 1단계, CLOSED) — this is axis ② 2단계 of the same AWS/GCP-comparison improvement list, ADR-first per the established 019…025 staged pattern.
- **blocks**: the ADR § 3.3 execution roadmap (ACCEPTED transition → `platform/access-conditions.md` + shared `AccessCondition` evaluator → pilot-domain enforcement). None start until ACCEPTED.

# Goal

Record the access-condition decision so a domain can adopt a conditional gate from a contract instead of re-deriving it, while keeping the change net-zero and explicitly bounding scope to a **closed-enum, restriction-only, fail-safe** condition gate — rejecting a full policy engine, an open condition SPI, boolean combinators, and additive/elevation conditions (which ADR-020/024 already own).

# Scope

- NEW `docs/adr/ADR-MONO-026-role-grant-access-conditions.md` (Status PROPOSED) — D1-D7 + alternatives + relationship to ADR-019/020/021/023/024/025 + Status Transition History.
- Update `tasks/INDEX.md` ready list.
- This task file.

**Out of scope** (post-ACCEPTED, separate tasks): the `platform/access-conditions.md` contract, the shared `AccessCondition` evaluator (`libs/java-security`), any pilot-domain enforcement code, any producer/token-customizer change (only if D3-A is chosen at the gate), any additional condition types/domains.

# Acceptance Criteria

- **AC-1** `ADR-MONO-026` exists with Status PROPOSED and the D1-D7 decisions: closed condition enum (`TIME_WINDOW`/`SOURCE_IP`/`RESOURCE_TAG`, AND-only, code-not-data); restriction-only + fail-safe, 4th composed gate; condition carrier options (signed claim vs domain guard-config) left for the gate; single pilot domain + condition subset left for the gate; net-zero/opt-in; standing no-full-engine boundary; staged execution.
- **AC-2** The ADR records the accurate current state: the composed RBAC→tenant→data-scope stack exists (ADR-019/024/025), the assume-tenant producer + `AbacDataScope` shared-evaluator pattern exist, and request-time/source-IP inputs are already available at enforcement.
- **AC-3** The ADR explicitly **lifts ADR-025 § D6** (the 2단계 deferral) and explicitly **rejects** a full policy engine, an open condition SPI, boolean combinators, and additive/elevation conditions (§ D1/D2/D6 + Alternatives).
- **AC-4** Status Transition History has the PROPOSED row with the user intent quote ("2단계 진행" + the design discussion that fixed closed-enum / restriction-only / fail-safe).
- **AC-5** Doc-only — no code, no contract, no producer change, no `platform/access-conditions.md` in this PR.

# Related Specs

- `docs/adr/ADR-MONO-025-abac-data-scope-generalization.md` § D6 (the 2단계 deferral being lifted) + the 1단계 data-scope it complements
- `docs/adr/ADR-MONO-024-tenant-admin-delegation.md` + `docs/adr/ADR-MONO-020-operator-multitenant-assignment.md` (the RBAC/elevation axes that own additive grant — referenced as the boundary for § D2/3.2)

# Related Contracts

- none (the `platform/access-conditions.md` contract is a post-ACCEPTED deliverable)

# Edge Cases

- The condition gate must be **net-zero/opt-in**: absent condition (no claim and/or no domain config) ⟺ unchanged behaviour — stated as the § D5 invariant so the gate never bites an existing path until configured.
- **Fail-safe** must be explicit: an unresolvable condition input (source IP unknown, resource tag set unknown) ⇒ deny, never allow (§ D2/3.1) — the security-critical direction.
- The carrier choice (D3-A signed claim) would require a producer touch (`TenantClaimTokenCustomizer`), inverting ADR-025 § D5; D3-B (domain guard-config) keeps the producer untouched. The ADR must present both and leave the choice to the gate.
- Additive/elevation ("conditional grant", break-glass) must be explicitly excluded and routed to ADR-020/024 — otherwise a reader could mistake 2단계 for a privilege-granting mechanism.

# Failure Scenarios

- If the ADR named a full policy engine / open SPI / boolean policy language as the decision, it would re-introduce the 고비용 scope the user explicitly rejected — § D1-B/C + D6 + Alternatives bound it to a closed enum + AND-only.
- If the ADR allowed conditions to *grant* (additive/elevation), a fail-dangerous escalation path opens — § D2 + § 3.1 pin conditions as restriction-only, gate-never-grant, fail-safe.
- If the ADR omitted net-zero/opt-in, adding the contract would risk regressing every existing read/write path — § D5 + § 3.1 pin absent-condition ⟺ unchanged.
- If the carrier/pilot were hard-decided in PROPOSED instead of left to the gate, the ACCEPTED transition would lose its decision surface — D3/D4 are deliberately PROPOSED-as-options (mirrors ADR-025 § D3 leaving the first domain to the gate).
