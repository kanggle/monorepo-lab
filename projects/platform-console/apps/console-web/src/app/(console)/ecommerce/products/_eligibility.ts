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
  /**
   * TASK-PC-FE-346 — the registry's ecommerce tenants, for the write-button gate
   * (`../_write-access.ts`). `null` = unknown (registry degraded / failed) ⇒ buttons shown.
   */
  tenants: readonly string[] | null;
}

export async function resolveEcommerceEligibility(): Promise<EcommerceEligibility> {
  try {
    const catalog = await getCatalog();
    if (catalog.degraded) {
      return { eligible: false, registryDegraded: true, tenants: null };
    }
    const ecommerce = catalog.products.find(
      (p) => p.productKey === 'ecommerce',
    );
    return {
      eligible: Boolean(
        ecommerce && ecommerce.available && ecommerce.tenants.length > 0,
      ),
      registryDegraded: false,
      tenants: ecommerce ? ecommerce.tenants : [],
    };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login?error=session_expired');
    }
    return { eligible: false, registryDegraded: true, tenants: null };
  }
}
