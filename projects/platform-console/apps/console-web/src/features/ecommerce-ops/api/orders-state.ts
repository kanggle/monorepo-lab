import { mapSectionResilience, mapDetailResilience } from './section-state';
import { listOrders, getOrder } from './ecommerce-api';
import type { OrderList, OrderDetail, OrderListParams } from './order-types';

/**
 * Server-side ecommerce order operations section state for the
 * `(console)/ecommerce/orders` routes (TASK-PC-FE-083 — the orders facet of
 * ADR-MONO-031 Phase 1b). Mirrors `products-state.ts` exactly.
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
export interface OrdersSectionState {
  /** Server-seeded page-0 order list (the table's initialData). */
  orders: OrderList | null;
  /** True when the operator is not ecommerce-eligible — actionable block. */
  notEligible: boolean;
  /** True on a role-insufficient (403) producer response — inline. */
  forbidden: boolean;
  /** True on 503 / timeout / network — section degrades only. */
  degraded: boolean;
}

const EMPTY: OrdersSectionState = {
  orders: null,
  notEligible: false,
  forbidden: false,
  degraded: false,
};

/**
 * @param eligible whether the operator is ecommerce-eligible, resolved by
 *   the page from the data-driven registry. `false` ⇒ block (no call).
 * @param params optional list filters (status / page / size).
 */
export async function getOrdersSectionState(
  eligible: boolean,
  params: OrderListParams = {},
): Promise<OrdersSectionState> {
  if (!eligible) {
    return { ...EMPTY, notEligible: true };
  }

  try {
    const orders = await listOrders({
      page: 0,
      size: 20,
      ...params,
    });
    return { ...EMPTY, orders };
  } catch (err) {
    return { ...EMPTY, ...mapSectionResilience(err) };
  }
}

export interface OrderDetailSectionState {
  detail: OrderDetail | null;
  notEligible: boolean;
  forbidden: boolean;
  /** True on 404 ORDER_NOT_FOUND — actionable not-found, not a crash. */
  notFound: boolean;
  degraded: boolean;
}

const DETAIL_EMPTY: OrderDetailSectionState = {
  detail: null,
  notEligible: false,
  forbidden: false,
  notFound: false,
  degraded: false,
};

/** Detail-page section state (the `[id]` route). */
export async function getOrderDetailSectionState(
  eligible: boolean,
  id: string,
): Promise<OrderDetailSectionState> {
  if (!eligible) {
    return { ...DETAIL_EMPTY, notEligible: true };
  }
  try {
    const detail = await getOrder(id);
    return { ...DETAIL_EMPTY, detail };
  } catch (err) {
    return { ...DETAIL_EMPTY, ...mapDetailResilience(err) };
  }
}
