# Task ID

TASK-PC-FE-060

# Title

console-web sidebar: group 감사·보안 + 운영자 관리 under a drill-in `IAM` parent — both are IAM-platform admin surfaces (`${IAM_ADMIN_API_BASE}/api/admin/{audit,operators}`), so they nest under one parent exactly like WMS → 운영/출고 (TASK-PC-FE-059)

# Status

ready

# Owner

frontend (Opus 4.8 analysis / Sonnet 4.6 impl) — `ConsoleSidebarNav` additive grouping; reuses the PC-FE-059 drill-in model; no contract/spec change.

# Task Tags

- code
- test

---

# Dependency Markers

- **builds on**: TASK-PC-FE-059 (the Vercel-style drill-in sidebar — a top-level item with submenus becomes a toggle that pins at the top + reveals its children; route auto-open + longest-prefix active). This task adds a **second** drill parent using that exact model.
- **rationale**: `감사·보안`(`/audit`) and `운영자 관리`(`/operators`) both call the **IAM platform** admin API — `audit-api.ts` → `${env.IAM_ADMIN_API_BASE}/api/admin/audit`, `operators-api.ts` → `${env.IAM_ADMIN_API_BASE}/api/admin/operators` (`IAM_ADMIN_API_BASE` default `http://iam.local`). They are IAM cross-cutting console-admin functions, so they belong under one `IAM` parent.
- **spec**: none. Sidebar interaction/grouping is a UI concern (PC-FE-039/059 precedent — no `console-integration-contract` change). Destinations `/audit`, `/operators` and their screens/backends are unchanged.

# Goal

Replace the two flat `관리`-group leaves (`감사·보안`, `운영자 관리`) with a single drill-in `IAM` parent (`nav-iam`) whose children are `감사·보안` (`nav-audit` → `/audit`) and `운영자 관리` (`nav-operators` → `/operators`). Behavior is identical to the WMS parent: clicking `IAM` pins it at the top + reveals the two children; clicking the pinned `IAM` drills back out; deep-linking `/audit` or `/operators` auto-opens `IAM` with the matching child `aria-current`.

# Scope

## In Scope

- **`src/shared/ui/ConsoleSidebarNav.tsx`** — in the `관리` group, replace the two `NavLeaf`s with one `NavParent` `{ key:'iam', label:'IAM', testid:'nav-iam', children:[감사·보안, 운영자 관리] }`. No other change — the existing drill-in render/state/auto-open/longest-prefix logic already handles any parent generically (`PARENTS`/`parentKeyForPath`/`activeHref`).
- Preserve `data-testid`s + `href`s: `nav-audit` (→ `/audit`), `nav-operators` (→ `/operators`) now the IAM children; `nav-iam` is the new parent toggle.
- **Tests**: `tests/unit/sidebar-iam-group.test.tsx` (new) — collapsed `관리` group shows `IAM` as a toggle (not a link) with `nav-audit`/`nav-operators` hidden; clicking `IAM` reveals both children + pins `IAM`; clicking the pinned `IAM` restores the top-level list; deep-link `/audit` auto-opens IAM with `감사·보안` active and `운영자 관리` inactive (and vice-versa for `/operators`). `domain-health-nav.test.tsx` source guard stays green (`nav-audit`/`nav-operators` literals preserved as children).

## Out of Scope

- Moving `/accounts` (the IAM account catalog surface, reached via the catalog `iam → /accounts`) into this nav parent — it is a separate catalog-reached destination, unchanged.
- Any change to `/audit`, `/operators` pages, screens, or their IAM backend.
- Converting any other group; multi-level (>1) drill.
- Any contract/spec change.

# Acceptance Criteria

- [ ] Collapsed: the `관리` group renders a single `IAM` toggle button (`nav-iam`, not a link); `감사·보안`/`운영자 관리` are hidden until drilled.
- [ ] Clicking `IAM` drills in — `감사·보안`(`nav-audit`→`/audit`) + `운영자 관리`(`nav-operators`→`/operators`) appear, `IAM` pinned at the top; clicking the pinned `IAM` returns to the full top-level list.
- [ ] Deep-link `/audit` → IAM auto-opened, `감사·보안` `aria-current=page`, `운영자 관리` not; deep-link `/operators` → the reverse.
- [ ] `pnpm exec vitest run` green (new `sidebar-iam-group` + existing `domain-health-nav` guard + `audit-nav`/`operators-nav` screen tests, no regression); `npx tsc --noEmit` clean; scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.2 (audit read surface) / operators admin surface — destinations unchanged.

# Related Contracts

- None changed. Sidebar grouping is a UI concern.

# Target Service

- `platform-console` / `apps/console-web` — `src/shared/ui/ConsoleSidebarNav.tsx` + a unit test. No route/data/backend change.

# Architecture

- Reuses the PC-FE-059 single-level drill model unchanged — only the `GROUPS` data gains a second `NavParent`. The generic `PARENTS`/`parentKeyForPath`/`activeHref` already drive auto-open + active resolution for any parent.

# Edge Cases

- Deep-link `/audit` or `/operators` → IAM auto-drilled, correct child active.
- Collapse (click pinned IAM) while on `/audit` → list shown; next navigation into `/audit`|`/operators` re-opens IAM.
- `/audit` and `/operators` are non-overlapping paths → no longest-prefix ambiguity (unlike `/wms` vs `/wms/outbound`), but the same resolution applies.

# Failure Scenarios

- `nav-iam` rendered as a `<Link>` → drill never triggers → AC asserts it is a toggle button.
- A preserved `nav-audit`/`nav-operators` literal dropped from source → `domain-health-nav` guard fails → AC asserts the guard stays green.

# Definition of Done

- [ ] `IAM` drill parent groups 감사·보안 + 운영자 관리; drill in/out + route auto-open + active correct
- [ ] vitest + tsc green, no regression; scope = console-web only
- [ ] Acceptance Criteria satisfied
- [ ] Ready for review
