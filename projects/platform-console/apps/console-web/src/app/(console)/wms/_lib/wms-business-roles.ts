import { getActiveTenant, isSampleVisitor } from '@/shared/lib/session';
import { getMyDomainRolesOrNull } from '@/shared/api/iam-operators-read';
import { businessRolesWithin, type DomainKey } from '@/shared/ui/console-nav-exposure';

/**
 * TASK-PC-FE-338 c-2 AC-2 — the operator's business roles for a WMS screen's write-button gate
 * (console-integration-contract § 2.4.3.4 «Read-only sets hide write buttons»): row 12
 * (`GET /api/admin/me/domain-access`) ∩ the active tenant's subscription to `wms` — the same input
 * as the nav's «업무 권한 없음» signal, which after P3 equals the assumed token's `roles`.
 *
 * `wmsTenants` = the registry's `wms` product tenants the page already fetched (no second
 * registry call). Returns `undefined` («don't know» ⇒ buttons shown) for a sample visitor, no
 * active tenant, an unknown registry, or a failed row 12. App-layer composition (it joins a
 * `features/catalog` read with `shared/` reads), so it lives under `app/`.
 */
export async function getWmsBusinessRoles(
  wmsTenants: readonly string[] | null,
): Promise<ReadonlySet<string> | undefined> {
  if (await isSampleVisitor()) return undefined;
  const activeTenant = await getActiveTenant();
  if (!activeTenant || wmsTenants === null) return undefined;
  const subscribed = new Set<DomainKey>(wmsTenants.includes(activeTenant) ? ['wms'] : []);
  return businessRolesWithin(await getMyDomainRolesOrNull(), subscribed);
}
