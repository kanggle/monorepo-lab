import { NextResponse } from 'next/server';
import { updateShippingStatus } from '@/features/ecommerce-ops/api/shippings-api';
import {
  UpdateShippingStatusBodySchema,
  mapEcommerceError,
  badRequest,
  tryParse,
  newRequestId,
} from '../../../products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce **shipping status update** mutation proxy
 * (TASK-PC-FE-088 — console-integration-contract § 2.4.10.3):
 * `PUT /api/ecommerce/shippings/{id}/status`
 *
 * Body: `{ status, trackingNumber?, carrier? }` — validated with Zod before
 * forwarding. `trackingNumber` + `carrier` are required when `status=SHIPPED`;
 * the producer enforces the constraint (400 InvalidShipping) and so does the
 * UI (ShipFormDialog); this proxy passes both through.
 *
 * Domain-facing IAM OIDC token is attached server-side in `shippings-api.ts`.
 * NO `Idempotency-Key` (the producer defines none — § 2.4.10); confirm-gate
 * + producer state guards (400/409/422) are the double-submit defence.
 *
 * Error passthrough:
 *   - 400 InvalidShipping (SHIPPED without carrier/tracking) / INVALID_STATUS
 *   - 404 SHIPPING_NOT_FOUND
 *   - 409/422 INVALID_TRANSITION (non-linear jump; UI shouldn't offer it)
 *   - 503 / timeout → 503 (section degrades only)
 */
export async function PUT(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;

  let body;
  try {
    body = tryParse(UpdateShippingStatusBodySchema, await req.json());
  } catch {
    return badRequest();
  }
  if (body === null) return badRequest();

  try {
    const result = await updateShippingStatus(id, body);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
