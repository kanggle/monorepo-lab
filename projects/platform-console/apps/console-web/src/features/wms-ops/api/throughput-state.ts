import { redirect } from 'next/navigation';
import { ApiError } from '@/shared/api/errors';
import { listRefs } from './wms-refs-api';
import { getThroughput } from './wms-inventory-api';
import type { CellStatus } from './overview-state';
import {
  activeWarehouseOptions,
  sumThroughput,
  kstThroughputWindow,
  zeroFillThroughput,
  type ThroughputCardStatus,
  type WarehouseOption,
  type WmsThroughputState,
} from '../lib/throughput';

/**
 * Server-side state for the `/wms` 개요 **daily throughput card**
 * (TASK-PC-FE-358, console-integration-contract § 2.4.5.2 «Daily throughput
 * card»). Runs in PARALLEL with — and independent of — `getWmsOverviewState`:
 * its failure never degrades the count tiles / alert distribution / recent
 * glances, and theirs never degrades it (the page awaits both in one
 * `Promise.all`, and this function never throws except for the redirect).
 *
 * Two reads, in order:
 *   1. op #11 `GET /dashboard/refs/warehouses?status=ACTIVE&size=100` →
 *      `activeWarehouseOptions` (ACTIVE, code ascending). 0 → `no-warehouses`
 *      and NO throughput read; ≥1 → the first is the default.
 *   2. op #3 `GET /dashboard/throughput` for the default warehouse over the
 *      14-day window ending at the **KST** date of `now` (see `lib/throughput`
 *      for why KST), zero-filled from..to.
 *
 * Resilience — same rule as the overview `cell()`: 403 → `forbidden`;
 * 503/timeout/network/parse/other → `degraded`; a `401` from EITHER read →
 * whole-session `redirect('/login?error=session_expired')` (no partial authed
 * state). When the warehouse list resolved but the throughput read failed, the
 * list is still returned, so the selector stays usable (a switch re-queries
 * through the same-origin proxy).
 */

export type { ThroughputCardStatus, WmsThroughputState };

const WAREHOUSE_LIST_SIZE = 100;

function statusOf(err: unknown): CellStatus {
  if (err instanceof ApiError && err.status === 403) return 'forbidden';
  return 'degraded';
}

function isUnauthorized(err: unknown): boolean {
  return err instanceof ApiError && err.status === 401;
}

/**
 * @param eligible whether the operator is wms-eligible (resolved by the page
 *   from the data-driven registry). `false` ⇒ no reads (§ 2.4.5 — never
 *   fabricate a cross-tenant wms call).
 * @param now the reference instant for the KST window (injectable for tests).
 */
export async function getWmsThroughputState(
  eligible: boolean,
  now: Date = new Date(),
): Promise<WmsThroughputState> {
  const { from, to } = kstThroughputWindow(now);
  const base: WmsThroughputState = {
    notEligible: false,
    status: 'degraded',
    warehouses: [],
    selectedWarehouseId: null,
    from,
    to,
    days: null,
    totals: null,
  };
  if (!eligible) {
    return { ...base, notEligible: true };
  }

  let warehouses: WarehouseOption[];
  try {
    const refs = await listRefs('warehouses', {
      status: 'ACTIVE',
      page: 0,
      size: WAREHOUSE_LIST_SIZE,
    });
    warehouses = activeWarehouseOptions(refs.data.content);
  } catch (err) {
    if (isUnauthorized(err)) redirect('/login?error=session_expired');
    return { ...base, status: statusOf(err) };
  }

  if (warehouses.length === 0) {
    return { ...base, status: 'no-warehouses' };
  }

  const selected = warehouses[0];
  try {
    const res = await getThroughput({ warehouseId: selected.id, from, to });
    const days = zeroFillThroughput(res.data, from, to);
    return {
      ...base,
      status: 'ok',
      warehouses,
      selectedWarehouseId: selected.id,
      days,
      totals: sumThroughput(days),
    };
  } catch (err) {
    if (isUnauthorized(err)) redirect('/login?error=session_expired');
    return {
      ...base,
      status: statusOf(err),
      warehouses,
      selectedWarehouseId: selected.id,
    };
  }
}
