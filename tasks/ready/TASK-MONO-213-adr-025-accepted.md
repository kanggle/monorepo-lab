# Task ID

TASK-MONO-213

# Title

ADR-MONO-025 PROPOSED → ACCEPTED. Flip the ABAC data-scope generalization ADR to ACCEPTED, finalising D3 = **wms warehouse-scope** as the first extension domain (user-selected at the gate) and authorising the § 3.3 execution roadmap (contract + shared dual-read utility → wms enforcement → optional federation-e2e proof). D1-D2/D4-D7 unchanged from PROPOSED. Doc-only.

# Status

ready

# Owner

backend

# Task Tags

- adr
- abac
- iam
- doc

---

# Dependency Markers

- **transitions**: ADR-MONO-025 (PROPOSED #1268 `1484c611`) → ACCEPTED.
- **authorises**: the § 3.3 execution roadmap (next tasks: `platform/abac-data-scope.md` + shared dual-read util; wms warehouse data-scope enforcement).
- **pattern**: same staged ADR ACCEPTED-transition discipline as TASK-MONO-209 (ADR-024) / ADR-023.

# Goal

Lock the ABAC data-scope decision with the user's gate choice (wms first) so execution can begin against a stable ACCEPTED main.

# Scope

- `docs/adr/ADR-MONO-025-abac-data-scope-generalization.md` — Status PROPOSED → ACCEPTED; D3 finalised to wms (chosen/rejected markers); Status Transition History ACCEPTED row.
- This task file.

**Out of scope**: any execution (contract, shared util, wms code) — those are the post-ACCEPTED follow-up tasks.

# Acceptance Criteria

- **AC-1** ADR-MONO-025 Status = ACCEPTED.
- **AC-2** D3 finalised to wms warehouse-scope (D3-A CHOSEN, D3-B rejected).
- **AC-3** Status Transition History has the PROPOSED → ACCEPTED row with the user gate quote ("wms 창고 스코프" + "ACCEPTED 후 구현까지 진행").
- **AC-4** D1-D2/D4-D7 unchanged from PROPOSED; doc-only PR.

# Related Specs

- `docs/adr/ADR-MONO-025-abac-data-scope-generalization.md`

# Related Contracts

- none

# Edge Cases

- The ACCEPTED base must be on main before execution tasks open (dependency-correct base), mirroring the ADR-024 step-1 sequencing.

# Failure Scenarios

- If execution tasks branched off the PROPOSED commit instead of ACCEPTED main, the roadmap base would be wrong — this task lands ACCEPTED first.
