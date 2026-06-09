# Task ID

TASK-PC-FE-062

# Title

console-web sidebar: add 계정 운영(`/accounts`) as a child of the IAM drill parent — make the sidebar IAM group consistent with the catalog IAM tile (which resolves to `/accounts`)

# Status

done

# Owner

frontend (Opus 4.8 analysis / Sonnet 4.6 impl) — one-line `GROUPS` data addition to `ConsoleSidebarNav`; reuses the PC-FE-059 drill engine; no contract/spec/backend change.

# Task Tags

- code
- test

---

# Dependency Markers

- **fixes inconsistency surfaced by user (2026-06-09)**: the catalog IAM tile resolves to `/accounts`(계정 운영) via `resolveConsoleRoute('iam') → '/accounts'` ([catalog/lib/console-route.ts]), but the sidebar IAM drill parent (TASK-PC-FE-060) only has 감사·보안(/audit) + 운영자 관리(/operators) — 계정 운영 is missing, so the sidebar "IAM" doesn't lead to the same place the catalog "IAM" does.
- **root cause**: `/accounts`(계정 운영, TASK-PC-FE-002 IAM accounts operator surface) was historically a CATALOG-reached destination with NO sidebar item; PC-FE-060 deliberately scoped it OUT. This task reverses that decision per the user, grouping all 3 IAM surfaces under the sidebar IAM drill.
- **builds on**: TASK-PC-FE-059 (drill engine) + TASK-PC-FE-060 (IAM drill parent).
- **distinct routes (do not confuse)**: `/accounts`(계정 운영 = manage IAM accounts, catalog IAM target) vs `/account`(계정 설정 = my own identity, top-bar ⋮ menu) vs `/operators`(운영자 관리).

# Goal

Add 계정 운영(`/accounts`, `nav-accounts`) as the FIRST child of the sidebar IAM drill parent, so the IAM drill = [계정 운영(/accounts), 감사·보안(/audit), 운영자 관리(/operators)]. Now the sidebar IAM and the catalog IAM both lead to 계정 운영, and all IAM surfaces are grouped + sidebar-reachable. Deep-linking `/accounts` auto-opens the IAM drill with 계정 운영 active.

# Scope

## In Scope

- **`src/shared/ui/ConsoleSidebarNav.tsx`** — in the IAM `NavParent.children`, prepend `{ href: '/accounts', label: '계정 운영', testid: 'nav-accounts' }`. No other change — the generic drill engine (`PARENTS`/`parentKeyForPath`/`activeHref`) already auto-opens IAM on `/accounts` and resolves the active child by longest prefix (`/accounts` vs `/audit`/`/operators` are non-overlapping; `/account` singular does NOT match `/accounts`).
- **Tests** — `tests/unit/sidebar-iam-group.test.tsx`: assert the IAM drill now reveals 계정 운영(`nav-accounts`→`/accounts`) alongside 감사·보안 + 운영자 관리; deep-link `/accounts` auto-opens IAM with 계정 운영 `aria-current=page` (and `/audit`,`/operators` not); `/account` (singular) does NOT activate 계정 운영. `domain-health-nav` source guard stays green (nav-audit/nav-operators literals preserved).

## Out of Scope

- Any change to the `/accounts` page, the `accounts` feature, or its backend.
- Removing the catalog IAM → /accounts routing (`resolveConsoleRoute` unchanged — both paths still lead to /accounts).
- Any other group/parent; the top-bar 계정 설정(`/account`) entry.
- Contract/spec change.

# Acceptance Criteria

- [ ] Sidebar IAM drill reveals 계정 운영(`nav-accounts`→`/accounts`) + 감사·보안(`nav-audit`→`/audit`) + 운영자 관리(`nav-operators`→`/operators`); 계정 운영 is the first child.
- [ ] Deep-link `/accounts` → IAM auto-opened, 계정 운영 `aria-current=page`; 감사·보안/운영자 관리 not active.
- [ ] `/account` (singular, my settings) does NOT activate 계정 운영 (`/account` !⊂ `/accounts`).
- [ ] `resolveConsoleRoute('iam')` still `/accounts` (catalog IAM unchanged — `catalog-route` test stays green).
- [ ] `pnpm exec vitest run` green (updated sidebar-iam-group + existing guards, no regression); `npx tsc --noEmit` clean; scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.1 (accounts surface) — destination unchanged.

# Related Contracts

- None changed.

# Target Service

- `platform-console` / `apps/console-web` — `ConsoleSidebarNav.tsx` (one child added) + unit test. No route/data/backend change.

# Architecture

- Reuses the PC-FE-059 drill engine unchanged — only the IAM parent's `children` array gains a leaf. Active resolution by longest prefix already disambiguates `/accounts` vs siblings and vs `/account`.

# Edge Cases

- Deep-link `/accounts/{id}` → 계정 운영 active via prefix match.
- `/account` (singular) → IAM drill NOT auto-opened by this child (distinct route); top-bar 계정 설정 path unaffected.
- Other IAM children (/audit, /operators) unaffected.

# Failure Scenarios

- `/account` wrongly activates 계정 운영 → AC asserts the singular route does not match.
- `nav-audit`/`nav-operators` literal dropped → `domain-health-nav` guard fails → AC asserts it stays green.

# Definition of Done

- [ ] 계정 운영 added as IAM drill child; auto-open + active correct; catalog routing intact
- [ ] vitest + tsc green, no regression; scope = console-web only
- [ ] Acceptance Criteria satisfied
- [ ] Ready for review
