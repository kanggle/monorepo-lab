# Task ID

TASK-PC-FE-262

# Title

console-web extract shared/ui/ConfirmDialog primitive, unify 9 confirm-gate dialog implementations (naming + duplication follow-up to TASK-PC-FE-260/261)

# Status

done

# Owner

frontend

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

# Goal

TASK-PC-FE-260's naming scan flagged a 3-way `<Domain>ConfirmDialog` / `ConfirmDialog` / `ConfirmActionDialog` naming split as lowest priority and deferred it. Re-investigating for this task found the real footprint is bigger: **9 components** across 4 naming schemes (`<Domain>ConfirmDialog` ×5, unprefixed `ConfirmDialog` ×1, `ConfirmActionDialog` ×1, `<Domain>ActionDialog` ×2) independently reimplement the *same* confirm-gate modal shell — backdrop, `role="dialog"` frame, ARIA labelling, Escape-to-cancel, (in 6 of 9) a Tab-loop focus trap, an error banner, a footer with Cancel/Confirm buttons. Per `platform/refactoring-policy.md` § Prioritization, **duplication outranks naming** — the correct fix is extracting the shared shell to `shared/ui/ConfirmDialog.tsx` (the same "promote structurally-identical shell, keep domain logic local" pattern already established for `StatusBadge`/`DetailHeader` per `docs/conventions/frontend-ui.md`), with each domain's confirm dialog becoming a thin wrapper (or, where there is zero domain-specific content, deleted entirely in favor of direct `shared/ui/ConfirmDialog` usage). Naming convergence (`<Domain>ConfirmDialog`) falls out of this as a side effect for the 2 renamed components.

**Known, deliberate side effect (documented, not smuggled — mirrors the `StatusBadge`/`FxRatesTable` dark-mode-bug precedent in `docs/conventions/frontend-ui.md` § 3):** `PartnershipConfirmDialog`, `SubscriptionConfirmDialog`, and `TenantConfirmDialog` currently have **no Tab-loop focus trap** (Escape-to-cancel only) — a real a11y gap versus the other 6. Adopting the shared primitive gives them the trap the other 6 already have. This is the ONLY behavior change in this task; everything else (testids, copy, validation, wire calls, focus target) is preserved exactly per component.

**Explicitly out of scope (do not chase further during implementation — recount later, don't inherit scope):** `features/wms-outbound-ops/components/OutboundCancelDialog.tsx` was found during investigation to be a **10th**, independently-implemented instance of the same shell (its own doc comment says "mirror `OutboundActionDialog`" — parallel reimplementation, not delegation). It is NOT touched by this task. `AcknowledgeAlertDialog` (wms-ops), `OrgReasonDialog` (org-hierarchy), and any other confirm-styled dialog not explicitly listed in Scope below are also not touched — they were seen in a broad grep but not verified to share the same prop/behavior shape, and are left for a future task to re-verify from scratch.

---

# Scope

## In Scope

### 1. New shared primitive: `shared/ui/ConfirmDialog.tsx`

Extracts the common shell. Props (final — explicit per-element testid props, NOT a derived-prefix scheme, because the 9 existing components' testid suffixes are NOT uniform — see Edge Cases):

```ts
export interface ConfirmDialogProps {
  open: boolean;
  title: string;
  description: ReactNode;
  confirmLabel: string;
  destructive?: boolean;       // tints title + confirm button
  pending?: boolean;
  confirmDisabled?: boolean;   // additional disable condition from the caller (e.g. reason-empty)
  errorMessage?: string | null;
  conflict?: boolean;
  conflictMessage?: ReactNode; // text DIFFERS per caller (order vs product vs shipment) — no default copy in the shared component
  children?: ReactNode;        // domain-specific body: reason textarea, role editor, summary dl, note field, etc.
  dialogTestId: string;
  overlayTestId?: string;
  cancelTestId: string;
  confirmTestId: string;
  errorTestId?: string;
  conflictTestId?: string;
  /** Element to auto-focus on open. Omit to auto-focus the Confirm button
   *  (the primitive's own ref) — pass e.g. a reason-textarea ref to preserve
   *  a caller's existing "focus the reason field" behavior. */
  initialFocusRef?: RefObject<HTMLElement>;
  onConfirm: () => void;
  onCancel: () => void;
}
```

Behavior: renders `null` when `!open`. Full a11y — focus-into-dialog on open (`initialFocusRef` else the Confirm button), Tab-loop focus trap (`querySelectorAll('button, textarea, input, select, [tabindex]:not([tabindex="-1"])')` inside the dialog frame), Escape-to-cancel, `role="dialog"`/`aria-modal`/`aria-labelledby`/`aria-describedby`. Renders (in order): title (destructive-tinted when `destructive`), description, `children`, conflict banner (amber, only if `conflict`), error banner (destructive, only if `errorMessage`), footer (Cancel secondary Button, Confirm primary Button — destructive-tinted when `destructive`, `disabled={pending || confirmDisabled}`).

### 2. Delete + replace at call sites (zero domain-specific fields — the existing component IS already exactly the shared shape)

- **`features/ecommerce-ops/components/ConfirmDialog.tsx`** — delete. Update its ~20 importers (`StockAdjustDialog.tsx`, `CouponIssueDialog.tsx`, `SellerDetail.tsx`, `PromotionDetail.tsx`, `ProductDetail.tsx`, `PeriodPayoutsScreen.tsx`, `CommissionRateForm.tsx`, `PeriodOpenForm.tsx`, `SettlementPeriodsSection.tsx`, `ProductForm.tsx`, `PromotionForm.tsx`, `ShippingsScreen.tsx`, `TemplateForm.tsx`, `VariantEditor.tsx`, `PromotionsScreen.tsx`, `ProductsScreen.tsx`, `ShipFormDialog.tsx`, `OrderStatusDialog.tsx`, `SellerRegisterForm.tsx`, `ImageManager.tsx` — grep for the exact set before editing, this list is from investigation and must be re-verified) from `import { ConfirmDialog } from './ConfirmDialog'` to `import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'`, and their JSX call sites add the 4 new required testid props with the EXACT existing values (`dialogTestId="ecommerce-confirm-dialog"`, `overlayTestId="ecommerce-confirm-overlay"`, `cancelTestId="ecommerce-confirm-cancel"`, `confirmTestId="ecommerce-confirm-confirm"`, `errorTestId="ecommerce-confirm-error"`, `conflictTestId="ecommerce-confirm-conflict"`, `conflictMessage="상품 상태가 변경되었습니다. 최신 상태를 확인했습니다 — 계속하려면 다시 시도하세요."` when `conflict` is used) — `tone="destructive"` callers become `destructive` boolean.
- **`features/wms-outbound-ops/components/OutboundActionDialog.tsx`** — delete. Update `features/wms-outbound-ops/components/OutboundOpsScreen.tsx` (2 JSX usages) similarly: import from `@/shared/ui/ConfirmDialog`, testids `dialogTestId="outbound-action-dialog"` / `overlayTestId="outbound-action-overlay"` / `cancelTestId="outbound-action-cancel"` / `confirmTestId="outbound-action-confirm"` / `errorTestId="outbound-action-error"` / `conflictTestId="outbound-action-conflict"` / `conflictMessage="주문 상태가 변경되었습니다. 최신 상태를 확인했습니다 — 계속하려면 다시 시도하세요."`. Remove the `export { OutboundActionDialog } from './components/OutboundActionDialog';` line from `features/wms-outbound-ops/index.ts` (verify first it is unused outside the feature — confirmed unused under `app/` during investigation, re-verify at implementation time).

### 3. Rename + refactor to thin wrapper (has domain-specific body content — keep a feature-owned file)

- **`features/scm-replenishment/components/ReplenishmentActionDialog.tsx`** → rename to `ReplenishmentConfirmDialog.tsx` (aligns naming, matches the dominant `<Domain>ConfirmDialog` scheme). Keeps its own `noteLabel`/`noteValue`/`onNoteChange` props (rendered as `children` — the note textarea — passed into the shared primitive). `initialFocusRef` = none (preserves existing "focus the confirm button" behavior, its default). Update `features/scm-replenishment/components/ReplenishmentScreen.tsx` (import path + name) and `features/scm-replenishment/index.ts` (barrel export name).

### 4. Refactor internals only — public prop API and file/export name UNCHANGED, so ZERO call-site changes (each wrapper passes `open={...}` — for the 3 that don't already take an `open` prop, they pass a hardcoded `open={true}` to the shared primitive internally, since they are only ever mounted by their parent when meant to be shown — this preserves each wrapper's existing external contract exactly)

- **`features/operators/components/OperatorConfirmDialog.tsx`** — keep name/props. Domain body (`children`): reason textarea (required) + optional `OperatorConfirmRoleEditor`. `initialFocusRef` = reason textarea ref (preserves existing behavior). `destructive` = current `elevated` prop, renamed internally only if it does not change the public prop name (**keep the public prop named `elevated`** — do not rename it, only the internal plumbing to the shared primitive's `destructive` prop changes). Testids verbatim: `operator-confirm-overlay`/`operator-confirm-dialog`/`operator-confirm-cancel`/`operator-confirm-submit`(note: **`-submit`, not `-confirm`** — the shared primitive's `confirmTestId` prop must be passed `"operator-confirm-submit"` to preserve this)/`operator-confirm-error`. Already has a full Tab-loop trap — no a11y change here.
- **`features/scm-config/components/ConfigConfirmDialog.tsx`** — keep name/props. Domain body (`children`): the summary `<dl>` + the "future evaluation only" disclaimer paragraph. `initialFocusRef` = none (preserves existing confirm-button auto-focus). Testids verbatim: `config-confirm-overlay`/`config-confirm-dialog`/`config-confirm-cancel`/`config-confirm-submit`(**`-submit`**, not `-confirm`)/`config-confirm-error`. Already has a full Tab-loop trap.
- **`features/accounts/components/ConfirmActionDialog.tsx`** + **`ConfirmActionDialogBody.tsx`** — keep name/props (per TASK-PC-FE-260 scan's own judgment: this one legitimately serves multiple distinct account actions, its generic name is correct, not forced into `<Domain>ConfirmDialog>`). Restructure: `ConfirmActionDialogBody` currently renders title/description/reason/typed/error/footer ALL itself (it predates the split-component pattern the others use) — trim it to render ONLY the reason textarea + optional typed-confirmation input (drop its title/description/error-banner/footer JSX, all of which the shared primitive now owns), and pass it as `children` to the shared primitive from `ConfirmActionDialog`. Testids verbatim (**no domain prefix at all** — this component predates the prefixed convention): `confirm-overlay`/`confirm-dialog`/`confirm-cancel`/`confirm-submit`/`confirm-error`/`confirm-reason`(body, unaffected)/`confirm-typed`(body, unaffected)/`reason-required-hint`(body, unaffected). `initialFocusRef` = reason textarea ref. Has a full Tab-loop trap already.
- **`features/partnerships/components/PartnershipConfirmDialog.tsx`** — keep name/props (no `open` prop in its public API — internally passes `open={true}` to the shared primitive). Domain body (`children`): reason textarea (required) + optional `warning` line. `destructive` prop passes straight through. `initialFocusRef` = reason textarea ref (preserves existing mount-time focus). Testids: this component currently has **no `data-testid` on its overlay/dialog wrapper at all** — pass `dialogTestId="partnership-confirm-dialog"` / `overlayTestId="partnership-confirm-overlay"` as NEW additions (safe — nothing currently asserts their absence) — but the buttons/error DO have existing testids that must be preserved verbatim: `cancelTestId="partnership-confirm-cancel"`, `confirmTestId="partnership-confirm-submit"`(**`-submit`**), `errorTestId="partnership-confirm-error"`. **Gains the Tab-loop trap (documented a11y side effect).**
- **`features/subscriptions/components/SubscriptionConfirmDialog.tsx`** — same shape as Partnership (near-duplicate today). Keep name/props, `open={true}` internally. Testids: `dialogTestId="subscription-confirm-dialog"` / `overlayTestId="subscription-confirm-overlay"` (new), `cancelTestId="subscription-confirm-cancel"`, `confirmTestId="subscription-confirm-submit"`(**`-submit`**), `errorTestId="subscription-confirm-error"`. **Gains the Tab-loop trap (documented a11y side effect).**
- **`features/tenants/components/TenantConfirmDialog.tsx`** — keep name/props, `open={true}` internally. Domain body (`children`): reason textarea (required, no warning line — Tenant has none). Testids: `dialogTestId="tenant-confirm-dialog"` / `overlayTestId="tenant-confirm-overlay"` (new), `cancelTestId="tenant-confirm-cancel"`, `confirmTestId="tenant-confirm-submit"`(**`-submit`**), `errorTestId="tenant-confirm-error"`. **Gains the Tab-loop trap (documented a11y side effect).**

## Out of Scope

- `features/wms-outbound-ops/components/OutboundCancelDialog.tsx` (10th instance, found during investigation — independently implemented, not delegating to `OutboundActionDialog`). Deliberately deferred to a future task that re-verifies the full instance count from scratch.
- `AcknowledgeAlertDialog` (`features/wms-ops`), `OrgReasonDialog` (`features/org-hierarchy`), and any other dialog-shaped component not explicitly named in Scope above — seen in a broad grep, not verified to share this exact shape, not touched.
- Any prop RENAME on the 7 kept wrapper components (`elevated` on `OperatorConfirmDialog` stays `elevated`, not renamed to `destructive`, etc.) — only the internal plumbing to the new shared primitive changes; each wrapper's own public API is unchanged so NO caller of any of these 7 wrappers needs to change.
- The a11y Tab-loop-trap gain for Partnership/Subscription/Tenant is the ONLY sanctioned behavior change. Do not additionally "fix" or "improve" anything else noticed along the way (e.g. do not add `data-testid`s beyond the two new dialog/overlay ones explicitly listed, do not change copy, do not change validation rules).
- Raw `<button>` → shared `Button` component conversion for Partnership/Subscription/Tenant is IN scope only as a necessary consequence of routing through the shared primitive's footer (which always uses `Button`) — it is not a separately-scoped visual redesign; the resulting button styling must look the same (same Tailwind classes as the other 6 already-`Button`-based dialogs, which is the established look).

---

# Acceptance Criteria

- [ ] `shared/ui/ConfirmDialog.tsx` exists, exporting `ConfirmDialog` + `ConfirmDialogProps`, matching the shape in Scope § 1.
- [ ] `features/ecommerce-ops/components/ConfirmDialog.tsx` and `features/wms-outbound-ops/components/OutboundActionDialog.tsx` no longer exist; all their former importers now import `ConfirmDialog` from `@/shared/ui/ConfirmDialog`.
- [ ] `features/scm-replenishment/components/ReplenishmentConfirmDialog.tsx` exists (renamed from `ReplenishmentActionDialog.tsx`); the old filename/export no longer exists; `ReplenishmentScreen.tsx` and the feature barrel updated.
- [ ] The 7 kept wrapper components (`OperatorConfirmDialog`, `ConfigConfirmDialog`, `ConfirmActionDialog`, `PartnershipConfirmDialog`, `SubscriptionConfirmDialog`, `TenantConfirmDialog`, plus the renamed `ReplenishmentConfirmDialog`) all delegate their shell rendering to `shared/ui/ConfirmDialog` internally.
- [ ] Every existing `data-testid` value listed in Scope §§ 2-4 is preserved verbatim (including the `-submit` vs `-confirm` inconsistency, and the accounts family's no-domain-prefix convention) — grep-diff each before/after to confirm zero testid string changes except the 2 explicitly-new ones per Partnership/Subscription/Tenant (dialog + overlay testids that did not exist before).
- [ ] No test file's assertions are modified — the existing test suite passes UNCHANGED (this is a duplication-extraction + naming task, not a test-authoring task; new Tab-loop-trap behavior for Partnership/Subscription/Tenant needs no new test since no existing test currently asserts the ABSENCE of a trap for those 3).
- [ ] `tsc --noEmit` — 0 errors.
- [ ] `next lint` — 0 warnings/errors.
- [ ] `vitest run` — same pass count as the TASK-PC-FE-261 post-merge baseline (280 files / 2909 tests), since this task adds no new tests and removes none.
- [ ] `OutboundCancelDialog.tsx` and any other confirm-dialog-shaped component outside this task's Scope are verified byte-unchanged (diff review).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification (platform-console: domain=saas, traits=[multi-tenant, integration-heavy, audit-heavy]). Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/refactoring-policy.md` § Prioritization — duplication (category 4) outranks naming (category 6); this task is primarily a **Reduce Duplication** refactor (Medium risk per § Allowed Refactoring Categories), with naming convergence as a side effect for the 2 renamed/relocated components.
- `platform/shared-library-policy.md` — governs promotion of feature-local code to a shared location; `shared/ui/` (frontend shared UI, not `libs/`) is the console-web-internal analogue already used for `StatusBadge`/`DetailHeader`.
- `projects/platform-console/docs/conventions/frontend-ui.md` § 3 — the `StatusBadge` extraction precedent this task mirrors (shared primitive + escape hatches; a documented a11y bug fixed as a side effect of the migration, same shape as this task's Tab-loop-trap side effect).
- `projects/platform-console/specs/services/console-web/architecture.md` — Layered by Feature architecture; `shared/ui/` is the cross-feature UI-primitive location per § Allowed Dependencies.
- `projects/platform-console/tasks/done/TASK-PC-FE-260-naming-convention-cleanup.md` — originating naming scan (finding #5, deferred as lowest priority).
- `projects/platform-console/tasks/done/TASK-PC-FE-261-rename-dashboards-iam-composed-overview.md` — the prior finding-#1/#2 follow-up in this same series.

# Related Skills

- `.claude/skills/frontend/architecture/layered-by-feature/SKILL.md`

---

# Related Contracts

None — no API or event contract is touched (pure internal UI-component consolidation; no wire-shape, no producer call changes).

---

# Target App

- `apps/console-web`

---

# Implementation Notes

- Build `shared/ui/ConfirmDialog.tsx` FIRST, then migrate call sites one family at a time (delete-and-replace pair first since it is the most mechanical and highest-file-count, then the rename, then the 7 refactor-only wrappers), verifying `tsc --noEmit` after each family before moving to the next — do not batch all 9+ site changes into one uncheckpointed pass given the size.
- Before deleting `ecommerce-ops/ConfirmDialog.tsx` and `wms-outbound-ops/OutboundActionDialog.tsx`, re-run the grep for their importers fresh (the lists in Scope § 2 are from investigation and could be stale by implementation time) — do not trust the enumerated list blindly.
- For the 3 `open={true}`-internal wrappers (Partnership/Subscription/Tenant), double-check each one's actual caller (`PartnershipsScreen.tsx`, `SubscriptionsScreen.tsx`, `TenantDetail.tsx`) truly only mounts the dialog conditionally (e.g. `{pendingDraft && <PartnershipConfirmDialog .../>}`) before hardcoding `open={true}` — if any caller instead always-mounts with a toggled prop under a different name, that wrapper needs its own `open`-prop passthrough instead.
- `ConfirmActionDialogBody.tsx`'s trim (dropping its title/description/error-banner/footer) will leave it as a much smaller component (reason + optional typed-confirmation fields only) — consider whether it is still worth keeping as a separate file (its own doc comment says it was split out in TASK-PC-FE-210 specifically to separate the container's state/effects from presentation; that reasoning still holds even with a smaller body) — default to keeping it as its own file unless it shrinks to near-trivial, in which case inlining it back into `ConfirmActionDialog.tsx` as `children` JSX directly is also acceptable and arguably cleaner — implementer's call, not a hard requirement either way.

---

# Edge Cases

- The `-submit` vs `-confirm` confirm-button testid inconsistency (`operator-confirm-submit`, `config-confirm-submit`, `confirm-submit`(accounts), `partnership-confirm-submit`, `subscription-confirm-submit`, `tenant-confirm-submit` all use `-submit`; `ecommerce-confirm-confirm`, `outbound-action-confirm`, `replenishment-action-confirm` use `-confirm`) is exactly why the shared primitive takes an explicit `confirmTestId` prop rather than deriving one from a prefix — do not "fix" this inconsistency by renaming any existing testid to match a pattern; that would break existing test assertions.
- `OperatorConfirmDialog`'s `roleEditor` body content changes the dialog's height/content significantly (renders an entire role checkbox grid) — verify the shared primitive's `children` slot placement (between description and conflict/error banners) still matches the original DOM order exactly, since some existing tests may query by DOM position or `nextSibling`-style traversal (check before assuming testid-only queries are used everywhere).
- `PartnershipConfirmDialog`/`SubscriptionConfirmDialog`'s existing `useEffect(() => { reasonRef.current?.focus(); }, [])` runs unconditionally on mount (not gated on an `open` prop, since they don't have one) — when delegating to the shared primitive's `initialFocusRef`-driven, `open`-gated focus effect, confirm the focus timing (both use a `setTimeout(…, 0)` in the shared primitive vs a synchronous effect currently) does not introduce a visible flash or a focus-race with the component's own mount.

---

# Failure Scenarios

- `tsc --noEmit` fails after a family's migration → fix before proceeding to the next family; do not accumulate type errors across families.
- A testid grep-diff turns up an unintended change → revert that one site and re-derive the correct explicit testid prop value from the original file (which stays available in git history / the pre-migration read) rather than guessing.
- Vitest suite fails on any of the 7 refactor-only wrappers → very likely a body/children placement or an `initialFocusRef` wiring mistake (wrong ref passed, or a ref pointing at an element that no longer exists in the new DOM structure) — diagnose against the specific failing assertion, do not paper over by loosening the assertion.
- Vitest suite fails specifically on Partnership/Subscription/Tenant in a way that indicates the NEW Tab-loop trap breaks an existing keyboard-interaction test (e.g. a test that tabs past the dialog expecting to reach background content) → this would mean the "side effect" assumption was wrong for that specific test; stop and re-confirm with the task's Goal section reasoning (the a11y gain is intentional) before deciding whether the test itself needs updating (which would need explicit note, per the Prohibited-list precedent about not silently editing test assertions) or whether the trap should be scoped differently for that site.
- Any importer of `ecommerce-ops/ConfirmDialog` or `wms-outbound-ops/OutboundActionDialog` is missed during the delete-and-replace sweep → `tsc --noEmit` will catch it as a missing-module error; do not leave a re-export shim under the old path — fix the import.

---

# Test Requirements

- No new tests required — this is a duplication-extraction + naming task with one documented, low-risk a11y side effect (a keyboard trap gain, not a loss) that no existing test asserts against. Existing test coverage across all 9+ sites' current test files (verify full list at implementation time — includes at minimum `tests/unit/features/operators/OperatorConfirmDialog.test.tsx` if it exists, ecommerce dialog-adjacent tests, partnerships/subscriptions/tenants screen tests, wms-outbound-ops tests, scm-replenishment tests — grep before starting) is the verification mechanism and must pass unmodified.

---

# Definition of Done

- [ ] `shared/ui/ConfirmDialog.tsx` created and adopted by all 9 target sites
- [ ] 2 components deleted (ecommerce `ConfirmDialog`, wms-outbound `OutboundActionDialog`), all their callers updated
- [ ] 1 component renamed (`ReplenishmentActionDialog` → `ReplenishmentConfirmDialog`), its callers + barrel updated
- [ ] 6 components refactored internally with zero public-API/call-site change
- [ ] Every existing testid preserved verbatim (grep-diff verified)
- [ ] `tsc --noEmit` clean
- [ ] `next lint` clean
- [ ] `vitest run` — same pass count as baseline, 0 failures
- [ ] `OutboundCancelDialog.tsx` and other out-of-scope dialogs verified byte-unchanged
- [ ] Ready for review
