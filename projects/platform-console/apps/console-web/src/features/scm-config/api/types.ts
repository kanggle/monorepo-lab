import { z } from 'zod';

/**
 * Feature-local types for the scm `demand-planning-service` **seed/config**
 * operator surface — TASK-PC-FE-080. The per-SKU reorder-policy +
 * sku-supplier-map inspect (GET) + upsert (PUT) surface that drives FUTURE
 * reorder evaluation, layered on the FE-077 `features/scm-replenishment`
 * (approve/dismiss gate) + FE-008 `features/scm-ops` (read) foundation
 * (share, do NOT fork — credential / flat-envelope / SCM_GATEWAY_BASE_URL /
 * 429 backoff primitives are reused).
 *
 * Authoritative producer contract (do NOT redefine — consumed unchanged):
 *   `scm-platform/specs/contracts/http/demand-planning-api.md`
 *     §  GET|PUT /api/v1/demand-planning/policies/{skuCode}
 *          body { reorderPoint, safetyStock, reorderQty } — 200 | 404 POLICY_NOT_FOUND
 *     §  GET|PUT /api/v1/demand-planning/sku-supplier-map/{skuCode}
 *          body { supplierId, defaultOrderQty, leadTimeDays, currency } — 200 | 404 MAPPING_NOT_FOUND
 * Consumer obligation: `console-integration-contract.md` § 2.4.6.2 (reuses the
 * § 2.4.6 / § 2.4.6.1 per-domain credential rule — NOT re-derived).
 *
 * DESIGN — **no list route**: the producer exposes ONLY per-`{skuCode}`
 * GET/PUT. There is therefore NO page/list view-model — the surface is
 * SKU-code-driven (the operator enters a SKU code, the screen GETs both rows).
 *
 * 404-as-empty-state: a GET 404 (`POLICY_NOT_FOUND` / `MAPPING_NOT_FOUND`) is
 * NOT an error — it is "not configured yet, create via PUT". The api client
 * surfaces it as a typed {@link NotFoundResult}, never a thrown error.
 *
 * TOLERANCE: the view-models keep only the fields the UI needs and pass the
 * rest through (`.passthrough()`) — a forward-compatible producer extra field
 * never throws.
 */

// --- reorder policy view-model -------------------------------------------
//   demand-planning-api.md § PUT /policies/{skuCode} body shape.

export const ReorderPolicySchema = z
  .object({
    reorderPoint: z.number().optional(),
    safetyStock: z.number().optional(),
    reorderQty: z.number().optional(),
  })
  .passthrough();
export type ReorderPolicy = z.infer<typeof ReorderPolicySchema>;

/** PUT body for a reorder-policy upsert (the FULL row — the body IS the row;
 *  idempotent upsert, no Idempotency-Key, no X-Operator-Reason). Non-negative
 *  integers (a negative qty is a producer `VALIDATION_ERROR` (422) — the
 *  client validates shape too so the operator sees an inline error early). */
export const ReorderPolicyInputSchema = z.object({
  reorderPoint: z.number().int().nonnegative(),
  safetyStock: z.number().int().nonnegative(),
  reorderQty: z.number().int().positive(),
});
export type ReorderPolicyInput = z.infer<typeof ReorderPolicyInputSchema>;

// --- sku→supplier mapping view-model -------------------------------------
//   demand-planning-api.md § PUT /sku-supplier-map/{skuCode} body shape.

export const SupplierMapSchema = z
  .object({
    supplierId: z.string().optional(),
    defaultOrderQty: z.number().optional(),
    leadTimeDays: z.number().optional(),
    // ISO-4217-ish; tolerated as a free string (an unknown/future code renders
    // generically and never throws on read).
    currency: z.string().optional(),
  })
  .passthrough();
export type SupplierMap = z.infer<typeof SupplierMapSchema>;

/** PUT body for a sku-supplier-map upsert (the FULL row). `supplierId` is a
 *  FREE-TEXT / uuid in v1 — there is no supplier master to resolve against
 *  (the `sku_supplier_map` is the deliberate minimal stand-in per ADR-MONO-027
 *  D3); validate SHAPE only, never resolve a non-existent supplier-service. */
export const SupplierMapInputSchema = z.object({
  supplierId: z.string().trim().min(1),
  defaultOrderQty: z.number().int().positive(),
  leadTimeDays: z.number().int().nonnegative(),
  // A 3-letter ISO-4217-style code; constrained input (uppercased UX).
  currency: z
    .string()
    .trim()
    .regex(/^[A-Za-z]{3}$/, 'currency must be a 3-letter code'),
});
export type SupplierMapInput = z.infer<typeof SupplierMapInputSchema>;

// --- typed GET result (200 found | 404 not-configured-yet) ---------------
//   The 404-as-empty-state discriminated union — the api client NEVER throws
//   on a seed-lookup 404; it returns `{ found: false }` so the screen renders
//   a "not configured yet → create" state (NOT an error toast).

export interface FoundResult<T> {
  found: true;
  value: T;
}
export interface NotFoundResult {
  found: false;
}
export type SeedLookup<T> = FoundResult<T> | NotFoundResult;

/** The closed set of currencies the upsert form offers as quick options. An
 *  arbitrary 3-letter code is still accepted (the input is free-text). */
export const COMMON_CURRENCIES = ['KRW', 'USD', 'EUR', 'JPY', 'CNY'] as const;
