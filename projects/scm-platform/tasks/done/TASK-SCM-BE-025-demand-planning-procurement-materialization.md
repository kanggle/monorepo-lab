# Task ID

TASK-SCM-BE-025

# Title

Materialize an approved reorder suggestion into a procurement DRAFT PO (ADR-MONO-027 D5) + sku→supplier mapping apply + open-suggestion guard close-out. Intra-scm, operator-gated, no auto-SUBMIT. impl.

# Status

done

# Owner

backend

# Task Tags

- code
- api

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **선행 (prerequisite)**: [TASK-SCM-BE-024](TASK-SCM-BE-024-demand-planning-service-bootstrap.md) (service + suggestions exist) + [TASK-SCM-BE-023](../ready/TASK-SCM-BE-023-demand-planning-service-spec.md) (procurement additive entry specced). **backlog → ready after BE-024 merges.**
- **후속**: TASK-SCM-INT-002 (E2E proves the full loop).

# Goal

Close the replenishment loop's last in-project leg: operator approves a `reorder_suggestion` → demand-planning resolves the supplier via `sku_supplier_map` → procurement creates a **DRAFT** PO (reusing its existing lifecycle) → the operator reviews and uses procurement's existing `DRAFT → SUBMITTED` path to dispatch. demand-planning never submits.

# Scope

## In Scope

- **procurement additive entry** (per BE-023 spec): internal endpoint/use-case to create a PO in `DRAFT` from a suggestion payload (`supplierId`, lines `[{skuCode, qty, unitPriceRef}]`, `currency`, `origin=DEMAND_PLANNING`, `sourceSuggestionId`). Reuses existing `DRAFT` state, PoStatusMachine, audit/outbox. **No new PO state, no auto-SUBMIT.** Idempotent on `sourceSuggestionId` (re-call returns the existing PO).
- **demand-planning `POST /suggestions/{id}/approve`**: guard status `SUGGESTED→APPROVED`, resolve `sku_supplier_map` (unmapped → `SKU_SUPPLIER_UNMAPPED` 422, suggestion stays SUGGESTED), call procurement (intra-scm internal REST, v1), on success set `MATERIALIZED` + `materialized_po_id`. Idempotent (already-MATERIALIZED → return linked poId; `SUGGESTION_ALREADY_MATERIALIZED` guard).
- **open-suggestion guard interplay**: MATERIALIZED/DISMISSED release the partial-unique guard so a future stock drop can re-suggest (D6).
- **Tests**: unit (approve state transitions, unmapped-SKU, idempotent re-approve), slice (approve endpoint), Testcontainers IT (approve → procurement DRAFT PO created with `origin=DEMAND_PLANNING` + `sourceSuggestionId`; re-approve → no duplicate PO; unmapped → 422 + suggestion unchanged).

## Out of Scope

- Actual supplier dispatch (`DRAFT → SUBMITTED → …`) — existing procurement operator flow, unchanged.
- Pricing logic — `unitPriceRef` is a reference/placeholder per BE-023; operator sets real price at review.
- Intra-scm event transport (vs REST) — REST chosen for v1; event option is v2 (ADR-027 D5).
- federation live proof — TASK-SCM-INT-002.

# Acceptance Criteria

- **AC-1** Approving a SUGGESTED suggestion creates a procurement PO in `DRAFT` carrying `origin=DEMAND_PLANNING` + `sourceSuggestionId`; suggestion → `MATERIALIZED` with `materialized_po_id` set.
- **AC-2** Re-approving (or duplicate call) yields **no** second PO — idempotent on `sourceSuggestionId`; returns the existing poId.
- **AC-3** Unmapped `skuCode` → `SKU_SUPPLIER_UNMAPPED` (422); suggestion stays `SUGGESTED`; no PO created.
- **AC-4** The created PO is **DRAFT only** — never auto-SUBMITted; procurement's existing lifecycle/states byte-unchanged (regression assertion).
- **AC-5** MATERIALIZED/DISMISSED release the open-suggestion guard (a subsequent low-stock for the same SKU+warehouse can raise a new suggestion).
- **AC-6** scm Integration job green; procurement existing IT regression 0.

# Related Specs

- [ADR-MONO-027](../../../../docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md) D5 (materialization), D6 (guard)
- [TASK-SCM-BE-023](../ready/TASK-SCM-BE-023-demand-planning-service-spec.md) (procurement additive entry + approve route specced)
- `specs/services/procurement-service/architecture.md` (DRAFT state + PoStatusMachine reused)

# Related Contracts

- `demand-planning-api.md` (approve), `procurement-api.md` (DRAFT-PO-from-suggestion additive)

# Edge Cases

- **suggestion approved but procurement call fails mid-way**: leave suggestion `APPROVED` (not MATERIALIZED), no partial PO; retry-safe (idempotent on `sourceSuggestionId`). Document the compensation-free retry.
- **two operators approve the same suggestion concurrently**: optimistic lock / status guard ensures one PO; the loser sees `SUGGESTION_ALREADY_MATERIALIZED` or the same linked poId.
- **supplier_id resolved but supplier unknown to procurement**: procurement does not FK-validate supplier (FK-free convention) — PO DRAFT created with the reference; operator validates at review.

# Failure Scenarios

- **procurement service down at approve time**: approve returns 5xx, suggestion stays APPROVED, operator retries — idempotent. No orphaned PO.
- **mapping seeded wrong supplier**: operator catches it at DRAFT review (the whole point of suggestion-only, D2). No financial commitment occurred.

# Notes

- 분석=Opus 4.8 / 구현 권장=Opus (cross-service idempotent materialization + state-guard interplay = correctness-sensitive).
- This is the leg that keeps the human in the loop where money is committed (ADR-027 D2). The DRAFT PO is the review surface.
