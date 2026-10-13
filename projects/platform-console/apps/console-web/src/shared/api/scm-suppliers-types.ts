import { z } from 'zod';

/**
 * Client-safe wire shape of the scm procurement **supplier master** list
 * (TASK-PC-FE-337 — `console-integration-contract.md` § 2.4.6 reuse).
 * zod only: no `next/headers`, no cookies, no fetch — both the server-only
 * read (`scm-suppliers-read.ts`) and the client hooks that parse the
 * same-origin proxy answer import this module. Split from the read module
 * per `architecture.md` § Forbidden Dependencies ("승격 시 server read 와
 * pure 타입 모듈을 분리" — a client component importing a module that reaches
 * `next/headers` breaks `next build`).
 *
 * Authoritative producer contract (do NOT redefine — consumed read-only):
 *   `scm-platform/specs/contracts/http/procurement-api.md`
 *     § `GET /api/procurement/suppliers` (list) — element shape identical to
 *     the § `POST /api/procurement/suppliers` 201 body's `data`.
 *
 * Two console screens share this list (the reason it is promoted to
 * `shared/` rather than living inside one feature — `features/A → features/B`
 * import is forbidden):
 *   - `features/scm-ops` `ScmPoTable` — picks a supplier's **id** for the
 *     `GET /po?supplierId=` filter.
 *   - `features/scm-config` `SupplierMapForm` — picks a supplier's **code**
 *     as the `sku-supplier-map` `supplierId` value (demand-planning-api.md —
 *     that field is the supplier business CODE, not the master row id;
 *     ADR-MONO-050 D9 / TASK-MONO-677).
 *
 * TOLERANCE: `status` is a free string — an unknown/future value renders
 * generically and never throws (the console never gates on the producer's
 * closed set).
 */

export const SupplierSchema = z
  .object({
    id: z.string(),
    tenantId: z.string().optional(),
    code: z.string(),
    name: z.string(),
    // ACTIVE | … — tolerated as a free string (unknown/future → generic label).
    status: z.string().optional(),
    contractStartedAt: z.string().nullable().optional(),
    contractExpiresAt: z.string().nullable().optional(),
    createdAt: z.string().optional(),
    updatedAt: z.string().optional(),
  })
  .passthrough();
export type Supplier = z.infer<typeof SupplierSchema>;

/** `GET /api/procurement/suppliers` → `{ data: { content, page, size,
 *  totalElements, totalPages }, meta }` — the SAME paginated envelope shape
 *  as `GET /api/procurement/po` (procurement-api.md § GET suppliers note). */
export const SupplierPageSchema = z.object({
  content: z.array(SupplierSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative().optional(),
});
export type SupplierPage = z.infer<typeof SupplierPageSchema>;

export interface SupplierQueryParams {
  status?: string;
  page?: number;
  size?: number;
}

// --- pagination -----------------------------------------------------------
// The producer's Pageable hard cap (`libs/java-common` `PageQuery.MAX_SIZE`,
// also the value the console already clamps the PO list query to) — a
// request above this is still served, just capped by the producer, so the
// console clamps client-side to the SAME number rather than discover a
// silent truncation.

export const SCM_SUPPLIER_DEFAULT_PAGE_SIZE = 20;
export const SCM_SUPPLIER_MAX_PAGE_SIZE = 100;
