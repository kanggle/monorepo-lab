# TASK-ERP-BE-034 — Extract read-model Kafka-consumer + envelope-mapper boilerplate (behavior-preserving)

**Status:** done

**Type:** TASK-ERP-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (mechanical extraction, behavior-preserving)

> Filed from the 2026-07-21 reconciliation-audit refactoring rescan (candidates ⑤ + ⑥). Rule-of-3 met and grep-verified.
> Behavior-preserving only — no contract/event/schema change.

---

## Goal

The erp read-model service has two grep-verified duplication clusters that meet the rule of 3. Extract the shared shape
into a small helper each, with **zero behavior change** (same retry/DLT policy, same validation, same logging).

## Scope

**In scope (read-model service):**

1. **4× near-identical Kafka consumers** — `CostCenterChanged`, `DepartmentChanged`, `EmployeeChanged`, `JobGradeChanged`
   consumers share the same retry / DLT / logging skeleton, differing only in the event/command type. Extract the common
   consume→validate→dispatch→(retry/DLT) flow into a shared base/helper; each consumer keeps only its type binding.
2. **3× `*EnvelopeToCommandMapper` try/catch boilerplate** — the three mappers repeat the same
   parse-envelope→validate→wrap-error try/catch. Extract a `parseAndValidate(...)` helper (or a shared base mapper).

**Out of scope:** any envelope/topic/contract change; the masterdata 5× controller CRUD shape (candidate ⑦ — **deferred**:
it collides with `TASK-ERP-BE-033`'s filter work on the same 5 controllers; reconsider only after ERP-BE-033 merges); any
cross-service infrastructure boilerplate (intentional per HARDSTOP-03 / `knowledge/intentional-infrastructure-duplication.md`).

## Acceptance Criteria

- **AC-0 (gate — re-measure; code wins)** — Re-confirm at current `main` that the 4 consumers and 3 mappers are still
  near-identical and still 4/3 in count (a service may have grown a 5th consumer or a mapper may have diverged). If a member
  has diverged so the shape no longer holds, narrow the extraction to the members that still match, or report and stop.
- **AC-1** — The consumer skeleton lives in one place; each of the 4 consumers is reduced to its type binding + the shared call.
- **AC-2** — The mapper try/catch lives in one `parseAndValidate` helper; the 3 mappers use it.
- **AC-3 (behavior-preserving)** — No change to retry counts, DLT routing, error codes, log messages, or emitted metrics.
  Existing read-model unit/slice tests stay GREEN unchanged; the Testcontainers IT lane (CI-Linux-authoritative) is the
  round-trip proof.
- **AC-4** — No new public API, no contract/event doc change.

## Related Specs
- `projects/erp-platform/specs/services/read-model/architecture.md` (consumer / projection design)

## Related Contracts
- None (behavior-preserving; envelopes/topics unchanged).

## Edge Cases
- If the 4 consumers' DLT/retry annotations differ in any parameter, the extraction must preserve each consumer's exact
  policy (parameterize, don't flatten to one policy).

## Failure Scenarios
- **F1 — flattening divergent retry/DLT policies into one.** Guarded by AC-3; verify each consumer's resilience config is
  byte-preserved after extraction.
- **F2 — extracting before re-measuring.** Guarded by AC-0; a diverged member silently breaks the "near-identical" premise.
