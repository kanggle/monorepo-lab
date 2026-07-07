import { mapSectionResilience, mapDetailResilience } from './section-state';
import { listPromotions, getPromotion } from './ecommerce-api';
import type { PromotionList, PromotionDetail, PromotionListParams } from './types';

/**
 * Server-side ecommerce promotion operations section state for the
 * `(console)/ecommerce/promotions` routes (TASK-PC-FE-086 — ADR-031 Phase 3b).
 *
 * Eligibility gate: ecommerce resolves the operator's tenant from the JWT
 * `tenant_id ∈ {ecommerce,*}` claim — the console sends NO `X-Tenant-Id`.
 * The page resolves ecommerce eligibility from the data-driven registry and
 * passes it in here. If not eligible the section blocks with no ecommerce call.
 *
 * Resilience boundary (mirrors products-state.ts):
 *   - `401` → `redirect('/login')` (whole-session re-login, NOT a per-section degrade).
 *   - `403` → non-crashing inline "not available to your role" state.
 *   - `503` / timeout / network → DEGRADED (only the ecommerce section degrades).
 *   - any other producer error → degrade rather than crash.
 */
export interface PromotionsSectionState {
  /** Server-seeded page-0 promotion list (the table's initialData). */
  promotions: PromotionList | null;
  /** True when the operator is not ecommerce-eligible — actionable block. */
  notEligible: boolean;
  /** True on a role-insufficient (403) producer response — inline. */
  forbidden: boolean;
  /** True on 503 / timeout / network — section degrades only. */
  degraded: boolean;
}

const EMPTY: PromotionsSectionState = {
  promotions: null,
  notEligible: false,
  forbidden: false,
  degraded: false,
};

/**
 * @param eligible whether the operator is ecommerce-eligible, resolved by the
 *   page from the data-driven registry. `false` ⇒ block (no ecommerce call).
 * @param params optional list filters (status / page / size).
 */
export async function getPromotionsSectionState(
  eligible: boolean,
  params: PromotionListParams = {},
): Promise<PromotionsSectionState> {
  if (!eligible) {
    return { ...EMPTY, notEligible: true };
  }
  try {
    const promotions = await listPromotions({ page: 0, size: 20, ...params });
    return { ...EMPTY, promotions };
  } catch (err) {
    return { ...EMPTY, ...mapSectionResilience(err) };
  }
}

export interface PromotionDetailSectionState {
  detail: PromotionDetail | null;
  notEligible: boolean;
  forbidden: boolean;
  /** True on 404 PROMOTION_NOT_FOUND — actionable not-found, not a crash. */
  notFound: boolean;
  degraded: boolean;
}

const DETAIL_EMPTY: PromotionDetailSectionState = {
  detail: null,
  notEligible: false,
  forbidden: false,
  notFound: false,
  degraded: false,
};

/** Detail-page section state (the `[id]` + `[id]/edit` routes). */
export async function getPromotionDetailSectionState(
  eligible: boolean,
  id: string,
): Promise<PromotionDetailSectionState> {
  if (!eligible) {
    return { ...DETAIL_EMPTY, notEligible: true };
  }
  try {
    const detail = await getPromotion(id);
    return { ...DETAIL_EMPTY, detail };
  } catch (err) {
    return { ...DETAIL_EMPTY, ...mapDetailResilience(err) };
  }
}
