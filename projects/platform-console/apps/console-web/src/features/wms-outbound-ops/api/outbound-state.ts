import { redirect } from 'next/navigation';
import { ApiError, WmsOutboundUnavailableError } from '@/shared/api/errors';
import { listOrders } from './outbound-api';
import type { OutboundOrderPage, OutboundStatusCount } from './types';
import {
  OUTBOUND_FLOW,
  OUTBOUND_SIDE_BRANCHES,
} from '../components/outbound-ops-helpers';

/**
 * Server-side wms outbound operations section state for the
 * `(console)/wms/outbound` route (TASK-PC-FE-057 — the second wms surface).
 *
 * Eligibility gate (console-integration-contract § 2.4.5 tenant-model
 * divergence, inherited by § 2.4.5.1): wms resolves the operator's tenant
 * from the JWT `tenant_id=wms` claim producer-side — the console does NOT
 * send a tenant. To avoid fabricating a cross-tenant call, the
 * `(console)/wms/outbound` PAGE (app layer — the layer allowed to compose
 * `features/*`) first resolves the operator's wms eligibility from the
 * data-driven registry (§ 2.2, `getCatalog()`, `productKey=wms`) and passes
 * it in here. If not eligible the section blocks with an actionable "no
 * wms-scoped access" state and NO wms call is ever made. wms still rejects
 * cross-tenant producer-side regardless (never weakened here).
 *
 * (The eligibility check lives in the page, not this api module, so this
 * feature never imports another feature — architecture.md § Allowed
 * Dependencies / § Boundary Rules.)
 *
 * Resilience boundary (§ 2.4.5.1 / § 2.5, mirrors `wms-state.ts`):
 *   - `401` (IAM OIDC session expired) → `redirect('/login')` — a
 *     WHOLE-SESSION re-login, NOT a per-section degrade.
 *   - `403` (role-insufficient) → a non-crashing inline "not available to
 *     your role" state.
 *   - `503` / timeout / network → DEGRADED — ONLY the wms outbound section
 *     renders a degraded notice; the console shell stays intact.
 *   - any other producer error → degrade rather than crash.
 */
export interface OutboundSectionState {
  /** Server-seeded page-0 order list (the table's initialData). */
  orders: OutboundOrderPage | null;
  /** True when the operator is not wms-eligible — actionable block, no call. */
  notEligible: boolean;
  /** True on a role-insufficient (403) producer response — inline. */
  forbidden: boolean;
  /** True on 503 / timeout / network — section degrades only. */
  degraded: boolean;
  /** Per-status order totals for the pipeline bar (TASK-PC-FE-362) — one
   *  independent cell per `OUTBOUND_COUNT_STATUSES` entry, in that order. */
  statusCounts: OutboundStatusCount[];
}

/** Flow (contract order) then side branches — 7 reads. */
const OUTBOUND_COUNT_STATUSES: readonly string[] = [
  ...OUTBOUND_FLOW,
  ...OUTBOUND_SIDE_BRANCHES,
];

/**
 * One status total as its own cell. `401` is re-thrown (whole-session
 * re-login by the caller's catch); `403` → `forbidden`; anything else →
 * `degraded`. Never fails the page — a bad count only hides the bar.
 */
async function countCell(status: string): Promise<OutboundStatusCount> {
  try {
    const page = await listOrders({ status, page: 0, size: 1 });
    return { status, count: page.page.totalElements, cellStatus: 'ok' };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) throw err;
    if (err instanceof ApiError && err.status === 403) {
      return { status, count: null, cellStatus: 'forbidden' };
    }
    return { status, count: null, cellStatus: 'degraded' };
  }
}

const EMPTY: OutboundSectionState = {
  statusCounts: [],
  orders: null,
  notEligible: false,
  forbidden: false,
  degraded: false,
};

/**
 * @param eligible whether the operator is wms-eligible, resolved by the
 *   page from the data-driven registry. `false` ⇒ block (no wms call).
 */
export async function getOutboundSectionState(
  eligible: boolean,
): Promise<OutboundSectionState> {
  if (!eligible) {
    // Not wms-eligible — never fabricate a cross-tenant call.
    return { ...EMPTY, notEligible: true };
  }

  try {
    // TASK-PC-FE-362 — the page-0 list plus one `size=1` total per status
    // (5 flow + 2 side branches), all counted ONCE here at SSR time with the
    // same credential / role / tenant scope. Each count is its own cell.
    const [orders, ...countCells] = await Promise.all([
      listOrders({ page: 0, size: 20 }),
      ...OUTBOUND_COUNT_STATUSES.map((s) => countCell(s)),
    ]);
    return { ...EMPTY, orders, statusCounts: countCells };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      // No partial authed state → clean WHOLE-SESSION re-login.
      redirect('/login?error=session_expired');
    }
    if (err instanceof ApiError && err.status === 403) {
      // Role-insufficient → inline "not available to your role".
      return { ...EMPTY, forbidden: true };
    }
    if (err instanceof WmsOutboundUnavailableError) {
      // Degrade ONLY this section — shell + other sections intact (§ 2.5).
      return { ...EMPTY, degraded: true };
    }
    // Any other producer error on a seeded page-0 read → degrade, not crash.
    return { ...EMPTY, degraded: true };
  }
}
