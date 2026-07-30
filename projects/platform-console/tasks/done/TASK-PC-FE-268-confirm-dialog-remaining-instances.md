# Task ID

TASK-PC-FE-268

# Title

console-web migrate remaining 3 confirm-dialog instances to `shared/ui/ConfirmDialog`

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

TASK-PC-FE-262 promoted the confirm-gate modal shell (backdrop overlay, `role="dialog"` frame, ARIA labelling, Escape-to-cancel, Tab-loop focus trap, error/conflict banners, Cancel/Confirm footer) to `shared/ui/ConfirmDialog.tsx`, migrating 9 feature-local dialogs. That task explicitly deferred 3 more instances discovered but not verified during its own investigation: `features/wms-outbound-ops/components/OutboundCancelDialog.tsx`, `features/wms-ops/components/AcknowledgeAlertDialog.tsx`, `features/org-hierarchy/components/OrgReasonDialog.tsx`. Investigation now (2026-07-30) confirms all 3 independently reimplement the same shell:

- **`OutboundCancelDialog`** — near-exact match (backdrop/frame/ARIA/focus-trap/Escape identical). One real deviation: its Cancel button reads "닫기" (dismiss), not the shared primitive's hardcoded "취소" — `shared/ui/ConfirmDialog` needs a new optional `cancelLabel` prop (default `'취소'`, backward-compatible — the 9 existing callers are unaffected) to preserve this without a behavior change.
- **`AcknowledgeAlertDialog`** — clean match, simplest of the 3 (no reason field, no conflict banner — just title/description/error/footer).
- **`OrgReasonDialog`** — the most diverged: uses raw `<button>` elements (not `@/shared/ui/Button`), has **no Tab-loop focus trap** and gates Escape on `!pending` (the shared primitive doesn't gate Escape on pending). Its own doc comment ("kept feature-local — no cross-feature import") predates `shared/ui/ConfirmDialog`'s existence — that rationale no longer applies now that the target is `shared/`, not another feature. Migrating it repeats TASK-PC-FE-262's own precedent for 3 of the original 9 (Partnership/Subscription/Tenant): gaining the Tab-loop trap and Escape-during-pending are already-reviewed, intentional a11y side effects of this exact migration pattern, not new risk.

---

# Scope

## In Scope

1. **`shared/ui/ConfirmDialog.tsx`** — add optional `cancelLabel?: string` prop (default `'취소'`), rendered as the Cancel button's text instead of the hardcoded string. Purely additive — the 9 existing call sites (none pass this prop) are byte-unchanged in behavior.

2. **`features/wms-outbound-ops/components/OutboundCancelDialog.tsx`** — becomes a thin wrapper delegating to `shared/ui/ConfirmDialog`. Keeps its exact public `OutboundCancelDialogProps` interface (`open`, `orderLabel`, `needsAdmin`, `pending`, `errorMessage`, `conflict`, `onConfirm`, `onCancel`) — zero changes to its 2 existing callers (`OutboundOpsScreen.tsx`, `use-outbound-cancel-dialog.ts`). `title`="출고 주문을 취소할까요?", `description`=the existing orderLabel paragraph, `confirmLabel`="주문 취소", `cancelLabel`="닫기", `confirmDisabled`=`!reasonValid`, `initialFocusRef`=the reason-textarea ref, `children`=the needsAdmin hint + reason textarea + char-count help (moved from `OutboundCancelDialogBody`), `conflictMessage`=the existing conflict copy. All 6 existing testids (`outbound-cancel-overlay`/`-dialog`/`-reason`/`-conflict`/`-error`/`-dismiss`/`-confirm`, plus `-admin-hint`) preserved exactly. **Delete `OutboundCancelDialogBody.tsx`** (single consumer, folded into the wrapper's `children`).

3. **`features/wms-ops/components/AcknowledgeAlertDialog.tsx`** — becomes a thin wrapper. Keeps its exact public `AcknowledgeAlertDialogProps` interface (`open`, `alertLabel`, `pending`, `errorMessage`, `onConfirm`, `onCancel`) — zero changes to its caller (`WmsOpsScreen.tsx`). `title`="알림을 확인 처리할까요?", `description`=the existing alertLabel paragraph, `confirmLabel`="확인 처리", no `children` (no reason field), no `conflict` prop (this dialog never had one). Testids `wms-ack-overlay`/`-dialog`/`-error`/`-cancel`/`-confirm` preserved exactly.

4. **`features/org-hierarchy/components/OrgReasonDialog.tsx`** — becomes a thin wrapper. Keeps its exact public `OrgReasonDialogProps` interface (`title`, `description`, `confirmLabel`, `tone`, `pending`, `error`, `onConfirm`, `onCancel` — **no `open` prop**, same as it has today) — zero changes to its 4 existing callers (`OrgNodeDetail.tsx`, `OrgAdminPanel.tsx`, `OrgHierarchyScreen.tsx`, `CeilingEditor.tsx`). Internally passes `open={true}` to the shared primitive (the parent conditionally mounts `<OrgReasonDialog>` itself — same pattern TASK-PC-FE-262 used for Partnership/Subscription/Tenant). `destructive`=`tone === 'destructive'`. `children`=the reason-textarea + label (moved in verbatim). `errorMessage`=`error`. `initialFocusRef`=the reason-textarea ref (preserves today's mount-time auto-focus). Existing testids `org-reason-input`/`-error`/`-cancel`/`-submit` preserved exactly (note: `-submit`, not `-confirm` — matches TASK-PC-FE-262's precedent of preserving non-uniform suffixes). **New** testids `org-reason-dialog`/`org-reason-overlay` gained (the shared primitive requires `dialogTestId`/accepts `overlayTestId`; this component had neither before — same "gained new testids" precedent as Partnership/Subscription/Tenant in TASK-PC-FE-262).

## Out of Scope

- Any change to the raw-button visual styling beyond what naturally results from swapping to the shared `Button` component inside `shared/ui/ConfirmDialog` (verify the resulting classes are visually equivalent to `OrgReasonDialog`'s current hand-rolled classes — see Edge Cases; a genuinely different resulting style is a real (documented) deviation, not a redesign to chase).
- Any other feature's confirm-dialog-shaped component not identified in this task or TASK-PC-FE-262's own investigation.
- Behavior changes beyond the 2 already-precedented ones (Tab-loop focus trap gained by `OrgReasonDialog`; Escape now cancels during `pending` for `OrgReasonDialog`, previously gated on `!pending`). Every other observable behavior, testid, and prop must be byte-identical before/after.

---

# Acceptance Criteria

- [ ] `shared/ui/ConfirmDialog.tsx` has a new optional `cancelLabel` prop, default `'취소'`; the 9 pre-existing call sites are unmodified.
- [ ] `OutboundCancelDialog.tsx` delegates to the shared primitive; `OutboundCancelDialogBody.tsx` no longer exists; both existing callers unchanged; all pre-existing testids preserved.
- [ ] `AcknowledgeAlertDialog.tsx` delegates to the shared primitive; its 1 existing caller unchanged; all pre-existing testids preserved.
- [ ] `OrgReasonDialog.tsx` delegates to the shared primitive; its 4 existing callers unchanged; pre-existing testids (`org-reason-input`/`-error`/`-cancel`/`-submit`) preserved; 2 new testids (`org-reason-dialog`/`org-reason-overlay`) added; documented deviations (focus trap gained, Escape-during-pending) verified against the migration, not silently introduced elsewhere.
- [ ] Grep for each of the 3 dialogs' import specifiers across `apps/console-web/src` and `apps/console-web/tests` — the caller list (7 total: 2 + 1 + 4) is byte-unchanged before/after.
- [ ] `tsc --noEmit` passes with zero new errors.
- [ ] `pnpm lint` passes with zero new errors.
- [ ] Full `console-web` unit test suite (vitest) passes unchanged — same pass count, no test assertion **values** modified (existing indirect coverage via `OutboundOpsScreen.test.tsx`, `WmsOpsScreen.test.tsx`, `features/org-hierarchy/CeilingEditor.test.tsx` must still pass against the preserved testids).
- [ ] Net LOC decreases (duplication removed, not just moved).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification (platform-console: domain=saas, traits=[multi-tenant, integration-heavy, audit-heavy]). Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/refactoring-policy.md` § Allowed Refactoring Categories → **Reduce Duplication** (Medium risk); duplication outranks naming per § Prioritization.
- TASK-PC-FE-262 (`shared/ui/ConfirmDialog.tsx` origin) — this task's direct precedent and continuation; its own Out of Scope section named these 3 dialogs as "unverified, deliberately excluded" pending a future re-investigation, which this task performs.
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

- Grep-verify the full importer list for `OutboundCancelDialog`, `AcknowledgeAlertDialog`, `OrgReasonDialog` BEFORE starting and again AFTER finishing, diffing the two lists to confirm zero call-site drift (per TASK-PC-FE-262/263/264's verification method).
- `OrgReasonDialog`'s raw-button classes should be compared against the shared `Button` component's `primary`/`secondary` variant classes (`shared/ui/Button.tsx`) before assuming a 1:1 visual match — note one likely small diff: the raw cancel button uses `hover:bg-muted`, the shared primitive's `secondary` variant uses `hover:bg-accent`. Document this as a deviation (mirrors TASK-PC-FE-262's own "hover class unified" precedent) rather than silently absorbing it.
- Do not modify any of the 9 dialogs TASK-PC-FE-262 already migrated.

---

# Edge Cases

- `OrgReasonDialog` has no `open` prop today (parent components conditionally mount/unmount it). The thin wrapper must pass `open={true}` internally to the shared primitive — do not add a new `open` prop to `OrgReasonDialogProps` (would be an unrequested public-API change touching 4 callers).
- `OutboundCancelDialog`'s `reasonValid` (3..500 char bound) is caller-owned validation, not something `shared/ui/ConfirmDialog` knows about — wire it through `confirmDisabled`, exactly as the already-migrated dialogs with reason validation do.

---

# Failure Scenarios

- `tsc --noEmit` fails after delegation → fix the prop wiring, do not leave the old duplicated JSX in place as a fallback.
- A testid that previously existed on one of the 3 dialogs is missing after migration → STOP, this breaks an existing test's selector; re-add it via the appropriate `shared/ui/ConfirmDialog` prop.
- Vitest suite fails post-change (in particular the 3 indirect-coverage test files named above) → indicates a missed testid/prop wiring, not a false positive; revert and re-diagnose rather than modifying test assertions (per `platform/refactoring-policy.md` Prohibited: "Refactoring production code and test code in the same change").

---

# Test Requirements

- No new tests required — existing indirect coverage (`OutboundOpsScreen.test.tsx`, `WmsOpsScreen.test.tsx`, `features/org-hierarchy/CeilingEditor.test.tsx`) is the verification mechanism and must pass unmodified against the preserved testids.

---

# Definition of Done

- [ ] All 4 scope items completed
- [ ] `tsc --noEmit` clean
- [ ] `pnpm lint` clean
- [ ] Full `console-web` vitest suite passing, zero test assertion values modified
- [ ] Ready for review
