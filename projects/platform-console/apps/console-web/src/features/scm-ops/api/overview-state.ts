import { redirect } from 'next/navigation';
import { ApiError } from '@/shared/api/errors';
import { listPurchaseOrders } from './scm-procurement-api';
import { getSnapshot } from './scm-inventory-visibility-api';
import type { PurchaseOrder } from './types';

/**
 * Server-side scm **operator overview snapshot** fan-out for the `/scm`
 * landing (TASK-PC-FE-167 — follows the PC-FE-166 wms reference of the console
 * domain-landing overview series; PC-FE-168 shared read-leg decision).
 *
 * ── ARCHITECTURE (console-web DIRECT fan-out; PC-FE-168) ──
 * Same console-web DIRECT model as ecommerce (§ 2.4.10.6) / wms (§ 2.4.5.2):
 * scm already reaches its gateway server-side via `getDomainFacingToken()`
 * (§ 2.4.6 direct client), so this reuses the feature's own `listPurchaseOrders`
 * / `getSnapshot` reads and derives counts from `totalElements` (`?page=0&size=1`).
 * NO console-bff leg, NO producer `/summary`, NO producer retrofit
 * (ADR-MONO-017 D3.B).
 *
 * ── S5 (§ 2.4.6, NORMATIVE) ──
 * The 재고 스냅샷 count comes from the inventory-visibility surface, which
 * carries the REQUIRED `meta.warning` ("Not for procurement decisions (S5)").
 * That warning is surfaced on the state (`s5Warning`) so the presentation can
 * render it PROMINENTLY (never stripped). The PO surface has no such warning
 * (PO is the authoritative procurement record).
 *
 * ── RESILIENCE (§ 2.5) ──
 * Bounded + parallel fan-out. Each cell CATCHES its own error into a cell
 * status (ok / forbidden / degraded) EXCEPT `401`, which it re-throws so the
 * top-level catch performs a whole-session `redirect('/login')`.
 * `ScmUnavailableError` / `ScmRateLimitedError` (not `ApiError`) degrade the
 * cell, never a re-login. No auto-refetch.
 */

export type CellStatus = 'ok' | 'forbidden' | 'degraded';

/** One operator-area count tile (label + total count + per-cell status). */
export interface ScmAreaCount {
  key: string;
  label: string;
  count: number | null;
  status: CellStatus;
}

/** One PO-status distribution bucket. */
export interface ScmPoStatusCount {
  status: string;
  count: number | null;
  cellStatus: CellStatus;
}

export interface ScmOverviewState {
  /** True when the operator is not scm-eligible — no fan-out was run. */
  notEligible: boolean;
  counts: ScmAreaCount[];
  poStatus: ScmPoStatusCount[];
  recentPos: PurchaseOrder[] | null;
  recentPosStatus: CellStatus;
  /** S5 visibility warning — surfaced when the 재고 스냅샷 cell resolved
   *  (inventory-visibility obligation, § 2.4.6); `null` otherwise. */
  s5Warning: string | null;
}

/** PO lifecycle statuses for the distribution (mirrors `KNOWN_PO_STATUSES`;
 *  kept local so the api layer does not import a components/ helper). Tolerant
 *  — an unknown/future producer status is simply not a bucket here (no throw). */
const PO_STATUS_BUCKETS = [
  'DRAFT',
  'SUBMITTED',
  'ACKNOWLEDGED',
  'CONFIRMED',
  'PARTIALLY_RECEIVED',
  'RECEIVED',
  'SETTLED',
  'CLOSED',
  'CANCELED',
] as const;

/** Recent-activity page size (clamped to [1, max] by the list client). */
const RECENT_SIZE = 5;

const EMPTY: ScmOverviewState = {
  notEligible: false,
  counts: [],
  poStatus: [],
  recentPos: null,
  recentPosStatus: 'degraded',
  s5Warning: null,
};

interface Cell<T> {
  value: T | null;
  status: CellStatus;
}

/**
 * Resolve a single fan-out leg into a cell: success → `ok`; `403` → `forbidden`;
 * everything else (`ScmUnavailableError` / `ScmRateLimitedError` / timeout /
 * other) → `degraded`. A `401` is RE-THROWN so the caller performs a
 * whole-session `redirect('/login')`.
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
 * @param eligible whether the operator is scm-eligible (resolved by the page
 *   from the data-driven registry). `false` ⇒ no fan-out, no calls (never
 *   fabricate a cross-tenant scm call — § 2.4.6).
 */
export async function getScmOverviewState(
  eligible: boolean,
): Promise<ScmOverviewState> {
  if (!eligible) {
    return { ...EMPTY, notEligible: true };
  }

  try {
    const [poCell, snapCell, recentCell, statusCells] = await Promise.all([
      cell(listPurchaseOrders({ page: 0, size: 1 })),
      cell(getSnapshot({ page: 0, size: 1 })),
      cell(listPurchaseOrders({ page: 0, size: RECENT_SIZE })),
      Promise.all(
        PO_STATUS_BUCKETS.map((s) =>
          cell(listPurchaseOrders({ status: s, page: 0, size: 1 })),
        ),
      ),
    ]);

    // 재고 스냅샷 count + the REQUIRED S5 warning (surfaced when the cell ok).
    let snapCount: number | null = null;
    let s5Warning: string | null = null;
    if (snapCell.status === 'ok' && snapCell.value) {
      const resp = snapCell.value.data; // SnapshotResponse
      snapCount = Array.isArray(resp.data)
        ? resp.data.length
        : resp.data.totalElements;
      s5Warning = resp.meta.warning;
    }

    const counts: ScmAreaCount[] = [
      {
        key: 'po',
        label: '발주',
        count: poCell.value?.data.totalElements ?? null,
        status: poCell.status,
      },
      {
        key: 'inventory',
        label: '재고 스냅샷',
        count: snapCount,
        status: snapCell.status,
      },
    ];

    const poStatus: ScmPoStatusCount[] = PO_STATUS_BUCKETS.map((s, i) => ({
      status: s,
      count: statusCells[i].value?.data.totalElements ?? null,
      cellStatus: statusCells[i].status,
    }));

    return {
      notEligible: false,
      counts,
      poStatus,
      recentPos: recentCell.value?.data.content.slice(0, RECENT_SIZE) ?? null,
      recentPosStatus: recentCell.status,
      s5Warning,
    };
  } catch (err) {
    // Only a `401` re-thrown by a cell reaches here → whole-session re-login.
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login');
    }
    throw err;
  }
}
