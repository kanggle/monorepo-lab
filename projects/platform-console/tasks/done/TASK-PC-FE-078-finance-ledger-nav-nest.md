# Task ID

TASK-PC-FE-078

# Title

console-web — nest the finance ledger surface under a Finance drill parent (운영 + 원장), mirroring the WMS two-surface nav

# Status

done

# Owner

frontend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

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

# Dependency Markers

- **depends on**: `TASK-PC-FE-072` (the `features/ledger-ops` section + the `/ledger` route and `nav-ledger` entry) **MERGED** → origin/main. This task only regroups the existing `/finance` + `/ledger` nav entries; it adds no route, no producer call, no contract.
- **precedent (no new pattern)**: the drill-parent model already exists for **WMS** (`nav-wms` parent → `운영`/`nav-wms-ops` + `출고`/`nav-wms-outbound`, TASK-PC-FE-059) and **IAM** (`nav-iam` → 계정 운영/감사/운영자). This task applies the SAME `NavParent` shape to Finance — the finance ledger code already frames itself as *"the SECOND finance-product service… exactly as the wms outbound surface is the second wms service"* (`features/ledger-ops/api/ledger-api.ts`).
- **nav-only**: no change to `/finance` or `/ledger` routes, page components, API clients, or the finance/ledger producers. Pure information-architecture regrouping in `ConsoleSidebarNav.tsx`.

---

# Goal

Finance is ONE domain (`finance-platform`) with TWO console surfaces — account-service (계좌·잔액·거래) and ledger-service (시산표·기간·대조). They share the finance tenant gate and a single entitlement (`entitled_domains ∋ finance` gates BOTH surfaces). Today the sidebar exposes them as two flat sibling leaves (`Finance` → `/finance`, `Finance Ledger` → `/ledger`), which is inconsistent with the established two-surface pattern (WMS nests 운영 + 출고 under one `WMS` parent). Regroup the two finance surfaces under one `Finance` drill parent so the navigation matches the domain/entitlement boundary and the existing WMS/IAM convention.

# Scope (in/out)

**In:**

- `ConsoleSidebarNav.tsx` — replace the two flat leaves (`nav-finance` → `/finance`, `nav-ledger` → `/ledger`) with a single `Finance` `NavParent` (`key: 'finance'`, `testid: 'nav-finance'`) whose children are:
  - `운영` → `/finance` (testid `nav-finance-ops`) — the former `nav-finance` destination, renamed for the `-ops` child convention (mirrors the WMS `nav-wms` → `nav-wms-ops` move).
  - `원장` → `/ledger` (testid `nav-ledger`) — unchanged href + testid.
- `tests/unit/sidebar-drilldown.test.tsx` — update the finance assertions (finance is now a toggle button, not a leaf link) + add finance drill-in / deep-link auto-open coverage mirroring the WMS tests.

**Out:**

- Any change to `/finance` or `/ledger` routes, page components, the finance/ledger API clients, or the producers.
- Any new list/search surface (finance + ledger remain id-driven — out of scope, separate decision).
- Nav-level entitlement gating (unchanged — eligibility is resolved per-page from the registry, not the nav).

# Acceptance Criteria

- The sidebar `도메인 운영` group renders a single `Finance` parent (toggle button, no `href`) between `SCM` and `ERP`; `Finance Ledger` is no longer a top-level entry.
- Opening the `Finance` parent reveals `운영` (→ `/finance`) and `원장` (→ `/ledger`).
- A deep link to `/finance` auto-opens the Finance drill with `운영` active (`aria-current="page"`) and `원장` inactive; a deep link to `/ledger` auto-opens it with `원장` active and `운영` inactive (longest-match rule — same as WMS `/wms` vs `/wms/outbound`).
- `nav-ledger` href/testid is unchanged; the `/finance` destination is reachable via the `nav-finance-ops` child.
- `domain-health-nav.test.tsx` (asserts `'nav-finance'` present + `nav-erp` after `nav-finance`) stays GREEN — `nav-finance` remains the parent testid and ERP stays after Finance in source order.
- `sidebar-drilldown.test.tsx` updated + GREEN; the console-web unit suite passes.

# Related Specs

- `projects/platform-console/apps/console-web/specs/.../console-web/architecture.md` — sidebar drill-in IA (TASK-PC-FE-059 / -060 / -062). This task adds Finance to the same `NavParent` model; no spec semantics change (pure regrouping). If the architecture doc enumerates the nav tree, update the Finance entry to the parent form.

# Related Contracts

- None. No API request/response, no event, no producer contract is touched. `/finance` and `/ledger` continue to call the same finance account-service / ledger-service endpoints unchanged.

# Edge Cases

- **Deep-link active state**: `/finance` must not also light up on `/ledger` (and vice-versa) — handled by the existing `activeHref` longest-match logic; `/finance` is not a prefix of `/ledger`, so the two are independent.
- **Active parent auto-open**: landing on `/finance` or `/ledger` must auto-open the Finance parent (existing `activeParentKey` logic over `parent.children`).
- **testid migration**: `nav-finance` flips from a leaf link to a parent button. Any test/e2e asserting `nav-finance` is an `<a href="/finance">` must move to `nav-finance-ops` (only `sidebar-drilldown.test.tsx` does — updated here; no Playwright spec references it).

# Failure Scenarios

- **Stale test referencing `nav-finance` as a link** → unit-test RED. Mitigation: the in-scope `sidebar-drilldown.test.tsx` edit; repo-wide grep confirms no other `nav-finance`/`nav-ledger` link-click reference.
- **Operator confusion (lost menu)**: a user who bookmarked the top-level `Finance Ledger` entry no longer sees it at the top level. Mitigation: deep links to `/ledger` still resolve and auto-open the Finance drill with 원장 active — the route is unchanged, only its nav position moved one level in.
