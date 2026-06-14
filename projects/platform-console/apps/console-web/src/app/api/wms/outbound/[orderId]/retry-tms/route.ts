import { NextResponse } from 'next/server';
import {
  resolveShipmentIdForOrder,
  retryTmsNotify,
} from '@/features/wms-outbound-ops/api/outbound-api';
import {
  ActionBodySchema,
  mapOutboundError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin wms outbound **manual TMS retry** mutation proxy (TASK-PC-FE-087
 * — console-integration-contract § 2.4.5.1 op 10):
 *
 *   1. Resolve the order's `shipmentId` from the admin read-model
 *      `GET /api/v1/admin/dashboard/shipments?orderId={id}` (the outbound
 *      order-centric reads carry no `shipmentId`). No shipment → 404
 *      `SHIPMENT_NOT_FOUND` (NO outbound retry POST is fired).
 *   2. `POST /shipments/{shipmentId}:retry-tms-notify` (§ 4.3) → re-triggers
 *      the carrier notification (saga `SHIPPED_NOT_NOTIFIED` → `COMPLETED` on
 *      success; stays failed if the carrier is still down — retry-able again).
 *
 * Reason-free (re-notify only — UNLIKE the reason-required cancel), so the body
 * is just the stable `Idempotency-Key` (reuse `ActionBodySchema`). The producer
 * enforces `OUTBOUND_ADMIN`; the console does NOT pre-gate on role — a
 * `403 FORBIDDEN` is mapped to an inline actionable state. The domain-facing
 * IAM OIDC token (NOT the operator token — § 2.4.5.1) is attached server-side
 * for BOTH the admin read and the outbound mutation (same gateway + credential,
 * distinct path prefix).
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ orderId: string }> },
) {
  const requestId = newRequestId();
  const { orderId } = await params;

  let body;
  try {
    body = ActionBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }

  try {
    const shipmentId = await resolveShipmentIdForOrder(orderId);
    if (!shipmentId) {
      // The order has no projected shipment to re-notify — inline actionable,
      // no outbound retry POST fired.
      return NextResponse.json(
        {
          code: 'SHIPMENT_NOT_FOUND',
          message: 'no shipment found for this order',
        },
        { status: 404 },
      );
    }
    const result = await retryTmsNotify(shipmentId, body.idempotencyKey);
    return NextResponse.json(result);
  } catch (err) {
    return mapOutboundError(err, requestId);
  }
}
