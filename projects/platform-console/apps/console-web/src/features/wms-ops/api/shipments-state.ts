import { redirect } from 'next/navigation';
import { ApiError, WmsUnavailableError } from '@/shared/api/errors';
import { listShipments } from './wms-api';
import type { ShipmentPage } from './types';

/**
 * Server-side wms **택배/출고 (shipments)** section state for the
 * `(console)/wms/outbound` route (TASK-PC-FE-175 — the shipments read table
 * moved OFF the `/wms` 개요 onto the existing 출고 page, mirroring the
 * `inventory-state.ts` / `wms-outbound-ops/api/outbound-state.ts` single-read
 * shape). The read model is projected from `outbound.shipping.confirmed`
 * (admin-service § 9) — the read-side companion to the outbound-order
 * operations (`OutboundOpsScreen`) rendered above it.
 *
 * Eligibility gate (console-integration-contract § 2.4.5 tenant-model
 * divergence): wms resolves the operator's tenant from the JWT `tenant_id`
 * claim producer-side — the console does NOT send a tenant. To avoid
 * fabricating a cross-tenant call, the `(console)/wms/outbound` PAGE (app
 * layer — the layer allowed to compose `features/*`) first resolves the
 * operator's wms eligibility from the data-driven registry (§ 2.2,
 * `getCatalog()`, `productKey=wms`) and passes it in here. If not eligible the
 * section blocks (no wms call). wms still rejects cross-tenant producer-side
 * regardless (never weakened here).
 *
 * (The eligibility check lives in the page, not this api module, so this
 * feature never imports another feature — architecture.md § Allowed
 * Dependencies / § Boundary Rules.)
 *
 * Resilience boundary (§ 2.4.5 / § 2.5, mirrors `wms-state.ts`):
 *   - `401` (IAM OIDC session expired) → `redirect('/login')` — a
 *     WHOLE-SESSION re-login, NOT a per-section degrade.
 *   - `403` (role-insufficient — shipments needs `WMS_VIEWER`, which is
 *     independent of the outbound-operation roles) → a non-crashing inline
 *     "not available to your role" state for THIS section only (the outbound
 *     operations above it stay intact).
 *   - `503` / timeout / network → DEGRADED — ONLY this section renders a
 *     degraded notice; the outbound screen + console shell stay intact.
 *   - any other producer error → degrade rather than crash.
 */
export interface WmsShipmentsSectionState {
  /** Server-seeded page-0 shipments read (the table's initialData). */
  shipments: ShipmentPage | null;
  /** True when the operator is not wms-eligible — block, no call. */
  notEligible: boolean;
  /** True on a role-insufficient (403) producer response — inline. */
  forbidden: boolean;
  /** True on 503 / timeout / network — this section degrades only. */
  degraded: boolean;
  /** NON-blocking eventual-consistency hint (seconds), or null. */
  lagSeconds: number | null;
}

const EMPTY: WmsShipmentsSectionState = {
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
export async function getWmsShipmentsState(
  eligible: boolean,
): Promise<WmsShipmentsSectionState> {
  if (!eligible) {
    // Not wms-eligible — never fabricate a cross-tenant call.
    return { ...EMPTY, notEligible: true };
  }

  try {
    const ship = await listShipments({ page: 0, size: 20 });
    return {
      ...EMPTY,
      shipments: ship.data,
      lagSeconds:
        ship.lagSeconds && ship.lagSeconds > 0 ? ship.lagSeconds : null,
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
      // Degrade ONLY this section — outbound screen + shell intact (§ 2.5).
      return { ...EMPTY, degraded: true };
    }
    // Any other producer error on a seeded page-0 read → degrade, not crash.
    return { ...EMPTY, degraded: true };
  }
}
