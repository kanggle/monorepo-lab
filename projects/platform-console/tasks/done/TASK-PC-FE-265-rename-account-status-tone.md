# Task ID

TASK-PC-FE-265

# Title

console-web rename colliding `accountStatusTone` — domain-qualify IAM vs Finance

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

Found by the same fresh `console-web` naming-convention re-scan (2026-07-30) that produced TASK-PC-FE-263/264, finding #3. Two UNRELATED domains each export a function named `accountStatusTone` with genuinely DIFFERENT logic — a true naming collision (not duplication, unlike TASK-PC-FE-263/264):

- `features/accounts/components/AccountStatusBadge.tsx` (IAM operator accounts): `ACTIVE → success`, `LOCKED → danger`, else `neutral`.
- `shared/api/finance-accounts-types.ts` (Finance KYC accounts), re-exported through `features/finance-ops/api/types.ts`: `PENDING_KYC/RESTRICTED → warning`, `ACTIVE → success`, `FROZEN → danger`, `CLOSED → neutral`.

Per the sibling-convention audit in the re-scan: of ~20 `<entity>StatusTone` functions in the codebase, none other reuses a name across two domains — each is qualified by its own specific business-entity noun (`tenantStatusTone`, `orderStatusTone`, `sellerStatusTone`, `poStatusTone`, etc.). `accountStatusTone` is the only name collision in that set (this task fixes it; it does not attempt to fix `periodStatusTone`, which independently also exists in both `shared/api/ledger-types/period.ts` and `features/ecommerce-ops/api/settlement-types.ts` — out of scope, not part of the re-scan's original 5 findings, a separate future candidate if picked up).

**Investigated and DECLINED as part of this task's original framing**: the re-scan's finding #4 (`AccountDetail`/`AccountLookup` component-name reuse between `features/finance-ops` and `features/ledger-ops`) was bundled with this task in the initial recommendation, but investigation before implementation found NO genuine collision risk — the two pairs are never imported together (each resolved via its own feature-relative path), and every rendered `data-testid` is already domain-prefixed (`finance-account-*` vs `ledger-account-*`). Renaming 4 components + updating a test file that exercises `ledger-ops`'s `AccountDetail` 9 times would be pure churn with no risk/ambiguity actually resolved — declined, consistent with TASK-PC-FE-260's own precedent of leaving `features/accounts`'s `ConfirmActionDialog` un-renamed when no genuine collision exists. Not part of this task's scope.

---

# Scope

## In Scope

1. **`features/accounts/components/AccountStatusBadge.tsx`** — rename the local function `accountStatusTone` → `iamAccountStatusTone` (matches the `iam-` domain-prefix convention already established at the shared-lib layer by TASK-PC-FE-259's `iam-{accounts,audit,operators}-{read,types}.ts`). Single-file change — grep-confirmed this function has NO external importer (only `AccountStatusBadge` the component, not the tone function itself, is imported elsewhere, by `AccountsTable.tsx`, which is unaffected).

2. **`shared/api/finance-accounts-types.ts`** — rename the exported function `accountStatusTone` → `financeAccountStatusTone` (matches the `finance-` prefix already used in this file's own name and in `features/finance-ops`'s other qualified exports like `txnStatusTone`'s sibling area).

3. **`features/finance-ops/api/types.ts`** — update the re-export (line ~88: `accountStatusTone` → `financeAccountStatusTone`) in the barrel that re-exports `shared/api/finance-accounts-types.ts` (this barrel is the finance-ops public surface per `console-integration-contract.md` § 2.4.7 discipline established by TASK-PC-FE-259 — verified via `tests/unit/parity-matrix.ts`/`parity-verification.test.ts` that `accountStatusTone` is NOT one of the tracked `clientExport` attestation rows, so this rename does not touch the guarded parity surface).

4. **Update both importers** of the finance-side function: `features/finance-overview/components/FinanceOverviewScreen.tsx` and `features/finance-ops/components/AccountDetail.tsx` — import + call-site renamed to `financeAccountStatusTone`.

5. **Update the direct unit test**: `tests/unit/finance-ledger-status-tone.test.ts` — import + all 7 call-sites renamed to `financeAccountStatusTone` (describe-block string may also be updated for clarity); assertions/expected values unchanged.

## Out of Scope

- `AccountDetail`/`AccountLookup` component-name reuse between `finance-ops`/`ledger-ops` (investigated and declined — see Goal).
- The `periodStatusTone` collision between `shared/api/ledger-types/period.ts` and `features/ecommerce-ops/api/settlement-types.ts` discovered incidentally during this task's sibling-convention audit — not part of the original re-scan's 5 findings, a separate future candidate.
- The remaining finding from the same re-scan (`ecommerce-ops/api/types.ts` internal products/promotions split) — tracked separately.
- Any behavior change. `iamAccountStatusTone`/`financeAccountStatusTone` must return byte-identical `StatusTone` values for every input the originals handled.

---

# Acceptance Criteria

- [ ] `features/accounts/components/AccountStatusBadge.tsx` exports/uses `iamAccountStatusTone` (renamed from `accountStatusTone`); no other file references the old name in this feature.
- [ ] `shared/api/finance-accounts-types.ts` exports `financeAccountStatusTone` (renamed from `accountStatusTone`).
- [ ] `features/finance-ops/api/types.ts`'s re-export barrel and both of its 2 consumers (`FinanceOverviewScreen.tsx`, `finance-ops/AccountDetail.tsx`) use `financeAccountStatusTone`.
- [ ] `tests/unit/finance-ledger-status-tone.test.ts` uses `financeAccountStatusTone`; all existing assertions (input → expected tone) unchanged.
- [ ] Grep for the bare identifier `accountStatusTone` across `apps/console-web/src` and `apps/console-web/tests` returns zero matches after the rename.
- [ ] `tsc --noEmit` passes with zero new errors.
- [ ] `pnpm lint` passes with zero new errors.
- [ ] Full `console-web` unit test suite (vitest) passes unchanged — same pass count, no assertion values modified.
- [ ] `parity-verification.test.ts` still passes (confirms the rename did not touch a tracked `clientExport` attestation row).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification (platform-console: domain=saas, traits=[multi-tenant, integration-heavy, audit-heavy]). Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/refactoring-policy.md` § Allowed Refactoring Categories → **Rename** (Low risk); this task performs Rename only, one category, no behavior change.
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.7 — the finance-ops public surface this task's item 3 touches; verified (see Implementation Notes) that `accountStatusTone` is not a tracked parity-attestation `clientExport` row.
- `projects/platform-console/docs/conventions/frontend-ui.md` — sibling `<entity>StatusTone` naming convention this task aligns both functions to.

# Related Skills

- `.claude/skills/frontend/architecture/layered-by-feature/SKILL.md`

---

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.7 — read-only verification only (confirmed the renamed export is not a tracked attestation row); no contract document edit needed.

---

# Target App

- `apps/console-web`

---

# Implementation Notes

- Grep-verify `accountStatusTone` importer list BEFORE starting and again AFTER finishing (per TASK-PC-FE-263/264's verification method) — the "after" grep for the bare old name should return zero results anywhere in `src/` or `tests/`.
- Do NOT touch `features/accounts/components/AccountsTable.tsx` — it imports the `AccountStatusBadge` component (unaffected name), not the `accountStatusTone`/`iamAccountStatusTone` function directly.
- `tests/unit/parity-matrix.ts` + `tests/unit/parity-verification.test.ts` were checked BEFORE writing this task (grep for `clientExport`) — `accountStatusTone` does not appear among the tracked rows (those track IAM mutating actions like `searchAccounts`/`lockAccount`/`createOperator`, not status-tone rendering helpers). Still worth a final green run of `parity-verification.test.ts` in Acceptance Criteria as a belt-and-suspenders check.

---

# Edge Cases

- None beyond the standard rename-verification (grep old name = 0, tsc/lint/vitest green) — this is a same-signature rename with zero call-shape change on either side.

---

# Failure Scenarios

- `tsc --noEmit` fails after rename → fix the missed reference, do not leave a re-export shim under the old name (this task's goal is to eliminate the collision, not paper over it with an alias).
- `parity-verification.test.ts` fails post-rename → STOP and re-investigate; it would mean the pre-implementation check that `accountStatusTone` isn't a tracked attestation row was wrong, and this rename may be touching a guarded contract surface after all.
- Vitest suite fails post-rename → indicates a missed reference, not a false positive; revert and re-diagnose (per `platform/refactoring-policy.md` Prohibited: "Refactoring production code and test code in the same change" — only identifier renames are being made in the test file, not new/changed assertions).

---

# Test Requirements

- No new tests required — this is a pure rename with zero behavior change; `tests/unit/finance-ledger-status-tone.test.ts`'s existing assertions (values unchanged, only the imported/called identifier renamed) are the verification mechanism.

---

# Definition of Done

- [ ] All 5 scope items completed
- [ ] `tsc --noEmit` clean
- [ ] `pnpm lint` clean
- [ ] Full `console-web` vitest suite passing, same pass count, zero assertion values modified
- [ ] Ready for review
