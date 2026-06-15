import { mapSectionResilience, mapDetailResilience } from './section-state';
import { listSellers, getSeller } from './sellers-api';
import type { SellerList, SellerDetail, SellerListParams } from './seller-types';

/**
 * Server-side ecommerce seller operations section state for the
 * `(console)/ecommerce/sellers` routes (TASK-PC-FE-090 — ADR-MONO-031
 * § 2.4.10 7th area). Mirrors `users-state.ts` / `promotions-state.ts`.
 *
 * Eligibility gate: the page resolves the operator's ecommerce eligibility
 * from the data-driven registry and passes it here. If not eligible the
 * section blocks with an actionable state and NO ecommerce call is made.
 *
 * Resilience boundary (§ 2.4.10 / § 2.5):
 *   - `401` (IAM OIDC session expired) → `redirect('/login')` — WHOLE-SESSION.
 *   - `403` (role-insufficient) → non-crashing inline "not available to role".
 *   - `503` / timeout / network → DEGRADED — ONLY the ecommerce section
 *     renders a degraded notice; the console shell stays intact.
 *   - any other producer error → degrade rather than crash.
 */
export interface SellersSectionState {
  /** Server-seeded page-0 seller list (the table's initialData). */
  sellers: SellerList | null;
  /** True when the operator is not ecommerce-eligible — actionable block. */
  notEligible: boolean;
  /** True on a role-insufficient (403) producer response — inline. */
  forbidden: boolean;
  /** True on 503 / timeout / network — section degrades only. */
  degraded: boolean;
}

const EMPTY: SellersSectionState = {
  sellers: null,
  notEligible: false,
  forbidden: false,
  degraded: false,
};

/**
 * @param eligible whether the operator is ecommerce-eligible, resolved by
 *   the page from the data-driven registry. `false` ⇒ block (no call).
 * @param params optional list filters (page / size).
 */
export async function getSellersSectionState(
  eligible: boolean,
  params: SellerListParams = {},
): Promise<SellersSectionState> {
  if (!eligible) {
    return { ...EMPTY, notEligible: true };
  }

  try {
    const sellers = await listSellers({ page: 0, size: 20, ...params });
    return { ...EMPTY, sellers };
  } catch (err) {
    return { ...EMPTY, ...mapSectionResilience(err) };
  }
}

export interface SellerDetailSectionState {
  detail: SellerDetail | null;
  notEligible: boolean;
  forbidden: boolean;
  /** True on 404 SELLER_NOT_FOUND — actionable not-found, not a crash. */
  notFound: boolean;
  degraded: boolean;
}

const DETAIL_EMPTY: SellerDetailSectionState = {
  detail: null,
  notEligible: false,
  forbidden: false,
  notFound: false,
  degraded: false,
};

/** Detail-page section state (the `[id]` route). */
export async function getSellerDetailSectionState(
  eligible: boolean,
  sellerId: string,
): Promise<SellerDetailSectionState> {
  if (!eligible) {
    return { ...DETAIL_EMPTY, notEligible: true };
  }
  try {
    const detail = await getSeller(sellerId);
    return { ...DETAIL_EMPTY, detail };
  } catch (err) {
    return { ...DETAIL_EMPTY, ...mapDetailResilience(err) };
  }
}
