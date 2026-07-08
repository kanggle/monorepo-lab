import { redirect } from 'next/navigation';
import { ApiError, WmsUnavailableError } from '@/shared/api/errors';
import { listRefs } from './wms-refs-api';
import { DEFAULT_REF_TYPE, type RefPage, type RefType } from './types';

/**
 * Server-side wms **마스터**(master reference data) section state for the
 * dedicated `(console)/wms/master` route (TASK-PC-FE-223 — surfaces the
 * previously-uncoded-but-unused `GET /dashboard/refs/{type}` read; mirrors
 * `inbound-state.ts` / `inventory-state.ts` single-read shape).
 *
 * Only the default tab (`DEFAULT_REF_TYPE`, `locations`) is seeded
 * server-side — the other supported ref types (`warehouses`, `zones`,
 * `skus`, `lots`, `partners`) are fetched client-side on tab switch via
 * `useWmsRefs` (mirrors the read-model-lag / no-tight-refetch discipline of
 * every other wms-ops section).
 *
 * Eligibility gate (console-integration-contract § 2.4.5 tenant-model
 * divergence): identical to `inbound-state.ts` — the `(console)/wms/master`
 * PAGE resolves the operator's wms eligibility from the data-driven registry
 * (`productKey=wms`) and passes it in here. Not eligible ⇒ block with NO
 * wms call ever fabricated (no cross-tenant call).
 *
 * Resilience boundary (§ 2.4.5 / § 2.5, mirrors `inbound-state.ts`):
 *   - `401` (IAM OIDC session expired) → `redirect('/login')` — a
 *     WHOLE-SESSION re-login, NOT a per-section degrade.
 *   - `403` (role-insufficient) → a non-crashing inline "not available to
 *     your role" state.
 *   - `503` / timeout / network → DEGRADED — ONLY this section renders a
 *     degraded notice; the console shell stays intact.
 *   - any other producer error → degrade rather than crash.
 */
export interface WmsMasterSectionState {
  /** Server-seeded page-0 snapshot for `refType` (the table's initialData). */
  refs: RefPage | null;
  /** The seeded tab — always `DEFAULT_REF_TYPE` server-side. */
  refType: RefType;
  /** True when the operator is not wms-eligible — actionable block, no call. */
  notEligible: boolean;
  /** True on a role-insufficient (403) producer response — inline. */
  forbidden: boolean;
  /** True on 503 / timeout / network — section degrades only. */
  degraded: boolean;
  /** NON-blocking eventual-consistency hint (seconds), or null. */
  lagSeconds: number | null;
}

const EMPTY: WmsMasterSectionState = {
  refs: null,
  refType: DEFAULT_REF_TYPE,
  notEligible: false,
  forbidden: false,
  degraded: false,
  lagSeconds: null,
};

/**
 * @param eligible whether the operator is wms-eligible, resolved by the
 *   page from the data-driven registry. `false` ⇒ block (no wms call).
 */
export async function getWmsMasterState(
  eligible: boolean,
): Promise<WmsMasterSectionState> {
  if (!eligible) {
    // Not wms-eligible — never fabricate a cross-tenant call.
    return { ...EMPTY, notEligible: true };
  }

  try {
    const page = await listRefs(DEFAULT_REF_TYPE, { page: 0, size: 20 });
    return {
      ...EMPTY,
      refs: page.data,
      lagSeconds:
        page.lagSeconds && page.lagSeconds > 0 ? page.lagSeconds : null,
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
