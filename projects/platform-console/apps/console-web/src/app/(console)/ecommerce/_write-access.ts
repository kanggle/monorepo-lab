import { canWriteOn } from '@/shared/ui/console-nav-exposure';
import { getDomainBusinessRoles } from '../_lib/domain-business-roles';
import type { EcommerceEligibility } from './products/_eligibility';

/**
 * TASK-PC-FE-346 (contract § 2.4.3.4 «Read-only sets hide write buttons») — may the operator see the
 * mutation buttons of the E-commerce screen at `href`?
 *
 * `eligibility.tenants` = the registry's ecommerce tenants the page already fetched (no second
 * registry call). Every «don't know» — sample visitor, no active tenant, registry unknown (`null`),
 * row 12 failed — is `undefined` roles ⇒ `true`, buttons SHOWN (an outage must never read as «no
 * permission»; the server stays the gate). Only KNOWN roles that hold none of the screen's
 * `writeRequires` ⇒ `false`.
 */
export async function resolveEcommerceCanWrite(
  href: string,
  eligibility: Pick<EcommerceEligibility, 'tenants'>,
): Promise<boolean> {
  return canWriteOn(href, await getDomainBusinessRoles({ ecommerce: eligibility.tenants }));
}
