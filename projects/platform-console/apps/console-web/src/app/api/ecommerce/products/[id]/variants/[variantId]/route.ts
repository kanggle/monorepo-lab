import { NextResponse } from 'next/server';
import {
  updateVariant,
  deleteVariant,
} from '@/features/ecommerce-ops/api/products-api';
import {
  UpdateVariantBodySchema,
  mapEcommerceError,
  badRequest,
  tryParse,
  newRequestId,
} from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce **update variant** mutation proxy
 * (console-integration-contract § 2.4.10 #7):
 * `PATCH /admin/products/{id}/variants/{variantId}`.
 *
 * UpdateVariantRequest carries optionName + additionalPrice (NO stock — stock
 * is adjusted via #9). Domain-facing IAM OIDC token server-side; Zod-validated;
 * NO `Idempotency-Key`; confirm-gated.
 */
export async function PATCH(
  req: Request,
  { params }: { params: Promise<{ id: string; variantId: string }> },
) {
  const requestId = newRequestId();
  const { id, variantId } = await params;

  let body;
  try {
    body = tryParse(UpdateVariantBodySchema, await req.json());
  } catch {
    return badRequest();
  }
  if (body === null) return badRequest();

  try {
    const result = await updateVariant(id, variantId, body);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}

/**
 * Same-origin ecommerce **delete variant** mutation proxy
 * (console-integration-contract § 2.4.10 #8):
 * `DELETE /admin/products/{id}/variants/{variantId}`. Producer returns 204;
 * the proxy returns 204. Confirm-gated.
 */
export async function DELETE(
  _req: Request,
  { params }: { params: Promise<{ id: string; variantId: string }> },
) {
  const requestId = newRequestId();
  const { id, variantId } = await params;
  try {
    await deleteVariant(id, variantId);
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
