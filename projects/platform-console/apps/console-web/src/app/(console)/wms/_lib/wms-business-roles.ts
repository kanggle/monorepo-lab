import { getDomainBusinessRoles } from '../../_lib/domain-business-roles';

/**
 * TASK-PC-FE-338 c-2 AC-2 — the operator's business roles for a WMS screen's write-button gate
 * (console-integration-contract § 2.4.3.4 «Read-only sets hide write buttons»): row 12
 * (`GET /api/admin/me/domain-access`) ∩ the active tenant's subscription to `wms` — the same input
 * as the nav's «업무 권한 없음» signal, which after P3 equals the assumed token's `roles`.
 *
 * `wmsTenants` = the registry's `wms` product tenants the page already fetched (no second
 * registry call). Returns `undefined` («don't know» ⇒ buttons shown) for a sample visitor, no
 * active tenant, an unknown registry, or a failed row 12. Thin wrapper over
 * {@link getDomainBusinessRoles} (TASK-PC-FE-342 generalised it).
 */
export async function getWmsBusinessRoles(
  wmsTenants: readonly string[] | null,
): Promise<ReadonlySet<string> | undefined> {
  return getDomainBusinessRoles({ wms: wmsTenants });
}
