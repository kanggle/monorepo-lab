import { redirect } from 'next/navigation';
import { ApiError } from '@/shared/api/errors';
import { listInventory } from './wms-inventory-api';
import { listShipments } from './wms-shipments-api';
import { listAlerts } from './wms-alerts-api';
import type { ShipmentRow } from './types';

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

/** One operator-area count tile (label + total count + per-cell status). */
export interface WmsAreaCount {
  key: string;
  label: string;
  /** `totalElements` from the area's list read, or `null` when the cell did
   *  not resolve (degraded/forbidden). */
  count: number | null;
  status: CellStatus;
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
}

const EMPTY: WmsOverviewState = {
  notEligible: false,
  counts: [],
  alertStatus: [],
  recentShipments: null,
  recentShipmentsStatus: 'degraded',
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

  try {
    const [invCell, shipCell, alertCell, unackedCell, ackedCell, recentCell] =
      await Promise.all([
        cell(listInventory({ page: 0, size: 1 })),
        cell(listShipments({ page: 0, size: 1 })),
        cell(listAlerts({ page: 0, size: 1 })),
        cell(listAlerts({ acknowledged: false, page: 0, size: 1 })),
        cell(listAlerts({ acknowledged: true, page: 0, size: 1 })),
        cell(listShipments({ page: 0, size: RECENT_SIZE })),
      ]);

    const counts: WmsAreaCount[] = [
      areaCount('inventory', '재고', invCell),
      areaCount('shipments', '배송', shipCell),
      areaCount('alerts', '알림', alertCell),
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
    };
  } catch (err) {
    // Only a `401` re-thrown by a cell reaches here → whole-session re-login.
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login');
    }
    throw err;
  }
}

/** Map a page-count cell (`WmsResult<{page:{totalElements}}>`) to an area tile. */
function areaCount(
  key: string,
  label: string,
  c: Cell<{ data: { page: { totalElements: number } } }>,
): WmsAreaCount {
  if (c.status === 'ok' && c.value !== null) {
    return { key, label, count: c.value.data.page.totalElements, status: 'ok' };
  }
  return { key, label, count: null, status: c.status };
}
