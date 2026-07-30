# Task ID

TASK-PC-FE-263

# Title

console-web shared RetryButton + degrade-banner shell extraction — dedup `domain-health` vs `operator-overview`

# Status

review

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

Found by a fresh `console-web` naming-convention re-scan (2026-07-30, run after TASK-PC-FE-260/261/262 closed the prior 7-finding pool) as finding #2. What looked naming-shaped (`RetryButton` duplicate name, `DegradeBanner`/`OverviewDegradeBanner` inconsistent name) is actually genuine logic/markup duplication between `features/domain-health` and `features/operator-overview` — per `platform/refactoring-policy.md` prioritization, duplication outranks naming, so this is a **Reduce Duplication** task, not a rename. Direct precedent: TASK-PC-FE-262 (`ConfirmDialog` shared shell extraction, same "delegate internally, preserve every call site/testid" pattern).

`RetryButton.tsx` is near-byte-identical in both features (same props interface, same JSX, same disabled/label logic) except which feature hook it calls (`useDomainHealth` vs `useOperatorOverview`) and its testid prefix. `DegradeBanner.tsx` / `OverviewDegradeBanner.tsx` share the same banner shell (role="status", same Tailwind classes, same two-paragraph copy structure, same embedded retry button) but differ in: card-status union (`domain-health`: `'ok' | 'degraded'` only, never `forbidden` per its own § 2.4.9.2 doc comment; `operator-overview`: `'ok' | 'degraded' | 'forbidden'`), the "all down" predicate (`isAllDegraded` checks `status === 'degraded'`; `isAllDown` checks `status !== 'ok'`), the banner testid, and the copy text ("상태 정보" vs "개요 정보").

---

# Scope

## In Scope

1. **New `shared/ui/RetryButton.tsx`** — presentational-only client component (no feature hook import). Props: `{ isFetching: boolean; onRetry: () => void; label?: string; testid: string }`. Renders the exact same `<Button variant="secondary">` markup/disabled/label-with-ellipsis logic both existing `RetryButton.tsx` files already have. `testid` is passed as a fully-resolved string by the caller (not derived from a prefix — mirrors TASK-PC-FE-262's explicit-testid-prop pattern; the two features' testid schemes are NOT identical: `domain-health-retry[-suffix]` vs `operator-overview-retry[-suffix]`).

2. **`features/domain-health/components/RetryButton.tsx` and `features/operator-overview/components/RetryButton.tsx` become thin wrappers.** Same file path, same exported name (`RetryButton`), same public props interface (`{ initial?, label?, testidSuffix? }`) — **zero changes to any of the 15 files that currently import either one** (grep-verify before finishing: `RetryButton` import specifiers unchanged, only the wrapper's own body changes). Each wrapper keeps calling its own feature hook (`useDomainHealth`/`useOperatorOverview`), computes its existing testid string exactly as today, and renders `<SharedRetryButton isFetching={...} onRetry={...} label={...} testid={...} />`.

3. **New `shared/ui/DegradeBanner.tsx`** — presentational-only shell. Props: `{ show: boolean; testid: string; heading: string; description: string; retry: ReactNode }`. Renders `null` when `!show`; otherwise the exact existing markup (`role="status"`, identical Tailwind classes, two-paragraph text block using `heading`/`description`, then `{retry}` in the existing button slot).

4. **`features/domain-health/components/DegradeBanner.tsx` and `features/operator-overview/components/OverviewDegradeBanner.tsx` become thin wrappers.** Keep both existing exported names (`DegradeBanner`, `OverviewDegradeBanner` — do NOT rename these; the earlier scan's naming-mismatch flag was a false lead once the real duplication is fixed at the shell level, and a rename here would be unrequested extra churn beyond this task's scope) and their existing exported predicate functions (`isAllDegraded`, `isAllDown` — keep both, keep their current different-shaped comparisons, do not unify into one predicate: `domain-health`'s card union genuinely never includes `forbidden`, per its own doc comment, so collapsing to a single `!== 'ok'` predicate would be an unrequested behavior-equivalence assumption, not a proven no-op). Each wrapper keeps computing its own `initial`/`cards` prop, its own predicate call, its own copy text, its own testid, and its own `<RetryButton initial={initial} testidSuffix="banner" />` (the feature-local thin wrapper from item 2, unchanged call), then renders `<SharedDegradeBanner show={...} testid={...} heading={...} description={...} retry={<RetryButton .../>} />`.

## Out of Scope

- Any rename of `DegradeBanner`/`OverviewDegradeBanner`/`isAllDegraded`/`isAllDown` — the naming-inconsistency framing from the initial scan is superseded by this task's duplication-first framing; a follow-on naming pass, if still wanted after this dedup, is a separate task.
- Unifying the `isAllDegraded`/`isAllDown` predicates into one shared function — different card-status unions per each feature's own documented invariant; not provably behavior-identical without deeper investigation, deferred.
- The other 4 findings from the same fresh scan (`cellPlaceholder`/`CellStatus` 5-domain duplication; `accountStatusTone` IAM/Finance name collision; `AccountDetail`/`AccountLookup` Finance/Ledger name collision; `ecommerce-ops/api/types.ts` internal products/promotions split) — tracked as separate future candidates, not part of this task.
- Any behavior, UI, testid, or API contract change. This is a pure duplication-reduction task — every existing testid, prop name, and rendered string must be byte-identical before/after.

---

# Acceptance Criteria

- [ ] `shared/ui/RetryButton.tsx` exists, presentational-only (no `useDomainHealth`/`useOperatorOverview` import).
- [ ] `shared/ui/DegradeBanner.tsx` exists, presentational-only (no feature-specific card-type import).
- [ ] `features/domain-health/components/RetryButton.tsx` and `features/operator-overview/components/RetryButton.tsx` both delegate to `shared/ui/RetryButton.tsx`; both keep their existing exported name, props interface, and testid strings unchanged.
- [ ] `features/domain-health/components/DegradeBanner.tsx` and `features/operator-overview/components/OverviewDegradeBanner.tsx` both delegate to `shared/ui/DegradeBanner.tsx`; both keep their existing exported names, `isAllDegraded`/`isAllDown` predicates, props interfaces, testid strings, and copy text unchanged.
- [ ] All 15 files currently importing `RetryButton`/`DegradeBanner`/`OverviewDegradeBanner` (per the pre-change grep) are unchanged (no import-path or call-site edits).
- [ ] `tsc --noEmit` passes with zero new errors.
- [ ] `pnpm lint` passes with zero new errors.
- [ ] Full `console-web` unit test suite (vitest) passes unchanged — no test assertions modified, including `tests/unit/features/operator-overview/OverviewDegradeBanner.test.tsx`, `tests/unit/features/operator-overview/OperatorOverviewScreen.test.tsx`, `tests/unit/features/operator-overview/DomainCard.test.tsx`, `tests/unit/domain-health-screen.test.tsx`.
- [ ] Net LOC decreases (duplication removed, not just moved).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification (platform-console: domain=saas, traits=[multi-tenant, integration-heavy, audit-heavy]). Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/refactoring-policy.md` § Allowed Refactoring Categories → **Reduce Duplication** (Medium risk) and § Prioritization (duplication outranks naming).
- `projects/platform-console/specs/services/console-web/architecture.md` § Server vs Client Components — both `RetryButton` originals are documented as "THE ONLY CLIENT COMPONENT" in their respective `components/` directories; the new `shared/ui/RetryButton.tsx` must also stay `'use client'`-scoped and presentational-only, consistent with that server-component-first discipline. `DomainHealthScreen`/`OperatorOverviewScreen`/`DegradeBanner`/`OverviewDegradeBanner` all stay server components.
- `projects/platform-console/docs/conventions/frontend-ui.md` — `StatusBadge`/`DetailHeader`/`ConfirmDialog` shared-component promotion precedent this task follows (delegate-internally-preserve-every-call-site pattern).

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

- Grep-verify the full importer list for `RetryButton`, `DegradeBanner`, `OverviewDegradeBanner` BEFORE starting (per TASK-PC-FE-260's edge-case lesson: an import can exist somewhere unexpected — e.g. `DomainCardStates.tsx`/`DomainCard.tsx` per-card degraded-state usages, not just the banners) and again AFTER finishing, diffing the two lists to confirm zero drift.
- `§ 2.4.9.2` invariant (`forbidden` never emitted on the domain-health route) is the reason `isAllDegraded`/`isAllDown` must NOT be unified — respect it as a hard constraint, not an oversight to "fix."
- Follow the `platform/refactoring-policy.md` Rule "one category at a time" — this task is Reduce Duplication only; do not also perform any Rename in the same change even though the fresh scan originally framed this as naming-shaped.

---

# Edge Cases

- `RetryButton` is consumed by more than just the two `DegradeBanner`-family components — also by per-card degraded-state blocks (`operator-overview/components/DomainCardStates.tsx`, and the `domain-health` equivalent if present) with a different `testidSuffix` per card domain. The thin-wrapper's public `testidSuffix` prop and its resulting testid string must stay byte-identical for those call sites too, not just the banner call sites.
- `shared/ui/RetryButton.tsx` must remain a **leaf client component** — do not let it re-introduce a `useQuery`/hook dependency; the whole point of the extraction is that hook-selection is feature-owned, only the presentational shell is shared.

---

# Failure Scenarios

- `tsc --noEmit` fails after delegation → fix the prop wiring, do not leave the old duplicated JSX in place as a fallback.
- `pnpm lint` flags an unused import in either feature's now-thin wrapper (e.g. a Tailwind class string or `Button` import that moved to `shared/ui`) → remove it, it is in-scope for a dedup task to clean up its own now-dead imports.
- Vitest suite fails post-change → indicates a missed testid/prop wiring, not a false positive; revert and re-diagnose rather than modifying test assertions (per `platform/refactoring-policy.md` Prohibited: "Refactoring production code and test code in the same change").

---

# Test Requirements

- No new tests required — this is a pure duplication-reduction with zero behavior change; existing test coverage (4 test files listed in Acceptance Criteria) is the verification mechanism and must pass unmodified.

---

# Definition of Done

- [ ] All 4 scope items completed
- [ ] `tsc --noEmit` clean
- [ ] `pnpm lint` clean
- [ ] Full `console-web` vitest suite passing, zero test assertions modified
- [ ] Ready for review
