import { NextResponse } from 'next/server';
import {
  listPickingRequests,
  confirmPick,
  type ConfirmPickLine,
} from '@/features/wms-outbound-ops/api/outbound-api';
import {
  ActionBodySchema,
  mapOutboundError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin wms outbound **Pick** mutation proxy (console-integration-
 * contract § 2.4.5.1 — confirm-as-planned). The client supplies ONLY a stable
 * `idempotencyKey` (confirm-gated, reason-free); the compound orchestration is
 * server-side:
 *
 *   1. `GET /orders/{id}/picking-requests` (§2.4 / TASK-BE-343) → take
 *      `content[0]` (v1: at most one picking request per order).
 *   2. Build the §2.3 confirmation lines from the planned lines —
 *      `actualLocationId = line.locationId`, `qtyConfirmed = line.qtyToPick`,
 *      carrying `orderLineId`/`skuId`/`lotId` through verbatim. The console
 *      NEVER fabricates a `locationId` or quantity.
 *   3. `POST /picking-requests/{pickingRequestId}/confirmations`.
 *
 * If `content` is empty (the saga has not yet reserved → no picking request
 * written) → an actionable `422 OUTBOUND_NO_PICKING_REQUEST` (NOT a crash);
 * the UI gates Pick on saga `RESERVED` but the server is authoritative.
 *
 * The domain-facing IAM OIDC token is attached server-side (NOT the operator
 * token — § 2.4.5.1); the single producer mutation carries the supplied
 * `Idempotency-Key`, no `X-Operator-Reason`.
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
    const planned = await listPickingRequests(orderId);
    const request = planned.content[0];
    if (!request) {
      // Not yet reserved → no picking request to confirm. Actionable, no crash.
      return NextResponse.json(
        {
          code: 'OUTBOUND_NO_PICKING_REQUEST',
          message: 'no picking request exists for this order yet',
        },
        { status: 422 },
      );
    }

    // Confirm-as-planned: map planned locationId/qtyToPick → actualLocationId/
    // qtyConfirmed (the console never invents warehouse master data).
    const lines: ConfirmPickLine[] = request.lines.map((l) => ({
      orderLineId: l.orderLineId,
      skuId: l.skuId,
      lotId: l.lotId ?? null,
      actualLocationId: l.locationId,
      qtyConfirmed: l.qtyToPick,
    }));

    const result = await confirmPick(
      request.pickingRequestId,
      lines,
      body.idempotencyKey,
    );
    return NextResponse.json(result);
  } catch (err) {
    return mapOutboundError(err, requestId);
  }
}
