import { z } from 'zod';

/**
 * GAP product/tenant registry response shape.
 *
 * Authoritative producer contract: TASK-BE-296
 * `projects/global-account-platform/specs/contracts/http/console-registry-api.md`
 * (`GET /api/admin/console/registry`, admin-service, operator-auth boundary).
 *
 * Item shape is governed by the consumer contract
 * `console-integration-contract.md § 2.2` — productKey / displayName /
 * available / tenants / baseRoute. Both contracts are kept in sync; this zod
 * schema is the runtime parser the contract test asserts against.
 */

export const ProductKeySchema = z.enum(['gap', 'wms', 'scm', 'erp', 'finance']);
export type ProductKey = z.infer<typeof ProductKeySchema>;

export const RegistryProductSchema = z.object({
  productKey: ProductKeySchema,
  displayName: z.string().min(1),
  available: z.boolean(),
  tenants: z.array(z.string()),
  baseRoute: z.string().min(1),
});
export type RegistryProduct = z.infer<typeof RegistryProductSchema>;

export const RegistryResponseSchema = z.object({
  products: z.array(RegistryProductSchema),
});
export type RegistryResponse = z.infer<typeof RegistryResponseSchema>;

/** What the catalog UI consumes. `degraded` ⇒ registry unreachable (resilience
 *  fallback — render shell, not blank crash; integration-heavy I2). */
export interface CatalogState {
  products: RegistryProduct[];
  degraded: boolean;
}
