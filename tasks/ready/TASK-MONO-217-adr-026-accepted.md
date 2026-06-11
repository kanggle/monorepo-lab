# Task ID

TASK-MONO-217

# Title

ADR-MONO-026 PROPOSED → ACCEPTED transition. Flip `ADR-MONO-026` (Role-Grant Access Conditions, axis ② 2단계) from PROPOSED to ACCEPTED, finalising the two gate decisions left open in the PROPOSED: **D3 = D3-B** (domain/endpoint guard-config — producer/token/IAM unchanged, net-zero consumer-side like ADR-025 § D5; the D3-A signed-claim carrier is deferred/promotable) and **D4 = iam admin endpoints + `SOURCE_IP`** (the single first pilot). Append the ACCEPTED row to the Status Transition History and authorise the § 3.3 execution roadmap (contract + shared `AccessCondition` evaluator → iam SOURCE_IP enforcement + IT). Doc-only; the contract/evaluator and the iam enforcement are separate follow-up tasks.

# Status

ready

# Owner

backend

# Task Tags

- adr
- conditional-policy
- multi-tenant
- iam
- doc

---

# Dependency Markers

- **depends on**: TASK-MONO-216 (ADR-MONO-026 PROPOSED, DONE — PR #1284 squash `58905a654`). This ACCEPTED transition bases on the merged PROPOSED main (dependency-correct base).
- **blocks**: TASK-MONO-218 (`platform/access-conditions.md` contract + shared `AccessCondition` evaluator) and the iam `SOURCE_IP` enforcement task — neither starts until this ACCEPTED.
- **amends**: ADR-MONO-025 § D6 (the 2단계 deferral lifted by ADR-026, now ACCEPTED).

# Goal

Ratify ADR-MONO-026 with the user's gate selections so the execution roadmap can begin from a dependency-correct ACCEPTED main: condition carrier = domain/endpoint guard-config (D3-B, producer untouched), first pilot = iam admin endpoints gated by `SOURCE_IP`.

# Scope

- EDIT `docs/adr/ADR-MONO-026-role-grant-access-conditions.md`: Status PROPOSED → ACCEPTED; mark D3-B and the iam/`SOURCE_IP` D4 option as CHOSEN/ACCEPTED; update § 3.3 roadmap task references; append the ACCEPTED row to § 6 Status Transition History with the user intent quote.
- Update `tasks/INDEX.md` ready list.
- This task file.

**Out of scope**: the `platform/access-conditions.md` contract, the shared `AccessCondition` evaluator, any iam enforcement code, any producer change (D3-B keeps the producer untouched).

# Acceptance Criteria

- **AC-1** `ADR-MONO-026` Status is ACCEPTED.
- **AC-2** D3 is finalised to **D3-B** (domain/endpoint guard-config) and D4 to **iam admin endpoints + `SOURCE_IP`**, both marked CHOSEN/ACCEPTED in the decision body.
- **AC-3** § 6 Status Transition History has the PROPOSED → ACCEPTED row with the user intent quote ("도메인 가드설정 (D3-B)" + "iam admin + SOURCE_IP" + "진행해줘") and the PROPOSED squash ref.
- **AC-4** The § 3.3 execution roadmap names the concrete follow-up tasks (MONO-218 contract+evaluator; an iam enforcement task) and records that D3-B keeps the producer untouched.
- **AC-5** Doc-only — no contract, no evaluator, no enforcement code in this PR.

# Related Specs

- `docs/adr/ADR-MONO-026-role-grant-access-conditions.md` (the decision being ratified)
- `docs/adr/ADR-MONO-025-abac-data-scope-generalization.md` § D7 (the sibling ACCEPTED-transition pattern this mirrors)

# Related Contracts

- none (the `platform/access-conditions.md` contract is the next task's deliverable)

# Edge Cases

- D3-B keeps the producer (`TenantClaimTokenCustomizer`) untouched — the ADR must record this so the contract/enforcement tasks do not introduce a producer change by default.
- The ACCEPTED transition is doc-only and must base on the merged PROPOSED main (not the PROPOSED branch) so the dependency order matches ADR-023/024/025.

# Failure Scenarios

- If the ACCEPTED row omitted the gate selections, the execution tasks would re-open the carrier/pilot decision — AC-2/AC-3 pin both.
- If the transition flipped Status without recording the producer-untouched D3-B consequence, a later task could needlessly touch the token customizer — AC-4 records it.
