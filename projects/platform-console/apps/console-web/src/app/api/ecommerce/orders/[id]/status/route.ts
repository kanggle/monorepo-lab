import { NextResponse } from 'next/server';
import { changeOrderStatus } from '@/features/ecommerce-ops/api/orders-api';
import {
  mapEcommerceError,
  badRequest,
  tryParse,
  newRequestId,
} from '../../../products/_proxy';
import { OrderStatusChangeBodySchema } from '@/features/ecommerce-ops/api/order-types';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce **change order status** mutation proxy
 * (console-integration-contract § 2.4.10 #17):
 * `POST /admin/orders/{id}/status`.
 *
 * Body: `{ status: string }` — validated with Zod before forwarding.
 * The domain-facing IAM OIDC token is attached server-side in `orders-api.ts`.
 * NO `Idempotency-Key` (the producer defines none — § 2.4.10): confirm-gate +
 * producer state guards (400 / 422 / 409) are the double-submit defence.
 *
 * Error mapping (passthrough to the client as inline actionable):
 *   - 400 InvalidOrder (invalid forward transition / unknown status) → 400
 *   - 422 OrderCannotBeCancelled → 422
 *   - 409 CONFLICT (optimistic lock) → 409
 *   - 404 OrderNotFound → 404
 *   - 503 / timeout → 503 (section degrades only)
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;

  let body;
  try {
    body = tryParse(OrderStatusChangeBodySchema, await req.json());
  } catch {
    return badRequest();
  }
  if (body === null) return badRequest();

  try {
    const result = await changeOrderStatus(id, body);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
