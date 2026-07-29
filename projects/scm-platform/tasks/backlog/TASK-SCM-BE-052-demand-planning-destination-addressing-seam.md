# Task ID

TASK-SCM-BE-052

# Title

demand-planning-service: move 3PL destination-addressing policy out of the REST outbound adapter

# Status

backlog

<!-- Blocked from ready/: see "Backlog -> Ready Blocker" below. Do not implement from backlog per tasks/INDEX.md. -->

# Owner

backend

# Task Tags

- code

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Backlog -> Ready Blocker

`projects/scm-platform/specs/contracts/http/procurement-api.md` § `POST /api/procurement/po/from-suggestion` currently documents a 5-field request body while the shipped `DraftFromSuggestionRequest.java` accepts 8 (`destinationWarehouseId`, `destinationNodeType`, `leadTimeDays` landed additively across `TASK-SCM-BE-035` → `-048` → `-049` without a contract update). Per `CLAUDE.md` § Layer Rules ("API and event changes must update `specs/contracts/` before implementation") and the `tasks/INDEX.md` backlog→ready gate ("related contracts are identified"), this task cannot move to `ready/` until that contract doc is reconciled to the real 8-field body. File and complete a small `docs(scm):` contract-reconciliation task first (add the 3 undocumented fields to `procurement-api.md`, cross-referencing `specs/contracts/events/scm-procurement-events.md` where they are already documented), then promote this task to `ready/`.

---

# Goal

Move the "which identifier addresses this destination" decision (ADR-MONO-055 §D2/§D3/§D4) out of `ProcurementDraftPoClient` — an outbound adapter — into the application layer, so `DraftPoCommand` carries one already-resolved destination reference and the adapter only serialises it. `ProcurementDraftPoClient.java:67-103` currently holds a three-branch domain decision (3PL → address by IVS node id; WMS + resolvable code → address by warehouse code; else → omit, fail-closed) plus a private redeclaration of `NODE_TYPE_WMS_WAREHOUSE`/`NODE_TYPE_THIRD_PARTY_LOGISTICS` that duplicates the already-public constant on `ReorderSuggestion.java:46`. `demand-planning-service/architecture.md` justifies the Hexagonal style specifically because "the procurement leg can move from sync REST to an intra-scm event in v2 (D5) by swapping one outbound adapter" — today, swapping that adapter would require re-implementing the addressing policy inside the replacement. Identical HTTP request bodies before and after; no behaviour change.

---

# Scope

## In Scope

- Resolve the destination in `SuggestionApprovalTxn.prepareApprove` (or a small framework-free `DestinationAddressing` value type in `domain/model/`): 3PL → node id; WMS with non-blank code → code; otherwise → none.
- Replace `DraftPoCommand`'s `destinationWarehouseId` + `warehouseId` pair with a single resolved `(nodeType, destinationRef)` carrier where `destinationRef` may be absent.
- Delete the private `NODE_TYPE_*` constants in `ProcurementDraftPoClient.java:44-45`; reference the domain's existing public constants instead.
- Reduce `ProcurementDraftPoClient.createDraftFromSuggestion` to unconditional body assembly + one `if (destinationRef present)`.
- Move the two existing log lines (3PL-honour info log, fail-closed warn log) to wherever the decision now lives, preserving message content.

## Out of Scope

- `procurement-service` (the receiving side, its DTO, entity, or emit-gate) — not touched.
- `ReorderSuggestion` persistence shape, `V3__reorder_suggestion_destination_node_type.sql`, or the null→`WMS_WAREHOUSE` normalisation — unchanged.
- Introducing a typed `NodeType` enum in demand-planning — the value crosses a service boundary as a string; converting it is a separate contract-typing decision, not part of this refactor.
- The inbound read path (`InventoryVisibilityRestAdapter`, `SweepReorderUseCase`) — already a clean pass-through, untouched.
- Test files — separate change per `platform/refactoring-policy.md`.
- The `procurement-api.md` contract reconciliation itself — that is the prerequisite docs task gating this one out of backlog, not part of this task's diff.

---

# Acceptance Criteria

- [ ] Baseline recorded: `demand-planning-service` `test` + `integrationTest` GREEN, pre-change test count in the PR body.
- [ ] `ApproveMaterializationIntegrationTest` passes unmodified, including the `WMS_WAREHOUSE` body assertion and the 3PL body assertion.
- [ ] `SweepReorderUseCaseTest`, `SuggestionApprovalTxnTest`, `ApproveSuggestionUseCaseTest`, `InventoryVisibilityRestAdapterTest`, `SuggestionControllerSliceTest` pass unmodified.
- [ ] `ProcurementDraftPoClient` contains no `if` on node type and no `NODE_TYPE_*` string literal.
- [ ] For a suggestion with a blank/null warehouse code and non-3PL type, the request body still omits both `destinationWarehouseId` and `destinationNodeType` (byte-for-byte the pre-change fail-closed shape).
- [ ] The 3PL branch still sends the IVS node id as `destinationWarehouseId`; a null node id still results in no `destinationWarehouseId` key.
- [ ] Zero `src/test` files modified in the production commit.
- [ ] scm Build & Test + scm Integration CI lanes GREEN; test count identical to baseline.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `projects/scm-platform/specs/services/demand-planning-service/architecture.md` (§ Layer Structure, § Architecture Style Rationale #3 — adapter swappability, § Failure Modes)
- `projects/scm-platform/specs/services/demand-planning-service/data-model.md`
- `projects/scm-platform/specs/services/demand-planning-service/reorder-policy.md`
- `projects/scm-platform/specs/services/inventory-visibility-service/data-model.md` (node id / node type source)

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

- `projects/scm-platform/specs/contracts/http/procurement-api.md` § `POST /api/procurement/po/from-suggestion` — **must be reconciled to the real 8-field body before this task is promoted to ready/** (see Backlog -> Ready Blocker). Must remain unchanged in wire shape by this task itself.
- `projects/scm-platform/specs/contracts/http/demand-planning-api.md` § approve — unchanged.
- `projects/scm-platform/specs/contracts/events/replenishment-subscriptions.md` (`payload.warehouseCode` provenance) — unchanged.

---

# Target Service

- `demand-planning-service`

---

# Architecture

Follow:

- `projects/scm-platform/specs/services/demand-planning-service/architecture.md`

---

# Implementation Notes

- `ApprovalPlan` is an 11-field positional record; an arity change to fold the two destination fields into one must keep `SuggestionApprovalTxn.java`'s all-null "already materialized" short-circuit construction compiling and functioning before any resolution logic runs.

---

# Edge Cases

- Pre-`ADR-MONO-055` persisted rows with `destination_node_type = NULL` must still normalise to `WMS_WAREHOUSE` and address by code — resolution must read the entity's getter (never null), not the raw column.
- 3PL + null node id (legacy caller): must still produce no `destinationWarehouseId` so procurement fail-closes the sink emit — do not fall back to the warehouse code, which would address a 3PL expectation at a wms warehouse.
- WMS + non-blank code + non-null node id: the node id must remain unsent — it is a dedup-key dimension, not an address.
- Blank vs. null warehouse code: the current guard is `!= null && !isBlank()`; a whitespace-only code must keep degrading to fail-closed, not become a sent value.

---

# Failure Scenarios

- **Silent wire change** — reordering body-assembly calls or switching to a different map implementation could change JSON key order; verify against the existing IT body assertions rather than assuming harmlessness.
- **Fail-closed inverted** — collapsing the three branches into a lookup that returns a default instead of "absent" would emit an unresolvable destination, the exact failure `ProcurementDraftPoClient.java:76-78`'s existing warn-log guards against.
- **Scope creep into procurement** — touching the receiving DTO/entity converts a behaviour-preserving refactor into a cross-service contract change, which requires the contract-first workflow instead of this task.
- **Promoted to ready/ without the contract reconciliation** — re-creates exactly the documentation gap this blocker exists to prevent; do not skip the prerequisite.

---

# Test Requirements

- No new test scenarios required — behaviour-preserving refactor. All existing tests listed in Acceptance Criteria must pass unmodified with an identical count to baseline.

---

# Definition of Done

- [ ] Prerequisite `procurement-api.md` contract reconciliation completed and merged
- [ ] Promoted backlog → ready
- [ ] Implementation completed
- [ ] Tests passing unmodified (same count as baseline)
- [ ] Contracts unchanged in wire shape (verified)
- [ ] Ready for review
