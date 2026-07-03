import { redirect } from 'next/navigation';
import { ApiError, WmsUnavailableError } from '@/shared/api/errors';
import { listInventory } from './wms-api';
import type { InventoryPage } from './types';

/**
 * Server-side wms **inventory** section state for the dedicated
 * `(console)/wms/inventory` route (TASK-PC-FE-173 — the inventory query
 * table split off the `/wms` 개요, mirroring `wms-state.ts` / the
 * `wms-outbound-ops/api/outbound-state.ts` single-read shape).
 *
 * Eligibility gate (console-integration-contract § 2.4.5 tenant-model
 * divergence): wms resolves the operator's tenant from the JWT `tenant_id`
 * claim producer-side — the console does NOT send a tenant. To avoid
 * fabricating a cross-tenant call, the `(console)/wms/inventory` PAGE (app
 * layer — the layer allowed to compose `features/*`) first resolves the
 * operator's wms eligibility from the data-driven registry (§ 2.2,
 * `getCatalog()`, `productKey=wms`) and passes it in here. If not eligible
 * the section blocks with an actionable "no wms-scoped access" state and NO
 * wms call is ever made. wms still rejects cross-tenant producer-side
 * regardless (never weakened here).
 *
 * (The eligibility check lives in the page, not this api module, so this
 * feature never imports another feature — architecture.md § Allowed
 * Dependencies / § Boundary Rules.)
 *
 * Resilience boundary (§ 2.4.5 / § 2.5, mirrors `wms-state.ts`):
 *   - `401` (IAM OIDC session expired) → `redirect('/login')` — a
 *     WHOLE-SESSION re-login, NOT a per-section degrade.
 *   - `403` (role-insufficient) → a non-crashing inline "not available to
 *     your role" state.
 *   - `503` / timeout / network → DEGRADED — ONLY this section renders a
 *     degraded notice; the console shell stays intact.
 *   - any other producer error → degrade rather than crash.
 */
export interface WmsInventorySectionState {
  /** Server-seeded page-0 inventory snapshot (the table's initialData). */
  inventory: InventoryPage | null;
  /** True when the operator is not wms-eligible — actionable block, no call. */
  notEligible: boolean;
  /** True on a role-insufficient (403) producer response — inline. */
  forbidden: boolean;
  /** True on 503 / timeout / network — section degrades only. */
  degraded: boolean;
  /** NON-blocking eventual-consistency hint (seconds), or null. */
  lagSeconds: number | null;
}

const EMPTY: WmsInventorySectionState = {
  inventory: null,
  notEligible: false,
  forbidden: false,
  degraded: false,
  lagSeconds: null,
};

/**
 * @param eligible whether the operator is wms-eligible, resolved by the
 *   page from the data-driven registry. `false` ⇒ block (no wms call).
 */
export async function getWmsInventoryState(
  eligible: boolean,
): Promise<WmsInventorySectionState> {
  if (!eligible) {
    // Not wms-eligible — never fabricate a cross-tenant call.
    return { ...EMPTY, notEligible: true };
  }

  try {
    const inv = await listInventory({ page: 0, size: 20 });
    return {
      ...EMPTY,
      inventory: inv.data,
      lagSeconds: inv.lagSeconds && inv.lagSeconds > 0 ? inv.lagSeconds : null,
    };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      // No partial authed state → clean WHOLE-SESSION re-login.
      redirect('/login');
    }
    if (err instanceof ApiError && err.status === 403) {
      // Role-insufficient → inline "not available to your role".
      return { ...EMPTY, forbidden: true };
    }
    if (err instanceof WmsUnavailableError) {
      // Degrade ONLY this section — shell + other sections intact (§ 2.5).
      return { ...EMPTY, degraded: true };
    }
    // Any other producer error on a seeded page-0 read → degrade, not crash.
    return { ...EMPTY, degraded: true };
  }
}
