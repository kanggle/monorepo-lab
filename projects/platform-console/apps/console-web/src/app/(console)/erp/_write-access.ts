import { canWriteOn } from '@/shared/ui/console-nav-exposure';
import { getDomainBusinessRoles } from '../_lib/domain-business-roles';
import type { ErpEligibility } from './_eligibility';

/**
 * TASK-PC-FE-347 (contract § 2.4.3.4 «Read-only sets hide write buttons») — may the operator see the
 * mutation buttons of the ERP screen at `href`? (`/erp/masters` = `ERP_MASTERDATA_WRITE`,
 * `/erp/approval` · `/erp/delegation` = `ERP_APPROVAL_WRITE`.)
 *
 * `eligibility.tenants` = the registry's erp tenants the page already fetched (no second registry
 * call). Every «don't know» — sample visitor, no active tenant, registry unknown (`null`), row 12
 * failed — is `undefined` roles => `true`, buttons SHOWN (an outage must never read as «no
 * permission»; the server stays the gate). Only KNOWN roles that hold none of the screen's
 * `writeRequires` => `false`.
 */
export async function resolveErpCanWrite(
  href: string,
  eligibility: Pick<ErpEligibility, 'tenants'>,
): Promise<boolean> {
  return canWriteOn(href, await getDomainBusinessRoles({ erp: eligibility.tenants }));
}
