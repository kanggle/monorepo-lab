# TASK-PC-FE-162 — cross-domain console landing rename **"운영 → 개요"** (capstone)

**Status:** done
**Area:** platform-console / console-web · **Scope:** overview-capable domain landings + their nav leaves
**Type:** cross-cutting consistency sweep — **capstone**, runs LAST.
**Implemented:** branch `task/pc-fe-162-overview-rename` (bundled with the PC-FE-160 finance park). `pnpm lint` + `tsc --noEmit` + `vitest` green.
**Analysis model:** Opus 4.8 · **Impl model:** Opus.

## Goal

Now that the domain section landings are genuine overviews (counts + status + recent), rename the console's landing
heading convention **`<도메인> 운영` → `<도메인> 개요`** so the label is honest (the page IS an overview) — in one
atomic sweep across the overview-capable domains.

## Re-scope (decision — the premise changed at capstone time)

The original AC ("all 5 landings read `<도메인> 개요`") is **not achievable** and was re-scoped (user-approved
2026-07-03) to honor the task's own principle — **naming must follow capability**:

- **finance (PC-FE-160) is PARKED** — no operator overview exists (finance v1 has no list/search GET; no count
  fan-out; no synthetic ₩). Renaming `Finance 운영` → `개요` would be a **lying label** (the exact failure mode this
  task warned about). → finance **keeps `Finance 운영`** (honest lookup/ops surface). N/A for this capstone.
- **erp is NOT a `운영` landing** — the `/erp` route is `ERP 마스터` (a masters-CRUD route in a multi-route drill:
  마스터/통합조회/결재함/위임, each content-named). It is out of the `운영→개요` convention → **keeps `마스터`**.
- **Renamed (the 3 `운영` landings that are now overviews):** ecommerce / wms / scm → `개요`.

Net honesty outcome: **every `개요` landing is a real overview; the only remaining `운영` landing (finance) is
honestly NOT an overview; erp `마스터` is honestly a masters route.** No lying labels.

## Scope (implemented)

- **h1 headings** `<X> 운영` → `<X> 개요` (all branches incl. the gate/degraded/not-eligible/forbidden headings):
  - `ecommerce/page.tsx` (E-Commerce), `WmsOpsScreen.tsx` + `wms/page.tsx` (WMS), `ScmOpsScreen.tsx` + `scm/page.tsx` (SCM).
- **nav leaves** (`ConsoleSidebarNav`): `nav-wms-ops` / `nav-scm-ops` / `nav-ecommerce-ops` label `운영` → `개요`
  (testids/hrefs unchanged — nav tests assert href/aria, not the label). Consistent with the IAM drill's `개요`
  first-child precedent (PC-FE-163). finance `nav-finance-ops` stays `운영`; erp `nav-erp-masters` stays `마스터`.
- **Not touched:** the `WmsOverview`/`ScmOverview` band aria-label `"<X> 운영 개요"` (describes the operations-overview
  snapshot content, not the page heading) and prose subtitles (honest references to the operational surface).
- **Tests:** heading-text assertions updated in `wms-nav` / `scm-nav` / `ecommerce-page` / `WmsOpsScreen` /
  `ScmOpsScreen` tests.

## Acceptance Criteria

- [x] **AC-1** The 3 overview-capable landing h1 headings read `<도메인> 개요` (ecommerce/wms/scm); finance stays
  `운영` (parked), erp stays `마스터` (not a `운영` landing) — no lying `개요` label anywhere.
- [x] **AC-2** The 3 nav leaves read `개요` (testids/hrefs byte-unchanged); finance `운영` / erp `마스터` unchanged.
- [x] **AC-3** All heading-text assertions updated; `pnpm lint` + `tsc --noEmit` + `vitest` green.
- [x] **AC-4** One atomic PR (no transiently mixed-naming main).

## Dependencies

- **Was blocked by:** PC-FE-166 (wms) ✅ / -167 (scm) ✅ / -161 (erp) ✅ / -160 (finance) → PARKED; ecommerce -156 ✅.
- Capstone satisfied for the overview-capable domains; finance re-joins only if 160 is un-parked.
