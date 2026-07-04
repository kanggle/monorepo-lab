import { redirect } from 'next/navigation';
import { ApiError } from '@/shared/api/errors';
import { listInventory, listOrders } from './wms-inventory-api';
import { listShipments, listAdjustments } from './wms-shipments-api';
import { listAlerts } from './wms-alerts-api';
import { kstPeriodBounds } from './kst-period';
import type { ShipmentRow, AdjustmentRow } from './types';

/**
 * Server-side wms **operator overview snapshot** fan-out for the `/wms`
 * landing (TASK-PC-FE-166 — the FIRST bff-domain reference implementation of
 * the console domain-landing overview series; the analogue of the ecommerce
 * `getEcommerceOverviewState` for the console-bff read-leg domains).
 *
 * ── ARCHITECTURE (console-web DIRECT fan-out; PC-FE-168 shared decision) ──
 * Per the PC-FE-168 shared read-leg decision, the wms/scm/finance/erp landing
 * overviews use the SAME console-web DIRECT fan-out as ecommerce (§ 2.4.10.6),
 * NOT a console-bff leg: every one of these domains already reaches its
 * producer server-side via `getDomainFacingToken()` (the § 2.4.5/6/7/8 direct
 * clients). The console-bff (§ 2.4.9.1/.2) is only the console-HOME
 * cross-domain dashboards — a single-domain landing snapshot needs no
 * server-side fan-in. So this reuses the feature's own `list*` api functions
 * and derives counts from each list's `totalElements` read with
 * `?page=0&size=1` (ADR-MONO-017 D3.B — NO producer `/summary`, NO producer
 * retrofit; the wms bff-domains do NOT get the ecommerce `/summary` treatment
 * that PC-FE-164 added, precisely because that is a producer retrofit D3.B
 * forbids for a non-absorbed federation).
 *
 * ── RESILIENCE (§ 2.4.5 / § 2.5) — the decisive rule ──
 * The fan-out is bounded + parallel. Each cell CATCHES its own error into a
 * cell status (ok / forbidden / degraded) EXCEPT `401`, which it re-throws so
 * the top-level catch performs a whole-session `redirect('/login')` (no partial
 * authed state — same invariant as `getWmsSectionState` / the ecommerce
 * `cell()`). One area's degrade never blanks the snapshot. `WmsUnavailableError`
 * (503/timeout/network) is NOT an `ApiError`, so it degrades the cell (never a
 * re-login). No auto-refetch (operator-overview discipline).
 */

export type CellStatus = 'ok' | 'forbidden' | 'degraded';

/** KST period-to-date counts for a FLOW area (배송) — 오늘/주간/월간. Each is
 *  `null` when that period's sub-read did not resolve (the tile stays `ok` as
 *  long as the total read did; the null bucket renders "—"). */
export interface WmsAreaPeriod {
  today: number | null;
  week: number | null;
  month: number | null;
}

/** One operator-area count tile (label + total count + per-cell status). */
export interface WmsAreaCount {
  key: string;
  label: string;
  /** `totalElements` from the area's list read, or `null` when the cell did
   *  not resolve (degraded/forbidden). */
  count: number | null;
  status: CellStatus;
  /** Period-to-date breakdown for a FLOW area (배송, PC-FE-174). `null` for a
   *  point-in-time LEVEL area (재고) which has no time dimension — that tile
   *  renders its single total only. */
  period?: WmsAreaPeriod | null;
  /** Low-stock sub-count for the 재고 LEVEL area (PC-FE-177) — the number of
   *  SKU-locations below their reorder point (`lowStockOnly=true`), a
   *  replenishment attention signal. `undefined` for non-inventory tiles;
   *  `null` when the low-stock sub-read did not resolve (the tile stays `ok`
   *  on its total read — same pattern as a 배송 period sub-read). */
  lowStock?: number | null;
}

/** One alert-acknowledgement distribution bucket. */
export interface WmsAlertStatusCount {
  key: 'unacknowledged' | 'acknowledged';
  label: string;
  count: number | null;
  cellStatus: CellStatus;
}

export interface WmsOverviewState {
  /** True when the operator is not wms-eligible — no fan-out was run. */
  notEligible: boolean;
  counts: WmsAreaCount[];
  alertStatus: WmsAlertStatusCount[];
  recentShipments: ShipmentRow[] | null;
  recentShipmentsStatus: CellStatus;
  /** 최근 재고 조정 (recent inventory adjustments) glance — the 재고-side
   *  activity companion to `recentShipments` (PC-FE-186). Null when the sub-read
   *  did not resolve (status carries the reason). */
  recentAdjustments: AdjustmentRow[] | null;
  recentAdjustmentsStatus: CellStatus;
}

const EMPTY: WmsOverviewState = {
  notEligible: false,
  counts: [],
  alertStatus: [],
  recentShipments: null,
  recentShipmentsStatus: 'degraded',
  recentAdjustments: null,
  recentAdjustmentsStatus: 'degraded',
};

/** Recent-activity page size (clamped to [1, max] by the list client). */
const RECENT_SIZE = 5;

interface Cell<T> {
  value: T | null;
  status: CellStatus;
}

/**
 * Resolve a single fan-out leg into a cell: success → `ok`; `403` → `forbidden`;
 * `503`/timeout/network (`WmsUnavailableError`, not an `ApiError`) / other →
 * `degraded`. A `401` is RE-THROWN so the caller performs a whole-session
 * `redirect('/login')` (never a per-cell degrade).
 */
async function cell<T>(p: Promise<T>): Promise<Cell<T>> {
  try {
    return { value: await p, status: 'ok' };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      throw err; // whole-session re-login — propagate, do not degrade.
    }
    if (err instanceof ApiError && err.status === 403) {
      return { value: null, status: 'forbidden' };
    }
    return { value: null, status: 'degraded' };
  }
}

/**
 * @param eligible whether the operator is wms-eligible (resolved by the page
 *   from the data-driven registry). `false` ⇒ no fan-out, no calls (never
 *   fabricate a cross-tenant wms call — wms resolves the tenant from the JWT
 *   claim, § 2.4.5).
 */
export async function getWmsOverviewState(
  eligible: boolean,
): Promise<WmsOverviewState> {
  if (!eligible) {
    return { ...EMPTY, notEligible: true };
  }

  // KST period-to-date windows for the 배송 flow metrics (PC-FE-174). Computed
  // console-side (no producer `/summary` retrofit — ADR-MONO-017 D3.B) and
  // passed as the existing `shippedAtFrom`/`shippedAtTo` window.
  const b = kstPeriodBounds();

  try {
    const [
      invCell,
      lowStockCell,
      shipCell,
      shipTodayCell,
      shipWeekCell,
      shipMonthCell,
      unackedCell,
      ackedCell,
      recentCell,
      openOrdersCell,
      recentAdjCell,
    ] = await Promise.all([
      cell(listInventory({ page: 0, size: 1 })),
      // 저재고 (low-stock) attention sub-count for the 재고 tile (PC-FE-177) —
      // SKU-locations below their reorder point. Own cell: a degrade nulls the
      // sub-count only, the 재고 tile stays ok on its total read.
      cell(listInventory({ lowStockOnly: true, page: 0, size: 1 })),
      cell(listShipments({ page: 0, size: 1 })),
      cell(
        listShipments({
          shippedAtFrom: b.todayStartInstant,
          shippedAtTo: b.nowInstant,
          page: 0,
          size: 1,
        }),
      ),
      cell(
        listShipments({
          shippedAtFrom: b.weekStartInstant,
          shippedAtTo: b.nowInstant,
          page: 0,
          size: 1,
        }),
      ),
      cell(
        listShipments({
          shippedAtFrom: b.monthStartInstant,
          shippedAtTo: b.nowInstant,
          page: 0,
          size: 1,
        }),
      ),
      cell(listAlerts({ acknowledged: false, page: 0, size: 1 })),
      cell(listAlerts({ acknowledged: true, page: 0, size: 1 })),
      cell(listShipments({ page: 0, size: RECENT_SIZE })),
      // 미출고 주문 (open outbound orders, PC-FE-186) — the read model collapses
      // order status to RECEIVED/SHIPPED/CANCELLED (picking/packing events are
      // swallowed), so `status=RECEIVED` counts orders received-but-not-yet-
      // shipped/cancelled = the open outbound backlog. A LEVEL count (no period),
      // the pipeline companion to 배송 (completed shipments).
      cell(listOrders({ status: 'RECEIVED', page: 0, size: 1 })),
      // 최근 재고 조정 (recent inventory adjustments, PC-FE-186) — the 재고-side
      // activity glance companion to 최근 출고.
      cell(listAdjustments({ page: 0, size: RECENT_SIZE })),
    ]);

    // Count tiles cover the WMS operational-scale areas only — 재고 and 배송
    // (business objects whose totals read as a scale snapshot). Alerts are a
    // derived attention-signal stream, NOT a scale area, and a total-alerts
    // count merely duplicates the (미확인 + 확인) sum in the alert-status
    // distribution below (PC-FE-170); so alerts are represented solely by that
    // distribution and no total-alerts fan-out leg is issued.
    //
    // 재고 is a point-in-time LEVEL (no time dimension) → single-total snapshot,
    // no period. 배송 is a FLOW → 오늘/주간/월간 period-to-date + 전체 total
    // (PC-FE-174); the tile status follows the TOTAL read, so a degraded period
    // sub-read only nulls that one bucket (rendered "—").
    const counts: WmsAreaCount[] = [
      areaCount('inventory', '재고', invCell, lowStockCell),
      // 미출고 주문 (open orders) — a LEVEL backlog tile, sits between 재고 and
      // 배송 so the band reads 재고 → 미출고(pending) → 배송(shipped).
      areaCount('openOrders', '미출고 주문', openOrdersCell),
      shipmentAreaCount(
        'shipments',
        '배송',
        shipCell,
        shipTodayCell,
        shipWeekCell,
        shipMonthCell,
      ),
    ];

    const alertStatus: WmsAlertStatusCount[] = [
      {
        key: 'unacknowledged',
        label: '미확인',
        count: unackedCell.value?.data.page.totalElements ?? null,
        cellStatus: unackedCell.status,
      },
      {
        key: 'acknowledged',
        label: '확인',
        count: ackedCell.value?.data.page.totalElements ?? null,
        cellStatus: ackedCell.status,
      },
    ];

    return {
      notEligible: false,
      counts,
      alertStatus,
      recentShipments:
        recentCell.value?.data.content.slice(0, RECENT_SIZE) ?? null,
      recentShipmentsStatus: recentCell.status,
      recentAdjustments:
        recentAdjCell.value?.data.content.slice(0, RECENT_SIZE) ?? null,
      recentAdjustmentsStatus: recentAdjCell.status,
    };
  } catch (err) {
    // Only a `401` re-thrown by a cell reaches here → whole-session re-login.
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login');
    }
    throw err;
  }
}

/** A page-count cell — the shape every count leg resolves to. */
type PageCountCell = Cell<{ data: { page: { totalElements: number } } }>;

/** `totalElements` of a resolved count cell, or `null` when it did not resolve. */
function totalElements(c: PageCountCell): number | null {
  return c.status === 'ok' && c.value !== null
    ? c.value.data.page.totalElements
    : null;
}

/**
 * Map a page-count cell to a point-in-time LEVEL area tile (no period, e.g. 재고).
 * An optional `lowStockCell` supplies the 저재고 attention sub-count (PC-FE-177):
 * its `totalElements`, or `null` when that sub-read did not resolve — the tile's
 * own status still follows the primary `c` read.
 */
function areaCount(
  key: string,
  label: string,
  c: PageCountCell,
  lowStockCell?: PageCountCell,
): WmsAreaCount {
  const count = totalElements(c);
  const lowStock =
    lowStockCell !== undefined ? totalElements(lowStockCell) : undefined;
  if (c.status === 'ok' && count !== null) {
    return { key, label, count, status: 'ok', period: null, lowStock };
  }
  return { key, label, count: null, status: c.status, period: null, lowStock };
}

/**
 * Map the four 배송 count reads (total + today/week/month windows) to a FLOW
 * area tile. The tile status follows the TOTAL read; each period bucket is the
 * `totalElements` of its windowed read, or `null` if that sub-read degraded
 * (rendered "—" — the tile does not collapse for a single failed window).
 */
function shipmentAreaCount(
  key: string,
  label: string,
  total: PageCountCell,
  today: PageCountCell,
  week: PageCountCell,
  month: PageCountCell,
): WmsAreaCount {
  const count = totalElements(total);
  if (total.status === 'ok' && count !== null) {
    return {
      key,
      label,
      count,
      status: 'ok',
      period: {
        today: totalElements(today),
        week: totalElements(week),
        month: totalElements(month),
      },
    };
  }
  return { key, label, count: null, status: total.status, period: null };
}
