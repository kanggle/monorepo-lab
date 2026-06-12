# Task ID

TASK-MONO-231

# Title

**ADR-MONO-030 PROPOSED → ACCEPTED** transition. Gated on explicit user approval of the §2 decisions (the three forks pre-fixed via AskUserQuestion + the "진행" intent on merge→3-dim→ACCEPTED). Flips Status, finalises the open sub-decisions (D3 seller placement / D4 plane wiring / D5 slice boundary) at the recommended directions, records the acceptance, and authorises the §3.4 execution roadmap (specs → outer axis → inner axis → deferred). Carries **one as-accepted refinement**: D7's `PROJECT.md` classification change re-sequenced Step 0 → Step 2 (user decision — apply the `multi-tenant` trait with the outer-axis code, not at this flip), so ACCEPTED is a **pure status flip with no `PROJECT.md` edit**.

# Status

done

> **DONE (2026-06-12)**: `ADR-MONO-030` Status PROPOSED → ACCEPTED on explicit user "진행" intent (merge #1365 → 3-dim → ACCEPTED) + AskUserQuestion D7 timing = "Step 2로 미룸". §6 ACCEPTED row added (user-directed, NOT self-ACCEPT). D1-D8 decision bodies byte-unchanged; the only as-accepted change is the **D7 application-timing** refinement (Step 0 → Step 2), recorded as an additive note under D7 — the decision to lift the `multi-tenant`+`marketplace` exclusions is unchanged, only *when* the trait is applied moved (to avoid declaring the 11 not-yet-migrated services M1-violating project-wide). ACCEPTED is a pure status flip — **no `PROJECT.md` edit, no code**. Bundled with MONO-230 close + ADR edit in one docs PR. Authorises the §3.4 roadmap. 분석=Opus 4.8 / 구현=Opus 직접.

# Owner

architecture

# Task Tags

- docs
- adr
- multi-tenant
- marketplace
- ecommerce

---

# Dependency Markers

- **선행 (prerequisite)**: TASK-MONO-230 (ADR-030 PROPOSED merged, #1365).
- **gated by**: explicit user "진행"/approval intent on the §2 decisions (NOT a self-ACCEPT — sibling MONO-220/217/153 pattern). The three forks (slice-first / row-level / reuse-IAM) were pre-fixed via AskUserQuestion at PROPOSED; the D7 timing refinement was user-decided via AskUserQuestion at ACCEPTED.
- **amends (timing only)**: this transition re-sequences D7's `PROJECT.md` change Step 0 → Step 2 (additive note under D7); the decision is unchanged.
- **unblocks**: the ADR §3.4 execution roadmap — Step 1 (ecommerce specs + `tenant_id`/`seller_id` model + `domain_key='ecommerce'` subscription contract + D4 plane doc) becomes authorable; Step 2 (outer axis + `PROJECT.md` trait change) follows; Step 3 (inner axis); Step 4 (deferred). None authored in this task.

# Goal

Transition ADR-MONO-030 to ACCEPTED once the user has reviewed and approved the proposed decisions, so execution proceeds against an accepted record — finalising the open sub-decisions at the recommended directions and recording the user-decided D7 application-timing refinement, without changing any decision.

# Scope

## In Scope

- `docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md` — Status `PROPOSED → ACCEPTED`; Date ACCEPTED clause; append a §6 status-history ACCEPTED row recording the user-explicit acceptance + the D7 timing refinement; add an additive note under D7 recording the Step 0 → Step 2 re-sequencing. D1-D8 decision **bodies byte-unchanged** (additive notes only, ADR-019 pattern).
- Close TASK-MONO-230 (ready → done, 3-dim verified note).
- This task file (done).
- `tasks/INDEX.md` ready/done lists.
- Doc-only.

## Out of Scope

- **Any decision change.** If the user had requested a different option (e.g. ecommerce-local tenancy, seller-as-sub-tenant, full 13-service v1), this task STOPS and a PROPOSED amend is created first — ACCEPTED records decisions as-reviewed, it does not redesign. The D7 Step 0 → Step 2 move is an **application-timing** refinement (when, not what), user-decided, recorded additively.
- **`PROJECT.md` edit** — re-sequenced to Step 2 (outer-axis execution), NOT this flip.
- Any `tenant_id`/`seller_id` code, migration, seed, subscription, or console integration (ADR §3.4 Steps 1-4).

# Acceptance Criteria

- **AC-1** ADR-030 Status = ACCEPTED with a dated §6 acceptance row naming the user intent ("진행" + the AskUserQuestion forks).
- **AC-2** The acceptance row explicitly states it is user-directed, not dispatcher self-ACCEPT.
- **AC-3** D1-D8 decision bodies byte-unchanged; the only changes are additive (Status, Date clause, D7 application-timing note, §6 row) — acceptance does not edit the decisions.
- **AC-4** The D7 application-timing refinement (Step 0 → Step 2) is recorded as an additive note under D7, stating the decision is unchanged and the rationale (avoid project-wide M1 misclassification of the 11 not-yet-migrated services).
- **AC-5** No `PROJECT.md` edit, no code, no migration in this PR. Doc-only.
- **AC-6** TASK-MONO-230 closed (ready → done) + INDEX reflects 230/231 in done, ready empty.

# Related Specs

- [ADR-MONO-030](../../docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md) (the document being transitioned)
- [ADR-MONO-022](../../docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) § 6 + [ADR-MONO-020](../../docs/adr/ADR-MONO-020-operator-multitenant-assignment.md) (same-session PROPOSED→ACCEPTED status-history precedent) + [ADR-MONO-019](../../docs/adr/ADR-MONO-019-platform-console-customer-tenant-model.md) (additive-note-without-editing-decision-tables precedent)

# Related Contracts

- None (doc-only transition).

# Edge Cases

- If the user had approved only a subset of decisions → record the approved subset; leave contested decisions PROPOSED or spin an amend. (User approved all three forks + the D7 timing → full ACCEPTED.)
- The D7 timing move must NOT be presented as a decision reversal — the `multi-tenant`+`marketplace` exclusions are still lifted; only the trait's *application step* moves. The additive note states this explicitly.

# Failure Scenarios

- Self-ACCEPT without user intent → violates the cross-cutting-tenancy decision-recording norm (genuine architecture decision promoting an independently-published portfolio axis). This task required explicit user approval as its precondition (satisfied: "진행" + AskUserQuestion forks).
- Editing a D1-D8 decision body at ACCEPTED → would make ACCEPTED a redesign. Prevented: only additive changes (Status/Date/§6/D7-note); bodies byte-unchanged.
- Applying the `PROJECT.md` trait at this flip (Step 0) → would declare 11/13 services M1-violating for the whole migration. Prevented: re-sequenced to Step 2 (user decision, AC-4).

# Notes

- 분석=Opus 4.8 / 구현 권장=Opus (ADR finalisation under the byte-unchanged-decision discipline + the D7 timing-refinement judgment). doc-only.
