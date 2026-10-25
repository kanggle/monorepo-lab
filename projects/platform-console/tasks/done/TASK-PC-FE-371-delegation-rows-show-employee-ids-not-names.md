# Task ID

TASK-PC-FE-371

# Title

ERP delegation screens draw the raw employee id (`01a16eaa-980d-…`) where the delegator / delegate name belongs

# Status

done

# Owner

frontend

# Task Tags

- code
- bug
- console
- erp

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (reuse the existing `ApprovalEmployeeRef` in two render sites + tests)
> Drafted 2026-10-25 UTC. Owner said «진행» to the recommendation (ERP delegation names first) on 2026-10-25 UTC. Facts read on origin/main `8aa66d152`.

# Dependency Markers

- 출처: `TASK-PC-FE-355` (done) — AC-0 recorded the cause and put it Out of Scope; the 34th demo window (`TASK-MONO-672` § 34th, Findings) saw it live: «위임 행이 직원 이름 대신 원시 id(`01a16eaa-980d-…`)를 그린다».
- 관련: `TASK-PC-FE-309` / `TASK-PC-FE-318` — the approval screens already resolve employee ids through `ApprovalEmployeeRef` (`useEmployee` single GET). `TASK-MONO-776` (approval v2.4) put every person field, delegation included, into the employee-id space.

# Goal

An operator reading «내가 위임한» / «나에게 위임된» and the 위임 현황 table sees who the delegator and delegate are (`EMP-0002 · 이운영`), not an opaque id — and an id that resolves to no employee says so honestly instead of passing for a name.

# Scope

## In Scope

- **AC-0 facts (measured while drafting):**
  - `features/erp-ops/components/DelegationGrantList.tsx:123` renders `{g[idField]}` (`delegateId` in «내가 위임한», `delegatorId` in «나에게 위임된») verbatim.
  - `features/erp-ops/components/DelegationFactCard.tsx:250-251` renders `fact.delegatorId` / `fact.delegateId` verbatim (read-model 위임 현황 table) — the same id space, the same defect.
  - The ids are employee ids: `projects/erp-platform/specs/contracts/http/approval-api.md:118-119` (v2.4 — 위임 `delegatorId` / `delegateId` are employee ids) and `:151-152` (`delegatorId` = the caller's employee, `delegateId` must be an `ACTIVE` employee).
  - The resolver already exists in the same feature: `features/erp-ops/components/approval-refs.tsx:93` `ApprovalEmployeeRef` → `useEmployee` (`hooks/masters/use-employees.ts:123`, `GET /api/erp/masterdata/employees/{id}`) → `masterRefLabel` (`code · name`, or `이름 확인 불가`, never the id). The create dialog already picks the delegate from the employee picker.
  - ⇒ **console only.** No backend or contract change, so this ships on Vercel and does not wait for a bake.
- **Fix:** render both sites through `ApprovalEmployeeRef` (the id stays in `title`).

## Out of Scope

- The 위임 현황 filter inputs (already employee selectors, `TASK-PC-FE-335`).
- Batch name resolution. One `useEmployee` per distinct id is cached by id (`staleTime` 15 s); delegation lists are short. Revisit if a list grows past a page.

# Acceptance Criteria

- [x] **AC-1** — «내가 위임한» shows the delegate's `employeeNumber · name`, «나에게 위임된» shows the delegator's, and the raw id is not in the row text (only in `title`). Red before the change.
- [x] **AC-2** — the 위임 현황 table's 위임자 / 대행자 cells show `employeeNumber · name`. Red before the change.
- [x] **AC-3** — an id with no employee (lookup 404) shows `이름 확인 불가`, never the id (control: the masterRefLabel contract).
- [x] **AC-4** — existing delegation suites stay green (revoke button, status badges, empty-list text, create dialog). Run with `--maxWorkers=2 --minWorkers=1`.
- [ ] **AC-5** — Vercel Production deploy of `kanggle-console` after merge, and the live screen at the next demo window (⚪ with the reason if not reached).

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § ERP
- `projects/erp-platform/specs/contracts/http/approval-api.md` § v2.4 person fields · § delegations

# Related Contracts

- None changed (`GET /api/erp/masterdata/employees/{id}` already exists and is already used by the approval screens).

# Edge Cases

- The same employee appears in many rows → one query (keyed by id).
- A pre-v2.4 row whose id is an account `sub` → `이름 확인 불가` (the honest answer, as on the approval screens — `approval-refs.tsx` header).

# Failure Scenarios

1. Only «내가 위임한» is fixed and the read-model table keeps the ids — AC-2.
2. A failed lookup falls back to drawing the id, which looks like a name to nobody but is not «honest unresolved» either — AC-3.

---

# Implementation — 2026-10-25 UTC

- `features/erp-ops/components/DelegationGrantList.tsx` — the row's person (`g[idField]`) through `ApprovalEmployeeRef` (`data-master-ref="approval.delegation.<idField>"`).
- `features/erp-ops/components/DelegationFactCard.tsx` — 위임자 / 대행자 cells through `ApprovalEmployeeRef` (`approval.delegationFact.*`).
- No new hook, API or contract: the component and `useEmployee` already serve the approval screens.

# Tests

- `DelegationScreen.test.tsx`: the fetch mock now answers `GET /api/erp/masterdata/employees/{id}` (known id → employee, unknown → 404). Two existing assertions that pinned the raw id in the row text (`toContain('emp-delegate')` / `('emp-boss')`) now pin the name. New: AC-1 (both lists named, id only in `title`) · AC-3 (unknown id → «이름 확인 불가», id absent).
- `DelegationFactCard.test.tsx`: new AC-2 (cells `EMP-0201 · 정위임` / `EMP-0301 · 한대행`, an orphan row → «이름 확인 불가» in both cells).
- **Red before the change:** with the fix reverted (the two component files only), the five tests above are red and the other 54 green. 🔴 The first red run did not count: my employee mock lacked the required `effectivePeriod`, so the lookup failed for a reason unrelated to the fix and «이름 확인 불가» passed for the wrong reason. Re-measured with the corrected mock: still 5 red / 54 green on the old code, 59/59 green on the fix.
- Full console: `vitest run --maxWorkers=2 --minWorkers=1` 447 files · 5544 tests · 0 failed; `tsc --noEmit` rc=0; `next lint` rc=0 (warnings only, pre-existing).
- e2e grep: no console e2e spec reads delegation row text (`overview-consolidation.spec.ts` only checks the `nav-erp-delegation` link); `tests/federation-hardening-e2e` «delegation» is IAM tenant-admin delegation, a different screen.

Open: AC-5 (Vercel Production after merge · live screen at the next demo window).

---

## CORRECTION — 35차 창 라이브 결과 (2026-10-25 UTC)

- **AC-5 ✅** (owner, Vercel production, 35th window). `/erp/delegation`:
  - «내가 위임한» row: `EMP-0002 · 이운영`.
  - 위임 현황 table: delegator `EMP-0001 · 김본부`, delegate `EMP-0002 · 이운영`.
  - No raw id in the person cells.
- Deploy: the Vercel deploy-hook run on `main` was `success`.

⇒ 판정: 모든 AC 닫힘 — 닫기 chore 대상
