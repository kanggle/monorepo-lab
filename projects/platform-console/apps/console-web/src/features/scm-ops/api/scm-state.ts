import { redirect } from 'next/navigation';
import {
  ApiError,
  ScmUnavailableError,
  ScmRateLimitedError,
} from '@/shared/api/errors';
import {
  listPurchaseOrders,
  getSnapshot,
  getStaleness,
} from './scm-api';
import type { PoPage, SnapshotResponse, StalenessResponse } from './types';

/**
 * Server-side scm section state for the split 조달/재고 routes
 * (TASK-PC-FE-008 read section, split per TASK-PC-FE-220). STRICTLY
 * READ-ONLY — no mutation ever.
 *
 * TASK-PC-FE-220: the former combined `getScmSectionState` (which seeded
 * PO list + snapshot + staleness in one call for the single `/scm` screen)
 * is split into two purpose-scoped fetchers — `getScmProcurementState`
 * (`/scm/procurement`: PO list) and `getScmInventoryState`
 * (`/scm/inventory`: snapshot + staleness) — so each route seeds only its
 * own reads. Both share the SAME eligibility gate + resilience mapping via
 * `classifyScmError`.
 *
 * Eligibility gate (console-integration-contract § 2.4.6, reusing the
 * § 2.4.5 tenant-model divergence): scm resolves the operator's tenant
 * from the JWT `tenant_id ∈ {scm,*}` claim producer-side — the console
 * does NOT send a tenant. To avoid fabricating a cross-tenant call, the
 * `(console)/scm/**` PAGE (the app layer — the layer allowed to compose
 * `features/*`) first resolves the operator's scm eligibility from the
 * data-driven registry (§ 2.2, `getCatalog()`) and passes it in here. If
 * not eligible the section blocks with an actionable "no scm-scoped
 * access" state and NO scm call is ever made. scm still rejects
 * cross-tenant producer-side regardless (`403 TENANT_FORBIDDEN`, never
 * weakened here).
 *
 * (The eligibility check lives in the page, not this api module, so this
 * feature never imports another feature — architecture.md § Allowed
 * Dependencies / § Boundary Rules.)
 *
 * Resilience boundary (§ 2.4.6 / § 2.5, mirrors `wms-state.ts`):
 *   - `401` (IAM OIDC session expired) → `redirect('/login')` — a
 *     WHOLE-SESSION re-login, NOT a per-section degrade (no partial authed
 *     state; consistent with the FE-002..007 401 discipline).
 *   - `403` (token not scm-scoped / insufficient scope) → a non-crashing
 *     inline "not available / not scoped" state.
 *   - `429` (rate-limited) → degrade the section with a "rate-limited"
 *     notice (the api client already did ONE bounded backoff — the page
 *     does NOT re-storm; an explicit user retry re-enters).
 *   - `503` / timeout / network → DEGRADED — ONLY the scm section renders
 *     a degraded notice; the console shell + the other sections stay
 *     intact.
 *   - any other producer error → degrade rather than crash.
 *
 * S5 (§ 2.4.6): the snapshot's REQUIRED `meta.warning` rides through in
 * the `SnapshotResponse` view-model (it is a required, surfaced field in
 * `types.ts` — never stripped here). The screen renders it prominently.
 */

/** Per-section resilience flags shared by both split states. */
interface ScmResilienceFlags {
  /** True when the operator is not scm-eligible (no scm product/tenant in
   *  their registry) — actionable block, no scm call fabricated. */
  notEligible: boolean;
  /** True on a 403 (token not scm-scoped / insufficient scope) — inline. */
  forbidden: boolean;
  /** True on 429 (rate-limited; one bounded backoff already done) — the
   *  section degrades with a "rate-limited, retry later" notice. */
  rateLimited: boolean;
  /** True on 503 / timeout / network — scm section degrades only. */
  degraded: boolean;
}

const CLEAR: ScmResilienceFlags = {
  notEligible: false,
  forbidden: false,
  rateLimited: false,
  degraded: false,
};

/**
 * Map a caught scm read error to the resilience flags, or perform a
 * whole-session `redirect('/login')` on a 401 (no partial authed state).
 * Shared by both split fetchers so the 조달/재고 routes degrade identically.
 */
function classifyScmError(err: unknown): ScmResilienceFlags {
  if (err instanceof ApiError && err.status === 401) {
    // No partial authed state → clean WHOLE-SESSION re-login.
    redirect('/login');
  }
  if (err instanceof ApiError && err.status === 403) {
    // Token not scm-scoped → inline "not available / not scoped".
    return { ...CLEAR, forbidden: true };
  }
  if (err instanceof ScmRateLimitedError) {
    // The api client already did ONE bounded backoff and the gateway is
    // STILL rate-limiting — degrade with a notice; NO further storm.
    return { ...CLEAR, rateLimited: true };
  }
  if (err instanceof ScmUnavailableError) {
    // Degrade ONLY the scm section — shell + other sections intact.
    return { ...CLEAR, degraded: true };
  }
  // Any other producer error (404/400/422 on a seeded page-0 read —
  // should not happen for these) → degrade rather than crash.
  return { ...CLEAR, degraded: true };
}

// ── 조달 (procurement PO list) ────────────────────────────────────────

export interface ScmProcurementState extends ScmResilienceFlags {
  poList: PoPage | null;
}

/**
 * Seed the `/scm/procurement` route: the PO list (read-only).
 *
 * @param eligible whether the operator is scm-eligible, resolved by the
 *   page from the data-driven registry. `false` ⇒ block (no scm call).
 */
export async function getScmProcurementState(
  eligible: boolean,
): Promise<ScmProcurementState> {
  if (!eligible) {
    // Not scm-eligible — never fabricate a cross-tenant call.
    return { poList: null, ...CLEAR, notEligible: true };
  }
  try {
    const po = await listPurchaseOrders({ page: 0, size: 20 });
    return { poList: po.data, ...CLEAR };
  } catch (err) {
    return { poList: null, ...classifyScmError(err) };
  }
}

// ── 재고 (inventory-visibility snapshot + staleness) ──────────────────

export interface ScmInventoryState extends ScmResilienceFlags {
  snapshot: SnapshotResponse | null;
  staleness: StalenessResponse | null;
}

/**
 * Seed the `/scm/inventory` route: the cross-node snapshot + node
 * staleness (read-only). The snapshot's REQUIRED S5 `meta.warning` rides
 * through the view-model (never stripped).
 *
 * @param eligible whether the operator is scm-eligible, resolved by the
 *   page from the data-driven registry. `false` ⇒ block (no scm call).
 */
export async function getScmInventoryState(
  eligible: boolean,
): Promise<ScmInventoryState> {
  if (!eligible) {
    // Not scm-eligible — never fabricate a cross-tenant call.
    return { snapshot: null, staleness: null, ...CLEAR, notEligible: true };
  }
  try {
    const [snap, stale] = await Promise.all([
      getSnapshot({ page: 0, size: 20 }),
      getStaleness(),
    ]);
    return { snapshot: snap.data, staleness: stale.data, ...CLEAR };
  } catch (err) {
    return { snapshot: null, staleness: null, ...classifyScmError(err) };
  }
}
