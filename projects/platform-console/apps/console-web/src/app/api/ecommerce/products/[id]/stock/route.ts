import { NextResponse } from 'next/server';
import { adjustStock } from '@/features/ecommerce-ops/api/products-api';
import {
  AdjustStockRequestSchema,
  mapEcommerceError,
  badRequest,
  tryParse,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce **adjust stock** mutation proxy
 * (console-integration-contract § 2.4.10 #9):
 * `PATCH /admin/products/{id}/stock`.
 *
 * AdjustStockRequest carries the target `variantId`, a SIGNED `quantity` delta,
 * and a required `reason` (IN THE BODY, not a header). Domain-facing IAM OIDC
 * token server-side; Zod-validated; confirm-gated. The producer now REQUIRES
 * `Idempotency-Key` (TASK-BE-536); the console mints it per confirmed adjustment
 * and sends it in the body (TASK-PC-FE-252) — stripped out here and passed as a
 * separate arg. A `400 INSUFFICIENT_STOCK` (decrement below zero) surfaces inline.
 */
export async function PATCH(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;

  let parsed;
  try {
    parsed = tryParse(AdjustStockRequestSchema, await req.json());
  } catch {
    return badRequest();
  }
  if (parsed === null) return badRequest();
  const { idempotencyKey, ...body } = parsed;

  try {
    const result = await adjustStock(id, body, idempotencyKey);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
