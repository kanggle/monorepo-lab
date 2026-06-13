import { NextResponse } from 'next/server';
import {
  updateProduct,
  deleteProduct,
} from '@/features/ecommerce-ops/api/products-api';
import {
  UpdateProductBodySchema,
  mapEcommerceError,
  badRequest,
  tryParse,
  newRequestId,
} from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce **update product** mutation proxy
 * (console-integration-contract § 2.4.10 #4): `PATCH /admin/products/{id}`.
 *
 * Partial update (the producer UpdateProductRequest is all-optional). The
 * domain-facing IAM OIDC token is attached server-side; the body is
 * Zod-validated. NO `Idempotency-Key`; confirm-gated. On `409 CONFLICT`
 * (optimistic lock) the error is surfaced inline → refetch + retry-prompt.
 */
export async function PATCH(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;

  let body;
  try {
    body = tryParse(UpdateProductBodySchema, await req.json());
  } catch {
    return badRequest();
  }
  if (body === null) return badRequest();

  try {
    const result = await updateProduct(id, body);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}

/**
 * Same-origin ecommerce **delete product** mutation proxy
 * (console-integration-contract § 2.4.10 #5): `DELETE /admin/products/{id}`.
 * Producer returns 204; the proxy returns 204. Confirm-gated in the UI.
 */
export async function DELETE(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    await deleteProduct(id);
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
