import { redirect } from 'next/navigation';
import {
  ApiError,
  ScmReplenishmentUnavailableError,
  ScmRateLimitedError,
} from '@/shared/api/errors';
import { listSuggestions } from './demand-planning-api';
import type { SuggestionPage } from './types';

/**
 * Server-side scm replenishment operator-section state for the
 * `(console)/scm/replenishment` route (TASK-PC-FE-077 — the ADR-MONO-027
 * loop's console operator gate). The first scm operator-MUTATION surface,
 * layered on the FE-008 scm read foundation.
 *
 * Eligibility gate (console-integration-contract § 2.4.6.1, reusing the
 * § 2.4.5/§ 2.4.6 tenant-model divergence): scm resolves the operator's tenant
 * from the JWT `tenant_id ∈ {scm,*}` claim producer-side — the console does NOT
 * send a tenant. To avoid fabricating a cross-tenant call, the
 * `(console)/scm/replenishment` PAGE (the app layer) first resolves the
 * operator's scm eligibility from the data-driven registry (§ 2.2,
 * `getCatalog()`) and passes it in here. If not eligible the section blocks
 * with an actionable "no scm-scoped access" state and NO scm call is ever made.
 * scm still rejects cross-tenant producer-side regardless
 * (`403 TENANT_FORBIDDEN`, never weakened here).
 *
 * (The eligibility check lives in the page, not this api module, so this
 * feature never imports another feature — architecture.md § Allowed
 * Dependencies / § Boundary Rules.)
 *
 * Resilience boundary (§ 2.4.6.1 / § 2.5, mirrors `scm-state.ts`):
 *   - `401` (IAM OIDC session expired) → `redirect('/login')` — a
 *     WHOLE-SESSION re-login, NOT a per-section degrade.
 *   - `403` (token not scm-scoped) → a non-crashing inline "not scoped" state.
 *   - `429` (rate-limited) → degrade the section with a "rate-limited" notice
 *     (the api client already did ONE bounded backoff — no re-storm).
 *   - `503` / timeout / network → DEGRADED — ONLY this section degrades; the
 *     console shell + the FE-008 scm read section stay intact.
 *   - any other producer error → degrade rather than crash.
 */
export interface ReplenishmentSectionState {
  suggestions: SuggestionPage | null;
  /** True when the operator is not scm-eligible — actionable block, no call. */
  notEligible: boolean;
  /** True on a 403 (token not scm-scoped) — inline. */
  forbidden: boolean;
  /** True on 429 (rate-limited; one bounded backoff already done). */
  rateLimited: boolean;
  /** True on 503 / timeout / network — this section degrades only. */
  degraded: boolean;
}

const EMPTY: ReplenishmentSectionState = {
  suggestions: null,
  notEligible: false,
  forbidden: false,
  rateLimited: false,
  degraded: false,
};

/**
 * @param eligible whether the operator is scm-eligible, resolved by the page
 *   from the data-driven registry. `false` ⇒ block (no scm call).
 */
export async function getReplenishmentSectionState(
  eligible: boolean,
): Promise<ReplenishmentSectionState> {
  if (!eligible) {
    // Not scm-eligible — never fabricate a cross-tenant call.
    return { ...EMPTY, notEligible: true };
  }

  try {
    const suggestions = await listSuggestions({ page: 0, size: 20 });
    return {
      suggestions,
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
      return { ...EMPTY, forbidden: true };
    }
    if (err instanceof ScmRateLimitedError) {
      // The api client already did ONE bounded backoff — degrade, no storm.
      return { ...EMPTY, rateLimited: true };
    }
    if (err instanceof ScmReplenishmentUnavailableError) {
      // Degrade ONLY this section — shell + scm read section intact.
      return { ...EMPTY, degraded: true };
    }
    // Any other producer error on a seeded page-0 read → degrade, never crash.
    return { ...EMPTY, degraded: true };
  }
}
