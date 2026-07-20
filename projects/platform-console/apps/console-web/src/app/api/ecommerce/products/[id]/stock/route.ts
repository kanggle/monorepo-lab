import { NextResponse } from 'next/server';
import { adjustStock } from '@/features/ecommerce-ops/api/products-api';
import {
  AdjustStockBodySchema,
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
 * `Idempotency-Key` (TASK-BE-536) — `products-api.ts#adjustStock` mints one per
 * call. A `400 INSUFFICIENT_STOCK` (decrement below zero) is surfaced inline.
 */
export async function PATCH(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;

  let body;
  try {
    body = tryParse(AdjustStockBodySchema, await req.json());
  } catch {
    return badRequest();
  }
  if (body === null) return badRequest();

  try {
    const result = await adjustStock(id, body);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
