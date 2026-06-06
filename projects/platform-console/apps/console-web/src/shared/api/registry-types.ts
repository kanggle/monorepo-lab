import { z } from 'zod';

/**
 * IAM product/tenant registry response shape.
 *
 * Authoritative producer contract: TASK-BE-296
 * `projects/iam-platform/specs/contracts/http/console-registry-api.md`
 * (`GET /api/admin/console/registry`, admin-service, operator-auth boundary).
 *
 * Item shape is governed by the consumer contract
 * `console-integration-contract.md § 2.2` — productKey / displayName /
 * available / tenants / baseRoute. Both contracts are kept in sync; this zod
 * schema is the runtime parser the contract test asserts against.
 */

export const ProductKeySchema = z.enum(['iam', 'wms', 'scm', 'erp', 'finance']);
export type ProductKey = z.infer<typeof ProductKeySchema>;

/**
 * Per-operator per-product profile attribute carrier (TASK-BE-304 producer /
 * TASK-PC-FE-014 consumer). The producer omits this field entirely when no
 * attribute is set (Jackson `@JsonInclude(NON_NULL)`) — `undefined` here,
 * never literal `null`. v1: only the `finance` product item populates
 * `defaultAccountId` (sourced from IAM `admin_operators.finance_default_account_id`);
 * the other 4 items always omit `operatorContext`. The schema is intentionally
 * additive + optional so a v0 producer / unprovisioned operator parses cleanly.
 * See `console-integration-contract.md § 2.2` + `console-registry-api.md
 * § Per-operator profile attributes`.
 */
export const OperatorContextSchema = z
  .object({
    defaultAccountId: z.string().optional(),
  })
  .optional();
export type OperatorContext = z.infer<typeof OperatorContextSchema>;

export const RegistryProductSchema = z.object({
  productKey: ProductKeySchema,
  displayName: z.string().min(1),
  available: z.boolean(),
  tenants: z.array(z.string()),
  baseRoute: z.string().min(1),
  operatorContext: OperatorContextSchema,
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
