import { redirect } from 'next/navigation';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
import { listShippings } from './shippings-api';
import type { ShippingList, ShippingListParams } from './shipping-types';

/**
 * Server-side ecommerce shipping operations section state for the
 * `(console)/ecommerce/shippings` routes (TASK-PC-FE-088 — ADR-031 Phase 4b).
 * Mirrors `promotions-state.ts` exactly.
 *
 * Eligibility gate: the page resolves the operator's ecommerce eligibility
 * from the data-driven registry and passes it here. If not eligible the
 * section blocks with an actionable state and NO ecommerce call is made.
 *
 * Resilience boundary (§ 2.4.10 / § 2.5):
 *   - `401` → `redirect('/login')` (whole-session re-login, NOT a per-section degrade).
 *   - `403` → non-crashing inline "not available to your role" state.
 *   - `503` / timeout / network → DEGRADED (only the ecommerce section degrades).
 *   - any other producer error → degrade rather than crash.
 */
export interface ShippingsSectionState {
  /** Server-seeded page-0 shipping list (the table's initialData). */
  shippings: ShippingList | null;
  /** True when the operator is not ecommerce-eligible — actionable block. */
  notEligible: boolean;
  /** True on a role-insufficient (403) producer response — inline. */
  forbidden: boolean;
  /** True on 503 / timeout / network — section degrades only. */
  degraded: boolean;
}

const EMPTY: ShippingsSectionState = {
  shippings: null,
  notEligible: false,
  forbidden: false,
  degraded: false,
};

/**
 * @param eligible whether the operator is ecommerce-eligible, resolved by the
 *   page from the data-driven registry. `false` ⇒ block (no ecommerce call).
 * @param params optional list filters (status / page / size).
 */
export async function getShippingsSectionState(
  eligible: boolean,
  params: ShippingListParams = {},
): Promise<ShippingsSectionState> {
  if (!eligible) {
    return { ...EMPTY, notEligible: true };
  }
  try {
    const shippings = await listShippings({ page: 0, size: 20, ...params });
    return { ...EMPTY, shippings };
  } catch (err) {
    return mapSectionError(err);
  }
}

/** Shared resilience mapping for the list server read. */
function mapSectionError(err: unknown): ShippingsSectionState {
  if (err instanceof ApiError && err.status === 401) {
    // No partial authed state → clean WHOLE-SESSION re-login.
    redirect('/login');
  }
  if (err instanceof ApiError && err.status === 403) {
    return { ...EMPTY, forbidden: true };
  }
  if (err instanceof EcommerceUnavailableError) {
    return { ...EMPTY, degraded: true };
  }
  // Any other producer error on a seeded read → degrade, not crash.
  return { ...EMPTY, degraded: true };
}
