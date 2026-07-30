# Task ID

TASK-PC-FE-269

# Title

console-web migrate 4 more confirm-dialog instances (broader `role="dialog"` sweep) to `shared/ui/ConfirmDialog`

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

TASK-PC-FE-268's own verification grep surfaced ~28 total `role="dialog"` files in `console-web`, of which only 4 were investigated (all migrated). The remaining ~24 were left as an explicitly unverified future candidate pool. A dedicated investigation (2026-07-30) read all ~14 remaining named files/nested components and classified each: most are genuinely different shapes (multi-field create/edit forms, multi-mode form engines, read-only detail panels, multi-step wizards) that would be **harmed**, not helped, by forcing them into `shared/ui/ConfirmDialog`'s single-confirm shape — those are explicitly Out of Scope below. **4 are genuine confirm-gate duplicates** of the same shell `shared/ui/ConfirmDialog.tsx` already promotes (TASK-PC-FE-262, extended by TASK-PC-FE-268):

- **`DiscrepancyResolveDialog`** (`features/ledger-ops/components/`) — title/description + a `resolutionType` select + required `note` textarea (children) + inline error + Cancel/Confirm. Has Escape-to-cancel and an explicit initial-focus-on-note effect, but **no Tab-loop focus trap**.
- **`OperatorProfileEditDialog`** + its split body `OperatorProfileEditDialogBody` (`features/operators/components/`) — title/description + value input + Clear checkbox + required reason textarea (children) + inline error + Cancel/Save. **Already has** a full Tab-loop focus trap, Escape-to-cancel, and auto-focus-first-input — this migration is pure dedup with **zero new a11y side effects**, unlike the other 3.
- **`ApprovalReasonDialog`** — a private (non-exported), nested component inside `features/erp-ops/components/ApprovalDetail.tsx`, used only for the reject/withdraw reason gate. Title (dynamic "반려 사유"/"회수 사유") directly followed by a reason textarea (children) + inline validation + Cancel/Confirm (always destructive-styled). **No focus trap, no Escape handling, no description text** (the title alone stands in for it) today.
- **`DelegationRevokeDialog`** — a private, nested component inside `features/erp-ops/components/DelegationScreen.tsx`, used only for the revoke-delegation reason gate. Title + a one-line delegate-id summary (description) + required reason textarea (children) + inline error + Cancel/Confirm (always destructive-styled). **No focus trap, no Escape handling; its Cancel button has no `data-testid` at all today.**

Migrating these 4 follows the exact precedent TASK-PC-FE-268 set for its own 3 (the Tab-loop trap and Escape-during-pending are already-reviewed, intentional, documented side effects of this migration pattern — not new risk to re-litigate).

---

# Scope

## In Scope

1. **`shared/ui/ConfirmDialog.tsx`** — add one small additive extension: an optional prop to attach a caller-supplied custom attribute to the dialog frame element (e.g. `dialogAttrs?: Record<string, string>`, spread onto the `role="dialog"` div). This is needed because `ApprovalScreen.test.tsx:256` asserts `expect(dialog).toHaveAttribute('data-transition', 'reject')` directly against `ApprovalReasonDialog`'s frame — this is a genuinely load-bearing, tested attribute, not incidental markup (verified via grep — it is the *only* runtime consumer of `data-transition`). Purely additive (undefined by default) — the now-13 pre-existing call sites (9 from FE-262 + 3 from FE-268 — wait, cancelLabel note: 9 original + FE-268's 3 = 12 existing, none pass this new prop) are byte-unchanged in behavior.

2. **`features/ledger-ops/components/DiscrepancyResolveDialog.tsx`** — becomes a thin wrapper delegating to `shared/ui/ConfirmDialog`. Keeps its exact public `DiscrepancyResolveDialogProps` interface (`discrepancyId`, `pending`, `error`, `onCancel`, `onConfirm`) — zero changes to its 1 existing caller (`DiscrepancyDetail.tsx`). `title`="대사 차이 해소", `description`=the existing 2-line paragraph, `confirmLabel`="해소", `confirmDisabled`=`!noteOk`, `initialFocusRef`=the existing `noteRef` (preserves today's focus-the-note behavior), `children`=the `resolutionType` select + `note` textarea + note-required-error paragraph (moved in verbatim), `errorMessage`=the mapped `discrepancyResolveErrorMessage(error)` result. `dialogAttrs={{ 'data-discrepancy-id': discrepancyId }}` (the new prop from item 1 — this attribute has **zero test consumers today**, grep-confirmed, but preserving it costs nothing and avoids a silent, undocumented attribute loss). All existing testids (`ledger-recon-resolve-overlay`/`-dialog`/`-type`/`-note`/`-note-error`/`-error`/`-cancel`/`-confirm`) preserved exactly.

3. **`features/operators/components/OperatorProfileEditDialog.tsx`** — becomes a thin wrapper. Keeps its exact public `OperatorProfileEditDialogProps` interface (`open`, `operatorIdLabel`, `initialDefaultAccountId`, `pending`, `errorMessage`, `onConfirm`, `onCancel`) — zero changes to its 1 existing caller (`OperatorsScreen.tsx`) and the dedicated `tests/unit/features/operators/OperatorProfileEditDialog.test.tsx`. `title`="프로파일 편집 — {operatorIdLabel}", `description`=the existing hint `<p data-testid="operator-profile-edit-hint">` (pass the element itself as the `description` ReactNode — its own testid survives regardless of the wrapping element `shared/ui/ConfirmDialog` puts around `description`), `confirmLabel`="저장", `confirmDisabled`=`!(reasonOk && valueOk)` (exclude `pending` from this expression — `ConfirmDialog` already ORs `pending` into its own disabled check, so including it again would be redundant, not wrong, but keep the wrapper's expression minimal), `initialFocusRef`=the existing `valueRef` (preserves today's focus-first-input behavior), `children`=the value-input block + Clear-checkbox block + reason-textarea block (moved in verbatim from `OperatorProfileEditDialogBody`), `errorMessage`=`errorMessage` passthrough. Testids preserved: `operator-profile-edit-overlay`/`-dialog`/`-hint`/`-value`/`-value-error`/`-clear`/`-reason`/`-reason-required`/`-error`/`-cancel`/`-save`. **Delete `OperatorProfileEditDialogBody.tsx`** (single consumer, folded into the wrapper's `children`). This is the one migration of the 4 with **no new a11y side effects** — the original already had a full focus trap + Escape handling; verify the migrated version's observable keyboard behavior is unchanged (not merely "close enough").

4. **`ApprovalReasonDialog`** (private component nested in `features/erp-ops/components/ApprovalDetail.tsx`) — becomes a thin wrapper delegating to `shared/ui/ConfirmDialog`. Stays non-exported — it is only used inline by `ApprovalDetail` itself, so its own tiny prop shape (`transition`, `pending`, `onCancel`, `onConfirm`) is not a public API and may be adjusted freely as long as `ApprovalDetail`'s own external behavior (props, testids, callers) is unchanged. `title`=`` `${verb} 사유` `` (verb = "반려"/"회수" per `transition`), `description`="" (no natural description text exists today — the title stands alone before the reason field; passing an empty string leaves a harmless empty `<div>` where the description slot would render — verify no meaningful visual regression, this is the one judgment call in this task), `confirmLabel`=`verb`, `destructive`=`true` (always, matching today's unconditional destructive styling), `confirmDisabled`=`!ok`, `dialogAttrs={{ 'data-transition': transition }}` (load-bearing — see Scope item 1), `children`=the reason label + textarea + validation-error paragraph (replace the static `id="approval-reason"` with `useId()`, per the `OrgReasonDialog`/`GroupReasonDialog` precedent from TASK-PC-FE-268). No `initialFocusRef` (today's version has no explicit focus management — gaining the shared primitive's default "auto-focus the Confirm button" behavior mirrors TASK-PC-FE-268's `AcknowledgeAlertDialog` precedent, which also had no `initialFocusRef`). Testids preserved: `approval-reason-overlay`/`-dialog`/`-input`/`-error`/`-cancel`/`-confirm`.

5. **`DelegationRevokeDialog`** (private component nested in `features/erp-ops/components/DelegationScreen.tsx`) — becomes a thin wrapper. Stays non-exported (prop shape `grant`, `onClose` may be adjusted freely — not public API). `title`="위임 회수", `description`=the existing 대결자 summary paragraph, `confirmLabel`="회수", `destructive`=`true` (always, matches today), `confirmDisabled`=`!ok`, `children`=the reason label + textarea + validation-error paragraph (replace the static `id="delegation-revoke-reason"` with `useId()`), `errorMessage`=the mapped `approvalErrorMessage(revokeM.error)` result when `revokeM.error` is truthy. No `initialFocusRef` (same reasoning as item 4). **Testid remapping** (see Edge Cases — today's `delegation-revoke-dialog` testid sits on the OUTER overlay div, not the inner `role="dialog"` frame): use `dialogTestId="delegation-revoke-dialog"` (same value, moved to the frame element `shared/ui/ConfirmDialog` renders — verify this does not break `DelegationScreen.test.tsx`'s existing assertions, which almost certainly just check the testid is present/visible, not which specific DOM node carries it). `errorTestId="delegation-error"` (preserved). `confirmTestId="delegation-revoke-confirm"` (preserved). **New** `cancelTestId="delegation-revoke-cancel"` (the Cancel button has no testid today at all — purely additive, cannot break an existing assertion since none existed against it; grep-verify this before assuming).

## Out of Scope

- All ~9 other files/components the same investigation classified as **not-a-candidate**: `OrgHierarchyScreen.tsx`'s `CreateNodePanel` (multi-field, "다음"-step), `GroupGrantDialog.tsx` (multi-field, "다음"-step), `GroupMemberDialog.tsx` (single-input data-entry step), `PoDetailDialog.tsx` (strictly read-only, no confirm affordance), `OrgScopeDialog.tsx`/`OrgScopeDialogBody.tsx` (tri-state selector + subset picker), `OperatorProfileEditDialog`'s sibling `OperatorConfirmDialog`-style relatives are unaffected, `ApprovalCreateDialog.tsx` (multi-field create form with a dynamic approver-row list), `ApprovalDetail` itself (multi-action read-heavy detail panel — only its nested `ApprovalReasonDialog` is in scope), `DelegationCreateDialog` nested in `DelegationScreen.tsx` (multi-field create form — only its sibling `DelegationRevokeDialog` is in scope), `DepartmentWriteDialog.tsx` and `MasterWriteDialog.tsx` (multi-mode/dynamic-field form engines — forcing even their single-mode "retire" path into `ConfirmDialog` would fragment their mode-branching architecture; not attempted).
- Any other `role="dialog"` instance not named in this task or TASK-PC-FE-262/268's own investigations.
- Behavior changes beyond the ones explicitly documented per item above (Tab-loop focus trap gained by items 2, 4, 5; Escape-to-cancel gained by items 2, 4, 5; default confirm-button auto-focus gained by items 4, 5 in the absence of an `initialFocusRef`). Item 3 (`OperatorProfileEditDialog`) must have **zero** new observable behavior — it already had full keyboard handling.

---

# Acceptance Criteria

- [ ] `shared/ui/ConfirmDialog.tsx` has a new optional `dialogAttrs` prop (or equivalently-scoped passthrough), spread onto the dialog frame element; the 12 pre-existing call sites (9 from FE-262 + 3 from FE-268) are unmodified.
- [ ] `DiscrepancyResolveDialog.tsx` delegates to the shared primitive; its 1 existing caller (`DiscrepancyDetail.tsx`) unchanged; all pre-existing testids preserved; `data-discrepancy-id` attribute preserved on the frame.
- [ ] `OperatorProfileEditDialog.tsx` delegates to the shared primitive; `OperatorProfileEditDialogBody.tsx` no longer exists; its 1 existing caller (`OperatorsScreen.tsx`) unchanged; the dedicated `OperatorProfileEditDialog.test.tsx` passes unmodified; all pre-existing testids preserved; **no new observable keyboard/focus behavior** (this component already had a full focus trap + Escape handling — verify parity, not just "close enough").
- [ ] `ApprovalReasonDialog` (nested in `ApprovalDetail.tsx`) delegates to the shared primitive; `ApprovalDetail`'s own public props/testids/callers unchanged; `ApprovalScreen.test.tsx`'s reject-flow assertions (including `toHaveAttribute('data-transition', 'reject')` at line 256) pass unmodified; pre-existing testids preserved; documented deviations (focus trap + Escape gained, default confirm-button auto-focus gained) verified, not silently introduced elsewhere.
- [ ] `DelegationRevokeDialog` (nested in `DelegationScreen.tsx`) delegates to the shared primitive; `DelegationScreen`'s own public props/testids/callers unchanged; `DelegationScreen.test.tsx` passes unmodified; `delegation-revoke-dialog` testid still resolves (now on the frame element, not the overlay); new `delegation-revoke-cancel` testid added; documented deviations verified.
- [ ] Grep for each of the 2 externally-imported dialogs' import specifiers (`DiscrepancyResolveDialog`, `OperatorProfileEditDialog`) across `apps/console-web/src` and `apps/console-web/tests` — the caller list (1 + 1) is byte-unchanged before/after. For the 2 private/nested components (`ApprovalReasonDialog`, `DelegationRevokeDialog`), confirm they remain non-exported with zero external importers before/after.
- [ ] `tsc --noEmit` passes with zero new errors.
- [ ] `pnpm lint` passes with zero new errors.
- [ ] Full `console-web` unit test suite (vitest) passes unchanged — same pass count (baseline: confirm current count before starting), no test assertion **values** modified.
- [ ] Net LOC decreases (duplication removed, not just moved).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification (platform-console: domain=saas, traits=[multi-tenant, integration-heavy, audit-heavy]). Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/refactoring-policy.md` § Allowed Refactoring Categories → **Reduce Duplication** (Medium risk); duplication outranks naming per § Prioritization.
- TASK-PC-FE-262 (`shared/ui/ConfirmDialog.tsx` origin) and TASK-PC-FE-268 (its first extension, `cancelLabel`) — this task's direct precedent and second continuation. TASK-PC-FE-268's own Out of Scope section explicitly named the broader `role="dialog"` sweep as unverified future work, which this task performs (a filtered subset — 4 of ~14 investigated names, the other ~9-10 confirmed NOT candidates and left alone).
- `projects/platform-console/docs/conventions/frontend-ui.md` § 3 — the `StatusBadge`/`DetailHeader`/`ConfirmDialog` shared-component promotion precedent.

# Related Skills

- `.claude/skills/frontend/architecture/layered-by-feature/SKILL.md`

---

# Related Contracts

None — no API or event contract touched; pure client-side component dedup.

---

# Target App

- `apps/console-web`

---

# Implementation Notes

- Grep-verify the full importer list for `DiscrepancyResolveDialog` and `OperatorProfileEditDialog` BEFORE starting and again AFTER finishing, diffing the two lists to confirm zero call-site drift (per TASK-PC-FE-262/268's verification method). `ApprovalReasonDialog` and `DelegationRevokeDialog` are private/non-exported — verify via grep that no external file imports them (they shouldn't, and must not gain any).
- The `dialogAttrs` (or equivalent) extension to `shared/ui/ConfirmDialog` should be the minimal shape that satisfies the one genuinely load-bearing case (`data-transition`) — do not over-generalize into a full arbitrary-props passthrough; a typed `Record<string, string>` spread onto the frame `<div>` is sufficient.
- Do not modify any of the 12 dialogs TASK-PC-FE-262/268 already migrated.
- Before assuming `delegation-revoke-dialog`'s testid can simply move from the overlay div to the frame div, read `DelegationScreen.test.tsx`'s actual assertion (likely `screen.getByTestId('delegation-revoke-dialog')` used only to confirm presence) — if any assertion depends on it being specifically the *overlay* element (e.g., checking overlay-specific styling/attributes), that changes the plan; do not assume without reading the test first.

---

# Edge Cases

- **`data-transition={transition}` on `ApprovalReasonDialog`'s frame is directly asserted by `ApprovalScreen.test.tsx:256`** (`expect(dialog).toHaveAttribute('data-transition', 'reject')`) — this is the one genuinely load-bearing custom attribute among the 4 migrations (grep-confirmed as its only consumer). This is why Scope item 1 adds `dialogAttrs` to the shared primitive rather than silently dropping it.
- **`data-discrepancy-id={discrepancyId}` on `DiscrepancyResolveDialog`'s frame has zero test consumers** (grep-confirmed) — preserve it anyway via the same `dialogAttrs` mechanism (cheap, avoids an undocumented silent attribute loss) rather than treating the lack of a test as license to drop it.
- **`delegation-revoke-dialog`'s testid is on the OUTER overlay div today, not the inner `role="dialog"` frame** — unlike every other migrated-or-migrating dialog's convention (testid on the frame). Moving it to the frame (as `dialogTestId`) is almost certainly safe (test assertions typically just check presence), but verify against `DelegationScreen.test.tsx` directly before assuming.
- **`DelegationRevokeDialog`'s Cancel button has no `data-testid` at all today** — `ConfirmDialog`'s `cancelTestId` prop is non-optional, so a new one (`delegation-revoke-cancel`) must be assigned. Purely additive; grep `DelegationScreen.test.tsx` to confirm no existing selector (e.g., by role/text) targets Cancel in a way a new testid could conflict with (it can't — adding a testid never breaks a role/text-based selector).
- **`ApprovalReasonDialog` has no natural `description` text** — title flows directly into the reason field today. Passing `description=""` is the minimal-diff choice; if this produces a visually awkward empty gap, that is real information for the implementer to weigh (a genuinely different resulting style is a documented deviation, not something to silently paper over — same standard TASK-PC-FE-268 applied to `OrgReasonDialog`'s button classes).
- **`OperatorProfileEditDialog` already has full keyboard handling** — this migration must not introduce ANY new observable behavior (no gained focus trap, no gained Escape handling — it already had both). Treat any behavioral diff here as a bug, not an acceptable side effect (contrast with items 4/5, where gaining focus-trap/Escape IS expected and pre-approved).

---

# Failure Scenarios

- `tsc --noEmit` fails after delegation → fix the prop wiring, do not leave the old duplicated JSX in place as a fallback.
- A testid that previously existed on one of the 4 dialogs is missing after migration → STOP, this breaks an existing test's selector; re-add it via the appropriate `shared/ui/ConfirmDialog` prop.
- `ApprovalScreen.test.tsx`'s `data-transition` assertion fails post-migration → this means the `dialogAttrs` wiring is incomplete or `ConfirmDialog` isn't spreading it onto the correct element; do not modify the test assertion to work around it (per `platform/refactoring-policy.md` Prohibited: "Refactoring production code and test code in the same change") — fix the production wiring.
- Vitest suite fails post-change (in particular `LedgerOpsScreen.test.tsx`, `tests/unit/features/operators/OperatorProfileEditDialog.test.tsx`, `ApprovalScreen.test.tsx`, `DelegationScreen.test.tsx`) → indicates a missed testid/prop/attribute wiring, not a false positive; revert and re-diagnose rather than modifying test assertions.

---

# Test Requirements

- No new tests required — existing indirect/dedicated coverage (`LedgerOpsScreen.test.tsx`, `tests/unit/features/operators/OperatorProfileEditDialog.test.tsx`, `ApprovalScreen.test.tsx`, `DelegationScreen.test.tsx`) is the verification mechanism and must pass unmodified against the preserved testids and attributes.

---

# Definition of Done

- [ ] All 5 scope items completed
- [ ] `tsc --noEmit` clean
- [ ] `pnpm lint` clean
- [ ] Full `console-web` vitest suite passing, zero test assertion values modified
- [ ] Ready for review
