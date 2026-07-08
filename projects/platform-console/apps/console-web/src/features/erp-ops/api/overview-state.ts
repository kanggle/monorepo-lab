import { redirect } from 'next/navigation';
import { ApiError } from '@/shared/api/errors';
import {
  listDepartments,
  listEmployees,
  listJobGrades,
  listCostCenters,
  listBusinessPartners,
} from './erp-api';
import { listApprovalInbox } from './approval-api';
import { listDelegationFacts } from './erp-delegation-facts-api';
import type { ErpListQueryParams } from './types';

/**
 * Server-side erp domain **overview snapshot** fan-out for the standalone
 * `/erp` overview landing (TASK-PC-FE-232 — PROMOTES + EXPANDS the former
 * masters-embedded TASK-PC-FE-161 `getErpMastersOverviewState` into an
 * independent domain-root `개요`, orthodox parity with every other domain's
 * 개요-at-root convention; Finance TASK-PC-FE-229 is the direct pattern
 * twin). The former masters slice moves to `/erp/masters` and no longer
 * embeds this fan-out — see `app/(console)/erp/masters/page.tsx`.
 *
 * ── ARCHITECTURE (console-web DIRECT fan-out; PC-FE-168) ──
 * Same console-web DIRECT model as ecommerce (§ 2.4.10.6) / wms (§ 2.4.5.2):
 * erp already reaches its 3 backend services server-side via
 * `getDomainFacingToken()` (§ 2.4.8 direct client). This reuses the
 * feature's own EXISTING `list*` reads and derives each count from
 * `meta.totalElements` (`?page=0&size=1`). NO console-bff leg, NO producer
 * `/summary`, NO producer retrofit (ADR-MONO-017 D3.B).
 *
 * erp remains the THINNEST of the 4 bff-domains: 7 counts only — no status
 * distribution and no "recent" feed (masters are effective-dated, not an
 * activity stream; approval/delegation counts are simple totals, not a
 * timeline). Adds TWO counts beyond the promoted masterdata 5:
 *   - **결재 대기** — the CALLER's pending approval inbox
 *     (`listApprovalInbox` — already the caller's own SUBMITTED queue, no
 *     new endpoint) `meta.totalElements`.
 *   - **활성 위임** — ACTIVE delegation-fact grants
 *     (`listDelegationFacts({ status: 'ACTIVE' })`, read-model, org-scope
 *     aware) `meta.totalElements`.
 * Both reuse EXISTING erp reads already bound by § 2.4.8 — no new producer
 * endpoint (ADR-MONO-017 D3.B).
 *
 * E3 (§ 2.4.8): an optional `asOf` threads through every MASTERDATA count
 * leg verbatim so the counts reflect the state-at-that-instant (matching
 * `getErpMastersState`). `active` is deliberately omitted so retired
 * masters are counted too (E2 honesty — the count is the true master
 * total, not just active rows). The approval / delegation legs are
 * NOT asOf-driven — they are current-time counts (a pending-approval
 * inbox / an active-delegation grant have no historical "state as of a
 * past date" concept in the console's read surface).
 *
 * ── RESILIENCE (§ 2.5) — per-cell independent degrade (the decisive rule,
 * AC-2) ──
 * Bounded + parallel. Each cell CATCHES its own error into a cell status
 * (ok / forbidden / degraded) EXCEPT `401`, which it re-throws so the
 * top-level catch performs a whole-session `redirect('/login')`. A `503`
 * / timeout / network error in ANY ONE leg (e.g. approval-service down)
 * degrades ONLY that tile — it NEVER blanks the sibling tiles or the whole
 * overview (Edge Cases / Failure Scenarios). No auto-refetch.
 */

export type CellStatus = 'ok' | 'forbidden' | 'degraded';

/** One overview count tile (label + total count + per-cell status). */
export interface ErpAreaCount {
  key: string;
  label: string;
  count: number | null;
  status: CellStatus;
}

export interface ErpOverviewState {
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

/** List count = `meta.totalElements` (producer count) or, absent that, the
 *  returned page length (defensive — never a throw). Generic over the
 *  masterdata / approval / read-model list envelope shapes — all three
 *  share the same `{ data, meta: { totalElements? } }` wire shape. */
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
 * @param asOf optional E3 point-in-time read — threaded through every
 *   MASTERDATA count leg verbatim (matching `getErpMastersState`). Omitted
 *   ⇒ producer resolves "today". Does NOT thread into the approval /
 *   delegation legs (current-time counts, no asOf concept).
 */
export async function getErpOverviewState(
  eligible: boolean,
  asOf?: string | null,
): Promise<ErpOverviewState> {
  if (!eligible) {
    return { notEligible: true, counts: [] };
  }

  const masterParams: ErpListQueryParams = { page: 0, size: 1 };
  if (asOf) masterParams.asOf = asOf;

  try {
    const [dep, emp, jg, cc, bp, approval, delegation] = await Promise.all([
      cell(listDepartments(masterParams)),
      cell(listEmployees(masterParams)),
      cell(listJobGrades(masterParams)),
      cell(listCostCenters(masterParams)),
      cell(listBusinessPartners(masterParams)),
      cell(listApprovalInbox({ page: 0, size: 1 })),
      cell(listDelegationFacts({ status: 'ACTIVE', page: 0, size: 1 })),
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
      {
        key: 'pendingApprovals',
        label: '결재 대기',
        count: countOf(approval),
        status: approval.status,
      },
      {
        key: 'activeDelegations',
        label: '활성 위임',
        count: countOf(delegation),
        status: delegation.status,
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
