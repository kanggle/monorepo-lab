# TASK-MONO-306 — Amend ADR-MONO-005 § 2.3 D3 to permit a domain-meaningful auto-resolving terminal for Category A sagas

**Status:** ready

**Type:** TASK-MONO (monorepo-level — shared `docs/adr/`)
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (portfolio-wide saga-escalation invariant refinement — decision-bearing ADR amendment, not a mechanical doc edit)

> **Prerequisite of [TASK-BE-435](../../projects/ecommerce-microservices-platform/tasks/ready/TASK-BE-435-stuck-order-cancel-instead-of-stuck-recovery-failed.md).** BE-435 changes the ecommerce order stuck-detector terminal from `STUCK_RECOVERY_FAILED` to `CANCELLED(PAYMENT_TIMEOUT)`. That contradicts ADR-MONO-005 § 2.3 D3 line 67 (a `MUST`) and § 2.6 D6 line 101 (the recorded ecommerce-order decision), so implementing BE-435 without this amendment is **HARDSTOP-09** (architecture decision not in specs). This task makes the policy decision and updates the ADR; BE-435 then implements against it.

---

## Goal

ADR-MONO-005 § 2.3 D3 (Category A multi-step saga sub-rules) currently mandates:

> **Attempt cap** = **5** by default. ... At cap the saga **MUST transition to a terminal `STUCK_RECOVERY_FAILED`-shaped state.** (line 67)

and § 2.6 D6 records the ecommerce order saga as compliant with that terminal, **"No further change"** (line 101).

The ADR's intent (§ 1 Context, lines 33–34) is **uniform operator escalation**: an on-call operator must be able to rely on "I'll get paged when a saga exhausts retries." `STUCK_RECOVERY_FAILED` encodes *"the system gave up — a human must triage."*

For some Category A sagas, however, the stuck condition has a **clean, self-serve resolution** the system can apply automatically without operator action. The ecommerce payment-pending order is the motivating case: a customer who closes the payment widget leaves a `PENDING` order that should simply **cancel** (a customer-meaningful outcome), with money-safety compensation guaranteeing no captured payment is stranded. Forcing it into `STUCK_RECOVERY_FAILED` (operator hand-off, `isCancellable()` excludes it, no refund) is the wrong terminal for that saga.

This task adds a narrowly-scoped **refinement** to § 2.3 D3 permitting a domain-meaningful auto-resolving terminal **iff** the saga still preserves the ADR's escalation/observability contract, and updates the § 2.6 D6 ecommerce-order row to record the new decision.

## Scope

**In scope** — `docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md` only:

1. **§ 2.3 D3 — add an auto-resolving-terminal refinement** to the attempt-cap rule (line 67). Permit, as an explicit alternative to `STUCK_RECOVERY_FAILED`, a **domain-meaningful terminal that auto-resolves the saga** (e.g. `CANCELLED`) **iff ALL** of:
   - **(R1) clean self-serve resolution** — the terminal is a legitimate business outcome of the stuck condition (not "we gave up"), reachable by the aggregate's existing state machine.
   - **(R2) compensation guaranteed** — any external side-effect that may have occurred despite the stuck condition (e.g. a captured-but-unacknowledged payment) is reversed by a defined, idempotent, at-least-once compensation. No value is stranded.
   - **(R3) escalation/observability preserved** — the `<service>.alert.saga.recovery.exhausted` escalation event (or an equivalent informational alert) still fires at cap, and the D3 metric set (line 69–73) is retained, so the operator's monorepo-wide "saga escalated" signal is **not** lost. The terminal name changes; the paging surface does not.
   - **(R4) defensive fallback** — `STUCK_RECOVERY_FAILED`-shaped terminal is retained as the fallback when the auto-resolving compensation cannot be co-committed.
   Sagas that do **not** meet R1–R4 keep the `STUCK_RECOVERY_FAILED`-shaped terminal unchanged (default remains operator escalation).
2. **§ 2.6 D6 — update the ecommerce order saga row** (line 101): record the terminal as `CANCELLED(PAYMENT_TIMEOUT)` (auto-resolving, R1–R4 satisfied) with the escalation event retained as informational; change "No further change." → follow-up `TASK-BE-435`.
3. **§ 6 History / amendment log** — append a row recording this refinement (the ADR's own amendment convention; mirror MONO-232/229 doc-correction style).

**Out of scope:**
- The ecommerce code change itself (that is TASK-BE-435).
- Any change to Category B/C/D sub-rules, metric naming, grace/cap defaults, or the escalation-event topic convention.
- Reclassifying any other saga — the refinement is *available* to all Category A sagas but only the ecommerce order row is re-decided here; other rows stay as-is.
- `rules/traits/transactional.md` / `platform/event-driven-policy.md` D3 pointers (line 114–115) — they reference § D3 generally and remain accurate; touch only if the refinement makes them inaccurate (it should not).

## Acceptance Criteria

- **AC-1** — § 2.3 D3 attempt-cap rule (line 67) carries the R1–R4 auto-resolving-terminal refinement; the default (`STUCK_RECOVERY_FAILED`-shaped, operator escalation) is preserved for sagas not meeting R1–R4.
- **AC-2** — The refinement explicitly requires the escalation event + D3 metrics to be retained (R3), so the ADR's uniform-paging invariant (§ 1 Context) is not weakened.
- **AC-3** — § 2.6 D6 ecommerce-order row updated to the new terminal decision with a `TASK-BE-435` follow-up pointer; no other D6 row changed.
- **AC-4** — § 6 amendment row appended; ADR status stays **ACCEPTED** (refinement, not reversal — D1–D8 decision bodies otherwise byte-unchanged).
- **AC-5** — Doc-only change; no service code, no contract schema touched. HARDSTOP-03 clean (ADR is project-agnostic; ecommerce is named only as the motivating adopter, consistent with existing D6 rows that name services).
- **AC-6** — Internal consistency: no remaining ADR sentence claims `STUCK_RECOVERY_FAILED` is the **only** permissible Category A terminal; the new refinement and the unchanged default do not contradict each other.

## Related Specs

- `docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md` — the amendment target (§ 2.3 D3, § 2.6 D6, § 6).
- `platform/event-driven-policy.md` § Consumer Rules (saga escalation topic convention — referenced, not changed).
- `rules/traits/transactional.md` § Required Artifacts (Category A → § D3 pointer — referenced, not changed).

## Related Contracts

- None directly. The downstream contract change (`OrderCancelled.cancelReason`) lives in TASK-BE-435 under the ecommerce project's `specs/contracts/events/order-events.md`.

## Dependencies

- **Downstream:** `TASK-BE-435` (ecommerce) depends on this — implement BE-435 only after this amendment is merged (or land them atomically with the ADR amendment committed first within the same PR).
- **Prior art:** `TASK-MONO-232` / `TASK-MONO-229` (ADR doc-correction/amendment pattern — same § 6 history-row convention).

## Edge Cases

- **Refinement read as a blanket relaxation** — a future author might cite R1–R4 to drop operator escalation entirely. Guarded by R3 (escalation event + metrics MUST remain) and AC-2: the terminal *name/semantics* may change, the *paging surface* may not.
- **Compensation undefined** — permitting `CANCELLED` without R2 would re-introduce the "money stranded" hole. The refinement makes R2 a hard precondition, so a saga cannot adopt the auto-resolving terminal without a defined compensation.
- **Other Category A sagas (wms outbound, future scm/fan)** — unaffected; they neither meet nor need R1–R4 and keep the default terminal. The refinement is opt-in per-saga via a D6 decision.

## Failure Scenarios

- **F1 — invariant erosion** — the amendment quietly weakens the uniform-escalation guarantee the ADR exists to provide. Guarded by R3/AC-2 (escalation + metrics retained) and by keeping the default unchanged.
- **F2 — ADR/code drift** — BE-435 ships the `CANCELLED` terminal while the ADR still mandates `STUCK_RECOVERY_FAILED`. This task is the prerequisite that removes the drift; the dependency ordering (AC / Dependencies) enforces amend-before-implement.
- **F3 — status over-reach** — flipping ADR status or editing D1–D8 decision bodies would overstate the change. Guarded by AC-4 (refinement only, ACCEPTED retained, bodies byte-unchanged).
