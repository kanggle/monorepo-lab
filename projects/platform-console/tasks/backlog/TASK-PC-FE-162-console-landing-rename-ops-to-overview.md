# TASK-PC-FE-162 — cross-domain console landing rename **"운영 → 개요"** (capstone)

**Status:** backlog
**Area:** platform-console / console-web · **Scope:** all 5 domain section landings + (optional) nav leaves
**Type:** cross-cutting consistency sweep — **capstone**, runs LAST.

## Goal

Once every domain section landing is a genuine overview (counts + status + recent), rename the console's domain
section landing heading convention from **`<도메인> 운영` → `<도메인> 개요`** in **one atomic sweep**, so the label
is honest (the page IS an overview) AND consistent across all 5 domains. Doing this only after the per-domain
overview tasks land avoids the two failure modes: (a) renaming ahead-of-capability = a "개요" page with no overview
content (lying label), and (b) renaming one domain alone = cross-domain inconsistency.

## Why capstone / sequencing (the decision this task records)

- Naming must follow capability. Today only ecommerce (PC-FE-156) has real overview content; the others are
  health + links. `운영` is currently the honest, consistent label for all 5.
- So: bring wms/scm/finance/erp landings up to overview parity (PC-FE-158/159/160/161) FIRST, then this single
  rename makes every landing genuinely an overview with a consistent title.

## Scope (to be finalized)

- Rename the h1 heading of each domain landing: `wms/scm/finance/erp/ecommerce/page.tsx`
  `<X> 운영` → `<X> 개요` (headings currently confirmed uniform `<X> 운영`).
- **Decide** whether to also rename the sidebar `ConsoleSidebarNav` per-domain leaf `운영` (→ /<domain>) to `개요`
  (its siblings are 상품/주문/etc., so `개요` reads better) — must be applied to ALL domains together if done.
- Update any heading testids / `aria-labelledby` copy references + tests that assert the `운영` heading text.
- Update `console-web/architecture.md` + `console-integration-contract.md` naming references if any.

## Acceptance Criteria (draft — finalize before ready)

- All 5 domain landing h1 headings read `<도메인> 개요`; no `<도메인> 운영` landing heading remains.
- (If nav leaf renamed) all 5 domain nav leaves consistent; no mixed 운영/개요.
- All heading-text assertions in tests updated; `pnpm lint` + `tsc` + `vitest` green.
- One atomic PR (cross-cutting consistency — avoid a transiently mixed-naming main).

## Dependencies

- **Blocked by (ALL must be done first):** TASK-PC-FE-158 (wms), -159 (scm), -160 (finance), -161 (erp) overview
  snapshots. ecommerce (TASK-PC-FE-156) already DONE.
- Do NOT promote this to `ready` until 158–161 are `done` (else it renames still-bare landings to a false "개요").

## Promotion note

Capstone — gated on the 4 per-domain overview tasks. Finalize the nav-leaf decision + test/spec impact before
`backlog → ready`.
