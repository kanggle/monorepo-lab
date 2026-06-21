# Task ID

TASK-MONO-302

# Title

ADR-MONO-032 § 3.3 — add a "roadmap COMPLETE" pointer so the execution sketch is not mis-read as open work

# Status

done

# Owner

backend

# Task Tags

- adr
- docs

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

ADR-MONO-032 (unified-identity) is **fully implemented end-to-end** — § 6 line 149 records
"D5 ROADMAP COMPLETE — all of steps 0–5 have landed on `main` (2026-06-15, TASK-MONO-265)",
and the code confirms it (`AccountTypeEnforcementFilter` is role-based, `account_type` claim
removed, ADR-033/034/035 ACCEPTED). However § 3.3 "Future-self … roadmap — sketch" still lists
six `TASK-…` **placeholders** with no completion marker. A reader (or a discovery scan) landing
on § 3.3 without reading as far as § 6 mis-reads the ADR as having an unimplemented execution
roadmap — exactly the phantom-candidate failure mode TASK-MONO-301 fixed for ADR-MONO-041's
acceptance log. (This recurred: a 2026-06-21 backlog discovery scan flagged ADR-032's § 3.3 as
"ACCEPTED but no implementation issued", missing § 6 line 149.)

Add a one-line "✅ ALL SIX STEPS COMPLETE — see § 6" pointer at the top of § 3.3 mapping each
sketch step to its realizing task, so the doc cannot be mis-read as open. No re-litigation of the
decision; § 6 stays the authoritative ledger.

# Scope

## In Scope

- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` § 3.3: a single blockquote pointer
  immediately under the § 3.3 heading recording COMPLETE + the per-step task mapping, with the
  `TASK-…` placeholders kept below for provenance.

## Out of Scope

- Any code/contract/behaviour change (ADR-032 is already realized — verified).
- Editing § 6 (already correct), the decision tables (D1-D6 finalised), or any other ADR.
- The vestigial class name `AccountTypeEnforcementFilter` (now role-based internally) — a cosmetic
  rename is a separate optional task, not required for correctness.

# Acceptance Criteria

- [ ] ADR-MONO-032 § 3.3 carries a "COMPLETE / see § 6" pointer mapping steps 0–5 to their tasks (MONO-255/256, ADR-033+BE-368-370, ADR-034+BE-371-374, ADR-035 4a-4d, MONO-265).
- [ ] The pointer states `account_type` is fully removed and `roles` is the sole authorization axis.
- [ ] No code/contract file changed; § 6 unchanged.

# Related Specs

- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` (edited — § 3.3 only)
- `docs/adr/ADR-MONO-033/034/035-*.md` (the realizing ADRs — unchanged, referenced)

# Related Contracts

- None — docs-only; `platform/contracts/jwt-standard-claims.md` was already rewritten by MONO-255 (step 0).

# Edge Cases

- Keep the original `TASK-…` placeholder list intact (provenance of the ACCEPTED-time sketch); only prepend the pointer.

# Failure Scenarios

- **F1 — recurrence**: without the pointer, the next discovery scan re-flags ADR-032 § 3.3 as actionable (4th time). Guarded by the explicit COMPLETE marker mapping each step to a done task.
