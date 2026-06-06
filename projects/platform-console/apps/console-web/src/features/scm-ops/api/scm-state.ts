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
 * Server-side scm operations section state for the `(console)/scm` route
 * (TASK-PC-FE-008 — the SECOND non-IAM federation; completes Phase 4).
 * STRICTLY READ-ONLY — no mutation ever.
 *
 * Eligibility gate (console-integration-contract § 2.4.6, reusing the
 * § 2.4.5 tenant-model divergence): scm resolves the operator's tenant
 * from the JWT `tenant_id ∈ {scm,*}` claim producer-side — the console
 * does NOT send a tenant. To avoid fabricating a cross-tenant call, the
 * `(console)/scm` PAGE (the app layer — the layer allowed to compose
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
 *     a degraded notice; the console shell + the GAP/wms sections stay
 *     intact.
 *   - any other producer error → degrade rather than crash.
 *
 * S5 (§ 2.4.6): the snapshot's REQUIRED `meta.warning` rides through in
 * the `SnapshotResponse` view-model (it is a required, surfaced field in
 * `types.ts` — never stripped here). The screen renders it prominently.
 */
export interface ScmSectionState {
  poList: PoPage | null;
  snapshot: SnapshotResponse | null;
  staleness: StalenessResponse | null;
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

const EMPTY: ScmSectionState = {
  poList: null,
  snapshot: null,
  staleness: null,
  notEligible: false,
  forbidden: false,
  rateLimited: false,
  degraded: false,
};

/**
 * @param eligible whether the operator is scm-eligible, resolved by the
 *   page from the data-driven registry. `false` ⇒ block (no scm call).
 */
export async function getScmSectionState(
  eligible: boolean,
): Promise<ScmSectionState> {
  if (!eligible) {
    // Not scm-eligible — never fabricate a cross-tenant call.
    return { ...EMPTY, notEligible: true };
  }

  try {
    const [po, snap, stale] = await Promise.all([
      listPurchaseOrders({ page: 0, size: 20 }),
      getSnapshot({ page: 0, size: 20 }),
      getStaleness(),
    ]);
    return {
      poList: po.data,
      snapshot: snap.data,
      staleness: stale.data,
      notEligible: false,
      forbidden: false,
      rateLimited: false,
      degraded: false,
    };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      // No partial authed state → clean WHOLE-SESSION re-login.
      redirect('/login');
    }
    if (err instanceof ApiError && err.status === 403) {
      // Token not scm-scoped → inline "not available / not scoped".
      return { ...EMPTY, forbidden: true };
    }
    if (err instanceof ScmRateLimitedError) {
      // The api client already did ONE bounded backoff and the gateway is
      // STILL rate-limiting — degrade with a notice; NO further storm.
      return { ...EMPTY, rateLimited: true };
    }
    if (err instanceof ScmUnavailableError) {
      // Degrade ONLY the scm section — shell + GAP/wms sections intact.
      return { ...EMPTY, degraded: true };
    }
    // Any other producer error (404/400/422 on a seeded page-0 read —
    // should not happen for these) → degrade rather than crash.
    return { ...EMPTY, degraded: true };
  }
}
