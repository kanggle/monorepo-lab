import { redirect } from 'next/navigation';
import { getCatalog } from '@/features/catalog';
import { ApiError } from '@/shared/api/errors';

/**
 * Shared ecommerce eligibility pre-flight for the `/ecommerce/products/**`
 * routes (TASK-PC-FE-081 — § 2.2 / § 2.4.10). Resolves the operator's
 * ecommerce eligibility from the data-driven registry (`productKey=ecommerce`)
 * so the page never fabricates a cross-tenant call.
 *
 *   - registry `401` → `redirect('/login')` (whole-session re-login; no
 *     partial authed state).
 *   - registry degraded / 5xx / circuit-open → `{ degraded: true }` (cannot
 *     prove ineligibility from a failed registry — render the degraded note).
 *   - else → `{ eligible }` from `available && tenants.length > 0`.
 */
export interface EcommerceEligibility {
  eligible: boolean;
  registryDegraded: boolean;
}

export async function resolveEcommerceEligibility(): Promise<EcommerceEligibility> {
  try {
    const catalog = await getCatalog();
    if (catalog.degraded) {
      return { eligible: false, registryDegraded: true };
    }
    const ecommerce = catalog.products.find(
      (p) => p.productKey === 'ecommerce',
    );
    return {
      eligible: Boolean(
        ecommerce && ecommerce.available && ecommerce.tenants.length > 0,
      ),
      registryDegraded: false,
    };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login');
    }
    return { eligible: false, registryDegraded: true };
  }
}
