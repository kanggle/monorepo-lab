# TASK-PC-FE-174 — WMS 운영 개요: 배송 기간별(오늘/주간/월간/전체) 지표

**Status:** done

**Type:** TASK-PC-FE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet (consumer-only front-end change reusing an existing producer param; no new architectural decision, no producer change)

> **Consumer-only, single-project.** Unlike the ecommerce analogue (TASK-PC-FE-164 + producer TASK-BE-468), this task adds **no producer endpoint** — it reuses the wms `admin-service` `GET /dashboard/shipments` `shippedAtFrom`/`shippedAtTo` window that already exists, honoring **ADR-MONO-017 D3.B** (no `/summary` producer retrofit on a non-absorbed federation). KST period boundaries are computed console-side.

---

## Goal

Bring the E-Commerce 운영 개요 period-metric treatment (오늘 / 주간 / 월간 + 전체, TASK-PC-FE-164) to the **배송 (shipments) count tile** of the WMS 운영 개요 (`/wms`). The tile shows 오늘 / 주간 / 월간 shipment counts (calendar KST, period-to-date) with the cumulative 전체 total retained as context.

**Scoped deliberately to 배송 only.** In the ecommerce overview the period treatment lands on the count *cards* (scale areas). WMS has two count tiles — 재고 and 배송 — but:

- **배송** is a **flow** (shipments occur over time); today/week/month is meaningful, and the producer already accepts a `shippedAt` window → period-to-date counts without a producer change.
- **재고** is a point-in-time **level** (on-hand stock); "오늘/주간/월간 재고" is not meaningful, and the inventory read model carries **no time dimension**. It stays a single-total snapshot.
- **알림 상태** is an ack-status distribution (the analogue of ecommerce's order-status distribution, which PC-FE-164 also left period-free). Unchanged.

## Scope

**In scope** (`projects/platform-console/apps/console-web/src/features/wms-ops/`):

1. **Consumer API** — `types.ts`: extend `ShipmentQueryParams` with `shippedAtFrom?: string; shippedAtTo?: string` (ISO instant). `wms-shipments-api.ts`: `listShipments` sets those query params when present. No schema change (the response shape is unchanged — only `totalElements` is read).
2. **KST boundaries helper** — new `api/kst-period.ts`: `kstPeriodBounds(now?)` returning `{ todayStartInstant, weekStartInstant, monthStartInstant, nowInstant }` as UTC instant ISO strings. The TS analogue of the shared java `KstPeriodBounds` (`libs/java-common`) the ecommerce `/summary` endpoints use: today = start of the KST day, week = current ISO week (Monday), month = the 1st. Korea has **no DST**, so a fixed +09:00 offset is used (no tz database).
3. **Overview state** — `overview-state.ts`: fan out three extra `listShipments({ shippedAtFrom: <bound>, shippedAtTo: now, page:0, size:1 })` reads (today/week/month) alongside the existing total + recent reads. `WmsAreaCount` gains an optional `period: { today, week, month } | null`; the 배송 tile carries it, the 재고 tile leaves it `null`. The tile's `status` is driven by the **total** read — a degraded period sub-read shows "—" for that bucket only, never collapsing the tile. Same `cell()` resilience (403 → forbidden, 401 → re-throw → `redirect('/login')`, else degraded).
4. **Overview presentation** — `WmsOverview.tsx`: `CountTile` renders 오늘 / 주간 / 월간 (전체 total as secondary, back-compat testid `wms-<key>-count`) when `area.period` is present; otherwise the current single-number snapshot tile (재고). New testids `wms-<key>-count-{today,week,month}`. Degraded/forbidden tile keeps the "점검 필요" / "권한 없음" placeholder.
5. **Tests** — `wms-overview-state.test.ts`: period fan-out mapping (배송 period populated, 재고 period null), a degraded period sub-read → tile stays ok with "—" bucket, existing resilience cases still green. `wms-overview.test.tsx`: 배송 period render + 전체 secondary, 재고 snapshot render unchanged. New `kst-period.test.ts`: KST boundary math incl. month/year rollover.

**Out of scope:** the inventory/alert/asn/adjustment reads (unchanged); any period *picker/toggle* (all three shown at once — no interactive filter); a period breakdown for 재고, the alert-ack distribution, or recent-shipments; the `/dashboard/throughput` endpoint (warehouse-required, not tenant-wide — not usable for this snapshot); any producer / contract change (the `shippedAtFrom/To` param is already in `console-integration-contract` § 2.4.5 / `admin-service-api.md` § 1.3).

## Acceptance Criteria

- **AC-1** — The 배송 tile displays 오늘 / 주간 / 월간 counts sourced from `shippedAtFrom`-windowed `GET /dashboard/shipments` reads (KST period-to-date), plus the cumulative 전체 total. The 재고 tile is unchanged (single total).
- **AC-2** — Resilience preserved: a `403` on the total shipments read → "권한 없음" on the 배송 tile only; a `503`/timeout on the total → "점검 필요" on the 배송 tile only; a `401` on any leg → whole-session `redirect('/login')`; a degraded **period sub-read** (today/week/month) with the total ok → that bucket renders "—", tile stays ok. No client-side auto-refetch.
- **AC-3** — KST boundaries match the shared java `KstPeriodBounds` semantics (today = KST day start, week = ISO Monday, month = 1st; +09:00 fixed). Month/year rollover for the week start is correct.
- **AC-4** — Back-compat testid `wms-shipments-count` retained (now the 전체 total); new `wms-shipments-count-{today,week,month}` present. 재고 testid `wms-inventory-count` unchanged.
- **AC-5** — `pnpm --filter console-web lint && tsc --noEmit && vitest run` green (local front-end gate — lint catches `no-unused-vars` that tsc/vitest miss).

## Related Specs

- `specs/contracts/console-integration-contract.md` § 2.4.5 (wms read-leg; `shippedAtFrom/To` window) + § 2.4.9.1 (overview snapshot leg).
- The ecommerce analogue: `tasks/done/TASK-PC-FE-164-ecommerce-overview-period-metrics.md`; the D3.B no-retrofit decision: `tasks/done/TASK-PC-FE-168-domain-overview-readleg-decision.md`.
- Shared boundary rule: `libs/java-common/.../time/KstPeriodBounds.java` (java analogue this task mirrors in TS).

## Related Contracts

- No change. Consumes existing `GET /dashboard/shipments` (`admin-service-api.md` § 1.3) `shippedAtFrom`/`shippedAtTo` (ISO instant) params.

## Edge Cases

- **Partial period degrade** — the today read `503`s while total/week/month resolve: 오늘 shows "—", 전체/주간/월간 render, tile status ok (driven by the total read).
- **Monotonicity** — the producer window guarantees 오늘 ≤ 주간 ≤ 월간 ≤ 전체; the UI does not re-derive or assert it, just renders.
- **재고 has no period** — inventory tile renders its single total with no 오늘/주간/월간 row (`period` is `null`).
- **Zero activity** — `{today:0, week:0, month:0}` renders as `0`/`0`/`0`, not "점검 필요" (a successful zero is `ok`).
- **KST week start at month boundary** — e.g. a Wednesday 2026-07-01: the ISO-Monday week start rolls back into June; `Date.UTC` normalization handles the negative day-of-month.
- **notEligible operator** — no fan-out, section renders null (unchanged).

## Failure Scenarios

- **`shippedAtFrom` not honored (producer skew)** — if a deploy predates the param, the producer ignores the unknown query param and returns the unfiltered total for every period read → 오늘/주간/월간 all equal 전체. Degraded UX, never a crash (no schema change; `totalElements` still parses). Acceptable pre-deploy skew behavior.
- **Clock skew (future shippedAt)** — `shippedAtTo: now` upper-bounds the window; a shipment stamped slightly in the future is excluded from 오늘 but still counted in 전체. Monotonicity holds.
- **Extra fan-out cost** — 배송 now issues 4 count reads (total + 3 windows) instead of 1; all parallel and bounded within the existing `Promise.all`. No producer `/summary` means the console pays with reads — the accepted D3.B trade-off.
