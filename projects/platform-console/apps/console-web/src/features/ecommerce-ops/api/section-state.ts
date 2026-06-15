import { redirect } from 'next/navigation';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';

/**
 * Shared, feature-internal section-state error mapping for the ecommerce-ops
 * slices (TASK-PC-FE-094 Unit 2 — Reduce Duplication). The `*-state.ts` files
 * (orders / users / sellers / shippings / notifications) each repeated an
 * identical `mapSectionError` and detail-state 404 mapping. This module hoists
 * the generic resilience flags so each state file stays a thin, typed wrapper.
 *
 * Behavior is byte-identical to the per-slice copies it replaces:
 *   - `401` (IAM OIDC session expired) → `redirect('/login')` — a clean
 *     WHOLE-SESSION re-login (no partial authed state). `redirect` throws, so
 *     control never returns to the caller on this branch.
 *   - `403` (role-insufficient) → `{ forbidden: true }` (inline, non-crashing).
 *   - `EcommerceUnavailableError` (503 / timeout / network) → `{ degraded: true }`.
 *   - any other producer error on a seeded read → `{ degraded: true }` (degrade,
 *     not crash).
 *   - detail reads additionally map a `404` → `{ notFound: true }`.
 *
 * The helpers return PLAIN flag objects (not the per-slice state shape); each
 * state file spreads them onto its own `EMPTY` / `DETAIL_EMPTY` constant, so the
 * returned section-state object is preserved exactly.
 */

/** Generic list/detail resilience flags (forbidden / degraded). */
export interface SectionResilienceFlags {
  forbidden: boolean;
  degraded: boolean;
}

/** Generic detail-read flags — adds the 404 not-found branch. */
export interface DetailResilienceFlags extends SectionResilienceFlags {
  notFound: boolean;
}

/**
 * Maps a server-read error to the generic resilience flags. A `401` triggers a
 * whole-session `redirect('/login')` (which throws — never returns). Every other
 * error degrades the section rather than crashing it.
 */
export function mapSectionResilience(err: unknown): SectionResilienceFlags {
  if (err instanceof ApiError && err.status === 401) {
    // No partial authed state → clean WHOLE-SESSION re-login.
    redirect('/login');
  }
  if (err instanceof ApiError && err.status === 403) {
    return { forbidden: true, degraded: false };
  }
  if (err instanceof EcommerceUnavailableError) {
    return { forbidden: false, degraded: true };
  }
  // Any other producer error on a seeded read → degrade, not crash.
  return { forbidden: false, degraded: true };
}

/**
 * Maps a detail server-read error to the generic detail flags: a `404` →
 * `{ notFound: true }`; otherwise the {@link mapSectionResilience} forbidden /
 * degraded mapping (with `notFound: false`).
 */
export function mapDetailResilience(err: unknown): DetailResilienceFlags {
  if (err instanceof ApiError && err.status === 404) {
    return { forbidden: false, degraded: false, notFound: true };
  }
  const { forbidden, degraded } = mapSectionResilience(err);
  return { forbidden, degraded, notFound: false };
}
