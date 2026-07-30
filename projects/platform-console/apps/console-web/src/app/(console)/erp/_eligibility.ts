import { getCatalog } from '@/features/catalog';
import { ApiError } from '@/shared/api/errors';
import { redirect } from 'next/navigation';

/**
 * Shared erp eligibility pre-flight for the `(console)/erp/**` routes
 * (TASK-PC-FE-076 drill-in split — extracted verbatim from the original
 * single `(console)/erp/page.tsx`).
 *
 * ── WHY THIS LIVES IN `app/` (TASK-PC-FE-259) ──
 * This is a **registry pre-flight**, i.e. route-level composition, and every
 * other federated domain already performs it in the page: `wms-ops`,
 * `scm-ops`, `scm-replenishment`, `finance-ops`, `ledger-ops` and
 * `ecommerce-ops` state modules all document "the page resolves … from the
 * data-driven registry (§ 2.2, `getCatalog()`) and passes it in here".
 * `features/erp-ops` was the lone straggler: it pulled the pre-flight INSIDE
 * the feature, which forced `import { getCatalog } from '@/features/catalog'`
 * — a `features/A → features/B` import that `architecture.md`
 * § Forbidden Dependencies bars. `app/` is explicitly allowed to import
 * `features/*` + `shared/*` (§ Allowed Dependencies), and the codebase already
 * has this exact file shape one directory over:
 * `app/(console)/ecommerce/products/_eligibility.ts`. So the fix is a LAYER
 * correction, not a `shared/` promotion — erp now matches its five siblings.
 * Behaviour is unchanged (moved verbatim); the five erp routes import it
 * relatively instead of through the `features/erp-ops` barrel.
 *
 * erp resolves the operator's tenant from the JWT `tenant_id ∈ {erp,*}`
 * claim producer-side (console-integration-contract § 2.4.8, reusing the
 * § 2.4.5 tenant-model divergence) — the console sends NO tenant. To
 * avoid fabricating a cross-tenant call, every erp route first resolves
 * the operator's erp eligibility from the data-driven registry (§ 2.2,
 * `getCatalog()`) and passes the result into the route's state loader.
 *
 * A `401` from the registry → whole-session re-login (`redirect('/login')`,
 * which throws — this function never returns in that branch). Any other
 * registry failure → `registryDegraded` so the route renders the shared
 * degraded notice instead of crashing.
 */
export interface ErpEligibility {
  /** True ⇒ the operator has an available erp product with ≥1 tenant. */
  eligible: boolean;
  /** True ⇒ the registry itself was unreachable (render the degraded
   *  notice; do NOT attempt the erp call). */
  registryDegraded: boolean;
}

export async function resolveErpEligibility(): Promise<ErpEligibility> {
  try {
    const catalog = await getCatalog();
    const erp = catalog.products.find((p) => p.productKey === 'erp');
    return {
      eligible: Boolean(erp && erp.available && erp.tenants.length > 0),
      registryDegraded: false,
    };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login');
    }
    return { eligible: false, registryDegraded: true };
  }
}
