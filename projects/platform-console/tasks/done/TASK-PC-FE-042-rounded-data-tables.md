# Task ID

TASK-PC-FE-042

# Title

`console-web` — give the bordered data tables **rounded corners** (Vercel look). The PC-FE-041 outer frame used `border-collapse + border`, but the collapsed-border model ignores `border-radius`, so corners stay square. Switch the 17 tables to a shared `.data-table` class (`border-separate + border-spacing-0 + overflow-hidden + rounded-lg + border`) and move the horizontal row dividers from the (now ignored) per-row `border-b` to the cells' bottom border.

# Status

done

# Owner

frontend-engineer (console-web presentation — table styling only; no API / contract / domain / markup-structure change)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code

---

# Dependency Markers

- **follows**: PC-FE-041 (added the `border border-border` outer frame on every `<table>`) / PC-FE-038 (design tokens — `border-border`).
- **root cause**: CSS `border-collapse: collapse` (the Tailwind `border-collapse` utility the tables use) **ignores `border-radius`** — the collapsed-border model has no rounded corners. PC-FE-041's `border border-border` therefore renders square corners. The only ways to round a table are (a) a wrapper element with `overflow-hidden rounded`, or (b) `border-collapse: separate` + `overflow-hidden rounded` on the table itself. (b) keeps the markup wrapper-free but **ignores per-row (`<tr>`) borders** (separate-borders model only honors borders on cells + the table), so the existing `<tr className="border-b">` dividers must be re-expressed as cell (`td`/`th`) bottom borders. Done once in a shared `.data-table` class rather than 17 wrappers.
- **no dependency on**: any backend / contract / ADR change.

# Goal

Every console data table renders as a rounded, bordered card (outer `border-border` frame with `rounded-lg` corners), with the same horizontal header/row dividers and no vertical gridlines — matching the Vercel data-table aesthetic.

# Scope

## In Scope

- **`app/globals.css`**: add a `@layer components` `.data-table` rule —
  `@apply w-full border-separate border-spacing-0 overflow-hidden rounded-lg border border-border text-sm;`
  plus `.data-table thead th, .data-table tbody td { @apply border-b border-border; }` (row dividers as cell bottom borders) and `.data-table tbody tr:last-child td { @apply border-b-0; }` (no divider under the last row, so the rounded bottom corners are clean).
- **17 `<table>` className edits** (wms ×2 / scm ×4 / scm PoDetailDialog / operators / finance Transactions + Balances / erp ×5 / audit / accounts): replace the shared `w-full border-collapse border border-border text-sm` with `data-table` (the leading margin utility — `mb-3` / `mb-6` / `mb-8` — and `data-testid` are kept verbatim). The per-`<tr>` `border-b border-border` classes are left in place but are inert under the separate-borders model (the cells now own the dividers); not removed to keep the diff to the table tag.

## Out of Scope

- Removing the now-inert `<tr> border-b` utility classes (cosmetic source debt only; would touch every row template for no visual change). Documented here instead.
- Vertical gridlines, zebra striping, sticky headers, per-cell rounding — unchanged.
- Any wrapper component / markup-structure change, any data / route / API / contract change.

# Acceptance Criteria

- [x] **AC-1** Every console `<table>` renders with `rounded-lg` corners on its `border-border` outer frame (verified by `next build` + visual at `:3000`).
- [x] **AC-2** Horizontal dividers are preserved (header underline + a line between each row) and there are still no vertical gridlines; the last row has no divider under it (clean rounded bottom).
- [x] **AC-3** No markup-structure change — only the `<table>` className and `globals.css`; all table `data-testid`s and the row/cell content are unchanged (existing unit/e2e selectors intact).
- [x] **AC-4** `pnpm test` + `tsc --noEmit` exit 0 + `next lint` clean + `next build` success.

# Related Specs

- `architecture.md` § presentation (Tailwind utility convention; `.data-table` is a `@layer components` shared style — the one place table chrome is defined). No contract/spec impact (presentation only).

# Edge Cases

- **Table without an explicit `<tbody>`/`<thead>`**: the browser auto-inserts `<tbody>`, so `.data-table tbody td` still matches; a header row living in `<tbody>` simply gets a cell divider too (still a valid divider).
- **Dark mode**: `border-border` is token-backed (`.dark` flips it) — the rounded frame + dividers follow the theme automatically.
- **`overflow-hidden` on `display: table`**: the standard rounded-table technique (separate borders + overflow-hidden) clips the rounded corners in modern browsers (Chromium/Playwright + the user's browser).

# Failure Scenarios

- If a leftover `border-collapse` utility stayed on a table, it would override `.data-table`'s `border-separate` and re-square the corners + hide the cell dividers. Avoided: the `border-collapse` utility is removed from every table className (folded into `.data-table`).

# Test Requirements

- No new unit test (pure CSS/className; no behaviour). `pnpm test` (regression) + `tsc --noEmit` + `next lint` + `next build` green.
- Local rebuild + container restart for live confirmation at `http://localhost:3000`.

# Definition of Done

- [x] `.data-table` component class + 17 table className swaps.
- [x] `pnpm test` + `tsc --noEmit` + `next lint` + `next build` green.
- [x] Local federation-e2e `console-web` rebuilt + restarted (live at :3000).
- [x] No API/route/contract/markup-structure change; diff confined to `globals.css` + the 17 `<table>` tags.
- [x] Task md + `INDEX.md` updated.
- [x] Reviewed + merged (impl PR #1063 squash `a6287aa2`, 3-dim verified; all CI GREEN, no transient).

---

분석=Opus 4.8 / 구현=Opus(직접). 사용자 요청 "테이블 테두리선 모서리 라운드 줘". **메타: `border-collapse` 는 `border-radius` 를 무시(collapsed-border model) — 라운드하려면 wrapper `overflow-hidden` 또는 `border-separate`. 후자는 `<tr>` 테두리를 무시하므로 행 구분선을 셀(`td/th`) 하단 테두리로 재구성. 17 테이블 공통이라 wrapper 17개 대신 단일 `.data-table` 컴포넌트 클래스로 집약(DRY).**
