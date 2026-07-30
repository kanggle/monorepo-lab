# Task ID

TASK-PC-FE-267

# Title

console-web rename colliding `periodStatusTone` — disambiguate ledger vs ecommerce settlement

# Status

done

# Owner

frontend

# Task Tags

- code

---

# Required Sections (must exist)

- Goal
- Scope
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

Found incidentally during TASK-PC-FE-265's sibling-convention audit (noted there as a separate future candidate, not part of the original 5-finding re-scan). Two UNRELATED domains each export a function named `periodStatusTone`:

- `shared/api/ledger-types/period.ts` (finance ledger accounting periods, promoted to `shared/` by TASK-PC-FE-259 because `features/ledger-ops` and other finance reads consume it): `OPEN → progress`, `CLOSED → success`, else `neutral`.
- `features/ecommerce-ops/api/settlement-types.ts` (ecommerce seller-settlement periods): `OPEN → progress`, `CLOSED → success`, else `neutral` — currently **byte-identical logic** to the ledger one.

Unlike TASK-PC-FE-263/264 (genuine shared UI vocabulary reimplemented per-domain, correctly centralized), this is the SAME shape of collision TASK-PC-FE-265 resolved for `accountStatusTone`: two domains that are conceptually unrelated (finance-ledger accounting periods vs ecommerce seller-settlement periods) coincidentally chose the same `OPEN`/`CLOSED` two-state vocabulary and therefore the same tone mapping. **This task follows TASK-PC-FE-265's precedent — rename to disambiguate, do NOT merge**: merging would couple two unrelated domains for a coincidental value match; the ledger module already lives in `shared/api/ledger-types/` specifically because it's a genuine finance-wide concept (per TASK-PC-FE-259), and folding an ecommerce-specific concern into it would be an unwanted new coupling, not a cleanup. The two mappings are free to diverge in the future (e.g. ecommerce settlement periods gaining a `PENDING` status) without that being a "drift bug" — they were never the same concept.

---

# Scope

## In Scope

1. **`shared/api/ledger-types/period.ts`** — rename `periodStatusTone` → `ledgerPeriodStatusTone`. Single production consumer chain: re-exported via `features/ledger-ops/api/types/index.ts`'s `export * from '@/shared/api/ledger-types/period'` barrel, consumed by `features/ledger-ops/components/PeriodsTable.tsx` and `features/ledger-ops/components/PeriodDetail.tsx` (both import from `'../api/types'`, unaffected by the rename itself — only the identifier they import changes).

2. **`features/ecommerce-ops/api/settlement-types.ts`** — rename `periodStatusTone` → `settlementPeriodStatusTone` (matches the file's own `settlement-` domain scope). Single production consumer: `features/ecommerce-ops/components/SettlementPeriodsTable.tsx` (imports directly from `'../api/settlement-types'`).

3. **Update the direct unit test**: `tests/unit/finance-ledger-status-tone.test.ts` — its `periodStatusTone` import (from `@/features/ledger-ops/api/types`) and all call sites renamed to `ledgerPeriodStatusTone`; assertions/expected values unchanged. (This test file does not currently exercise the ecommerce settlement `periodStatusTone` — confirm via grep before finishing that no other test file does either.)

## Out of Scope

- Merging the two tone mappings into one shared function — explicitly declined (see Goal); the identical-today logic is coincidental, not a shared concept.
- Any other export in `period.ts`/`settlement-types.ts` (`KNOWN_PERIOD_STATUSES`/`PERIOD_STATUS_VALUES`, `PeriodSchema`/etc.) — only the colliding function name is in scope.
- Any behavior change. `ledgerPeriodStatusTone`/`settlementPeriodStatusTone` must return byte-identical `StatusTone` values for every input the originals handled.

---

# Acceptance Criteria

- [ ] `shared/api/ledger-types/period.ts` exports `ledgerPeriodStatusTone` (renamed from `periodStatusTone`).
- [ ] `features/ecommerce-ops/api/settlement-types.ts` exports `settlementPeriodStatusTone` (renamed from `periodStatusTone`).
- [ ] `features/ledger-ops/components/PeriodsTable.tsx` and `PeriodDetail.tsx` updated to import/call `ledgerPeriodStatusTone`.
- [ ] `features/ecommerce-ops/components/SettlementPeriodsTable.tsx` updated to import/call `settlementPeriodStatusTone`.
- [ ] `tests/unit/finance-ledger-status-tone.test.ts` updated to `ledgerPeriodStatusTone`; assertions unchanged.
- [ ] Grep for the bare identifier `periodStatusTone` across `apps/console-web/src` and `apps/console-web/tests` returns zero matches after the rename.
- [ ] `tsc --noEmit` passes with zero new errors.
- [ ] `pnpm lint` passes with zero new errors.
- [ ] Full `console-web` unit test suite (vitest) passes unchanged — same pass count, no assertion values modified.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification (platform-console: domain=saas, traits=[multi-tenant, integration-heavy, audit-heavy]). Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/refactoring-policy.md` § Allowed Refactoring Categories → **Rename** (Low risk); this task performs Rename only, no behavior change.
- TASK-PC-FE-265 (`accountStatusTone` → `iamAccountStatusTone`/`financeAccountStatusTone`) — the direct precedent this task follows: rename to disambiguate a coincidental cross-domain name collision, do not merge.
- TASK-PC-FE-259 — established that `shared/api/ledger-types/period.ts` is promoted specifically because it's a genuine finance-wide read, not a general-purpose module; this task's rename respects that boundary rather than blurring it by merging in an ecommerce concern.

# Related Skills

- `.claude/skills/frontend/architecture/layered-by-feature/SKILL.md`

---

# Related Contracts

None — no API or event contract touched; pure internal rename. Verified via `tests/unit/parity-matrix.ts` that `periodStatusTone` is not a tracked `console-integration-contract` `clientExport` attestation row.

---

# Target App

- `apps/console-web`

---

# Implementation Notes

- Grep-verify the full importer list for `periodStatusTone` BEFORE starting and again AFTER finishing (per TASK-PC-FE-263/264/265's verification method) — the "after" grep for the bare old name should return zero results anywhere in `src/` or `tests/`.
- Do not touch `KNOWN_PERIOD_STATUSES`/`PERIOD_STATUS_VALUES`/`PeriodStatus` type names — those are not colliding (`ledger-types/period.ts` uses `KnownPeriodStatus`, `settlement-types.ts` uses `PeriodStatus` — already distinct).

---

# Edge Cases

- None beyond the standard rename-verification (grep old name = 0, tsc/lint/vitest green) — this is a same-signature rename with zero call-shape change on either side.

---

# Failure Scenarios

- `tsc --noEmit` fails after rename → fix the missed reference, do not leave a re-export shim under the old name.
- Vitest suite fails post-rename → indicates a missed reference, not a false positive; revert and re-diagnose (per `platform/refactoring-policy.md` Prohibited: "Refactoring production code and test code in the same change" — only the identifier rename is being made in the test file, no assertion changes).

---

# Test Requirements

- No new tests required — pure rename with zero behavior change; `tests/unit/finance-ledger-status-tone.test.ts`'s existing assertions are the verification mechanism and must pass unmodified.

---

# Definition of Done

- [ ] All 3 scope items completed
- [ ] `tsc --noEmit` clean
- [ ] `pnpm lint` clean
- [ ] Full `console-web` vitest suite passing, same pass count, zero assertion values modified
- [ ] Ready for review
