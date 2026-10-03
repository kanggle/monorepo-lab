import { z } from 'zod';
import { OperatorContextSchema } from './operator-context-types';

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

// TASK-MONO-751 added `fan` (ADR-MONO-079 D4-A) together with admin-service `ProductCatalog` — the
// fixed-membership guard: a producer key missing here makes the whole catalog parse fail.
export const ProductKeySchema = z.enum(['iam', 'wms', 'scm', 'erp', 'finance', 'ecommerce', 'fan']);
export type ProductKey = z.infer<typeof ProductKeySchema>;

// `OperatorContextSchema`/`OperatorContext` moved to
// `shared/api/operator-context-types.ts` (TASK-PC-FE-271 — was
// byte-identically duplicated here and in `shared/api/iam-operators-types.ts`;
// see that module's doc comment for the full producer-symmetry citation).
// Per-operator per-product profile attribute carrier (TASK-BE-304 producer /
// TASK-PC-FE-014 consumer). The producer omits this field entirely when no
// attribute is set (Jackson `@JsonInclude(NON_NULL)`) — `undefined` here,
// never literal `null`. v1: only the `finance` product item populates
// `defaultAccountId`; the other 5 items always omit `operatorContext`. See
// `console-integration-contract.md § 2.2` + `console-registry-api.md
// § Per-operator profile attributes`.

export const RegistryProductSchema = z.object({
  productKey: ProductKeySchema,
  displayName: z.string().min(1),
  available: z.boolean(),
  tenants: z.array(z.string()),
  baseRoute: z.string().min(1),
  operatorContext: OperatorContextSchema.optional(),
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
