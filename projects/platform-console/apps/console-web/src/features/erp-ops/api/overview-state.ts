import { redirect } from 'next/navigation';
import { ApiError } from '@/shared/api/errors';
import {
  listDepartments,
  listEmployees,
  listJobGrades,
  listCostCenters,
  listBusinessPartners,
} from './erp-api';
import type { ErpListQueryParams } from './types';

/**
 * Server-side erp masters **operator overview snapshot** fan-out for the `/erp`
 * masters landing (TASK-PC-FE-161 — follows the PC-FE-166 wms reference of the
 * console domain-landing overview series; PC-FE-168 shared read-leg decision).
 *
 * ── ARCHITECTURE (console-web DIRECT fan-out; PC-FE-168) ──
 * Same console-web DIRECT model as ecommerce (§ 2.4.10.6) / wms (§ 2.4.5.2):
 * erp already reaches its `masterdata-service` server-side via
 * `getDomainFacingToken()` (§ 2.4.8 direct client). This reuses the feature's
 * own `list*` reads and derives each master count from `meta.totalElements`
 * (`?page=0&size=1`). NO console-bff leg, NO producer `/summary`, NO producer
 * retrofit (ADR-MONO-017 D3.B).
 *
 * erp is the THINNEST of the 4 bff-domains: masterdata counts only — no status
 * distribution (a master `status` filter is not a first-class list param) and
 * no "recent" feed (masters are effective-dated, not an activity stream). The
 * overview lives on the `/erp` **masters** route (`getErpMastersState`).
 *
 * E3 (§ 2.4.8): an optional `asOf` threads through every count leg verbatim so
 * the counts reflect the state-at-that-instant (matching `getErpMastersState`).
 * `active` is deliberately omitted so retired masters are counted too (E2
 * honesty — the count is the true master total, not just active rows).
 *
 * ── RESILIENCE (§ 2.5) ──
 * Bounded + parallel. Each cell CATCHES its own error into a cell status
 * (ok / forbidden / degraded) EXCEPT `401`, which it re-throws so the top-level
 * catch performs a whole-session `redirect('/login')`. `ErpUnavailableError`
 * (503/timeout/network, not an `ApiError`) degrades the cell. No auto-refetch.
 */

export type CellStatus = 'ok' | 'forbidden' | 'degraded';

/** One master-area count tile (label + total count + per-cell status). */
export interface ErpAreaCount {
  key: string;
  label: string;
  count: number | null;
  status: CellStatus;
}

export interface ErpMastersOverviewState {
  /** True when the operator is not erp-eligible — no fan-out was run. */
  notEligible: boolean;
  counts: ErpAreaCount[];
}

interface Cell<T> {
  value: T | null;
  status: CellStatus;
}

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

/** Master list count = `meta.totalElements` (producer count) or, absent that,
 *  the returned page length (defensive — never a throw). */
function countOf(
  c: Cell<{ meta: { totalElements?: number }; data: unknown[] }>,
): number | null {
  if (c.status === 'ok' && c.value) {
    return c.value.meta.totalElements ?? c.value.data.length;
  }
  return null;
}

/**
 * @param eligible whether the operator is erp-eligible (resolved by the page
 *   from the data-driven registry). `false` ⇒ no fan-out, no calls.
 * @param asOf optional E3 point-in-time read — threaded through every count leg
 *   verbatim (matching `getErpMastersState`). Omitted ⇒ producer resolves "today".
 */
export async function getErpMastersOverviewState(
  eligible: boolean,
  asOf?: string | null,
): Promise<ErpMastersOverviewState> {
  if (!eligible) {
    return { notEligible: true, counts: [] };
  }

  const params: ErpListQueryParams = { page: 0, size: 1 };
  if (asOf) params.asOf = asOf;

  try {
    const [dep, emp, jg, cc, bp] = await Promise.all([
      cell(listDepartments(params)),
      cell(listEmployees(params)),
      cell(listJobGrades(params)),
      cell(listCostCenters(params)),
      cell(listBusinessPartners(params)),
    ]);

    const counts: ErpAreaCount[] = [
      { key: 'departments', label: '부서', count: countOf(dep), status: dep.status },
      { key: 'employees', label: '직원', count: countOf(emp), status: emp.status },
      { key: 'jobGrades', label: '직급', count: countOf(jg), status: jg.status },
      { key: 'costCenters', label: '원가센터', count: countOf(cc), status: cc.status },
      {
        key: 'businessPartners',
        label: '거래처',
        count: countOf(bp),
        status: bp.status,
      },
    ];

    return { notEligible: false, counts };
  } catch (err) {
    // Only a `401` re-thrown by a cell reaches here → whole-session re-login.
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login');
    }
    throw err;
  }
}
