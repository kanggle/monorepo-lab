import { NextResponse } from 'next/server';
import {
  getOrder,
  confirmShipping,
} from '@/features/wms-outbound-ops/api/outbound-api';
import {
  ActionBodySchema,
  mapOutboundError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin wms outbound **Ship** mutation proxy (console-integration-
 * contract § 2.4.5.1):
 *
 *   1. `GET /orders/{id}` → the current order `version` (optimistic lock).
 *   2. `POST /orders/{id}/shipments` (§4.1) `{ carrierCode: "DEMO-CARRIER",
 *      version }` → order SHIPPED (and, via the existing return-leg events,
 *      flips the ecommerce order to SHIPPED — ADR-MONO-022 § D7).
 *
 * On `409 CONFLICT` (stale version — someone else advanced it) the error is
 * surfaced inline → the UI refetches + prompts retry (no silent auto-retry
 * with a bumped version — the lost-update hazard). The domain-facing IAM OIDC
 * token is attached server-side (NOT the operator token — § 2.4.5.1); the
 * mutation carries the supplied `Idempotency-Key`, no `X-Operator-Reason`.
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
    const order = await getOrder(orderId);
    const result = await confirmShipping(
      orderId,
      order.version,
      body.idempotencyKey,
    );
    return NextResponse.json(result);
  } catch (err) {
    return mapOutboundError(err, requestId);
  }
}
