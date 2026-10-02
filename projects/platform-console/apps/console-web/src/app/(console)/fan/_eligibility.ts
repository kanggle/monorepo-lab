import { redirect } from 'next/navigation';
import { getCatalog } from '@/features/catalog';
import { ApiError } from '@/shared/api/errors';

/**
 * Shared fan eligibility pre-flight for the `(console)/fan/**` routes (TASK-MONO-751). Lives
 * in the app layer, like the ecommerce `products/_eligibility.ts`, because a feature may not
 * import another feature (architecture.md § Forbidden Dependencies — the catalog is a
 * feature).
 */
export interface FanEligibility {
  eligible: boolean;
  registryDegraded: boolean;
}

/**
 * The operator's `fan` eligibility from the data-driven registry (`productKey=fan`): the
 * product is listed AND has at least one selectable tenant. For a customer-tenant operator
 * the registry always answers `fan.tenants = []` (admin-service drops the platform-operator-
 * only tenant — ADR-MONO-079 rider R3), so this is `false` and NO fan call is made.
 */
export async function resolveFanEligibility(): Promise<FanEligibility> {
  try {
    const catalog = await getCatalog();
    if (catalog.degraded) return { eligible: false, registryDegraded: true };
    const fan = catalog.products.find((p) => p.productKey === 'fan');
    return {
      eligible: Boolean(fan && fan.available && fan.tenants.length > 0),
      registryDegraded: false,
    };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login?error=session_expired');
    }
    return { eligible: false, registryDegraded: true };
  }
}

