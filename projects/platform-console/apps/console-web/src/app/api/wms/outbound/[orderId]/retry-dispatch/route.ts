import { NextResponse } from 'next/server';
import {
  resolveShipmentIdForOrder,
  resolveDispatchIdForShipment,
  retryDispatch,
} from '@/features/wms-outbound-ops/api/outbound-api';
import {
  ActionBodySchema,
  mapOutboundError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin operator **dispatch-retry** mutation proxy (TASK-PC-FE-258 —
 * console-integration-contract § 2.4.5.1 op 10, repointed from the retired wms
 * TMS notify side-channel to logistics `dispatches/{id}:retry`, ADR-MONO-053 §D8):
 *
 *   1. Resolve the order's `shipmentId` from the wms admin read-model
 *      `GET /api/v1/admin/dashboard/shipments?orderId={id}` (the outbound
 *      order-centric reads carry no `shipmentId`). No shipment → 404
 *      `SHIPMENT_NOT_FOUND` (inline actionable — NO dispatch resolve, NO retry).
 *   2. Resolve the carrier dispatch from **logistics-service** (scm gateway)
 *      `GET /api/v1/logistics/dispatches/by-shipment/{shipmentId}`. No dispatch
 *      yet (the wms `outbound.shipping.confirmed` seam event not consumed) → 404
 *      `DISPATCH_NOT_FOUND` (inline actionable "아직 발송 접수 전" — NO retry POST).
 *   3. `POST /api/v1/logistics/dispatches/{id}:retry` (§ logistics-service) →
 *      re-drives a `DISPATCH_FAILED` dispatch (already-`DISPATCHED` → cached ack,
 *      no vendor call — naturally idempotent).
 *
 * TWO GATEWAYS, TWO CREDENTIALS-BY-SAME-TOKEN: the wms admin read hits the wms
 * gateway; the logistics resolve + retry hit the **scm** gateway
 * (`SCM_GATEWAY_BASE_URL`) — REUSING the proven scm demand-planning
 * mutation client (`callScmGateway`, the domain-facing IAM OIDC credential —
 * § 2.4.6), NOT the wms `callOutbound` client. Both are attached server-side; no
 * new credential/env/entitlement. Reason-free (empty body + stable
 * `Idempotency-Key`); role is producer-enforced — a `403 FORBIDDEN` maps inline.
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
      // The order has no projected shipment — inline actionable, no dispatch
      // resolve and no retry POST fired.
      return NextResponse.json(
        {
          code: 'SHIPMENT_NOT_FOUND',
          message: 'no shipment found for this order',
        },
        { status: 404 },
      );
    }
    const dispatch = await resolveDispatchIdForShipment(shipmentId);
    if (!dispatch) {
      // The shipment exists but no carrier dispatch has been created yet (the
      // seam event has not been consumed) — inline actionable, NO retry fired.
      return NextResponse.json(
        {
          code: 'DISPATCH_NOT_FOUND',
          message: 'no dispatch found for this shipment yet',
        },
        { status: 404 },
      );
    }
    const result = await retryDispatch(dispatch.id, body.idempotencyKey);
    return NextResponse.json(result);
  } catch (err) {
    return mapOutboundError(err, requestId);
  }
}
