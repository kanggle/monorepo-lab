import { getActiveTenant, isSampleVisitor } from '@/shared/lib/session';
import { getMyDomainRolesOrNull } from '@/shared/api/iam-operators-read';
import { businessRolesWithin, type DomainKey } from '@/shared/ui/console-nav-exposure';

/**
 * The operator's business roles for a domain screen's write-button gate
 * (console-integration-contract § 2.4.3.4 «Read-only sets hide write buttons»): row 12
 * (`GET /api/admin/me/domain-access`) ∩ the active tenant's subscriptions to the given domains —
 * the same input as the nav's «업무 권한 없음» signal, which after P3 equals the assumed token's
 * `roles`.
 *
 * `tenantsByDomain` = for each domain whose buttons the screen gates, the registry product's
 * tenants the page already fetched (no second registry call). A screen with a button served by
 * another domain passes BOTH (TASK-PC-FE-342: `/wms/outbound` «발송 재시도» → `{ wms, scm }`).
 *
 * Returns `undefined` («don't know» ⇒ buttons shown) for a sample visitor, no active tenant, an
 * unknown registry (any `null` entry), or a failed row 12. App-layer composition (it joins a
 * `features/catalog` read with `shared/` reads), so it lives under `app/`.
 *
 * TASK-PC-FE-338 c-2 introduced this as the WMS-only `getWmsBusinessRoles`; TASK-PC-FE-342
 * generalised it to any set of domains.
 */
export async function getDomainBusinessRoles(
  tenantsByDomain: Partial<Record<DomainKey, readonly string[] | null>>,
): Promise<ReadonlySet<string> | undefined> {
  if (await isSampleVisitor()) return undefined;
  const activeTenant = await getActiveTenant();
  if (!activeTenant) return undefined;
  const subscribed = new Set<DomainKey>();
  for (const [domain, tenants] of Object.entries(tenantsByDomain) as [
    DomainKey,
    readonly string[] | null | undefined,
  ][]) {
    if (tenants === null || tenants === undefined) return undefined;
    if (tenants.includes(activeTenant)) subscribed.add(domain);
  }
  return businessRolesWithin(await getMyDomainRolesOrNull(), subscribed);
}
