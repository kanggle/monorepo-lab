import { mapSectionResilience, mapDetailResilience } from './section-state';
import { listProducts, getProduct } from './ecommerce-api';
import type { ProductList, ProductDetail, ProductListParams } from './types';

/**
 * Server-side ecommerce product operations section state for the
 * `(console)/ecommerce/products` routes (TASK-PC-FE-081 — the first ecommerce
 * write surface, ADR-MONO-031 Phase 1b).
 *
 * Eligibility gate (console-integration-contract § 2.4.10 / § 2.2): ecommerce
 * resolves the operator's tenant from the JWT `tenant_id ∈ {ecommerce,*}`
 * claim producer-side — the console does NOT send a tenant. To avoid
 * fabricating a cross-tenant call, the PAGE (app layer — the layer allowed to
 * compose `features/*`) first resolves the operator's ecommerce eligibility
 * from the data-driven registry (§ 2.2, `getCatalog()`, `productKey=ecommerce`)
 * and passes it in here. If not eligible the section blocks with an actionable
 * "no ecommerce-scoped access" state and NO ecommerce call is ever made.
 * ecommerce still rejects cross-tenant producer-side regardless.
 *
 * (The eligibility check lives in the page, not this api module, so this
 * feature never imports another feature — architecture.md § Allowed
 * Dependencies / § Boundary Rules.)
 *
 * Resilience boundary (§ 2.4.10 / § 2.5, mirrors `outbound-state.ts`):
 *   - `401` (IAM OIDC session expired) → `redirect('/login')` — a
 *     WHOLE-SESSION re-login, NOT a per-section degrade.
 *   - `403` (role-insufficient) → a non-crashing inline "not available to
 *     your role" state.
 *   - `503` / timeout / network → DEGRADED — ONLY the ecommerce section
 *     renders a degraded notice; the console shell stays intact.
 *   - any other producer error → degrade rather than crash.
 */
export interface ProductsSectionState {
  /** Server-seeded page-0 product list (the table's initialData). */
  products: ProductList | null;
  /** True when the operator is not ecommerce-eligible — actionable block. */
  notEligible: boolean;
  /** True on a role-insufficient (403) producer response — inline. */
  forbidden: boolean;
  /** True on 503 / timeout / network — section degrades only. */
  degraded: boolean;
}

const EMPTY: ProductsSectionState = {
  products: null,
  notEligible: false,
  forbidden: false,
  degraded: false,
};

/**
 * @param eligible whether the operator is ecommerce-eligible, resolved by the
 *   page from the data-driven registry. `false` ⇒ block (no ecommerce call).
 * @param params optional list filters (status / category / page).
 */
export async function getProductsSectionState(
  eligible: boolean,
  params: ProductListParams = {},
): Promise<ProductsSectionState> {
  if (!eligible) {
    // Not ecommerce-eligible — never fabricate a cross-tenant call.
    return { ...EMPTY, notEligible: true };
  }

  try {
    const products = await listProducts({
      page: 0,
      size: 20,
      ...params,
    });
    return { ...EMPTY, products };
  } catch (err) {
    return { ...EMPTY, ...mapSectionResilience(err) };
  }
}

export interface ProductDetailSectionState {
  detail: ProductDetail | null;
  notEligible: boolean;
  forbidden: boolean;
  /** True on 404 PRODUCT_NOT_FOUND — actionable not-found, not a crash. */
  notFound: boolean;
  degraded: boolean;
}

const DETAIL_EMPTY: ProductDetailSectionState = {
  detail: null,
  notEligible: false,
  forbidden: false,
  notFound: false,
  degraded: false,
};

/** Detail-page section state (the `[id]` + `[id]/edit` routes). */
export async function getProductDetailSectionState(
  eligible: boolean,
  id: string,
): Promise<ProductDetailSectionState> {
  if (!eligible) {
    return { ...DETAIL_EMPTY, notEligible: true };
  }
  try {
    const detail = await getProduct(id);
    return { ...DETAIL_EMPTY, detail };
  } catch (err) {
    return { ...DETAIL_EMPTY, ...mapDetailResilience(err) };
  }
}
