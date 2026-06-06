import { z } from 'zod';

/**
 * Feature-local types for the scm gateway's read-only procurement-PO +
 * inventory-visibility surface (TASK-PC-FE-008 — ADR-MONO-013 Phase 4
 * slice 2, the SECOND non-IAM federated domain — completes Phase 4).
 *
 * Authoritative producer contracts (do NOT redefine — consume read-only):
 *   `scm-platform/specs/contracts/http/procurement-api.md`
 *     § `GET /api/procurement/po` (list) + § `GET .../po/{poId}` (detail)
 *   `scm-platform/specs/contracts/http/inventory-visibility-api.md`
 *     § `/snapshot` `/sku/{sku}` `/staleness` `/nodes`
 * Consumer obligation: `console-integration-contract.md` § 2.4.6 (reuses
 * the § 2.4.5 per-domain credential rule — NOT re-derived).
 *
 * These zod schemas are the runtime parsers the api-client / tests assert
 * against. They are feature-local (not cross-feature) per
 * architecture.md § Allowed Dependencies.
 *
 * TOLERANCE invariant (§ 2.4.6 / task Edge Case "Unknown/future enum"):
 * every read shape is permissive — unknown / future PO `status`, node
 * `status`, `staleness` values parse to a generic value and NEVER throw.
 * Only the fields the UI strictly needs are required; everything else is
 * passthrough.
 *
 * S5 invariant (§ 2.4.6, NORMATIVE — a CONTRACT obligation, not a UX
 * nicety): every inventory-visibility response carries the producer
 * envelope `meta.warning: "Not for procurement decisions (S5)"`. It is
 * modeled here as a **REQUIRED, surfaced** field of every
 * inventory-visibility view-model — never optional / discardable. The UI
 * MUST render it prominently and MUST NOT strip / hide / de-emphasise it.
 */

// --- scm success/meta envelope -------------------------------------------
// procurement-api.md / inventory-visibility-api.md:
//   { data, meta: { timestamp, warning?, staleness?, nodeId?, count? } }
// The inventory-visibility meta ALWAYS carries `warning` (S5). The
// procurement-PO meta does NOT (PO is the authoritative procurement record).

/** The S5 visibility-warning string (producer-fixed text). The console
 *  never invents / localises it away — it surfaces the producer string. */
export const S5_WARNING = 'Not for procurement decisions (S5)';

/** inventory-visibility meta — `warning` is REQUIRED (S5 contract
 *  obligation). A response missing it is a producer contract breach; the
 *  parser still does not throw (defensive — it falls back to the canonical
 *  S5 string so the warning is NEVER silently dropped from the view). */
export const VisibilityMetaSchema = z
  .object({
    timestamp: z.string().optional(),
    warning: z.string().optional(),
    staleness: z.string().optional(),
    nodeId: z.string().optional(),
    count: z.number().optional(),
  })
  .passthrough()
  .transform((m) => ({
    ...m,
    // S5 is a REQUIRED surfaced field — never discardable. If the producer
    // omitted it (contract breach) we still surface the canonical warning
    // rather than hide it (the obligation is to NEVER strip/hide it).
    warning: m.warning && m.warning.trim() ? m.warning : S5_WARNING,
  }));
export type VisibilityMeta = z.infer<typeof VisibilityMetaSchema>;

// ---------------------------------------------------------------------------
// procurement — PO read (list + detail). NO write surface (read-only).
// ---------------------------------------------------------------------------

export const PoLineSchema = z
  .object({
    id: z.string().optional(),
    lineNo: z.number().optional(),
    sku: z.string().optional(),
    supplierSku: z.string().nullable().optional(),
    quantity: z.string().optional(),
    unitPrice: z.string().optional(),
    receivedQuantity: z.string().optional(),
  })
  .passthrough();
export type PoLine = z.infer<typeof PoLineSchema>;

export const PurchaseOrderSchema = z
  .object({
    id: z.string(),
    tenantId: z.string().optional(),
    poNumber: z.string().optional(),
    supplierId: z.string().optional(),
    buyerAccountId: z.string().nullable().optional(),
    // PoStatus is a producer enum — tolerated as a free string so an
    // unknown/future status renders a generic label, never a parser throw.
    status: z.string().optional(),
    totalAmount: z.string().optional(),
    currency: z.string().optional(),
    submittedAt: z.string().nullable().optional(),
    acknowledgedAt: z.string().nullable().optional(),
    confirmedAt: z.string().nullable().optional(),
    canceledAt: z.string().nullable().optional(),
    cancellationReason: z.string().nullable().optional(),
    createdAt: z.string().optional(),
    updatedAt: z.string().optional(),
    lines: z.array(PoLineSchema).optional(),
  })
  .passthrough();
export type PurchaseOrder = z.infer<typeof PurchaseOrderSchema>;

/** `GET /api/v1/procurement/po` →
 *  `{ data: { content, page, size, totalElements, totalPages }, meta }`.
 *  procurement-api.md does NOT carry the S5 warning (PO is the
 *  authoritative procurement record). */
export const PoPageSchema = z.object({
  content: z.array(PurchaseOrderSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative().optional(),
});
export type PoPage = z.infer<typeof PoPageSchema>;

// ---------------------------------------------------------------------------
// inventory-visibility — snapshot / sku / staleness / nodes (read-only).
// EVERY view-model carries the REQUIRED S5 `warning` (surfaced, not
// discardable).
// ---------------------------------------------------------------------------

export const SnapshotRowSchema = z
  .object({
    id: z.string().optional(),
    nodeId: z.string().optional(),
    sku: z.string().optional(),
    quantity: z.number().optional(),
    lastEventAt: z.string().nullable().optional(),
    version: z.number().optional(),
    // FRESH | STALE | UNREACHABLE — tolerated as string (unknown → generic).
    staleness: z.string().optional(),
  })
  .passthrough();
export type SnapshotRow = z.infer<typeof SnapshotRowSchema>;

/** Cross-node snapshot WITHOUT nodeId = paginated list; the page envelope
 *  itself has no totalPages in inventory-visibility-api.md (only
 *  totalElements) — tolerated. */
export const SnapshotPageDataSchema = z.object({
  content: z.array(SnapshotRowSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalElements: z.number().int().nonnegative(),
});

/** The full snapshot response: the paginated cross-node form (no nodeId)
 *  OR the array form (single nodeId). Both carry the REQUIRED S5 meta. */
export const SnapshotResponseSchema = z.object({
  data: z.union([SnapshotPageDataSchema, z.array(SnapshotRowSchema)]),
  meta: VisibilityMetaSchema,
});
export type SnapshotResponse = z.infer<typeof SnapshotResponseSchema>;

export const SkuNodeSchema = z
  .object({
    nodeId: z.string(),
    quantity: z.number().optional(),
    staleness: z.string().optional(),
  })
  .passthrough();
export type SkuNode = z.infer<typeof SkuNodeSchema>;

export const SkuBreakdownSchema = z.object({
  data: z
    .object({
      sku: z.string(),
      nodes: z.array(SkuNodeSchema),
      totalQuantity: z.number().optional(),
    })
    .passthrough(),
  meta: VisibilityMetaSchema,
});
export type SkuBreakdown = z.infer<typeof SkuBreakdownSchema>;

export const StalenessRowSchema = z
  .object({
    nodeId: z.string(),
    stalenessStatus: z.string().optional(),
    lastEventAt: z.string().nullable().optional(),
    lastCheckedAt: z.string().nullable().optional(),
  })
  .passthrough();
export type StalenessRow = z.infer<typeof StalenessRowSchema>;

export const StalenessResponseSchema = z.object({
  data: z.array(StalenessRowSchema),
  meta: VisibilityMetaSchema,
});
export type StalenessResponse = z.infer<typeof StalenessResponseSchema>;

export const NodeRowSchema = z
  .object({
    id: z.string(),
    nodeExternalId: z.string().optional(),
    nodeType: z.string().optional(),
    name: z.string().optional(),
    // ACTIVE | … — tolerated (unknown/future status → generic label).
    status: z.string().optional(),
  })
  .passthrough();
export type NodeRow = z.infer<typeof NodeRowSchema>;

export const NodesResponseSchema = z.object({
  data: z.array(NodeRowSchema),
  meta: VisibilityMetaSchema,
});
export type NodesResponse = z.infer<typeof NodesResponseSchema>;

// --- query params ---------------------------------------------------------

export const SCM_DEFAULT_PAGE_SIZE = 20;
export const SCM_MAX_PAGE_SIZE = 100;

export interface PoQueryParams {
  status?: string;
  supplierId?: string;
  page?: number;
  size?: number;
}

export interface SnapshotQueryParams {
  nodeId?: string;
  page?: number;
  size?: number;
}

/** A list/detail/snapshot result + the optional `X-Cache` freshness hint
 *  (HIT|MISS|UNAVAILABLE) the per-SKU read surfaces. */
export interface ScmResult<T> {
  data: T;
  /** inventory-visibility `X-Cache` header on the per-SKU read; `null`
   *  when the producer did not set it. Surfaced honestly (§ 2.4.6). */
  cache: 'HIT' | 'MISS' | 'UNAVAILABLE' | null;
}
