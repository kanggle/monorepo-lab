# Task ID

TASK-BE-052

# Title

Master.Lot cross-service consumer audit — inbound/inventory architecture spec drift

# Status

ready

# Owner

backend

# Task Tags

- code
- spec

---

# Goal

Audit whether `inbound-service` and `inventory-service` should consume `master.lot.*` events. Current state: only `outbound-service` lists `MasterLotConsumer` in its architecture spec; inbound/inventory do not. Determine whether this is intentional (Lot is outbound-only) or drift (the other two services should also consume), and update the relevant `architecture.md` files accordingly.

After this task: the master read-model coverage table in each service's `architecture.md` § Master Reads is **consistent with what the actual code does**, and any cross-service Lot reference is documented (or the absence justified).

---

# Scope

## In Scope

- Inspect `apps/inbound-service/.../adapter/in/messaging/consumer/` and `apps/inventory-service/.../adapter/in/messaging/consumer/` for any existing `MasterLotConsumer` (or equivalent) class.
- Inspect `apps/inbound-service/.../domain/` and `apps/inventory-service/.../domain/` to determine whether Lot identity is referenced (e.g., `lot_id` foreign-key columns in JPA entities, lot-aware reservation logic).
- Decide which of the two scenarios applies:
  - **Scenario A (drift)**: Lot is referenced but no consumer exists → file follow-up task to add the consumer + corresponding architecture.md row.
  - **Scenario B (intentional)**: Lot is genuinely not used → add an explicit "no Lot consumer — Lot tracking is outbound-only in v1" note to the inbound-service and inventory-service `architecture.md` § Master Reads section.
- Update `projects/wms-platform/specs/services/inbound-service/architecture.md` § Master Reads (or equivalent table).
- Update `projects/wms-platform/specs/services/inventory-service/architecture.md` § Master Reads (or equivalent table).

## Out of Scope

- Implementing a `MasterLotConsumer` for inbound/inventory (if Scenario A, file as separate task).
- v2 lot-tracking semantics (e.g., FEFO/FIFO inventory rotation by lot expiration).
- Outbound-service spec changes (already correct).

---

# Acceptance Criteria

- [ ] Code-evidence audit completed: which service references Lot identity, which doesn't.
- [ ] If Scenario A: separate follow-up task filed with the implementation work.
- [ ] If Scenario B: `inbound-service/architecture.md` § Master Reads contains an explicit "no Lot" note.
- [ ] If Scenario B: `inventory-service/architecture.md` § Master Reads contains an explicit "no Lot" note.
- [ ] No production code changes (this task is spec-only unless Scenario A triggers a follow-up).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0.

- `projects/wms-platform/specs/services/master-service/architecture.md` (Lot is one of 6 master entities)
- `projects/wms-platform/specs/services/outbound-service/architecture.md` § Master Reads (reference pattern — already lists MasterLotConsumer)
- `projects/wms-platform/specs/services/inbound-service/architecture.md` § Master Reads (target)
- `projects/wms-platform/specs/services/inventory-service/architecture.md` § Master Reads (target)
- `projects/wms-platform/specs/contracts/events/master-events.md` § master.lot.* events

---

# Related Contracts

- `master-events.md` (no contract change — read-only audit)

---

# Target Service

- inbound-service (spec only)
- inventory-service (spec only)

---

# Architecture

Per `platform/architecture-decision-rule.md`, follow each service's declared architecture style. This task is spec-only — no architecture changes.

---

# Implementation Notes

- `grep -r "master.lot\|MasterLot\|lot_id\|LotEntity" projects/wms-platform/apps/inbound-service/ projects/wms-platform/apps/inventory-service/` is the primary evidence-gathering command.
- If both inbound and inventory have `lot_id` JPA columns but no consumer, that's a strong "should consume" signal (drift). Otherwise it's "v1 design — no Lot tracking in these services" (intentional).
- For Scenario B note language, use the existing pattern from outbound-service architecture.md § Master Reads (consistent table style + footnote).

---

# Edge Cases

- Lot identity is referenced in test fixtures but not production domain → still Scenario B (test fixture is allowed without consumer).
- `master.lot.created.v1` is consumed somewhere but the consumer class is not in the standard `consumer/` package layout → record this as a finding for follow-up cleanup.

---

# Failure Scenarios

- Audit finds neither A nor B clearly applies (mixed evidence) → record findings and file a richer follow-up task with options for the user.

---

# Test Requirements

- N/A (spec-only). If Scenario A triggers a follow-up impl task, that task carries its own test requirements.

---

# Definition of Done

- [ ] Audit evidence documented in PR description
- [ ] inbound-service/architecture.md § Master Reads updated (Scenario B) or follow-up task filed (Scenario A)
- [ ] inventory-service/architecture.md § Master Reads updated (Scenario B) or follow-up task filed (Scenario A)
- [ ] Ready for review

---

# Provenance

Surfaced from `/refactor-spec all` (2026-05-11) audit Finding [WMS 4]. Skipped from PR #326 because requires code-vs-spec verification + decision (intentional vs drift).

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (audit + 1-2 spec line update, low complexity).
