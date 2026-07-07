import {
  OutboundOrderPageSchema,
  type OutboundOrderPage,
  OutboundOrderDetailSchema,
  type OutboundOrderDetail,
  OutboundSagaSchema,
  type OutboundSaga,
  PickingRequestListSchema,
  type PickingRequestList,
  CancelResultSchema,
  type CancelResult,
  type OutboundListParams,
} from './types';
import { callOutbound, clampSize } from './outbound-client';

/**
 * ORDER domain — wms outbound-service order reads and cancel mutation
 * (TASK-PC-FE-147 sub-module; behavior-preserving split of `outbound-api.ts`).
 *
 * Covers: § 1.2 getOrder, § 1.3 listOrders, § 1.4 cancelOrder, § 2.4
 * listPickingRequests, § 5.1 getSaga.
 *
 * External code must import from `outbound-api.ts` (the barrel), not directly
 * from this file.
 */

// ===========================================================================
// READS (OUTBOUND_READ — no mutation artifacts)
// ===========================================================================

/** 1.3 — GET /orders (paginated order summaries). */
export function listOrders(
  params: OutboundListParams = {},
): Promise<OutboundOrderPage> {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  if (params.warehouseId) qs.set('warehouseId', params.warehouseId);
  if (params.orderNo) qs.set('orderNo', params.orderNo);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return callOutbound({ method: 'GET', path: `/orders?${qs.toString()}` }, (j) =>
    OutboundOrderPageSchema.parse(j),
  );
}

/** 1.2 — GET /orders/{id} (lines + status + version). */
export function getOrder(orderId: string): Promise<OutboundOrderDetail> {
  return callOutbound(
    { method: 'GET', path: `/orders/${encodeURIComponent(orderId)}` },
    (j) => OutboundOrderDetailSchema.parse(j),
  );
}

/** 5.1 — GET /orders/{id}/saga. */
export function getSaga(orderId: string): Promise<OutboundSaga> {
  return callOutbound(
    { method: 'GET', path: `/orders/${encodeURIComponent(orderId)}/saga` },
    (j) => OutboundSagaSchema.parse(j),
  );
}

/** 2.4 — GET /orders/{id}/picking-requests (planned lines; may be `[]`). */
export function listPickingRequests(
  orderId: string,
): Promise<PickingRequestList> {
  return callOutbound(
    {
      method: 'GET',
      path: `/orders/${encodeURIComponent(orderId)}/picking-requests`,
    },
    (j) => PickingRequestListSchema.parse(j),
  );
}

// ===========================================================================
// MUTATIONS (OUTBOUND_WRITE/ADMIN — each Idempotency-Key, reason-free except cancel)
// ===========================================================================

/**
 * 1.4 — POST /orders/{id}:cancel (cancel order, reason-required,
 * version-checked). TASK-PC-FE-085.
 *
 * Diverges from the reason-free forward mutations: a **REQUIRED `reason`**
 * (3..500, producer § 1.4) rides in the JSON body (NOT a header — the wms
 * surface still has no `X-Operator-Reason`). Role is producer-enforced
 * (`OUTBOUND_WRITE` for PICKING / `OUTBOUND_ADMIN` post-pick) — a 403 maps
 * inline; the console never pre-gates on role. Note the `:cancel` action
 * suffix on the path (not a `/cancel` sub-resource).
 */
export function cancelOrder(
  orderId: string,
  version: number,
  reason: string,
  idempotencyKey: string,
): Promise<CancelResult> {
  return callOutbound(
    {
      method: 'POST',
      path: `/orders/${encodeURIComponent(orderId)}:cancel`,
      idempotencyKey,
      body: { reason, version },
    },
    (j) => CancelResultSchema.parse(j),
  );
}
