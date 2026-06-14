import { redirect } from 'next/navigation';
import { ApiError, WmsUnavailableError } from '@/shared/api/errors';
import { listInventory, listAlerts, listShipments } from './wms-api';
import type { InventoryPage, AlertPage, ShipmentPage } from './types';

/**
 * Server-side wms operations section state for the `(console)/wms` route
 * (TASK-PC-FE-007 — first non-IAM federation).
 *
 * Eligibility gate (console-integration-contract § 2.4.5 tenant-model
 * divergence): wms resolves the operator's tenant from the JWT `tenant_id`
 * claim producer-side — the console does NOT send a tenant. To avoid
 * fabricating a cross-tenant call, the `(console)/wms` PAGE (app layer —
 * the layer allowed to compose `features/*`) first resolves the operator's
 * wms eligibility from the data-driven registry (§ 2.2, `getCatalog()`) and
 * passes it in here. If not eligible the section blocks with an actionable
 * "no wms-scoped access" state and NO wms call is ever made. wms still
 * rejects cross-tenant producer-side regardless (never weakened here).
 *
 * (The eligibility check lives in the page, not this api module, so this
 * feature never imports another feature — architecture.md § Allowed
 * Dependencies / § Boundary Rules.)
 *
 * Resilience boundary (§ 2.4.5 / § 2.5, mirrors `accounts-state.ts`):
 *   - `401` (IAM OIDC session expired) → `redirect('/login')` — a
 *     WHOLE-SESSION re-login, NOT a per-section degrade (no partial authed
 *     state; consistent with the FE-002..005 401 discipline).
 *   - `403` (role-insufficient) → a non-crashing inline "not available to
 *     your role" state.
 *   - `503` / timeout / network → DEGRADED — ONLY the wms section renders
 *     a degraded notice; the console shell + the IAM sections stay intact.
 *   - any other producer error → degrade rather than crash.
 *
 * Read-model lag (§ 2.4.5): the inventory/alerts lag hint (if the producer
 * set `X-Read-Model-Lag-Seconds`) is surfaced as a NON-blocking banner —
 * the section still renders (eventual-consistency honesty).
 */
export interface WmsSectionState {
  inventory: InventoryPage | null;
  alerts: AlertPage | null;
  /** Shipment read-model rows (carrier code / tracking no), seeded page-0.
   *  Projected from `outbound.shipping.confirmed` (admin-service § 9). */
  shipments: ShipmentPage | null;
  /** True when the operator is not wms-eligible (no wms product/tenant in
   *  their registry) — actionable block, no wms call fabricated. */
  notEligible: boolean;
  /** True on a role-insufficient (403) producer response — inline. */
  forbidden: boolean;
  /** True on 503 / timeout / network — wms section degrades only. */
  degraded: boolean;
  /** Max observed read-model lag (seconds) across the seeded reads, when
   *  the producer surfaced it; null when not lagging / absent. */
  lagSeconds: number | null;
}

const EMPTY: WmsSectionState = {
  inventory: null,
  alerts: null,
  shipments: null,
  notEligible: false,
  forbidden: false,
  degraded: false,
  lagSeconds: null,
};

/**
 * @param eligible whether the operator is wms-eligible, resolved by the
 *   page from the data-driven registry. `false` ⇒ block (no wms call).
 */
export async function getWmsSectionState(
  eligible: boolean,
): Promise<WmsSectionState> {
  if (!eligible) {
    // Not wms-eligible — never fabricate a cross-tenant call.
    return { ...EMPTY, notEligible: true };
  }

  try {
    const [inv, alerts, shipments] = await Promise.all([
      listInventory({ page: 0, size: 20 }),
      listAlerts({ page: 0, size: 20 }),
      listShipments({ page: 0, size: 20 }),
    ]);
    const lagSeconds = Math.max(
      inv.lagSeconds ?? 0,
      alerts.lagSeconds ?? 0,
      shipments.lagSeconds ?? 0,
    );
    return {
      inventory: inv.data,
      alerts: alerts.data,
      shipments: shipments.data,
      notEligible: false,
      forbidden: false,
      degraded: false,
      lagSeconds: lagSeconds > 0 ? lagSeconds : null,
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
      // Degrade ONLY the wms section — shell + IAM sections intact (§ 2.5).
      return { ...EMPTY, degraded: true };
    }
    // Any other producer error (404/400/422/409 on a seeded page-0 read —
    // should not happen for these) → degrade rather than crash.
    return { ...EMPTY, degraded: true };
  }
}
