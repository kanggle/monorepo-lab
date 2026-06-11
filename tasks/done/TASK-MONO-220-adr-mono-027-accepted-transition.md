# Task ID

TASK-MONO-220

# Title

**ADR-MONO-027 PROPOSED → ACCEPTED** transition. Gated on explicit user approval of the §2 decisions. Flips Status, records the acceptance rationale, and unblocks the scm Phase 1 implementation tasks (BE-024/025/INT-002 backlog → ready as their specs land).

# Status

done

> **DONE (2026-06-11)**: ADR-MONO-027 Status PROPOSED → ACCEPTED on explicit user "진행" intent accepting the §2 decisions as reviewed (PR #1292). §6 status-history ACCEPTED line added (user-directed, NOT self-ACCEPT). §2 decision bodies byte-unchanged. Unblocks SCM-BE-022/023 spec tasks. Bundled with MONO-219 close + ADR edit in one docs PR. 분석=Opus 4.8 / 구현=Opus(직접).

# Owner

architecture

# Task Tags

- docs
- adr

---

# Dependency Markers

- **선행 (prerequisite)**: TASK-MONO-219 (ADR-027 PROPOSED merged).
- **gated by**: explicit user "진행"/approval intent on the §2 decisions (NOT a self-ACCEPT — sibling MONO-165/153/157 pattern).
- **unblocks**: scm BE-022/023 spec tasks proceed; on their merge, BE-024/025/INT-002 move backlog→ready.

# Goal

Transition ADR-MONO-027 to ACCEPTED once the user has reviewed and approved the proposed decisions, so implementation proceeds against an accepted record.

# Scope

## In Scope

- `docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md` — Status `PROPOSED → ACCEPTED`; append a § 6 status-history line recording the user-explicit acceptance (date + which decisions affirmed).
- (If an ADR audit table exists per ADR-003a) append the acceptance row.
- Doc-only.

## Out of Scope

- Any decision *change*. If the user requests a different option (e.g. auto-submit instead of suggestion-only), this task STOPS and a PROPOSED amend task is created first — ACCEPTED records the decision as-reviewed, it does not redesign.

# Acceptance Criteria

- **AC-1** ADR-027 Status = ACCEPTED with a dated § 6 acceptance line naming the user intent.
- **AC-2** The acceptance line explicitly states it is user-directed, not dispatcher self-ACCEPT.
- **AC-3** Doc-only diff; §2 decision bodies byte-unchanged (acceptance does not edit the decisions).

# Related Specs

- [ADR-MONO-027](../../docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md) (the document being transitioned)
- [ADR-MONO-022](../../docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) § 6 (same-session PROPOSED→ACCEPTED status-history precedent)

# Related Contracts

- None (doc-only transition).

# Edge Cases

- If the user approves only a subset of decisions → record the approved subset; leave contested decisions PROPOSED or spin an amend. Do not silently accept all.

# Failure Scenarios

- Self-ACCEPT without user intent → violates the cross-project-coupling decision-recording norm (genuine architecture decision between two published axes). This task requires explicit user approval as its precondition.

# Notes

- 분석=Opus 4.8 / 구현 권장=Sonnet (mechanical status flip once approved). doc-only.
