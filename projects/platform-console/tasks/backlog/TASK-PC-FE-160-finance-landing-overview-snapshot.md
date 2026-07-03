# TASK-PC-FE-160 — finance landing **operator overview snapshot**

**Status:** backlog — **⚠️ PARKED / DECLINED (2026-07-03)**
**Area:** platform-console / console-web · **Route:** `app/(console)/finance/page.tsx`
**Reference impl (template):** TASK-PC-FE-156 (ecommerce overview snapshot) — mirror the pattern, adapt the data source.

## ⚠️ PARKED — do NOT re-pick from a backlog sweep

**Decision (2026-07-03, user-approved):** finance is **PARKED** — a wms-style operator overview snapshot does **not
exist** for finance, so 160 is not implemented. finance keeps its account-lookup landing (`Finance 운영`). Do NOT
re-surface this in a backlog/audit sweep as a REAL-GAP; the gap is intentional.

- **Why (PC-FE-168 finding):** finance v1 has **NO list/search GET** — `getFinanceSectionState` is account-id-driven
  (account / balances / transactions are all keyed by an operator-supplied `accountId`; there is no "list accounts"
  endpoint). So there is **no `totalElements` to fan out over**, and — per the money-sensitivity rule — **NO
  synthetic ₩/balance aggregation** is permitted either. The count/distribution/recent overview shape that
  ecommerce (156) / wms (166) / scm (167) / erp (161) share is structurally impossible here.
- **Consequence for PC-FE-162 (capstone):** finance is treated as **N/A** — it keeps the honest `Finance 운영`
  heading + `운영` nav leaf (it is a lookup/ops surface, NOT an overview). The 162 rename applied `개요` only to the
  overview-capable "운영" landings (ecommerce/wms/scm); erp keeps `마스터` (a masters-CRUD route, not a `운영`
  landing). See TASK-PC-FE-162.

## Resume condition

Re-open ONLY if a genuine finance operator need appears that a non-count band could serve — e.g.
(a) the producer adds a list/search GET (then a count overview becomes possible), OR (b) a concrete operator demand
for a minimal non-aggregate band (e.g. account-lookup + a recent-transactions glance *once an accountId is
supplied* — still no synthetic ₩ aggregation). Neither exists today. Until then, PARKED.

## Original goal (for reference)

Elevate the `/finance` section landing into an operator overview snapshot matching the ecommerce landing (PC-FE-156)
— **superseded by the PARKED decision above** (finance cannot carry the count-overview shape).

## Dependencies

- **Was blocked by:** TASK-PC-FE-168 (shared read-leg decision) — DONE (#2148); its review produced this park.
- **Relation to:** TASK-PC-FE-162 — finance is N/A in the capstone (stays `운영`).
- **Reference:** TASK-PC-FE-156 (ecommerce, DONE).
