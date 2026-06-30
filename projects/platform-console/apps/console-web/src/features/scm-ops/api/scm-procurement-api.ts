import {
  PoPageSchema,
  type PoPage,
  PurchaseOrderSchema,
  type PurchaseOrder,
  type PoQueryParams,
  type ScmResult,
} from './types';
import { callScm, pageParams } from './scm-client';

/**
 * scm procurement (PO) read endpoints (TASK-PC-FE-145 split of the former
 * single `scm-api.ts`). STRICTLY READ-ONLY — pure GETs, NO PO write
 * (`/submit|/confirm|/cancel`), NO body, NO idempotency. The credential /
 * resilience / error-envelope narrative lives in the shared `scm-client.ts`
 * core (`callScm`). 0 behavior / contract / log-event change.
 */

// ---------------------------------------------------------------------------
// procurement — PO list (search) — GET /api/v1/procurement/po
//   procurement-api.md envelope = { data: { content, page, size,
//   totalElements, totalPages }, meta }. READ-ONLY.
// ---------------------------------------------------------------------------

export async function listPurchaseOrders(
  params: PoQueryParams = {},
): Promise<ScmResult<PoPage>> {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  if (params.supplierId) qs.set('supplierId', params.supplierId);
  pageParams(qs, params.page, params.size);
  const { raw } = await callScm(
    { path: `/api/v1/procurement/po?${qs.toString()}` },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return PoPageSchema.parse(env.data);
    },
  );
  return { data: raw, cache: null };
}

// ---------------------------------------------------------------------------
// procurement — PO detail — GET /api/v1/procurement/po/{poId}
// ---------------------------------------------------------------------------

export async function getPurchaseOrder(
  poId: string,
): Promise<ScmResult<PurchaseOrder>> {
  const { raw } = await callScm(
    { path: `/api/v1/procurement/po/${encodeURIComponent(poId)}` },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return PurchaseOrderSchema.parse(env.data);
    },
  );
  return { data: raw, cache: null };
}
